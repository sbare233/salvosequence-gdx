import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hysx.salvosequence.screen.CardLoopScreen;
import com.hysx.salvosequence.screen.ScreenContext;
import com.hysx.salvosequence.run.Run;
import com.hysx.salvosequence.ui.PixelGameView;
import com.hysx.salvosequence.ui.ScreenManager;

import java.lang.reflect.Field;

/** 临时测速：进星图、模拟快速左右拖动，看冒烟 FPS。 */
public final class MapPerf {

    private boolean started;
    private long lastNanos;
    private double sum;
    private int n;
    private int frames;

    public void start() {
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setTitle("map perf");
        c.setWindowedMode(1280, 720);
        if (Boolean.getBoolean("perf.novsync")) { c.useVsync(false); c.setForegroundFPS(0); }
        PixelGameView view = new PixelGameView();
        view.frameListener = (frame, v) -> {
            long now = System.nanoTime();
            if (lastNanos != 0 && frames > 120) {
                sum += (now - lastNanos) / 1e6;
                n++;
            }
            lastNanos = now;
            frames++;
            try {
                if (frame == 1) {
                    if (Boolean.getBoolean("perf.start")) { return; }
                    System.out.println("[gl] vendor=" + com.badlogic.gdx.Gdx.gl.glGetString(com.badlogic.gdx.graphics.GL20.GL_VENDOR)
                            + " renderer=" + com.badlogic.gdx.Gdx.gl.glGetString(com.badlogic.gdx.graphics.GL20.GL_RENDERER));
                    v.go(ScreenContext.Target.CARD);
                    return;
                }
                ScreenManager sm = (ScreenManager) field(v, "screens");
                if (!(sm.current() instanceof CardLoopScreen s)) {
                    return;
                }
                if (!started) {
                    started = true;
                    s.debugAdvanceTo(Run.Stage.MAP);
                    System.out.println("[perf] stage=" + s.run().stage());
                    sm.touchDown(320, 180);
                    return;
                }
                if (!Boolean.parseBoolean(System.getProperty("perf.drag","true"))) { return; }
                // 每 10 帧来回扫 300px：模拟快速拖动
                int step = Boolean.getBoolean("perf.everyframe") ? 1 : 10;
                double amp = Double.parseDouble(System.getProperty("perf.px", "300"));
                double x = 320 + (((frame / step) % 2 == 0) ? -amp : amp);
                sm.touchDragged(x, 180);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        };
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                System.out.printf("[steady] %.2f ms/帧  (%.1f FPS, %d 帧)%n",
                        sum / Math.max(1, n), 1000.0 / Math.max(1e-9, sum / Math.max(1, n)), n)));
        new Lwjgl3Application(view, c);
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    public static void main(String[] args) {
        new MapPerf().start();
    }
}
