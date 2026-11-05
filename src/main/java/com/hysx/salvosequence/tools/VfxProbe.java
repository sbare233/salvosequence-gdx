package com.hysx.salvosequence.tools;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.crashinvaders.vfx.VfxManager;
import com.crashinvaders.vfx.effects.BloomEffect;
import com.hysx.salvosequence.core.PngLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** 临时探针：复现 gdx-vfx 辉光链 + GargantuaSky 的纹理单元泄漏假设。 */
public final class VfxProbe extends ApplicationAdapter {

    private int[] src;
    private Pixmap pm;
    private Texture tex;
    private Texture noiseTex;
    private Texture diskTex;
    private VfxManager manager;
    private BloomEffect bloom;
    private SpriteBatch batch;
    private FrameBuffer fbo;
    private int frame;

    @Override
    public void create() {
        try {
            BufferedImage bi = ImageIO.read(new File("target/ingame-buffer.png"));
            int w = bi.getWidth(), h = bi.getHeight();
            src = new int[w * h];
            bi.getRGB(0, 0, w, h, src, 0, w);
            pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
            tex = new Texture(pm);
            tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            batch = new SpriteBatch();
            fbo = new FrameBuffer(Pixmap.Format.RGBA8888, 320, 180, true);

            manager = new VfxManager(Pixmap.Format.RGBA8888);
            bloom = new BloomEffect();
            bloom.setThreshold(0.2f);
            bloom.disableBlending();
            manager.addEffect(bloom);
            manager.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

            // 模拟 GargantuaSky 的纹理单元泄漏：unit0=噪声，unit1=盘纹理
            PngLoader.Bitmap n = PngLoader.loadFromAssets("textures/rgba_noise_medium_256.png");
            PngLoader.Bitmap d = PngLoader.loadFromAssets("textures/organic_1_1024.png");
            noiseTex = texFromBitmap(n);
            diskTex = texFromBitmap(d);
            noiseTex.bind(0);
            diskTex.bind(1);
        } catch (Exception e) {
            e.printStackTrace();
            Gdx.app.exit();
        }
    }

    private static Texture texFromBitmap(PngLoader.Bitmap bm) {
        if (bm == null) return null;
        Pixmap pm = new Pixmap(bm.width(), bm.height(), Pixmap.Format.RGBA8888);
        java.nio.ByteBuffer bytes = pm.getPixels();
        for (int i = 0; i < bm.argb().length; i++) {
            int p = bm.argb()[i];
            bytes.put(i * 4, (byte) ((p >> 16) & 0xFF));
            bytes.put(i * 4 + 1, (byte) ((p >> 8) & 0xFF));
            bytes.put(i * 4 + 2, (byte) (p & 0xFF));
            bytes.put(i * 4 + 3, (byte) ((p >> 24) & 0xFF));
        }
        Texture t = new Texture(pm);
        pm.dispose();
        return t;
    }

    @Override
    public void render() {
        frame++;
        if (frame < 3) {
            Gdx.gl.glClearColor(0, 0, 0, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            return;
        }
        // 场景 FBO：画干净缓冲
        fbo.begin();
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin();
        batch.disableBlending();
        batch.draw(tex, 0, 0, fbo.getWidth(), fbo.getHeight());
        batch.end();
        fbo.end();

        // 辉光链
        manager.update(0.016f);
        manager.cleanUpBuffers();
        manager.beginInputCapture();
        batch.begin();
        batch.draw(fbo.getColorBufferTexture(), 0, 0,
                Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.end();
        manager.endInputCapture();
        manager.applyEffects();
        manager.renderToScreen();

        if (frame == 5) {
            Pixmap shot = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(
                    0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            java.nio.ByteBuffer b = shot.getPixels();
            BufferedImage out = new BufferedImage(shot.getWidth(), shot.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < shot.getHeight(); y++) {
                for (int x = 0; x < shot.getWidth(); x++) {
                    int i = (y * shot.getWidth() + x) * 4;
                    out.setRGB(x, y, (b.get(i) & 0xFF) << 16 | (b.get(i + 1) & 0xFF) << 8
                            | (b.get(i + 2) & 0xFF));
                }
            }
            try {
                ImageIO.write(out, "png", new File("target/vfx-probe.png"));
                System.out.println("[vfx-probe] -> target/vfx-probe.png");
            } catch (Exception e) {
                e.printStackTrace();
            }
            Gdx.app.exit();
        }
    }

    @Override
    public void dispose() {
        tex.dispose();
        pm.dispose();
        batch.dispose();
        fbo.dispose();
        manager.dispose();
        bloom.dispose();
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setWindowedMode(1280, 720);
        c.setInitialVisible(false);
        new Lwjgl3Application(new VfxProbe(), c);
    }
}
