import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.live.LiveBackdrops;
import com.hysx.salvosequence.tools.PngWriter;
import com.hysx.salvosequence.ui.VideoSettings;

import java.io.File;

/** 同一帧实时背景，分别用拜耳 / 像素滤镜输出，看两种"像素化"的差别。 */
public final class FilterArena {
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
                    if (step == 2) {
                        run();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (++step > 4) {
                    System.exit(0);
                }
            }
        }, cfg);
    }

    private static void run() throws Exception {
        for (int scene = 0; scene < LiveBackdrops.count(); scene++) {
            for (VideoSettings.BackdropFilter which : VideoSettings.BackdropFilter.values()) {
                VideoSettings.setBackdropFilter(which);
                int[] frame = null;
                // 换个时刻逼缓存重算
                frame = LiveBackdrops.frame(scene, 2.0 + scene * 0.25);
                if (frame == null) {
                    System.out.println("null frame " + LiveBackdrops.id(scene));
                    continue;
                }
                save(LiveBackdrops.id(scene) + "-" + which.labelEn().toLowerCase(), frame);
            }
        }
    }

    private static void save(String name, int[] src) throws Exception {
        PixelArt art = new PixelArt(W, H);
        int[] buf = art.buffer();
        for (int y = 0; y < H; y++) {
            int sy = y * 180 / H;
            for (int x = 0; x < W; x++) {
                int sx = x * 320 / W;
                int argb = src[sy * 320 + sx];
                int a = (argb >>> 24) & 255;
                int r = ((argb >>> 16) & 255) * a / 255;
                int g = ((argb >>> 8) & 255) * a / 255;
                int b = (argb & 255) * a / 255;
                buf[y * W + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        PngWriter.write(buf, W, H, new File(".tmp/shots/arena-" + name + ".png"));
        System.out.println("wrote arena-" + name);
    }
}
