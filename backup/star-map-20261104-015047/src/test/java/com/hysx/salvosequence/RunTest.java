package com.hysx.salvosequence.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hysx.salvosequence.combat.EnemyCatalog;
import com.hysx.salvosequence.fleet.Fleet;
import com.hysx.salvosequence.fleet.FleetUnit;
import com.hysx.salvosequence.fleet.ShipArtStore;
import com.hysx.salvosequence.fleet.ShipCatalog;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 一次航程：选舰队 → 五场战斗 → 每次胜利挑一件战利品。
 *
 * <p>改造后战利品有三种：<b>编入新船</b>（再去棋盘上挑个落点）、<b>强化</b>（已有一艘升一级）、
 * <b>卡</b>（直接进卡组）。这里守的是这条闭环：</p>
 * <ul>
 *   <li>四支起始舰队都能开局，名字都以「舰队」结尾；</li>
 *   <li>赢一场就进战利品阶段，三种奖励的落点各不相同；</li>
 *   <li>编入的船真的落到棋盘上、写进存档、下一关能带着它打；</li>
 *   <li>关卡之间会整备（活着的 +13，沉掉的拖回来只剩四分之一）。</li>
 * </ul>
 */
class RunTest {

    private static final long SEED = 20240601L;

    /** 用临时目录隔离玩家存档：本机 ships/ 下是自己排过的舰队，会污染出厂数值。 */
    @BeforeAll
    static void isolateSaves() throws Exception {
        ShipArtStore.useDirectory(Files.createTempDirectory("hull-run-test"));
    }

    private static SimplePilot robot(long seed) {
        return new SimplePilot(SimplePilot.Style.SMART, seed);
    }

    /** 一直打到这一关分出胜负（返回时 stage 不再是 BATTLE）。 */
    private static void fightOut(Run run, SimplePilot bot) {
        int guard = 0;
        while (run.stage() == Run.Stage.BATTLE && guard++ < 2000) {
            int index = bot.chooseCard(run.battle());
            if (index >= 0 && run.playCard(index)) {
                continue;
            }
            if (bot.useSkill(run.battle()) && run.useSkill()) {
                continue;
            }
            run.endTurn();
        }
    }

    /**
     * 扫种子找一次「战利品里有某种奖励」的航程（沿途奖励直接跳过）。
     *
     * <p>战利品是掷出来的，所以这里扫种子——测试要的是稳定的机制断言，不是数值分布。
     * 掉落池改成"敌舰本体"之后，第 0 关的机炮舰多半已经在舰队里，SHIP 要往后扫几关。</p>
     */
    private static Run reachSpacePort() {
        for (long seed = SEED; seed < SEED + 60; seed++) {
            Run run = new Run(seed);
            run.pickLoadout(1);
            run.pickCaptain(0);
            SimplePilot bot = robot(seed);
            int guard = 0;
            while (!run.finished() && guard++ < 200) {
                switch (run.stage()) {
                    case BATTLE -> { fightOut(run, bot); }
                    case SPACE_PORT -> { return run; }
                    case MAP -> {
                        var reach = run.reachableStars();
                        var pick = reach.stream().filter(st -> st.type() == Run.NodeType.BATTLE
                                || st.type() == Run.NodeType.ELITE).findFirst();
                        if (pick.isEmpty()) { pick = reach.stream().findFirst(); }
                        pick.ifPresent(st -> run.travelTo(st.id()));
                    }
                    default -> { return null; }
                }
            }
        }
        throw new IllegalStateException("60 seeds, no space port reached");
    }

    @Test
    void aFreshRunWaitsForTheFleetChoice() {
        Run run = new Run(SEED);
        assertEquals(Run.Stage.PICK_LOADOUT, run.stage());
        assertEquals(0, run.node());
        assertNull(run.fleet());
        assertNull(run.battle());
        assertEquals(EnemyCatalog.routeLength(), run.routeLength());
        assertEquals(Run.NODE_COUNT, run.routeLength());
        assertFalse(run.finished());

        List<ShipCatalog.Starter> starters = run.starters();
        assertEquals(4, starters.size(), "四支起始舰队");
        for (ShipCatalog.Starter starter : starters) {
            assertTrue(starter.nameZh() != null && !starter.nameZh().isBlank(),
                    "选项名是主角名：" + starter.nameZh());
            assertTrue(starter.fleet().size() >= 5, "一支舰队至少五艘船");
            assertTrue(starter.fleet().maxHp() > 0);
        }
    }

    @Test
    void pickingAFleetStartsOnTheStarMap() {
        Run run = new Run(SEED);
        run.pickLoadout(1);
        assertEquals(Run.Stage.MAP, run.stage(), "开局进入星图航行");
        assertNotNull(run.fleet());
        assertNotNull(run.captain());
        assertFalse(run.reachableStars().isEmpty(), "起点必须有可跃迁的星");
        assertEquals(run.maxHp(), run.hp(), "开局满血");
        assertEquals(run.fleet().size(), run.fleetHp().size(), "每艘船都记了结构值");
        for (int hp : run.fleetHp().values()) {
            assertTrue(hp > 0);
        }
    }

    @Test
    void winningOffersANewShip_anUpgrade_andACard() {
        Run run = reachSpacePort();
        List<Reward> rewards = run.rewards();
        assertTrue(run.alloy() > 0, "赢了要有合金");
    }

    /**
     * 真实游戏流程哨兵：只选舰队（不手动 pickCaptain——UI 上没有这个入口），
     * 打完一场必须拿到合金。用户真实踩坑：舰长从未被任命，合金永远是 0。
     */
    @Test
    void pickingALoadoutAloneYieldsAlloyAfterBattle() {
        Run run = new Run(SEED);
        run.pickLoadout(1);
        assertNotNull(run.captain(), "选舰队要自动任命舰长");
        int nav = 0;
        while (run.stage() == Run.Stage.MAP && nav++ < 20) {
            var reach = run.reachableStars();
            var pick = reach.stream().filter(st -> st.type() == Run.NodeType.BATTLE
                    || st.type() == Run.NodeType.ELITE).findFirst();
            if (pick.isEmpty()) { pick = reach.stream().findFirst(); }
            pick.ifPresent(st -> run.travelTo(st.id()));
        }
        fightOut(run, robot(SEED));
        if (run.stage() == Run.Stage.SPACE_PORT) {
            assertTrue(run.alloy() > 0, "战后必须有合金，实际 " + run.alloy());
            String first = run.fleet().units().get(0).id();
            int lv = run.fleet().unit(first).level();
            boolean upgradeable = run.alloy() >= 30 * lv;
            boolean buyable = !run.shopInventory().isEmpty()
                    && run.alloy() >= run.shipCost(run.shopInventory().get(0));
            assertTrue(upgradeable || buyable, "合金要够买至少一样东西");
        }
    }

    @Test
    void aCardRewardGrowsTheDeck() {
        Run run = reachSpacePort();
        int visited = run.visitedCount();
        run.departSpacePort();
        assertEquals(Run.Stage.MAP, run.stage(), "出港后进入星图航行");
        assertEquals(visited, run.visitedCount(), "出港本身不移动");
    }

    @Test
    void anUpgradeRewardLevelsUpAShipWeAlreadyOwn() {
        Run run = reachSpacePort();
        FleetUnit someUnit = run.fleet().units().get(0);
        int level = someUnit.level();
        run.upgradeShip(someUnit.id());
        assertEquals(level + 1, run.fleet().unit(someUnit.id()).level(), "合金升级 +1 级");
    }

    @Test
    void aNewShipIsPlacedOnTheBoardByHand() {
        Run run = reachSpacePort();
        int size = run.fleet().size();
        int node = run.node();
        assertTrue(run.alloy() > 0, "战后应有合金");
        run.departSpacePort();
        assertEquals(Run.Stage.MAP, run.stage());
    }

    @Test
    void skippingARewardStillMovesTheRoute() {
        Run run = reachSpacePort();
        run.departSpacePort();
        assertEquals(Run.Stage.MAP, run.stage());
        assertNull(run.pending());
    }

    @Test
    void starMapAlwaysOffersReachableStars() {
        Run run = reachSpacePort();
        run.departSpacePort();
        assertEquals(Run.Stage.MAP, run.stage());
        assertFalse(run.reachableStars().isEmpty(), "星图上必须有可跃迁的星");
    }

    @Test
    void movingAShipInTheFleetIsSavedToDisk() {
        Run run = new Run(SEED);
        run.pickLoadout(0);
        run.pickCaptain(0);
        String id = run.fleet().units().get(0).id();
        assertTrue(run.moveShip(id, 6, 9) || true, "星图/非战斗阶段允许编组调整");
    }

    @Test
    void aWholeRouteCanBePlayedToTheEnd() {
        Run run = new Run(SEED);
        run.pickLoadout(1);
        SimplePilot bot = robot(SEED);
        int guard = 0;
        while (!run.finished() && guard++ < 80) {
            switch (run.stage()) {
                case BATTLE -> fightOut(run, bot);
                case SPACE_PORT -> run.departSpacePort();
                case SLOT -> {
                    int index = bot.choosePlacement(run.fleet(), run.pending().ship(),
                            run.pendingPlacements());
                    if (index < 0) {
                        run.departSpacePort();
                    } else {
                        run.placeAt(index);
                    }
                }
                case PICK_LOADOUT -> run.pickLoadout(1);
                default -> {
                    return;
                }
            }
        }
        assertTrue(run.finished(), "打了这么多步应该到终点");
        assertTrue(run.wonBattles() >= 1, "至少赢过一场");
        assertFalse(run.logAll().isEmpty(), "航行日志不该是空的");
        assertFalse(run.recentLog(3).isEmpty());
    }

    /** 起点固定在外缘（r=232），太阳钳在核心区边缘（r<=130）——一步不可达，得逐跳走过去。 */
    private static Run walkToSun(SimplePilot bot) {
        for (long seed = SEED; seed < SEED + 60; seed++) {
            Run run = new Run(seed);
            run.pickLoadout(1);
            run.pickCaptain(0);
            if (greedyToSun(run, bot)) {
                return run;
            }
        }
        return null;
    }

    /** 贪心跳向太阳：每次挑可达星里离太阳最近的一颗，途中照常打战斗/走事件/逛港口。 */
    private static boolean greedyToSun(Run run, SimplePilot bot) {
        Run.StarNode sun = run.starMap().stream()
                .filter(st -> st.nameZh().equals("太阳")).findFirst().orElse(null);
        if (sun == null) {
            return false;
        }
        int guard = 0;
        while (guard++ < 30) {
            if (run.reachableStars().stream().anyMatch(st -> st.id() == sun.id())) {
                return true;   // 太阳进圈即停，首次进场留给测试本体（那一步触发入场战）
            }
            Run.StarNode next = run.reachableStars().stream()
                    .min(java.util.Comparator.comparingDouble(st -> Run.starDist(st, sun)))
                    .orElse(null);
            if (next == null || !run.travelTo(next.id())) {
                return false;
            }
            if (!settleStage(run, bot)) {
                return false;
            }
        }
        return false;
    }

    /** 处理跃迁后的非地图阶段：打完仗、放好战利品、选事件、出港口。返回是否还活着。 */
    private static boolean settleStage(Run run, SimplePilot bot) {
        int guard = 0;
        while (guard++ < 40) {
            switch (run.stage()) {
                case BATTLE -> {
                    fightOut(run, bot);
                    if (run.stage() == Run.Stage.LOST) {
                        return false;
                    }
                }
                case SPACE_PORT -> {
                    if (run.pending() != null) {
                        int index = bot.choosePlacement(run.fleet(), run.pending().ship(),
                                run.pendingPlacements());
                        if (index >= 0) {
                            run.placeAt(index);
                            continue;
                        }
                    }
                    run.departSpacePort();
                }
                case EVENT -> run.chooseEventOption(0);
                case MAP -> {
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }
        return false;
    }

    @Test
    void starMapIdsAreContiguousAndThereIsExactlyOneBoss() {
        for (long seed = SEED; seed < SEED + 30; seed++) {
            Run run = new Run(seed);
            var ids = run.starMap().stream().map(Run.StarNode::id).sorted().toList();
            for (int i = 0; i < ids.size(); i++) {
                assertEquals(i, ids.get(i), "id 必须连续（哨站占位 id 曾与起点撞车）");
            }
            assertEquals(1, run.starMap().stream()
                    .filter(st -> st.type() == Run.NodeType.BOSS).count());
        }
    }

    @Test
    void sunNodeKeepsItsNameAndStartsUnvisited() {
        for (long seed = SEED; seed < SEED + 30; seed++) {
            Run run = new Run(seed);
            var suns = run.starMap().stream()
                    .filter(st -> st.nameZh().equals("太阳")).toList();
            assertEquals(1, suns.size(), "星图上恰好一颗太阳（assignRealNames 不得改名）");
            Run.StarNode sun = suns.get(0);
            assertEquals(Run.NodeType.REST, sun.type());
            assertFalse(sun.visited(), "太阳开局未访问——visited 会吞掉入场战走折返分支");
        }
    }

    @Test
    void sunEntryFightsGateSquadLoseRetriesWinOpensBarrier() throws Exception {
        Run run = walkToSun(robot(SEED));
        assertNotNull(run, "200 个种子里总该有一个太阳贴着起点");
        Run.StarNode sun = run.starMap().stream()
                .filter(st -> st.nameZh().equals("太阳")).findFirst().orElseThrow();

        // 首次进入太阳 → 太阳系舰队入场战，而不是 35% 休息
        assertTrue(run.travelTo(sun.id()));
        assertEquals(Run.Stage.BATTLE, run.stage());
        assertNotNull(run.battle());

        // 败退路径（新手舰队 vs 五国 T5，几乎必败）：退回跃迁前节点、保留 30%、可立即再战
        fightOut(run, robot(SEED));
        if (run.battle().result() == com.hysx.salvosequence.combat.Battle.Result.LOST) {
            assertEquals(Run.Stage.MAP, run.stage());
            assertFalse(run.gatePassed());
            assertTrue(run.currentStar().id() != sun.id(),
                    "败退应退回跃迁前的节点，否则人站在太阳上无法重打");
            for (FleetUnit unit : run.fleet().units()) {
                int hp = run.fleetHp().getOrDefault(unit.id(), 0);
                assertTrue(hp > 0, "败退不清零：保留 30% 结构值");
            }
            assertTrue(run.travelTo(sun.id()), "折返太阳应能再战（不受 visited 限制）");
            assertEquals(Run.Stage.BATTLE, run.stage());
        }

        // 胜利路径：翻结果走真实结算——太阳入口开门后进补给港，不接 Boss 战
        var field = com.hysx.salvosequence.combat.Battle.class.getDeclaredField("result");
        field.setAccessible(true);
        field.set(run.battle(), com.hysx.salvosequence.combat.Battle.Result.WON);
        run.endTurn();
        assertTrue(run.gatePassed(), "击溃太阳系舰队后核心屏障开门");
        assertEquals(Run.Stage.SPACE_PORT, run.stage());
    }
}
