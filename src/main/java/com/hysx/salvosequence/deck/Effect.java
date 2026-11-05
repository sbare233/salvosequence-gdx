package com.hysx.salvosequence.deck;

/**
 * 一张牌的效果。<b>数据，不含规则</b>：结算写在 {@code combat.Battle} 里。
 *
 * <p>用 sealed + record 而不是"脚本/字符串"：战斗侧可以对每类效果做模式匹配，
 * 机器人（{@code run.Pilot}）也能从效果结构直接读数值来做决策，
 * 不需要解析文本——数值和文案只有一处真相。</p>
 */
public sealed interface Effect {

    /**
     * 造成伤害。
     *
     * @param amount    单次伤害
     * @param hits      命中次数（多段；逐段判类型倍率、逐段减防御力、可被防空削段）
     * @param perCharge 每层蓄力追加的伤害（&gt;0 表示这张牌吃蓄力，打完清空蓄力）
     * @param pierce    是否穿透防御（无视防御力，护盾/格挡照挡）
     * @param type      伤害类型（决定对盾/对体的倍率）
     */
    record Damage(int amount, int hits, int perCharge, boolean pierce, DamageType type) implements Effect {
        public Damage {
            if (amount < 0 || hits <= 0 || perCharge < 0) {
                throw new IllegalArgumentException("伤害参数非法: " + amount + "/" + hits + "/" + perCharge);
            }
            if (type == null) {
                throw new IllegalArgumentException("伤害必须带类型");
            }
        }

        /** 无类型的旧写法默认动能（存量调用点零改动）。 */
        public Damage(int amount, int hits, int perCharge, boolean pierce) {
            this(amount, hits, perCharge, pierce, DamageType.KINETIC);
        }
    }

    /** 获得护盾（本回合有效，回合开始清空后重算）。 */
    record Block(int amount) implements Effect {
        public Block {
            if (amount < 0) {
                throw new IllegalArgumentException("护盾不能为负: " + amount);
            }
        }
    }

    /** 校射：下一次炮击伤害 +{@code bonus}，攻击卡消耗之。 */
    record Aim(int bonus) implements Effect {
        public Aim {
            if (bonus < 0) {
                throw new IllegalArgumentException("校射加成不能为负: " + bonus);
            }
        }
    }

    /** 蓄力：波动炮的充值层数。 */
    record Charge(int stacks) implements Effect {
        public Charge {
            if (stacks <= 0) {
                throw new IllegalArgumentException("蓄力层数必须为正: " + stacks);
            }
        }
    }

    /** 抽牌。 */
    record Draw(int count) implements Effect {
        public Draw {
            if (count <= 0) {
                throw new IllegalArgumentException("抽牌数必须为正: " + count);
            }
        }
    }

    /**
     * 参谋检索：从牌库挑 {@code count} 张攻击牌加入手牌（牌库没有攻击牌时结算方
     * 改为抽等量牌）。抽牌代表"参谋建议/战场机会"，检索代表"指挥官点名要什么"。
     */
    record Search(int count) implements Effect {
        public Search {
            if (count <= 0) {
                throw new IllegalArgumentException("检索数必须为正: " + count);
            }
        }
    }

    /** 立刻获得能量。 */
    record Energy(int amount) implements Effect {
        public Energy {
            if (amount <= 0) {
                throw new IllegalArgumentException("能量必须为正: " + amount);
            }
        }
    }

    /** 暴露：敌方受到伤害 +50%，持续 {@code turns} 个玩家回合。 */
    record Expose(int turns) implements Effect {
        public Expose {
            if (turns <= 0) {
                throw new IllegalArgumentException("暴露回合数必须为正: " + turns);
            }
        }
    }

    /** 修复结构值。 */
    record Repair(int hp) implements Effect {
        public Repair {
            if (hp <= 0) {
                throw new IllegalArgumentException("修复量必须为正: " + hp);
            }
        }
    }

    /**
     * 干扰（EVE 能量中和）：受击侧下回合能量 -{@code energy}。
     *
     * <p>对称效果：敌方打向我方时砍能量；我方打向敌方时无事发生（敌方没有能量，
     * 它的经济就是计划表本身）。给玩家的机制先问一句"敌人能不能用"——
     * 反过来这句也成立：没有能量可砍的卡不该出现在我方卡组里。</p>
     */
    record Drain(int energy) implements Effect {
        public Drain {
            if (energy <= 0) {
                throw new IllegalArgumentException("干扰量必须为正: " + energy);
            }
        }
    }

    // ------------------------------------------------------------------ 简写

    /** 单段伤害（动能）。 */
    static Damage hit(int amount) {
        return new Damage(amount, 1, 0, false);
    }

    /** 指定类型的单段伤害。 */
    static Damage hit(DamageType type, int amount) {
        return new Damage(amount, 1, 0, false, type);
    }

    /** 多段伤害（动能）。 */
    static Damage multi(int amount, int hits) {
        return new Damage(amount, hits, 0, false);
    }

    /** 指定类型的多段伤害。 */
    static Damage multi(DamageType type, int amount, int hits) {
        return new Damage(amount, hits, 0, false, type);
    }

    /** 单段穿透防御伤害（电离系：无视防御力，格挡照挡）。 */
    static Damage pierce(int amount) {
        return new Damage(amount, 1, 0, true, DamageType.ION);
    }

    /** 指定类型的穿透防御伤害（光矛：热能长矛）。 */
    static Damage pierce(DamageType type, int amount) {
        return new Damage(amount, 1, 0, true, type);
    }

    /** 波动炮：吃蓄力的一次大伤害（热能重型），打完清空蓄力。 */
    static Damage wave(int base, int perCharge) {
        return new Damage(base, 1, perCharge, false, DamageType.THERMAL);
    }

    /** 护盾简写。 */
    static Block block(int amount) {
        return new Block(amount);
    }

    /** 抽牌简写。 */
    static Draw draw(int count) {
        return new Draw(count);
    }

    /** 校射简写。 */
    static Aim aim(int bonus) {
        return new Aim(bonus);
    }

    /** 蓄力简写。 */
    static Charge charge(int stacks) {
        return new Charge(stacks);
    }
}
