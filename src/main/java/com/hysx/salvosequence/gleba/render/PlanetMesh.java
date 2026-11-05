// 来源：gleba-libgdx-mvp（蓝色大肥鱼）——Gleba 星球渲染（网格位移 / 大气散射）。
package com.hysx.salvosequence.gleba.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.hysx.salvosequence.gleba.gen.Biome;
import com.hysx.salvosequence.gleba.gen.PlanetData;

/**
 * 把 PlanetData 变成立方球网格，按**最近邻**呈现：每个 tile 独立一个四边形，
 * 四角共用同一高度与同一颜色，顶点之间不做任何插值 —— 就是放大像素的观感。
 *
 * 每面一个 Mesh：每面 res² 个四边形 × 4 顶点 = 36,864 顶点（res=96），
 * 仍能装进 16 位索引；整球若合成一个 Mesh 会超过 65,535 顶点上限。
 */
public final class PlanetMesh {

    private static final int[][] FACE_AXIS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private static final float[] RIVER = {0.16f, 0.42f, 0.66f};

    /** 六个面的网格集合。 */
    public static final class Meshes {
        private final Mesh[] faces;
        private final int quads;

        Meshes(Mesh[] faces, int quads) {
            this.faces = faces;
            this.quads = quads;
        }

        public void render(ShaderProgram shader) {
            for (Mesh m : faces) m.render(shader, GL20.GL_TRIANGLES);
        }

        public void dispose() {
            for (Mesh m : faces) m.dispose();
        }

        public int quadCount() {
            return quads;
        }
    }

    public static Meshes build(PlanetData d, float exaggeration, boolean showRivers) {
        int res = d.grid.faceRes;             // 一格 = 一个四边形，不做超采样
        int quadsPerFace = res * res;
        int vertsPerFace = quadsPerFace * 4;
        int idxPerFace = quadsPerFace * 6;

        // 河流阈值按本次最大汇流量自适应，避免不同大陆比例下河流一直看不见
        float riverFlow = 8f;
        if (showRivers) {
            float maxFlow = 0f;
            for (int t = 0; t < d.n; t++) if (d.land[t]) maxFlow = Math.max(maxFlow, d.water[t]);
            riverFlow = Math.max(6f, maxFlow * 0.035f);
        }

        Mesh[] meshes = new Mesh[6];
        float[] rgb = new float[3];

        for (int f = 0; f < 6; f++) {
            int[] ax = FACE_AXIS[f];
            int[] uAx, vAx;
            if (ax[0] != 0) { uAx = new int[]{0, 1, 0}; vAx = new int[]{0, 0, 1}; }
            else if (ax[1] != 0) { uAx = new int[]{1, 0, 0}; vAx = new int[]{0, 0, 1}; }
            else { uAx = new int[]{1, 0, 0}; vAx = new int[]{0, 1, 0}; }

            // 角点方向表：(res+1)²，避免每个四边形重复算四次归一化
            float[] cornerDir = new float[(res + 1) * (res + 1) * 3];
            for (int b = 0; b <= res; b++) {
                for (int a = 0; a <= res; a++) {
                    float u = 2f * a / res - 1f;
                    float v = 2f * b / res - 1f;
                    float x = ax[0] + uAx[0] * u + vAx[0] * v;
                    float y = ax[1] + uAx[1] * u + vAx[1] * v;
                    float z = ax[2] + uAx[2] * u + vAx[2] * v;
                    float len = (float) Math.sqrt(x * x + y * y + z * z);
                    int o = (b * (res + 1) + a) * 3;
                    cornerDir[o] = x / len;
                    cornerDir[o + 1] = y / len;
                    cornerDir[o + 2] = z / len;
                }
            }

            float[] verts = new float[vertsPerFace * 7];
            short[] idx = new short[idxPerFace];
            int vi = 0, ii = 0;
            int vertBase = 0;

            for (int j = 0; j < res; j++) {
                for (int i = 0; i < res; i++) {
                    // tile 中心方向 —— 用它做最近邻取格，取到的就是这一格自己的数据
                    float uc = 2f * (i + 0.5f) / res - 1f;
                    float vc = 2f * (j + 0.5f) / res - 1f;
                    float cx = ax[0] + uAx[0] * uc + vAx[0] * vc;
                    float cy = ax[1] + uAx[1] * uc + vAx[1] * vc;
                    float cz = ax[2] + uAx[2] * uc + vAx[2] * vc;
                    float cl = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
                    cx /= cl; cy /= cl; cz /= cl;

                    int t = d.grid.nearest(cx, cy, cz);
                    float elev = d.elevation[t];
                    float r;
                    // 位移硬上限 0.010，保证任何起伏都待在大气层（半径 1.022）以内
                    if (d.land[t]) {
                        r = 1f + Math.min(0.010f, Math.max(0f, elev) / 9000f * exaggeration);
                    } else {
                        // 【移植注记】海洋下沉同样乘 exaggeration：原式不乘的话，
                        // exaggeration=0 时海洋格仍按深度下沉（最多 0.002），
                        // 相邻格半径不齐 → 6 张面网格接缝处漏光的洞
                        r = 1f - Math.min(0.002f, Math.max(0f, -elev) / 6000f * 0.002f * exaggeration);
                    }

                    Biome.color(d, t, rgb);
                    float rr = rgb[0], gg = rgb[1], bb = rgb[2];
                    if (showRivers && d.land[t] && d.water[t] > riverFlow) {
                        float k = Math.min(0.65f, (float) (Math.log(d.water[t] / riverFlow) * 0.35f));
                        rr = rr * (1 - k) + RIVER[0] * k;
                        gg = gg * (1 - k) + RIVER[1] * k;
                        bb = bb * (1 - k) + RIVER[2] * k;
                    }
                    float packed = Color.toFloatBits(rr, gg, bb, 1f);

                    // 四个角：同一个 r、同一个法线（= tile 中心方向）、同一个颜色
                    int c00 = (j * (res + 1) + i) * 3;
                    int c10 = (j * (res + 1) + i + 1) * 3;
                    int c11 = ((j + 1) * (res + 1) + i + 1) * 3;
                    int c01 = ((j + 1) * (res + 1) + i) * 3;
                    vi = put(verts, vi, cornerDir, c00, r, cx, cy, cz, packed);
                    vi = put(verts, vi, cornerDir, c10, r, cx, cy, cz, packed);
                    vi = put(verts, vi, cornerDir, c11, r, cx, cy, cz, packed);
                    vi = put(verts, vi, cornerDir, c01, r, cx, cy, cz, packed);

                    short v0 = (short) vertBase;
                    short v1 = (short) (vertBase + 1);
                    short v2 = (short) (vertBase + 2);
                    short v3 = (short) (vertBase + 3);
                    idx[ii++] = v0; idx[ii++] = v1; idx[ii++] = v2;
                    idx[ii++] = v0; idx[ii++] = v2; idx[ii++] = v3;
                    vertBase += 4;
                }
            }

            Mesh m = new Mesh(false, vertsPerFace, idxPerFace,
                    new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                    new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
                    new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, "a_color"));
            m.setVertices(verts);
            m.setIndices(idx);
            meshes[f] = m;
        }

        return new Meshes(meshes, 6 * quadsPerFace);
    }

    private static int put(float[] verts, int vi, float[] dir, int cOff,
                           float r, float nx, float ny, float nz, float packed) {
        verts[vi] = dir[cOff] * r;
        verts[vi + 1] = dir[cOff + 1] * r;
        verts[vi + 2] = dir[cOff + 2] * r;
        verts[vi + 3] = nx;
        verts[vi + 4] = ny;
        verts[vi + 5] = nz;
        verts[vi + 6] = packed;
        return vi + 7;
    }

    private PlanetMesh() {}
}
