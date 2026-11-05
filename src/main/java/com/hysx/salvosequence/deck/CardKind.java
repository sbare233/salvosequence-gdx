package com.hysx.salvosequence.deck;

/**
 * 卡牌的分类。
 *
 * <h2>三类卡的分工</h2>
 * <ul>
 *   <li>{@link #OPERATION} <b>操作卡</b>：组件带出来的、直接干活的卡（炮击、护盾、校射、充能）。
 *       它的数量完全由已装组件决定——拆掉一门主炮，主炮齐射就从卡组里消失。</li>
 *   <li>{@link #BUFF} <b>增益卡</b>：战利品里的"独立卡"，不挂在任何组件上，永久留在卡组。
 *       用来给玩家的构筑加一层"脱离船体也成立"的成长。</li>
 *   <li>{@link #SPECIAL} <b>特殊卡</b>：稀有的一次性/大牌，同样只从战利品来。</li>
 * </ul>
 *
 * <p>区分它们不是为了机制，而是为了<b>让玩家一眼看出卡组的骨架在哪</b>：
 * 操作卡=船体，增益/特殊卡=这一轮航行里攒下来的运气。</p>
 */
public enum CardKind {

    /** 操作卡：组件自带。 */
    OPERATION("操作"),

    /** 增益卡：战利品。 */
    BUFF("增益"),

    /** 特殊卡：战利品（稀有）。 */
    SPECIAL("特殊");

    private final String labelZh;

    CardKind(String labelZh) {
        this.labelZh = labelZh;
    }

    /** 中文标签（覆盖层用）。 */
    public String labelZh() {
        return labelZh;
    }
}
