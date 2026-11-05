// 来源：gleba-libgdx-mvp（蓝色大肥鱼）——Gleba 星球生成核心，包名归入本项目。
package com.hysx.salvosequence.gleba.gen;

/**
 * 侵蚀（对应 gleba 的 erosion.rs）。
 * 热侵蚀常量取自二进制实证：10 轮迭代、坡度阈值 3.0°、靠拢系数 0.5、坡度用 atan2(Δh, 25231.325)。
 */
public final class Erosion {
    public static final float TILE_SPACING_M = 25231.325f;
    private static final float RAD2DEG = 57.29577951308232f;
    private static final float SLOPE_THRESHOLD_DEG = 3.0f;
    private static final float BLEND = 0.5f;

    /** 热侵蚀：把过陡的坡按 0.5 系数拉向最陡邻居的高程。 */
    public static void thermal(SphereGrid g, PlanetData d, int iterations) {
        int n = g.tiles;
        float[] h = d.elevation;
        for (int it = 0; it < iterations; it++) {
            float[] next = h.clone();
            for (int t = 0; t < n; t++) {
                float maxSlope = 0f;
                int steepest = -1;
                for (int k = g.nbStart[t]; k < g.nbStart[t + 1]; k++) {
                    int nb = g.nbList[k];
                    float dh = h[nb] - h[t];
                    float slope = Math.abs((float) Math.atan2(dh, TILE_SPACING_M)) * RAD2DEG;
                    if (slope > maxSlope && dh < 0) {
                        maxSlope = slope;
                        steepest = nb;
                    }
                }
                if (steepest >= 0 && maxSlope > SLOPE_THRESHOLD_DEG) {
                    next[t] = h[t] + (h[steepest] - h[t]) * BLEND;
                }
            }
            System.arraycopy(next, 0, h, 0, n);
        }
    }

    /**
     * 水力侵蚀（简化）：按高程降序做汇流累积，再沿流路下切。
     * 同时把汇流量写进 data.water，供渲染河流。
     */
    public static void hydraulic(SphereGrid g, PlanetData d, float strength) {
        int n = g.tiles;
        float[] h = d.elevation;
        // 先做几轮邻域平滑再填洼：高频细节会把地面打成无数小坑，直接填洼只能得到
        // 一堆几百格的小流域（尽量沿"大尺度地势"排水，才可能长出长河）
        float[] filled = fillDepressions(g, smooth(g, h, 4));

        // 1. 按"填洼后"高程降序
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Float.compare(filled[b], filled[a]));

        // 2. 汇流累积：routing 走 filled 面，洼地不会截断流域
        float[] flow = d.water;
        java.util.Arrays.fill(flow, 1f);
        for (Integer t : order) {
            if (filled[t] <= 0f) continue;              // 海洋是汇，不再往更低的格子传
            int lowest = routeTarget(g, filled, t);
            if (lowest >= 0) flow[lowest] += flow[t];
        }

        // 3. 下切：只切陆地流路，且不倒坡
        for (int t = 0; t < n; t++) {
            if (h[t] <= 0f) continue;
            int lowest = routeTarget(g, filled, t);
            if (lowest < 0) continue;
            float drop = h[t] - h[lowest];
            if (drop < 1f) continue;
            float pow = (float) Math.sqrt(flow[t]);
            float cut = strength * pow * Math.min(drop * 0.45f, 220f);
            h[t] -= cut;
            if (h[t] < h[lowest]) h[t] = h[lowest];
        }
    }


    /**
     * 填洼后大片区域会变成等高平地，只用高程比大小会"没有下坡"而截断流域。
     * 用 (filled, 索引) 的字典序做严格全序：保证无环，且平地上总能继续往低处走。
     */
    private static int routeTarget(SphereGrid g, float[] filled, int t) {
        int best = -1;
        for (int k = g.nbStart[t]; k < g.nbStart[t + 1]; k++) {
            int nb = g.nbList[k];
            if (better(filled, nb, t)) {
                if (best < 0 || better(filled, nb, best)) best = nb;
            }
        }
        return best;
    }

    private static boolean better(float[] f, int a, int b) {
        return f[a] < f[b] || (f[a] == f[b] && a < b);
    }

    /**
     * 优先级洪泛填洼：从所有海洋格出发按高程由低到高扩展，
     * 陆地洼地被抬到"溢出点"高度，保证每块陆地都有一条通往海洋的下坡路径。
     * 这是长河网出现的前提（不填洼时每个小坑都会截断流域）。
     */
    /** 邻域平均平滑（保留海陆边界：只平滑陆地格，海洋格保持原值）。 */
    private static float[] smooth(SphereGrid g, float[] h, int iterations) {
        int n = g.tiles;
        float[] cur = h.clone();
        float[] next = new float[n];
        for (int it = 0; it < iterations; it++) {
            for (int t = 0; t < n; t++) {
                if (h[t] <= 0f) { next[t] = h[t]; continue; }
                float sum = cur[t];
                int cnt = 1;
                for (int k = g.nbStart[t]; k < g.nbStart[t + 1]; k++) {
                    int nb = g.nbList[k];
                    if (h[nb] <= 0f) continue;
                    sum += cur[nb];
                    cnt++;
                }
                next[t] = sum / cnt;
            }
            float[] tmp = cur; cur = next; next = tmp;
        }
        return cur;
    }

    private static float[] fillDepressions(SphereGrid g, float[] elevation) {
        int n = g.tiles;
        float[] w = elevation.clone();
        boolean[] done = new boolean[n];
        java.util.PriorityQueue<float[]> pq = new java.util.PriorityQueue<>((a, b) -> Float.compare(a[0], b[0]));
        for (int t = 0; t < n; t++) {
            if (elevation[t] <= 0f) {
                done[t] = true;
                if (w[t] > 0f) w[t] = 0f;
                pq.add(new float[]{w[t], t});
            }
        }
        if (pq.isEmpty()) {
            for (int t = 0; t < n; t++) { done[t] = true; pq.add(new float[]{w[t], t}); }
        }
        while (!pq.isEmpty()) {
            float[] cur = pq.poll();
            int t = (int) cur[1];
            float lim = cur[0] + 0.01f;
            for (int k = g.nbStart[t]; k < g.nbStart[t + 1]; k++) {
                int nb = g.nbList[k];
                if (done[nb]) continue;
                done[nb] = true;
                if (w[nb] < lim) w[nb] = lim;
                pq.add(new float[]{w[nb], nb});
            }
        }
        return w;
    }

    /** 重新判定陆海。 */
    public static void updateLand(PlanetData d, float seaLevel) {
        for (int t = 0; t < d.n; t++) d.land[t] = d.elevation[t] > seaLevel;
    }
}
