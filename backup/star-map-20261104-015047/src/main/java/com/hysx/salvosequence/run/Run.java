package com.hysx.salvosequence.run;

import com.hysx.salvosequence.combat.Battle;
import com.hysx.salvosequence.combat.Enemy;
import com.hysx.salvosequence.combat.EnemyCatalog;
import com.hysx.salvosequence.deck.Card;
import com.hysx.salvosequence.deck.CardCatalog;
import com.hysx.salvosequence.fleet.AppearanceDesign;
import com.hysx.salvosequence.fleet.Fleet;
import com.hysx.salvosequence.fleet.FleetRoster;
import com.hysx.salvosequence.fleet.FleetUnit;
import com.hysx.salvosequence.fleet.Ship;
import com.hysx.salvosequence.fleet.ShipArtStore;
import com.hysx.salvosequence.fleet.ShipCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Random;
import java.util.Set;

/**
 * 一次航行：选舰队 → 五场战斗 → 战利品 → 下一场。
 *
 * <p>改造前后的流程骨架没变（选起始 → 打 → 三选一战利品 → 下一关），
 * 变的是"什么是你的资产"：</p>
 *
 * <ul>
 *   <li>改造前：你有一艘<b>船体</b>，战利品是往上装/换的<b>模块</b>，
 *       结构值是一根总血条（{@code hullHp}）；</li>
 *   <li>改造后：你有一支<b>舰队</b>，战利品是<b>新船</b>或<b>同型船的等级</b>，
 *       结构值是每艘船各自的血（{@link #fleetHp()}），全队沉没才算输。</li>
 * </ul>
 */
public final class Run {

    /** 打几场。 */
    public static final int NODE_COUNT = EnemyCatalog.routeLength();

    /** 每关之间整备回多少结构值。 */
    public static final int REPAIR_PER_NODE = 35;

    /** 战利品里最多给几艘新船。 */
    public static final int DROP_SHIPS = 2;

    /** 舰队里有轴炮舰时，额外塞一艘轴炮船的概率（百分比）。 */
    public static final int AXIAL_LUCK_PERCENT = 12;

    /** 战利品最多几项（船 + 船 + 强化 + 卡）。 */
    public static final int REWARD_MAX = 4;

    private static final long STAGE_SEED_MUL = 0x9E3779B1L;

    /** 流程阶段。 */
    public enum Stage {

        /** 选起始舰队。 */
        PICK_LOADOUT,

        /** 打。 */
        BATTLE,

        /** 太空港（编组/定制/培训/测绘）。 */
        SPACE_PORT,

        /** 事件遭遇（问号节点）。 */
        EVENT,

        /** 星图航行（自由跃迁）。 */
        MAP,

        /** 挑战利品。 */
        REWARD,

        /** 给新船找位置。 */
        SLOT,

        /** 通关。 */
        WON,

        /** 全灭。 */
        LOST
    }

    private final long seed;
    private final Random rng;
    private final List<Enemy> route;
    private final List<ShipCatalog.Starter> starterChoices;
    private final List<String> log = new ArrayList<>();
    private final Map<String, Integer> fleetHp = new LinkedHashMap<>();

    private Fleet fleet;
    private int node;
    private Battle battle;
    private Stage stage = Stage.PICK_LOADOUT;
    private List<Reward> rewards = List.of();
    private Reward pending;
    private List<FleetUnit> placements = List.of();
    private int wonBattles;
    private Captain captain;
    private Ship pendingBuyShip;
    private List<Ship> shopCache;
    /** 自由航行星图（一整局一张，种子生成）。 */
    private List<StarNode> starMap = List.of();
    /** 星图连边（邻接表）：每颗星只连最近的 2~4 颗，跃迁只能沿这些边。 */
    private int[][] starGraph = new int[0][];
    private int currentStarId;
    private int visitedCount;
    /** 太阳系舰队（核心入场挑战）是否已被击败。 */
    private boolean gatePassed;
    /** 当前战斗是否是太阳系舰队（影响 settle 的胜负处理）。 */
    private boolean inGateBattle;
    /** 本次跃迁出发的节点：入场战败退时退回这里，否则人站在太阳上没法重打。 */
    private int starBeforeJump;
    /** 星图节点类型（自由航行星图上的异构恒星）。 */
    public enum NodeType { START, BATTLE, ELITE, EVENT, REST, SHOP, BOSS }

    /** 星图上的一颗恒星：极坐标（theta/radius，UI 换算屏幕位置）+ 类型 + 探索状态。 */
    public record StarNode(int id, String nameZh, NodeType type,
                           double theta, double radius, boolean visited) {
    }

    /** 一个事件选项：文字 + 效果（合金增减 / 修复百分比 / 全队掉血）。 */
    public record EventOption(String label, int alloyDelta, int healPct, int hpCost) {}

    /** 一次事件遭遇：标题 + 描述 + 2~3 个选项。 */
    public record GameEvent(String title, String desc, java.util.List<EventOption> options) {}

    private GameEvent eventCard;

    /** 用随机种子。 */
    public Run() {
        this(System.nanoTime() & 0x7FFF_FFFFL);
    }

    /** 指定种子（复现用）。 */
    public Run(long seed) {
        this.seed = seed;
        this.rng = new Random(seed);
        this.route = EnemyCatalog.route();
        this.starterChoices = ShipCatalog.startersFor(seed);
        buildStarMap();
        logMsg("航程开始：种子 " + seed);
    }

    // ------------------------------------------------------------------ 阶段入口

    /** 本局四支起始舰队（选项名 = 主角名；岚的编队每局现抓）。 */
    public List<ShipCatalog.Starter> starters() {
        return starterChoices;
    }

    /** 选起始舰队。舰队与主角绑定：选中哪支舰队，就由对应的主角出航。 */
    public void pickLoadout(int index) {
        require(stage == Stage.PICK_LOADOUT, "现在不是选舰队阶段");
        ShipCatalog.Starter starter = starterChoices.get(Math.max(0, Math.min(index, starterChoices.size() - 1)));
        fleet = starter.fleet();
        captain = new Captain(Captain.Protagonist.forStarter(starter.id()), 60);
        logMsg("舰长：" + captain.protagonist().nameZh()
                + "（" + starter.nameZh() + " · 专属技「" + captain.protagonist().signature().nameZh() + "」）");
        fleetHp.clear();
        for (FleetUnit unit : fleet.units()) {
            fleetHp.put(unit.id(), maxHpOf(unit));
        }
        logMsg("选择「" + starter.nameZh() + "」：" + fleet.size() + " 艘，合计 "
                + maxHp() + " 结构值");
        stage = Stage.MAP;
    }

    /** 选舰长（全舰队一名）。 */
    public void pickCaptain(int protagonistIndex) {
        var protos = Captain.Protagonist.values();
        var proto = protos[Math.max(0, Math.min(protos.length - 1, protagonistIndex))];
        captain = new Captain(proto, 60);
        logMsg("舰长：" + proto.nameZh());
        // v5：不再自动开战——星图模式下战斗由 travelTo 触发
    }

    /**
     * 玩家舰队战力估计（含舰长技能）。
     *
     * <p>口径与敌方编队一致：每艘船 = 结构值 + 防御×3 + 护盾×2 + 齐射×3，
     * 再加卡组爆发（卡面伤害 × 携带倍率 ×2）和舰长的结构/护盾/再生/首击/
     * 增伤/能量/抽牌。战斗节点的敌舰按这个值的 1.3×（普通）或 1.8×（精英）生成，
     * 不再按固定关数加码——玩家变强，遇到的编队跟着变强。</p>
     */
    public int playerPower() {
        if (fleet == null) {
            return 1;
        }
        Captain.Effects fx = captain == null ? Captain.Effects.NONE : captain.effects();
        return estimatePower(fleet, fx);
    }

    /** 纯函数版战力估算（供平衡测试/工具复用）。 */
    public static int estimatePower(Fleet fleet, Captain.Effects fx0) {
        Captain.Effects fx = fx0 == null ? Captain.Effects.NONE : fx0;
        // 结构只按 0.3 权重计入：血量翻倍后若按 1:1 算，堆血会压过火力/护甲，敌方编队被撑得过肉。
        double power = 0;
        for (FleetUnit unit : fleet.units()) {
            power += (fleet.maxHpOf(unit) + fx.structure()) * 0.3;
            power += (fleet.armorOf(unit) + fx.thorns()) * 2;
            power += fleet.plateOf(unit) * 3;
            power += fleet.volleyOf(unit) * 3;
        }
        // 只估船体：卡组/伤害% 不算进战力（否则牌多的舰队会虚高，敌人被生成得过强）。
        power += fx.regen() * 8 + fx.firstStrike() * 6 + fx.energy() * 10 + fx.draw() * 6;
        return Math.max(1, (int) Math.round(power));
    }

    /** 舰长被动的结构上限加成（对所有舰船持续生效）。 */
    public int structureBonus() {
        return captain == null ? 0 : captain.effects().structure();
    }

    /** 单舰结构上限（含舰长被动）。 */
    public int maxHpOf(FleetUnit unit) {
        return fleet == null ? 0 : fleet.maxHpOf(unit) + structureBonus();
    }

    /** 单舰当前结构值（非战斗界面用，含舰长被动的上限）。 */
    public int hpOf(FleetUnit unit) {
        int max = maxHpOf(unit);
        return Math.max(0, Math.min(max, fleetHp.getOrDefault(unit.id(), max)));
    }

    /** 开打。 */
    private void startBattle() {
        Captain.Effects fx = captain == null ? Captain.Effects.NONE : captain.effects();
        // 太阳系舰队：BOSS 节点首次跳跃 → 先打入场挑战，赢了才开门
        if (currentStar().type() == NodeType.BOSS && !gatePassed) {
            startGateBattle();
            return;
        }
        inGateBattle = false;
        int ownPower = playerPower();
        List<Enemy> squad;
        if (currentStar().type() == NodeType.BOSS) {
            // 最终旗舰：比精英再高一档（×2.0），仍是随玩家成长的编队
            int bossPower = (int) Math.round(ownPower * 1.5);
            squad = EnemyCatalog.squadForPower(bossPower, seed + visitedCount * 0x51EDL,
                    starMap.get(currentStarId).theta(), true);
            logMsg("我方战力 " + ownPower + "（含舰长）→ 最终旗舰编队战力 "
                    + EnemyCatalog.squadPower(squad) + "（旗舰 ×1.5）");
        } else {
            boolean elite = currentStar().type() == NodeType.ELITE;
            int targetPower = (int) Math.round(ownPower * (elite ? 1 : 0.8));
            squad = EnemyCatalog.squadForPower(targetPower, seed + visitedCount * 0x51EDL,
                    starMap.get(currentStarId).theta(), elite);
            logMsg("我方战力 " + ownPower + "（含舰长）→ 敌方编队战力 "
                    + EnemyCatalog.squadPower(squad)
                    + (elite ? "（精英 ）" : "（普通 ×0.8）"));
        }
        battle = new Battle(fleet, squad, Map.copyOf(fleetHp),
                seed ^ (visitedCount * STAGE_SEED_MUL), fx);
        battle.setSignature(captain == null ? Captain.Signature.NONE : captain.protagonist().signature());
        stage = Stage.BATTLE;
    }

    /** 打一张手牌。 */
    public boolean playCard(int index) {
        if (stage != Stage.BATTLE) {
            return false;
        }
        boolean played = battle.playCard(index);
        settle();
        return played;
    }

    /** 舰长技能。 */
    public boolean useSkill() {
        if (stage != Stage.BATTLE) {
            return false;
        }
        boolean used = battle.useSkill();
        settle();
        return used;
    }

    /** 结束回合。 */
    public void endTurn() {
        if (stage != Stage.BATTLE) {
            return;
        }
        battle.endTurn();
        settle();
    }

    /** 换目标。 */
    public void cycleTarget(int delta) {
        if (battle != null && stage == Stage.BATTLE) {
            battle.cycleTarget(delta);
        }
    }

    /** 选中某个敌人。 */
    public void selectTarget(int index) {
        if (battle != null && stage == Stage.BATTLE) {
            battle.selectTarget(index);
        }
    }

    // ------------------------------------------------------------------ 结算

    private void settle() {
        if (battle == null) {
            return;
        }
        if (battle.result() == Battle.Result.WON) {
            wonBattles++;
            fleetHp.clear();
            fleetHp.putAll(battle.fleetRoster().hpSnapshot());
            logMsg("清空 " + battle.squad().size() + " 艘敌舰，第 " + battle.turn() + " 回合收尾，"
                    + "舰队还剩 " + battle.hp() + " 结构值");
            int alloyGain = 12 + node * 8 + battle.squad().size() * 3 + rng.nextInt(6);
            if (captain != null) {
                alloyGain += alloyGain * captain.effects().alloyPct() / 100;
                captain.addAlloy(alloyGain);
            }
            logMsg("打捞因瓦合金 x" + alloyGain);
            if (inGateBattle) {
                gatePassed = true;
                inGateBattle = false;
                logMsg("太阳系舰队被击溃——核心入口打开了。");
                fleetHp.clear();
                fleetHp.putAll(battle.fleetRoster().hpSnapshot());
                if (currentStar().type() == NodeType.BOSS) {
                    startBattle();   // BOSS 入口：直接接决战
                } else {
                    stage = Stage.SPACE_PORT;   // 太阳入口：开门后照常补给
                }
                return;
            }
            if (currentStar().type() == NodeType.BOSS) {
                stage = Stage.WON;
                logMsg("抵达星核，航程通关");
            } else {
                stage = Stage.SPACE_PORT;
            }
        } else if (battle.result() == Battle.Result.LOST) {
            if (inGateBattle) {
                // 太阳系舰队战斗失败：不结束游戏，退回跃迁前的节点（舰保留 30% 结构值）
                fleetHp.clear();
                for (FleetRoster.Unit unit : battle.fleetUnits()) {
                    fleetHp.put(unit.id(),
                            Math.max(unit.maxHp() * 30 / 100, Math.max(0, unit.hp())));
                }
                currentStarId = starBeforeJump;
                stage = Stage.MAP;
                inGateBattle = false;
                logMsg("太阳系舰队击退了你的进攻——但你的舰队并未全灭。回去修整，再来。");
            } else {
                fleetHp.clear();
                for (FleetRoster.Unit unit : battle.fleetUnits()) {
                    fleetHp.put(unit.id(), Math.max(0, unit.hp()));
                }
                stage = Stage.LOST;
                logMsg("舰队在 " + battle.enemy().nameZh() + " 处全灭");
            }
        }
    }

    /**
     * 掷战利品：最多两艘新船 + 一条强化 + 一张卡。
     *
     * <p>掉落池 = 这一关编队里各敌舰的<b>本体船型</b>——敌我同源之后不需要单独的掉落表，
     * 打沉谁就有机会俘获谁。</p>
     */
    private List<Reward> rollRewards() {
        List<Reward> out = new ArrayList<>(REWARD_MAX);
        Set<String> offered = new LinkedHashSet<>();

        Set<Ship> pool = new LinkedHashSet<>();
        for (Enemy foe : battle.squad()) {
            pool.add(foe.hull());
        }
        List<Ship> candidates = new ArrayList<>();
        for (Ship ship : pool) {
            if (fleet.unit(ship.id()) == null && !offered.contains(ship.id())) {
                candidates.add(ship);
            }
        }
        for (int i = 0; i < DROP_SHIPS && !candidates.isEmpty(); i++) {
            Ship pick = candidates.remove(rng.nextInt(candidates.size()));
            offered.add(pick.id());
            out.add(Reward.ship(pick));
        }

        // 轴炮舰队：有一定概率补一艘轴炮船（不然轴炮倍率很难爬上去）
        if (fleet.hasAxial() && rng.nextInt(100) < AXIAL_LUCK_PERCENT) {
            List<Ship> axial = new ArrayList<>();
            for (Ship ship : ShipCatalog.axialShips()) {
                if (fleet.unit(ship.id()) == null && !offered.contains(ship.id())) {
                    axial.add(ship);
                }
            }
            if (!axial.isEmpty()) {
                Ship pick = axial.get(rng.nextInt(axial.size()));
                offered.add(pick.id());
                if (out.size() >= REWARD_MAX) {
                    out.remove(out.size() - 1);
                }
                out.add(0, Reward.ship(pick));
            }
        }

        // 强化：手上已经有的船升一级
        List<FleetUnit> owned = new ArrayList<>(fleet.units());
        if (!owned.isEmpty()) {
            owned.sort((a, b) -> a.id().compareTo(b.id()));
            FleetUnit pick = owned.get(rng.nextInt(owned.size()));
            out.add(Reward.upgrade(pick.ship()));
        }

        // 卡：战利品只掉增益/特殊卡（操作卡只能从船上和敌方计划表来，别混进来）
        Set<String> have = new LinkedHashSet<>(fleet.cardIds());
        List<Card> cards = new ArrayList<>();
        for (Card card : CardCatalog.dropPool()) {
            if (!have.contains(card.id())) {
                cards.add(card);
            }
        }
        if (!cards.isEmpty()) {
            out.add(Reward.card(cards.get(rng.nextInt(cards.size()))));
        }
        return List.copyOf(out);
    }

    /** 挑一项战利品。 */
    public void chooseReward(int index) {
        throw new IllegalStateException("deprecated: use space port");
    }

    /** 当前舰队的棋盘上，这型船的每一个合法落点。 */
    public List<FleetUnit> candidatePlacements(Ship ship) {
        if (fleet == null || ship == null) {
            return List.of();
        }
        List<FleetUnit> out = new ArrayList<>();
        for (int[] spot : fleet.placementsFor(ship)) {
            out.add(new FleetUnit(ship, 1, spot[0], spot[1]));
        }
        return List.copyOf(out);
    }

    /** 当前待编入的船的落点。 */
    public List<FleetUnit> candidatePlacements() {
        return pending == null || pending.ship() == null ? List.of() : candidatePlacements(pending.ship());
    }

    /** 把待编入的船放到第 {@code index} 个候选落点。 */
    public void placeAt(int index) {
        require(stage == Stage.SLOT, "现在不是编排阶段");
        List<FleetUnit> spots = candidatePlacements();
        if (index < 0 || index >= spots.size()) {
            return;
        }
        FleetUnit spot = spots.get(index);
        fleet = fleet.withUnit(spot);
        fleetHp.put(spot.id(), maxHpOf(fleet.unit(spot.id())));
        logMsg("「" + spot.nameZh() + "」编入舰队，落在 " + spot.x() + "/" + spot.y());
        ShipArtStore.trySaveFleet(fleet);
        pending = null;
        placements = List.of();
        nextNode();
    }

    /** 放弃战利品。 */
    public void skipReward() {
        throw new IllegalStateException("deprecated: use space port");
    }

    /**
     * 变阵：把某艘船挪到新落位。战斗中挪会同步改战斗棋盘（血和盾不丢），挪成功就写盘。
     *
     * @return 挪不动（越界或压住别的船）返回 false
     */
    public boolean moveShip(String shipId, int x, int y) {
        if (stage == Stage.BATTLE) {
            return false;
        }
        {
            Fleet moved = fleet.movedUnit(shipId, x, y);
            if (moved == null) {
                return false;
            }
            fleet = moved;
        }
        ShipArtStore.trySaveFleet(fleet);
        return true;
    }

    public Captain captain() { return captain; }
    public int alloy() { return captain == null ? 0 : captain.alloy(); }

    /** 一整局的星图：30 颗恒星在盘面内<b>均匀网状分布</b>（不贴旋臂），
     * 起点 random 外缘、Boss 居中。跃迁圈交叉成网——走哪条线是真正的策略选择。
     * 生成校验：最小间距 34（热区不重叠）、每颗非 Boss 星跃迁半径内至少 3 颗邻居（网状连通）。
     * 名字不预设：生成完位置后按盘面投影到赤经/赤纬，从真实亮星表里取最近的一颗。 */
    private void buildStarMap() {
        List<StarNode> out = null;
        Random r = new Random(seed ^ 0x57A2L);
        for (int attempt = 0; attempt < 80; attempt++) {
            List<StarNode> candidate = tryBuildStarMap(r);
            if (candidate != null) {
                out = candidate;
                break;
            }
        }
        if (out == null) {
            out = fallbackStarMap();
        }
        // id 重编为下标：哨站占位 id 与起点重复，会让 travelTo/currentStar 认错节点
        for (int i = 0; i < out.size(); i++) {
            StarNode st = out.get(i);
            if (st.id() != i) {
                out.set(i, new StarNode(i, st.nameZh(), st.type(),
                        st.theta(), st.radius(), st.visited()));
            }
        }
        // 太阳节点：距太阳最近的常规节点让位给太阳（起点/Boss 除外）
        double[] sunPos = sunGamePos();
        double sunX = Math.cos(sunPos[0]) * sunPos[1];
        double sunY = Math.sin(sunPos[0]) * sunPos[1] * GALAXY_SQUASH;
        int closestIdx = 0;
        double closestDist = Double.MAX_VALUE;
        for (int i = 0; i < out.size(); i++) {
            StarNode st = out.get(i);
            if (st.type() == NodeType.START || st.type() == NodeType.BOSS) { continue; }
            double sx = Math.cos(st.theta()) * st.radius();
            double sy = Math.sin(st.theta()) * st.radius() * GALAXY_SQUASH;
            double d = Math.hypot(sx - sunX, sy - sunY);
            if (d < closestDist) { closestDist = d; closestIdx = i; }
        }
        // 太阳节点就放在太阳的真实投影位置（不再退回旧槽位：节点必须和太阳标记重合）
        StarNode oldNode = out.get(closestIdx);
        StarNode sunAt = new StarNode(oldNode.id(), "太阳", NodeType.REST,
                sunPos[0], sunPos[1], false);
        // 若和别的节点挤在一起，把别的节点沿自己的半径往外推，保证太阳节点位置准确
        for (int i = 0; i < out.size(); i++) {
            if (i == closestIdx) {
                continue;
            }
            StarNode st = out.get(i);
            double rr = st.radius();
            while (starDist(st, sunAt) < 34 && rr < MAP_RX - 1) {
                rr += 1;
                st = new StarNode(st.id(), st.nameZh(), st.type(),
                        st.theta(), rr, st.visited());
            }
            out.set(i, st);
        }
        out.set(closestIdx, sunAt);

        out = assignRealNames(out);
        starGraph = buildStarGraph(out);
        starMap = List.copyOf(out);
        currentStarId = 0;
        visitedCount = 1;
    }

    /** 每颗星最多连几条航路（只连最近的，天然形成稀疏路网）。 */
    private static final int MAX_STAR_LINKS = 4;

    /** 每颗星至少连几条航路，避免死路。 */
    private static final int MIN_STAR_LINKS = 2;

    /** 单条航路的最大长度（设计像素）：超过就不连，避免出现横跨半个银河的“虫洞”。 */
    private static final double MAX_LINK_DIST = 125.0;

    /**
     * 星图连边：先给每颗星分配 2/3/4 的目标度数（4 只占少数），
     * 再按距离从近到远加边（每端不超过自己的目标度数），
     * 然后给不足 {@link #MIN_STAR_LINKS} 条的星补最近的边，最后把断开的连通块接起来。
     *
     * <p>结果是稀疏航路网：多数星只和最近的 2~3 颗相连，少数枢纽连 4 颗；
     * 玩家在每个节点的选择从"圈内所有星"收敛到 2~4 条路。</p>
     */
    private static int[][] buildStarGraph(List<StarNode> nodes) {
        int n = nodes.size();
        boolean[][] adj = new boolean[n][n];
        int[] deg = new int[n];
        int sun = -1;
        for (int i = 0; i < n; i++) {
            if ("太阳".equals(nodes.get(i).nameZh())) {
                sun = i;
                break;
            }
        }
        if (sun >= 0) {
            // 核心区唯一的对外入口：先把太阳和最近的核心区节点接起来，避免核心区变孤岛
            int coreBest = -1;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (i == sun || nodes.get(i).radius() >= CORE_BARRIER) {
                    continue;
                }
                double d = starDist(nodes.get(sun), nodes.get(i));
                if (d < best) {
                    best = d;
                    coreBest = i;
                }
            }
            if (coreBest >= 0) {
                adj[sun][coreBest] = true;
                adj[coreBest][sun] = true;
                deg[sun]++;
                deg[coreBest]++;
            }
        }
        List<int[]> edges = new ArrayList<>(n * (n - 1) / 2);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (!starEdgeAllowed(nodes, i, j, sun)) {
                    continue;
                }
                double d = starDist(nodes.get(i), nodes.get(j));
                if (d > MAX_LINK_DIST) {
                    continue;
                }
                edges.add(new int[]{i, j, (int) Math.round(d * 100)});
            }
        }
        // 每颗星的目标度数：多留 2/3 连，只有少数星给到 4，避免满屏四岔路口
        int[] cap = new int[n];
        java.util.Random capRnd = new java.util.Random(nodes.hashCode() ^ 0x5EEDL);
        for (int i = 0; i < n; i++) {
            int roll = capRnd.nextInt(100);
            cap[i] = roll < 15 ? MAX_STAR_LINKS : roll < 60 ? 3 : MIN_STAR_LINKS;
        }
        edges.sort(java.util.Comparator.comparingInt(e -> e[2]));
        for (int[] e : edges) {
            if (deg[e[0]] < cap[e[0]] && deg[e[1]] < cap[e[1]]) {
                adj[e[0]][e[1]] = true;
                adj[e[1]][e[0]] = true;
                deg[e[0]]++;
                deg[e[1]]++;
            }
        }
        // 补最低度数：找最近的、还有空位的邻居
        for (int i = 0; i < n; i++) {
            while (deg[i] < MIN_STAR_LINKS) {
                int best = nearestNeighbour(nodes, adj, deg, cap, i, sun, true, MAX_STAR_LINKS);
                if (best < 0) {
                    // 兜底：目标度数已满，但仍不超过 4 条硬上限
                    best = nearestNeighbour(nodes, adj, deg, cap, i, sun, false, MAX_STAR_LINKS);
                }
                if (best < 0) {
                    // 最后兜底：连 4 条都满了，宁可多一条也不留死路
                    best = nearestNeighbour(nodes, adj, deg, cap, i, sun, false, Integer.MAX_VALUE);
                }
                if (best < 0) {
                    break;
                }
                adj[i][best] = true;
                adj[best][i] = true;
                deg[i]++;
                deg[best]++;
            }
        }
        // 连通性：断开的块用最短跨块边接起来（优先还有空位的端点）
        while (true) {
            int[] comp = components(adj);
            if (isSingleComponent(comp)) {
                break;
            }
            int bi = -1;
            int bj = -1;
            double bestDist = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (comp[i] == comp[j] || deg[i] >= cap[i]
                            || deg[j] >= cap[j]
                            || !starEdgeAllowed(nodes, i, j, sun)) {
                        continue;
                    }
                    double d = starDist(nodes.get(i), nodes.get(j));
                    if (d > MAX_LINK_DIST) {
                        continue;
                    }
                    if (d < bestDist) {
                        bestDist = d;
                        bi = i;
                        bj = j;
                    }
                }
            }
            if (bi < 0) {
                // 兜底 1：目标度数满了，但尽量不超过 4 条硬上限
                for (int i = 0; i < n && bi < 0; i++) {
                    for (int j = i + 1; j < n; j++) {
                        if (comp[i] == comp[j] || deg[i] >= MAX_STAR_LINKS
                                || deg[j] >= MAX_STAR_LINKS
                                || !starEdgeAllowed(nodes, i, j, sun)) {
                            continue;
                        }
                        double d = starDist(nodes.get(i), nodes.get(j));
                        if (d > MAX_LINK_DIST) {
                            continue;
                        }
                        if (d < bestDist) {
                            bestDist = d;
                            bi = i;
                            bj = j;
                        }
                    }
                }
            }
            if (bi < 0) {
                // 兜底 2：所有端点都满 4 条时，宁可多一条边也不让星图断开
                for (int i = 0; i < n && bi < 0; i++) {
                    for (int j = i + 1; j < n; j++) {
                        if (comp[i] == comp[j] || !starEdgeAllowed(nodes, i, j, sun)) {
                            continue;
                        }
                        double d = starDist(nodes.get(i), nodes.get(j));
                        if (d < bestDist) {
                            bestDist = d;
                            bi = i;
                            bj = j;
                        }
                    }
                }
                if (bi < 0) {
                    break;
                }
            }
            adj[bi][bj] = true;
            adj[bj][bi] = true;
            deg[bi]++;
            deg[bj]++;
        }
        int[][] graph = new int[n][];
        for (int i = 0; i < n; i++) {
            int count = 0;
            for (int j = 0; j < n; j++) {
                if (adj[i][j]) {
                    count++;
                }
            }
            graph[i] = new int[count];
            int at = 0;
            for (int j = 0; j < n; j++) {
                if (adj[i][j]) {
                    graph[i][at++] = j;
                }
            }
        }
        return graph;
    }

    private static int[] components(boolean[][] adj) {
        int n = adj.length;
        int[] comp = new int[n];
        java.util.Arrays.fill(comp, -1);
        int next = 0;
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        for (int start = 0; start < n; start++) {
            if (comp[start] >= 0) {
                continue;
            }
            comp[start] = next;
            queue.add(start);
            while (!queue.isEmpty()) {
                int u = queue.poll();
                for (int v = 0; v < n; v++) {
                    if (adj[u][v] && comp[v] < 0) {
                        comp[v] = next;
                        queue.add(v);
                    }
                }
            }
            next++;
        }
        return comp;
    }

    private static boolean isSingleComponent(int[] comp) {
        for (int c : comp) {
            if (c != 0) {
                return false;
            }
        }
        return true;
    }

    /** 核心区和外部不能直连，唯一的门是太阳节点。 */
    private static boolean starEdgeAllowed(List<StarNode> nodes, int a, int b, int sun) {
        if (a == sun || b == sun) {
            return true;
        }
        boolean coreA = nodes.get(a).radius() < CORE_BARRIER;
        boolean coreB = nodes.get(b).radius() < CORE_BARRIER;
        return coreA == coreB;
    }

    /** 最近的、未相连、且不超过硬上限/目标度数的邻居；没有则返回 -1。 */
    private static int nearestNeighbour(List<StarNode> nodes, boolean[][] adj, int[] deg,
                                        int[] cap, int from, int sun,
                                        boolean respectCap, int hardLimit) {
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int j = 0; j < nodes.size(); j++) {
            if (j == from || adj[from][j] || deg[j] >= hardLimit
                    || (respectCap && cap != null && deg[j] >= cap[j])
                    || !starEdgeAllowed(nodes, from, j, sun)) {
                continue;
            }
            double d = starDist(nodes.get(from), nodes.get(j));
            if (d > MAX_LINK_DIST) {
                continue;
            }
            if (d < bestDist) {
                bestDist = d;
                best = j;
            }
        }
        return best;
    }

    /**
     * 太阳在星图上的极坐标 (theta, radius)。
     * 当天真实太阳位置公式 → 投影到星图坐标系 → 钳制到核心区边缘（53% 半径）。
     */
    private static double[] sunGamePos() {
        long n = java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.of(2000, 1, 1), java.time.LocalDate.now());
        double meanLon = 280.460 + 0.9856474 * n;
        double meanAnom = Math.toRadians(357.528 + 0.9856003 * n);
        double lambda = Math.toRadians(meanLon + 1.915 * Math.sin(meanAnom)
                + 0.020 * Math.sin(2 * meanAnom));
        double eps = Math.toRadians(23.439 - 0.0000004 * n);
        double ra = Math.toDegrees(Math.atan2(Math.cos(eps) * Math.sin(lambda),
                Math.cos(lambda)));
        double dec = Math.toDegrees(Math.asin(Math.sin(eps) * Math.sin(lambda)));
        double raH = (ra / 15.0 + 24.0) % 24.0;
        double x = (raH / 12.0 - 1.0) * MAP_RX;
        double y = -(dec / MAP_DEC_SPAN) * (MAP_RX * GALAXY_SQUASH);
        double dist = Math.hypot(x, y);
        double maxR = MAP_RX * 0.53;
        if (dist > maxR) { x = x / dist * maxR; y = y / dist * maxR; }
        double theta = Math.atan2(y / GALAXY_SQUASH, x);
        double radius = Math.hypot(x, y / GALAXY_SQUASH);
        return new double[]{theta, radius};
    }

    /** 星图节点数：起点 + Boss + 28 颗。 */
    private static final int STAR_MAP_SIZE = 48;
    /** 星图盘面的最大半径（与撒点范围 r∈[42,246] 一致）。 */
    public static final double MAP_RX = 246.0;
    /** 星图投影出的赤纬跨度：盘面上/下缘对应 ±60°。 */
    public static final double MAP_DEC_SPAN = 60.0;

    /**
     * 盘面坐标 → 天球坐标（等距圆柱投影）：横轴 x 映射赤经 0–24h，纵轴 y 映射赤纬 +60–-60°。
     * 盘面是虚构的银河俯视图，这里只是给“最近的真实恒星”一个确定的换算规则。
     */
    private static double[] skyCoordOf(double theta, double radius) {
        double x = Math.cos(theta) * radius;
        double y = Math.sin(theta) * radius * GALAXY_SQUASH;
        double ra = (x / MAP_RX + 1.0) * 12.0;
        double dec = -(y / (MAP_RX * GALAXY_SQUASH)) * MAP_DEC_SPAN;
        return new double[]{ra, dec};
    }

    /** 按盘面坐标给每颗星挑最近的、尚未用过的真实恒星名（30 颗星不重名）。 */
    private static List<StarNode> assignRealNames(List<StarNode> nodes) {
        boolean[] used = new boolean[StarCatalog.STARS.size()];
        List<StarNode> out = new ArrayList<>(nodes.size());
        for (StarNode st : nodes) {
            if ("太阳".equals(st.nameZh())) { out.add(st); continue; }
            double[] sky = skyCoordOf(st.theta(), st.radius());
            int best = -1;
            double bestDeg = Double.MAX_VALUE;
            for (int i = 0; i < StarCatalog.STARS.size(); i++) {
                if (used[i]) {
                    continue;
                }
                var cand = StarCatalog.STARS.get(i);
                double deg = StarCatalog.angleDeg(sky[0], sky[1], cand.raHours(), cand.decDeg());
                if (deg < bestDeg) {
                    bestDeg = deg;
                    best = i;
                }
            }
            if (best < 0) {
                out.add(st);
                continue;
            }
            used[best] = true;
            out.add(new StarNode(st.id(), StarCatalog.STARS.get(best).nameZh(),
                    st.type(), st.theta(), st.radius(), st.visited()));
        }
        return out;
    }

    /** 均匀撒点：极坐标 (r∈[42,246], θ 全周)，拒绝采样保证最小间距。 */
    private List<StarNode> tryBuildStarMap(Random r) {
        int need = STAR_MAP_SIZE - 4;   // 起点 + Boss + 2 哨站
        List<StarNode> out = new ArrayList<>(STAR_MAP_SIZE);
        // 起点：外缘随机方向
        double startTheta = r.nextDouble() * Math.PI * 2;
        out.add(new StarNode(0, "", NodeType.START, startTheta, 232, true));
        // Boss：中心
        out.add(new StarNode(1, "", NodeType.BOSS, r.nextDouble() * Math.PI * 2, 10, false));
        // 核心区哨站 ×2（屏障内、Boss 外——太阳系舰队卫戍）
        for (int si = 0; si < 2; si++) {
            double sth = r.nextDouble() * Math.PI * 2;
            double srr = 22 + r.nextDouble() * 8;
            out.add(new StarNode(0, "", NodeType.BATTLE, sth, srr, false));
        }
        // 其余：泊松盘式拒绝采样（均匀 + 最小间距 34）
        int placed = 0;
        int tries = 0;
        while (placed < need && tries++ < 900) {
            double theta = r.nextDouble() * Math.PI * 2;
            double radius = 42 + r.nextDouble() * 204;
            StarNode cand = new StarNode(0, "", NodeType.BATTLE, theta, radius, false);
            boolean ok = true;
            for (StarNode st : out) {
                if (starDist(st, cand) < 34) { ok = false; break; }
            }
            if (!ok) { continue; }
            int id = 2 + placed;
            out.add(new StarNode(id, "", rollNodeType(r), theta, radius, false));
            placed++;
        }
        if (placed < need) { return null; }
        // 校验：每颗非 Boss 星跃迁半径内至少 3 颗邻居（网状，不是链状）
        for (StarNode st : out) {
            if (st.type() == NodeType.BOSS) { continue; }
            int near = 0;
            for (StarNode other : out) {
                if (other.id() != st.id() && starDist(st, other) <= JUMP_RADIUS) {
                    near++;
                }
            }
            if (near < 3) { return null; }
        }
        return out;
    }

    /** 兜底布局：3 圈网格 + 扰动（随机撒点全失败时用，概率极低）。 */
    private List<StarNode> fallbackStarMap() {
        List<StarNode> out = new ArrayList<>(STAR_MAP_SIZE);
        out.add(new StarNode(0, "", NodeType.START, 0, 232, true));
        out.add(new StarNode(1, "", NodeType.BOSS, 0, 10, false));
        NodeType[] types = {
                NodeType.BATTLE, NodeType.EVENT, NodeType.REST, NodeType.SHOP,
                NodeType.BATTLE, NodeType.ELITE, NodeType.EVENT, NodeType.BATTLE,
                NodeType.REST, NodeType.ELITE, NodeType.BATTLE, NodeType.EVENT,
                NodeType.SHOP, NodeType.REST, NodeType.BATTLE, NodeType.ELITE,
                NodeType.EVENT, NodeType.BATTLE, NodeType.REST, NodeType.SHOP,
                NodeType.BATTLE, NodeType.EVENT, NodeType.ELITE, NodeType.REST,
                NodeType.BATTLE, NodeType.SHOP, NodeType.EVENT, NodeType.BATTLE};
        int need = STAR_MAP_SIZE - 2;
        int placed = 0;
        int rings = 3;
        int base = need / rings;
        int extra = need % rings;
        for (int ring = 0; ring < rings && placed < need; ring++) {
            int count = base + (ring < extra ? 1 : 0);
            double radius = 70 + ring * 80;
            for (int k = 0; k < count && placed < need; k++) {
                int id = 2 + placed;
                double theta = Math.PI * 2 * k / count + 0.3;
                out.add(new StarNode(id, "", types[placed % types.length],
                        theta, radius, false));
                placed++;
            }
        }
        return out;
    }

    private NodeType rollNodeType(Random r) {
        int roll = r.nextInt(100);
        if (roll < 38) { return NodeType.BATTLE; }
        if (roll < 52) { return NodeType.ELITE; }
        if (roll < 74) { return NodeType.EVENT; }
        if (roll < 88) { return NodeType.REST; }
        return NodeType.SHOP;
    }

    /** 星图。 */
    public List<StarNode> starMap() { return starMap; }

    /** 星图邻接表：{@code starGraph()[i]} 是第 i 颗星连着的星的下标（2~4 条）。 */
    public int[][] starGraph() { return starGraph; }

    /** 当前所在的恒星。 */
    public StarNode currentStar() {
        for (StarNode st : starMap) {
            if (st.id() == currentStarId) { return st; }
        }
        return starMap.get(0);
    }

    /** 已探索恒星数。 */
    /** 太阳系舰队是否已被击溃。 */
    public boolean gatePassed() { return gatePassed; }

    public int visitedCount() { return visitedCount; }

    /** 星系 y 压扁系数（宽屏椭圆星系）。UI 与距离计算共用这一个口径。 */
    public static final double GALAXY_SQUASH = 0.62;

    /** 两星之间的笛氏距离（设计坐标尺度，星图中心为原点、y 按 {@link #GALAXY_SQUASH} 压扁）。 */
    public static double starDist(StarNode a, StarNode b) {
        double ax = Math.cos(a.theta()) * a.radius();
        double ay = Math.sin(a.theta()) * a.radius() * GALAXY_SQUASH;
        double bx = Math.cos(b.theta()) * b.radius();
        double by = Math.sin(b.theta()) * b.radius() * GALAXY_SQUASH;
        return Math.hypot(ax - bx, ay - by);
    }

    /** 跃迁半径：星图生成时用来校验网密度（每颗非 Boss 星周围至少 3 颗），
     * 实际跃迁走 {@link #starGraph()} 的连边，不再是"半径内全部可达"。 */
    public static final double JUMP_RADIUS = 105.0;

    /**
     * 核心区屏障半径：radius 低于此值的节点属于"银心禁区"，
     * 与外部节点之间不可跃迁（除非太阳系舰队已被击溃）。
     * 常规节点撒在 r∈[42,246]，BOSS 在 r=10 —— 屏障设在 30。
     */
    public static final double CORE_BARRIER = 30.0;

    /** 这颗星现在能不能跳过去（未探索 + 圈内；圈外无星时开应急跃迁；Boss 需探索 2/3）。 */
    public boolean canJumpTo(int starId) {
        for (StarNode st : reachableStars()) {
            if (st.id() == starId) {
                return true;
            }
        }
        return false;
    }

    /** 当前可跃迁的恒星：沿星图连边相邻的星（未访问的触发节点、已访问的可走回头路——纯移动）。
     * 死胡同靠回头路解决：跳回旧星再跳它邻接的未访问星。 */
    public List<StarNode> reachableStars() {
        List<StarNode> out = new ArrayList<>();
        boolean bossOpen = visitedCount >= starMap.size() * 2 / 3;
        boolean curInside = currentStar().radius() < CORE_BARRIER;
        for (StarNode st : starMap) {
            if (st.id() == currentStarId) { continue; }
            if (!st.visited() && st.type() == NodeType.BOSS && !bossOpen) { continue; }
            // 核心屏障：太阳系舰队未击溃前，只阻止"外→内"——从核心区撤退永远允许
            if (!gatePassed) {
                boolean stInside = st.radius() < CORE_BARRIER;
                if (!curInside && stInside) {
                    if (st.type() == NodeType.BOSS && bossOpen) {
                        // 放行——startBattle 检测 !gatePassed 触发太阳系舰队
                    } else {
                        continue;
                    }
                }
            }
            if (linked(currentStarId, st.id())) {
                out.add(st);
            }
        }
        return out;
    }

    private boolean linked(int a, int b) {
        if (a < 0 || a >= starGraph.length) {
            return false;
        }
        for (int v : starGraph[a]) {
            if (v == b) {
                return true;
            }
        }
        return false;
    }

    /** 太阳系舰队入场战：太阳节点或 BOSS 节点首次进入时触发，胜利后核心屏障开门。 */
    private void startGateBattle() {
        Captain.Effects fx = captain == null ? Captain.Effects.NONE : captain.effects();
        battle = new Battle(fleet,
                EnemyCatalog.gateSquad(seed),
                Map.copyOf(fleetHp), seed ^ 0x6A7E1L, fx);
        battle.setSignature(captain == null ? Captain.Signature.NONE : captain.protagonist().signature());
        inGateBattle = true;
        stage = Stage.BATTLE;
        logMsg("太阳系舰队出现在核心入口——他们第一次并肩作战。");
    }

    /** 跃迁到恒星：未访问的按类型分派（战斗/事件/休息/商店/Boss）；已访问的 = 回头路（纯移动）。 */
    public boolean travelTo(int starId) {
        if (stage != Stage.MAP) { return false; }
        if (!canJumpTo(starId)) { return false; }
        StarNode target = null;
        for (StarNode st : starMap) {
            if (st.id() == starId) { target = st; break; }
        }
        if (target == null) { return false; }
        boolean revisited = target.visited();
        if (!revisited) {
            List<StarNode> next = new ArrayList<>(starMap.size());
            for (StarNode st : starMap) {
                next.add(st.id() == starId
                        ? new StarNode(st.id(), st.nameZh(), st.type(), st.theta(),
                                st.radius(), true)
                        : st);
            }
            starMap = List.copyOf(next);
            visitedCount++;
        }
        starBeforeJump = currentStarId;
        currentStarId = starId;
        logMsg(revisited ? "折返「" + target.nameZh() + "」"
                : "跃迁到「" + target.nameZh() + "」（" + target.type() + "）");
        if (revisited) {
            switch (target.type()) {
                case SHOP -> {
                    shopCache = null;
                    stage = Stage.SPACE_PORT;
                    logMsg("重返「" + target.nameZh() + "」：商店补货了");
                }
                case REST -> {
                    if (target.nameZh().equals("太阳") && !gatePassed) {
                        startGateBattle();   // 入场战败退后太阳已记 visited：折返也要能再战
                        return true;
                    }
                    restFleet(17);
                    stage = Stage.MAP;
                    logMsg("重返「" + target.nameZh() + "」：锚地余温修复 17%");
                }
                case BATTLE, ELITE -> {
                    stage = Stage.MAP;
                    logMsg("重返「" + target.nameZh() + "」：废墟。残骸已捞干净");
                }
                case EVENT -> {
                    stage = Stage.MAP;
                    logMsg("重返「" + target.nameZh() + "」：事件已结束");
                }
                default -> {
                    stage = Stage.MAP;
                }
            }
            return true;
        }
        switch (target.type()) {
            case BOSS -> {
                restFleet(60); // 决战整备：抵达星核前最后修一轮
                logMsg("决战整备：全队修复 60%");
                startBattle();
            }
            case BATTLE, ELITE -> startBattle();
            case EVENT -> {
                eventCard = rollEvent();
                stage = Stage.EVENT;
            }
            case REST -> {
                if (target.nameZh().equals("太阳") && !gatePassed) {
                    startGateBattle();   // 太阳系舰队：核心区入场挑战
                } else {
                    restFleet(35);
                    logMsg("静默锚地：全队修复 35%");
                    stage = Stage.MAP;
                }
            }
            case SHOP -> {
                shopCache = null;
                stage = Stage.SPACE_PORT;
            }
            default -> stage = Stage.MAP;
        }
        return true;
    }



    public boolean upgradeShip(String shipId) {
        if (stage != Stage.SPACE_PORT || fleet == null) { return false; }
        FleetUnit unit = fleet.unit(shipId);
        if (unit == null) { return false; }
        int cost = 30 * unit.level();
        if (captain == null || !captain.spendAlloy(cost)) { return false; }
        fleet = fleet.upgradedUnit(shipId);
        fleetHp.put(shipId, maxHpOf(fleet.unit(shipId)));
        logMsg("Lv up: " + unit.nameZh());
        return true;
    }

    public boolean trainSkill(int branchOrdinal) {
        if (stage != Stage.SPACE_PORT || captain == null) { return false; }
        int before = structureBonus();
        if (!captain.unlock(branchOrdinal)) { return false; }
        int delta = structureBonus() - before;
        if (delta > 0 && fleet != null) {
            // 结构被动是持续属性：解锁/升级立刻把新上限发给全队，并补上这部分结构值
            for (FleetUnit unit : fleet.units()) {
                int max = maxHpOf(unit);
                int hp = fleetHp.getOrDefault(unit.id(), max);
                fleetHp.put(unit.id(), Math.min(max, hp + delta));
            }
        }
        logMsg("Skill: " + captain.branches().get(branchOrdinal).nameZh());
        return true;
    }

    /** 太空港商店：3 艘不在舰队里的船（进港时生成一次，绘制每帧调用不能变——变了就闪烁）。 */
    public List<Ship> shopInventory() {
        if (stage != Stage.SPACE_PORT) { return List.of(); }
        if (shopCache == null) {
            if (fleet == null) { return List.of(); }
            List<Ship> available = new ArrayList<>();
            for (Ship s : ShipCatalog.all()) {
                if (fleet.unit(s.id()) == null) {
                    available.add(s);
                }
            }
            Collections.shuffle(available, rng);
            shopCache = List.copyOf(available.subList(0, Math.min(3, available.size())));
        }
        return shopCache;
    }

    /** 船价 = 原始 EHP + 基础费（不随全局血量翻倍膨胀）。 */
    public int shipCost(Ship ship) {
        return ship.rawHp() + ship.plate() * 3 + ship.volley() * 2 + 20;
    }

    /**
     * 战舰融合：两艘同国同型 → 同国同型高一梯度，等级取两艘里较低的。
     * 两艘都消耗，合金按成品梯度计价。返回 null = 成功，否则为提示文本。
     */
    public String fuseShip(String idA, String idB) {
        if (stage != Stage.SPACE_PORT) {
            return "只能在太空港融合";
        }
        if (pendingBuyShip != null) {
            return "先放好采购的新船";
        }
        if (idA.equals(idB)) {
            return "选两艘不同的船";
        }
        com.hysx.salvosequence.fleet.Ship a = com.hysx.salvosequence.fleet.ShipCatalog.byId(idA);
        com.hysx.salvosequence.fleet.Ship b = com.hysx.salvosequence.fleet.ShipCatalog.byId(idB);
        if (a == null || b == null || fleet.unit(idA) == null || fleet.unit(idB) == null) {
            return "两艘船都必须在编组内";
        }
        if (!com.hysx.salvosequence.fleet.FactionCatalog.factionOf(a)
                .equals(com.hysx.salvosequence.fleet.FactionCatalog.factionOf(b))) {
            return "国家不同，拒绝融合";
        }
        if (a.role() != b.role()) {
            return "舰种不同";
        }
        int tier = Math.min(a.tier(), b.tier()) + 1;
        if (tier > 4) {
            return "已经是最高梯度";
        }
        var target = com.hysx.salvosequence.fleet.ShipCatalog.all().stream()
                .filter(x -> x.tier() == tier && x.role() == a.role()
                        && com.hysx.salvosequence.fleet.FactionCatalog.factionOf(x)
                                .equals(com.hysx.salvosequence.fleet.FactionCatalog.factionOf(a)))
                .findFirst().orElse(null);
        if (target == null) {
            return "该国该型没有更高梯度的船";
        }
        int cost = 80 * tier;
        if (captain == null || !captain.spendAlloy(cost)) {
            return "合金不足（需要 " + cost + "）";
        }
        int lv = Math.min(fleet.unit(idA).level(), fleet.unit(idB).level());
        var units = new java.util.ArrayList<FleetUnit>();
        for (FleetUnit u : fleet.units()) {
            if (!u.id().equals(idA) && !u.id().equals(idB)) {
                units.add(u);
            }
        }
        // 成品优先放 A 位，放不下扫全图，再不行整队重铺
        int w = target.w();
        int h = target.h();
        int[] spot = freeSpot(units, w, h, fleet.unit(idA).x(), fleet.unit(idA).y());
        if (spot == null) {
            spot = freeSpot(units, w, h, fleet.unit(idB).x(), fleet.unit(idB).y());
        }
        if (spot == null) {
            fleet = com.hysx.salvosequence.fleet.Fleet.autoArrange(fleet.id(), fleet.nameZh(),
                    fleet.nameEn(), units);
            var extra = new java.util.ArrayList<>(units);
            extra.add(new FleetUnit(target, lv, 0, 0));
            fleet = com.hysx.salvosequence.fleet.Fleet.autoArrange(fleet.id(), fleet.nameZh(),
                    fleet.nameEn(), extra);
        } else {
            units.add(new FleetUnit(target, lv, spot[0], spot[1]));
            fleet = com.hysx.salvosequence.fleet.Fleet.of(fleet.id(), fleet.nameZh(),
                    fleet.nameEn(), units);
        }
        logMsg("「" + a.nameZh() + "」+「" + b.nameZh() + "」融合为「" + target.nameZh()
                + "」（Lv" + lv + "，-" + cost + " 合金）");
        // 融合把两艘旧船从编组里拿掉了：清掉它们残留的结构值，新船按满结构入场，
        // 同时把全队当前值重新钳到新上限（光环覆盖变了，上限也会变）。
        fleetHp.remove(idA);
        fleetHp.remove(idB);
        for (FleetUnit u : fleet.units()) {
            int max = maxHpOf(u);
            fleetHp.put(u.id(), Math.min(max, Math.max(0, fleetHp.getOrDefault(u.id(), max))));
        }
        return null;
    }

    /** 给新船找一个不重叠的摆位：先试偏好点，再扫全图。找不到返回 null。 */
    private int[] freeSpot(java.util.List<FleetUnit> units, int w, int h, int px, int py) {
        int size = 16;
        if (px >= 0 && py >= 0 && px + w <= size && py + h <= size) {
            boolean ok = true;
            for (FleetUnit u : units) {
                for (int cy = py; cy < py + h && ok; cy++) {
                    for (int cx = px; cx < px + w && ok; cx++) {
                        if (u.covers(cx, cy)) {
                            ok = false;
                        }
                    }
                }
            }
            if (ok) {
                return new int[]{px, py};
            }
        }
        for (int y = 0; y <= size - h; y++) {
            for (int x = 0; x <= size - w; x++) {
                boolean ok = true;
                for (FleetUnit u : units) {
                    for (int cy = y; cy < y + h && ok; cy++) {
                        for (int cx = x; cx < x + w && ok; cx++) {
                            if (u.covers(cx, cy)) {
                                ok = false;
                            }
                        }
                    }
                }
                if (ok) {
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    /** 待落位的采购船。 */
    public Ship pendingBuyShip() { return pendingBuyShip; }

    /** 开始采购：扣合金挂起，等玩家在棋盘上选落位。 */
    public boolean beginBuy(Ship ship) {
        if (stage != Stage.SPACE_PORT || fleet == null) { return false; }
        if (pendingBuyShip != null || fleet.unit(ship.id()) != null) { return false; }
        if (candidatePlacements(ship).isEmpty()) { return false; }
        if (captain == null || !captain.spendAlloy(shipCost(ship))) { return false; }
        pendingBuyShip = ship;
        logMsg("采购「" + ship.nameZh() + "」，在棋盘上选落位");
        return true;
    }

    /** 把采购船放到 (x,y)（左上角格）。 */
    public boolean placeBoughtShip(int x, int y) {
        if (stage != Stage.SPACE_PORT || pendingBuyShip == null) { return false; }
        FleetUnit candidate = new FleetUnit(pendingBuyShip, 1, x, y);
        if (fleet.placementsFor(pendingBuyShip).stream().noneMatch(
                spot -> spot[0] == x && spot[1] == y)) {
            return false;
        }
        fleet = fleet.withUnit(candidate);
        fleetHp.put(candidate.id(), maxHpOf(fleet.unit(candidate.id())));
        logMsg("「" + pendingBuyShip.nameZh() + "」编入舰队，落在 " + x + "/" + y);
        ShipArtStore.trySaveFleet(fleet);
        pendingBuyShip = null;
        return true;
    }

    /** 取消采购（退款）。 */
    public void cancelBuy() {
        if (pendingBuyShip == null) { return; }
        if (captain != null) {
            captain.addAlloy(shipCost(pendingBuyShip));
        }
        logMsg("取消采购「" + pendingBuyShip.nameZh() + "」，合金退回");
        pendingBuyShip = null;
    }

    /** 买船：花合金，自动铺位。 */
    public boolean buyShip(Ship ship) {
        if (stage != Stage.SPACE_PORT || fleet == null) { return false; }
        if (fleet.unit(ship.id()) != null) { return false; }
        int cost = shipCost(ship);
        if (captain == null || !captain.spendAlloy(cost)) { return false; }
        List<FleetUnit> spots = candidatePlacements(ship);
        if (spots.isEmpty()) {
            captain.addAlloy(cost); // 退钱
            return false;
        }
        FleetUnit spot = spots.get(0);
        fleet = fleet.withUnit(spot);
        fleetHp.put(spot.id(), maxHpOf(fleet.unit(spot.id())));
        logMsg("购入「" + spot.nameZh() + "」，花费合金 " + cost);
        return true;
    }

    public void departSpacePort() {
        require(stage == Stage.SPACE_PORT, "not in space port");
        if (pendingBuyShip != null) {
            cancelBuy(); // 没放好的采购退款
        }
        shopCache = null;
        stage = Stage.MAP;
    }

    /**
     * 放弃当前事件：不领选项奖励，直接回星图（节点已算走过）。
     *
     * <p>给 ESC 用——事件界面不是死路，玩家可以随时跳回星图。</p>
     */
    public boolean leaveEvent() {
        if (stage != Stage.EVENT) {
            return false;
        }
        eventCard = null;
        stage = Stage.MAP;
        logMsg("离开事件节点，返回星图");
        return true;
    }

    /** 事件节点：掷一个事件。 */
    private GameEvent rollEvent() {
        String[] en = {"漂流残骸", "落难商船", "神秘信标", "空间站废墟", "雇佣兵小队", "补给浮站"};
        String title = en[rng.nextInt(en.length)];
        return switch (title) {
            case "漂流残骸" -> new GameEvent(title,
                    "一片战场的残骸还在缓慢旋转，打捞钳够得着。",
                    java.util.List.of(
                            new EventOption("打捞（+25 合金）", 25, 0, 0),
                            new EventOption("离开", 0, 0, 0)));
            case "落难商船" -> new GameEvent(title,
                    "灰港级的商船瘫在航道边，船长广播说愿意付报酬。",
                    java.util.List.of(
                            new EventOption("护航抢修（全队修 20%）", 0, 20, 0),
                            new EventOption("趁乱打劫（+40 合金，全队 -8）", 40, 0, 8)));
            case "神秘信标" -> new GameEvent(title,
                    "一个不明信标在有节奏地闪烁，像某种邀请。",
                    java.util.List.of(
                            new EventOption("扫描破译（+15 合金）", 15, 0, 0),
                            new EventOption("直接拆走（+30 合金，全队 -6）", 30, 0, 6)));
            case "空间站废墟" -> new GameEvent(title,
                    "废弃空间站的气闸还开着，里面可能有货，也可能有防卫系统。",
                    java.util.List.of(
                            new EventOption("深入搜索（+20 合金，全队 -5）", 20, 0, 5),
                            new EventOption("只拿表面的（+8 合金）", 8, 0, 0)));
            case "雇佣兵小队" -> new GameEvent(title,
                    "一支失去雇主的机修小队想搭便船，工钱好商量。",
                    java.util.List.of(
                            new EventOption("雇下来检修（-30 合金，全队修 45%）", -30, 45, 0),
                            new EventOption("拒绝", 0, 0, 0)));
            default -> new GameEvent(title,
                    "一座无人补给浮站挂满了标准货柜，取走多少看你良心。",
                    java.util.List.of(
                            new EventOption("搬走补给（全队修 25%）", 0, 25, 0),
                            new EventOption("连货柜卖掉（+18 合金）", 18, 0, 0)));
        };
    }

    /** 当前事件卡（EVENT 阶段读）。 */
    public GameEvent eventCard() { return eventCard; }

    /** 选事件选项：结算效果，然后跳过这场战斗直接进下一站的太空港。 */
    public void chooseEventOption(int index) {
        if (stage != Stage.EVENT || eventCard == null) { return; }
        var opt = eventCard.options().get(
                Math.max(0, Math.min(index, eventCard.options().size() - 1)));
        if (captain != null && opt.alloyDelta() != 0) {
            if (opt.alloyDelta() > 0) {
                captain.addAlloy(opt.alloyDelta());
            } else {
                captain.spendAlloy(Math.min(-opt.alloyDelta(), captain.alloy()));
            }
            logMsg("事件：" + opt.label());
        }
        if (opt.healPct() > 0) {
            restFleet(opt.healPct());
        }
        if (opt.hpCost() > 0) {
            for (FleetUnit unit : fleet.units()) {
                int max = maxHpOf(unit);
                int hp = fleetHp.getOrDefault(unit.id(), max);
                fleetHp.put(unit.id(), Math.max(1, hp - opt.hpCost()));
            }
            logMsg("事件代价：全队 -" + opt.hpCost());
        }
        eventCard = null;
        stage = Stage.MAP;
    }

    /** 按百分比修全队。 */
    private void restFleet(int pct) {
        for (FleetUnit unit : fleet.units()) {
            int max = maxHpOf(unit);
            int hp = fleetHp.getOrDefault(unit.id(), max);
            fleetHp.put(unit.id(), Math.min(max, hp + max * pct / 100));
        }
    }

    /** 不打这场：回星图继续航行（黑流树海式——事件/休息不中断航行）。 */
    private void advanceWithoutBattle() {
        stage = Stage.MAP;
    }

    /** 进下一关（星图模式：旅行本身推进，这里只做整备后回星图）。 */
    private void nextNode() {
        stage = Stage.MAP;
    }

    /**
     * 关卡之间的整备：活着的修 {@link #REPAIR_PER_NODE}，沉掉的拖回来只剩四分之一。
     */
    private void maintain() {
        for (FleetUnit unit : fleet.units()) {
            int max = maxHpOf(unit);
            int hp = fleetHp.getOrDefault(unit.id(), max);
            if (hp <= 0) {
                hp = Math.max(1, max / 4);
                logMsg("「" + unit.nameZh() + "」从打捞里拖回来，结构值 " + hp);
            } else {
                int healed = Math.min(max, hp + REPAIR_PER_NODE);
                if (healed > hp) {
                    logMsg("「" + unit.nameZh() + "」整备 " + (healed - hp));
                }
                hp = healed;
            }
            fleetHp.put(unit.id(), hp);
        }
    }

    // ------------------------------------------------------------------ 读数

    /** 种子。 */
    public long seed() {
        return seed;
    }

    /** 阶段。 */
    public Stage stage() {
        return stage;
    }

    /** 第几关（0 基）。 */
    public int node() {
        return node;
    }

    /** 一共几关。 */
    public int routeLength() {
        return route.size();
    }

    /** 路线。 */
    public List<Enemy> route() {
        return route;
    }

    /** 当前舰队（没选之前是 null）。 */
    public Fleet fleet() {
        return fleet;
    }

    /** 每艘船剩下的结构值。 */
    public Map<String, Integer> fleetHp() {
        return Map.copyOf(fleetHp);
    }

    /** 当前战斗（没开打是 null）。 */
    public Battle battle() {
        return battle;
    }

    /** 舰队总结构值。 */
    public int hp() {
        if (stage == Stage.BATTLE && battle != null) {
            return battle.hp();
        }
        int sum = 0;
        for (int v : fleetHp.values()) {
            sum += Math.max(0, v);
        }
        return sum;
    }

    /** 舰队总结构值上限（含舰长被动）。 */
    public int maxHp() {
        if (fleet == null) {
            return 0;
        }
        int sum = 0;
        for (FleetUnit unit : fleet.units()) {
            sum += maxHpOf(unit);
        }
        return sum;
    }

    /** 战利品。 */
    public List<Reward> rewards() {
        return rewards;
    }

    /** 待处理的战利品（编入/强化时非空）。 */
    public Reward pending() {
        return pending;
    }

    /**
     * 待编入船的落点。
     *
     * <p>现算而不是转发 {@link #placements}：编入阶段允许先拖船变阵腾地方，棋盘一改，
     * 原来看好的落点就可能已经压住别人——拿旧单子落子会摆出重叠的舰队。</p>
     */
    public List<FleetUnit> pendingPlacements() {
        return stage == Stage.SLOT ? candidatePlacements() : placements;
    }

    /** 赢了机场。 */
    public int wonBattles() {
        return wonBattles;
    }

    /** 结束了没。 */
    public boolean finished() {
        return stage == Stage.WON || stage == Stage.LOST;
    }

    /** 最近几条日志。 */
    public List<String> recentLog(int n) {
        int from = Math.max(0, log.size() - Math.max(0, n));
        return List.copyOf(log.subList(from, log.size()));
    }

    /** 全部日志。 */
    public List<String> logAll() {
        return List.copyOf(log);
    }

    /** 某种船的外观图（界面上直接要显示时用）。 */
    public AppearanceDesign lookOf(Ship ship) {
        return ShipArtStore.loadLook(ship);
    }

    private void logMsg(String line) {
        log.add(line);
        if (log.size() > 96) {
            log.remove(0);
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalStateException(message);
        }
    }

    @Override
    public String toString() {
        return "Run[seed" + seed + " " + stage + " node" + (node + 1) + "/" + route.size() + "]";
    }
}
