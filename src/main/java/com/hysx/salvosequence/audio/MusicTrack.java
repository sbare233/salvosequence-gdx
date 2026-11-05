package com.hysx.salvosequence.audio;

/**
 * 配乐清单。
 *
 * <p>和 {@link SoundId} 一样，调用方只写常量，不出现文件名。区别在于音乐是
 * "有状态的"：同一时刻只应该响一首，而且能被战况调强度（见 {@link Music}）。</p>
 */
public enum MusicTrack {

    /** 标题/菜单：安静，只有铺底和稀疏的旋律。 */
    TITLE("title", 84, "TITLE THEME"),

    /** 常规战斗：完整编制，132 BPM。 */
    BATTLE("battle", 132, "BATTLE THEME"),

    /** 危机：更快、更冲，给"血少了"的紧张感。 */
    CRISIS("crisis", 150, "CRISIS THEME");

    /** 类路径根目录：{@code /music/}。 */
    public static final String ROOT = "/music/";

    private final String file;
    private final int bpm;
    private final String label;

    MusicTrack(String file, int bpm, String label) {
        this.file = file;
        this.bpm = bpm;
        this.label = label;
    }

    /** 不带扩展名的文件名。 */
    public String file() {
        return file;
    }

    /** 类路径资源路径。 */
    public String resourcePath() {
        return ROOT + file + ".mid";
    }

    /** 生成这段曲子时用的速度。 */
    public int bpm() {
        return bpm;
    }

    /** 界面上显示的英文名（点阵字只有 ASCII）。 */
    public String label() {
        return label;
    }
}
