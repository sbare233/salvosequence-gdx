package com.hysx.salvosequence.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** 40 张主题背景：确定性、非空、动态层不炸。 */
class BackdropTest {

    @Test
    void allFortyRenderDeterministicAndDistinct() {
        for (int theme = 0; theme < Backdrops.THEMES; theme++) {
            for (int variant = 0; variant < Backdrops.VARIANTS; variant++) {
                int[] a = Backdrops.staticLayer(theme, variant, 42L);
                int[] b = Backdrops.staticLayer(theme, variant, 42L);
                assertArrayEquals(a, b, "主题 " + theme + " 变体 " + variant + " 必须确定");
                long distinct = IntStream.of(a).distinct().count();
                assertTrue(distinct >= 8, "主题 " + theme + " 变体 " + variant
                        + " 只有 " + distinct + " 种颜色，太空了");
            }
        }
    }

    @Test
    void variantsDifferWithinTheme() {
        for (int theme = 0; theme < Backdrops.THEMES; theme++) {
            int[] v0 = Backdrops.staticLayer(theme, 0, 42L);
            int[] v1 = Backdrops.staticLayer(theme, 1, 42L);
            assertFalse(Arrays.equals(v0, v1), "同主题变体之间要有差异");
        }
    }

    @Test
    void animateLayersNeverThrow() {
        PixelArt art = new PixelArt(Backdrops.W, Backdrops.H);
        for (int theme = 0; theme < Backdrops.THEMES; theme++) {
            final int t = theme;
            for (int variant = 0; variant < Backdrops.VARIANTS; variant++) {
                final int v = variant;
                assertDoesNotThrow(() -> Backdrops.animate(art, t, v, 7L, 1.7));
            }
        }
    }
}
