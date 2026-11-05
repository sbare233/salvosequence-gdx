import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.run.Run;
import com.hysx.salvosequence.screen.CardLoopScreen;
import com.hysx.salvosequence.tools.PngWriter;

import java.io.File;

/** 把 Run 推到战斗阶段，画一帧（含实时背景），看战斗底图长什么样。 */
public final class BattleRender {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("battle");
        cfg.setWindowedMode(640, 360);
        cfg.setInitialVisible(false);
        cfg.useVsync(false);
        cfg.setForegroundFPS(0);
        new Lwjgl3Application(new ApplicationAdapter() {
            private CardLoopScreen screen;
            private CanvasBuffer buffer;
            private int frame;

            @Override
            public void create() {
                try {
                    Run run = new Run(20261105L);
                    screen = new CardLoopScreen(run);
                    screen.onKey("DIGIT1");
                    screen.update(0.016);
                    run.pickCaptain(0);
                    var reach = run.reachableStars();
                    reach.stream().filter(st -> st.type() == Run.NodeType.BATTLE
                            || st.type() == Run.NodeType.ELITE).findFirst()
                            .or(() -> reach.stream().findFirst())
                            .ifPresent(st -> run.travelTo(st.id()));
                    screen.update(0.016);
                    buffer = new CanvasBuffer(640, 360);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void render() {
                if (System.getProperty("live.select") != null) {
                    com.hysx.salvosequence.live.LiveBackdrops.setSelected(
                            Integer.parseInt(System.getProperty("live.select")));
                }
                Run run = screen.run();
                for (int guard = 0; guard < 6 && run.stage() != Run.Stage.BATTLE; guard++) {
                    switch (run.stage()) {
                        case EVENT -> run.chooseEventOption(0);
                        case MAP -> run.reachableStars().stream()
                                .filter(st -> st.type() == Run.NodeType.BATTLE
                                        || st.type() == Run.NodeType.ELITE)
                                .findFirst().ifPresent(st -> run.travelTo(st.id()));
                        case LULL -> run.startNextWave();
                        default -> {
                        }
                    }
                }
                screen.update(1.0 / 60.0);
                screen.draw(buffer);
                if (frame >= 40) {
                    try {
                        PngWriter.write(buffer.art().buffer(), 640, 360,
                                new File(".tmp/shots/battle-live" + System.getProperty("live.select", "auto") + ".png"));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    Gdx.app.exit();
                }
                frame++;
            }
        }, cfg);
    }
}
