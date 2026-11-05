import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hysx.salvosequence.core.BayerPixelator;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.core.PixelArtFilter;
import com.hysx.salvosequence.screen.experiment.PortraitOverlay;
import com.hysx.salvosequence.tools.PngWriter;

import java.io.File;
import java.lang.reflect.Field;

/** 人物立绘三种处理对比：原样缩小 / 项目拜耳滤镜 / 新像素滤镜。 */
public final class PortraitFilters {
    private static final int W = 640;
    private static final int H = 360;

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setWindowedMode(320, 180);
        cfg.setInitialVisible(false);
        cfg.useVsync(false);
        cfg.setForegroundFPS(0);
        new Lwjgl3Application(new ApplicationAdapter() {
            private int step;

            @Override
            public void render() {
                try {
                    if (step == 1) {
                        run();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (++step > 2) {
                    System.exit(0);
                }
            }
        }, cfg);
    }

    private static void run() throws Exception {
        PortraitOverlay overlay = new PortraitOverlay();
        Field f = PortraitOverlay.class.getDeclaredField("scaled");
        f.setAccessible(true);
        int[] portrait = (int[]) f.get(overlay);
        int pw = overlay.scaledWidth();
        int ph = overlay.scaledHeight();

        int[] frame = new int[W * H];
        for (int i = 0; i < frame.length; i++) {
            frame[i] = 0xFF080A10;
        }
        int ox = 505 - pw / 2;
        int oy = 338 - ph;
        for (int y = 0; y < ph; y++) {
            for (int x = 0; x < pw; x++) {
                int src = portrait[y * pw + x];
                int alpha = (src >>> 24) & 0xFF;
                if (alpha < 8) {
                    continue;
                }
                int dst = frame[(oy + y) * W + ox + x];
                int r = (((src >>> 16) & 0xFF) * alpha + ((dst >>> 16) & 0xFF) * (255 - alpha)) / 255;
                int g = (((src >>> 8) & 0xFF) * alpha + ((dst >>> 8) & 0xFF) * (255 - alpha)) / 255;
                int b = ((src & 0xFF) * alpha + (dst & 0xFF) * (255 - alpha)) / 255;
                frame[(oy + y) * W + ox + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        write("portrait-raw", frame);

        int[] bayer = BayerPixelator.pixelate(frame, W, H, BayerPixelator.Params.DEFAULT);
        write("portrait-bayer", bayer);
        // 不抖动的拜耳：只做色阶量化 + 2px 块，作对照
        int[] bayer2 = BayerPixelator.pixelate(frame, W, H,
                new BayerPixelator.Params(2, 8, 0, 0, 0, 100, false));
        write("portrait-bayer2", bayer2);
        int[] bayer3 = BayerPixelator.pixelate(frame, W, H,
                new BayerPixelator.Params(1, 8, 0, 0, 0, 100, false));
        write("portrait-bayer3", bayer3);

        PixelArtFilter.setEnabled(true);
        PixelArtFilter.reset();
        write("portrait-pixel", PixelArtFilter.apply(frame, W, H));

        PixelArtFilter.cycleMode(2);   // 冷色
        write("portrait-pixel-cold", PixelArtFilter.apply(frame, W, H));
        System.out.println("portrait " + pw + "x" + ph);
    }

    private static void write(String name, int[] img) throws Exception {
        PixelArt art = new PixelArt(W, H);
        System.arraycopy(img, 0, art.buffer(), 0, W * H);
        PngWriter.write(art.buffer(), W, H, new File(".tmp/shots/" + name + ".png"));
    }
}
