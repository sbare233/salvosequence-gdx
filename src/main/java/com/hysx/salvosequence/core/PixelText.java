package com.hysx.salvosequence.core;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 中文/混合文本辅助（libGDX 版，AWT 字体后端）。
 *
 * <p>UI 文字统一用打包的 12px 点阵像素字体（resources/fonts，简中覆盖）：
 * 12px 是它的原生点阵尺寸，整倍渲染时边缘干净、笔画完整 —— 系统矢量字体
 * （雅黑）在 8-11 号的小字上渲染出来细横发虚、认字靠猜，已经弃用。
 * 字体加载失败（资源缺失/损坏）时回退系统字体，游戏不至于开不出来。</p>
 *
 * <p>原版走 JavaFX {@code Font.loadFont}；libGDX 版换成 AWT
 * {@code Font.createFont}——渲染管线在 {@link CjkRasterizer} 里本来就是
 * "3 倍字号离屏光栅化再盒式平均"，两个工具箱在像素字体的点阵字形上结果一致。</p>
 */
public final class PixelText {

    /** UI 最小可辨认字号 = 像素字体原生点阵 12px；所有小于它的字号一律按 12 画。 */
    public static final double BASE_SIZE = 12;
    public static final double MIN_SIZE = 12;

    /** 像素字体相对路径（外部 res/ 或 jar 内都按这个路径放）。 */
    static final String FONT_RESOURCE = "/fonts/ZLabsPixel_12px_M_CN.ttf";

    private static Font pixelBase;
    private static String family;

    private PixelText() {
    }

    static {
        loadFont();
    }

    /** 优先加载像素字体（外部 res/ 优先，jar 内兜底）；失败回退系统 CJK 字体。 */
    private static void loadFont() {
        String src = Assets.source(FONT_RESOURCE);
        try (InputStream in = Assets.open(FONT_RESOURCE)) {
            if (in == null || in == Assets.EMPTY) {
                System.out.println("pixel font resource missing: " + FONT_RESOURCE);
                throw new IllegalStateException("missing");
            }
            byte[] bytes = in.readAllBytes();
            System.out.println("[font] read " + bytes.length + " bytes from " + src);
            if (bytes.length == 0) {
                throw new IllegalStateException("empty");
            }
            pixelBase = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(bytes));
            family = pixelBase.getFamily();
            System.out.println("[font] " + family + " <- " + src);
        } catch (Throwable t) {
            pixelBase = null;
            family = resolveFamily();
            System.out.println("pixel font load failed (" + src + "), fallback to system CJK: "
                    + family + " (" + t + ")");
        }
    }

    private static String resolveFamily() {
        try {
            for (String candidate : new String[]{"Microsoft YaHei", "微软雅黑", "SimHei", "黑体", "SimSun", "宋体"}) {
                for (String available : GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getAvailableFontFamilyNames()) {
                    if (candidate.equals(available)) {
                        return candidate;
                    }
                }
            }
        } catch (Throwable headless) {
            // 完全无字体环境：交给 AWT 的 Dialog 默认值
        }
        return Font.SANS_SERIF;
    }

    /** 以设计像素字号创建中文字体（真实渲染字号为 size * scale）；小于 12 按下限钳制。 */
    public static Font cjk(double designSize, double scale, boolean bold) {
        double size = Math.max(2, Math.rint(Math.max(MIN_SIZE, designSize) * Math.max(1, scale)));
        if (pixelBase != null) {
            return pixelBase.deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) size);
        }
        return new Font(family, bold ? Font.BOLD : Font.PLAIN, (int) size);
    }

    public static String family() {
        return family;
    }

    /** 像素字体是否真的加载上了（false = 用的是系统字体回退）。 */
    public static boolean pixelFontLoaded() {
        return pixelBase != null;
    }
}
