// 来源：gleba-libgdx-mvp（蓝色大肥鱼）——Gleba 星球生成核心，包名归入本项目。
package com.hysx.salvosequence.gleba.gen;

/** Köppen 简化分类 + 调色板。只做自然分类，不含文明/种族等内容。 */
public final class Biome {
    public static final byte OCEAN = 0, ICE = 1, TUNDRA = 2, BOREAL = 3, COLD_DESERT = 4,
            HOT_DESERT = 5, STEPPE = 6, TEMPERATE_FOREST = 7, SAVANNA = 8, TROPICAL_FOREST = 9,
            TEMPERATE_RAINFOREST = 10, ALPINE = 11;

    public static final String[] NAMES = {
            "Ocean", "Ice (EF)", "Tundra (ET)", "Boreal (Dfc)", "Cold desert (BWk)",
            "Hot desert (BWh)", "Steppe (BSk)", "Temperate forest (Cfb/Dfb)",
            "Savanna (Aw)", "Tropical forest (Af/Am)", "Temperate rainforest (Cfa)", "Alpine"
    };

    private static final float[][] COLOR = {
            {0.061000f, 0.195200f, 0.414800f}, // OCEAN (渲染时按深度覆盖)
            {1.000000f, 1.000000f, 1.000000f},
            {0.756400f, 0.793000f, 0.671000f},
            {0.244000f, 0.512400f, 0.317200f},
            {0.951600f, 0.890600f, 0.707600f},
            {1.000000f, 0.963800f, 0.610000f},
            {0.890600f, 0.829600f, 0.512400f},
            {0.414800f, 0.683200f, 0.329400f},
            {0.671000f, 0.756400f, 0.292800f},
            {0.195200f, 0.561200f, 0.207400f},
            {0.317200f, 0.634400f, 0.402600f},
            {0.634400f, 0.597800f, 0.573400f},
    };

    public static void classify(PlanetData d) {
        for (int t = 0; t < d.n; t++) {
            if (!d.land[t]) { d.koppen[t] = OCEAN; continue; }
            float temp = d.tempC[t];
            float rain = d.rainMm[t];
            byte k;
            if (temp < -8f) k = ICE;
            else if (temp < 0f) k = TUNDRA;
            else if (temp < 8f) {
                k = rain < 240f ? COLD_DESERT : (rain < 430f ? STEPPE : BOREAL);
            } else if (temp < 20f) {
                if (rain < 200f) k = COLD_DESERT;
                else if (rain < 440f) k = STEPPE;
                else if (rain < 950f) k = TEMPERATE_FOREST;
                else k = TEMPERATE_RAINFOREST;
            } else {
                if (rain < 300f) k = HOT_DESERT;
                else if (rain < 700f) k = STEPPE;
                else if (rain < 1500f) k = SAVANNA;
                else k = TROPICAL_FOREST;
            }
            // 高山：按海拔再覆盖一次
            if (d.elevation[t] > 4700f) k = ALPINE;
            d.koppen[t] = k;
        }
    }

    /** 基础色（含雪的混白），再按星球类型做海洋配色 / 去饱和 / 色相旋转。 */
    public static void color(PlanetData d, int t, float[] out) {
        PlanetTheme th = d.theme == null ? PlanetTheme.EARTH : d.theme;
        byte k = d.koppen[t];
        float[] c = COLOR[k];
        float r = c[0], g = c[1], b = c[2];
        if (!d.land[t]) {
            float depth = Math.min(1f, Math.max(0f, -d.elevation[t]) / 5200f);
            r = th.shallowR + (th.deepR - th.shallowR) * depth;
            g = th.shallowG + (th.deepG - th.shallowG) * depth;
            b = th.shallowB + (th.deepB - th.shallowB) * depth;
            // 海冰：水温低于 0℃ 时海面从蓝转为灰白（冰封型的海洋才真的像冻住的，
            // 否则整颗星球看起来只是"蓝海上漂着几块陆冰"）
            float ice = Math.min(1f, Math.max(0f, -d.tempC[t] / 8f));
            if (ice > 0f) {
                r = r * (1 - ice) + 0.80f * ice;
                g = g * (1 - ice) + 0.87f * ice;
                b = b * (1 - ice) + 0.93f * ice;
            }
        } else {
            float snow = Math.min(1f, d.snow[t] * th.snowBoost * 0.55f);
            r = r * (1 - snow) + 0.95f * snow;
            g = g * (1 - snow) + 0.96f * snow;
            b = b * (1 - snow) + 0.98f * snow;
            // 海拔提亮，制造立体感
            float lift = Math.min(0.25f, Math.max(0f, d.elevation[t]) / 9000f);
            r = Math.min(1f, r + lift);
            g = Math.min(1f, g + lift);
            b = Math.min(1f, b + lift);
        }
        if (d.land[t]) {
            // 只有陆地参与去饱和 / 变亮 / 色相旋转：海洋（岩浆）配色是按主题直接给死的
            // 负值 = 提饱和（荒漠型靠这个把浅卡其压成赭石）
            if (th.desaturate != 0f) {
                float lum = 0.299f * r + 0.587f * g + 0.114f * b;
                r += (lum - r) * th.desaturate;
                g += (lum - g) * th.desaturate;
                b += (lum - b) * th.desaturate;
            }
            r *= th.landBright;
            g *= th.landBright;
            b *= th.landBright;
            if (th.hueShift != 0f) {
                // 整颗转色相：地球的绿/棕转到紫/红/青，才是"外星植被"而不是"地球刷了层滤镜"
                float[] hsb = rgbToHsb(r, g, b);
                hsb[0] = (hsb[0] + th.hueShift + 360f) % 360f;
                float[] rgb = hsbToRgb(hsb[0], hsb[1], hsb[2]);
                r = rgb[0];
                g = rgb[1];
                b = rgb[2];
            }
        }
        out[0] = tinted(r, t); out[1] = tinted(g, t); out[2] = tinted(b, t);
    }

    private static float[] rgbToHsb(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d < 1e-5f) {
            h = 0f;
        } else if (max == r) {
            h = 60f * (((g - b) / d) % 6f);
        } else if (max == g) {
            h = 60f * ((b - r) / d + 2f);
        } else {
            h = 60f * ((r - g) / d + 4f);
        }
        if (h < 0f) h += 360f;
        float s = max <= 1e-5f ? 0f : d / max;
        return new float[]{h, s, max};
    }

    private static float[] hsbToRgb(float h, float s, float v) {
        h = (h % 360f + 360f) % 360f;
        float c = v * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = v - c;
        float r, g, b;
        int seg = (int) (h / 60f) % 6;
        switch (seg) {
            case 0: r = c; g = x; b = 0; break;
            case 1: r = x; g = c; b = 0; break;
            case 2: r = 0; g = c; b = x; break;
            case 3: r = 0; g = x; b = c; break;
            case 4: r = x; g = 0; b = c; break;
            default: r = c; g = 0; b = x; break;
        }
        return new float[]{r + m, g + m, b + m};
    }

    /** 逐格微扰，打散等值带，让大片同色区域看起来有纹理。 */
    private static float jitter(int t) {
        int h = t * 374761393 + 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);
        return (h & 0xFFFF) / 65535f;
    }

    private static float tinted(float c, int t) {
        return Math.min(1f, c * (0.93f + 0.14f * jitter(t)));
    }
}
