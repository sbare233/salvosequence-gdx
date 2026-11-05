package com.hysx.salvosequence.tools;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** 临时探针：复现"CPU 缓冲 → 纹理上传 → 绘制 → 模糊 pass → 扫描线 → 帧缓冲"全链。 */
public final class PresentProbe extends ApplicationAdapter {

    private static final int[] BAYER4 = {
            0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    private int[] src;
    private Texture tex;
    private Pixmap pm;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private ShaderProgram glowShader;
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
            shapes = new ShapeRenderer();
        } catch (Exception e) {
            e.printStackTrace();
            Gdx.app.exit();
        }
    }

    private static final String GLOW_VERT =
            "attribute vec4 a_position;\n" +
            "attribute vec2 a_texCoord0;\n" +
            "uniform mat4 u_projTrans;\n" +
            "varying vec2 v_tex;\n" +
            "void main() {\n" +
            "  v_tex = a_texCoord0;\n" +
            "  gl_Position = u_projTrans * a_position;\n" +
            "}\n";

    private static final String GLOW_FRAG =
            "varying vec2 v_tex;\n" +
            "uniform sampler2D u_texture;\n" +
            "uniform vec2 u_texel;\n" +
            "void main() {\n" +
            "  vec4 center = texture2D(u_texture, v_tex);\n" +
            "  vec3 sum = center.rgb * center.a * 0.196382;\n" +
            "  float wsum = 0.196382;\n" +
            "  for (int i = 0; i < 8; i++) {\n" +
            "    float a = 3.14159265 * float(i) / 4.0;\n" +
            "    vec2 o = u_texel * vec2(cos(a), sin(a));\n" +
            "    vec4 c1 = texture2D(u_texture, v_tex + o);\n" +
            "    vec4 c2 = texture2D(u_texture, v_tex + o * 2.2);\n" +
            "    vec4 c3 = texture2D(u_texture, v_tex + o * 3.0);\n" +
            "    sum += c1.rgb * c1.a * 0.196382;\n" +
            "    sum += c2.rgb * c2.a * 0.096382;\n" +
            "    sum += c3.rgb * c3.a * 0.039812;\n" +
            "    wsum += 0.196382 + 0.096382 + 0.039812;\n" +
            "  }\n" +
            "  gl_FragColor = vec4(sum / wsum, 1.0);\n" +
            "}\n";

    private void glowPass() {
        if (glowShader == null) {
            ShaderProgram.pedantic = false;
            glowShader = new ShaderProgram(GLOW_VERT, GLOW_FRAG);
            System.out.println("[probe-glow] isCompiled=" + glowShader.isCompiled());
            System.out.println("[probe-glow] log=" + glowShader.getLog());
            if (!glowShader.isCompiled()) {
                return;
            }
        }
        glowShader.bind();
        glowShader.setUniformf("u_texel", 1.5f / 640f, 1.5f / 360f);
        batch.setShader(glowShader);
        batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_COLOR);
        batch.enableBlending();
        batch.begin();
        batch.draw(tex, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.end();
        batch.setShader(null);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void uploadAndDraw(boolean withDither) {
        int w = 640, h = 360;
        java.nio.ByteBuffer bytes = java.nio.ByteBuffer.allocateDirect(w * h * 4);
        for (int i = 0; i < src.length; i++) {
            int p = src[i];
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            if (withDither) {
                int d = BAYER4[(i / w & 3) * 4 + (i % w & 3)];
                int delta = d < 4 ? -1 : d < 12 ? 0 : 1;
                r = Math.max(0, Math.min(255, r + delta));
                g = Math.max(0, Math.min(255, g + delta));
                b = Math.max(0, Math.min(255, b + delta));
            }
            bytes.put((byte) r).put((byte) g).put((byte) b).put((byte) ((p >> 24) & 0xFF));
        }
        bytes.flip();
        pm.getPixels().clear();
        pm.getPixels().put(bytes);
        pm.getPixels().flip();
        tex.draw(pm, 0, 0);
        batch.enableBlending();
        batch.begin();
        batch.draw(tex, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.end();
    }

    @Override
    public void render() {
        frame++;
        boolean dither = frame >= 2;
        boolean glow = frame >= 3;
        boolean scanlines = frame >= 4;
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        uploadAndDraw(dither);
        if (glow) {
            glowPass();
        }
        if (scanlines) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0f, 0f, 0f, 0.75f);
            for (float y = 0; y < Gdx.graphics.getHeight(); y += 3) {
                shapes.rect(0, y, Gdx.graphics.getWidth(), 1.5f);
            }
            shapes.end();
        }
        if (frame == 5) {
            dump();
            Gdx.app.exit();
        }
    }

    private void dump() {
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
            ImageIO.write(out, "png", new File("target/present-probe.png"));
            System.out.println("[present-probe] -> target/present-probe.png");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void dispose() {
        tex.dispose();
        pm.dispose();
        batch.dispose();
        shapes.dispose();
        if (glowShader != null) {
            glowShader.dispose();
        }
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setWindowedMode(1280, 720);
        c.setInitialVisible(false);
        new Lwjgl3Application(new PresentProbe(), c);
    }
}
