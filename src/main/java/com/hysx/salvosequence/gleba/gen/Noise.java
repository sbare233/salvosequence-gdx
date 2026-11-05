// 来源：gleba-libgdx-mvp（蓝色大肥鱼）——Gleba 星球生成核心，包名归入本项目。
package com.hysx.salvosequence.gleba.gen;

/**
 * 3D Perlin 梯度噪声（Ken Perlin improved noise）+ fbm。
 * 与 libGDX 的 MathUtils.random 解耦：同一个 seed 永远得到同一张星球。
 */
public final class Noise {
    private final int[] perm = new int[512];
    private final float[] gx = new float[256], gy = new float[256], gz = new float[256];

    public Noise(long seed) {
        java.util.Random r = new java.util.Random(seed);
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = p[i]; p[i] = p[j]; p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
        for (int i = 0; i < 256; i++) {
            // 均匀球面随机梯度
            double z = r.nextDouble() * 2 - 1;
            double a = r.nextDouble() * Math.PI * 2;
            double s = Math.sqrt(1 - z * z);
            gx[i] = (float) (s * Math.cos(a));
            gy[i] = (float) (s * Math.sin(a));
            gz[i] = (float) z;
        }
    }

    private static float fade(float t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /** 3D Perlin，返回约 [-1,1]。 */
    public float noise(float x, float y, float z) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        int zi = (int) Math.floor(z) & 255;
        float xf = x - (float) Math.floor(x);
        float yf = y - (float) Math.floor(y);
        float zf = z - (float) Math.floor(z);
        float u = fade(xf), v = fade(yf), w = fade(zf);

        int a = perm[xi] + yi, aa = perm[a] + zi, ab = perm[a + 1] + zi;
        int b = perm[xi + 1] + yi, ba = perm[b] + zi, bb = perm[b + 1] + zi;

        float x1 = lerp(grad(aa, xf, yf, zf), grad(ba, xf - 1, yf, zf), u);
        float x2 = lerp(grad(ab, xf, yf - 1, zf), grad(bb, xf - 1, yf - 1, zf), u);
        float y1 = lerp(x1, x2, v);

        x1 = lerp(grad(aa + 1, xf, yf, zf - 1), grad(ba + 1, xf - 1, yf, zf - 1), u);
        x2 = lerp(grad(ab + 1, xf, yf - 1, zf - 1), grad(bb + 1, xf - 1, yf - 1, zf - 1), u);
        float y2 = lerp(x1, x2, v);

        return lerp(y1, y2, w);
    }

    private float grad(int hash, float x, float y, float z) {
        int h = hash & 255;
        return gx[h] * x + gy[h] * y + gz[h] * z;
    }

    /** 分形叠加，返回约 [-1,1]。 */
    public float fbm(float x, float y, float z, int octaves, float lacunarity, float gain) {
        float sum = 0, amp = 1, norm = 0, freq = 1;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise(x * freq, y * freq, z * freq);
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return norm > 0 ? sum / norm : 0;
    }

    public float fbm(float x, float y, float z, int octaves) {
        return fbm(x, y, z, octaves, 2.03f, 0.5f);
    }

    /** 山脊型噪声，返回 [0,1]，适合山脉细节。 */
    public float ridged(float x, float y, float z, int octaves) {
        float sum = 0, amp = 1, norm = 0, freq = 1;
        for (int i = 0; i < octaves; i++) {
            float n = 1f - Math.abs(noise(x * freq, y * freq, z * freq));
            sum += amp * n * n;
            norm += amp;
            amp *= 0.5f;
            freq *= 2.07f;
        }
        return norm > 0 ? sum / norm : 0;
    }
}
