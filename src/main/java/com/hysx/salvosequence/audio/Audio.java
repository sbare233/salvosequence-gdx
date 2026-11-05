package com.hysx.salvosequence.audio;

/**
 * 音频子系统的唯一入口：持有那一个共享混音器。
 *
 * <p>为什么要有一个"持有者"类：混音器是<b>全局独占资源</b>（一块声卡、一条输出流），
 * 不能每个 {@link SoundManager} 实例各建一个，否则两个管理器会抢同一台设备。
 * 放在这里而不是做成 {@link Mixer} 的静态单例，是为了让它的生命周期看得见：
 * 谁构造了 {@code SoundManager}，谁就能通过 {@link SoundManager#shutdown()}
 * 把它关掉。</p>
 */
final class Audio {

    private static final Mixer MIXER = new Mixer();

    private Audio() {
    }

    /** 全局混音器。 */
    static Mixer mixer() {
        return MIXER;
    }
}
