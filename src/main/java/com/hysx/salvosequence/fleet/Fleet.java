package com.hysx.salvosequence.fleet;

import com.hysx.salvosequence.deck.Card;
import com.hysx.salvosequence.deck.CardCatalog;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 一支舰队：棋盘 + 派生数值 + 相邻规则。
 *
 * <p>改造前这套东西分在 {@code ShipLoadout}（模块/等级/倍率/能量/抽牌/相邻加成）
 * 和 {@code FleetFormation}（僚机摆位）两处，一个是"船"、一个是"护航的舰"。
 * 改造后合成一个：<b>舰队就是船的全部</b>，没有船体、没有分区、没有僚机。</p>
 *
 * <p>规则来源与改造前的对应关系：</p>
 * <ul>
 *   <li>能量 = {@link #BASE_ENERGY} + Σ(船的额外能量)，抽牌 = {@link #BASE_DRAW} + Σ(船的额外抽牌)</li>
 *   <li>倍率 = Σ(携带该卡的船的等级)——旧模型里"同名件 → Lv+1 → 伤害×等级"</li>
 *   <li>齐射 = Σ(携带该卡的船的齐射值)，旧模型里是"参与舰的 volley"，现在每艘船都算参与者</li>
 *   <li>轴炮倍率 = 管数 + 口数 + 相接对 + (管口都有 ? 1 : 0)，与旧 {@code ShipLoadout.axialMultiplier()} 同公式</li>
 *   <li>相邻：装甲舰贴指挥舰 → +结构值；炮舰贴装甲舰 → +齐射；炮舰贴动力舰 → +校射</li>
 * </ul>
 *
 * <p>不可变：所有修改都返回新舰队。</p>
 */
public final class Fleet {

    /** 基础能量。 */
    public static final int BASE_ENERGY = 3;

    /** 基础抽牌。 */
    public static final int BASE_DRAW = 5;

    /** 船给的额外能量最多叠这么多。 */
    public static final int ENERGY_CAP = 4;

    /** 船给的额外抽牌最多叠这么多。 */
    public static final int DRAW_CAP = 3;

    /** 轴炮倍率上限。 */
    public static final int AXIAL_MULT_CAP = 6;

    /** 贴装甲的伤害加成，全队上限。 */
    public static final int ADJACENT_SPOT_CAP = 3;

    private final String id;
    private final String nameZh;
    private final String nameEn;
    private final FleetBoard board;
    private final List<Card> bonusCards;

    private Fleet(String id, String nameZh, String nameEn, FleetBoard board, List<Card> bonusCards) {
        this.id = id;
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.board = board;
        this.bonusCards = List.copyOf(bonusCards);
    }

    /** 建一支舰队（会校验棋盘几何）。 */
    public static Fleet of(String id, String nameZh, String nameEn, List<FleetUnit> units) {
        return of(id, nameZh, nameEn, FleetBoard.of(units), List.of());
    }

    /** 建一支舰队（自带棋盘与额外卡）。 */
    public static Fleet of(String id, String nameZh, String nameEn, FleetBoard board, List<Card> bonusCards) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("舰队必须有 id");
        }
        if (board == null) {
            throw new IllegalArgumentException("舰队必须有棋盘");
        }
        return new Fleet(id, nameZh == null ? "" : nameZh, nameEn == null ? "" : nameEn,
                board, bonusCards == null ? List.of() : bonusCards);
    }

    /**
     * 把一堆"还没摆好"的船自动排进棋盘：面积大的先摆，从上往下、从左往右铺。
     *
     * <p>起始舰队和新建存档都走这条路——分工明确：这里只求"摆得下且好看"，
     * 真正的位置由玩家在「舰队编排」里改。</p>
     *
     * @throws IllegalArgumentException 摆不下（棋盘 256 格不够）
     */
    public static Fleet autoArrange(String id, String nameZh, String nameEn, List<FleetUnit> loose) {
        List<FleetUnit> order = new ArrayList<>(loose);
        order.sort((a, b) -> b.cells() != a.cells() ? Integer.compare(b.cells(), a.cells())
                : a.id().compareTo(b.id()));
        List<FleetUnit> placed = new ArrayList<>(order.size());
        for (FleetUnit unit : order) {
            boolean done = false;
            for (int y = 0; y + unit.h() <= FleetBoard.SIZE && !done; y++) {
                for (int x = 0; x + unit.w() <= FleetBoard.SIZE && !done; x++) {
                    FleetUnit candidate = unit.at(x, y);
                    if (FleetBoard.of(placed).canPlace(candidate)) {
                        placed.add(candidate);
                        done = true;
                    }
                }
            }
            if (!done) {
                throw new IllegalArgumentException("「" + unit.nameZh() + "」摆不下：棋盘没有空位了");
            }
        }
        return of(id, nameZh, nameEn, FleetBoard.of(placed), List.of());
    }

    /** 舰队 id。 */
    public String id() {
        return id;
    }

    /** 中文名。 */
    public String nameZh() {
        return nameZh;
    }

    /** 英文名。 */
    public String nameEn() {
        return nameEn;
    }

    /** 改名（起始舰队从"炮术舰队"改成玩家手上的名字时用）。 */
    public Fleet renamed(String newNameZh, String newNameEn) {
        return new Fleet(id, newNameZh, newNameEn, board, bonusCards);
    }

    /** 棋盘。 */
    public FleetBoard board() {
        return board;
    }

    /** 棋盘上的船。 */
    public List<FleetUnit> units() {
        return board.units();
    }

    /** 船数。 */
    public int size() {
        return board.units().size();
    }

    /** 有没有船。 */
    public boolean isEmpty() {
        return board.isEmpty();
    }

    /** 按船型 id 找棋子。 */
    public FleetUnit unit(String shipId) {
        return board.byShip(shipId);
    }

    /** 盖住某格的那艘船。 */
    public FleetUnit at(int cx, int cy) {
        return board.at(cx, cy);
    }

    /** 代表舰（旧旗舰概念；旗舰现在是普通船，仅供旧技能兜底）。 */
    public FleetUnit flag() {
        for (FleetUnit u : board.units()) {
            if (u.isFlag()) {
                return u;
            }
        }
        List<FleetUnit> order = board.soakOrder();
        return order.isEmpty() ? null : order.get(0);
    }

    /** 额外卡（奖励直接塞进卡组、不挂在任何船上的那些）。 */
    public List<Card> bonusCards() {
        return bonusCards;
    }

    // ---------------------------------------------------------------- 数值

    /**
     * 落在 {@code target} 上的全部光环总量：遍历全队每艘船的光环，按各自生效范围匹配。
     *
     * <p>这是属性叠加系统的聚合点——派生数值（血/盾/防御/防空/齐射）与卡牌加成
     * （火控/弹夹/盾链/损管）都从这份总量读。敌我同一套公式。</p>
     */
    public Perk.Totals perksOn(FleetUnit target) {
        return perksOn(target, null);
    }

    /**
     * 同上，但只统计 {@code aliveIds} 里的船——击沉即断链：沉了的船不再提供光环。
     * {@code aliveIds} 为 null 表示不过滤（战前构筑/展示用）。
     */
    public Perk.Totals perksOn(FleetUnit target, java.util.Set<String> aliveIds) {
        Perk.Totals totals = Perk.Totals.NONE;
        for (FleetUnit source : board.units()) {
            if (aliveIds != null && !aliveIds.contains(source.id())) {
                continue;
            }
            for (Perk perk : source.ship().perks()) {
                if (FleetBoard.inRange(source, target, perk.range())) {
                    totals = totals.add(perk);
                }
            }
        }
        return totals;
    }

    /** 全队范围的光环总量（能量/过牌这类"编队资源"加成）。 */
    public Perk.Totals fleetWidePerks() {
        return fleetWidePerks(null);
    }

    /** 同上，带存活过滤（击沉即断链）。 */
    public Perk.Totals fleetWidePerks(java.util.Set<String> aliveIds) {
        Perk.Totals totals = Perk.Totals.NONE;
        for (FleetUnit source : board.units()) {
            if (aliveIds != null && !aliveIds.contains(source.id())) {
                continue;
            }
            for (Perk perk : source.ship().perks()) {
                if (perk.range() == Perk.Range.FLEET) {
                    totals = totals.add(perk);
                }
            }
        }
        return totals;
    }

    /** 这一艘的结构值（基础 + 光环）。 */
    public int maxHpOf(FleetUnit unit) {
        return unit.maxHp() + perksOn(unit).hp();
    }

    /** 这一艘的护盾（基础 + 光环）。 */
    public int armorOf(FleetUnit unit) {
        return unit.armor() + perksOn(unit).shield();
    }

    /** 这一艘的防御力（基础 + 光环）。 */
    public int plateOf(FleetUnit unit) {
        return unit.ship().plate() + perksOn(unit).plate();
    }

    /** 这一艘的防空（基础 + 光环）。 */
    public int flakOf(FleetUnit unit) {
        return unit.ship().flak() + perksOn(unit).flak();
    }

    /** 这一艘的齐射（基础 + 光环）。 */
    public int volleyOf(FleetUnit unit) {
        return unit.volley() + perksOn(unit).volley();
    }

    /** 这艘船每回合开局被损管修多少（0 = 没有损管光环罩着它）。 */
    public int repairOn(FleetUnit unit) {
        return perksOn(unit).repair();
    }

    /** 同上，带存活过滤（击沉即断链）。 */
    public int repairOn(FleetUnit unit, java.util.Set<String> aliveIds) {
        return perksOn(unit, aliveIds).repair();
    }

    /** 这艘船的护盾连接量：护盾卡结算时它额外拿多少。 */
    public int shieldLinkOn(FleetUnit unit) {
        return perksOn(unit).shieldLink();
    }

    /** 同上，带存活过滤（击沉即断链）。 */
    public int shieldLinkOn(FleetUnit unit, java.util.Set<String> aliveIds) {
        return perksOn(unit, aliveIds).shieldLink();
    }

    /**
     * 兼容读数：这艘船<b>收到</b>的光环，折算成旧的三元组（齐射/护盾/结构）。
     * 编排界面与落点评分还在用；新代码请直接读 {@link #perksOn}。
     */
    public FleetBonus bonusOf(FleetUnit unit) {
        Perk.Totals totals = unit == null ? Perk.Totals.NONE : perksOn(unit);
        return new FleetBonus(totals.volley(), totals.shield(), totals.hp());
    }

    /** 带这张卡的船装填能缩短多少（弹夹光环叠在携带者身上）。 */
    public int magazineFor(String cardId) {
        int sum = 0;
        for (FleetUnit u : carriers(cardId)) {
            sum += perksOn(u).magazine();
        }
        return sum;
    }

    /** 全队结构值合计。 */
    public int maxHp() {
        int sum = 0;
        for (FleetUnit u : board.units()) {
            sum += maxHpOf(u);
        }
        return sum;
    }

    /** 全队护盾合计。 */
    public int armor() {
        int sum = 0;
        for (FleetUnit u : board.units()) {
            sum += armorOf(u);
        }
        return sum;
    }

    /** 全队齐射合计。 */
    public int volley() {
        int sum = 0;
        for (FleetUnit u : board.units()) {
            sum += volleyOf(u);
        }
        return sum;
    }

    /** 每回合能量上限：基础 + 船的额外能量 + 全队光环，封顶 {@link #ENERGY_CAP}。 */
    public int maxEnergy() {
        return maxEnergy(null);
    }

    /** 同上，带存活过滤（击沉即断链：沉船的供能光环失效）。 */
    public int maxEnergy(java.util.Set<String> aliveIds) {
        int extra = 0;
        for (FleetUnit u : board.units()) {
            if (aliveIds != null && !aliveIds.contains(u.id())) {
                continue;
            }
            extra += u.ship().energy();
        }
        return BASE_ENERGY + Math.min(ENERGY_CAP, extra + fleetWidePerks(aliveIds).energy());
    }

    /** 每回合抽牌数：基础 + 船的额外抽牌 + 全队光环，封顶 {@link #DRAW_CAP}。 */
    public int handSize() {
        return handSize(null);
    }

    /** 同上，带存活过滤（击沉即断链）。 */
    public int handSize(java.util.Set<String> aliveIds) {
        int extra = 0;
        for (FleetUnit u : board.units()) {
            if (aliveIds != null && !aliveIds.contains(u.id())) {
                continue;
            }
            extra += u.ship().draw();
        }
        return BASE_DRAW + Math.min(DRAW_CAP, extra + fleetWidePerks(aliveIds).draw());
    }

    // ---------------------------------------------------------------- 卡与倍率

    /** 带这张卡的船（按棋盘顺序）。 */
    public List<FleetUnit> carriers(String cardId) {
        return carriers(cardId, null);
    }

    /** 同上，带存活过滤（击沉即断链：沉船不再响应它的卡）。 */
    public List<FleetUnit> carriers(String cardId, java.util.Set<String> aliveIds) {
        List<FleetUnit> out = new ArrayList<>(2);
        for (FleetUnit u : board.units()) {
            if (aliveIds != null && !aliveIds.contains(u.id())) {
                continue;
            }
            if (u.carries(cardId)) {
                out.add(u);
            }
        }
        return out;
    }

    /** 舰队能出哪些卡（按船序 + 卡序去重）。 */
    public List<String> cardIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (FleetUnit u : board.units()) {
            ids.addAll(u.ship().cardIds());
        }
        for (Card c : bonusCards) {
            ids.add(c.id());
        }
        return List.copyOf(ids);
    }

    /** 卡组（卡 id → 卡对象，认不出的卡跳过）。 */
    public List<Card> deck() {
        List<Card> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String cardId : cardIds()) {
            Card card = CardCatalog.byId(cardId);
            if (card != null && seen.add(card.id())) {
                out.add(card);
            }
        }
        return List.copyOf(out);
    }

    /** 卡组大小。 */
    public int deckSize() {
        return deck().size();
    }

    /**
     * 这张卡的伤害倍率。
     *
     * <p>轴炮卡走另一条公式：{@link #axialMultiplier()}（管+口+相接对+成对）。
     * 其余卡 = 携带它的船的等级之和。</p>
     *
     * <p>一张卡至少 ×1（额外卡没有携带者）；两艘同型炮舰 = 各 Lv1 → ×2；
     * 一艘 Lv3 的主炮舰 → ×3。改造前是"同名件 Lv+1 → ×等级"，语义一致。</p>
     */
    public int multiplierOf(String cardId) {
        return multiplierOf(cardId, null);
    }

    /** 同上，带存活过滤（击沉即断链）。 */
    public int multiplierOf(String cardId, java.util.Set<String> aliveIds) {
        if (cardId != null && cardId.startsWith("axial.")) {
            return axialMultiplier(aliveIds);
        }
        int sum = 0;
        for (FleetUnit u : carriers(cardId, aliveIds)) {
            sum += u.level();
        }
        return Math.max(1, sum);
    }

    /** 出这张卡时"开火"的船的齐射合计（含相邻加成）。 */
    public int volleyFor(String cardId) {
        int sum = 0;
        for (FleetUnit u : carriers(cardId)) {
            sum += volleyOf(u);
        }
        return sum;
    }

    /**
     * 携带这张卡的船从光环里吃到的火控加成（攻击卡总伤直接 +，全队封顶 {@link #ADJACENT_SPOT_CAP}）。
     */
    public int adjacencyDamageBonus(String cardId) {
        int bonus = 0;
        for (FleetUnit u : carriers(cardId)) {
            bonus += perksOn(u).spot();
        }
        return Math.min(ADJACENT_SPOT_CAP, bonus);
    }

    /** 全队从光环里拿到的结构值总和（挑落点/报告用）。 */
    public int adjacencyHpBonusTotal() {
        int sum = 0;
        for (FleetUnit u : board.units()) {
            sum += perksOn(u).hp();
        }
        return sum;
    }

    /** 全队从光环里拿到的齐射总和（挑落点/报告用）。 */
    public int adjacencyVolleyBonusTotal() {
        int sum = 0;
        for (FleetUnit u : board.units()) {
            sum += perksOn(u).volley();
        }
        return sum;
    }

    /** 全队从光环里拿到的护盾总和。 */
    public int adjacencyArmorBonusTotal() {
        int sum = 0;
        for (FleetUnit u : board.units()) {
            sum += perksOn(u).shield();
        }
        return sum;
    }

    // ---------------------------------------------------------------- 轴炮

    /** 棋盘上有几艘轴炮炮管舰。 */
    public int axialBarrels() {
        return axialBarrels(null);
    }

    /** 同上，带存活过滤（击沉即断链）。 */
    public int axialBarrels(java.util.Set<String> aliveIds) {
        int n = 0;
        for (FleetUnit u : board.units()) {
            if (aliveIds != null && !aliveIds.contains(u.id())) {
                continue;
            }
            if (u.ship().isBarrel()) {
                n++;
            }
        }
        return n;
    }

    /** 棋盘上有几艘轴炮炮口舰。 */
    public int axialMuzzles() {
        return axialMuzzles(null);
    }

    /** 同上，带存活过滤（击沉即断链）。 */
    public int axialMuzzles(java.util.Set<String> aliveIds) {
        int n = 0;
        for (FleetUnit u : board.units()) {
            if (aliveIds != null && !aliveIds.contains(u.id())) {
                continue;
            }
            if (u.ship().isMuzzle()) {
                n++;
            }
        }
        return n;
    }

    /** 管口相接的对数（贴着的炮管—炮口对，上限 2）。 */
    public int axialLinkPairs() {
        return axialLinkPairs(null);
    }

    /** 同上，带存活过滤（击沉即断链）。 */
    public int axialLinkPairs(java.util.Set<String> aliveIds) {
        int pairs = 0;
        for (FleetUnit barrel : board.units()) {
            if (aliveIds != null && !aliveIds.contains(barrel.id())) {
                continue;
            }
            if (!barrel.ship().isBarrel()) {
                continue;
            }
            for (FleetUnit near : board.neighbours(barrel)) {
                if (near.ship().isMuzzle() && (aliveIds == null || aliveIds.contains(near.id()))) {
                    pairs++;
                }
            }
        }
        return Math.min(2, pairs);
    }

    /** 轴炮倍率：管 + 口 + 相接对 + (管口都有 ? 1 : 0)，夹在 1..{@link #AXIAL_MULT_CAP}。 */
    public int axialMultiplier() {
        return axialMultiplier(null);
    }

    /** 同上，带存活过滤（击沉即断链）。 */
    public int axialMultiplier(java.util.Set<String> aliveIds) {
        int barrels = axialBarrels(aliveIds);
        int muzzles = axialMuzzles(aliveIds);
        int raw = barrels + muzzles + axialLinkPairs(aliveIds) + ((barrels > 0 && muzzles > 0) ? 1 : 0);
        return Math.max(1, Math.min(AXIAL_MULT_CAP, raw));
    }

    /** 有没有轴炮件。 */
    public boolean hasAxial() {
        for (FleetUnit u : board.units()) {
            if (u.ship().isAxial()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 摆位

    /** 这型船所有能放下的左上角。 */
    public List<int[]> placementsFor(Ship ship) {
        return board.slots(ship.w(), ship.h());
    }

    /** 这型船是不是哪儿都塞不下。 */
    public boolean canFitAnywhere(Ship ship) {
        return ship != null && !placementsFor(ship).isEmpty();
    }

    /** 加一艘船（同型则替换/合并）。 */
    public Fleet withUnit(FleetUnit unit) {
        return new Fleet(id, nameZh, nameEn, board.replaced(unit), bonusCards);
    }

    /** 去掉一艘船。 */
    public Fleet withoutUnit(String shipId) {
        return new Fleet(id, nameZh, nameEn, board.without(shipId), bonusCards);
    }

    /** 挪一艘船，非法返回 {@code null}。 */
    public Fleet movedUnit(String shipId, int x, int y) {
        FleetBoard moved = board.moved(shipId, x, y);
        return moved == null ? null : new Fleet(id, nameZh, nameEn, moved, bonusCards);
    }

    /** 同型合并一级。 */
    public Fleet upgradedUnit(String shipId) {
        FleetUnit unit = board.byShip(shipId);
        if (unit == null) {
            return this;
        }
        return withUnit(unit.upgraded());
    }

    /** 塞一张额外卡进卡组。 */
    public Fleet withBonusCard(Card card) {
        List<Card> next = new ArrayList<>(bonusCards);
        for (Card c : next) {
            if (c.id().equals(card.id())) {
                return this;
            }
        }
        next.add(card);
        return new Fleet(id, nameZh, nameEn, board, next);
    }

    /** 把棋盘换成另一块（存档/编排界面用）。 */
    public Fleet withBoard(FleetBoard newBoard) {
        return new Fleet(id, nameZh, nameEn, newBoard, bonusCards);
    }

    /** 调试用字符画。 */
    public String ascii() {
        return board.ascii();
    }

    /**
     * 单舰相邻加成。
     *
     * @param volley 齐射加成
     * @param armor  护盾加成
     * @param hp     结构值加成
     */
    public record FleetBonus(int volley, int armor, int hp) {

        /** 没有任何加成。 */
        public static final FleetBonus NONE = new FleetBonus(0, 0, 0);

        /** 有没有加成。 */
        public boolean any() {
            return volley != 0 || armor != 0 || hp != 0;
        }

        /** 面板读数，如 {@code 齐射+1 护盾+1}；没有加成返回空串。 */
        public String labelZh() {
            StringBuilder sb = new StringBuilder();
            if (volley != 0) {
                sb.append("齐射+").append(volley);
            }
            if (armor != 0) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append("护盾+").append(armor);
            }
            if (hp != 0) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append("结构+").append(hp);
            }
            return sb.toString();
        }
    }

    @Override
    public String toString() {
        return "Fleet[" + nameZh + " " + board.units().size() + "舰 hp" + maxHp()
                + " en" + maxEnergy() + " dr" + handSize() + "]";
    }

    /** 调试：把船型 id 列出来。 */
    public String idList() {
        StringBuilder sb = new StringBuilder();
        for (FleetUnit u : board.units()) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(u.id()).append('@').append(u.x()).append(':').append(u.y())
                    .append("x").append(u.level());
        }
        return sb.toString();
    }

    /** 小写化的 id（存档文件名用）。 */
    public static String fileName(String shipId) {
        return shipId.toLowerCase(Locale.ROOT);
    }
}
