// 来源：gleba-libgdx-mvp（蓝色大肥鱼）——Gleba 星球生成核心，包名归入本项目。
package com.hysx.salvosequence.gleba.gen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * 生成流水线（去掉人文部分后的最小闭环）：
 * grid → tectonics → elevation/boundary → erosion → climate → koppen。
 * 与 Gleba 的 60 阶段相比，这里只保留到"分配 biome"为止，不再有 POP/文明/地理区划。
 */
public final class PlanetGenerator {

    public static final class Result {
        public final PlanetData data;
        public final Map<String, Long> stageMillis = new LinkedHashMap<>();
        public Result(PlanetData data) { this.data = data; }
    }

    /**
     * 陆地高程压缩：|e|/8000 做 1.4 次幂。中低海拔被拉低（大陆内部不再整片变成"高山"），
     * 只有真正的山峰还能越过高山线，从而让 ALPINE 只出现在山脊而不是整块高原上。
     */
    private static void compressLandElevation(PlanetData d) {
        for (int t = 0; t < d.n; t++) {
            if (!d.land[t]) continue;
            float e = d.elevation[t];
            if (e <= 0f) continue;
            float x = Math.min(1.15f, e / 8000f);
            d.elevation[t] = (float) Math.pow(x, 1.4f) * 8000f;
        }
    }

    /** 把海平面平移到"陆地占比 = fraction"的高程分位点（限制在 ±3000 m 内），保持相对地形不变。 */
    private static void normalizeSeaLevel(PlanetData d, float fraction) {
        int n = d.n;
        float[] sorted = d.elevation.clone();
        java.util.Arrays.sort(sorted);
        int idx = Math.max(0, Math.min(n - 1, (int) ((1f - fraction) * (n - 1))));
        float sl = Math.max(-3000f, Math.min(3000f, sorted[idx]));
        if (sl != 0f) for (int t = 0; t < n; t++) d.elevation[t] -= sl;
    }

    public static Result generate(PlanetParams p) {
        Result res = new Result(null);
        long t0 = System.nanoTime();
        Random rnd = new Random(p.seed * 6364136223846793005L + 1442695040888963407L);

        SphereGrid grid = new SphereGrid(p.faceRes);
        PlanetData d = new PlanetData(grid);
        long t1 = System.nanoTime();

        Noise noise = new Noise(p.seed);
        Tectonics.Plates plates = Tectonics.build(grid, p, d, noise, rnd);
        long t2 = System.nanoTime();

        Terrain.build(grid, p, d, plates, noise, rnd);
        long t3 = System.nanoTime();

        // 海平面归一化：让"大陆比例"滑杆真正决定最终陆地占比（否则边界负地形会把大陆吃掉）
        normalizeSeaLevel(d, p.continentalFraction);
        Erosion.updateLand(d, 0f);
        if (p.erosionEnabled) {
            Erosion.thermal(grid, d, Math.max(0, p.erosionIterations));
            long t3b = System.nanoTime();
            Erosion.hydraulic(grid, d, 0.55f);
            res.stageMillis.put("erosion.thermal", (t3b - t3) / 1_000_000);
            res.stageMillis.put("erosion.hydraulic", (System.nanoTime() - t3b) / 1_000_000);
        }
        // 海平面归一化：让"大陆比例"滑杆真正决定最终陆地占比（否则边界负地形会把大陆吃掉）
        normalizeSeaLevel(d, p.continentalFraction);
        Erosion.updateLand(d, 0f);
        compressLandElevation(d);
        Erosion.updateLand(d, 0f);
        long t4 = System.nanoTime();

        if (p.climateEnabled) {
            Climate.build(grid, p, d, noise, Math.max(4, p.advectionSteps));
        } else {
            java.util.Arrays.fill(d.tempC, 10f);
            java.util.Arrays.fill(d.rainMm, 600f);
            java.util.Arrays.fill(d.continentality, 0.3f);
        }
        long t5 = System.nanoTime();

        Biome.classify(d);
        long t6 = System.nanoTime();

        res.stageMillis.put("grid", (t1 - t0) / 1_000_000);
        res.stageMillis.put("tectonics", (t2 - t1) / 1_000_000);
        res.stageMillis.put("terrain", (t3 - t2) / 1_000_000);
        res.stageMillis.put("erosion(total)", (t4 - t3) / 1_000_000);
        res.stageMillis.put("climate", (t5 - t4) / 1_000_000);
        res.stageMillis.put("biome", (t6 - t5) / 1_000_000);
        res.stageMillis.put("TOTAL", (t6 - t0) / 1_000_000);

        Result r = new Result(d);
        r.stageMillis.putAll(res.stageMillis);
        return r;
    }
}
