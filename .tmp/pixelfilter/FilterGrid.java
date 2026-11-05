import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.core.PixelArtFilter;
import com.hysx.salvosequence.live.nebula.NebulaLive;
import com.hysx.salvosequence.live.star.StarLive;
import com.hysx.salvosequence.tools.PngWriter;

import java.io.File;

/** 生成四种模式（原色/暖/冷/灰）的对比图。 */
public final class FilterGrid {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setWindowedMode(320, 180);
        cfg.setInitialVisible(false);
        cfg.useVsync(false);
        cfg.setForegroundFPS(0);
        new Lwjgl3Application(new ApplicationAdapter() {
            private NebulaLive nebula;
            private StarLive star;
            private int step;

            @Override
            public void render() {
                try {
                    if (step == 0) {
                        nebula = new NebulaLive();
                        star = new StarLive();
                    }
                    if (step == 1) {
                        run("nebula", nebula.render(6.0));
                        run("star", star.render(4.0));
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

    private static void run(String name, int[] img) throws Exception {
        write(name + "-m0", img, false);
        PixelArtFilter.setEnabled(true);
        for (int m = 0; m < 4; m++) {
            if (PixelArtFilter.mode() != m) {
                while (PixelArtFilter.mode() != m) {
                    PixelArtFilter.cycleMode(1);
                }
            }
            write(name + "-m" + m, PixelArtFilter.apply(img, 320, 180), true);
        }
    }

    private static void write(String name, int[] img, boolean unused) throws Exception {
        PixelArt art = new PixelArt(640, 360);
        for (int y = 0; y < 360; y++) {
            for (int x = 0; x < 640; x++) {
                art.buffer()[y * 640 + x] = img[(y / 2) * 320 + (x / 2)];
            }
        }
        PngWriter.write(art.buffer(), 640, 360, new File(".tmp/shots/" + name + ".png"));
    }
}
