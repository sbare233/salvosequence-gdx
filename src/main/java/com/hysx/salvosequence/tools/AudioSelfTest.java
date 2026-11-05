package com.hysx.salvosequence.tools;

import com.hysx.salvosequence.audio.Sound;
import com.hysx.salvosequence.audio.SoundId;
import com.hysx.salvosequence.audio.SoundManager;

/**
 * 音频自检：把 {@code src/main/resources/sounds/*.wav} 逐个走一遍生产代码路径，
 * 打印格式、时长、峰值、有效值，并（默认）真的出声。
 *
 * <p><b>为什么要把峰值打出来</b>：上一版音频出过一个"加载成功但听不见"的毛病——
 * 文件能读、对象能建，只是单声道 PCM 根本没被播出来。光看"加载成功"是发现不了的，
 * 得看幅度：峰值≈0 说明资源本身就是静音的，峰值正常却听不见才是播放链路的问题。
 * 现在换成 JDK 自带的 {@code javax.sound.sampled} 之后，这两件事都能量得出来。</p>
 *
 * <p>它<b>不依赖任何 JavaFX 音频类</b>，所以精简运行时（jlink 只带 javafx.controls）
 * 里也能跑——这正是"分发包有没有声音"该用的验证手段。</p>
 *
 * <p><b>和 {@link AudioProbe} 的分工</b>：这是"批量脚本"自检——把全部音效逐个走
 * 一遍生产代码路径并实际播放；命令行版 {@code AudioProbe} 偏向对着单个资源排障。
 * 两者共用同一套 {@code Sound}/{@code SoundManager} 代码路径，结论一致。</p>
 *
 * <p>退出码：0 = 全部通过；2 = 有失败项（供打包脚本的启动自检判断）。</p>
 */
public final class AudioSelfTest {

    /** 全部音效都试，别只挑几个——少一个资源在生产里就是一处哑火。 */
    private static final SoundId[] IDS = SoundId.values();

    public void start() {        boolean play = playRequested();
        Sound.setQuiet(false);
        System.out.println("[audio] 输出格式 " + SoundManager.formatSummary()
                + "    设备 " + SoundManager.deviceSummary());
        System.out.println("[audio] 另有一个非 JavaFX 的纯 JDK 入口，见 AudioProbe");

        int ok = 0;
        int failed = 0;
        for (SoundId id : IDS) {
            try {
                SoundManager.ClipInfo info = Sound.manager().diagnostic(id);
                if (info == null) {
                    System.out.println("[audio] " + id + " -> 不可用"
                            + (Sound.manager().audioBrokenReason() == null ? "（资源缺失）"
                            : ": " + Sound.manager().audioBrokenReason()));
                    failed++;
                    continue;
                }
                System.out.printf("[audio] %-11s ok  %5.0fms  峰值=%.3f  有效值=%.3f  %s%n",
                        id, info.durationSeconds() * 1000, info.peak(), info.rms(),
                        id.resourcePath());
                if (play) {
                    // 真的推一次：构造/加载成功不代表能出声
                    Sound.play(id, 0.7, 1.0);
                    // 留出播放时间，否则自检还没出声就退出了
                    sleep(info.durationSeconds() * 1000 + 60);
                }
                ok++;
            } catch (Throwable ex) {
                // 故意接 Throwable：设备层偶发 Error，不能让它看起来像"自检自己崩了"
                System.out.println("[audio] " + id + " -> 失败: " + ex);
                failed++;
            }
        }
        System.out.println("[audio] 成功 " + ok + "/" + IDS.length
                + (play ? "（已实际播放，注意听声音）" : "（仅加载，未播放：AUDIO_PLAY=false）"));
        if (failed > 0) {
            System.out.println("[audio] 失败 " + failed + " 项");
        }
        exit(failed);
    }

    /**
     * 是否要真的推一次声音。
     *
     * <p>默认开；设 {@code AUDIO_PLAY=false} 关掉（批量脚本里不想吵）。
     * 之所以多这一条环境变量：<b>PowerShell 传 {@code -Daudio.play=true} 会被从点号处
     * 拆开</b>（实测 java 收到的是 {@code .play=true}），环境变量没有这个问题。</p>
     */
    private static boolean playRequested() {
        String env = System.getenv("AUDIO_PLAY");
        if (env != null && !env.isBlank()) {
            return !env.trim().equalsIgnoreCase("false") && !env.trim().equals("0");
        }
        return !Boolean.getBoolean("audio.silent");
    }

    private static void sleep(double ms) {
        try {
            Thread.sleep((long) ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void exit(int failures) {
        Sound.shutdown();
        // 退出码要显式设定，脚本才判断得了成败。
        if (failures > 0) {
            System.exit(2);
        } else {
            System.exit(0);
        }
    }

    public static void main(String[] args) {
        new AudioSelfTest().start();
    }
}
