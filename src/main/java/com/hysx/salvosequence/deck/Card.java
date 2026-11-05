package com.hysx.salvosequence.deck;

import java.util.List;

/**
 * 一张牌（纯数据，不可变）。
 *
 * <h2>为什么卡牌不持有"属于哪个组件"</h2>
 * <p>组件 → 卡 的关系是<b>按 id 引用</b>的（{@code fleet.Component#cardIds()}），
 * 组件被拆掉时它的卡自然不再进卡组，而卡牌自身不需要知道谁带它。
 * 这样同一张"力场护盾"可以被主装甲带和偏导装甲同时引用，
 * 掉落表也不需要为每个敌人维护一份卡牌列表。</p>
 *
 * <h2>装填（{@link #reload()}）</h2>
 * <p>打出后进"装填区"，等 N 个玩家回合才回弃牌堆。它不是冷却条：
 * 装填期间这张牌<b>根本不在可抽的牌堆里</b>，所以重炮的代价是
 * "这一轮剩下的时间里它不参与循环"，而不是"等一会儿又能打"。</p>
 *
 * @param id      稳定标识
 * @param nameZh   中文名
 * @param nameEn   英文名（点阵字库只认 ASCII）
 * @param kind     分类（操作/增益/特殊）
 * @param cost     能量费用
 * @param reload   装填回合数（0 = 打完直接进弃牌堆）
 * @param effects  效果列表（按顺序结算）
 * @param textZh   给玩家看的说明（一句话）
 */
public record Card(
        String id,
        String nameZh,
        String nameEn,
        CardKind kind,
        int cost,
        int reload,
        List<Effect> effects,
        String textZh) {

    public Card {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("卡牌必须有 id");
        }
        if (nameZh == null || nameZh.isBlank() || nameEn == null || nameEn.isBlank()) {
            throw new IllegalArgumentException("卡牌 " + id + " 必须有中英文名");
        }
        if (kind == null) {
            throw new IllegalArgumentException("卡牌 " + id + " 必须有分类");
        }
        if (cost < 0) {
            throw new IllegalArgumentException("卡牌 " + id + " 的费用不能为负");
        }
        if (reload < 0) {
            throw new IllegalArgumentException("卡牌 " + id + " 的装填不能为负");
        }
        if (effects == null || effects.isEmpty()) {
            throw new IllegalArgumentException("卡牌 " + id + " 至少要有一个效果");
        }
        effects = List.copyOf(effects);
        if (textZh == null) {
            textZh = "";
        }
    }

    /** 单张牌的裸伤害（不含校射/蓄力/暴露加成），给排序与机器人用。 */
    public int baseDamage() {
        int total = 0;
        for (Effect e : effects) {
            if (e instanceof Effect.Damage d) {
                total += d.amount() * d.hits();
            }
        }
        return total;
    }

    /** 多段数（伤害效果里最大的命中次数；没有伤害就是 0）。 */
    public int hits() {
        int best = 0;
        for (Effect e : effects) {
            if (e instanceof Effect.Damage d) {
                best = Math.max(best, d.hits());
            }
        }
        return best;
    }

    /** 伤害类型（没有伤害效果的卡返回 null，比如护盾/抽牌）。 */
    public DamageType damageType() {
        for (Effect e : effects) {
            if (e instanceof Effect.Damage d) {
                return d.type();
            }
        }
        return null;
    }

    /** 单张牌的护盾量。 */
    public int blockValue() {
        int total = 0;
        for (Effect e : effects) {
            if (e instanceof Effect.Block b) {
                total += b.amount();
            }
        }
        return total;
    }

    /** 吃蓄力的幅度（每层蓄力加多少伤害，0 = 不吃蓄力）。 */
    public int perCharge() {
        int best = 0;
        for (Effect e : effects) {
            if (e instanceof Effect.Damage d) {
                best = Math.max(best, d.perCharge());
            }
        }
        return best;
    }

    /** 是否穿透防御（无视防御力，格挡照挡）。 */
    public boolean pierce() {
        for (Effect e : effects) {
            if (e instanceof Effect.Damage d && d.pierce()) {
                return true;
            }
        }
        return false;
    }

    /** 是不是纯输出牌（机器人用来判断"该不该留能量"）。 */
    public boolean isAttack() {
        return baseDamage() > 0 || perCharge() > 0;
    }

    /** 装填是不是"重"（≥2 回合），机器人据此避免在残局乱开重炮。 */
    public boolean heavyReload() {
        return reload >= 2;
    }

    /** 调试用一行摘要。 */
    public String summary() {
        return String.format(java.util.Locale.ROOT, "%-14s %-10s cost=%d reload=%d dmg=%d block=%d",
                id, kind.labelZh(), cost, reload, baseDamage(), blockValue());
    }
}
