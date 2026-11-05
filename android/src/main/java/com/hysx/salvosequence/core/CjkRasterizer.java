package com.hysx.salvosequence.core;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 中文位图光栅器（Android 版）：与桌面版同一套"3 倍字号离屏光栅化 → 3x3 盒式平均 →
 * 25% 覆盖率二值化"管线，只是把 AWT 换成 {@code android.graphics}。
 *
 * <p>桌面版用 {@code java.awt.Font}；Android 没有 AWT，启动时由
 * {@code AndroidLauncher} 调 {@link #install(AssetManager)} 从 assets 里加载同一份
 * 像素 TTF。基线取整、画布尺寸、阈值都与桌面版逐行一致，两端的字形落点不会漂。</p>
 */
public final class CjkRasterizer {

    /** 一张光栅好的文本位图：非预乘 ARGB，行距 = width。 */
    public record Rendered(int[] argb, int width, int height) {
    }

    private record Key(String content, double designSize, Color color) {
    }

    private static final Map<Key, Rendered> CACHE = new HashMap<>();
    private static final int CACHE_LIMIT = 512;
    private static final int FACTOR = 3;
    private static final String FONT_ASSET = "fonts/ZLabsPixel_12px_M_CN.ttf";

    private static AssetManager assets;
    private static Typeface typeface;
    private static boolean broken;

    private CjkRasterizer() {
    }

    /** 启动时调用：准备字体；没有它 render() 全部返回 null（文字不显示）。 */
    public static void install(AssetManager manager) {
        assets = manager;
        try {
            typeface = Typeface.createFromAsset(manager, FONT_ASSET);
            broken = false;
        } catch (Throwable t) {
            typeface = Typeface.MONOSPACE;
            broken = true;
            System.out.println("android cjk rasterizer: font missing (" + t + ")");
        }
        CACHE.clear();
    }

    public static boolean ready() {
        return typeface != null;
    }

    /**
     * 渲染一段文本（口径同桌面版：&lt;12 号按 12 算，基线取整）。
     *
     * @return 位图；字体不可用或文本为空时返回 null
     */
    public static Rendered render(String content, double designSize, Color color) {
        if (broken || typeface == null || content == null || content.isEmpty()) {
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
            double eff = Math.max(PixelText.MIN_SIZE, designSize);
            int baselineInImage = (int) Math.round(eff * 1.2) * FACTOR;
            int w = (int) Math.ceil(content.length() * eff) + 6;
            int h = (int) Math.ceil(eff * 2.4) + 6;
            if (w <= 0 || h <= 0) {
                return null;
            }
            Bitmap big = Bitmap.createBitmap(w * FACTOR, h * FACTOR, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(big);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTypeface(typeface);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize((float) (Math.rint(Math.max(PixelText.MIN_SIZE, designSize) * FACTOR)));
            canvas.drawColor(0x00000000);
            canvas.drawText(content, 3 * FACTOR, baselineInImage, paint);

            int[] raw = new int[w * FACTOR * h * FACTOR];
            big.getPixels(raw, 0, w * FACTOR, 0, 0, w * FACTOR, h * FACTOR);
            big.recycle();

            int[] argb = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int sumA = 0;
                    for (int dy = 0; dy < FACTOR; dy++) {
                        int row = (y * FACTOR + dy) * w * FACTOR + x * FACTOR;
                        for (int dx = 0; dx < FACTOR; dx++) {
                            sumA += (raw[row + dx] >>> 24) & 0xFF;
                        }
                    }
                    if (sumA * 4 < FACTOR * FACTOR * 255) {
                        argb[y * w + x] = 0;
                    } else {
                        argb[y * w + x] = (0xFF << 24)
                                | ((int) Math.round(color.getRed() * 255) << 16)
                                | ((int) Math.round(color.getGreen() * 255) << 8)
                                | (int) Math.round(color.getBlue() * 255);
                    }
                }
            }
            return new Rendered(argb, w, h);
        } catch (Throwable t) {
            broken = true;
            System.out.println("android cjk rasterizer failed: " + t);
            return null;
        }
    }
}
