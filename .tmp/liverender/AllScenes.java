import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hysx.salvosequence.live.LiveBackdrops;
import com.hysx.salvosequence.tools.PngWriter;

import java.io.File;

/** 把每个实时背景各渲一帧，拼成 2×2 联系表看观感。 */
public final class AllScenes {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("all");
        cfg.setWindowedMode(320, 180);
        cfg.setInitialVisible(false);
        cfg.useVsync(false);
        cfg.setForegroundFPS(0);
        new Lwjgl3Application(new ApplicationAdapter() {
            private int index;
            private int[] sheet = new int[640 * 360];

            @Override
            public void render() {
                if (index >= LiveBackdrops.count()) {
                    try {
                        PngWriter.write(sheet, 640, 360, new File(".tmp/shots/live-all.png"));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    LiveBackdrops.disposeAll();
                    Gdx.app.exit();
                    return;
                }
                int[] img = null;
                for (int k = 0; k < 240 && img == null; k++) {
                    img = LiveBackdrops.frame(index, 11.0 + k * 0.05);
                    if (img == null) {
                        try {
                            Thread.sleep(4);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
                int ox = (index % 2) * 320;
                int oy = (index / 2) * 180;
                for (int y = 0; y < 180; y++) {
                    for (int x = 0; x < 320; x++) {
                        int argb = img == null ? 0xFF200810 : img[y * 320 + x];
                        sheet[(oy + y) * 640 + ox + x] = argb;
                    }
                }
                System.out.println("[all] " + LiveBackdrops.id(index) + " -> "
                        + (img == null ? "null" : "ok"));
                index++;
            }
        }, cfg);
    }
}
