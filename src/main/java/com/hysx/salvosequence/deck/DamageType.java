package com.hysx.salvosequence.deck;

/**
 * 伤害类型（群星克制三角的数值化）。
 *
 * <p>类型只回答一个问题：<b>这一发撞上哪池肉、乘多少</b>——盾亮着按 {@link #vsShield}，
 * 盾空了按 {@link #vsHull}。防御力（甲）和防空与类型无关：
 * 穿甲靠攻击高（每段 −甲，攻击高自然磨得动），削段靠防空（多段武器被砍段数）。</p>
 *
 * <p>倍率表是静态常量，<b>不是配平旋钮</b>——配平永远不碰它。</p>
 */
public enum DamageType {

    /** 动能：破盾者（机炮/主炮/重炮/轴炮）。 */
    KINETIC("动能", "K", 1.5, 1.0),

    /** 热能：烧甲烧体者（光矛/波动炮）。 */
    THERMAL("热能", "T", 0.75, 1.5),

    /** 爆破：通吃但低伤×多段，被防空削段（导弹）。 */
    EXPLOSIVE("爆破", "E", 1.0, 1.0),

    /** 电离：穿透直打体层，面板低（电子战）。 */
    ION("电离", "I", 1.0, 1.0);

    private final String labelZh;
    private final String tag;
    private final double vsShield;
    private final double vsHull;

    DamageType(String labelZh, String tag, double vsShield, double vsHull) {
        this.labelZh = labelZh;
        this.tag = tag;
        this.vsShield = vsShield;
        this.vsHull = vsHull;
    }

    /** 中文名。 */
    public String labelZh() {
        return labelZh;
    }

    /** ASCII 标（点阵字库只认 ASCII）：意图/卡面上的 K/T/E/I。 */
    public String tag() {
        return tag;
    }

    /** 对护盾（格挡）的倍率。 */
    public double vsShield() {
        return vsShield;
    }

    /** 对结构值的倍率。 */
    public double vsHull() {
        return vsHull;
    }
}
