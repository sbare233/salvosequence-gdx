package com.hysx.salvosequence.core;

import com.hysx.salvosequence.core.Color;

import java.util.HashMap;
import java.util.Map;

/**
 * 像素调色板小图：用字符网格描述小图案，每个字符对应调色板里的一种颜色，{@code '.'} 表示透明。
 *
 * <pre>{@code
 * PaletteSprite ship = new PaletteSprite(new String[]{
 *     "..XX..",
 *     ".X11X.",
 *     "XX11XX",
 * }, PaletteSprite.palette('X', Color.WHITE, '1', Color.GRAY));
 * }</pre>
 *
 * 得到的结果可转成非预乘 ARGB 数组（{@code toArgbArray}），
 * 也可用 {@link PixelArt#blit} 直接画到像素缓冲上，边缘保持硬像素。
 */
public final class PaletteSprite {

    private final Color[][] pixels;
    private final int width;
    private final int height;

    public PaletteSprite(String[] rows, Map<Character, Color> palette) {
        this.height = rows.length;
        int w = 0;
        for (String row : rows) {
            w = Math.max(w, row.length());
        }
        this.width = w;
        this.pixels = new Color[height][width];
        for (int y = 0; y < height; y++) {
            String row = rows[y];
            if (row.length() != width) {
                // 字符网格最容易出的错就是某一行少打/多打一个字符，
                // 这里直接报错并给出定位，避免图案被静默改成错形状
                throw new IllegalArgumentException(String.format(
                        "字符网格第 %d 行长度为 %d，应为 %d（图案宽度取最长行）", y, row.length(), width));
            }
            for (int x = 0; x < width; x++) {
                pixels[y][x] = palette.get(row.charAt(x));
            }
        }
    }

    private PaletteSprite(Color[][] pixels) {
        this.pixels = pixels;
        this.height = pixels.length;
        this.width = pixels.length == 0 ? 0 : pixels[0].length;
    }

    /** 便捷构造：按 {@code 字符, 颜色} 成对传入；颜色传 {@code null} 即透明。 */
    public static Map<Character, Color> palette(Object... pairs) {
        Map<Character, Color> map = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            Color color = (Color) pairs[i + 1];
            if (color != null) {
                map.put((Character) pairs[i], color);
            }
        }
        return map;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Color colorAt(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return null;
        }
        return pixels[y][x];
    }

    /** 逐个像素替换颜色（例如把机体自带的配色映射成敌方配色）。 */
    public PaletteSprite remap(Map<Color, Color> colorMap) {
        Color[][] out = new Color[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color c = pixels[y][x];
                if (c == null) {
                    continue;
                }
                out[y][x] = colorMap.getOrDefault(c, c);
            }
        }
        return new PaletteSprite(out);
    }

    /** 水平翻转副本（用于左右对称的机体 / 图标）。 */
    public PaletteSprite mirrored() {
        Color[][] out = new Color[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                out[y][x] = pixels[y][width - 1 - x];
            }
        }
        return new PaletteSprite(out);
    }

    /** 顺时针旋转 90 度的副本（用于斜置挂架上的图标）。 */
    public PaletteSprite rotatedQuarter() {
        Color[][] out = new Color[width][height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                out[x][height - 1 - y] = pixels[y][x];
            }
        }
        return new PaletteSprite(out);
    }

    /** 转成非预乘 ARGB 数组（导出 PNG / 与 Sprite 互转用），行距 = width。 */
    public int[] toArgbArray() {
        int[] out = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color c = pixels[y][x];
                out[y * width + x] = c == null ? 0 : PixelArt.toArgb(c);
            }
        }
        return out;
    }
}
