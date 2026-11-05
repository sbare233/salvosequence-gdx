import com.hysx.salvosequence.screen.StartScreen;
import com.hysx.salvosequence.screen.experiment.PortraitOverlay;

import java.lang.reflect.Field;

public final class F12Check2 {
    public static void main(String[] args) throws Exception {
        StartScreen screen = new StartScreen();
        screen.onEnter();
        System.out.println("consumes F12? " + screen.consumesKey("F12"));
        System.out.println("consumes F1?  " + screen.consumesKey("F1"));
        Field f = StartScreen.class.getDeclaredField("portrait");
        f.setAccessible(true);
        PortraitOverlay p = (PortraitOverlay) f.get(screen);
        System.out.println("visible before: " + p.visible());
        screen.onKey("F12");
        System.out.println("visible after : " + p.visible());
        screen.onKey("F12");
        System.out.println("visible again : " + p.visible());
    }
}
