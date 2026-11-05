package com.hysx.salvosequence.fleet;

import java.util.Locale;

/**
 * 船的职能。
 *
 * <p>改造前这是"模块槽位"（{@code Slot}）：槽位限制一个件能装在哪一段船体上，
 * 是硬约束。改造后整个棋盘不再分区，一艘船放在哪里都合法，
 * 所以职能只剩两件事：<b>画面上怎么涂</b>，以及<b>相邻加成按什么算</b>。</p>
 *
 * <p>因此本枚举没有 {@code accepts()} 之类的落位判定——那正是本次要拆掉的东西。</p>
 */
public enum ShipRole {

    /** 指挥：编队的心脏，掉光即败，不可拆。 */
    COMMAND("指挥", "COMMAND"),

    /** 主炮：大威力、低射速的炮舰。 */
    MAIN_GUN("主炮", "MAIN GUN"),

    /** 副炮：小威力、多频次的炮舰。 */
    SECONDARY_GUN("副炮", "SECONDARY"),

    /** 装甲：给全队挡伤的厚壳舰。 */
    ARMOR("装甲", "ARMOR"),

    /** 动力：补能量、加闪避的引擎舰。 */
    DRIVE("动力", "DRIVE"),

    /** 战术：抽牌与干扰的支援舰。 */
    TACTICS("战术", "TACTICS");

    private final String labelZh;
    private final String labelEn;

    ShipRole(String labelZh, String labelEn) {
        this.labelZh = labelZh;
        this.labelEn = labelEn;
    }

    /** 中文短标签（两字，面板上直接用）。 */
    public String labelZh() {
        return labelZh;
    }

    /** 英文标签。 */
    public String labelEn() {
        return labelEn;
    }

    /** 是不是炮舰（主炮 or 副炮）——相邻加成只认这两类。 */
    public boolean isGun() {
        return this == MAIN_GUN || this == SECONDARY_GUN;
    }

    /** 按 id / 枚举名 / 中文标签认一个职能，认不出返回 {@code null}。 */
    public static ShipRole byId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        for (ShipRole role : values()) {
            if (role.name().equals(key) || role.labelZh.equals(raw.trim())) {
                return role;
            }
        }
        return null;
    }
}
