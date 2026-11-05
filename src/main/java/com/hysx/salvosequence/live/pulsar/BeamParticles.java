package com.hysx.salvosequence.live.pulsar;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.util.Random;

/**
 * 脉冲束：用几千颗软点精灵堆成的体积柱（和星云那套一样）。
 *
 * <p>为什么不用锥面壳：壳只有一层，正对轴看过去时视线方向与锥面近乎相切，
 * 片元里算出来的"弦长"反而趋于零，于是正对屏幕最暗——正好反了。
 * 粒子柱是真正的体积：正对轴看时视线穿过最多粒子，堆叠起来最亮。
 */
public final class BeamParticles
{
    private static final int MAX_PER_POLE = 160000;   // 一次分配够，运行时只画前面 count 颗

    private final Mesh mesh;
    private final ShaderProgram shader;

    private final Vector3 tmpRight = new Vector3();
    private final Vector3 tmpUp = new Vector3();
    private final Vector3 tmpAxis = new Vector3();

    public BeamParticles()
    {
        Random rnd = new Random(20261031L);
        float[] v = new float[MAX_PER_POLE * 4];
        int p = 0;
        for (int i = 0; i < MAX_PER_POLE; i++)
        {
            // 沿轴：靠近根部略密（pow 0.85），末端散开
            float t = (float) Math.pow(rnd.nextFloat(), 0.85f);
            float ang = rnd.nextFloat() * MathUtils.PI2;
            float rad = rnd.nextFloat();          // 0..1，着色器里再开方
            float r2 = rnd.nextFloat();
            v[p++] = t;
            v[p++] = ang;
            v[p++] = rad;
            v[p++] = r2;
        }

        // 用 Position(2) + Normal(2) 这两个标准语义承载 4 个浮点。
        // 不要用两个 Usage.Generic 自定义属性：libGDX 在这个组合上会串位（彗星那边踩过）。
        mesh = new Mesh(true, MAX_PER_POLE, 0,
                new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_ta"),
                new VertexAttribute(VertexAttributes.Usage.Normal, 2, "a_rr"));
        mesh.setVertices(v);

        shader = new ShaderProgram(
                Gdx.files.internal("shaders/pulsar/beampt.vert"),
                Gdx.files.internal("shaders/pulsar/beampt.frag"));
        if (!shader.isCompiled())
            throw new IllegalStateException("beampt shader: " + shader.getLog());
    }

    public void render(PerspectiveCamera cam, Vector3 camPos, float radius, Vector3 axis, float start, float base, float length,
                       float widthRad, float bright, Vector3 color, float falloff, float rollRad, float particleSize,
                       float time, float speed, int count)
    {
        // 束流先画、不做深度测试：随后画的不透明星体会把它盖住，
        // 视觉上就是"束流从星体后面射出"。遮挡在顶点着色器里用解析射线-球求交算。
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        // 兼容 profile 下点精灵需要这两个开关，否则 gl_PointCoord / gl_PointSize 无效
        Gdx.gl.glEnable(0x8861);   // GL_POINT_SPRITE
        Gdx.gl.glEnable(0x8642);   // GL_VERTEX_PROGRAM_POINT_SIZE

        float px = cam.viewportHeight / (2f * (float) Math.tan(Math.toRadians(cam.fieldOfView * 0.5f)));

        shader.begin();
        Matrix4 viewProj = cam.combined;
        Uniforms.m(shader, "u_mvp", viewProj);
        Uniforms.v3(shader, "u_camPos", camPos);
        Uniforms.f(shader, "u_radius", radius);
        Uniforms.f(shader, "u_length", length);
        Uniforms.f(shader, "u_base", base);
        Uniforms.f(shader, "u_width", widthRad);
        Uniforms.v3(shader, "u_color", color);
        Uniforms.f(shader, "u_bright", bright);
        Uniforms.f(shader, "u_falloff", falloff);
        Uniforms.f(shader, "u_roll", rollRad);
        Uniforms.f(shader, "u_px", px);
        Uniforms.f(shader, "u_size", particleSize);
        Uniforms.f(shader, "u_time", time);
        Uniforms.f(shader, "u_speed", speed);
        // 粒子数归一：n 越多每颗越淡（按 sqrt 缩放），总量缓慢上升而不是线性爆掉
        Uniforms.f(shader, "u_density", MathUtils.clamp((float) Math.sqrt(40000.0 / Math.max(count, 1)), 0.25f, 3.0f));

        for (int pole = 0; pole < 2; pole++)
        {
            tmpAxis.set(axis);
            if (pole == 1) tmpAxis.scl(-1f);

            Vector3 helper = Math.abs(tmpAxis.y) < 0.9f ? new Vector3(0f, 1f, 0f) : new Vector3(1f, 0f, 0f);
            tmpRight.set(tmpAxis).crs(helper).nor();
            tmpUp.set(tmpRight).crs(tmpAxis).nor();

            Uniforms.v3(shader, "u_axis", tmpAxis);
            Uniforms.v3(shader, "u_right", tmpRight);
            Uniforms.v3(shader, "u_up", tmpUp);
            Uniforms.f3(shader, "u_apex", tmpAxis.x * start, tmpAxis.y * start, tmpAxis.z * start);

            mesh.render(shader, GL20.GL_POINTS, 0, Math.min(count, MAX_PER_POLE));
        }

        shader.end();

        Gdx.gl.glDisable(0x8861);
        Gdx.gl.glDisable(0x8642);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    public void dispose()
    {
        mesh.dispose();
        shader.dispose();
    }
}
