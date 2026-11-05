import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.screen.StartScreen;
import com.hysx.salvosequence.tools.PngWriter;

import java.io.File;

public final class PortraitRender {
    public static void main(String[] args) throws Exception {
        StartScreen screen = new StartScreen();
        screen.onEnter();
        CanvasBuffer buffer = new CanvasBuffer(640, 360);
        screen.draw(buffer);
        PngWriter.write(buffer.art().buffer().clone(), 640, 360, new File(".tmp/shots/portrait.png"));
        System.out.println("wrote .tmp/shots/portrait.png");
    }
}
