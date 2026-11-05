package com.hysx.salvosequence.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hysx.salvosequence.fleet.FleetBoard;
import com.hysx.salvosequence.fleet.FleetUnit;
import com.hysx.salvosequence.fleet.ShipCatalog;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 敌方编队上棋盘。
 *
 * <p>改造前敌方只在 6 行"敌方带"里排队；现在敌方<b>铺满自己那张 16×16</b>，
 * 画法和我方一模一样。这里守四件事：都在棋盘内、两两不重叠、顺序与编队一致、
 * 塞不下要吵。</p>
 */
class EnemyFormationTest {

    @Test
    void everyRouteEntryFitsInsideTheBoard() {
        for (int node = 0; node < EnemyCatalog.routeLength(); node++) {
            List<Enemy> squad = EnemyCatalog.squad(node, node * 7919L + 3);
            List<FleetUnit> units = EnemyFormation.layout(squad);
            assertEquals(squad.size(), units.size(), "每艘敌舰都要落位");
            for (FleetUnit unit : units) {
                assertTrue(unit.x() >= 0 && unit.y() >= 0, "不能摆到棋盘左边/上边外面");
                assertTrue(unit.x() + unit.w() <= FleetBoard.SIZE, "第 " + node + " 关越界：" + unit.labelZh());
                assertTrue(unit.y() + unit.h() <= FleetBoard.SIZE, "第 " + node + " 关越界：" + unit.labelZh());
            }
        }
    }

    @Test
    void shipsNeverOverlapEachOther() {
        for (int node = 0; node < EnemyCatalog.routeLength(); node++) {
            List<FleetUnit> units = EnemyFormation.layout(EnemyCatalog.squad(node, node * 7919L + 3));
            for (int i = 0; i < units.size(); i++) {
                for (int j = i + 1; j < units.size(); j++) {
                    assertFalse(units.get(i).overlaps(units.get(j)),
                            "第 " + node + " 关两艘舰糊在一起了：" + units.get(i).labelZh()
                                    + " / " + units.get(j).labelZh());
                }
            }
        }
    }

    @Test
    void theLayoutKeepsTheSquadOrderSoTargetIndexesStillLineUp() {
        for (int node = 0; node < EnemyCatalog.routeLength(); node++) {
            List<Enemy> squad = EnemyCatalog.squad(node, node * 7919L + 3);
            List<FleetUnit> units = EnemyFormation.layout(squad);
            for (int i = 0; i < squad.size(); i++) {
                assertEquals(squad.get(i).w(), units.get(i).w(), "第 " + node + " 关第 " + i + " 艘的宽度对不上");
                assertEquals(squad.get(i).h(), units.get(i).h(), "第 " + node + " 关第 " + i + " 艘的高度对不上");
            }
        }
    }

    @Test
    void theLastBattleSpreadsAcrossTheBoardInsteadOfStacking() {
        List<Enemy> squad = EnemyCatalog.squad(EnemyCatalog.routeLength() - 1, 42L);
        List<FleetUnit> units = EnemyFormation.layout(squad);
        assertTrue(units.size() >= 3, "最后一关是三艘");
        int minX = FleetBoard.SIZE;
        int maxX = 0;
        for (FleetUnit unit : units) {
            minX = Math.min(minX, unit.x());
            maxX = Math.max(maxX, unit.x() + unit.w());
        }
        assertTrue(maxX - minX > FleetBoard.SIZE / 2, "三艘舰要横向铺开，不能挤在一角");
    }

    @Test
    void theFootprintFollowsTheShipImage() {
        for (Enemy enemy : EnemyCatalog.route()) {
            int[] footprint = EnemyFormation.footprintOf(enemy);
            assertEquals(enemy.w(), footprint[0]);
            assertEquals(enemy.h(), footprint[1]);
        }
        assertEquals(ShipCatalog.GUN_HEAVY.w(), EnemyCatalog.byId("foe.heavy").w(),
                "旗舰用的就是重型主炮那张舰图（5 格宽）");
    }

    @Test
    void everyRouteSquadUsesDistinctShipImages() {
        // 棋盘上按舰图 id 区分船（FleetUnit.id() 就是舰图 id），
        // 所以一关里不能出现两艘用同一张图的敌舰——这条就是那条约束的哨兵。
        for (int node = 0; node < EnemyCatalog.routeLength(); node++) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Enemy enemy : EnemyCatalog.squad(node, node * 7919L + 3)) {
                assertTrue(seen.add(enemy.image().id()),
                        "第 " + node + " 关有两艘敌舰共用舰图 " + enemy.image().id());
            }
        }
    }

    @Test
    void aSquadThatCannotBeLaidOutIsRejectedLoudly() {
        List<Enemy> crowd = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            crowd.add(EnemyCatalog.byId("foe.heavy").renamed("旗舰·" + i));
        }
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> EnemyFormation.layout(crowd), "40 艘旗舰塞不进 16×16，必须吵出来");
        assertTrue(error.getMessage().contains("摆不下"), "错误要说清楚摆不下：" + error.getMessage());
    }

    @Test
    void theAsciiPictureHasOneLinePerRow() {
        List<FleetUnit> units = EnemyFormation.layout(EnemyCatalog.squad(4, 7L));
        String ascii = EnemyFormation.ascii(units);
        assertEquals(FleetBoard.SIZE, ascii.lines().count(), "16 行");
        assertTrue(ascii.chars().filter(c -> c == '.').count() < FleetBoard.SIZE * FleetBoard.SIZE,
                "棋盘上应该有船，不是一片空：" + ascii);
    }
}
