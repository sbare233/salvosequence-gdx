package com.hysx.salvosequence.audio;

import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * 软件混音器：把同时响的所有音效混进一条 {@link SourceDataLine}。
 *
 * <p><b>为什么自己混音，而不是"一次播放开一条 line"</b>：实测每次
 * {@code AudioSystem.getSourceDataLine(fmt).open(fmt)} 要 20~60ms，而战斗里
 * 开火/命中是每帧都可能触发的——按次开 line 会带来肉眼可见的延迟，
 * 而且连射时几条 line 抢同一台设备很容易 {@link LineUnavailableException}。
 * 这里改成<b>一条常开的输出流 + 自己把多个"声部"混音</b>：触发延迟从
 * 几十毫秒降到"下一个混音块"（约 20ms），重叠数量也不受设备限制。</p>
 *
 * <p><b>固定输出格式</b>：44100Hz / 16bit / 立体声 / 小端。所有音源在加载时
 * 统一转换到这个格式，于是混音循环里没有任何格式分支（也就不用每次播放重采样）。</p>
 *
 * <p><b>没有声卡也不会崩</b>：开不了 line 就进入"永久静音"状态，
 * {@link #isBroken()} 返回原因，游戏照常跑——音效对游戏是锦上添花。</p>
 *
 * <p>线程模型：只有一个守护线程 {@code SoundMixer}。{@code register} 由游戏线程调用
 * （界面点击、开火），混音循环在自己的线程里跑，共享的只有那个 {@code voices} 列表，
 * 全程加同一把锁。</p>
 */
final class Mixer {

    /** 输出格式：所有音源都被转换到这里。 */
    static final AudioFormat FORMAT = new AudioFormat(44100f, 16, 2, true, false);

    /** 每个混音块的帧数。4096 帧 ≈ 93ms，line 缓冲区也按这个量级开。 */
    private static final int CHUNK_FRAMES = 4096;

    /** 同时最多几个声部。超过就丢掉最老的（连射时人耳分不出第 25 个子弹声）。 */
    private static final int MAX_VOICES = 24;

    /** 没有声部时线程的休眠间隔：决定"静默后第一次出声"的唤醒延迟。 */
    private static final long IDLE_SLEEP_MS = 25;

    /** 输出缓冲区（立体声 16bit 小端）。 */
    private final byte[] out = new byte[CHUNK_FRAMES * 4];

    /** 混音累加用的整型缓冲：先累加再限幅，避免每个声部各自截断产生爆音。 */
    private final int[] accum = new int[CHUNK_FRAMES * 2];

    private final List<Voice> voices = new ArrayList<>();

    private volatile double volume = 1.0;
    private volatile boolean muted;
    private volatile boolean running = true;
    private volatile String broken;

    private SourceDataLine line;

    /** 一个正在发声的声部：把某段 PCM 按音量/音速渲染到混音缓冲里。 */
    interface Voice {
        /**
         * 渲染最多 {@code frames} 个立体声帧到 {@code target}（交错 L,R，short）。
         *
         * @param offset      从 target 的第几个 short 开始写
         * @param masterVolume 主音量（0~1）；为 0 时按静音处理
         * @param advanceOnly 为 true 时只推进播放位置、不产生声音
         *                    （全局静音时用：不写数据，但节奏要对，否则取消静音会听到积压的声音）
         * @return 实际写入的帧数；返回 0 表示这个声部已经放完
         */
        int render(short[] target, int offset, int frames, double masterVolume, boolean advanceOnly);
    }

    synchronized void setVolume(double value) {
        volume = Math.max(0.0, Math.min(1.0, value));
    }

    synchronized double volume() {
        return volume;
    }

    synchronized void setMuted(boolean value) {
        muted = value;
    }

    synchronized boolean isMuted() {
        return muted;
    }

    /** 音频是否已确认不可用（没有声卡/设备被独占）；可用时为 null。 */
    synchronized String brokenReason() {
        return broken;
    }

    /** 立刻掐掉所有正在响的声部（停止/退出用）。 */
    synchronized void stopAll() {
        voices.clear();
        SourceDataLine l = line;
        if (l != null) {
            try {
                l.flush();
            } catch (Throwable ignored) {
                // 设备已经没了就算了
            }
        }
    }

    /**
     * 注册一个要发声的声部。出声的实际时机是"下一个混音块"。
     *
     * @param voice 声部；已放完的声部会被自动清理
     */
    synchronized void register(Voice voice) {
        if (broken != null) {
            return;
        }
        ensureThread();
        if (voices.size() >= MAX_VOICES) {
            voices.remove(0);
        }
        voices.add(voice);
    }

    void shutdown() {
        running = false;
        SourceDataLine l;
        synchronized (this) {
            voices.clear();
            l = line;
            line = null;
        }
        if (l != null) {
            closeQuietly(l);
        }
    }

    // ------------------------------------------------------------ 内部

    private Thread thread;

    private synchronized void ensureThread() {
        if (thread != null && thread.isAlive()) {
            return;
        }
        thread = new Thread(this::pump, "SoundMixer");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 混音主循环：永远只写"一个块"，靠 {@link SourceDataLine#write} 自己按播放速度
     * 阻塞来定节奏（实测写 12x4096 帧静音耗时 1038ms ≈ 理论值 1117ms）。
     *
     * <p>这也意味着<b>最后一块写返回时声音已经真的播出去了</b>，所以没有声部时
     * 可以放心关掉 line，不会截断尾巴。</p>
     */
    private void pump() {
        final short[] mix = new short[CHUNK_FRAMES * 2];
        while (running) {
            if (!renderChunk(mix)) {
                idleSleep();
            }
        }
        SourceDataLine l;
        synchronized (this) {
            l = line;
            line = null;
        }
        if (l != null) {
            closeQuietly(l);
        }
    }

    /**
     * @return 是否真的写了音频（没人发声时返回 false，调用方去休眠）
     */
    private boolean renderChunk(short[] mix) {
        final List<Voice> active;
        final double vol;
        final boolean silent;
        synchronized (this) {
            if (voices.isEmpty()) {
                return false;
            }
            active = new ArrayList<>(voices);
            vol = volume;
            silent = muted || vol <= 0.0;
        }

        java.util.Arrays.fill(accum, 0);
        boolean any = false;
        List<Voice> done = null;
        for (Voice v : active) {
            int frames = v.render(mix, 0, CHUNK_FRAMES, vol, silent);
            if (frames <= 0) {
                if (done == null) {
                    done = new ArrayList<>(2);
                }
                done.add(v);
                continue;
            }
            if (silent) {
                continue;
            }
            any = true;
            for (int i = 0; i < frames * 2; i++) {
                accum[i] += mix[i];
            }
        }
        if (done != null) {
            synchronized (this) {
                voices.removeAll(done);
            }
        }
        if (!any) {
            return false;
        }

        for (int i = 0; i < out.length; i += 2) {
            int s = accum[i / 2];
            if (s > Short.MAX_VALUE) {
                s = Short.MAX_VALUE;
            } else if (s < Short.MIN_VALUE) {
                s = Short.MIN_VALUE;
            }
            out[i] = (byte) s;
            out[i + 1] = (byte) (s >> 8);
        }

        SourceDataLine l = line();
        if (l == null) {
            return false;
        }
        try {
            l.write(out, 0, out.length);
            return true;
        } catch (Throwable ex) {
            disable("写入音频设备失败: " + ex);
            return false;
        }
    }

    /** 拿（必要时打开）输出流；开不了就永久静音并记原因。 */
    private SourceDataLine line() {
        synchronized (this) {
            if (broken != null) {
                return null;
            }
            if (line != null && line.isOpen()) {
                return line;
            }
        }
        SourceDataLine opened = null;
        try {
            opened = AudioSystem.getSourceDataLine(FORMAT);
            opened.open(FORMAT, out.length * 4);
            opened.start();
        } catch (LineUnavailableException | IllegalArgumentException ex) {
            disable(ex.toString());
            return null;
        } catch (Throwable ex) {
            disable(ex.toString());
            return null;
        }
        synchronized (this) {
            line = opened;
            return line;
        }
    }

    private void disable(String reason) {
        String first;
        synchronized (this) {
            if (broken == null) {
                broken = reason;
                voices.clear();
            }
            first = broken;
        }
        System.out.println("[Sound] 音频不可用，已切换为静音继续运行: " + first);
    }

    private static void closeQuietly(SourceDataLine l) {
        try {
            l.stop();
            l.flush();
            l.close();
        } catch (Throwable ignored) {
            // 关闭阶段不再报错
        }
    }

    private static void idleSleep() {
        try {
            Thread.sleep(IDLE_SLEEP_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 供 {@link SoundClip} 之外的地方（诊断）查询当前声部数。 */
    synchronized int activeVoices() {
        return voices.size();
    }

    /** 设备信息，自检用。 */
    static String describeDevices() {
        int count;
        String names;
        try {
            javax.sound.sampled.Mixer.Info[] infos = AudioSystem.getMixerInfo();
            count = infos.length;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(3, infos.length); i++) {
                if (i > 0) {
                    sb.append(" / ");
                }
                sb.append(infos[i].getName());
            }
            names = sb.toString();
        } catch (Throwable ex) {
            return "查询 mixer 失败: " + ex;
        }
        return count + " 个 mixer" + (names.isEmpty() ? "" : "（" + names + "…）");
    }
}
