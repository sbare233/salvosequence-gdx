package com.hysx.salvosequence.audio;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

/**
 * 配乐生成器：用代码写出 MIDI 序列，不依赖任何外部素材文件。
 *
 * <p><b>为什么是"生成"而不是"读 .mid 文件"</b>：曲子要和战况联动（速度、声部、
 * 音区都要能被 {@link Music#setIntensity} 实时改动），生成式让"主题"成为一份
 * 可以按参数重组的和声/旋律表——改一个数字就能试速度、试配器，不用回 DAW 重导。
 * 生成的曲子一共几 KB，比任何录音素材都便宜。</p>
 *
 * <p><b>配器思路</b>：全部选 GM 里的电子音色，因为它们恰好对 Java 自带的
 * Gervill 合成器友好——方波主音、锯齿贝斯、合成鼓听起来就是"那个年代的味道"，
 * 而钢琴/弦乐这类采样型音色在 Gervill 默认音色库里偏闷。</p>
 *
 * <p>类对外公开只是为了 {@code tools.MusicExport} 能写出 .mid 文件对拍；
 * 游戏运行时只经 {@link Music} 使用它。</p>
 */
public final class MusicRegistry {

    /** 主旋律：方波主音（GM 80 Lead 1 square）。 */
    static final int CH_LEAD = 0;
    /** 副旋律/铺底：暖垫（GM 89 Pad 2 warm）。 */
    static final int CH_PAD = 1;
    /** 中声部：合成弦乐（GM 51 SynthStrings 1），危机时才进来。 */
    static final int CH_STRING = 2;
    /** 贝斯：合成贝斯（GM 38 Synth Bass 1）。 */
    static final int CH_BASS = 3;
    /** 打击乐：GM 规定通道 9。 */
    static final int CH_DRUM = 9;

    private static final int PPQ = 480;
    private static final int BEAT = PPQ;
    private static final int BAR = 4 * BEAT;

    /**
     * 和声走向：两段各 4 小节，A 段陈述、B 段转开（Dm → Bb → E7 制造张力）。
     * 每小节 3 个音（根音、三音、五音），危机版只用后两小节。
     */
    private static final int[][] PROGRESSION = {
            {57, 60, 64},  // Am
            {53, 57, 60},  // F
            {48, 52, 55},  // C
            {55, 59, 62},  // G
            {50, 53, 57},  // Dm
            {57, 60, 64},  // Am
            {58, 62, 65},  // Bb
            {52, 56, 59},  // E
    };

    /** 旋律：每小节 4 个八分位置，0 = 休止。 */
    private static final int[][] MELODY = {
            {69, 0, 72, 76},
            {72, 0, 69, 65},
            {67, 0, 64, 60},
            {62, 0, 67, 71},
            {74, 0, 77, 74},
            {72, 0, 69, 72},
            {70, 0, 65, 70},
            {71, 0, 68, 64},
    };

    /** 各声部的基准音量。运行时的主音量按比例缩放这份基准，所以这里同时也是平衡表。 */
    static final int[] BASE_VOLUME = new int[16];

    static {
        for (int i = 0; i < BASE_VOLUME.length; i++) {
            BASE_VOLUME[i] = 100;
        }
        BASE_VOLUME[CH_LEAD] = 104;
        BASE_VOLUME[CH_PAD] = 44;
        BASE_VOLUME[CH_STRING] = 52;
        BASE_VOLUME[CH_BASS] = 82;
        BASE_VOLUME[CH_DRUM] = 74;
    }

    private MusicRegistry() {
    }

    /**
     * 生成一首曲子。
     *
     * <p>public 是为了让 {@code tools.MusicExport} 能把它写成 .mid 文件（只导出用，
     * 游戏本身走 {@link Music}）。类本身仍是包私有的，外部拿不到别的入口。</p>
     *
     * @param track 要哪一首（决定速度与编制）
     */
    public static Sequence compose(MusicTrack track) throws InvalidMidiDataException {
        Sequence seq = new Sequence(Sequence.PPQ, PPQ);
        Track t = seq.createTrack();

        int bars = track == MusicTrack.TITLE ? 4 : 8;
        setTempo(t, 0, track.bpm());

        program(t, CH_LEAD, 80);
        program(t, CH_PAD, 89);
        program(t, CH_STRING, 51);
        program(t, CH_BASS, 38);
        // 第 0 小节先写一遍各声部音量；标题曲没有中声部，压到 0
        int[] startVolume = BASE_VOLUME.clone();
        startVolume[CH_PAD] = track == MusicTrack.TITLE ? 70 : BASE_VOLUME[CH_PAD];
        startVolume[CH_STRING] = track == MusicTrack.CRISIS ? 74 : 0;
        writeVolumes(t, 0, startVolume);
        // 稍微加点混响感（GM 91 = Reverb Send），像素味但不干巴
        send(t, CH_LEAD, 91, 64);
        send(t, CH_PAD, 91, 90);
        send(t, CH_STRING, 91, 80);
        send(t, CH_BASS, 91, 20);

        boolean crisis = track == MusicTrack.CRISIS;
        for (int bar = 0; bar < bars; bar++) {
            int at = bar * BAR;
            int[] chord = PROGRESSION[bar % PROGRESSION.length];
            int[] melody = MELODY[bar % MELODY.length];

            if (track == MusicTrack.TITLE) {
                titleBar(t, at, chord, melody);
            } else {
                battleBar(t, at, chord, melody, crisis);
            }
            // 每小节重申一次声部音量：运行时的主音量下发到合成器通道上，序列这边
            // 隔一小节盖回来一次，两边就不会各说各话（见 Music.applyVolume 的说明）
            writeVolumes(t, at, BASE_VOLUME);
        }
        return seq;
    }

    /** 把 16 个通道的音量一次性写进序列。 */
    private static void writeVolumes(Track t, int tick, int[] volumes) throws InvalidMidiDataException {
        for (int ch = 0; ch < volumes.length; ch++) {
            volume(t, tick, ch, volumes[ch]);
        }
    }

    /** 标题曲：铺底长音 + 稀疏旋律，没有鼓。 */
    private static void titleBar(Track t, int at, int[] chord, int[] melody)
            throws InvalidMidiDataException {
        for (int n : chord) {
            note(t, at, BAR - 20, CH_PAD, n, 48);
        }
        note(t, at, BEAT, CH_BASS, chord[0] - 24, 58);
        note(t, at + 2 * BEAT, BEAT, CH_BASS, chord[0] - 12, 50);
        // 一拍一下，只取旋律里的三个音，留白比战斗版多得多
        note(t, at + BEAT, BEAT, CH_LEAD, melody[0], 62);
        note(t, at + 2 * BEAT, BEAT, CH_LEAD, melody[2], 58);
        note(t, at + 3 * BEAT, (int) (BEAT * 1.5), CH_LEAD, melody[3], 54);
    }

    /** 战斗曲：鼓 + 八分贝斯 + 主旋律 + 铺底；危机版更密。 */
    private static void battleBar(Track t, int at, int[] chord, int[] melody, boolean crisis)
            throws InvalidMidiDataException {
        int root = chord[0] - 24;

        // 贝斯：每拍两下（八分），危机版换根音八度跳
        for (int i = 0; i < 8; i++) {
            int pitch = crisis && i % 4 == 3 ? root + 12 : root;
            note(t, at + i * (BEAT / 2), BEAT / 2 + 20, CH_BASS, pitch, i % 2 == 0 ? 78 : 62);
        }

        // 鼓：底鼓四踩，反拍军鼓，八分闭镲
        for (int beat = 0; beat < 4; beat++) {
            int when = at + beat * BEAT;
            note(t, when, 60, CH_DRUM, 36, 86);
            if (beat % 2 == 1) {
                note(t, when, 60, CH_DRUM, 38, 74);
            }
            note(t, when, 40, CH_DRUM, 42, 54);
            note(t, when + BEAT / 2, 40, CH_DRUM, 42, 38);
            if (crisis && beat == 3) {
                note(t, when + 3 * BEAT / 4, 30, CH_DRUM, 49, 44); // 反拍镲片，危机版才有
            }
        }

        // 铺底：整小节长音
        for (int n : chord) {
            note(t, at, BAR - 20, CH_PAD, n, 40);
        }
        // 中声部：危机版加一条持续音，把密度抬高
        if (crisis) {
            note(t, at, BAR - 20, CH_STRING, chord[1], 56);
            note(t, at, BAR - 20, CH_STRING, chord[2] + 12, 48);
        }

        // 主旋律：每小节 4 个音，休止符（0）保持安静
        for (int i = 0; i < 4; i++) {
            int n = melody[i];
            if (n == 0) {
                continue;
            }
            note(t, at + i * BEAT, BEAT - 150, CH_LEAD, n, 86);
        }
    }

    // ------------------------------------------------------------ MIDI 工具

    private static void setTempo(Track t, int tick, int bpm) throws InvalidMidiDataException {
        int usPerQuarter = 60_000_000 / bpm;
        byte[] data = {(byte) (usPerQuarter >> 16), (byte) (usPerQuarter >> 8),
                (byte) usPerQuarter};
        t.add(new MidiEvent(new MetaMessage(0x51, data, 3), tick));
    }

    private static void program(Track t, int channel, int patch) throws InvalidMidiDataException {
        add(t, 0, ShortMessage.PROGRAM_CHANGE, channel, patch, 0);
    }

    private static void volume(Track t, int tick, int channel, int value)
            throws InvalidMidiDataException {
        add(t, tick, ShortMessage.CONTROL_CHANGE, channel, 7, value);
    }

    private static void send(Track t, int channel, int controller, int value)
            throws InvalidMidiDataException {
        add(t, 0, ShortMessage.CONTROL_CHANGE, channel, controller, value);
    }

    private static void add(Track t, int tick, int command, int channel, int d1, int d2)
            throws InvalidMidiDataException {
        t.add(new MidiEvent(new ShortMessage(command, channel, d1, d2), tick));
    }

    private static void note(Track t, int tick, int duration, int channel, int pitch, int velocity)
            throws InvalidMidiDataException {
        add(t, tick, ShortMessage.NOTE_ON, channel, pitch, velocity);
        add(t, tick + Math.max(1, duration), ShortMessage.NOTE_OFF, channel, pitch, 0);
    }
}
