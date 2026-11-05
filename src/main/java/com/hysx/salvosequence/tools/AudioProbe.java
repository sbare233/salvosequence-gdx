package com.hysx.salvosequence.tools;

import com.hysx.salvosequence.audio.Sound;
import com.hysx.salvosequence.audio.SoundId;
import com.hysx.salvosequence.audio.SoundManager;

/**
 * 纯 JDK 音频自检：<b>不碰 JavaFX、不碰游戏窗口</b>，可以直接对着分发包跑。
 *
 * <pre>
 * 开发态： mvn -Paudio javafx:run     （或 java -cp target\classes com.hysx.salvosequence.tools.AudioProbe）
 * 分发包： .\runtime\bin\java.exe -cp ".\app\salvosequence.jar" com.hysx.salvosequence.tools.AudioProbe
 *          （命令行里加 --play 就真的出声；不加只量数据）
 * </pre>
 *
 * <p><b>为什么单独有这么一个类</b>：以前音频走 {@code javafx.media}，自检必须起一个
 * JavaFX 窗口才能跑；而"组员机器上没声音"恰恰要在一个最小环境里复现。现在音频后端是
 * JDK 自带的 {@code javax.sound.sampled}，于是自检可以退化成一条最朴素的命令行程序：
 * 能跑通就说明运行时、模块、资源、声卡四件事都没问题，跑不通的那一行日志就是结论。</p>
 *
 * <p>它同时直接解析 WAV 头（{@code parseWavHeader}），这样"Java Sound 能不能读"
 * 和"文件本身是什么规格"两个问题不会混在一起——以前就吃过这个亏：文件读出来了、
 * 对象也建了，但单声道 PCM 根本没出声。</p>
 *
 * <p>退出码：0 = 全部通过；2 = 有失败项。</p>
 */
public final class AudioProbe {

    /** WAV 头解析结果，只取自检要看的那几项。 */
    private record WavHeader(int channels, int sampleRate, int bitsPerSample,
                             long dataBytes, double seconds) {
        String describe() {
            return sampleRate + "Hz " + bitsPerSample + "bit " + channels + "声道 "
                    + String.format("%.0fms", seconds * 1000);
        }
    }

    private AudioProbe() {
    }

    public static void main(String[] args) {
        boolean play = false;
        for (String a : args) {
            if (a.equalsIgnoreCase("--play") || a.equalsIgnoreCase("-p")) {
                play = true;
            }
        }
        if (Boolean.getBoolean("audio.play") || "true".equalsIgnoreCase(System.getenv("AUDIO_PLAY"))) {
            play = true;
        }

        System.out.println("[probe] 输出格式 " + SoundManager.formatSummary());
        System.out.println("[probe] 设备     " + SoundManager.deviceSummary());
        reportMidi();
        if (Boolean.getBoolean("audio.devices") || "true".equalsIgnoreCase(System.getenv("AUDIO_DEVICES"))) {
            dumpDevices();
        }

        int ok = 0;
        int failed = 0;
        for (SoundId id : SoundId.values()) {
            String resource = id.resourcePath();
            try {
                byte[] bytes = readResource(resource);
                WavHeader header = parseWavHeader(bytes);
                SoundManager.ClipInfo info = Sound.manager().diagnostic(id);
                if (info == null) {
                    System.out.println("[probe] " + id + " 失败: 解码失败或资源缺失 "
                            + (Sound.manager().audioBrokenReason() == null ? ""
                            : "(" + Sound.manager().audioBrokenReason() + ")"));
                    failed++;
                    continue;
                }
                System.out.printf("[probe] %-11s ok  %s  峰值=%.3f  有效值=%.3f%n",
                        id, header.describe(), info.peak(), info.rms());
                if (play) {
                    Sound.play(id, 0.7, 1.0);
                    Thread.sleep((long) (info.durationSeconds() * 1000) + 60);
                }
                ok++;
            } catch (Throwable ex) {
                System.out.println("[probe] " + id + " 抛出: " + ex);
                failed++;
            }
        }

        String broken = Sound.manager().audioBrokenReason();
        if (broken != null) {
            System.out.println("[probe] 音频设备不可用: " + broken);
        }
        System.out.println("[probe] 成功 " + ok + "/" + SoundId.values().length
                + (play ? "（已实际播放）" : "（仅加载；加 --play 出声）"));
        Sound.shutdown();
        // reportMidi() 会把 MIDI 合成器开起来，这里必须成对关掉：Gervill 带着非守护
        // 线程，不关的话 JVM 到 main 结束也不退出，而调用方（打包脚本）是同步等
        // 退出码的 —— 实测就是这么卡死过一次。
        com.hysx.salvosequence.audio.Music.shutdown();
        if (failed > 0 || broken != null) {
            System.out.println("[probe] 有失败项");
            System.exit(2);
        }
        // 显式退出：Java Sound 里万一带了别的非守护线程，工具也得报完结果就结束。
        System.exit(0);
    }

    /**
     * 顺带报一句配乐能不能用。
     *
     * <p>音效走 {@code javax.sound.sampled}、配乐走 {@code javax.sound.midi}，是<b>两条独立
     * 的链路</b>：一个坏了不代表另一个坏。合并成一句报出来，省得"没声音"时还要猜是哪条。</p>
     */
    private static void reportMidi() {
        try {
            boolean ok = com.hysx.salvosequence.audio.Music.available();
            System.out.println("[probe] 配乐     " + (ok ? "可用（MIDI 合成器就绪）"
                    : "不可用（" + com.hysx.salvosequence.audio.Music.failureReason() + "）"));
        } catch (Throwable ex) {
            System.out.println("[probe] 配乐     不可用（" + ex + "）");
        }
    }

    /**
     * 列出声卡（设 {@code AUDIO_DEVICES=true} 打开）。
     *
     * <p>为什么需要它：{@code AudioSystem.getSourceDataLine(fmt)} 不给设备名，
     * 只挑"默认 mixer"，在装了虚拟声卡/多显示器的机器上可能挑到一条通往
     * 没人听的输出。听不到声音时先看这张表，再决定要不要指定设备。</p>
     */
    private static void dumpDevices() {
        javax.sound.sampled.Mixer.Info[] infos = javax.sound.sampled.AudioSystem.getMixerInfo();
        System.out.println("[probe] 声卡清单（" + infos.length + "）：");
        for (int i = 0; i < infos.length; i++) {
            javax.sound.sampled.Mixer mixer = javax.sound.sampled.AudioSystem.getMixer(infos[i]);
            javax.sound.sampled.Line.Info[] lines = mixer.getSourceLineInfo();
            int output = 0;
            for (javax.sound.sampled.Line.Info li : lines) {
                if (li instanceof javax.sound.sampled.DataLine.Info d
                        && d.getLineClass() == javax.sound.sampled.SourceDataLine.class) {
                    output++;
                }
            }
            System.out.printf("[probe]   #%02d %-52s 输出线路=%d%n",
                    i, shorten(infos[i].getName(), 52), output);
        }
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static byte[] readResource(String path) throws Exception {
        try (var in = AudioProbe.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("类路径上找不到 " + path);
            }
            return in.readAllBytes();
        }
    }

    /**
     * 直接读 WAV 头，不经过 Java Sound。
     *
     * <p>按 chunk 遍历而不是写死偏移：真实素材里 {@code fmt } 和 {@code data} 之间
     * 常夹着 {@code LIST}/{@code fact} 块，写死偏移会读出垃圾。</p>
     */
    static WavHeader parseWavHeader(byte[] b) {
        if (b.length < 44 || b[0] != 'R' || b[1] != 'I' || b[2] != 'F' || b[3] != 'F'
                || b[8] != 'W' || b[9] != 'A' || b[10] != 'V' || b[11] != 'E') {
            throw new IllegalArgumentException("不是 RIFF/WAVE 文件");
        }
        int channels = 0;
        int rate = 0;
        int bits = 0;
        long dataBytes = 0;
        int pos = 12;
        while (pos + 8 <= b.length) {
            String id = new String(b, pos, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int size = le32(b, pos + 4);
            int body = pos + 8;
            if (id.equals("fmt ") && body + 16 <= b.length) {
                channels = le16(b, body + 2);
                rate = le32(b, body + 4);
                bits = le16(b, body + 14);
            } else if (id.equals("data")) {
                dataBytes = Math.min(size, b.length - body);
            }
            pos = body + size + (size & 1); // 块长度奇数时有 1 字节填充
        }
        if (channels == 0 || rate == 0 || bits == 0) {
            throw new IllegalArgumentException("缺少 fmt 块");
        }
        double seconds = dataBytes / (double) (rate * channels * (bits / 8));
        return new WavHeader(channels, rate, bits, dataBytes, seconds);
    }

    private static int le16(byte[] b, int i) {
        return (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8);
    }

    private static int le32(byte[] b, int i) {
        return (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8)
                | ((b[i + 2] & 0xFF) << 16) | ((b[i + 3] & 0xFF) << 24);
    }
}
