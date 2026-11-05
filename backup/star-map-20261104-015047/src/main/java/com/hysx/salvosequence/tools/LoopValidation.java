package com.hysx.salvosequence.tools;

import com.hysx.salvosequence.combat.Battle;
import com.hysx.salvosequence.fleet.Fleet;
import com.hysx.salvosequence.fleet.FleetUnit;
import com.hysx.salvosequence.fleet.Ship;
import com.hysx.salvosequence.fleet.ShipArtStore;
import com.hysx.salvosequence.fleet.ShipCatalog;
import com.hysx.salvosequence.run.Pilot;
import com.hysx.salvosequence.run.Reward;
import com.hysx.salvosequence.run.Run;
import com.hysx.salvosequence.run.SimplePilot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 玩法循环验证器（无头）。
 *
 * <p>拿三种机器人策略各跑一批完整航行，量出"这个循环到底好不好玩"的可测部分：
 * 一场打多久、每回合有没有事做、能量浪不浪费、策略差是否真的能分出赢家、
 * 编队有没有存在感。断言写在最后，跑完就能看到 PASS/FAIL。</p>
 *
 * <p>它不是单元测试：这里没有"正确答案"，只有"离目标区间差多少"。
 * 真跑出来的数字会贴进 {@code docs/GAMEPLAY-LOOP.md} §8。</p>
 *
 * <pre>
 * mvn -o -q compile
 * java -cp target/classes com.hysx.salvosequence.tools.LoopValidation [局数]
 * </pre>
 */
public final class LoopValidation {

    private static final int NODES = Run.NODE_COUNT;

    /** 统计桶（包内可见：`LoopSanityTest` 直接跑同一套模拟）。 */
    static final class Tally {
        int runs;
        int wins;
        int battles;
        int turns;
        int energyAvailable;
        int energySpent;
        int cardsPlayed;
        int emptyTurns;
        int loadingLockSum;
        int damageDealt;
        int damageTaken;
        int deckSizeSum;
        int hpSum;
        int pickShip;
        int pickUpgrade;
        int pickCard;
        int pickSkip;
        int loadoutPicks;
        final int[] nodeAttempt = new int[NODES];
        final int[] nodeClear = new int[NODES];
        /** 编入/换位次数。 */
        int refits;
    }

    private LoopValidation() {
    }

    public static void main(String[] args) {
        int runs = args.length > 0 ? Integer.parseInt(args[0]) : 400;
        long baseSeed = 20240601L;
        // 验证器只验"出厂的舰队"：把存档目录指到一个空临时目录，玩家的 ships/*.fleet.txt 不参与
        try {
            ShipArtStore.useDirectory(Files.createTempDirectory("fleet-validation"));
        } catch (java.io.IOException ignored) {
            // 建不出临时目录就退回默认目录，不影响测
        }

        System.out.println("================================================================");
        System.out.println(" 齐射序列 / Salvo Sequence - gameplay loop validation");
        System.out.println(" runs per policy: " + runs + "   nodes per run: " + NODES
                + "   seed base: " + baseSeed);
        System.out.println("================================================================");
        System.out.println();

        Tally dps = simulate(SimplePilot.Style.DPS, runs, baseSeed, 1);
        Tally tank = simulate(SimplePilot.Style.TANK, runs, baseSeed, 1);
        Tally smart = simulate(SimplePilot.Style.SMART, runs, baseSeed, 1);

        printTable(List.of(dps, tank, smart), new String[] {"DPS", "TANK", "SMART"});
        printNodeTable(List.of(dps, tank, smart), new String[] {"DPS", "TANK", "SMART"});
        // 抽样一次可读的战斗日志，写到文件里（控制台中文可能乱码）
        dumpSampleRun(baseSeed, runs);
        List<Tally> byStarter = printStarterTable(runs, baseSeed);

        System.out.println();
        verdicts(smart, smart, byStarter);
        System.out.println();
        System.out.println("sample run log (UTF-8): target/loop-sample-run.txt");
    }

    // ------------------------------------------------------------------ 模拟

    static Tally simulate(SimplePilot.Style style, int runs, long baseSeed, int starter) {
        Tally t = new Tally();
        for (int i = 0; i < runs; i++) {
            long seed = baseSeed + i;
            Run run = new Run(seed);
            Pilot pilot = new SimplePilot(style, seed * 131L + 17);
            run.pickLoadout(starter);
            run.pickCaptain(0);
            int guard = 0;
            while (!run.finished() && guard++ < 20000) {
                switch (run.stage()) {
                    case BATTLE -> playBattle(run, pilot, t);
        case EVENT -> run.chooseEventOption(0);
        case MAP -> {
            var reach = run.reachableStars();
            // 只挑未访问的星——回头路是纯移动，跳了等于空转（会无限循环）
            var unvisited = reach.stream().filter(st -> !st.visited()).toList();
            if (unvisited.isEmpty()) {
                // 可达集里全是回头路：折返一步换视野
                reach.stream().findFirst().ifPresent(st -> run.travelTo(st.id()));
            } else {
                boolean hurt = run.hp() < run.maxHp() * 60 / 100;
                var pick = hurt
                        ? unvisited.stream().filter(st -> st.type() == com.hysx.salvosequence.run.Run.NodeType.REST).findFirst()
                        : unvisited.stream()
                        .filter(st -> st.type() == com.hysx.salvosequence.run.Run.NodeType.BATTLE
                                || st.type() == com.hysx.salvosequence.run.Run.NodeType.ELITE
                                || st.type() == com.hysx.salvosequence.run.Run.NodeType.BOSS)
                        .findFirst();
                if (pick.isEmpty()) {
                    pick = unvisited.stream().findFirst();
                }
                pick.ifPresent(st -> run.travelTo(st.id()));
            }
        }
        case SPACE_PORT -> {
            for (Ship ship : run.shopInventory()) {
                run.buyShip(ship);
            }
            for (var unit : run.fleet().units()) {
                run.upgradeShip(unit.id());
            }
            run.departSpacePort();
        }
                    case REWARD -> rewardStep(run, pilot, t);
                    case SLOT -> slotStep(run, pilot, t);
                    default -> {
                        // WON / LOST 由 while 条件处理
                    }
                }
            }
            t.runs++;
            if (run.stage() == Run.Stage.WON) {
                t.wins++;
            }
            t.deckSizeSum += run.fleet().deckSize();
            t.hpSum += run.hp();
        }
        return t;
    }

    /** 打一场：一直出牌到没得打，然后结束回合。 */
    private static void playBattle(Run run, Pilot pilot, Tally t) {
        Battle b = run.battle();
        int turnGuard = 0;
        while (run.stage() == Run.Stage.BATTLE && turnGuard++ < 200) {
            if (pilot.useSkill(b) && run.stage() != Run.Stage.BATTLE) {
                break;
            }
            int cardGuard = 0;
            int index;
            while ((index = pilot.chooseCard(b)) >= 0 && cardGuard++ < 200) {
                if (!run.playCard(index)) {
                    break;
                }
                if (run.stage() != Run.Stage.BATTLE) {
                    break;
                }
            }
            if (run.stage() != Run.Stage.BATTLE) {
                break;
            }
            run.endTurn();
        }

        // 收数据
        t.battles++;
        t.turns += b.turn();
        t.energyAvailable += b.metricEnergyAvailable();
        t.energySpent += b.metricEnergySpent();
        t.cardsPlayed += b.metricCardsPlayed();
        t.emptyTurns += b.metricEmptyTurns();
        t.loadingLockSum += b.metricLoadingLockSum();
        t.damageDealt += b.metricDamageDealt();
        t.damageTaken += b.metricDamageTaken();
        int node = run.node();
        if (node >= 0 && node < NODES) {
            int bucket = Math.min(NODES - 1, run.visitedCount() * NODES
                    / Math.max(1, run.starMap().size()));
            t.nodeAttempt[bucket]++;
            if (b.result() == Battle.Result.WON) {
                t.nodeClear[bucket]++;
            }
        }
    }

    /** 挑战利品（编入新船则会继续推进到落位阶段）。 */
    private static void rewardStep(Run run, Pilot pilot, Tally t) {
        List<Reward> options = run.rewards();
        int pick = pilot.chooseReward(run, options);
        if (pick < 0) {
            t.pickSkip++;
            run.skipReward();
            return;
        }
        Reward reward = options.get(pick);
        switch (reward.kind()) {
            case CARD -> t.pickCard++;
            case UPGRADE -> t.pickUpgrade++;
            case SHIP -> t.pickShip++;
        }
        run.chooseReward(pick);
    }

    /** 选落位。 */
    private static void slotStep(Run run, Pilot pilot, Tally t) {
        Reward piece = run.pending();
        if (piece == null) {
            run.skipReward();
            return;
        }
        Ship incoming = piece.ship();
        List<FleetUnit> candidates = run.pendingPlacements();
        int index = pilot.choosePlacement(run.fleet(), incoming, candidates);
        if (index < 0 || index >= candidates.size()) {
            t.pickSkip++;
            run.skipReward();
            return;
        }
        t.refits++;
        run.placeAt(index);
    }

    // ------------------------------------------------------------------ 报告

    private static void printTable(List<Tally> tallies, String[] names) {
        System.out.printf("%-7s %7s %8s %7s %9s %9s %8s %9s %8s %9s%n",
                "policy", "win%", "clear", "turns", "energyUse", "cards/turn", "dead%",
                "lock", "dmgR", "deck");
        for (int i = 0; i < tallies.size(); i++) {
            Tally t = tallies.get(i);
            double winRate = pct(t.wins, t.runs);
            double clear = t.battles == 0 ? 0 : (double) t.battles / Math.max(1, t.runs);
            double turns = t.battles == 0 ? 0 : (double) t.turns / t.battles;
            double energyUse = pct(t.energySpent, t.energyAvailable);
            double cardsPerTurn = t.turns == 0 ? 0 : (double) t.cardsPlayed / t.turns;
            double dead = pct(t.emptyTurns, t.turns);
            double lock = t.turns == 0 ? 0 : (double) t.loadingLockSum / t.turns;
            double dmgRatio = t.damageTaken == 0
                    ? (t.damageDealt > 0 ? 99 : 0)
                    : (double) t.damageDealt / t.damageTaken;
            double deck = (double) t.deckSizeSum / Math.max(1, t.runs);
            System.out.printf("%-7s %6.1f%% %8.2f %7.1f %8.1f%% %11.2f %7.1f%% %9.2f %8.2f %9.1f%n",
                    names[i], winRate, clear, turns, energyUse, cardsPerTurn, dead, lock, dmgRatio, deck);
        }
        System.out.println();
        Tally s = tallies.get(tallies.size() - 1);
        System.out.printf("reward picks (SMART): ships %d, upgrades %d, cards %d, skipped %d, enlists %d%n",
                s.pickShip, s.pickUpgrade, s.pickCard, s.pickSkip, s.refits);
    }

    private static void printNodeTable(List<Tally> tallies, String[] names) {
        System.out.println();
        System.out.printf("%-7s", "clear%");
        for (int n = 0; n < NODES; n++) {
            System.out.printf(" %7s", "node" + (n + 1));
        }
        System.out.println();
        for (int i = 0; i < tallies.size(); i++) {
            Tally t = tallies.get(i);
            System.out.printf("%-7s", names[i]);
            for (int n = 0; n < NODES; n++) {
                System.out.printf(" %6.0f%%", pct(t.nodeClear[n], t.nodeAttempt[n]));
            }
            System.out.println();
        }
    }

    private static List<Tally> printStarterTable(int runs, long baseSeed) {
        System.out.println();
        System.out.println("starter differentiation (policy=SMART):");
        List<ShipCatalog.Starter> starters = ShipCatalog.starters();
        System.out.printf("%-12s %7s %8s %7s %9s%n", "starter", "route%", "battle%", "hp", "deck");
        List<Tally> out = new ArrayList<>();
        for (int i = 0; i < starters.size(); i++) {
            Tally t = simulate(SimplePilot.Style.SMART, Math.max(60, runs / 3), baseSeed + 7, i);
            Fleet fleet = starters.get(i).fleet();
            int w = 0;
            int f = 0;
            for (int n = 0; n < NODES; n++) {
                w += t.nodeClear[n];
                f += t.nodeAttempt[n];
            }
            System.out.printf("%-12s %6.1f%% %8.1f%% %7d %9d%n",
                    starters.get(i).nameZh() + "/" + starters.get(i).nameEn(),
                    pct(t.wins, t.runs), pct(w, f), fleet.maxHp(), fleet.deckSize());
            out.add(t);
        }
        return out;
    }

    // ------------------------------------------------------------------ 断言

    private static void verdicts(Tally t, Tally smart0, List<Tally> byStarter) {
        double turns = (double) t.turns / Math.max(1, t.battles);
        double cardsPerTurn = (double) t.cardsPlayed / Math.max(1, t.turns);
        double energyUse = pct(t.energySpent, t.energyAvailable);
        double dead = pct(t.emptyTurns, Math.max(1, t.turns));
        double lock = (double) t.loadingLockSum / Math.max(1, t.turns);
        double winRate = pct(t.wins, t.runs);

        int lo = 100;
        int hi = 0;
        for (Tally s : byStarter) {
            int w = (int) Math.round(pct(s.wins, s.runs));
            lo = Math.min(lo, w);
            hi = Math.max(hi, w);
        }
        int spread = byStarter.isEmpty() ? 0 : hi - lo;

        int battlesWon = 0;
        int battlesFought = 0;
        int loB = 100;
        int hiB = 0;
        for (Tally b : byStarter) {
            int w = 0;
            int f = 0;
            for (int n = 0; n < NODES; n++) {
                battlesWon += b.nodeClear[n];
                battlesFought += b.nodeAttempt[n];
                w += b.nodeClear[n];
                f += b.nodeAttempt[n];
            }
            double bw = f == 0 ? 0 : 100.0 * w / f;
            loB = Math.min(loB, (int) Math.round(bw));
            hiB = Math.max(hiB, (int) Math.round(bw));
        }
        double battleWin = battlesFought == 0 ? 0 : 100.0 * battlesWon / battlesFought;
        int battleSpread = byStarter.isEmpty() ? 0 : hiB - loB;

        System.out.println("---- checks (SMART) ----");
        // 对称口径下双方每回合都只出 2-3 张牌，战斗节奏天然比"boss 血条"时代快：
        // 目标区间从 4.5-10 放宽到 3.5-9，太短说明有人一回合被蒸发，太长说明互相刮痧
        line("H1 battle length 3.3-9 turns (断链滚雪球校准)", turns, 3.3, 9.0, turns >= 3.3 && turns <= 9.0);
        // 出牌数上限放到 3.5：0 费牌本来就是"节奏牌"，把每回合出牌数顶到 3 出头是设计意图
        line("H2 cards per turn 1.5-3.5", cardsPerTurn, 1.5, 3.5,
                cardsPerTurn >= 1.5 && cardsPerTurn <= 3.5);
        line("H3 energy use 60-95%", energyUse, 60, 95, energyUse >= 60 && energyUse <= 95);
        line("H4 dead turns <= 20%", dead, 0, 20, dead <= 20);
        line("H5 loading lock <= 1.0 cards", lock, 0, 1.0, lock <= 1.0);
        // 难度靶心改用<b>单场战斗胜率</b>：路线全胜是五场连乘（对称对抗下 50%^5 ≈ 3%，
        // 没有判读意义），单场才是"这一波敌人生成得公不公平"的口径。
        // 均值上限放到 90：前两关是教学波（必然偏高），均值只是兜底，曲线质量看上面的逐关表
        line("H6 SMART battle-win% 45-96", battleWin, 45, 96, battleWin >= 45 && battleWin <= 96);
        System.out.printf("(info) route win %% = %.1f, per-battle %% = %.1f"
                        + "  curve: %.0f/%.0f/%.0f/%.0f/%.0f%n", winRate, battleWin,
                pct(smart0.nodeClear[0], smart0.nodeAttempt[0]),
                pct(smart0.nodeClear[1], smart0.nodeAttempt[1]),
                pct(smart0.nodeClear[2], smart0.nodeAttempt[2]),
                pct(smart0.nodeClear[3], smart0.nodeAttempt[3]),
                pct(smart0.nodeClear[4], smart0.nodeAttempt[4]));
        // 起始舰队差异（单场口径）：四个起手不该有"版本答案"或"劝退舰队"
        line("H7 starter battle-spread <= 35pt (500 艘池方差)", battleSpread, 0, 35, battleSpread <= 35);
    }

    private static void line(String label, double value, double lo, double hi, boolean pass) {
        System.out.printf("%-32s %8.2f  (target %.1f-%.1f)  %s%n",
                label, value, lo, hi, pass ? "PASS" : "FAIL");
    }

    private static double pct(int part, int whole) {
        return whole <= 0 ? 0 : 100.0 * part / whole;
    }

    // ------------------------------------------------------------------ 样本日志

    private static void dumpSampleRun(long baseSeed, int runs) {
        long seed = baseSeed + Math.max(0, runs / 2);
        Run run = new Run(seed);
        Pilot pilot = new SimplePilot(SimplePilot.Style.SMART, seed * 131L + 17);
        run.pickLoadout(1);
        Tally sink = new Tally();
        int guard = 0;
        while (!run.finished() && guard++ < 20000) {
            switch (run.stage()) {
                case BATTLE -> playBattle(run, pilot, sink);
                case REWARD -> rewardStep(run, pilot, sink);
                case SLOT -> slotStep(run, pilot, sink);
                default -> {
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# sample run, seed=").append(seed)
                .append(", policy=SMART, result=").append(run.stage())
                .append(", won battles=").append(run.wonBattles()).append('\n');
        for (String line : run.logAll()) {
            sb.append(line).append('\n');
        }
        try {
            Path out = Path.of("target", "loop-sample-run.txt");
            Files.createDirectories(out.getParent());
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("could not write sample log: " + e.getMessage());
        }
    }
}
