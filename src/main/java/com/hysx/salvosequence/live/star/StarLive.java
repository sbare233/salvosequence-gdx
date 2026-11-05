package com.hysx.salvosequence.live.star;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.nio.ByteBuffer;

/**
 * 近景恒星实时渲染（背景工坊 / 战斗背景用）：光球颗粒 + 黑子 + 边缘变暗 + 日冕 + 泛光/星芒。
 *
 * <p>移植自 {@code star-libgdx-mvp} 的 {@code StarApp}，砍掉调参 UI 与导出，改成固定 320×180
 * 的离屏渲染；相机拉远并把恒星推到画面左上角，当"远处的一颗太阳"用。</p>
 */
public final class StarLive {

    public static final int FBW = 320;
    public static final int FBH = 180;

    private final Params params = new Params();
    private final Vector3 seedVec = new Vector3();
    private final Vector3 tmpRight = new Vector3();
    private final Vector3 tmpUp = new Vector3();
    private final Matrix4 model = new Matrix4();

    private PerspectiveCamera cam;
    private StarSphere sphere;
    private ShaderProgram starShader;
    private Corona corona;
    private Bloom bloom;
    private FrameBuffer out;

    private boolean ready;
    /** 恒星在画面里的位置（0..1，左上为 0,0）与视距。 */
    public double offsetX = 0.74;
    public double offsetY = 0.28;
    public float distance = 9.6f;
    /** 手动相机（不随时间转）。 */
    public double yawRad = 0.6;
    public double pitchRad = 0.18;

    public void ensureGl() {
        if (ready) {
            return;
        }
        seedVec.set((params.seed % 997) * 0.137f, (params.seed % 691) * 0.211f, (params.seed % 431) * 0.317f);
        cam = new PerspectiveCamera(45f, FBW, FBH);
        cam.near = 0.05f;
        cam.far = 100f;
        sphere = new StarSphere();
        starShader = new ShaderProgram(
                Gdx.files.internal("shaders/star/star.vert"),
                Gdx.files.internal("shaders/star/star.frag"));
        if (!starShader.isCompiled()) {
            throw new IllegalStateException("star shader: " + starShader.getLog());
        }
        corona = new Corona(params.seed, 6);
        bloom = new Bloom(FBW, FBH);
        out = new FrameBuffer(Pixmap.Format.RGBA8888, FBW, FBH, false);
        ready = true;
    }

    public Params params() {
        return params;
    }

    public void orbit(double dx, double dy) {
        yawRad -= dx * 0.006;
        pitchRad = Math.max(-1.35, Math.min(1.35, pitchRad + dy * 0.006));
    }

    public void zoom(double notches) {
        distance = (float) Math.max(4.0, Math.min(24.0, distance * Math.pow(1.0015, -notches * 40.0)));
    }

    public double[] camera() {
        return new double[]{Math.toDegrees(yawRad), Math.toDegrees(pitchRad), distance};
    }

    public void setCamera(double yaw, double pitch, double dist) {
        yawRad = Math.toRadians(yaw);
        pitchRad = Math.toRadians(Math.max(-75, Math.min(75, pitch)));
        distance = (float) Math.max(4.0, Math.min(24.0, dist));
    }

    public int[] render(double time) {
        ensureGl();
        float yaw = (float) yawRad;
        float pitch = (float) pitchRad;

        cam.viewportWidth = FBW;
        cam.viewportHeight = FBH;
        cam.position.set(
                distance * MathUtils.cos(pitch) * MathUtils.sin(yaw),
                distance * MathUtils.sin(pitch),
                distance * MathUtils.cos(pitch) * MathUtils.cos(yaw));
        cam.up.set(0, 1, 0);
        cam.lookAt(0, 0, 0);
        // 把恒星推到背景角落：沿相机右/上轴反向平移
        float wpp = 2f * distance * MathUtils.tanDeg(cam.fieldOfView * 0.5f) / FBH;
        tmpRight.set(cam.direction).crs(cam.up).nor();
        tmpUp.set(tmpRight).crs(cam.direction).nor();
        cam.position.mulAdd(tmpRight, (float) ((0.5 - offsetX) * 2.0) * (FBW * 0.5f) * wpp);
        cam.position.mulAdd(tmpUp, (float) ((offsetY - 0.5) * 2.0) * (FBH * 0.5f) * wpp);
        cam.update();

        corona.update((float) time, params.flares);

        bloom.beginScene();
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        model.idt();
        model.rotate(0.35f, 0.82f, 0.45f, 12f + (float) time * 1.6f);

        starShader.begin();
        starShader.setUniformMatrix("u_projTrans", cam.combined);
        starShader.setUniformMatrix("u_model", model);
        starShader.setUniformf("u_camPos", cam.position);
        starShader.setUniformf("u_time", (float) time);
        starShader.setUniform3fv("u_seed", new float[]{seedVec.x, seedVec.y, seedVec.z}, 0, 3);
        starShader.setUniformf("u_gran", params.granulation);
        starShader.setUniformf("u_spot", params.spots);
        starShader.setUniformf("u_limb", params.limb);
        starShader.setUniformf("u_exposure", params.exposure);
        starShader.setUniformf("u_dbg", 0f);
        sphere.render(starShader);
        starShader.end();
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);

        float tanHalf = MathUtils.tanDeg(cam.fieldOfView * 0.5f);
        corona.render(cam, (float) time, params.prominences, params.exposure, tanHalf,
                FBW / (float) FBH, seedVec);

        Vector3 center = new Vector3(0, 0, 0).prj(cam.combined);
        float su = (center.x + 1f) * 0.5f;
        float sv = (center.y + 1f) * 0.5f;

        out.begin();
        Gdx.gl.glViewport(0, 0, FBW, FBH);
        bloom.endSceneAndComposite(params.bloom * 1.35f, params.spikes, params.dispersion, su, sv);

        Pixmap src = Pixmap.createFromFrameBuffer(0, 0, FBW, FBH);
        out.end();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        int[] argb = new int[FBW * FBH];
        ByteBuffer bb = src.getPixels();
        for (int y = 0; y < FBH; y++) {
            int srcY = FBH - 1 - y;
            for (int x = 0; x < FBW; x++) {
                int i = (srcY * FBW + x) * 4;
                int r = bb.get(i) & 0xFF;
                int g = bb.get(i + 1) & 0xFF;
                int b = bb.get(i + 2) & 0xFF;
                argb[y * FBW + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        src.dispose();
        return argb;
    }

    public void dispose() {
        if (!ready) {
            return;
        }
        ready = false;
        sphere.dispose();
        starShader.dispose();
        corona.dispose();
        bloom.dispose();
        out.dispose();
    }
}
