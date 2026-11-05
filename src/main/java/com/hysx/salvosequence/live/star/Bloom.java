package com.hysx.salvosequence.live.star;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/**
 * 场景 FBO + 三级 bloom + 合成（含色散与衍射星芒）。
 * 场景先画进 scene FBO，日冕/日珥也在里面叠加，最后统一做 bloom 与合成。
 */
public class Bloom {
    private static final int LEVELS = 3;

    private final FullscreenQuad quad;
    private final ShaderProgram bright, blur, copy, composite;

    private FrameBuffer scene;
    private final FrameBuffer[] a = new FrameBuffer[LEVELS];
    private final FrameBuffer[] b = new FrameBuffer[LEVELS];

    private int width, height;

    public Bloom(int width, int height) {
        this.width = width;
        this.height = height;

        quad = new FullscreenQuad();
        bright = load("shaders/star/bright.frag");
        blur = load("shaders/star/blur.frag");
        copy = load("shaders/star/copy.frag");
        composite = load("shaders/star/composite.frag");

        scene = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, true);
        int w = Math.max(1, width / 2), h = Math.max(1, height / 2);
        for (int i = 0; i < LEVELS; i++) {
            a[i] = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
            b[i] = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
        }
    }

    private static ShaderProgram load(String frag) {
        ShaderProgram s = new ShaderProgram(
                Gdx.files.internal("shaders/star/quad.vert"),
                Gdx.files.internal(frag));
        if (!s.isCompiled()) throw new IllegalStateException(frag + ": " + s.getLog());
        return s;
    }

    public void resize(int width, int height) {
        if (width == this.width && height == this.height) return;
        this.width = width;
        this.height = height;
        scene.dispose();
        for (int i = 0; i < LEVELS; i++) { a[i].dispose(); b[i].dispose(); }
        scene = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, true);
        int w = Math.max(1, width / 2), h = Math.max(1, height / 2);
        for (int i = 0; i < LEVELS; i++) {
            a[i] = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
            b[i] = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
        }
    }

    public void beginScene() {
        scene.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    }

    /** 场景结束：做 bloom 并合成到默认帧缓冲。 */
    private void dbg(String tag) {
        if (System.getProperty("star.dbg") == null) return;
        java.nio.ByteBuffer bb = com.badlogic.gdx.utils.BufferUtils.newByteBuffer(4);
        Gdx.gl.glReadPixels(width / 2, height / 2, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, bb);
        Gdx.app.log("bloom", tag + " = " + (bb.get(0) & 0xff) + "," + (bb.get(1) & 0xff) + "," + (bb.get(2) & 0xff));
    }

    public void endSceneAndComposite(float bloomStrength, float spikes, float dispersion,
                                     float starU, float starV) {
        dbg("scene");
        scene.end();

        // 亮部提取 → level0
        a[0].begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        bright.begin();
        scene.getColorBufferTexture().bind(0);
        bright.setUniformi("u_scene", 0);
        bright.setUniformf("u_threshold", 0.95f);
        quad.render(bright);
        bright.end();
        a[0].end();
        a[0].begin(); dbg("bloom0"); a[0].end();

        // 逐级模糊 + 降采样
        for (int i = 0; i < LEVELS; i++) {
            blurLevel(a[i], b[i], i);
            if (i + 1 < LEVELS) {
                // 把当前级复制到下一级（更小）
                a[i + 1].begin();
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                copy.begin();
                a[i].getColorBufferTexture().bind(0);
                copy.setUniformi("u_tex", 0);
                copy.setUniformf("u_scale", 1f);
                quad.render(copy);
                copy.end();
                a[i + 1].end();
            }
        }

        // 把 level1/level2 叠加到 level0
        a[0].begin();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        copy.begin();
        copy.setUniformi("u_tex", 0);
        for (int i = 1; i < LEVELS; i++) {
            a[i].getColorBufferTexture().bind(0);
            copy.setUniformf("u_scale", 0.75f);
            quad.render(copy);
        }
        copy.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        a[0].end();

        // 合成到屏幕（必须关掉深度测试与混合，否则全屏四边形会被上一帧的深度挡掉）
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glViewport(0, 0, width, height);
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        composite.begin();
        scene.getColorBufferTexture().bind(0);
        composite.setUniformi("u_scene", 0);
        a[0].getColorBufferTexture().bind(1);
        composite.setUniformi("u_bloom", 1);
        composite.setUniformf("u_bloomStrength", bloomStrength);
        composite.setUniformf("u_spikes", spikes);
        composite.setUniformf("u_dispersion", dispersion);
        composite.setUniformf("u_starScreen", starU, starV);
        quad.render(composite);
        composite.end();
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    }

    private void blurLevel(FrameBuffer src, FrameBuffer dst, int level) {
        float tw = 1f / src.getWidth();
        float th = 1f / src.getHeight();

        dst.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        blur.begin();
        src.getColorBufferTexture().bind(0);
        blur.setUniformi("u_tex", 0);
        blur.setUniformf("u_dir", 1f, 0f);
        blur.setUniformf("u_texel", tw, th);
        quad.render(blur);
        blur.end();
        dst.end();

        src.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        blur.begin();
        dst.getColorBufferTexture().bind(0);
        blur.setUniformi("u_tex", 0);
        blur.setUniformf("u_dir", 0f, 1f);
        blur.setUniformf("u_texel", tw, th);
        quad.render(blur);
        blur.end();
        src.end();
    }

    public void dispose() {
        scene.dispose();
        for (int i = 0; i < LEVELS; i++) { a[i].dispose(); b[i].dispose(); }
        quad.dispose();
        bright.dispose();
        blur.dispose();
        copy.dispose();
        composite.dispose();
    }
}
