package com.hysx.salvosequence.core;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.GdxNativesLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * PNG → ARGB 整数数组（libGDX 版图片入口）。
 *
 * <p>原来走 JavaFX {@code Image} + {@code PixelReader}；libGDX 版用
 * {@link Pixmap} 解码（自带 stb 风格解码器，不需要 GL 上下文，无头单测也能用），
 * 解出来再统一转成项目通用的<b>非预乘 ARGB</b> {@code int[]}，
 * 这样 {@link Sprite}、{@link Backdrops}、{@link PixelArt} 的像素语义完全不变。</p>
 *
 * <p>Pixmap 的 RGBA8888 缓冲是每像素 4 字节 R,G,B,A 顺序，转 ARGB 只重排字节。</p>
 */
public final class PngLoader {

    /** 一张解码好的图：非预乘 ARGB，行距 = width。 */
    public record Bitmap(int[] argb, int width, int height) {

        public int argbAt(int x, int y) {
            if (x < 0 || y < 0 || x >= width || y >= height) {
                return 0;
            }
            return argb[y * width + x];
        }
    }

    private static boolean nativesReady;

    private PngLoader() {
    }

    /** 解码任意输入流里的 PNG；失败抛 IOException，由调用方决定退化。 */
    public static Bitmap load(InputStream in) throws IOException {
        ensureNatives();
        if (in == null) {
            throw new IOException("输入流为空");
        }
        byte[] bytes = readAll(in);
        Pixmap pixmap = new Pixmap(bytes, 0, bytes.length);
        try {
            int w = pixmap.getWidth();
            int h = pixmap.getHeight();
            java.nio.ByteBuffer rgba = pixmap.getPixels();
            int[] argb = new int[w * h];
            for (int i = 0; i < argb.length; i++) {
                int r = rgba.get(i * 4) & 0xFF;
                int g = rgba.get(i * 4 + 1) & 0xFF;
                int b = rgba.get(i * 4 + 2) & 0xFF;
                int a = rgba.get(i * 4 + 3) & 0xFF;
                argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            return new Bitmap(argb, w, h);
        } finally {
            pixmap.dispose();
        }
    }

    /** 从资源定位器（外部 res/ 优先，classpath 兜底）读 PNG；读不到返回 null。 */
    public static Bitmap loadFromAssets(String path) {
        try (InputStream in = Assets.open(path)) {
            if (in == null || in == Assets.EMPTY) {
                return null;
            }
            return load(in);
        } catch (Exception e) {
            System.out.println("[png] " + path + " 解码失败: " + e);
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) > 0) {
            out.write(chunk, 0, n);
        }
        return out.toByteArray();
    }

    private static void ensureNatives() {
        if (!nativesReady) {
            GdxNativesLoader.load();
            nativesReady = true;
        }
    }
}
