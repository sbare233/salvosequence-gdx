package com.hysx.salvosequence.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.fleet.AppearanceDesign;
import com.hysx.salvosequence.fleet.Ship;
import com.hysx.salvosequence.fleet.ShipArtStore;
import com.hysx.salvosequence.fleet.ShipCatalog;

import java.nio.file.Files;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 设计器：改造后外观是<b>按船</b>画的，一张图对应一艘船，画布尺寸 = 那艘船的占地×18。
 *
 * <p>这里守四件事：选船会换画布尺寸、画笔落在对的像素上、撤销一格一格退、
 * 放大镜跟着指针且能收起。</p>
 */
class ShipDesignerScreenTest {

    @BeforeAll
    static void isolateSaves() throws Exception {
        ShipArtStore.useDirectory(Files.createTempDirectory("hull-designer-test"));
    }

    private static CanvasBuffer newBuffer() {
        return new CanvasBuffer(640, 360);
    }

    @Test
    void theCanvasIsSizedAfterTheShipOnTheBench() {
        ShipDesignerScreen screen = new ShipDesignerScreen();
        screen.selectShipForTest(ShipCatalog.GUN_MAIN.id());
        int[] canvas = screen.debugCanvas();
        assertEquals(ShipCatalog.GUN_MAIN.w() * AppearanceDesign.CELL, canvas[3], "画布宽 = 占地宽×18");
        assertEquals(ShipCatalog.GUN_MAIN.h() * AppearanceDesign.CELL, canvas[4], "画布高 = 占地高×18");

        screen.selectShipForTest(ShipCatalog.CORE_COMMAND.id());
        int[] command = screen.debugCanvas();
        assertEquals(ShipCatalog.CORE_COMMAND.w() * AppearanceDesign.CELL, command[3],
                "换一艘船就换一张画布");
    }

    @Test
    void zoomIsAnIntegerFactorThatFitsTheCanvas() {
        ShipDesignerScreen screen = new ShipDesignerScreen();
        for (Ship ship : ShipCatalog.all()) {
            screen.selectShipForTest(ship.id());
            int zoom = screen.zoom();
            assertTrue(zoom >= 1, "缩放至少 1 倍：" + ship.nameZh());
            int[] canvas = screen.debugCanvas();
            assertTrue(canvas[3] * zoom <= ShipDesignerScreen.CANVAS_SIDE,
                    ship.nameZh() + " 放大后不能溢出画布");
            assertTrue(canvas[4] * zoom <= ShipDesignerScreen.CANVAS_SIDE,
                    ship.nameZh() + " 放大后不能溢出画布");
        }
    }

    @Test
    void paintingSetsThePixelUnderTheBrushAndUndoStepsBack() {
        ShipDesignerScreen screen = new ShipDesignerScreen();
        screen.selectShipForTest(ShipCatalog.GUN_VULCAN.id());
        int before = screen.look().pixel(0, 4, 4);
        int brush = screen.brushArgb();

        screen.paintForTest(4, 4);
        int after = screen.look().pixel(0, 4, 4);
        assertNotEquals(before, after, "画了一笔颜色要变");
        assertEquals(brush, after, "落下的就是当前画笔色");
        assertEquals(1, screen.undoDepth(), "一笔 = 一步撤销");

        screen.paintForTest(5, 4);
        assertEquals(2, screen.undoDepth());

        screen.onKey("Z");
        assertEquals(before, screen.look().pixel(0, 5, 4), "撤销先退回最后一格");
        assertEquals(after, screen.look().pixel(0, 4, 4), "更早那一笔还在");
        assertEquals(1, screen.undoDepth());

        screen.onKey("Z");
        assertEquals(before, screen.look().pixel(0, 4, 4));
        assertEquals(0, screen.undoDepth());
    }

    @Test
    void paintingTheSameColorTwiceDoesNotPileUpUndoSteps() {
        ShipDesignerScreen screen = new ShipDesignerScreen();
        screen.selectShipForTest(ShipCatalog.CORE_COMMAND.id());
        screen.paintForTest(2, 2);
        int depth = screen.undoDepth();
        screen.paintForTest(2, 2);
        screen.paintForTest(2, 2);
        assertEquals(depth, screen.undoDepth(), "同一格刷同一个颜色不该再记一步");
    }

    @Test
    void theEraserPaintsTransparent() {
        ShipDesignerScreen screen = new ShipDesignerScreen();
        screen.selectShipForTest(ShipCatalog.ARMOR_BELT.id());
        screen.paintForTest(6, 2);
        assertNotEquals(AppearanceDesign.TRANSPARENT, screen.look().pixel(0, 6, 2));

        screen.onKey("DIGIT0");
        assertEquals(AppearanceDesign.TRANSPARENT, screen.brushArgb(), "按 0 换成橡皮");
        screen.paintForTest(6, 2);
        assertEquals(AppearanceDesign.TRANSPARENT, screen.look().pixel(0, 6, 2), "橡皮擦成透明");
    }

    @Test
    void theMagnifierFollowsThePointerAndClearsWhenItLeaves() {
        ShipDesignerScreen screen = new ShipDesignerScreen();
        screen.selectShipForTest(ShipCatalog.GUN_MAIN.id());

        int[] canvas = screen.debugCanvas();
        double insideX = canvas[0] + canvas[2] * 2.5;
        double insideY = canvas[1] + canvas[2] * 2.5;
        screen.debugHoverAt(insideX, insideY);
        int[] at = screen.debugHover();
        assertTrue(at[0] >= 0 && at[1] >= 0, "指针在画布上时放大镜要对准一个像素");
        assertTrue(at[0] < screen.look().width() && at[1] < screen.look().height());

        screen.debugHoverAt(-50, -50);
        int[] away = screen.debugHover();
        assertTrue(away[0] < 0 && away[1] < 0, "指针离开画布后放大镜要收起来");
    }

    @Test
    void savingThenResettingRoundTripsThroughTheStore() {
        ShipDesignerScreen screen = new ShipDesignerScreen();
        screen.selectShipForTest(ShipCatalog.TACTIC_EW.id());
        int brush = screen.brushArgb();
        screen.paintForTest(3, 3);

        screen.onKey("S");
        AppearanceDesign saved = ShipArtStore.loadLook(ShipCatalog.TACTIC_EW);
        assertEquals(brush, saved.pixel(0, 3, 3), "存盘的那张图上应该有这一笔");

        screen.onKey("R");
        assertNotEquals(brush, screen.look().pixel(0, 3, 3), "恢复出厂之后这一笔没了");
    }

    @Test
    void everyScreenDrawsWithoutCrashing() {
        ShipDesignerScreen screen = new ShipDesignerScreen();
        screen.selectShipForTest(ShipCatalog.GUN_HEAVY.id());
        CanvasBuffer buffer = newBuffer();
        screen.update(0.016);
        screen.draw(buffer);
        assertFalse(buffer.texts().isEmpty(), "设计器要画出文本");
        boolean mentionsShip = false;
        for (CanvasBuffer.TextRun run : buffer.texts()) {
            if (run.content().contains(ShipCatalog.GUN_HEAVY.nameZh())) {
                mentionsShip = true;
                break;
            }
        }
        assertTrue(mentionsShip, "顶栏要写着现在画的是哪艘船");
    }
}
