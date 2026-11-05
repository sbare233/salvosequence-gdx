package com.hysx.salvosequence.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 舰队棋盘：落位、相邻、承伤顺序。
 *
 * <p>本次改造取消了"分区"——船不再受槽位约束，随便摆。那么棋盘规则就只剩三条硬约束：
 * <b>不出界、不重叠、同一艘不能出现两次</b>。这三条加上"相邻"判定，
 * 撑起了整个舰队玩法（贴装甲加齐射、贴指挥加结构值）。</p>
 */
class FleetBoardTest {

    private static FleetUnit unit(Ship ship, int x, int y) {
        return new FleetUnit(ship, 1, x, y);
    }

    @Test
    void aPlacedShipCoversEveryCellOfItsFootprint() {
        FleetBoard board = FleetBoard.of(List.of(unit(ShipCatalog.GUN_MAIN, 2, 3)));
        assertNotNull(board.at(2, 3), "左上角要算里面");
        assertNotNull(board.at(5, 5), "右下角也要算里面");
        assertNull(board.at(6, 3), "4x3 右边一格应该是空的");
        assertNull(board.at(2, 6), "4x3 下面一格应该是空的");
        assertEquals(12, ShipCatalog.GUN_MAIN.cells(), "占地面积 = w*h");
    }

    @Test
    void shipsMayNotOverlap() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FleetBoard.of(List.of(
                        unit(ShipCatalog.GUN_MAIN, 0, 0),
                        unit(ShipCatalog.GUN_VULCAN, 3, 2))));
        assertTrue(error.getMessage().contains("压"), "报错要说清楚是压在一起：" + error.getMessage());
    }

    @Test
    void aShipThatHangsOffTheBoardIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FleetBoard.of(List.of(unit(ShipCatalog.GUN_MAIN, 14, 0))));
        assertTrue(error.getMessage().contains("16"), "报错要提到棋盘尺寸：" + error.getMessage());
    }

    @Test
    void theSameShipCannotBeFieldedTwice() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FleetBoard.of(List.of(
                        unit(ShipCatalog.GUN_VULCAN, 0, 0),
                        unit(ShipCatalog.GUN_VULCAN, 4, 0))));
        assertTrue(error.getMessage().contains("两次"), "报错要说清楚是重复：" + error.getMessage());
    }

    @Test
    void noPartitionsLeft_anyShipMaySitAnywhere() {
        // 旧版：主炮只能进主炮区、装甲只能进装甲区。现在只剩"能不能放下"。
        FleetBoard board = FleetBoard.of(List.of(
                unit(ShipCatalog.ARMOR_DEFLECT, 0, 0),
                unit(ShipCatalog.GUN_HEAVY, 6, 0),
                unit(ShipCatalog.TACTIC_FIRE, 12, 0)));
        assertEquals(3, board.units().size());
        assertEquals(ShipCatalog.ARMOR_DEFLECT, board.at(0, 0).ship());
        assertEquals(ShipCatalog.GUN_HEAVY, board.at(6, 0).ship());
    }

    @Test
    void adjacencyMeansTouchingNotGap() {
        FleetUnit a = unit(ShipCatalog.GUN_VULCAN, 0, 0);          // x 0..1, y 0..1
        FleetUnit touching = unit(ShipCatalog.ARMOR_BELT, 2, 0);   // 正好贴住右边缘
        FleetUnit withGap = unit(ShipCatalog.ARMOR_BELT, 3, 0);    // 中间空一格
        assertTrue(FleetBoard.adjacent(a, touching), "贴边要算相邻");
        assertFalse(FleetBoard.adjacent(a, withGap), "隔一格就不算相邻了");
    }

    @Test
    void soakOrderStartsAtTheRightColumn() {
        FleetBoard board = FleetBoard.of(List.of(
                unit(ShipCatalog.GUN_VULCAN, 0, 0),
                unit(ShipCatalog.CORE_COMMAND, 8, 8)));
        List<FleetUnit> order = board.soakOrder();
        assertEquals(ShipCatalog.CORE_COMMAND, order.get(0).ship(), "x 大的先挨打（我方朝右）");
        assertEquals(ShipCatalog.GUN_VULCAN, order.get(1).ship());
    }

    @Test
    void aMoveCanBeRepresentedWithoutMutatingTheOldBoard() {
        FleetBoard board = FleetBoard.of(List.of(unit(ShipCatalog.GUN_VULCAN, 0, 0)));
        FleetBoard moved = board.moved(ShipCatalog.GUN_VULCAN.id(), 4, 4);
        assertNotNull(moved);
        assertEquals(0, board.at(0, 0).x(), "原棋盘不变");
        assertEquals(4, moved.at(4, 4).x(), "新棋盘上已经挪过去了");
        assertNull(board.moved(ShipCatalog.GUN_VULCAN.id(), 15, 15), "挪出界要返回 null");
    }

    @Test
    void freeCellsReportTheEmptySpace() {
        FleetBoard empty = FleetBoard.empty();
        assertEquals(FleetBoard.SIZE * FleetBoard.SIZE, empty.freeCells());
        FleetBoard one = FleetBoard.of(List.of(unit(ShipCatalog.GUN_MAIN, 0, 0)));
        assertEquals(FleetBoard.SIZE * FleetBoard.SIZE - 12, one.freeCells(), "主炮舰 4x3 吃掉 12 格");
    }

    @Test
    void removingAShipGivesTheCellsBack() {
        FleetBoard board = FleetBoard.of(List.of(
                unit(ShipCatalog.GUN_MAIN, 0, 0),
                unit(ShipCatalog.GUN_VULCAN, 5, 0)));
        FleetBoard after = board.without(ShipCatalog.GUN_MAIN.id());
        assertEquals(1, after.units().size());
        assertTrue(after.canPlace(unit(ShipCatalog.GUN_MAIN, 0, 0), 0, 0), "腾出来的地方能重新放");
    }
}
