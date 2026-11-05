package com.hysx.salvosequence.tools;

import com.hysx.salvosequence.audio.Music;
import com.hysx.salvosequence.audio.Sound;
import com.hysx.salvosequence.fleet.ShipArtStore;
import com.hysx.salvosequence.run.Run;
import com.hysx.salvosequence.screen.CardLoopScreen;
import com.hysx.salvosequence.screen.ScreenContext;
import com.hysx.salvosequence.ui.PixelGameView;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 「组舰者」玩法循环界面的核对工具：把循环各阶段 + 舰队相关界面各导一张设计分辨率稿件。
 *
 * <p>跑法：{@code mvn compile exec:java -Dexec.mainClass=com.hysx.salvosequence.tools.CardLoopAudit}，
 * 输出 {@code target/audit-card-*.png}。</p>
 *
 * <p>为什么单独一个工具而不是塞进 {@link CanvasAudit}：这一屏要核对的不是"画得出来"，
 * 而是"<b>循环走得通</b>"——选舰队 → 出牌 → 结束回合 → 捡战利品 → 编入新船 → 挑落点，
 * 每一步都要留一份画面证据，所以这里用一串按键脚本把整局驱动一遍、逐阶段导出。
 * 种子固定，同样的代码永远导出同样的图，方便前后对比。</p>
 *
 * <p>驱动方式说明：所有状态变化都是 {@code handleKey} 的同步副作用，所以只要每次按键之间
 * 留几帧给渲染（libGDX 版用逐帧回调排 10 帧 ≈ 160ms），画面就一定会跟上。</p>
 */
public final class CardLoopAudit {

    /** 固定种子：核对稿件必须可复现。 */
    private static final long SEED = 20240601L;

    /** 每次按键之间留的渲染帧数（60fps 下 ≈ 160ms）。 */
    private static final int STEP_FRAMES = 10;

    private final Deque<Runnable> steps = new ArrayDeque<>();
    private PixelGameView view;
    private File outDir;
    /** 步进计数：每 STEP_FRAMES 帧执行下一步（libGDX 逐帧回调替代 PauseTransition）。 */
    private long stepClock;
    private boolean started;

    public void start() {
        outDir = new File(System.getProperty("card.outdir", "target"));
        // 用临时目录当舰队档案：核对稿件必须只反映出厂设计，
        // 否则玩家本机的 ships/*.fleet.txt（可能是上一版摆位留下的）会混进来。
        try {
            ShipArtStore.useDirectory(java.nio.file.Files.createTempDirectory("fleet-audit"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("建临时舰队目录失败", e);
        }
        // 固定种子：CardLoopScreen 默认构造器会读这个属性
        System.setProperty("card.seed", Long.toString(SEED));

        view = new PixelGameView();
        view.screens().setScanlineEnabled(false);
        view.screens().setDebugVisible(false);
        view.frameListener = (frame, v) -> {
            if (!started) {
                started = true;
                runNext();
                return;
            }
            stepClock++;
            if (stepClock % STEP_FRAMES == 0) {
                runNext();
            }
        };

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("齐射序列 - 组舰者循环核对");
        config.setWindowedMode(1600, 900);
        config.setInitialVisible(false);
        new Lwjgl3Application(view, config);
    }

    private void buildScript() {
        // ---- 核对脚本：每一步都在渲染线程上串行执行 ----
        step(() -> {
            view.go(ScreenContext.Target.CARD);
            view.screens().finishTransition();
        });
        step(() -> export("audit-card-1-loadout.png", "loadout"));

        // 选第 2 支舰队（齐射舰队），用机器人快进到第 2 关并打两回合
        step(() -> press("2"));
        step(() -> {
            CardLoopScreen screen = cardScreen();
            if (screen != null) {
                screen.debugFastForward(1);
            }
        });
        // 手牌悬浮：右栏描述跟着指针走
        step(() -> {
            CardLoopScreen s = cardScreen();
            if (s != null) {
                s.debugHoverCard(0);
            }
        });
        step(() -> export("audit-card-2-battle.png", "battle"));
        step(() -> {
            CardLoopScreen s = cardScreen();
            if (s != null) {
                s.debugHoverCard(-1);
            }
        });

        step(this::driveToReward);
        step(() -> export("audit-card-3-reward.png", "reward"));

        // 战利品里挑一艘船编入 → 落点阶段（我方棋盘上画候选框）
        step(this::enlistShip);
        step(() -> export("audit-card-4-slot.png", "slot"));

        // 第 5 张：多敌编队（重启后快进到第 5 关：旗舰 + 两艘僚舰）
        step(() -> press("R"));
        step(() -> press("1"));
        step(() -> {
            CardLoopScreen screen = cardScreen();
            if (screen != null) {
                screen.debugFastForward(4);
            }
        });
        step(() -> export("audit-card-5-squad.png", "squad"));

        // 第 6-7 张：战舰设计器（按船画外观 + 放大镜）
        step(() -> view.go(ScreenContext.Target.DESIGNER));
        step(() -> view.screens().finishTransition());
        step(() -> exportDesigner("audit-card-6-designer.png", "designer"));
        step(() -> {
            if (view.screens().current() instanceof com.hysx.salvosequence.screen.ShipDesignerScreen screen) {
                screen.debugHoverAt(16 + 120, 30 + 90);
            }
        });
        step(() -> exportDesigner("audit-card-7-magnifier.png", "magnify"));
        step(() -> {
            if (view.screens().current() instanceof com.hysx.salvosequence.screen.ShipDesignerScreen screen) {
                screen.debugHoverAt(-1, -1);
            }
        });

        // 第 8 张：舰队编排（拖动船只排阵型）
        step(() -> view.go(ScreenContext.Target.ARRANGE));
        step(() -> view.screens().finishTransition());
        step(() -> exportDesigner("audit-card-8-arrange.png", "arrange"));

        // 第 9 张：轴炮舰队（管口走廊 + 倍率读数）
        step(() -> view.go(ScreenContext.Target.CARD));
        step(() -> view.screens().finishTransition());
        step(() -> press("4"));
        step(() -> export("audit-card-9-axial.png", "axial"));

        // 第 10 张：战斗里按 Z 的"放大看船"
        step(() -> view.go(ScreenContext.Target.CARD));
        step(() -> view.screens().finishTransition());
        step(() -> press("1"));
        step(() -> {
            CardLoopScreen screen = cardScreen();
            if (screen != null) {
                screen.debugFastForward(1);
                screen.debugZoomShip(true);
            }
        });
        step(() -> export("audit-card-10-zoom.png", "zoom"));
        step(() -> {
            CardLoopScreen screen = cardScreen();
            if (screen != null) {
                screen.debugZoomShip(false);
            }
        });

        // 第 11 张：左右两块 16×16（左 = 我方舰队，右 = 敌方舰队，同一套画法）+ 底部方形手牌
        step(() -> view.go(ScreenContext.Target.CARD));
        step(() -> view.screens().finishTransition());
        step(() -> press("1"));
        step(() -> {
            CardLoopScreen screen = cardScreen();
            if (screen != null) {
                screen.debugFastForward(3);
                screen.debugHoverCard(1);
            }
        });
        step(() -> export("audit-card-11-board.png", "board"));

        step(() -> {
            System.out.println("[card-audit] 11 sheets -> " + outDir.getAbsolutePath());
            quit();
        });
    }

    // ------------------------------------------------------------------ 脚本

    /**
     * 用 SMART 机器人推进到战利品阶段。
     *
     * <p>早先这里是"反复按 1 出牌 + 空格"，但牌越加越多、敌人越加厚之后，
     * 这种按键脚本会被打沉（稿件落到 LOST 阶段）。机器人推进是确定的：
     * 同样的种子永远到同一个阶段，所以核对稿件还可以前后对比。</p>
     */
    private void driveToReward() {
        CardLoopScreen screen = cardScreen();
        if (screen == null) {
            return;
        }
        screen.debugAdvanceTo(Run.Stage.REWARD);
        System.out.println("[card-audit] drive to reward, stage = " + screen.run().stage()
                + " node=" + (screen.run().node() + 1));
    }

    private void enlistShip() {
        CardLoopScreen screen = cardScreen();
        if (screen == null) {
            return;
        }
        boolean reached = screen.debugEnlist();
        System.out.println("[card-audit] enlist -> " + reached + ", stage = " + screen.run().stage());
    }

    // ------------------------------------------------------------------ 工具

    private CardLoopScreen cardScreen() {
        return view.screens().current() instanceof CardLoopScreen screen ? screen : null;
    }

    private void press(String code) {
        // 界面收的是 JavaFX 的 KeyCode.name()，数字键要写成 DIGIT1..DIGIT9
        String normalized = code.length() == 1 && Character.isDigit(code.charAt(0)) ? "DIGIT" + code : code;
        view.screens().handleKey(normalized);
        view.screens().finishTransition();
        clearToast();
    }

    /**
     * 抹掉提示条。
     *
     * <p>提示条活 2.6 秒，而核对脚本每步只隔 160ms——不管的话「出发：炮术舰队」会一路
     * 挂到后面好几张稿件上（快照是静止的，它不会自己消失）。</p>
     */
    private void clearToast() {
        CardLoopScreen screen = cardScreen();
        if (screen != null) {
            screen.debugClearToast();
        }
    }

    /** 设计器/编排界面的稿件：它们不在 Run 里，单独取窗口画面。 */
    private void exportDesigner(String fileName, String label) {
        String state = view.screens().currentName();
        File file = new File(outDir, fileName);
        try {
            PngWriter.write(view.buffer().art().buffer(),
                    view.buffer().width(), view.buffer().height(), file);
            System.out.printf("[card-audit] %-6s %-14s 640x360 -> %s%n", label, state, file.getAbsolutePath());
        } catch (RuntimeException | java.io.IOException error) {
            System.out.println("[card-audit] 导出失败 " + fileName + ": " + error.getMessage());
        }
    }

    private void export(String fileName, String label) {
        CardLoopScreen screen = cardScreen();
        String state = screen == null ? "?" : screen.run().stage() + " node=" + (screen.run().node() + 1);
        try {
            File file = new File(outDir, fileName);
            PngWriter.write(view.buffer().art().buffer(),
                    view.buffer().width(), view.buffer().height(), file);
            System.out.println(String.format("[card-audit] %-6s %-14s %dx%d -> %s",
                    label, state, view.buffer().width(), view.buffer().height(), file.getAbsolutePath()));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void step(Runnable action) {
        // 每步收尾都抹掉提示条：导出取的是「上一帧已经渲染好的画面」，
        // 所以提示条必须在上一步结束前就清掉，不能等到导出那一步再清。
        steps.add(() -> {
            try {
                action.run();
            } finally {
                clearToast();
            }
        });
    }

    private void runNext() {
        Runnable next = steps.poll();
        if (next == null) {
            return;
        }
        next.run();
    }

    private static void quit() {
        try {
            Music.shutdown();
        } catch (Throwable ignored) {
            // 退出路径上不再抛
        }
        try {
            Sound.shutdown();
        } catch (Throwable ignored) {
            // 同上
        }
        System.exit(0);
    }

    public static void main(String[] args) {
        new CardLoopAudit().run();
    }

    /** 组装脚本 + 启动 libGDX 应用（main 与测试共用的入口）。 */
    private void run() {
        outDir = new File(System.getProperty("card.outdir", "target"));
        try {
            ShipArtStore.useDirectory(java.nio.file.Files.createTempDirectory("fleet-audit"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("建临时舰队目录失败", e);
        }
        System.setProperty("card.seed", Long.toString(SEED));
        buildScript();
        start();
    }
}
