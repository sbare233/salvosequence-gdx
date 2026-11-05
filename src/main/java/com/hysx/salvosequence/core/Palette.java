package com.hysx.salvosequence.core;

import com.hysx.salvosequence.core.Color;

/**
 * 界面配色。整体沿用草图的“线框荧光”风格：黑底 + 冷白描线 + 少量青蓝高光与告警红。
 */
public final class Palette {

    /** 画布外的纯黑留边。 */
    public static final Color BACKDROP = Color.rgb(0, 0, 0);
    /** 太空底色。 */
    public static final Color SPACE = Color.rgb(4, 7, 14);
    public static final Color SPACE_DEEP = Color.rgb(2, 3, 8);

    /** 主描线（草图里的白线）。 */
    public static final Color LINE = Color.rgb(233, 240, 248);
    public static final Color LINE_DIM = Color.rgb(150, 162, 178);
    public static final Color LINE_FAINT = Color.rgb(84, 96, 112);

    /** 面板填充。 */
    public static final Color PANEL = Color.rgb(10, 14, 22);
    public static final Color PANEL_ALT = Color.rgb(17, 23, 34);
    public static final Color PANEL_EDGE = Color.rgb(44, 58, 78);

    /** 高光。 */
    public static final Color CYAN = Color.rgb(96, 226, 255);
    public static final Color CYAN_DIM = Color.rgb(38, 118, 148);
    public static final Color BLUE = Color.rgb(88, 140, 255);
    public static final Color BLUE_DIM = Color.rgb(34, 58, 118);

    /** 状态色。 */
    public static final Color OK = Color.rgb(120, 240, 168);
    public static final Color WARN = Color.rgb(255, 196, 84);
    public static final Color DANGER = Color.rgb(255, 92, 92);

    /** 火控锁定：红菱形 + 箭矢（HOT 只给箭尖/刚合拢的一瞬）。 */
    public static final Color LOCK = Color.rgb(255, 74, 62);
    public static final Color LOCK_DIM = Color.rgb(138, 34, 30);
    public static final Color LOCK_HOT = Color.rgb(255, 198, 176);

    /** 星空。 */
    public static final Color STAR_FAR = Color.rgb(64, 74, 96);
    public static final Color STAR_MID = Color.rgb(126, 140, 166);
    public static final Color STAR_NEAR = Color.rgb(224, 236, 255);

    /** 太阳标记：暖金核心 + 暗金光芒，和冷色星图区分开。 */
    public static final Color SUN = Color.rgb(255, 232, 170);
    public static final Color SUN_DIM = Color.rgb(196, 138, 52);

    /** 立绘角色肤色/发色（像素小人）。 */
    public static final Color SKIN = Color.rgb(236, 198, 172);
    public static final Color HAIR = Color.rgb(74, 108, 190);
    public static final Color SUIT_DARK = Color.rgb(28, 38, 60);
    public static final Color SUIT_LIGHT = Color.rgb(58, 76, 110);

    /** 我方机体。 */
    public static final Color SHIP_HULL = Color.rgb(198, 216, 236);
    public static final Color SHIP_SHADE = Color.rgb(112, 132, 160);
    public static final Color SHIP_DEEP = Color.rgb(56, 70, 92);
    public static final Color SHIP_GLOW = Color.rgb(120, 226, 255);

    /** 敌方机体。 */
    public static final Color FOE_HULL = Color.rgb(206, 148, 148);
    public static final Color FOE_SHADE = Color.rgb(122, 78, 84);
    public static final Color FOE_DEEP = Color.rgb(64, 38, 44);
    public static final Color FOE_GLOW = Color.rgb(255, 138, 120);

    private Palette() {
    }
}
