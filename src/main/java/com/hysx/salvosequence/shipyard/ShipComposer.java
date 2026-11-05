package com.hysx.salvosequence.shipyard;

import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.core.Color;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 把模块与武器<b>按层</b>画到画布上。
 *
 * <h2>核心规则（用户点名要的那条）</h2>
 * <p><b>炮塔永远画在模块之上。</b>实现方式不是「先画模块再画炮」这种靠调用顺序的约定——
 * 那种约定迟早会在某个调用点被搞反。这里把绘制拆成两步：</p>
 * <ol>
 *   <li>先把所有 {@link Module} 按 {@link ShipLayer} 分组（层内保留输入顺序）；</li>
 *   <li>再画所有武器（{@link ShipLayer#TURRET}）。</li>
 * </ol>
 * <p>也就是说<b>无论调用方把武器塞在列表的哪个位置</b>，炮塔都在最后画。
 * 想验证这一点，看 {@link #draw} 的 javadoc 里那条「层序不会被输入顺序破坏」。</p>
 *
 * <h2>坐标</h2>
 * <p>模块位置用格；一格 = {@link #DEFAULT_CELL_PX} 像素（默认 4，与 §6.7 的结论一致）。
 * 数据层不掺像素，只有这一层做乘法。</p>
 */
public final class ShipComposer {

    private ShipComposer() {
    }

    /** 一格多少设计像素。§6.7 定的 4：玩家机 16x8 格 = 64x32 px，与现有船体尺度吻合。 */
    public static final int DEFAULT_CELL_PX = 4;

    /** 玩家侧炮塔色调。 */
    public static final Color ALLY_TONE = Palette.SHIP_GLOW;

    /** 敌人侧炮塔色调。 */
    public static final Color FOE_TONE = Palette.FOE_GLOW;

    /**
     * 一个已摆在格坐标上的模块。
     *
     * @param module 模块定义
     * @param cellX  左上角 x（格）
     * @param cellY  左上角 y（格）
     */
    public record Placement(Module module, int cellX, int cellY) {

        public Placement {
            if (module == null) {
                throw new IllegalArgumentException("摆放必须有模块");
            }
        }

        public int right() {
            return cellX + module.shape().w();
        }

        public int bottom() {
            return cellY + module.shape().h();
        }

        /** 两件模块是否重叠（格空间，含边界相接不算重叠）。 */
        public boolean overlaps(Placement other) {
            return cellX < other.right() && other.cellX < right()
                    && cellY < other.bottom() && other.cellY < bottom();
        }
    }

    /**
     * 一个已装在挂点上的武器。
     *
     * @param weapon 武器定义
     * @param owner  承载它的模块
     * @param slot   挂点（相对模块左上角）
     */
    public record Hardpoint(Weapon weapon, Placement owner, MountSlot slot) {

        public Hardpoint {
            if (weapon == null || owner == null || slot == null) {
                throw new IllegalArgumentException("挂载点需要武器、模块与挂点三者");
            }
            if (!owner.module().canMount()) {
                throw new IllegalArgumentException("模块 " + owner.module().id() + " 不能挂炮");
            }
        }

        /** 挂点在画布上的格坐标。 */
        public int cellX() {
            return owner.cellX() + slot.dx();
        }

        public int cellY() {
            return owner.cellY() + slot.dy();
        }
    }

    /**
     * 按层画一整套模块与武器。
     *
     * <p><b>层序不会被输入顺序破坏：</b>内部先把模块按层排序再逐层画，
     * 然后无条件画武器。所以把 {@code weapons} 里的东西混进 {@code modules} 也影响不到结果。</p>
     *
     * @param art      画布
     * @param cellPx   一格像素（&lt;=0 时用 {@link #DEFAULT_CELL_PX}）
     * @param modules  模块摆放（顺序只影响同层内的先后）
     * @param weapons  武器挂载（一律画在最上层）
     * @param originX  舰体原点（设计像素）
     * @param originY  舰体原点（设计像素）
     * @param withHullBase 是否先画一层舰体底色（自动蒙皮的结果以后接在这里）
     */
    public static void draw(PixelArt art, int cellPx, List<Placement> modules, List<Hardpoint> weapons,
                            int originX, int originY, boolean withHullBase) {
        if (art == null) {
            return;
        }
        int px = cellPx > 0 ? cellPx : DEFAULT_CELL_PX;
        List<Placement> mods = modules == null ? List.of() : modules;
        List<Hardpoint> guns = weapons == null ? List.of() : weapons;

        if (withHullBase) {
            drawModuleBoundsAsBase(art, px, mods, originX, originY);
        }

        // ⓵ 模块按层分桶，逐层画。层序由枚举的声明顺序决定，不看输入顺序。
        for (ShipLayer layer : ShipLayer.values()) {
            if (layer.aboveModules() || layer == ShipLayer.HULL_BASE) {
                continue;
            }
            for (Placement p : mods) {
                if (p.module().layer() == layer) {
                    drawModule(art, px, p, originX, originY);
                }
            }
        }

        // ⓶ 挂点标记：画在模块之上、炮塔之下，方便校对挂点位置对不对
        for (Hardpoint h : guns) {
            int cx = originX + h.cellX() * px + px / 2;
            int cy = originY + h.cellY() * px + px / 2;
            art.frame(cx - px / 2, cy - px / 2, px, px, Palette.WARN);
        }

        // ⓷ 炮塔：无条件最后画。这一行就是用户要的「炮塔层高于模块」。
        for (Hardpoint h : guns) {
            int cx = originX + h.cellX() * px + px / 2;
            int cy = originY + h.cellY() * px + px / 2;
            PlaceholderArt.turret(art, cx, cy, h.weapon().barrels(), toneOf(h));
        }
    }

    /** 只画模块（不画炮）。给「层序对照图」的中间那格用。 */
    public static void drawModules(PixelArt art, int cellPx, List<Placement> modules,
                                   int originX, int originY) {
        draw(art, cellPx, modules, List.of(), originX, originY, false);
    }

    /** 只画炮塔（不画模块）。给「层序对照图」的最后一格用。 */
    public static void drawWeapons(PixelArt art, int cellPx, List<Hardpoint> weapons,
                                   int originX, int originY) {
        if (art == null) {
            return;
        }
        int px = cellPx > 0 ? cellPx : DEFAULT_CELL_PX;
        for (Hardpoint h : weapons) {
            int cx = originX + h.cellX() * px + px / 2;
            int cy = originY + h.cellY() * px + px / 2;
            PlaceholderArt.turret(art, cx, cy, h.weapon().barrels(), toneOf(h));
        }
    }

    /** 舰体在格空间里的包围盒（宽, 高）。用来算画布要留多大。 */
    public static int[] boundsCells(List<Placement> modules) {
        if (modules == null || modules.isEmpty()) {
            return new int[]{0, 0};
        }
        int w = 0;
        int h = 0;
        for (Placement p : modules) {
            w = Math.max(w, p.right());
            h = Math.max(h, p.bottom());
        }
        return new int[]{w, h};
    }

    /** 检查摆放是否合法：两两不重叠。返回第一个冲突，没有冲突返回 null。 */
    public static String firstOverlap(List<Placement> modules) {
        if (modules == null) {
            return null;
        }
        List<Placement> sorted = new ArrayList<>(modules);
        sorted.sort(Comparator.comparingInt(Placement::cellY).thenComparingInt(Placement::cellX));
        for (int i = 0; i < sorted.size(); i++) {
            for (int j = i + 1; j < sorted.size(); j++) {
                if (sorted.get(i).overlaps(sorted.get(j))) {
                    return sorted.get(i).module().id() + " 与 " + sorted.get(j).module().id() + " 重叠";
                }
            }
        }
        return null;
    }

    // ===== 内部 =====

    private static void drawModule(PixelArt art, int px, Placement p, int originX, int originY) {
        int x = originX + p.cellX() * px;
        int y = originY + p.cellY() * px;
        int w = p.module().shape().w() * px;
        int h = p.module().shape().h() * px;
        PlaceholderArt.module(art, x, y, w, h, p.module().kind(), p.module().kind() == ModuleKind.CORE);
    }

    /** 用模块的并集轮廓做底色：先铺一层深色，后面模块本体画在上面。 */
    private static void drawModuleBoundsAsBase(PixelArt art, int px, List<Placement> modules,
                                               int originX, int originY) {
        for (Placement p : modules) {
            int x = originX + p.cellX() * px;
            int y = originY + p.cellY() * px;
            int w = p.module().shape().w() * px;
            int h = p.module().shape().h() * px;
            art.fillRect(x, y, w, h, Palette.SHIP_DEEP);
        }
    }

    private static Color toneOf(Hardpoint h) {
        return ALLY_TONE;
    }
}
