package com.hysx.salvosequence.tools;

import javax.sound.midi.*;
import java.io.File;
import java.util.*;

/**
 * 把卡农排成曲子，同时导出 **标准 MIDI 文件**和参考 WAV。
 *
 * 为什么两种都导：
 *   - MIDI 问的是"换台机器能不能放、谱子对不对"——任何播放器、任何 DAW 都能开，
 *     组员双击就能听，也能拿去改。代价是音色由对方机器的音源决定，混响没了。
 *   - WAV 是我们自己渲染的（Gervill 取样 + 自写混响），代表这个项目最后会是什么声音。
 *     两份对着听，就能分清"曲子的问题"和"音源的问题"。
 *
 * 曲子：Pachelbel Canon in D —— 公有领域，怎么变调、怎么改配器都不涉及授权。
 * 结构：通奏低音（8 小节固定音型）× 循环，三把小提琴声部错开进入。
 */
public final class AudioExporter {

    static final double BPM = 56;
    static final int PPQ = 480;
    static final int LOOPS = 2;

    // ---- GM 音色 ----
    static final int PROG_PIANO = 0;
    static final int PROG_CELLO = 42;
    static final int PROG_STRINGS = 48;
    static final int PROG_PAD = 89;

    // ---- 卡农 in D 的和声骨架 ----
    /** 通奏低音：每小节 2 个音（各 2 拍）。D A B F# G D G A */
    static final int[] BASS = {38, 33, 35, 30, 31, 26, 31, 33};
    /** 每小节和弦根音（铺底用） */
    static final int[] ROOT = {50, 45, 47, 42, 43, 38, 43, 45};

    /** 第一小提琴主题：{八分音符计数, 音高, 时值(八分)} */
    static final int[][] V1 = {
            {0, 74, 4}, {4, 73, 4}, {8, 71, 4}, {12, 73, 4},
            {16, 74, 4}, {20, 74, 4}, {24, 76, 4}, {28, 74, 4},
            {32, 74, 4}, {36, 74, 4}, {40, 76, 4}, {44, 76, 4},
            {48, 78, 4}, {52, 76, 4}, {56, 74, 4}, {60, 74, 4},
            {64, 74, 4}, {68, 76, 4}, {72, 78, 4}, {76, 79, 4},
            {80, 78, 4}, {84, 76, 4}, {88, 74, 4}, {92, 73, 4},
            {96, 74, 4}, {100, 76, 4}, {104, 74, 4}, {108, 73, 4},
            {112, 74, 4}, {116, 73, 4}, {120, 76, 4}, {124, 74, 4},
    };
    /** 第二小提琴：晚 2 小节进入，低八度 */
    static final int[][] V2 = {
            {16, 66, 4}, {20, 69, 4}, {24, 71, 4}, {28, 69, 4},
            {32, 69, 4}, {36, 69, 4}, {40, 71, 4}, {44, 71, 4},
            {48, 71, 4}, {52, 71, 4}, {56, 69, 4}, {60, 69, 4},
            {64, 69, 4}, {68, 71, 4}, {72, 73, 4}, {76, 74, 4},
            {80, 71, 4}, {84, 71, 4}, {88, 69, 4}, {92, 69, 4},
            {96, 69, 4}, {100, 71, 4}, {104, 69, 4}, {108, 69, 4},
            {112, 67, 4}, {116, 69, 4}, {120, 71, 4}, {124, 71, 4},
    };
    /** 第三声部：八分音符流动音型 */
    static final int[][] V3 = {
            {32, 62, 2}, {34, 64, 2}, {36, 66, 2}, {38, 64, 2},
            {40, 62, 2}, {42, 61, 2}, {44, 62, 2}, {46, 64, 2},
            {48, 66, 2}, {50, 64, 2}, {52, 62, 2}, {54, 59, 2},
            {56, 61, 2}, {58, 62, 2}, {60, 64, 2}, {62, 66, 2},
            {64, 64, 2}, {66, 62, 2}, {68, 61, 2}, {70, 59, 2},
            {72, 61, 2}, {74, 62, 2}, {76, 64, 2}, {78, 62, 2},
            {80, 61, 2}, {82, 62, 2}, {84, 64, 2}, {86, 66, 2},
            {88, 67, 2}, {90, 66, 2}, {92, 64, 2}, {94, 62, 2},
    };

    /** 一个音符事件。beat 以四分音符为单位；duration 同理。 */
    public record Note(double beat, int program, int pitch, int velocity, double duration, double pan) {}

    public static void main(String[] args) throws Exception {
        // 参数：可选输出目录；加 --wav 才渲染参考 WAV。
        // 默认只导 MIDI：渲染那条路要开 Gervill，而默认合成器在某些环境会去摸音频设备，
        // 能不开就不开（导谱子的时候没必要占声卡）。
        String dir = "target/music";
        boolean withWav = false;
        for (String a : args) {
            if (a.equals("--wav")) withWav = true;
            else dir = a;
        }
        File outDir = new File(dir);
        outDir.mkdirs();

        List<Note> score = arrange();
        double lastBeat = 0;
        for (Note n : score) lastBeat = Math.max(lastBeat, n.beat + n.duration);

        System.out.println("[export] 事件 " + score.size() + " 个 / " + lastBeat + " 拍 / "
                + String.format("%.1f", lastBeat * 60 / BPM) + " 秒");

        File mid = new File(outDir, "canon-in-d.mid");
        int bytes = writeMidi(mid, score, lastBeat);
        System.out.println("[export] MIDI  " + mid + "  " + bytes + " 字节");

        if (withWav) {
            File wav = new File(outDir, "canon-in-d.wav");
            writeWav(wav, render(score, lastBeat, 0.36));
            System.out.println("[export] WAV   " + wav + "  " + wav.length() / 1048576 + " MB");
        } else {
            System.out.println("[export] （要一起渲染参考 WAV 就加 --wav）");
        }

        System.exit(0);
    }

    // ==================== 排谱 ====================

    static List<Note> arrange() {
        List<Note> out = new ArrayList<>();
        for (int loop = 0; loop < LOOPS; loop++) {
            double base = loop * 32;          // 一遍 = 8 小节 × 4 拍
            for (int bar = 0; bar < 8; bar++) {
                double bt = base + bar * 4;
                int root = ROOT[bar];

                // 通奏低音：两分音符 x2
                out.add(new Note(bt, PROG_CELLO, BASS[bar], 78, 1.9, -0.12));
                out.add(new Note(bt + 2, PROG_CELLO, BASS[(bar + 4) % 8], 68, 1.9, -0.12));

                // 铺底：三度和弦长音
                out.add(new Note(bt, PROG_PAD, root, 48, 3.9, -0.30));
                out.add(new Note(bt, PROG_PAD, root + 7, 40, 3.9, 0.30));
                out.add(new Note(bt, PROG_PAD, root + 12, 36, 3.9, 0.0));

                // 钢琴：每拍一个分解和弦音
                int[] arp = {root, root + 7, root + 12, root + 7};
                for (int b = 0; b < 4; b++) {
                    out.add(new Note(bt + b, PROG_PIANO, arp[b] + 12, 52, 0.9, b % 2 == 0 ? -0.18 : 0.18));
                }
            }
            // 三把小提琴声部（数据是"整首"的拍号，按本遍窗口截取）
            addVoice(out, V1, base, 0, 92);
            if (loop >= 1) {
                addVoice(out, V2, base, 0, 78);
                addVoice(out, V3, base, 0, 66);
            }
        }
        out.sort(Comparator.comparingDouble(Note::beat));
        return out;
    }

    static void addVoice(List<Note> out, int[][] voice, double base, double from, int velocity) {
        for (int[] n : voice) {
            double beat = n[0] * 0.5;
            double dur = n[2] * 0.5;
            if (beat < from || beat >= from + 32) continue;
            double pan = (beat % 8) < 4 ? -0.22 : 0.22;
            out.add(new Note(base + beat, PROG_STRINGS, n[1], velocity, Math.max(0.4, dur * 0.95), pan));
        }
    }

    // ==================== 写 MIDI ====================

    /**
     * 类型 1 多轨：0 号轨放曲名/速度/拍号，其余按乐器分轨。
     * 每轨开头补 PROGRAM_CHANGE + 音量(CC7) + 声像(CC10)。
     */
    static int writeMidi(File file, List<Note> score, double lastBeat) throws Exception {
        Sequence seq = new Sequence(Sequence.PPQ, PPQ);

        Track meta = seq.createTrack();
        meta.add(new MidiEvent(new MetaMessage(0x03, "Canon in D (J. Pachelbel)".getBytes("UTF-8"), 3), 0));
        meta.add(new MidiEvent(new MetaMessage(0x01, "arranged for salvosequence".getBytes("UTF-8"), 1), 0));
        meta.add(new MidiEvent(new MetaMessage(0x51, tempoBytes(BPM), 3), 0));
        meta.add(new MidiEvent(new MetaMessage(0x58, new byte[]{4, 2, 24, 8}, 4), 0));
        meta.add(new MidiEvent(new MetaMessage(0x2F, new byte[0], 0), tick(lastBeat + 2)));

        Map<Integer, Track> byProgram = new TreeMap<>();
        for (Note n : score) {
            byProgram.computeIfAbsent(n.program(), p -> {
                Track t = seq.createTrack();
                t.add(new MidiEvent(shortMsg(ShortMessage.PROGRAM_CHANGE, 0, p, 0), 0));
                t.add(new MidiEvent(shortMsg(ShortMessage.CONTROL_CHANGE, 0, 7, 100), 0));    // 通道音量
                t.add(new MidiEvent(shortMsg(ShortMessage.CONTROL_CHANGE, 0, 10, 64), 0));    // 声像居中
                t.add(new MidiEvent(shortMsg(ShortMessage.CONTROL_CHANGE, 0, 91, 64), 0));    // 混响深度
                t.add(new MidiEvent(shortMsg(ShortMessage.CONTROL_CHANGE, 0, 93, 24), 0));    // 轻微合唱
                return t;
            });
        }
        for (Map.Entry<Integer, Track> e : byProgram.entrySet()) {
            e.getValue().add(new MidiEvent(endOfTrack(), tick(lastBeat + 2)));
        }

        for (Note n : score) {
            Track t = byProgram.get(n.program());
            int on = tick(n.beat);
            int off = tick(n.beat + n.duration);
            if (off <= on) off = on + 12;
            t.add(new MidiEvent(shortMsg(ShortMessage.NOTE_ON, 0, n.pitch(), n.velocity()), on));
            t.add(new MidiEvent(shortMsg(ShortMessage.NOTE_OFF, 0, n.pitch(), 0), off));
        }

        int type = 1;
        if (!MidiSystem.isFileTypeSupported(type, seq)) type = 0;
        return MidiSystem.write(seq, type, file);
    }

    static int tick(double beat) { return (int) Math.round(beat * PPQ); }

    /**
     * 造一条短消息。ShortMessage 的构造器声明的受检异常来自"消息类型非法"，
     * 这里所有调用点都是硬编码的合法类型，所以直接包成非受检异常，
     * 免得每个调用点都写 try（addVoice 这类静态辅助方法里没法抛受检异常）。
     */
    static MidiMessage shortMsg(int cmd, int ch, int d1, int d2) {
        try {
            return new ShortMessage(cmd, ch, d1, d2);
        } catch (InvalidMidiDataException e) {
            throw new IllegalStateException("非法 MIDI 消息: cmd=" + cmd + " ch=" + ch, e);
        }
    }

    static MidiMessage endOfTrack() {
        try {
            return new MetaMessage(0x2F, new byte[0], 0);
        } catch (InvalidMidiDataException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 速度元数据（FF 51 03）：三个字节，单位是"微秒每四分音符" */
    public static byte[] tempoBytes(double bpm) {
        int us = (int) Math.round(60_000_000.0 / bpm);
        return new byte[]{(byte) (us >> 16), (byte) (us >> 8), (byte) us};
    }

    // ==================== 渲染 WAV（Gervill 取样 + 自写混响）====================

    static Synthesizer synth;
    static Sequencer sequencer;
    static javax.sound.sampled.AudioInputStream render;
    static final byte[] rbuf = new byte[1 << 17];
    static int rpos, rlen;
    static final float RATE = 44100f;
    static final Map<Long, float[]> BANK = new HashMap<>();
    static final int[][] INSTS = {{PROG_CELLO, 36, 60}, {PROG_STRINGS, 55, 84},
            {PROG_PIANO, 48, 84}, {PROG_PAD, 48, 84}};
    static final int STEP = 6;

    static float[] render(List<Note> score, double lastBeat, double reverbMix) throws Exception {
        openSynth();
        buildBank();
        int total = (int) (lastBeat * (60.0 / BPM) * RATE) + (int) (RATE * 3);
        float[] dry = new float[total], wet = new float[total];
        Reverb rv = new Reverb();
        float[] out = new float[total];
        float peak = 0;
        for (Note n : score) {
            long key = bankKey(n.program(), n.pitch());
            float[] smp = BANK.get(key);
            int srcNote = bankNote(n.program(), n.pitch());
            if (smp == null) continue;
            double step = Math.pow(2.0, (n.pitch() - srcNote) / 12.0);
            int start = (int) (n.beat() * (60.0 / BPM) * RATE);
            if (start >= total) continue;
            double g = n.velocity() / 127.0;
            double gl = Math.sqrt((1 - n.pan()) / 2) * g;
            double gr = Math.sqrt((1 + n.pan()) / 2) * g;
            double pos = 0;
            for (int i = start; i < total && pos < smp.length - 1; i++) {
                int i0 = (int) pos;
                float v = (float) (smp[i0] * (1 - (pos - i0)) + smp[i0 + 1] * (pos - i0));
                dry[i] += (float) (v * gl);
                wet[i] += (float) (v * (gl + gr) * 0.5);
                pos += step;
            }
        }
        for (int i = 0; i < total; i++) {
            out[i] = dry[i] * 0.9f + rv.process(wet[i]) * (float) reverbMix;
            peak = Math.max(peak, Math.abs(out[i]));
        }
        if (peak > 0.88f || peak < 0.6f) {
            float g = (peak > 0.88f ? 0.88f : 0.85f) / Math.max(peak, 1e-6f);
            for (int i = 0; i < total; i++) out[i] *= g;
            System.out.printf("[render] 增益 x%.2f%n", g);
        }
        synth.close();
        System.out.printf("[render] %d 个音符 / %.1f 秒 / 峰值 %.3f%n", score.size(), total / RATE, peak);
        return out;
    }

    static void openSynth() throws Exception {
        synth = MidiSystem.getSynthesizer();
        java.lang.reflect.Method m = synth.getClass().getMethod("openStream",
                javax.sound.sampled.AudioFormat.class, Map.class);
        m.setAccessible(true);
        render = (javax.sound.sampled.AudioInputStream) m.invoke(synth,
                new javax.sound.sampled.AudioFormat(RATE, 16, 2, true, false),
                new HashMap<String, Object>());
        sequencer = MidiSystem.getSequencer(false);
        sequencer.getTransmitter().setReceiver(synth.getReceiver());
        sequencer.open();
    }

    static void buildBank() throws Exception {
        long t0 = System.currentTimeMillis();
        for (int[] in : INSTS) {
            for (int n = in[1]; n <= in[2]; n += STEP) {
                BANK.put((((long) in[0]) << 8) | n, trim(renderNote(in[0], n)));
            }
        }
        long b = 0;
        for (float[] s : BANK.values()) b += s.length * 2L;
        System.out.printf("[bank] %d 个样本 / %.2f MB / %.2fs%n", BANK.size(), b / 1048576.0,
                (System.currentTimeMillis() - t0) / 1000.0);
    }

    static long bankKey(int program, int pitch) { return (((long) program) << 8) | bankNote(program, pitch); }

    static int bankNote(int program, int pitch) {
        int lo = 60, hi = 60;
        for (int[] in : INSTS) if (in[0] == program) { lo = in[1]; hi = in[2]; }
        int best = lo;
        for (int n = lo; n <= hi; n += STEP) if (Math.abs(n - pitch) < Math.abs(best - pitch)) best = n;
        return Math.max(lo, Math.min(hi, best));
    }

    static float[] renderNote(int program, int note) throws Exception {
        Sequence seq = new Sequence(Sequence.PPQ, 480);
        Track t = seq.createTrack();
        t.add(new MidiEvent(shortMsg(ShortMessage.PROGRAM_CHANGE, 0, program, 0), 0));
        t.add(new MidiEvent(shortMsg(ShortMessage.NOTE_ON, 0, note, 100), 0));
        rpos = rlen = 0;
        sequencer.setSequence(seq);
        sequencer.setTempoInBPM(120);
        sequencer.start();
        int n = (int) (RATE * 2.6);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = nextSample();
        sequencer.stop();
        synth.getReceiver().send(shortMsg(ShortMessage.CONTROL_CHANGE, 0, 123, 0), -1);
        rpos = rlen = 0;
        return out;
    }

    static float nextSample() throws Exception {
        if (rpos + 4 > rlen) {
            rlen = render.read(rbuf, 0, rbuf.length);
            rpos = 0;
            if (rlen <= 0) return 0f;
        }
        int lo = rbuf[rpos] & 0xFF, hi = rbuf[rpos + 1];
        int lo2 = rbuf[rpos + 2] & 0xFF, hi2 = rbuf[rpos + 3];
        rpos += 4;
        return (float) (((short) ((hi << 8) | lo) + (short) ((hi2 << 8) | lo2)) / 65536.0);
    }

    static float[] trim(float[] src) {
        int win = (int) (RATE * 0.01), last = win * 6;
        for (int i = src.length - win; i >= 0; i -= win) {
            double e = 0;
            for (int j = i; j < i + win; j++) e += src[j] * src[j];
            if (Math.sqrt(e / win) > 0.0035) { last = Math.min(src.length, i + win); break; }
        }
        last = Math.min(src.length, Math.max(last, win * 6));
        int fade = (int) (RATE * 0.03);
        for (int i = 0; i < fade && last - 1 - i >= 0; i++) src[last - 1 - i] *= i / (float) fade;
        return Arrays.copyOf(src, last);
    }

    /** 简化 Freeverb */
    static class Reverb {
        final float[][] comb = new float[6][];
        final int[] idx = new int[6];
        final int[] len = {1557, 1617, 1491, 1422, 1277, 1356};
        final float[][] ap = new float[3][];
        final int[] apIdx = new int[3];
        final int[] apLen = {225, 556, 441};
        final float fb = 0.84f, damp = 0.28f;
        final float[] store = new float[6];

        Reverb() {
            for (int i = 0; i < comb.length; i++) comb[i] = new float[len[i]];
            for (int i = 0; i < ap.length; i++) ap[i] = new float[apLen[i]];
        }

        float process(float in) {
            float acc = 0;
            for (int i = 0; i < comb.length; i++) {
                float[] b = comb[i];
                int p = idx[i];
                float v = b[p];
                store[i] = v * (1 - damp) + store[i] * damp;
                acc += v;
                b[p] = in + store[i] * fb;
                idx[i] = (p + 1) % b.length;
            }
            float x = acc / comb.length;
            for (int i = 0; i < ap.length; i++) {
                float[] b = ap[i];
                int p = apIdx[i];
                float v = b[p];
                b[p] = x + v * 0.5f;
                apIdx[i] = (p + 1) % b.length;
                x = v - x;
            }
            return x;
        }
    }

    static void writeWav(File f, float[] mono) throws Exception {
        byte[] data = new byte[mono.length * 2];
        for (int i = 0; i < mono.length; i++) {
            int s = (int) (Math.max(-1, Math.min(1, mono[i])) * 32767);
            data[i * 2] = (byte) (s & 0xFF);
            data[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        try (java.io.DataOutputStream o = new java.io.DataOutputStream(new java.io.FileOutputStream(f))) {
            o.writeBytes("RIFF"); le32(o, 36 + data.length); o.writeBytes("WAVE");
            o.writeBytes("fmt "); le32(o, 16); le16(o, 1); le16(o, 1);
            le32(o, (int) RATE); le32(o, (int) (RATE * 2)); le16(o, 2); le16(o, 16);
            o.writeBytes("data"); le32(o, data.length); o.write(data);
        }
    }

    static void le32(java.io.DataOutputStream o, int v) throws Exception {
        o.write(v & 0xFF); o.write((v >> 8) & 0xFF); o.write((v >> 16) & 0xFF); o.write((v >> 24) & 0xFF);
    }

    static void le16(java.io.DataOutputStream o, int v) throws Exception {
        o.write(v & 0xFF); o.write((v >> 8) & 0xFF);
    }
}
