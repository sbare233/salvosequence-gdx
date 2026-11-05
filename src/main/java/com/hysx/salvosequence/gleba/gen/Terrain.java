// 来源：gleba-libgdx-mvp（蓝色大肥鱼）——Gleba 星球生成核心，包名归入本项目。
package com.hysx.salvosequence.gleba.gen;

import com.badlogic.gdx.utils.IntArray;

import java.util.Random;

/**
 * 高程生成（对应 gleba 的 elevation.rs / boundary_features.rs / volcanism.rs 的自然部分）。
 * 流程：地壳基准面 → 板块边界分类（海沟/洋脊/裂谷/安第斯/喜马拉雅/西藏）→ 距离场叠加剖面
 *      → 热点火山 → 老山脉 → 大陆架 → 海洋平滑。
 */
public final class Terrain {
    public static final int TRENCH = 0, RIDGE = 1, RIFT = 2, ANDEAN = 3, HIMALAYA = 4, TIBET = 5, SHELF = 6, OLD = 7, HOTSPOT = 8;
    private static final int CLASSES = 9;

    /** 剖面：t=0 在山脉轴部，t>=1 无影响。e 越小越"平顶"（西藏高原 e≈0.0125）。 */
    private static float shape(float t, float e) {
        if (t >= 1f) return 0f;
        float s = 1f - t;
        if (s <= 0f) return 0f;
        return (float) Math.pow(s, e);
    }

    public static void build(SphereGrid g, PlanetParams p, PlanetData d, Tectonics.Plates plates,
                             Noise noise, Random rnd) {
        final int n = g.tiles;

        // ---- 1. 基准面：大陆 +260m，洋底 -4400m，各叠一层 fbm + 山脊噪声 ----
        for (int t = 0; t < n; t++) {
            float x = g.px[t], y = g.py[t], z = g.pz[t];
            float fbm = noise.fbm(x * 2.2f + 1.7f, y * 2.2f - 3.3f, z * 2.2f + 0.9f, 5);
            boolean cont = d.continental[t];
            float base = cont ? 180f : -4400f;
            float amp = cont ? 520f : 900f;
            d.elevation[t] = base + fbm * amp * p.noiseStrength;
            if (cont) {
                // 低频道"大陆穹隆"：给出区域性排水分水岭，否则高频噪声会把地形打成无数小洼地，
                // 汇流累积永远长不成大河（实测：加这一项前最大流域只有 ~90 格）
                d.elevation[t] += 700f * noise.fbm(x * 0.85f + 31f, y * 0.85f - 17f, z * 0.85f + 9f, 3) * p.noiseStrength;
                // 山脊化细节：让大陆内部不是平铺的 fbm，而是有走向的山系纹理
                float ridge = noise.ridged(x * 3.4f - 5.1f, y * 3.4f + 2.7f, z * 3.4f - 8.3f, 4);
                d.elevation[t] += ridge * 260f * p.noiseStrength;
            } else {
                float ridge = noise.ridged(x * 2.6f + 6.3f, y * 2.6f - 1.9f, z * 2.6f + 3.7f, 3);
                d.elevation[t] -= ridge * 380f * p.noiseStrength;
            }
        }

        // ---- 2. 板块边界分类 ----
        IntArray[] src = new IntArray[CLASSES];
        for (int i = 0; i < CLASSES; i++) src[i] = new IntArray(false, 256);
        float speedFactor = 0.55f + 0.45f * (Math.max(0.5f, p.plateSpeed) / 5f);

        for (int a = 0; a < n; a++) {
            for (int k = g.nbStart[a]; k < g.nbStart[a + 1]; k++) {
                int b = g.nbList[k];
                if (b <= a) continue;
                if (d.plate[a] == d.plate[b]) continue;

                // 边界法向（切向分量）与相对漂移
                float nx = g.px[b] - g.px[a], ny = g.py[b] - g.py[a], nz = g.pz[b] - g.pz[a];
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (nl < 1e-6f) continue;
                nx /= nl; ny /= nl; nz /= nl;
                float rx = plates.driftX[d.plate[b]] - plates.driftX[d.plate[a]];
                float ry = plates.driftY[d.plate[b]] - plates.driftY[d.plate[a]];
                float rz = plates.driftZ[d.plate[b]] - plates.driftZ[d.plate[a]];
                boolean convergent = (rx * nx + ry * ny + rz * nz) < 0f;

                boolean ca = d.continental[a], cb = d.continental[b];
                if (!ca && !cb) {
                    (convergent ? src[TRENCH] : src[RIDGE]).add(a, b);
                } else if (ca && cb) {
                    (convergent ? src[HIMALAYA] : src[RIFT]).add(a, b);
                } else {
                    if (convergent) {
                        src[TRENCH].add(ca ? b : a);       // 俯冲侧（大洋）
                        src[ANDEAN].add(ca ? a : b);       // 上盘（大陆）→ 安第斯
                    } else {
                        src[SHELF].add(ca ? a : b);        // 被动大陆边缘
                    }
                }
            }
        }

        // ---- 3. 距离场叠加剖面 ----
        applyDist(g, d, src[TRENCH], 3.2f, 5.0f, -4300f * speedFactor, -1, false);
        applyDist(g, d, src[RIDGE], 5.0f, 2.0f, +1900f * speedFactor, -1, true);
        applyDist(g, d, src[RIFT], 3.0f, 0.7f, -1700f, -1, true);
        applyDist(g, d, src[ANDEAN], 4.5f, 0.40f, +2500f * speedFactor, 1, false);
        applyDist(g, d, src[HIMALAYA], 3.6f, 0.40f, +3900f * speedFactor, 1, true);
        // 西藏高原：喜马拉雅带后方 2~3 格开始的大范围平坦抬升
        int[] himalayaDist = g.bfsDistance(src[HIMALAYA].toArray());
        IntArray tibetSrc = new IntArray(false, 512);
        for (int t = 0; t < n; t++) {
            if (d.continental[t] && himalayaDist[t] >= 2 && himalayaDist[t] <= 3) tibetSrc.add(t);
        }
        applyDist(g, d, tibetSrc, 13f, 0.0125f, +2400f * speedFactor, 1, true);

        // ---- 4. 热点火山（Hawaiian / Icelandic 两种频率的简化）----
        int hotspots = 4 + rnd.nextInt(6);
        float coneAngle = g.tileAngle * 1.5f;
        for (int h = 0; h < hotspots; h++) {
            int c = rnd.nextInt(n);
            float amp = 900f + rnd.nextFloat() * 2600f;
            // 锥体：角距越近越高
            for (int t = 0; t < n; t++) {
                float dot = g.px[t] * g.px[c] + g.py[t] * g.py[c] + g.pz[t] * g.pz[c];
                float ang = (float) Math.acos(Math.max(-1, Math.min(1, dot)));
                if (ang < coneAngle) {
                    float f = 1f - ang / coneAngle;
                    d.elevation[t] += amp * f * f;
                }
            }
            // 热点链：沿漂移方向拖出一串小岛
            float dx = plates.driftX[d.plate[c]] * 0.8f, dy = plates.driftY[d.plate[c]] * 0.8f, dz = plates.driftZ[d.plate[c]] * 0.8f;
            for (int s = 1; s <= 4; s++) {
                float x = g.px[c] + dx * s * g.tileAngle * 0.6f;
                float y = g.py[c] + dy * s * g.tileAngle * 0.6f;
                float z = g.pz[c] + dz * s * g.tileAngle * 0.6f;
                int t = g.nearest(x, y, z);
                d.elevation[t] += amp * 0.5f / s;
            }
        }

        // ---- 5. 老山脉 / 老丘陵（内陆随机抬升）----
        int oldRanges = 10 + rnd.nextInt(14);
        for (int o = 0; o < oldRanges; o++) {
            int c = rnd.nextInt(n);
            if (!d.continental[c]) continue;
            float amp = 700f + rnd.nextFloat() * 1600f;
            float width = g.tileAngle * (3f + rnd.nextFloat() * 5f);
            for (int t = 0; t < n; t++) {
                if (!d.continental[t]) continue;
                float dot = g.px[t] * g.px[c] + g.py[t] * g.py[c] + g.pz[t] * g.pz[c];
                float ang = (float) Math.acos(Math.max(-1, Math.min(1, dot)));
                if (ang < width) {
                    float f = 1f - ang / width;
                    d.elevation[t] += amp * f * f * (0.7f + 0.3f * noise.noise(g.px[t] * 14f, g.py[t] * 14f, g.pz[t] * 14f));
                }
            }
        }

        // ---- 6. 克拉通 / 内陆盆地起伏 ----
        for (int t = 0; t < n; t++) {
            if (!d.continental[t]) continue;
            float inner = noise.fbm(g.px[t] * 9f - 5.2f, g.py[t] * 9f + 2.4f, g.pz[t] * 9f - 7.7f, 3);
            d.elevation[t] += inner * 180f * p.noiseStrength;
        }

        // ---- 7. 大陆架：靠近大陆的浅海抬到 -30m 左右 ----
        IntArray contSrc = new IntArray(false, n / 3);
        for (int t = 0; t < n; t++) if (d.continental[t]) contSrc.add(t);
        int[] contDist = g.bfsDistance(contSrc.toArray());
        for (int t = 0; t < n; t++) {
            if (d.continental[t] || contDist[t] < 0 || contDist[t] > 2) continue;
            float target = -420f + 140f * contDist[t];
            if (d.elevation[t] < target) d.elevation[t] = Math.min(-25f, target);
        }

        // ---- 8. 海洋平滑（原版 Smoothing ocean elevation）----
        float[] tmp = new float[n];
        System.arraycopy(d.elevation, 0, tmp, 0, n);
        for (int it = 0; it < 3; it++) {
            for (int t = 0; t < n; t++) {
                if (d.continental[t]) continue;
                int deg = g.degree(t);
                float sum = tmp[t];
                int cnt = 1;
                for (int k = g.nbStart[t]; k < g.nbStart[t + 1]; k++) {
                    int nb = g.nbList[k];
                    if (d.continental[nb]) continue;
                    sum += tmp[nb];
                    cnt++;
                }
                d.elevation[t] = sum / cnt;
            }
            System.arraycopy(d.elevation, 0, tmp, 0, n);
        }
    }

    /**
     * @param sideMask 0=不限, 1=仅大陆
     * @param sign     +1 抬升, -1 下陷
     */
    private static void applyDist(SphereGrid g, PlanetData d, IntArray sources, float widthTiles,
                                  float exponent, float amp, int sideMask, boolean smoothRidge) {
        if (sources.size == 0) return;
        int[] dist = g.bfsDistance(sources.toArray());
        for (int t = 0; t < g.tiles; t++) {
            int dd = dist[t];
            if (dd < 0) continue;
            if (sideMask == 1 && !d.continental[t]) continue;
            float tt = dd / widthTiles;
            float f = shape(tt, exponent);
            if (f <= 0) continue;
            float add = amp * f;
            if (smoothRidge) {
                // 沿轴部的锯齿：用噪声调制，避免出现完美的数学剖面
                float nn = 0.75f + 0.25f * (float) Math.sin(g.px[t] * 31f + g.py[t] * 17f + g.pz[t] * 23f);
                add *= nn;
            }
            d.elevation[t] += add;
        }
    }
}
