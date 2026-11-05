package com.hysx.salvosequence.ui;

/**
 * Android 上的桌面入口替身。
 *
 * <p>桌面版 {@code SalvoSequenceApp} 负责建 Lwjgl3Application、切换窗口、退出时关音频；
 * Android 上这些由 {@code AndroidLauncher} 与 libGDX 自己管，这里只留
 * {@link #closeAudio()} 一个空实现给 {@code PixelGameView.dispose()} 调用。</p>
 */
public final class SalvoSequenceApp {

    private SalvoSequenceApp() {
    }

    /** 桌面版在这里关掉 MIDI 合成器；Android 没有 MIDI，无需处理。 */
    public static void closeAudio() {
    }
}
