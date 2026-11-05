package com.hysx.salvosequence.combat;

import com.hysx.salvosequence.deck.Card;
import com.hysx.salvosequence.deck.CardCatalog;
import com.hysx.salvosequence.deck.DamageType;
import com.hysx.salvosequence.deck.Effect;
import com.hysx.salvosequence.fleet.Fleet;
import com.hysx.salvosequence.fleet.FleetUnit;
import com.hysx.salvosequence.fleet.Perk;
import com.hysx.salvosequence.fleet.Fleet;
import com.hysx.salvosequence.fleet.FleetOrders;
import com.hysx.salvosequence.fleet.FleetRoster;
import com.hysx.salvosequence.fleet.FleetUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 一场战斗。
 *
 * <p><b>改造前后最大的差别是"你在保护什么"</b>：改造前玩家这边只有一个血条（主力舰的结构值），
 * 僚机是挡箭牌；现在你有一整支舰队，每艘船都有自己的结构值，
 * 敌方的炮火按棋盘次序落在<b>前排（我方朝右，舰体右边缘最靠外）</b>，扣穿了继续往里打，<b>全队沉没才算输</b>。</p>
 *
 * <p>卡牌侧的口径也跟着变了：</p>
 * <ul>
 *   <li>倍率 = 携带这张卡的船的等级之和（同型两艘 = ×2，一艘 Lv3 = ×3）</li>
 *   <li>齐射 = 携带这张卡的船的齐射值之和（旧模型里是"参与舰种"的齐射）</li>
 *   <li>敌方随机换靶时一律重新挑"残血的那艘"</li>
 * </ul>
 */
public final class Battle {

    /** 舰长技能费用。 */
    public static final int SKILL_COST = 2;

    /** 舰长技能给的校射。 */
    public static final int SKILL_AIM = 10;

    /** 舰长技能给的护盾（给指挥舰）。 */
    public static final int SKILL_BLOCK = 4;

    /** 火控光环的加伤上限（一张卡最多吃这么多）。 */
    private static final int SPOT_CAP = com.hysx.salvosequence.fleet.Fleet.ADJACENT_SPOT_CAP;

    /** 手牌上限。 */
    public static final int HAND_LIMIT = 10;

    /** 暴露状态下的伤害倍率。 */
    public static final double EXPOSE_MULTIPLIER = 1.5;

    /** 系统损伤阈值：击沉我方一艘、且还有这么多余量在溢出，就追加连锁惩罚（BFG 暴击表）。 */
    public static final int SYSTEM_DAMAGE_THRESHOLD = 6;

    /** 防空的全队封顶：编队再多护卫舰，一次齐射最多也就被拦 2 发（敌我同一条）。 */
    public static final int FLAK_CAP = 2;


    /** 日志最多留几行。 */
    private static final int LOG_CAP = 96;

    /**
     * 一艘敌舰的运行时状态。
     */
    public static final class Foe {

        private final Enemy enemy;
        private final FleetUnit unit;
        private Perk.Totals perks;
        private int plate;
        private int flak;
        private int maxHp;
        private int hp;
        private int block;
        /** 自上次格挡刷新以来是否被打到过（护盾窗口，与我方同款）。 */
        private boolean hitSinceRefresh;
        private int planIndex;

        /** 协同链重导出：一位友军沉了，它提供的光环从我的数值里扣掉。 */
        void rederive() {
            plate = enemy.hull().plate() + perks.plate();
            flak = enemy.hull().flak() + perks.flak();
            maxHp = enemy.hp() + perks.hp();
            if (hp > maxHp) {
                hp = maxHp;
            }
            block = Math.min(block, shieldRefresh());
        }

        private Foe(Enemy enemy, FleetUnit unit, Perk.Totals perks) {
            this.enemy = enemy;
            this.unit = unit;
            this.perks = perks == null ? Perk.Totals.NONE : perks;
            this.plate = enemy.hull().plate() + this.perks.plate();
            this.flak = enemy.hull().flak() + this.perks.flak();
            this.maxHp = enemy.hp() + this.perks.hp();
            this.hp = this.maxHp;
            this.block = enemy.hull().armor() + this.perks.shield();
        }

        /** 模板。 */
        public Enemy enemy() {
            return enemy;
        }

        /** 棋盘上的那艘船（画外观用）。 */
        public FleetUnit unit() {
            return unit;
        }

        /** 舰名。 */
        public String nameZh() {
            return enemy.nameZh();
        }

        /**
         * 船型：盾/防御力/防空/齐射/卡全从它身上读——敌我同源，
         * 俘获这艘船就是把它编进舰队，不需要翻译。
         */
        public com.hysx.salvosequence.fleet.Ship hull() {
            return enemy.hull();
        }

        /** 阵型协同叠出来的光环总量（敌我同一套公式）。 */
        public Perk.Totals perks() {
            return perks;
        }

        /** 防御力（基础 + 光环）。 */
        public int plate() {
            return plate;
        }

        /** 防空（基础 + 光环）。 */
        public int flak() {
            return flak;
        }

        /** 每回合开局损管修血（损管光环罩着它才有）。 */
        public int repair() {
            return perks.repair();
        }

        /** 每回合格挡刷新量（船型盾值 + 光环）。 */
        public int shieldRefresh() {
            return enemy.hull().armor() + perks.shield();
        }

        /** 结构值上限。 */
        public int maxHp() {
            return maxHp;
        }

        /** 当前结构值。 */
        public int hp() {
            return hp;
        }

        /** 当前格挡。 */
        public int block() {
            return block;
        }

        /** 还在不在。 */
        public boolean alive() {
            return hp > 0;
        }

        /** 血条比例。 */
        public double hpRatio() {
            return maxHp <= 0 ? 0 : Math.max(0, Math.min(1, (double) hp / maxHp));
        }

        /** 计划表下标（读数的回合序号）。 */
        public int planIndex() {
            return planIndex;
        }

        /** 是不是测试脚本敌舰（固定表打牌、不耗能量）。 */
        public boolean scripted() {
            return enemy.scripted();
        }

        /** 下一步脚本（活牌敌方没有脚本，返回 null）。 */
        public Enemy.Played nextStep() {
            return enemy.plan().isEmpty() ? null : enemy.stepAt(planIndex);
        }

        /** 再下一步脚本。 */
        public Enemy.Played upcomingStep() {
            return enemy.plan().isEmpty() ? null : enemy.stepAt(planIndex + 1);
        }

        /** 下一步脚本的读数（活牌敌方返回空串——它的手牌你看不见，和你看不见对面的手牌对称）。 */
        public String nextLabelZh() {
            Enemy.Played step = nextStep();
            return step == null ? "" : enemy.labelZh(step);
        }

        /** 再下一步脚本的读数。 */
        public String upcomingLabelZh() {
            Enemy.Played step = upcomingStep();
            return step == null ? "" : enemy.labelZh(step);
        }
    }

    /** 战斗结果。 */
    public enum Result {ONGOING, WON, LOST}

    /**
     * 出牌的一侧。
     *
     * <p>敌我同源：同一条 resolve 管线，区别只在读数来源——
     * 倍率/齐射我方查 {@code Fleet}、敌方数"哪些敌舰带着这张卡"；
     * 敌方没有能量和手牌（计划表就是它的经济），也没有自由开火。</p>
     */
    private enum Side {
        PLAYER, FOE;

        Side opponent() {
            return this == PLAYER ? FOE : PLAYER;
        }
    }

    private Fleet fleet;
    private FleetRoster roster;
    /** 敌方编队也当成一支舰队——光环、倍率、齐射全走同一套派生公式（敌我同源）。 */
    private final Fleet enemyFleet;
    private final List<Foe> foes;
    private final List<Enemy> squad;
    private final Random rng;

    /** 本节点战术休整里禁掉的卡（不进牌库，换节点即失效）。 */
    private final java.util.Set<String> bannedCards;
    private final List<Card> drawPile = new ArrayList<>();
    private final List<Card> discardPile = new ArrayList<>();
    private final List<Loading> loadingZone = new ArrayList<>();
    private final List<Card> hand = new ArrayList<>();
    /** 敌方那侧的一整套牌（与玩家完全同构：牌库/弃牌/手牌/装填区）。 */
    private final List<Card> enemyDrawPile = new ArrayList<>();
    private final List<Card> enemyDiscard = new ArrayList<>();
    private final List<Card> enemyHand = new ArrayList<>();
    private final List<Loading> enemyLoading = new ArrayList<>();
    private final List<String> log = new ArrayList<>();

    private int energy;
    private int energyPenalty;
    private int aim;
    private int chargeStacks;
    /** 暴露：我方给敌方挂的受伤加深。 */
    private int foeExpose;
    /** 暴露：敌方给我方挂的受伤加深（对称机制）。 */
    private int fleetExpose;
    /** 敌方侧的校射/蓄力（计划表卡牌攒出来的，吃到一发伤害清空）。 */
    private int foeAim;
    private int foeCharge;
    /** 敌方舰队能量池：与我方同一套公式（每回合回满、不累积、付不起就蓄能）。 */
    private int enemyEnergy;
    private int enemyEnergyPenalty;
    private com.hysx.salvosequence.run.Captain.Effects captainFx =
            com.hysx.salvosequence.run.Captain.Effects.NONE;
    /** 舰长专属技（每场一次，S 键）；NONE 时退回旧的通用技能。 */
    private com.hysx.salvosequence.run.Captain.Signature signature =
            com.hysx.salvosequence.run.Captain.Signature.NONE;
    /** 专属技给全队加的固定火控（每次炮击 +n）。 */
    private int signatureSpot;
    /** 本回合已打出的攻击卡数（首击被动的判定）。 */
    private int attacksThisTurn;
    private int turn = 1;
    private int targetIndex = -1;
    private boolean skillUsed;
    /** 舰长专属技是否已用（每场一次，不随回合重置）。 */
    private boolean signatureUsed;
    /** 舰船主动技能（T5 脸面舰）：每场一次。 */
    private boolean shipSkillUsed;
    /** 过载齐射：本回合全队伤害加成（%）。 */
    private int shipDamagePct;
    /** 全体暴露：所有敌方受到伤害 +50%/层。 */
    private int allFoesExpose;
    private int playedThisTurn;
    private Result result = Result.ONGOING;
    /** 本场已念过断链清单的船（不重复念）。 */
    private final java.util.Set<String> chainLogged = new java.util.HashSet<>();
    /** 连续多少回合没抽到攻击牌（≥2 触发参谋部保底）。 */
    private int turnsWithoutAttack = 2;
    /**
     * 火力模式，<b>默认自由开火</b>（打沉后余量续打下一艘，×1.2）——
     * 敌方的伤害本来就沿我方队列溢出，默认集火会让玩家的大炮全浪费在击沉溢出上，
     * 这是纯不对称的劣势。集火（G 切换）留给点名：捏维修舰、锁后排那类目标。
     */
    private boolean freeFire = true;

    private int energyAvailable;
    private int energySpent;
    private int cardsDrawn;
    private int cardsPlayed;
    private int emptyTurns;
    private int loadingLockSum;
    private int damageDealt;
    private int damageTaken;

    /** 单场（一个敌人）。 */
    public Battle(Fleet fleet, Enemy enemy, Map<String, Integer> carriedHp, long seed) {
        this(fleet, List.of(enemy), carriedHp, seed);
    }

    /** 满血开打。 */
    public Battle(Fleet fleet, List<Enemy> squad, long seed) {
        this(fleet, squad, Map.of(), seed);
    }

    /**
     * 开打。
     *
     * @param fleet     舰队（棋盘 + 数值）
     * @param squad     敌方编队（同序摆上棋盘）
     * @param carriedHp 上一场剩下的结构值（船型 id → 剩下多少）
     */
    public Battle(Fleet fleet, List<Enemy> squad, Map<String, Integer> carriedHp, long seed) {
        this(fleet, squad, carriedHp, seed, null);
    }

    /**
     * 开打（带舰长被动）。
     *
     * <p>舰长效果必须在 {@link #startTurn()} 之前注入：首回合的能量/抽牌/再生/护盾
     * 都在这时结算，先开回合再设效果会白白丢掉第一回合的加成。</p>
     */
    public Battle(Fleet fleet, List<Enemy> squad, Map<String, Integer> carriedHp, long seed,
                  com.hysx.salvosequence.run.Captain.Effects fx) {
        this(fleet, squad, carriedHp, seed, fx, java.util.Set.of());
    }

    /**
     * 开打（本节点战术休整里禁掉的手牌不进牌库）。
     *
     * @param bannedCards 本节点内不再抽到的卡牌 id，null 等同空集
     */
    public Battle(Fleet fleet, List<Enemy> squad, Map<String, Integer> carriedHp, long seed,
                  com.hysx.salvosequence.run.Captain.Effects fx, java.util.Set<String> bannedCards) {
        this.bannedCards = bannedCards == null ? java.util.Set.of() : java.util.Set.copyOf(bannedCards);
        if (fleet == null || fleet.isEmpty()) {
            throw new IllegalArgumentException("舰队不能是空的");
        }
        if (squad == null || squad.isEmpty()) {
            throw new IllegalArgumentException("敌人不能是空的");
        }
        this.fleet = fleet;
        this.captainFx = fx == null ? com.hysx.salvosequence.run.Captain.Effects.NONE : fx;
        this.roster = FleetRoster.of(fleet, carriedHp == null ? Map.of() : carriedHp,
                captainFx.structure());
        this.squad = List.copyOf(squad);
        List<FleetUnit> placed = EnemyFormation.layout(this.squad);
        this.enemyFleet = Fleet.of("foe.line", squadName(), "ENEMY LINE", placed);
        List<Foe> built = new ArrayList<>(this.squad.size());
        for (int i = 0; i < this.squad.size(); i++) {
            built.add(new Foe(this.squad.get(i), placed.get(i), enemyFleet.perksOn(placed.get(i))));
        }
        this.foes = List.copyOf(built);
        this.rng = new Random(seed);
        for (Card card : fleet.deck()) {
            if (!this.bannedCards.contains(card.id())) {
                drawPile.add(card);
            }
        }
        Collections.shuffle(drawPile, rng);
        // 敌方牌库：从编队船图的卡牌生成，与我方同一套去重规则（Fleet.deck）
        for (Card card : enemyFleet.deck()) {
            enemyDrawPile.add(card);
        }
        Collections.shuffle(enemyDrawPile, rng);
        autoTarget();
        log("遭遇 " + squadName() + "：合计 " + enemyMaxHp() + " 结构值");
        log("我方 " + fleet.nameZh() + "：" + fleet.size() + " 艘，合计 " + roster.totalHp() + " 结构值");
        startTurn();
    }

    // ------------------------------------------------------------------ 读数

    /** 舰队定义。 */
    public Fleet fleet() {
        return fleet;
    }

    /** 舰队运行时状态（每艘船的血/盾）。 */
    public FleetRoster fleetRoster() {
        return roster;
    }

    /** 每艘船的运行状态。 */
    public List<FleetRoster.Unit> fleetUnits() {
        return roster.units();
    }

    /**
     * 战斗里变阵：把某艘船挪到新落位。
     *
     * <p>站位决定相邻加成，所以这既是操作也是战术：把装甲舰挪到炮舰身边能立刻多出齐射。
     * 掉过的结构值不会被这一下补回来。
     *
     * @return 挪不动（越界或压住别的船）返回 false
     */
    public boolean moveShip(String shipId, int x, int y) {
        if (!roster.reposition(shipId, x, y)) {
            return false;
        }
        fleet = roster.fleet();
        return true;
    }

    /** 敌方编队模板。 */
    public List<Enemy> squad() {
        return squad;
    }

    /** 敌方棋子。 */
    public List<Foe> foes() {
        return foes;
    }

    /** 当前目标。 */
    public Foe target() {
        return targetIndex >= 0 && targetIndex < foes.size() ? foes.get(targetIndex) : null;
    }

    /** 当前目标下标（-1 = 全灭）。 */
    public int targetIndex() {
        return targetIndex;
    }

    /** 还活着几艘敌舰。 */
    public int aliveCount() {
        int n = 0;
        for (Foe f : foes) {
            if (f.alive()) {
                n++;
            }
        }
        return n;
    }

    /** 选择目标（死的不让选）。 */
    public void selectTarget(int index) {
        if (index < 0 || index >= foes.size() || !foes.get(index).alive()) {
            return;
        }
        targetIndex = index;
        log("换目标：" + foes.get(index).nameZh());
    }

    /** 轮换目标。 */
    public void cycleTarget(int delta) {
        if (foes.isEmpty()) {
            return;
        }
        int start = Math.max(0, targetIndex);
        for (int step = 1; step <= foes.size(); step++) {
            int i = Math.floorMod(start + delta * step, foes.size());
            if (foes.get(i).alive()) {
                targetIndex = i;
                log("换目标：" + foes.get(i).nameZh());
                return;
            }
        }
    }

    /**
     * 自动挑"最前排"的那艘当目标：大炮不再默认浪费在残血杂兵上（手动换目标照旧）。
     *
     * <p>朝向是水平对射：我方在左、敌方在右，所以敌舰<b>靠左（x 小）</b>的一侧才是前排，
     * 最左那列先接敌；同列再按从上到下排。</p>
     */
    private void autoTarget() {
        int best = -1;
        for (int i = 0; i < foes.size(); i++) {
            Foe f = foes.get(i);
            if (!f.alive()) {
                continue;
            }
            if (best < 0 || frontOf(f) < frontOf(foes.get(best))) {
                best = i;
            }
        }
        if (best >= 0) {
            targetIndex = best;
        }
    }

    /** 前排次序：x 越小越靠前（敌舰朝左，玩家在左侧）；同列 y 小者先。 */
    private static int frontOf(Foe f) {
        FleetUnit u = f.unit();
        return u.x() * 100 + u.y();
    }

    /** 第一个还没沉的敌舰模板（战斗外的读数用）。 */
    public Enemy enemy() {
        Foe t = target();
        return t != null ? t.enemy() : squad.get(0);
    }

    /** 结果。 */
    public Result result() {
        return result;
    }

    /** 打完了。 */
    public boolean over() {
        return result != Result.ONGOING;
    }

    /** 第几回合（从 1 开始）。 */
    public int turn() {
        return turn;
    }

    /** 我方全队剩余结构值。 */
    public int hp() {
        return roster.totalHp();
    }

    /** 我方全队结构值上限。 */
    public int maxHp() {
        return roster.totalMaxHp();
    }

    /** 我方全队当前护盾。 */
    public int block() {
        return roster.totalBlock();
    }

    /** 指挥舰当前结构值。 */
    public int flagHp() {
        FleetRoster.Unit flag = roster.flag();
        return flag == null ? 0 : flag.hp();
    }

    /** 当前能量。 */
    public int energy() {
        return energy;
    }

    /** 每回合能量上限。 */
    public int maxEnergy() {
        return fleet.maxEnergy(aliveIds());
    }

    /** 校射层数。 */
    public int aim() {
        return aim;
    }

    /** 蓄力层数。 */
    public int chargeStacks() {
        return chargeStacks;
    }

    /** 本回合技能用过没。 */
    public boolean skillUsed() {
        return skillUsed;
    }

    /** 当前目标结构值。 */
    public int enemyHp() {
        return target() == null ? 0 : target().hp();
    }

    /** 当前目标结构值上限。 */
    public int enemyMaxHp() {
        return target() == null ? 0 : target().maxHp();
    }

    /** 当前目标格挡。 */
    public int enemyBlock() {
        return target() == null ? 0 : target().block();
    }

    /** 敌方全队剩余结构值。 */
    public int enemyTotalHp() {
        int sum = 0;
        for (Foe f : foes) {
            sum += Math.max(0, f.hp());
        }
        return sum;
    }

    /** 敌方全队结构值上限。 */
    public int enemyTotalMaxHp() {
        int sum = 0;
        for (Foe f : foes) {
            sum += f.maxHp();
        }
        return sum;
    }

    /** 暴露还剩几回合（我方给敌方挂的）。 */
    public int expose() {
        return foeExpose;
    }

    /** 敌方给我方挂的暴露还剩几回合。 */
    public int fleetExpose() {
        return fleetExpose;
    }

    /** 当前目标的防御力（每段伤害先减这一格）。 */
    public int targetPlate() {
        Foe t = target();
        return t == null ? 0 : t.hull().plate();
    }

    /** 敌方有效防空（全队之和，封顶 {@link #FLAK_CAP}）：我方多段攻击按这个数砍尾段。 */
    public int targetFlak() {
        return Math.min(FLAK_CAP, enemyFlak());
    }

    /** 这张卡打向当前目标吃的类型倍率（看目标此刻最外层）。 */
    public double typeMultiplierOf(Card card) {
        DamageType type = card.damageType();
        if (type == null) {
            return 1.0;
        }
        Foe t = target();
        boolean outerShield = t != null && t.block > 0;
        return !outerShield ? type.vsHull() : type.vsShield();
    }

    /** 当前目标下一步的读数（卡名 + 类型标 + 段数）。 */
    public String nextLabel() {
        Foe t = target();
        return t == null ? "" : t.nextLabelZh();
    }

    /** 当前目标再下一步的读数。 */
    public String upcomingLabel() {
        Foe t = target();
        return t == null ? "" : t.upcomingLabelZh();
    }    /**
     * 敌方威胁估算：敌方手牌里攻击卡的裸面板之和（上限口径）。
     *
     * <p>出牌对称之后敌方手牌不可见——玩家（和机器人）只能按"它手里还攥着多少伤害"
     * 来决定要不要垫盾，与敌方 AI 估玩家的威胁是同一条公式。</p>
     */
    public int nextIncoming() {
        return enemyThreat();
    }

    private int enemyThreat() {
        int sum = 0;
        for (Card card : enemyHand) {
            if (card.isAttack()) {
                sum += card.baseDamage();
            }
        }
        return sum;
    }

    /** 敌方这一回合总共会打进来多少（上限口径，同 {@link #nextIncoming()}）。 */
    public int incomingTotal() {
        return enemyThreat();
    }

    /** 敌方当前手牌数。 */
    public int enemyHandCount() {
        return enemyHand.size();
    }

    /** 敌方牌库剩余张数。 */
    public int enemyDrawCount() {
        return enemyDrawPile.size();
    }

    /** 敌方舰队本回合还剩的能量。 */
    public int enemyEnergy() {
        return enemyEnergy;
    }

    /** 敌方舰队每回合能量上限（与我方同一套公式）。 */
    public int enemyMaxEnergy() {
        return enemyFleet.maxEnergy();
    }

    /** 手牌。 */
    public List<Card> hand() {
        return List.copyOf(hand);
    }

    /** 牌库。 */
    public List<Card> drawPile() {
        return List.copyOf(drawPile);
    }

    /** 弃牌堆。 */
    public List<Card> discard() {
        return List.copyOf(discardPile);
    }

    /** 装填区（每张还剩几回合）。 */
    public List<Loading> loadingZone() {
        return List.copyOf(loadingZone);
    }

    /** 装填区里的一张牌：还要等几个回合才能回到弃牌堆。 */
    public record Loading(Card card, int turns) {}

    /** 手牌上限。 */
    public int handSizeLimit() {
        return HAND_LIMIT;
    }

    /** 这张手牌现在能不能打。 */
    public boolean canPlay(int index) {
        if (index < 0 || index >= hand.size() || over()) {
            return false;
        }
        return hand.get(index).cost() <= energy;
    }

    /** 第 {@code index} 张手牌的实际倍率。 */
    public int multiplierOfCard(int index) {
        if (index < 0 || index >= hand.size()) {
            return 1;
        }
        return fleet.multiplierOf(hand.get(index).id(), aliveIds());
    }

    /** 某张卡的倍率。 */
    public int multiplierOfCardId(String cardId) {
        return fleet.multiplierOf(cardId, aliveIds());
    }

    /** 自由开火。 */
    public boolean freeFire() {
        return freeFire;
    }

    /**
     * 注入舰长被动（整场生效）。
     *
     * <p>必须在开打前注入（构造函数已接收），否则首回合的能量/抽牌/再生/护盾
     * 已经结算完，那部分加成补不回来。</p>
     */
    public void setCaptainEffects(com.hysx.salvosequence.run.Captain.Effects fx) {
        this.captainFx = fx == null ? com.hysx.salvosequence.run.Captain.Effects.NONE : fx;
    }

    /** 注入舰长专属技（每场一次，S 键）。战斗中途注入即可。 */
    public void setSignature(com.hysx.salvosequence.run.Captain.Signature sig) {
        this.signature = sig == null ? com.hysx.salvosequence.run.Captain.Signature.NONE : sig;
    }

    public com.hysx.salvosequence.run.Captain.Signature signature() {
        return signature;
    }

    public void setFreeFire(boolean on) {
        this.freeFire = on;
        log(on ? "火力模式：自由开火 ×" + FleetOrders.FREE_FIRE_BONUS + "（溢出会续打到下一艘）"
                : "火力模式：集火（溢出浪费，但伤害全落在一个目标上）");
    }

    /** 切火力模式。 */
    public void toggleFreeFire() {
        setFreeFire(!freeFire);
    }

    /** 日志（最近的 N 条）。 */
    public List<String> recentLog(int n) {
        int from = Math.max(0, log.size() - Math.max(0, n));
        return List.copyOf(log.subList(from, log.size()));
    }

    /** 全部日志。 */
    public List<String> logAll() {
        return List.copyOf(log);
    }

    /** 能量总量（统计用）。 */
    public int metricEnergyAvailable() {
        return energyAvailable;
    }

    /** 花掉的能量（统计用）。 */
    public int metricEnergySpent() {
        return energySpent;
    }

    /** 抽过几张（统计用）。 */
    public int metricCardsDrawn() {
        return cardsDrawn;
    }

    /** 打了几张（统计用）。 */
    public int metricCardsPlayed() {
        return cardsPlayed;
    }

    /** 空回合数（统计用）。 */
    public int metricEmptyTurns() {
        return emptyTurns;
    }

    /** 装填锁总和（统计用）。 */
    public int metricLoadingLockSum() {
        return loadingLockSum;
    }

    /** 打出去的总伤害（统计用）。 */
    public int metricDamageDealt() {
        return damageDealt;
    }

    /** 承伤总量（统计用）。 */
    public int metricDamageTaken() {
        return damageTaken;
    }

    // ------------------------------------------------------------------ 回合

    /** 开始新回合。 */
    private void startTurn() {
        if (over()) {
            return;
        }
        tickLoading();
        int halved = roster.refreshBlock();
        if (halved > 0) {
            log("回盾减半：" + halved + " 艘（上回合护盾被击穿）");
        }
        repairByPerks();
        energy = Math.max(0, fleet.maxEnergy(aliveIds()) + captainFx.energy() - energyPenalty);
        energyPenalty = 0;
        skillUsed = false;
        aim = 0;
        playedThisTurn = 0;
        attacksThisTurn = 0;
        if (captainFx.regen() > 0) {
            int healed = 0;
            for (FleetRoster.Unit u : roster.units()) {
                if (u.alive() && u.hp() < u.maxHp()) {
                    roster.heal(u.id(), Math.min(u.maxHp() - u.hp(), captainFx.regen()));
                    healed += captainFx.regen();
                }
            }
            if (healed > 0) {
                log("舰长再生：全队修复 " + healed);
            }
        }
        if (captainFx.thorns() > 0) {
            roster.addBlockToAll(captainFx.thorns());
        }
        loadingLockSum += loadingZone.size();
        energyAvailable += energy;
        draw(fleet.handSize(aliveIds()) + captainFx.draw());
        boolean drewAttack = false;
        for (Card c : hand) {
            if (c.isAttack()) {
                drewAttack = true;
                break;
            }
        }
        turnsWithoutAttack = drewAttack ? 0 : turnsWithoutAttack + 1;
        if (turnsWithoutAttack >= 2) {
            guaranteeAttackCard();
            turnsWithoutAttack = 0;
        }
    }

    /** 测试钩子：把一张牌塞进手牌（同包测试用）。 */
    void debugPutInHand(String cardId) {
        hand.add(CardCatalog.byId(cardId));
    }

    /**
     * 参谋部保底：连续两回合没摸到攻击牌，从牌库检索一张，换掉手里一张非攻击牌。
     * 随机被降级成"参谋建议"——玩家管理的是命令，不是运气。
     */
    private void guaranteeAttackCard() {
        Card pick = null;
        for (Card c : drawPile) {
            if (c.isAttack()) {
                pick = c;
                break;
            }
        }
        if (pick == null || hand.size() >= HAND_LIMIT) {
            return;
        }
        drawPile.remove(pick);
        for (int i = hand.size() - 1; i >= 0; i--) {
            if (!hand.get(i).isAttack()) {
                discardPile.add(hand.remove(i));
                break;
            }
        }
        hand.add(pick);
        log("参谋部保底：检索到「" + pick.nameZh() + "」");
    }

    /** 损管光环：回合开始时，被罩着的船各自修一点结构值。 */
    private void repairByPerks() {
        int total = 0;
        for (FleetRoster.Unit u : roster.units()) {
            if (!u.alive() || u.hp() >= u.maxHp()) {
                continue;
            }
            FleetUnit unit = fleet.unit(u.id());
            int heal = unit == null ? 0
                    : Math.min(u.maxHp() - u.hp(), fleet.repairOn(unit, aliveIds()));
            if (heal > 0) {
                roster.heal(u.id(), heal);
                total += heal;
            }
        }
        if (total > 0) {
            log("损管光环：全队修复 " + total);
        }
    }

    /** 装填区倒计时：每回合减 1，减到 0 就回弃牌堆。 */
    private void tickLoading() {
        if (loadingZone.isEmpty()) {
            return;
        }
        List<Loading> stillLoading = new ArrayList<>(loadingZone.size());
        for (Loading entry : loadingZone) {
            int left = entry.turns() - 1;
            if (left <= 0) {
                discardPile.add(entry.card());
                log("「" + entry.card().nameZh() + "」装填完毕");
            } else {
                stillLoading.add(new Loading(entry.card(), left));
            }
        }
        loadingZone.clear();
        loadingZone.addAll(stillLoading);
    }

    /** 抽牌（牌库空了洗弃牌堆）。 */
    private void draw(int count) {
        for (int i = 0; i < count; i++) {
            if (hand.size() >= HAND_LIMIT) {
                return;
            }
            if (drawPile.isEmpty()) {
                if (discardPile.isEmpty()) {
                    return;
                }
                drawPile.addAll(discardPile);
                discardPile.clear();
                Collections.shuffle(drawPile, rng);
                log("洗回弃牌堆");
            }
            hand.add(drawPile.remove(drawPile.size() - 1));
            cardsDrawn++;
        }
    }

    /** 打一张手牌。 */
    public boolean playCard(int index) {
        if (over() || index < 0 || index >= hand.size()) {
            return false;
        }
        Card card = hand.get(index);
        if (card.cost() > energy) {
            return false;
        }
        hand.remove(index);
        energy -= card.cost();
        energySpent += card.cost();
        cardsPlayed++;
        playedThisTurn++;
        log("打出「" + card.nameZh() + "」");
        if (card.isAttack()) {
            attacksThisTurn++;
        }
        resolveCard(card, Side.PLAYER, false, null);
        // 弹夹光环：装填倒计时缩短，缩短到 0 就是打完直接回弃牌堆
        int reload = Math.max(0, card.reload() - fleet.magazineFor(card.id()));
        if (reload > 0) {
            loadingZone.add(new Loading(card, reload));
        } else {
            if (card.reload() > 0) {
                log("「" + card.nameZh() + "」弹夹供弹：跳过装填");
            }
            discardPile.add(card);
        }
        settle();
        return true;
    }

    /** 舰队里有没有带主动技能的船（T5 脸面舰）。 */
    public boolean shipSkillAvailable() {
        return !over() && !shipSkillUsed
                && com.hysx.salvosequence.fleet.ShipActiveSkill.ofFleet(fleet) != com.hysx.salvosequence.fleet.ShipActiveSkill.NONE;
    }

    /** 舰船主动技能：每场战斗一次，效果走各自的现有结算通道。 */
    public boolean useShipSkill() {
        var skill = com.hysx.salvosequence.fleet.ShipActiveSkill.ofFleet(fleet);
        if (over() || shipSkillUsed || skill == com.hysx.salvosequence.fleet.ShipActiveSkill.NONE) {
            return false;
        }
        shipSkillUsed = true;
        if (skill.shieldAll() > 0) {
            roster.addBlockToAll(skill.shieldAll());
        }
        if (skill.energy() > 0) {
            energy += skill.energy();
        }
        if (skill.draw() > 0) {
            draw(skill.draw());
        }
        if (skill.damagePct() > 0) {
            shipDamagePct = skill.damagePct();
        }
        if (skill.exposeTurns() > 0) {
            allFoesExpose = Math.max(allFoesExpose, skill.exposeTurns());
        }
        log("舰船技能「" + skill.nameZh() + "」：" + skill.desc());
        return true;
    }

    /**
     * 舰长专属技：每场战斗一次、不耗能量（与舰船主动技同档）。
     * 没有专属技时退回旧的通用效果（每回合一次，校射/护盾/抽牌，2 能量）。
     */
    public boolean useSkill() {
        if (over()) {
            return false;
        }
        if (signature == com.hysx.salvosequence.run.Captain.Signature.NONE) {
            if (skillUsed || energy < SKILL_COST) {
                return false;
            }
            energy -= SKILL_COST;
            energySpent += SKILL_COST;
            skillUsed = true;
            aim += SKILL_AIM;
            FleetRoster.Unit flag = roster.flag();
            if (flag != null) {
                roster.addBlock(flag.id(), SKILL_BLOCK);
            }
            draw(1);
            log("舰长技能：校射 +" + SKILL_AIM + "，护盾 +" + SKILL_BLOCK + "，抽 1 张");
            return true;
        }
        if (signatureUsed) {
            return false;
        }
        signatureUsed = true;
        if (signature.shieldAll() > 0) {
            roster.addBlockToAll(signature.shieldAll());
        }
        if (signature.energy() > 0) {
            energy += signature.energy();
        }
        if (signature.draw() > 0) {
            draw(signature.draw());
        }
        if (signature.damagePct() > 0) {
            shipDamagePct += signature.damagePct();
        }
        if (signature.spot() > 0) {
            signatureSpot += signature.spot();
        }
        log("舰长专属技「" + signature.nameZh() + "」：" + signature.desc());
        return true;
    }

    /** 专属技本场是否已经用过。 */
    public boolean signatureUsed() {
        return signatureUsed;
    }

    /** 结束回合。 */
    public void endTurn() {
        if (over()) {
            return;
        }
        if (playedThisTurn == 0) {
            emptyTurns++;
        }
        for (Card card : hand) {
            discardPile.add(card);
        }
        hand.clear();
        if (foeExpose > 0) {
            foeExpose--;
        }
        if (allFoesExpose > 0) {
            allFoesExpose--;
        }
        if (fleetExpose > 0) {
            fleetExpose--;
        }
        runEnemyTurn();
        if (!over()) {
            if (roster.wiped()) {
                lose();
                return;
            }
            turn++;
            startTurn();
        }
    }

    // ------------------------------------------------------------------ 结算

    /**
     * 敌方回合——与玩家完全同构的一侧：
     * 能量回满 → 盾刷新 + 损管 → 抽到手牌上限 → 出牌（付得起才打）→ 弃手牌。
     *
     * <p>出牌决策（{@link #chooseEnemyCard}）用与 {@code SimplePilot} 同源的启发式：
     * 斩杀优先、快死先挡、然后是有效伤害最高的牌。有测试脚本（{@code Enemy.scripted()}）
     * 的编队走旧的固定表通道（不耗能量），用于确定性测试靶船。</p>
     */
    private void runEnemyTurn() {
        boolean anyScripted = false;
        boolean anyLive = false;
        for (Foe foe : foes) {
            if (foe.alive()) {
                if (foe.scripted()) {
                    anyScripted = true;
                } else {
                    anyLive = true;
                }
            }
        }
        if (anyLive && !anyScripted) {
            runEnemyFleetPhase();
        } else {
            runScriptedPhase();
        }
        if (!over() && roster.wiped()) {
            lose();
        }
    }

    /**
     * 参谋建议：从牌库检索攻击牌加入手牌；牌库没有攻击牌就改为抽等量的牌。
     */
    private void resolveSearch(int count) {
        int got = 0;
        for (int i = 0; i < count; i++) {
            Card pick = null;
            for (Card c : drawPile) {
                if (c.isAttack()) {
                    pick = c;
                    break;
                }
            }
            if (pick == null) {
                break;
            }
            drawPile.remove(pick);
            if (hand.size() < HAND_LIMIT) {
                hand.add(pick);
            } else {
                discardPile.add(pick);
            }
            log("参谋建议：检索到「" + pick.nameZh() + "」");
            got++;
        }
        if (got < count) {
            log("牌库没有攻击牌，改为抽 " + (count - got) + " 张");
            draw(count - got);
        }
    }

    /** 活牌敌方的整回合（与玩家 startTurn + 出牌 + 弃牌同构）。 */
    private void runEnemyFleetPhase() {
        enemyEnergy = Math.max(0, enemyFleet.maxEnergy() - enemyEnergyPenalty);
        enemyEnergyPenalty = 0;
        foeAim = 0;
        tickEnemyLoading();
        int repaired = 0;
        for (Foe foe : foes) {
            if (!foe.alive()) {
                continue;
            }
            foe.block = foe.hitSinceRefresh ? Math.max(0, foe.shieldRefresh() / 2) : foe.shieldRefresh();
            foe.hitSinceRefresh = false;
            if (foe.repair() > 0 && foe.hp() < foe.maxHp()) {
                int healed = Math.min(foe.maxHp() - foe.hp(), foe.repair());
                foe.hp += healed;
                repaired += healed;
            }
        }
        if (repaired > 0) {
            log("敌方损管光环：修复 " + repaired);
        }
        drawEnemy(enemyFleet.handSize());
        int guard = 0;
        while (!over() && guard++ < 24) {
            int index = chooseEnemyCard();
            if (index < 0) {
                break;
            }
            Card card = enemyHand.remove(index);
            enemyEnergy -= card.cost();
            log("敌方打出「" + card.nameZh() + "」（-" + card.cost() + " 能量）");
            resolveCard(card, Side.FOE, false, null);
            // 弹夹光环：装填倒计时缩短，缩短到 0 就是打完直接回弃牌堆
            int reload = Math.max(0, card.reload() - enemyMagazineFor(card.id()));
            if (reload > 0) {
                enemyLoading.add(new Loading(card, reload));
            } else {
                enemyDiscard.add(card);
            }
        }
        enemyDiscard.addAll(enemyHand);
        enemyHand.clear();
    }

    /** 测试脚本通道：固定表逐舰打牌，不耗能量（与 v2 语义一致）。 */
    private void runScriptedPhase() {
        for (Foe foe : foes) {
            if (!foe.alive() || !foe.scripted()) {
                continue;
            }
            if (foe.repair() > 0 && foe.hp() < foe.maxHp()) {
                int healed = Math.min(foe.maxHp() - foe.hp(), foe.repair());
                foe.hp += healed;
                log(foe.nameZh() + " 损管修复 " + healed);
            }
            foe.block = foe.hitSinceRefresh ? Math.max(0, foe.shieldRefresh() / 2) : foe.shieldRefresh();
            foe.hitSinceRefresh = false;
            Enemy.Played step = foe.nextStep();
            foe.planIndex++;
            Card card = step.card();
            log(foe.nameZh() + " 打出「" + card.nameZh() + "」" + (step.fromBack() ? "（锁后排）" : ""));
            resolveCard(card, Side.FOE, step.fromBack(), foe);
            if (over()) {
                return;
            }
        }
    }

    /**
     * 敌方出牌决策：与 {@code SimplePilot} 同源的启发式——
     * 斩杀 → 快死先挡 → 有效伤害最高的牌 → 铺垫（校射/蓄力）→ 举盾。
     */
    private int chooseEnemyCard() {
        int best = -1;
        double bestScore = 0;
        int myHp = enemyTotalHp();
        int threat = playerThreat();
        for (int i = 0; i < enemyHand.size(); i++) {
            Card card = enemyHand.get(i);
            if (card.cost() > enemyEnergy) {
                continue;
            }
            double score;
            int dmg = estimatedEnemyDamage(card);
            if (dmg >= roster.totalHp() && dmg > 0) {
                score = 1000 + dmg; // 斩杀
            } else if (dmg > 0) {
                score = dmg;
            } else if (card.blockValue() > 0 && threat >= myHp) {
                score = card.blockValue() * 2.0; // 快死了必须挡
            } else if (hasAim(card) || hasCharge(card)) {
                score = 3; // 铺垫
            } else if (card.blockValue() > 0) {
                score = card.blockValue() * 0.5;
            } else {
                score = 0; // 纯抽牌/能量牌压着不打，防循环
            }
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /** 这张敌牌打向玩家侧大概能打多少（与 {@code SimplePilot.estimatedDamage} 同一套近似）。 */
    private int estimatedEnemyDamage(Card card) {
        int base = card.baseDamage();
        if (base == 0) {
            return 0;
        }
        if (card.perCharge() > 0) {
            base += card.perCharge() * foeCharge;
        }
        int mult = Math.max(1, multiplierOfCard(card.id(), Side.FOE));
        int total = base * mult + volleyOfCard(card.id(), Side.FOE) + enemySpotFor(card);
        if (foeAim > 0) {
            total += foeAim;
        }
        if (fleetExpose > 0) {
            total = (int) Math.floor(total * EXPOSE_MULTIPLIER);
        }
        FleetRoster.Unit front = roster.pick(false);
        if (front == null) {
            return 0;
        }
        double m = front.block() <= 0 ? card.damageType().vsHull() : card.damageType().vsShield();
        total = (int) Math.floor(total * m);
        int hits = Math.max(1, card.hits());
        int effHits = Math.max(1, hits - roster.flakTotal());
        int seg = total / hits;
        int rem = total % hits;
        int reduced = 0;
        for (int i = 0; i < effHits; i++) {
            reduced += Math.max(1, seg + (i < rem ? 1 : 0) - (card.pierce() ? 0 : front.plate()));
        }
        return Math.max(0, reduced - front.block());
    }

    /** 敌方这张卡吃到的火控光环（与 resolveDamage 同一公式）。 */
    private int enemySpotFor(Card card) {
        int spot = 0;
        for (Foe f : foes) {
            if (f.alive() && f.hull().cardIds().contains(card.id())) {
                spot += f.perks().spot();
            }
        }
        return Math.min(SPOT_CAP, spot);
    }

    /** 敌方弹夹：带这张卡的活敌舰身上的弹夹光环之和。 */
    private int enemyMagazineFor(String cardId) {
        int sum = 0;
        for (Foe f : foes) {
            if (f.alive() && f.hull().cardIds().contains(cardId)) {
                sum += f.perks().magazine();
            }
        }
        return sum;
    }

    /** 敌方抽牌（牌库空了洗弃牌堆）。 */
    private void drawEnemy(int count) {
        for (int i = 0; i < count; i++) {
            if (enemyHand.size() >= HAND_LIMIT) {
                return;
            }
            if (enemyDrawPile.isEmpty()) {
                if (enemyDiscard.isEmpty()) {
                    return;
                }
                enemyDrawPile.addAll(enemyDiscard);
                enemyDiscard.clear();
                Collections.shuffle(enemyDrawPile, rng);
                log("敌方洗回弃牌堆");
            }
            enemyHand.add(enemyDrawPile.remove(enemyDrawPile.size() - 1));
        }
    }

    /** 敌方装填区倒数。 */
    private void tickEnemyLoading() {
        if (enemyLoading.isEmpty()) {
            return;
        }
        List<Loading> still = new ArrayList<>(enemyLoading.size());
        for (Loading entry : enemyLoading) {
            int left = entry.turns() - 1;
            if (left <= 0) {
                enemyDiscard.add(entry.card());
            } else {
                still.add(new Loading(entry.card(), left));
            }
        }
        enemyLoading.clear();
        enemyLoading.addAll(still);
    }

    /** 玩家手牌威胁估算：手牌里攻击卡的裸面板之和（上限口径，供双方 AI 与面板读数）。 */
    private int playerThreat() {
        int sum = 0;
        for (Card card : hand) {
            if (card.isAttack()) {
                sum += card.baseDamage();
            }
        }
        return sum;
    }

    /** 最前面的活敌舰（敌舰朝左，x 最小的一艘；敌方 Block 的落点）。 */
    private Foe frontFoe() {
        Foe best = null;
        for (Foe foe : foes) {
            if (!foe.alive()) {
                continue;
            }
            if (best == null || frontOf(foe) < frontOf(best)) {
                best = foe;
            }
        }
        return best;
    }

    /** 伤得最重的活敌舰（敌方 Repair 的落点，对称于我方的 mostDamaged）。 */
    private Foe mostDamagedFoe() {
        Foe best = null;
        int worst = 0;
        for (Foe foe : foes) {
            if (!foe.alive()) {
                continue;
            }
            int missing = foe.maxHp() - foe.hp();
            if (missing > worst) {
                worst = missing;
                best = foe;
            }
        }
        return best;
    }

    private static boolean hasAim(Card card) {
        for (Effect effect : card.effects()) {
            if (effect instanceof Effect.Aim) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCharge(Card card) {
        for (Effect effect : card.effects()) {
            if (effect instanceof Effect.Charge) {
                return true;
            }
        }
        return false;
    }

    /** 结算一侧的一张牌：我方从手牌、敌方从计划表，同一条管线。 */
    private void resolveCard(Card card, Side actor, boolean fromBack, Foe actorFoe) {
        int mult = multiplierOfCard(card.id(), actor);
        boolean aimTaken = false;
        // 注意：这里刻意用 instanceof 而非模式匹配 switch。Android 运行时没有
        // java.lang.runtime.SwitchBootstraps，d8 也不会把它脱糖，模式 switch 会在
        // 设备上抛 NoClassDefFoundError；instanceof 链编译成普通 bytecode，两端都能跑。
        for (Effect effect : card.effects()) {
            if (effect instanceof Effect.Damage d) {
                int aimBonus = 0;
                if (!aimTaken) {
                    aimTaken = true;
                    aimBonus = takeAim(actor);
                }
                resolveDamage(card, d, actor, fromBack, actorFoe, mult, aimBonus);
            } else if (effect instanceof Effect.Block b) {
                resolveBlock(card, b, actor, actorFoe, mult);
            } else if (effect instanceof Effect.Aim a) {
                addAim(actor, a.bonus());
            } else if (effect instanceof Effect.Charge c) {
                addCharge(actor, c.stacks());
            } else if (effect instanceof Effect.Draw dr) {
                if (actor == Side.PLAYER) {
                    draw(dr.count());
                }
                // 敌方没有手牌：计划表里不该出现抽牌卡（结构测试管着）
            } else if (effect instanceof Effect.Energy en) {
                if (actor == Side.PLAYER) {
                    energy += en.amount();
                    energyAvailable += en.amount();
                }
            } else if (effect instanceof Effect.Expose ex) {
                if (actor == Side.PLAYER) {
                    foeExpose = Math.max(foeExpose, ex.turns());
                } else {
                    fleetExpose = Math.max(fleetExpose, ex.turns());
                    log("我方舰队被暴露：" + ex.turns() + " 回合内受到的伤害 +50%");
                }
            } else if (effect instanceof Effect.Repair rp) {
                resolveRepair(rp, actor, actorFoe, mult);
            } else if (effect instanceof Effect.Search se) {
                resolveSearch(se.count());
            } else if (effect instanceof Effect.Drain dr) {
                if (actor == Side.FOE) {
                    energyPenalty += dr.energy();
                    log("能量被干扰：下回合 -" + dr.energy());
                } else {
                    // 对称机制：我方打出的干扰砍敌方舰队能量池（当前卡池还没有我方干扰卡）
                    enemyEnergyPenalty += dr.energy();
                }
            }
        }
    }

    /** 我方侧的校射/蓄力来自玩家牌，敌方侧来自计划表——各存各的。 */
    private int takeAim(Side actor) {
        if (actor == Side.PLAYER) {
            int taken = aim;
            aim = 0;
            return taken;
        }
        int taken = foeAim;
        foeAim = 0;
        return taken;
    }

    private void addAim(Side actor, int bonus) {
        if (actor == Side.PLAYER) {
            aim += bonus;
        } else {
            foeAim += bonus;
            log("敌方校射：下一次炮击 +" + bonus);
        }
    }

    private void addCharge(Side actor, int stacks) {
        if (actor == Side.PLAYER) {
            chargeStacks += stacks;
        } else {
            foeCharge += stacks;
        }
    }

    private int chargeOf(Side actor) {
        return actor == Side.PLAYER ? chargeStacks : foeCharge;
    }

    private void clearCharge(Side actor) {
        if (actor == Side.PLAYER) {
            chargeStacks = 0;
        } else {
            foeCharge = 0;
        }
    }

    /**
     * 这张卡在出牌侧的倍率：我方 = 携带者等级之和；
     * 敌方 = 携带这张卡的敌舰数（都是 Lv1，同一条公式的退化形式）。
     */
    private int multiplierOfCard(String cardId, Side actor) {
        if (actor == Side.PLAYER) {
            return fleet.multiplierOf(cardId);
        }
        int carriers = 0;
        for (Foe f : foes) {
            if (f.alive() && f.hull().cardIds().contains(cardId)) {
                carriers++;
            }
        }
        return Math.max(1, carriers);
    }

    /** 这张卡在出牌侧的齐射合计：我方查舰队，敌方数"一起开火的敌舰"的齐射（含阵型光环）。 */
    private int volleyOfCard(String cardId, Side actor) {
        if (actor == Side.PLAYER) {
            return fleet.volleyFor(cardId);
        }
        int sum = 0;
        for (Foe f : foes) {
            if (f.alive() && f.hull().cardIds().contains(cardId)) {
                sum += f.hull().volley() + f.perks().volley();
            }
        }
        return sum;
    }

    /** 全队齐射总和（侧弦齐射专用：出这张牌时全舰队一起开火）。 */
    private int allVolley(Side actor) {
        if (actor == Side.PLAYER) {
            int sum = 0;
            for (FleetRoster.Unit u : roster.units()) {
                if (!u.alive()) {
                    continue;
                }
                FleetUnit fu = fleet.unit(u.id());
                if (fu != null) {
                    sum += fleet.volleyOf(fu);
                }
            }
            return sum;
        }
        int sum = 0;
        for (Foe f : foes) {
            if (f.alive()) {
                sum += f.hull().volley() + f.perks().volley();
            }
        }
        return sum;
    }

    /** 一次伤害效果的结算（含倍率、齐射、校射、蓄力、暴露、自由开火、类型/甲/防空分段）。 */
    private void resolveDamage(Card card, Effect.Damage d, Side actor, boolean fromBack,
                               Foe actorFoe, int mult, int aimBonus) {
        int per = d.amount();
        if (d.perCharge() > 0 && chargeOf(actor) > 0) {
            per += d.perCharge() * chargeOf(actor);
            clearCharge(actor);
        }
        // 齐射按段数走：多段武器每一段都吃一次齐射加成；侧弦齐射吃全队齐射
        int volley = volleyOfCard(card.id(), actor);
        if ("broad.fire".equals(card.id())) {
            volley = allVolley(actor);
        } else if ("broad.rapid".equals(card.id())) {
            // 快速轮射：每段吃 1/3 全队齐射
            volley = allVolley(actor) / 3;
        }
        int total = (per * mult + volley) * d.hits();
        if (actor == Side.PLAYER) {
            total += fleet.adjacencyDamageBonus(card.id());
        } else {
            // 敌方火控光环：一起开火的敌舰把观测数据借给这张卡
            int spot = 0;
            for (Foe f : foes) {
                if (f.alive() && f.hull().cardIds().contains(card.id())) {
                    spot += f.perks().spot();
                }
            }
            total += Math.min(SPOT_CAP, spot);
        }
        total += aimBonus;
        if (actor == Side.PLAYER) {
            if (captainFx.damagePct() > 0) {
                total += total * captainFx.damagePct() / 100;
            }
            if (shipDamagePct > 0) {
                total += total * shipDamagePct / 100;
            }
            // 专属技「信标脉冲」：全队火控 +n，每次炮击的固定加成
            if (signatureSpot > 0) {
                total += signatureSpot;
            }
            // 首击：本回合第一张攻击卡
            if (captainFx.firstStrike() > 0 && attacksThisTurn == 0) {
                total += captainFx.firstStrike();
                log("舰长首击 +" + captainFx.firstStrike());
            }
            // 斩杀：目标残血（<30%）
            if (captainFx.executePct() > 0) {
                Foe t = target();
                if (t != null && t.hp() * 10 < t.maxHp() * 3) {
                    total += total * captainFx.executePct() / 100;
                    log("斩杀 +" + captainFx.executePct() + "%");
                }
            }
            // 弱点：目标无格挡
            if (captainFx.weakpointPct() > 0) {
                Foe t = target();
                if (t != null && t.block() <= 0) {
                    total += total * captainFx.weakpointPct() / 100;
                }
            }
        }
        if (actor == Side.FOE) {
            total = (int) Math.floor(total * EnemyCatalog.damageScale());
        }
        if (exposeOn(actor.opponent()) > 0) {
            total = (int) Math.floor(total * EXPOSE_MULTIPLIER);
        }
        if (actor == Side.PLAYER && freeFire) {
            total = (int) Math.round(total * FleetOrders.FREE_FIRE_BONUS);
        }
        if (actor == Side.PLAYER) {
            damageDealt += total;
        }
        applyAttack(total, d.hits(), d.type(), d.pierce(), actor, fromBack, card.nameZh());
    }

    private int exposeOn(Side side) {
        return side == Side.FOE ? foeExpose + allFoesExpose : fleetExpose;
    }

    /** 护盾效果：我方落在“当前被锁定的前排舰”（敌方下一次攻击的目标），全队盾仍给全队；敌方落打出者自己（举盾）。随后结算护盾连接。 */
    private void resolveBlock(Card card, Effect.Block b, Side actor, Foe actorFoe, int mult) {
        int amount = b.amount() * mult;
        if (actor == Side.PLAYER) {
            if (FleetOrders.isFleetShield(card.id())) {
                roster.addBlockToAll(amount);
            } else {
                // 单舰护盾跟着敌方的锁定目标走：谁在挨打就给谁举盾，而不是永远给指挥舰
                FleetRoster.Unit front = roster.pick(false);
                FleetRoster.Unit target = front != null ? front : roster.flag();
                if (target != null) {
                    roster.addBlock(target.id(), amount);
                }
            }
            // 护盾连接：打出护盾卡时，范围内每艘友舰额外 +n
            int linked = 0;
            for (FleetRoster.Unit u : roster.units()) {
                if (!u.alive()) {
                    continue;
                }
                FleetUnit unit = fleet.unit(u.id());
                int extra = unit == null ? 0 : fleet.shieldLinkOn(unit, aliveIds());
                if (extra > 0) {
                    roster.addBlock(u.id(), extra);
                    linked += extra;
                }
            }
            if (linked > 0) {
                log("护盾连接：友舰额外 +" + linked);
            }
        } else {
            Foe target = actorFoe != null && actorFoe.alive() ? actorFoe : frontFoe();
            if (target != null && target.alive()) {
                target.block += amount;
                log(target.nameZh() + " 举盾 +" + amount);
            }
            for (Foe f : foes) {
                if (f.alive() && f.perks().shieldLink() > 0) {
                    f.block += f.perks().shieldLink();
                }
            }
        }
    }

    /** 修复效果：我方修伤得最重的那艘；敌方修伤得最重的那艘（对称）。 */
    private void resolveRepair(Effect.Repair rp, Side actor, Foe actorFoe, int mult) {
        if (actor == Side.PLAYER) {
            FleetRoster.Unit hurt = mostDamaged();
            if (hurt != null) {
                int amount = rp.hp() * mult;
                roster.heal(hurt.id(), amount);
                log("「" + hurt.nameZh() + "」修复 " + amount);
            }
        } else {
            Foe hurt = actorFoe != null && actorFoe.alive() ? actorFoe : mostDamagedFoe();
            if (hurt != null && hurt.alive()) {
                int healed = Math.min(hurt.maxHp() - hurt.hp(), rp.hp() * mult);
                if (healed > 0) {
                    hurt.hp += healed;
                    log(hurt.nameZh() + " 修复 " + healed);
                }
            }
        }
    }

    /** 敌方全队防空之和（我方多段攻击的生效段数会被砍掉；含阵型光环）。 */
    private int enemyFlak() {
        int sum = 0;
        for (Foe f : foes) {
            if (f.alive()) {
                sum += f.flak();
            }
        }
        return sum;
    }

    /** 把总伤按段数均分（余数给首段）——这是"完整的一段齐射"，防空还没动手。 */
    private static int[] segments(int total, int hits) {
        int count = Math.max(1, hits);
        int[] out = new int[count];
        int base = total / count;
        int rem = total % count;
        for (int i = 0; i < count; i++) {
            out[i] = base + (i < rem ? 1 : 0);
        }
        return out;
    }

    /**
     * 一次攻击的总闸：先均分成完整齐射，再让防空<b>砍掉尾段</b>——
     * 被拦截的弹直接损失，而不是把伤害挤进剩下的段（那样防空反而会把多段武器浓缩成巨炮）。
     *
     * <p>单段武器（hits=1）天然免疫防空："攻击高就是穿甲"的另一面。
     * 击沉我方船且余量仍在溢出（≥{@link #SYSTEM_DAMAGE_THRESHOLD}）→ 系统损伤。</p>
     */
    /** 我方还活着的船 id（击沉即断链的过滤口径）。 */
    private java.util.Set<String> aliveIds() {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (FleetRoster.Unit u : roster.units()) {
            if (u.alive()) {
                out.add(u.id());
            }
        }
        return out;
    }

    /** 敌方还活着的船 id。 */
    private java.util.Set<String> foeAliveIds() {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (Foe f : foes) {
            if (f.alive()) {
                out.add(f.unit().id());
            }
        }
        return out;
    }

    /** 断链清单：一艘船沉了，把它广播的光环逐条念出来（UI 替玩家算）。 */
    private void logChainBreak(String nameZh, com.hysx.salvosequence.fleet.Ship ship) {
        var perks = ship.perks();
        if (perks.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder("断链：「" + nameZh + "」—— ");
        for (int i = 0; i < perks.size(); i++) {
            var perk = perks.get(i);
            if (i > 0) {
                sb.append("、");
            }
            sb.append(perk.kind().labelZh()).append(" −").append(perk.value());
        }
        log(sb.toString());
    }

    private void applyAttack(int total, int hits, DamageType type, boolean pierce,
                             Side actor, boolean fromBack, String source) {
        if (total <= 0) {
            return;
        }
        Side target = actor.opponent();
        int flak = Math.min(FLAK_CAP, target == Side.FOE ? enemyFlak() : roster.flakTotal());
        int[] segs = segments(total, hits);
        int effective = Math.max(1, segs.length - flak);
        int overflow = 0;
        for (int i = 0; i < effective && i < segs.length; i++) {
            if (over()) {
                return;
            }
            int seg = segs[i];
            if (seg <= 0) {
                continue;
            }
            if (target == Side.FOE) {
                applySegmentToFoes(seg, type, pierce, source);
            } else {
                FleetRoster.Soak soak = soakSegmentToFleet(seg, type, pierce, fromBack, source);
                damageTaken += soak.lost();
                overflow += soak.overflow();
            }
        }
        if (target == Side.PLAYER && !over() && overflow >= SYSTEM_DAMAGE_THRESHOLD) {
            systemDamage();
        }
        logChainBreaks(target);
    }

    /** 对比这一轮攻击前后的存活名单，给每艘新沉的船念断链清单；敌方侧顺带重导协同。 */
    private void logChainBreaks(Side target) {
        if (target == Side.FOE) {
            java.util.Set<String> alive = foeAliveIds();
            for (Foe f : foes) {
                if (!f.alive() && !chainLogged.contains(f.unit().id())) {
                    chainLogged.add(f.unit().id());
                    logChainBreak(f.nameZh(), f.hull());
                }
            }
            for (Foe f : foes) {
                if (f.alive() && f.perks() != enemyFleet.perksOn(f.unit(), alive)) {
                    f.perks = enemyFleet.perksOn(f.unit(), alive);
                    f.rederive();
                }
            }
        } else {
            for (FleetRoster.Unit u : roster.units()) {
                if (!u.alive() && !chainLogged.contains(u.id())) {
                    chainLogged.add(u.id());
                    FleetUnit unit = fleet.unit(u.id());
                    if (unit != null) {
                        logChainBreak(u.nameZh(), unit.ship());
                    }
                }
            }
        }
    }

    /** 一段伤害打到敌方侧（类型倍率与防御力在"落点最外层"判一次，溢出续打不再换算）。 */
    private void applySegmentToFoes(int seg, DamageType type, boolean pierce, String source) {
        int remaining = seg;
        boolean first = true;
        while (remaining > 0) {
            Foe t = target();
            if (t == null) {
                return;
            }
            int eff;
            if (first) {
                first = false;
                double m = t.block <= 0 ? type.vsHull() : type.vsShield();
                eff = Math.max(1, (int) Math.floor(remaining * m) - (pierce ? 0 : t.plate()));
            } else {
                eff = remaining;
            }
            int absorbed = Math.min(t.block, eff);
            t.block -= absorbed;
            if (t.block <= 0 && t.shieldRefresh() > 0) {
                t.hitSinceRefresh = true;   // 盾被击穿：下回合回充被打断
            }
            remaining = eff - absorbed;
            int applied = Math.min(remaining, t.hp);
            t.hp -= applied;
            remaining -= applied;
            log("「" + source + "」对 " + t.nameZh() + " 造成 " + (absorbed + applied)
                    + (absorbed > 0 ? "（格挡吃掉 " + absorbed + "）" : ""));
            if (!t.alive()) {
                log(t.nameZh() + " 被击沉");
                if (aliveCount() == 0) {
                    win();
                    return;
                }
                autoTarget();
                if (remaining > 0 && !freeFire) {
                    log("集火：溢出 " + remaining + " 点浪费了（自由开火会续打）");
                    return;
                }
            } else if (remaining > 0) {
                return;
            }
        }
    }

    /** 一段伤害打到我方舰队（穿防御、格挡照吃、防御力逐段减、链式溢出）。 */
    private FleetRoster.Soak soakSegmentToFleet(int seg, DamageType type, boolean pierce,
                                                boolean fromBack, String source) {
        FleetRoster.Unit u = roster.pick(fromBack);
        int eff = seg;
        if (u != null) {
            double m = u.block() <= 0 ? type.vsHull() : type.vsShield();
            eff = Math.max(1, (int) Math.floor(seg * m) - (pierce ? 0 : u.plate()));
        }
        FleetRoster.Soak soak = roster.soak(eff, false, fromBack);
        int absorbed = Math.max(0, eff - soak.lost() - soak.leftover());
        log("「" + source + "」命中：盾/甲挡下 " + absorbed + "，结构 -" + soak.lost());
        return soak;
    }

    /** 伤得最重的那艘（缺得最多）。 */
    private FleetRoster.Unit mostDamaged() {
        FleetRoster.Unit best = null;
        int worst = 0;
        for (FleetRoster.Unit u : roster.units()) {
            if (!u.alive()) {
                continue;
            }
            int missing = u.maxHp() - u.hp();
            if (missing > worst) {
                worst = missing;
                best = u;
            }
        }
        return best;
    }

    /** 系统损伤（BFG 暴击表）：下回合能量 -1，随机一张攻击卡被震进装填区。 */
    private void systemDamage() {
        energyPenalty += 1;
        List<Card> attacks = new ArrayList<>();
        for (Card c : hand) {
            if (c.isAttack()) {
                attacks.add(c);
            }
        }
        if (!attacks.isEmpty()) {
            Card hit = attacks.get(rng.nextInt(attacks.size()));
            hand.remove(hit);
            loadingZone.add(new Loading(hit, 1));
            log("系统损伤：「" + hit.nameZh() + "」被震进装填区");
        }
        log("系统损伤：下回合能量 -1");
    }

    /** 打完一遍看结果。 */
    private void settle() {
        if (over()) {
            return;
        }
        if (aliveCount() == 0) {
            win();
        } else if (roster.wiped()) {
            lose();
        }
    }

    private void win() {
        if (result != Result.ONGOING) {
            return;
        }
        result = Result.WON;
        log("战斗胜利");
    }

    private void lose() {
        if (result != Result.ONGOING) {
            return;
        }
        result = Result.LOST;
        log("舰队全灭");
    }

    /** 日志一行。 */
    private void log(String line) {
        log.add(line);
        if (log.size() > LOG_CAP) {
            log.remove(0);
        }
    }

    /** 敌方编队名。 */
    private String squadName() {
        if (squad.size() == 1) {
            return squad.get(0).nameZh();
        }
        return squad.get(0).nameZh() + " 等 " + squad.size() + " 艘";
    }

    /** 调试用一行摘要。 */
    public String summary() {
        return "Battle[t" + turn + " " + result + " hp" + roster.totalHp() + "/" + roster.totalMaxHp()
                + " en" + energy + " 敌" + aliveCount() + "/" + foes.size() + "]";
    }

    @Override
    public String toString() {
        return summary();
    }
}
