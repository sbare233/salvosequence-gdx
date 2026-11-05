package com.hysx.salvosequence.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hysx.salvosequence.deck.Card;
import com.hysx.salvosequence.fleet.Fleet;
import com.hysx.salvosequence.fleet.FleetUnit;
import com.hysx.salvosequence.fleet.Ship;
import com.hysx.salvosequence.fleet.ShipCatalog;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 战斗：我方是<b>一支舰队</b>，敌方也是——而且<b>同一套管线</b>（敌我同源）。
 *
 * <p>敌方是一张 {@code ShipCatalog} 船图 + 一串卡牌计划表：出牌公式、
 * 「类型倍率 → 防空削段 → 逐段减防御力 → 盾 → 体」的结算，两侧完全一致。
 * 这里守的是六条：</p>
 * <ol>
 *   <li>伤害 = 卡面 × 倍率 + 齐射 + 相邻加成 + 校射，然后逐段过类型倍率与防御力；</li>
 *   <li>每回合开局护盾刷回盾值，敌我都是；</li>
 *   <li>防空把多段攻击的生效段数砍掉（最低 1 段）；</li>
 *   <li>自由开火溢出续打、集火溢出浪费；</li>
 *   <li>全队沉没 = 输；敌舰全沉 = 赢；</li>
 *   <li>击沉我方一艘还有大余量溢出 → 系统损伤。</li>
 * </ol>
 */
class BattleTest {

    private static Fleet fleet() {
        // 指挥舰在右侧前排（我方朝右），主炮舰在左后方贴着它。没装甲舰 → 没有相邻伤害加成，算式干净。
        return Fleet.of("test.battle", "试验舰队", "TEST FLEET", List.of(
                new FleetUnit(ShipCatalog.CORE_COMMAND, 1, 4, 0),
                new FleetUnit(ShipCatalog.GUN_MAIN, 1, 0, 0)));
    }

    private static Fleet vulcanFleet() {
        return Fleet.of("test.vulcan", "机炮试验舰队", "TEST VULCAN FLEET", List.of(
                new FleetUnit(ShipCatalog.CORE_COMMAND, 1, 4, 0),
                new FleetUnit(ShipCatalog.GUN_VULCAN, 1, 2, 0)));
    }

    private static Fleet broadFleet() {
        return Fleet.of("test.broad", "侧弦试验舰队", "TEST BROAD FLEET", List.of(
                new FleetUnit(ShipCatalog.CORE_COMMAND, 1, 4, 0),
                new FleetUnit(ShipCatalog.GUN_BROAD, 1, 0, 0)));
    }

    /**
     * 造一艘靶船：一张船图 + 一串计划表（敌我同源，没有第二套敌方数值）。
     *
     * <p>棋盘上每艘船按<a>舰图 id</a> 区分（{@code FleetUnit.id()} 就是舰图的 id），
     * 所以同一场里两艘靶船要用<b>不同</b>的舰图，否则落位时会被判成"同一艘摆两次"。</p>
     */
    private static Enemy dummy(int hp, Ship image, Enemy.Played... plan) {
        return new Enemy("test.dummy." + image.id(), "靶船", "DUMMY", hp, image,
                List.of(plan), "试验用的靶船。");
    }

    /** 默认靶船壳：机炮舰（防御力 1、防空 2、无盾）。 */
    private static Enemy dummy(int hp, Enemy.Played... plan) {
        return dummy(hp, ShipCatalog.GUN_VULCAN, plan);
    }

    /** 只举盾的靶船（站着挨打用）。 */
    private static Enemy guardDummy(int hp, Ship image) {
        return dummy(hp, image, new Enemy.Played("core.patch", false));
    }

    private static Battle battle(Enemy enemy) {
        return new Battle(fleet(), List.of(enemy), 7L);
    }

    private static Battle battle(Fleet fleet, Enemy enemy) {
        return new Battle(fleet, List.of(enemy), 7L);
    }

    private static Battle battle(List<Enemy> squad) {
        return new Battle(fleet(), squad, 7L);
    }

    private static int indexOf(Battle battle, String cardId) {
        List<Card> hand = battle.hand();
        for (int i = 0; i < hand.size(); i++) {
            if (hand.get(i).id().equals(cardId)) {
                return i;
            }
        }
        return -1;
    }

    /** 抽到这张卡并打出去（没抽到就结束回合）。 */
    private static boolean playById(Battle battle, String cardId) {
        for (int guard = 0; guard < 12; guard++) {
            if (battle.over()) {
                return false;
            }
            int index = indexOf(battle, cardId);
            if (index >= 0 && battle.canPlay(index)) {
                return battle.playCard(index);
            }
            battle.endTurn();
        }
        return false;
    }

    @Test
    void aBattleStartsOnTurnOneWithAFullFleetAndAFullHand() {
        Battle battle = battle(EnemyCatalog.byId("foe.scout"));
        assertEquals(1, battle.turn());
        assertEquals(Battle.Result.ONGOING, battle.result());
        assertEquals(2, battle.fleetUnits().size(), "两艘船都活着");
        assertEquals(battle.fleet().maxHp(), battle.hp(), "开局满血");
        assertEquals(battle.fleet().maxEnergy(), battle.energy());
        assertFalse(battle.hand().isEmpty());
        assertTrue(battle.aliveCount() >= 1);
        assertNotNull(battle.target(), "开局要有当前目标");
    }

    @Test
    void damageIsTheCardPlusTheShipsThatCarryIt_minusTargetPlate() {
        Battle battle = battle(EnemyCatalog.byId("foe.scout"));
        battle.setFreeFire(false); // 验基础公式，排除自由开火的 ×1.2
        int before = battle.enemyHp();
        assertTrue(playById(battle, "core.rapid"), "牌库里一定有速射炮击");
        int raw = 5 * battle.multiplierOfCardId("core.rapid")
                + battle.fleet().adjacencyDamageBonus("core.rapid")
                + battle.fleet().volleyFor("core.rapid");
        // 靶船是机炮舰壳：防御力 1，每段先减一格
        assertEquals(before - Math.max(1, raw - battle.targetPlate()), battle.enemyHp(),
                "5 点卡面 + 舰队齐射，一起打在同一个目标上，减掉防御力");
        assertEquals(1, battle.turn());
    }

    @Test
    void enemyLocksTheRightmostShipAsFront() {
        // 我方朝右：装甲舰在左后、反应堆舰在右前，敌方索敌应该锁反应堆
        Fleet fleet = Fleet.of("test.front", "前后排试验舰队", "FRONT TEST", List.of(
                new FleetUnit(ShipCatalog.ARMOR_BELT, 1, 0, 0),
                new FleetUnit(ShipCatalog.ENGINE_REACTOR, 1, 10, 0)));
        Battle battle = new Battle(fleet, List.of(dummy(50, new Enemy.Played("vulcan.suppress", false))), 7L);
        assertEquals(ShipCatalog.ENGINE_REACTOR.id(), battle.fleetRoster().pick(false).id(),
                "前排 = x 最大（右侧）");
        assertEquals(ShipCatalog.ARMOR_BELT.id(), battle.fleetRoster().pick(true).id(),
                "锁后排 = x 最小（左侧）");
        int reactorHp = battle.fleetRoster().byId(ShipCatalog.ENGINE_REACTOR.id()).hp();
        int armorHp = battle.fleetRoster().byId(ShipCatalog.ARMOR_BELT.id()).hp();
        battle.endTurn();
        assertTrue(battle.fleetRoster().byId(ShipCatalog.ENGINE_REACTOR.id()).hp() < reactorHp,
                "伤害落在反应堆舰上");
        assertEquals(armorHp, battle.fleetRoster().byId(ShipCatalog.ARMOR_BELT.id()).hp(),
                "装甲舰没挨打");
    }

    @Test
    void theEnemyActsOnEndTurnAndHitsTheFrontShip() {
        // 计划表：压制射击。敌方出牌同一条公式：卡面 5 + 船图齐射 3，再过伤害旋钮
        Battle battle = battle(dummy(50, new Enemy.Played("vulcan.suppress", false)));
        int before = battle.hp();
        battle.endTurn();
        int total = (int) Math.floor((5 + 3) * EnemyCatalog.damageScale());
        assertEquals(before - Math.max(1, total - 2), battle.hp(),
                "落到指挥舰，防御力吃掉 2");
        assertEquals(2, battle.turn(), "回合数往前走");
        assertEquals(0, battle.block(), "开新回合时护盾刷回盾值（指挥舰没有盾）");
    }

    @Test
    void shieldsSoakTheFirstBlockAndNoneOfItReachesTheHull() {
        // 热能武器 vs 盾 ×0.75：波动炮卡面 10 + 船图齐射 0，过旋钮后折算 ≤ 5+2，正好被盾吃满
        Battle battle = battle(dummy(50, new Enemy.Played("wave.fire", false)));
        int before = battle.hp();
        assertTrue(playById(battle, "core.patch"), "牌库里一定有应急装甲");
        assertTrue(battle.block() >= 5, "拿到 5 点护盾");
        battle.endTurn();
        assertEquals(before, battle.hp(), "折算后的伤害全被护盾吃掉");
    }

    @Test
    void killingEveryEnemyEndsTheBattleAsAWon() {
        Battle battle = battle(guardDummy(6, ShipCatalog.GUN_VULCAN));
        assertTrue(playById(battle, "core.rapid"));
        assertEquals(Battle.Result.WON, battle.result(),
                "6 血靶船一发打沉（7 卡面+齐射 − 1 防御力 = 6）");
        assertTrue(battle.over());
    }

    @Test
    void staffBriefPullsAnAttackCardFromTheDeck() {
        Battle battle = battle(guardDummy(6, ShipCatalog.GUN_VULCAN));
        battle.debugPutInHand("buff.staff");
        int idx = indexOf(battle, "buff.staff");
        assertTrue(battle.canPlay(idx), "1 费在开局能量内");
        assertTrue(battle.playCard(idx));
        boolean gotAttack = false;
        for (Card c : battle.hand()) {
            if (c.isAttack()) {
                gotAttack = true;
                break;
            }
        }
        assertTrue(gotAttack, "参谋建议从牌库检索到一张攻击牌");
        assertTrue(battle.logAll().stream().anyMatch(l -> l.contains("参谋建议")),
                "检索要有日志");
    }

    @Test
    void heavyShellsPunchPlateThatMultiHitsCannot() {
        // 靶船壳 = 装甲舰（盾 2、防御力 6、结构 50）。盾亮着 → 动能吃 vs 盾 ×1.5。
        // 效力射 34 + 齐射 7 = 41 → floor(41×1.5) − 6 = 55：一炮贯到底，直接击沉。
        Battle heavy = battle(guardDummy(50, ShipCatalog.ARMOR_BELT));
        assertTrue(playById(heavy, "main.effective"), "牌库里一定有效力射");
        assertEquals(Battle.Result.WON, heavy.result(), "单发大伤：攻击高就是穿甲");

        // 机炮点射 3×3（+齐射 3 = 12）：三段各 4 点，逐段被防御力磨到保底 1 →
        // 前两段被盾吃掉、只有最后 1 点落在结构上。
        // 计划用炮击而不是自举盾：靶船盾必须恒为 2，测试才不依赖抽牌时序
        Battle vulcan = battle(vulcanFleet(), dummy(50, ShipCatalog.ARMOR_BELT,
                new Enemy.Played("core.rapid", false)));
        assertTrue(playById(vulcan, "vulcan.burst"), "牌库里一定有点射");
        assertEquals(49, vulcan.enemyHp(), "多段低伤被高防御力逐段磨掉，只啃掉 1 点结构");
        assertFalse(vulcan.over());
    }

    @Test
    void flakCutsTheTailSegmentsOff() {
        // 靶船壳 GUN_VULCAN 自带防空 2：弹幕覆盖 5 段的齐射被砍掉尾段 2 段——
        // 被拦的弹直接损失（不是把伤害挤进剩下的段）
        Battle battle = battle(broadFleet(), guardDummy(200, ShipCatalog.GUN_VULCAN));
        battle.setFreeFire(false); // 验基础公式，排除自由开火的 ×1.2
        int before = battle.enemyHp();
        assertTrue(playById(battle, "broad.barrage"), "手牌里一定有弹幕覆盖（5 张卡全起手）");
        int raw = 25 * battle.multiplierOfCardId("broad.barrage")
                + battle.fleet().volleyFor("broad.barrage");
        int hits = 5;
        int effHits = Math.max(1, hits - battle.targetFlak());
        int base = raw / hits;
        int rem = raw % hits;
        int expected = 0;
        for (int i = 0; i < effHits; i++) {
            expected += Math.max(1, base + (i < rem ? 1 : 0) - battle.targetPlate());
        }
        assertEquals(before - expected, battle.enemyHp(),
                "防空把 5 段砍成 " + effHits + " 段（尾段损失），每段再被防御力减一格");
    }

    @Test
    void whenTheFleetIsWipedTheBattleIsLost() {
        // 敌舰每回合 ~19 点落到前排，血厚了要连打几回合才沉
        Battle battle = battle(dummy(500, new Enemy.Played("main.volley", false)));
        int guard = 0;
        while (!battle.over() && guard++ < 60) {
            battle.endTurn();
        }
        assertTrue(battle.over(), "挨够以后总该结束");
        assertEquals(Battle.Result.LOST, battle.result(), "前排列沉光 → 本场结束");
        assertNotNull(battle.fleetRoster().flag(), "全灭时旗舰读数也不能是 null");
    }

    @Test
    void freeFireCarriesOverflowIntoTheNextShip_butFocusFireWastesIt() {
        // 两艘 1 血的靶船：7 点伤害减 1 防御力 = 6，打穿第一条，剩下 5 点
        Battle free = new Battle(fleet(), List.of(
                guardDummy(1, ShipCatalog.GUN_VULCAN),
                guardDummy(1, ShipCatalog.GUN_MISSILE)), 7L);
        free.setFreeFire(true);
        assertTrue(playById(free, "core.rapid"));
        assertEquals(0, free.aliveCount(), "自由开火：溢出续打，两艘一起沉");

        Battle focus = new Battle(fleet(), List.of(
                guardDummy(1, ShipCatalog.GUN_VULCAN),
                guardDummy(1, ShipCatalog.GUN_MISSILE)), 7L);
        focus.setFreeFire(false);
        assertTrue(playById(focus, "core.rapid"));
        assertEquals(1, focus.aliveCount(), "集火：只打沉一艘，溢出浪费");
    }

    @Test
    void theSkillIsOncePerTurnAndCostsEnergy() {
        Battle battle = battle(guardDummy(500, ShipCatalog.GUN_VULCAN));
        int energy = battle.energy();
        assertTrue(battle.useSkill());
        assertEquals(energy - Battle.SKILL_COST, battle.energy());
        assertEquals(Battle.SKILL_AIM, battle.aim());
        assertFalse(battle.useSkill(), "一回合只能点一次");
        battle.endTurn();
        assertFalse(battle.skillUsed(), "新回合可以再用");
    }

    @Test
    void targetingSkipsSunkShips() {
        Battle battle = battle(List.of(
                guardDummy(1, ShipCatalog.GUN_VULCAN),
                guardDummy(80, ShipCatalog.GUN_MISSILE)));
        battle.selectTarget(0);
        assertTrue(playById(battle, "core.rapid"));
        assertFalse(battle.foes().get(0).alive(), "第一艘被打沉了");
        assertEquals(1, battle.targetIndex(), "打沉之后自动换到还活着的那艘");
        battle.selectTarget(0);
        assertEquals(1, battle.targetIndex(), "选已沉的会被跳过");
    }

    @Test
    void enemyShipsReadTheirStatsFromTheSharedHull() {
        // 敌我同源：装甲舰图当靶船 → 它的盾/防御力就是我方装甲舰那套
        Battle battle = battle(guardDummy(50, ShipCatalog.ARMOR_BELT));
        assertEquals(ShipCatalog.ARMOR_BELT.armor(), battle.enemyBlock(),
                "敌方格挡 = 船型的盾值");
        assertEquals(ShipCatalog.ARMOR_BELT.plate(), battle.targetPlate(),
                "敌方防御力 = 船型的防御力");
    }

    @Test
    void theShieldLinkPerkSpreadsBlockCardsToNeighbors() {
        // 指挥舰自带 盾链1(十字)：应急装甲给“当前被锁定的前排舰”+5，盾链再罩到主炮舰 +1
        Battle battle = battle(guardDummy(500, ShipCatalog.GUN_VULCAN));
        assertTrue(playById(battle, "core.patch"));
        assertEquals(5 + 1, battle.block(), "前排 5 + 主炮舰的盾链 1");
    }

    @Test
    void theMagazinePerkCutsReloadTime() {
        // 光矛舰自带 弹夹1(自身)：光矛射击装填 2 → 1
        Fleet lance = Fleet.of("test.lance", "光矛试验舰队", "LANCE FLEET", List.of(
                new FleetUnit(ShipCatalog.CORE_COMMAND, 1, 4, 0),
                new FleetUnit(ShipCatalog.GUN_LANCE, 1, 1, 0)));
        Battle battle = new Battle(lance, List.of(guardDummy(500, ShipCatalog.GUN_VULCAN)), 7L);
        assertTrue(playById(battle, "lance.beam"));
        assertEquals(1, battle.loadingZone().get(0).turns(), "装填 2 − 弹夹 1 = 1");
    }

    @Test
    void theRepairPerkHealsTheCoveredShipsAtTurnStart() {
        // 维修舰的 损管2(十字) 罩住指挥舰：挨 floor(8×旋钮)−防御2 点，下回合开局修回 2
        Fleet tender = Fleet.of("test.tender", "损管试验舰队", "TENDER FLEET", List.of(
                new FleetUnit(ShipCatalog.CORE_COMMAND, 1, 0, 0),
                new FleetUnit(ShipCatalog.SUPPORT_REPAIR, 1, 0, 3)));
        Battle battle = new Battle(tender, List.of(dummy(500, new Enemy.Played("vulcan.suppress", false))), 7L);
        battle.endTurn();
        int total = (int) Math.floor((5 + 3) * EnemyCatalog.damageScale());
        assertEquals(24 - Math.max(1, total - 2) + 2, battle.fleetRoster().flag().hp(),
                "回合开始损管修回 2 点");
    }

    @Test
    void enemyFormationGainsPerksFromTheSynergyLayout() {
        // 波次随机化后编队随种子变：扫种子找一次带光环舰的编队，
        // 协同铺位应该让编队叠到结构/损管/盾链之类的收益
        boolean synergyFound = false;
        for (long seed = 0; seed < 40 && !synergyFound; seed++) {
            Battle battle = battle(EnemyCatalog.squad(2, seed));
            int raw = 0;
            int withPerks = 0;
            for (Battle.Foe foe : battle.foes()) {
                raw += foe.enemy().hp();
                withPerks += foe.maxHp();
            }
            if (withPerks > raw) {
                synergyFound = true;
            }
            assertTrue(battle.foes().size() >= 3, "敌方编队已经是多舰制");
        }
        assertTrue(synergyFound, "40 个种子里居然没有一个编队叠到光环");
    }

    @Test
    void metricsCountWhatThePlayerActuallyDid() {
        Battle battle = battle(guardDummy(500, ShipCatalog.GUN_VULCAN));
        assertTrue(playById(battle, "core.rapid"));
        assertEquals(1, battle.metricCardsPlayed());
        assertTrue(battle.metricEnergySpent() >= 1);
        assertTrue(battle.metricEnergyAvailable() >= battle.metricEnergySpent());
        assertTrue(battle.metricDamageDealt() > 0);
        assertTrue(battle.metricCardsDrawn() >= 1);
    }

    @Test
    void reloadingCardsLeaveTheDiscardForALaterTurn() {
        Battle battle = battle(guardDummy(500, ShipCatalog.GUN_VULCAN));
        assertTrue(playById(battle, "main.volley"));
        assertFalse(battle.loadingZone().isEmpty(), "装填中的牌先进装填区");
        int reload = battle.loadingZone().get(0).card().reload();
        assertEquals(reload, battle.loadingZone().get(0).turns(), "刚进装填区时倒计时等于装填值");
        assertTrue(reload > 0);

        for (int i = 1; i < reload; i++) {
            battle.endTurn();
            assertEquals(1, battle.loadingZone().size(), "装填 " + reload + "：第 " + i + " 回合还在倒数");
        }
        battle.endTurn();
        assertTrue(battle.loadingZone().isEmpty(), "装填倒数到 0 就回弃牌堆");
    }
}
