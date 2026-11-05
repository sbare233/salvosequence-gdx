package com.hysx.salvosequence.core;

import java.util.Map;

/**
 * 设计像素画布：所有绘制都在“设计像素”坐标系里进行，最后整层统一放大。
 *
 * <p>关键点：画布尺寸等于设计尺寸（默认 640x360），放大交给上屏层的纹理采样
 * （Nearest 过滤，等价原 JavaFX 的 {@code Scale} + {@code setSmooth(false)}），
 * 因此不会出现半像素模糊，也不会把 UI 画在设备像素上导致缩放抖动。</p>
 *
 * <h2>为什么内部是 int[] 缓冲</h2>
 * <p>每个像素都走系统图像接口的话，画一张 16x16 的图就是 256 次调用，而清屏一次是
 * 640x360 = 230400 次——图像一旦上量就撑不住。所以所有原语先写进 {@code int[] pixels}
 * （ARGB 非预乘），只在 {@link #flush()} 时一次性推给挂载的上传器
 * （libGDX 版是 {@link Sink}，由视图把它写进 {@code Pixmap}/纹理）。叠加/拷贝混合因此
 * 都是纯整数运算，没有颜色对象分配。</p>
 */
public final class PixelArt {

    /** 上传器：flush() 时把整幅（或一块）像素交给它，由宿主写进纹理。 */
    public interface Sink {

        /** 整幅上传。 */
        void upload(int[] argb, int width, int height);

        /** 只上传一块（局部刷新）。 */
        void uploadBlock(int x, int y, int width, int height, int[] block);
    }

    private final int width;
    private final int height;
    private final int[] pixels;
    private Sink sink;

    /** 本帧被改动过的矩形（设计像素坐标），供上层做增量放大。 */
    private int dirtyMinX = Integer.MAX_VALUE;
    private int dirtyMinY = Integer.MAX_VALUE;
    private int dirtyMaxX = Integer.MIN_VALUE;
    private int dirtyMaxY = Integer.MIN_VALUE;

    /** 是否要把像素推给上传器（{@link #flush(int, int, int, int)} 时才真正写）。 */
    private boolean pendingUpload;

    public PixelArt(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
    }

    /** 挂上/换掉上传器（libGDX 视图在 create() 里挂；无头环境可以不挂）。 */
    public void attachSink(Sink newSink) {
        this.sink = newSink;
        pendingUpload = true;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** 底层 ARGB 缓冲（只读用途；写操作请走原语，否则脏矩形不会更新）。 */
    public int[] buffer() {
        return pixels;
    }

    // ---------------------------------------------------------------- 提交

    /**
     * 把缓冲推给上传器。由 {@link CanvasBuffer} 在每帧绘制结束后调用一次。
     */
    public void flush() {
        if (!pendingUpload) {
            return;
        }
        if (sink != null) {
            sink.upload(pixels, width, height);
        }
        pendingUpload = false;
    }

    /**
     * 只推一块（给将来的局部刷新用）。
     *
     * @param x 左上角 x
     * @param y 左上角 y
     * @param w 宽
     * @param h 高
     */
    public void flush(int x, int y, int w, int h) {
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(width, x + w);
        int y1 = Math.min(height, y + h);
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        int cw = x1 - x0;
        int ch = y1 - y0;
        int[] block = new int[cw * ch];
        for (int row = 0; row < ch; row++) {
            System.arraycopy(pixels, (y0 + row) * width + x0, block, row * cw, cw);
        }
        if (sink != null) {
            sink.uploadBlock(x0, y0, cw, ch, block);
        }
    }

    /** 清空为全透明。 */
    public void clear() {
        java.util.Arrays.fill(pixels, 0);
        pendingUpload = true;
        markDirty(0, 0, width - 1, height - 1);
    }

    /**
     * 取出并重置"本帧改动范围"。
     *
     * @return {minX, minY, maxX, maxY}；本帧没有任何改动时返回 null
     */
    public int[] pollDirty() {
        if (dirtyMaxX < dirtyMinX) {
            return null;
        }
        int[] rect = {dirtyMinX, dirtyMinY, dirtyMaxX, dirtyMaxY};
        dirtyMinX = Integer.MAX_VALUE;
        dirtyMinY = Integer.MAX_VALUE;
        dirtyMaxX = Integer.MIN_VALUE;
        dirtyMaxY = Integer.MIN_VALUE;
        return rect;
    }

    /** 标记一块区域为脏（内部使用；绘制原语都会调用）。 */
    void markDirty(int minX, int minY, int maxX, int maxY) {
        if (maxX < 0 || maxY < 0 || minX >= width || minY >= height) {
            return;
        }
        dirtyMinX = Math.min(dirtyMinX, Math.max(0, minX));
        dirtyMinY = Math.min(dirtyMinY, Math.max(0, minY));
        dirtyMaxX = Math.max(dirtyMaxX, Math.min(width - 1, maxX));
        dirtyMaxY = Math.max(dirtyMaxY, Math.min(height - 1, maxY));
    }

    public void fill(Color color) {
        fillRect(0, 0, width, height, color);
    }

    // ------------------------------------------------------------ 单像素

    public void pixel(int x, int y, Color color) {
        if (color == null || color.getOpacity() == 0) {
            return;
        }
        pixelArgb(x, y, toArgb(color));
    }

    /** 直接写 ARGB（热路径；不做透明混合，调用方自己算好颜色）。 */
    public void pixelArgb(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        pixels[y * width + x] = argb;
        pendingUpload = true;
        markDirty(x, y, x, y);
    }

    /** 取已有像素颜色（透明返回 null）。 */
    public Color pixelAt(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return null;
        }
        int argb = pixels[y * width + x];
        return (argb >>> 24) == 0 ? null : fromArgb(argb);
    }

    // -------------------------------------------------------------- 矩形

    public void rect(int x, int y, int w, int h, Color color) {
        fillRect(x, y, w, h, color);
    }

    /** 实心矩形（裁剪 + 逐行 Arrays.fill）。 */
    public void fillRect(int x, int y, int w, int h, Color color) {
        if (color == null || color.getOpacity() == 0 || w <= 0 || h <= 0) {
            return;
        }
        fillRectArgb(x, y, w, h, toArgb(color));
    }

    void fillRectArgb(int x, int y, int w, int h, int argb) {
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(width, x + w);
        int y1 = Math.min(height, y + h);
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        if ((argb >>> 24) == 0xFF) {
            for (int yy = y0; yy < y1; yy++) {
                java.util.Arrays.fill(pixels, yy * width + x0, yy * width + x1, argb);
            }
        } else {
            int rowW = x1 - x0;
            for (int yy = y0; yy < y1; yy++) {
                int base = yy * width;
                for (int xx = x0; xx < x1; xx++) {
                    pixels[base + xx] = over(argb, pixels[base + xx]);
                }
            }
        }
        pendingUpload = true;
        markDirty(x0, y0, x1 - 1, y1 - 1);
    }

    /** 单像素描边矩形。 */
    public void frame(int x, int y, int w, int h, Color color) {
        hline(x, x + w - 1, y, color);
        hline(x, x + w - 1, y + h - 1, color);
        vline(y, y + h - 1, x, color);
        vline(y, y + h - 1, x + w - 1, color);
    }

    public void hline(int x0, int x1, int y, Color color) {
        if (color == null) {
            return;
        }
        fillRectArgb(Math.min(x0, x1), y, Math.abs(x1 - x0) + 1, 1, toArgb(color));
    }

    public void vline(int y0, int y1, int x, Color color) {
        if (color == null) {
            return;
        }
        fillRectArgb(x, Math.min(y0, y1), 1, Math.abs(y1 - y0) + 1, toArgb(color));
    }

    public void line(double x0, double y0, double x1, double y1, Color color) {
        if (color == null) {
            return;
        }
        int argb = toArgb(color);
        // Bresenham
        int ix0 = (int) Math.round(x0);
        int iy0 = (int) Math.round(y0);
        int ix1 = (int) Math.round(x1);
        int iy1 = (int) Math.round(y1);
        int dx = Math.abs(ix1 - ix0);
        int dy = -Math.abs(iy1 - iy0);
        int sx = ix0 < ix1 ? 1 : -1;
        int sy = iy0 < iy1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            pixelArgb(ix0, iy0, argb);
            if (ix0 == ix1 && iy0 == iy1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                ix0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                iy0 += sy;
            }
        }
    }

    // ------------------------------------------------------- 椭圆 / 多边形

    public void ellipse(int cx, int cy, int rx, int ry, Color color) {
        if (color == null || rx <= 0 || ry <= 0) {
            return;
        }
        int argb = toArgb(color);
        for (int yy = -ry; yy <= ry; yy++) {
            double t = 1.0 - (double) (yy * yy) / (double) (ry * ry);
            if (t < 0) {
                continue;
            }
            int half = (int) Math.round(rx * Math.sqrt(t));
            fillRectArgb(cx - half, cy + yy, half * 2 + 1, 1, argb);
        }
    }

    public void polygon(double[] xs, double[] ys, Color color) {
        if (color == null || xs == null || xs.length < 3) {
            return;
        }
        int argb = toArgb(color);
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (double y : ys) {
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        int y0 = Math.max(0, (int) Math.floor(minY));
        int y1 = Math.min(height - 1, (int) Math.ceil(maxY));
        int n = xs.length;
        double[] xsAt = new double[n];
        for (int y = y0; y <= y1; y++) {
            double cy = y + 0.5;
            int count = 0;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                double yi = ys[i];
                double yj = ys[j];
                if ((yi <= cy && yj > cy) || (yj <= cy && yi > cy)) {
                    double x = xs[i] + (cy - yi) / (yj - yi) * (xs[j] - xs[i]);
                    xsAt[count++] = x;
                }
            }
            if (count < 2) {
                continue;
            }
            java.util.Arrays.sort(xsAt, 0, count);
            for (int k = 0; k + 1 < count; k += 2) {
                int sx = (int) Math.round(xsAt[k]);
                int ex = (int) Math.round(xsAt[k + 1]) - 1;
                fillRectArgb(sx, y, ex - sx + 1, 1, argb);
            }
        }
    }

    public void polygonOutline(double[] xs, double[] ys, Color color) {
        if (color == null || xs == null || xs.length < 2) {
            return;
        }
        for (int i = 0; i < xs.length; i++) {
            int j = (i + 1) % xs.length;
            line(xs[i], ys[i], xs[j], ys[j], color);
        }
    }

    public void ditherRect(int x, int y, int w, int h, Color color, int density) {
        if (color == null || density <= 0) {
            return;
        }
        int argb = toArgb(color);
        int step = density + 1;
        // 【性能】按步长跳着写，而不是逐像素取模判断：整屏抖动的内层循环
        // 从"宽"次降到"宽/step"次（StartScreen 每帧都整屏抖一次，实测省 ~0.8ms）。
        for (int yy = y; yy < y + h; yy++) {
            int offset = Math.floorMod(-(x + yy), step);
            for (int xx = x + offset; xx < x + w; xx += step) {
                pixelArgb(xx, yy, argb);
            }
        }
    }

    public void bevelFrame(int x, int y, int w, int h, int cut, Color color) {
        if (color == null || w <= 0 || h <= 0) {
            return;
        }
        int c = Math.max(0, Math.min(cut, Math.min(w, h) / 2));
        double[] xs = {x + c, x + w - 1 - c, x + w - 1, x + w - 1 - c, x + c, x};
        double[] ys = {y, y, y + c, y + h - 1, y + h - 1, y + h - 1 - c};
        polygonOutline(xs, ys, color);
    }

    // -------------------------------------------------------------- 位图

    /**
     * 原样叠加一张整数 ARGB 图（src 尺寸 = sw x sh，从 (sx,sy) 起读，0 表不透明）。
     *
     * <p>这是所有图像绘制的底座：{@link Sprite} 与 {@link PixelArt#blitImage} 都走它。</p>
     *
     * @param swapRB true 表示源是 BGRA（JavaFX 的 {@code PixelFormat.getIntArgbPreInstance}
     *               之外的读法），会交换 R/B 通道
     */
    void blitArgbRgba(int[] src, int sw, int sh, int sx, int sy,
                      int dx, int dy, boolean replaceTransparent, boolean swapRB) {
        if (src == null || sw <= 0 || sh <= 0) {
            return;
        }
        int x0 = Math.max(0, dx);
        int y0 = Math.max(0, dy);
        int x1 = Math.min(width, dx + sw);
        int y1 = Math.min(height, dy + sh);
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        for (int yy = y0; yy < y1; yy++) {
            int srcRow = (yy - dy + sy) * sw;
            int dstRow = yy * width;
            for (int xx = x0; xx < x1; xx++) {
                int s = src[srcRow + (xx - dx + sx)];
                if (swapRB) {
                    s = (s & 0xFF00FF00) | ((s & 0x00FF0000) >>> 16) | ((s & 0x000000FF) << 16);
                }
                int a = s >>> 24;
                if (a == 0 && !replaceTransparent) {
                    continue;
                }
                int dst = dstRow + xx;
                pixels[dst] = (a == 0 || a == 0xFF) ? s : over(s, pixels[dst]);
            }
        }
        pendingUpload = true;
        markDirty(x0, y0, x1 - 1, y1 - 1);
    }

    /**
     * 最近邻缩放叠加（像素画的正确缩放方式，不会糊）。
     *
     * <p>支持非整数倍：目标像素反查源像素，不用先放大源图，避免中间缓冲。</p>
     */
    void blitArgbScaled(int[] src, int sw, int sh, int sx, int sy,
                        int dx, int dy, int dw, int dh, boolean replaceTransparent) {
        blitArgbTile(src, sw, sh, sx, sy, dx, dy, dw, dh, replaceTransparent, false, false, 1.0);
    }

    /**
     * 整幅原样覆盖（源尺寸可以小于画布，按目标尺寸最近邻铺满，左上角对齐、不缩放坐标以外的地方不动）。
     *
     * <p>{@link #blitArgbTile} 是通用版：逐像素做 {@code (long) col * sw / dw} 的整除换算、
     * 再判 alpha 混色。铺一张本来就 1:1、全不透明的整屏背景（星图的星系盘帧）时这些
     * 全是白算——一次整屏就是 23 万像素，实测能吃掉 2~4ms。这里只做行拷贝。</p>
     *
     * <p>目标尺寸等于源尺寸时退化成一次 {@code arraycopy}；不等时按行为单位最近邻取样。</p>
     */
    public void copyFrame(int[] src, int sw, int sh) {
        if (src == null || sw <= 0 || sh <= 0) {
            return;
        }
        int x1 = Math.min(width, sw);
        int y1 = Math.min(height, sh);
        if (sw == width && sh == height) {
            System.arraycopy(src, 0, pixels, 0, width * height);
        } else {
            for (int yy = 0; yy < y1; yy++) {
                int syy = (int) ((long) yy * sh / height);
                int srcRow = syy * sw;
                int dstRow = yy * width;
                for (int xx = 0; xx < x1; xx++) {
                    int sxx = (int) ((long) xx * sw / width);
                    pixels[dstRow + xx] = src[srcRow + sxx];
                }
            }
        }
        pendingUpload = true;
        markDirty(0, 0, x1 - 1, y1 - 1);
    }

    /**
     * 完整版位图绘制：裁剪 + 最近邻缩放 + 水平/垂直翻转 + 亮度着色。
     *
     * <p>这是 {@link com.hysx.salvosequence.graphics.Sprite} 的唯一出口，所以参数
     * 直接收 int[]，不让 core 反向依赖 graphics 包。</p>
     *
     * @param src                源 ARGB（非预乘），行距 = sw
     * @param sw                 源整幅宽（行距）
     * @param sh                 源整幅高
     * @param sx                 源子矩形左上角 x
     * @param sy                 源子矩形左上角 y
     * @param dx                 目标左上角 x（设计像素）
     * @param dy                 目标左上角 y（设计像素）
     * @param dw                 目标宽（设计像素）
     * @param dh                 目标高（设计像素）
     * @param replaceTransparent 源透明像素是否覆盖目标（true = 整块覆盖，false = 叠加）
     * @param flipX              水平翻转
     * @param flipY              垂直翻转
     * @param tint               亮度系数，1.0 = 原色；&gt;1 提亮（受击闪白），&lt;1 压暗
     */
    public void blitArgbTile(int[] src, int sw, int sh, int sx, int sy,
                             int dx, int dy, int dw, int dh, boolean replaceTransparent,
                             boolean flipX, boolean flipY, double tint) {
        if (src == null || sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) {
            return;
        }
        int x0 = Math.max(0, dx);
        int y0 = Math.max(0, dy);
        int x1 = Math.min(width, dx + dw);
        int y1 = Math.min(height, dy + dh);
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        boolean shade = tint != 1.0;
        for (int yy = y0; yy < y1; yy++) {
            int localY = yy - dy;
            int row = flipY ? (dh - 1 - localY) : localY;
            int syy = sy + (int) ((long) row * sh / dh);
            if (syy < 0 || syy >= sh) {
                continue;
            }
            int srcRow = syy * sw;
            int dstRow = yy * width;
            for (int xx = x0; xx < x1; xx++) {
                int localX = xx - dx;
                int col = flipX ? (dw - 1 - localX) : localX;
                int sxx = sx + (int) ((long) col * sw / dw);
                if (sxx < 0 || sxx >= sw) {
                    continue;
                }
                int s = src[srcRow + sxx];
                int a = s >>> 24;
                if (a == 0 && !replaceTransparent) {
                    continue;
                }
                if (shade && a != 0) {
                    s = tint(s, tint);
                }
                int dst = dstRow + xx;
                pixels[dst] = (a == 0 || a == 0xFF) ? s : over(s, pixels[dst]);
            }
        }
        pendingUpload = true;
        markDirty(x0, y0, x1 - 1, y1 - 1);
    }

    /**
     * {@link #blitArgbTile} 的拜耳滤镜版：非透明像素在写入前按 8×8 拜耳阈值
     * 量化到 {@code quantize} 的色阶网格。阈值取<b>目标屏幕像素</b>坐标——
     * 最近邻放大时同一源像素的多个目标点各过各的阈值格，抖动图案在任意
     * 缩放下都保持屏幕分辨率锐利（渐变盘的色带变成像素画十字网）。
     *
     * <p>开销：每个非透明目标像素多 ~12 次整数运算（查表 + 三通道量化），
     * 全屏最坏 ~40 万像素 ≈ 1~2ms/帧；alpha 不量化（边缘不会碎）。</p>
     *
     * @param quantize 每通道色阶宽度（8~24 观感明显；&lt;=1 等价普通 blit）
     */
    public void blitArgbTileDithered(int[] src, int sw, int sh, int sx, int sy,
                                     int dx, int dy, int dw, int dh,
                                     boolean replaceTransparent, double tint, int quantize) {
        if (quantize <= 1) {
            blitArgbTile(src, sw, sh, sx, sy, dx, dy, dw, dh,
                    replaceTransparent, false, false, tint);
            return;
        }
        if (src == null || sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) {
            return;
        }
        int x0 = Math.max(0, dx);
        int y0 = Math.max(0, dy);
        int x1 = Math.min(width, dx + dw);
        int y1 = Math.min(height, dy + dh);
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        boolean shade = tint != 1.0;
        int[] bayer = Backdrops.BAYER;
        for (int yy = y0; yy < y1; yy++) {
            int localY = yy - dy;
            int syy = sy + (int) ((long) localY * sh / dh);
            if (syy < 0 || syy >= sh) {
                continue;
            }
            int srcRow = syy * sw;
            int dstRow = yy * width;
            int bayRow = (yy & 7) << 3;
            for (int xx = x0; xx < x1; xx++) {
                int localX = xx - dx;
                int sxx = sx + (int) ((long) localX * sw / dw);
                if (sxx < 0 || sxx >= sw) {
                    continue;
                }
                int s = src[srcRow + sxx];
                int a = s >>> 24;
                if (a == 0 && !replaceTransparent) {
                    continue;
                }
                if (shade && a != 0) {
                    s = tint(s, tint);
                }
                if (a != 0) {
                    s = dither8(s, bayer[bayRow + (xx & 7)], quantize);
                }
                int dst = dstRow + xx;
                pixels[dst] = (a == 0 || a == 0xFF) ? s : over(s, pixels[dst]);
            }
        }
        pendingUpload = true;
        markDirty(x0, y0, x1 - 1, y1 - 1);
    }

    /** 拜耳有序抖动：加 ±半档偏置后四舍五入到 quantize 网格（RGB；alpha 不动）。 */
    private static int dither8(int argb, int threshold, int q) {
        int off = (threshold - 32) * q >> 6;          // 阈值 0..63 → 偏置 ±q/2
        int r = quantize8(((argb >> 16) & 0xFF) + off, q);
        int g = quantize8(((argb >> 8) & 0xFF) + off, q);
        int b = quantize8((argb & 0xFF) + off, q);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int quantize8(int v, int q) {
        v = ((v + (q >> 1)) / q) * q;
        return v < 0 ? 0 : Math.min(255, v);
    }

    /**
     * 任意角度旋转叠加一张位图（中心为旋转轴）。
     *
     * <p>给「图片以非 90 度角转起来」这种需求用（F12 那只大和）。三个取舍：</p>
     * <ul>
     *   <li><b>双线性采样</b>：最近邻在旋转时会让细线断断续续，双线性在小尺寸下更稳。</li>
     *   <li><b>超采样</b>：每个目标像素再打 {@code 2×2} 个子样本。缩小时这一步是必须的，
     *       否则源图上的细节会被整片跳过，边缘还会闪。</li>
     *   <li><b>不生成中间图</b>：每个目标像素直接反解回源坐标，省一次全图旋转。</li>
     * </ul>
     *
     * <p>角度按<b>弧度</b>给（{@code 0} = 不转，正数 = 屏幕坐标下的顺时针）。</p>
     *
     * @param src      源 ARGB（非预乘），行距 = {@code sw}
     * @param sw       源整幅宽（行距）
     * @param sh       源整幅高
     * @param cx       旋转轴（设计像素）
     * @param cy       旋转轴（设计像素）
     * @param w        目标宽（设计像素）
     * @param h        目标高（设计像素）
     * @param radians  旋转角（弧度）
     * @param tint     亮度系数，1.0 = 原色
     */
    public void blitArgbRotated(int[] src, int sw, int sh,
                                double cx, double cy, int w, int h,
                                double radians, double tint) {
        if (src == null || sw <= 0 || sh <= 0 || w <= 0 || h <= 0) {
            return;
        }
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        // 保守包围盒：四个角都转过去取整数包围（宁可多扫几行，不能少画一块）
        double hw = w / 2.0;
        double hh = h / 2.0;
        double reach = Math.max(Math.abs(hw * cos) + Math.abs(hh * sin),
                Math.abs(hw * sin) + Math.abs(hh * cos));
        int x0 = Math.max(0, (int) Math.floor(cx - reach) - 1);
        int y0 = Math.max(0, (int) Math.floor(cy - reach) - 1);
        int x1 = Math.min(width - 1, (int) Math.ceil(cx + reach) + 1);
        int y1 = Math.min(height - 1, (int) Math.ceil(cy + reach) + 1);
        if (x1 < x0 || y1 < y0) {
            return;
        }

        // 每个像素 2x2 子样本；子样本自身也做双线性
        final int sub = 2;
        final double subCount = sub * sub;
        final double px = 1.0 / sub;

        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double colAcc = 0;   // 预乘色（每通道各一份）
                double accR = 0;
                double accG = 0;
                double accB = 0;
                double alphaAcc = 0;
                for (int jy = 0; jy < sub; jy++) {
                    for (int jx = 0; jx < sub; jx++) {
                        double rx = (x - cx) + (jx + 0.5) * px;
                        double ry = (y - cy) + (jy + 0.5) * px;
                        // 反向旋转回未旋转坐标系
                        double u = rx * cos + ry * sin;
                        double v = -rx * sin + ry * cos;
                        // 归一到 [0,1]，再换成源像素坐标
                        double ux = ((u + w / 2.0) / w) * sw;
                        double uy = ((v + h / 2.0) / h) * sh;
                        if (uy < 0 || uy > sh - 1 || ux < 0 || ux > sw - 1) {
                            continue;   // 图外：这个子样本贡献 0（是覆盖率，不是颜色）
                        }
                        double wsum = 0;
                        double asum = 0;
                        double rsum = 0;
                        double gsum = 0;
                        double bsum = 0;
                        for (int oy = -1; oy <= 1; oy++) {
                            int syy = (int) Math.floor(uy) + oy;
                            if (syy < 0 || syy >= sh) {
                                continue;
                            }
                            double fy = 1 - Math.abs(uy - syy);
                            if (fy <= 0) {
                                continue;
                            }
                            for (int ox = -1; ox <= 1; ox++) {
                                int sxx = (int) Math.floor(ux) + ox;
                                if (sxx < 0 || sxx >= sw) {
                                    continue;
                                }
                                double fx = 1 - Math.abs(ux - sxx);
                                if (fx <= 0) {
                                    continue;
                                }
                                double k = fx * fy;
                                if (k < 0.002) {
                                    continue;
                                }
                                int argb = src[syy * sw + sxx];
                                double a = (argb >>> 24) / 255.0;
                                asum += a * k;
                                rsum += ((argb >> 16) & 0xFF) * a * k;
                                gsum += ((argb >> 8) & 0xFF) * a * k;
                                bsum += (argb & 0xFF) * a * k;
                                wsum += k;
                            }
                        }
                        if (wsum <= 0) {
                            continue;
                        }
                        // 归一到"整格"，这样缺角处 alpha 自然下降 = 抗锯齿边
                        double a = asum / wsum;
                        if (a <= 0.0015) {
                            continue;
                        }
                        alphaAcc += a;
                        // 颜色按 alpha 加权平均（asum 已经是权重和）
                        double inv = 1.0 / asum;
                        accR += rsum * inv * a;
                        accG += gsum * inv * a;
                        accB += bsum * inv * a;
                        colAcc += a;
                    }
                }
                if (colAcc <= 0.0005) {
                    continue;
                }
                int a = (int) Math.round(Math.min(1.0, alphaAcc / subCount) * 255);
                if (a <= 0) {
                    continue;
                }
                int r = clamp255((int) Math.round(accR / colAcc));
                int g = clamp255((int) Math.round(accG / colAcc));
                int b = clamp255((int) Math.round(accB / colAcc));
                int out = (a << 24) | (r << 16) | (g << 8) | b;
                if (tint != 1.0) {
                    out = tint(out, tint);
                }
                int dst = y * width + x;
                pixels[dst] = (a == 0xFF) ? out : over(out, pixels[dst]);
            }
        }
        pendingUpload = true;
        markDirty(x0, y0, x1, y1);
    }

    /** 用 ARGB 缓冲整块替换一块矩形（不做叠加）。 */
    public void blitBuffer(int[] src, int sw, int sh, int dx, int dy) {
        blitArgbRgba(src, sw, sh, 0, 0, dx, dy, true, false);
    }

    public void blit(PaletteSprite sprite, int dx, int dy) {
        if (sprite == null) {
            return;
        }
        blitSprite(sprite, dx, dy, 1);
    }

    /** 按整数倍放大叠加字符画的像素图（保持硬边）。 */
    public void blitSprite(PaletteSprite sprite, int dx, int dy, int scale) {
        if (sprite == null || scale <= 0) {
            return;
        }
        for (int y = 0; y < sprite.height(); y++) {
            for (int x = 0; x < sprite.width(); x++) {
                Color c = sprite.colorAt(x, y);
                if (c == null) {
                    continue;
                }
                fillRectArgb(dx + x * scale, dy + y * scale, scale, scale, toArgb(c));
            }
        }
    }

    public void blitStretched(PaletteSprite sprite, int dx, int dy, int dw, int dh) {
        if (sprite == null || dw <= 0 || dh <= 0) {
            return;
        }
        int sw = sprite.width();
        int sh = sprite.height();
        for (int yy = 0; yy < dh; yy++) {
            int sy = (int) ((long) yy * sh / dh);
            for (int xx = 0; xx < dw; xx++) {
                int sx = (int) ((long) xx * sw / dw);
                Color c = sprite.colorAt(sx, sy);
                if (c != null) {
                    pixelArgb(dx + xx, dy + yy, toArgb(c));
                }
            }
        }
    }

    /**
     * 把一张 JavaFX 图像按最近邻叠加到画布上（1 设计像素 = 1 图像像素）。
     *
     * @param src                源图
     * @param dx                 目标左上角 x
     * @param dy                 目标左上角 y
     * @param replaceTransparent 透明像素是否覆盖目标（true = 整块覆盖）
     */
    /** 原样叠加一张整数 ARGB 图（1 设计像素 = 1 图像像素；libGDX 版图片入口）。 */
    public void blitArgb(int[] argb, int sw, int sh, int dx, int dy, boolean replaceTransparent) {
        if (argb == null || sw <= 0 || sh <= 0) {
            return;
        }
        blitArgbRgba(argb, sw, sh, 0, 0, dx, dy, replaceTransparent, false);
    }

    // ------------------------------------------------------------ 取用/导出

    public void savePng(java.io.File file) throws java.io.IOException {
        com.hysx.salvosequence.tools.PngWriter.write(pixels, width, height, file);
    }

    public void debugGrid(Color color, int step) {
        if (color == null || step <= 0) {
            return;
        }
        for (int x = 0; x < width; x += step) {
            vline(0, height - 1, x, color);
        }
        for (int y = 0; y < height; y += step) {
            hline(0, width - 1, y, color);
        }
    }

    // ------------------------------------------------------------ 颜色工具

    public static Color shade(Color base, double factor) {
        if (base == null) {
            return null;
        }
        return Color.color(
                clamp01(base.getRed() * factor),
                clamp01(base.getGreen() * factor),
                clamp01(base.getBlue() * factor),
                base.getOpacity());
    }

    public static Color pick(Map<Color, Color> map, Color key, Color fallback) {
        if (map == null || key == null) {
            return fallback;
        }
        Color c = map.get(key);
        return c != null ? c : fallback;
    }

    /** Color -> 非预乘 ARGB。 */
    public static int toArgb(Color c) {
        int a = (int) Math.round(c.getOpacity() * 255);
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static Color fromArgb(int argb) {
        return Color.rgb((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF,
                ((argb >>> 24) & 0xFF) / 255.0);
    }

    /**
     * 标准 source-over 叠加（非预乘）。
     *
     * @param src 上层 ARGB
     * @param dst 下层 ARGB
     * @return 混合后的 ARGB
     */
    public static int over(int src, int dst) {
        int sa = src >>> 24;
        if (sa == 0) {
            return dst;
        }
        if (sa == 0xFF) {
            return src;
        }
        int da = dst >>> 24;
        int outA = sa + da * (255 - sa) / 255;
        if (outA == 0) {
            return 0;
        }
        int sr = (src >> 16) & 0xFF;
        int sg = (src >> 8) & 0xFF;
        int sb = src & 0xFF;
        int dr = (dst >> 16) & 0xFF;
        int dg = (dst >> 8) & 0xFF;
        int db = dst & 0xFF;
        int r = (sr * sa + dr * da * (255 - sa) / 255) / outA;
        int g = (sg * sa + dg * da * (255 - sa) / 255) / outA;
        int b = (sb * sa + db * da * (255 - sa) / 255) / outA;
        return (outA << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    /** 给 ARGB 乘一个亮度系数（做高光/受击闪白用）。 */
    public static int tint(int argb, double factor) {
        int a = argb >>> 24;
        if (a == 0) {
            return 0;
        }
        int r = clamp255((int) Math.round(((argb >> 16) & 0xFF) * factor));
        int g = clamp255((int) Math.round(((argb >> 8) & 0xFF) * factor));
        int b = clamp255((int) Math.round((argb & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
