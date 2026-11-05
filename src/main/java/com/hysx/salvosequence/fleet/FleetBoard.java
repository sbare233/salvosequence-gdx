package com.hysx.salvosequence.fleet;

import java.util.ArrayList;
import java.util.List;

/**
 * 16×16 的舰队棋盘：<b>只负责"哪艘船占哪几格"</b>，不管数值、不管战斗。
 *
 * <p>改造前这块逻辑长在 {@code Hull} + {@code Slot} 上（船体被切成核心/主炮/副炮/装甲/动力/战术六段，
 * 每段只许装对应模块）。改造后分区没有了：任何船能放在任何位置，
 * 唯一的两条硬约束是<b>不出界</b>与<b>不压住别的船</b>，都在 {@link #canPlace} 里。</p>
 *
 * <p>数值与规则（结构值合计、能量、抽牌、倍率、相邻加成）在 {@link Fleet} 里，
 * 这里保持一个纯几何容器，好让存档、编排界面、敌方阵型和单元测试都只依赖几何。</p>
 */
public final class FleetBoard {

    /** 棋盘边长（格）。改造前等于 {@code HullDesign.SIZE}，现在自己说了算。 */
    public static final int SIZE = 16;

    private static final FleetBoard EMPTY = new FleetBoard(List.of());

    private final List<FleetUnit> units;

    private FleetBoard(List<FleetUnit> units) {
        this.units = List.copyOf(units);
    }

    /**
     * 用一组船建棋盘。
     *
     * @throws IllegalArgumentException 出界、重叠、或同型船出现两次（同型应该合并等级）
     */
    public static FleetBoard of(List<FleetUnit> units) {
        if (units == null || units.isEmpty()) {
            return EMPTY;
        }
        List<FleetUnit> copy = List.copyOf(units);
        for (int i = 0; i < copy.size(); i++) {
            FleetUnit u = copy.get(i);
            if (u.x() < 0 || u.y() < 0 || u.x2() > SIZE || u.y2() > SIZE) {
                throw new IllegalArgumentException(
                        "「" + u.nameZh() + "」放不下：" + u + " 超出 " + SIZE + "x" + SIZE + " 棋盘");
            }
            for (int j = 0; j < i; j++) {
                FleetUnit other = copy.get(j);
                if (u.id().equals(other.id())) {
                    throw new IllegalArgumentException(
                            "「" + u.nameZh() + "」在舰队里出现了两次（同型船应该合并等级）");
                }
                if (u.overlaps(other)) {
                    throw new IllegalArgumentException(
                            "「" + u.nameZh() + "」和「" + other.nameZh() + "」压在一起了：" + u + " / " + other);
                }
            }
        }
        return new FleetBoard(copy);
    }

    /** 空棋盘。 */
    public static FleetBoard empty() {
        return EMPTY;
    }

    /** 棋盘上的船（顺序即添加顺序）。 */
    public List<FleetUnit> units() {
        return units;
    }

    /** 有没有船。 */
    public boolean isEmpty() {
        return units.isEmpty();
    }

    /** 盖住 (cx,cy) 的那艘船，没有则 {@code null}。 */
    public FleetUnit at(int cx, int cy) {
        for (FleetUnit u : units) {
            if (u.covers(cx, cy)) {
                return u;
            }
        }
        return null;
    }

    /** 按船型 id 找棋子，没有则 {@code null}。 */
    public FleetUnit byShip(String shipId) {
        for (FleetUnit u : units) {
            if (u.id().equals(shipId)) {
                return u;
            }
        }
        return null;
    }

    /** 占用掩码（{@code [x][y]}，true = 有船）。 */
    public boolean[][] occupiedMask() {
        boolean[][] mask = new boolean[SIZE][SIZE];
        for (FleetUnit u : units) {
            for (int cx = u.x(); cx < u.x2(); cx++) {
                for (int cy = u.y(); cy < u.y2(); cy++) {
                    mask[cx][cy] = true;
                }
            }
        }
        return mask;
    }

    /** 还空着的格数。 */
    public int freeCells() {
        int taken = 0;
        for (FleetUnit u : units) {
            taken += u.cells();
        }
        return SIZE * SIZE - taken;
    }

    /** 一艘 {@code w×h} 的船所有能放下的左上角坐标（行优先）。 */
    public List<int[]> slots(int w, int h) {
        List<int[]> out = new ArrayList<>();
        for (int y = 0; y + h <= SIZE; y++) {
            for (int x = 0; x + w <= SIZE; x++) {
                if (fits(w, h, x, y, null)) {
                    out.add(new int[]{x, y});
                }
            }
        }
        return out;
    }

    /** 这艘船摆在它自己记录的坐标上合不合法（不压别人——同型船自己除外）。 */
    public boolean canPlace(FleetUnit unit) {
        if (unit == null) {
            return false;
        }
        if (unit.x() < 0 || unit.y() < 0 || unit.x2() > SIZE || unit.y2() > SIZE) {
            return false;
        }
        return fits(unit.w(), unit.h(), unit.x(), unit.y(), unit.id());
    }

    /** 这艘船摆到 (x,y) 合不合法。 */
    public boolean canPlace(FleetUnit unit, int x, int y) {
        return unit != null && canPlace(unit.at(x, y));
    }

    /** 挪一艘船，越界或压住别人返回 {@code null}。 */
    public FleetBoard moved(String shipId, int x, int y) {
        FleetUnit unit = byShip(shipId);
        if (unit == null) {
            return null;
        }
        FleetUnit moved = unit.at(x, y);
        if (!canPlace(moved)) {
            return null;
        }
        List<FleetUnit> next = new ArrayList<>(units.size());
        for (FleetUnit u : units) {
            next.add(u.id().equals(shipId) ? moved : u);
        }
        return new FleetBoard(next);
    }

    /** 去掉某艘船。 */
    public FleetBoard without(String shipId) {
        List<FleetUnit> next = new ArrayList<>(units.size());
        for (FleetUnit u : units) {
            if (!u.id().equals(shipId)) {
                next.add(u);
            }
        }
        return next.size() == units.size() ? this : new FleetBoard(next);
    }

    /** 加一艘船（同型替换）。 */
    public FleetBoard with(FleetUnit unit) {
        return replaced(unit);
    }

    /** 同型替换（等同"把这艘船换掉"）。 */
    public FleetBoard replaced(FleetUnit unit) {
        List<FleetUnit> next = new ArrayList<>(units.size() + 1);
        boolean swapped = false;
        for (FleetUnit u : units) {
            if (u.id().equals(unit.id())) {
                next.add(unit);
                swapped = true;
            } else {
                next.add(u);
            }
        }
        if (!swapped) {
            next.add(unit);
        }
        return new FleetBoard(next);
    }

    /**
     * 和这艘船"贴着"的船：包围盒各向外扩一格后相交（对角也算贴着）。
     *
     * <p>相邻加成用它——玩家把装甲舰护在炮舰旁边就能吃到加成，
     * 改造前的 {@code ShipLoadout.touches} 是"件与件正交共边"，换成船之后一格缝隙太苛刻，
     * 放宽到"贴着或者斜着挨着"。</p>
     */
    public List<FleetUnit> neighbours(FleetUnit unit) {
        List<FleetUnit> out = new ArrayList<>(2);
        if (unit == null) {
            return out;
        }
        for (FleetUnit u : units) {
            if (!u.id().equals(unit.id()) && adjacent(u, unit)) {
                out.add(u);
            }
        }
        return out;
    }

    /** 两艘船贴不贴（包围盒间隙 ≤1 格，横竖斜都算）。 */
    public static boolean adjacent(FleetUnit a, FleetUnit b) {
        if (a == null || b == null) {
            return false;
        }
        int gapX = Math.max(0, Math.max(a.x() - b.x2(), b.x() - a.x2()) + 1);
        int gapY = Math.max(0, Math.max(a.y() - b.y2(), b.y() - a.y2()) + 1);
        return gapX <= 1 && gapY <= 1;
    }

    /** 两艘船共边贴着（横竖挨着，不含纯斜角）——{@link Perk.Range#CROSS} 的判定。 */
    public static boolean edgeAdjacent(FleetUnit a, FleetUnit b) {
        return adjacent(a, b) && (overlapsOnX(a, b) || overlapsOnY(a, b));
    }

    /** 两艘船的 x 区间有没有重叠（共占某些列）。 */
    public static boolean overlapsOnX(FleetUnit a, FleetUnit b) {
        return a != null && b != null && a.x() < b.x2() && b.x() < a.x2();
    }

    /** 两艘船的 y 区间有没有重叠（同一排）。 */
    public static boolean overlapsOnY(FleetUnit a, FleetUnit b) {
        return a != null && b != null && a.y() < b.y2() && b.y() < a.y2();
    }

    /**
     * 光环范围判定：{@code target} 是否落在 {@code source} 的一条 {@code range} 光环里。
     *
     * <p>纯几何、不查棋盘——范围只由两艘船的相对位置决定，阵型协同铺位也用它。</p>
     */
    public static boolean inRange(FleetUnit source, FleetUnit target, Perk.Range range) {
        if (source == null || target == null || range == null) {
            return false;
        }
        boolean self = source.id().equals(target.id());
        return switch (range) {
            case SELF -> self;
            case CROSS -> !self && edgeAdjacent(source, target);
            case RING -> !self && adjacent(source, target);
            case ROW -> !self && overlapsOnY(source, target);
            case FLEET -> true;
        };
    }

    /** 最靠前的一列：我方朝右，以舰体右边缘（x2）为准，"前线"在右侧。 */
    public int frontLine() {
        int best = -1;
        for (FleetUnit u : units) {
            best = Math.max(best, u.x2());
        }
        return Math.max(0, best);
    }

    /** 最靠后的一行（y2 最大）。 */
    public int backLine() {
        int best = 0;
        for (FleetUnit u : units) {
            best = Math.max(best, u.y2());
        }
        return best;
    }

    /** 承伤顺序：我方朝右，以舰体右边缘（x2）为准，越靠右越靠前；同列从上往下（y 小先）。 */
    public List<FleetUnit> soakOrder() {
        List<FleetUnit> out = new ArrayList<>(units);
        out.sort((a, b) -> a.x2() != b.x2() ? Integer.compare(b.x2(), a.x2())
                : Integer.compare(a.y(), b.y()));
        return out;
    }

    /** 调试用字符画（{@code .} = 空，其余是船名首字）。 */
    public String ascii() {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                FleetUnit u = at(x, y);
                sb.append(u == null ? '.' : u.nameZh().charAt(0));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private boolean fits(int w, int h, int x, int y, String ignoreId) {
        if (x < 0 || y < 0 || x + w > SIZE || y + h > SIZE) {
            return false;
        }
        for (FleetUnit u : units) {
            if (ignoreId != null && u.id().equals(ignoreId)) {
                continue;
            }
            if (x < u.x2() && u.x() < x + w && y < u.y2() && u.y() < y + h) {
                return false;
            }
        }
        return true;
    }
}
