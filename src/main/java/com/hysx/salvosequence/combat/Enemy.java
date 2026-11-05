package com.hysx.salvosequence.combat;

import com.hysx.salvosequence.deck.Card;
import com.hysx.salvosequence.deck.CardCatalog;
import com.hysx.salvosequence.fleet.Ship;

import java.util.List;

/**
 * 一艘敌舰的<b>模板</b>（不可变）。
 *
 * <h2>敌我同源</h2>
 * <p>敌舰不是第二套系统：它的血量/盾/甲/防空/齐射/卡全部来自它引用的 {@link #hull()}
 * （一张 {@code ShipCatalog} 里的船型）。战斗里敌方舰队就是<b>另一侧的卡牌玩家</b>：
 * 从船图卡牌生成牌库、每回合抽牌、用能量池出牌——玩家与敌人唯一的差别是
 * 玩家能构筑与升级，敌人是每波生成的编队（配合 {@code EnemyFormation} 的协同铺位）。</p>
 *
 * <p><b>数值对称（背包英雄口径）</b>：同一张船图，敌我血量与攻击完全一样——
 * 敌方不喝boss血、我方也不吃攻击加成。{@code hp} 字段留在记录上只为让测试能造靶船，
 * {@code EnemyCatalog} 里一律原样抄 {@code hull.hp()}。</p>
 *
 * <p>{@code plan} 是<b>测试脚本通道</b>：null/空 = 正常的活牌敌方（牌库+手牌+能量）；
 * 非空 = 按固定表打牌、不耗能量（只给测试造确定性靶船用，正式编队一律为空）。</p>
 *
 * @param id     唯一 id
 * @param nameZh 中文名（编队里直接用船图的舰名，让"同种船同数值"一眼可读）
 * @param nameEn 英文名
 * @param hp     结构值（= hull.hp()；测试造靶船时可以另给）
 * @param hull   船型（决定占地、外观、盾/防御力/防空/齐射/光环，以及它的「掉落」——掉落就是它自己）
 * @param plan   测试脚本（可空；正式编队一律空 = 活牌敌方）
 * @param textZh 一句话说明
 */
public record Enemy(String id, String nameZh, String nameEn, int hp, Ship hull,
                    List<Played> plan, String textZh) {

    public Enemy {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("敌舰必须有 id");
        }
        if (hull == null) {
            throw new IllegalArgumentException("敌舰 " + id + " 缺船型");
        }
        if (hp < 1) {
            throw new IllegalArgumentException("敌舰 " + id + " 结构值必须大于 0");
        }
        plan = plan == null ? List.of() : List.copyOf(plan);
        for (Played step : plan) {
            if (!CardCatalog.has(step.cardId())) {
                throw new IllegalStateException("敌舰 " + id + " 的脚本里有不存在的卡：" + step.cardId());
            }
        }
    }

    /** 有没有测试脚本（有 = 打固定表，没有 = 活牌）。 */
    public boolean scripted() {
        return !plan.isEmpty();
    }

    /**
     * 计划表上的一步：打哪张卡、要不要锁后排。
     *
     * @param cardId   卡 id（必须在 {@code CardCatalog} 里，构造时校验）
     * @param fromBack true = 锁后排（从我方队尾开始承伤）
     */
    public record Played(String cardId, boolean fromBack) {

        /** 这一步要打出的卡。 */
        public Card card() {
            return CardCatalog.byId(cardId);
        }
    }

    /** 第 {@code index} 回合的计划（按循环取模，永远有答案）。 */
    public Played stepAt(int index) {
        return plan.get(Math.floorMod(index, plan.size()));
    }

    /** 计划循环长度。 */
    public int stepCount() {
        return plan.size();
    }

    /** 战场上的船型（决定占地与外观；掉落也是它）。 */
    public Ship image() {
        return hull;
    }

    /** 改名（编队里区分同型船的场合已经不需要了，保留给调试）。 */
    public Enemy renamed(String newNameZh) {
        return new Enemy(id, newNameZh, nameEn, hp, hull, plan, textZh);
    }

    /** 战场占地宽。 */
    public int w() {
        return hull.w();
    }

    /** 战场占地高。 */
    public int h() {
        return hull.h();
    }

    /** 占地格数。 */
    public int cells() {
        return hull.cells();
    }

    /**
     * 计划步骤的读数（意图面板）：卡名 + 类型标 + 段数（+ 费用）（+ 锁后排）。
     *
     * <p>从卡面自动生成，不再手写意图文案——类型标是 ASCII（K/T/E/I），点阵字库能画；
     * 费用跟着卡面走，玩家能读出"这发打不打得出来"（敌方也要付能量）。</p>
     */
    public String labelZh(Played step) {
        Card card = step.card();
        StringBuilder sb = new StringBuilder(card.nameZh());
        if (card.baseDamage() > 0) {
            sb.append(' ').append(card.damageType().tag());
            int hits = card.hits();
            if (hits > 1) {
                sb.append(' ').append(hits).append('×').append(card.baseDamage() / hits);
            } else {
                sb.append(' ').append(card.baseDamage());
            }
        }
        if (card.cost() > 0) {
            sb.append(' ').append(card.cost()).append("费");
        }
        if (step.fromBack()) {
            sb.append(" 锁后排");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "Enemy[" + nameZh + " hp" + hp + " " + hull.w() + "x" + hull.h() + "]";
    }
}
