package com.hysx.salvosequence.fleet;

import java.util.List;

import static com.hysx.salvosequence.fleet.Perk.Kind.DRAW;
import static com.hysx.salvosequence.fleet.Perk.Kind.ENERGY;
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
 * 四大封锁国舰船目录（波次主力 + 俘获大头）。
 *
 * <p>数值风格参考史实舰种与碧蓝航线式阵营倾向，全部收敛在对称区间内
 * （hp 8~52、防御 0~8、防空 0~3、齐射 0~9）：</p>
 * <ul>
 *   <li><b>铁冕帝国</b>：装甲厚、齐射平——铁血流；按下水年份命名。</li>
 *   <li><b>白鹰联邦</b>：全能均衡、抽牌多——航母流；按抽象名词（格言）命名。</li>
 *   <li><b>北境联合</b>：血厚齐射猛、防空薄——攻坚流；按革命词+序数命名。</li>
 *   <li><b>樱幕府</b>：齐射高血量低、防空好——鱼雷夜袭流；按花与季节气象命名。</li>
 *   <li><b>南十字自治领</b>：防空与引导光环——观测流；按星座与航海家命名。</li>
 * </ul>
 * 背景设定与命名家规见 {@code docs/LORE.md}。
 */
public final class FactionCatalog {

    // ---------------------------------------------------------------- 卡组套件

    private static final List<String> KIT_GUN_LIGHT = List.of("vulcan.burst", "vulcan.suppress");
    private static final List<String> KIT_GUN_MED = List.of("main.volley", "main.spot");
    private static final List<String> KIT_GUN_MED2 = List.of("main.volley", "main.salvo");
    private static final List<String> KIT_GUN_HEAVY = List.of("main.effective", "main.spot");
    private static final List<String> KIT_BROAD = List.of("broad.fire", "broad.rapid");
    private static final List<String> KIT_BROAD_HEAVY = List.of("broad.barrage", "broad.fire");
    private static final List<String> KIT_LANCE = List.of("lance.beam", "lance.focus");
    private static final List<String> KIT_WAVE = List.of("wave.charge", "wave.fire");
    private static final List<String> KIT_MISSILE = List.of("miss.salvo", "miss.swarm");
    private static final List<String> KIT_MISSILE_HEAVY = List.of("miss.salvo", "miss.swarm", "vulcan.suppress");
    private static final List<String> KIT_WALL = List.of("armor.shield", "armor.brace");
    private static final List<String> KIT_WALL_RX = List.of("armor.reactive", "armor.shield");
    private static final List<String> KIT_HEAL = List.of("core.patch", "buff.repair");
    private static final List<String> KIT_EW = List.of("ew.jam", "ew.datalink");
    private static final List<String> KIT_ENERGY = List.of("reactor.surge", "drive.evade");
    private static final List<String> KIT_EVADE = List.of("drive.evade", "core.patch");
    private static final List<String> KIT_AIM = List.of("heavy.lock", "buff.ammo");
    private static final List<String> KIT_SCOUT = List.of("vulcan.spray", "drive.evade");
    private static final List<String> KIT_ACE = List.of("buff.ace", "buff.warp");

    private static List<Perk> perks(Perk... list) {
        return List.of(list);
    }

    private static Ship ship(String id, String zh, String en, ShipRole role, int w, int h,
                             int hp, int shield, int plate, int flak, int volley,
                             List<String> cards, List<Perk> perks, int tier, String lore) {
        return new Ship(id, zh, en, role, w, h, hp, shield, plate, flak, volley, 0, 0,
                cards, perks, tier, lore);
    }

    // ---------------------------------------------------------------- 铁冕帝国（年份）

    public static final Ship EMPIRE_T762 = ship("empire.t762", "762年型 巡卫舰", "T762 PATROL",
            ShipRole.MAIN_GUN, 3, 3, 16, 0, 2, 0, 5,
            KIT_GUN_MED, perks(), 1,
            "762 年型是帝国服役最久的量产舰，翻修次数比船员换装次数还多。老兵说它全身只剩舰名是原装的。");

    public static final Ship EMPIRE_T763 = ship("empire.t763", "763年型 雷击舰", "T763 TORPEDO",
            ShipRole.SECONDARY_GUN, 2, 3, 12, 0, 0, 0, 6,
            KIT_MISSILE, perks(), 1,
            "雷击舰的战术条令只有一页：冲过去，放完就跑。776 年修订版加了一句：跑不掉就算了。");

    public static final Ship EMPIRE_T766 = ship("empire.t766", "766年型 潜行舰", "T766 STEALTH",
            ShipRole.TACTICS, 2, 2, 10, 0, 0, 1, 2,
            KIT_SCOUT, perks(new Perk(SPOT, 1, RING)), 2,
            "潜行舰涂了帝国最贵的消磁漆，效果拔群——雷达上看不见，肉眼上一清二楚。");

    public static final Ship EMPIRE_T769 = ship("empire.t769", "769年型 袖珍舰", "T769 POCKET",
            ShipRole.MAIN_GUN, 4, 2, 20, 0, 3, 0, 6,
            KIT_GUN_MED2, perks(new Perk(PLATE, 1, SELF)), 2,
            "条令要求战列舰的火力塞进巡洋舰的吨位。工程师做到了，代价是住舱全改成弹药库，船员睡炮弹箱。");

    public static final Ship EMPIRE_T770 = ship("empire.t770", "770年型 战列舰", "T770 BATTLESHIP",
            ShipRole.MAIN_GUN, 5, 5, 24, 1, 4, 0, 8,
            KIT_GUN_HEAVY, perks(new Perk(PLATE, 1, SELF)), 3,
            "770 年型首舰的舵机在处女航就卡死过一次，被半个封锁舰队围殴。帝国宣传部门称之为“战术机动”。");

    /** 帝国封锁舰队的旗舰——薇拉开走的就是这一艘。 */
    public static final Ship EMPIRE_T776 = ship("empire.t776", "776年型 指挥舰", "T776 COMMAND",
            ShipRole.COMMAND, 3, 3, 26, 0, 3, 1, 2,
            List.of("core.rapid", "core.patch", "buff.staff"),
            perks(new Perk(SHIELD_LINK, 1, CROSS)), 4,
            "776 年型是帝国封锁舰队的旗舰型。它的护盾令能沿着贴身友舰扩散——所以叛逃那天，薇拉带走了整条指挥链。");

    /** 白鹰联邦的指挥舰：国会授权，舰队的数据链从这里分发。 */
    public static final Ship EAGLE_COMMAND = ship("eagle.congress", "国会级 指挥舰", "CONGRESS COMMAND",
            ShipRole.COMMAND, 3, 3, 24, 0, 2, 1, 2,
            List.of("core.rapid", "core.patch", "buff.staff"),
            perks(new Perk(SHIELD_LINK, 1, CROSS)), 4,
            "国会级不装主炮：它的火力是授权。每一道开火令都要在这里盖一次章，所以联邦舰队总是慢半拍，却从不打错人。");

    /** 北境联合的指挥舰：号令从舰桥直接压到炮位。 */
    public static final Ship NORTH_COMMAND = ship("north.order", "号令级 指挥舰", "ORDER COMMAND",
            ShipRole.COMMAND, 3, 3, 27, 0, 3, 0, 2,
            List.of("core.rapid", "core.patch", "buff.staff"),
            perks(new Perk(SHIELD_LINK, 1, CROSS)), 4,
            "号令级的舰桥没有座椅——政委站着发令，炮手站着装填。北境人认为坐下会让命令变软。");

    /** 樱幕府的指挥舰：花道与炮术在同一张图上。 */
    public static final Ship SAKURA_COMMAND = ship("sakura.command", "樱庭级 指挥舰", "SAKURA COMMAND",
            ShipRole.COMMAND, 3, 3, 22, 0, 2, 1, 3,
            List.of("core.rapid", "core.patch", "buff.staff"),
            perks(new Perk(SHIELD_LINK, 1, CROSS)), 4,
            "樱庭级的甲板真的种着樱树。幕府的解释是：炮手看见花，手会更稳；看见花落，就知道该撤了。");

    /** 南十字自治领的指挥舰：靠星图而不是命令指挥。 */
    public static final Ship SOUTH_COMMAND = ship("south.flagstar", "旗星级 指挥舰", "FLAGSTAR COMMAND",
            ShipRole.COMMAND, 3, 3, 24, 0, 2, 1, 2,
            List.of("core.rapid", "core.patch", "buff.staff"),
            perks(new Perk(SHIELD_LINK, 1, CROSS)), 4,
            "旗星级把指挥席放在观测舱下面——领航员抬头看星，舰长低头看领航员。自治领相信星图比命令可靠。");

    /** 帝国年型里的动力舰：多一格能量，尾流替贴身友舰卸火力。 */
    public static final Ship EMPIRE_T768 = new Ship(
            "empire.t768", "768年型 动力舰", "T768 REACTOR", ShipRole.DRIVE, 2, 2,
            12, 0, 1, 0, 0, 1, 0,
            KIT_ENERGY, perks(new Perk(SHIELD, 1, CROSS)), 2,
            "768 年型是帝国年型舰的通用动力段——拆下来能当反应堆，装上去能当船。帝国后勤最爱它。");

    public static final Ship EMPIRE_T772 = ship("empire.t772", "772年型 猎杀舰", "T772 HUNTER",
            ShipRole.SECONDARY_GUN, 2, 3, 13, 0, 1, 1, 5,
            KIT_GUN_LIGHT, perks(new Perk(FLAK, 1, CROSS)), 1,
            "猎杀舰专门猎杀猎杀舰。帝国的命名逻辑在这里打了个结，档案员建议不要深究。");

    public static final Ship EMPIRE_T773 = ship("empire.t773", "773年型 破交舰", "T773 RAIDER",
            ShipRole.MAIN_GUN, 3, 3, 15, 0, 1, 0, 6,
            KIT_BROAD, perks(new Perk(VOLLEY, 1, ROW)), 2,
            "破交舰挂商旗干活。被认出来之前它是一艘货船，被认出来之后它是一艘战舰，被击沉之后它是一笔坏账。");

    public static final Ship EMPIRE_T775 = ship("empire.t775", "775年型 前卫舰", "T775 VANGUARD",
            ShipRole.MAIN_GUN, 3, 2, 14, 0, 2, 1, 6,
            KIT_GUN_MED, perks(new Perk(SPOT, 1, CROSS)), 3,
            "775 年型还在图纸阶段就宣传了三年。真船下水那天，前卫舰队发现自己已经过时了。");

    // ---------------------------------------------------------------- 灰港共和国（地名）

    public static final Ship REP_FREEPORT = ship("rep.freeport", "自由港级 商船", "FREEPORT MERCHANT",
            ShipRole.DRIVE, 3, 2, 15, 0, 0, 0, 0,
            KIT_ENERGY, perks(new Perk(SHIELD, 1, RING)), 1,
            "自由港级是共和国的移动海关。它给谁盖章，谁就不用交税——所以它自己永远不用交税。");

    public static final Ship REP_GENOA = ship("rep.genoa", "热那亚级 快船", "GENOA CLIPPER",
            ShipRole.SECONDARY_GUN, 2, 2, 11, 0, 0, 1, 5,
            KIT_GUN_LIGHT, perks(new Perk(FLAK, 1, SELF)), 1,
            "热那亚级保持共和国的最速纪录。船东说纪录是绕出来的，不是跑出来的。");

    public static final Ship REP_VENICE = ship("rep.venice", "威尼斯级 武库舰", "VENICE ARSENAL",
            ShipRole.MAIN_GUN, 4, 3, 21, 0, 2, 1, 7,
            KIT_BROAD_HEAVY, perks(new Perk(SPOT, 1, ROW)), 3,
            "威尼斯兵工厂一天下水一条船，武库舰是那套流水线的得意作——炮管标准化到可以互相换着用。");

    public static final Ship REP_HANSA = ship("rep.hansa", "汉萨级 运输舰", "HANSA FREIGHTER",
            ShipRole.DRIVE, 3, 3, 18, 0, 1, 1, 0,
            KIT_WALL, perks(new Perk(HP, 2, ROW)), 1,
            "汉萨级的货舱能装半个舰队的补给。海盗叫它“会跑的金库”，共和国叫它“会跑的赤字”。");

    public static final Ship REP_MARSEILLE = ship("rep.marseille", "马赛级 巡防舰", "MARSEILLE FRIGATE",
            ShipRole.SECONDARY_GUN, 3, 2, 14, 0, 1, 0, 6,
            KIT_BROAD, perks(), 1,
            "马赛级的船长全是同一家航海学校毕业的，战术手册都是同一个人的笔记，抄袭都没改错题号。");

    public static final Ship REP_BERGEN = ship("rep.bergen", "卑尔根级 捕鲸船", "BERGEN WHALER",
            ShipRole.SECONDARY_GUN, 3, 3, 16, 0, 1, 0, 5,
            KIT_MISSILE_HEAVY, perks(), 2,
            "捕鲸船的鱼雷本来是叉鲸鱼的。叉到军舰属于意外收获，按共和国海商法可以报销售。");

    public static final Ship REP_LISBON = ship("rep.lisbon", "里斯本级 远航舰", "LISBON VOYAGER",
            ShipRole.DRIVE, 2, 3, 13, 0, 0, 1, 1,
            KIT_SCOUT, perks(new Perk(REPAIR, 1, CROSS)), 2,
            "里斯本级跑过最远的航线，回来时船壳上长满了不属于本星球的藤壶。藤壶已列入保护名录。");

    public static final Ship REP_CARTAGENA = ship("rep.cartagena", "卡塔赫纳级 堡垒舰", "CARTAGENA FORT",
            ShipRole.ARMOR, 3, 3, 36, 0, 5, 1, 0,
            KIT_WALL, perks(new Perk(FLAK, 1, ROW)), 2,
            "卡塔赫纳是共和国的南海要塞。堡垒舰的建造规格写着「能挡住帝国一次齐射」——帝国听了之后试了三次。");

    public static final Ship REP_ALEXANDRIA = ship("rep.alexandria", "亚历山大级 灯塔舰", "ALEXANDRIA LIGHT",
            ShipRole.TACTICS, 2, 2, 12, 0, 0, 1, 0,
            KIT_EW, perks(new Perk(SPOT, 2, FLEET)), 3,
            "灯塔舰的探照灯亮到全舰队都能借光。帝国建议它关灯，共和国回复：付费了。");

    // ---------------------------------------------------------------- 圣痕王朝（人名）

    public static final Ship DYN_NELSON = ship("dyn.nelson", "纳尔逊级 战列舰", "NELSON BATTLESHIP",
            ShipRole.MAIN_GUN, 5, 5, 22, 1, 3, 1, 9,
            KIT_GUN_HEAVY, perks(new Perk(SPOT, 1, ROW)), 3,
            "纳尔逊级把“近距离接敌”写进了造船指标。设计师的解释是：反正要接，不如接的时候炮更大。");

    public static final Ship DYN_DRAKE = ship("dyn.drake", "德雷克级 掠私舰", "DRAKE PRIVATEER",
            ShipRole.SECONDARY_GUN, 2, 3, 12, 0, 0, 0, 6,
            KIT_MISSILE, perks(new Perk(VOLLEY, 1, RING)), 2,
            "掠私舰持王朝私掠证作业。证书的措辞经过三位法务官润色，翻译过来是“抢敌国的合法，抢自己的上绞架”。");

    public static final Ship DYN_GEORGE = ship("dyn.george", "圣乔治级 撞角舰", "GEORGE RAMMER",
            ShipRole.MAIN_GUN, 3, 2, 16, 0, 2, 0, 4,
            KIT_GUN_MED2, perks(new Perk(PLATE, 1, SELF)), 2,
            "圣乔治级保留了一根实体撞角，属于传统装饰。上一次用它撞沉敌舰时，炮还没发明。");

    public static final Ship DYN_MICHAEL = ship("dyn.michael", "圣米迦勒级 护教舰", "MICHAEL CRUSADER",
            ShipRole.ARMOR, 3, 3, 34, 0, 5, 0, 0,
            KIT_WALL, perks(new Perk(HP, 2, CROSS)), 2,
            "护教舰的装甲上铸着经文。敌方炮手表示瞄着字打，打穿的字都跟“宽恕”有关。");

    public static final Ship DYN_ISABELLA = ship("dyn.isabella", "伊莎贝拉级 商盟舰", "ISABELLA TRADE",
            ShipRole.DRIVE, 3, 2, 14, 0, 0, 1, 1,
            KIT_ENERGY, perks(new Perk(SHIELD, 1, RING)), 2,
            "商盟舰一半是外交使馆一半是当铺。王朝的战争借款都在它账上，所以没人敢把它打沉。");

    public static final Ship DYN_COLUMBUS = ship("dyn.columbus", "哥伦布级 探索舰", "COLUMBUS EXPLORER",
            ShipRole.TACTICS, 2, 3, 11, 0, 0, 1, 1,
            KIT_SCOUT, perks(new Perk(SPOT, 1, FLEET)), 2,
            "哥伦布级负责找新航路，三次远航三次迷航，顺便“发现”了四块已经被别人发现的大陆。");

    public static final Ship DYN_JEANNE = ship("dyn.jeanne", "贞德级 突破舰", "JEANNE BREAKER",
            ShipRole.MAIN_GUN, 2, 2, 12, 0, 1, 0, 7,
            KIT_GUN_HEAVY, perks(new Perk(SHIELD_LINK, 1, CROSS)), 3,
            "贞德级的任务写在船名里：突破。舰队条例补充：突破不了就当诱饵，圣女会原谅的。");

    public static final Ship DYN_LOUIS = ship("dyn.louis", "圣路易级 炮舰", "LOUIS GUNNER",
            ShipRole.MAIN_GUN, 3, 3, 15, 0, 1, 1, 5,
            KIT_GUN_MED, perks(), 1,
            "圣路易级的炮闩上刻着王朝格言。格言被炮烟熏黑之后，炮打得反而更准了。");

    // ---------------------------------------------------------------- 灰市商联（参数串）

    public static final Ship COMB_KZ7 = ship("comb.kz7", "KZ-7 型 拖船", "KZ-7 TUG",
            ShipRole.DRIVE, 2, 2, 11, 0, 1, 0, 1,
            KIT_EVADE, perks(new Perk(HP, 1, RING)), 1,
            "KZ-7 的拖钩能拉动战列舰，账单也能。商联的定价规则：按被拖者的财产估价。");

    public static final Ship COMB_RD44 = ship("comb.rd44", "RD-44 型 打捞舰", "RD-44 SALVAGE",
            ShipRole.TACTICS, 3, 3, 13, 0, 0, 1, 1,
            KIT_HEAL, perks(new Perk(REPAIR, 1, ROW)), 2,
            "RD-44 的抓斗专门打捞还没沉的船——先把船打残的也是它，全流程一体化服务。");

    public static final Ship COMB_XX9 = ship("comb.xx9", "XX-9 型 走私舰", "XX-9 SMUGGLER",
            ShipRole.TACTICS, 2, 3, 10, 0, 0, 2, 2,
            KIT_SCOUT, perks(new Perk(FLAK, 1, SELF)), 2,
            "XX-9 的货舱申报是“空”。每次临检都是空的，商联的账面上它从来没运过任何东西。");

    public static final Ship COMB_TQ1 = ship("comb.tq1", "TQ-1 型 改装舰", "TQ-1 REFIT",
            ShipRole.TACTICS, 2, 2, 10, 0, 0, 1, 1,
            KIT_EW, perks(new Perk(MAGAZINE, 1, ROW)), 2,
            "TQ-1 的改装水平看手气：同一批图纸改出来十二艘，十二个性能。商联说这叫产品多样性。");

    public static final Ship COMB_SL88 = ship("comb.sl88", "SL-88 型 燃料舰", "SL-88 FUELER",
            ShipRole.DRIVE, 3, 2, 13, 0, 0, 0, 0,
            KIT_ENERGY, perks(new Perk(ENERGY, 1, FLEET)), 2,
            "SL-88 装着全舰队的口粮。它上战场从不带武装——最好的防御就是让所有人不敢开炮。");

    public static final Ship COMB_BZ2 = ship("comb.bz2", "BZ-2 型 浮台", "BZ-2 PLATFORM",
            ShipRole.ARMOR, 3, 3, 30, 0, 4, 1, 0,
            KIT_WALL_RX, perks(), 1,
            "BZ-2 不是造出来的，是攒出来的：一个集装箱基座加四台废引擎加一块装甲板。验收文件写的是“合格”。");

    public static final Ship COMB_GX0 = ship("comb.gx0", "GX-0 型 原型舰", "GX-0 PROTOTYPE",
            ShipRole.MAIN_GUN, 2, 3, 12, 0, 1, 0, 6,
            KIT_GUN_MED2, perks(new Perk(SPOT, 1, SELF)), 3,
            "GX-0 是商联所有型号的原型机，永不下岗——因为定型量产比它便宜的说法还没人信。");

    public static final Ship COMB_QQ5 = ship("comb.qq5", "QQ-5 型 无人舰", "QQ-5 DRONE",
            ShipRole.SECONDARY_GUN, 1, 2, 8, 0, 0, 1, 4,
            KIT_GUN_LIGHT, perks(), 1,
            "QQ-5 说是无人舰，其实驾驶舱里坐着人，只是按“无人舰”报的预算。那个人的抚恤金按无人舰标准发。");

    // ---------------------------------------------------------------- 白鹰联邦（抽象名词）

    public static final Ship EAGLE_CONSTITUTION = ship("eagle.constitution", "宪法级 巡防舰", "CONSTITUTION FRIGATE",
            ShipRole.MAIN_GUN, 3, 3, 17, 0, 2, 1, 5,
            KIT_GUN_MED, perks(new Perk(PLATE, 1, SELF)), 2,
            "宪法级的装甲弹开过三发实弹，绰号“老铁甲”。联邦把这三次写进了所有征兵海报。");

    public static final Ship EAGLE_ENTERPRISE = ship("eagle.enterprise", "企业级 突袭舰", "ENTERPRISE STRIKER",
            ShipRole.SECONDARY_GUN, 2, 3, 13, 0, 1, 1, 6,
            KIT_GUN_MED2, perks(new Perk(VOLLEY, 1, ROW)), 3,
            "企业级的战功多到宣传部门用了二十页纸，其中十九页是同一场战斗的不同角度。");

    public static final Ship EAGLE_INTREPID = ship("eagle.intrepid", "无畏级 突击舰", "INTREPID ASSAULT",
            ShipRole.MAIN_GUN, 3, 2, 14, 0, 1, 0, 6,
            KIT_GUN_MED, perks(), 2,
            "无畏级的舰训是“无畏”，乘员手册的第一页写着：害怕是正常的，无畏是纪律。");

    public static final Ship EAGLE_INDEPENDENCE = ship("eagle.independence", "独立级 轻巡舰", "INDEPENDENCE CL",
            ShipRole.MAIN_GUN, 3, 3, 15, 0, 1, 1, 6,
            KIT_GUN_MED, perks(new Perk(SPOT, 1, CROSS)), 2,
            "独立级强调独立作战，条令却要求它永远结伴行动。舰长们的理解是：独立地结伴。");

    public static final Ship EAGLE_TENACIOUS = ship("eagle.tenacious", "坚忍级 驱逐舰", "TENACIOUS DD",
            ShipRole.SECONDARY_GUN, 2, 2, 11, 0, 1, 1, 5,
            KIT_GUN_LIGHT, perks(new Perk(FLAK, 1, RING)), 1,
            "坚忍级挨过六次重创六次返厂，每次出厂都带着新的伤疤徽章。徽章生意养活了半个船坞。");

    public static final Ship EAGLE_VIGILANT = ship("eagle.vigilant", "警觉级 哨戒舰", "VIGILANT PICKET",
            ShipRole.TACTICS, 2, 2, 10, 0, 0, 2, 2,
            KIT_SCOUT, perks(new Perk(SPOT, 1, FLEET)), 2,
            "警觉级的瞭望哨从不换班，因为没人接得住那个班的清醒程度。联邦体格检查表里有一条“过于清醒”。");

    public static final Ship EAGLE_BATAAN = ship("eagle.bataan", "巴丹级 壁垒舰", "BATAAN BASTION",
            ShipRole.ARMOR, 3, 3, 38, 0, 4, 2, 0,
            KIT_WALL_RX, perks(new Perk(FLAK, 1, CROSS)), 2,
            "巴丹级的防御条令写着「坚守阵地直到最后一刻」。联邦的审计条令接着写：最后一刻的加班费双倍。");

    public static final Ship EAGLE_VALIANT = ship("eagle.valiant", "英勇级 战列舰", "VALIANT BB",
            ShipRole.MAIN_GUN, 5, 5, 23, 1, 3, 1, 8,
            KIT_GUN_HEAVY, perks(new Perk(HP, 2, CROSS)), 3,
            "英勇级的装甲按“能扛住自己主炮”设计，联邦称之为工程自信。");

    public static final Ship EAGLE_PIONEER = ship("eagle.pioneer", "进取级 支援舰", "PIONEER SUPPORT",
            ShipRole.DRIVE, 3, 2, 14, 0, 0, 1, 1,
            KIT_ENERGY, perks(new Perk(DRAW, 1, FLEET)), 2,
            "进取级的后勤效率是联邦之最，因为它的补给清单是按点心的形式填的。");

    public static final Ship EAGLE_JUSTICE = ship("eagle.justice", "公正级 仲裁舰", "JUSTICE ARBITER",
            ShipRole.MAIN_GUN, 4, 2, 18, 0, 2, 1, 7,
            KIT_GUN_MED2, perks(new Perk(SPOT, 1, ROW)), 2,
            "仲裁舰负责执行联邦的封锁裁决。裁决书就是炮口指向，生效时间就是弹道时间。");

    public static final Ship EAGLE_PIONEERING = ship("eagle.colony", "开拓级 殖民舰", "COLONY SHIP",
            ShipRole.DRIVE, 3, 3, 16, 0, 1, 1, 0,
            KIT_WALL, perks(new Perk(HP, 1, ROW)), 1,
            "开拓级运过七批移民，每一批都发誓再也不坐船了。第八批的船票已经卖完。");

    public static final Ship EAGLE_HERALD = ship("eagle.herald", "先驱级 侦察舰", "HERALD SCOUT",
            ShipRole.TACTICS, 1, 2, 9, 0, 0, 2, 2,
            KIT_SCOUT, perks(new Perk(SPOT, 1, RING)), 1,
            "先驱级最小最快最便宜，也是联邦损失率最高的——侦察的定义就是先看到所有麻烦。");

    public static final Ship EAGLE_FEDERATION = ship("eagle.federation", "联邦级 旗舰", "FEDERATION FLAG",
            ShipRole.MAIN_GUN, 4, 3, 20, 1, 2, 1, 7,
            KIT_GUN_MED, perks(new Perk(SPOT, 2, ROW)), 3,
            "联邦级用国名命名自己，属于命名法里的自指。宪法级为此提过抗议，被以“不独立”为由驳回。");

    public static final Ship EAGLE_WATCHDOG = ship("eagle.watchdog", "守望级 护卫舰", "WATCHDOG ESCORT",
            ShipRole.SECONDARY_GUN, 2, 3, 12, 0, 1, 2, 4,
            KIT_GUN_LIGHT, perks(new Perk(FLAK, 1, ROW)), 1,
            "守望级的防空记录是全联邦第一，因为它的舰长拒绝相信任何“敌情解除”通报。");

    // ---------------------------------------------------------------- 北境联合（革命词+序数）

    public static final Ship NORTH_OCTOBER = ship("north.october", "十月级 战列舰", "OCTOBER BB",
            ShipRole.MAIN_GUN, 5, 5, 25, 0, 2, 0, 9,
            KIT_GUN_HEAVY, perks(new Perk(VOLLEY, 1, ROW)), 3,
            "十月级的甲板上能召开全员大会。设计师说这不是设计，是传统——船体结构件全是会议桌改的。");

    public static final Ship NORTH_REDBANNER = ship("north.redbanner", "红旗七号 巡卫舰", "REDBANNER-7 CL",
            ShipRole.MAIN_GUN, 3, 3, 17, 0, 1, 0, 7,
            KIT_GUN_MED2, perks(new Perk(VOLLEY, 1, RING)), 2,
            "红旗一号到六号都在演习里“击沉”过红旗七号，七号每次都活着回来交报告。");

    public static final Ship NORTH_STORM = ship("north.storm", "风暴级 驱逐舰", "STORM DD",
            ShipRole.SECONDARY_GUN, 2, 3, 13, 0, 0, 0, 7,
            KIT_MISSILE, perks(), 1,
            "风暴级只在风暴里出航，舰训是“天气越差，武器越准”。气象部门对此保留意见。");

    public static final Ship NORTH_DAWN = ship("north.dawn", "黎明级 布雷舰", "DAWN MINELAYER",
            ShipRole.TACTICS, 3, 2, 14, 0, 0, 1, 1,
            KIT_SCOUT, perks(new Perk(SHIELD, 1, ROW)), 2,
            "黎明级布的雷都有登记造册。战后回收率百分之三，回收的雷再布回去，属于循环经济。");

    public static final Ship NORTH_FURNACE = ship("north.furnace", "熔炉级 装甲舰", "FURNACE ARMOR",
            ShipRole.ARMOR, 4, 2, 38, 0, 5, 0, 0,
            KIT_WALL, perks(new Perk(HP, 2, CROSS)), 2,
            "熔炉级的装甲是自家熔炉一次浇出来的，浇铸那天全厂停电。装甲合格，电费至今未结。");

    public static final Ship NORTH_TORCH = ship("north.torch", "火炬级 支援舰", "TORCH SUPPORT",
            ShipRole.DRIVE, 2, 2, 12, 0, 0, 1, 1,
            KIT_HEAL, perks(new Perk(REPAIR, 1, CROSS)), 1,
            "火炬级传火不限阵营——联合的船修，账记联合头上；别家的船修，账记别家头上。");

    public static final Ship NORTH_IRONFLOW = ship("north.ironflow", "铁流级 突破舰", "IRONFLOW BREAKER",
            ShipRole.MAIN_GUN, 3, 3, 18, 0, 1, 0, 7,
            KIT_GUN_MED2, perks(new Perk(VOLLEY, 1, ROW)), 2,
            "铁流级的突破方案永远是同一条直线。参谋部画过曲线，曲线被评价为不够铁。");

    public static final Ship NORTH_AWAKENING = ship("north.awakening", "觉醒级 侦察舰", "AWAKENING SCOUT",
            ShipRole.TACTICS, 1, 2, 9, 0, 0, 1, 2,
            KIT_SCOUT, perks(new Perk(SPOT, 1, RING)), 1,
            "觉醒级的乘员要接受“觉醒考核”，内容是在零下四十度的桅杆上说出三条敌情。说错了也算说过了。");

    public static final Ship NORTH_HORN = ship("north.horn", "号角级 通讯舰", "HORN SIGNAL",
            ShipRole.TACTICS, 2, 2, 12, 0, 0, 2, 0,
            KIT_EW, perks(new Perk(SHIELD, 1, RING)), 2,
            "号角级的天线能把命令传到整个封锁线。命令的内容大多是“再坚持一下”。");

    public static final Ship NORTH_WARRIOR = ship("north.warrior", "战士级 炮舰", "WARRIOR GUNBOAT",
            ShipRole.MAIN_GUN, 3, 2, 15, 0, 1, 0, 6,
            KIT_GUN_MED, perks(), 1,
            "战士级的全员都是炮手，没有炊事员。伙食问题由炮击解决：打谁，吃谁。");

    public static final Ship NORTH_ONWARD = ship("north.onward", "前进级 运输舰", "ONWARD FREIGHTER",
            ShipRole.DRIVE, 3, 3, 17, 0, 1, 0, 0,
            KIT_WALL, perks(new Perk(HP, 1, ROW)), 1,
            "前进级的名字取自后勤条令第一条：前进。第二条：不管车有没有装满，前进。");

    public static final Ship NORTH_POLARIS = ship("north.polar", "极地级 破冰舰", "POLAR ICEBREAKER",
            ShipRole.MAIN_GUN, 4, 2, 19, 0, 2, 1, 5,
            KIT_GUN_MED, perks(new Perk(PLATE, 1, ROW)), 2,
            "极地级的主炮是破冰锤改装的，冰是它的主场。敌人不是，但敌人可以变成冰。");

    public static final Ship NORTH_WINTER = ship("north.winter", "冬将军级 巡战舰", "GENERAL WINTER",
            ShipRole.MAIN_GUN, 4, 3, 21, 1, 2, 0, 8,
            KIT_AIM, perks(new Perk(VOLLEY, 1, ROW)), 3,
            "冬将军级的武器清单里有一条“严寒”，工程部至今不知道怎么把这条装上船。反正它每次都在。");

    // ---------------------------------------------------------------- 樱幕府（花与季节）

    public static final Ship SAKURA_FUBUKI = ship("sakura.fubuki", "吹雪级 驱逐舰", "FUBUKI DD",
            ShipRole.SECONDARY_GUN, 2, 3, 12, 0, 0, 1, 6,
            KIT_MISSILE, perks(), 1,
            "吹雪级是幕府新式驱逐舰的头胎，全族条令都从它身上总结——包括那条后来被删掉的“夜战必胜”。");

    public static final Ship SAKURA_SHIMAKAZE = ship("sakura.shimakaze", "岛风级 鱼雷艇", "SHIMAKAZE TB",
            ShipRole.SECONDARY_GUN, 1, 2, 9, 0, 0, 1, 7,
            KIT_MISSILE_HEAVY, perks(), 2,
            "岛风级是幕府最快的船，快到全速航行时甲板是弯的。弯的那一段正好是弹药库，没人敢提加固。");

    public static final Ship SAKURA_YUKIKAZE = ship("sakura.yukikaze", "雪风级 祥瑞舰", "YUKIKAZE LUCKY",
            ShipRole.SECONDARY_GUN, 2, 3, 13, 0, 0, 2, 5,
            KIT_MISSILE, perks(new Perk(SHIELD, 1, SELF)), 2,
            "雪风级打满全场，同伴全沉了它没沉。现在所有船都不愿意跟它编队——不是因为不吉利，是因为太吉利，吉利得像有预谋。");

    public static final Ship SAKURA_SHIGURE = ship("sakura.shigure", "时雨级 驱逐舰", "SHIGURE DD",
            ShipRole.SECONDARY_GUN, 2, 3, 12, 0, 0, 1, 6,
            KIT_MISSILE, perks(), 1,
            "时雨级总在雨战中捞到战功，雨一停就熄火。幕府研究了三年气象武运，报告写了四百页。");

    public static final Ship SAKURA_ASASHIO = ship("sakura.asashio", "朝潮级 驱逐舰", "ASASHIO DD",
            ShipRole.SECONDARY_GUN, 2, 2, 11, 0, 0, 1, 6,
            KIT_MISSILE, perks(), 1,
            "朝潮级的十条船在同一场战斗里排成一列纵队，被一发穿到底。此后条令禁止写“一列纵队”四个字。");

    public static final Ship SAKURA_YUUDACHI = ship("sakura.yuudachi", "夕立级 荒潮舰", "YUUDACHI SQUALL",
            ShipRole.SECONDARY_GUN, 2, 2, 11, 0, 0, 0, 7,
            KIT_MISSILE_HEAVY, perks(), 2,
            "夕立级战报里最著名的一句话是“哪艘是夕立？”——敌我在雨里互问了同样的问题。");

    public static final Ship SAKURA_HATSUZUKI = ship("sakura.hatsuzuki", "初月级 防空舰", "HATSUZUKI AA",
            ShipRole.SECONDARY_GUN, 2, 3, 12, 0, 0, 3, 3,
            KIT_GUN_LIGHT, perks(new Perk(FLAK, 1, ROW)), 2,
            "初月级的防空炮比主炮多。主炮的存在意义是让防空炮手知道自己保卫的是一艘军舰。");

    public static final Ship SAKURA_YAEGAKI = ship("sakura.yaegaki", "八重垣级 重壁舰", "YAEGAKI BULWARK",
            ShipRole.ARMOR, 3, 3, 36, 0, 5, 1, 0,
            KIT_WALL, perks(new Perk(HP, 2, CROSS)), 2,
            "八重垣意为八层城墙——船体实际只有两层，但幕府的说法是「另外六层是精神」。");

    public static final Ship SAKURA_TAIHO = ship("sakura.taiho", "大凤级 装甲航舰", "TAIHO ARMOURED",
            ShipRole.MAIN_GUN, 4, 3, 20, 0, 3, 1, 6,
            KIT_WAVE, perks(new Perk(PLATE, 1, SELF)), 3,
            "大凤级重装甲重到浮力余量只剩一条鱼雷的位置。那条鱼雷来了。新型号把余量加到了三条。");

    public static final Ship SAKURA_SHOKAKU = ship("sakura.shokaku", "翔鹤级 突击舰", "SHOKAKU STRIKER",
            ShipRole.MAIN_GUN, 4, 2, 17, 0, 1, 1, 7,
            KIT_MISSILE_HEAVY, perks(new Perk(SPOT, 1, ROW)), 2,
            "翔鹤级与瑞鹤级是双生舰，编队战术天衣无缝——直到分开部署的那天，两艘船都开始怀念对方。");

    public static final Ship SAKURA_ZUIKAKU = ship("sakura.zuikaku", "瑞鹤级 突袭舰", "ZUIKAKU RAIDER",
            ShipRole.MAIN_GUN, 4, 2, 17, 0, 1, 1, 7,
            KIT_MISSILE_HEAVY, perks(new Perk(VOLLEY, 1, ROW)), 2,
            "瑞鹤级每次都能从必败战役里捞出来。幕府把它当吉祥物宣传，它自己对此很低调。");

    public static final Ship SAKURA_KONGO = ship("sakura.kongo", "金刚级 巡战舰", "KONGO BATTLECRUISER",
            ShipRole.MAIN_GUN, 4, 3, 18, 0, 2, 0, 8,
            KIT_GUN_MED2, perks(new Perk(VOLLEY, 1, RING)), 2,
            "金刚级服役三十年，从战列舰改成巡洋战舰再改回来，改到档案部门拒绝再登记它的舰种。");

    public static final Ship SAKURA_NAGATO = ship("sakura.nagato", "长门级 战列舰", "NAGATO BB",
            ShipRole.MAIN_GUN, 5, 5, 24, 1, 3, 1, 8,
            KIT_GUN_HEAVY, perks(new Perk(HP, 2, CROSS)), 3,
            "长门级扛过两次解体审判和一次拆解令，现在是幕府唯一有资格说“我见过全部战争”的船。");

    // ---------------------------------------------------------------- 南十字自治领（星座与航海家）

    public static final Ship SOUTH_CRUX = ship("south.crux", "南十字级 巡洋舰", "CRUX CRUISER",
            ShipRole.MAIN_GUN, 3, 3, 16, 0, 1, 1, 6,
            KIT_GUN_MED, perks(new Perk(SHIELD, 1, RING)), 2,
            "南十字级以自治领旗上的十字命名。十字的四颗星各代表一个加盟港，其中一颗已经不发光了。");

    public static final Ship SOUTH_MAGELLAN = ship("south.magellan", "麦哲伦级 环球舰", "MAGELLAN GLOBAL",
            ShipRole.MAIN_GUN, 4, 3, 19, 0, 2, 1, 6,
            KIT_GUN_MED, perks(new Perk(SPOT, 1, ROW)), 2,
            "麦哲伦级完成了三次环球航行，三次都没走完——环球的定义是回来时船还认得。");

    public static final Ship SOUTH_COMPASS = ship("south.compass", "罗盘级 引导舰", "COMPASS GUIDE",
            ShipRole.TACTICS, 2, 2, 12, 0, 0, 1, 0,
            KIT_EW, perks(new Perk(SHIELD, 1, RING)), 2,
            "罗盘级的指针永远指向友舰最多的方向。这导航逻辑让整个自治领舰队抱团了二十年。");

    public static final Ship SOUTH_SEXTANT = ship("south.sextant", "六分仪级 哨戒舰", "SEXTANT PICKET",
            ShipRole.TACTICS, 2, 2, 10, 0, 0, 2, 2,
            KIT_SCOUT, perks(new Perk(SPOT, 1, RING)), 1,
            "六分仪级测角误差 0.1 度，属于全自治领的骄傲。敌人管它叫“那个会算术的”。");

    public static final Ship SOUTH_POLARIS2 = ship("south.polarstar", "北极星级 破袭舰", "POLARIS RAIDER",
            ShipRole.SECONDARY_GUN, 2, 3, 12, 0, 0, 1, 6,
            KIT_MISSILE, perks(new Perk(VOLLEY, 1, RING)), 2,
            "北极星级专挑敌舰队里最亮的那艘下手。理由写进了作战条令：灭了最亮的，剩下的都会慌。");

    public static final Ship SOUTH_VELA = ship("south.vela", "船帆座级 快船", "VELA SAILER",
            ShipRole.SECONDARY_GUN, 2, 2, 10, 0, 0, 2, 5,
            KIT_GUN_LIGHT, perks(new Perk(FLAK, 1, SELF)), 1,
            "船帆座级保留了一面实体帆，纯装饰。全速时帆会响，船员说那是它在背作战条令。");

    public static final Ship SOUTH_CARINA = ship("south.carina", "船底座级 载机舰", "CARINA KEEL",
            ShipRole.MAIN_GUN, 4, 2, 18, 0, 1, 2, 5,
            KIT_MISSILE_HEAVY, perks(new Perk(FLAK, 1, ROW)), 2,
            "船底座级的龙骨是自治领仅有的“整龙骨”，别家的都是拼的。这决定了它永远跑在舰队最前面挡雷。");

    public static final Ship SOUTH_CENTAURUS = ship("south.centaurus", "半人马级 猎舰", "CENTAURUS HUNTER",
            ShipRole.MAIN_GUN, 3, 2, 15, 0, 1, 0, 6,
            KIT_GUN_MED2, perks(), 1,
            "半人马级的人马配比是三比一：三条船员守则对一条马术守则。马术守则其实更实用。");

    public static final Ship SOUTH_CYGNUS = ship("south.cygnus", "天鹅级 医疗舰", "CYGNUS MEDICAL",
            ShipRole.DRIVE, 3, 2, 13, 0, 0, 1, 0,
            KIT_HEAL, perks(new Perk(REPAIR, 1, ROW)), 2,
            "天鹅级有全自治领唯一的病房，墙是白色的。其他船的墙是灰的，灰的好处是看不出哪块新伤。");

    public static final Ship SOUTH_PHOENIX = ship("south.phoenix", "凤凰座级 重生舰", "PHOENIX REBUILT",
            ShipRole.ARMOR, 3, 3, 32, 0, 4, 2, 0,
            KIT_WALL_RX, perks(new Perk(SHIELD, 1, CROSS)), 2,
            "凤凰座级在珍珠式偷袭里烧沉过一次，捞起来修好了。它的舰徽是一团火，火里写着“保险已续”。");

    public static final Ship SOUTH_PAVO = ship("south.pavo", "孔雀级 巡防舰", "PAVO FRIGATE",
            ShipRole.SECONDARY_GUN, 2, 3, 13, 0, 1, 1, 4,
            KIT_GUN_LIGHT, perks(new Perk(SHIELD_LINK, 1, CROSS)), 1,
            "孔雀级的外壳涂了自治领全谱涂装，是舰队仪仗。涂装每季度换色，理由是“气象万千”。");

    private FactionCatalog() {
    }

    /** 势力分组（按 id 前缀）：波次同国编组用。 */
    public static String factionOf(Ship ship) {
        return factionOf(ship.id());
    }

    /** 星区势力名（按 theta 五等分，和星图角度对齐）。 */
    public static final String[] ZONES = {"铁冕帝国", "白鹰联邦", "北境联合", "樱幕府", "南十字自治领"};

    /** theta → 星区势力名。 */
    public static String zoneOf(double theta) {
        double norm = (theta % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
        return ZONES[(int) (norm / (Math.PI * 2 / 5))];
    }

    /** 星区势力 → 该势力的传说舰 slug（用于 pickWave 主题波匹配）。 */
    public static String zoneSlug(String zoneName) {
        return switch (zoneName) {
            case "铁冕帝国" -> "leg.break.";
            case "白鹰联邦" -> "leg.eagle.";
            case "北境联合" -> "leg.north.";
            case "樱幕府" -> "leg.sakura.";
            case "南十字自治领" -> "leg.south.";
            default -> "";
        };
    }

    /** 按船 id 判阵营（传说舰前缀 leg.<国>. 已映射）。 */
    public static String factionOf(String id) {
        if (id.startsWith("empire.")) return "铁冕帝国";
        if (id.startsWith("rep.")) return "灰港共和国";
        if (id.startsWith("dyn.")) return "圣痕王朝";
        if (id.startsWith("comb.")) return "灰市商联";
        if (id.startsWith("eagle.")) return "白鹰联邦";
        if (id.startsWith("north.")) return "北境联合";
        if (id.startsWith("sakura.")) return "樱幕府";
        if (id.startsWith("south.")) return "南十字自治领";
        if (id.startsWith("leg.emp.") || id.startsWith("leg.break.")) return "铁冕帝国";
        if (id.startsWith("leg.eagle.")) return "白鹰联邦";
        if (id.startsWith("leg.north.")) return "北境联合";
        if (id.startsWith("leg.sakura.")) return "樱幕府";
        if (id.startsWith("leg.south.")) return "南十字自治领";
        return "破障者船团";
    }

    /** 四大封锁国全部舰型（供 ShipCatalog 聚合与波次抽取）。 */
    public static final List<Ship> ALL = List.of(
            EMPIRE_T762, EMPIRE_T763, EMPIRE_T766, EMPIRE_T768, EMPIRE_T769, EMPIRE_T770, EMPIRE_T772, EMPIRE_T773, EMPIRE_T775, EMPIRE_T776,
            REP_FREEPORT, REP_GENOA, REP_VENICE, REP_HANSA, REP_MARSEILLE, REP_BERGEN, REP_LISBON, REP_ALEXANDRIA,
            DYN_NELSON, DYN_DRAKE, DYN_GEORGE, DYN_MICHAEL, DYN_ISABELLA, DYN_COLUMBUS, DYN_JEANNE, DYN_LOUIS,
            COMB_KZ7, COMB_RD44, COMB_XX9, COMB_TQ1, COMB_SL88, COMB_BZ2, COMB_GX0, COMB_QQ5,
            EAGLE_CONSTITUTION, EAGLE_ENTERPRISE, EAGLE_INTREPID, EAGLE_INDEPENDENCE, EAGLE_TENACIOUS,
            EAGLE_VIGILANT, EAGLE_VALIANT, EAGLE_PIONEER, EAGLE_JUSTICE, EAGLE_PIONEERING, EAGLE_HERALD,
            EAGLE_FEDERATION, EAGLE_COMMAND, EAGLE_WATCHDOG,
            NORTH_OCTOBER, NORTH_REDBANNER, NORTH_STORM, NORTH_DAWN, NORTH_FURNACE, NORTH_TORCH,
            NORTH_IRONFLOW, NORTH_AWAKENING, NORTH_HORN, NORTH_WARRIOR, NORTH_ONWARD, NORTH_POLARIS, NORTH_WINTER,
            NORTH_COMMAND,
            SAKURA_FUBUKI, SAKURA_SHIMAKAZE, SAKURA_YUKIKAZE, SAKURA_SHIGURE, SAKURA_ASASHIO, SAKURA_YUUDACHI,
            SAKURA_HATSUZUKI, SAKURA_TAIHO, SAKURA_SHOKAKU, SAKURA_ZUIKAKU, SAKURA_KONGO, SAKURA_NAGATO,
            SAKURA_COMMAND,
            SOUTH_CRUX, SOUTH_MAGELLAN, SOUTH_COMPASS, SOUTH_SEXTANT, SOUTH_POLARIS2, SOUTH_VELA,
            SOUTH_CARINA, SOUTH_CENTAURUS, SOUTH_CYGNUS, SOUTH_PHOENIX, SOUTH_PAVO,
            SOUTH_COMMAND,
            REP_CARTAGENA, EAGLE_BATAAN, SAKURA_YAEGAKI);
}
