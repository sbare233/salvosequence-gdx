package com.hysx.salvosequence.audio;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 音频管理器：加载、播放、自动回收音效。
 *
 * <p>缓存与回收策略参考了 blendefine 的 {@code SoundManager}（单例 + 空闲超时 +
 * LRU 上限 + 守护清理线程 + 同一音效可重叠播放），但有四处针对性改动：</p>
 *
 * <ol>
 *   <li><b>资源走类路径</b>而非 {@code src/...} 文件路径：打包成 exe 之后
 *       相对路径会失效，类路径在开发态和发布态都成立。</li>
 *   <li><b>找不到音频降级为静音并记一次警告</b>，不抛异常。blendefine 的版本在
 *       未加载时 {@code play} 会抛 {@code IllegalArgumentException}——
 *       游戏里因为少一个 wav 就崩掉不值得。</li>
 *   <li><b>全局音量/静音</b>做在这里，避免每个调用点都手写 {@code * volume}。</li>
 *   <li><b>清理线程可关闭</b>，且 {@link #shutdown()} 可重复调用（离屏核对
 *       工具会反复建/拆）。</li>
 * </ol>
 *
 * <p><b>播放后端换成了 JDK 自带的 {@code javax.sound.sampled}</b>（见
 * {@link Mixer} 与 {@link SoundClip}）。之前单声道 PCM 完全听不见，
 * 只有高采样率的音效偶尔能出声，根因无从修——那套 API 又被
 * {@code javafx.media} 模块绑死（jlink 精简运行时里干脆没有）。换成 Java Sound 后：
 * 音频不再依赖任何 JavaFX 模块，C 编译器 21 自带，分发包不用再静音。</p>
 *
 * <p>清理策略：超过空闲阈值未使用的条目被移除；若条目数超过上限，按"最久未使用"
 * 淘汰到上限。音效对象的资源很小（几百 KB 的 PCM），丢掉引用即可。</p>
 */
public final class SoundManager {

    /** 空闲多久之后可以被回收（毫秒）。 */
    public static final long DEFAULT_IDLE_TIMEOUT_MS = 2 * 60 * 1000;

    /** 最多同时缓存多少个音效。 */
    public static final int DEFAULT_MAX_CACHE_SIZE = 64;

    /** 清理线程多久跑一次（秒）。 */
    private static final long CLEAN_INTERVAL_SECONDS = 30;

    /** 项目内音频的类路径根目录。 */
    private static final String ROOT = "/sounds/";

    private final Map<String, SoundClip> cache = new HashMap<>();
    private final Map<String, Long> lastAccess = new HashMap<>();
    /** 已经确认加载失败的 key，避免每帧重试再刷屏。 */
    private final Deque<String> failed = new ArrayDeque<>();

    private final long idleTimeoutMs;
    private final int maxCacheSize;
    private final ScheduledExecutorService cleaner;

    private double volume = 1.0;
    private boolean muted;
    private boolean quiet;

    public SoundManager() {
        this(DEFAULT_IDLE_TIMEOUT_MS, DEFAULT_MAX_CACHE_SIZE);
    }

    /**
     * @param idleTimeoutMs 空闲超时（毫秒）
     * @param maxCacheSize  缓存上限
     */
    public SoundManager(long idleTimeoutMs, int maxCacheSize) {
        this.idleTimeoutMs = Math.max(1000, idleTimeoutMs);
        this.maxCacheSize = Math.max(1, maxCacheSize);
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SoundManager-Cleaner");
            t.setDaemon(true); // 守护线程：不会拖住 JVM 退出
            return t;
        });
        cleaner.scheduleWithFixedDelay(this::sweep, CLEAN_INTERVAL_SECONDS,
                CLEAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
        Audio.mixer().setVolume(volume);
    }

    // ------------------------------------------------------------ 全局开关

    /** 全局音量（0.0 ~ 1.0）。 */
    public void setVolume(double volume) {
        double v = clamp01(volume);
        synchronized (this) {
            this.volume = v;
        }
        Audio.mixer().setVolume(v);
    }

    public synchronized double volume() {
        return volume;
    }

    /** 静音开关。静音时连输出流都不写，等于彻底停掉。 */
    public void setMuted(boolean muted) {
        synchronized (this) {
            this.muted = muted;
        }
        Audio.mixer().setMuted(muted);
        if (muted) {
            Audio.mixer().stopAll();
        }
    }

    public synchronized boolean isMuted() {
        return muted;
    }

    /** 静音模式下是否打印"资源缺失"警告。 */
    public synchronized void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    // -------------------------------------------------------------- 加载

    /**
     * 预加载一个音效（进战斗前把要用的都热一遍，避免第一发卡顿）。
     */
    public void preload(SoundId id) {
        clip(id);
    }

    /** 批量预加载。 */
    public void preload(SoundId... ids) {
        for (SoundId id : ids) {
            clip(id);
        }
    }

    /**
     * 取（必要时加载）音效。
     *
     * @return 加载失败时返回 null（调用方按静音处理）
     */
    public SoundClip clip(SoundId id) {
        if (id == null) {
            return null;
        }
        String key = id.resourcePath();
        synchronized (this) {
            SoundClip cached = cache.get(key);
            if (cached != null) {
                lastAccess.put(key, System.currentTimeMillis());
                return cached;
            }
            if (failed.contains(key)) {
                return null;
            }
        }
        var url = com.hysx.salvosequence.core.Assets.url(ROOT + id.file() + ".wav");
        if (url == null) {
            markFailed(key, "[Sound] 资源缺失: " + key);
            return null;
        }
        SoundClip loaded;
        try {
            loaded = new SoundClip(key, url, id.baseVolume());
        } catch (Throwable ex) {
            // 必须捕获 Throwable 而不是 Exception：音频设备层偶尔抛 Error，
            // 穿透到 JavaFX 应用线程会把整局游戏打死。音效对游戏是锦上添花，
            // 缺了就该静音继续跑，绝不能让玩家看堆栈。
            markFailed(key, "[Sound] 加载失败 " + key + ": " + ex);
            return null;
        }
        synchronized (this) {
            cache.put(key, loaded);
            lastAccess.put(key, System.currentTimeMillis());
        }
        return loaded;
    }

    private void markFailed(String key, String message) {
        synchronized (this) {
            failed.add(key);
        }
        warn(message);
    }

    /**
     * 音频自检用的一份"体检结果"。
     *
     * <p>刻意用不可变记录而不是把 {@link SoundClip} 暴露出去：{@code SoundClip}
     * 保持包内可见，整个项目里只有 {@code audio} 包能操作播放对象，
     * 别的地方想看一眼数据只能拿这个快照。</p>
     *
     * @param id        音效标识
     * @param resource  类路径资源
     * @param frames    单声道采样帧数
     * @param durationSeconds 时长（秒）
     * @param peak      幅度峰值（0~1）；≈0 说明资源本身是静音的
     * @param rms       有效值（0~1）
     */
    public record ClipInfo(SoundId id, String resource, int frames, double durationSeconds,
                           double peak, double rms) {
    }

    /**
     * 取某个音效的体检结果（会触发加载）。
     *
     * @return 加载失败返回 null（原因见 {@link #audioBrokenReason()} 或 {@link #failures()}）
     */
    public ClipInfo diagnostic(SoundId id) {
        SoundClip c = clip(id);
        if (c == null) {
            return null;
        }
        return new ClipInfo(id, c.source(), c.frames(), c.durationSeconds(), c.peak(), c.rms());
    }

    /** 输出格式摘要（采样率/位深/声道），自检与日志用。 */
    public static String formatSummary() {
        return (int) Mixer.FORMAT.getSampleRate() + "Hz "
                + Mixer.FORMAT.getSampleSizeInBits() + "bit "
                + Mixer.FORMAT.getChannels() + "声道";
    }

    /** 本机音频设备数量摘要（诊断用）。 */
    public static String deviceSummary() {
        return Mixer.describeDevices();
    }

    /** 音频设备当前的可用状态，启动时打一行。
     *
     * <p>为什么要打印结论："没声音"和"游戏坏了"在玩家看来是一样的，
     * 组员报问题时一行日志就能区分。</p>
     *
     * @return 正常返回 null；不可用返回原因
     */
    public String probe() {
        String reason = Audio.mixer().brokenReason();
        if (reason != null) {
            warn("[Sound] 音频不可用，已切换为静音继续运行: " + reason);
        }
        return reason;
    }

    /** 底层音频是否已经确认不可用（界面可以据此隐藏音量项）。 */
    public boolean isAudioBroken() {
        return Audio.mixer().brokenReason() != null;
    }

    /** 音频不可用的原因；正常时为 null。 */
    public String audioBrokenReason() {
        return Audio.mixer().brokenReason();
    }

    /** 当前正在发声的声部数（诊断用）。 */
    public int activeVoices() {
        return Audio.mixer().activeVoices();
    }

    // -------------------------------------------------------------- 播放

    /** 以基准音量播放。 */
    public void play(SoundId id) {
        play(id, 1.0, 1.0);
    }

    /**
     * 播放。
     *
     * @param id   音效
     * @param gain 额外增益（1.0 = 基准音量；音效用它做远近/轻重）
     * @param rate 播放速率（0.5 ~ 2.0，音调随之变化；同一个音效变调可当多个用）
     */
    public void play(SoundId id, double gain, double rate) {
        if (id == null) {
            return;
        }
        synchronized (this) {
            if (muted || volume <= 0) {
                return;
            }
        }
        if (gain <= 0) {
            return;
        }
        SoundClip c = clip(id);
        if (c == null) {
            return;
        }
        c.play(gain, rate);
    }

    /** 播放并指定音量。 */
    public void play(SoundId id, double gain) {
        play(id, gain, 1.0);
    }

    /**
     * 停掉某个音效。
     *
     * <p>混音器里声部不按音效区分（只按"谁在响"），所以这里是<b>停掉全部</b>：
     * 目前游戏只在"切界面/退出"时用得到它。要按音效单独停，得给声部加个来源标记。</p>
     */
    public void stop(SoundId id) {
        if (id == null) {
            return;
        }
        stopAll();
    }

    /** 停掉所有正在响的声部。 */
    public void stopAll() {
        Audio.mixer().stopAll();
    }

    // -------------------------------------------------------------- 回收

    /**
     * 清掉空闲超时的、并在超出上限时按 LRU 淘汰。
     *
     * @return 本次回收了几个
     */
    public synchronized int sweep() {
        long now = System.currentTimeMillis();
        int before = cache.size();
        var it = cache.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            long last = lastAccess.getOrDefault(e.getKey(), 0L);
            if (now - last > idleTimeoutMs) {
                e.getValue().close();
                it.remove();
                lastAccess.remove(e.getKey());
            }
        }
        // 仍然超上限就淘汰最久未用的
        while (cache.size() > maxCacheSize) {
            String oldest = null;
            long oldestTime = Long.MAX_VALUE;
            for (var e : lastAccess.entrySet()) {
                if (cache.containsKey(e.getKey()) && e.getValue() < oldestTime) {
                    oldestTime = e.getValue();
                    oldest = e.getKey();
                }
            }
            if (oldest == null) {
                break;
            }
            SoundClip gone = cache.remove(oldest);
            if (gone != null) {
                gone.close();
            }
            lastAccess.remove(oldest);
        }
        return before - cache.size();
    }

    /** 当前缓存的音效数量（诊断用）。 */
    public synchronized int cachedCount() {
        return cache.size();
    }

    /** 已经加载失败过的资源路径（诊断用）。 */
    public synchronized java.util.List<String> failures() {
        return java.util.List.copyOf(failed);
    }

    /**
     * 关闭：停掉混音器与清理线程并释放引用。可重复调用。
     */
    public void shutdown() {
        synchronized (this) {
            cache.clear();
            lastAccess.clear();
            failed.clear();
        }
        Audio.mixer().stopAll();
        Audio.mixer().shutdown();
        cleaner.shutdownNow();
    }

    // -------------------------------------------------------------- 工具

    private void warn(String message) {
        if (!quiet) {
            System.out.println(message);
        }
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
