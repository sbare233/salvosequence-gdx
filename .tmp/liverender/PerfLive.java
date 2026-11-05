import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hysx.salvosequence.live.LiveBackdrops;

/** 量每个实时背景一帧的耗时（含 GL 读回）。 */
public final class PerfLive {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setWindowedMode(320, 180);
        cfg.setInitialVisible(false);
        cfg.useVsync(false);
        cfg.setForegroundFPS(0);
        new Lwjgl3Application(new ApplicationAdapter() {
            private int index;
            private int n;
            private long t0;

            @Override
            public void render() {
                if (index >= LiveBackdrops.count()) {
                    Gdx.app.exit();
                    return;
                }
                if (n == 0) {
                    LiveBackdrops.frame(index, 0.0);   // 预热：建 GL 资源
                    t0 = System.nanoTime();
                }
                LiveBackdrops.frame(index, 10.0 + n * 0.07);
                n++;
                if (n >= 30) {
                    double ms = (System.nanoTime() - t0) / 1e6 / 30;
                    System.out.printf("[perf] %s  %.2f ms/frame%n", LiveBackdrops.id(index), ms);
                    index++;
                    n = 0;
                }
            }
        }, cfg);
    }
}
