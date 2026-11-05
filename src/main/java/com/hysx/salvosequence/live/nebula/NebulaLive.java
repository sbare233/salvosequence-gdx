package com.hysx.salvosequence.live.nebula;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

import java.nio.ByteBuffer;

/**
 * 星云实时渲染（背景工坊 / 战斗背景用）：体积光线步进 + 星点 + 泛光，输出 320×180 ARGB。
 *
 * <p>移植自 {@code nebula-libgdx-mvp} 的 {@code NebulaApp}，去掉场景 2D 调参界面与自动降分辨率，
 * 只保留渲染核心。与 {@code GlebaPlanet} 同一个套路：GL 资源惰性创建 → 每帧 {@link #render}
 * 返回一帧像素 → 主循环按最近邻 2 倍贴进设计缓冲。</p>
 */
public final class NebulaLive {

    public static final int FBW = 320;
    public static final int FBH = 180;

    private final Params params = new Params();

    private FullscreenQuad quad;
    private ShaderProgram volume;
    private ShaderProgram starShader;
    private PerspectiveCamera cam;
    private FrameBuffer scene;
    /** 最终合成目标：bloom 的合成结果先落这里，再从它读回 ARGB。 */
    private FrameBuffer out;
    private BloomPipeline bloom;
    private StarField stars;
    private NebulaNoise noise;
    private int noiseTex;

    private final Vector3 lightDir = new Vector3(0.22f, 0.34f, 0.91f).nor();
    private final Vector3 warmDir = new Vector3(-0.75f, 0.35f, 0.55f).nor();
    private final Vector3 camRight = new Vector3();
    private final Vector3 camUp = new Vector3();

    private boolean ready;
    /** 手动相机：不再随时间自动环绕（背景工坊里用指针拖、滚轮缩放）。 */
    private float distance = 1.30f;
    private float yawDeg = -118.0f;
    private float pitchDeg = 9.0f;

    /** 用固定种子生成星点、烤一张噪声体积；必须在 GL 线程调用（首次渲染时惰性执行）。 */
    public void ensureGl() {
        if (ready) {
            return;
        }
        quad = new FullscreenQuad();
        volume = FullscreenQuad.load("shaders/nebula/quad.vert", "shaders/nebula/volume.frag");
        starShader = FullscreenQuad.load("shaders/nebula/star.vert", "shaders/nebula/star.frag");

        cam = new PerspectiveCamera(params.fov, FBW, FBH);
        cam.near = 0.01f;
        cam.far = 40f;

        bakeNoise();
        stars = new StarField(params.seed, params.largeStars, params.largeStarMin, params.largeStarMax,
                params.smallStars, params.smallStarMin, params.smallStarMax,
                params.starCoverage, params.starDensity, params.sphereSize, params.noiseScale * 1.6f);

        scene = new FrameBuffer(Pixmap.Format.RGBA8888, FBW, FBH, false);
        out = new FrameBuffer(Pixmap.Format.RGBA8888, FBW, FBH, false);
        bloom = new BloomPipeline(FBW, FBH);
        ready = true;
    }

    private void bakeNoise() {
        noise = new NebulaNoise(params.seed, params.bakeRes);
        if (noiseTex == 0) {
            noiseTex = Gdx.gl.glGenTexture();
        }
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, noiseTex);
        Gdx.gl.glPixelStorei(GL20.GL_UNPACK_ALIGNMENT, 1);
        noise.rgb.rewind();
        Gdx.gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL20.GL_RGB, noise.atlas, noise.atlas, 0,
                GL20.GL_RGB, GL20.GL_UNSIGNED_BYTE, noise.rgb);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_LINEAR);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_LINEAR);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
    }

    /** 相机只受手动控制（拖拽/滚轮/存档），不随时间环绕。 */
    private void updateCamera() {
        float yawR = yawDeg * MathUtils.degreesToRadians;
        float pitchR = pitchDeg * MathUtils.degreesToRadians;
        float dist = distance;
        float cx = dist * MathUtils.cos(pitchR) * MathUtils.cos(yawR);
        float cy = dist * MathUtils.cos(pitchR) * MathUtils.sin(yawR);
        float cz = dist * MathUtils.sin(pitchR);
        cam.position.set(cx, cy, cz);
        cam.direction.set(-cx, -cy, -cz).nor();
        cam.up.set(0f, 0f, 1f);
        cam.fieldOfView = params.fov;
        cam.viewportWidth = FBW;
        cam.viewportHeight = FBH;
        cam.update();
        camRight.set(cam.direction).crs(cam.up).nor();
        camUp.set(camRight).crs(cam.direction).nor();
    }

    public Params params() {
        return params;
    }

    public void orbit(double dx, double dy) {
        yawDeg -= (float) dx * 0.35f;
        pitchDeg = MathUtils.clamp(pitchDeg + (float) dy * 0.25f, -80f, 80f);
    }

    public void zoom(double notches) {
        distance = MathUtils.clamp((float) (distance * Math.pow(1.0015, -notches * 40.0)), 0.45f, 3.5f);
    }

    public double[] camera() {
        return new double[]{yawDeg, pitchDeg, distance};
    }

    public void setCamera(double yaw, double pitch, double dist) {
        yawDeg = (float) yaw;
        pitchDeg = (float) Math.max(-80, Math.min(80, pitch));
        distance = (float) Math.max(0.45, Math.min(3.5, dist));
    }

    /** 渲染一帧到 320×180 的 ARGB（非预乘、y 向下），失败时返回 null。 */
    public int[] render(double time) {
        ensureGl();
        updateCamera();

        scene.begin();
        Gdx.gl.glClearColor(0.004f, 0.005f, 0.009f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE1);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, noiseTex);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);

        volume.bind();
        ui("u_noise", 1);
        uf("u_noiseCfg", noise.n, noise.tile, noise.grid);
        uf("u_colThin", Params.PALETTE_THIN[params.palette][0], Params.PALETTE_THIN[params.palette][1],
                Params.PALETTE_THIN[params.palette][2]);
        uf("u_colMid", Params.PALETTE_MID[params.palette][0], Params.PALETTE_MID[params.palette][1],
                Params.PALETTE_MID[params.palette][2]);
        uf("u_colWarm", Params.PALETTE_WARM[params.palette][0], Params.PALETTE_WARM[params.palette][1],
                Params.PALETTE_WARM[params.palette][2]);
        uv3("u_warmDir", warmDir);
        uf("u_starColor", params.starColor[0], params.starColor[1], params.starColor[2]);
        uf("u_starEnergy", params.starEnergy);
        uf("u_multi", params.multiScatter);
        uf("u_dbg", 0f);
        uf("u_shadow", params.shadowPower);
        ui("u_darkMode", params.darkMode);
        uf("u_darkAmount", params.darkAmount);
        uf("u_darkSep", params.darkSep);
        uf("u_darkAlpha", params.darkAlpha);
        uf("u_res", (float) FBW, (float) FBH);
        uf("u_time", (float) time);
        uv3("u_camPos", cam.position);
        uv3("u_camRight", camRight);
        uv3("u_camUp", camUp);
        uv3("u_camFwd", cam.direction);
        uf("u_tanHalfFov", MathUtils.tanDeg(params.fov * 0.5f));
        uf("u_aspect", FBW / (float) FBH);
        uf("u_seed", (float) (params.seed % 10000L));
        uf("u_noiseScale", params.noiseScale);
        uf("u_octaves", params.noiseDetail);
        uf("u_roughness", params.noiseRoughness);
        uf("u_distortion", params.distortion);
        uf("u_dustDensity", params.dustDensity);
        uf("u_gasDensity", params.gasDensity);
        uf("u_dustThreshold", params.dustThreshold);
        uf("u_gasInner", params.gasInner);
        uf("u_gasOuter", params.gasOuter);
        uf("u_sphereSize", params.sphereSize);
        uv3("u_lightDir", lightDir);
        uf("u_lightColor", params.lightR, params.lightG, params.lightB);
        uf("u_lightEnergy", params.lightEnergy);
        uf("u_coreEnergy", params.coreEnergy);
        uf("u_emission", params.emission);
        uf("u_gasHue", params.gasHueShift);
        ui("u_steps", params.steps);
        uf("u_exposure", params.exposure);
        uf("u_hsv", params.hue + params.hueDeg / 360f, params.saturation, params.value);
        // 体积光是预乘 alpha-over：既能加亮也能遮挡后面的星点
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        quad.draw(volume);

        if (params.starsEnabled && stars != null) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
            starShader.bind();
            stars.render(starShader, cam.combined, camRight, camUp);
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
        scene.end();

        // 合成必须写进自己的 FBO 再读回：直接往默认帧缓冲画会写坏窗口左下角，
        // 而且真机窗口尺寸不是 320×180，读回来的也就不是这张图。
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        out.begin();
        Gdx.gl.glViewport(0, 0, FBW, FBH);
        bloom.render(scene, params.bloomThreshold, params.bloomStrength, params.bloomEnabled);

        Pixmap src = Pixmap.createFromFrameBuffer(0, 0, FBW, FBH);
        out.end();
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
        quad.dispose();
        volume.dispose();
        starShader.dispose();
        bloom.dispose();
        stars.dispose();
        scene.dispose();
        out.dispose();
        if (noiseTex != 0) {
            Gdx.gl.glDeleteTexture(noiseTex);
            noiseTex = 0;
        }
    }

    private void uf(String name, float... v) {
        int loc = volume.getUniformLocation(name);
        if (loc < 0) {
            return;
        }
        if (v.length == 1) {
            volume.setUniformf(loc, v[0]);
        } else if (v.length == 2) {
            volume.setUniformf(loc, v[0], v[1]);
        } else {
            volume.setUniformf(loc, v[0], v[1], v[2]);
        }
    }

    private void uv3(String name, Vector3 v) {
        uf(name, v.x, v.y, v.z);
    }

    private void ui(String name, int v) {
        int loc = volume.getUniformLocation(name);
        if (loc >= 0) {
            volume.setUniformi(loc, v);
        }
    }
}
