import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.live.pulsar.PulsarLive;
import com.hysx.salvosequence.tools.PngWriter;

import java.io.File;

/** 离屏渲染实时背景，检查观感。 */
public final class LiveRender {
    public static void main(String[] args) throws Exception {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("live");
        cfg.setWindowedMode(320, 180);
        cfg.setInitialVisible(false);
        cfg.useVsync(false);
        cfg.setForegroundFPS(0);
        new Lwjgl3Application(new ApplicationAdapter() {
            private final PulsarLive live = new PulsarLive();
            private int frame;
            private final double[] times = {3.0, 11.0, 25.0};

            @Override
            public void render() {
                float t = (float) times[Math.min(frame, times.length - 1)];
                int[] img = live.render(t);
                PixelArt art = new PixelArt(640, 360);
                for (int y = 0; y < 360; y++) {
                    for (int x = 0; x < 640; x++) {
                        art.buffer()[y * 640 + x] = img[(y / 2) * 320 + (x / 2)];
                    }
                }
                try {
                    PngWriter.write(art.buffer(), 640, 360, new File(".tmp/shots/pulsar-" + frame + ".png"));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                frame++;
                if (frame >= times.length) {
                    live.dispose();
                    Gdx.app.exit();
                }
            }
        }, cfg);
    }
}
