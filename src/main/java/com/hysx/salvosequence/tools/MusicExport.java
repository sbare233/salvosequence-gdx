package com.hysx.salvosequence.tools;

import com.hysx.salvosequence.audio.MusicRegistry;
import com.hysx.salvosequence.audio.MusicTrack;

import java.io.File;
import java.io.IOException;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;

/**
 * 把生成的配乐导出成 {@code .mid} 文件，方便用 DAW / 播放器看和改。
 *
 * <pre>
 * mvn -Pmusic-export javafx:run                              → 写到 src/main/resources/music/
 * mvn -Pmusic-export javafx:run "-Dmusic.out=target/music"   → 写到临时目录（先试试）
 * </pre>
 *
 * <p><b>为什么还要导出</b>：运行时的曲子是代码生成的（见 {@link MusicRegistry}），
 * 生成式的好处是能被战况实时驱动，坏处是没法用耳朵之外的手段改。导出成 .mid 之后，
 * 想调旋律就丢进任何音序器改完再放回去 —— {@code Music} 加载时优先读类路径上的
 * {@code /music/*.mid}，找不到才现场生成。也就是说：<b>文件在就听文件，文件不在就听
 * 生成版</b>，两者是覆盖关系而不是替代关系。</p>
 */
public final class MusicExport {

    private MusicExport() {
    }

    public static void main(String[] args) {
        String out = System.getProperty("music.out", "src/main/resources/music");
        File dir = new File(out);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            System.out.println("[music-export] 建不出目录: " + dir.getAbsolutePath());
            System.exit(2);
            return;
        }

        int written = 0;
        int failed = 0;
        for (MusicTrack track : MusicTrack.values()) {
            File file = new File(dir, track.file() + ".mid");
            try {
                Sequence seq = MusicRegistry.compose(track);
                int type = pickFileType(seq);
                // write 返回写出的字节数，失败返回 -1（不是 boolean）
                int bytes = MidiSystem.write(seq, type, file);
                if (bytes <= 0) {
                    throw new IOException("MidiSystem.write 返回 " + bytes);
                }
                System.out.printf(
                        "[music-export] 写出 %-14s %5d 字节  类型%d  PPQ=%d  轨道=%d  %d BPM%n",
                        file.getName(), bytes, type, seq.getResolution(),
                        seq.getTracks().length, track.bpm());
                written++;
            } catch (InvalidMidiDataException | IOException e) {
                System.out.println("[music-export] " + track + " 失败: " + e);
                failed++;
            }
        }

        System.out.println("[music-export] 完成: 写出 " + written + " 个，失败 " + failed + " 个"
                + "  → " + dir.getAbsolutePath());
        if (failed > 0) {
            System.exit(2);
        }
    }

    /** 优先类型 1（多轨，任何音序器都认），不支持就退到平台能写的第一个类型。 */
    private static int pickFileType(Sequence seq) {
        int[] types = MidiSystem.getMidiFileTypes(seq);
        if (types.length == 0) {
            return 1;
        }
        for (int t : types) {
            if (t == 1) {
                return 1;
            }
        }
        return types[0];
    }
}
