package com.hysx.salvosequence.fleet;

import java.util.List;
import java.util.Locale;

/**
 * 一艘船的定义（改造前的 {@code Component}「模块」）。
 *
 * <p>改造前：一个模块装进船体上的一块<b>固定分区</b>，形状必须塞进那个分区，
 * 每个分区只能装一件，所以"模块"其实更像插件。</p>
 *
 * <p>改造后：一艘船就是舰队棋盘上的一个<b>实体</b>——它自己占地（{@link #w()}×{@link #h()} 格）、
 * 自己的结构值、自己的齐射值、自己的涂装。落位不再受分区限制，只要不出界、不压别的船。</p>
 *
 * <p>卡片仍然挂在船上（一艘船带哪几张卡，它就在编队里打哪几张牌），
 * 伤害倍率按<b>携带该卡的船数与等级</b>叠加，见 {@link Fleet#multiplierOf(String)}。</p>
 *
 * @param id      唯一 id（沿用旧模块 id，存档兼容：{@code core.command} 等）
 * @param nameZh  中文名（一个<b>舰名</b>，如"主炮舰"）
 * @param nameEn  英文名
 * @param role    职能：显示配色与相邻加成用，不限制落位
 * @param w       占地宽（格）
 * @param h       占地高（格）
 * @param hp      结构值（体层：最后一池，扣光 = 沉没）
 * @param armor   护盾（每回合刷新的抵挡值，唯一的自动回层）
 * @param plate   防御力（每段伤害先减这一格，最低 1；穿甲 = 攻击高，没有特殊机制）
 * @param flak    防空（受击侧全队之和，把对方多段攻击的生效段数砍掉，最低 1 段）
 * @param volley  齐射：这艘船为"它带的卡"贡献的基础伤害
 * @param energy  额外能量（每能量点回满一次）
 * @param draw    额外抽牌
 * @param cardIds 这艘船带的卡
 * @param perks   光环（背包英雄式叠加：按生效范围把加成叠到范围内的船上，含自己/邻船/同排/全队）
 * @param tier    稀有度（0 = 起始指挥舰，越大越难得，掉落排序用）
 * @param textZh  一句话说明
 */
public record Ship(String id, String nameZh, String nameEn, ShipRole role, int w, int h,
                   int hp, int armor, int plate, int flak, int volley, int energy, int draw,
                   List<String> cardIds, List<Perk> perks, int tier, String textZh) {

    public Ship {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("船必须有 id");
        }
        if (nameZh == null || nameZh.isBlank()) {
            throw new IllegalArgumentException("船 " + id + " 缺中文名");
        }
        if (nameEn == null || nameEn.isBlank()) {
            throw new IllegalArgumentException("船 " + id + " 缺英文名");
        }
        if (role == null) {
            throw new IllegalArgumentException("船 " + id + " 缺职能");
        }
        if (w < 1 || h < 1) {
            throw new IllegalArgumentException("船 " + id + " 占地至少 1x1，实际 " + w + "x" + h);
        }
        if (hp < 1) {
            throw new IllegalArgumentException("船 " + id + " 结构值必须大于 0，实际 " + hp);
        }
        if (armor < 0 || plate < 0 || flak < 0 || volley < 0 || energy < 0 || draw < 0 || tier < 0) {
            throw new IllegalArgumentException("船 " + id + " 的数值不能为负");
        }
        if (cardIds == null) {
            throw new IllegalArgumentException("船 " + id + " 的卡表不能是 null（可以是空表）");
        }
        if (perks == null) {
            throw new IllegalArgumentException("船 " + id + " 的光环表不能是 null（可以是空表）");
        }
        cardIds = List.copyOf(cardIds);
        perks = List.copyOf(perks);
        textZh = textZh == null ? "" : textZh;
    }

    /** 占地格数。 */
    public int cells() {
        return w * h;
    }

    /** 带几张卡。 */
    public int cardCount() {
        return cardIds.size();
    }

    /** 历史字段：指挥舰身份。旗舰已改为普通船，不再限制拆解、掉落或战败条件。 */
    public boolean isFlag() {
        return role == ShipRole.COMMAND;
    }

    /** 是不是炮舰。 */
    public boolean isGun() {
        return role.isGun();
    }

    /** 是不是轴炮件（{@code gun.axial.*}）。 */
    public boolean isAxial() {
        return id.toLowerCase(Locale.ROOT).startsWith("gun.axial");
    }

    /** 无所属制式舰（黑市/海盗/民兵杂牌）：防御比正规军多一层。 */
    public boolean unaffiliated() {
        String k = id.toLowerCase(Locale.ROOT);
        return k.startsWith("core.") || k.startsWith("gun.") || k.startsWith("armor.")
                || k.startsWith("engine.") || k.startsWith("tactic.") || k.startsWith("support.")
                || k.startsWith("guard.");
    }

    /** 是不是铁冕帝国年型舰（血量额外再 ×1.5）。 */
    public boolean imperial() {
        return id.toLowerCase(Locale.ROOT).startsWith("empire.");
    }

    /** 结构值：全体翻倍，帝国船在翻倍基础上再 ×1.5（即 ×3）。 */
    @Override
    public int hp() {
        return imperial() ? hp * 3 : hp * 2;
    }

    /** 面板原始结构值（不含全局翻倍），价格/基准换算用。 */
    public int rawHp() {
        return hp;
    }

    /** 防御力：全体 +3；无所属海盗船再 +1。 */
    @Override
    public int plate() {
        return plate + 3 + (unaffiliated() ? 1 : 0);
    }

    /** 是不是轴炮炮管。 */
    public boolean isBarrel() {
        return isAxial() && id.toLowerCase(Locale.ROOT).contains("barrel");
    }

    /** 是不是轴炮炮口（唯一给轴炮卡的地方）。 */
    public boolean isMuzzle() {
        return isAxial() && id.toLowerCase(Locale.ROOT).contains("muzzle");
    }

    /** 换一个中文名（调试/展示用）。 */
    public Ship renamed(String newNameZh) {
        return new Ship(id, newNameZh, nameEn, role, w, h, hp, armor, plate, flak,
                volley, energy, draw, cardIds, perks, tier, textZh);
    }

    /** 按等级长出来的结构值：Lv1 = 原值，每升一级加一半。 */
    public int hpAtLevel(int level) {
        int lv = Math.max(1, level);
        return Math.max(1, hp() * (lv + 1) / 2);
    }

    /** 按等级长出来的齐射值：每级 +2。 */
    public int volleyAtLevel(int level) {
        return volley + Math.max(0, Math.max(1, level) - 1) * 2;
    }

    /** 按等级长出来的护盾：每级 +1。 */
    public int armorAtLevel(int level) {
        return armor + Math.max(0, Math.max(1, level) - 1);
    }

    @Override
    public String toString() {
        return "Ship[" + id + " " + nameZh + " " + w + "x" + h + " hp" + hp + "]";
    }
}
