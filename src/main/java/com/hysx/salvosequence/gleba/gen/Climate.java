// 来源：gleba-libgdx-mvp（蓝色大肥鱼）——Gleba 星球生成核心，包名归入本项目。
package com.hysx.salvosequence.gleba.gen;

/**
 * 气候（对应 gleba 的 climate_simulation.rs：纬度查表 + 半拉格朗日平流 + 地形雨）。
 * 平流用与二进制一致的结构：沿风场反向回溯 dt·0.01·宽度，取最近格点（一阶、无插值的高斯平滑近似）。
 */
public final class Climate {
    private static final float EARTH_RADIUS_KM = 6371f;

    public static void build(SphereGrid g, PlanetParams p, PlanetData d, Noise noise, int steps) {
        final int n = g.tiles;

        // ---- 1. 风场：低纬信风（东→西）、中纬西风（西→东）、极地东风 ----
        float[] wx = new float[n], wy = new float[n], wz = new float[n];
        float[] east = new float[3], north = new float[3];
        for (int t = 0; t < n; t++) {
            g.east(t, east);
            g.north(t, north);
            float lat = g.latDeg[t];
            float alat = Math.abs(lat);
            float ec, nc;
            if (alat < 30f) {
                ec = -1.0f;
                nc = -0.28f * Math.signum(lat);
            } else if (alat < 62f) {
                ec = 1.25f;
                nc = 0.18f * Math.signum(lat);
            } else {
                ec = -0.85f;
                nc = -0.18f * Math.signum(lat);
            }
            float jitter = noise.noise(g.px[t] * 3.1f + 9f, g.py[t] * 3.1f, g.pz[t] * 3.1f - 4f) * 0.35f;
            ec += jitter;
            wx[t] = east[0] * ec + north[0] * nc;
            wy[t] = east[1] * ec + north[1] * nc;
            wz[t] = east[2] * ec + north[2] * nc;
            float len = (float) Math.sqrt(wx[t] * wx[t] + wy[t] * wy[t] + wz[t] * wz[t]);
            if (len > 1e-6f) { wx[t] /= len; wy[t] /= len; wz[t] /= len; }
        }

        // ---- 2. 大陆性：离海距离 ----
        com.badlogic.gdx.utils.IntArray sea = new com.badlogic.gdx.utils.IntArray(false, n / 2);
        for (int t = 0; t < n; t++) if (!d.land[t]) sea.add(t);
        int[] seaDist = g.bfsDistance(sea.toArray());
        float tileKm = 2f * (float) Math.PI * EARTH_RADIUS_KM / (4f * g.faceRes);
        for (int t = 0; t < n; t++) {
            int dd = seaDist[t];
            float km = dd < 0 ? 4000f : dd * tileKm;
            d.continentality[t] = Math.min(1f, km / 2600f);
        }

        // ---- 3. 温度：纬度基准 − 海拔递减 − 大陆性 ----
        for (int t = 0; t < n; t++) {
            float f = Math.abs(g.latDeg[t]) / 90f;
            float base = 29f - 48f * (float) Math.pow(f, 1.35);
            float elev = Math.max(0f, d.elevation[t]);
            float temp = base - 6.5f * (elev / 1000f);
            float cont = d.continentality[t];
            temp -= cont * 9f * f;
            if (!d.land[t]) temp = temp * 0.92f + 1.5f; // 海洋热惯性
            d.tempC[t] = temp;
            float snow = 0f;
            if (temp < 0f) snow = Math.min(1f, -temp / 9f);
            if (!d.land[t] && temp > -2f) snow = 0f;
            d.snow[t] = snow;
        }

        // ---- 4. 回溯索引：风场是静态的，每格的上游邻居只算一次 ----
        float stepLen = 0.055f;
        int[] back = new int[n];
        int[] upwind = new int[n];
        float[] evap = new float[n];
        for (int t = 0; t < n; t++) {
            float bx = g.px[t] - wx[t] * stepLen;
            float by = g.py[t] - wy[t] * stepLen;
            float bz = g.pz[t] - wz[t] * stepLen;
            back[t] = g.nearest(bx, by, bz);
            float ux = g.px[t] - wx[t] * stepLen * 1.5f;
            float uy = g.py[t] - wy[t] * stepLen * 1.5f;
            float uz = g.pz[t] - wz[t] * stepLen * 1.5f;
            upwind[t] = g.nearest(ux, uy, uz);
            evap[t] = d.land[t] ? 0.0016f : 0.011f;
        }

        // ---- 5. 湿度平流：m ← 上游 m × 0.985 + 蒸发 ----
        float[] moisture = new float[n];
        float[] m2 = new float[n];
        for (int t = 0; t < n; t++) moisture[t] = d.land[t] ? 0.004f : 0.02f;
        for (int s = 0; s < steps; s++) {
            for (int t = 0; t < n; t++) m2[t] = moisture[back[t]] * 0.985f + evap[t];
            float[] tmp = moisture;
            moisture = m2;
            m2 = tmp;
        }

        // ---- 6. 降水：纬度基准 + 地形雨（迎风抬升失水，背风自然变干）----
        float[] rain = new float[n];
        for (int t = 0; t < n; t++) {
            float lat = Math.abs(g.latDeg[t]);
            float itcz = 2300f * (float) Math.exp(-(lat / 9f) * (lat / 9f));
            float mid = 880f * (float) Math.exp(-((lat - 52f) / 13f) * ((lat - 52f) / 13f));
            rain[t] = itcz + mid + 170f;
        }
        for (int t = 0; t < n; t++) {
            if (!d.land[t]) continue;
            float rise = d.elevation[t] - d.elevation[upwind[t]];
            float m = moisture[t];
            if (rise > 0f) {
                float precip = Math.min(m, m * Math.min(0.55f, rise / 2600f));
                moisture[t] = m - precip;
                rain[t] += precip * 26000f;
            } else {
                rain[t] += m * 2600f;
            }
        }
        for (int t = 0; t < n; t++) d.rainMm[t] = Math.max(0f, rain[t]);
    }
}
