package com.hysx.salvosequence.core;

import com.hysx.salvosequence.core.Color;

/**
 * 界面配色 —— 「星幕协议 A.A.A.」风格。
 *
 * <p>原来的线框荧光（黑底 + 冷白描线 + 青蓝高光）整体换成星幕协议那套
 * <b>暖橙结构色 + 钢蓝框架色 + 暗红告警色 + 羊皮纸标题牌</b>：
 * 底色是偏蓝的近黑，面板靠双层钢蓝细线框住，选中/主色是琥珀橙，
 * 危险与锁定是砖红，能量与科技读数是青绿。字段名保持不变（全项目 300 多处引用），
 * 只换值，再补上新语义的名字。</p>
 */
public final class Palette {

    /** 画布外的纯黑留边。 */
    public static final Color BACKDROP = Color.rgb(0, 0, 0);
    /** 太空底色：比原来更偏暖一点点，和橙色结构线不打架。 */
    public static final Color SPACE = Color.rgb(7, 9, 14);
    public static final Color SPACE_DEEP = Color.rgb(3, 4, 8);

    /** 主描线/正文白：星幕协议的正文是偏暖的灰白，不是纯白。 */
    public static final Color LINE = Color.rgb(233, 227, 213);
    public static final Color LINE_DIM = Color.rgb(166, 160, 146);
    public static final Color LINE_FAINT = Color.rgb(103, 100, 94);

    /** 面板填充：近黑蓝，带一点钢蓝底噪。 */
    public static final Color PANEL = Color.rgb(11, 14, 20);
    public static final Color PANEL_ALT = Color.rgb(19, 25, 36);
    public static final Color PANEL_EDGE = Color.rgb(53, 65, 90);

    /** 高光（选中/我方位/主按钮）：琥珀橙 —— 星幕协议里所有"活着"的东西都是这个色。 */
    public static final Color CYAN = Color.rgb(240, 145, 60);
    public static final Color CYAN_DIM = Color.rgb(162, 85, 31);
    /** 钢蓝：框架、副标题、非激活结构线。 */
    public static final Color BLUE = Color.rgb(124, 158, 201);
    public static final Color BLUE_DIM = Color.rgb(51, 73, 107);

    /**
     * 紫罗兰：星图「商店」节点的专属色。
     *
     * <p>星盘底色是暖橙的银河，商店再给暖色就糊进去了；紫是这套暖橙/钢蓝里
     * 唯一能拉开色相的冷色，又和青绿（事件）、钢蓝（休息）分得开。</p>
     */
    public static final Color VIOLET = Color.rgb(170, 122, 214);
    public static final Color VIOLET_DIM = Color.rgb(84, 56, 116);

    /** 状态色。 */
    public static final Color OK = Color.rgb(92, 201, 167);
    public static final Color WARN = Color.rgb(255, 180, 84);
    public static final Color DANGER = Color.rgb(224, 69, 58);

    /** 火控锁定：砖红菱形 + 箭矢（HOT 只给箭尖/刚合拢的一瞬）。 */
    public static final Color LOCK = Color.rgb(226, 59, 47);
    public static final Color LOCK_DIM = Color.rgb(122, 29, 23);
    public static final Color LOCK_HOT = Color.rgb(255, 217, 192);

    /** 星空。 */
    public static final Color STAR_FAR = Color.rgb(70, 76, 96);
    public static final Color STAR_MID = Color.rgb(132, 144, 166);
    public static final Color STAR_NEAR = Color.rgb(228, 234, 246);

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
    public static final Color SHIP_GLOW = Color.rgb(255, 176, 96);

    /** 敌方机体。 */
    public static final Color FOE_HULL = Color.rgb(206, 148, 148);
    public static final Color FOE_SHADE = Color.rgb(122, 78, 84);
    public static final Color FOE_DEEP = Color.rgb(64, 38, 44);
    public static final Color FOE_GLOW = Color.rgb(230, 96, 72);

    // ---------------------------------------------------------------- 星幕协议新增语义色

    /** 框架钢蓝：面板外框、模块边、非激活分割线。 */
    public static final Color STEEL = Color.rgb(96, 124, 162);
    public static final Color STEEL_DIM = Color.rgb(56, 74, 102);
    public static final Color STEEL_FAINT = Color.rgb(32, 43, 62);

    /** 结构橙：面板高亮框、标题条、四角括号。 */
    public static final Color AMBER = Color.rgb(240, 145, 60);
    public static final Color AMBER_DIM = Color.rgb(150, 78, 28);
    public static final Color AMBER_DEEP = Color.rgb(74, 38, 16);

    /** 警示砖红：战斗视窗框、被锁定、扣血。 */
    public static final Color CRIMSON = Color.rgb(200, 52, 44);
    public static final Color CRIMSON_DIM = Color.rgb(96, 26, 22);

    /** 科技青绿：能量、EN、护盾读数。 */
    public static final Color TEAL = Color.rgb(88, 196, 186);
    public static final Color TEAL_DIM = Color.rgb(38, 92, 92);

    /** 羊皮纸标题牌：底色与压在上面的深字。 */
    public static final Color PARCHMENT = Color.rgb(214, 182, 132);
    public static final Color PARCHMENT_DIM = Color.rgb(158, 126, 82);
    public static final Color INK = Color.rgb(26, 18, 10);

    /**
     * 面板内扫描线/网点。
     *
     * <p><b>必须是不透明色</b>：{@code PixelArt.ditherRect} 走 {@code pixelArgb} 直写，
     * 不做 alpha 混合，半透明色在这里会画成"半透明白点"，整块面板发灰。
     * 想要"轻微提亮"就让颜色本身比面板底浅一点点。</p>
     */
    public static final Color SCAN = Color.rgb(6, 8, 13);
    public static final Color HALFTONE = Color.rgb(20, 27, 39);

    private Palette() {
    }
}
