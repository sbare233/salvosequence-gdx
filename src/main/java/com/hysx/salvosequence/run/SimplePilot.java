package com.hysx.salvosequence.run;

import com.hysx.salvosequence.combat.Battle;
import com.hysx.salvosequence.deck.Card;
import com.hysx.salvosequence.deck.CardCatalog;
import com.hysx.salvosequence.deck.Effect;
import com.hysx.salvosequence.fleet.Fleet;
import com.hysx.salvosequence.fleet.FleetUnit;
import com.hysx.salvosequence.fleet.Ship;
import com.hysx.salvosequence.fleet.ShipRole;

import java.util.List;
import java.util.Random;

/**
 * 三种"玩家的玩法风格"，用来回答"这个循环能不能被不同的打法玩出不同的结果"。
 *
 * <p>刻意做得简单、可解释——它能跑通的道理，人类玩家五秒就能想明白；
 * 它跑不通的，人类玩家多半也玩不下去。</p>
 *
 * <ul>
 *   <li><b>DPS（莽）</b>：只打伤害最高的牌，不防御，不用技能；</li>
 *   <li><b>TANK（稳）</b>：先挡满这回合的来袭，再输出；能挡的船优先编进来；</li>
 *   <li><b>SMART（算）</b>：斩杀优先 → 快死了必须先挡 → 算性价比 →
 *       敌舰快死时避免把自己装填锁死。</li>
 * </ul>
 *
 * <p>改造后战利品是"编入一艘船"，所以这里还要回答"这艘船值不值得编、编哪儿"：
 * 挨着别的船有相邻加成，所以落点按相邻加成与前后排位置打分。</p>
 */
public final class SimplePilot implements Pilot {

    /** 风格。 */
    public enum Style {
        /** 莽：堆伤害。 */
        DPS("莽", "DPS"),
        /** 稳：先挡后打。 */
        TANK("稳", "TANK"),
        /** 算：综合性价比。 */
        SMART("算", "SMART");

        private final String zh;
        private final String en;

        Style(String zh, String en) {
            this.zh = zh;
            this.en = en;
        }

        public String zh() {
            return zh;
        }

        public String en() {
            return en;
        }
    }

    private final Style style;
    private final Random rng;

    public SimplePilot(Style style, long seed) {
        this.style = style;
        this.rng = new Random(seed);
    }

    public Style style() {
        return style;
    }

    @Override
    public String nameZh() {
        return style.zh();
    }

    @Override
    public String nameEn() {
        return style.en();
    }

    // ------------------------------------------------------------------ 出牌

    @Override
    public int chooseCard(Battle b) {
        List<Card> hand = b.hand();
        if (hand.isEmpty() || b.energy() <= 0) {
            return -1;
        }
        int lethal = findLethal(b, hand);
        if (lethal >= 0) {
            return lethal;
        }
        return switch (style) {
            case DPS -> dpsPick(b, hand);
            case TANK -> tankPick(b, hand);
            case SMART -> smartPick(b, hand);
        };
    }

    /** 莽：只在"这回合要疼"的时候才挡，否则一味输出。 */
    private int dpsPick(Battle b, List<Card> hand) {
        int uncovered = Math.max(0, b.nextIncoming() - b.block());
        // 真人也怕疼：掉血超过三成就随手挡一下，只有"必死"才叫致命
        boolean hurts = uncovered >= b.hp() * 0.35 || uncovered >= b.hp();
        if (hurts) {
            int best = -1;
            int bestBlock = 0;
            for (int i = 0; i < hand.size(); i++) {
                Card c = hand.get(i);
                if (c.cost() > b.energy()) {
                    continue;
                }
                if (c.blockValue() > bestBlock) {
                    bestBlock = c.blockValue();
                    best = i;
                }
            }
            if (best >= 0 && b.energy() > 1) {
                return best;
            }
        }
        return bestDamage(b, hand, -1);
    }

    /** 能直接打死的牌（优先不打那种装填 3 回合的，除非它就是唯一解）。 */
    private int findLethal(Battle b, List<Card> hand) {
        int best = -1;
        int bestReload = Integer.MAX_VALUE;
        for (int i = 0; i < hand.size(); i++) {
            Card c = hand.get(i);
            if (c.cost() > b.energy()) {
                continue;
            }
            int dmg = estimatedDamage(b, c);
            if (dmg >= b.enemyHp() && c.reload() < bestReload) {
                best = i;
                bestReload = c.reload();
            }
        }
        return best;
    }

    private int bestDamage(Battle b, List<Card> hand, int avoid) {
        int best = -1;
        int bestDmg = 0;
        for (int i = 0; i < hand.size(); i++) {
            if (i == avoid) {
                continue;
            }
            Card c = hand.get(i);
            if (c.cost() > b.energy()) {
                continue;
            }
            int dmg = estimatedDamage(b, c);
            if (dmg > bestDmg) {
                bestDmg = dmg;
                best = i;
            }
        }
        return best;
    }

    private int tankPick(Battle b, List<Card> hand) {
        int incoming = b.nextIncoming();
        int uncovered = Math.max(0, incoming - b.block());
        if (uncovered > 0) {
            int best = -1;
            int bestBlock = 0;
            for (int i = 0; i < hand.size(); i++) {
                Card c = hand.get(i);
                if (c.cost() > b.energy()) {
                    continue;
                }
                int blk = c.blockValue();
                if (blk > bestBlock) {
                    bestBlock = blk;
                    best = i;
                }
            }
            if (best >= 0) {
                return best;
            }
        }
        return bestDamage(b, hand, -1);
    }

    private int smartPick(Battle b, List<Card> hand) {
        int incoming = b.nextIncoming();
        int uncovered = Math.max(0, incoming - b.block());
        boolean dying = uncovered >= b.hp();
        int best = -1;
        double bestScore = 0;
        for (int i = 0; i < hand.size(); i++) {
            Card c = hand.get(i);
            if (c.cost() > b.energy()) {
                continue;
            }
            double score = score(b, c, uncovered, dying);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private double score(Battle b, Card c, int uncovered, boolean dying) {
        int dmg = estimatedDamage(b, c);
        int blockUsed = Math.min(c.blockValue(), Math.max(uncovered, 0));
        double s = dmg + blockUsed * 0.8;
        if (dying) {
            // 这回合就要被打死：格挡的每一分都值钱
            s += Math.min(c.blockValue(), uncovered) * 2.0;
        }
        // 铺垫价值：校射 / 蓄力现在拿不到伤害，但它们能把后面那张大牌推上去。
        // 只按"当期性价比"算，机器人永远不会为 校射→重炮 付钱（这就是它的盲区）；
        // 这里把兑现价值打折计进来。
        s += setupCredit(b, c);
        if (hasExpose(c)) {
            s += 12.0;
        }
        if (hasDraw(c)) {
            s += 3.0;
        }
        if (c.reload() > 0 && b.enemyHp() - dmg <= 10) {
            // 敌舰就快沉了，别在这时候把自己一张牌锁进装填区
            s -= 6.0;
        }
        return s / Math.max(1, c.cost());
    }

    /**
     * 铺垫折现：这张"现在不打伤害"的牌，大约能给以后的攻击牌加多少。
     *
     * <p>校射 +8 只有在后面真有一张能打出去的炮击时才值钱，
     * 所以按"手上能打出的最高伤害"打个折扣（0.55）计分；
     * 蓄力则要看卡组里有没有吃蓄力的牌。</p>
     */
    private double setupCredit(Battle b, Card c) {
        int best = bestAffordableDamage(b);
        if (best <= 0) {
            return 0;
        }
        double credit = 0;
        int aim = aimValue(c);
        if (aim > 0) {
            credit += Math.min(aim, best) * 0.55;
        }
        int charge = chargeValue(c);
        if (charge > 0 && hasChargePayoff(b)) {
            credit += Math.min(charge * 4, best) * 0.45;
        }
        return credit;
    }

    /** 手上现在能打出的最大单张伤害（含倍率/相邻加成/齐射）。 */
    private int bestAffordableDamage(Battle b) {
        int best = 0;
        for (Card c : b.hand()) {
            if (c.cost() > b.energy()) {
                continue;
            }
            best = Math.max(best, estimatedDamage(b, c));
        }
        return best;
    }

    private boolean affordableAttack(Battle b) {
        return bestAffordableDamage(b) > 0;
    }

    /** 卡组里有没有吃蓄力的牌（波动炮那种）。 */
    private boolean hasChargePayoff(Battle b) {
        for (Card c : b.hand()) {
            if (c.perCharge() > 0) {
                return true;
            }
        }
        for (Card c : b.drawPile()) {
            if (c.perCharge() > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 这张牌大概能打多少（按新管线近似：类型倍率 → 防空削段 → 逐段减防御力 → 盾）。
     * 和 {@code Battle} 的结算保持一致的近似，够机器人做比较就行。
     */
    private int estimatedDamage(Battle b, Card c) {
        int base = c.baseDamage();
        if (base == 0) {
            return 0;
        }
        int perCharge = c.perCharge();
        if (perCharge > 0) {
            base += perCharge * b.chargeStacks();
        }
        Fleet fleet = b.fleet();
        int mult = b.multiplierOfCardId(c.id());
        if (mult < 1) {
            mult = 1;
        }
        int total = base * mult + fleet.adjacencyDamageBonus(c.id()) + fleet.volleyFor(c.id());
        if (b.aim() > 0) {
            total += b.aim();
        }
        if (b.expose() > 0) {
            total = (int) Math.floor(total * Battle.EXPOSE_MULTIPLIER);
        }
        // 类型倍率：穿透直取 vs 体，否则看目标此刻最外层（盾亮着 vs 盾，空了 vs 体）
        total = (int) Math.floor(total * b.typeMultiplierOf(c));
        // 防空砍尾段（被拦的弹直接损失）+ 逐段减防御力（每段最低 1）
        int hits = Math.max(1, c.hits());
        int effHits = Math.max(1, hits - b.targetFlak());
        int seg = total / hits;
        int rem = total % hits;
        int reduced = 0;
        for (int i = 0; i < effHits; i++) {
            reduced += Math.max(1, seg + (i < rem ? 1 : 0) - b.targetPlate());
        }
        // 盾最后吸收（穿透跳过）
        return c.pierce() ? reduced : Math.max(0, reduced - b.enemyBlock());
    }

    @Override
    public boolean useSkill(Battle b) {
        if (b.skillUsed() || b.energy() < Battle.SKILL_COST) {
            return false;
        }
        return switch (style) {
            case DPS -> false;
            case TANK -> b.nextIncoming() > b.block() + 4 && b.energy() >= 3;
            case SMART -> {
                boolean worth = affordableAttack(b) && b.energy() >= Battle.SKILL_COST + 1;
                yield worth || b.hp() <= b.maxHp() / 3;
            }
        };
    }

    // ------------------------------------------------------------------ 战利品与编排

    @Override
    public int chooseReward(Run run, List<Reward> options) {
        Fleet fleet = run.fleet();
        int best = -1;
        double bestScore = 0;
        for (int i = 0; i < options.size(); i++) {
            Reward reward = options.get(i);
            switch (reward.kind()) {
                case CARD -> {
                    double score = cardValue(reward.card()) * 1.4;
                    if (score > bestScore) {
                        bestScore = score;
                        best = i;
                    }
                }
                case UPGRADE -> {
                    FleetUnit unit = fleet == null ? null : fleet.unit(reward.ship().id());
                    if (unit == null) {
                        continue;
                    }
                    // 升级：同型再叠一层，卡伤害与齐射都涨，值是"这件 × 一份"
                    double score = shipValue(reward.ship()) * 0.9 + 4;
                    if (score > bestScore) {
                        bestScore = score;
                        best = i;
                    }
                }
                case SHIP -> {
                    List<FleetUnit> spots = run.candidatePlacements(reward.ship());
                    double gain = placementGain(fleet, reward.ship(), spots);
                    if (gain > bestScore) {
                        bestScore = gain;
                        best = i;
                    }
                }
            }
        }
        return best;
    }

    @Override
    public int choosePlacement(Fleet fleet, Ship incoming, List<FleetUnit> candidates) {
        int best = -1;
        double bestGain = 0.5;
        for (int i = 0; i < candidates.size(); i++) {
            double gain = gainOf(fleet, incoming, candidates.get(i));
            if (gain > bestGain) {
                bestGain = gain;
                best = i;
            }
        }
        return best;
    }

    /** 这批候选里最好的一种编法值多少（不够划算就是 0）。 */
    private double placementGain(Fleet fleet, Ship incoming, List<FleetUnit> candidates) {
        if (candidates.isEmpty()) {
            return 0;
        }
        int index = choosePlacement(fleet, incoming, candidates);
        return index < 0 ? 0 : gainOf(fleet, incoming, candidates.get(index));
    }

    /**
     * 把这艘船编在这个落点值多少。
     *
     * <p>编入是净增一艘船（不顶掉任何东西），所以基础值就是这艘船本身；
     * 落点会改变相邻加成，所以再按"编进去之后相邻加成涨了多少"加减一笔。</p>
     */
    private double gainOf(Fleet fleet, Ship incoming, FleetUnit target) {
        double gain = shipValue(incoming) * 0.6;
        if (fleet == null) {
            return gain;
        }
        Fleet after = fleet.withUnit(target);
        if (after == null) {
            return gain;
        }
        int beforeHp = fleet.adjacencyHpBonusTotal();
        int afterHp = after.adjacencyHpBonusTotal();
        int beforeVolley = fleet.adjacencyVolleyBonusTotal();
        int afterVolley = after.adjacencyVolleyBonusTotal();
        gain += (afterHp - beforeHp) * 1.2 + (afterVolley - beforeVolley) * 2.0;
        // 挨着船才好，孤零零摆一边加成吃不到
        if (fleet.board().neighbours(target).isEmpty()) {
            gain -= 2.5;
        }
        // 我方朝右：以舰体右边缘为前排，越靠右越挨打——脆船往左缩，肉船往右顶
        if (isSquishy(incoming)) {
            gain -= target.x2() * 0.35;
        } else {
            gain += target.x2() * 0.2;
        }
        return gain;
    }

    private boolean isSquishy(Ship ship) {
        return ship.hp() <= 8 && ship.armor() == 0;
    }

    /** 一艘船的综合价值（风格不同、权重不同）。 */
    private double shipValue(Ship s) {
        double cards = 0;
        for (String id : s.cardIds()) {
            cards += cardValue(CardCatalog.byId(id));
        }
        double volley = s.volley() * 1.4;
        return switch (style) {
            case DPS -> s.hp() * 0.12 + s.armor() * 1.2 + s.energy() * 8 + s.draw() * 3
                    + cards * 1.8 + volley;
            case TANK -> s.hp() * 0.45 + s.armor() * 3.0 + s.energy() * 5 + s.draw() * 2
                    + cards * 0.9 + volley * 0.6;
            case SMART -> s.hp() * 0.3 + s.armor() * 2.0 + s.energy() * 6 + s.draw() * 2.5
                    + cards * 1.3 + volley * 1.1;
        };
    }

    /** 一张卡的价值：单位能量的产出，装填长的打折。 */
    private double cardValue(Card c) {
        double raw = c.baseDamage() * 1.0 + c.blockValue() * 0.8;
        if (hasDraw(c)) {
            raw += 6;
        }
        if (aimValue(c) > 0) {
            raw += 5;
        }
        if (hasExpose(c)) {
            raw += 8;
        }
        if (chargeValue(c) > 0) {
            raw += 4;
        }
        return raw / Math.max(1, c.cost()) / (1.0 + c.reload() * 0.5);
    }

    // ------------------------------------------------------------------ 读效果

    private static int aimValue(Card c) {
        for (Effect e : c.effects()) {
            if (e instanceof Effect.Aim a) {
                return a.bonus();
            }
        }
        return 0;
    }

    private static int chargeValue(Card c) {
        for (Effect e : c.effects()) {
            if (e instanceof Effect.Charge ch) {
                return ch.stacks();
            }
        }
        return 0;
    }

    private static boolean hasExpose(Card c) {
        for (Effect e : c.effects()) {
            if (e instanceof Effect.Expose) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDraw(Card c) {
        for (Effect e : c.effects()) {
            if (e instanceof Effect.Draw) {
                return true;
            }
        }
        return false;
    }
}
