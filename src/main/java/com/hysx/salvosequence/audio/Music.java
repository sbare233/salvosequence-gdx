package com.hysx.salvosequence.audio;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;

/**
 * 配乐引擎：用 Java 自带的 MIDI 合成器播放背景音乐。
 *
 * <pre>
 * Music.play(MusicTrack.BATTLE);        // 进战斗，放战斗曲（循环）
 * Music.setIntensity(0.8);              // 血少了，变急
 * Music.setLevel(0.5);                  // 整体音量
 * Music.stop();
 * </pre>
 *
 * <h2>为什么是 MIDI 而不是音频文件</h2>
 * <ul>
 *   <li><b>体积</b>：一首曲子生成出来几 KB，而同样长度的 OGG/WAV 是几 MB —— 分发
 *       包里最贵的资源就是音频，MIDI 直接把它抹掉。</li>
 *   <li><b>能实时改</b>：战况要驱动音乐（速度、声部、音区），改音频只能换文件重放，
 *       而 MIDI 就是"乐谱 + 合成器参数"，改一个控制器消息立即生效。</li>
 *   <li><b>不依赖素材</b>：不用到处找免费曲子、不用担心授权，也不用把二进制塞进仓库。</li>
 * </ul>
 *
 * <h2>依赖与降级</h2>
 * <p>走 {@code javax.sound.midi}（JDK 自带，模块 {@code java.desktop}），
 * {@code jlink} 出来的精简运行时里能用，不需要 javafx.media —— 这正是当初
 * 音效换掉 {@code AudioClip} 的原因，音乐沿用同一条思路。</p>
 *
 * <p>任何一步失败（没有合成器、设备被占、曲子生成异常）都只记一次警告并永久
 * 静音，<b>绝不抛异常打断游戏</b>。{@link #available()} 与
 * {@link #failureReason()} 可以把"没配乐"和"游戏坏了"分开。</p>
 */
public final class Music {

    /** 曲子的加载状态：能拿到 Sequence 就说明生成/读取没问题。 */
    private static final Map<MusicTrack, Sequence> SEQUENCES = new EnumMap<>(MusicTrack.class);

    /** 单个 .mid 的载入上限：正常曲子几十 KB，超过这个数基本是路径或素材搞错了。 */
    private static final long MAX_MIDI_BYTES = 8L * 1024 * 1024;

    private static Sequencer sequencer;
    private static Synthesizer synthesizer;
    private static boolean initialized;
    private static boolean available;
    private static String failureReason = "";
    private static boolean quiet;

    private static MusicTrack current;
    /** 有曲子正在放（外部曲子的 current 是 null，所以不能只看 current）。 */
    private static boolean playingSomething;
    private static double level = 0.55;
    private static double intensity = 0.35;
    private static boolean muted;
    private static ScheduledExecutorService fadeExecutor;

    private Music() {
    }

    // ------------------------------------------------------------ 生命周期

    /** 惰性初始化：第一次要放曲子时才开合成器，没音乐需求就不占设备。 */
    private static synchronized void ensureOpen() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            sequencer = MidiSystem.getSequencer(false);
            if (sequencer == null) {
                fail("这个运行时没有 MIDI 音序器");
                return;
            }
            synthesizer = MidiSystem.getSynthesizer();
            if (synthesizer == null) {
                fail("这个运行时没有 MIDI 合成器");
                return;
            }
            synthesizer.open();
            if (synthesizer.getChannels() == null || synthesizer.getChannels().length < 10) {
                fail("合成器通道不足（拿不到 10 个通道）");
                return;
            }
            sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());
            sequencer.open();
            if (sequencer instanceof Synthesizer) {
                // 有些实现把两者合一：这时上面拿到的就是同一个对象，别再关它
                synthesizer = (Synthesizer) sequencer;
            }
            available = true;
            applyVolume();
            applyTempo();
            if (!quiet) {
                System.out.println("[music] MIDI 合成器就绪: "
                        + synthesizer.getDeviceInfo().getName());
            }
        } catch (MidiUnavailableException | RuntimeException e) {
            fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void fail(String reason) {
        available = false;
        failureReason = reason;
        if (sequencer != null) {
            try {
                sequencer.close();
            } catch (RuntimeException ignored) {
                // 关不掉就算了，反正已经判定不可用
            }
            sequencer = null;
        }
        if (!quiet) {
            System.out.println("[music] 配乐不可用（" + reason + "），已静音继续运行");
        }
    }

    /** 配乐是不是真的能出声。 */
    public static synchronized boolean available() {
        ensureOpen();
        return available;
    }

    /** 不可用的原因，可用时是空串。 */
    public static synchronized String failureReason() {
        ensureOpen();
        return failureReason;
    }

    /** 关掉合成器，退出游戏时调用。可以重复调用。 */
    public static synchronized void shutdown() {
        if (sequencer != null) {
            try {
                if (sequencer.isRunning()) {
                    sequencer.stop();
                }
                sequencer.close();
            } catch (RuntimeException ignored) {
                // 退出路径上不再纠缠
            }
            sequencer = null;
        }
        if (synthesizer != null) {
            try {
                synthesizer.close();
            } catch (RuntimeException ignored) {
                // 同上
            }
            synthesizer = null;
        }
        if (fadeExecutor != null) {
            fadeExecutor.shutdownNow();
            fadeExecutor = null;
        }
        current = null;
        playingSomething = false;
        available = false;
        initialized = false;
    }

    // ------------------------------------------------------------ 播放控制

    /**
     * 播放一首曲子（默认循环）。
     *
     * <p>已经在放同一首就什么都不做 —— 每帧调用 {@code play(BATTLE)} 是安全的，
     * 这比"记住自己放没放过"更不容易错。</p>
     */
    public static void play(MusicTrack track) {
        play(track, true);
    }

    /** 播放一首曲子；{@code loop=false} 时放完自己停下（{@link #isPlaying()} 变 false）。 */
    public static synchronized void play(MusicTrack requested, boolean loop) {
        if (requested == null) {
            return;
        }
        // 试听开关可能会改写曲目，所以之后一律用 track，不再看 requested
        MusicTrack track = override(requested);
        ensureOpen();
        if (!available) {
            return;
        }
        if (track == current && sequencer != null && sequencer.isRunning()) {
            return;
        }
        startSequence(sequence(track), track, loop);
    }

    /**
     * 把一首已经拿到手的 Sequence 交给音序器播放。
     *
     * @param seq   曲子；null 直接返回 false
     * @param track 记进 {@link #currentTrack()} 的身份，外部曲子传 null
     */
    private static synchronized boolean startSequence(Sequence seq, MusicTrack track, boolean loop) {
        if (seq == null || sequencer == null) {
            return false;
        }
        try {
            // 换曲时先让音效停手：MIDI 合成器和音效各自占音频设备，短促的音效
            // 让路能明显减少"设备忙"这类偶发失败。
            Sound.stopAll();
            sequencer.stop();
            sequencer.setSequence(seq);
            sequencer.setTickPosition(0);
            sequencer.setLoopCount(loop ? Sequencer.LOOP_CONTINUOUSLY : 1);
            applyTempo();
            applyVolume();
            sequencer.start();
            current = track;
            playingSomething = true;
            return true;
        } catch (InvalidMidiDataException | RuntimeException e) {
            fail(e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    /** 停下当前曲子（保留播放位置，方便 {@link #resume()}）。 */
    public static synchronized void pause() {
        if (sequencer != null && sequencer.isRunning()) {
            sequencer.stop();
        }
    }

    /** 从暂停处继续。没在放任何曲子时是空操作。 */
    public static synchronized void resume() {
        if (sequencer != null && current != null && !sequencer.isRunning()) {
            sequencer.start();
        }
    }

    /**
     * 播放类路径上的任意一首曲子（给外部素材和自检工具用）。
     *
     * <p>和 {@link #play(MusicTrack)} 的区别只在于曲子从哪来：那条路会去看
     * {@code /music/<file>.mid}，找不到就退回程序生成的曲子；这条路只认给定
     * 路径，读不到就返回 false，不生成替代品。</p>
     *
     * <p><b>注意</b>：外部曲子的配器平衡是原作者定的（各通道自己的 CC7），
     * 而 {@link #applyVolume()} 会把 {@link MusicRegistry#BASE_VOLUME} 那份
     * 基准盖上去 —— 所以放外部曲子时整体音量照样受 {@link #setLevel} 控制，
     * 只是各声部的相对音量会按我们的配器表走。</p>
     *
     * @param resourcePath 类路径绝对路径，例如 {@code /music/xxx.mid}
     * @param loop         true = 循环，false = 放完自己停
     * @return 真的开始放了才返回 true
     */
    public static synchronized boolean playPath(String resourcePath, boolean loop) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return false;
        }
        Sequence seq = load(resourcePath);
        if (seq == null) {
            return false;
        }
        return startSequence(seq, null, loop);
    }

    /**
     * 只把曲子读出来做体检，不播放。
     *
     * @return 读不出来（文件缺失、格式不认识、没有音序器）时返回 null
     */
    public static synchronized Sequence load(String resourcePath) {
        ensureOpen();
        if (!available) {
            return null;
        }
        return readResource(resourcePath);
    }

    /**
     * 播放磁盘上的任意一首曲子（试听、以后做选曲界面都用得上）。
     *
     * <p>和 {@link #playPath} 的区别只是曲子从哪来：这个走文件系统，那个走类路径。
     * 两条路都落到同一个 {@code startSequence}，所以音量/强度/循环的行为完全一致。</p>
     *
     * @param file .mid/.rmi 文件
     * @param loop true = 循环，false = 放完自己停
     * @return 真的开始放了才返回 true
     */
    public static synchronized boolean playFile(File file, boolean loop) {
        Sequence seq = loadFile(file);
        return seq != null && startSequence(seq, null, loop);
    }

    /**
     * 从磁盘读一首曲子做体检，不播放。
     *
     * @return 读不出来（文件不在、格式不认识、没有音序器）时返回 null
     */
    public static synchronized Sequence loadFile(File file) {
        ensureOpen();
        if (!available || file == null) {
            return null;
        }
        if (!file.isFile()) {
            if (!quiet) {
                System.out.println("[music] 找不到文件 " + file);
            }
            return null;
        }
        if (file.length() > MAX_MIDI_BYTES) {
            if (!quiet) {
                System.out.println("[music] " + file.getName() + " 太大（"
                        + (file.length() / 1024) + " KB），拒绝载入");
            }
            return null;
        }
        try {
            Sequence seq = MidiSystem.getSequence(file);
            if (!quiet) {
                System.out.println("[music] 载入 " + file.getName());
            }
            return seq;
        } catch (IOException | InvalidMidiDataException | RuntimeException e) {
            if (!quiet) {
                System.out.println("[music] " + file.getName() + " 读取失败（" + e.getMessage() + "）");
            }
            return null;
        }
    }

    /**
     * 当前曲子总时长（秒）；没在放曲子或拿不到长度时返回 0。
     *
     * <p>不依赖具体放的是哪一首，所以外部曲子和生成曲子都适用。</p>
     */
    public static synchronized double currentLengthSeconds() {
        if (sequencer == null || !playingSomething) {
            return 0;
        }
        try {
            return sequencer.getMicrosecondLength() / 1_000_000.0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /**
     * 当前播放位置（秒）；没在放曲子时返回 0。
     *
     * <p>给自检工具用：光看 {@link #isPlaying()} 分不清"在放但没声"和"根本没动"，
     * 位置一直在涨才说明音序器真的在跑。</p>
     */
    public static synchronized double positionSeconds() {
        if (sequencer == null || !playingSomething) {
            return 0;
        }
        try {
            return sequencer.getMicrosecondPosition() / 1_000_000.0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** 停止并回到曲子开头。 */
    public static synchronized void stop() {
        if (sequencer != null) {
            sequencer.stop();
            try {
                sequencer.setTickPosition(0);
            } catch (RuntimeException ignored) {
                // 没有合法 tick 位置时忽略
            }
        }
        current = null;
        playingSomething = false;
    }

    /** 正在放哪一首，没放就是 null。 */
    public static synchronized MusicTrack currentTrack() {
        return current;
    }

    /** 是不是正在出声。 */
    public static synchronized boolean isPlaying() {
        return sequencer != null && sequencer.isRunning() && playingSomething;
    }

    // ------------------------------------------------------------ 音量与强度

    /** 整体音量 0..1（相对值，不是分贝）。 */
    public static synchronized void setLevel(double value) {
        level = clamp(value, 0, 1);
        applyVolume();
    }

    public static synchronized double level() {
        return level;
    }

    /** 直接整体静音，保留音量设置。 */
    public static synchronized void setMuted(boolean value) {
        muted = value;
        applyVolume();
    }

    public static synchronized boolean isMuted() {
        return muted;
    }

    /** 关掉启动期的日志。 */
    public static synchronized void setQuiet(boolean value) {
        quiet = value;
    }

    /**
     * 战况强度 0..1：0 = 平静，1 = 危机。
     *
     * <p>做的事：速度在标称值的 ±15% 内浮动，音区随强度整体上移（越高越紧张）。
     * 内部对值做了量化，值没变就不重复下发，所以每帧调用是安全的。</p>
     */
    public static synchronized void setIntensity(double value) {
        double next = clamp(value, 0, 1);
        if (Math.abs(next - intensity) < 0.01) {
            return;
        }
        intensity = next;
        applyTempo();
        applyPitchBend();
    }

    public static synchronized double intensity() {
        return intensity;
    }

    /**
     * 淡出到停止：{@code fadeOut(1.5)} 表示 1.5 秒后静音。
     *
     * <p>用定时器逐步降音量而不是直接 {@link #stop()}，因为 MIDI 音符被硬切会在
     * 合成器里留下"挂住"的音（收不到 NOTE_OFF）。淡到 0 再停不会有这个问题。</p>
     */
    public static synchronized void fadeOut(double seconds) {
        if (!available || sequencer == null) {
            stop();
            return;
        }
        double from = level;
        int steps = Math.max(1, (int) Math.round(seconds * 20));
        long periodMs = Math.max(10, (long) (seconds * 1000 / steps));
        if (fadeExecutor != null) {
            fadeExecutor.shutdownNow();
        }
        fadeExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread th = new Thread(r, "Music-Fade");
            th.setDaemon(true);
            return th;
        });
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double target = from * (1 - t);
            boolean last = i == steps;
            fadeExecutor.schedule(() -> {
                setLevel(target);
                if (last) {
                    stop();
                    setLevel(from);
                }
            }, periodMs * i, TimeUnit.MILLISECONDS);
        }
    }

    // ------------------------------------------------------------ 内部

    /**
     * 下发主音量。
     *
     * <p><b>为什么只能逐通道改 CC7</b>（2026 实测，别再去试别的路）：</p>
     * <ul>
     *   <li>{@code com.sun.media.sound.SoftSynthesizer} 没有 {@code setMasterVolume}
     *       这类方法，只有 {@code getMasterVolumeMax/Min}（{@code NoSuchMethodException}）；</li>
     *   <li>Master Volume SysEx 送不进去：Gervill 的接收器按通道号解析，直接
     *       {@code ArrayIndexOutOfBoundsException: Index 64 out of bounds for length 16}。</li>
     * </ul>
     * <p>所以主音量就乘在 {@link MusicRegistry#BASE_VOLUME} 这份基准上（它同时也
     * 是配器平衡表：主旋律 104、铺底 44…）。序列每小节会把这份基准重申一次，
     * 于是"运行时的音量"和"曲子里的平衡"不会互相打架。</p>
     */
    private static void applyVolume() {
        if (!available || synthesizer == null) {
            return;
        }
        try {
            double gain = muted ? 0 : level;
            MidiChannel[] channels = synthesizer.getChannels();
            if (channels == null) {
                return;
            }
            for (int i = 0; i < channels.length && i < MusicRegistry.BASE_VOLUME.length; i++) {
                if (channels[i] == null) {
                    continue;
                }
                channels[i].controlChange(7,
                        (int) Math.round(MusicRegistry.BASE_VOLUME[i] * gain));
            }
        } catch (RuntimeException ignored) {
            // 音量下发失败不影响播放：宁可大声，不要没声
        }
    }

    /** 战况强度 → 播放速度（标称值的 ±15%）。 */
    private static void applyTempo() {
        if (!available || sequencer == null) {
            return;
        }
        try {
            sequencer.setTempoFactor((float) (0.85 + 0.30 * intensity));
        } catch (RuntimeException ignored) {
            // 速度改不动就按原速放
        }
    }

    /**
     * 战况强度 → 轻微升调（0 ~ +50 音分，约半个半音）。
     *
     * <p>这是老游戏里常用的"紧张感"手法：音高整体抬一点点，耳朵察觉不到具体
     * 变化，但会感觉更"绷"。做成 Pitch Bend 而不是重新移调，是为了不改动
     * Sequence，可以随时实时来回。</p>
     */
    private static void applyPitchBend() {
        if (!available || synthesizer == null) {
            return;
        }
        try {
            int bend = (int) Math.round(4096 * intensity * 0.5);
            MidiChannel[] channels = synthesizer.getChannels();
            if (channels == null) {
                return;
            }
            for (MidiChannel ch : channels) {
                if (ch != null) {
                    ch.setPitchBend(bend);
                }
            }
        } catch (RuntimeException ignored) {
            // 同上
        }
    }

    /**
     * 试听开关：{@code -Dmusic.track=crisis} 会把所有请求都改成指定曲目。
     *
     * <p>为什么要它：想听某一首就得先玩到那个界面（危机曲要打到一半才够强度），
     * 而调曲子恰恰要反复听。这个开关让"界面切歌"在这台机器上被临时改写，
     * 于是三段曲子都能一次听完 —— 只影响本机试听，不改任何游戏逻辑。</p>
     */
    private static MusicTrack override(MusicTrack requested) {
        String name = System.getProperty("music.track");
        if (name == null || name.isBlank()) {
            return requested;
        }
        for (MusicTrack candidate : MusicTrack.values()) {
            if (candidate.name().equalsIgnoreCase(name) || candidate.file().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return requested;
    }

    /** 取 Sequence：先找类路径上的 .mid（方便以后直接换手工做的曲子），没有就现场生成。 */
    private static synchronized Sequence sequence(MusicTrack track) {
        Sequence cached = SEQUENCES.get(track);
        if (cached != null) {
            return cached;
        }
        Sequence seq = readResource(track.resourcePath());
        if (seq == null) {
            try {
                seq = MusicRegistry.compose(track);
                if (!quiet) {
                    System.out.println("[music] 生成 " + track.label() + "（" + track.bpm() + " BPM）");
                }
            } catch (InvalidMidiDataException | RuntimeException e) {
                fail("生成曲子失败: " + e.getMessage());
                return null;
            }
        }
        SEQUENCES.put(track, seq);
        return seq;
    }

    /**
     * 从类路径读一个 .mid/.rmi；读不出来返回 null 且不抛异常。
     *
     * <p>长度上限 8 MB：分发用的曲子都是几十 KB 级，真读到超大文件多半是路径写错了，
     * 与其把内存吃满不如当场拒绝并说清原因。</p>
     */
    private static Sequence readResource(String path) {
        try (InputStream in = com.hysx.salvosequence.core.Assets.open(path)) {
            if (in == null || in == com.hysx.salvosequence.core.Assets.EMPTY) {
                return null;
            }
            long size = in.available();
            if (size > MAX_MIDI_BYTES) {
                if (!quiet) {
                    System.out.println("[music] " + path + " 太大（" + (size / 1024) + " KB），拒绝载入");
                }
                return null;
            }
            Sequence seq = MidiSystem.getSequence(in);
            if (!quiet) {
                System.out.println("[music] 载入 " + path);
            }
            return seq;
        } catch (IOException | InvalidMidiDataException | RuntimeException e) {
            if (!quiet) {
                System.out.println("[music] " + path + " 读取失败（" + e.getMessage() + "）");
            }
            return null;
        }
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
