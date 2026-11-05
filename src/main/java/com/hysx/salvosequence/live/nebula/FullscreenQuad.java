package com.hysx.salvosequence.live.nebula;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/** A single clip-space quad used for every full-screen pass. */
public final class FullscreenQuad {
    private final Mesh mesh;

    public FullscreenQuad() {
        mesh = new Mesh(true, 4, 6,
                VertexAttribute.Position(),
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord"));
        mesh.setVertices(new float[]{
                -1f, -1f, 0f, 0f, 0f,
                 1f, -1f, 0f, 1f, 0f,
                 1f,  1f, 0f, 1f, 1f,
                -1f,  1f, 0f, 0f, 1f,
        });
        mesh.setIndices(new short[]{0, 1, 2, 0, 2, 3});
    }

    public void draw(ShaderProgram shader) {
        mesh.render(shader, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES);
    }

    public void dispose() {
        mesh.dispose();
    }

    public static ShaderProgram load(String vert, String frag) {
        String v = Gdx.files.internal(vert).readString("UTF-8");
        String f = Gdx.files.internal(frag).readString("UTF-8");
        ShaderProgram p = new ShaderProgram(v, f);
        if (!p.isCompiled()) {
            throw new IllegalStateException(frag + " failed to compile:\n" + p.getLog());
        }
        return p;
    }
}
