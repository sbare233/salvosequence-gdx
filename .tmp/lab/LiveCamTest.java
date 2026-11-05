import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hysx.salvosequence.core.AwtImageCodec;
import com.hysx.salvosequence.core.ImageCodec;
import com.hysx.salvosequence.live.LiveBackdrops;
import com.hysx.salvosequence.screen.BackdropLabScreen;
import com.hysx.salvosequence.screen.ScreenContext;
import com.hysx.salvosequence.ui.PixelGameView;
import com.hysx.salvosequence.ui.ScreenManager;

import java.lang.reflect.Field;

/** 临时：进背景工坊、切到指定实时场景，拖几下鼠标，看偏航/俯仰有没有动。 */
public final class LiveCamTest {

    private boolean entered;
    private int settle;
    private int step = -1;

    public void start() {
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setTitle("live cam test");
        c.setWindowedMode(1280, 720);
        ImageCodec.setBackend(new AwtImageCodec());
        PixelGameView view = new PixelGameView();
        view.frameListener = (frame, v) -> {
            try {
                if (frame == 1) {
                    v.go(ScreenContext.Target.BACKDROP_LAB);
                    return;
                }
                ScreenManager sm = (ScreenManager) field(v, "screens");
                if (!(sm.current() instanceof BackdropLabScreen lab)) {
                    return;
                }
                String want = System.getProperty("lab.live", "nebula");
                int live = (Integer) field(lab, "liveIndex");
                int wantIndex = -1;
                for (int i = 0; i < LiveBackdrops.count(); i++) {
                    if (LiveBackdrops.id(i).equals(want)) {
                        wantIndex = i;
                    }
                }
                if (live != wantIndex) {
                    if (!entered) {
                        entered = true;
                        System.out.println("[cam] entered lab");
                    }
                    sm.handleKey("L");
                    return;
                }
                if (Boolean.getBoolean("lab.orbitonly")) {
                    if (step < 0) {
                        step = 0;
                        double[] before = LiveBackdrops.camera(live);
                        LiveBackdrops.orbit(live, 0, 40);
                        double[] after = LiveBackdrops.camera(live);
                        System.out.printf("[cam] orbit only: %.1f/%.1f -> %.1f/%.1f%n",
                                before[0], before[1], after[0], after[1]);
                    }
                    return;
                }
                if (Boolean.getBoolean("lab.click")) {
                    if (settle++ < 6) {
                        return;
                    }
                    if (step < 0) {
                        step = 0;
                        double[] c0 = LiveBackdrops.camera(live);
                        System.out.printf("[cam] click before yaw=%.1f pitch=%.1f dist=%.2f%n",
                                c0[0], c0[1], c0[2]);
                        // 参数面板：LIVE_X=468, LIVE_Y=176，行高 14，行从 LIVE_Y+22 开始
                        System.out.println("[cam] down row0+ = " + sm.touchDown(468 + 150, 176 + 22 + 7));
                        System.out.println("[cam] down row1+ = " + sm.touchDown(468 + 150, 176 + 22 + 14 + 7)
                                + " row2- = " + sm.touchDown(468 + 20, 176 + 22 + 28 + 7));
                        double[] c1 = LiveBackdrops.camera(live);
                        System.out.printf("[cam] click after  yaw=%.1f pitch=%.1f dist=%.2f%n",
                                c1[0], c1[1], c1[2]);
                    }
                    return;
                }
                if (!Boolean.getBoolean("lab.drag")) {
                    return;
                }
                if (settle++ < 6) {
                    return;
                }
                if (step < 0) {
                    step = 0;
                    double[] c0 = LiveBackdrops.camera(live);
                    System.out.printf("[cam] before yaw=%.1f pitch=%.1f dist=%.2f  down=%s%n",
                            c0[0], c0[1], c0[2], sm.touchDown(320, 180));
                    return;
                }
                step++;
                sm.touchDragged(320 + step * 6, 180 + step * 3);
                if (step >= 14) {
                    double[] c1 = LiveBackdrops.camera(live);
                    System.out.printf("[cam] after  yaw=%.1f pitch=%.1f dist=%.2f%n",
                            c1[0], c1[1], c1[2]);
                    step = -1000;
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
        new LiveCamTest().start();
    }
}
