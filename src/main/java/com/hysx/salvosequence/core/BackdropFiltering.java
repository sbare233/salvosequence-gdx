package com.hysx.salvosequence.core;

import com.hysx.salvosequence.ui.VideoSettings;

/**
 * 「背景滤镜」的统一入口：按设置里的 {@link VideoSettings.BackdropFilter} 把一张源图
 * 处理成设计尺寸（{@link BayerPixelator#W}×{@link BayerPixelator#H}）。
 *
 * <p>两条路各管各的参数：拜耳用 {@link BayerPixelator.Params}（工坊面板 Q/A、W/S… 调的
 * 那一套），像素滤镜用 {@link PixelArtFilter} 自己的色阶/锐化/描边/抖动 + J 键调色板。
 * 实时 3D 背景走的是 {@code live/LiveBackdrops.post()} 的 320×180 同尺寸版，这里服务
 * 静态图（工坊预览、ENTER 导出、P 批量导出）。</p>
 */
public final class BackdropFiltering {

    private BackdropFiltering() {
    }

    /**
     * @param src    源图 ARGB
     * @param sw/sh  源图尺寸
     * @param bayer  拜耳参数（像素滤镜用不到，可传 null 走默认）
     * @param filter 用哪套滤镜
     * @return 640×360 ARGB
     */
    public static int[] render(int[] src, int sw, int sh,
                               BayerPixelator.Params bayer,
                               VideoSettings.BackdropFilter filter) {
        if (src == null || sw <= 0 || sh <= 0 || src.length < sw * sh) {
            return new int[BayerPixelator.W * BayerPixelator.H];
        }
        if (filter == VideoSettings.BackdropFilter.PIXEL) {
            return PixelArtFilter.apply(cover(src, sw, sh, BayerPixelator.W, BayerPixelator.H),
                    BayerPixelator.W, BayerPixelator.H);
        }
        return BayerPixelator.pixelate(src, sw, sh,
                bayer == null ? BayerPixelator.Params.DEFAULT : bayer);
    }

    /**
     * 面积平均（box）缩放到恰好铺满并居中裁切，口径与 {@link BayerPixelator#pixelate} 一致。
     *
     * <p>先按 alpha 预乘再平均，否则星云那种"亮色 + 低 alpha"的边缘会被平均成黑边。</p>
     */
    static int[] cover(int[] src, int sw, int sh, int tw, int th) {
        double scale = Math.max((double) tw / sw, (double) th / sh);
        double offX = (tw - sw * scale) / 2.0;
        double offY = (th - sh * scale) / 2.0;
        int[] out = new int[tw * th];
        for (int y = 0; y < th; y++) {
            double sy0 = (y - offY) / scale;
            double sy1 = (y + 1 - offY) / scale;
            int iy0 = clamp((int) Math.floor(sy0), 0, sh - 1);
            int iy1 = clamp((int) Math.ceil(sy1) - 1, iy0, sh - 1);
            for (int x = 0; x < tw; x++) {
                double sx0 = (x - offX) / scale;
                double sx1 = (x + 1 - offX) / scale;
                int ix0 = clamp((int) Math.floor(sx0), 0, sw - 1);
                int ix1 = clamp((int) Math.ceil(sx1) - 1, ix0, sw - 1);

                double ar = 0;
                double ag = 0;
                double ab = 0;
                double aa = 0;
                int n = 0;
                for (int sy = iy0; sy <= iy1; sy++) {
                    int row = sy * sw;
                    for (int sx = ix0; sx <= ix1; sx++) {
                        int argb = src[row + sx];
                        double a = ((argb >>> 24) & 0xFF) / 255.0;
                        ar += ((argb >>> 16) & 0xFF) * a;
                        ag += ((argb >>> 8) & 0xFF) * a;
                        ab += (argb & 0xFF) * a;
                        aa += a;
                        n++;
                    }
                }
                if (n == 0) {
                    out[y * tw + x] = 0;
                    continue;
                }
                double a = aa / n;
                if (a <= 0.0001) {
                    out[y * tw + x] = 0;
                    continue;
                }
                int r = clamp((int) Math.round(ar / n / a), 0, 255);
                int g = clamp((int) Math.round(ag / n / a), 0, 255);
                int b = clamp((int) Math.round(ab / n / a), 0, 255);
                out[y * tw + x] = (clamp((int) Math.round(a * 255), 0, 255) << 24)
                        | (r << 16) | (g << 8) | b;
            }
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    /** 导出文件名里的滤镜标记：拜耳沿用原来的 {@code -x1-l12}，像素用 {@code -px-s8}。 */
    public static String suffix(VideoSettings.BackdropFilter filter, BayerPixelator.Params bayer) {
        if (filter == VideoSettings.BackdropFilter.PIXEL) {
            PixelArtFilter.Params p = PixelArtFilter.params();
            return "-px-s" + p.steps();
        }
        BayerPixelator.Params q = bayer == null ? BayerPixelator.Params.DEFAULT : bayer;
        return "-x" + q.cell() + "-l" + q.levels();
    }
}
