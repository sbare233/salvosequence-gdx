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
 * 舰队规则引擎：卡倍率、齐射、相邻加成、能量与抽牌。
 *
 * <p>这一层的几条算法是"模块 → 船"改造的核心变化：</p>
 * <ul>
 *   <li>旧版「同名模块叠成等级」→ 现在「带同一张卡的船，等级相加 = 倍率」；</li>
 *   <li>旧版「轴炮管/口的相接」→ 现在「轴炮管舰与炮口舰贴着就合流」；</li>
 *   <li>旧版「模块贴装甲加伤害」→ 现在「炮舰贴装甲舰加齐射、贴动力舰加校射」。</li>
 * </ul>
 */
class FleetTest {

    private static FleetUnit at(Ship ship, int x, int y) {
        return new FleetUnit(ship, 1, x, y);
    }

    /** 指挥舰 + 装甲舰贴住 + 主炮舰贴装甲 + 推进舰贴主炮（一条典型的"贴身"阵型）。 */
    private static Fleet gangfleet() {
        return Fleet.of("test.gang", "贴脸试验舰队", "GANG TEST", List.of(
                at(ShipCatalog.CORE_COMMAND, 0, 0),   // x0..2 y0..2
                at(ShipCatalog.ARMOR_BELT, 3, 0),     // x3..6 y0..1 贴着指挥舰
                at(ShipCatalog.GUN_MAIN, 3, 2),       // x3..6 y2..4 贴着装甲舰
                at(ShipCatalog.ENGINE_THRUST, 7, 2)));// x7..8 y2..3 贴着主炮舰
    }

    @Test
    void multiplierIsTheSumOfTheLevelsOfEveryCarrier() {
        Fleet single = Fleet.of("t", "试验", "TEST", List.of(
                at(ShipCatalog.CORE_COMMAND, 0, 0),
                at(ShipCatalog.TACTIC_EW, 3, 0)));
        assertEquals(1, single.multiplierOf("ew.datalink"), "一艘 Lv1 → ×1");

        // 同型船会合并等级，所以"两艘带同一张卡的船"必须换型：电子战舰 + 火控指挥舰都带 ew.datalink
        Fleet two = Fleet.of("t", "试验", "TEST", List.of(
                at(ShipCatalog.CORE_COMMAND, 0, 0),
                at(ShipCatalog.TACTIC_EW, 3, 0),
                new FleetUnit(ShipCatalog.TACTIC_FIRE, 2, 5, 0)));
        assertEquals(3, two.multiplierOf("ew.datalink"), "Lv1 + Lv2 = ×3");
        assertEquals(1, two.multiplierOf("vulcan.burst"), "没人带的卡至少 ×1，不能变 0");
    }

    @Test
    void everyCarrierAddsItsOwnVolley() {
        Fleet fleet = gangfleet();
        int volley = fleet.volleyFor("main.volley");
        assertEquals(ShipCatalog.GUN_MAIN.volley() + 1, volley,
                "主炮舰 7 齐射，旁边贴着装甲舰再 +1");
        assertEquals(0, fleet.volleyFor("miss.salvo"), "没船带这张卡就没有齐射");
    }

    @Test
    void armorNextToTheFlagRaisesItsHp() {
        Fleet fleet = gangfleet();
        FleetUnit flag = fleet.flag();
        // 光环方向：装甲舰把结构值加给贴身的船（旧规则是"装甲贴指挥"的同一条，泛化到所有邻船）
        assertEquals(2, fleet.bonusOf(flag).hp(), "贴着装甲舰的指挥舰 +2 结构值");
        assertEquals(ShipCatalog.CORE_COMMAND.hp() + 2, fleet.maxHpOf(flag));
        assertEquals(ShipCatalog.ARMOR_BELT.hp(), fleet.maxHpOf(fleet.unit(ShipCatalog.ARMOR_BELT.id())),
                "光环不覆盖携带舰自己的结构值（装甲舰的 HP 光环是十字范围）");
    }

    @Test
    void gunNextToArmorGainsVolley_andNextToDriveGainsShield() {
        Fleet fleet = gangfleet();
        FleetUnit gun = fleet.unit(ShipCatalog.GUN_MAIN.id());
        Fleet.FleetBonus bonus = fleet.bonusOf(gun);
        assertEquals(1, bonus.volley(), "贴装甲舰 +1 齐射");
        assertEquals(1, bonus.armor(), "贴动力舰 +1 护盾");
        assertEquals(ShipCatalog.GUN_MAIN.volley() + 1, fleet.volleyOf(gun));
        assertEquals(1, fleet.armorOf(gun));
    }

    @Test
    void spotPerksFeedTheCardsDamage() {
        Fleet fleet = gangfleet();
        assertEquals(1, fleet.adjacencyDamageBonus("main.volley"), "炮舰身边有装甲舰的火控光环 → +1 伤害");
        assertEquals(0, fleet.adjacencyDamageBonus("miss.salvo"), "没人带的卡没有加成");
    }

    @Test
    void magazinePerksCoverTheSameRowOnly() {
        Fleet fleet = Fleet.of("t", "弹夹试验", "MAG TEST", List.of(
                at(ShipCatalog.CORE_COMMAND, 0, 0),
                at(ShipCatalog.SUPPORT_MAGAZINE, 4, 0),   // y0..1
                at(ShipCatalog.GUN_MAIN, 7, 0),           // 同排
                at(ShipCatalog.GUN_HEAVY, 7, 4)));        // 隔两排，无弹夹
        assertEquals(1, fleet.magazineFor("main.volley"), "同排的炮舰吃到弹库的弹夹");
        assertEquals(0, fleet.magazineFor("heavy.volley"), "不同排的重炮吃不到");
    }

    @Test
    void sunkShipsStopContributingPerksAndEnergy() {
        Fleet fleet = Fleet.of("t", "断链试验", "BREAK TEST", List.of(
                at(ShipCatalog.CORE_COMMAND, 0, 0),
                at(ShipCatalog.SUPPORT_REPAIR, 0, 3),    // CROSS 维修光环罩指挥舰
                at(ShipCatalog.ENGINE_OVERDRIVE, 4, 0)));
        FleetUnit flag = fleet.flag();
        assertEquals(2, fleet.repairOn(flag), "维修舰的 CROSS 光环罩着指挥舰");
        assertEquals(1, fleet.multiplierOf("core.rapid"), "指挥舰自己带速射炮击");
        java.util.Set<String> alive = java.util.Set.of(
                ShipCatalog.CORE_COMMAND.id(), ShipCatalog.SUPPORT_REPAIR.id());
        assertEquals(2, fleet.repairOn(flag, alive), "引擎舰沉了不影响这条链");
        java.util.Set<String> onlyCore = java.util.Set.of(ShipCatalog.CORE_COMMAND.id());
        assertEquals(0, fleet.repairOn(flag, onlyCore), "击沉即断链：维修光环失效");
        int fullEnergy = fleet.maxEnergy();
        int lessEnergy = fleet.maxEnergy(onlyCore);
        assertTrue(lessEnergy < fullEnergy, "引擎舰沉了，供能光环失效（" + fullEnergy + "→" + lessEnergy + "）");
        assertEquals(1, fleet.multiplierOf("core.rapid", onlyCore), "倍率不受无关沉船影响");
    }

    @Test
    void shieldLinkAndRepairPerksLandPerUnit() {
        Fleet fleet = Fleet.of("t", "支援试验", "SUPPORT TEST", List.of(
                at(ShipCatalog.CORE_COMMAND, 0, 0),   // x0..2 y0..2
                at(ShipCatalog.SUPPORT_LINK, 3, 0),   // x3..4 y0..2，贴着指挥舰
                at(ShipCatalog.SUPPORT_REPAIR, 0, 3)));// x0..2 y3..4，共边贴着指挥舰下缘
        assertEquals(2, fleet.shieldLinkOn(fleet.flag()), "盾桥舰的 RING 光环罩住指挥舰");
        assertEquals(2, fleet.repairOn(fleet.flag()), "维修舰的 CROSS 光环罩住指挥舰");
        assertEquals(0, fleet.repairOn(fleet.unit(ShipCatalog.SUPPORT_REPAIR.id())),
                "CROSS 光环不覆盖携带舰自己");
    }

    @Test
    void ringCoversDiagonalsThatCrossDoesNot() {
        Fleet fleet = Fleet.of("t", "观察试验", "SPOT TEST", List.of(
                at(ShipCatalog.CORE_COMMAND, 0, 0),      // x0..2 y0..2
                at(ShipCatalog.SUPPORT_OBSERVER, 3, 3)));// x3..4 y3..4，纯斜角
        assertEquals(2, fleet.adjacencyDamageBonus("core.rapid"),
                "观察舰的火控是 RING 范围，斜角也算");
        Fleet crossOnly = Fleet.of("t", "对照", "CONTROL", List.of(
                at(ShipCatalog.SUPPORT_OBSERVER, 0, 0),
                at(ShipCatalog.CORE_COMMAND, 2, 2)));    // 2×2 的观察舰斜角贴着 3×3 的指挥舰
        assertEquals(2, crossOnly.adjacencyDamageBonus("core.rapid"),
                "范围跟着几何走，跟谁带光环无关");
    }

    @Test
    void plateAndFlakStackFromPerksOntoTheDerivedStats() {
        Fleet fleet = Fleet.of("t", "防空试验", "FLAK TEST", List.of(
                at(ShipCatalog.CORE_COMMAND, 0, 0),
                at(ShipCatalog.GUARD_FLAK, 3, 0)));      // FLAK 2 ROW，同排
        assertEquals(ShipCatalog.CORE_COMMAND.flak() + 2, fleet.flakOf(fleet.flag()),
                "指挥舰同排有防空护卫舰 → 防空 1+2");
        assertEquals(ShipCatalog.GUARD_FLAK.flak(), fleet.flakOf(fleet.unit(ShipCatalog.GUARD_FLAK.id())),
                "同排光环不覆盖自己");
        assertEquals(ShipCatalog.CORE_COMMAND.plate(), fleet.plateOf(fleet.flag()),
                "没有防御光环时防御力就是基础值");
    }

    @Test
    void fleetWideDrawPerkRaisesTheHandSize() {
        Fleet fleet = Fleet.of("t", "过牌试验", "DRAW TEST", List.of(
                at(ShipCatalog.CORE_COMMAND, 0, 0),
                at(ShipCatalog.SUPPORT_RADAR, 3, 0)));
        assertEquals(Fleet.BASE_DRAW + 1, fleet.handSize(), "预警舰的过牌光环全队 +1 抽牌");
    }

    @Test
    void energyAndDrawStackButStayUnderTheirCaps() {
        Fleet plain = Fleet.of("t", "试验", "TEST", List.of(at(ShipCatalog.CORE_COMMAND, 0, 0)));
        assertEquals(Fleet.BASE_ENERGY, plain.maxEnergy());
        assertEquals(Fleet.BASE_DRAW, plain.handSize());

        Fleet boosted = Fleet.of("t", "试验", "TEST", List.of(
                at(ShipCatalog.CORE_COMMAND, 0, 0),
                at(ShipCatalog.ENGINE_OVERDRIVE, 3, 0),
                at(ShipCatalog.TACTIC_FIRE, 6, 0)));
        assertEquals(Fleet.BASE_ENERGY + 2, boosted.maxEnergy(), "超载引擎舰 +2 能量");
        assertEquals(Fleet.BASE_DRAW + 1, boosted.handSize(), "火控指挥舰 +1 抽牌");
    }

    @Test
    void axialBarrelsAndMuzzlesThatTouchMultiplyTheAxialCards() {
        Fleet fleet = Fleet.of("t", "试验", "TEST", List.of(
                at(ShipCatalog.CORE_COMMAND, 0, 0),
                at(ShipCatalog.GUN_AXIAL_BARREL, 3, 0),        // x3..4 y0..1
                at(ShipCatalog.GUN_AXIAL_MUZZLE, 5, 0)));      // x5 y0..1 贴住炮管
        assertTrue(fleet.hasAxial());
        assertEquals(1, fleet.axialBarrels());
        assertEquals(1, fleet.axialMuzzles());
        assertEquals(1, fleet.axialLinkPairs(), "管口贴住 → 1 对相接");
        assertEquals(4, fleet.axialMultiplier(), "管 1 + 口 1 + 相接 1 + 都有 1 = ×4");
    }

    @Test
    void axialMultiplierIsCapped() {
        // 管 2 + 口 2 + 相接 2（上限）+ 都有 1 = 7 → 封顶 6
        Fleet fleet = Fleet.of("t", "试验", "TEST", List.of(
                at(ShipCatalog.CORE_COMMAND, 0, 0),
                at(ShipCatalog.GUN_AXIAL_BARREL, 3, 0),        // x3..4 y0..1
                at(ShipCatalog.GUN_AXIAL_BARREL_LONG, 8, 0),   // x8..10 y0..1
                at(ShipCatalog.GUN_AXIAL_MUZZLE, 3, 2),        // x3 y2..3，贴住第一根管
                at(ShipCatalog.GUN_AXIAL_MUZZLE_WIDE, 11, 0)));// x11..12 y0..1，贴住第二根管
        assertEquals(2, fleet.axialBarrels());
        assertEquals(2, fleet.axialMuzzles());
        assertEquals(Fleet.AXIAL_MULT_CAP, fleet.axialMultiplier(), "倍率封顶 6");
    }

    @Test
    void autoArrangePutsEveryStarterShipOnTheBoard() {
        Fleet fleet = Fleet.autoArrange("t", "自动编队", "AUTO",
                ShipCatalog.starters().get(0).fleet().units().stream()
                        .map(u -> new FleetUnit(u.ship(), u.level(), 0, 0))
                        .toList());
        assertEquals(6, fleet.size(), "炮术舰队六艘船都要上棋盘");
        assertNotNull(fleet.flag());
        assertTrue(fleet.maxHp() > 0);
    }

    @Test
    void autoArrangeNeverOverlapsAnything() {
        for (ShipCatalog.Starter starter : ShipCatalog.starters()) {
            Fleet fleet = Fleet.autoArrange(starter.id(), starter.nameZh(), starter.nameEn(),
                    starter.fleet().units().stream()
                            .map(u -> new FleetUnit(u.ship(), u.level(), 0, 0))
                            .toList());
            List<FleetUnit> units = fleet.units();
            assertEquals(starter.fleet().size(), units.size(), starter.nameZh() + "：一艘都不能漏");
            for (int i = 0; i < units.size(); i++) {
                for (int j = i + 1; j < units.size(); j++) {
                    assertFalse(units.get(i).overlaps(units.get(j)),
                            starter.nameZh() + "：" + units.get(i).nameZh() + " 和 "
                                    + units.get(j).nameZh() + " 叠在一起了");
                }
            }
        }
    }

    @Test
    void movingOrDroppingAShipProducesANewFleet() {
        Fleet fleet = gangfleet();
        Fleet moved = fleet.movedUnit(ShipCatalog.ENGINE_THRUST.id(), 8, 4);
        assertNotNull(moved);
        assertEquals(7, fleet.unit(ShipCatalog.ENGINE_THRUST.id()).x(), "原舰队不变");
        assertEquals(8, moved.unit(ShipCatalog.ENGINE_THRUST.id()).x());
        assertNull(fleet.movedUnit(ShipCatalog.ENGINE_THRUST.id(), 15, 15), "摆不下就返回 null");
        assertEquals(3, fleet.withoutUnit(ShipCatalog.GUN_MAIN.id()).size());
    }

    @Test
    void upgradesRaiseLevelWithoutChangingTheBoard() {
        Fleet fleet = gangfleet();
        Fleet upgraded = fleet.upgradedUnit(ShipCatalog.GUN_MAIN.id());
        assertEquals(2, upgraded.unit(ShipCatalog.GUN_MAIN.id()).level());
        assertEquals(2, upgraded.multiplierOf("main.volley"), "Lv2 的船把那张卡变成 ×2");
        assertEquals(2, upgraded.unit(ShipCatalog.GUN_MAIN.id()).y(), "位置不变");
    }

    @Test
    void deckIsDedupedByCardId() {
        Fleet fleet = gangfleet();
        List<String> ids = fleet.deck().stream().map(c -> c.id()).toList();
        assertEquals(ids.size(), ids.stream().distinct().count(), "同一张卡在牌库里只出现一次");
        assertTrue(ids.contains("core.rapid"));
        assertTrue(ids.contains("main.volley"));
    }

    @Test
    void aFlagIsWhateverShipCarriesTheCommandRole() {
        Fleet fleet = gangfleet();
        assertEquals(ShipCatalog.CORE_COMMAND.id(), fleet.flag().id());
        assertTrue(fleet.flag().isFlag());
        assertFalse(fleet.unit(ShipCatalog.GUN_MAIN.id()).isFlag());
    }
}
