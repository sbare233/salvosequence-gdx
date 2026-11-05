package com.hysx.salvosequence.ui;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
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
 * 原版的辉光层（GaussianBlur + SCREEN 叠加）与 Bloom 在这里用同一个片元着色器复刻。</p>
 */
public final class PixelGameView extends ApplicationAdapter {

    /** 构建标记：视觉口径改动时更新，用于排查"跑的是不是最新构建"。 */
    public static final String BUILD_TAG = "2026-11-04-star-disk-v23";

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
    private Texture whitePixel;
    private ShaderProgram glowShader;
    /** 辉光着色器里当前帧的模糊半径（uv 单位），随视口刷新。 */
    private float blurRadiusUv = 3f / (DESIGN_WIDTH * 3f);

    // ---- 辉光（BLOOM）：gdx-vfx BloomEffect（开源后处理链，Apache 2.0）----
    // 链路：亮部阈值 -> 多 pass 高斯模糊 -> 原画/辉光合成。下面这组参数可直接调观感。
    /** 亮度阈值：高于它的像素才进辉光（0~1）。0.2 = 暗部也发光（用户口径）。 */
    private static final float BLOOM_THRESHOLD = 0.2f;
    /** 高斯模糊 pass 数：多 pass 迭代让光晕铺得更开。 */
    private static final int BLOOM_BLUR_PASSES = 2;
    /** 单 pass 模糊强度（像素偏移量）。 */
    private static final float BLOOM_BLUR_AMOUNT = 2.0f;
    /** 辉光层叠加强度。 */
    private static final float BLOOM_INTENSITY = 1.3f;
    /** 原画层保留强度（1 = 原样）。 */
    private static final float BLOOM_BASE_INTENSITY = 1.0f;

    /** 模糊层的叠加强度（SCREEN 混合前乘在模糊结果上；1 = 全强度）。 */
    private static final float BLUR_STRENGTH = 0.25f;

    /** 屏幕空间投影（0..w, 0..h，GL 原点左下）：正常绘制与扫描线都用它。 */
    private final com.badlogic.gdx.math.Matrix4 screenMatrix =
            new com.badlogic.gdx.math.Matrix4();
    /** 设计空间投影（0..640, 0..360）：后处理捕获时用，让 VFX 链在 640x360 上跑。 */
    private final com.badlogic.gdx.math.Matrix4 designMatrix =
            new com.badlogic.gdx.math.Matrix4().setToOrtho2D(
                    0, 0, DESIGN_WIDTH, DESIGN_HEIGHT);

    private com.crashinvaders.vfx.VfxManager vfxManager;
    private com.crashinvaders.vfx.effects.BloomEffect bloomEffect;

    /** 当前生效的画质风格（设置面板里改，每帧检测变化）。 */
    private VideoSettings.Style appliedStyle;
    /** 当前生效的像素化开关（F7 切换时换纹理过滤）。 */
    private boolean appliedPixelated = true;
    private long frameCount;
    private int fps;
    private int framesInSecond;
    private double fpsSecond;
    /** 冒烟自检：设置了 -Dsalvosequence.smoke.frames 时，画够帧数自动退出。 */
    private final int smokeFrames = Integer.getInteger("salvosequence.smoke.frames", -1);
    /** 冒烟模式的计时起点：用来打印平均帧时间（关掉垂直同步时可比性能改动）。 */
    private long smokeStartNanos;

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

        framePixmap = new Pixmap(DESIGN_WIDTH, DESIGN_HEIGHT, Pixmap.Format.RGBA8888);
        frameTexture = new Texture(framePixmap);
        frameTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        buffer.art().attachSink(new FrameSink());

        whitePixel = makeWhitePixel();
        glowShader = makeGlowShader();

        vfxManager = new com.crashinvaders.vfx.VfxManager(Pixmap.Format.RGBA8888);
        bloomEffect = new com.crashinvaders.vfx.effects.BloomEffect();
        bloomEffect.setThreshold(BLOOM_THRESHOLD);
        bloomEffect.setBlurPasses(BLOOM_BLUR_PASSES);
        bloomEffect.setBlurAmount(BLOOM_BLUR_AMOUNT);
        bloomEffect.setBloomIntensity(BLOOM_INTENSITY);
        bloomEffect.setBaseIntensity(BLOOM_BASE_INTENSITY);
        bloomEffect.disableBlending();
        vfxManager.addEffect(bloomEffect);
        // 【性能】后处理链固定跑 640x360，而不是窗口/全屏分辨率：Bloom 的
        // 阈值->2 pass 高斯->合成一共 7 趟全屏绘制，1920x1080 下每帧要摸 2M 像素 x7，
        // 核显上占掉 1/4 的 GPU；换成 640x360 后像素数少 9 倍，最后由
        // renderToScreen(0,0,w,h) 最近邻放大到整屏（整数倍缩放，像素依旧锐利）。
        vfxManager.resize(DESIGN_WIDTH, DESIGN_HEIGHT);
        vfxManager.setEffectTextureParams(
                com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge,
                com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge,
                com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest,
                com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest);

        Gdx.input.setInputProcessor(new ViewInput());

        // 调试用：-Dsalvosequence.style=RAW|BLUR|BLOOM 可强制画质风格（截图核对后处理链）
        String forcedStyle = System.getProperty("salvosequence.style");
        if (forcedStyle != null && !forcedStyle.isBlank()) {
            try {
                VideoSettings.setStyle(VideoSettings.Style.valueOf(forcedStyle.trim()));
            } catch (IllegalArgumentException ignored) {
                // 名字写错就按默认走，不打断启动
            }
        }
        applyStyle(VideoSettings.style());
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
        double dt = Math.min(0.1, Gdx.graphics.getDeltaTime());
        tick(dt);

        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        double scale = pixelScale.effectiveScale();
        double cw = DESIGN_WIDTH * scale;
        double ch = DESIGN_HEIGHT * scale;
        double ox = pixelScale.offsetX();
        double oy = h - pixelScale.offsetY() - ch;   // GL 坐标原点在左下，设计 y 轴向下

        // 扫描线时机：默认"特效后"在风格化之后压线；"特效前"则先画进画面，
        // 让辉光/模糊把线一起处理。
        boolean scanlinesOn = screens.isScanlineEnabled();
        boolean scanBefore = scanlinesOn
                && VideoSettings.scanlineOrder() == VideoSettings.ScanlineOrder.BEFORE_EFFECT;
        boolean bloomOn = appliedStyle == VideoSettings.Style.BLOOM && vfxManager != null;
        // 辉光模式下后处理在 640x360 上跑，屏幕级的 2px 扫描线没法画进捕获
        // （会变成 6px 粗线），所以"特效前"在这种模式下等价于"特效后"。
        boolean scanAfter = scanlinesOn && (!scanBefore || bloomOn);

        // 1) 原画（所有风格共用的底）。【移植版关键修复】设计缓冲不是不透明
        // 图像：nebula 背景只含星点（大片 alpha=0），盘面盘缘 alpha 仅 8~160，
        // 必须开混合按 alpha 上屏。此前 disableBlending 把 alpha 整个丢弃，
        // 盘缘以满亮度 rgb 直写帧缓冲 = 实心盘 + 图层边界切边（断崖）。
        // JavaFX 原版合成同一份缓冲时 alpha 被正确处理，故原版无此现象。
        drawFrame(ox, oy, cw, ch);

        // 1b) 像素化开关（F7）：Nearest 放大 <-> Linear 平滑，纹理过滤热切换
        if (appliedPixelated != VideoSettings.pixelated()) {
            appliedPixelated = VideoSettings.pixelated();
            Texture.TextureFilter f = appliedPixelated
                    ? Texture.TextureFilter.Nearest
                    : Texture.TextureFilter.Linear;
            frameTexture.setFilter(f, f);
        }

        // 1c) 特效前：扫描线先压在原画上，后面的风格化把线一起处理。
        //     辉光例外——它的合成输出叠在原画之上（本游戏未启用 vfx 的 blendingEnabled，
        //     不会整屏覆盖），扫描线改画进后处理的输入捕获（见 2），
        //     否则同一道线会在屏幕上被压暗两次。
        VideoSettings.Style style = appliedStyle;
        boolean bloomActive = style == VideoSettings.Style.BLOOM && vfxManager != null;
        if (scanBefore && !bloomActive) {
            drawScanlines(ox, oy, cw, ch, scale);
        }

        // 2) 画质风格。辉光走 gdx-vfx 开源后处理链（阈值->多 pass 高斯->合成）；
        //    模糊走单 pass 高斯 + SCREEN 叠加（原画保留在下面一层）。
        if (bloomActive) {
            vfxManager.cleanUpBuffers();
            vfxManager.beginInputCapture();
            // 捕获缓冲是 640x360：换成设计空间投影按 1:1 画，最后由 renderToScreen 放大
            batch.setProjectionMatrix(designMatrix);
            drawFrame(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT);
            batch.setProjectionMatrix(screenMatrix);
            vfxManager.endInputCapture();
            vfxManager.applyEffects();
            // 全屏放大：GL 直接拉伸到整个窗口（整数倍时像素仍然对齐）
            vfxManager.renderToScreen(0, 0, w, h);
        } else {
            // 2a) 模糊风格：同一帧高斯模糊后 SCREEN 叠加（原画底已在步骤 1 画好）。
            //     注意：混合函数必须走 batch.setBlendFunction（begin() 会用它重设 GL 状态），
            //     直接 glBlendFunc 会被 begin() 里的默认 alpha 混合覆盖——第二遍就会
            //     把原画整个替换掉，表现成"模糊没有叠加原画"。
            if (style == VideoSettings.Style.BLUR && glowShader != null) {
                glowShader.bind();
                glowShader.setUniformf("u_texel", blurRadiusUv, blurRadiusUv);
                glowShader.setUniformf("u_threshold", -1f);
                glowShader.setUniformf("u_gain", BLUR_STRENGTH);
                // SCREEN：src + dst - src*dst -> (GL_ONE, GL_ONE_MINUS_SRC_COLOR)
                batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_COLOR);
                batch.setShader(glowShader);
                batch.enableBlending();
                batch.begin();
                batch.draw(frameTexture, (float) ox, (float) oy, (float) cw, (float) ch);
                batch.end();
                batch.setShader(null);
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            }
        }

        // 3) 特效后（默认）：扫描线最后画，压在风格化结果之上，线条锐利。
        if (scanAfter) {
            drawScanlines(ox, oy, cw, ch, scale);
        }
    }

    /** 原画上屏：整幅设计缓冲按 alpha 混合铺到视口里的目标矩形。 */
    private void drawFrame(double ox, double oy, double cw, double ch) {
        batch.setShader(null);
        batch.enableBlending();
        batch.begin();
        batch.draw(frameTexture, (float) ox, (float) oy, (float) cw, (float) ch);
        batch.end();
    }

    /**
     * CRT 扫描线（屏幕空间）：每个设计像素压暗下半像素。
     *
     * <p>和内容走同一个 batch —— 同一张投影矩阵、同一个坐标系，等价 JavaFX 版
     * "扫描线画进同一张画布"。【关键修复】此前走 ShapeRenderer，而它的
     * begin() 只在 setProjectionMatrix() 时才重建组合矩阵（matrixDirty 标志），
     * resize() 里原地改 getProjectionMatrix() 对它完全不可见，于是扫描线
     * 永远停在 create() 时的全屏投影里 —— 窗口缩小后只剩"左下角"有扫描线
     * （内容却是对的，因为 SpriteBatch 每帧按值重读投影矩阵）。</p>
     *
     * <p>周期跟随缩放：一个设计像素一暗一明。低倍率时周期下限 2 像素，
     * 保证每行至少压暗 1 像素，不会因为缩得太小而消失。</p>
     *
     * <p>辉光风格"特效前"时会先画进后处理的输入捕获而不是屏幕，见
     * {@link #render()} 步骤 1c/2。</p>
     */
    private void drawScanlines(double ox, double oy, double cw, double ch, double scale) {
        float period = (float) Math.max(2.0, scale);
        float line = period * 0.5f;
        batch.enableBlending();
        batch.setColor(0f, 0f, 0f, 0.75f);
        batch.begin();
        for (float y = period - line; y < ch; y += period) {
            batch.draw(whitePixel, (float) ox, (float) (oy + ch - y - line), (float) cw, line);
        }
        batch.end();
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void tick(double dt) {
        frameCount++;

        // 0) 触摸手势计时（长按悬浮到点要派发 hover）：安卓上没有鼠标移动事件，
        //    只能靠每帧推进一次。
        if (gestures != null) {
            gestures.update(dt);
        }

        // 0b) 画质设置可能在设置面板里被改过，逐帧对齐一次
        if (appliedStyle != VideoSettings.style()) {
            applyStyle(VideoSettings.style());
        }

        // 1) 界面推进（含过渡）
        screens.update(dt);

        // 2) 画进同一张设计像素缓冲（FrameSink 在 flush 时整幅上传纹理）
        buffer.beginFrame();
        screens.draw(buffer);
        buffer.art().flush();

        framesInSecond++;
        fpsSecond += dt;
        if (fpsSecond >= 1.0) {
            fps = (int) Math.round(framesInSecond / fpsSecond);
            framesInSecond = 0;
            fpsSecond = 0;
        }

        if (smokeFrames > 0 && frameCount >= smokeFrames) {
            String shot = System.getProperty("salvosequence.smoke.png");
            if (shot != null && !shot.isBlank()) {
                // 冒烟截图：直接抓默认帧缓冲（y 翻转后写盘），用来核对后处理链的输出
                com.badlogic.gdx.graphics.Pixmap px =
                        com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(
                                0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                com.badlogic.gdx.graphics.PixmapIO.writePNG(
                        Gdx.files.absolute(shot), px, -1, true);
                px.dispose();
                System.out.println("[smoke] 截图 -> " + shot);
            }
            if (smokeStartNanos == 0) {
                smokeStartNanos = System.nanoTime();
            }
            double secs = (System.nanoTime() - smokeStartNanos) / 1_000_000_000.0;
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
    private void applyStyle(VideoSettings.Style style) {
        appliedStyle = style;
    }

    private void onViewportChanged() {
        pixelScale.updateViewport(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        double scale = pixelScale.effectiveScale();
        double cw = DESIGN_WIDTH * scale;
        // 模糊半径 1.5 屏幕像素（原 GaussianBlur(3) 的一半，用户口径），换算成 uv
        blurRadiusUv = (float) (1.5 / cw);
        screens.setViewport(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(),
                cw, DESIGN_HEIGHT * scale);
        // 后处理链始终保持设计分辨率（见 create() 里的性能说明），窗口变化不用跟着改
        if (vfxManager != null && vfxManager.getWidth() != DESIGN_WIDTH) {
            vfxManager.resize(DESIGN_WIDTH, DESIGN_HEIGHT);
        }
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
        if (whitePixel != null) {
            whitePixel.dispose();
        }
        if (glowShader != null) {
            glowShader.dispose();
        }
        if (bloomEffect != null) {
            bloomEffect.dispose();
        }
        if (vfxManager != null) {
            vfxManager.dispose();
        }
    }

    /**
     * 整幅像素上传：ARGB 非预乘 int[] -> RGBA8888 字节序 -> Pixmap -> 纹理。
     * 640x360 全幅约 23 万像素，纯 Java 循环 <1ms，每帧一次可以接受。
     */
    private final class FrameSink implements com.hysx.salvosequence.core.PixelArt.Sink {

        @Override
        public void upload(int[] argb, int width, int height) {
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
     * 诊断双导出（F12）：设计缓冲（CPU 真值，画了什么）与 GL 屏幕帧（实际显示）
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

    private static Texture makeWhitePixel() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(1f, 1f, 1f, 1f);
        pm.fill();
        Texture t = new Texture(pm);
        pm.dispose();
        return t;
    }

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
