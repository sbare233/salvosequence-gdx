package com.hysx.salvosequence.live.star;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/** 一个 UV 球：位置即单位方向，着色完全在片元里按方向程序化生成。 */
public class StarSphere {
    private final Mesh mesh;
    private static final int RINGS = 72;
    private static final int SECTORS = 144;

    public StarSphere() {
        int vcount = (RINGS + 1) * (SECTORS + 1);
        int icount = RINGS * SECTORS * 6;
        float[] verts = new float[vcount * 3];
        short[] idx = new short[icount];

        int vi = 0;
        for (int r = 0; r <= RINGS; r++) {
            double phi = Math.PI * r / RINGS;            // 0..pi
            float y = (float) Math.cos(phi);
            float rad = (float) Math.sin(phi);
            for (int s = 0; s <= SECTORS; s++) {
                double theta = 2.0 * Math.PI * s / SECTORS;
                float x = (float) (rad * Math.cos(theta));
                float z = (float) (rad * Math.sin(theta));
                verts[vi++] = x;
                verts[vi++] = y;
                verts[vi++] = z;
            }
        }

        int ii = 0;
        for (int r = 0; r < RINGS; r++) {
            for (int s = 0; s < SECTORS; s++) {
                int a = r * (SECTORS + 1) + s;
                int b = a + 1;
                int c = a + (SECTORS + 1);
                int d = c + 1;
                // 逆时针绕序（从球外看）：否则背面剔除会把"朝向相机的半球"剔掉，
                // 只留下远侧半球的背面 —— 看起来就是"能看见太阳背面/中间是空的"
                idx[ii++] = (short) a; idx[ii++] = (short) b; idx[ii++] = (short) c;
                idx[ii++] = (short) b; idx[ii++] = (short) d; idx[ii++] = (short) c;
            }
        }

        mesh = new Mesh(true, vcount, icount, new VertexAttribute(Usage.Position, 3, "a_pos"));
        mesh.setVertices(verts);
        mesh.setIndices(idx);
    }

    public void render(ShaderProgram shader) {
        mesh.render(shader, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES);
    }

    public void dispose() {
        mesh.dispose();
    }
}
