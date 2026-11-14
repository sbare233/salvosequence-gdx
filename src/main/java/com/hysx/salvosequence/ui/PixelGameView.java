package com.hysx.salvosequence.ui;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelScale;
import com.hysx.salvosequence.core.PixelText;
import com.hysx.salvosequence.screen.ScreenContext;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * 应用视图（libGDX 版）：一张 640x360 的设计像素缓冲 + 每帧整幅上传成纹理。
 *
 * <p>它只做三件事：</p>
 * <ol>
 *   <li>拥有 {@link CanvasBuffer}（640x360 设计像素）与整屏唯一的一张 {@link Texture}；</li>
 *   <li>每帧让 {@link ScreenManager} 把当前界面画进缓冲，然后<b>一次性</b>上传上屏
 *       （Nearest 采样 + 整数倍缩放，等价原 JavaFX {@code setSmooth(false)} + {@code Scale}）；</li>
 *   <li>把键鼠事件换算成设计坐标/按键名转发给管理器。</li>
 * </ol>
 *
 * <p>界面切换、热区、扫描线、调试读数全部在 {@link ScreenManager}，这里不掺业务。
 * 最终上屏只走一个 CRT pass（扫描线 + 孔径格栅），不再有模糊/辉光后处理链。</p>
 */
public final class PixelGameView extends ApplicationAdapter {

    /** 构建标记：视觉口径改动时更新，用于排查"跑的是不是最新构建"。 */
    public static final String BUILD_TAG = "2026-11-13-crt-axis-mix-v47";

    /** 设计分辨率。 */
    public static final int DESIGN_WIDTH = 640;
    public static final int DESIGN_HEIGHT = 360;

    private final CanvasBuffer buffer = new CanvasBuffer(DESIGN_WIDTH, DESIGN_HEIGHT);
    private final ScreenManager screens = new ScreenManager();
    /**
     * 触摸手势：安卓单指点击 / 长按悬浮 / 双指缩放。桌面鼠标仍走原来的直通路径
     * （见 {@code ViewInput}），所以桌面上按住不动不会变成"悬浮"、也不会吞点击。
     */
    private TouchGestures gestures;
    private final PixelScale pixelScale = new PixelScale();

    private Texture frameTexture;
    private Pixmap framePixmap;
    private final ByteBuffer rgbaBytes = ByteBuffer.allocateDirect(DESIGN_WIDTH * DESIGN_HEIGHT * 4);

    private com.badlogic.gdx.graphics.g2d.SpriteBatch batch;
    /** 上屏 CRT shader（扫描线 + 荫栅，一次采样完成）。 */
    private ShaderProgram crtShader;

    /** 屏幕空间投影（0..w, 0..h，GL 原点左下）：CRT 上屏与输入换算共用。 */
    private final com.badlogic.gdx.math.Matrix4 screenMatrix =
            new com.badlogic.gdx.math.Matrix4();
    private long profTick, profFrame, profUpload, profUpdate, profDraw;
    /** 当前生效的像素化开关（F7 切换时换纹理过滤）。 */
    private boolean appliedPixelated = true;
    private long frameCount;
    private int fps;
    private int framesInSecond;
    private double fpsSecond;
    /** 冒烟自检：设置了 -Dsalvosequence.smoke.frames 时，画够帧数自动退出。 */
    private final int smokeFrames = Integer.getInteger("salvosequence.smoke.frames", -1);
    private boolean smokeShotPending;
    /** 冒烟模式的计时起点：用来打印平均帧时间（关掉垂直同步时可比性能改动）。 */
    private long smokeStartNanos;
    /** 性能剖析：-Dsalvosequence.profile=true 时按段累加耗时（冒烟结束打印）。 */
    private final boolean profiling = Boolean.getBoolean("salvosequence.profile");
    /** 桌面端直接按 GL_BGRA 上传像素（安卓 GLES2 不保证支持，退回逐字节转换）。 */
    private java.nio.IntBuffer intPixels;
    private boolean useBgraUpload;

    /** 每帧回调（离屏核对工具用）：帧推进完成（含纹理上传）后调用。 */
    public java.util.function.BiConsumer<Long, PixelGameView> frameListener;

    @Override
    public void create() {
        // Android 在这里接管 Gdx.files（classpath 资源改读 assets），必须先于任何着色器加载
        com.hysx.salvosequence.core.Assets.runStartupHook();
        batch = new com.badlogic.gdx.graphics.g2d.SpriteBatch();
        // 触摸设备（安卓/iOS）才开长按悬浮：桌面鼠标按住不动不该被当成"悬浮"。
        gestures = new TouchGestures(screens, isTouchDevice());
        screens.setTouchHover(isTouchDevice());
        // 实时背景的调参存档：开游戏时套一次，没存过就保持代码默认值
        try {
            com.hysx.salvosequence.live.LiveSettings.load();
        } catch (RuntimeException ignored) {
            // 参数档坏了不该拦住启动
        }

        framePixmap = new Pixmap(DESIGN_WIDTH, DESIGN_HEIGHT, Pixmap.Format.RGBA8888);
        frameTexture = new Texture(framePixmap);
        frameTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        intPixels = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(DESIGN_WIDTH * DESIGN_HEIGHT);
        useBgraUpload = Gdx.app.getType() != com.badlogic.gdx.Application.ApplicationType.Android;
        buffer.art().attachSink(new FrameSink());

        crtShader = makeCrtShader();

        Gdx.input.setInputProcessor(new ViewInput());

        // 调试用：-Dsalvosequence.crt=OFF|SCANLINE|GRILLE|SCANLINE_GRILLE 直接选滤镜；
        // 旧属性 -Dsalvosequence.scanlines=true|false 仍兼容（true = 完整 CRT）。
        String forcedCrt = System.getProperty("salvosequence.crt");
        if (forcedCrt != null && !forcedCrt.trim().isEmpty()) {
            String wanted = forcedCrt.trim().toUpperCase(java.util.Locale.ROOT)
                    .replace('-', '_').replace('+', '_').replace(' ', '_');
            try {
                VideoSettings.setCrtFilter(VideoSettings.CrtFilter.valueOf(wanted));
            } catch (IllegalArgumentException unknown) {
                System.out.println("[crt] 未知模式 " + forcedCrt
                        + "，可用 OFF/SCANLINE/GRILLE/SCANLINE_GRILLE");
            }
        } else {
            String forcedScanlines = System.getProperty("salvosequence.scanlines");
            if (forcedScanlines != null && !forcedScanlines.trim().isEmpty()) {
                VideoSettings.setScanlines(Boolean.parseBoolean(forcedScanlines.trim()));
            }
        }
        // 调试用：-Dsalvosequence.grille=APERTURE|SHADOW、-Dsalvosequence.warp=true|false、
        // -Dsalvosequence.mix=true|false（旧名 bloom 仍兼容）
        String forcedGrille = System.getProperty("salvosequence.grille");
        if (forcedGrille != null && !forcedGrille.trim().isEmpty()) {
            String wanted = forcedGrille.trim().toUpperCase(java.util.Locale.ROOT)
                    .replace('-', '_').replace('+', '_').replace(' ', '_');
            try {
                VideoSettings.setGrilleStyle(VideoSettings.GrilleStyle.valueOf(wanted));
            } catch (IllegalArgumentException unknown) {
                System.out.println("[crt] 未知格栅图案 " + forcedGrille
                        + "，可用 APERTURE/SHADOW");
            }
        }
        String forcedWarp = System.getProperty("salvosequence.warp");
        if (forcedWarp != null && !forcedWarp.trim().isEmpty()) {
            VideoSettings.setScreenWarp(Boolean.parseBoolean(forcedWarp.trim()));
        }
        String forcedBloom = System.getProperty("salvosequence.mix",
                System.getProperty("salvosequence.bloom"));
        if (forcedBloom != null && !forcedBloom.trim().isEmpty()) {
            VideoSettings.setBloom(Boolean.parseBoolean(forcedBloom.trim()));
        }
        onViewportChanged();
        smokeStartNanos = System.nanoTime();
        System.out.println("[build] " + BUILD_TAG);
        System.out.println(String.format(
                "[app] 视口=%dx%d  缩放=%.4f  整数倍=%s  字体=%s",
                Gdx.graphics.getWidth(), Gdx.graphics.getHeight(),
                pixelScale.effectiveScale(), pixelScale.isIntegerScale(),
                PixelText.pixelFontLoaded() ? PixelText.family() + "（点阵）" : "系统回退"));
    }

    @Override
    public void render() {
        updateSampling();
        double dt = Math.min(0.1, Gdx.graphics.getDeltaTime());
        long t0 = profiling ? System.nanoTime() : 0;
        tick(dt);
        if (profiling) { profTick += System.nanoTime() - t0; t0 = System.nanoTime(); }

        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        double scale = pixelScale.effectiveScale();
        double cw = DESIGN_WIDTH * scale;
        double ch = DESIGN_HEIGHT * scale;
        double ox = pixelScale.offsetX();
        double oy = h - pixelScale.offsetY() - ch;   // GL 坐标原点在左下，设计 y 轴向下

        // 一次上屏：CRT 扫描线 + 荫栅全部在片元着色器里完成。
        drawCrt(ox, oy, cw, ch);

        // 冒烟截图：整帧画完再抓默认帧缓冲（y 翻转后写盘），核对后处理链输出
        if (smokeShotPending) {
            smokeShotPending = false;
            String shot = System.getProperty("salvosequence.smoke.png");
            if (shot != null && !shot.trim().isEmpty()) {
                com.badlogic.gdx.graphics.Pixmap px =
                        com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(
                                0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                com.badlogic.gdx.graphics.PixmapIO.writePNG(
                        Gdx.files.absolute(shot), px, -1, true);
                px.dispose();
                System.out.println("[smoke] 截图 -> " + shot);
            }
        }
    }

    /** 原画上屏：整幅设计缓冲按 alpha 混合铺到视口目标矩形（CRT 关闭时的直通路径）。 */
    private void drawFrame(double ox, double oy, double cw, double ch) {
        batch.setShader(null);
        batch.enableBlending();
        batch.begin();
        batch.draw(frameTexture, (float) ox, (float) oy, (float) cw, (float) ch);
        batch.end();
    }

    /**
     * 单个全屏 CRT pass：横向扫描线 + 纵向孔径格栅。
     *
     * <p><b>像素口径</b>：{@code u_res} 就是设计分辨率，一个纹素 = 一个设计像素；
     * 扫描线周期因此恰好是一行设计像素。旧实现把 y 相位乘 0.3333，变成 3 个设计像素
     * 才一条扫描线，还把 x/y 的相位混在同一个 Dist 里，所以画面上是粗横纹 + 文字发虚。</p>
     *
     * <p><b>两种效果分开</b>：{@code u_scan} 控制行消隐暗带（横向），
     * {@code u_grille} 控制红/绿/蓝荧光条（纵向），来自 {@link VideoSettings.CrtFilter}。
     * 格栅固定在设备像素上：一条荧光条 = 1 个设备像素，三条一组；不跟设计像素放大，
     * 否则 3× 上屏后会变成每条 3 个设备像素宽，看起来像像素又被放大了一次。
     * 图案由 {@code u_shadow} 在纵向栅条（格栅2.txt）和旋转荫罩（crt.txt shadow mask）之间切换。
     * 配比取 Lottes 的 0.5 / 1.5（白场约压到 83%，就是荫罩本身的透光损失）。</p>
     *
     * <p>玻璃桶形扭曲由 {@code u_warp} 单独控制，设置页可以只关扭曲、保留扫描线和格栅；
     * 邻像素混合由 {@code u_mix} 控制：它既是开关也是强度系数 {@code k}（0 = 直通、不跑混合），
     * 按设备像素在设计像素里的位置给左右/上下邻居加权，
     * 横向始终参与、纵向只在格栅开着时参与；越靠边权重越大、中心几乎不混，k 随缩放归一化，
     * 亮度守恒，只混色不加亮。k 的取值来自 {@link VideoSettings#mixK()}，设置页有一根可拖的滑条。</p>
     */
    private ShaderProgram makeCrtShader() {
        String vert =
                "#version 120\n" +
                "attribute vec2 a_position;\n" +
                "attribute vec2 a_texCoord0;\n" +
                "uniform mat4 u_projTrans;\n" +
                "varying vec2 v_tex;\n" +
                "void main() {\n" +
                "  v_tex = a_texCoord0;\n" +
                "  gl_Position = u_projTrans * vec4(a_position, 0.0, 1.0);\n" +
                "}\n";
        String frag =
                "#version 120\n" +
                "varying vec2 v_tex;\n" +
                "uniform sampler2D u_texture;\n" +
                "uniform vec2 u_res;\n" +
                "uniform vec2 u_quad;\n" +
                "uniform float u_scale;\n" +
                "uniform float u_scan;\n" +
                "uniform float u_grille;\n" +
                "uniform float u_shadow;\n" +
                "uniform float u_warp;\n" +
                "uniform float u_mix;\n" +
                "// 显像管玻璃的轻微桶形畸变：越靠边越向外鼓。\n" +
                "vec2 Warp(vec2 pos) {\n" +
                "  if (u_warp < 0.5) return pos;\n" +
                "  pos = pos * 2.0 - 1.0;\n" +
                "  pos *= vec2(1.0 + (pos.y * pos.y) * (1.0 / 64.0),\n" +
                "              1.0 + (pos.x * pos.x) * (1.0 / 48.0));\n" +
                "  return pos * 0.5 + 0.5;\n" +
                "}\n" +
                "// 扫描线：一个设计像素一行，行尾压暗一条设备像素线。\n" +
                "float Scanline(vec2 pos) {\n" +
                "  float line = max(1.0, floor(u_scale * 0.25 + 0.5));\n" +
                "  float dev = fract(pos.y * u_res.y) * u_scale;\n" +
                "  float edge = smoothstep(u_scale - line - 0.5, u_scale - line + 0.5, dev);\n" +
                "  return 1.0 - 0.35 * u_scan * edge;\n" +
                "}\n" +
                "// 邻像素混合（古法抗锯齿）：权重按设备像素在设计像素里的位置算，亮度归一化。\n" +
                "// 越靠像素边缘邻居权重越大，中心几乎不混，所以光是从边缘连续渗出去的，\n" +
                "// 不会把整个放大像素染成一块；k 随缩放归一化，2x/3x 渗色量接近（横向始终有，纵向只在格栅开时参与）。\n" +
                "vec3 MixTap(vec2 pos, vec2 off) {\n" +
                "  return texture2D(u_texture, clamp(pos + off / u_res, 0.0, 1.0)).rgb;\n" +
                "}\n" +
                "vec3 Mix(vec2 pos) {\n" +
                "  vec2 f = fract(pos * u_res) - 0.5;\n" +
                "  float s = 2.0 * u_scale / (u_scale + 1.0);\n" +
                "  float k = u_mix * s * s;\n" +
                "  float cl = exp2(-k * (f.x + 1.0) * (f.x + 1.0));\n" +
                "  float cr = exp2(-k * (f.x - 1.0) * (f.x - 1.0));\n" +
                "  float cu = exp2(-k * (f.y + 1.0) * (f.y + 1.0)) * 0.5 * u_grille;\n" +
                "  float cd = exp2(-k * (f.y - 1.0) * (f.y - 1.0)) * 0.5 * u_grille;\n" +
                "  vec3 sum = MixTap(pos, vec2(0.0))\n" +
                "           + MixTap(pos, vec2(-1.0, 0.0)) * cl + MixTap(pos, vec2(1.0, 0.0)) * cr\n" +
                "           + MixTap(pos, vec2(0.0, -1.0)) * cu + MixTap(pos, vec2(0.0, 1.0)) * cd;\n" +
                "  return sum / (1.0 + cl + cr + cu + cd);\n" +
                "}\n" +
                "// 孔径格栅：一条荧光条 = 一个设备像素，三条一组。\n" +
                "// 不跟设计像素放大：游戏本身已经按 2x/3x 放大，格栅再按设计像素算\n" +
                "// 会变成每条 3 个设备像素宽，看起来像像素又被放大了一次。\n" +
                "// u_shadow = 1 时改用 crt.txt 的旋转荫罩：斜着走，一组 6 个设备像素、每条 2 像素宽。\n" +
                "// 斜率不能写死：crt.txt 里输入像素是 6 设备像素，所以写 3 正好一行走一组；\n" +
                "// 这里一个设计像素只有 u_scale 个设备像素，斜率要取 6/u_scale，一个设计像素行\n" +
                "// 才刚好走满一组。写死 3 的话 3x 每行只走半组，奇偶设计行相位错开，看起来\n" +
                "// 就是荫罩和像素对不齐。\n" +
                "vec3 Grille(vec2 frag) {\n" +
                "  float x = mod(frag.x, 3.0);\n" +
                "  if (u_shadow > 0.5) x = mod(frag.x + frag.y * (6.0 / u_scale), 6.0) * 0.5;\n" +
                "  float p = floor(x);\n" +
                "  vec3 mask = vec3(0.5);\n" +
                "  if (p < 1.0) mask.r = 1.5;\n" +
                "  else if (p < 2.0) mask.g = 1.5;\n" +
                "  else mask.b = 1.5;\n" +
                "  return mix(vec3(1.0), mask, u_grille);\n" +
                "}\n" +
                "void main() {\n" +
                "  vec2 pos = Warp(v_tex);\n" +
                "  vec3 col;\n" +
                "  if (max(abs(pos.x - 0.5), abs(pos.y - 0.5)) > 0.5) {\n" +
                "    col = vec3(0.0);\n" +
                "  } else {\n" +
                "    col = u_mix > 0.001 ? Mix(pos) : texture2D(u_texture, pos).rgb;\n" +
                "  }\n" +
                "  col *= Scanline(pos);\n" +
                "  col *= Grille(v_tex * u_quad);\n" +
                "  gl_FragColor = vec4(col, 1.0);\n" +
                "}\n";
        ShaderProgram p = new ShaderProgram(vert, frag);
        if (!p.isCompiled()) {
            throw new GdxRuntimeException("CRT shader: " + p.getLog());
        }
        return p;
    }

    /**
     * CRT 上屏：按当前 {@link VideoSettings.CrtFilter} 选扫描线 / 格栅。
     * 滤镜关闭、或缩放太小（一个设计像素不足 2 个设备像素）时走直通路径。
     */
    private void drawCrt(double ox, double oy, double cw, double ch) {
        VideoSettings.CrtFilter mode = VideoSettings.crtFilter();
        float scale = (float) (cw / DESIGN_WIDTH);
        if (crtShader == null || mode == VideoSettings.CrtFilter.OFF || scale < 2f) {
            drawFrame(ox, oy, cw, ch);
            return;
        }
        crtShader.bind();
        crtShader.setUniformf("u_res", (float) DESIGN_WIDTH, (float) DESIGN_HEIGHT);
        crtShader.setUniformf("u_quad", (float) cw, (float) ch);
        crtShader.setUniformf("u_scale", scale);
        crtShader.setUniformf("u_scan", mode.hasScanline() ? 1f : 0f);
        crtShader.setUniformf("u_grille", mode.hasGrille() ? 1f : 0f);
        crtShader.setUniformf("u_shadow",
                VideoSettings.grilleStyle() == VideoSettings.GrilleStyle.SHADOW ? 1f : 0f);
        crtShader.setUniformf("u_warp", VideoSettings.screenWarp() ? 1f : 0f);
        crtShader.setUniformf("u_mix",
                VideoSettings.bloom() ? (float) VideoSettings.mixK() : 0f);
        batch.setShader(crtShader);
        batch.disableBlending();
        batch.begin();
        batch.draw(frameTexture, (float) ox, (float) oy, (float) cw, (float) ch);
        batch.end();
        batch.setShader(null);
        batch.enableBlending();
    }



    private void tick(double dt) {
        frameCount++;

        // 0) 触摸手势计时（长按悬浮到点要派发 hover）：安卓上没有鼠标移动事件，
        //    只能靠每帧推进一次。
        if (gestures != null) {
            gestures.update(dt);
        }

        // 0b) 像素化/CRT 开关可能在设置面板里被改过，逐帧对齐纹理过滤
        updateSampling();

        // 1) 界面推进（含过渡）
        long pu = profiling ? System.nanoTime() : 0;
        screens.update(dt);
        if (profiling) { profUpdate += System.nanoTime() - pu; pu = System.nanoTime(); }

        // 2) 画进同一张设计像素缓冲（FrameSink 在 flush 时整幅上传纹理）
        buffer.beginFrame();
        long pd = profiling ? System.nanoTime() : 0;
        screens.draw(buffer);
        if (profiling) { profDraw += System.nanoTime() - pd; }
        buffer.art().flush();

        framesInSecond++;
        fpsSecond += dt;
        if (fpsSecond >= 1.0) {
            fps = (int) Math.round(framesInSecond / fpsSecond);
            framesInSecond = 0;
            fpsSecond = 0;
        }

        if (smokeFrames > 0 && frameCount >= smokeFrames) {
            // 截图不能在这里抓：tick() 跑在 GL 绘制之前，抓到的会是上一帧（甚至只剩
            // 3D 背景、没有界面）。打标记，交给 render() 画完整帧之后再抓。
            smokeShotPending = true;
            if (smokeStartNanos == 0) {
                smokeStartNanos = System.nanoTime();
            }
            double secs = (System.nanoTime() - smokeStartNanos) / 1_000_000_000.0;
            if (profiling && frameCount > 0) {
                double f = frameCount;
                System.out.println(String.format(
                        "[prof] tick=%.2f ms（update %.2f + draw %.2f + 上传 %.2f）  CRT=%.2f",
                        profTick / 1e6 / f, profUpdate / 1e6 / f, profDraw / 1e6 / f,
                        profUpload / 1e6 / f, profFrame / 1e6 / f));
            }
            if (secs > 0) {
                System.out.println(String.format("[smoke] 平均 %.2f FPS（%.2f ms/帧）",
                        frameCount / secs, secs * 1000.0 / frameCount));
            }
            System.out.println("[smoke] 渲染 " + frameCount + " 帧无异常，退出");
            Gdx.app.exit();
        }
        if (frameListener != null) {
            frameListener.accept(frameCount, this);
        }
    }

    /** 应用画质风格：原画（无效果）、模糊（第二层高斯叠加）、辉光（亮部模糊叠加）。 */
    /**
     * 同步纹理采样方式：孔径格栅开 = 双线性平滑放大（像素浸染），否则按 F7 的
     * 像素化开关走 Nearest/Linear。改动只在状态变化时执行，避免每帧重设 GL 状态。
     */
    /** 同步纹理采样方式：F7 像素化开关决定 Nearest/Linear。 */
    private void updateSampling() {
        boolean pixelated = VideoSettings.pixelated();
        if (pixelated == appliedPixelated) {
            return;
        }
        appliedPixelated = pixelated;
        Texture.TextureFilter f = pixelated
                ? Texture.TextureFilter.Nearest
                : Texture.TextureFilter.Linear;
        if (frameTexture != null) {
            frameTexture.setFilter(f, f);
        }
    }

    private void onViewportChanged() {
        pixelScale.updateViewport(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        double scale = pixelScale.effectiveScale();
        double cw = DESIGN_WIDTH * scale;
        screens.setViewport(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(),
                cw, DESIGN_HEIGHT * scale);
    }

    @Override
    public void resize(int width, int height) {
        onViewportChanged();
        // 【关键修复】SpriteBatch 的投影矩阵只在构造时按"当时的窗口尺寸"
        // setToOrtho2D 一次，libGDX 不会随窗口 resize 自动更新。窗口一旦缩小，
        // 画面仍按旧尺寸（比如全屏 1920x1080）投影进变小了的 GL 视口：内容整体被
        // 投影缩小并缩在左下角（GL 原点在左下）——表现成"窗口越小、内容占比越小"。
        // PixelScale 每次其实都重算了，缺的是把新视口同步给投影矩阵。
        // 注意两点：一、用 Gdx.graphics 的口径（HdpiMode.Pixels = 物理像素），
        // 不用回调参数——LWJGL3 传进来的 width/height 在 150% 系统缩放下可能是
        // 逻辑尺寸；二、必须走 setProjectionMatrix() 而不是原地改
        // getProjectionMatrix() 返回的矩阵——本项目扫描线曾因 ShapeRenderer 的
        // matrixDirty 标志吃过大亏，投影矩阵统一走 setter，不留这种暗坑。
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        screenMatrix.setToOrtho2D(0, 0, w, h);
        if (batch != null) {
            batch.setProjectionMatrix(screenMatrix);
        }
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        SalvoSequenceApp.closeAudio();
        if (frameTexture != null) {
            frameTexture.dispose();
        }
        if (framePixmap != null) {
            framePixmap.dispose();
        }
        if (batch != null) {
            batch.dispose();
        }
        if (crtShader != null) {
            crtShader.dispose();
        }
    }

    /**
     * 整幅像素上传：ARGB 非预乘 int[] -> RGBA8888 字节序 -> Pixmap -> 纹理。
     * 640x360 全幅约 23 万像素，纯 Java 循环 <1ms，每帧一次可以接受。
     */
    private final class FrameSink implements com.hysx.salvosequence.core.PixelArt.Sink {

        @Override
        public void upload(int[] argb, int width, int height) {
            long up = profiling ? System.nanoTime() : 0;
            try {
            // 【性能】桌面端把 int[] 直接以 GL_BGRA 灌进纹理：ARGB 的 int 在
            // 小端机上正好是 B,G,R,A 四个字节，和 GL 要的顺序一致，省掉每帧
            // 23 万像素 x4 次的 Java 逐字节搬运（实测 0.71ms -> 0.14ms）。
            // 安卓的 GLES2 不保证支持 BGRA 上传格式，仍走下面的慢路径。
            if (useBgraUpload) {
                intPixels.clear();
                intPixels.put(argb, 0, Math.min(argb.length, intPixels.capacity()));
                intPixels.flip();
                frameTexture.bind(0);
                Gdx.gl.glTexSubImage2D(GL20.GL_TEXTURE_2D, 0, 0, 0, width, height,
                        0x80E1 /* GL_BGRA（libGDX GL20 没给这个常量）*/,
                        GL20.GL_UNSIGNED_BYTE, intPixels);
                return;
            }
            ByteBuffer bytes = rgbaBytes;
            bytes.clear();
            for (int i = 0; i < argb.length; i++) {
                int p = argb[i];
                bytes.put((byte) ((p >> 16) & 0xFF));   // R
                bytes.put((byte) ((p >> 8) & 0xFF));    // G
                bytes.put((byte) (p & 0xFF));           // B
                bytes.put((byte) ((p >> 24) & 0xFF));   // A
            }
            bytes.flip();
            framePixmap.getPixels().clear();
            framePixmap.getPixels().put(bytes);
            framePixmap.getPixels().flip();
            frameTexture.draw(framePixmap, 0, 0);
            } finally {
                if (profiling) { profUpload += System.nanoTime() - up; }
            }
        }

        @Override
        public void uploadBlock(int x, int y, int width, int height, int[] block) {
            // 局部刷新走整幅（flush 的频率是每帧一次，全幅成本可忽略）
            upload(buffer.art().buffer(), buffer.art().width(), buffer.art().height());
        }
    }

    /**
     * 键鼠输入：把窗口像素坐标换算成设计坐标，把 libGDX 键码换成原 JavaFX
     * {@code KeyCode.name()} 口径的按键名（界面层代码一行没改）。
     */
    private final class ViewInput extends InputAdapter {

        /** 事件自带的窗口坐标 -> 设计坐标。多指时每根手指各自换算（不再读全局指针）。 */
        private double designX(int screenX) {
            return (screenX - pixelScale.offsetX()) / pixelScale.effectiveScale();
        }

        private double designY(int screenY) {
            return (screenY - pixelScale.offsetY()) / pixelScale.effectiveScale();
        }

        private double designX() {
            return designX(Gdx.input.getX());
        }

        private double designY() {
            return designY(Gdx.input.getY());
        }

        @Override
        public boolean keyDown(int keycode) {
            // 安卓返回键：先当 ESC 用（关设置 / 退出覆盖层 / 返回上一屏），
            // 标题页设置也没开的时候再按一次就退出游戏（见 ScreenManager.handleBack）。
            if (keycode == Input.Keys.BACK) {
                screens.handleBack();
                return true;
            }
            // 窗口级按键（原 SalvoSequenceApp 的场景监听）
            if (keycode == Input.Keys.F7) {
                boolean on = VideoSettings.togglePixelated();
                System.out.println("[画质] 像素化上屏: "
                        + (on ? "开（Nearest 放大）" : "关（Linear 平滑，不放大像素）"));
                return true;
            }
            if (keycode == Input.Keys.F8) {
                boolean on = VideoSettings.toggleBayer();
                System.out.println("[画质] 拜耳滤镜: " + (on ? "开" : "关"));
                return true;
            }
            if (keycode == Input.Keys.F9) {
                boolean on = VideoSettings.toggleOutputDither();
                System.out.println("[画质] 输出抖动: " + (on ? "开" : "关"));
                return true;
            }
            if (keycode == Input.Keys.F12) {
                // 全屏人物立绘：所有界面通用（开关在 ScreenManager 里）
                screens.handleKey("F12");
                return true;
            }
            if (keycode == Input.Keys.F10) {
                // 诊断双导出挪到 F10，F12 让给立绘彩蛋
                dumpFrames();
                return true;
            }
            if (keycode == Input.Keys.F11) {
                toggleFullscreen();
                return true;
            }
            boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
            if (ctrl && keycode == Input.Keys.W) {
                toWindowed();
                return true;
            }
            if (ctrl && keycode == Input.Keys.Q) {
                Gdx.app.exit();
                return true;
            }
            String name = KEY_NAMES.getOrDefault(keycode, "");
            if (!name.isEmpty()) {
                screens.handleKey(name);
            }
            return true;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            // 安卓多指的第二根手指可能被报成 RIGHT，触摸设备上不看按键；
            // 桌面仍只认左键（右键不拖、不点）。
            if (button != Input.Buttons.LEFT && !isTouchDevice()) {
                return false;
            }
            if (gestures == null) {
                return screens.touchDown(designX(screenX), designY(screenY));
            }
            return gestures.touchDown(pointer, designX(screenX), designY(screenY));
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (button != Input.Buttons.LEFT && !isTouchDevice()) {
                return false;
            }
            if (gestures == null) {
                return screens.touchUp(designX(screenX), designY(screenY));
            }
            return gestures.touchUp(pointer, designX(screenX), designY(screenY));
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (gestures == null) {
                return screens.touchDragged(designX(screenX), designY(screenY));
            }
            return gestures.touchDragged(pointer, designX(screenX), designY(screenY));
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            return screens.mouseMoved(designX(screenX), designY(screenY));
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            // JavaFX 滚轮一格 = 40 个 delta 单位，libGDX 一格 = ±1；符号一致（向下为负）。
            // 双指缩放在 TouchGestures 里换算成同一个 delta，两条路走同一个 onScroll。
            screens.handleScroll(-amountY * 40.0, designX(), designY());
            return true;
        }
    }

    /** 触摸设备（安卓/iOS）：长按悬浮、双指缩放、触摸端不按鼠标键过滤，都看这个。 */
    private static boolean isTouchDevice() {
        if (Gdx.app == null) {
            return false;
        }
        com.badlogic.gdx.Application.ApplicationType type = Gdx.app.getType();
        return type == com.badlogic.gdx.Application.ApplicationType.Android
                || type == com.badlogic.gdx.Application.ApplicationType.iOS;
    }

    /**
     * 诊断双导出（F10）：设计缓冲（CPU 真值，画了什么）与 GL 屏幕帧（实际显示）
     * 各存一张 PNG。两者一致 → 问题在缓冲内容之外；不一致 → 合成/呈现链路。
     */
    private void dumpFrames() {
        try {
            java.io.File dir = new java.io.File("target");
            if (!dir.exists() && !dir.mkdirs()) {
                System.out.println("[dump] 建不了 target/ 目录");
                return;
            }
            buffer.art().savePng(new java.io.File(dir, "ingame-buffer.png"));
            int w = Gdx.graphics.getWidth();
            int h = Gdx.graphics.getHeight();
            Pixmap pm = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(0, 0, w, h);
            java.nio.ByteBuffer bytes = pm.getPixels();
            int[] argb = new int[w * h];
            for (int i = 0; i < argb.length; i++) {
                int r = bytes.get(i * 4) & 0xFF;
                int g = bytes.get(i * 4 + 1) & 0xFF;
                int b = bytes.get(i * 4 + 2) & 0xFF;
                int a = bytes.get(i * 4 + 3) & 0xFF;
                argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            pm.dispose();
            com.hysx.salvosequence.tools.PngWriter.write(argb, w, h,
                    new java.io.File(dir, "ingame-screen.png"));
            System.out.println("[dump] 设计缓冲 -> target/ingame-buffer.png  屏幕帧 -> target/ingame-screen.png"
                    + "  (构建 " + BUILD_TAG + ")");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void toggleFullscreen() {
        // 【已还原】原版切换：独占全屏 <-> 1280x720 窗口。
        if (Gdx.graphics.isFullscreen()) {
            toWindowed();
        } else {
            Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        }
    }

    private void toWindowed() {
        Gdx.graphics.setWindowedMode(1280, 720);
    }

    // ------------------------------------------------------------------ 对外读数

    /** UI 控制层（切界面、开关扫描线等）。 */
    public ScreenManager screens() {
        return screens;
    }

    /** 当前缩放（设计像素 -> 窗口像素）。 */
    public double scale() {
        return pixelScale.effectiveScale();
    }

    /** 当前 FPS。 */
    public int fps() {
        return fps;
    }

    /** 已渲染帧数（离屏核对工具用）。 */
    public long frameCount() {
        return frameCount;
    }

    /** 设计像素缓冲：当前界面按顺序画完的结果。 */
    public CanvasBuffer buffer() {
        return buffer;
    }

    /** 把当前设计像素缓冲导出为 PNG（离屏核对用）。 */
    public void saveDesignPng(java.io.File file) throws java.io.IOException {
        buffer.art().savePng(file);
    }

    /** 主动切换到某个界面（等价于 screens().go(...)）。 */
    public void go(ScreenContext.Target target) {
        screens.go(target);
    }

    // ------------------------------------------------------------------ 资源小件

    /**
     * 辉光/模糊着色器：9+1 tap 高斯（±3 屏幕像素内三圈），可带亮度阈值。
     * 原版是 JavaFX GaussianBlur(3) / Bloom(0.15)，观感对齐即可，不必逐像素一致。
     */
    private static ShaderProgram makeGlowShader() {
        String vertex = "attribute vec4 a_position;\n"
                + "attribute vec2 a_texCoord0;\n"
                + "uniform mat4 u_projTrans;\n"
                + "varying vec2 v_tex;\n"
                + "void main() {\n"
                + "  v_tex = a_texCoord0;\n"
                + "  gl_Position = u_projTrans * a_position;\n"
                + "}\n";
        String fragment = "#ifdef GL_ES\n"
                + "precision mediump float;\n"
                + "#endif\n"
                + "varying vec2 v_tex;\n"
                + "uniform sampler2D u_texture;\n"
                + "uniform vec2 u_texel;\n"
                + "uniform float u_threshold;\n"
                + "uniform float u_gain;\n"
                + "void main() {\n"
                + "  // 纹理是非预乘 ARGB：采样后必须先乘 alpha 再参与模糊/叠加，\n"
                + "  // 否则低 alpha 像素（盘缘纱）以满亮度泄进模糊层，盘缘出现一圈\n"
                + "  // 过亮光晕并在图层边界硬切（断崖）。\n"
                + "  vec4 center = texture2D(u_texture, v_tex);\n"
                + "  vec3 sum = center.rgb * center.a * 0.196382;\n"
                + "  float wsum = 0.196382;\n"
                + "  for (int i = 0; i < 8; i++) {\n"
                + "    float a = 3.14159265 * float(i) / 4.0;\n"
                + "    vec2 off1 = u_texel * vec2(cos(a), sin(a)) * 1.0;\n"
                + "    vec2 off2 = u_texel * vec2(cos(a), sin(a)) * 2.2;\n"
                + "    vec2 off3 = u_texel * vec2(cos(a), sin(a)) * 3.0;\n"
                + "    vec4 c1 = texture2D(u_texture, v_tex + off1);\n"
                + "    vec4 c2 = texture2D(u_texture, v_tex + off2);\n"
                + "    vec4 c3 = texture2D(u_texture, v_tex + off3);\n"
                + "    sum += c1.rgb * c1.a * 0.196382;\n"
                + "    sum += c2.rgb * c2.a * 0.096382;\n"
                + "    sum += c3.rgb * c3.a * 0.039812;\n"
                + "    wsum += 0.196382 + 0.096382 + 0.039812;\n"
                + "  }\n"
                + "  vec3 glow = sum / wsum;\n"
                + "  if (u_threshold >= 0.0) {\n"
                + "    float lum = dot(glow, vec3(0.299, 0.587, 0.114));\n"
                + "    float k = smoothstep(u_threshold, u_threshold + 0.18, lum);\n"
                + "    glow *= k * u_gain;\n"
                + "  }\n"
                + "  gl_FragColor = vec4(glow, 1.0);\n"
                + "}\n";
        ShaderProgram shader = new ShaderProgram(vertex, fragment);
        if (!shader.isCompiled()) {
            System.out.println("[glow] shader 编译失败: " + shader.getLog());
            shader.dispose();
            return null;
        }
        return shader;
    }

    /** libGDX 键码 -> 原 JavaFX {@code KeyCode.name()}。 */
    static final Map<Integer, String> KEY_NAMES = buildKeyNames();

    private static Map<Integer, String> buildKeyNames() {
        Map<Integer, String> m = new HashMap<>();
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for (int i = 0; i < letters.length(); i++) {
            m.put(Input.Keys.A + i, letters.substring(i, i + 1));
        }
        for (int i = 0; i <= 9; i++) {
            m.put(Input.Keys.NUM_0 + i, "DIGIT" + i);
            m.put(Input.Keys.NUMPAD_0 + i, "NUMPAD" + i);
        }
        for (int i = 1; i <= 12; i++) {
            m.put(Input.Keys.F1 + (i - 1), "F" + i);
        }
        m.put(Input.Keys.UP, "UP");
        m.put(Input.Keys.DOWN, "DOWN");
        m.put(Input.Keys.LEFT, "LEFT");
        m.put(Input.Keys.RIGHT, "RIGHT");
        m.put(Input.Keys.ENTER, "ENTER");
        m.put(Input.Keys.NUMPAD_ENTER, "ENTER");
        m.put(Input.Keys.ESCAPE, "ESCAPE");
        m.put(Input.Keys.SPACE, "SPACE");
        m.put(Input.Keys.TAB, "TAB");
        m.put(Input.Keys.BACKSPACE, "BACK_SPACE");
        m.put(Input.Keys.DEL, "DELETE");
        m.put(Input.Keys.HOME, "HOME");
        m.put(Input.Keys.END, "END");
        m.put(Input.Keys.PAGE_UP, "PAGE_UP");
        m.put(Input.Keys.PAGE_DOWN, "PAGE_DOWN");
        m.put(Input.Keys.PERIOD, "PERIOD");
        m.put(Input.Keys.COMMA, "COMMA");
        m.put(Input.Keys.MINUS, "MINUS");
        m.put(Input.Keys.EQUALS, "EQUALS");
        m.put(Input.Keys.SEMICOLON, "SEMICOLON");
        m.put(Input.Keys.APOSTROPHE, "APOSTROPHE");
        m.put(Input.Keys.LEFT_BRACKET, "OPEN_BRACKET");
        m.put(Input.Keys.RIGHT_BRACKET, "CLOSE_BRACKET");
        m.put(Input.Keys.BACKSLASH, "BACK_SLASH");
        m.put(Input.Keys.SLASH, "SLASH");
        m.put(Input.Keys.GRAVE, "BACK_QUOTE");
        m.put(Input.Keys.SHIFT_LEFT, "SHIFT");
        m.put(Input.Keys.SHIFT_RIGHT, "SHIFT");
        m.put(Input.Keys.CONTROL_LEFT, "CONTROL");
        m.put(Input.Keys.CONTROL_RIGHT, "CONTROL");
        m.put(Input.Keys.ALT_LEFT, "ALT");
        m.put(Input.Keys.ALT_RIGHT, "ALT");
        return m;
    }
}
