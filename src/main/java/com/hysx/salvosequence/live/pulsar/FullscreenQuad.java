package com.hysx.salvosequence.live.pulsar;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/** 覆盖整个视口的两个三角形，用于全屏后处理（日冕/日珥、bloom、合成）。 */
public class FullscreenQuad {
    private final Mesh mesh;

    public FullscreenQuad() {
        // 位置 (-1..1) + uv (0..1)
        float[] v = {
                -1f, -1f, 0f, 0f,
                 1f, -1f, 1f, 0f,
                 1f,  1f, 1f, 1f,
                -1f, -1f, 0f, 0f,
                 1f,  1f, 1f, 1f,
                -1f,  1f, 0f, 1f,
        };
        mesh = new Mesh(false, 6, 0,
                new VertexAttribute(Usage.Position, 2, "a_pos"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_uv"));
        mesh.setVertices(v);
    }

    public void render(ShaderProgram shader) {
        mesh.render(shader, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES);
    }

    public void dispose() {
        mesh.dispose();
    }
}
