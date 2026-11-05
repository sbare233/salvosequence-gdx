package com.hysx.salvosequence.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 船的外形图：一船一张，尺寸跟着船走。
 *
 * <p>这是本次改造里"外观绑到船、不绑到船体"的落点：旧版一张 288×288 的船体涂装，
 * 现在每艘船（原模块）各有自己的画布，尺寸 = 占地格数 × {@link AppearanceDesign#CELL}。
 * 只要尺寸对得上，绘制就是 1:1——不会因为缩放糊掉。</p>
 */
class AppearanceDesignTest {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    @Test
    void aShipsCanvasIsItsFootprintInAppearancePixels() {
        Ship gun = ShipCatalog.GUN_MAIN; // 4x3
        assertEquals(4 * AppearanceDesign.CELL, AppearanceDesign.widthOf(gun));
        assertEquals(3 * AppearanceDesign.CELL, AppearanceDesign.heightOf(gun));
        assertEquals(1, ShipCatalog.GUN_AXIAL_MUZZLE.w(), "炮口舰只有 1×2");
        assertEquals(AppearanceDesign.CELL, AppearanceDesign.widthOf(ShipCatalog.GUN_AXIAL_MUZZLE),
                "1 格 = 16 像素");
    }

    @Test
    void builtinArtFitsItsOwnCanvasAndIsNotBlank() {
        for (Ship ship : ShipCatalog.all()) {
            AppearanceDesign look = AppearanceDesign.builtin(ship);
            assertEquals(AppearanceDesign.widthOf(ship), look.width(), ship.id());
            assertEquals(AppearanceDesign.heightOf(ship), look.height(), ship.id());
            assertEquals(ship.id(), look.shipId());
            assertFalse(look.isBlank(), "出厂外观不该是空的：" + ship.id());
            assertEquals(4, look.frameCount(), "出厂 4 帧动画");
        }
    }

    @Test
    void paintingIsImmutableAndPixelReadable() {
        AppearanceDesign look = AppearanceDesign.builtin(ShipCatalog.GUN_VULCAN);
        AppearanceDesign painted = look.withPixel(0, 1, 1, RED);
        assertEquals(RED, painted.pixel(0, 1, 1));
        assertTrue(look.pixel(0, 1, 1) != RED, "原图不能被改");
        assertEquals(BLUE, look.withPixel(0, 1, 1, BLUE).pixel(0, 1, 1));
    }

    @Test
    void clearingGivesABlankCanvasOfTheRightSize() {
        AppearanceDesign blank = AppearanceDesign.builtin(ShipCatalog.ARMOR_DEFLECT).cleared();
        assertTrue(blank.isBlank());
        assertEquals(5 * AppearanceDesign.CELL, blank.width());
        assertEquals(5 * AppearanceDesign.CELL, blank.height());
    }

    @Test
    void serializeAndParseRoundTripPixels() {
        Ship ship = ShipCatalog.GUN_MISSILE; // 3x2
        AppearanceDesign look = AppearanceDesign.builtin(ship)
                .withPixel(0, 0, 0, RED)
                .withPixel(1, 3, 2, BLUE)
                .withFps(7);
        String text = look.serialize();
        assertTrue(text.startsWith(AppearanceDesign.HEADER),
                "要有版本头：" + text.lines().findFirst().orElse("?"));

        AppearanceDesign back = AppearanceDesign.parse(text, ship);
        assertEquals(look.width(), back.width());
        assertEquals(look.height(), back.height());
        assertEquals(7, back.fps());
        assertEquals(look.frameCount(), back.frameCount());
        assertEquals(RED, back.pixel(0, 0, 0));
        assertEquals(BLUE, back.pixel(1, 3, 2));
    }

    @Test
    void aLookForTheWrongShipIsRejected() {
        AppearanceDesign big = AppearanceDesign.builtin(ShipCatalog.GUN_HEAVY); // 5x5
        String text = big.serialize();
        // 拿 5x5 的图去装 2x2 的机炮舰：必须报错，而不是悄悄缩放
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AppearanceDesign.parse(text, ShipCatalog.GUN_VULCAN));
        assertTrue(error.getMessage().contains("2"),
                "报错要说清楚该是多少：" + error.getMessage());
    }

    @Test
    void frameCountAndFpsAreClamped() {
        AppearanceDesign look = AppearanceDesign.builtin(ShipCatalog.CORE_COMMAND);
        assertEquals(AppearanceDesign.MAX_FRAMES, look.withFrameCount(99).frameCount());
        assertEquals(1, look.withFrameCount(0).frameCount());
        assertEquals(AppearanceDesign.MAX_FPS, look.withFps(99).fps());
        assertEquals(AppearanceDesign.MIN_FPS, look.withFps(0).fps());
    }

    @Test
    void anEmptyFrameIsTransparentAndReadsBackAsZero() {
        Ship ship = ShipCatalog.TACTIC_EW;
        int[] frame = AppearanceDesign.blank(ship);
        assertEquals(AppearanceDesign.widthOf(ship) * AppearanceDesign.heightOf(ship), frame.length);
        for (int argb : frame) {
            assertEquals(AppearanceDesign.TRANSPARENT, argb);
        }
        AppearanceDesign blank = AppearanceDesign.of(ship, "试验", List.of(frame), 4);
        assertEquals(0, blank.pixel(0, 0, 0));
        assertTrue(blank.isBlank());
    }

    @Test
    void shadeDarkensWithoutTouchingAlpha() {
        int shaded = AppearanceDesign.shade(0xFFFFFFFF, 0.5);
        assertEquals(0xFF, (shaded >>> 24) & 0xFF, "alpha 不变");
        assertTrue(((shaded >> 16) & 0xFF) < 0xFF, "颜色变暗");
        assertNotNull(AppearanceDesign.of("x", "空", 16, 16, List.of(new int[16 * 16]), 1));
    }
}
