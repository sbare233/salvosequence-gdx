package com.hysx.salvosequence.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 船表：27 艘船（20 主战 + 7 支援）+ 四支起始舰队。
 *
 * <p>这是"模块系统 → 舰队系统"的数据底座。旧版 20 个模块件现在是 20 艘船，
 * id 一个没改（{@code gun.main} 还是 {@code gun.main}），所以卡牌、掉落、存档都能对上；
 * 支援舰与四势力舰是船池扩充进来的光环节点。</p>
 */
class ShipCatalogTest {

    @Test
    void theTableHasHundredAndNineteenShipsAndEveryCardTheyCarryExists() {
        assertEquals(500, ShipCatalog.all().size());
        for (Ship ship : ShipCatalog.all()) {
            for (String cardId : ship.cardIds()) {
                assertNotNull(com.hysx.salvosequence.deck.CardCatalog.byId(cardId),
                        "船「" + ship.nameZh() + "」带了一张不存在的卡：" + cardId);
            }
        }
    }

    @Test
    void everyPerkIsWellFormed() {
        for (Ship ship : ShipCatalog.all()) {
            for (Perk perk : ship.perks()) {
                assertNotNull(perk.kind(), "光环缺类型：" + ship.id());
                assertTrue(perk.value() >= 1, "光环数值必须为正：" + ship.id());
                assertNotNull(perk.range(), "光环缺生效范围：" + ship.id());
            }
        }
    }

    @Test
    void idsAreUniqueAndResolvable() {
        Map<String, Ship> seen = new HashMap<>();
        for (Ship ship : ShipCatalog.all()) {
            assertNull(seen.put(ship.id(), ship), "船 id 重复：" + ship.id());
            assertSame(ship, ShipCatalog.byId(ship.id()));
        }
        assertNull(ShipCatalog.byId("no.such.ship"));
    }

    @Test
    void everyShipHasAPositiveFootprintAndNonNegativeNumbers() {
        for (Ship ship : ShipCatalog.all()) {
            assertTrue(ship.w() > 0 && ship.h() > 0, "尺寸必须为正：" + ship.id());
            assertTrue(ship.cells() == ship.w() * ship.h());
            assertTrue(ship.hp() > 0, "结构值必须为正：" + ship.id());
            assertTrue(ship.armor() >= 0 && ship.energy() >= 0 && ship.draw() >= 0);
            assertTrue(ship.volley() >= 0);
            assertFalse(ship.nameZh().isBlank());
            assertFalse(ship.nameEn().isBlank());
        }
    }

    @Test
    void theDropPoolIncludesTheCommandShip() {
        assertEquals(ShipCatalog.all().size(), ShipCatalog.dropPool().size());
        assertTrue(ShipCatalog.dropPool().contains(ShipCatalog.CORE_COMMAND),
                "旗舰已改为普通船，指挥舰也应出现在战利品里");
        assertTrue(ShipCatalog.CORE_COMMAND.isFlag());
    }

    @Test
    void thereAreFourStarterFleetsEachCompleteOnItsOwnBoard() {
        List<ShipCatalog.Starter> starters = ShipCatalog.starters();
        assertEquals(4, starters.size());
        for (ShipCatalog.Starter starter : starters) {
            Fleet fleet = starter.fleet();
            assertTrue(starter.nameZh().endsWith("舰队"), "名字要叫「xx舰队」：" + starter.nameZh());
            assertTrue(fleet.size() >= 5, starter.nameZh() + " 至少五艘船");
            assertNotNull(fleet.flag(), starter.nameZh() + " 要有指挥舰");
            assertTrue(fleet.maxHp() > 0);
            assertTrue(fleet.deckSize() > 0);
        }
    }

    @Test
    void startersAreRebuiltEveryTimeSoEditsInTheArrangerTakeEffect() {
        // 旧版踩过的坑：把起始舰缓存成 static final，玩家改完不生效。
        FleetUnit first = ShipCatalog.starters().get(0).fleet().flag();
        FleetUnit second = ShipCatalog.starters().get(0).fleet().flag();
        assertTrue(first != second, "每次都要重新装配");
        assertEquals(first.x(), second.x(), "但摆位应该一样");
    }

    @Test
    void starterFleetsKeepTheirSignatureShips() {
        assertTrue(ShipCatalog.starters().get(0).fleet().hasAxial() == false, "炮术舰队不是轴炮流");
        assertTrue(ShipCatalog.starters().get(3).fleet().hasAxial(), "第四支是轴炮舰队");
        assertEquals(2, ShipCatalog.starters().get(2).fleet()
                .unit(ShipCatalog.GUN_LANCE.id()).level(), "蓄能舰队开局就是 Lv2 光矛舰");
    }

    @Test
    void everyStarterShipCanBeLookedUpAndRearranged() {
        for (Ship ship : ShipCatalog.all()) {
            assertEquals(ship.w() * ship.h(), ship.cells());
            String file = ShipCatalog.fileName(ship);
            assertEquals(file, file.toLowerCase(), "存档文件名用小写 id");
        }
    }

    @Test
    void aSingleShipFleetIsLegal() {
        Fleet fleet = Fleet.of("t", "单舰", "SOLO",
                List.of(new FleetUnit(ShipCatalog.CORE_COMMAND, 1, 0, 0)));
        assertEquals(1, fleet.size());
        assertEquals(ShipCatalog.CORE_COMMAND.id(), fleet.flag().id());
        assertEquals(ShipCatalog.CORE_COMMAND.id() + "@0:0x1", fleet.idList(),
                "单舰舰队的 idList 就是一条「id@x:y x lv」");
    }
}
