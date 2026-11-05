package com.hysx.salvosequence.run;

import com.hysx.salvosequence.deck.Card;
import com.hysx.salvosequence.fleet.Ship;

/**
 * 一条战利品。
 *
 * <p>改造前的战利品只有两种：<b>组件</b>（装到船体上）与<b>卡</b>。
 * 改造后第一种拆成两种：<b>编入一艘新船</b>、或<b>把已有的同型船升一级</b>。
 * 两者的区别是战术性的——新船扩棋盘、升级只加这张卡的倍率，
 * 但升级不需要找位置，也永远不会把棋盘塞满。</p>
 */
public record Reward(Kind kind, Ship ship, Card card) {

    /** 战利品类型。 */
    public enum Kind {

        /** 编入一艘新船。 */
        SHIP("编入", "ENLIST"),

        /** 同型船升一级。 */
        UPGRADE("强化", "UPGRADE"),

        /** 直接进卡组的额外卡。 */
        CARD("卡", "CARD");

        private final String labelZh;
        private final String labelEn;

        Kind(String labelZh, String labelEn) {
            this.labelZh = labelZh;
            this.labelEn = labelEn;
        }

        /** 中文标签。 */
        public String labelZh() {
            return labelZh;
        }

        /** 英文标签。 */
        public String labelEn() {
            return labelEn;
        }
    }

    public Reward {
        if (kind == null) {
            throw new IllegalArgumentException("战利品必须有类型");
        }
        if ((kind == Kind.CARD) == (card == null)) {
            throw new IllegalArgumentException("卡战利品必须带卡，其他类型不能带卡");
        }
        if ((kind != Kind.CARD) == (ship == null)) {
            throw new IllegalArgumentException("船战利品必须带船，卡战利品不能带船");
        }
    }

    /** 编入一艘新船。 */
    public static Reward ship(Ship ship) {
        return new Reward(Kind.SHIP, ship, null);
    }

    /** 同型船升一级。 */
    public static Reward upgrade(Ship ship) {
        return new Reward(Kind.UPGRADE, ship, null);
    }

    /** 额外卡。 */
    public static Reward card(Card card) {
        return new Reward(Kind.CARD, null, card);
    }

    /** 一行标题。 */
    public String titleZh() {
        return switch (kind) {
            case SHIP -> "编入「" + ship.nameZh() + "」";
            case UPGRADE -> "强化「" + ship.nameZh() + "」";
            case CARD -> "卡牌「" + card.nameZh() + "」";
        };
    }

    /** 英文副标题。 */
    public String subtitleEn() {
        return switch (kind) {
            case SHIP -> ship.nameEn() + " ×1";
            case UPGRADE -> ship.nameEn() + " LV+1";
            case CARD -> card.nameEn() + " COST " + card.cost();
        };
    }

    /** 一句话说明。 */
    public String textZh() {
        return switch (kind) {
            case SHIP -> "编入舰队：" + ship.textZh();
            case UPGRADE -> "同型船合为一级，" + ship.nameZh() + " 的卡伤害倍率 +1。";
            case CARD -> card.textZh();
        };
    }

    /** 占地宽（卡战利品返回 0）。 */
    public int w() {
        return ship == null ? 0 : ship.w();
    }

    /** 占地高（卡战利品返回 0）。 */
    public int h() {
        return ship == null ? 0 : ship.h();
    }

    @Override
    public String toString() {
        return "Reward[" + kind + " " + (ship != null ? ship.id() : card.id()) + "]";
    }
}
