package com.hysx.salvosequence.live.nebula;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/** Bright pass + separable blur + additive composite, the cheap stand-in for Blender's Bloom Glare node. */
public final class BloomPipeline {
    private final FullscreenQuad quad = new FullscreenQuad();
    private final ShaderProgram bright = FullscreenQuad.load("shaders/nebula/tex.vert", "shaders/nebula/bright.frag");
    private final ShaderProgram blur = FullscreenQuad.load("shaders/nebula/tex.vert", "shaders/nebula/blur.frag");
    private final ShaderProgram composite = FullscreenQuad.load("shaders/nebula/tex.vert", "shaders/nebula/composite.frag");

    private FrameBuffer a;
    private FrameBuffer b;
    private int width;
    private int height;

    public BloomPipeline(int width, int height) {
        resize(width, height);
    }

    public void resize(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        int w = Math.max(1, this.width / 4);
        int h = Math.max(1, this.height / 4);
        if (a != null) {
            a.dispose();
            b.dispose();
        }
        a = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        b = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
    }

    public void render(FrameBuffer scene, float threshold, float strength, boolean enabled) {
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        int w = a.getWidth();
        int h = a.getHeight();

        if (enabled) {
            a.begin();
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            bright.bind();
            bright.setUniformi("u_tex", 0);
            bright.setUniformf("u_threshold", threshold);
            scene.getColorBufferTexture().bind(0);
            quad.draw(bright);
            a.end();

            blur.bind();
            blur.setUniformi("u_tex", 0);
            blur.setUniformf("u_dir", 1f / w, 0f);
            b.begin();
            a.getColorBufferTexture().bind(0);
            quad.draw(blur);
            b.end();

            blur.setUniformf("u_dir", 0f, 1f / h);
            a.begin();
            b.getColorBufferTexture().bind(0);
            quad.draw(blur);
            a.end();

            blur.setUniformf("u_dir", 1f / w, 0f);
            b.begin();
            a.getColorBufferTexture().bind(0);
            quad.draw(blur);
            b.end();

            blur.setUniformf("u_dir", 0f, 1f / h);
            a.begin();
            b.getColorBufferTexture().bind(0);
            quad.draw(blur);
            a.end();
        }

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        composite.bind();
        composite.setUniformi("u_scene", 0);
        composite.setUniformi("u_bloom", 1);
        composite.setUniformf("u_strength", enabled ? strength : 0f);
        scene.getColorBufferTexture().bind(0);
        a.getColorBufferTexture().bind(1);
        quad.draw(composite);
    }

    public void dispose() {
        quad.dispose();
        bright.dispose();
        blur.dispose();
        composite.dispose();
        a.dispose();
        b.dispose();
    }
}
