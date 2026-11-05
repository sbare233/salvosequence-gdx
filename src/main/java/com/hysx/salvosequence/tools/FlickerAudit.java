package com.hysx.salvosequence.tools;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hysx.salvosequence.fleet.ShipArtStore;
import com.hysx.salvosequence.screen.CardLoopScreen;
import com.hysx.salvosequence.screen.ScreenContext;
import com.hysx.salvosequence.ui.PixelGameView;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 文本闪烁诊断：真实 {@link PixelGameView} + 真实事件循环，战斗中悬停手牌让描述弹窗
 * 常驻，逐帧导出设计分辨率 PNG（target/flicker2/f*.png）。
 *
 * <p>三段脚本：静置（输入完全相同，帧间应当逐位一致）→ 微抖（弹窗 ±2px 扫过相邻文本，
 * 复现弹窗扫过相邻文本的遮挡表现）→ 再静置。跑法：</p>
 * <pre>
 * mvn compile 后：
 * java -cp target/classes com.hysx.salvosequence.tools.FlickerAudit
 * </pre>
 */
public final class FlickerAudit {

    private static final long SEED = 20240601L;
    private static final int SETTLE = 60;
    private static final int JITTER = 200;
    private static final int STILL = 160;
    private static final int TOTAL = SETTLE + JITTER + STILL;

    private PixelGameView view;
    private CardLoopScreen screen;
    private Field mouseX;
    private Field mouseY;
    private Method optionRects;
    private File outDir;

    public void start() {
        outDir = new File("target/flicker2");
        outDir.mkdirs();
        try {
            ShipArtStore.useDirectory(java.nio.file.Files.createTempDirectory("flicker-audit"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        System.setProperty("card.seed", Long.toString(SEED));

        view = new PixelGameView();
        view.screens().setScanlineEnabled(false);
        view.screens().setDebugVisible(false);

        try {
            mouseX = CardLoopScreen.class.getDeclaredField("mouseX");
            mouseY = CardLoopScreen.class.getDeclaredField("mouseY");
            optionRects = CardLoopScreen.class.getDeclaredMethod("optionRects");
            mouseX.setAccessible(true);
            mouseY.setAccessible(true);
            optionRects.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }

        // 手牌 0 的矩形（弹窗锚点）
        double[] card = null;
        try {
            @SuppressWarnings("unchecked")
            List<int[]> boxes = (List<int[]>) optionRects.invoke(cardScreen());
            if (boxes != null && !boxes.isEmpty()) {
                int[] b = boxes.get(0);
                card = new double[]{b[0] + b[2] / 2.0, b[1] + b[3] / 2.0};
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // cardScreen() 在 create() 之前可能还不是战斗界面：锚点用默认值
        }
        final double cx = card == null ? 320 : card[0];
        final double cy = card == null ? 300 : card[1];

        view.frameListener = (frame, v) -> {
            try {
                if (frame == 1) {
                    // 进战斗
                    v.go(ScreenContext.Target.CARD);
                    v.screens().finishTransition();
                    press("1");
                    if (cardScreen() != null) {
                        cardScreen().debugFastForward(1);
                        cardScreen().debugClearToast();
                    }
                    // 锚点在进战斗后重算一次（此时 optionRects 才是手牌的矩形）
                    try {
                        @SuppressWarnings("unchecked")
                        List<int[]> boxes = (List<int[]>) optionRects.invoke(cardScreen());
                        if (boxes != null && !boxes.isEmpty()) {
                            int[] b = boxes.get(0);
                            anchor[0] = b[0] + b[2] / 2.0;
                            anchor[1] = b[1] + b[3] / 2.0;
                        }
                    } catch (ReflectiveOperationException ignored) {
                        // 锚点维持默认值
                    }
                }
                double ax = anchor[0];
                double ay = anchor[1];
                if (frame == SETTLE) {
                    screen = cardScreen();
                    screen.debugHoverCard(0);
                }
                if (frame > SETTLE && frame <= SETTLE + JITTER) {
                    double t = frame - SETTLE;
                    mouseX.setDouble(screen, ax + Math.round(2 * Math.sin(t * 0.55)));
                    mouseY.setDouble(screen, ay + Math.round(2 * Math.cos(t * 0.31)));
                }
                if (frame == SETTLE + JITTER + 1) {
                    // 静置段开始：最后一次把鼠标放回锚点，之后不再碰任何状态
                    mouseX.setDouble(screen, ax);
                    mouseY.setDouble(screen, ay);
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            capture(frame);
            if (frame >= TOTAL) {
                Gdx.app.exit();
            }
        };

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("flicker audit");
        config.setWindowedMode(1600, 900);
        config.setInitialVisible(false);
        new Lwjgl3Application(view, config);
    }

    private final double[] anchor = {320, 300};

    private void capture(long frame) {
        // 视图每帧渲染完成后，设计缓冲就是完整的一帧：直接从 int[] 导 PNG
        try {
            PngWriter.write(view.buffer().art().buffer(),
                    view.buffer().width(), view.buffer().height(),
                    new File(outDir, String.format("f%04d.png", frame)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private CardLoopScreen cardScreen() {
        return view.screens().current() instanceof CardLoopScreen s ? s : null;
    }

    private void press(String code) {
        String normalized = code.length() == 1 && Character.isDigit(code.charAt(0)) ? "DIGIT" + code : code;
        view.screens().handleKey(normalized);
        view.screens().finishTransition();
        if (cardScreen() != null) {
            cardScreen().debugClearToast();
        }
    }

    public static void main(String[] args) {
        new FlickerAudit().start();
    }
}
