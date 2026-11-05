// 来源：gleba-libgdx-mvp（蓝色大肥鱼）——Gleba 星球生成核心，包名归入本项目。
package com.hysx.salvosequence.gleba.gen;

import java.util.Random;

/**
 * 板块构造初始化（对应 gleba 的 tectonics.rs: generate_plates / determine_crust_age）。
 * 注意：原版不是 Voronoi 划分（spade 只用在本作的物理层），这里是"随机放种子 + 有效性检验 + 距离场归属"。
 */
public final class Tectonics {

    public static final class Plates {
        public int count;
        public float[] seedX, seedY, seedZ;
        public boolean[] continental;
        public float[] driftX, driftY, driftZ; // 切向漂移方向 × 速度
    }

    public static Plates build(SphereGrid g, PlanetParams p, PlanetData d, Noise noise, Random rnd) {
        int want = Math.max(3, p.plateCount);
        Plates plates = new Plates();
        plates.count = want;
        plates.seedX = new float[want];
        plates.seedY = new float[want];
        plates.seedZ = new float[want];

        // ---- 1. 种子放置 + 有效性检验（最小角距）----
        float minAngle = (float) (Math.sqrt(4.0 * Math.PI / want) * 0.85);
        int placed = 0;
        for (int k = 0; k < want; k++) {
            int bestTile = -1;
            float bestScore = -1;
            for (int attempt = 0; attempt < 64; attempt++) {
                int t = rnd.nextInt(g.tiles);
                float minD = Float.MAX_VALUE;
                for (int j = 0; j < placed; j++) {
                    float dot = g.px[t] * plates.seedX[j] + g.py[t] * plates.seedY[j] + g.pz[t] * plates.seedZ[j];
                    float ang = (float) Math.acos(Math.max(-1, Math.min(1, dot)));
                    if (ang < minD) minD = ang;
                }
                if (minD > bestScore) { bestScore = minD; bestTile = t; }
                if (minD >= minAngle) break;
            }
            if (bestTile < 0) bestTile = rnd.nextInt(g.tiles);
            plates.seedX[k] = g.px[bestTile];
            plates.seedY[k] = g.py[bestTile];
            plates.seedZ[k] = g.pz[bestTile];
            placed++;
        }

        // ---- 2. 归属：先把球面按"构造扭曲"扰动，再取最近种子（扭曲让板块边界不规则）----
        float warp = Math.max(0f, p.crustWarping);
        for (int t = 0; t < g.tiles; t++) {
            float x = g.px[t], y = g.py[t], z = g.pz[t];
            if (warp > 0.001f) {
                float s = 2.6f;
                x += warp * 0.55f * noise.fbm(g.px[t] * s + 11.3f, g.py[t] * s - 4.7f, g.pz[t] * s + 7.1f, 3);
                y += warp * 0.55f * noise.fbm(g.px[t] * s - 9.2f, g.py[t] * s + 5.5f, g.pz[t] * s + 1.9f, 3);
                z += warp * 0.55f * noise.fbm(g.px[t] * s + 3.4f, g.py[t] * s + 8.8f, g.pz[t] * s - 12.6f, 3);
                float len = (float) Math.sqrt(x * x + y * y + z * z);
                x /= len; y /= len; z /= len;
            }
            int best = 0;
            float bestD = -2;
            for (int k = 0; k < plates.count; k++) {
                float dot = x * plates.seedX[k] + y * plates.seedY[k] + z * plates.seedZ[k];
                if (dot > bestD) { bestD = dot; best = k; }
            }
            d.plate[t] = best;
        }

        // ---- 3. 地壳类型：低频噪声场决定大陆/大洋（比"整块板块就是大陆"自然），板块只负责边界 ----
        float[] score = new float[g.tiles];
        for (int t = 0; t < g.tiles; t++) {
            float x = g.px[t], y = g.py[t], z = g.pz[t];
            score[t] = 0.62f * noise.fbm(x * 1.55f + 21.7f, y * 1.55f - 13.1f, z * 1.55f + 4.9f, 5)
                    + 0.38f * noise.fbm(x * 3.7f - 7.3f, y * 3.7f + 9.1f, z * 3.7f - 2.5f, 3);
        }
        Integer[] rank = new Integer[g.tiles];
        for (int i = 0; i < rank.length; i++) rank[i] = i;
        final float[] sc = score;
        java.util.Arrays.sort(rank, (a, b) -> Float.compare(sc[b], sc[a]));
        int target = (int) (p.continentalFraction * g.tiles);
        for (int i = 0; i < g.tiles; i++) d.continental[rank[i]] = i < target;

        // 两轮多数表决：去掉椒盐噪点，让海岸线连续
        boolean[] tmpC = new boolean[g.tiles];
        for (int pass = 0; pass < 2; pass++) {
            for (int t = 0; t < g.tiles; t++) {
                int c = 0, tot = 0;
                for (int k = g.nbStart[t]; k < g.nbStart[t + 1]; k++) {
                    tot++;
                    if (d.continental[g.nbList[k]]) c++;
                }
                tmpC[t] = (tot == 0) ? d.continental[t] : (c * 2 > tot || (c * 2 == tot && d.continental[t]));
            }
            System.arraycopy(tmpC, 0, d.continental, 0, g.tiles);
        }

        plates.continental = new boolean[plates.count];
        int[] contCount = new int[plates.count];
        int[] plateSize = new int[plates.count];
        for (int t = 0; t < g.tiles; t++) {
            plateSize[d.plate[t]]++;
            if (d.continental[t]) contCount[d.plate[t]]++;
        }
        for (int k = 0; k < plates.count; k++) {
            plates.continental[k] = plateSize[k] > 0 && contCount[k] * 2 > plateSize[k];
        }

        // ---- 4. 漂移速度（cm/yr → 弧度/百万年的相对比例，只用于判断汇聚/离散）----
        plates.driftX = new float[plates.count];
        plates.driftY = new float[plates.count];
        plates.driftZ = new float[plates.count];
        float speed = Math.max(0.5f, p.plateSpeed);
        for (int k = 0; k < plates.count; k++) {
            float x = (float) (rnd.nextGaussian());
            float y = (float) (rnd.nextGaussian());
            float z = (float) (rnd.nextGaussian());
            float s = plates.seedX[k] * x + plates.seedY[k] * y + plates.seedZ[k] * z;
            x -= s * plates.seedX[k];
            y -= s * plates.seedY[k];
            z -= s * plates.seedZ[k];
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len < 1e-6f) { x = 1; y = 0; z = 0; len = 1; }
            float mag = speed * (0.4f + 0.9f * rnd.nextFloat()) / 5f;
            plates.driftX[k] = x / len * mag;
            plates.driftY[k] = y / len * mag;
            plates.driftZ[k] = z / len * mag;
        }

        // 海岸线锯齿：地壳边缘用低频噪声做小扰动（原版靠噪声破对称）
        for (int t = 0; t < g.tiles; t++) {
            if (p.crustWarping > 0.01f) {
                float n = noise.fbm(g.px[t] * 6.5f + 3.1f, g.py[t] * 6.5f - 2.2f, g.pz[t] * 6.5f + 5.4f, 2);
                if (Math.abs(n) > 0.42f) {
                    int deg = g.degree(t);
                    if (deg > 0) {
                        int nb = g.neighbor(t, rnd.nextInt(deg));
                        if (d.plate[nb] != d.plate[t]) d.continental[t] = d.continental[nb];
                    }
                }
            }
        }
        return plates;
    }
}
