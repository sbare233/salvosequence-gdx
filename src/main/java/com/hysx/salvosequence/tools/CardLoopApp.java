package com.hysx.salvosequence.tools;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hysx.salvosequence.screen.ScreenContext;
import com.hysx.salvosequence.ui.PixelGameView;

/**
 * 「组舰者」卡牌循环验证的<b>独立可玩入口</b>：开窗直接进卡牌循环那一屏。
 *
 * <p>跑法：{@code mvn compile exec:java -Dexec.mainClass=com.hysx.salvosequence.tools.CardLoopApp}
 * （窗口 1280x720，正好 2 倍整数缩放）。</p>
 *
 * <p><b>为什么需要这个类</b>：{@link com.hysx.salvosequence.screen.CardLoopScreen} 是
 * {@code Screen} 的实现，它不是一个应用——它只是 {@code ScreenManager}
 * 里的一个状态。所以"只想看这一屏"有两条路：</p>
 * <ul>
 *   <li>完整游戏：运行 {@code PixelHudApp}，然后按 {@code C}（这条路径永远可用）</li>
 *   <li>直达调试：本类，省掉按 C 这一步，也省掉全屏铺满屏幕</li>
 * </ul>
 *
 * <p>可选系统属性：{@code -Dcard.seed=20240601} 固定种子；{@code -Dcard.fullscreen=true}
 * 全屏启动（默认窗口化，方便截图/对照）。</p>
 *
 * <p>退出路径必须自己走一遍音频收尾：{@code Music}／{@code Mixer} 的线程是非守护线程，
 * 不关就会"窗口没了、进程还挂在后台"（见 README 的退出规则）。
 * libGDX 版里收尾挂在视图 {@code dispose()} -> {@code SalvoSequenceApp.closeAudio()}。</p>
 */
public final class CardLoopApp {

    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;

    public void start() {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("组舰者  -  card loop");
        if (Boolean.getBoolean("card.fullscreen")) {
            config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        } else {
            config.setWindowedMode(WINDOW_WIDTH, WINDOW_HEIGHT);
        }

        // 直达卡牌循环：走 ScreenManager 的正常切换流程（带淡入），不硬造 Screen。
        PixelGameView view = new PixelGameView();
        view.frameListener = (frame, v) -> {
            if (frame != 1) {
                return;
            }
            v.go(ScreenContext.Target.CARD);
            System.out.println(String.format(
                    "[card] 直达卡牌循环  种子=%s  视口=%dx%d  缩放=%.4f",
                    System.getProperty("card.seed", "(随机)"),
                    com.badlogic.gdx.Gdx.graphics.getWidth(),
                    com.badlogic.gdx.Gdx.graphics.getHeight(), v.scale()));
        };
        new Lwjgl3Application(view, config);
    }

    public static void main(String[] args) {
        new CardLoopApp().start();
    }
}
