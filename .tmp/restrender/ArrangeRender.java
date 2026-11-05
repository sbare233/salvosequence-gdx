import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.fleet.Fleet;
import com.hysx.salvosequence.run.Run;
import com.hysx.salvosequence.screen.CardLoopScreen;
import com.hysx.salvosequence.screen.FleetArrangementScreen;
import com.hysx.salvosequence.tools.PngWriter;

import java.io.File;

/** 离屏渲染：关卡内编排界面（FleetArrangementScreen 绑定当前这局的舰队）。 */
public final class ArrangeRender {
    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 20261105L;
        String out = args.length > 1 ? args[1] : ".tmp/shots/arrange-run.png";
        CardLoopScreen loop = new CardLoopScreen(new Run(seed));
        loop.onKey("DIGIT1");
        loop.update(0.016);
        loop.run().pickCaptain(0);
        loop.update(0.016);
        Fleet fleet = loop.run().fleet();
        if (fleet == null) {
            throw new IllegalStateException("no fleet");
        }
        FleetArrangementScreen screen = new FleetArrangementScreen(fleet);
        screen.moveTo(1, 9, 3);
        screen.moveTo(2, 10, 5);
        screen.update(0.016);

        CanvasBuffer buffer = new CanvasBuffer(640, 360);
        screen.draw(buffer);
        new File(".tmp/shots").mkdirs();
        PngWriter.write(buffer.art().buffer(), buffer.width(), buffer.height(), new File(out));
        System.out.println("wrote " + out + " units=" + screen.fleet().units().size()
                + " forRun=" + screen.forRun() + " selected=" + screen.selectedIndex());
    }
}
