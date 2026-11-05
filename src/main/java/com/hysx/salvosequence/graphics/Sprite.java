package com.hysx.salvosequence.graphics;

import com.hysx.salvosequence.core.PixelArt;

/**
 * 一张像素图：ARGB（非预乘）整数数组 + 尺寸。
 *
 * <p>为什么不用 {@code javafx.scene.image.Image} 到处传：</p>
 * <ul>
 *   <li>读一次 {@code Image} 的像素要过 {@code PixelReader}，逐像素调用极慢；
 *       转成 int[] 之后叠加、着色、翻转全是整数运算。</li>
 *   <li>像素画要的是<b>硬边最近邻</b>，用 {@code ImageView} 缩放会被
 *       {@code smooth} 与设备像素比影响，画进设计像素画布后由
 *       {@link PixelArt} 统一放大才可控。</li>
 * </ul>
 *
 * <p>不可变：所有绘制方法都不改自己，只往目标画布写。</p>
 */
public final class Sprite {

    private final String name;
    private final int width;
    private final int height;
    private final int[] pixels;

    /**
     * @param name   名字（日志/调试用，例如 "player/player_fighter_f4_16x16"）
     * @param width  宽（设计像素）
     * @param height 高（设计像素）
     * @param pixels ARGB 非预乘，长度必须 &gt;= width*height，行距为 width
     */
    public Sprite(String name, int width, int height, int[] pixels) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("尺寸必须为正: " + width + "x" + height);
        }
        if (pixels == null || pixels.length < width * height) {
            throw new IllegalArgumentException(
                    "像素数据不足: 需要 " + (width * height) + " 个，实际 "
                            + (pixels == null ? 0 : pixels.length));
        }
        this.name = name;
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }

    /** 用同一个颜色板全填（做占位图/纯色块用）。 */
    public static Sprite filled(String name, int width, int height, int argb) {
        int[] px = new int[width * height];
        java.util.Arrays.fill(px, argb);
        return new Sprite(name, width, height, px);
    }

    /**
     * 直接读一张类路径图片当整张精灵，<b>不看文件名约定</b>。
     *
     * <p>{@link SpriteStore} 管的是「有帧数/尺寸约定的游戏素材」；这个方法管的是
     * 「就是一张图」——比如美术给的图、临时拿来试效果的图。读到就返回，
     * 读不到（不存在/解码失败）返回 {@code null}，由调用方决定退化成什么。</p>
     *
     * @param name          名字（日志用）
     * @param classpathPath 以 {@code /} 开头的类路径路径，例如 {@code /sprites/yamato.png}
     */
    public static Sprite loadWhole(String name, String classpathPath) {
        com.hysx.salvosequence.core.PngLoader.Bitmap img =
                com.hysx.salvosequence.core.PngLoader.loadFromAssets(classpathPath);
        if (img == null) {
            return null;
        }
        return new Sprite(name, img.width(), img.height(), img.argb());
    }

    public String name() {
        return name;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** 底层 ARGB（只读用；调用方不得修改）。 */
    public int[] pixels() {
        return pixels;
    }

    /** 取某点 ARGB（越界返回 0）。 */
    public int argbAt(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return 0;
        }
        return pixels[y * width + x];
    }

    /** 是否所有像素都透明（用来判断一张图是不是画失败了）。 */
    public boolean isBlank() {
        for (int p : pixels) {
            if ((p >>> 24) != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 切出一块子图（精灵表按帧切分就是它）。
     *
     * @param sx 子图左上角 x
     * @param sy 子图左上角 y
     * @param w  子图宽
     * @param h  子图高
     * @return 新的 Sprite；越界部分按透明处理
     */
    public Sprite sub(String name, int sx, int sy, int w, int h) {
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            int srcY = sy + y;
            if (srcY < 0 || srcY >= height) {
                continue;
            }
            for (int x = 0; x < w; x++) {
                int srcX = sx + x;
                if (srcX < 0 || srcX >= width) {
                    continue;
                }
                out[y * w + x] = pixels[srcY * width + srcX];
            }
        }
        return new Sprite(name, w, h, out);
    }

    /** 水平镜像一份（朝向相反的单位用）。 */
    public Sprite mirrored(String name) {
        int[] out = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                out[y * width + (width - 1 - x)] = pixels[y * width + x];
            }
        }
        return new Sprite(name, width, height, out);
    }

    /** 乘一个亮度系数，返回新图（1.0 = 原样，>1 提亮，<1 压暗）。 */
    public Sprite shaded(String name, double factor) {
        int[] out = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            out[i] = PixelArt.tint(pixels[i], factor);
        }
        return new Sprite(name, width, height, out);
    }

    // ------------------------------------------------------------ 绘制出口

    /**
     * 1:1 叠加到画布（像素画默认用法：1 图像像素 = 1 设计像素）。
     *
     * @param art 目标画布
     * @param dx  目标左上角 x（设计像素）
     * @param dy  目标左上角 y（设计像素）
     */
    public void draw(PixelArt art, int dx, int dy) {
        draw(art, dx, dy, 1.0, false, 1.0);
    }

    /** 按整数倍放大叠加（整数倍时永远对齐，不会有小数取整的抖动）。 */
    public void drawScaled(PixelArt art, int dx, int dy, int scale) {
        draw(art, dx, dy, scale, false, 1.0);
    }

    /**
     * 通用绘制。
     *
     * @param art    目标画布
     * @param dx     目标左上角 x（设计像素）
     * @param dy     目标左上角 y（设计像素）
     * @param scale  缩放倍数（1.0 = 原尺寸；非整数走最近邻，仍然是硬边）
     * @param flipX  水平翻转
     * @param tint   亮度系数（1.0 = 原色）
     */
    public void draw(PixelArt art, int dx, int dy, double scale, boolean flipX, double tint) {
        if (art == null || scale <= 0) {
            return;
        }
        int dw = Math.max(1, (int) Math.round(width * scale));
        int dh = Math.max(1, (int) Math.round(height * scale));
        art.blitArgbTile(pixels, width, height, 0, 0, dx, dy, dw, dh, false, flipX, false, tint);
    }

    /** 整块覆盖式绘制（透明像素也覆盖目标，用于清场后贴底图）。 */
    public void drawOpaque(PixelArt art, int dx, int dy) {
        art.blitArgbTile(pixels, width, height, 0, 0, dx, dy, width, height,
                true, false, false, 1.0);
    }

    @Override
    public String toString() {
        return "Sprite[" + name + " " + width + "x" + height + "]";
    }
}
