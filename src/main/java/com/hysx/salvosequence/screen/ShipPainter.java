package com.hysx.salvosequence.screen;

import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.fleet.AppearanceDesign;
import com.hysx.salvosequence.fleet.Ship;
import com.hysx.salvosequence.fleet.ShipArtStore;
import com.hysx.salvosequence.core.Color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把一艘船的<b>外观图</b>画进棋盘。
 *
 * <h2>改造前后</h2>
 * <p>改造前这里画的是"船体 + 零件 + 炮塔"：整艘船共用一张 288×288 外观图，
 * 装在上面的模块再各自画一遍程序图形（炮塔、装甲板、喷口、天线），炮塔还能单独换皮肤
 * （{@code ships/turret-main.txt}）。于是同一艘船的涂装和零件是两套互不相干的东西。</p>
 *
 * <p>现在只有一件事要画：<b>这艘船自己那张图</b>。每种船一张（尺寸 = 占地格数 × {@link AppearanceDesign#CELL}），
 * 一格 18 个外观像素，画到棋盘上时按显示尺寸最近邻取样。我方与敌方走的是同一个方法，
 * 所以"敌方棋盘的船看起来和我方一样"是自然结果。</p>
 *
 * <p>炮塔绘制整体删除：没有 {@code drawTurret}/{@code isExposed}/炮塔皮肤这回事了。</p>
 */
final class ShipPainter {

    private static final Map<String, AppearanceDesign> CACHE = new HashMap<>();

    /** 敌舰红系涂装缓存（按船型）。 */
    private static final Map<String, AppearanceDesign> ENEMY_CACHE = new HashMap<>();

    private ShipPainter() {
    }

    /** 当前该画第几帧。 */
    static int frameOf(AppearanceDesign look, double time) {
        if (look == null || look.frameCount() <= 1) {
            return 0;
        }
        int f = (int) Math.floor(time * look.fps());
        return Math.floorMod(f, look.frameCount());
    }

    /** 一格多少显示像素 → 这艘船画出来多大。 */
    static int spanOf(AppearanceDesign look, int cellPx) {
        if (look == null) {
            return 0;
        }
        return (look.width() / AppearanceDesign.CELL) * cellPx;
    }

    /**
     * 画一艘船。
     *
     * @param look   外观图
     * @param cellPx 一格多少显示像素
     * @param time   秒（播帧）
     */
    static void draw(PixelArt art, AppearanceDesign look, int x0, int y0, int cellPx, double time) {
        if (look == null || cellPx <= 0) {
            return;
        }
        int spanW = spanOf(look, cellPx);
        int spanH = (look.height() / AppearanceDesign.CELL) * cellPx;
        if (spanW <= 0 || spanH <= 0) {
            return;
        }
        art.blitArgbTile(look.frameRaw(frameOf(look, time)), look.width(), look.height(),
                0, 0, x0, y0, spanW, spanH, false, false, false, 1.0);
    }

    /** 画一艘船（按船型读外观图，带缓存）。 */
    static void draw(PixelArt art, Ship ship, int x0, int y0, int cellPx, double time) {
        draw(art, lookOf(ship), x0, y0, cellPx, time);
    }

    /** 画一艘船，外面套一圈框（选中/悬停/预览）。 */
    static void drawFramed(PixelArt art, AppearanceDesign look, int x0, int y0, int cellPx,
                           double time, Color frameColor) {
        draw(art, look, x0, y0, cellPx, time);
        if (look == null || frameColor == null) {
            return;
        }
        int spanW = spanOf(look, cellPx);
        int spanH = (look.height() / AppearanceDesign.CELL) * cellPx;
        art.frame(x0 - 1, y0 - 1, spanW + 2, spanH + 2, frameColor);
    }

    /** 缩略图（战利品行、说明面板里的那艘小船，一格 1~4 像素）。 */
    static void drawIcon(PixelArt art, Ship ship, int x, int y, int cellPx, double time) {
        if (ship == null) {
            return;
        }
        draw(art, lookOf(ship), x, y, Math.max(1, cellPx), time);
    }

    /** 这艘船的外观图（读一次缓存）。 */
    static AppearanceDesign lookOf(Ship ship) {
        if (ship == null) {
            return null;
        }
        return CACHE.computeIfAbsent(ship.id(), id -> ShipArtStore.loadLook(ship));
    }

    /**
     * 敌舰的"外观图"：同一套出厂涂装，按亮度重映射到红系。
     *
     * <p>为什么要重映射而不是另画一套图：玩家与敌方共用同一个绘制方法
     * （{@link #draw}），差别只在取哪张图。这样"敌方棋盘看起来和我方一样"
     * 是结构保证，而不是两边各写一遍代码保持同步。</p>
     */
    static AppearanceDesign lookOfEnemy(Ship ship) {
        if (ship == null) {
            return null;
        }
        return ENEMY_CACHE.computeIfAbsent(ship.id(), id -> tintEnemy(AppearanceDesign.builtin(ship)));
    }

    /** 画一艘敌舰。 */
    static void drawEnemy(PixelArt art, Ship ship, int x0, int y0, int cellPx, double time) {
        AppearanceDesign look = lookOfEnemy(ship);
        if (look == null) {
            return;
        }
        draw(art, look, x0, y0, cellPx, time);
    }

    /**
     * 把一张涂装的亮度重新映射到红系。
     *
     * <p>关键在**先量出这张图自己的亮度范围再拉伸**：出厂涂装整艘船都在同一个灰阶附近
     * （板体 ~95、板缝 ~72、灯 ~230），如果不做归一化，直接线性套红，
     * 板体和板缝会落到几乎同一个红值上，整艘船摊成一块实心红方块。</p>
     */
    private static AppearanceDesign tintEnemy(AppearanceDesign base) {
        int min = 255;
        int max = 0;
        for (int f = 0; f < base.frameCount(); f++) {
            int[] src = base.frameRaw(f);
            for (int argb : src) {
                if ((argb >>> 24) == 0) {
                    continue;
                }
                int lum = luminance(argb);
                min = Math.min(min, lum);
                max = Math.max(max, lum);
            }
        }
        int span = Math.max(1, max - min);
        List<int[]> frames = new ArrayList<>();
        for (int f = 0; f < base.frameCount(); f++) {
            int[] src = base.frameRaw(f);
            int[] dst = new int[src.length];
            for (int i = 0; i < src.length; i++) {
                int argb = src[i];
                int alpha = argb >>> 24;
                if (alpha == 0) {
                    continue;
                }
                double t = (luminance(argb) - min) / (double) span;   // 0..1
                dst[i] = (alpha << 24) | ramp(t);
            }
            frames.add(dst);
        }
        return AppearanceDesign.of(base.shipId(), base.nameZh(),
                base.width(), base.height(), frames, base.fps());
    }

    private static int luminance(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (r * 30 + g * 59 + b * 11) / 100;
    }

    /** 敌舰配色：暗部偏紫、板体中段是砖红、灯与炮口烧成橙白。 */
    private static int ramp(double t) {
        int[] dark = {38, 14, 22};
        int[] mid = {150, 28, 34};
        int[] hot = {255, 112, 64};
        int[] glow = {255, 214, 168};
        int[] from;
        int[] to;
        double k;
        if (t < 0.5) {
            from = dark;
            to = mid;
            k = t / 0.5;
        } else if (t < 0.88) {
            from = mid;
            to = hot;
            k = (t - 0.5) / 0.38;
        } else {
            from = hot;
            to = glow;
            k = (t - 0.88) / 0.12;
        }
        int r = (int) Math.round(from[0] + (to[0] - from[0]) * k);
        int g = (int) Math.round(from[1] + (to[1] - from[1]) * k);
        int b = (int) Math.round(from[2] + (to[2] - from[2]) * k);
        return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }


    /** 丢掉全部缓存（存盘后重新读）。 */
    static void reload() {
        CACHE.clear();
        ENEMY_CACHE.clear();
    }

    /** 只丢掉一艘的缓存。 */
    static void reload(Ship ship) {
        if (ship != null) {
            CACHE.remove(ship.id());
            ENEMY_CACHE.remove(ship.id());
        }
    }

    /** 出厂涂装全透明的兜底：一格一个方块。 */
    static void drawPlaceholder(PixelArt art, Ship ship, int x0, int y0, int cellPx, Color color) {
        if (ship == null || cellPx <= 0) {
            return;
        }
        for (int y = 0; y < ship.h(); y++) {
            for (int x = 0; x < ship.w(); x++) {
                art.rect(x0 + x * cellPx, y0 + y * cellPx, cellPx - 1, cellPx - 1, color);
            }
        }
    }
}
