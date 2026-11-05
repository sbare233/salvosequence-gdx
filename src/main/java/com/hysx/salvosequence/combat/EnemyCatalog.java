package com.hysx.salvosequence.combat;

import com.hysx.salvosequence.deck.CardCatalog;
import com.hysx.salvosequence.fleet.Ship;
import com.hysx.salvosequence.fleet.ShipCatalog;
import com.hysx.salvosequence.fleet.FactionCatalog;
import com.hysx.salvosequence.fleet.ShipRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 敌舰总表 + 一次航行的路线。
 *
 * <p>五场，编队 8/8/9/10/12 艘。</p>
 *
 * <h2>数值对称（背包英雄口径）</h2>
 * <p>敌舰<b>就是</b>船池里那艘船：血量 = 船图结构值、卡组 = 船图卡牌、
 * 出牌 = 与我方同一条管线（牌库→手牌→能量→结算，{@link #DAMAGE_SCALE} 固定 1.0）。
 * 玩家与敌人唯一的差别：玩家能构筑与升级，敌人是每波生成的编队——
 * 波的"构筑"就是选哪些船、摆出什么协同阵。打赢谁就能俘获谁。</p>
 */
public final class EnemyCatalog {

    private EnemyCatalog() {
    }

    /**
     * 配平旋钮：敌方卡面伤害的全局倍率。<b>对称口径 = 1.0，不要动它</b>；
     * 要调难度去改编队规模与舰种搭配（§squad）。
     */
    private static final double DAMAGE_SCALE = 1.0;

    /** 敌方伤害倍率（{@code Battle} 结算敌方卡牌时读）。 */
    public static double damageScale() {
        return DAMAGE_SCALE;
    }

    /** 一艘敌舰 = 船图本体：名字、血量、卡组全从船型来（活牌敌方，无测试脚本）。 */
    private static Enemy foe(String id, Ship hull, String textZh) {
        return new Enemy(id, hull.nameZh(), hull.nameEn(), hull.hp(), hull, null, textZh);
    }

    // ---------------------------------------------------------------- 编队（每关一组互不重复的船图）

    /** 第 1 关：八艘小船的教学局。 */
    public static final Enemy F_SCOUT = foe("foe.scout", ShipCatalog.GUN_VULCAN,
            "薄壳侦察兵。先教你什么叫「校射 + 齐射」。");
    public static final Enemy F_BROAD = foe("foe.broad", ShipCatalog.GUN_BROAD,
            "侧舷齐射：一轮覆盖一整排，别跟她对站。");
    public static final Enemy F_THRUST = foe("foe.thrust", ShipCatalog.ENGINE_THRUST,
            "推进舰：只顾闪避，打死它不亏。");
    public static final Enemy F_MAGAZINE = foe("foe.magazine", ShipCatalog.SUPPORT_MAGAZINE,
            "不显眼，但它的弹夹喂着同一排的炮。");
    public static final Enemy F_RADAR = foe("foe.radar", ShipCatalog.SUPPORT_RADAR,
            "先看到你，再叫别人打你。");
    public static final Enemy F_FLAK = foe("foe.flak", ShipCatalog.GUARD_FLAK,
            "跟编队摆在一起时，整片天都是它的。");
    public static final Enemy F_EW = foe("foe.ew", ShipCatalog.TACTIC_EW,
            "它一开镜，你的舰队就暴露了。");
    public static final Enemy F_OBSERVER = foe("foe.observer", ShipCatalog.SUPPORT_OBSERVER,
            "观测数据借给周围的炮，先捏它。");

    /** 第 2 关起：装甲墙进场。 */
    public static final Enemy F_MISSILE = foe("foe.missile", ShipCatalog.GUN_MISSILE,
            "低伤多段的爆破齐射，就等你把防空撤了。");
    public static final Enemy F_MAIN = foe("foe.main", ShipCatalog.GUN_MAIN,
            "主炮单发：攻击高就是穿甲。");
    public static final Enemy F_BELT = foe("foe.belt", ShipCatalog.ARMOR_BELT,
            "44 血 6 防御的墙：动能打上去全是火星，找热能。");
    public static final Enemy F_LANCE = foe("foe.lance", ShipCatalog.GUN_LANCE,
            "热能长矛无视护盾直贯体层，垫盾没用。");
    public static final Enemy F_REPAIR = foe("foe.repair", ShipCatalog.SUPPORT_REPAIR,
            "焊枪不熄火：先捏它，不然邻船的血永远修得回来。");
    public static final Enemy F_LINK = foe("foe.link", ShipCatalog.SUPPORT_LINK,
            "它举盾，周围一起沾光。");
    public static final Enemy F_SUPPLY = foe("foe.supply", ShipCatalog.SUPPORT_LOGISTICS,
            "一条补给线喂饱整排。");
    public static final Enemy F_CRUISER = foe("foe.cruiser", ShipCatalog.GUN_WAVE,
            "蓄力波动炮：那记热能大炮必须提前一回合垫护盾。");
    public static final Enemy F_HEAVY = foe("foe.heavy", ShipCatalog.GUN_HEAVY,
            "重炮锁定后的那一发，躲不掉就挡满。");
    public static final Enemy F_REACTIVE = foe("foe.reactive", ShipCatalog.ARMOR_REACTIVE,
            "反应装甲舰：把防御力分给贴身的友舰。");
    public static final Enemy F_DEFLECT = foe("foe.deflect", ShipCatalog.ARMOR_DEFLECT,
            "52 血 8 防御的护盾塔，站前排就是一面墙。");
    public static final Enemy F_FIRE = foe("foe.fire", ShipCatalog.TACTIC_FIRE,
            "火控指挥舰：同排的炮线全归它调。");

    // ---------------------------------------------------------------- 势力舰原型（命名限定词随船图走）

    public static final Enemy F_T774 = foe("foe.t774", ShipCatalog.EMPIRE_T774,
            "帝国的年型突击舰：手册只有一句“往前开，开炮”。");
    public static final Enemy F_T771 = foe("foe.t771", ShipCatalog.EMPIRE_T771,
            "年型要塞舰，当年焊死在边境，现在焊死在你的航线上。");
    public static final Enemy F_GREYHARBOR = foe("foe.greyharbor", ShipCatalog.REP_GREYHARBOR,
            "共和国四十艘全叫灰港的商船之一（编号不详）。");
    public static final Enemy F_WHITEBAY = foe("foe.whitebay", ShipCatalog.REP_WHITEBAY,
            "先装炮后焊船的产物，观测数据共享给周围。");
    public static final Enemy F_AURELIA = foe("foe.aurelia", ShipCatalog.DYN_AURELIA,
            "第十九世奥蕾莉亚。前十八世都沉在自己人手里，它想换个死法。");
    public static final Enemy F_VERONICA = foe("foe.veronica", ShipCatalog.DYN_VERONICA,
            "圣女维罗妮卡级：专捞那些用圣女名字命名的船。");
    public static final Enemy F_VX2C = foe("foe.vx2c", ShipCatalog.COMB_VX2C,
            "全称四行的干扰艇，商联自己只记得 VX。");
    public static final Enemy F_MK3 = foe("foe.mk3", ShipCatalog.COMB_MK3,
            "四个阵营的残骸拼的，焊接处有三种语言的使用警告。");

    private static final List<Enemy> ROUTE = List.of(F_SCOUT, F_MISSILE, F_CRUISER, F_MAIN, F_HEAVY);

    private static final Map<String, Enemy> BY_ID = buildIndex();

    /** 一共几关。 */
    public static int routeLength() {
        return ROUTE.size();
    }

    /** 整条路线（最后一场是旗舰）。 */
    public static List<Enemy> route() {
        return ROUTE;
    }

    private static Map<String, Enemy> buildIndex() {
        Map<String, Enemy> map = new LinkedHashMap<>();
        for (Enemy e : allEnemies()) {
            if (map.put(e.id(), e) != null) {
                throw new IllegalStateException("敌舰 id 重复：" + e.id());
            }
        }
        return Map.copyOf(map);
    }

    private static List<Enemy> allEnemies() {
        return List.of(F_SCOUT, F_BROAD, F_THRUST, F_MAGAZINE, F_RADAR, F_FLAK, F_EW, F_OBSERVER,
                F_MISSILE, F_MAIN, F_BELT, F_LANCE, F_REPAIR, F_LINK, F_SUPPLY,
                F_CRUISER, F_HEAVY, F_REACTIVE, F_DEFLECT, F_FIRE);
    }

    /**
     * 第 {@code node} 关出场的编队（8/8/9/10/12 艘，同关舰图不重复）。
     *
     * <p>难度结构：船的总血量与玩家舰队同一量级（起始 ~126，终局 ~240），
     * 逐关加的是<b>质量</b>（装甲墙、重炮、光环协同）而不是数值通胀。
     * 敌方每回合 ~2-3 张牌（能量与我方同池），船多撑的是血肉与光环，不是火力翻倍。</p>
     */
    public static List<Enemy> squad(int node, long seed) {
        return squad(node, seed, -1);
    }

    /**
     * 太阳系舰队：五国联合封锁编队（核心入场挑战）。
     * 五阵营各出一艘 FAME5/T5 脸面舰 + 2~3 艘 T3~T4 精英副炮。
     * 总白值远超普通波次——这是全游戏最强的非 Boss 编队。
     */
    public static List<Enemy> gateSquad(long seed) {
        // 五阵营 T5 脸面舰（id 固定）
        String[] faces = {"leg.emp", "leg.eagle", "leg.north", "leg.sakura", "leg.south"};
        List<Ship> picked = new ArrayList<>();
        for (String id : faces) {
            Ship s = ShipCatalog.byId(id);
            if (s != null) {
                picked.add(s);
            }
        }
        // 补 2~3 艘 T3~T4 副炮/装甲（从所有非旗非 T5 船里挑 tier >= 3 的）
        Random r = new Random(seed ^ 0x6A7E2L);
        var fillers = ShipCatalog.all().stream()
                .filter(s -> s.tier() >= 3 && s.tier() <= 4
                        && !picked.contains(s) && s.cardCount() > 0)
                .collect(java.util.stream.Collectors.toList());
        java.util.Collections.shuffle(fillers, r);
        for (int i = 0; i < Math.min(3, fillers.size()); i++) {
            picked.add(fillers.get(i));
        }
        List<Enemy> out = new ArrayList<>(picked.size());
        int hp = 0;
        for (int i = 0; i < picked.size(); i++) {
            Ship hull = picked.get(i);
            out.add(new Enemy("gate." + hull.id(), hull.nameZh(), hull.nameEn(),
                    hull.hp(), hull, null, hull.textZh()));
        }
        return List.copyOf(out);
    }

    /** 带星区势力的波次生成：nodeTheta 用于匹配当前星区的势力主题。 */
    public static List<Enemy> squad(int node, long seed, double nodeTheta) {
        List<Ship> hulls = pickWave(Math.max(0, Math.min(SPECS.length - 1, node)), seed, nodeTheta);
        List<Enemy> out = new ArrayList<>(hulls.size());
        for (Ship hull : hulls) {
            out.add(new Enemy("wave." + hull.id(), hull.nameZh(), hull.nameEn(), hull.hp(),
                    hull, null, hull.textZh()));
        }
        return List.copyOf(out);
    }

    /**
     * 单舰模板战力：与玩家舰队同一口径（血量 + 防御×3 + 护盾×2 + 齐射×3，不算卡牌）。
     * 不含光环与摆位——那些是编队层面的额外价值。
     */
    public static int shipPower(Ship ship) {
        return (int) Math.round(ship.hp() * 0.3) + ship.plate() * 3 + ship.armor() * 2
                + ship.volley() * 3;
    }

    /** 一艘敌舰的实际战力（结构值可能按玩家成长补过）。 */
    public static int enemyPower(Enemy enemy) {
        Ship hull = enemy.hull();
        return (int) Math.round(enemy.hp() * 0.3) + hull.plate() * 3 + hull.armor() * 2
                + hull.volley() * 3;
    }

    /** 编队总战力。 */
    public static int squadPower(List<Enemy> squad) {
        int sum = 0;
        for (Enemy enemy : squad) {
            sum += enemyPower(enemy);
        }
        return sum;
    }

    /**
     * 按玩家战力生成战斗编队：普通节点目标 = 玩家战力×1.1，精英 = ×1.3。
     *
     * <p>从船池里挑不重复的船凑目标战力；池子凑不够时按比例补结构值（最多翻倍），
     * 保证后期玩家变强后编队不是纸糊的。主题势力仍然保留：同一星区优先同势力船。</p>
     */
    public static List<Enemy> squadForPower(int targetPower, long seed, double nodeTheta, boolean elite) {
        return squadForPower(targetPower, seed, nodeTheta, elite, 12);
    }

    /**
     * 同上，但限制敌舰数量——战斗波次用：第一波只来几艘侦察舰。
     *
     * @param maxShips 本波最多几艘（2..12，超出会被夹住）
     */
    public static List<Enemy> squadForPower(int targetPower, long seed, double nodeTheta, boolean elite,
                                            int maxShips) {
        int cap = Math.max(2, Math.min(12, maxShips));
        int want = Math.max(2, Math.min(cap, (int) Math.round(targetPower / 60.0)));
        Random rng = new Random(seed ^ 0x9E2F1L);
        List<Ship> pool = ShipCatalog.all().stream()
                .filter(s -> s.cardCount() > 0)
                .filter(s -> s.cells() <= 20)
                .collect(Collectors.toList());
        if (pool.size() < want) {
            want = Math.max(2, Math.min(cap, pool.size()));
        }
        final int count = want;

        String theme = null;
        Map<String, List<Ship>> byFaction = pool.stream()
                .collect(Collectors.groupingBy(FactionCatalog::factionOf));
        List<String> themeable = byFaction.entrySet().stream()
                .filter(e -> e.getValue().size() >= count)
                .map(Map.Entry::getKey).sorted().toList();
        if (!themeable.isEmpty()) {
            if (nodeTheta >= 0) {
                String zone = FactionCatalog.zoneOf(nodeTheta);
                for (String faction : themeable) {
                    if (faction.equals(zone)) {
                        theme = faction;
                        break;
                    }
                }
            }
            if (theme == null && !elite && rng.nextInt(100) < 40) {
                theme = themeable.get(rng.nextInt(themeable.size()));
            }
        }

        List<Ship> best = null;
        int bestErr = Integer.MAX_VALUE;
        for (int attempt = 0; attempt < 128; attempt++) {
            Collections.shuffle(pool, rng);
            List<Ship> ordered = new ArrayList<>(pool.size());
            if (theme != null) {
                for (Ship ship : pool) {
                    if (FactionCatalog.factionOf(ship).equals(theme)) {
                        ordered.add(ship);
                    }
                }
                for (Ship ship : pool) {
                    if (!ordered.contains(ship)) {
                        ordered.add(ship);
                    }
                }
            } else {
                ordered.addAll(pool);
            }
            List<Ship> picked = new ArrayList<>();
            int area = 0;
            int power = 0;
            for (Ship ship : ordered) {
                if (picked.size() >= want) {
                    break;
                }
                if (area + ship.cells() > 210) {
                    continue;
                }
                picked.add(ship);
                area += ship.cells();
                power += shipPower(ship);
            }
            // 波次会要求更小的编队（第一波只有几艘侦察舰），下限跟着 want 走，别再死守 6 艘
            if (picked.size() < Math.min(6, cap)) {
                continue;
            }
            int err = Math.abs(power - targetPower) + 60 * Math.abs(picked.size() - want);
            if (err < bestErr) {
                bestErr = err;
                best = picked;
            }
        }
        if (best == null) {
            best = new ArrayList<>(pool.subList(0, Math.min(Math.min(6, cap), pool.size())));
        }

        int basePower = 0;
        double hullPower = 0;
        for (Ship ship : best) {
            basePower += shipPower(ship);
            hullPower += ship.hp() * 0.3;
        }
        // 血量是唯一能无损缩放的部分：把结构值调到让总战力贴近目标（结构按 0.3 权重）。
        double otherPower = basePower - hullPower;
        double scale = hullPower <= 0 ? 1.0
                : (targetPower - otherPower) / hullPower;
        scale = Math.max(0.5, Math.min(2.5, scale));
        List<Enemy> out = new ArrayList<>(best.size());
        for (Ship hull : best) {
            int hp = (int) Math.round(hull.hp() * scale);
            hp = Math.max(1, hp);
            out.add(new Enemy("wave." + hull.id(), hull.nameZh(), hull.nameEn(), hp,
                    hull, null, hull.textZh()));
        }
        return List.copyOf(out);
    }

    /**
     * 一关的编队规格：船数、单舰血量带、目标总血量、单舰占地上限、装甲墙数量下限。
     * 目标总血量就是这一关的难度旋钮——对称口径下不涨单船数值，只涨编队总量。
     */
    private record WaveSpec(int count, int minHp, int maxHp, int targetEhp, int maxCells,
                            int minWalls, int maxThreat, boolean themed) {
        /** 目标按编队总 EHP（血 + 防御×3 + 盾×2）计——墙和盾在对称口径下都是真肉。
         *  themed = 同势力编组开关（教学关关掉）。 */
    }

    private static final WaveSpec[] SPECS = {
            new WaveSpec(9, 4, 18, 230, 14, 0, 45, false),
            new WaveSpec(9, 4, 44, 290, 16, 1, 60, true),
            new WaveSpec(10, 4, 70, 340, 16, 1, 65, true),
            new WaveSpec(10, 4, 120, 420, 20, 1, 90, true),
            new WaveSpec(12, 4, 180, 480, 25, 1, 105, true),
    };

    /**
     * 从 116 艘船池按血量带随机抽取（种子确定性）：同关舰图不重复、
     * 装甲墙数量恰为规格下限、半数以上带攻击卡、总占地塞得进棋盘、
     * 总血量尽量贴近目标带。每一局遇到的敌人都不一样，百余艘船都会在某个种子轮出现——也就都能被俘获。
     */
    private static List<Ship> pickWave(int node, long seed) {
        return pickWave(node, seed, -1);
    }

    private static List<Ship> pickWave(int node, long seed, double nodeTheta) {
        WaveSpec spec = SPECS[node];
        Random rng = new Random(seed ^ 0x5EEDL);
        List<Ship> pool = ShipCatalog.all().stream()
                .filter(s -> s.cardCount() > 0)
                .filter(s -> s.rawHp() >= spec.minHp() && s.rawHp() <= spec.maxHp())
                .filter(s -> s.cells() <= spec.maxCells())
                .collect(Collectors.toList());
        // 两段式：先把装甲墙抽出来（洗牌后墙排在尾部会被非墙船抢满位置），
        // 再从其余船里补齐名额——墙在场是波次质感的下限保障。
        List<Ship> wallPool = new ArrayList<>(pool.stream()
                .filter(s -> s.role() == ShipRole.ARMOR).toList());
        List<Ship> otherPool = new ArrayList<>(pool.stream()
                .filter(s -> s.role() != ShipRole.ARMOR).toList());
        // 同势力编组：池子里有足够同势力船时 ~40% 概率主题化——"遭遇某国分舰队"
        Map<String, List<Ship>> byFaction = pool.stream()
                .collect(Collectors.groupingBy(FactionCatalog::factionOf));
        String theme = null;
        List<String> themeable = byFaction.entrySet().stream()
                .filter(e -> e.getValue().size() >= spec.count())
                .map(Map.Entry::getKey).sorted().toList();
        if (spec.themed() && !themeable.isEmpty()) {
            // 星区势力优先：当前节点落在哪个势力的扇区，波次就主题化为该势力
            if (nodeTheta >= 0) {
                String zoneName = com.hysx.salvosequence.fleet.FactionCatalog.zoneOf(nodeTheta);
                for (String fac : themeable) {
                    if (fac.equals(zoneName)) { theme = fac; break; }
                }
            }
            if (theme == null && rng.nextInt(100) < 45) {
                theme = themeable.get(rng.nextInt(themeable.size()));
            }
        }

        List<Ship> best = null;
        int bestErr = Integer.MAX_VALUE;
        for (int attempt = 0; attempt < 96 && bestErr > 0; attempt++) {
            Collections.shuffle(wallPool, rng);
            Collections.shuffle(otherPool, rng);
            List<Ship> fillPool = otherPool;
            if (theme != null) {
                // 主题波：非墙位优先填主题势力，不足用无所属补
                List<Ship> themed = byFaction.getOrDefault(theme, List.of());
                List<Ship> neutral = byFaction.getOrDefault("破障者船团", List.of());
                fillPool = new ArrayList<>();
                for (Ship s : otherPool) {
                    if (FactionCatalog.factionOf(s).equals(theme)) {
                        fillPool.add(s);
                    }
                }
                for (Ship s : neutral) {
                    if (s.role() != ShipRole.ARMOR && !fillPool.contains(s)) {
                        fillPool.add(s);
                    }
                }
            }
            List<Ship> picked = new ArrayList<>(wallPool.subList(0, Math.min(spec.minWalls(), wallPool.size())));
            int ehp = 0;
            int area = 0;
            int attackers = 0;
            int threat = 0;
            for (Ship ship : picked) {
                ehp += ehpOf(ship);
                area += ship.cells();
                threat += bestStrike(ship);
            }
            for (Ship ship : fillPool) {
                if (picked.size() == spec.count()) {
                    break;
                }
                picked.add(ship);
                ehp += ehpOf(ship);
                area += ship.cells();
                threat += bestStrike(ship);
                if (hasAttack(ship)) {
                    attackers++;
                }
            }
            // 硬约束只留船数与占地；威胁/攻击手/墙不足都算软罚——约束联立死锁时总能出解
            if (area > 210) {
                continue;
            }
            int err = Math.abs(ehp - spec.targetEhp())
                    + 15 * Math.max(0, threat - spec.maxThreat())
                    + 30 * Math.max(0, spec.minWalls() - wallsOf(picked))
                    + 8 * Math.max(0, picked.size() / 3 - attackers);
            if (err < bestErr) {
                bestErr = err;
                best = picked;
            }
        }
        if (best == null) {
            throw new IllegalStateException("第 " + (node + 1) + " 关抽不出合格编队（池 " + pool.size() + " 艘）");
        }
        return best;
    }

    /** 这艘船最强一击的裸面板（卡面 × 段数，不含倍率/光环——对称口径）。 */
    private static int bestStrike(Ship ship) {
        int best = 0;
        for (String cardId : ship.cardIds()) {
            best = Math.max(best, CardCatalog.byId(cardId).baseDamage());
        }
        return best;
    }

    /** 等效血量：血 + 防御×3 + 盾×2（防御/盾在对称口径下都是真肉）。 */
    private static int ehpOf(Ship ship) {
        return ship.hp() + ship.plate() * 3 + ship.armor() * 2;
    }

    private static int wallsOf(List<Ship> picked) {
        int n = 0;
        for (Ship ship : picked) {
            if (ship.role() == ShipRole.ARMOR) {
                n++;
            }
        }
        return n;
    }

    private static boolean hasAttack(Ship ship) {
        for (String cardId : ship.cardIds()) {
            if (CardCatalog.byId(cardId).baseDamage() > 0) {
                return true;
            }
        }
        return false;
    }

    /** 每关最多几艘。 */
    public static int maxSquadSize() {
        int max = 0;
        for (WaveSpec spec : SPECS) {
            max = Math.max(max, spec.count());
        }
        return max;
    }

    /** 按 id 找敌舰。 */
    public static Enemy byId(String id) {
        Enemy enemy = BY_ID.get(id);
        if (enemy == null) {
            throw new IllegalArgumentException("没有这艘敌舰：" + id);
        }
        return enemy;
    }

    /** 供界面预览：某关的编队里的船（还没摆位；固定示例种子，只保证格式合法）。 */
    public static List<Ship> shipsOf(int node) {
        return squad(node, node * 7919L + 7).stream().map(Enemy::image).toList();
    }
}
