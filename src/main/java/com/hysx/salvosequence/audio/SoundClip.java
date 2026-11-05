package com.hysx.salvosequence.audio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * 一个已加载的音效：把 WAV 读成 PCM 并转成混音器统一的输出格式，
 * 播放时向 {@link Mixer} 注册一个声部。
 *
 * <p><b>为什么不用 {@code javafx.scene.media.AudioClip}</b>：它属于
 * {@code javafx.media} 模块。jlink 出来的精简运行时一旦带上这个模块，体积和
 * 原生库依赖都会上去，而且实测本机 JDK 的 {@code javafx.media} 缺
 * {@code glib-lite} 原生库，加载就抛 {@code UnsatisfiedLinkError}。
 * {@code javax.sound.sampled} 属于 {@code java.desktop}——游戏本来就要它
 * （JavaFX 图形栈依赖它），所以<b>音频变成零新增依赖</b>，分发版再也不用静音。</p>
 *
 * <p><b>音色对比</b>：{@code AudioClip} 内部是 DirectSound 播放，这里是走
 * {@code SourceDataLine}（本机自动选 DirectSound 后端）。差别主要在两处：
 * 起播延迟（这里是"下一个混音块"，约 20ms）和采样精度（这里是自己做的
 * 线性插值升降调，音质足够这种 8bit 风格的音效）。</p>
 *
 * <p>加载失败只记一次警告并返回 null，<b>绝不抛异常</b>：音效对游戏是锦上添花。</p>
 */
final class SoundClip {

    /** 拉伸采样率的上下限，和原来 AudioClip 的 0.5~2.0 保持一致。 */
    static final double MIN_RATE = 0.5;
    static final double MAX_RATE = 2.0;

    private final String key;
    private final String source;

    /**
     * 原始单声道 PCM（16bit 小端）。存单声道而不是已转好的立体声：
     * 立体声转换 + 音量缩放是"每帧一次乘法"的活，放在加载时做一次，
     * 别在混音线程里对每个声部重复做。
     */
    private final short[] mono;

    /** 预乘过基准音量的副本：rate=1.0（绝大多数调用）时直接用它，混音循环里零乘法。 */
    private final short[] preScaled;

    private final double baseVolume;
    private volatile boolean closed;

    /**
     * @param url        类路径资源
     * @param baseVolume 该音效的基准音量（{@link SoundId#baseVolume()}）
     * @throws IOException                  读不出来
     * @throws UnsupportedAudioFileException 不是能识别的 WAV
     */
    SoundClip(String key, URL url, double baseVolume)
            throws IOException, UnsupportedAudioFileException {
        this.key = key;
        this.source = url.toExternalForm();
        this.baseVolume = Math.max(0.0, Math.min(1.0, baseVolume));
        this.mono = decode(url);
        this.preScaled = new short[mono.length];
        for (int i = 0; i < mono.length; i++) {
            preScaled[i] = (short) (mono[i] * this.baseVolume);
        }
    }

    String key() {
        return key;
    }

    String source() {
        return source;
    }

    /** 时长（秒），自检和日志用。 */
    double durationSeconds() {
        return mono.length / (double) Mixer.FORMAT.getSampleRate();
    }

    int frames() {
        return mono.length;
    }

    /**
     * 幅度峰值（相对满量程 1.0）。
     *
     * <p>自检里最该看的一个数：峰值≈0 说明资源虽然读成功了但其实是静音的，
     * 这种"加载成功却没声音"只能靠量峰值发现。</p>
     */
    double peak() {
        int max = 0;
        for (short s : mono) {
            int v = Math.abs(s);
            if (v > max) {
                max = v;
            }
        }
        return max / (double) Short.MAX_VALUE;
    }

    /** 有效值（相对满量程 1.0），判断响度用。 */
    double rms() {
        double sum = 0;
        for (short s : mono) {
            sum += (double) s * s;
        }
        return Math.sqrt(sum / Math.max(1, mono.length)) / Short.MAX_VALUE;
    }

    /**
     * 播放一次。同一个音效连续调用会叠加（连射不会互相打断），
     * 这是当初选 AudioClip 而不是 MediaPlayer 的原因，这里由混音器保证。
     *
     * @param gain 额外增益（1.0 = 基准音量）
     * @param rate 播放速率（0.5~2.0，音调随之变化）
     */
    void play(double gain, double rate) {
        if (closed) {
            return;
        }
        double clampedRate = Math.max(MIN_RATE, Math.min(MAX_RATE, rate));
        double clampedGain = Math.max(0.0, Math.min(2.0, gain));
        Mixer mixer = Audio.mixer();
        if (clampedRate == 1.0) {
            mixer.register(new PreScaledVoice(preScaled));
        } else {
            mixer.register(new ResamplingVoice(mono, baseVolume, clampedGain, clampedRate));
        }
    }

    /**
     * 停止：把还在响的声部标记为结束。
     *
     * <p>声部是自己从混音器的列表里摘掉的，这里只做标记，避免在音频线程之外
     * 改动那个列表。</p>
     */
    void stop() {
        Audio.mixer().stopAll();
    }

    void close() {
        closed = true;
    }

    // ------------------------------------------------------------ 声部实现

    /** rate = 1.0 的快路径：直接喂预乘好的样本，混音循环里只有加法。 */
    private static final class PreScaledVoice implements Mixer.Voice {
        private final short[] data;
        private int pos;

        PreScaledVoice(short[] data) {
            this.data = data;
        }

        @Override
        public int render(short[] target, int offset, int frames,
                          double masterVolume, boolean advanceOnly) {
            int n = Math.min(frames, (data.length - pos));
            if (n <= 0) {
                return 0;
            }
            if (!advanceOnly) {
                for (int i = 0; i < n; i++) {
                    short s = data[pos + i];
                    int v = (int) (s * masterVolume);
                    int o = offset + i * 2;
                    target[o] = (short) v;
                    target[o + 1] = (short) v;
                }
            }
            pos += n;
            return n;
        }
    }

    /**
     * rate != 1.0：线性插值重采样。
     *
     * <p>用 16.16 定点而不是 double：混音线程每帧要跑几十万个样本，
     * 定点在热点循环里更省；插值误差对音效听感没有影响。</p>
     */
    private static final class ResamplingVoice implements Mixer.Voice {
        private final short[] data;
        private final double scale;
        private final int step;
        private int cursor;

        ResamplingVoice(short[] data, double baseVolume, double gain, double rate) {
            this.data = data;
            this.scale = baseVolume * gain;
            this.step = (int) Math.round(rate * 65536.0);
        }

        @Override
        public int render(short[] target, int offset, int frames,
                          double masterVolume, boolean advanceOnly) {
            double amp = scale * masterVolume;
            int produced = 0;
            for (int i = 0; i < frames; i++) {
                int index = cursor >>> 16;
                if (index >= data.length - 1) {
                    break;
                }
                short a = data[index];
                short b = data[index + 1];
                int frac = cursor & 0xFFFF;
                int sample = a + (int) (((b - a) * (long) frac) >> 16);
                if (!advanceOnly) {
                    int v = (int) (sample * amp);
                    if (v > Short.MAX_VALUE) {
                        v = Short.MAX_VALUE;
                    } else if (v < Short.MIN_VALUE) {
                        v = Short.MIN_VALUE;
                    }
                    int o = offset + i * 2;
                    target[o] = (short) v;
                    target[o + 1] = (short) v;
                }
                cursor += step;
                produced++;
            }
            return produced;
        }
    }

    // ------------------------------------------------------------ WAV 解码

    /**
     * 把任意（单/多声道、8/16bit、任意采样率）的音频读成单声道 16bit PCM。
     *
     * <p>重采样交给 Java Sound 的格式转换器（{@code getAudioInputStream(format, stream)}），
     * 这样素材规格变了也不用改代码。</p>
     */
    private static short[] decode(URL url) throws IOException, UnsupportedAudioFileException {
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(url)) {
            AudioFormat src = raw.getFormat();
            AudioFormat mono16 = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    Mixer.FORMAT.getSampleRate(),
                    16,
                    1,
                    2,
                    Mixer.FORMAT.getSampleRate(),
                    false);
            AudioInputStream converted = AudioSystem.isConversionSupported(mono16, src)
                    ? AudioSystem.getAudioInputStream(mono16, raw)
                    : raw;
            byte[] bytes = converted.readAllBytes();
            if (converted != raw) {
                converted.close();
            }
            short[] out = new short[bytes.length / 2];
            for (int i = 0; i < out.length; i++) {
                out[i] = (short) ((bytes[i * 2] & 0xFF) | (bytes[i * 2 + 1] << 8));
            }
            return out;
        }
    }

    /** 便于自检：把一段资源直接读成 PCM（不注册声部）。 */
    static short[] readPcm(URL url) throws IOException, UnsupportedAudioFileException {
        return decode(url);
    }

    /** 便于自检：从字节数组解码（离线核对用）。 */
    static short[] readPcm(byte[] bytes) throws IOException, UnsupportedAudioFileException {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(
                new ByteArrayInputStream(bytes))) {
            byte[] raw = in.readAllBytes();
            short[] out = new short[raw.length / 2];
            for (int i = 0; i < out.length; i++) {
                out[i] = (short) ((raw[i * 2] & 0xFF) | (raw[i * 2 + 1] << 8));
            }
            return out;
        }
    }
}
