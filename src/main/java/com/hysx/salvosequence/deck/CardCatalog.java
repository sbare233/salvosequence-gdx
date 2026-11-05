package com.hysx.salvosequence.deck;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hysx.salvosequence.deck.Effect.aim;
import static com.hysx.salvosequence.deck.Effect.block;
import static com.hysx.salvosequence.deck.Effect.charge;
import static com.hysx.salvosequence.deck.Effect.draw;
import static com.hysx.salvosequence.deck.Effect.hit;
import static com.hysx.salvosequence.deck.Effect.multi;
import static com.hysx.salvosequence.deck.Effect.pierce;
import static com.hysx.salvosequence.deck.Effect.wave;

/**
 * 卡牌总表。
 *
 * <h2>数值是怎么定的</h2>
 * <p>先把「1 点能量 ≈ 6 点伤害 / 5 点护盾」当基准，再按三件事加减：</p>
 * <ul>
 *   <li><b>装填</b>：装填 1/2/3 回合分别把这张牌的实际效率砍到约 3/4、1/2、1/3，
 *       所以装填越久，单次数值给得越狠（效力射 20 伤害 / 3 费 / 装填 3）。</li>
 *   <li><b>条件</b>：校射、蓄力这类"要先垫一张"的牌，单位能量的账面值可以超标
 *       （校射 1 费换 +8），因为兑现它本身要再花一张牌和一次抽到。</li>
 *   <li><b>定位</b>：光矛穿透、波动炮爆发、侧弦多段——三种"重武器"的手感差异
 *       全部落在数值结构上，不靠额外系统。</li>
 * </ul>
 *
 * <p>所有卡都是<b>纯数据</b>：效果用 {@link Effect} 的 record 描述，结算在
 * {@code combat.Battle}，机器人直接读结构做决策，不需要解析文案。</p>
 */
public final class CardCatalog {

    private CardCatalog() {
    }

    // ===== 核心（每艘船都有）=====

    public static final Card CORE_RAPID = new Card(
            "core.rapid", "速射炮击", "RAPID FIRE", CardKind.OPERATION, 1, 0,
            List.of(hit(5)), "造成 5 点伤害。");

    public static final Card CORE_PATCH = new Card(
            "core.patch", "应急装甲", "EMERGENCY PLATE", CardKind.OPERATION, 1, 0,
            List.of(block(5)), "为被锁定的前排舰获得 5 点护盾。");

    // ===== 主炮座 =====

    public static final Card MAIN_VOLLEY = new Card(
            "main.volley", "主炮齐射", "VOLLEY", CardKind.OPERATION, 2, 2,
            List.of(hit(18)), "造成 18 点伤害。装填 2 回合。");

    public static final Card MAIN_SPOT = new Card(
            "main.spot", "校射", "SPOT", CardKind.OPERATION, 1, 1,
            List.of(aim(8), draw(1)), "下一次炮击 +8 伤害，抽 1 张。装填 1 回合。");

    public static final Card MAIN_SALVO = new Card(
            "main.salvo", "轮射", "SALVO", CardKind.OPERATION, 2, 2,
            List.of(multi(9, 2)), "造成 9 点伤害 2 次。装填 2 回合。");

    public static final Card MAIN_EFFECTIVE = new Card(
            "main.effective", "效力射", "EFFECTIVE FIRE", CardKind.OPERATION, 3, 2,
            List.of(hit(34)), "造成 34 点伤害。装填 2 回合。");

    // ===== 机炮巢 =====

    public static final Card VULCAN_BURST = new Card(
            "vulcan.burst", "点射", "BURST", CardKind.OPERATION, 1, 0,
            List.of(multi(10, 3)), "造成 10 点伤害 3 次。");

    public static final Card VULCAN_SUPPRESS = new Card(
            "vulcan.suppress", "压制射击", "SUPPRESS", CardKind.OPERATION, 1, 0,
            List.of(hit(5)), "造成 5 点伤害。");

    public static final Card VULCAN_SPRAY = new Card(
            "vulcan.spray", "弹药倾泻", "SPRAY", CardKind.OPERATION, 0, 0,
            List.of(multi(4, 4)), "造成 4 点伤害 4 次。");

    // ===== 侧弦炮组 =====

    public static final Card BROAD_FIRE = new Card(
            "broad.fire", "侧弦齐射", "BROADSIDE", CardKind.OPERATION, 3, 1,
            List.of(multi(6, 2)), "造成 6 点伤害 2 次；齐射按全队总和、每段各计一次。装填 1 回合。");

    public static final Card BROAD_RAPID = new Card(
            "broad.rapid", "快速轮射", "RAPID SALVO", CardKind.OPERATION, 1, 1,
            List.of(multi(5, 3)), "造成 5 点伤害 3 次；每段额外吃 1/3 全队齐射。装填 1 回合。");

    public static final Card BROAD_BARRAGE = new Card(
            "broad.barrage", "弹幕覆盖", "BARRAGE", CardKind.OPERATION, 2, 2,
            List.of(multi(5, 5)), "造成 5 点伤害 5 次。装填 2 回合。");

    // ===== 光矛阵列 =====

    public static final Card LANCE_FOCUS = new Card(
            "lance.focus", "聚焦充能", "FOCUS", CardKind.OPERATION, 1, 0,
            List.of(block(4), aim(6)), "获得 4 点护盾，下一次炮击 +6 伤害。");

    public static final Card LANCE_BEAM = new Card(
            "lance.beam", "光矛射击", "LANCE BEAM", CardKind.OPERATION, 2, 2,
            List.of(pierce(DamageType.THERMAL, 16)),
            "造成 16 点热能伤害（无视防御力，护盾照挡）。装填 2 回合。");

    public static final Card LANCE_BURN = new Card(
            "lance.burn", "灼烧定位", "SCORCH", CardKind.OPERATION, 1, 0,
            List.of(new Effect.Expose(2)), "暴露敌舰 2 回合：它受到的伤害 +50%。");

    // ===== 波动炮 =====

    public static final Card WAVE_CHARGE = new Card(
            "wave.charge", "充能", "CHARGE", CardKind.OPERATION, 1, 0,
            List.of(block(5), charge(1)), "获得 5 点护盾，蓄力 +1。");

    public static final Card WAVE_OVERLOAD = new Card(
            "wave.overload", "过载充能", "OVERLOAD", CardKind.OPERATION, 0, 1,
            List.of(charge(2)), "蓄力 +2。装填 1 回合。");

    public static final Card WAVE_FIRE = new Card(
            "wave.fire", "波动炮射击", "WAVE CANNON", CardKind.OPERATION, 2, 3,
            List.of(wave(10, 10)),
            "造成 10 点伤害，每层蓄力 +10，然后清空蓄力。装填 3 回合。");

    // ===== 装甲 =====

    public static final Card ARMOR_SHIELD = new Card(
            "armor.shield", "力场护盾", "FIELD SHIELD", CardKind.OPERATION, 1, 0,
            List.of(block(8)), "获得 8 点护盾。");

    public static final Card ARMOR_BRACE = new Card(
            "armor.brace", "装甲转向", "BRACE", CardKind.OPERATION, 1, 0,
            List.of(block(5), draw(1)), "获得 5 点护盾，抽 1 张。");

    public static final Card ARMOR_REACTIVE = new Card(
            "armor.reactive", "反应装甲", "REACTIVE ARMOR", CardKind.OPERATION, 1, 0,
            List.of(block(6), hit(4)), "为被锁定的前排舰获得 6 点护盾，造成 4 点伤害。");

    // ===== 动力 =====

    public static final Card DRIVE_EVADE = new Card(
            "drive.evade", "机动规避", "EVADE", CardKind.OPERATION, 0, 0,
            List.of(block(4), draw(1)), "为被锁定的前排舰获得 4 点护盾，抽 1 张。");

    public static final Card REACTOR_SURGE = new Card(
            "reactor.surge", "反应堆过载", "REACTOR SURGE", CardKind.OPERATION, 0, 1,
            List.of(new Effect.Energy(2)), "立刻获得 2 点能量。装填 1 回合。");

    // ===== 战术 =====

    public static final Card EW_JAM = new Card(
            "ew.jam", "电子干扰", "JAM", CardKind.OPERATION, 1, 0,
            List.of(new Effect.Expose(2)), "暴露敌舰 2 回合：它受到的伤害 +50%。");

    public static final Card EW_DATALINK = new Card(
            "ew.datalink", "数据链", "DATALINK", CardKind.OPERATION, 1, 0,
            List.of(draw(2)), "抽 2 张。");

    /**
     * 功率干扰：敌方专用的能量中和（EVE）。
     *
     * <p>不挂任何我方船——敌方没有能量，我方放这张无事发生，
     * 所以它只会出现在敌方计划表里（敌我同源：同一张卡、同一条结算管线）。</p>
     */
    public static final Card EW_DRAIN = new Card(
            "ew.drain", "功率干扰", "POWER DRAIN", CardKind.OPERATION, 1, 0,
            List.of(new Effect.Drain(1)), "干扰目标舰队：下回合能量 -1。");

    // ===== 重型主炮（敌人带出来的）=====

    public static final Card HEAVY_VOLLEY = new Card(
            "heavy.volley", "重炮齐射", "HEAVY VOLLEY", CardKind.OPERATION, 3, 3,
            List.of(hit(22)), "造成 22 点伤害。装填 3 回合。");

    public static final Card HEAVY_LOCK = new Card(
            "heavy.lock", "火控锁定", "FIRE CONTROL", CardKind.OPERATION, 1, 0,
            List.of(aim(10), draw(1)), "下一次炮击 +10 伤害，抽 1 张。");

    // ===== 导弹巢 =====

    public static final Card MISS_SALVO = new Card(
            "miss.salvo", "导弹齐射", "MISSILE SALVO", CardKind.OPERATION, 2, 2,
            List.of(multi(DamageType.EXPLOSIVE, 7, 2)),
            "造成 7 点爆破伤害 2 次（会被防空削段）。装填 2 回合。");

    public static final Card MISS_SWARM = new Card(
            "miss.swarm", "蜂群导弹", "SWARM", CardKind.OPERATION, 1, 1,
            List.of(multi(DamageType.EXPLOSIVE, 12, 4)),
            "造成 12 点爆破伤害 4 次（会被防空削段）。装填 1 回合。");

    // ===== 掉落卡（增益 / 特殊，不挂在组件上）=====

    public static final Card BUFF_ACE = new Card(
            "buff.ace", "王牌炮术", "ACE GUNNERY", CardKind.SPECIAL, 2, 2,
            List.of(multi(5, 4)), "造成 5 点伤害 4 次。装填 2 回合。");

    public static final Card BUFF_AMMO = new Card(
            "buff.ammo", "弹药强化", "AMMO BOOST", CardKind.BUFF, 1, 0,
            List.of(aim(12)), "下一次炮击 +12 伤害。");

    public static final Card BUFF_STAFF = new Card(
            "buff.staff", "参谋建议", "STAFF BRIEF", CardKind.BUFF, 1, 0,
            List.of(new Effect.Search(1)), "从牌库检索一张攻击牌；没有就抽一张。");

    public static final Card BUFF_WARP = new Card(
            "buff.warp", "紧急跃迁", "EMERGENCY WARP", CardKind.SPECIAL, 1, 2,
            List.of(block(20)), "获得 20 点护盾。装填 2 回合。");

    public static final Card BUFF_REPAIR = new Card(
            "buff.repair", "紧急损管", "DAMAGE CONTROL", CardKind.SPECIAL, 1, 0,
            List.of(new Effect.Repair(8)), "修复 8 点结构值。");

    public static final Card BUFF_CALL = new Card(
            "buff.call", "舰长号令", "CAPTAIN'S CALL", CardKind.BUFF, 0, 0,
            List.of(draw(1), new Effect.Energy(1)), "抽 1 张，能量 +1。");

    public static final Card BUFF_VOLLEY = new Card(
            "buff.volley", "齐射指挥", "VOLLEY ORDER", CardKind.BUFF, 1, 0,
            List.of(aim(8), draw(1)), "下一次炮击 +8 伤害，抽 1 张。");

    // ===== 轴炮（炮管 / 炮口）=====
    //
    // 轴炮舰的核心是"管身与炮口叠起来才成炮"：卡面伤害不变，倍率由
    // ShipLoadout.axialMultiplier() 给出（管数 + 口数 + 相邻对），所以
    // 玩家每多插一根管、多接一个口，这张牌就实实在在变强一档。

    public static final Card AXIAL_FIRE = new Card(
            "axial.fire", "轴炮轰击", "AXIAL BARRAGE", CardKind.OPERATION, 2, 2,
            List.of(hit(7)), "造成 7 点伤害；每根炮管、每个炮口都叠进倍率。装填 2 回合。");

    public static final Card AXIAL_RAPID = new Card(
            "axial.rapid", "轴炮急射", "AXIAL RAPID", CardKind.OPERATION, 1, 0,
            List.of(hit(6)), "造成 6 点伤害；同样吃管口倍率，用来填装填空窗。");

    public static final Card AXIAL_LOCK = new Card(
            "axial.lock", "轴炮校准", "AXIAL LOCK", CardKind.OPERATION, 1, 0,
            List.of(aim(8), draw(1)), "下一次炮击 +8 伤害，抽 1 张。");

    // ------------------------------------------------------------------ 索引

    private static final List<Card> ALL = List.of(
            CORE_RAPID, CORE_PATCH, BUFF_STAFF,
            MAIN_VOLLEY, MAIN_SPOT, MAIN_SALVO, MAIN_EFFECTIVE,
            VULCAN_BURST, VULCAN_SUPPRESS, VULCAN_SPRAY,
            BROAD_FIRE, BROAD_RAPID, BROAD_BARRAGE,
            LANCE_FOCUS, LANCE_BEAM, LANCE_BURN,
            WAVE_CHARGE, WAVE_OVERLOAD, WAVE_FIRE,
            ARMOR_SHIELD, ARMOR_BRACE, ARMOR_REACTIVE,
            DRIVE_EVADE, REACTOR_SURGE,
            EW_JAM, EW_DATALINK, EW_DRAIN,
            HEAVY_VOLLEY, HEAVY_LOCK,
            MISS_SALVO, MISS_SWARM,
            AXIAL_FIRE, AXIAL_RAPID, AXIAL_LOCK,
            BUFF_ACE, BUFF_AMMO, BUFF_WARP, BUFF_REPAIR, BUFF_CALL, BUFF_VOLLEY);

    /** 战利品里"独立卡"的池子：只有增益与特殊卡，操作卡只能从组件来。 */
    private static final List<Card> DROPS = List.of(
            BUFF_ACE, BUFF_AMMO, BUFF_STAFF, BUFF_WARP, BUFF_REPAIR, BUFF_CALL, BUFF_VOLLEY);

    private static final Map<String, Card> BY_ID = buildIndex();

    private static Map<String, Card> buildIndex() {
        Map<String, Card> map = new LinkedHashMap<>();
        for (Card c : ALL) {
            if (map.put(c.id(), c) != null) {
                throw new IllegalStateException("卡牌 id 重复: " + c.id());
            }
        }
        return Map.copyOf(map);
    }

    public static List<Card> all() {
        return ALL;
    }

    /** 战利品可掉的独立卡。 */
    public static List<Card> dropPool() {
        return DROPS;
    }

    public static Card byId(String id) {
        Card card = BY_ID.get(id);
        if (card == null) {
            throw new IllegalArgumentException("没有这张卡: " + id);
        }
        return card;
    }

    public static boolean has(String id) {
        return BY_ID.containsKey(id);
    }

    /** 一次拿多张（组件就是这么给卡的，允许重复）。 */
    public static List<Card> several(String... ids) {
        List<Card> out = new ArrayList<>(ids.length);
        for (String id : ids) {
            out.add(byId(id));
        }
        return List.copyOf(out);
    }
}
