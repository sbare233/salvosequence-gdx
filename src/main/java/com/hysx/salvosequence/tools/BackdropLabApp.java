package com.hysx.salvosequence.tools;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hysx.salvosequence.screen.ScreenContext;
import com.hysx.salvosequence.ui.PixelGameView;
import com.hysx.salvosequence.ui.VideoSettings;

/**
 * 背景工坊的独立入口：开窗直达 {@code BackdropLabScreen}，方便截图核对滤镜。
 *
 * <p>可选系统属性：{@code -Dlab.filter=BAYER|PIXEL} 指定进工坊时用的背景滤镜；
 * 配合 {@code -Dsalvosequence.smoke.frames=N -Dsalvosequence.smoke.png=...} 可以无人值守出图。</p>
 */
public final class BackdropLabApp {

    public void start() {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("背景工坊 - backdrop lab");
        config.setWindowedMode(1280, 720);

        PixelGameView view = new PixelGameView();
        // 工坊要读 backdrops-src/ 里的 PNG：桌面端装 AWT 解码器，否则会写"没有图片解码器"
        com.hysx.salvosequence.core.ImageCodec.setBackend(
                new com.hysx.salvosequence.core.AwtImageCodec());
        view.frameListener = (frame, v) -> {
            if (frame != 1) {
                return;
            }
            String filter = System.getProperty("lab.filter");
            if (filter != null && !filter.isBlank()) {
                try {
                    VideoSettings.setBackdropFilter(VideoSettings.BackdropFilter.valueOf(
                            filter.trim().toUpperCase(java.util.Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    // 名字写错就用设置里的值
                }
            }
            v.go(ScreenContext.Target.BACKDROP_LAB);
            System.out.println("[lab] 背景工坊  滤镜=" + VideoSettings.backdropFilter().labelEn());
        };
        new Lwjgl3Application(view, config);
    }

    public static void main(String[] args) {
        new BackdropLabApp().start();
    }
}
