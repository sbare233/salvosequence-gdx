import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.live.LiveBackdrops;
import com.hysx.salvosequence.screen.BackdropLabScreen;
import com.hysx.salvosequence.tools.PngWriter;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;

/** 背景工坊的实时参数面板：出图 + 验证相机不再自己转。 */
public final class LabLive2 {
    public static void main(String[] args) throws Exception {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("lab");
        cfg.setWindowedMode(640, 360);
        cfg.setInitialVisible(false);
        cfg.useVsync(false);
        cfg.setForegroundFPS(0);
        new Lwjgl3Application(new ApplicationAdapter() {
            private final BackdropLabScreen lab = new BackdropLabScreen();
            private CanvasBuffer buffer;
            private int frame;

            @Override
            public void create() {
                buffer = new CanvasBuffer(640, 360);
                try {
                    Field live = BackdropLabScreen.class.getDeclaredField("liveIndex");
                    live.setAccessible(true);
                    live.setInt(lab, 0);
                    Field page = BackdropLabScreen.class.getDeclaredField("livePage");
                    page.setAccessible(true);
                    page.setInt(lab, 1);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void render() {
                if (frame == 0) {
                    System.out.println("cam t0 " + Arrays.toString(LiveBackdrops.camera(0)));
                    lab.update(0.0);
                    lab.draw(buffer);
                } else if (frame == 1) {
                    // 让时间跑 6 秒、模拟拖拽
                    for (int i = 0; i < 60; i++) {
                        lab.update(0.1);
                    }
                    System.out.println("cam t6 " + Arrays.toString(LiveBackdrops.camera(0)));
                    LiveBackdrops.orbit(0, 24, 6);
                    for (int i = 0; i < 10; i++) {
                        lab.update(0.1);   // 触发"停手 0.4 秒后存档"
                    }
                    lab.draw(buffer);
                } else if (frame == 2) {
                    System.out.println("cam orbited " + Arrays.toString(LiveBackdrops.camera(0)));
                    for (var t : buffer.texts()) {
                        if (t.content().contains("实时背景") || t.content().contains("星球模式")) {
                            System.out.println("text=" + t.content() + " @" + t.x() + "," + t.baselineY());
                        }
                    }
                    try {
                        PngWriter.write(buffer.art().buffer(), 640, 360,
                                new File(".tmp/shots/lab-live.png"));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    System.out.println("params=" + LiveBackdrops.params(0).getClass().getSimpleName()
                            + " settings=" + com.hysx.salvosequence.live.LiveSettings.file());
                    com.badlogic.gdx.Gdx.app.exit();
                }
                frame++;
            }
        }, cfg);
    }
}
