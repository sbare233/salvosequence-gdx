package com.hysx.salvosequence.live.pulsar;

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
        bright = load("shaders/pulsar/bright.frag");
        blur = load("shaders/pulsar/blur.frag");
        copy = load("shaders/pulsar/copy.frag");
        composite = load("shaders/pulsar/composite.frag");

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
                Gdx.files.internal("shaders/pulsar/quad.vert"),
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
    public void endSceneAndComposite(float bloomStrength, float threshold, float exposure) {
        scene.end();

        // 亮部提取 → level0
        a[0].begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        bright.begin();
        scene.getColorBufferTexture().bind(0);
        bright.setUniformi("u_scene", 0);
        bright.setUniformf("u_threshold", threshold);
        quad.render(bright);
        bright.end();
        a[0].end();

        // 逐级模糊 + 降采样：每一级都做 水平+垂直 两趟高斯（原来只模糊到 b[i] 却从未被使用，
        // 结果 bloom 实际是"没模糊的亮部副本"，硬边叠加会在盘面上出现竖直亮/暗带）
        for (int i = 0; i < LEVELS; i++) {
            if (i > 0) {
                a[i].begin();
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                copy.begin();
                a[i - 1].getColorBufferTexture().bind(0);
                copy.setUniformi("u_tex", 0);
                copy.setUniformf("u_scale", 1f);
                quad.render(copy);
                copy.end();
                a[i].end();
            }
            blurLevel(a[i], b[i], 0);   // 水平
            blurLevel(b[i], a[i], 1);   // 垂直，结果回到 a[i]
        }

        // 把 level1/level2 叠加到 level0
        a[0].begin();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        copy.begin();
        copy.setUniformi("u_tex", 0);
        for (int i = 1; i < LEVELS; i++) {
            a[i].getColorBufferTexture().bind(0);
            copy.setUniformf("u_scale", 0.60f);
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
        composite.setUniformf("u_exposure", exposure);
        quad.render(composite);
        composite.end();
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    }

    /** 单方向高斯：src -> dst。level 0 = 水平，1 = 垂直。 */
    private void blurLevel(FrameBuffer src, FrameBuffer dst, int level) {
        float tw = 1f / src.getWidth();
        float th = 1f / src.getHeight();

        dst.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        blur.begin();
        src.getColorBufferTexture().bind(0);
        blur.setUniformi("u_tex", 0);
        blur.setUniformf("u_dir", level == 0 ? 1f : 0f, level == 0 ? 0f : 1f);
        blur.setUniformf("u_texel", tw, th);
        quad.render(blur);
        blur.end();
        dst.end();
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
