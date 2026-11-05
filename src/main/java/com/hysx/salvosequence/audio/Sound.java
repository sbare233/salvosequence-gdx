package com.hysx.salvosequence.audio;

/**
 * 音效门面：游戏代码统一从这里出声。
 *
 * <p>只暴露静态方法，内部持有一个 {@link SoundManager}。之所以不让业务代码直接
 * 拿 SoundManager，是为了留一条"整条音频链路可以整体换掉/关掉"的缝：
 * {@link #setEnabled(boolean)} 一关，所有调用点都不用改。</p>
 *
 * <p>典型用法：</p>
 * <pre>
 * Sound.play(SoundId.UI_CLICK);              // 界面点击
 * Sound.play(SoundId.SHOT_LIGHT, 0.8);       // 稍轻的开火
 * Sound.play(SoundId.EXPLODE, 1.0, 1.3);     // 同一个爆炸音效升高调，当第二个音效用
 * </pre>
 */
public final class Sound {

    private static SoundManager manager = new SoundManager();
    private static boolean enabled = true;

    private Sound() {
    }

    /** 全局开关（关掉后所有 play 直接返回，不加载也不出声）。 */
    public static synchronized void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            manager.stopAll();
        }
    }

    public static synchronized boolean isEnabled() {
        return enabled;
    }

    public static synchronized SoundManager manager() {
        return manager;
    }

    /** 全局音量（0.0 ~ 1.0）。 */
    public static synchronized void setVolume(double volume) {
        manager.setVolume(volume);
    }

    public static synchronized double volume() {
        return manager.volume();
    }

    public static synchronized void setMuted(boolean muted) {
        manager.setMuted(muted);
    }

    public static synchronized boolean isMuted() {
        return manager.isMuted();
    }

    /** 是否压制"资源缺失/音频不可用"这类警告（离屏核对与单元测试里打开）。 */
    public static synchronized void setQuiet(boolean quiet) {
        manager.setQuiet(quiet);
    }

    /** 预加载（进战斗前热一遍，避免首次播放卡顿）。 */
    public static synchronized void preload(SoundId... ids) {
        if (enabled) {
            manager.preload(ids);
        }
    }

    /** 以基准音量播放。 */
    public static synchronized void play(SoundId id) {
        if (enabled) {
            manager.play(id);
        }
    }

    /** 按增益播放。 */
    public static synchronized void play(SoundId id, double gain) {
        if (enabled) {
            manager.play(id, gain, 1.0);
        }
    }

    /**
     * 按增益 + 速率播放。
     *
     * @param gain 额外增益（1.0 = 基准）
     * @param rate 播放速率（0.5 ~ 2.0）
     */
    public static synchronized void play(SoundId id, double gain, double rate) {
        if (enabled) {
            manager.play(id, gain, rate);
        }
    }

    public static synchronized void stopAll() {
        manager.stopAll();
    }

    /**
     * 关掉并重建整条音频链路（离屏核对 / 测试里反复初始化用）。
     */
    public static synchronized void reset() {
        manager.shutdown();
        manager = new SoundManager();
    }

    /** 应用退出时调用：停声音 + 结束清理线程。 */
    public static synchronized void shutdown() {
        manager.shutdown();
    }
}
