import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hysx.salvosequence.run.Run;
import com.hysx.salvosequence.screen.CardLoopScreen;
import com.hysx.salvosequence.screen.ScreenContext;
import com.hysx.salvosequence.ui.PixelGameView;
import com.hysx.salvosequence.ui.ScreenManager;

import java.lang.reflect.Field;

/** 临时：跳到指定阶段截图（-Dshot.stage=LULL|SPACE_PORT|BATTLE）。 */
public final class StageShot {

    private boolean advanced;

    public void start() {
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setTitle("stage shot");
        c.setWindowedMode(1280, 720);
        PixelGameView view = new PixelGameView();
        view.frameListener = (frame, v) -> {
            try {
                if (frame == 1) {
                    v.go(ScreenContext.Target.CARD);
                    return;
                }
                ScreenManager sm = (ScreenManager) field(v, "screens");
                if (!(sm.current() instanceof CardLoopScreen s)) {
                    return;
                }
                if (!advanced) {
                    advanced = true;
                    String st = System.getProperty("shot.stage", "LULL");
                    s.debugAdvanceTo(Run.Stage.valueOf(st));
                    System.out.println("[shot] frame=" + frame + " -> " + st);
                }
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        };
        new Lwjgl3Application(view, c);
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    public static void main(String[] args) {
        new StageShot().start();
    }
}
