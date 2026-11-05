package com.hysx.salvosequence.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 拜耳像素化器：尺寸/确定性/色阶量化/网点抖动/深空化。 */
class BayerPixelatorTest {

    private static final BayerPixelator.Params DEF = BayerPixelator.Params.DEFAULT;

    private static int[] flat(int w, int h, int argb) {
        int[] out = new int[w * h];
        Arrays.fill(out, argb);
        return out;
    }

    private static int[] gradient(int w, int h) {
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            int v = y * 255 / h;
            Arrays.fill(out, y * w, (y + 1) * w, 0xFF000000 | (v << 16) | (v << 8) | v);
        }
        return out;
    }

    @Test
    void outputIsFixedSizeAndOpaque() {
        int[] out = BayerPixelator.pixelate(flat(64, 64, 0xFF808080), 64, 64, DEF);
        assertEquals(BayerPixelator.W * BayerPixelator.H, out.length);
        for (int i = 0; i < out.length; i += 997) {
            assertEquals(0xFF000000, out[i] & 0xFF000000, "背景必须不透明");
        }
    }

    @Test
    void deterministicForSameParams() {
        int[] a = BayerPixelator.pixelate(gradient(640, 360), 640, 360, DEF);
        int[] b = BayerPixelator.pixelate(gradient(640, 360), 640, 360, DEF);
        assertArrayEquals(a, b);
    }

    @Test
    void noDitherKeepsFlatInputFlat() {
        int[] out = BayerPixelator.pixelate(flat(64, 64, 0xFF808080), 64, 64,
                new BayerPixelator.Params(1, 4, 0, 0, 0, 100, false));
        Set<Integer> colors = new HashSet<>();
        for (int v : out) {
            colors.add(v & 0xFFFFFF);
        }
        assertEquals(1, colors.size(), "无抖动 + 纯色输入 = 纯色输出");
    }

    @Test
    void ditherMixesTwoLevelsOnGradient() {
        // 黑白渐变 + 2 色阶 + 满抖动：输出只应有两档，且两档都出现（网点渐变）
        int[] out = BayerPixelator.pixelate(gradient(640, 360), 640, 360,
                new BayerPixelator.Params(1, 2, 100, 0, 0, 100, false));
        Set<Integer> colors = new HashSet<>();
        for (int v : out) {
            colors.add(v & 0xFFFFFF);
        }
        assertEquals(2, colors.size(), "2 色阶满抖动：黑白网点，没有中间色");
    }

    @Test
    void deepSpaceDarkensAndTints() {
        int[] plain = BayerPixelator.pixelate(gradient(640, 360), 640, 360,
                new BayerPixelator.Params(1, 8, 0, 0, 0, 100, false));
        int[] deep = BayerPixelator.pixelate(gradient(640, 360), 640, 360,
                new BayerPixelator.Params(1, 8, 0, 0, 0, 100, true));
        long sumPlain = 0;
        long sumDeep = 0;
        for (int i = 0; i < plain.length; i++) {
            sumPlain += plain[i] & 0xFFFFFF;
            sumDeep += deep[i] & 0xFFFFFF;
        }
        assertTrue(sumDeep < sumPlain, "深空化要压暗整图");
    }

    @Test
    void chunkyCellMakesUniformBlocks() {
        // cell=4：一个 4x4 块内所有像素同色（块平均 + 块对齐的拜耳阈值）
        int[] out = BayerPixelator.pixelate(gradient(640, 360), 640, 360,
                new BayerPixelator.Params(4, 12, 0, 0, 0, 100, false));
        int base = out[5 * BayerPixelator.W + 5];
        for (int dy = 4; dy < 8; dy++) {
            for (int dx = 4; dx < 8; dx++) {
                assertEquals(base, out[dy * BayerPixelator.W + dx], "块内必须同色");
            }
        }
    }
}
