package com.hysx.salvosequence.tools;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hysx.salvosequence.run.SimplePilot;
import org.junit.jupiter.api.Test;

/**
 * 把"无头验证器"塞进 {@code mvn test}：跑一个小样本，只看**宽的**区间。
 *
 * <p>为什么不照抄 {@code LoopValidation} 的 H1–H7：那些阈值是按 400~1000 局标定的，
 * 样本一缩小（这里 60 局）噪声就吃掉几个百分点，卡死会天天红。这里只守四件事：
 * 机器人打得动、回合数没崩、能量没有浪费到离谱、没有哪艘起始舰完全不能玩。
 * 真正的数值平衡仍然靠手动跑 {@code LoopValidation 1000}。</p>
 */
class LoopSanityTest {

    private static final int RUNS = 150;

    /**
     * 用临时目录隔离玩家存档。
     *
     * <p>开发机上 `ships/*.layout.txt` 是我自己调过的布局（比如把炮都堆在一条走廊里），
     * 不隔离的话这个"冒烟测试"测的就不是出厂数值了——同一份代码在干净机器上会给出完全不同的胜率。</p>
     */
    @org.junit.jupiter.api.BeforeAll
    static void isolateSaves() throws java.io.IOException {
        com.hysx.salvosequence.fleet.ShipArtStore.useDirectory(
                java.nio.file.Files.createTempDirectory("hull-sanity"));
    }
    private static final long SEED = 20240601L;

    /** 单场战斗胜率（Tally 里逐关累计）：对称对抗的口径，路线全胜是五场连乘没有判读意义。 */
    private static double battleWinRate(LoopValidation.Tally t) {
        int won = 0;
        int fought = 0;
        for (int n = 0; n < t.nodeAttempt.length; n++) {
            won += t.nodeClear[n];
            fought += t.nodeAttempt[n];
        }
        return fought == 0 ? 0 : 100.0 * won / fought;
    }

    @Test
    void theSmartPilotWinsASaneShareOfBattles() {
        LoopValidation.Tally smart = LoopValidation.simulate(SimplePilot.Style.SMART, RUNS, SEED, 1);
        double winRate = battleWinRate(smart);
        assertTrue(winRate > 40.0, "SMART 单场胜率太低，循环可能被卡住了：" + winRate);
        assertTrue(winRate < 100.0, "SMART 单场几乎全胜说明难度被推平了：" + winRate);
    }

    @Test
    void battlesDoNotCollapseIntoOneTurnNorDragForever() {
        LoopValidation.Tally smart = LoopValidation.simulate(SimplePilot.Style.SMART, RUNS, SEED, 1);
        double turns = smart.turns / (double) Math.max(1, smart.battles);
        assertTrue(turns >= 3.0 && turns <= 12.0, "每场回合数跑偏：" + turns);
    }

    @Test
    void energyIsMostlySpentInsteadOfPilingUp() {
        LoopValidation.Tally smart = LoopValidation.simulate(SimplePilot.Style.SMART, RUNS, SEED, 1);
        double use = smart.energySpent * 100.0 / Math.max(1, smart.energyAvailable);
        assertTrue(use > 50.0, "能量使用率太低，说明手牌常常打不出来：" + use);
        assertTrue(use <= 100.0, "能量花超了：" + use);
    }

    @Test
    void theDeckActuallyGrowsAlongTheRoute() {
        LoopValidation.Tally smart = LoopValidation.simulate(SimplePilot.Style.SMART, RUNS, SEED, 1);
        double deck = smart.deckSizeSum / (double) Math.max(1, smart.runs);
        assertTrue(deck > 10.0, "卡组没有成长，战利品循环可能断了：" + deck);
    }

    @Test
    void everyStarterIncludingTheAxialGunIsPlayable() {
        int starters = com.hysx.salvosequence.fleet.ShipCatalog.starters().size();
        assertTrue(starters >= 4, "起始舰队少了一支：" + starters);
        for (int i = 0; i < starters; i++) {
            LoopValidation.Tally tally = LoopValidation.simulate(SimplePilot.Style.SMART, RUNS, SEED, i);
            double winRate = battleWinRate(tally);
            assertTrue(winRate > 40.0,
                    "起始舰队 #" + i + " 几乎打不动（单场 " + winRate + "%），检查它的编成："
                            + com.hysx.salvosequence.fleet.ShipCatalog.starters().get(i).nameZh());
        }
    }
}
