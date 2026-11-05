package com.hysx.salvosequence.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配乐导出的回归测试。
 *
 * 为什么值得测：MIDI 文件是二进制格式，写错了不会报错、只会"放出来不对"——
 * 音符没配对会卡音，时间戳写反会让曲子节奏乱掉，速度元数据写错会让整首快慢不对。
 * 这些用肉眼看文件是看不出来的，必须解析回来数。
 *
 * 注意这里**不测 WAV 渲染**：那条路要开 Gervill 合成器，会拉进 JavaFX 之外的原生音频栈，
 * 在 surefire 里既慢又不稳。渲染结果用 AudioExporter 手工跑一次核对（见 README）。
 */
class AudioExporterTest {

    @Test
    void 卡农的音符数量与时值符合编曲() {
        List<AudioExporter.Note> score = AudioExporter.arrange();
        assertFalse(score.isEmpty(), "谱子不该是空的");

        // 每遍 8 小节：低音 2 + 铺底 3 + 钢琴 4 = 每小节 9 个 × 8 小节 × 2 遍 = 144
        long bass = score.stream().filter(n -> n.program() == AudioExporter.PROG_CELLO).count();
        long pad = score.stream().filter(n -> n.program() == AudioExporter.PROG_PAD).count();
        long piano = score.stream().filter(n -> n.program() == AudioExporter.PROG_PIANO).count();
        long strings = score.stream().filter(n -> n.program() == AudioExporter.PROG_STRINGS).count();

        assertEquals(32, bass, "通奏低音：每小节 2 个 × 8 小节 × 2 遍");
        assertEquals(48, pad, "铺底：每小节 3 个 × 8 小节 × 2 遍");
        assertEquals(64, piano, "钢琴：每小节 4 个 × 8 小节 × 2 遍");
        // 弦乐声部的进入是"错开"的：V1 从第 0 拍起、V2 从第 8 拍起、V3 从第 16 拍起，
        // 而每遍只有 32 拍。于是第一遍只轮到 V1（16 个）与 V2 的前半（8 个），
        // 第二遍轮到 V1 后半（16 个）+ V2 后半（12 个）+ V3 前半（8 个）= 60。
        // 写成断言是为了锁住"错开进入"这个编曲意图，不是随手记的数。
        assertEquals(60, strings, "弦乐：见上方注释的错开进入计算");
        assertEquals(32 + 48 + 64 + 60, score.size(), "总事件数");
        assertEquals(204, score.size(), "换编曲时这个数一定变，强制重新核对");

        // 音高都要在 GM 的合理范围内
        for (AudioExporter.Note n : score) {
            assertTrue(n.pitch() >= 21 && n.pitch() <= 108, "音高越界: " + n.pitch());
            assertTrue(n.velocity() >= 1 && n.velocity() <= 127, "力度越界: " + n.velocity());
            assertTrue(n.duration() > 0, "时值必须为正");
            assertTrue(n.beat() >= 0, "起始拍不能为负");
        }
    }

    @Test
    void 导出的MIDI能被解析回来且音符配对(@TempDir Path tmp) throws Exception {
        List<AudioExporter.Note> score = AudioExporter.arrange();
        double lastBeat = 0;
        for (AudioExporter.Note n : score) lastBeat = Math.max(lastBeat, n.beat() + n.duration());

        File out = new File(tmp.toFile(), "canon.mid");
        int written = AudioExporter.writeMidi(out, score, lastBeat);
        assertTrue(written > 0, "MidiSystem.write 返回写入字节数，<=0 表示失败");
        assertEquals(out.length(), written, "返回的字节数应等于文件实际大小");

        Sequence seq = MidiSystem.getSequence(out);
        assertEquals(1, MidiSystem.getMidiFileFormat(out).getType(), "应写成类型 1（多轨）");
        assertEquals(AudioExporter.PPQ, seq.getResolution(), "PPQ 必须是 480");

        int on = 0, off = 0, prog = 0, cc = 0;
        TreeSet<Integer> programs = new TreeSet<>();
        for (Track t : seq.getTracks()) {
            for (int i = 0; i < t.size(); i++) {
                if (!(t.get(i).getMessage() instanceof ShortMessage sm)) continue;
                switch (sm.getCommand()) {
                    case ShortMessage.NOTE_ON -> { if (sm.getData2() > 0) on++; else off++; }
                    case ShortMessage.NOTE_OFF -> off++;
                    case ShortMessage.PROGRAM_CHANGE -> { prog++; programs.add(sm.getData1()); }
                    case ShortMessage.CONTROL_CHANGE -> cc++;
                    default -> { }
                }
            }
        }
        assertEquals(score.size(), on, "NOTE_ON 数量应等于音符数");
        assertEquals(on, off, "每个 NOTE_ON 都必须有配对的 NOTE_OFF，否则会卡音");
        assertEquals(4, prog, "四个乐器各一次音色切换");
        assertTrue(programs.contains(AudioExporter.PROG_CELLO), "应包含大提琴音色 42");
        assertTrue(programs.contains(AudioExporter.PROG_STRINGS), "应包含弦乐音色 48");
        assertTrue(cc >= 16, "每个乐器轨至少要写音量/声像/混响/合唱，实测 " + cc + " 条");
    }

    @Test
    void 速度元数据与BPM一致() throws Exception {
        // 500000 微秒/四分音符 = 120 BPM。卡农是 56 BPM，反过来算一遍
        byte[] body = tempoBytesOf(AudioExporter.BPM);
        int us = ((body[0] & 0xFF) << 16) | ((body[1] & 0xFF) << 8) | (body[2] & 0xFF);
        double bpm = 60_000_000.0 / us;
        assertEquals(AudioExporter.BPM, bpm, 0.5, "速度元数据换算回来的 BPM 应等于设定值");
    }

    /** 借一次真实写出把速度元数据取出来，避免另写一份编码逻辑 */
    private byte[] tempoBytesOf(double bpm) throws Exception {
        Sequence seq = new Sequence(Sequence.PPQ, AudioExporter.PPQ);
        Track meta = seq.createTrack();
        meta.add(new javax.sound.midi.MidiEvent(
                new javax.sound.midi.MetaMessage(0x51, AudioExporter.tempoBytes(bpm), 3), 0));
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        MidiSystem.write(seq, 1, bos);
        byte[] all = bos.toByteArray();
        for (int i = 0; i < all.length - 3; i++) {
            if ((all[i] & 0xFF) == 0xFF && (all[i + 1] & 0xFF) == 0x51) {
                return new byte[]{all[i + 3], all[i + 4], all[i + 5]};
            }
        }
        throw new AssertionError("导出结果里找不到速度元数据 (FF 51)");
    }
}
