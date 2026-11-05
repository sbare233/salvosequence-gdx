import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.screen.StartScreen;
import com.hysx.salvosequence.tools.PngWriter;

import java.io.File;

/** 首页设置面板（7 行）离屏截图。 */
public final class StartSettingsShot {
    public static void main(String[] args) throws Exception {
        StartScreen screen = new StartScreen();
        CanvasBuffer buffer = new CanvasBuffer(640, 360);
        screen.draw(buffer);
        // 菜单项里找到「设置」：DOWN 到底再 ENTER
        for (int i = 0; i < 3; i++) {
            screen.onKey("DOWN");
        }
        screen.onKey("ENTER");
        CanvasBuffer panel = new CanvasBuffer(640, 360);
        screen.draw(panel);
        int[] buf = panel.art().buffer();
        int w = 1280;
        int h = 720;
        int[] big = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                big[y * w + x] = buf[(y / 2) * 640 + (x / 2)];
            }
        }
        PngWriter.write(big, w, h, new File(".tmp/shots/start-settings-2x.png"));
        System.out.println("ok");
    }
}
