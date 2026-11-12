package com.hysx.salvosequence.gleba;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.hysx.salvosequence.core.Assets;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.gleba.gen.PlanetData;
import com.hysx.salvosequence.gleba.gen.PlanetGenerator;
import com.hysx.salvosequence.gleba.gen.PlanetParams;
import com.hysx.salvosequence.gleba.render.Atmosphere;
import com.hysx.salvosequence.gleba.render.PlanetMesh;
import com.hysx.salvosequence.ui.Language;

import java.io.InputStream;
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
    public static final int[] FACE_RES = {48, 96, 148, 256};

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
    /**
     * 要不要在这颗行星背后画黑洞天空。行星场景默认关掉——黑洞已经是独立的背景场景，
     * 两者挤在同一张图里会分不清看的是哪个天体。
     */
    public boolean showSky = true;

    private ShaderProgram shader;
    private Atmosphere atmo;
    private FrameBuffer fbo;
    private PerspectiveCamera cam;
    private com.hysx.salvosequence.gleba.render.HexMesh.Cells mesh;
    /** 气态行星的球面（非气态时为 null）。 */
    private com.hysx.salvosequence.gleba.render.GasGiant gasMesh;

    /** 太阳方向：方位角 + 高度角（全方位角）。默认对准 PlanetApp 原版光照。 */
    private float sunAzimuthDeg = 123f;
    private float sunElevDeg = 17f;
    private Vector3 sunDir = computeSun();

    /** 机位角度上限（弧度，60°）：轨道面要包含当前机位，俯仰再高轨道面就贴到极点上了。 */
    private static final float MAX_PITCH = 1.05f;

    private float yaw = 0.7f, pitch = 0.32f, dist = 3.6f;
    /** 轨道角速度（弧度/秒）：镜头沿一条倾斜圆轨道绕行星走，行星本体一动不动。 */
    private float orbitSpeed = 0.01f;
    /** 当前轨道相位（弧度）：0 = 预设机位。 */
    private float orbitPhase;
    /** 轨道基：相位 0 的机位方向、沿轨切向、轨道面法线（单位向量）。 */
    private final Vector3 orbitDir = new Vector3(0.643f, 0.315f, 0.7f).nor();
    private final Vector3 orbitTangent = new Vector3(1f, 0f, 0f);
    private final Vector3 orbitNormal = new Vector3(0f, 1f, 0f);
    /** 这一帧的相机方向（行星中心 → 相机，单位向量）：轨道相位在这里换算成角度。 */
    private final Vector3 viewDir = new Vector3(0.643f, 0.315f, 0.7f).nor();
    /** 连续方位角/俯仰（度）：给存档和星空视差用，避免 ±180° 回绕处背景跳一下。 */
    private float viewYawDeg = 40f;
    private float viewPitchDeg = 18f;
    private float orbitLastX = -1, orbitLastY = -1;

    public GlebaPlanet() {
        rebuildOrbit();
    }

    private volatile boolean generating;
    private volatile boolean gasDirty;
    private PlanetData pending;
    /** 后台线程算好的六边形几何，等渲染线程做一次上传。 */
    private com.hysx.salvosequence.gleba.render.HexMesh.Geo pendingGeo;
    private long pendingMs = -1;
    private int faceResIdx;
    private long genMs;
    private int tiles;

    private double time;

    // ------------------------------------------------------------ 星环

    /** 星环（移植自 gleba-libgdx-mvp）：内径、外径、浓度、环面倾角。 */
    public boolean rings = true;
    public float ringInner = 1.34f;
    public float ringOuter = 2.20f;
    public float ringOpacity = 0.46f;
    public float ringTilt = 0f;
    /** 环系剖面：0 土星式主环 / 1 细亮丝 / 2 双环 / 3 宽尘环 / 4 断续弧环。 */
    public int ringStyle = 0;
    /**
     * 环系种子：剖面骨架不变，带的位置/宽窄/亮度/细纹相位都随它变。
     * 每场战斗掷一次，0 表示用该剖面最标准的那条（主题默认）。
     */
    public float ringSeed = 0f;
    /** 细丝/弧环的条数（2..4），只有 1 号剖面用得到。 */
    public int ringBands = 3;

    private com.hysx.salvosequence.gleba.render.Rings ringMesh;
    private com.hysx.salvosequence.gleba.render.Clouds cloudMesh;

    /** 云层：实拍卫星云图的球壳（等距圆柱，加载一次，不再按星球种子重算）。 */
    public boolean clouds = !Boolean.getBoolean("salvo.noclouds");
    public float cloudAmount = 0.62f;
    public float cloudHue = 0f;
    public float cloudBright = 1f;
    public float cloudContrast = 1f;
    /** 大气厚度倍率（0 = 真空，不画大气；1 = 默认；1.4+ = 厚壳）。每颗星球随机。 */
    public float atmoThickness = 1f;

    /** 星球类型：大陆占比 / 温度 / 降水 / 配色 / 云量 / 星环都从预设来。 */
    public com.hysx.salvosequence.gleba.gen.PlanetTheme theme =
            com.hysx.salvosequence.gleba.gen.PlanetTheme.EARTH;

    // ------------------------------------------------------------ 帧驱动

    public void update(double dt) {
        time += dt;
        // 轨道相机：行星和星环在世界坐标里完全静止，动的只有镜头。
        // 镜头沿一条过行星中心、带倾角的圆轨道走（见 rebuildOrbit），俯仰是轨道位置的
        // 函数，不再是一条独立的摇摆曲线——以前那种"方位角一直加 + 俯仰按正弦摆"的
        // 两条曲线周期不同，走出来的其实是条拧着的螺线，看着像星球/星环在原地晃。
        // 截图/对比时用 -Dsalvo.nospin=true 钉死相位。
        if (!Boolean.getBoolean("salvo.nospin")) {
            orbitPhase += dt * orbitSpeed;
        }
        syncView();
        if (gasDirty) {
            gasDirty = false;
            if (mesh != null) {
                mesh.dispose();
                mesh = null;
            }
            if (gasMesh != null) {
                gasMesh.dispose();
                gasMesh = null;
            }
            gasMesh = com.hysx.salvosequence.gleba.render.GasGiant.build(params.theme);
            tiles = 0;
            lastLand = 0;
            genMs = 0;
        }
        if (pending != null) {
            PlanetData d = pending;
            pending = null;
            generating = false;
            genMs = pendingMs;
            tiles = d.n;
            lastLand = d.landCount();
            if (mesh != null) {
                mesh.dispose();
                mesh = null;
            }
            if (gasMesh != null) {
                gasMesh.dispose();
                gasMesh = null;
            }
            if (d.theme != null && d.theme.gas) {
                // 气态行星不建地形网格：换成一张高密度经纬球，色带由 GasBands 逐顶点算
                gasMesh = com.hysx.salvosequence.gleba.render.GasGiant.build(d.theme);
            } else {
                // 六边形球面网格（Gleba 那条路）：生成按 faceRes 最小档，渲染按 meshFreq 另建，
                // 所以大陆是大块像素、球面却是二十面体细分的六边形拼面（-Dsalvo.hex=N 覆盖）
                if (pendingGeo != null) {
                    long t0 = System.nanoTime();
                    mesh = com.hysx.salvosequence.gleba.render.HexMesh.upload(pendingGeo);
                    pendingGeo = null;
                    if (Boolean.getBoolean("salvo.profile")) {
                        System.out.printf("[gleba] 六边形上传 %d ms%n", (System.nanoTime() - t0) / 1_000_000);
                    }
                } else {
                    int freq = Integer.getInteger("salvo.hex", params.meshFreq);
                    mesh = com.hysx.salvosequence.gleba.render.HexMesh.build(d, 0f, true, freq);
                }
            }
        }
    }

    /**
     * 由当前机位反推一条<b>真实轨道</b>：一个过行星中心、包含当前机位方向的大圆。
     *
     * <p>轨道面倾角取「当前俯仰 + 18°」（夹在 24°–60°）：起始机位恰好落在面上，预设
     * 构图不会被改变；绕一圈时俯仰自然从 +坡 摆到 -坡（真实圆轨道就是这样），
     * 期间稳稳掠过赤道面两次——星环由张开到侧看成一条线，再张开。</p>
     */
    private void rebuildOrbit() {
        orbitPhase = 0f;
        float cp = MathUtils.cos(pitch), sp = MathUtils.sin(pitch);
        orbitDir.set(cp * MathUtils.sin(yaw), sp, cp * MathUtils.cos(yaw)).nor();

        double phi0 = Math.asin(MathUtils.clamp(orbitDir.y, -0.99f, 0.99f));
        double incl = Math.max(0.42, Math.min(1.05, Math.abs(phi0) + 0.32));
        double cosPhi0 = Math.cos(phi0);
        double cosI = Math.cos(incl);
        // 轨道面法线 n = aN cosψ + b sinψ：n⊥orbitDir，且与 +Y 夹角 = 倾角
        double cosPsi = Math.min(1.0, cosI / cosPhi0);
        double sinPsi = Math.sqrt(Math.max(0.0, 1.0 - cosPsi * cosPsi));
        Vector3 aN = new Vector3(0f, 1f, 0f).mulAdd(orbitDir, -orbitDir.y);
        if (aN.len2() < 1e-6f) {
            aN.set(1f, 0f, 0f);
        }
        aN.nor();
        Vector3 b = new Vector3(orbitDir).crs(aN).nor();
        orbitNormal.set(aN).scl((float) cosPsi).mulAdd(b, (float) sinPsi).nor();
        // 相位前进方向取"上升段"：镜头先从当前高度往上走，不是先栽下去
        orbitTangent.set(orbitNormal).crs(orbitDir).nor();
        if (orbitTangent.y < 0f) {
            orbitNormal.scl(-1f);
            orbitTangent.set(orbitNormal).crs(orbitDir).nor();
        }
        viewYawDeg = (float) Math.toDegrees(yaw);
        syncView();
    }

    /** 把轨道相位换算成这一帧的相机方向（并维护连续方位角，供视差/存档使用）。 */
    private void syncView() {
        float cp = MathUtils.cos(orbitPhase), sp = MathUtils.sin(orbitPhase);
        viewDir.set(orbitDir).scl(cp).mulAdd(orbitTangent, sp).nor();
        viewPitchDeg = (float) Math.toDegrees(Math.asin(MathUtils.clamp(viewDir.y, -1f, 1f)));
        float az = (float) Math.toDegrees(Math.atan2(viewDir.x, viewDir.z));
        float d = az - (viewYawDeg % 360f);
        if (d > 180f) {
            d -= 360f;
        } else if (d < -180f) {
            d += 360f;
        }
        viewYawDeg += d;
    }

    /** 3D 星空要的相机视场（度）。 */
    public float fovDeg() {
        return cam == null ? 50f : cam.fieldOfView;
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
        ringMesh = new com.hysx.salvosequence.gleba.render.Rings();
        cloudMesh = new com.hysx.salvosequence.gleba.render.Clouds();
        loadCloudCovers();
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, FBW, FBH, true);
        cam = new PerspectiveCamera(50f, FBW, FBH);
        cam.near = 0.05f;
        cam.far = 400f;
        orbitLastX = -1;
    }

    /** 从 classpath / Android assets 读 3 张实拍云图，只在 GL 线程上传一次。 */
    private void loadCloudCovers() {
        String[] paths = {
                "textures/earth_clouds.jpg",
                "textures/earth_clouds_2f.jpg",
                "textures/earth_clouds_3f.jpg"
        };
        for (String path : paths) {
            try (InputStream in = Assets.open(path)) {
                byte[] data = in.readAllBytes();
                if (data.length == 0) {
                    throw new IllegalStateException("资源为空");
                }
                Pixmap map = new Pixmap(data, 0, data.length);
                cloudMesh.addCover(map);
                map.dispose();
                if (Boolean.getBoolean("salvo.profile")) {
                    System.out.println("[gleba] 云图 " + data.length + " bytes ← "
                            + Assets.source(path));
                }
            } catch (Exception e) {
                System.out.println("[gleba] 云图加载失败: " + e
                        + "（" + Assets.source(path) + "）");
            }
        }
        int forced = Integer.getInteger("salvo.cloudvariant", -1);
        cloudMesh.setVariant(forced >= 0 ? forced
                : (int) Math.floorMod(params.seed, Math.max(1, paths.length)));
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
        if (sky != null && showSky) {
            sky.render(-(float) Math.toRadians(viewPitchDeg),
                    (float) Math.toRadians(viewYawDeg), (float) time, FBW, FBH);
        }

        // 相机沿倾斜圆轨道走：位置就是当前相位对应的方向 × 距离，up 固定世界 +Y，
        // 所以画面不滚转——行星的轴始终是竖直的，变化只有"从哪条轨道位置看过去"
        cam.position.set(viewDir).scl(dist);
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
        if (gasMesh != null) {
            // 气态行星：同一套光照着色器，只是几何换成经纬球
            Gdx.gl.glDisable(GL20.GL_BLEND);
            shader.bind();
            shader.setUniformMatrix("u_projTrans", cam.combined);
            shader.setUniformf("u_dbg", 0f);
            shader.setUniformf("u_lightDir", sunDir);
            shader.setUniformf("u_cameraPos", cam.position);
            shader.setUniformf("u_diskGlow", 0f);
            gasMesh.render(shader);
        }
        // 云层：贴在地表之上、大气之下的一层球壳，半透明混合
        if (cloudMesh != null && clouds && cloudAmount > 0.01f) {
            cloudMesh.setGrade(theme.cloudHue + cloudHue, cloudBright, cloudContrast);
            cloudMesh.renderShadow(cam.combined, cam.position, sunDir, (float) time, cloudAmount);
            cloudMesh.render(cam.combined, cam.position, sunDir, (float) time, cloudAmount);
        }
        if (atmo != null) {
            // 大气颜色跟着星球类型走：紫色星球连晨昏线都是紫的
            String ov = System.getProperty("salvo.atmoTint");
            if (ov != null) {
                String[] p = ov.split(",");
                atmo.setTint(Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2]));
            } else {
                atmo.setTint(theme.atmoR, theme.atmoG, theme.atmoB);
            }
            // 厚度每颗星球随机：有的几乎没有大气（0 时整层不画），有的裹着厚云
            atmo.thickness = atmoThickness;
            atmo.render(cam.combined, cam.position, sunDir, 24f);
        }
        // 星环：半透明，深度测试开、深度写入关；行星的深度已经写好了，被挡住的那半自然消失
        if (ringMesh != null && rings) {
            ringMesh.render(cam.combined, cam.position, sunDir,
                    ringInner, ringOuter, ringOpacity, ringTilt, true, ringStyle,
                    ringSeed, ringBands);
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
        if (gasMesh != null) {
            gasMesh.dispose();
            gasMesh = null;
        }
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
        if (ringMesh != null) {
            ringMesh.dispose();
            ringMesh = null;
        }
        if (cloudMesh != null) {
            cloudMesh.dispose();
            cloudMesh = null;
        }
        pending = null;
        generating = false;
    }

    // ------------------------------------------------------------ 控制（按钮调用）

    public void newSeed() {
        params.seed = new Random().nextLong();
        if (cloudMesh != null) {
            cloudMesh.setVariant((int) Math.floorMod(params.seed,
                    Math.max(1, cloudMesh.coverCount())));
        }
        regen();
    }

    /**
     * 切换星球类型：大陆占比进生成参数，云量与星环进观感参数，然后重新生成。
     *
     * <p>类型差异来自生成参数（海陆比、温度、降水），不是后期滤镜——
     * 所以同一颗星球换成荒漠型，海岸线和山脉都还在，只是植被变成了荒漠。</p>
     */
    public void setTheme(com.hysx.salvosequence.gleba.gen.PlanetTheme t) {
        if (t == null) {
            t = com.hysx.salvosequence.gleba.gen.PlanetTheme.EARTH;
        }
        theme = t;
        params.theme = t;
        params.continentalFraction = t.continentalFraction;
        cloudAmount = t.cloudAmount;
        cloudHue = t.cloudHue;
        rings = t.rings;
        applyThemeRings(t);
        regen();
    }

    /**
     * 每种星球的环都不一样——环是最好认的身份标识，全都一套土星环最省事也最假：
     * 冰封型是一圈又细又亮的冰丝（天王星 ε 环那种），甲烷海是一层暖褐色尘，
     * 木星型是「宽内环 + 一道细外环」，冰巨星是近乎竖着、还被撞断成几段的弧环。
     * 倾角一起给：环面贴着赤道还是立成极环，一眼就能看出是另一个天体。
     */
    private void applyThemeRings(com.hysx.salvosequence.gleba.gen.PlanetTheme t) {
        switch (t) {
            case ICE -> {
                ringInner = 1.48f;
                ringOuter = 2.62f;
                ringOpacity = 0.58f;
                ringTilt = 24f;
                ringStyle = 1;
            }
            case LIQUID_METHANE -> {
                ringInner = 1.40f;
                ringOuter = 2.34f;
                ringOpacity = 0.30f;
                ringTilt = -18f;
                ringStyle = 3;
            }
            case GAS_JOVIAN -> {
                ringInner = 1.52f;
                ringOuter = 2.06f;
                ringOpacity = 0.30f;
                ringTilt = 26f;
                ringStyle = 2;
            }
            case GAS_ICE -> {
                ringInner = 1.56f;
                ringOuter = 1.98f;
                ringOpacity = 0.36f;
                ringTilt = 68f;
                ringStyle = 4;
            }
            default -> {
                ringInner = 1.34f;
                ringOuter = 2.20f;
                ringOpacity = 0.46f;
                ringTilt = 0f;
                ringStyle = 0;
            }
        }
        if (t.gas) {
            // 气态行星的环更薄更暗：又宽又亮的环是岩质/冰质行星的观感
            ringOpacity *= 0.8f;
        }
        // 主题只给「最标准的那一条」；真正的随机化在开战时由 LiveBackdrops 掷
        ringSeed = 0f;
        ringBands = ringStyle == 1 ? 4 : 3;
    }

    /** 环系剖面：0 土星式主环 / 1 细亮丝 / 2 双环 / 3 宽尘环 / 4 断续弧环。 */
    public void setRingStyle(int style) {
        ringStyle = Math.max(0, Math.min(4, style));
    }

    /** 环面倾角（度）：0 贴赤道，±70 就是极环。 */
    public void setRingTilt(float deg) {
        ringTilt = Math.max(-75f, Math.min(75f, deg));
    }

    /** 环系随机种子：换一个种子就是同一种剖面骨架下的另一条环。 */
    public void setRingSeed(float seed) {
        ringSeed = seed;
    }

    /** 细丝条数（2..4）。 */
    public void setRingBands(int bands) {
        ringBands = Math.max(2, Math.min(4, bands));
    }

    /**
     * 每颗星球掷一次大气厚度：真实天体里"薄如无物"和"厚到发光"都常见，
     * 全都裹一层标准蓝边才最假。岩质/熔岩偏向真空，冰封/海洋偏厚，气态必然厚。
     */
    private static float rollAtmosphere(long seed, com.hysx.salvosequence.gleba.gen.PlanetTheme t) {
        String ov = System.getProperty("salvo.atmo");
        if (ov != null) {
            return Float.parseFloat(ov);
        }
        java.util.Random rng = new java.util.Random(seed * 0x9E3779B97F4A7C15L ^ 0x5D2F1A3BL);
        float roll = rng.nextFloat();
        float v;
        if (roll < 0.18f) {
            v = 0f;                                          // 真空：整层不画
        } else if (roll < 0.46f) {
            v = 0.30f + rng.nextFloat() * 0.30f;             // 稀薄
        } else if (roll < 0.86f) {
            v = 0.80f + rng.nextFloat() * 0.40f;             // 正常
        } else {
            v = 1.20f + rng.nextFloat() * 0.40f;             // 厚壳
        }
        if (t != null) {
            switch (t) {
                case ROCK -> v *= 0.35f;
                case LAVA -> v *= 0.55f;
                case ICE, OCEAN, LIQUID_OCEAN -> v *= 1.15f;
                case GAS_JOVIAN, GAS_ICE -> v = Math.max(v, 0.85f);
                default -> { }
            }
        }
        return v;
    }

    public void regen() {
        if (generating) {
            return;
        }
        atmoThickness = rollAtmosphere(params.seed, params.theme);
        // 气态行星没有地形：不跑板块/侵蚀/气候，等下一帧直接建色带球（省掉一次完整生成）
        if (params.theme != null && params.theme.gas) {
            gasDirty = true;
            return;
        }
        generating = true;
        final PlanetParams snapshot = params.copy();
        final int freq = Integer.getInteger("salvo.hex", params.meshFreq);
        final boolean gas = params.theme != null && params.theme.gas;
        Thread th = new Thread(() -> {
            try {
                // 六边形几何在后台算完，渲染线程只做一次上传
                PlanetGenerator.Result r = PlanetGenerator.generate(snapshot);
                pendingMs = r.stageMillis.getOrDefault("TOTAL", 0L);
                if (!gas && r.data.theme != null && !r.data.theme.gas) {
                    pendingGeo = com.hysx.salvosequence.gleba.render.HexMesh
                            .buildGeometry(r.data, 0f, true, freq);
                }
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
        // 以当前实际分辨率为准取档位，避免构造时索引与 conf 里的 faceRes 不同步、一按就跳档
        int idx = 0;
        for (int i = 0; i < FACE_RES.length; i++) {
            if (FACE_RES[i] == params.faceRes) {
                idx = i;
            }
        }
        idx = (idx + 1) % FACE_RES.length;
        faceResIdx = idx;
        params.faceRes = FACE_RES[idx];
        regen();
    }

    /** 当前档位索引（0 = 24，1 = 48，2 = 96），供界面显示。 */
    public int faceResIndex() {
        return faceResIdx;
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

    /** 指针拖拽 = 重新给轨道定锚点（yaw/pitch 是轨道起点，不是逐帧累加的角度）。 */
    public void orbitDrag(double mx, double my) {
        if (orbitLastX < 0) {
            orbitLastX = (float) mx;
            orbitLastY = (float) my;
            return;
        }
        yaw -= (mx - orbitLastX) * 0.012f;
        pitch = MathUtils.clamp(pitch + (float) (my - orbitLastY) * 0.012f, -MAX_PITCH, MAX_PITCH);
        orbitLastX = (float) mx;
        orbitLastY = (float) my;
        rebuildOrbit();
    }

    /** 相对拖拽（像素）：背景工坊里指针拖动直接调它，不依赖上一帧的绝对坐标。 */
    public void orbitBy(double dx, double dy) {
        yaw -= (float) dx * 0.012f;
        pitch = MathUtils.clamp(pitch + (float) dy * 0.012f, -MAX_PITCH, MAX_PITCH);
        rebuildOrbit();
    }

    /** 滚轮缩放（格数，正数放大）。 */
    public void zoomBy(double notches) {
        dist = MathUtils.clamp((float) (dist * Math.pow(1.0015, -notches * 40.0)), 1.25f, 12f);
    }

    public double[] camera() {
        syncView();
        return new double[]{viewYawDeg, viewPitchDeg, dist};
    }

    /**
     * 行星在画面上的圆盘（{cx, cy, r}，单位 FBW×FBH 像素）。
     * 背景的闪烁星要在这一块外面才画，否则星星会浮在行星夜面上。
     */
    public float[] screenDisc() {
        if (cam == null) {
            return null;
        }
        // 相机始终看向原点，行星就永远在画面正中；半径按「竖直半高 / tan(fov/2) / √(d²-1)」算。
        // （投影矩阵直接 project() 会混进相机当前的视口尺寸——实机是窗口大小，不是 320×180，
        //   之前遮罩套不上就是这个原因。）
        double f = (FBH * 0.5) / Math.tan(Math.toRadians(cam.fieldOfView * 0.5));
        double r = f / Math.sqrt(Math.max(0.01, (double) dist * dist - 1.0));
        return new float[]{FBW * 0.5f, FBH * 0.5f, (float) r};
    }

    public void setCamera(double yawDeg, double pitchDeg, double distance) {
        yaw = (float) Math.toRadians(yawDeg);
        pitch = MathUtils.clamp((float) Math.toRadians(pitchDeg), -MAX_PITCH, MAX_PITCH);
        dist = (float) Math.max(1.25, Math.min(12, distance));
        rebuildOrbit();
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

    /** 直接指定太阳方位/高度角（度）。行星单独当背景时用它把朝阳面摆给镜头。 */
    public void setSun(float azimuthDeg, float elevationDeg) {
        sunAzimuthDeg = ((azimuthDeg % 360f) + 360f) % 360f;
        sunElevDeg = Math.max(-90f, Math.min(90f, elevationDeg));
        sunDir = computeSun();
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
