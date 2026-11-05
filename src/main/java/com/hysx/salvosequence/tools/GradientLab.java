package com.hysx.salvosequence.tools;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelArt;

import java.nio.ByteBuffer;

/**
 * 渐变诊断窗（定位"渐变到透明在哪一级出问题"）。
 *
 * <p>纯黑背景上画三行<b>同一条</b>「纯蓝 → 全透明」水平线性渐变（纯蓝 0,0,255，
 * alpha 从 255 线性降到 0，非预乘 ARGB），三行走游戏的真实管线逐级加料：</p>
 * <ol>
 *   <li><b>行 1 · 原始图层</b>：图层在设计分辨率 1:1 直接叠加 —— 管线基准；</li>
 *   <li><b>行 2 · 完美像素</b>：同一图层先降到 1/3 分辨率，再最近邻放大 3 倍 ——
 *       大像素块；</li>
 *   <li><b>行 3 · 拜耳滤镜</b>：行 2 的源再过 {@code blitArgbTileDithered}
 *       （默认 quantize=14，与星系盘一致；↑/↓ 调节，Esc 退出）。</li>
 * </ol>
 *
 * <p>三行最终都走游戏同一条上屏管线（PixelArt int[] → RGBA8888 → 纹理 Nearest 3 倍）。
 * 哪一行开始出现硬边/断层，问题就在哪一级：行 1 坏 = 上屏管线；行 2 坏 = 缩放采样；
 * 行 3 坏 = 拜耳量化。跑法：
 * {@code mvn compile exec:java -Dexec.mainClass=com.hysx.salvosequence.tools.GradientLab}</p>
 */
public final class GradientLab {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("渐变诊断：原始图层 / 完美像素 / 拜耳滤镜（↑↓ 调 quantize，Esc 退出）");
        config.setWindowedMode(1920, 1080);
        new Lwjgl3Application(new View(), config);
    }

    private GradientLab() {
    }

    private static final class View extends ApplicationAdapter {

        /** 行内渐变图层尺寸：行 1 全分辨率；行 2/3 用它的 1/3（模拟低清图层放大）。 */
        private static final int W = 640;
        private static final int H = 92;
        private static final int LW = W / 3;
        private static final int LH = H / 3;

        private final CanvasBuffer buffer = new CanvasBuffer(640, 360);
        private Texture texture;
        private Pixmap pixmap;
        private final ByteBuffer rgbaBytes = ByteBuffer.allocateDirect(640 * 360 * 4);
        private SpriteBatch batch;

        private int[] srcFull;
        private int[] srcLow;
        private int quantize = 14;

        @Override
        public void create() {
            batch = new SpriteBatch();
            pixmap = new Pixmap(640, 360, Pixmap.Format.RGBA8888);
            texture = new Texture(pixmap);
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            buffer.art().attachSink(new FrameSink());
            buildSources();

            Gdx.input.setInputProcessor(new InputAdapter() {
                @Override
                public boolean keyDown(int keycode) {
                    if (keycode == Input.Keys.UP) {
                        quantize = Math.min(40, quantize + 2);
                        return true;
                    }
                    if (keycode == Input.Keys.DOWN) {
                        quantize = Math.max(2, quantize - 2);
                        return true;
                    }
                    if (keycode == Input.Keys.ESCAPE) {
                        Gdx.app.exit();
                        return true;
                    }
                    return false;
                }
            });
        }

        /** 纯蓝 (0,0,255)，alpha 从左 255 线性降到右 0（非预乘 ARGB）。 */
        private void buildSources() {
            srcFull = new int[W * H];
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    int a = 255 - (int) (255L * x / (W - 1));
                    srcFull[y * W + x] = (a << 24) | 0x0000FF;
                }
            }
            srcLow = new int[LW * LH];
            for (int y = 0; y < LH; y++) {
                for (int x = 0; x < LW; x++) {
                    int a = 255 - (int) (255L * x / (LW - 1));
                    srcLow[y * LW + x] = (a << 24) | 0x0000FF;
                }
            }
        }

        @Override
        public void render() {
            PixelArt art = buffer.art();
            buffer.beginFrame();
            art.fill(Palette.BACKDROP);   // 纯黑背景

            // 行 1：设计分辨率 1:1 叠加（基准）
            int y1 = 22;
            art.blitArgb(srcFull, W, H, 0, y1, false);
            buffer.text("1 原始图层  设计分辨率 1:1 叠加（基准）", 8, 16, 12, Palette.LINE);

            // 行 2：1/3 源最近邻放大 3 倍（完美像素块）
            int y2 = 142;
            art.blitArgbTile(srcLow, LW, LH, 0, 0, 0, y2, W, H, false, false, false, 1.0);
            buffer.text("2 完美像素  1/3 源最近邻放大 3 倍", 8, 136, 12, Palette.LINE);

            // 行 3：行 2 的源 + 拜耳抖动（quantize 可调，14 = 星系盘当前值）
            int y3 = 262;
            art.blitArgbTileDithered(srcLow, LW, LH, 0, 0, 0, y3, W, H, false, 1.0, quantize);
            buffer.text("3 拜耳滤镜  quantize=" + quantize + "（↑↓ 调节，星系盘=14）",
                    8, 256, 12, Palette.LINE);

            buffer.art().flush();

            // 上屏：与游戏同管线（Nearest 整数倍缩放、黑底 letterbox）
            int w = Gdx.graphics.getWidth();
            int h = Gdx.graphics.getHeight();
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
            Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);
            float scale = Math.min(w / 640f, h / 360f);
            float cw = 640 * scale;
            float ch = 360 * scale;
            float ox = (w - cw) / 2f;
            float oy = h - (h - ch) / 2f - ch;
            batch.begin();
            batch.enableBlending();
            batch.draw(texture, ox, oy, cw, ch);
            batch.end();
        }

        @Override
        public void dispose() {
            texture.dispose();
            pixmap.dispose();
            batch.dispose();
        }

        /** 与 PixelGameView 同款：ARGB int[] → RGBA8888 → Pixmap → 纹理。 */
        private final class FrameSink implements PixelArt.Sink {

            @Override
            public void upload(int[] argb, int width, int height) {
                ByteBuffer bytes = rgbaBytes;
                bytes.clear();
                for (int p : argb) {
                    bytes.put((byte) ((p >> 16) & 0xFF));
                    bytes.put((byte) ((p >> 8) & 0xFF));
                    bytes.put((byte) (p & 0xFF));
                    bytes.put((byte) ((p >> 24) & 0xFF));
                }
                bytes.flip();
                pixmap.getPixels().clear();
                pixmap.getPixels().put(bytes);
                pixmap.getPixels().flip();
                texture.draw(pixmap, 0, 0);
            }

            @Override
            public void uploadBlock(int x, int y, int width, int height, int[] block) {
                upload(buffer.art().buffer(), buffer.art().width(), buffer.art().height());
            }
        }
    }
}
