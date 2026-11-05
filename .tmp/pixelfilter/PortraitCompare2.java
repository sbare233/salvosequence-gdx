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

/** 人物立绘：原样 / 拜耳 / 新像素滤镜（原色·冷色）对比。 */
public final class PortraitCompare2 {
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
                step++;
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
                int a = (src >>> 24) & 0xFF;
                if (a < 8) {
                    continue;
                }
                int dst = frame[(oy + y) * W + ox + x];
                int r = (((src >>> 16) & 0xFF) * a + ((dst >>> 16) & 0xFF) * (255 - a)) / 255;
                int g = (((src >>> 8) & 0xFF) * a + ((dst >>> 8) & 0xFF) * (255 - a)) / 255;
                int b = ((src & 0xFF) * a + (dst & 0xFF) * (255 - a)) / 255;
                frame[(oy + y) * W + ox + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        write("p2-raw", frame);
        write("p2-bayer-dither", BayerPixelator.pixelate(frame, W, H, BayerPixelator.Params.DEFAULT));
        write("p2-bayer-clean", BayerPixelator.pixelate(frame, W, H,
                new BayerPixelator.Params(2, 6, 0, 0, 0, 100, false)));

        PixelArtFilter.setEnabled(true);
        PixelArtFilter.cycleMode(-PixelArtFilter.mode());   // 回到 0
        PixelArtFilter.setParams(new PixelArtFilter.Params(8, 0, 0.25f, 0.75f, 0.22f, 0f, 1f));
        write("p2-pixel-nodither", PixelArtFilter.apply(frame, W, H));

        PixelArtFilter.setParams(new PixelArtFilter.Params(8, 0, 0.25f, 0.75f, 0.22f, 0.35f, 1f));
        write("p2-pixel-default", PixelArtFilter.apply(frame, W, H));

        PixelArtFilter.cycleMode(2);   // 冷色
        write("p2-pixel-cold", PixelArtFilter.apply(frame, W, H));
        System.out.println("portrait " + pw + "x" + ph + " mode=" + PixelArtFilter.mode() + " " + PixelArtFilter.modeName());
        System.exit(0);
    }

    private static void write(String name, int[] img) throws Exception {
        PixelArt art = new PixelArt(W, H);
        System.arraycopy(img, 0, art.buffer(), 0, W * H);
        PngWriter.write(art.buffer(), W, H, new File(".tmp/shots/" + name + ".png"));
    }
}
