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

    /** 基础色（含雪的混白）。 */
    public static void color(PlanetData d, int t, float[] out) {
        byte k = d.koppen[t];
        float[] c = COLOR[k];
        float r = c[0], g = c[1], b = c[2];
        if (!d.land[t]) {
            float depth = Math.max(0f, -d.elevation[t]) / 5200f;
            depth = Math.min(1f, depth);
            r = 0.20f - 0.17f * depth;
            g = 0.48f - 0.40f * depth;
            b = 0.68f - 0.50f * depth;
        } else {
            float snow = d.snow[t] * 0.55f;
            r = r * (1 - snow) + 0.95f * snow;
            g = g * (1 - snow) + 0.96f * snow;
            b = b * (1 - snow) + 0.98f * snow;
            // 海拔提亮，制造立体感
            float lift = Math.min(0.25f, Math.max(0f, d.elevation[t]) / 9000f);
            r = Math.min(1f, r + lift);
            g = Math.min(1f, g + lift);
            b = Math.min(1f, b + lift);
        }
        out[0] = tinted(r, t); out[1] = tinted(g, t); out[2] = tinted(b, t);
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
