package com.hysx.salvosequence.ui;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hysx.salvosequence.audio.Music;
import com.hysx.salvosequence.audio.MusicTrack;
import com.hysx.salvosequence.audio.Sound;
import com.hysx.salvosequence.audio.SoundManager;
import com.hysx.salvosequence.core.AwtImageCodec;
import com.hysx.salvosequence.core.ImageCodec;

/**
 * 入口（libGDX 版）：把 {@link PixelGameView} 装进 LWJGL3 窗口。
 *
 * <p><b>默认全屏</b>（当前桌面显示模式的分辨率）：和原版一致，铺满屏幕更容易落在
 * 整数倍缩放上（1920x1080 -> 3.0 倍），每个设计像素都是干净的 NxN 物理像素。
 * 仍然可以缩放/还原：{@code F11} 切全屏，{@code Ctrl+W} 切回 1280x720 窗口，
 * {@code Ctrl+Q} 直接退出（全屏状态下没有关窗按钮）。</p>
 *
 * <p>启动参数：</p>
 * <ul>
 *   <li>{@code -Dsalvosequence.windowed=true}：以窗口模式启动；</li>
 *   <li>{@code -Dsalvosequence.music=battle}：启动 1.5 秒后强制试听战斗曲；</li>
 *   <li>{@code -Dsalvosequence.smoke.frames=N}：渲染 N 帧后自动退出（冒烟自检）。</li>
 * </ul>
 */
public class SalvoSequenceApp {

    /** 窗口模式尺寸（设计分辨率的 2 倍，仅作对比用）。 */
    public static final int WINDOWED_WIDTH = 1280;
    public static final int WINDOWED_HEIGHT = 720;

    /** 目标满屏尺寸（全屏直接用桌面当前模式，等价原版"按屏幕铺满"）。 */
    public static final int FULL_WIDTH = 1920;
    public static final int FULL_HEIGHT = 1080;

    public static void main(String[] args) {
        boolean windowed = Boolean.getBoolean("salvosequence.windowed")
                || (System.getProperty("salvosequence.smoke.frames") != null
                        && !Boolean.getBoolean("salvosequence.fullscreen"));

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("齐射序列  -  Salvo Sequence");
        // 【关键】HdpiMode.Pixels：全部坐标以物理像素为口径。默认 Logical 在
        // Windows 150% 缩放下会把 1280x720 的物理窗口报告成 853x480"逻辑"尺寸，
        // 游戏缩放变成 1.33（非整数、内容缩小错位）——窗口模式错误的根因。
        config.setHdpiMode(com.badlogic.gdx.graphics.glutils.HdpiMode.Pixels);
        // 独占全屏启动（显示器原生 1920x1080 物理，缩放 3.0 整数）。
        // 可选：-Dsalvosequence.windowed=true 窗口启动（1280x720 物理，缩放 2.0）。
        if (windowed) {
            config.setWindowedMode(WINDOWED_WIDTH, WINDOWED_HEIGHT);
        } else {
            config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        }
        config.setResizable(true);
        // 性能对比用：-Dsalvosequence.novsync=true 关掉垂直同步，让帧率反映真实渲染开销
        if (Boolean.getBoolean("salvosequence.novsync")) {
            config.useVsync(false);
            config.setForegroundFPS(0);
        }

        // 背景工坊的图片编解码后端（Android 版换成 BitmapFactory 实现）
        ImageCodec.setBackend(new AwtImageCodec());

        System.out.println("[app] 音频: " + audioStatus());

        // 只想听战斗曲时用：-Dsalvosequence.music=battle（正式流程是进战斗界面才切）
        String wantMusic = System.getProperty("salvosequence.music");
        if (wantMusic != null && !wantMusic.isBlank()) {
            new Thread(() -> {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                Music.play(MusicTrack.BATTLE, true);
                Music.setIntensity(0.5);
                System.out.println("[app] 试听: 强制播放 " + wantMusic);
            }, "salvosequence-music-tryout").start();
        }

        new Lwjgl3Application(new PixelGameView(), config);
    }

    /**
     * 报告音频链路是否可用，并挂上退出时的清理钩子。
     *
     * <p>音频后端是 JDK 自带的 {@code javax.sound.sampled}（{@code java.desktop}），
     * 与 JavaFX/libGDX 都无关，所以这段和原版完全一样。</p>
     *
     * @return 一行给日志用的状态描述
     */
    private static String audioStatus() {
        try {
            String reason = Sound.manager().probe();
            // 退出时停声音 + 结束清理线程
            Runtime.getRuntime().addShutdownHook(new Thread(Sound::shutdown,
                    "salvosequence-audio-shutdown"));
            String sfx = reason == null ? "音效可用（" + SoundManager.formatSummary() + "）"
                    : "音效不可用（" + reason + "）";
            return sfx + "，" + musicStatus();
        } catch (Throwable unavailable) {
            return "不可用（" + unavailable + "），已静音继续运行";
        }
    }

    /**
     * 配乐状态。
     *
     * <p>这里<b>只查可用性、不初始化合成器</b>：合成器是真要放曲子时才开的（进标题
     * 界面会开）。把"能不能用"和"现在占没占设备"分开报，出问题时一眼能看出是哪种。</p>
     */
    private static String musicStatus() {
        try {
            return Music.available() ? "配乐可用（MIDI）" : "配乐不可用（" + Music.failureReason() + "）";
        } catch (Throwable unavailable) {
            return "配乐不可用（" + unavailable + "）";
        }
    }

    /**
     * 音频的收尾：先停配乐再把混音器那条 {@code SourceDataLine} 关掉。
     *
     * <p>顺序不能反。{@code Music} 的合成器与 {@code Mixer} 的混音线程都是
     * <b>非守护线程</b>，只要有一个活着，JVM 就不会退出 —— 现象是
     * 「窗口没了、音乐还在响、进程挂在后台」。libGDX 版在视图 {@code dispose()}
     * 时调用这里，退出钩子兜底。</p>
     */
    public static void closeAudio() {
        try {
            Music.shutdown();
        } catch (Throwable ignored) {
            // 退出路径上不该再抛东西
        }
        try {
            Sound.shutdown();
        } catch (Throwable ignored) {
            // 同上
        }
    }
}
