import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.screen.BackdropLabScreen;
import com.hysx.salvosequence.tools.PngWriter;

import java.io.File;

/** 背景工坊里按 L 切到实时背景，各出一张图看观感。 */
public final class LabLive {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setWindowedMode(640, 360);
        cfg.setInitialVisible(false);
        cfg.useVsync(false);
        cfg.setForegroundFPS(0);
        new Lwjgl3Application(new ApplicationAdapter() {
            private BackdropLabScreen screen;
            private CanvasBuffer buffer;
            private int frame;
            private int picks;

            @Override
            public void create() {
                screen = new BackdropLabScreen();
                buffer = new CanvasBuffer(640, 360);
            }

            @Override
            public void render() {
                if (frame == 0) {
                    screen.onKey("L");   // 从"关"切到第 1 个实时背景
                }
                if (frame == 20) {
                    try {
                        screen.draw(buffer);
                        PngWriter.write(buffer.art().buffer(), 640, 360,
                                new File(".tmp/shots/lab-live1.png"));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    screen.onKey("L");
                }
                if (frame == 40) {
                    try {
                        screen.draw(buffer);
                        PngWriter.write(buffer.art().buffer(), 640, 360,
                                new File(".tmp/shots/lab-live2.png"));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    Gdx.app.exit();
                }
                screen.update(1 / 30.0);
                frame++;
            }
        }, cfg);
    }
}
