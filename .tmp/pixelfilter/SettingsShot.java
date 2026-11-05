import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.run.Run;
import com.hysx.salvosequence.screen.CardLoopScreen;
import com.hysx.salvosequence.tools.PngWriter;

import java.io.File;

/** 把「设置」面板画进离屏缓冲，检查 12 行在 340 高的面板里排得下。 */
public final class SettingsShot {
    public static void main(String[] args) throws Exception {
        CardLoopScreen screen = new CardLoopScreen(new Run(20240601L));
        screen.onKey("DIGIT1");
        screen.run().pickCaptain(0);
        screen.update(0.016);
        int guard = 0;
        while (screen.run().stage() == Run.Stage.MAP && guard++ < 40) {
            var reach = screen.run().reachableStars();
            var pick = reach.stream()
                    .filter(st -> st.type() == Run.NodeType.BATTLE || st.type() == Run.NodeType.ELITE)
                    .findFirst();
            if (pick.isEmpty()) {
                pick = reach.stream().findFirst();
            }
            if (pick.isEmpty()) {
                break;
            }
            screen.run().travelTo(pick.get().id());
        }
        screen.update(0.016);
        screen.onKey("ESCAPE");
        for (int i = 0; i < 8; i++) {
            screen.onKey("DOWN");
        }
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
        PngWriter.write(big, w, h, new File(".tmp/shots/settings-panel-2x.png"));
        System.out.println("stage=" + screen.run().stage() + " ok");
    }
}
