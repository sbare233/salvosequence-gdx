package com.hysx.salvosequence.core;

/**
 * 文本口径常量（Android 版）：字号下限、字体来源提示。
 *
 * <p>桌面版这里还负责用 AWT 加载 TTF（{@code Font.createFont}）；Android 没有
 * AWT，加载挪到了 {@link CjkRasterizer#install}，本类只保留共用口径，让
 * {@code PixelGameView} 等共享代码照常编译。</p>
 */
public final class PixelText {

    /** UI 最小可辨认字号 = 像素字体原生点阵 12px；所有小于它的字号一律按 12 画。 */
    public static final double BASE_SIZE = 12;
    public static final double MIN_SIZE = 12;

    /** 像素字体相对路径（assets 下同一路径）。 */
    static final String FONT_RESOURCE = "fonts/ZLabsPixel_12px_M_CN.ttf";

    private PixelText() {
    }

    /** 字体家族名（调试读数用）。 */
    public static String family() {
        return "ZLabsPixel 12px (android)";
    }

    /** 像素字体是否真的加载上了。 */
    public static boolean pixelFontLoaded() {
        return CjkRasterizer.ready();
    }
}
