package com.hysx.salvosequence.live.pulsar;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * 中子星本体。
 *
 * 这里**不用球体网格**，而是画一个全屏四边形，在片元着色器里做射线-球求交：
 * 球面由解析式给出，天然精确，没有 UV 接缝、没有三角形绕序问题、
 * 也不会出现"背面被剔掉导致看到内壁"这类毛病。
 */
public final class NeutronStar
{
    private final FullscreenQuad quad;
    private final ShaderProgram shader;

    private final Vector3 right = new Vector3();
    private final Vector3 up = new Vector3();

    public NeutronStar()
    {
        quad = new FullscreenQuad();
        shader = new ShaderProgram(
                Gdx.files.internal("shaders/pulsar/star.vert"),
                Gdx.files.internal("shaders/pulsar/star.frag"));
        if (!shader.isCompiled())
            throw new IllegalStateException("star shader: " + shader.getLog());
    }

    public void render(Camera cam, float spinRad, Vector3 magAxisWorld, float[] spotDirs,
                       float radius, float temp, float capBright, float limb, float exposure)
    {
        PerspectiveCamera pc = (PerspectiveCamera) cam;
        right.set(pc.direction).crs(pc.up).nor();
        up.set(right).crs(pc.direction).nor();
        float tanHalf = (float) Math.tan(Math.toRadians(pc.fieldOfView * 0.5f));
        float aspect = pc.viewportWidth / Math.max(1f, pc.viewportHeight);

        // 星体写深度：这样后面画的喷流用深度测试就能正确地"前面可见、后面被挡"。
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        shader.begin();
        Uniforms.v3(shader, "u_camPos", pc.position);
        Uniforms.v3(shader, "u_camRight", right);
        Uniforms.v3(shader, "u_camUp", up);
        Uniforms.v3(shader, "u_camFwd", pc.direction);
        Uniforms.f(shader, "u_tanHalf", tanHalf);
        Uniforms.f(shader, "u_aspect", aspect);
        Uniforms.f(shader, "u_spinRad", spinRad);
        Uniforms.v3(shader, "u_magAxis", magAxisWorld);
        int loc = shader.getUniformLocation("u_spots");
        if (loc < 0) loc = shader.getUniformLocation("u_spots[0]");
        if (loc >= 0) shader.setUniform3fv(loc, spotDirs, 0, spotDirs.length / 3);
        Uniforms.f(shader, "u_radius", radius);
        Uniforms.f(shader, "u_temp", temp);
        Uniforms.f(shader, "u_spot", capBright);
        Uniforms.f(shader, "u_limb", limb);
        Uniforms.f(shader, "u_exposure", exposure);
        Uniforms.m(shader, "u_viewProj", cam.combined);
        quad.render(shader);
        shader.end();
    }

    public void dispose()
    {
        quad.dispose();
        shader.dispose();
    }
}
