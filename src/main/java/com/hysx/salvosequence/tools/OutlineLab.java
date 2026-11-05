package com.hysx.salvosequence.tools;

import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.core.PixelFont;
import com.hysx.salvosequence.shipyard.HullGrid;
import com.hysx.salvosequence.shipyard.OutlineTracer;
import com.hysx.salvosequence.core.Color;

import java.io.File;

/**
 * 自动蒙皮的核对图：把「全方格舰体」到「合并轮廓」这条链路离屏画成一张 PNG。
 *
 * <h2>为什么要单独做一个工具</h2>
 * <p>轮廓拟合的正确性没法靠断言全看出来——顶点数、面积、内洞数量都能测，但
 * 「楼梯有没有塌成斜线」「凹口是不是真的凹进去」「内部接缝有没有消失」这些只能看图。
 * 这个工具把每一步分开画出来，让眼睛一眼判断。</p>
 *
 * <h2>画了什么</h2>
 * <ul>
 *   <li>每套舰体画两遍：<b>左</b>是原始方格（每格一个淡框，看得出是方块堆的），
 *       <b>右</b>是并集轮廓 + 蒙皮填充（看得出是一条折线）。</li>
 *   <li>原始方格底下垫一列「每格轮廓」的灰线，用来对比：接缝在右图必须消失。</li>
 *   <li>轮廓顶点用青色小方块标出来，顶到边的毛刺用黄色标。</li>
 * </ul>
 *
 * <h2>环境变量</h2>
 * <ul>
 *   <li>{@code OUTLINE_STEPS} — 最少几级楼梯才塌成斜线，默认 {@link OutlineTracer#MIN_STAIRCASE_STEPS}。</li>
 *   <li>{@code OUTLINE_OUT} — 输出目录，默认 {@code target}。</li>
 * </ul>
 *
 * <p>用法：{@code mvn compile exec:java -Dexec.mainClass=com.hysx.salvosequence.tools.OutlineLab}
 * （环境变量 OUTLINE_STEPS / OUTLINE_OUT / OUTLINE_CASE 见类头说明）。</p>
 */
public final class OutlineLab {

    private static final int CANVAS_W = 640;
    private static final int CANVAS_H = 360;

    /** 舰体网格上限（格）。够画一艘 20x12 的船。 */
    private static final int GRID_W = 24;
    private static final int GRID_H = 14;

    /** 形态名子串；设置后只画匹配的那一个形态，出大图。见 {@link #focusCase()}。 */
    private static final String FOCUS = System.getenv().getOrDefault("OUTLINE_CASE", "").trim();

    public void start() throws Exception {
        int tol = readInt("OUTLINE_STEPS", OutlineTracer.MIN_STAIRCASE_STEPS);
        File outDir = new File(System.getenv().getOrDefault("OUTLINE_OUT", "target"));

        PixelArt art = new PixelArt(CANVAS_W, CANVAS_H);
        art.fill(Palette.SPACE_DEEP);

        PixelFont font = new PixelFont();
        font.draw(art, "AUTO SKIN LAB", 8, 12, Palette.CYAN);
        font.draw(art, "CELLS " + GRID_W + "x" + GRID_H + "   TOL " + fmt(tol),
                8, 24, Palette.LINE_FAINT);
        font.draw(art, "LEFT = RAW CELLS    RIGHT = MERGED OUTLINE", 8, 36, Palette.LINE_FAINT);

        HullGrid slab = slab();
        HullGrid wedge = wedge();
        HullGrid ring = ring();
        HullGrid bridge = bridge();
        HullGrid pinch = pinch();

        if (!FOCUS.isEmpty()) {
            focusCase(art, font, slab, wedge, ring, bridge, pinch, tol);
            if (!outDir.exists() && !outDir.mkdirs()) {
                throw new IllegalStateException("建不了输出目录: " + outDir);
            }
            File focused = new File(outDir, "outline-" + FOCUS.toLowerCase(java.util.Locale.ROOT) + ".png");
            art.savePng(focused);
            System.out.println("[outline-lab] 聚焦 " + FOCUS + " 写出 " + focused.getAbsolutePath());
            System.exit(0);
        }
        panel(art, font, slab, 8, 46, tol, "SLAB  SLOPE BOW");
        panel(art, font, wedge, 8, 150, tol, "WEDGE  STAIR HULL");
        panel(art, font, ring, 8, 254, tol, "RING  INNER HOLE");
        panel(art, font, bridge, 328, 46, tol, "BRIDGE  CONCAVE");
        panel(art, font, slab.mirrored(), 328, 150, tol, "SLAB  MIRRORED");
        panel(art, font, wedge.rotatedCw(), 328, 254, tol, "WEDGE  ROT 90");

        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IllegalStateException("建不了输出目录: " + outDir);
        }
        File png = new File(outDir, "outline-lab.png");
        art.savePng(png);
        System.out.println("[outline-lab] 写出 " + png.getAbsolutePath()
                + "  tol=" + fmt(tol));
        for (HullGrid g : new HullGrid[]{slab, wedge, ring, bridge}) {
            OutlineTracer.Trace t = g.outline(tol);
            System.out.println("[outline-lab] 格数=" + g.cellCount()
                    + " 环=" + t.rings().length + " 顶点=" + t.pointCount()
                    + " 面积=" + fmt(t.area()) + " 周长=" + fmt(t.perimeter())
                    + " 连通=" + g.isConnected());
        }
        System.exit(0);
    }

    // ---------------------------------------------------------------- 聚焦模式

    /** 形态名子串到网格的查表；命中即进入聚焦模式。 */
    private static void focusCase(PixelArt art, PixelFont font,
                                  HullGrid slab, HullGrid wedge, HullGrid ring,
                                  HullGrid bridge, HullGrid pinch, int tol) {
        String key = FOCUS.toUpperCase(java.util.Locale.ROOT);
        HullGrid g;
        String label;
        if (key.contains("PINCH")) {
            g = pinch;
            label = "CORNER PINCH";
        } else if (key.contains("SLAB")) {
            g = slab;
            label = "SLAB  SLOPE BOW";
        } else if (key.contains("WEDGE")) {
            g = wedge;
            label = "WEDGE  STAIR HULL";
        } else if (key.contains("RING")) {
            g = ring;
            label = "RING  INNER HOLE";
        } else if (key.contains("BRIDGE")) {
            g = bridge;
            label = "BRIDGE  CONCAVE";
        } else {
            System.out.println("[outline-lab] 不认识的形态: " + FOCUS
                    + "（可用 PINCH / SLAB / WEDGE / RING / BRIDGE）");
            g = pinch;
            label = "CORNER PINCH";
        }

        font.draw(art, label + "   CELLS=" + g.cellCount()
                + "   CONNECTED(4-neighbour)=" + g.isConnected(), 8, 60, Palette.CYAN);
        font.draw(art, "LEFT = RAW CELLS      RIGHT = MERGED OUTLINE", 8, 72, Palette.LINE_FAINT);

        OutlineTracer.Trace raw = g.trace();
        OutlineTracer.Trace sim = g.outline(tol);
        font.draw(art, "RAW  RINGS=" + raw.rings().length + "  VERTS=" + raw.pointCount()
                + "  AREA=" + fmt(raw.area()) + "  PERIM=" + fmt(raw.perimeter()),
                8, 300, Palette.LINE_DIM);
        font.draw(art, "SIMP RINGS=" + sim.rings().length + "  VERTS=" + sim.pointCount()
                + "  AREA=" + fmt(sim.area()) + "  PERIM=" + fmt(sim.perimeter()),
                8, 312, Palette.LINE_DIM);
        for (int i = 0; i < raw.rings().length; i++) {
            int[] r = raw.rings()[i];
            StringBuilder sb = new StringBuilder("RAW RING " + i + ": ");
            for (int k = 0; k < r.length; k += 2) {
                sb.append('(').append(r[k] - 10).append(',').append(r[k + 1] - 5).append(')');
            }
            font.draw(art, sb.toString(), 8, 324, Palette.LINE_DIM);
        }
        for (int i = 0; i < sim.rings().length; i++) {
            int[] r = sim.rings()[i];
            StringBuilder sb = new StringBuilder("SIM RING " + i + ": ");
            for (int k = 0; k < r.length; k += 2) {
                sb.append('(').append(r[k] - 10).append(',').append(r[k + 1] - 5).append(')');
            }
            font.draw(art, sb.toString(), 8, 336, Palette.LINE_DIM);
        }

        // 画在网格原点附近，取一个能整块塞进画布的格子尺寸
        int cell = 34;
        int ox = 70;
        int oy = 120;
        rawCells(art, g, ox, oy, cell);
        // 两块相接的那个顶点：画个红框，肉眼确认轮廓是不是在那儿连上了
        art.frame(ox + 1 * cell - 4, oy + 1 * cell - 4, 9, 9, Palette.DANGER);
        int ox2 = 370;
        skinned(art, g, ox2, oy, cell, tol);
        art.frame(ox2 + 1 * cell - 4, oy + 1 * cell - 4, 9, 9, Palette.DANGER);
        font.draw(art, "RED = THE SHARED CORNER", ox, oy + 3 * cell + 16, Palette.DANGER);
    }

    // ---------------------------------------------------------------- 舰体样张

    /**
     * 用户点名的经典形状 {@code (0,0) (1,1) (2,1) (0,2)}：
     * 左下角一块与右上一条<b>只在一个角上相接</b>，四邻域不连通。
     * 这是轮廓追踪最容易出事的一类输入。
     */
    private static HullGrid pinch() {
        HullGrid g = new HullGrid(GRID_W, GRID_H, 6);
        g.set(10, 5, true);
        g.set(11, 6, true);
        g.set(12, 6, true);
        g.set(10, 7, true);
        return g;
    }

    /** 光滑侧影：斜艏 + 斜艉 + 两层上层建筑，用来验证楼梯能塌成斜线。 */
    private static HullGrid slab() {
        HullGrid g = new HullGrid(GRID_W, GRID_H, 6);
        // 船体：逐行向内收，形成斜艏与斜艉
        g.fillRect(3, 3, 2, 6);
        g.fillRect(5, 2, 2, 8);
        g.fillRect(7, 1, 2, 10);
        g.fillRect(9, 0, 4, 12);
        g.fillRect(13, 1, 2, 10);
        g.fillRect(15, 2, 2, 8);
        g.fillRect(17, 3, 1, 6);
        // 上层建筑：中间一坨，带阶梯
        g.fillRect(8, 2, 2, 2);
        g.fillRect(10, 1, 3, 2);
        g.fillRect(13, 2, 1, 2);
        return g;
    }

    /** 全方格楔形：纯粹的对角台阶，最能看出简化效果。 */
    private static HullGrid wedge() {
        HullGrid g = new HullGrid(GRID_W, GRID_H, 6);
        for (int i = 0; i < 12; i++) {
            g.fillRect(2 + i, 9 - i, 2, i + 1);
        }
        return g;
    }

    /** 中间挖空：验证内洞被单独识别成一个环（外环 + 内环 = 2 个环）。 */
    private static HullGrid ring() {
        HullGrid g = new HullGrid(GRID_W, GRID_H, 6);
        g.fillRect(3, 2, 16, 9);
        for (int y = 5; y < 8; y++) {
            for (int x = 8; x < 14; x++) {
                g.set(x, y, false);   // 挖空 -> 内洞
            }
        }
        return g;
    }

    /** 凹字形：验证凹角处轮廓往内拐，而不是直接连过去。 */
    private static HullGrid bridge() {
        HullGrid g = new HullGrid(GRID_W, GRID_H, 6);
        g.fillRect(4, 2, 14, 3);     // 上横梁
        g.fillRect(4, 5, 3, 5);      // 左支柱
        g.fillRect(15, 5, 3, 5);     // 右支柱
        g.fillRect(4, 10, 14, 2);    // 下横梁
        return g;
    }

    // ---------------------------------------------------------------- 绘制

    /** 一个面板：左边原始方格，右边蒙皮结果。 */
    private static void panel(PixelArt art, PixelFont font, HullGrid grid,
                              int px, int py, int tol, String label) {
        int boxW = 300;
        int boxH = 96;
        art.frame(px, py, boxW, boxH, Palette.PANEL_EDGE);
        art.fillRect(px + 1, py + 1, boxW - 2, 1, Palette.PANEL);
        font.draw(art, label, px + 4, py + 11, Palette.LINE_DIM);

        int cell = fitCell(grid);
        int availH = boxH - 20;
        int ox = px + 6;
        int oy = py + 18 + Math.max(0, (availH - grid.height() * cell) / 2);

        rawCells(art, grid, ox, oy, cell);
        skinned(art, grid, ox + grid.width() * cell + 12, oy, cell, tol);
    }

    /** 一格画多大：优先按宽度塞进半栏，最小 2 像素。 */
    private static int fitCell(HullGrid grid) {
        int half = 130;
        int byWidth = Math.max(2, half / Math.max(1, grid.width()));
        int byHeight = Math.max(2, 66 / Math.max(1, grid.height()));
        return Math.max(2, Math.min(byWidth, byHeight));
    }

    /** 左半：原始方格。每格一个淡框 —— 接缝清晰可见。 */
    private static void rawCells(PixelArt art, HullGrid grid, int ox, int oy, int cell) {
        for (int y = 0; y < grid.height(); y++) {
            for (int x = 0; x < grid.width(); x++) {
                if (!grid.occupied(x, y)) {
                    continue;
                }
                int rx = ox + x * cell;
                int ry = oy + y * cell;
                art.fillRect(rx, ry, cell - 1, cell - 1, Palette.PANEL_ALT);
                art.frame(rx, ry, cell, cell, Palette.LINE_FAINT);
            }
        }
    }

    /** 右半：并集轮廓 + 蒙皮。顶点用青点标出。 */
    private static void skinned(PixelArt art, HullGrid grid, int ox, int oy, int cell, int tol) {
        OutlineTracer.Trace trace = grid.outline(tol);
        // 船体底色：用格子的并集铺一层（模拟「蒙皮」），再压轮廓线
        for (int y = 0; y < grid.height(); y++) {
            for (int x = 0; x < grid.width(); x++) {
                if (grid.occupied(x, y)) {
                    art.fillRect(ox + x * cell, oy + y * cell, cell, cell, Palette.PANEL_ALT);
                }
            }
        }
        for (int[] ring : trace.rings()) {
            int n = ring.length / 2;
            double[] xs = new double[n];
            double[] ys = new double[n];
            for (int i = 0; i < n; i++) {
                xs[i] = ox + (double) ring[i * 2] * cell;
                ys[i] = oy + (double) ring[i * 2 + 1] * cell;
            }
            art.polygonOutline(xs, ys, Palette.LINE);
            for (int i = 0; i < n; i++) {
                int vx = (int) Math.round(xs[i]);
                int vy = (int) Math.round(ys[i]);
                art.fillRect(vx - 1, vy - 1, 2, 2, Palette.CYAN);
            }
        }
        if (trace.rings().length == 0) {
            art.frame(ox, oy, 40, 20, Palette.DANGER);
        }
    }

    // ---------------------------------------------------------------- 工具

    private static int readInt(String key, int fallback) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            System.out.println("[outline-lab] 环境变量 " + key + " 不是数字: " + v);
            return fallback;
        }
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    /** 直接跑（无窗口，导出核对图后自动退出）。 */
    public static void main(String[] args) throws Exception {
        new OutlineLab().start();
    }
}
