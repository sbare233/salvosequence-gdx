package com.hysx.salvosequence.core;

import com.badlogic.gdx.graphics.Pixmap;

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

    private static volatile boolean nativesReady;

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
            Pixmap.Format format = pixmap.getFormat();
            // 调色板 PNG（PNG8，mapback 那 1440 张）会被解成 RGB888：每像素 3 字节、没有 alpha。
            // 以前这里写死按 4 字节读，一碰调色板图就 IndexOutOfBounds 越界。
            int bpp = format == Pixmap.Format.RGBA8888 ? 4 : format == Pixmap.Format.RGB888 ? 3 : 0;
            if (bpp == 4) {
                for (int i = 0; i < argb.length; i++) {
                    int r = rgba.get(i * 4) & 0xFF;
                    int g = rgba.get(i * 4 + 1) & 0xFF;
                    int b = rgba.get(i * 4 + 2) & 0xFF;
                    int a = rgba.get(i * 4 + 3) & 0xFF;
                    argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            } else if (bpp == 3) {
                for (int i = 0; i < argb.length; i++) {
                    int r = rgba.get(i * 3) & 0xFF;
                    int g = rgba.get(i * 3 + 1) & 0xFF;
                    int b = rgba.get(i * 3 + 2) & 0xFF;
                    argb[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            } else {
                // 其它格式（灰度、单通道 alpha 等）走慢路径，逐点问 Pixmap 要颜色。
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        argb[y * w + x] = pixmap.getPixel(x, y);
                    }
                }
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

    /**
     * 桌面端要先加载 gdx 的动态库，{@link Pixmap} 的 PNG 解码走的就是它
     * （Gdx2DPixmap 是 native 的）。
     *
     * <p>安卓上不能走这条路：{@code AndroidApplication} 启动时已经
     * {@code System.loadLibrary("gdx")} 装好了，再调桌面版的
     * {@code GdxNativesLoader.load()} 会去碰 {@code SharedLibraryLoader}——
     * 那个类在 gdx 1.13 里被拆成了单独的 {@code gdx-jnigen-loader} 依赖，
     * 手打 APK 的 d8 输入里没有，于是一进星图就
     * {@code NoClassDefFoundError: SharedLibraryLoader}。</p>
     *
     * <p>加载代码单独放在 {@link DesktopNatives} 里：安卓上这个内部类根本不会被解析，
     * 也就不会去解析 {@code GdxNativesLoader} 那个方法引用。</p>
     */
    private static void ensureNatives() {
        if (nativesReady) {
            return;
        }
        // 先加载、后置标记：星盘的解码在后台线程跑，标记提前置上会让主线程
        // 在 native 库还没就绪时就去碰 Pixmap（UnsatisfiedLinkError）
        synchronized (PngLoader.class) {
            if (nativesReady) {
                return;
            }
            if (!Platform.isAndroid()) {
                DesktopNatives.load();
            }
            nativesReady = true;
        }
    }

    /** 只在桌面端加载 gdx 动态库；安卓端不解析这个类。 */
    private static final class DesktopNatives {
        static void load() {
            com.badlogic.gdx.utils.GdxNativesLoader.load();
        }
    }
}
