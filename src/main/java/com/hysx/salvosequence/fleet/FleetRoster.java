package com.hysx.salvosequence.fleet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一支舰队在战斗中的<b>可变状态</b>：每艘船的结构值、护盾。
 *
 * <p>改造前的 Roster 里"主力舰"和"僚机"是两种东西（主力舰承伤口径是血条、僚机只是挡箭牌）。
 * 改造后一律平等：每艘船都有自己的结构值，挨打按棋盘次序（前排先）落，溢出会续打到下一艘，
 * <b>全部沉没才算输</b>。</p>
 *
 * <p>{@link Fleet} 是"这支舰队是什么"，本类是"它现在还剩多少"。</p>
 */
public final class FleetRoster {

    /**
     * 一艘船的运行时状态。
     */
    public static final class Unit {

        private final FleetUnit ship;
        private final int maxHp;
        private final int armor;
        private final int volley;
        private final int plate;
        private final int flak;
        private int hp;
        private int block;
        /** 自上次格挡刷新以来是否被打到过（护盾窗口：受损的船下回合回盾减半）。 */
        private boolean hitSinceRefresh;

        private Unit(FleetUnit ship, int maxHp, int armor, int volley, int plate, int flak, int hp) {
            this.ship = ship;
            this.maxHp = maxHp;
            this.armor = armor;
            this.volley = volley;
            this.plate = plate;
            this.flak = flak;
            this.hp = Math.max(0, Math.min(maxHp, hp));
            this.block = armor;
        }

        /** 棋盘上的那艘船。 */
        public FleetUnit ship() {
            return ship;
        }

        /** 船型 id。 */
        public String id() {
            return ship.id();
        }

        /** 舰名。 */
        public String nameZh() {
            return ship.nameZh();
        }

        /** 带等级读数的舰名。 */
        public String labelZh() {
            return ship.labelZh();
        }

        /** 结构值上限（含相邻加成）。 */
        public int maxHp() {
            return maxHp;
        }

        /** 当前结构值。 */
        public int hp() {
            return hp;
        }

        /** 当前护盾。 */
        public int block() {
            return block;
        }

        /** 每回合刷新的护盾值。 */
        public int armor() {
            return armor;
        }

        /** 防御力（基础 + 光环：每段伤害先减这一格，最低 1）。 */
        public int plate() {
            return plate;
        }

        /** 防空（基础 + 光环：编队护卫弹幕的一部分）。 */
        public int flak() {
            return flak;
        }

        /** 齐射值（含相邻加成）。 */
        public int volley() {
            return volley;
        }

        /** 还在不在。 */
        public boolean alive() {
            return hp > 0;
        }

        /** 血条比例（0~1）。 */
        public double hpRatio() {
            return maxHp <= 0 ? 0 : Math.max(0, Math.min(1, (double) hp / maxHp));
        }

        @Override
        public String toString() {
            return labelZh() + " " + hp + "/" + maxHp;
        }
    }

    private Fleet fleet;
    private List<Unit> units;

    private FleetRoster(Fleet fleet, List<Unit> units) {
        this.fleet = fleet;
        this.units = List.copyOf(units);
    }

    /**
     * 变阵：把某艘船挪到新落位。
     *
     * <p>站位就是战术——相邻加成（装甲舰贴指挥舰 +2 结构值、炮舰贴装甲舰 +1 齐射、
     * 炮舰贴动力舰 +1 护盾）靠摆位置挣。结构值/护盾上限在战斗里<b>只涨不跌</b>：
     * 把船挪开不会倒扣已经拿到的加成，否则拖一下棋子就掉血，看着像 bug。
     * 齐射值照实重算（它不是一个会被消耗的资源）。
     *
     * @return 挪不动（越界或压住别的船）返回 false
     */
    public boolean reposition(String shipId, int x, int y) {
        Fleet moved = fleet.movedUnit(shipId, x, y);
        if (moved == null) {
            return false;
        }
        Map<String, Integer> hp = new HashMap<>();
        Map<String, Integer> block = new HashMap<>();
        Map<String, Integer> max = new HashMap<>();
        Map<String, Integer> armor = new HashMap<>();
        for (Unit u : units) {
            hp.put(u.id(), u.hp());
            block.put(u.id(), u.block());
            max.put(u.id(), u.maxHp());
            armor.put(u.id(), u.armor());
        }
        fleet = moved;
        List<Unit> rebuilt = new ArrayList<>(moved.size());
        for (FleetUnit ship : moved.units()) {
            int cap = Math.max(max.getOrDefault(ship.id(), 0), moved.maxHpOf(ship));
            int shield = Math.max(armor.getOrDefault(ship.id(), 0), moved.armorOf(ship));
            int carried = Math.min(cap, Math.max(0, hp.getOrDefault(ship.id(), cap)));
            Unit fresh = new Unit(ship, cap, shield, moved.volleyOf(ship),
                    moved.plateOf(ship), moved.flakOf(ship), carried);
            fresh.block = Math.max(0, block.getOrDefault(ship.id(), fresh.block));
            rebuilt.add(fresh);
        }
        units = List.copyOf(rebuilt);
        return true;
    }

    /** 满状态出战。 */
    public static FleetRoster of(Fleet fleet) {
        return of(fleet, Map.of());
    }

    /**
     * 带着上一场剩下的结构值出战。
     *
     * @param hpByUnitId 船型 id → 剩余结构值；缺的、非法的按满血算
     */
    public static FleetRoster of(Fleet fleet, Map<String, Integer> hpByUnitId) {
        return of(fleet, hpByUnitId, 0);
    }

    /**
     * 带着上一场剩下的结构值出战，并给全舰加一个结构值上限加成（舰长技能）。
     *
     * @param hpByUnitId 船型 id → 剩余结构值（已含加成）；缺的、非法的按满血算
     * @param maxHpBonus 每艘船的结构值上限加成（不重复补当前值，当前值由 Run 维护）
     */
    public static FleetRoster of(Fleet fleet, Map<String, Integer> hpByUnitId, int maxHpBonus) {
        if (fleet == null) {
            throw new IllegalArgumentException("舰队不能是 null");
        }
        int bonus = Math.max(0, maxHpBonus);
        List<Unit> out = new ArrayList<>(fleet.size());
        for (FleetUnit ship : fleet.units()) {
            int max = fleet.maxHpOf(ship) + bonus;
            Integer carried = hpByUnitId == null ? null : hpByUnitId.get(ship.id());
            int hp = carried == null ? max : Math.min(max, Math.max(0, carried));
            out.add(new Unit(ship, max, fleet.armorOf(ship), fleet.volleyOf(ship),
                    fleet.plateOf(ship), fleet.flakOf(ship), hp));
        }
        return new FleetRoster(fleet, out);
    }

    /** 舰队定义。 */
    public Fleet fleet() {
        return fleet;
    }

    /** 每艘船的运行状态。 */
    public List<Unit> units() {
        return units;
    }

    /** 盖住某格的那艘船的状态。 */
    public Unit at(int cx, int cy) {
        for (Unit u : units) {
            if (u.ship.covers(cx, cy)) {
                return u;
            }
        }
        return null;
    }

    /** 按船型 id 找状态。 */
    public Unit byId(String unitId) {
        for (Unit u : units) {
            if (u.id().equals(unitId)) {
                return u;
            }
        }
        return null;
    }

    /** 还活着几艘。 */
    public int aliveCount() {
        int n = 0;
        for (Unit u : units) {
            if (u.alive()) {
                n++;
            }
        }
        return n;
    }

    /** 一共几艘。 */
    public int maxCount() {
        return units.size();
    }

    /** 全都沉了。 */
    public boolean wiped() {
        return aliveCount() == 0;
    }

    /** 全队当前结构值合计。 */
    public int totalHp() {
        int sum = 0;
        for (Unit u : units) {
            sum += Math.max(0, u.hp);
        }
        return sum;
    }

    /** 全队结构值上限合计。 */
    public int totalMaxHp() {
        int sum = 0;
        for (Unit u : units) {
            sum += u.maxHp;
        }
        return sum;
    }

    /** 全队护盾合计。 */
    public int totalBlock() {
        int sum = 0;
        for (Unit u : units) {
            sum += u.block;
        }
        return sum;
    }

    /** 全队防空之和（受击侧的护卫弹幕：把对方多段攻击的生效段数砍掉）。 */
    public int flakTotal() {
        int sum = 0;
        for (Unit u : units) {
            if (u.alive()) {
                sum += u.flak();
            }
        }
        return sum;
    }

    /** 代表舰（旧旗舰概念；旗舰现在是普通船，仅供旧技能兜底）。 */
    public Unit flag() {
        for (Unit u : units) {
            if (u.ship.isFlag()) {
                return u;
            }
        }
        return units.isEmpty() ? null : pick(false);
    }

    /**
     * 每回合开局刷新格挡（护盾窗口）：上回合被打到过的船回盾减半——
     * 分散输出会被回盾吃掉，同回合集中齐射才有意义。返回减半的船数。
     */
    public int refreshBlock() {
        int halved = 0;
        for (Unit u : units) {
            if (!u.alive()) {
                continue;
            }
            if (u.hitSinceRefresh) {
                u.block = Math.max(0, u.armor / 2);
                halved++;
            } else {
                u.block = u.armor;
            }
            u.hitSinceRefresh = false;
        }
        return halved;
    }

    /** 给某一艘加护盾。 */
    public void addBlock(String unitId, int amount) {
        Unit u = byId(unitId);
        if (u != null && u.alive()) {
            u.block += Math.max(0, amount);
        }
    }

    /** 给全队加护盾。 */
    public void addBlockToAll(int amount) {
        for (Unit u : units) {
            if (u.alive()) {
                u.block += Math.max(0, amount);
            }
        }
    }

    /** 修某一艘（受上限约束）。 */
    public void heal(String unitId, int amount) {
        Unit u = byId(unitId);
        if (u != null && u.alive()) {
            u.hp = Math.min(u.maxHp, u.hp + Math.max(0, amount));
        }
    }

    /**
     * 承伤目标：前排先挨打；{@code fromBack} 时从后排往前挑（敌方"锁后排"）。
     *
     * <p>我方朝右，所以<b>右侧一列是前排</b>：x 大者先，同列从上往下（y 小者先）；
     * 敌方"锁后排"则反过来从 x 小的一列开始。</p>
     */
    public Unit pick(boolean fromBack) {
        Unit best = null;
        for (Unit u : units) {
            if (!u.alive()) {
                continue;
            }
            if (best == null) {
                best = u;
                continue;
            }
            int cmp = compare(u, best);
            if (fromBack ? cmp > 0 : cmp < 0) {
                best = u;
            }
        }
        return best;
    }

    /**
     * 挨一次伤害：先削护盾、再扣结构值，扣穿了继续打下一艘（溢出续打）。
     *
     * @param amount   总伤害
     * @param fromBack true = 从后排开始扣
     * @return 实际掉的结构值（不含被护盾吃掉的）
     */
    public int applyIncoming(int amount, boolean fromBack) {
        return soak(amount, false, fromBack).lost();
    }

    /**
     * 一次承伤的读数。
     *
     * @param lost     实际掉的结构值（不含被护盾吃掉的）
     * @param leftover 打不出去的余量（全队沉光后剩余的部分）
     * @param overflow 击沉瞬间还在外溢的余量（系统损伤的判定依据）
     */
    public record Soak(int lost, int leftover, int overflow) {
    }

    /**
     * 挨一段已折算的伤害（类型倍率与防御力在调用侧算完，这里只做 盾→体 的链式吸收）。
     *
     * <p>穿透（{@code pierce}）跳过护盾直扣结构值。击沉一艘还有余量就续打下一艘，
     * 击沉瞬间的余量记进 {@link Soak#overflow}。</p>
     */
    public Soak soak(int amount, boolean pierce, boolean fromBack) {
        int remaining = Math.max(0, amount);
        int lost = 0;
        int overflow = 0;
        while (remaining > 0) {
            Unit u = pick(fromBack);
            if (u == null) {
                return new Soak(lost, remaining, overflow);
            }
            int absorbed = pierce ? 0 : Math.min(u.block, remaining);
            u.block -= absorbed;
            remaining -= absorbed;
            int applied = Math.min(remaining, u.hp);
            u.hp -= applied;
            if (u.block <= 0 && u.armor > 0) {
                u.hitSinceRefresh = true;   // 盾被击穿：下回合回充被打断
            }
            remaining -= applied;
            lost += applied;
            if (u.hp <= 0 && remaining > 0) {
                overflow += remaining;
            }
            if (remaining > 0 && u.hp > 0) {
                // 这艘还没沉、伤害却还有余量：只可能是伤害为 0 的极端情况，防死循环
                break;
            }
        }
        return new Soak(lost, 0, overflow);
    }

    /** 各船剩余结构值快照（跨关卡带着走）。 */
    public Map<String, Integer> hpSnapshot() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Unit u : units) {
            out.put(u.id(), Math.max(0, u.hp));
        }
        return Map.copyOf(out);
    }

    /** 调试用字符画：{@code #} = 有血，{@code x} = 沉了。 */
    public String ascii() {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < FleetBoard.SIZE; y++) {
            for (int x = 0; x < FleetBoard.SIZE; x++) {
                Unit u = at(x, y);
                sb.append(u == null ? '.' : u.alive() ? '#' : 'x');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * 前后排比较：我方朝右，以舰体**右边缘**（x + w）为准，越靠右越靠前；
     * 右边缘相同再从上往下（y 小者先）。
     */
    private static int compare(Unit a, Unit b) {
        FleetUnit sa = a.ship;
        FleetUnit sb2 = b.ship;
        if (sa.x2() != sb2.x2()) {
            return Integer.compare(sb2.x2(), sa.x2());
        }
        return Integer.compare(sa.y(), sb2.y());
    }
}
