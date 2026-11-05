package com.hysx.salvosequence.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 舰队战斗态：每艘船各算各的账，前排先挨打。
 *
 * <p>改造后玩家这边是<b>真舰队</b>：伤害不再全落在"主力舰"身上，
 * 而是从最靠前（y 最小）的那艘开始扣，扣穿了继续打到下一艘。
 * 这些断言守的就是这条对称规则——我方挨打和敌方挨打是同一套。</p>
 *
 * <p>数值全部从 {@link ShipCatalog} 现读，不写死：结构值是要调的旋钮，
 * 断言写死会在下一次配平里整片倒。</p>
 */
class FleetRosterTest {

    private static final Ship CORE = ShipCatalog.CORE_COMMAND;
    private static final Ship BELT = ShipCatalog.ARMOR_BELT;

    private static FleetUnit at(Ship ship, int x, int y) {
        return new FleetUnit(ship, 1, x, y);
    }

    /** 指挥舰在右侧前排，后面跟一艘装甲舰（x 更小 = 更靠后，且离得远不触发相邻加成）。 */
    private static FleetRoster roster() {
        return FleetRoster.of(Fleet.of("t", "试验舰队", "TEST", List.of(
                at(CORE, 6, 6),    // 前排：x6..8 y6..8（我方朝右，右侧一列在前）
                at(BELT, 0, 0)))); // 后排：x0..3 y0..3
    }

    @Test
    void everyShipStartsAtFullHpAndWithItsArmorAsBlock() {
        FleetRoster roster = roster();
        assertEquals(2, roster.aliveCount());
        assertEquals(CORE.hp() + BELT.hp(), roster.totalHp(), "指挥舰 + 装甲舰（没贴在一起，不加相邻结构值）");
        roster.refreshBlock();
        assertEquals(BELT.armor(), roster.byId(BELT.id()).block(), "护甲当护盾");
        assertEquals(CORE.armor(), roster.byId(CORE.id()).block(), "指挥舰的护盾就是它的护甲");
    }

    @Test
    void shieldWindowHalvesRegenForShipsHitLastTurn() {
        FleetRoster roster = roster();
        roster.refreshBlock();
        roster.soak(3, false, true);   // 后排装甲舰挨 3 点：盾 2 吃 2、结构 -1
        assertEquals(0, roster.byId(BELT.id()).block(), "先扣格挡");
        assertEquals(1, roster.refreshBlock(), "被打到的船回盾减半");
        assertEquals(1, roster.byId(BELT.id()).block(), "回盾 2 → 1");
        assertEquals(0, roster.refreshBlock(), "这回合没被打到");
        assertEquals(2, roster.byId(BELT.id()).block(), "下回合回盾恢复满额");
    }

    @Test
    void theFrontMostShipEatsTheDamageFirst() {
        FleetRoster roster = roster();
        roster.refreshBlock();
        int lost = roster.applyIncoming(5, false);
        assertEquals(5, lost, "5 点全落在前排（指挥舰没护盾）");
        assertEquals(CORE.hp() - 5, roster.byId(CORE.id()).hp());
        assertEquals(BELT.hp(), roster.byId(BELT.id()).hp(), "后排一点没掉");
    }

    @Test
    void armorSoaksDamageBeforeHpAndTheOverflowCarriesOn() {
        FleetRoster roster = roster();
        roster.refreshBlock();
        // 前排打沉，余量打到后排：后排先吃 2 点护盾，再吃结构值
        int lost = roster.applyIncoming(CORE.hp() + BELT.armor() + 10, false);
        assertEquals(CORE.hp() + 10, lost, "护盾不算结构值损失");
        assertEquals(0, roster.byId(CORE.id()).hp());
        assertEquals(BELT.hp() - 10, roster.byId(BELT.id()).hp());
        assertFalse(roster.byId(CORE.id()).alive());
    }

    @Test
    void aPinnedBackLineShotHitsTheBackFirst() {
        FleetRoster roster = roster();
        roster.refreshBlock();
        roster.applyIncoming(BELT.armor() + 3, true);
        assertEquals(CORE.hp(), roster.byId(CORE.id()).hp(), "前排没动");
        assertEquals(BELT.hp() - 3, roster.byId(BELT.id()).hp(), "后排吃了 3 点");
    }

    @Test
    void wipingOutTheWholeFleetIsReported() {
        FleetRoster roster = roster();
        roster.refreshBlock();
        roster.applyIncoming(999, false);
        assertTrue(roster.wiped(), "全沉了");
        assertEquals(0, roster.aliveCount());
        assertNotNull(roster.flag(), "旗舰读数在全灭时也不能是 null");
    }

    @Test
    void shieldsAndRepairsAreClamped() {
        FleetRoster roster = roster();
        roster.refreshBlock();
        roster.addBlock(CORE.id(), 4);
        assertEquals(CORE.armor() + 4, roster.byId(CORE.id()).block(), "盾是叠着加的");
        roster.addBlockToAll(1);
        assertEquals(BELT.armor() + 1, roster.byId(BELT.id()).block());
        roster.heal(CORE.id(), 99);
        assertEquals(CORE.hp(), roster.byId(CORE.id()).hp(), "修到上限为止");
    }

    @Test
    void aSnapshotCanBeCarriedIntoTheNextBattle() {
        FleetRoster roster = roster();
        roster.refreshBlock();
        roster.applyIncoming(5, false);
        Map<String, Integer> snapshot = roster.hpSnapshot();
        assertEquals(CORE.hp() - 5, snapshot.get(CORE.id()));
        FleetRoster next = FleetRoster.of(roster.fleet(), snapshot);
        assertEquals(CORE.hp() - 5, next.byId(CORE.id()).hp(), "上一场剩下的血带过去");
        assertEquals(CORE.hp(), next.byId(CORE.id()).maxHp());
    }

    @Test
    void aShipThatDiedStaysDeadAcrossBattles() {
        FleetRoster roster = roster();
        roster.refreshBlock();
        roster.applyIncoming(CORE.hp(), false);
        assertFalse(roster.byId(CORE.id()).alive());
        FleetRoster next = FleetRoster.of(roster.fleet(), roster.hpSnapshot());
        assertFalse(next.byId(CORE.id()).alive(), "0 血的船不会自己活过来");
        assertEquals(1, next.aliveCount());
    }

    @Test
    void cellsResolveToTheirShip() {
        FleetRoster roster = roster();
        assertEquals(CORE.id(), roster.at(7, 7).id());
        assertNull(roster.at(1, 9), "空格上没有船");
    }

    @Test
    void repositioningMovesTheShipAndKeepsItsDamage() {
        FleetRoster roster = roster();
        roster.refreshBlock();
        roster.applyIncoming(5, false);
        int hurt = roster.byId(CORE.id()).hp();

        assertTrue(roster.reposition(CORE.id(), 10, 10), "右下角是空的");
        assertEquals(10, roster.fleet().unit(CORE.id()).x(), "棋盘挪了");
        assertEquals(10, roster.fleet().unit(CORE.id()).y());
        assertEquals(hurt, roster.byId(CORE.id()).hp(), "挪一格不会回血");
        assertEquals(2, roster.aliveCount());
    }

    @Test
    void repositioningNeverStacksTwoShips() {
        FleetRoster roster = roster();
        assertFalse(roster.reposition(CORE.id(), 0, 0), "压着装甲舰的地方放不下");
        assertEquals(6, roster.fleet().unit(CORE.id()).x(), "放不下就原地不动");
    }

    @Test
    void theArmorBonusIsKeptOnceYouHaveIt() {
        FleetRoster roster = FleetRoster.of(Fleet.of("t", "试验舰队", "TEST", List.of(
                at(CORE, 0, 0),
                at(BELT, 4, 6))));
        assertTrue(roster.reposition(BELT.id(), 3, 0), "贴到指挥舰右边");
        int withBonus = roster.byId(CORE.id()).maxHp();
        assertTrue(withBonus > CORE.hp(), "装甲舰贴过来，指挥舰才拿得到 +2 结构值：" + withBonus);
        assertTrue(roster.reposition(BELT.id(), 8, 8), "再挪开");
        assertEquals(withBonus, roster.byId(CORE.id()).maxHp(),
                "战斗中的结构值上限只涨不跌——拖一下不能倒扣");
    }
}
