import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.run.Run;
import com.hysx.salvosequence.screen.CardLoopScreen;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public final class MapRotate {
    public static void main(String[] args) throws Exception {
        CardLoopScreen screen = new CardLoopScreen(new Run(20261104L));
        screen.onKey("DIGIT1");
        screen.update(0.016);
        screen.run().pickCaptain(0);
        screen.update(0.016);
        CanvasBuffer buffer = new CanvasBuffer(640, 360);

        Method begin = CardLoopScreen.class.getDeclaredMethod("beginStarPan", double.class, double.class);
        Method drag = CardLoopScreen.class.getDeclaredMethod("dragStarMap", double.class, double.class);
        begin.setAccessible(true);
        drag.setAccessible(true);
        Field mapField = CardLoopScreen.class.getDeclaredField("nodeFrameColors");
        mapField.setAccessible(true);

        screen.draw(buffer);
        Map<?, ?> colors = (Map<?, ?>) mapField.get(screen);
        System.out.println("after first draw: " + colors.size() + " " + colors);

        begin.invoke(screen, 320.0, 180.0);
        drag.invoke(screen, 720.0, 180.0);
        screen.update(0.016);
        screen.draw(buffer);
        Map<?, ?> after = (Map<?, ?>) mapField.get(screen);
        System.out.println("after rotating 200deg: " + after.size() + " same=" + after.equals(colors));
        com.hysx.salvosequence.tools.PngWriter.write(buffer.art().buffer(), 640, 360, new java.io.File(".tmp/shots/starmap-rot.png"));
        System.out.println(after);
    }
}
