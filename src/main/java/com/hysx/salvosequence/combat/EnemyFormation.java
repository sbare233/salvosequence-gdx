package com.hysx.salvosequence.combat;

import com.hysx.salvosequence.fleet.Fleet;
import com.hysx.salvosequence.fleet.FleetBoard;
import com.hysx.salvosequence.fleet.FleetUnit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 敌方编队在棋盘上的落位——<b>按光环协同贪心摆出最优阵</b>。
 *
 * <p>改造前是"面积大的先摆、从最上一行开始一行一行往下铺"；现在敌我同源之后
 * 敌舰也带光环，所以铺位换成了协同版贪心：面积大的先当锚（大船不好塞），
 * 每一艘都试遍全部合法落点，挑<b>摆下去之后全队能叠到的战斗光环总量</b>最高的那格；
 * 光环分打平时沿用旧口径——靠上（先接敌）、靠右。</p>
 *
 * <p>一条约束不变：棋子上按<b>舰图 id</b> 区分船（{@code FleetUnit.id()} 就是舰图 id），
 * 同一关里不能出现两艘用同一张舰图的敌舰；真出现时这里会抛 {@code IllegalStateException}。</p>
 */
public final class EnemyFormation {

    /** 棋盘边长。 */
    public static final int SIZE = FleetBoard.SIZE;

    private EnemyFormation() {
    }

    /** 一艘敌舰的占地（宽, 高）。 */
    public static int[] footprintOf(Enemy enemy) {
        return new int[]{enemy.w(), enemy.h()};
    }

    /**
     * 把一关的编队摆进 16×16 棋盘。
     *
     * @return 与 {@code squad} <b>同序</b>的棋子（{@code Battle.Foe} 直接按下标对目标）
     * @throws IllegalStateException 棋盘塞不下
     */
    public static List<FleetUnit> layout(List<Enemy> squad) {
        if (squad == null || squad.isEmpty()) {
            return List.of();
        }
        List<Enemy> order = new ArrayList<>(squad);
        order.sort((a, b) -> {
            if (a.cells() != b.cells()) {
                return Integer.compare(b.cells(), a.cells());
            }
            int pa = perkScoreOf(a.image());
            int pb = perkScoreOf(b.image());
            if (pa != pb) {
                return Integer.compare(pb, pa);
            }
            return a.id().compareTo(b.id());
        });
        FleetBoard board = FleetBoard.empty();
        Map<String, FleetUnit> placed = new LinkedHashMap<>();
        for (Enemy enemy : order) {
            if (placed.containsKey(enemy.id())) {
                throw new IllegalStateException("「" + enemy.nameZh() + "」在 " + SIZE + "x" + SIZE
                        + " 棋盘上摆不下：同一关里两艘敌舰共用了舰图 " + enemy.image().id());
            }
            FleetUnit best = null;
            int bestScore = Integer.MIN_VALUE;
            for (int y = 0; y + enemy.h() <= SIZE; y++) {
                for (int x = SIZE - enemy.w(); x >= 0; x--) {
                    FleetUnit candidate = new FleetUnit(enemy.image(), 1, x, y);
                    if (!board.canPlace(candidate)) {
                        continue;
                    }
                    int score = synergyScore(board, candidate);
                    if (score > bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                    // 遍历顺序（行从上往下、列从右往左）+ 严格大于 = 平手时取更靠上、更靠右的落点
                }
            }
            if (best == null) {
                throw new IllegalStateException("「" + enemy.nameZh() + "」在 " + SIZE + "x" + SIZE
                        + " 棋盘上摆不下");
            }
            board = board.with(best);
            placed.put(enemy.id(), best);
        }
        List<FleetUnit> out = new ArrayList<>(squad.size());
        for (Enemy enemy : squad) {
            out.add(placed.get(enemy.id()));
        }
        return List.copyOf(out);
    }

    /** 候选落点的协同分：摆下去之后，全队（含它自己）能叠到的战斗光环总量。 */
    private static int synergyScore(FleetBoard board, FleetUnit candidate) {
        Fleet probe = Fleet.of("probe", "协同试摆", "SYNERGY PROBE", board.with(candidate).units());
        int score = probe.perksOn(candidate).combatScore();
        for (FleetUnit other : probe.units()) {
            if (!other.id().equals(candidate.id())) {
                score += probe.perksOn(other).combatScore();
            }
        }
        return score;
    }

    /** 一张船图自带的光环价值（出场顺序里同面积时优先摆光环大的）。 */
    private static int perkScoreOf(com.hysx.salvosequence.fleet.Ship ship) {
        int score = 0;
        for (com.hysx.salvosequence.fleet.Perk perk : ship.perks()) {
            score += perk.value();
        }
        return score;
    }

    /** 一组棋子铺成的字符画（调试/核对稿件用）。 */
    public static String ascii(List<FleetUnit> units) {
        return FleetBoard.of(units).ascii();
    }
}
