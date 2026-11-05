package com.hysx.salvosequence.live.nebula;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.stream.IntStream;

/**
 * Bakes the fractal cloud volume once per seed instead of evaluating the noise chain
 * inside the ray marcher.
 *
 * <p>The volume is stored as a 2D atlas of {@code n} slices (a 3D texture needs a core
 * profile context, which would break libGDX's GLSL 120 sprite shaders), so the shader
 * mimics a trilinear fetch with two bilinear taps. Every slice tile carries a one texel
 * wrap border, so filtering never bleeds between tiles.
 *
 * <p>Channels: R = fine cloud (domain warped "detail" field), G = medium band,
 * B = coarse band (void mask + warm/cool split). Each channel is contrast stretched to
 * the full 0..1 range after baking, otherwise the density ramps never bite.
 */
public final class NebulaNoise {
    public final long seed;
    public final int n;        // slices per axis
    public final int tile;     // n + 2
    public final int grid;     // slices per atlas row
    public final int atlas;    // atlas edge in texels
    public final ByteBuffer rgb;

    public NebulaNoise(long seed, int n) {
        this.seed = seed;
        this.n = Math.max(16, n);
        this.tile = this.n + 2;
        this.grid = (int) Math.ceil(Math.sqrt(this.n));
        this.atlas = this.grid * this.tile;
        this.rgb = ByteBuffer.allocateDirect(this.atlas * this.atlas * 3).order(ByteOrder.nativeOrder());

        final int vox = this.n * this.n * this.n;
        final float[][] ch = new float[3][vox];
        final float[] ox = offset(seed, 0.137f);
        final float[] oy = offset(seed, 0.531f);
        final float[] oz = offset(seed, 0.921f);

        IntStream.range(0, this.n).parallel().forEach(z -> bakeSlice(z, ch, ox, oy, oz));

        // contrast stretch each channel to 0..1 so the shader ramps have real range
        final float[] lo = new float[3], hi = new float[3];
        for (int c = 0; c < 3; c++) {
            float mn = 1f, mx = 0f;
            for (int i = 0; i < vox; i++) {
                float v = ch[c][i];
                if (v < mn) mn = v;
                if (v > mx) mx = v;
            }
            lo[c] = mn;
            hi[c] = Math.max(mx - mn, 1e-4f);
        }

        if (Boolean.getBoolean("nebula.noiseStats")) {
            for (int c = 0; c < 3; c++) {
                float[] v = ch[c].clone();
                java.util.Arrays.sort(v);
                StringBuilder sb = new StringBuilder("nebula: channel " + c + " pct");
                for (int q = 5; q <= 95; q += 5) {
                    float x = (v[(int) ((q / 100f) * (v.length - 1))] - lo[c]) / hi[c];
                    sb.append(String.format(" p%d=%.3f", q, x));
                }
                System.out.println(sb);
            }
        }
        for (int z = 0; z < this.n; z++) {
            writeSlice(z, ch, lo, hi);
            fillBorders(z);
        }
        rgb.rewind();
    }

    private static float[] offset(long seed, float salt) {
        long h = seed * 0x9E3779B97F4A7C15L + (long) (salt * 1e6f);
        float[] o = new float[3];
        for (int i = 0; i < 3; i++) {
            h ^= h >>> 33;
            h *= 0xFF51AFD7ED558CCDL;
            h ^= h >>> 33;
            o[i] = ((h >>> 11) & 0xFFFFF) / (float) 0xFFFFF;
        }
        return o;
    }

    // ---------------------------------------------------------------- noise
    /** Lattice hash wrapped modulo {@code period}, so the field repeats exactly on the unit cube. */
    private static float hash(int x, int y, int z, int period) {
        int h = Math.floorMod(x, period) * 374761393
              + Math.floorMod(y, period) * 668265263
              + Math.floorMod(z, period) * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0x00FFFFFF) / (float) 0x01000000;
    }

    private static float vnoise(float x, float y, float z, int period) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        float fx = x - xi, fy = y - yi, fz = z - zi;
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);
        fz = fz * fz * (3f - 2f * fz);

        float n000 = hash(xi, yi, zi, period), n100 = hash(xi + 1, yi, zi, period);
        float n010 = hash(xi, yi + 1, zi, period), n110 = hash(xi + 1, yi + 1, zi, period);
        float n001 = hash(xi, yi, zi + 1, period), n101 = hash(xi + 1, yi, zi + 1, period);
        float n011 = hash(xi, yi + 1, zi + 1, period), n111 = hash(xi + 1, yi + 1, zi + 1, period);

        float nx00 = n000 + (n100 - n000) * fx;
        float nx10 = n010 + (n110 - n010) * fx;
        float nx01 = n001 + (n101 - n001) * fx;
        float nx11 = n011 + (n111 - n011) * fx;
        float nxy0 = nx00 + (nx10 - nx00) * fy;
        float nxy1 = nx01 + (nx11 - nx01) * fy;
        return nxy0 + (nxy1 - nxy0) * fz;
    }

    /** Highest octave count whose top frequency (base * 2^o) stays under Nyquist. */
    private static int clampOctaves(int base, float nyquist, int max) {
        int o = 1;
        int f = base;
        while (o < max && f * 2 < nyquist) {
            f *= 2;
            o++;
        }
        return o;
    }

    /**
     * Periodic fbm: octave {@code i} runs at frequency {@code base << i} and wraps its lattice at
     * exactly that frequency, so the sum repeats on the unit cube instead of merely resembling it.
     */
    private static float fbm(float x, float y, float z, int base, int octaves, float roughness) {
        float amp = 0.5f, sum = 0f, norm = 0f;
        int freq = base;
        for (int i = 0; i < octaves; i++) {
            sum += amp * vnoise(x * freq, y * freq, z * freq, freq);
            norm += amp;
            amp *= roughness;
            freq *= 2;
        }
        return sum / Math.max(norm, 1e-4f);
    }

    // ---------------------------------------------------------------- bake
    private void bakeSlice(int z, float[][] ch, float[] ox, float[] oy, float[] oz) {
        int n2 = n * n;
        float nyq = n * 0.5f;
        // Integer bases only: the shader rewinds with fract(), which is only seamless if the
        // baked field is exactly 1-periodic on the unit cube.
        final int warpBase = 2;
        final int fineBase = 12;
        final int midBase = 4;
        final int coarseBase = 2;
        final int warpOct = clampOctaves(warpBase, nyq, 3);
        final int fineOct = clampOctaves(fineBase, nyq, 4);
        final int midOct = clampOctaves(midBase, nyq, 5);
        final int coarseOct = clampOctaves(coarseBase, nyq, 5);

        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                // x / n, not x / (n - 1): the last sample has to sit one step before the wrap.
                float px = x / (float) n;
                float py = y / (float) n;
                float pz = z / (float) n;

                // domain warp (the "Distortion" socket of Blender's noise node)
                float wx = fbm(px + ox[0], py + ox[1], pz + ox[2], warpBase, warpOct, 0.5f) - 0.5f;
                float wy = fbm(px + oy[0] + 0.37f, py + oy[1], pz + oy[2], warpBase, warpOct, 0.5f) - 0.5f;
                float wz = fbm(px + oz[0], py + oz[1], pz + oz[2] + 0.71f, warpBase, warpOct, 0.5f) - 0.5f;

                float qx = px + wx * 0.28f;
                float qy = py + wy * 0.28f;
                float qz = pz + wz * 0.28f;

                // Octave counts keep the top frequency under the volume's Nyquist limit (n / 2
                // cycles). Overshooting aliases, which made the old 96^3 bake look grainy.
                float fine = fbm(qx + ox[0], qy + ox[1], qz + ox[2], fineBase, fineOct, 0.574f);
                float mid = fbm(qx + oy[0], qy + oy[1], qz + oy[2], midBase, midOct, 0.55f);
                float coarse = fbm(qx + oz[0], qy + oz[1], qz + oz[2], coarseBase, coarseOct, 0.62f);

                int i = z * n2 + y * n + x;
                ch[0][i] = fine;
                ch[1][i] = mid;
                ch[2][i] = coarse;
            }
        }
    }

    private void writeSlice(int z, float[][] ch, float[] lo, float[] hi) {
        int n2 = n * n;
        int gx = z % grid;
        int gy = z / grid;
        for (int y = 0; y < n; y++) {
            int ty = gy * tile + 1 + y;
            for (int x = 0; x < n; x++) {
                int i = z * n2 + y * n + x;
                int tx = gx * tile + 1 + x;
                int idx = (ty * atlas + tx) * 3;
                rgb.put(idx, (byte) clamp255((ch[0][i] - lo[0]) / hi[0]));
                rgb.put(idx + 1, (byte) clamp255((ch[1][i] - lo[1]) / hi[1]));
                rgb.put(idx + 2, (byte) clamp255((ch[2][i] - lo[2]) / hi[2]));
            }
        }
    }

    private static int clamp255(float v) {
        int i = Math.round(v * 255f);
        return i < 0 ? 0 : Math.min(i, 255);
    }

    /** Copies the interior ring outward so bilinear filtering wraps instead of bleeding. */
    private void fillBorders(int z) {
        int gx = z % grid;
        int gy = z / grid;
        int ox = gx * tile;
        int oy = gy * tile;
        for (int i = 0; i < n; i++) {
            copy(ox + 1 + i, oy + 0, ox + 1 + i, oy + n);          // top row  <- bottom
            copy(ox + 1 + i, oy + n + 1, ox + 1 + i, oy + 1);      // bottom row <- top
            copy(ox + 0, oy + 1 + i, ox + n, oy + 1 + i);          // left col <- right
            copy(ox + n + 1, oy + 1 + i, ox + 1, oy + 1 + i);      // right col <- left
        }
        copy(ox + 0, oy + 0, ox + n, oy + n);
        copy(ox + n + 1, oy + 0, ox + 1, oy + n);
        copy(ox + 0, oy + n + 1, ox + n, oy + 1);
        copy(ox + n + 1, oy + n + 1, ox + 1, oy + 1);
    }

    private void copy(int dx, int dy, int sx, int sy) {
        int d = (dy * atlas + dx) * 3;
        int s = (sy * atlas + sx) * 3;
        rgb.put(d, rgb.get(s));
        rgb.put(d + 1, rgb.get(s + 1));
        rgb.put(d + 2, rgb.get(s + 2));
    }
}
