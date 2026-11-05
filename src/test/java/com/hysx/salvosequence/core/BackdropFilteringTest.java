package com.hysx.salvosequence.core;

import com.hysx.salvosequence.ui.VideoSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「背景滤镜」的公共入口：拜耳 / 像素两条路、cover 适配口径。
 */
class BackdropFilteringTest {

    @AfterEach
    void resetFilter() {
        PixelArtFilter.reset();
        // 调色板是静态状态，测试之间必须回到"原色"
        for (int i = 0; i < 4 && PixelArtFilter.mode() != 0; i++) {
            PixelArtFilter.cycleMode(1);
        }
    }

    @Test
    void bayerPathIsExactlyTheOldPixelator() {
        int[] src = gradient(96, 54);
        BayerPixelator.Params p = new BayerPixelator.Params(2, 6, 70, 0, 10, 110, true);

        int[] viaFiltering = BackdropFiltering.render(src, 96, 54, p,
                VideoSettings.BackdropFilter.BAYER);
        int[] direct = BayerPixelator.pixelate(src, 96, 54, p);

        assertArrayEquals(direct, viaFiltering);
        assertEquals(BayerPixelator.W * BayerPixelator.H, viaFiltering.length);
    }

    @Test
    void pixelPathIgnoresTheBayerParamsAndValidatesItsOwn() {
        int[] src = gradient(64, 64);
        BayerPixelator.Params coarse = new BayerPixelator.Params(4, 3, 0, 40, 40, 200, true);
        BayerPixelator.Params fine = new BayerPixelator.Params(1, 12, 0, -40, -40, 60, false);

        int[] a = BackdropFiltering.render(src, 64, 64, coarse, VideoSettings.BackdropFilter.PIXEL);
        int[] b = BackdropFiltering.render(src, 64, 64, fine, VideoSettings.BackdropFilter.PIXEL);

        // 像素滤镜不读拜耳的像素块/色阶/亮度那一套，参数换了应该一模一样
        assertArrayEquals(a, b);
        assertEquals(BayerPixelator.W * BayerPixelator.H, a.length);

        // 但它自己的色阶要生效
        PixelArtFilter.setParams(new PixelArtFilter.Params(3, 0, 0f, 0.75f, 0f, 0f, 1f));
        int[] few = BackdropFiltering.render(src, 64, 64, coarse, VideoSettings.BackdropFilter.PIXEL);
        PixelArtFilter.setParams(new PixelArtFilter.Params(16, 0, 0f, 0.75f, 0f, 0f, 1f));
        int[] many = BackdropFiltering.render(src, 64, 64, coarse, VideoSettings.BackdropFilter.PIXEL);
        assertNotEquals(distinctColors(few), distinctColors(many));
        assertTrue(distinctColors(few) < distinctColors(many));
    }

    @Test
    void coverFillsTheFrameWithoutBlackEdges() {
        // 左右两半：左红右蓝，4:1 的源图
        int[] src = new int[80 * 20];
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 80; x++) {
                src[y * 80 + x] = x < 40 ? 0xFFFF0000 : 0xFF0000FF;
            }
        }
        int[] out = BackdropFiltering.cover(src, 80, 20, BayerPixelator.W, BayerPixelator.H);

        assertEquals(BayerPixelator.W * BayerPixelator.H, out.length);
        int left = out[(BayerPixelator.H / 2) * BayerPixelator.W + 8];
        int right = out[(BayerPixelator.H / 2) * BayerPixelator.W + BayerPixelator.W - 8];
        assertTrue(((left >>> 16) & 0xFF) > 200, "左边该是红的");
        assertTrue((right & 0xFF) > 200, "右边该是蓝的");
        for (int x : new int[]{0, BayerPixelator.W - 1}) {
            int px = out[(BayerPixelator.H / 2) * BayerPixelator.W + x];
            assertEquals(0xFF, (px >>> 24) & 0xFF, "不应出现透明/黑边");
        }
    }

    @Test
    void suffixMarksWhichFilterProducedTheFile() {
        assertEquals("-x2-l6", BackdropFiltering.suffix(VideoSettings.BackdropFilter.BAYER,
                new BayerPixelator.Params(2, 6, 70, 0, 0, 100, false)));
        PixelArtFilter.setParams(new PixelArtFilter.Params(10, 0, 0.25f, 0.75f, 0.22f, 0.35f, 1f));
        assertEquals("-px-s10", BackdropFiltering.suffix(VideoSettings.BackdropFilter.PIXEL, null));
    }

    private static int[] gradient(int w, int h) {
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = x * 255 / Math.max(1, w - 1);
                int g = y * 255 / Math.max(1, h - 1);
                int b = 128;
                out[y * w + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return out;
    }

    private static int distinctColors(int[] argb) {
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        for (int v : argb) {
            seen.add(v & 0xFFFFFF);
        }
        return seen.size();
    }
}
