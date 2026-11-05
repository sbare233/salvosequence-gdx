package com.hysx.salvosequence.core;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * 中文位图光栅器：把一段文本渲染成"设计像素尺寸"的 1:1 位图（带缓存）。
 *
 * <p>管线（libGDX 版）：AWT 以 3 倍字号画到离屏 BufferedImage → 3x3 盒式平均 +
 * 25% 覆盖率二值化。基线在图内必须取整（round(1.2x字号)）——和原 Prism 管线一样，
 * 分数基线会把每个字形上下各画一份（双影），整数基线永远干净。</p>
 *
 * <p>字体工具不可用的环境（如无头单测）渲染会失败：失败被记住，之后直接返回
 * null，调用方只记录不贴图。</p>
 */
public final class CjkRasterizer {

    /** 一张光栅好的文本位图：非预乘 ARGB，行距 = width。 */
    public record Rendered(int[] argb, int width, int height) {
    }

    private record Key(String content, double designSize, Color color) {
    }

    private static final Map<Key, Rendered> CACHE = new HashMap<>();
    private static final int CACHE_LIMIT = 512;
    private static boolean broken;

    private CjkRasterizer() {
    }

    /**
     * 渲染一段文本。
     *
     * @param content    文本
     * @param designSize 设计字号（&lt;12 钳到 12，与 PixelText 口径一致）
     * @param color      字色
     * @return 位图；字体工具不可用或文本为空时返回 null
     */
    public static Rendered render(String content, double designSize, Color color) {
        if (broken || content == null || content.isEmpty()) {
            return null;
        }
        Key key = new Key(content, designSize, color);
        Rendered image = CACHE.get(key);
        if (image == null) {
            image = renderOnce(content, designSize, color);
            if (image == null) {
                return null;
            }
            if (CACHE.size() >= CACHE_LIMIT) {
                CACHE.clear();
            }
            CACHE.put(key, image);
        }
        return image;
    }

    private static Rendered renderOnce(String content, double designSize, Color color) {
        try {
            int factor = 3;
            // 字号<12 会被 PixelText 钳到 12：画布必须按生效字号分配，
            // 否则 9 号字按 9 分配、按 12 渲染——每个名字尾部裁掉一个半字
            double eff = Math.max(PixelText.MIN_SIZE, designSize);
            int baselineInImage = (int) Math.round(eff * 1.2) * factor;
            int w = (int) Math.ceil(content.length() * eff) + 6;
            int h = (int) Math.ceil(eff * 2.4) + 6;
            if (w <= 0 || h <= 0) {
                return null;
            }

            BufferedImage big = new BufferedImage(w * factor, h * factor,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = big.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                        RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
                g.setFont(PixelText.cjk(designSize, factor, false));
                g.setColor(new java.awt.Color(
                        (int) Math.round(color.getRed() * 255),
                        (int) Math.round(color.getGreen() * 255),
                        (int) Math.round(color.getBlue() * 255),
                        (int) Math.round(color.getOpacity() * 255)));
                g.drawString(content, 3 * factor, baselineInImage);
            } finally {
                g.dispose();
            }

            int[] argb = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    // 3x3 盒式平均 + 覆盖率阈值：像素字体的字形点正好 3×3 采样，
                    // 覆盖率要么满格要么为零，二值化只影响边缘过渡带
                    int sumA = 0;
                    int sumR = 0;
                    int sumG = 0;
                    int sumB = 0;
                    for (int dy = 0; dy < factor; dy++) {
                        for (int dx = 0; dx < factor; dx++) {
                            int src = big.getRGB(x * factor + dx, y * factor + dy);
                            int a = (src >>> 24) & 0xFF;
                            sumA += a;
                            sumR += ((src >> 16) & 0xFF) * a;
                            sumG += ((src >> 8) & 0xFF) * a;
                            sumB += (src & 0xFF) * a;
                        }
                    }
                    int pixels = factor * factor;
                    if (sumA * 4 < pixels * 255) {
                        argb[y * w + x] = 0;
                    } else {
                        int r = sumR / sumA;
                        int gg = sumG / sumA;
                        int b = sumB / sumA;
                        argb[y * w + x] = (0xFF << 24) | (r << 16) | (gg << 8) | b;
                    }
                }
            }
            return new Rendered(argb, w, h);
        } catch (Throwable t) {
            // 无字体工具（单测环境）：记住失败，之后直接短路
            broken = true;
            System.out.println("cjk rasterizer unavailable (headless?): " + t);
            return null;
        }
    }
}
