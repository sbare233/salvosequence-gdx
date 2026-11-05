package com.hysx.salvosequence.gleba;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.gleba.gen.PlanetData;
import com.hysx.salvosequence.gleba.gen.PlanetGenerator;
import com.hysx.salvosequence.gleba.gen.PlanetParams;
import com.hysx.salvosequence.gleba.render.Atmosphere;
import com.hysx.salvosequence.gleba.render.PlanetMesh;
import com.hysx.salvosequence.ui.Language;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Random;

/**
 * Gleba 3D 行星渲染器（自含、可复用）：生成线程 + 地形位移网格 + 瑞利散射大气 +
 * 轨道相机 + 全方位角太阳。GlebaScreen（独立观看）与背景工坊（拜耳实时联动）共用。
 *
 * <p>用法：{@link #ensureGl()} 惰性建 GL 资源 → 每帧 {@link #update(double)} →
 * {@link #renderInto(PixelArt)} 把 3D 视图按最近邻 2 倍贴进设计像素缓冲 →
 * {@link #dispose()} 释放。控制全部走方法（按钮调用），无键位。</p>
 */
public final class GlebaPlanet {

    /** 3D 渲染分辨率（设计缓冲的 1/2，最近邻 2 倍贴回）。 */
    public static final int FBW = 320;
    public static final int FBH = 180;

    /** 球体大小档位（每面边长）：24 ≈ 0.2s，48 ≈ 1.7s，96 ≈ 2.7s 生成。 */
    public static final int[] FACE_RES = {24, 48, 96};

    /** 行星着色（晨昏线/海面高光/菲涅尔），照搬 PlanetApp。 */
    private static final String VERT =
            "attribute vec3 a_position;\n" +
            "attribute vec3 a_normal;\n" +
            "attribute vec4 a_color;\n" +
            "uniform mat4 u_projTrans;\n" +
            "varying vec3 v_normal;\n" +
            "varying vec4 v_color;\n" +
            "varying vec3 v_pos;\n" +
            "void main() {\n" +
            "  v_normal = a_normal;\n" +
            "  v_color = a_color;\n" +
            "  v_pos = a_position;\n" +
            "  gl_Position = u_projTrans * vec4(a_position, 1.0);\n" +
            "}\n";

    private static final String FRAG =
            "uniform float u_dbg;\n" +
            "varying vec3 v_normal;\n" +
            "varying vec4 v_color;\n" +
            "varying vec3 v_pos;\n" +
            "uniform vec3 u_lightDir;\n" +
            "uniform vec3 u_cameraPos;\n" +
            "void main() {\n" +
            "  vec3 n = normalize(v_pos);\n" +
            "  vec3 L = normalize(u_lightDir);\n" +
            "  vec3 viewDir = normalize(u_cameraPos - v_pos);\n" +
            "  vec3 base = v_color.rgb * 0.90;\n" +
            "  float water = clamp((base.b - base.r) * 3.5, 0.0, 1.0);\n" +
            "  float ndl = dot(n, L);\n" +
            "  float lit = smoothstep(-0.10, 0.34, ndl);\n" +
            "  vec3 day = base * (0.70 + 0.58 * max(ndl, 0.0));\n" +
            "  vec3 night = base * 0.10 + vec3(0.010, 0.016, 0.034);\n" +
            "  vec3 col = mix(night, day, lit);\n" +
            "  vec3 halfV = normalize(L + viewDir);\n" +
            "  float spec = pow(max(dot(n, halfV), 0.0), 48.0);\n" +
            "  col += vec3(0.85, 0.93, 1.0) * spec * water * lit * 0.50;\n" +
            "  float fres = pow(1.0 - max(dot(n, viewDir), 0.0), 3.0);\n" +
            "  col += vec3(0.18, 0.38, 0.72) * fres * water * lit * 0.22;\n" +
            "  gl_FragColor = vec4(col, 1.0);\n" +
            "}\n";

    public final PlanetParams params = new PlanetParams();

    /** 黑洞天空（GargantuaSky，远场 raymarch），画在行星之前——光照互动的第二光源。 */
    public final com.hysx.salvosequence.celestial.GargantuaSky sky =
            new com.hysx.salvosequence.celestial.GargantuaSky();
    /** 吸积盘亮度（作用于黑洞天空本身）。 */
    private float skyBrightness = 1.0f;
    /** 吸积盘对行星的橙光照度（第二光源）。 */
    private float diskGlow = 0.45f;

    private ShaderProgram shader;
    private Atmosphere atmo;
    private FrameBuffer fbo;
    private PerspectiveCamera cam;
    private PlanetMesh.Meshes mesh;

    /** 太阳方向：方位角 + 高度角（全方位角）。默认对准 PlanetApp 原版光照。 */
    private float sunAzimuthDeg = 123f;
    private float sunElevDeg = 17f;
    private Vector3 sunDir = computeSun();

    private float yaw = 0.7f, pitch = 0.32f, dist = 3.3f;
    private float orbitLastX = -1, orbitLastY = -1;

    private volatile boolean generating;
    private PlanetData pending;
    private long pendingMs = -1;
    private int faceResIdx;
    private long genMs;
    private int tiles;

    private double time;

    // ------------------------------------------------------------ 帧驱动

    public void update(double dt) {
        time += dt;
        yaw += dt * 0.12f;   // 缓慢自转
        if (pending != null) {
            PlanetData d = pending;
            pending = null;
            generating = false;
            genMs = pendingMs;
            tiles = d.n;
            lastLand = d.landCount();
            if (mesh != null) {
                mesh.dispose();
            }
            mesh = PlanetMesh.build(d, 0f, true);
        }
    }

    public void ensureGl() {
        if (shader != null) {
            return;
        }
        shader = new ShaderProgram(VERT, FRAG);
        if (!shader.isCompiled()) {
            System.out.println("[gleba] shader 编译失败: " + shader.getLog());
        }
        atmo = new Atmosphere();
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, FBW, FBH, true);
        cam = new PerspectiveCamera(50f, FBW, FBH);
        cam.near = 0.05f;
        cam.far = 400f;
        orbitLastX = -1;
    }

    /** 3D 渲染 → FBO → 读回像素（非预乘 ARGB，行距 FBW，已翻转成设计坐标）。 */
    public int[] renderFrame() {
        ensureGl();
        fbo.begin();
        Gdx.gl.glViewport(0, 0, FBW, FBH);
        Gdx.gl.glClearColor(0.015f, 0.02f, 0.035f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        // 远场天空（黑洞）：最先画、不写深度，网格天体随后正确遮挡它
        if (sky != null) {
            sky.render(-pitch, yaw, (float) time, FBW, FBH);
        }

        float cy = MathUtils.cos(pitch), sy = MathUtils.sin(pitch);
        cam.position.set(dist * cy * MathUtils.sin(yaw), dist * sy, dist * cy * MathUtils.cos(yaw));
        cam.up.set(0f, 1f, 0f);
        cam.lookAt(0f, 0f, 0f);
        cam.update();

        if (mesh != null) {
            // 明确关闭混合：上一帧大气留下的加法混合状态会把行星压暗
            Gdx.gl.glDisable(GL20.GL_BLEND);
            shader.bind();
            shader.setUniformMatrix("u_projTrans", cam.combined);
            shader.setUniformf("u_dbg", 0f);
            shader.setUniformf("u_lightDir", sunDir);
            shader.setUniformf("u_cameraPos", cam.position);
            shader.setUniformf("u_diskGlow", diskGlow);
            mesh.render(shader);
        }
        if (atmo != null) {
            atmo.render(cam.combined, cam.position, sunDir, 24f);
        }
        // FBO 仍绑定时读回（此处读到的就是刚渲染完的行星画面）
        Pixmap src = Pixmap.createFromFrameBuffer(0, 0, FBW, FBH);
        fbo.end();

        int[] argb = new int[FBW * FBH];
        ByteBuffer bb = src.getPixels();
        for (int y = 0; y < FBH; y++) {
            int srcY = FBH - 1 - y;   // GL 自下而上 → 设计坐标 y 向下
            for (int x = 0; x < FBW; x++) {
                int i = (srcY * FBW + x) * 4;
                int r = bb.get(i) & 0xFF;
                int g = bb.get(i + 1) & 0xFF;
                int b = bb.get(i + 2) & 0xFF;
                argb[y * FBW + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        src.dispose();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        return argb;
    }

    /** 3D 视图最近邻 2 倍贴满设计缓冲（不透明整幅，黑空背景 + 行星 + 大气）。 */
    public void renderInto(PixelArt art) {
        art.blitArgbTile(renderFrame(), FBW, FBH, 0, 0, 0, 0, FBW * 2, FBH * 2,
                true, false, false, 1.0);
    }

    public void dispose() {
        if (mesh != null) {
            mesh.dispose();
            mesh = null;
        }
        if (fbo != null) {
            fbo.dispose();
            fbo = null;
        }
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
        if (atmo != null) {
            atmo.dispose();
            atmo = null;
        }
        pending = null;
        generating = false;
    }

    // ------------------------------------------------------------ 控制（按钮调用）

    public void newSeed() {
        params.seed = new Random().nextLong();
        regen();
    }

    public void regen() {
        if (generating) {
            return;
        }
        generating = true;
        final PlanetParams snapshot = params.copy();
        Thread th = new Thread(() -> {
            try {
                PlanetGenerator.Result r = PlanetGenerator.generate(snapshot);
                pendingMs = r.stageMillis.getOrDefault("TOTAL", 0L);
                pending = r.data;
            } catch (Throwable e) {
                System.out.println("[gleba] 生成失败: " + e);
                generating = false;
            }
        }, "gleba-gen");
        th.setDaemon(true);
        th.start();
    }

    public void cycleFaceRes() {
        faceResIdx = (faceResIdx + 1) % FACE_RES.length;
        params.faceRes = FACE_RES[faceResIdx];
        regen();
    }

    public void adjustLand(float delta) {
        params.continentalFraction = Math.max(0.10f,
                Math.min(0.70f, params.continentalFraction + delta));
        regen();
    }

    public void adjustAzimuth(float delta) {
        sunAzimuthDeg = (sunAzimuthDeg + delta + 360f) % 360f;
        sunDir = computeSun();
    }

    public void adjustElevation(float delta) {
        sunElevDeg = Math.max(-80f, Math.min(80f, sunElevDeg + delta));
        sunDir = computeSun();
    }

    /** 指针拖拽 = 轨道旋转（yaw/pitch），口径同 PlanetApp 的 InputAdapter。 */
    public void orbitDrag(double mx, double my) {
        if (orbitLastX < 0) {
            orbitLastX = (float) mx;
            orbitLastY = (float) my;
            return;
        }
        yaw -= (mx - orbitLastX) * 0.012f;
        pitch = MathUtils.clamp(pitch + (float) (my - orbitLastY) * 0.012f, -1.45f, 1.45f);
        orbitLastX = (float) mx;
        orbitLastY = (float) my;
    }

    /** 相对拖拽（像素）：背景工坊里指针拖动直接调它，不依赖上一帧的绝对坐标。 */
    public void orbitBy(double dx, double dy) {
        yaw -= (float) dx * 0.012f;
        pitch = MathUtils.clamp(pitch + (float) dy * 0.012f, -1.45f, 1.45f);
    }

    /** 滚轮缩放（格数，正数放大）。 */
    public void zoomBy(double notches) {
        dist = MathUtils.clamp((float) (dist * Math.pow(1.0015, -notches * 40.0)), 1.25f, 12f);
    }

    public double[] camera() {
        return new double[]{Math.toDegrees(yaw), Math.toDegrees(pitch), dist};
    }

    public void setCamera(double yawDeg, double pitchDeg, double distance) {
        yaw = (float) Math.toRadians(yawDeg);
        pitch = MathUtils.clamp((float) Math.toRadians(pitchDeg), -1.45f, 1.45f);
        dist = (float) Math.max(1.25, Math.min(12, distance));
    }

    public void orbitReset() {
        orbitLastX = -1;
        orbitLastY = -1;
    }

    /** 滚轮缩放（delta 口径同屏幕滚轮：一格 ±40）。 */
    public void scrollZoom(double delta) {
        dist = MathUtils.clamp((float) (dist * (1.0 + (-delta / 40.0) * 0.12)), 1.25f, 12f);
    }

    // ------------------------------------------------------------ 读数

    public boolean isGenerating() {
        return generating;
    }

    public String status() {
        return generating
                ? Language.t("生成中…", "generating…")
                : String.format(Locale.ROOT, "%d ms · %d 格 · 陆地 %.1f%%",
                        genMs, tiles, tiles == 0 ? 0 : 100.0 * lastLand / tiles);
    }

    public int faceRes() {
        return params.faceRes;
    }

    public float sunAzimuthDeg() {
        return sunAzimuthDeg;
    }

    public float sunElevDeg() {
        return sunElevDeg;
    }

    public float skyBrightness() {
        return skyBrightness;
    }

    public void adjustSkyBrightness(float delta) {
        skyBrightness = Math.max(0.2f, Math.min(2.0f, skyBrightness + delta));
        sky.setBrightness(skyBrightness);
    }

    public float diskGlow() {
        return diskGlow;
    }

    public void adjustDiskGlow(float delta) {
        diskGlow = Math.max(0.0f, Math.min(1.0f, diskGlow + delta));
    }

    private int lastLand;

    private Vector3 computeSun() {
        double az = Math.toRadians(sunAzimuthDeg);
        double el = Math.toRadians(sunElevDeg);
        return new Vector3(
                (float) (Math.cos(el) * Math.cos(az)),
                (float) Math.sin(el),
                (float) (Math.cos(el) * Math.sin(az))).nor();
    }
}
