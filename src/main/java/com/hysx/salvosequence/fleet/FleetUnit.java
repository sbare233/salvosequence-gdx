package com.hysx.salvosequence.fleet;

/**
 * 舰队棋盘上的一艘船：{@link Ship 船} + 等级 + 左上角坐标。
 *
 * <p>改造前的 {@code FleetUnit} 是"僚机/炮艇/护卫舰"这种固定舰级（{@code FleetClass}），
 * 和模块系统是两套互不相干的东西。改造后只有这一套：
 * <b>棋盘上的每一个格子归属都是"某艘船"</b>，玩家掉的、装的、打进战斗的都是它。</p>
 *
 * <p>等级是"同型船再来一艘"时合并出来的：一到手就同一型 → 等级 +1，
 * 而不是往棋盘上塞第二艘（棋盘只有 256 格，塞不下无限舰队）。</p>
 *
 * @param ship  船型
 * @param level 等级（≥1）
 * @param x     左上角列
 * @param y     左上角行
 */
public record FleetUnit(Ship ship, int level, int x, int y) {

    public FleetUnit {
        if (ship == null) {
            throw new IllegalArgumentException("棋子必须带船型");
        }
        if (level < 1) {
            throw new IllegalArgumentException("棋子等级至少 1，实际 " + level);
        }
    }

    /** 船型 id（同型船在舰队里唯一，所以它也当棋子 id 用）。 */
    public String id() {
        return ship.id();
    }

    /** 中文舰名。 */
    public String nameZh() {
        return ship.nameZh();
    }

    /** 英文舰名。 */
    public String nameEn() {
        return ship.nameEn();
    }

    /** 职能。 */
    public ShipRole role() {
        return ship.role();
    }

    /** 占地宽（格）。 */
    public int w() {
        return ship.w();
    }

    /** 占地高（格）。 */
    public int h() {
        return ship.h();
    }

    /** 占地格数。 */
    public int cells() {
        return ship.cells();
    }

    /** 右开边界列。 */
    public int x2() {
        return x + w();
    }

    /** 右开边界行。 */
    public int y2() {
        return y + h();
    }

    /** 基础结构值（不含相邻加成）。 */
    public int maxHp() {
        return ship.hpAtLevel(level);
    }

    /** 护盾（不含相邻加成）。 */
    public int armor() {
        return ship.armorAtLevel(level);
    }

    /** 齐射（不含相邻加成）。 */
    public int volley() {
        return ship.volleyAtLevel(level);
    }

    /** 这艘船带不带这张卡。 */
    public boolean carries(String cardId) {
        return cardId != null && ship.cardIds().contains(cardId);
    }

    /** 是不是指挥舰。 */
    public boolean isFlag() {
        return ship.isFlag();
    }

    /** 盖不盖住某一格。 */
    public boolean covers(int cx, int cy) {
        return cx >= x && cx < x2() && cy >= y && cy < y2();
    }

    /** 两艘船占的格子有没有重叠。 */
    public boolean overlaps(FleetUnit other) {
        return other != null && x < other.x2() && other.x < x2() && y < other.y2() && other.y < y2();
    }

    /** 挪到 (nx,ny)。 */
    public FleetUnit at(int nx, int ny) {
        return nx == x && ny == y ? this : new FleetUnit(ship, level, nx, ny);
    }

    /** 换等级。 */
    public FleetUnit withLevel(int newLevel) {
        return newLevel == level ? this : new FleetUnit(ship, newLevel, x, y);
    }

    /** 升一级。 */
    public FleetUnit upgraded() {
        return withLevel(level + 1);
    }

    /** 面板读数：{@code 主炮舰 ×2}。 */
    public String labelZh() {
        return level > 1 ? nameZh() + " ×" + level : nameZh();
    }

    @Override
    public String toString() {
        return labelZh() + "@" + x + "," + y + " " + w() + "x" + h();
    }
}
