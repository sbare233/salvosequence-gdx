package com.hysx.salvosequence.core;

import java.awt.Color;

/**
 * 把"平滑的 3D 渲染图"压成程序化像素画的后处理滤镜（纯 CPU，320×180 一张约 2~4ms）。
 *
 * <p>算法译自 Godot 的 {@code PixelArt.gdshader}
 * （bukkbeek/GodotPixelRenderer，MIT）：锐化 → 调色板吸附 → 色阶量化 → Sobel 描边 →
 * 只在明暗过渡带做的 4×4 Bayer 抖动。原文是 canvas_item 着色器，这里改成对 ARGB 数组操作，
 * 好处是不用给每个 3D 场景各自接一条 GL 后处理链。</p>
 *
 * <p>像素化本身不在这里做：3D 场景本来就渲染在 320×180，放大的那一步是最近邻，格子已经在了。</p>
 */
public final class PixelArtFilter {

    /** 可调参数。默认值取自原着色器（8 色步进、8 色调色板、边阈 0.5）。 */
    public record Params(int steps, int palette, float sharpness, float edgeThreshold,
                         float outlineMix, float ditherAmount, float ditherBlend) {
    }

    /** 原着色器的 8 色调色板（#0d2b45 … #ffecd6，落日桃色系）。 */
    private static final int[] PALETTE_WARM = {
            0x000000,
            0x0D2B45, 0x203C56, 0x544E68, 0x8D697A,
            0xD08159, 0xFFAA5E, 0xFFD4A3, 0xFFECD6,
    };

    /** 冷色系：和太空背景更搭（深蓝 → 青 → 白）。 */
    private static final int[] PALETTE_COLD = {
            0x000000,
            0x0D1B33, 0x16304F, 0x24507A, 0x3C7FA8,
            0x6FB3C9, 0xA8DCE8, 0xD8F1F7, 0xF2FBFF,
    };

    /** 灰阶：检查结构用。 */
    private static final int[] PALETTE_GREY = {
            0x000000, 0x242424, 0x494949, 0x6D6D6D,
            0x929292, 0xB6B6B6, 0xDBDBDB, 0xFFFFFF,
    };

    private static final int[][] PALETTES = {PALETTE_WARM, PALETTE_COLD, PALETTE_GREY};
    private static final String[] PALETTE_NAMES = {"暖色", "冷色", "灰阶"};

    /** 默认只做色阶量化 + 描边 + 抖动，不动色相（调色板吸附会改写整体色调，留给 J 键手动开）。 */
    private static final Params DEFAULT = new Params(8, 0, 0.25f, 0.75f, 0.22f, 0.35f, 1f);

    /** 4×4 Bayer 阈值表，除以 15 归一化。 */
    private static final float[] BAYER = {
            0f / 15f, 8f / 15f, 2f / 15f, 10f / 15f,
            12f / 15f, 4f / 15f, 14f / 15f, 6f / 15f,
            3f / 15f, 11f / 15f, 1f / 15f, 9f / 15f,
            15f / 15f, 7f / 15f, 13f / 15f, 5f / 15f,
    };

    private static boolean enabled;
    private static Params params = DEFAULT;
    /** 0 = 不吸附调色板；1..3 = 暖色/冷色/灰阶。 */
    private static int mode;
    /** 滤镜状态版本号：开关、调色板、参数一变就 +1，调用方用它判断缓存是否过期。 */
    private static int stamp;

    private PixelArtFilter() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean on) {
        if (enabled != on) {
            enabled = on;
            stamp++;
        }
    }

    public static int stamp() {
        return stamp;
    }

    public static boolean toggle() {
        enabled = !enabled;
        stamp++;
        return enabled;
    }

    public static Params params() {
        return params;
    }

    public static void setParams(Params p) {
        params = p == null ? DEFAULT : p;
        stamp++;
    }

    public static void reset() {
        params = DEFAULT;
        stamp++;
    }

    /** 0 = 不吸附调色板；1..3 = 调色板序号 + 1。 */
    public static int mode() {
        return mode;
    }

    public static String modeName() {
        if (mode == 0) {
            return "原色";
        }
        return PALETTE_NAMES[Math.floorMod(mode - 1, PALETTE_NAMES.length)];
    }

    public static int cycleMode(int direction) {
        mode = Math.floorMod(mode + direction, PALETTES.length + 1);
        params = new Params(params.steps(), mode == 0 ? 0 : 1, params.sharpness(),
                params.edgeThreshold(), params.outlineMix(), params.ditherAmount(), params.ditherBlend());
        stamp++;
        return mode;
    }

    /** 当前生效的调色板（mode == 0 时返回 null）。 */
    private static int[] activePalette() {
        return mode == 0 ? null : PALETTES[mode - 1];
    }

    /**
     * 就地过滤一张 ARGB 图（不修改入参）。
     *
     * @return 新的 ARGB 数组；入参为 null 时返回 null
     */
    public static int[] apply(int[] src, int w, int h) {
        if (src == null || w <= 0 || h <= 0 || src.length < w * h) {
            return src;
        }
        Params p = params;
        float[] lum = luminance(src, w, h);
        int[] sharpened = p.sharpness > 0f ? sharpen(src, lum, w, h, p.sharpness) : src;
        float[] sharpLum = p.sharpness > 0f ? luminance(sharpened, w, h) : lum;

        int[] out = new int[w * h];
        int[] pal = activePalette();
        float step = p.steps <= 1 ? 0f : 1f / (p.steps - 1);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int argb = sharpened[i];
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;

                if (pal != null) {
                    // 调色板模式：直接吸附到最近色（再抖动就跳色了）
                    int nearest = nearest(pal, r, g, b);
                    r = (nearest >>> 16) & 0xFF;
                    g = (nearest >>> 8) & 0xFF;
                    b = nearest & 0xFF;
                } else if (p.steps > 1) {
                    // 色阶模式：带 4×4 Bayer 的有序抖动，只在相邻两档之间跳动。
                    // 近黑区不抖动——否则 0/1 两档之间会抖出一层彩色棋盘格。
                    int top = Math.max(r, Math.max(g, b));
                    float t = step * 255f;
                    if (top < t * 1.6f) {
                        r = 0;
                        g = 0;
                        b = 0;
                    } else {
                        // 按"最大通道"量化亮度、再等比缩放三通道：逐通道独立量化会把米色
                        // (233,217,201) 拉成 (255,219,219) 那种发粉的偏色。
                        float th = BAYER[(y & 3) * 4 + (x & 3)] * p.ditherAmount;
                        int raw = Math.max(r, Math.max(g, b));
                        float scaled = raw / t;
                        int base = (int) Math.floor(scaled);
                        if (scaled - base > th) {
                            base++;
                        }
                        float k = clamp(Math.round(base * t)) / (float) raw;
                        r = clamp(Math.round(r * k));
                        g = clamp(Math.round(g * k));
                        b = clamp(Math.round(b * k));
                    }
                }
                // 描边：Sobel 在亮度上取梯度，超过阈值就压向描边色
                if (p.outlineMix > 0f) {
                    float edge = sobel(sharpLum, w, h, x, y);
                    if (edge > p.edgeThreshold) {
                        float k = Math.min(1f, p.outlineMix * Math.min(1f, (edge - p.edgeThreshold) * 2.5f));
                        r = (int) (r * (1f - k));
                        g = (int) (g * (1f - k));
                        b = (int) (b * (1f - k));
                    }
                }
                out[i] = (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
            }
        }
        return out;
    }

    // ------------------------------------------------------------ 内部

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    private static int nearest(int[] pal, int r, int g, int b) {
        int best = pal[0];
        int bestD = Integer.MAX_VALUE;
        for (int c : pal) {
            int dr = ((c >>> 16) & 0xFF) - r;
            int dg = ((c >>> 8) & 0xFF) - g;
            int db = (c & 0xFF) - b;
            int d = dr * dr * 3 + dg * dg * 4 + db * db * 2;   // 近似人眼权重
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    private static float[] luminance(int[] argb, int w, int h) {
        float[] out = new float[w * h];
        for (int i = 0; i < out.length; i++) {
            int c = argb[i];
            out[i] = (((c >>> 16) & 0xFF) * 0.299f + ((c >>> 8) & 0xFF) * 0.587f + (c & 0xFF) * 0.114f) / 255f;
        }
        return out;
    }

    /** 3×3 非锐化掩模：center + (center - blur) * strength。 */
    private static int[] sharpen(int[] src, float[] lum, int w, int h, float strength) {
        int[] out = new int[src.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float sum = 0f;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) {
                            continue;
                        }
                        sum += lum[Math.min(h - 1, Math.max(0, y + dy)) * w + Math.min(w - 1, Math.max(0, x + dx))];
                    }
                }
                float blur = sum / 8f;
                float target = lum[i] + (lum[i] - blur) * strength;
                int argb = src[i];
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                float k = lum[i] <= 0.0001f ? 1f : Math.max(0f, Math.min(2.5f, target / lum[i]));
                out[i] = (a << 24) | (clamp(Math.round(r * k)) << 16)
                        | (clamp(Math.round(g * k)) << 8) | clamp(Math.round(b * k));
            }
        }
        return out;
    }

    /** Sobel 梯度幅值（0..1）。 */
    private static float sobel(float[] lum, int w, int h, int x, int y) {
        int x0 = Math.max(0, x - 1);
        int x1 = Math.min(w - 1, x + 1);
        int y0 = Math.max(0, y - 1);
        int y1 = Math.min(h - 1, y + 1);
        float tl = lum[y0 * w + x0];
        float tc = lum[y0 * w + x];
        float tr = lum[y0 * w + x1];
        float ml = lum[y * w + x0];
        float mr = lum[y * w + x1];
        float bl = lum[y1 * w + x0];
        float bc = lum[y1 * w + x];
        float br = lum[y1 * w + x1];
        float gx = (tr + 2f * mr + br) - (tl + 2f * ml + bl);
        float gy = (bl + 2f * bc + br) - (tl + 2f * tc + tr);
        return (float) Math.sqrt(gx * gx + gy * gy) * 0.25f;
    }

    /** 给调试用：调色板的 Java 颜色（面板上画色块）。 */
    public static Color paletteColor(int index) {
        int[] pal = activePalette();
        int c = pal[Math.floorMod(index, pal.length)];
        return new Color((c >>> 16) & 0xFF, (c >>> 8) & 0xFF, c & 0xFF);
    }
}
