package com.hysx.salvosequence.tools;

import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.shipyard.HullGrid;
import com.hysx.salvosequence.shipyard.Module;
import com.hysx.salvosequence.shipyard.ModuleCatalog;
import com.hysx.salvosequence.shipyard.MountRules;
import com.hysx.salvosequence.shipyard.MountSlot;
import com.hysx.salvosequence.shipyard.OutlineTracer;
import com.hysx.salvosequence.shipyard.ShipComposer;
import com.hysx.salvosequence.shipyard.Weapon;
import com.hysx.salvosequence.shipyard.WeaponCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * 一艘真正拼出来的船（不依赖 JavaFX 工具包，纯数据 + 几何）。
 *
 * <p>存在的理由：{@link ModuleCatalog} 里那 19 种模块只是一张菜单，没人把它们摆在一起过。
 * 这个类把它们按一张具体的设计图码放，顺便回答三个只有拼起来才知道的问题：</p>
 * <ol>
 *   <li><b>模块之间有没有重叠、有没有超出网格</b>（{@code ShipComposer.firstOverlap} + 边界检查）；</li>
 *   <li><b>拼出来的并集轮廓长什么样</b>（{@code HullGrid.trace} 自动追踪，不用手画）；</li>
 *   <li><b>凹口会不会被简化算法切掉</b>（凹槽处的盖格校验）。</li>
 * </ol>
 *
 * <h2>设计图的意图</h2>
 * <p>一艘 26x16 格的横列舰：<b>左侧是斜艏</b>（逐列收窄，每列比前一列多缩一格，形成 45° 斜线）、
 * <b>中部是主体</b>（指挥核心被装甲包住）、<b>右侧是尾段与两个主推进</b>。</p>
 *
 * <p>刻意留出的凹口有三处，正是为了测凹形：</p>
 * <ul>
 *   <li>艏部上缘的阶梯缺口（x=11..12, y=0）——简化算法最爱在这里抄近路；</li>
 *   <li>艏尖上下两块「伸出去的」装甲，与主体之间形成两个夹口；</li>
 *   <li>尾段上下缘各有一列空缺，让尾段比主体窄。</li>
 * </ul>
 *
 * <p><b>全部字段都是静态只读的</b>：这个类只描述「一艘船」，不做生成。
 * 肉鸽生成器以后写的是另一个类，它产出的也是同样两个列表。</p>
 */
public final class ShipDemo {

    /** 网格宽（格）。 */
    public static final int GRID_W = 28;
    /** 网格高（格）。 */
    public static final int GRID_H = 22;
    /** 一格几像素。 */
    public static final int CELL_PX = 4;

    private static final List<ShipComposer.Placement> MODULES = buildModules();
    private static final List<ShipComposer.Hardpoint> GUNS = buildGuns(MODULES);

    private ShipDemo() {
    }

    /** 模块摆放。 */
    public static List<ShipComposer.Placement> modules() {
        return MODULES;
    }

    /** 武器挂载（炮塔层）。 */
    public static List<ShipComposer.Hardpoint> guns() {
        return GUNS;
    }

    /** 由模块摆放推出的并集占用格。 */
    public static HullGrid grid() {
        HullGrid grid = new HullGrid(GRID_W, GRID_H, CELL_PX);
        for (ShipComposer.Placement p : MODULES) {
            grid.fillRect(p.cellX(), p.cellY(), p.module().shape().w(), p.module().shape().h());
        }
        return grid;
    }

    /** 舰体并集轮廓（简化后）。 */
    public static OutlineTracer.Trace outline() {
        return grid().outline();
    }

    /** 舰体并集轮廓（未简化，用来对照）。 */
    public static OutlineTracer.Trace rawOutline() {
        return grid().trace();
    }

    // ===== 设计图 =====

    private static List<ShipComposer.Placement> buildModules() {
        List<ShipComposer.Placement> out = new ArrayList<>();

        // 排法：先摆「大件」（核心、推进、炮座、衬层），再用 fill() 把剩下的补满。
        // fill() 只从 fillers() 里挑模块，那里面没有炮座也没有核心，所以不可能与大件抢位置。
        // 这样做的理由是实测出来的：手工一行行填会在两层之间漏格，而「一个只有一格宽的洞」
        // 在核对图上几乎看不出来，必须让程序去填、让测试去数洞。
        //
        // 外形（横列舰，艏在左、尾在右）：
        //   y= 2.. 3   上缘那一条甲板带，x=2..14      → 与两侧形成两个 L 形凹角
        //   y= 4.. 9   船体主段，x=0..17              → 最宽
        //   y= 8..10   x=3..7 是空的                  → 舷侧一个大凹口
        //   y=10..17   船体下段，x=0..17
        //   y=18..19   中间一长条有东西，两侧空      → 下缘两个凹口
        //   y=20..21   x=10..17 有东西（炮廓）
        //   y=22..23   只有 x=11..14 有东西          → 尾段收窄

        // ===== 大件 =====
        put(out, ModuleCatalog.CORE_COMMAND, 5, 5);     // 5x5  x=5..9,  y=5..9   指挥核心
        put(out, ModuleCatalog.ARMOR_BAY, 3, 10);       // 5x5  x=3..7,  y=10..14 装甲舱（舷侧凹口下方）
        put(out, ModuleCatalog.MOUNT_BROADSIDE, 8, 10); // 5x5  x=8..12, y=10..14 ← 舷侧大座，3 门
        put(out, ModuleCatalog.MOUNT_TWIN, 13, 10);     // 2x2  x=13..14,y=10..11 ← 尾部小座
        put(out, ModuleCatalog.CORE_PINNACE, 0, 5);     // 3x3  x=0..2,  y=5..7   艏尖
        put(out, ModuleCatalog.MOUNT_TWIN, 2, 2);       // 2x2  x=2..3,  y=2..3   ← 艏部小座
        put(out, ModuleCatalog.ENGINE_MAIN, 13, 2);     // 4x5  x=13..16,y=2..6   主推进（上）
        put(out, ModuleCatalog.ENGINE_MAIN, 13, 15);    // 4x5  x=13..16,y=15..19 主推进（下）
        put(out, ModuleCatalog.MOUNT_SPONSON, 12, 20);  // 4x2  x=12..15,y=20..21 ← 尾部炮廓，2 门

        // ===== 填充：把剩下的补满 =====
        fill(out, 0, 2, 13, 1);     // y=2    x=0..12
        fill(out, 17, 2, 1, 1);     // y=2    x=17
        fill(out, 0, 3, 7, 2);      // y=3..4 x=0..6
        fill(out, 7, 3, 6, 2);      // y=3..4 x=7..12 ← 这一条必须填：不填它上面（y=2）与下面（y=5）就把它围成一个洞
        fill(out, 0, 5, 3, 5);      // x=0..2,y=5..9（艏尖右边）
        fill(out, 3, 5, 2, 5);      // x=3..4,y=5..9
        fill(out, 10, 5, 3, 5);     // x=10..12,y=5..9
        fill(out, 0, 10, 3, 5);     // x=0..2,y=10..14
        fill(out, 0, 15, 5, 5);     // x=0..4,y=15..19
        fill(out, 5, 15, 8, 5);     // x=5..12,y=15..19
        fill(out, 12, 19, 1, 3);    // x=12,y=19..21
        fill(out, 16, 19, 1, 3);    // x=16,y=19..21
        fill(out, 16, 20, 1, 1);    // y=20   x=16

        return out;
    }

    /** 填充用的小模块，按「尽量宽」排序。里面没有炮座也没有核心，所以不会跟大件抢位置。 */
    private static List<Module> fillers() {
        return List.of(
                ModuleCatalog.ARMOR_BELT,   // 4x2
                ModuleCatalog.ARMOR_TILE,   // 2x2
                ModuleCatalog.HULL_FRAME,   // 2x2
                ModuleCatalog.ARMOR_SCALE); // 1x1
    }

    /**
     * 用 {@link #fillers()} 里的模块把一个矩形区域铺满，跳过已经被占的格。
     *
     * <p>为什么不肉眼看图手填：漏一格的后果是多一个洞，而洞在核对图上非常难发现
     * （只有一格宽的洞看上去就是一条线）。交给一个循环做，再让测试数洞。</p>
     */
    private static void fill(List<ShipComposer.Placement> out, int x0, int y0, int w, int h) {
        boolean[] used = new boolean[w * h];
        for (ShipComposer.Placement p : out) {
            for (int y = y0; y < y0 + h; y++) {
                for (int x = x0; x < x0 + w; x++) {
                    if (x >= p.cellX() && x < p.right() && y >= p.cellY() && y < p.bottom()) {
                        used[(y - y0) * w + (x - x0)] = true;
                    }
                }
            }
        }
        for (int y = y0; y < y0 + h; y++) {
            int x = x0;
            while (x < x0 + w) {
                if (used[(y - y0) * w + (x - x0)]) {
                    x++;
                    continue;
                }
                // 找这一行里从这个位置开始的连续空格长度
                int run = 0;
                while (x + run < x0 + w && !used[(y - y0) * w + (x + run - x0)]) {
                    run++;
                }
                Module pick = null;
                for (Module m : fillers()) {
                    if (m.shape().w() <= run && m.shape().h() <= (y0 + h - y)) {
                        // 竖直方向也要够，且这一段在下一行同样空着
                        if (fitsFree(used, w, x0, y0, x, y, m.shape().w(), m.shape().h())) {
                            pick = m;
                            break;
                        }
                    }
                }
                if (pick == null) {
                    pick = ModuleCatalog.ARMOR_SCALE;
                }
                put(out, pick, x, y);
                for (int yy = y; yy < y + pick.shape().h(); yy++) {
                    for (int xx = x; xx < x + pick.shape().w(); xx++) {
                        used[(yy - y0) * w + (xx - x0)] = true;
                    }
                }
                x += pick.shape().w();
            }
        }
    }

    private static boolean fitsFree(boolean[] used, int w, int x0, int y0,
                                    int x, int y, int mw, int mh) {
        for (int yy = y; yy < y + mh; yy++) {
            for (int xx = x; xx < x + mw; xx++) {
                if (used[(yy - y0) * w + (xx - x0)]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<ShipComposer.Hardpoint> buildGuns(List<ShipComposer.Placement> mods) {
        List<ShipComposer.Hardpoint> out = new ArrayList<>();
        for (ShipComposer.Placement p : mods) {
            if (!p.module().isMount() || p.module().socketCount() == 0) {
                continue;
            }
            List<MountSlot> slots = MountRules.slotsFor(p.module());
            for (int i = 0; i < slots.size(); i++) {
                // 一个座上挂不同炮：中间那门口径最大，两侧挂轻的。以后由玩家在船坞里选。
                out.add(new ShipComposer.Hardpoint(pickGun(p.module(), i, slots.size()), p, slots.get(i)));
            }
        }
        return out;
    }

    private static Weapon pickGun(Module mount, int index, int slotCount) {
        if (slotCount >= 3) {
            return index == 1 ? WeaponCatalog.CANNON_TRIPLE : WeaponCatalog.MISSILE_MICRO;
        }
        return WeaponCatalog.CANNON_TWIN;
    }

    private static void put(List<ShipComposer.Placement> out, Module module, int cellX, int cellY) {
        out.add(new ShipComposer.Placement(module, cellX, cellY));
    }

    // ===== 自检：给测试与工具共用 =====

    /** 第一处重叠的描述，没有重叠返回 null。 */
    public static String firstOverlap() {
        return ShipComposer.firstOverlap(MODULES);
    }

    /** 模块是否全部落在网格内，越界返回描述。 */
    public static String outOfBounds() {
        for (ShipComposer.Placement p : MODULES) {
            if (p.cellX() < 0 || p.cellY() < 0 || p.right() > GRID_W || p.bottom() > GRID_H) {
                return p.module().id() + " 在 (" + p.cellX() + "," + p.cellY() + ") "
                        + p.module().shape().label() + " 越界，网格 " + GRID_W + "x" + GRID_H;
            }
        }
        return null;
    }

    /** 舰体是否是连成一片的（四邻域）。 */
    public static boolean connected() {
        return grid().isConnected();
    }

    /** 简化后的轮廓有没有切到实心格外面，返回不匹配的格数（0 = 完全盖住）。 */
    public static int outlineMismatch() {
        HullGrid g = grid();
        return g.outlineMismatchCount(g.outline());
    }

    /** 造价合计。 */
    public static int totalCost() {
        int sum = 0;
        for (ShipComposer.Placement p : MODULES) {
            sum += p.module().cost();
        }
        for (ShipComposer.Hardpoint h : GUNS) {
            sum += Math.max(0, h.weapon().cost());
        }
        return sum;
    }

    /** 内部空洞（四邻域走不到网格边缘的空格）。拼装漏格时会大于 0。 */
    public static int holeCount() {
        return grid().holes().size();
    }

    /** 内部空洞的坐标，给核对图与调试用。 */
    public static List<String> holeList() {
        List<String> out = new ArrayList<>();
        for (com.badlogic.gdx.math.GridPoint2 p : grid().holes()) {
            out.add(p.x + "," + p.y);
        }
        return out;
    }

    /** 占用格数。 */
    public static int cellCount() {
        return grid().cellCount();
    }

    /** 拼出来的舰体占多少设计像素。 */
    public static String sizeLabel() {
        int[] b = ShipComposer.boundsCells(MODULES);
        if (b == null || b.length < 2) {
            return "0x0";
        }
        // boundsCells 返回的是「占用到第几格」= {w, h}，不是 {x0,y0,x1,y1}（踩过一次）。
        int w = b[0] * CELL_PX;
        int h = b[1] * CELL_PX;
        return w + "x" + h + " PX";
    }

    /** 给核对图角上盖的一行摘要。 */
    public static String summary() {
        return "CELLS " + cellCount() + "   COST " + totalCost()
                + "   GUNS " + GUNS.size() + "   " + sizeLabel();
    }

    /** 只给调试：把模块坐标一行一个打出来。 */
    public static List<String> dump() {
        List<String> out = new ArrayList<>();
        for (ShipComposer.Placement p : MODULES) {
            out.add(String.format("%-14s %-5s (%2d,%2d) -> (%2d,%2d)",
                    p.module().id(), p.module().shape().label(),
                    p.cellX(), p.cellY(), p.right(), p.bottom()));
        }
        return out;
    }

    /** 供工具直接画：模块 + 炮塔 + 可选的并集底色。 */
    public static void draw(PixelArt art, int originX, int originY, boolean withHullBase) {
        ShipComposer.draw(art, CELL_PX, MODULES, GUNS, originX, originY, withHullBase);
    }

    /** 供工具对照：只画自动蒙皮的轮廓（不画模块）。 */
    public static void drawOutline(PixelArt art, int originX, int originY, boolean fill) {
        OutlineTracer.Trace trace = outline();
        int px = ShipComposer.DEFAULT_CELL_PX;
        for (int[] ring : trace.rings()) {
            double[] xs = new double[ring.length / 2];
            double[] ys = new double[ring.length / 2];
            for (int i = 0; i < xs.length; i++) {
                xs[i] = originX + ring[i * 2] * px;
                ys[i] = originY + ring[i * 2 + 1] * px;
            }
            if (fill) {
                art.polygon(xs, ys, Palette.SHIP_HULL);
            }
            art.polygonOutline(xs, ys, Palette.LINE);
        }
    }
}
