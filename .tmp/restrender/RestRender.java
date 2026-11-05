import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.run.Run;
import com.hysx.salvosequence.screen.CardLoopScreen;
import com.hysx.salvosequence.tools.PngWriter;

import java.io.File;
import java.lang.reflect.Field;

/** 把 Run 强行推到战术休整阶段，画一帧出来看排版。 */
public final class RestRender {
    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 20261105L;
        String out = args.length > 1 ? args[1] : ".tmp/shots/rest.png";
        CardLoopScreen screen = new CardLoopScreen(new Run(seed));
        screen.onKey("DIGIT1");
        screen.update(0.016);
        screen.run().pickCaptain(0);
        screen.update(0.016);
        Run run = screen.run();

        Field stage = Run.class.getDeclaredField("stage");
        stage.setAccessible(true);
        stage.set(run, Run.Stage.LULL);
        Field wave = Run.class.getDeclaredField("wave");
        wave.setAccessible(true);
        wave.setInt(run, 1);

        // 禁掉一张牌，看看禁用态长什么样
        if (run.fleet() != null && !run.fleet().deck().isEmpty()) {
            run.toggleBannedCard(run.fleet().deck().get(0).id());
        }

        if (args.length > 2 && args[2].equals("deck")) {
            Field tab = CardLoopScreen.class.getDeclaredField("restTab");
            tab.setAccessible(true);
            tab.setInt(screen, 1);
        }
        screen.update(0.016);
        CanvasBuffer buffer = new CanvasBuffer(640, 360);
        screen.draw(buffer);
        PngWriter.write(buffer.art().buffer(), buffer.width(), buffer.height(), new File(out));
        System.out.println("wrote " + out + " stage=" + run.stage()
                + " wave=" + run.wave() + "/" + run.waveCount()
                + " ships=" + run.restShips().size() + " banned=" + run.bannedCards());
    }
}
