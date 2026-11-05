import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.tools.PngWriter;
import com.hysx.salvosequence.run.Run;
import com.hysx.salvosequence.screen.CardLoopScreen;
import java.io.File;

public final class MapRender {
    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 20261104L;
        String out = args.length > 1 ? args[1] : ".tmp/shots/starmap.png";
        CardLoopScreen screen = new CardLoopScreen(new Run(seed));
        screen.onKey("DIGIT1");
        screen.update(0.016);
        screen.run().pickCaptain(0);
        screen.update(0.016);
        CanvasBuffer buffer = new CanvasBuffer(640, 360);
        screen.draw(buffer);
        PngWriter.write(buffer.art().buffer(), buffer.width(), buffer.height(), new File(out));
        System.out.println("wrote " + out + " stage=" + screen.run().stage());
    }
}
