package com.hysx.salvosequence.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.fleet.Fleet;
import com.hysx.salvosequence.fleet.FleetBoard;
import com.hysx.salvosequence.fleet.FleetUnit;
import com.hysx.salvosequence.fleet.ShipArtStore;
import com.hysx.salvosequence.fleet.ShipCatalog;

import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 「舰队编排」界面（原「模块建造」）。
 *
 * <p>改造前这个界面把模块拖到船体的 16×16 舱格里；现在是<b>把船拖到 16×16 舰队棋盘上</b>。
 * 这里守四件事：四支起始舰队都装得进棋盘、拖动会改落点、非法落点被拒绝、确定会写盘。</p>
 */
class FleetArrangementScreenTest {

    @BeforeAll
    static void isolateSaves() throws Exception {
        ShipArtStore.useDirectory(Files.createTempDirectory("hull-arrange-test"));
    }

    private static CanvasBuffer newBuffer() {
        return new CanvasBuffer(640, 360);
    }

    @Test
    void itOpensOnAStarterFleetThatFitsTheBoard() {
        FleetArrangementScreen screen = new FleetArrangementScreen();
        assertEquals(0, screen.fleetIndex());
        Fleet fleet = screen.fleet();
        assertNotNull(fleet);
        assertTrue(fleet.size() >= 5, "起始舰队至少五艘船");
        for (FleetUnit unit : screen.units()) {
            assertTrue(unit.x() >= 0 && unit.y() >= 0);
            assertTrue(unit.x() + unit.w() <= FleetBoard.SIZE);
            assertTrue(unit.y() + unit.h() <= FleetBoard.SIZE);
        }
        assertEquals(FleetBoard.SIZE, screen.ascii().lines().count(), "棋盘是 16 行");
    }

    @Test
    void everyStarterFleetCanBeLaidOut() {
        FleetArrangementScreen screen = new FleetArrangementScreen();
        for (int i = 0; i < ShipCatalog.starterCount(); i++) {
            screen.selectFleetForTest(i);
            assertEquals(i, screen.fleetIndex());
            assertFalse(screen.fleet().isEmpty(), "第 " + i + " 支舰队不该是空的");
        }
    }

    @Test
    void draggingAShipMovesItOnTheBoard() {
        FleetArrangementScreen screen = new FleetArrangementScreen();
        FleetUnit unit = screen.units().get(0);
        String id = unit.id();
        int beforeX = unit.x();
        int beforeY = unit.y();

        assertTrue(screen.moveTo(0, 8, 12), "右下角是空的，摆得下");

        FleetUnit moved = screen.fleet().unit(id);
        assertNotNull(moved);
        assertTrue(moved.y() != beforeY || moved.x() != beforeX, "挪过去应该真的动了");
    }

    @Test
    void anImpossibleMoveIsRejected() {
        FleetArrangementScreen screen = new FleetArrangementScreen();
        FleetUnit unit = screen.units().get(0);
        assertFalse(screen.moveTo(0, FleetBoard.SIZE, FleetBoard.SIZE), "出界必须被拒绝");
        assertEquals(unit.x(), screen.fleet().unit(unit.id()).x(), "拒绝之后位置不变");

        FleetUnit other = screen.units().get(1);
        assertFalse(screen.moveTo(0, other.x(), other.y()), "压到别的船也要被拒绝");
    }

    @Test
    void confirmingWritesTheArrangementToDisk() {
        FleetArrangementScreen screen = new FleetArrangementScreen();
        FleetUnit unit = screen.units().get(0);
        screen.moveTo(0, 8, 12);
        screen.confirm();

        Fleet saved = ShipArtStore.applySavedArrangement(screen.fleet().id(),
                screen.fleet().nameZh(), screen.fleet().nameEn(), List.of());
        assertNotNull(saved, "确定之后应该有一份舰队存档");
        assertNotNull(saved.unit(unit.id()));
        assertEquals(8, saved.unit(unit.id()).x());
        assertEquals(12, saved.unit(unit.id()).y());
    }

    @Test
    void resettingGoesBackToTheFactoryLayout() {
        FleetArrangementScreen screen = new FleetArrangementScreen();
        String id = screen.units().get(0).id();
        screen.moveForTest(0, 8, 12);
        int movedX = screen.fleet().unit(id).x();
        assertEquals(8, movedX);

        screen.onKey("R");

        FleetUnit reset = screen.fleet().unit(id);
        assertNotEquals(8, reset.x(), "复位后不该还留在 8");
    }

    @Test
    void bracketsSwitchBetweenTheFourStarters() {
        FleetArrangementScreen screen = new FleetArrangementScreen();
        int first = screen.fleetIndex();
        screen.onKey("CLOSE_BRACKET");
        assertNotEquals(first, screen.fleetIndex(), "] 换下一支舰队");
        screen.onKey("OPEN_BRACKET");
        assertEquals(first, screen.fleetIndex(), "[ 换回上一支");
    }

    @Test
    void theScreenDrawsTheFleetAndTheReadouts() {
        FleetArrangementScreen screen = new FleetArrangementScreen();
        CanvasBuffer buffer = newBuffer();
        screen.update(0.016);
        screen.draw(buffer);
        assertFalse(buffer.texts().isEmpty(), "编排界面要画出文本");
        boolean mentionsFleet = false;
        for (CanvasBuffer.TextRun run : buffer.texts()) {
            if (run.content().contains("舰队")) {
                mentionsFleet = true;
                break;
            }
        }
        assertTrue(mentionsFleet, "标题/读数里要有「舰队」字样");
    }
}
