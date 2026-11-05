package com.hysx.salvosequence.audio;

/**
 * 配乐门面（Android 版）：占位实现，全部方法可安全调用但不出声。
 *
 * <p>桌面版的配乐是运行时用 {@code javax.sound.midi} 实时合成 MIDI（可被战况驱动
 * 改强度），Android 没有 MIDI 合成器，又不想在 APK 里塞一段固定音频，所以首版
 * APK 只保留音效（见 {@link Sound}），配乐关闭；设置面板里的「音乐」开关仍然是
 * 可用的静音开关，{@link #available()} 如实返回 false。</p>
 *
 * <p>对外 API 与桌面版同名同签，调用点不用改；将来接上 libGDX Music 时只需
 * 在这个文件里换成真实实现。</p>
 */
public final class Music {

    private static boolean muted;
    private static double level = 0.8;
    private static double intensity = 0.5;
    private static String failure = "Android 版暂未接入配乐";

    private Music() {
    }

    public static synchronized boolean available() {
        return false;
    }

    public static synchronized String failureReason() {
        return failure;
    }

    public static synchronized void shutdown() {
    }

    public static void play(MusicTrack track) {
        play(track, true);
    }

    public static synchronized void play(MusicTrack requested, boolean loop) {
        // 静音占位：桌面版在这里切曲
    }

    public static synchronized void pause() {
    }

    public static synchronized void resume() {
    }

    /** @return 是否真的开始播放（Android 版恒为 false，调用方会自行降级） */
    public static synchronized boolean playPath(String resourcePath, boolean loop) {
        return false;
    }

    public static synchronized double currentLengthSeconds() {
        return 0;
    }

    public static synchronized double positionSeconds() {
        return 0;
    }

    public static synchronized void stop() {
    }

    public static synchronized MusicTrack currentTrack() {
        return null;
    }

    public static synchronized boolean isPlaying() {
        return false;
    }

    public static synchronized void setLevel(double value) {
        level = Math.max(0, Math.min(1, value));
    }

    public static synchronized double level() {
        return level;
    }

    public static synchronized void setMuted(boolean value) {
        muted = value;
    }

    public static synchronized boolean isMuted() {
        return muted;
    }

    public static synchronized void setQuiet(boolean value) {
    }

    public static synchronized void setIntensity(double value) {
        intensity = Math.max(0, Math.min(1, value));
    }

    public static synchronized double intensity() {
        return intensity;
    }

    public static synchronized void fadeOut(double seconds) {
    }
}
