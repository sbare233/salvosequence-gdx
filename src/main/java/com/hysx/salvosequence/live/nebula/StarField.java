package com.hysx.salvosequence.live.nebula;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;

import java.util.Random;

/**
 * Camera facing star sprites, built from the same distribution rules the add-on uses:
 * volume distributed points filtered by noise (Star Coverage / Star Density), random
 * sizes between Min/Max Star Size and a blue-to-warm colour ramp per star.
 */
public final class StarField {
    private static final int STRIDE = 10;
    private final Mesh mesh;
    private final int stars;

    public StarField(long seed, int large, float largeMin, float largeMax,
                     int small, float smallMin, float smallMax,
                     float coverage, float density, float radius, float noiseScale) {
        Random rnd = new Random(seed * 0x9E3779B97F4A7C15L + 12345L);
        FloatArray v = new FloatArray(60 * (large + small));
        int count = 0;

        for (int i = 0; i < large; i++) {
            Vector3 p = randomPoint(rnd, radius * 0.92f);
            float size = MathUtils.lerp(largeMin, largeMax, rnd.nextFloat());
            addSprite(v, p, size, starColour(rnd), 1f);
            count++;
        }
        for (int i = 0; i < small; i++) {
            Vector3 p = randomPoint(rnd, radius * 0.95f);
            float n = fbm(p.x * noiseScale, p.y * noiseScale, p.z * noiseScale, 3, 0.5f);
            if (n < coverage) continue;
            if (rnd.nextFloat() > density) continue;
            float size = MathUtils.lerp(smallMin, smallMax, rnd.nextFloat() * rnd.nextFloat());
            addSprite(v, p, size, starColour(rnd), 0f);
            count++;
        }

        stars = count;
        mesh = new Mesh(true, count * 4, count * 6,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_center"),
                new VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_corner"),
                new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_size"),
                new VertexAttribute(VertexAttributes.Usage.Generic, 3, "a_color"),
                new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_glare"));
        mesh.setVertices(v.toArray());

        short[] idx = new short[count * 6];
        for (int i = 0; i < count; i++) {
            int o = i * 4;
            int d = i * 6;
            idx[d] = (short) o;
            idx[d + 1] = (short) (o + 1);
            idx[d + 2] = (short) (o + 2);
            idx[d + 3] = (short) o;
            idx[d + 4] = (short) (o + 2);
            idx[d + 5] = (short) (o + 3);
        }
        mesh.setIndices(idx);
    }

    public int starCount() {
        return stars;
    }

    public void render(ShaderProgram shader, Matrix4 viewProj, Vector3 camRight, Vector3 camUp) {
        shader.setUniformMatrix("u_viewProj", viewProj);
        shader.setUniformf("u_camRight", camRight);
        shader.setUniformf("u_camUp", camUp);
        mesh.render(shader, GL20.GL_TRIANGLES);
    }

    public void dispose() {
        mesh.dispose();
    }

    private static Vector3 randomPoint(Random rnd, float radius) {
        // uniform inside a ball
        double u = rnd.nextDouble();
        double r = radius * Math.cbrt(u);
        double ct = 2.0 * rnd.nextDouble() - 1.0;
        double st = Math.sqrt(Math.max(0.0, 1.0 - ct * ct));
        double ph = 2.0 * Math.PI * rnd.nextDouble();
        return new Vector3((float) (r * st * Math.cos(ph)), (float) (r * st * Math.sin(ph)), (float) (r * ct));
    }

    private static float[] starColour(Random rnd) {
        float t = rnd.nextFloat();
        float r = MathUtils.lerp(0.16f, 0.86f, t);
        float g = MathUtils.lerp(0.28f, 0.80f, t);
        float b = MathUtils.lerp(0.95f, 0.72f, t);
        float bright = 0.35f + 0.65f * rnd.nextFloat() * rnd.nextFloat();
        return new float[]{r * bright, g * bright, b * bright};
    }

    private void addSprite(FloatArray v, Vector3 c, float size, float[] col, float glare) {
        float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (float[] k : corners) {
            v.add(c.x);
            v.add(c.y);
            v.add(c.z);
            v.add(k[0]);
            v.add(k[1]);
            v.add(size);
            v.add(col[0]);
            v.add(col[1]);
            v.add(col[2]);
            v.add(glare);
        }
    }

    // ---- CPU twin of the shader noise, used only for placement decisions ----
    private static float fract(float x) {
        return x - (float) Math.floor(x);
    }

    private static float hash(float x, float y, float z) {
        float px = fract(x * 0.3183099f + 0.1f);
        float py = fract(y * 0.3183099f + 0.2f);
        float pz = fract(z * 0.3183099f + 0.3f);
        px *= 17f;
        py *= 17f;
        pz *= 17f;
        return fract(px * py * pz * (px + py + pz));
    }

    private static float vnoise(float x, float y, float z) {
        float ix = (float) Math.floor(x), iy = (float) Math.floor(y), iz = (float) Math.floor(z);
        float fx = x - ix, fy = y - iy, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        fz = fz * fz * (3 - 2 * fz);
        float n000 = hash(ix, iy, iz);
        float n100 = hash(ix + 1, iy, iz);
        float n010 = hash(ix, iy + 1, iz);
        float n110 = hash(ix + 1, iy + 1, iz);
        float n001 = hash(ix, iy, iz + 1);
        float n101 = hash(ix + 1, iy, iz + 1);
        float n011 = hash(ix, iy + 1, iz + 1);
        float n111 = hash(ix + 1, iy + 1, iz + 1);
        float nx00 = MathUtils.lerp(n000, n100, fx);
        float nx10 = MathUtils.lerp(n010, n110, fx);
        float nx01 = MathUtils.lerp(n001, n101, fx);
        float nx11 = MathUtils.lerp(n011, n111, fx);
        return MathUtils.lerp(MathUtils.lerp(nx00, nx10, fy), MathUtils.lerp(nx01, nx11, fy), fz);
    }

    private static float fbm(float x, float y, float z, int oct, float rough) {
        float a = 0.5f, sum = 0f, norm = 0f;
        for (int i = 0; i < oct; i++) {
            sum += a * vnoise(x, y, z);
            norm += a;
            x = x * 2.02f + 13.7f;
            y = y * 2.02f + 7.1f;
            z = z * 2.02f + 5.3f;
            a *= rough;
        }
        return sum / Math.max(norm, 1e-4f);
    }
}
