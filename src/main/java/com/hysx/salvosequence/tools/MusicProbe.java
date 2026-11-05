package com.hysx.salvosequence.tools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import com.hysx.salvosequence.audio.Music;

/**
 * MIDI 体检工具：读一个 .mid，报告它的规格，然后真放一遍。
 *
 * <p>分两件事：</p>
 * <ol>
 *   <li><b>体检（纯 JDK，不需要 JavaFX）</b>：格式、轨数、时长、用到的乐器、
 *       藏在里面的文本元数据（版权声明常常就在这儿）。</li>
 *   <li><b>试放</b>：走游戏自己的 {@link Music} 引擎，验证"外部曲子能不能被
 *       我们的合成器、音量、强度这套机制带动"。默认放 15 秒。</li>
 * </ol>
 *
 * <pre>
 * java -cp salvosequence.jar com.hysx.salvosequence.tools.MusicProbe &lt;文件.mid&gt; [秒数]
 * </pre>
 *
 * <p>中文输出，Windows 控制台想看清要先 {@code chcp 65001}；这里每个类别前都有
 * ASCII 前缀（{@code [musicprobe]}），看不懂中文也能定位哪一步失败。</p>
 */
public final class MusicProbe {

    private static final String TAG = "[musicprobe]";

    private MusicProbe() {
    }

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.out.println(TAG + " 用法: MusicProbe <文件.mid> [试放秒数]");
                System.exit(2);
                return;
            }
            File file = new File(args[0]);
            double seconds = args.length > 1 ? Double.parseDouble(args[1]) : 15;
            if (!file.isFile()) {
                System.out.println(TAG + " 找不到文件: " + file);
                System.exit(2);
                return;
            }

            System.out.println(TAG + " 文件: " + file.getName()
                    + "  (" + (file.length() / 1024) + " KB)");
            inspect(file);
            play(file, seconds);
            System.out.println(TAG + " 完成");
            System.exit(0);
        } catch (Throwable t) {
            System.out.println(TAG + " 出错: " + t);
            t.printStackTrace(System.out);
            System.exit(2);
        } finally {
            Music.shutdown();
        }
    }

    /** 体检：把文件的规格和文本元数据打出来。 */
    private static void inspect(File file) throws Exception {
        Sequence seq = MidiSystem.getSequence(file);
        double seconds = seq.getMicrosecondLength() / 1_000_000.0;
        TreeSet<Integer> programs = new TreeSet<>();
        List<String> texts = new ArrayList<>();
        int notes = 0;
        for (Track t : seq.getTracks()) {
            for (int i = 0; i < t.size(); i++) {
                MidiEvent e = t.get(i);
                MidiMessage m = e.getMessage();
                if (m instanceof ShortMessage sm) {
                    if (sm.getCommand() == ShortMessage.PROGRAM_CHANGE) {
                        programs.add(sm.getData1());
                    } else if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                        notes++;
                    }
                } else if (m instanceof MetaMessage mm) {
                    int type = mm.getType();
                    if (type == 0x01 || type == 0x02 || type == 0x03) {
                        String s = new String(mm.getData(), java.nio.charset.StandardCharsets.ISO_8859_1).trim();
                        if (!s.isBlank() && !texts.contains(s)) {
                            texts.add(s);
                        }
                    }
                }
            }
        }
        boolean ppq = seq.getDivisionType() == Sequence.PPQ;
        System.out.println(TAG + " 格式: 类型 " + (seq.getTracks().length > 1 ? 1 : 0)
                + (ppq ? "  PPQ=" + seq.getResolution() : "  SMPTE")
                + "  轨数=" + seq.getTracks().length
                + "  音符=" + notes
                + "  时长=" + String.format("%.1f", seconds) + "s");
        System.out.println(TAG + " 乐器数: " + programs.size()
                + (programs.isEmpty() ? "" : "  音色号=" + programs));
        if (texts.isEmpty()) {
            System.out.println(TAG + " 文本元数据: 无");
        } else {
            for (String s : texts) {
                if (s.length() > 100) {
                    s = s.substring(0, 100) + "…";
                }
                System.out.println(TAG + " 文本元数据: " + s);
            }
        }
    }

    /**
     * 试放：走游戏自己的引擎。
     *
     * <p>用 {@link Music#loadFile} + {@link Music#playFile} 而不是直接开合成器，
     * 就是为了验证"外部曲子进我们的引擎"这条真实链路；顺带确认强度/音量下发
     * 不会把它搞哑。</p>
     */
    private static void play(File file, double seconds) throws Exception {
        Sequence seq = Music.loadFile(file);
        if (seq == null) {
            System.out.println(TAG + " 引擎读不了这个文件"
                    + (Music.available() ? "" : "（音乐不可用: " + Music.failureReason() + "）"));
            System.exit(2);
            return;
        }
        if (!Music.playFile(file, true)) {
            System.out.println(TAG + " 引擎拒绝播放");
            System.exit(2);
            return;
        }
        System.out.println(TAG + " 正在试放 " + seconds + "s（音量 " + Music.level()
                + "，强度 " + Music.intensity() + "）…");
        long end = System.nanoTime() + (long) (seconds * 1_000_000_000L);
        while (System.nanoTime() < end) {
            Thread.sleep(2000);
            System.out.println(TAG + "   2 秒后: 正在放=" + Music.isPlaying()
                    + "  已播=" + String.format("%.1f", Music.positionSeconds()) + "s"
                    + " / 总长=" + String.format("%.1f", Music.currentLengthSeconds()) + "s");
        }
        System.out.println(TAG + " 播放状态: 正在放=" + Music.isPlaying()
                + "  时长读数=" + String.format("%.1f", Music.currentLengthSeconds()) + "s");
        Music.stop();
    }
}
