import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.core.PixelArtFilter;
import com.hysx.salvosequence.live.nebula.NebulaLive;
import com.hysx.salvosequence.tools.PngWriter;

import java.io.File;

/** 三组参数并排，挑一组默认值。 */
public final class TuneGrid {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setWindowedMode(320, 180);
        cfg.setInitialVisible(false);
        cfg.useVsync(false);
        cfg.setForegroundFPS(0);
        new Lwjgl3Application(new ApplicationAdapter() {
            private NebulaLive nebula;
            private int step;

            @Override
            public void render() {
                try {
                    if (step == 0) {
                        nebula = new NebulaLive();
                    }
                    if (step == 1) {
                        int[] img = nebula.render(6.0);
                        PixelArtFilter.setEnabled(true);
                        PixelArtFilter.Params[] sets = {
                                new PixelArtFilter.Params(8, 0, 0.35f, 0.5f, 0.22f, 0.5f, 1f),
                                new PixelArtFilter.Params(8, 0, 0.25f, 0.75f, 0.22f, 0.3f, 1f),
                                new PixelArtFilter.Params(6, 0, 0.25f, 0.85f, 0.26f, 0.3f, 1f),
                        };
                        for (int i = 0; i < sets.length; i++) {
                            PixelArtFilter.setParams(sets[i]);
                            write("tune-" + i, PixelArtFilter.apply(img, 320, 180));
                        }
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

    private static void write(String name, int[] img) throws Exception {
        PixelArt art = new PixelArt(640, 360);
        for (int y = 0; y < 360; y++) {
            for (int x = 0; x < 640; x++) {
                art.buffer()[y * 640 + x] = img[(y / 2) * 320 + (x / 2)];
            }
        }
        PngWriter.write(art.buffer(), 640, 360, new File(".tmp/shots/" + name + ".png"));
    }
}
