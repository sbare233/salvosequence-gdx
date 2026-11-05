package com.hysx.salvosequence.fleet;

import com.hysx.salvosequence.deck.CardCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static com.hysx.salvosequence.fleet.Perk.Kind.DRAW;
import static com.hysx.salvosequence.fleet.Perk.Kind.FLAK;
import static com.hysx.salvosequence.fleet.Perk.Kind.HP;
import static com.hysx.salvosequence.fleet.Perk.Kind.MAGAZINE;
import static com.hysx.salvosequence.fleet.Perk.Kind.PLATE;
import static com.hysx.salvosequence.fleet.Perk.Kind.REPAIR;
import static com.hysx.salvosequence.fleet.Perk.Kind.SHIELD;
import static com.hysx.salvosequence.fleet.Perk.Kind.SHIELD_LINK;
import static com.hysx.salvosequence.fleet.Perk.Kind.SPOT;
import static com.hysx.salvosequence.fleet.Perk.Kind.VOLLEY;
import static com.hysx.salvosequence.fleet.Perk.Range.CROSS;
import static com.hysx.salvosequence.fleet.Perk.Range.FLEET;
import static com.hysx.salvosequence.fleet.Perk.Range.RING;
import static com.hysx.salvosequence.fleet.Perk.Range.ROW;
import static com.hysx.salvosequence.fleet.Perk.Range.SELF;

/**
 * 船的全表。
 *
 * <p>舰名带势力限定词：铁冕帝国按年份（774年型）、灰港共和国按地名（灰港级）、
 * 圣痕王朝按人名（奥蕾莉亚级）、灰市商联按参数串（VX-2c MK-3 MOD.7）。
 * 背景设定见 {@code docs/LORE.md}。</p>
 *
 * <p>每艘船 = 基础数值 + 带的卡 + <b>光环</b>（{@link Perk}，按生效范围叠加到别的船上）。
 * 旧的"相邻加成"三条硬规则（装甲贴指挥 +2 结构、炮贴装甲 +1 齐射、炮贴动力 +1 护盾）
 * 现在就是装甲/动力船身上的光环——摆位即构筑，敌我同一套公式。</p>
 *
 * <p><b>不要把 {@link #starters()} 缓存成 static final</b>：玩家在「舰队编排」里改完阵型要立刻生效，
 * 旧版 {@code ComponentCatalog} 就踩过这个坑。</p>
 */
public final class ShipCatalog {

    // ---------------------------------------------------------------- 指挥

    /** 指挥舰：历史身份，现已按普通船处理（可掉、可买、可拆）。 */
    public static final Ship CORE_COMMAND = new Ship(
            "core.command", "CM-01 指挥舰", "CM-01 COMMAND", ShipRole.COMMAND, 3, 3,
            24, 0, 2, 1, 2, 0, 0,
            List.of("core.rapid", "core.patch", "buff.staff"),
            List.of(new Perk(SHIELD_LINK, 1, CROSS)), 0,
            "编队的旗舰：它沉了整个舰队就散了。护盾令向贴身的友舰扩散。");

    // ---------------------------------------------------------------- 炮舰

    public static final Ship GUN_MAIN = new Ship(
            "gun.main", "MG-01 主炮舰", "MG-01 MAIN GUN", ShipRole.MAIN_GUN, 4, 3,
            16, 0, 1, 0, 7, 0, 0,
            List.of("main.volley", "main.spot", "main.salvo", "main.effective"),
            List.of(new Perk(SPOT, 1, ROW)), 1,
            "中口径主炮舰：同排的炮线共享一条火控。");

    public static final Ship GUN_VULCAN = new Ship(
            "gun.vulcan", "SG-01 机炮舰", "SG-01 VULCAN", ShipRole.SECONDARY_GUN, 2, 2,
            12, 0, 0, 2, 3, 0, 0,
            List.of("vulcan.burst", "vulcan.suppress", "vulcan.spray"),
            List.of(new Perk(FLAK, 1, CROSS)), 1,
            "小口径速射舰：兼职贴身防空网。");

    public static final Ship GUN_BROAD = new Ship(
            "gun.broadside", "SG-02 侧弦炮舰", "SG-02 BROADSIDE", ShipRole.SECONDARY_GUN, 4, 2,
            14, 0, 0, 1, 8, 0, 0,
            List.of("broad.fire", "broad.rapid", "broad.barrage"),
            List.of(new Perk(VOLLEY, 1, ROW)), 1,
            "侧舷齐射舰：同排的船都跟着它多打一轮。");

    public static final Ship GUN_LANCE = new Ship(
            "gun.lance", "SG-03 光矛舰", "SG-03 LANCE", ShipRole.SECONDARY_GUN, 3, 3,
            14, 0, 0, 0, 5, 0, 0,
            List.of("lance.focus", "lance.beam", "lance.burn"),
            List.of(new Perk(MAGAZINE, 1, SELF)), 1,
            "聚能光矛舰：热能长矛，自带快装弹夹。");

    public static final Ship GUN_WAVE = new Ship(
            "gun.wave", "SG-04 波动炮舰", "SG-04 WAVE CANNON", ShipRole.SECONDARY_GUN, 4, 4,
            14, 0, 0, 0, 6, 0, 0,
            List.of("wave.charge", "wave.overload", "wave.fire"),
            List.of(new Perk(SHIELD_LINK, 1, RING)), 1,
            "波动炮舰：热能重型，波导把护盾荡给周围。");

    public static final Ship GUN_HEAVY = new Ship(
            "gun.heavy", "MG-02 重炮舰", "MG-02 HEAVY GUN", ShipRole.MAIN_GUN, 5, 5,
            20, 0, 2, 0, 9, 0, 0,
            List.of("heavy.volley", "heavy.lock"),
            List.of(new Perk(PLATE, 2, SELF)), 3,
            "重型主炮舰：一条命换一炮，炮座自重就是装甲。");

    public static final Ship GUN_MISSILE = new Ship(
            "gun.missile", "SG-05 导弹舰", "SG-05 MISSILE", ShipRole.SECONDARY_GUN, 3, 2,
            18, 0, 0, 1, 5, 0, 0,
            List.of("miss.salvo", "miss.swarm"),
            List.of(new Perk(FLAK, 1, ROW)), 2,
            "导弹舰：低伤多段的爆破齐射，垂发兼职一排防空。");

    /** 轴炮管舰：不给卡，只给轴炮倍率。 */
    public static final Ship GUN_AXIAL_BARREL = new Ship(
            "gun.axial.barrel", "AX-01 轴炮管舰", "AX-01 BARREL", ShipRole.MAIN_GUN, 2, 2,
            10, 0, 1, 0, 2, 0, 0, List.of(),
            List.of(new Perk(HP, 1, CROSS)), 1,
            "轴炮的管：本身不开火，接上炮口才给倍率，龙骨撑着邻船。");

    public static final Ship GUN_AXIAL_BARREL_LONG = new Ship(
            "gun.axial.barrel.long", "AX-02 长轴炮管舰", "AX-02 LONG BARREL", ShipRole.MAIN_GUN, 3, 2,
            14, 0, 1, 0, 3, 0, 0, List.of(),
            List.of(new Perk(HP, 2, CROSS)), 2,
            "更长的轴炮管：龙骨更长，邻船更扛揍。");

    /** 轴炮口舰：轴炮唯一的给卡处。 */
    public static final Ship GUN_AXIAL_MUZZLE = new Ship(
            "gun.axial.muzzle", "AX-03 轴炮口舰", "AX-03 MUZZLE", ShipRole.MAIN_GUN, 1, 2,
            8, 0, 0, 0, 1, 0, 0,
            List.of("axial.fire", "axial.rapid"), List.of(), 1,
            "轴炮的炮口：贴着炮管就合流，给全队轴炮牌。");

    public static final Ship GUN_AXIAL_MUZZLE_WIDE = new Ship(
            "gun.axial.muzzle.wide", "AX-04 扩口炮口舰", "AX-04 WIDE MUZZLE", ShipRole.MAIN_GUN, 2, 2,
            12, 0, 1, 0, 2, 0, 0,
            List.of("axial.fire", "axial.rapid", "axial.lock"),
            List.of(new Perk(SPOT, 1, CROSS)), 2,
            "扩口炮口舰：多一张锁敌，炮口校正惠及贴身友舰。");

    // ---------------------------------------------------------------- 装甲

    public static final Ship ARMOR_BELT = new Ship(
            "armor.belt", "AR-01 装甲舰", "AR-01 ARMOR BELT", ShipRole.ARMOR, 4, 2,
            44, 2, 6, 0, 0, 0, 0,
            List.of("armor.shield", "armor.brace"),
            List.of(new Perk(HP, 2, CROSS), new Perk(VOLLEY, 1, CROSS), new Perk(SPOT, 1, CROSS)), 1,
            "主装甲舰：邻船跟着它多血、多齐射、多火控——舰队骨架。");

    public static final Ship ARMOR_REACTIVE = new Ship(
            "armor.reactive", "AR-02 反应装甲舰", "AR-02 REACTIVE", ShipRole.ARMOR, 3, 4,
            32, 1, 5, 1, 0, 0, 0,
            List.of("armor.reactive", "armor.shield"),
            List.of(new Perk(HP, 2, CROSS), new Perk(VOLLEY, 1, CROSS),
                    new Perk(SPOT, 1, CROSS), new Perk(PLATE, 1, CROSS)), 1,
            "反应装甲舰：装甲舰的兄弟，额外把防御力分给贴身友舰。");

    public static final Ship ARMOR_DEFLECT = new Ship(
            "armor.deflect", "AR-03 偏导装甲舰", "AR-03 DEFLECTOR", ShipRole.ARMOR, 5, 5,
            52, 3, 8, 0, 0, 0, 0,
            List.of("armor.shield"),
            List.of(new Perk(HP, 2, CROSS), new Perk(VOLLEY, 1, CROSS),
                    new Perk(SPOT, 1, CROSS), new Perk(SHIELD, 1, RING)), 3,
            "偏导装甲舰：五乘五的护盾塔，周围一圈都蹭它的偏导场。");

    // ---------------------------------------------------------------- 动力

    public static final Ship ENGINE_THRUST = new Ship(
            "engine.thrust", "EN-01 推进舰", "EN-01 THRUSTER", ShipRole.DRIVE, 2, 2,
            12, 0, 0, 1, 1, 0, 0,
            List.of("drive.evade"),
            List.of(new Perk(SHIELD, 1, CROSS)), 1,
            "侧推进舰：引擎尾流替贴身的船卸掉一点火力。");

    public static final Ship ENGINE_REACTOR = new Ship(
            "engine.reactor", "EN-02 反应堆舰", "EN-02 REACTOR", ShipRole.DRIVE, 2, 2,
            12, 0, 1, 0, 0, 1, 0,
            List.of("reactor.surge"),
            List.of(new Perk(SHIELD, 1, CROSS)), 2,
            "反应堆舰：多一格能量，尾流同样护着贴身友舰。");

    public static final Ship ENGINE_OVERDRIVE = new Ship(
            "engine.overdrive", "EN-03 超载引擎舰", "EN-03 OVERDRIVE", ShipRole.DRIVE, 3, 3,
            10, 0, 1, 0, 0, 2, 0,
            List.of("drive.evade"),
            List.of(new Perk(SHIELD, 1, CROSS)), 3,
            "超载引擎舰：两格能量，脆弱但值。");

    // ---------------------------------------------------------------- 战术

    public static final Ship TACTIC_EW = new Ship(
            "tactic.ew", "TC-01 电子战舰", "TC-01 EW SHIP", ShipRole.TACTICS, 2, 2,
            16, 0, 1, 1, 1, 0, 0,
            List.of("ew.jam", "ew.datalink"),
            List.of(new Perk(SPOT, 1, FLEET)), 2,
            "电子战舰：数据链标记全队目标，人人都打得准一点。");

    public static final Ship TACTIC_FIRE = new Ship(
            "tactic.fire", "TC-02 火控指挥舰", "TC-02 FIRE CONTROL", ShipRole.TACTICS, 3, 3,
            16, 0, 1, 0, 1, 0, 1,
            List.of("heavy.lock", "ew.datalink"),
            List.of(new Perk(SPOT, 1, ROW)), 3,
            "火控指挥舰：多抽一张，同排炮线全部受它指挥。");

    // ---------------------------------------------------------------- 支援（船池扩充：每艘一个独特功能 + 生效范围）

    /** 维修舰：损管光环——邻船每回合自己长血。 */
    public static final Ship SUPPORT_REPAIR = new Ship(
            "support.repair", "SP-01 维修舰", "SP-01 REPAIR TENDER", ShipRole.DRIVE, 3, 2,
            14, 0, 0, 0, 0, 0, 0,
            List.of("core.patch", "buff.repair"),
            List.of(new Perk(REPAIR, 2, CROSS)), 2,
            "维修舰：焊枪不熄火，贴着它的船每回合自己修 2 点结构值。");

    /** 盾桥舰：护盾连接的放大器——周围的船在护盾卡结算时都多一层。 */
    public static final Ship SUPPORT_LINK = new Ship(
            "support.link", "SP-02 盾桥舰", "SP-02 SHIELD RELAY", ShipRole.TACTICS, 2, 3,
            12, 2, 0, 0, 0, 0, 0,
            List.of("armor.shield"),
            List.of(new Perk(SHIELD_LINK, 2, RING)), 2,
            "盾桥舰：任何护盾卡打出来，周围一圈的友舰都多拿 2 点。");

    /** 弹库舰：弹夹光环——同排的卡装填更快。 */
    public static final Ship SUPPORT_MAGAZINE = new Ship(
            "support.magazine", "SP-03 弹库舰", "SP-03 MAGAZINE", ShipRole.DRIVE, 2, 2,
            10, 0, 1, 0, 0, 0, 0,
            List.of("vulcan.spray"),
            List.of(new Perk(MAGAZINE, 1, ROW)), 2,
            "弹库舰：同排的卡进装填区时倒计时少 1 回合。");

    /** 预警舰：过牌光环——全队每回合多抽一张。 */
    public static final Ship SUPPORT_RADAR = new Ship(
            "support.radar", "SP-04 预警舰", "SP-04 PICKET", ShipRole.TACTICS, 2, 2,
            10, 0, 0, 1, 0, 0, 0,
            List.of("ew.datalink"),
            List.of(new Perk(DRAW, 1, FLEET)), 2,
            "预警舰：全队每回合多抽 1 张牌。");

    /** 补给舰：结构光环——同排的船都更扛揍。 */
    public static final Ship SUPPORT_LOGISTICS = new Ship(
            "support.logistics", "SP-05 补给舰", "SP-05 SUPPLY", ShipRole.ARMOR, 3, 3,
            16, 0, 1, 0, 0, 0, 0,
            List.of(),
            List.of(new Perk(HP, 3, ROW)), 2,
            "补给舰：一条补给线喂饱整排，同排友舰结构值 +3。");

    /** 防空护卫舰：哨戒火力，同排防空网。 */
    public static final Ship GUARD_FLAK = new Ship(
            "guard.flak", "SG-06 防空护卫舰", "SG-06 FLAK PICKET", ShipRole.SECONDARY_GUN, 2, 2,
            12, 0, 0, 3, 2, 0, 0,
            List.of("vulcan.burst"),
            List.of(new Perk(FLAK, 2, ROW)), 1,
            "防空护卫舰：自己防空 3，同排再各 +2——导弹雨的克星。");

    /** 炮术观察舰：火控光环——周围的炮都打得更疼。 */
    public static final Ship SUPPORT_OBSERVER = new Ship(
            "support.observer", "SP-06 炮术观察舰", "SP-06 SPOTTER", ShipRole.TACTICS, 2, 2,
            10, 0, 0, 0, 0, 0, 0,
            List.of("main.spot"),
            List.of(new Perk(SPOT, 2, RING)), 2,
            "炮术观察舰：周围一圈的炮全吃它的观测数据，攻击卡 +2。");

    // ---------------------------------------------------------------- 四大势力（舰名限定词各有家规）

    /** 铁冕帝国：按下水年份命名，说明书通常只有一句。 */
    public static final Ship EMPIRE_T774 = new Ship(
            "empire.t774", "774年型 突击舰", "T774 STRIKE SHIP", ShipRole.MAIN_GUN, 2, 3,
            14, 0, 1, 0, 4, 0, 0,
            List.of("main.spot", "vulcan.suppress"),
            List.of(new Perk(VOLLEY, 1, RING)), 2,
            "铁冕帝国按下水年份命名军舰。774 年型的操作手册只有一句：往前开，开炮。");

    /** 铁冕帝国：拿防线当商品卖的年型要塞。 */
    public static final Ship EMPIRE_T771 = new Ship(
            "empire.t771", "771年型 要塞舰", "T771 BULWARK", ShipRole.ARMOR, 3, 3,
            40, 1, 5, 1, 0, 0, 0,
            List.of("armor.shield", "armor.brace"),
            List.of(new Perk(PLATE, 2, ROW)), 2,
            "771 年型当年太重开不动，帝国索性把它焊死在边境当防线卖——现在退役流入黑市，还是焊死的。");

    /** 灰港共和国：商船队全叫同一个地名，靠编号区分，然后编号丢了。 */
    public static final Ship REP_GREYHARBOR = new Ship(
            "rep.greyharbor", "灰港级 商护舰", "GREYHARBOR MERCHANT", ShipRole.DRIVE, 3, 2,
            16, 0, 0, 1, 0, 1, 0,
            List.of("reactor.surge", "drive.evade"),
            List.of(new Perk(REPAIR, 1, ROW)), 2,
            "共和国用地名命名商船。灰港级造了四十艘全叫灰港，本想靠编号区分，后来编号丢了，索性都不区分了。");

    /** 灰港共和国：先把炮放上去，船是后来焊的。 */
    public static final Ship REP_WHITEBAY = new Ship(
            "rep.whitebay", "白湾级 炮垒舰", "WHITEBAY BATTERY", ShipRole.MAIN_GUN, 4, 2,
            18, 0, 1, 1, 6, 0, 0,
            List.of("broad.barrage", "heavy.lock"),
            List.of(new Perk(SPOT, 2, RING)), 2,
            "白湾是共和国第一座造船港。炮垒舰的建造顺序是先装炮再焊船壳——审计问起，就说船是炮的附件。");

    /** 圣痕王朝：建国圣女的名字排到第十九世。 */
    public static final Ship DYN_AURELIA = new Ship(
            "dyn.aurelia", "奥蕾莉亚级 圣锋舰", "AURELIA BLADE", ShipRole.SECONDARY_GUN, 2, 2,
            12, 0, 1, 0, 5, 0, 0,
            List.of("heavy.lock", "main.effective"),
            List.of(new Perk(SHIELD_LINK, 1, CROSS)), 3,
            "王朝用人名命名战舰，全是建国圣女。奥蕾莉亚级已传到第十九世——前十八艘都沉在自己人手里。");

    /** 圣痕王朝：唯一没当过舰名的圣女。 */
    public static final Ship DYN_VERONICA = new Ship(
            "dyn.veronica", "维罗妮卡级 救援舰", "VERONICA RESCUE", ShipRole.DRIVE, 2, 2,
            12, 0, 0, 1, 0, 0, 0,
            List.of("core.patch", "buff.repair"),
            List.of(new Perk(REPAIR, 2, RING)), 2,
            "圣女维罗妮卡是王朝史上唯一没被拿来命名战舰的人——因为她负责把那些用圣女名字的船捞回来。");

    /** 灰市商联：参数越多越贵。 */
    public static final Ship COMB_VX2C = new Ship(
            "comb.vx2c", "VX-2c 型 干扰艇", "VX-2c JAMMER", ShipRole.TACTICS, 2, 2,
            10, 0, 0, 1, 1, 0, 0,
            List.of("ew.jam", "ew.datalink"),
            List.of(new Perk(MAGAZINE, 1, FLEET)), 3,
            "商联的规矩是参数越长越贵。VX-2c 的全称有四行，卖家自己只记得 VX，买家只记得被多收了钱。");

    /** 灰市商联：四个阵营的残骸拼的，商联管这叫多国合作。 */
    public static final Ship COMB_MK3 = new Ship(
            "comb.mk3", "MK-3 MOD.7 拾荒舰", "MK-3 SALVAGE", ShipRole.DRIVE, 3, 3,
            14, 0, 1, 1, 1, 0, 0,
            List.of("drive.evade", "vulcan.spray"),
            List.of(new Perk(FLAK, 1, CROSS), new Perk(REPAIR, 1, CROSS)), 2,
            "MK-3 MOD.7 的船体拼自四个阵营的残骸，焊接处还留着三种语言的使用警告。商联管这叫多国合作。");

    // ---------------------------------------------------------------- 蓝星（旧地球远征队）

    /**
     * 蓝星船：旧地球远征队带来的那一批，编号和图纸都不在任何现行阵营的序列里。
     * 只给「归航者」夏初的起始舰队用，不进掉落/敌方船池。
     */
    public static final Ship BLUE_HOME = new Ship(
            "blue.home", "故土级 指挥舰", "HOMELAND COMMAND", ShipRole.COMMAND, 3, 3,
            24, 0, 2, 1, 2, 0, 0,
            List.of("core.rapid", "core.patch", "buff.staff"),
            List.of(new Perk(SHIELD_LINK, 1, CROSS)), 0,
            "蓝星远征队的指挥舰。龙骨上刻着启航那天的日期——没人记得那是几百年前。");

    public static final Ship BLUE_LONGNIGHT = new Ship(
            "blue.longnight", "长夜级 装甲舰", "LONG NIGHT ARMOR", ShipRole.ARMOR, 4, 2,
            44, 2, 6, 0, 0, 0, 0,
            List.of("armor.shield", "armor.brace"),
            List.of(new Perk(HP, 2, CROSS), new Perk(VOLLEY, 1, CROSS), new Perk(SPOT, 1, CROSS)), 0,
            "蓝星人的战列线：船体旧得掉漆，却把整支舰队的命都扛在自己身上。");

    public static final Ship BLUE_FARSIGHT = new Ship(
            "blue.farsight", "远望级 主炮舰", "FARSIGHT MAIN GUN", ShipRole.MAIN_GUN, 4, 3,
            16, 0, 1, 0, 7, 0, 0,
            List.of("main.volley", "main.spot", "main.salvo", "main.effective"),
            List.of(new Perk(SPOT, 1, ROW)), 0,
            "用旧地球望远镜命名的炮舰：它能看见的，炮就能打到。");

    public static final Ship BLUE_HOMEWARD = new Ship(
            "blue.homeward", "归途级 副炮舰", "HOMEWARD SECONDARY", ShipRole.SECONDARY_GUN, 2, 2,
            12, 0, 0, 2, 3, 0, 0,
            List.of("vulcan.burst", "vulcan.suppress", "vulcan.spray"),
            List.of(new Perk(FLAK, 1, CROSS)), 0,
            "近防炮舰。蓝星水兵管它叫「回家的最后一公里」。");

    public static final Ship BLUE_LIGHTHOUSE = new Ship(
            "blue.lighthouse", "灯塔级 动力舰", "LIGHTHOUSE DRIVE", ShipRole.DRIVE, 2, 2,
            12, 0, 1, 0, 0, 1, 0,
            List.of("reactor.surge"),
            List.of(new Perk(SHIELD, 1, CROSS)), 0,
            "反应堆还是启航时那台，修过一千次。灯亮着，方向就没错。");

    public static final Ship BLUE_CAMPFIRE = new Ship(
            "blue.campfire", "篝火级 战术舰", "CAMPFIRE TACTICS", ShipRole.TACTICS, 2, 2,
            16, 0, 1, 1, 1, 0, 0,
            List.of("ew.jam", "ew.datalink"),
            List.of(new Perk(SPOT, 1, FLEET)), 0,
            "数据链里循环着一首蓝星的旧歌。听不见，但每个水兵都知道调子。");

    /** 蓝星起始编队（只此一套，不进通用船池）。 */
    private static final List<Ship> BLUE_STAR = List.of(
            BLUE_HOME, BLUE_LONGNIGHT, BLUE_FARSIGHT, BLUE_HOMEWARD,
            BLUE_LIGHTHOUSE, BLUE_CAMPFIRE);

    /** 无所属制式舰（黑市/海盗/民兵杂牌）：岚不抽，它们不是任何阵营的制式舰。 */
    private static final List<Ship> UNAFFILIATED = List.of(
            CORE_COMMAND,
            GUN_MAIN, GUN_VULCAN, GUN_BROAD, GUN_LANCE, GUN_WAVE, GUN_HEAVY, GUN_MISSILE,
            GUN_AXIAL_BARREL, GUN_AXIAL_BARREL_LONG, GUN_AXIAL_MUZZLE, GUN_AXIAL_MUZZLE_WIDE,
            ARMOR_BELT, ARMOR_REACTIVE, ARMOR_DEFLECT,
            ENGINE_THRUST, ENGINE_REACTOR, ENGINE_OVERDRIVE,
            TACTIC_EW, TACTIC_FIRE,
            SUPPORT_REPAIR, SUPPORT_LINK, SUPPORT_MAGAZINE, SUPPORT_RADAR,
            SUPPORT_LOGISTICS, GUARD_FLAK, SUPPORT_OBSERVER);

    /** 岚的「百家饭」不抽这些船：蓝星旧船（剧情专属）、传说名舰与无所属杂牌舰。 */
    private static final java.util.Set<String> MIX_EXCLUDED = mixExcludedIds();

    private static java.util.Set<String> mixExcludedIds() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Ship s : BLUE_STAR) {
            ids.add(s.id());
        }
        for (Ship s : UNAFFILIATED) {
            ids.add(s.id());
        }
        for (Ship s : Legends.ALL) {
            ids.add(s.id());
        }
        return java.util.Set.copyOf(ids);
    }

    /** 全表（顺序即掉落/列表展示顺序）= 无所属 27 + 四小势力 8 + 四大封锁国 81。 */
    private static final List<Ship> BASE_ALL = List.of(
            CORE_COMMAND,
            GUN_MAIN, GUN_VULCAN, GUN_BROAD, GUN_LANCE, GUN_WAVE, GUN_HEAVY, GUN_MISSILE,
            GUN_AXIAL_BARREL, GUN_AXIAL_BARREL_LONG, GUN_AXIAL_MUZZLE, GUN_AXIAL_MUZZLE_WIDE,
            ARMOR_BELT, ARMOR_REACTIVE, ARMOR_DEFLECT,
            ENGINE_THRUST, ENGINE_REACTOR, ENGINE_OVERDRIVE,
            TACTIC_EW, TACTIC_FIRE,
            SUPPORT_REPAIR, SUPPORT_LINK, SUPPORT_MAGAZINE, SUPPORT_RADAR,
            SUPPORT_LOGISTICS, GUARD_FLAK, SUPPORT_OBSERVER,
            EMPIRE_T774, EMPIRE_T771, REP_GREYHARBOR, REP_WHITEBAY,
            DYN_AURELIA, DYN_VERONICA, COMB_VX2C, COMB_MK3);


    /** 初始舰队成员：梯度基线（白值不缩放，它们就是"最弱初始船"的参照系）。 */
    private static final java.util.Set<String> STARTER_IDS = java.util.Set.of(
            "armor.belt", "armor.reactive", "gun.main", "gun.broadside", "gun.vulcan",
            "gun.lance", "gun.axial.barrel.long", "gun.axial.muzzle.wide",
            "engine.reactor", "tactic.ew", "tactic.fire", "core.command");

    /** 随机编队允许偏离基准战力的幅度：±4 点（约 1.4%）内都算"总战力不变"。 */
    private static final int MIX_TOLERANCE = 4;

    /** 水库抽样保留的候选组合数：越大越不容易重复，也越费一点开局时间。 */
    private static final int MIX_SAMPLES = 96;

    /** 随机编队的最低总结构值：防止抽到一堆低血高甲船，战力够但一碰就碎。 */
    private static final int MIX_MIN_HP = 105;

    /** 最弱初始船白值（反应堆舰 12+1）：T1 梯度基线，T5 = ×20。 */
    public static final int WHITE_BASE = 13;

    public static final List<Ship> ALL = List.copyOf(concatAll());


    /**
     * 梯度缩放：白值（血+盾+防御+防空+齐射）按 tier 0-4 = 13/27/58/123/260
     * 几何爬升（T5 = 基线 ×20），白值按角色权重分配到各项。初始船不缩放。
     */
    private static Ship scaled(Ship s) {
        if (STARTER_IDS.contains(s.id())) {
            return s;
        }
        int g = s.id().startsWith("leg.") ? Math.max(0, Math.min(4, s.tier() - 1))
                : Math.min(3, s.tier());
        if (g <= 0) {
            return s;
        }
        double target = WHITE_BASE * Math.pow(20, g / 4.0);
        double[] wt = switch (s.role()) {
            case MAIN_GUN -> new double[]{0.42, 0.08, 0.22, 0.03, 0.25};
            case SECONDARY_GUN -> new double[]{0.38, 0.10, 0.15, 0.12, 0.25};
            case ARMOR -> new double[]{0.55, 0.15, 0.25, 0.00, 0.05};
            case DRIVE -> new double[]{0.45, 0.10, 0.15, 0.05, 0.05};
            default -> new double[]{0.42, 0.08, 0.15, 0.15, 0.20};
        };
        int hp = Math.max(4, (int) Math.round(target * wt[0]));
        int armor = (int) Math.round(target * wt[1]);
        int plate = (int) Math.round(target * wt[2]);
        int flak = (int) Math.round(target * wt[3]);
        int volley = Math.max(s.role() == ShipRole.ARMOR ? 0 : 1,
                (int) Math.round(target * wt[4]));
        return new Ship(s.id(), s.nameZh(), s.nameEn(), s.role(), s.w(), s.h(),
                hp, armor, plate, flak, volley, s.energy(), s.draw(),
                s.cardIds(), s.perks(), g, s.textZh());
    }

    private static List<Ship> concatAll() {
        List<Ship> out = new ArrayList<>(BASE_ALL.size() + FactionCatalog.ALL.size() + Legends.ALL.size());
        out.addAll(BASE_ALL);
        out.addAll(FactionCatalog.ALL);
        out.addAll(Legends.ALL);
        return out.stream().map(ShipCatalog::scaled).toList();
    }


    /** 轴炮四件。 */
    public static final List<Ship> AXIAL = List.of(
            GUN_AXIAL_BARREL, GUN_AXIAL_BARREL_LONG, GUN_AXIAL_MUZZLE, GUN_AXIAL_MUZZLE_WIDE);

    /** 掉落池：旗舰概念已移除，所有船都可掉落/购买。 */
    public static final List<Ship> DROPS = ALL;

    private static final Map<String, Ship> BY_ID = index();

    static {
        for (Ship ship : ALL) {
            for (String cardId : ship.cardIds()) {
                if (CardCatalog.byId(cardId) == null) {
                    throw new IllegalStateException("船「" + ship.nameZh() + "」带的卡不存在：" + cardId);
                }
            }
            for (Perk perk : ship.perks()) {
                if (perk == null) {
                    throw new IllegalStateException("船「" + ship.nameZh() + "」的光环表里有 null");
                }
            }
        }
    }

    /**
     * 起始舰队。
     *
     * @param id     唯一 id（{@code starter.gunship}…）
     * @param nameZh 中文名（游戏内 = 主角名）
     * @param nameEn 英文名
     * @param desc   选舰队界面的一句话描述
     * @param fleet  舰队（已摆好）
     */
    public record Starter(String id, String nameZh, String nameEn, String desc, Fleet fleet) {
    }

    private ShipCatalog() {
    }

    /** 全表。 */
    public static List<Ship> all() {
        return ALL;
    }

    /** 掉落池。 */
    public static List<Ship> dropPool() {
        return DROPS;
    }

    /** 按 id 找船，认不出返回 {@code null}。 */
    public static Ship byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** 轴炮四件。 */
    public static List<Ship> axialShips() {
        return AXIAL;
    }

    /** 是不是轴炮件。 */
    public static boolean isAxial(Ship ship) {
        return ship != null && ship.isAxial();
    }

    /**
     * 旧版四支起始舰队（工具/测试仍用；游戏内改用 {@link #startersFor(long)}）。
     *
     * <p>每支都是"指挥舰 + 一条主战炮线 + 装甲/动力/战术"，区别在炮线：
     * 炮术（主炮）、齐射（侧弦）、蓄能（双光矛）、轴炮（管口合流）。</p>
     */
    public static List<Starter> starters() {
        List<Starter> out = new ArrayList<>(4);
        out.add(fixed("starter.gunship", "炮术舰队", "GUNSHIP FLEET",
                "标准炮术编队：主炮压中，装甲与电子战护住两翼。", gunshipUnits()));
        out.add(fixed("starter.broadsider", "齐射舰队", "BROADSIDER FLEET",
                "侧弦齐射编队：靠一排炮线把对手洗成筛子。", empireUnits()));
        out.add(fixed("starter.charger", "蓄能舰队", "CHARGER FLEET",
                "蓄能光矛编队：先攒能量，再一炮贯穿。", chargerUnits()));
        out.add(fixed("starter.axialgun", "轴炮舰队", "AXIALGUN FLEET",
                "轴炮合流编队：管与口贴在一起，倍率才爬得上去。", axialUnits()));
        return out;
    }

    /**
     * 本局可选的起始舰队——选项名就是主角名，旧「xx舰队」只留给工具/测试。
     *
     * <p>岚的船每局现抓：各舰种各一艘、阵营随机，总战力对齐炮术舰队基准，
     * 免得随机出太强或太弱的开局。其余三位用各自的固定编队。</p>
     */
    public static List<Starter> startersFor(long seed) {
        List<Starter> out = new ArrayList<>(4);
        out.add(randomMixedStarter(seed));
        out.add(fixed("starter.broadsider", "「叛逃者」薇拉", "VERA",
                "铁冕帝国的通缉令还挂在港口；她带走的那支舰队，炮口都调转了方向。",
                empireUnits(), false));
        out.add(fixed("starter.charger", "「核心信标」零", "ZERO",
                "没有船厂，也没有船坞；只有残骸堆里拼回来的旧壳，和不肯熄灭的火控。",
                chargerUnits(), false));
        out.add(fixed("starter.bluestar", "「归航者」夏初", "XIA CHU",
                "蓝星远征队的最后一支舰队，沿着祖母留下的星图，往家的方向开。",
                blueUnits(), false));
        return out;
    }

    /** 「归航者」夏初：蓝星（旧地球）远征队留下的六艘旧船。 */
    private static List<FleetUnit> blueUnits() {
        return List.of(
                at(BLUE_LONGNIGHT, 10, 0),
                at(BLUE_FARSIGHT, 10, 2),
                at(BLUE_HOMEWARD, 8, 0),
                at(BLUE_LIGHTHOUSE, 8, 2),
                at(BLUE_CAMPFIRE, 8, 4),
                at(BLUE_HOME, 0, 4));
    }

    private static List<FleetUnit> gunshipUnits() {
        return List.of(
                at(ARMOR_BELT, 4, 0),
                at(GUN_MAIN, 4, 2),
                at(GUN_VULCAN, 2, 2),
                at(ENGINE_REACTOR, 8, 2),
                at(TACTIC_EW, 8, 4),
                at(CORE_COMMAND, 0, 4));
    }

    /** 「叛逃者」薇拉：清一色铁冕帝国年型舰（帝国年型没有装甲舰这一档）。 */
    private static List<FleetUnit> empireUnits() {
        return List.of(
                at(FactionCatalog.EMPIRE_T773, 2, 0),
                at(FactionCatalog.EMPIRE_T762, 2, 4),
                at(FactionCatalog.EMPIRE_T763, 5, 8),
                at(FactionCatalog.EMPIRE_T769, 8, 0),
                at(FactionCatalog.EMPIRE_T770, 8, 4),
                at(FactionCatalog.EMPIRE_T768, 0, 6),
                at(FactionCatalog.EMPIRE_T776, 0, 10));
    }

    /** 「核心信标」零：蓄能光矛 + 电子战；装甲舰压在右前排。 */
    private static List<FleetUnit> chargerUnits() {
        return List.of(
                at(ARMOR_BELT, 10, 0),
                at(GUN_LANCE, 10, 2).withLevel(2),
                at(TACTIC_FIRE, 4, 0),
                at(TACTIC_EW, 10, 5),
                at(ENGINE_REACTOR, 4, 3),
                at(CORE_COMMAND, 0, 8));
    }

    /** 「归航者」初：轴炮管口合流。 */
    private static List<FleetUnit> axialUnits() {
        return List.of(
                at(ARMOR_BELT, 2, 0),
                at(GUN_AXIAL_BARREL_LONG, 2, 3),
                at(GUN_AXIAL_MUZZLE_WIDE, 5, 3),
                at(GUN_VULCAN, 8, 3),
                at(ENGINE_REACTOR, 8, 6),
                at(CORE_COMMAND, 0, 8));
    }

    /**
     * 主角的「百家饭」：每局从各阵营现抓，各舰种各一艘，总战力贴近基准。
     */
    private static Starter randomMixedStarter(long seed) {
        Random rnd = new Random(seed ^ 0x9E3779B97F4A7C15L);
        List<Ship> pool = ALL.stream()
                .filter(s -> !MIX_EXCLUDED.contains(s.id()))
                .filter(s -> !s.id().startsWith("gun.axial"))
                .toList();
        List<List<Ship>> pools = new ArrayList<>(6);
        for (ShipRole role : List.of(ShipRole.COMMAND, ShipRole.MAIN_GUN, ShipRole.SECONDARY_GUN,
                ShipRole.ARMOR, ShipRole.DRIVE, ShipRole.TACTICS)) {
            pools.add(shuffle(pool.stream().filter(s -> s.role() == role).toList(), rnd));
        }
        int target = gunshipUnits().stream().mapToInt(u -> shipPower(u.ship())).sum();
        List<Ship> mix = pickMix(pools, target, rnd);
        if (mix.isEmpty()) {
            for (List<Ship> rolePool : pools) {
                if (!rolePool.isEmpty()) {
                    mix.add(rolePool.get(0));
                }
            }
        }
        // 前排到后排：装甲 → 指挥 → 主炮 → 副炮 → 战术 → 动力。从棋盘右侧往左找空位，
        // 装甲舰永远顶在最右（我方朝右，x 大 = 先挨打），指挥舰和炮线依次缩在后面。
        List<FleetUnit> order = new ArrayList<>(mix.size());
        for (Ship ship : mix) {
            order.add(unit(ship));
        }
        order.sort((a, b) -> Integer.compare(frontRank(a.ship().role()), frontRank(b.ship().role())));
        List<FleetUnit> placed = new ArrayList<>(order.size());
        for (FleetUnit unit : order) {
            boolean front = unit.ship().role() == ShipRole.ARMOR;
            int startX = front ? FleetBoard.SIZE - unit.w() : 0;
            int step = front ? -1 : 1;
            boolean done = false;
            for (int x = startX; x >= 0 && x + unit.w() <= FleetBoard.SIZE && !done; x += step) {
                for (int y = 0; y + unit.h() <= FleetBoard.SIZE && !done; y++) {
                    FleetUnit candidate = unit.at(x, y);
                    if (FleetBoard.of(placed).canPlace(candidate)) {
                        placed.add(candidate);
                        done = true;
                    }
                }
            }
        }
        Fleet fleet = Fleet.of("starter.gunship", "「领航员」岚", "LAN", placed);
        return new Starter("starter.gunship", "「领航员」岚", "LAN",
                "边贸航线上跑了七年，她的船每一艘都换过三个主人，却都还认得她。",
                fleet);
    }

    /** 按种子打乱候选池：战力相同的前提下，每局抓到的船不同。 */
    private static List<Ship> shuffle(List<Ship> ships, Random rnd) {
        List<Ship> out = new ArrayList<>(ships);
        java.util.Collections.shuffle(out, rnd);
        return out;
    }

    /** 每个舰种各挑一艘，枚举出总战力最接近 target 的组合（池子很小，直接穷举）。 */
    private static void closestMix(List<List<Ship>> pools, int depth, int sum, int target,
                                   List<Ship> cur, List<Ship> best, int[] bestDiff) {
        if (depth == pools.size()) {
            int diff = Math.abs(sum - target);
            if (diff < bestDiff[0]) {
                bestDiff[0] = diff;
                best.clear();
                best.addAll(cur);
            }
            return;
        }
        for (Ship ship : pools.get(depth)) {
            cur.add(ship);
            closestMix(pools, depth + 1, sum + shipPower(ship), target, cur, best, bestDiff);
            cur.remove(cur.size() - 1);
        }
    }

    /** 在“最接近 target”的若干组合里水库抽样，避免每局都抽到同一套船。 */
    private static List<Ship> pickMix(List<List<Ship>> pools, int target, Random rnd) {
        List<Ship> picked = pickMix(pools, target, rnd, MIX_MIN_HP);
        return picked.isEmpty() ? pickMix(pools, target, rnd, 0) : picked;
    }

    private static List<Ship> pickMix(List<List<Ship>> pools, int target, Random rnd, int minHp) {
        int[] bestDiff = {Integer.MAX_VALUE};
        closestMix(pools, 0, 0, target, new ArrayList<>(), new ArrayList<>(), bestDiff);
        List<List<Ship>> matches = new ArrayList<>();
        int[] seen = {0};
        collectNear(pools, 0, 0, 0, target, bestDiff[0] + MIX_TOLERANCE, minHp, rnd,
                new ArrayList<>(), matches, seen);
        if (matches.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(matches.get(rnd.nextInt(matches.size())));
    }

    private static void collectNear(List<List<Ship>> pools, int depth, int sum, int hpSum,
                                    int target, int allowedDiff, int minHp, Random rnd,
                                    List<Ship> cur, List<List<Ship>> matches, int[] seen) {
        if (depth == pools.size()) {
            if (hpSum >= minHp && Math.abs(sum - target) <= allowedDiff) {
                seen[0]++;
                if (matches.size() < MIX_SAMPLES) {
                    matches.add(new ArrayList<>(cur));
                } else {
                    int j = rnd.nextInt(seen[0]);
                    if (j < MIX_SAMPLES) {
                        matches.set(j, new ArrayList<>(cur));
                    }
                }
            }
            return;
        }
        for (Ship ship : pools.get(depth)) {
            cur.add(ship);
            collectNear(pools, depth + 1, sum + shipPower(ship), hpSum + ship.hp(),
                    target, allowedDiff, minHp, rnd, cur, matches, seen);
            cur.remove(cur.size() - 1);
        }
    }

    /** 随机编队的站位顺序：0 最靠前排。 */
    private static int frontRank(ShipRole role) {
        return switch (role) {
            case ARMOR -> 0;
            case COMMAND -> 1;
            case MAIN_GUN -> 2;
            case SECONDARY_GUN -> 3;
            case TACTICS -> 4;
            case DRIVE -> 5;
        };
    }

    /** 粗口径战力：和生成敌方编队时的船强度同源（只看船体，不算卡牌）。 */
    private static int shipPower(Ship ship) {
        // 与 Run.estimatePower 同权：结构 0.3、护甲 2、防御 3、齐射 3
        return (int) Math.round(ship.hp() * 0.3) + ship.armor() * 2 + ship.plate() * 3
                + ship.volley() * 3;
    }

    /** 起始舰队（越界取最后一个，不再抛）。 */
    public static Starter starter(int index) {
        List<Starter> all = starters();
        int i = Math.max(0, Math.min(all.size() - 1, index));
        return all.get(i);
    }

    /** 起始舰队有几支。 */
    public static int starterCount() {
        return starters().size();
    }

    /** 一艘 Lv1 船（还没摆位）。 */
    public static FleetUnit unit(Ship ship) {
        return new FleetUnit(ship, 1, 0, 0);
    }

    /** 一艘 Lv1 船摆在 (x,y)。 */
    public static FleetUnit at(Ship ship, int x, int y) {
        return new FleetUnit(ship, 1, x, y);
    }

    /** 文件名片段（{@code gun.main} → {@code gun.main}）。 */
    public static String fileName(Ship ship) {
        return ship.id().toLowerCase(Locale.ROOT);
    }

    private static Starter starter(String id, String nameZh, String nameEn, String desc,
                                   List<FleetUnit> units) {
        return starter(id, nameZh, nameEn, desc, units, true);
    }

    private static Starter starter(String id, String nameZh, String nameEn, String desc,
                                   List<FleetUnit> units, boolean respectSaved) {
        Fleet saved = respectSaved ? ShipArtStore.applySavedArrangement(id, nameZh, nameEn, units) : null;
        Fleet fleet = saved != null ? saved : Fleet.autoArrange(id, nameZh, nameEn, units);
        return new Starter(id, nameZh, nameEn, desc, fleet);
    }

    /** 用给定坐标直接成军（{@code units} 里的 x/y 就是出厂编排）。 */
    private static Starter fixed(String id, String nameZh, String nameEn, String desc,
                                 List<FleetUnit> units, boolean respectSaved) {
        Fleet saved = respectSaved ? ShipArtStore.applySavedArrangement(id, nameZh, nameEn, units) : null;
        Fleet fleet = saved != null ? saved : Fleet.of(id, nameZh, nameEn, units);
        return new Starter(id, nameZh, nameEn, desc, fleet);
    }

    private static Starter fixed(String id, String nameZh, String nameEn, String desc,
                                 List<FleetUnit> units) {
        return fixed(id, nameZh, nameEn, desc, units, true);
    }

    private static Map<String, Ship> index() {
        Map<String, Ship> map = new LinkedHashMap<>();
        for (Ship ship : ALL) {
            Ship prev = map.put(ship.id(), ship);
            if (prev != null) {
                throw new IllegalStateException("船 id 重复：" + ship.id());
            }
        }
        return Map.copyOf(map);
    }
}
