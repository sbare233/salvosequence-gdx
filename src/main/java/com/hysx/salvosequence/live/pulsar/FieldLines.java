package com.hysx.salvosequence.live.pulsar;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * 偶极磁力线：r = L·sin²θ，在磁坐标系里采样，绕磁轴若干方位铺开。
 * 用 GL_LINES + 加法混合，避免线宽依赖（兼容 profile 下 glLineWidth 不可靠）。
 */
public final class FieldLines
{
    private final Mesh mesh;
    private final ShaderProgram shader;
    private final int segments;

    public FieldLines()
    {
        float[] shells = { 1.65f, 2.45f, 3.60f };
        int azim = 5;
        int steps = 64;
        segments = shells.length * azim * steps;

        float[] v = new float[segments * 2 * 4];
        int k = 0;
        for (float L : shells)
        {
            for (int a = 0; a < azim; a++)
            {
                double phi = 2.0 * Math.PI * a / azim;
                float cx = (float) Math.cos(phi), cz = (float) Math.sin(phi);
                for (int i = 0; i < steps; i++)
                {
                    double t0 = Math.PI * (0.10 + 0.80 * i / (double) steps);
                    double t1 = Math.PI * (0.10 + 0.80 * (i + 1) / (double) steps);
                    k = put(v, k, L, t0, cx, cz);
                    k = put(v, k, L, t1, cx, cz);
                }
            }
        }

        mesh = new Mesh(true, segments * 2, 0,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, "a_color"));
        mesh.setVertices(v);

        shader = new ShaderProgram(
                Gdx.files.internal("shaders/pulsar/lines.vert"),
                Gdx.files.internal("shaders/pulsar/lines.frag"));
        if (!shader.isCompiled())
            throw new IllegalStateException("lines shader: " + shader.getLog());
    }

    private static int put(float[] v, int k, float L, double theta, float cx, float cz)
    {
        double s = Math.sin(theta);
        double r = L * s * s;
        float x = (float) (r * s * cx);
        float y = (float) (r * Math.cos(theta));
        float z = (float) (r * s * cz);
        v[k++] = x; v[k++] = y; v[k++] = z;

        // 靠近星体的线更亮，远处淡出
        float d = (float) Math.sqrt(x * x + y * y + z * z);
        float t = Math.max(0f, Math.min(1f, 1.0f - (d - 1.0f) / 5.0f));
        Color c = new Color(0.32f + 0.30f * t, 0.52f + 0.34f * t, 0.95f, 1f);
        v[k++] = c.toFloatBits();
        return k;
    }

    public void render(Matrix4 viewProj, Matrix4 model, float alpha)
    {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);

        Matrix4 mvp = new Matrix4(viewProj).mul(model);
        shader.begin();
        Uniforms.m(shader, "u_mvp", mvp);
        Uniforms.f(shader, "u_alpha", alpha);
        mesh.render(shader, GL20.GL_LINES);
        shader.end();

        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    public void dispose()
    {
        mesh.dispose();
        shader.dispose();
    }
}
