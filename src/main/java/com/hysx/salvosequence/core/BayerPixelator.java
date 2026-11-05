package com.hysx.salvosequence.core;

/**
 * 拜耳像素化器：把照片/原图转成游戏风格的像素背景。
 *
 * <p>管线：cover 适配取样 → 块平均（像素块 1~4）→ 亮度/对比/饱和 →
 * 深空化（伽马压暗 + 暗部冷色偏移，让照片贴游戏基调）→
 * <b>拜耳网点色阶量化</b>：每通道按 8×8 阈值矩阵在相邻色阶间散点，
 * 渐变区域是点的密度变化，不是平滑过渡。</p>
 */
public final class BayerPixelator {

    public static final int W = 640;
    public static final int H = 360;

    private static final int[] BAYER = {
            0, 32, 8, 40, 2, 34, 10, 42,
            48, 16, 56, 24, 50, 18, 58, 26,
            12, 44, 4, 36, 14, 46, 6, 38,
            60, 28, 52, 20, 62, 30, 54, 22,
            3, 35, 11, 43, 1, 33, 9, 41,
            51, 19, 59, 27, 49, 17, 57, 25,
            15, 47, 7, 39, 13, 45, 5, 37,
            63, 31, 55, 23, 61, 29, 53, 21,
    };

    /** 全部可调参数（不可变，调整即换新实例）。 */
    public record Params(int cell, int levels, int ditherPct, int brightness,
                         int contrast, int saturationPct, boolean deepSpace) {
        public static final Params DEFAULT = new Params(1, 12, 100, 0, 0, 100, false);
    }

    private BayerPixelator() {
    }

    /**
     * @param src  源图 ARGB 像素（行主序）
     * @param sw   源宽
     * @param sh   源高
     * @return 640×360 ARGB
     */
    public static int[] pixelate(int[] src, int sw, int sh, Params p) {
        if (src == null || src.length != sw * sh || sw <= 0 || sh <= 0) {
            return new int[W * H];
        }
        int cell = Math.max(1, Math.min(6, p.cell()));
        int lv = Math.max(2, Math.min(16, p.levels()));
        double dither = Math.max(0, Math.min(100, p.ditherPct())) / 100.0;
        double bright = Math.max(-80, Math.min(80, p.brightness())) / 100.0;
        double contrast = 1.0 + Math.max(-60, Math.min(80, p.contrast())) / 100.0;
        double sat = Math.max(0, Math.min(300, p.saturationPct())) / 100.0;

        // cover 适配：源图缩放到恰好覆盖 640×360，居中裁切
        double scale = Math.max((double) W / sw, (double) H / sh);
        double ox = (W - sw * scale) / 2.0;
        double oy = (H - sh * scale) / 2.0;

        int[] out = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                // 块平均：cell>1 时一个块共享一次取样均值
                double sr = 0;
                double sg = 0;
                double sb = 0;
                int samples = 0;
                for (int dy = 0; dy < cell; dy += Math.max(1, cell / 2)) {
                    for (int dx = 0; dx < cell; dx += Math.max(1, cell / 2)) {
                        double px = (x + dx + 0.5 - ox) / scale;
                        double py = (y + dy + 0.5 - oy) / scale;
                        int sx = Math.max(0, Math.min(sw - 1, (int) px));
                        int sy = Math.max(0, Math.min(sh - 1, (int) py));
                        int argb = src[sy * sw + sx];
                        sr += ((argb >> 16) & 255) / 255.0;
                        sg += ((argb >> 8) & 255) / 255.0;
                        sb += (argb & 255) / 255.0;
                        samples++;
                    }
                }
                double r = sr / samples;
                double g = sg / samples;
                double b = sb / samples;

                // 亮度 / 对比
                r = (r + bright - 0.5) * contrast + 0.5;
                g = (g + bright - 0.5) * contrast + 0.5;
                b = (b + bright - 0.5) * contrast + 0.5;
                // 饱和
                double gray = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                r = gray + (r - gray) * sat;
                g = gray + (g - gray) * sat;
                b = gray + (b - gray) * sat;
                // 深空化：伽马压暗，暗部向冷色收
                if (p.deepSpace()) {
                    r = Math.pow(clamp01(r), 1.45);
                    g = Math.pow(clamp01(g), 1.5);
                    b = Math.pow(clamp01(b), 1.35) * 1.12 + 0.03;
                    double l = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                    if (l < 0.30) {
                        double k = (0.30 - l) / 0.30 * 0.55;
                        r += (0.040 - r) * k;
                        g += (0.040 - g) * k;
                        b += (0.110 - b) * k;
                    }
                }
                r = clamp01(r);
                g = clamp01(g);
                b = clamp01(b);

                // 拜耳网点量化：块对齐（同一块同一阈值，保住"大像素"的观感）
                double bayer = BAYER[((y / cell) & 7) * 8 + ((x / cell) & 7)] / 64.0 - 0.5;
                double dd = bayer * dither / lv;
                int ir = quantize(r + dd, lv);
                int ig = quantize(g + dd, lv);
                int ib = quantize(b + dd, lv);
                out[y * W + x] = 0xFF000000 | (ir << 16) | (ig << 8) | ib;
            }
        }
        return out;
    }

    /**
     * 同尺寸拜耳量化：给实时背景（320×180）用，不缩放、不裁切，只走"块平均 → 色彩调整 → 网点量化"。
     *
     * <p>与 {@link #pixelate} 的区别只有取样方式：这里一个输出像素对一个输入像素（块内取 cell×cell），
     * {@code pixelate} 则是先把源图 cover 适配到 640×360。</p>
     */
    public static int[] quantizeInPlace(int[] src, int w, int h, Params p) {
        if (src == null || w <= 0 || h <= 0 || src.length < w * h) {
            return src;
        }
        Params q = p == null ? Params.DEFAULT : p;
        int cell = Math.max(1, Math.min(6, q.cell()));
        int lv = Math.max(2, Math.min(16, q.levels()));
        double dither = Math.max(0, Math.min(100, q.ditherPct())) / 100.0;
        double bright = Math.max(-80, Math.min(80, q.brightness())) / 100.0;
        double contrast = 1.0 + Math.max(-60, Math.min(80, q.contrast())) / 100.0;
        double sat = Math.max(0, Math.min(300, q.saturationPct())) / 100.0;

        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double sr = 0;
                double sg = 0;
                double sb = 0;
                int samples = 0;
                for (int dy = 0; dy < cell; dy++) {
                    for (int dx = 0; dx < cell; dx++) {
                        int sx = Math.min(w - 1, x + dx);
                        int sy = Math.min(h - 1, y + dy);
                        int argb = src[sy * w + sx];
                        sr += ((argb >> 16) & 255) / 255.0;
                        sg += ((argb >> 8) & 255) / 255.0;
                        sb += (argb & 255) / 255.0;
                        samples++;
                    }
                }
                double r = sr / samples;
                double g = sg / samples;
                double b = sb / samples;

                r = (r + bright - 0.5) * contrast + 0.5;
                g = (g + bright - 0.5) * contrast + 0.5;
                b = (b + bright - 0.5) * contrast + 0.5;
                double gray = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                r = gray + (r - gray) * sat;
                g = gray + (g - gray) * sat;
                b = gray + (b - gray) * sat;

                double bayer = BAYER[((y / cell) & 7) * 8 + ((x / cell) & 7)] / 64.0 - 0.5;
                double dd = bayer * dither / lv;
                int ir = quantize(r + dd, lv);
                int ig = quantize(g + dd, lv);
                int ib = quantize(b + dd, lv);
                int alpha = (src[y * w + x] >>> 24) & 0xFF;
                out[y * w + x] = (alpha << 24) | (ir << 16) | (ig << 8) | ib;
            }
        }
        return out;
    }

    /** v(0..1) 量化到 lv 档。 */
    private static int quantize(double v, int lv) {
        int q = (int) Math.round(clamp01(v) * (lv - 1));
        return q * 255 / (lv - 1);
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
