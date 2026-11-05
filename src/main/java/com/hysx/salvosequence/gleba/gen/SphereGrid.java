// 来源：gleba-libgdx-mvp（蓝色大肥鱼）——Gleba 星球生成核心，包名归入本项目。
package com.hysx.salvosequence.gleba.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 立方球（cube-sphere）网格：6 个面 × N×N 个 tile，共 6N² 个单元。
 * 对齐 gleba 的 cube_sphere_topology：单元中心映射到单位球面，邻接关系按角距建图。
 * 邻接用 CSR 存（nbStart / nbList），支持 BFS 距离场与邻域运算。
 */
public final class SphereGrid {
    public final int faceRes;
    public final int tiles;
    public final float[] px, py, pz;        // 单元中心单位向量
    public final float[] latDeg, lonDeg;    // 纬度/经度（度）
    public final float tileAngle;           // 相邻单元中心角距（弧度，约等于边长）

    public final int[] nbStart;
    public final int[] nbList;

    // ---- 空间哈希，用于"给我离某点最近的 tile"（平流回溯用）----
    private final float cellSize;
    private final Map<Long, int[]> buckets;
    private final int bucketDim;

    private static final int[][] FACE_AXIS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    public SphereGrid(int faceRes) {
        this.faceRes = faceRes;
        this.tiles = 6 * faceRes * faceRes;
        this.tileAngle = 2f / faceRes;

        px = new float[tiles];
        py = new float[tiles];
        pz = new float[tiles];
        latDeg = new float[tiles];
        lonDeg = new float[tiles];

        float[] tx = new float[tiles], ty = new float[tiles], tz = new float[tiles];
        int t = 0;
        for (int f = 0; f < 6; f++) {
            int[] ax = FACE_AXIS[f];
            // 面内两个切向轴
            int[] uAx, vAx;
            if (ax[0] != 0) { uAx = new int[]{0, 1, 0}; vAx = new int[]{0, 0, 1}; }
            else if (ax[1] != 0) { uAx = new int[]{1, 0, 0}; vAx = new int[]{0, 0, 1}; }
            else { uAx = new int[]{1, 0, 0}; vAx = new int[]{0, 1, 0}; }
            for (int j = 0; j < faceRes; j++) {
                for (int i = 0; i < faceRes; i++) {
                    float u = (2f * i + 1f) / faceRes - 1f;
                    float v = (2f * j + 1f) / faceRes - 1f;
                    float x = ax[0] + uAx[0] * u + vAx[0] * v;
                    float y = ax[1] + uAx[1] * u + vAx[1] * v;
                    float z = ax[2] + uAx[2] * u + vAx[2] * v;
                    float len = (float) Math.sqrt(x * x + y * y + z * z);
                    x /= len; y /= len; z /= len;
                    tx[t] = x; ty[t] = y; tz[t] = z;
                    t++;
                }
            }
        }

        // ---- 空间哈希 ----
        cellSize = Math.max(tileAngle * 1.5f, 0.02f);
        bucketDim = (int) Math.ceil(2.0 / cellSize) + 1;
        Map<Long, List<Integer>> tmp = new HashMap<>();
        for (int i = 0; i < tiles; i++) {
            long key = bucketKey(tx[i], ty[i], tz[i]);
            tmp.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        buckets = new HashMap<>(tmp.size() * 2);
        for (Map.Entry<Long, List<Integer>> e : tmp.entrySet()) {
            int[] arr = new int[e.getValue().size()];
            for (int i = 0; i < arr.length; i++) arr[i] = e.getValue().get(i);
            buckets.put(e.getKey(), arr);
        }
        for (int i = 0; i < tiles; i++) {
            px[i] = tx[i]; py[i] = ty[i]; pz[i] = tz[i];
            latDeg[i] = (float) Math.toDegrees(Math.asin(clamp(ty[i], -1, 1)));
            lonDeg[i] = (float) Math.toDegrees(Math.atan2(pz[i], px[i]));
        }

        // ---- 邻接（按角距，自适应局部畸变）----
        List<List<Integer>> adj = new ArrayList<>(tiles);
        for (int i = 0; i < tiles; i++) adj.add(new ArrayList<>(8));
        float searchRadius = tileAngle * 2.2f;
        for (int i = 0; i < tiles; i++) {
            int[] cand = queryRadius(px[i], py[i], pz[i], searchRadius);
            float[] ang = new float[cand.length];
            for (int k = 0; k < cand.length; k++) {
                ang[k] = (float) Math.acos(clamp(dot(px[i], py[i], pz[i], px[cand[k]], py[cand[k]], pz[cand[k]]), -1, 1));
            }
            // 按角距排序（候选很少，插入排序足够）
            for (int a = 1; a < cand.length; a++) {
                int cv = cand[a];
                float ca = ang[a];
                int b = a - 1;
                while (b >= 0 && ang[b] > ca) { cand[b + 1] = cand[b]; ang[b + 1] = ang[b]; b--; }
                cand[b + 1] = cv;
                ang[b + 1] = ca;
            }
            float nearest = Float.MAX_VALUE;
            for (int k = 0; k < cand.length; k++) {
                if (cand[k] != i && ang[k] > 1e-7f) { nearest = ang[k]; break; }
            }
            if (nearest == Float.MAX_VALUE) continue;
            int taken = 0;
            for (int k = 0; k < cand.length && taken < 8; k++) {
                if (cand[k] == i) continue;
                if (ang[k] > nearest * 1.62f) break;
                adj.get(i).add(cand[k]);
                taken++;
            }
        }
        // 对称化
        for (int i = 0; i < tiles; i++) {
            for (int nb : adj.get(i)) {
                if (!adj.get(nb).contains(i)) adj.get(nb).add(i);
            }
        }
        nbStart = new int[tiles + 1];
        for (int i = 0; i < tiles; i++) nbStart[i + 1] = nbStart[i] + adj.get(i).size();
        nbList = new int[nbStart[tiles]];
        for (int i = 0; i < tiles; i++) {
            List<Integer> l = adj.get(i);
            for (int k = 0; k < l.size(); k++) nbList[nbStart[i] + k] = l.get(k);
        }
    }

    public int degree(int t) { return nbStart[t + 1] - nbStart[t]; }

    public int neighbor(int t, int k) { return nbList[nbStart[t] + k]; }

    /** 最近单元（平流回溯 / 风向采样）。p 不必归一化。无临时分配。 */
    public int nearest(float x, float y, float z) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len < 1e-6f) return 0;
        x /= len; y /= len; z /= len;
        for (float r = cellSize; r <= 0.5f; r *= 1.6f) {
            int span = Math.max(1, (int) Math.ceil(r / cellSize));
            int bx = (int) ((x + 1f) / cellSize);
            int by = (int) ((y + 1f) / cellSize);
            int bz = (int) ((z + 1f) / cellSize);
            int best = -1;
            float bestD = -2f;
            for (int dx = -span; dx <= span; dx++) {
                for (int dy = -span; dy <= span; dy++) {
                    for (int dz = -span; dz <= span; dz++) {
                        long key = key(bx + dx, by + dy, bz + dz);
                        int[] arr = buckets.get(key);
                        if (arr == null) continue;
                        for (int c : arr) {
                            float d = dot(x, y, z, px[c], py[c], pz[c]);
                            if (d > bestD) { bestD = d; best = c; }
                        }
                    }
                }
            }
            if (best >= 0) return best;
        }
        // 兜底：线性扫描（极少发生）
        int best = 0;
        float bestD = -2;
        for (int i = 0; i < tiles; i++) {
            float d = dot(x, y, z, px[i], py[i], pz[i]);
            if (d > bestD) { bestD = d; best = i; }
        }
        return best;
    }

    /** 东向单位切向量（沿经度增加方向）。 */
    public void east(int t, float[] out) {
        // east = normalize(cross(north, p))
        float nx = 0, ny = 1, nz = 0;
        float cx = ny * pz[t] - nz * py[t];
        float cy = nz * px[t] - nx * pz[t];
        float cz = nx * py[t] - ny * px[t];
        float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (len < 1e-6f) { out[0] = 1; out[1] = 0; out[2] = 0; return; }
        out[0] = cx / len; out[1] = cy / len; out[2] = cz / len;
    }

    /** 北向单位切向量。 */
    public void north(int t, float[] out) {
        float[] e = new float[3];
        east(t, e);
        float cx = e[1] * pz[t] - e[2] * py[t];
        float cy = e[2] * px[t] - e[0] * pz[t];
        float cz = e[0] * py[t] - e[1] * px[t];
        float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (len < 1e-6f) { out[0] = 0; out[1] = 1; out[2] = 0; return; }
        out[0] = cx / len; out[1] = cy / len; out[2] = cz / len;
    }

    // ------------------------------------------------------------------

    private long key(int bx, int by, int bz) {
        return ((long) (bx & 0x3FF) << 40) | ((long) (by & 0x3FF) << 20) | (bz & 0x3FF);
    }

    private long bucketKey(float x, float y, float z) {
        int bx = (int) ((x + 1f) / cellSize);
        int by = (int) ((y + 1f) / cellSize);
        int bz = (int) ((z + 1f) / cellSize);
        return key(bx, by, bz);
    }

    private int[] queryRadius(float x, float y, float z, float radius) {
        int span = Math.max(1, (int) Math.ceil(radius / cellSize));
        int bx = (int) ((x + 1f) / cellSize);
        int by = (int) ((y + 1f) / cellSize);
        int bz = (int) ((z + 1f) / cellSize);
        List<int[]> found = new ArrayList<>();
        int total = 0;
        for (int dx = -span; dx <= span; dx++) {
            for (int dy = -span; dy <= span; dy++) {
                for (int dz = -span; dz <= span; dz++) {
                    long key = (((long) ((bx + dx) & 0x3FF)) << 40)
                            | (((long) ((by + dy) & 0x3FF)) << 20)
                            | (((bz + dz) & 0x3FF));
                    int[] b = buckets.get(key);
                    if (b != null) { found.add(b); total += b.length; }
                }
            }
        }
        int[] out = new int[total];
        int p = 0;
        for (int[] b : found) { System.arraycopy(b, 0, out, p, b.length); p += b.length; }
        return out;
    }

    private static float dot(float ax, float ay, float az, float bx, float by, float bz) {
        return ax * bx + ay * by + az * bz;
    }

    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    /** 从一组源 tile 出发做多源 BFS，返回每 tile 的跳数（不可达为 -1）。 */
    public int[] bfsDistance(int[] sources) {
        int[] dist = new int[tiles];
        Arrays.fill(dist, -1);
        int[] queue = new int[tiles];
        int head = 0, tail = 0;
        for (int s : sources) {
            if (dist[s] == -1) { dist[s] = 0; queue[tail++] = s; }
        }
        while (head < tail) {
            int cur = queue[head++];
            int d = dist[cur] + 1;
            for (int k = nbStart[cur]; k < nbStart[cur + 1]; k++) {
                int nb = nbList[k];
                if (dist[nb] == -1) { dist[nb] = d; queue[tail++] = nb; }
            }
        }
        return dist;
    }
}
