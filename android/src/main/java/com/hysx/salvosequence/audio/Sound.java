package com.hysx.salvosequence.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.util.EnumMap;
import java.util.Map;

/**
 * 音效门面（Android 版）：桌面版用 {@code javax.sound.sampled} 自己混音，Android
 * 没有这套 API，改成直接把 {@code assets/sounds/*.wav} 交给 libGDX 的
 * {@code com.badlogic.gdx.audio.Sound} 播放。
 *
 * <p>对外 API 与桌面版一致（{@code Sound.play(...)}），呼叫点一行都不用改；
 * 混音策略（全局音量、静音、相对基准音量、速率）在这里按同样的口径折算成
 * libGDX 的 {@code play(volume, pitch, pan)}。</p>
 */
public final class Sound {

    /** 音效缓存：SoundId → libGDX Sound（懒加载，失败记为 null 不再重试）。 */
    private static final Map<SoundId, com.badlogic.gdx.audio.Sound> CLIPS = new EnumMap<>(SoundId.class);

    private static boolean enabled = true;
    private static double volume = 1.0;
    private static boolean muted;

    private Sound() {
    }

    /** 全局开关（关掉后所有 play 直接返回，不加载也不出声）。 */
    public static synchronized void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            stopAll();
        }
    }

    public static synchronized boolean isEnabled() {
        return enabled;
    }

    public static synchronized void setVolume(double value) {
        volume = Math.max(0, Math.min(1, value));
    }

    public static synchronized double volume() {
        return volume;
    }

    public static synchronized void setMuted(boolean value) {
        muted = value;
        if (value) {
            stopAll();
        }
    }

    public static synchronized boolean isMuted() {
        return muted;
    }

    /** Android 端没有"警告压制"这一层，保留空实现让共享代码照常调用。 */
    public static synchronized void setQuiet(boolean quiet) {
    }

    /** 预加载（首次播放前把 wav 读进内存）。 */
    public static synchronized void preload(SoundId... ids) {
        if (!enabled || ids == null) {
            return;
        }
        for (SoundId id : ids) {
            clip(id);
        }
    }

    public static synchronized void play(SoundId id) {
        play(id, 1.0, 1.0);
    }

    public static synchronized void play(SoundId id, double gain) {
        play(id, gain, 1.0);
    }

    /** 按增益 + 速率播放；速率会同时改变音高（与桌面版口径一致）。 */
    public static synchronized void play(SoundId id, double gain, double rate) {
        if (!enabled || muted || volume <= 0 || id == null) {
            return;
        }
        com.badlogic.gdx.audio.Sound clip = clip(id);
        if (clip == null) {
            return;
        }
        float v = (float) Math.max(0, Math.min(1, volume * id.baseVolume() * gain));
        if (v <= 0) {
            return;
        }
        float pitch = (float) Math.max(0.5, Math.min(2.0, rate));
        try {
            clip.play(v, pitch, 0f);
        } catch (Throwable ignored) {
            // 设备音频偶发失败不该把游戏拖垮
        }
    }

    public static synchronized void stopAll() {
        for (com.badlogic.gdx.audio.Sound clip : CLIPS.values()) {
            try {
                if (clip != null) {
                    clip.stop();
                }
            } catch (Throwable ignored) {
                // 已释放/未就绪：忽略
            }
        }
    }

    /** 关掉整条音频链路（离屏测试 / 重建用）。 */
    public static synchronized void reset() {
        shutdown();
    }

    public static synchronized void shutdown() {
        stopAll();
        for (com.badlogic.gdx.audio.Sound clip : CLIPS.values()) {
            try {
                if (clip != null) {
                    clip.dispose();
                }
            } catch (Throwable ignored) {
                // 已释放
            }
        }
        CLIPS.clear();
    }

    private static com.badlogic.gdx.audio.Sound clip(SoundId id) {
        if (CLIPS.containsKey(id)) {
            return CLIPS.get(id);
        }
        com.badlogic.gdx.audio.Sound loaded = null;
        if (Gdx.audio != null) {
            try {
                FileHandle file = Gdx.files.internal("sounds/" + id.file() + ".wav");
                if (file.exists()) {
                    loaded = Gdx.audio.newSound(file);
                }
            } catch (Throwable t) {
                System.out.println("[sound] " + id + " 加载失败: " + t);
            }
        }
        CLIPS.put(id, loaded);
        return loaded;
    }
}
