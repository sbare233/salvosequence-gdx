package com.hysx.salvosequence.audio;

/**
 * 音效清单：把"游戏里会出现的声音"和"文件放在哪"绑在一处。
 *
 * <p>调用方只写 {@code Sound.play(SoundId.UI_CLICK)}，不出现路径字符串 ——
 * 换素材、改目录、加一个音效都只动这一个文件。{@link SoundManager} 会据此
 * 去类路径 {@code /sounds/} 下加载。</p>
 *
 * <p>音量为<b>相对基准音量</b>（0.0 ~ 1.0）：最终音量 = 基准 × 全局音量。
 * 这样全局音量滑杆一动，所有音效比例关系不变。</p>
 */
public enum SoundId {

    // ---- 界面 ----
    /** 光标移到菜单项。 */
    UI_MOVE("beep", 0.45),
    /** 按下 / 确认。 */
    UI_CLICK("click", 0.60),
    /** 退回 / 取消。 */
    UI_BACK("click", 0.45),

    // ---- 战斗 ----
    /** 我方开火（轻）。 */
    SHOT_LIGHT("shot_light", 0.50),
    /** 敌方开火（重一点、闷一点）。 */
    SHOT_HEAVY("shot_heavy", 0.40),
    /** 命中但没打穿（打在护盾上）。 */
    HIT("hit", 0.70),
    /** 击毁。 */
    EXPLODE("explode", 0.80),
    /** 警报（护盾见底、进入战斗）。 */
    ALARM("alarm", 0.55);

    private final String file;
    private final double baseVolume;

    SoundId(String file, double baseVolume) {
        this.file = file;
        this.baseVolume = baseVolume;
    }

    /** 类路径资源名（不含目录与扩展名）。 */
    public String file() {
        return file;
    }

    /** 类路径绝对路径，例如 {@code /sounds/click.wav}。 */
    public String resourcePath() {
        return "/sounds/" + file + ".wav";
    }

    /** 基准音量。 */
    public double baseVolume() {
        return baseVolume;
    }
}
