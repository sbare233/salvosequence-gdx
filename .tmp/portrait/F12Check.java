import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.screen.StartScreen;

public final class F12Check {
    public static void main(String[] args) throws Exception {
        StartScreen screen = new StartScreen();
        screen.onEnter();
        CanvasBuffer buffer = new CanvasBuffer(640, 360);
        System.out.println("before: " + count(buffer, screen));
        System.out.println("consumed? " + screen.onKey("F12"));
        System.out.println("after : " + count(buffer, screen));
        System.out.println("consumed2? " + screen.onKey("F12"));
        System.out.println("after2: " + count(buffer, screen));
    }

    private static String count(CanvasBuffer buffer, StartScreen screen) {
        screen.draw(buffer);
        long n = buffer.texts().size();
        return "texts=" + n;
    }
}
