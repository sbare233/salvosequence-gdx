package com.hysx.salvosequence.core;

import java.io.File;

/**
 * 背景工坊用的图片编解码后端。
 *
 * <p>桌面端用 AWT（{@link AwtImageCodec}，ImageIO + Graphics2D 双线性缩放）；
 * Android 没有 java.awt，启动时换成 {@code AndroidImageCodec}（BitmapFactory）。
 * 界面代码只认这个门面，两个平台共用同一份 {@code BackdropLabScreen}。</p>
 *
 * <p>后端由平台入口在启动时安装（桌面 {@code SalvoSequenceApp.create()}、
 * Android {@code AndroidLauncher.onCreate()}），没装时 {@link #backend()} 返回 null，
 * 背景工坊会显示"当前平台没有图片解码器"而不是崩掉。</p>
 */
public final class ImageCodec {

    /** 平台后端。 */
    public interface Backend {

        /** 读图并等比缩进 maxW×maxH 的框里；读不了返回 null。 */
        Loaded loadScaled(File file, int maxW, int maxH);

        /** 把 ARGB 像素写成 PNG；成功返回 true。 */
        boolean writePng(int[] argb, int width, int height, File target);
    }

    /** 读出来并缩放后的结果（ARGB，同 PngLoader 的口径）。 */
    public record Loaded(int[] argb, int width, int height) {
    }

    private static Backend backend;

    private ImageCodec() {
    }

    public static Backend backend() {
        return backend;
    }

    public static void setBackend(Backend codec) {
        backend = codec;
    }
}
