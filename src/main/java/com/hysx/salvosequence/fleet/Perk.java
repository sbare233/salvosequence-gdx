package com.hysx.salvosequence.fleet;

import java.util.List;
import java.util.Locale;

/**
 * 舰船光环（背包英雄式的属性叠加）：一艘船带着的光环，按<b>生效范围</b>把加成叠到范围内的船上，
 * 对基础属性和卡牌结算同时生效。多个同种光环照常叠加——摆位就是构筑。
 *
 * <h2>两类光环</h2>
 * <ul>
 *   <li><b>属性光环</b>（{@code HP/SHIELD/PLATE/FLAK/VOLLEY}）：直接涨范围内友舰的派生数值；</li>
 *   <li><b>效果光环</b>（{@code REPAIR/SPOT/SHIELD_LINK/MAGAZINE/ENERGY/DRAW}）：
 *       在战斗结算或回合流程里兑现——损管修血、火控加伤、护盾连接扩散、弹夹缩短装填、
 *       能量与过牌抬全队资源。</li>
 * </ul>
 *
 * <p>敌我同源：敌方棋盘同样是一支 {@link Fleet}，同一套聚合公式——敌人摆出好阵型也会变强。</p>
 */
public record Perk(Kind kind, int value, Range range) {

    public Perk {
        if (kind == null) {
            throw new IllegalArgumentException("光环必须带类型");
        }
        if (value < 1) {
            throw new IllegalArgumentException("光环 " + kind + " 的数值必须为正: " + value);
        }
        if (range == null) {
            throw new IllegalArgumentException("光环 " + kind + " 必须带生效范围");
        }
        if ((kind == Kind.ENERGY || kind == Kind.DRAW) && range != Range.FLEET) {
            throw new IllegalArgumentException("能量/过牌是全队资源，只能是全队范围");
        }
    }

    /** 光环类型。 */
    public enum Kind {

        /** 结构值上限 +n。 */
        HP("结构"),
        /** 每回合护盾刷新 +n。 */
        SHIELD("护盾"),
        /** 防御力 +n（每段伤害先减这一格）。 */
        PLATE("防御"),
        /** 防空 +n（多段攻击的生效段数被砍）。 */
        FLAK("防空"),
        /** 齐射 +n。 */
        VOLLEY("齐射"),
        /** 全队能量上限 +n（只能是全队范围）。 */
        ENERGY("能量"),
        /** 全队每回合抽牌 +n（只能是全队范围）。 */
        DRAW("过牌"),
        /** 每回合开始：范围内友舰修复 n 结构值。 */
        REPAIR("损管"),
        /** 范围内友舰的攻击卡 +n 伤害。 */
        SPOT("火控"),
        /** 护盾连接：打出护盾卡时，范围内每艘友舰额外 +n 护盾。 */
        SHIELD_LINK("盾链"),
        /** 弹夹：范围内友舰的卡进装填区时，装填回合数 −n（最低 0）。 */
        MAGAZINE("弹夹");

        private final String labelZh;

        Kind(String labelZh) {
            this.labelZh = labelZh;
        }

        /** 中文名（面板/表格用）。 */
        public String labelZh() {
            return labelZh;
        }
    }

    /** 生效范围。 */
    public enum Range {

        /** 只对携带舰自己生效。 */
        SELF("自身"),
        /** 边贴相邻（共边，不含斜角）。 */
        CROSS("十字"),
        /** 贴邻（含斜角，即包围盒间隙 ≤1）。 */
        RING("周围"),
        /** 同一排（y 区间有重叠）的所有友舰。 */
        ROW("同排"),
        /** 全队。 */
        FLEET("全队");

        private final String labelZh;

        Range(String labelZh) {
            this.labelZh = labelZh;
        }

        /** 中文名（面板/表格用）。 */
        public String labelZh() {
            return labelZh;
        }
    }

    /**
     * 一组光环叠出来的总量。
     *
     * @param hp         结构值上限加成
     * @param shield     护盾刷新加成
     * @param plate      防御力加成
     * @param flak       防空加成
     * @param volley     齐射加成
     * @param energy     全队能量加成
     * @param draw       全队抽牌加成
     * @param repair     每回合修血
     * @param spot       攻击卡加伤
     * @param shieldLink 护盾连接扩散量
     * @param magazine   弹夹（装填缩短）
     */
    public record Totals(int hp, int shield, int plate, int flak, int volley, int energy, int draw,
                         int repair, int spot, int shieldLink, int magazine) {

        /** 什么都没有。 */
        public static final Totals NONE = new Totals(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        /** 叠上一条光环。 */
        public Totals add(Perk perk) {
            return switch (perk.kind()) {
                case HP -> new Totals(hp + perk.value(), shield, plate, flak, volley, energy, draw,
                        repair, spot, shieldLink, magazine);
                case SHIELD -> new Totals(hp, shield + perk.value(), plate, flak, volley, energy, draw,
                        repair, spot, shieldLink, magazine);
                case PLATE -> new Totals(hp, shield, plate + perk.value(), flak, volley, energy, draw,
                        repair, spot, shieldLink, magazine);
                case FLAK -> new Totals(hp, shield, plate, flak + perk.value(), volley, energy, draw,
                        repair, spot, shieldLink, magazine);
                case VOLLEY -> new Totals(hp, shield, plate, flak, volley + perk.value(), energy, draw,
                        repair, spot, shieldLink, magazine);
                case ENERGY -> new Totals(hp, shield, plate, flak, volley, energy + perk.value(), draw,
                        repair, spot, shieldLink, magazine);
                case DRAW -> new Totals(hp, shield, plate, flak, volley, energy, draw + perk.value(),
                        repair, spot, shieldLink, magazine);
                case REPAIR -> new Totals(hp, shield, plate, flak, volley, energy, draw,
                        repair + perk.value(), spot, shieldLink, magazine);
                case SPOT -> new Totals(hp, shield, plate, flak, volley, energy, draw,
                        repair, spot + perk.value(), shieldLink, magazine);
                case SHIELD_LINK -> new Totals(hp, shield, plate, flak, volley, energy, draw,
                        repair, spot, shieldLink + perk.value(), magazine);
                case MAGAZINE -> new Totals(hp, shield, plate, flak, volley, energy, draw,
                        repair, spot, shieldLink, magazine + perk.value());
            };
        }

        /** 叠上另一组总量。 */
        public Totals add(Totals other) {
            return new Totals(hp + other.hp, shield + other.shield, plate + other.plate,
                    flak + other.flak, volley + other.volley, energy + other.energy,
                    draw + other.draw, repair + other.repair, spot + other.spot,
                    shieldLink + other.shieldLink, magazine + other.magazine);
        }

        /** 全零判定。 */
        public boolean none() {
            return hp == 0 && shield == 0 && plate == 0 && flak == 0 && volley == 0
                    && energy == 0 && draw == 0 && repair == 0 && spot == 0
                    && shieldLink == 0 && magazine == 0;
        }

        /** 阵型评分用：战斗里越值钱的权重越高。 */
        public int combatScore() {
            return hp + 2 * (shield + plate + flak + volley + repair + spot + shieldLink + magazine);
        }

        /** 面板读数，如 {@code 结构+2 护盾+1}；没有加成返回空串。 */
        public String labelZh() {
            StringBuilder sb = new StringBuilder();
            append(sb, "结构", hp);
            append(sb, "护盾", shield);
            append(sb, "防御", plate);
            append(sb, "防空", flak);
            append(sb, "齐射", volley);
            append(sb, "能量", energy);
            append(sb, "过牌", draw);
            append(sb, "损管", repair);
            append(sb, "火控", spot);
            append(sb, "盾链", shieldLink);
            append(sb, "弹夹", magazine);
            return sb.toString();
        }

        private static void append(StringBuilder sb, String label, int value) {
            if (value != 0) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(label).append('+').append(value);
            }
        }

        @Override
        public String toString() {
            return labelZh().toLowerCase(Locale.ROOT);
        }
    }

    /** 把一组光环逐条列成面板文本：{@code 结构+2(十字) 齐射+1(十字)}。 */
    public static String labelsOf(List<Perk> perks) {
        StringBuilder sb = new StringBuilder();
        for (Perk perk : perks) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(perk.kind().labelZh()).append('+').append(perk.value())
                    .append('(').append(perk.range().labelZh()).append(')');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "Perk[" + kind.labelZh() + "+" + value + " " + range.labelZh() + "]";
    }
}
