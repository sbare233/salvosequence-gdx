package com.hysx.salvosequence.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缩放策略回归测试。
 *
 * <p>这是整个像素观感的地基：只有"窗口正好是设计分辨率的整数倍"时，
 * 每个设计像素才落在 N x N 个设备像素上，配合 {@code setImageSmoothing(false)}
 * 得到硬边像素。一旦这里被改坏，画面会整体变模糊。</p>
 */
class PixelScaleTest {

    private static final double EPS = 1e-9;

    @Test
    void fullHdIsExactlyThreeTimes() {
        PixelScale scale = new PixelScale();
        scale.updateViewport(1920, 1080);

        assertEquals(3.0, scale.rawScale(), EPS);
        assertEquals(3.0, scale.effectiveScale(), EPS);
        assertTrue(scale.isIntegerScale(), "1920x1080 应当判定为 3 倍整数缩放");
        // 变换与居中偏移
        assertEquals(3.0, scale.effectiveScale(), EPS);
        assertEquals(0.0, scale.offsetX(), EPS);
        assertEquals(0.0, scale.offsetY(), EPS);
    }

    @Test
    void windowedDefaultIsTwoTimes() {
        PixelScale scale = new PixelScale();
        scale.updateViewport(1280, 720);

        assertTrue(scale.isIntegerScale());
        assertEquals(2.0, scale.effectiveScale(), EPS);
        assertEquals(0.0, scale.offsetX(), EPS);
    }

    @Test
    void windowWithDecorationsStillSnapsToIntegerScale() {
        PixelScale scale = new PixelScale();
        // 实测数据：1280x720 的窗口，减掉边框与标题栏后场景视口是 1264x681，
        // 直接算倍数是 1.89；纵向偏差 2.5% 落在容差内，应当仍然取 2 倍整数缩放，
        // 而不是退回浮点缩放让整屏像素错位
        scale.updateViewport(1264, 681);

        assertTrue(scale.isIntegerScale(), "1264x681 应吸附到 2 倍整数缩放");
        assertEquals(2.0, scale.effectiveScale(), EPS);
    }

    @Test
    void fullHdWindowWithDecorationsStillSnapsToThreeTimes() {
        PixelScale scale = new PixelScale();
        // 1920x1080 的窗口，实际视口 1904x1041（纵向偏差 3.6%）
        scale.updateViewport(1904, 1041);

        assertTrue(scale.isIntegerScale(), "1904x1041 应吸附到 3 倍整数缩放");
        assertEquals(3.0, scale.effectiveScale(), EPS);
        // 放大后的设计区比视口大一点：这部分会被裁掉，换取像素对齐
        assertTrue(640 * 3 > 1904 && 360 * 3 > 1041);
    }

    @Test
    void clearlyFractionalViewportFallsBack() {
        PixelScale scale = new PixelScale();
        // 1600x900 对应 2.5 倍，离任何整数倍都超过容差，必须退回浮点缩放
        scale.updateViewport(1600, 900);

        assertFalse(scale.isIntegerScale());
        assertEquals(2.5, scale.effectiveScale(), EPS);
        // 浮点缩放下图层必须仍然铺满窗口（允许 1px 以内的取整误差）
        double covered = PixelScale.DESIGN_WIDTH * scale.effectiveScale();
        assertTrue(Math.abs(covered - 1600) <= 1.0, "宽度未铺满: " + covered);
    }

    @Test
    void tinyWindowStillScalesBelowOne() {
        PixelScale scale = new PixelScale();
        scale.updateViewport(320, 180);

        assertFalse(scale.isIntegerScale());
        assertEquals(0.5, scale.effectiveScale(), EPS);
    }

    @Test
    void fourKIsSixTimes() {
        PixelScale scale = new PixelScale();
        scale.updateViewport(3840, 2160);

        assertTrue(scale.isIntegerScale());
        assertEquals(6.0, scale.effectiveScale(), EPS);
    }

    @Test
    void centeringOffsetsAreWholePixels() {
        PixelScale scale = new PixelScale();
        // 故意给一个稍高的窗口：整数倍 2 生效后应在竖直方向居中
        scale.updateViewport(1280, 760);

        assertEquals(2.0, scale.effectiveScale(), EPS);
        assertEquals(0.0, scale.offsetX(), EPS);
        assertEquals(20.0, scale.offsetY(), EPS);
    }

    @Test
    void textScaleIsIntegralForCrispGlyphs() {
        PixelScale scale = new PixelScale();
        scale.updateViewport(1920, 1080);
        assertEquals(3.0, scale.textScale(), EPS);

        scale.updateViewport(1600, 900);
        assertEquals(Math.rint(scale.rawScale()), scale.textScale(), EPS);
    }
}
