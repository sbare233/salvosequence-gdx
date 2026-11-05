package com.hysx.salvosequence.tools;

import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.core.PixelFont;
import com.hysx.salvosequence.shipyard.HullGrid;
import com.hysx.salvosequence.shipyard.OutlineTracer;
import com.hysx.salvosequence.shipyard.ShipComposer;

import java.io.File;
import java.util.List;

/**
 * 拼起来的那艘船的核对图。
 *
 * <p>左边是「模块摆法」（每个模块各画各的边框），右边是「自动蒙皮之后的成品」
 * （并集轮廓 + 叠上去的炮塔）。两半用的是同一份 {@link ShipDemo#grid()}，所以只要
 * 右边哪里缺一块，就一定是蒙皮那一步的问题，不是设计图的问题。</p>
 *
 * <p>下面一排是三个局部放大：艏部、舷侧凹口、尾部炮廓。放大是为了能用肉眼确认
 * 凹口的两个内角确实被轮廓切出来了（缩到 1/3 看的话，差一格是看不出来的）。</p>
 *
 * <p>输出目录用环境变量 {@code SHIP_OUT} 指定，默认 {@code target}；
 * 放大倍数用 {@code SHIP_SCALE}，默认 3。</p>
 */
public final class ShipLab {

    private static final int CANVAS_W = 640;
    private static final int CANVAS_H = 360;
    private static final int CELL = 4;

    public void start() throws Exception {
        PixelArt art = new PixelArt(CANVAS_W, CANVAS_H);
        PixelFont font = new PixelFont();

        art.fill(Palette.BACKDROP);
        drawBackdrop(art);

        HullGrid grid = ShipDemo.grid();
        int originX = 18;
        int originY = 34;

        // 左半：模块摆法（每块各画各的框，故意留着接缝）
        font.draw(art, "MODULES  AS PLACED", originX, 20, Palette.CYAN);
        ShipDemo.draw(art, originX, originY, false);
        art.frame(originX - 2, originY - 2,
                grid.width() * CELL + 3, grid.height() * CELL + 3, Palette.PANEL_EDGE);

        // 右半：蒙皮成品
        int skinX = 300;
        font.draw(art, "AFTER AUTO SKIN", skinX, 20, Palette.CYAN);
        drawSkinned(art, grid, skinX, originY);
        art.frame(skinX - 2, originY - 2,
                grid.width() * CELL + 3, grid.height() * CELL + 3, Palette.PANEL_EDGE);

        drawSummary(art, font, grid);
        drawZooms(art, font, grid);

        String outDir = readEnv("SHIP_OUT", "target");
        File out = new File(outDir, "ship-demo.png");
        try {
            art.savePng(out);
        } catch (java.io.IOException e) {
            System.out.println("[ship-lab] 写 PNG 失败: " + e.getMessage());
            System.exit(1);
        }
        System.out.println("[ship-lab] 已写出 " + out.getAbsolutePath());
        System.out.println("[ship-lab] " + ShipDemo.summary());
        System.out.println("[ship-lab] 轮廓 " + ShipDemo.outline().pointCount() + " 个角 / "
                + ShipDemo.outline().rings().length + " 条环");
        for (String line : ShipDemo.dump()) {
            System.out.println("[ship-lab]   " + line);
        }
        System.exit(0);
    }

    /** 蒙皮成品：先填并集轮廓，再把模块与炮塔按层序盖上去。 */
    private static void drawSkinned(PixelArt art, HullGrid grid, int ox, int oy) {
        OutlineTracer.Trace outline = grid.outline();
        ShipDemo.drawOutline(art, ox, oy, true);
        ShipDemo.draw(art, ox, oy, false);

        // 轮廓顶点用亮点标出来，方便数凹角（+1 让它压在勾线上而不是被勾线盖住）
        outlineDots(art, outline, ox, oy, CELL);
    }

    /** 把一条轮廓的顶点画成亮点。cell 是每格多少设计像素，(ox,oy) 是「格 (0,0)」的落点。 */
    private static void outlineDots(PixelArt art, OutlineTracer.Trace outline,
                                    int ox, int oy, int cell) {
        for (int[] ring : outline.rings()) {
            for (int i = 0; i + 1 < ring.length; i += 2) {
                int px = ox + ring[i] * cell;
                int py = oy + ring[i + 1] * cell;
                art.fillRect(px - 1, py - 1, 3, 3, Palette.WARN);
                art.pixel(px, py, Palette.LINE);
            }
        }
    }

    private static void drawSummary(PixelArt art, PixelFont font, HullGrid grid) {
        int y = 132;
        font.draw(art, "SUMMARY", 18, y, Palette.CYAN);
        y += 12;
        font.draw(art, ShipDemo.summary(), 18, y, Palette.LINE);
        y += 10;
        font.draw(art, "GRID " + grid.width() + "x" + grid.height() + " CELLS  "
                + (grid.width() * CELL) + "x" + (grid.height() * CELL) + " PX", 18, y, Palette.LINE);
        y += 10;
        font.draw(art, "HOLES " + ShipDemo.holeCount() + "   CONNECTED "
                + (ShipDemo.connected() ? "YES" : "NO"), 18, y, Palette.LINE);
        y += 10;
        font.draw(art, "OUTLINE CORNERS " + ShipDemo.outline().pointCount()
                + "   RINGS " + ShipDemo.outline().rings().length, 18, y, Palette.LINE);
        y += 10;
        font.draw(art, "MISMATCH " + ShipDemo.outlineMismatch()
                + "   OVERLAP " + (ShipDemo.firstOverlap() == null ? "NONE" : "YES"), 18, y, Palette.LINE);
        y += 10;
        font.draw(art, "YELLOW DOTS = OUTLINE CORNERS  (CONCAVE ONES COUNT)",
                18, y, Palette.LINE_DIM);
    }

    /**
     * 三个局部放大：同一条边并排画「模块摆法」与「蒙皮之后」。
     *
     * <p>并排是刻意的：只看蒙皮结果没法判断少掉的那块是轮廓的错还是设计图的错，
     * 两边放在一起，差别一眼就能归因。</p>
     */
    private static void drawZooms(PixelArt art, PixelFont font, HullGrid grid) {
        int scale = readInt("SHIP_SCALE", 3);
        font.draw(art, "ZOOM  x" + scale + "   LEFT = MODULES   RIGHT = SKINNED", 18, 218, Palette.CYAN);

        // (格子 x, 格子 y, 宽, 高) × 三处
        int[][] areas = {
                {0, 0, 10, 9},     // 艏部与上缘凹角
                {5, 6, 10, 9},     // 舷侧大凹口
                {11, 11, 12, 11},  // 尾部炮廓与下缘凹口
        };
        String[] labels = {"BOW + TOP NOTCH", "SIDE NOTCH", "STERN BAY"};
        int panelW = 140;
        int x = 18;
        for (int i = 0; i < areas.length; i++) {
            zoom(art, font, grid, areas[i], x, 244, scale, labels[i]);
            x += panelW;
        }
    }

    /** 一格局部：左边模块摆法，右边蒙皮成品，中间一条分隔线。 */
    private static void zoom(PixelArt art, PixelFont font, HullGrid grid, int[] area,
                             int ox, int oy, int scale, String label) {
        int cx = area[0];
        int cy = area[1];
        int cw = area[2];
        int ch = area[3];
        int w = cw * scale;
        int h = ch * scale;

        font.draw(art, label, ox, oy - 9, Palette.LINE_DIM);

        // 左：模块
        for (int y = 0; y < ch && cy + y < grid.height(); y++) {
            for (int x = 0; x < cw && cx + x < grid.width(); x++) {
                if (grid.occupied(cx + x, cy + y)) {
                    art.fillRect(ox + x * scale, oy + y * scale, scale, scale, Palette.SHIP_SHADE);
                }
            }
        }

        // 右：同一格区域，把轮廓画上去
        int rx = ox + w + 6;
        OutlineTracer.Trace outline = grid.outline();
        for (int[] ring : outline.rings()) {
            double[] xs = new double[ring.length / 2];
            double[] ys = new double[ring.length / 2];
            boolean anyInside = false;
            for (int i = 0; i < xs.length; i++) {
                int gx = ring[i * 2];
                int gy = ring[i * 2 + 1];
                xs[i] = rx + (gx - cx) * (double) scale;
                ys[i] = oy + (gy - cy) * (double) scale;
                if (gx >= cx - 1 && gx <= cx + cw + 1 && gy >= cy - 1 && gy <= cy + ch + 1) {
                    anyInside = true;
                }
            }
            if (!anyInside) {
                continue;
            }
            art.polygon(xs, ys, Palette.SHIP_HULL);
            art.polygonOutline(xs, ys, Palette.LINE);
        }
        outlineDots(art, outline, rx - cx * scale, oy - cy * scale, scale);

        art.fillRect(ox + w + 2, oy, 1, h, Palette.PANEL_EDGE);
    }

    private static void drawBackdrop(PixelArt art) {
        for (int y = 0; y < CANVAS_H; y += 4) {
            art.ditherRect(0, y, CANVAS_W, 2, Palette.SPACE, 2);
        }
    }

    private static String readEnv(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    private static int readInt(String key, int fallback) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static void main(String[] args) throws Exception {
        new ShipLab().start();
    }
}
