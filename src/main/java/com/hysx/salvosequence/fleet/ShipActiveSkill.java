package com.hysx.salvosequence.fleet;

import java.util.Map;

/**
 * 舰船主动技能：每个阵营梯度最高（T5）的脸面舰一艘，每场战斗限一次（X 键）。
 * 效果字段全部走 Battle 现有结算通道（护盾/抽牌/能量/伤害%/暴露）。
 */
public record ShipActiveSkill(String nameZh, String desc, int shieldAll, int draw,
                              int energy, int damagePct, int exposeTurns) {

    public static final ShipActiveSkill NONE = new ShipActiveSkill("", "", 0, 0, 0, 0, 0);

    /** 船 id → 技能（id 固定：leg.emp/eagle/north/sakura/south）。 */
    public static final Map<String, ShipActiveSkill> BY_SHIP = Map.of(
            "leg.emp", new ShipActiveSkill("阵地坚守", "全队护盾 +8", 8, 0, 0, 0, 0),
            "leg.eagle", new ShipActiveSkill("舰载机联队", "抽 3 张 · 能量 +2", 0, 3, 2, 0, 0),
            "leg.north", new ShipActiveSkill("过载齐射", "本回合全队伤害 +40%", 0, 0, 0, 40, 0),
            "leg.sakura", new ShipActiveSkill("夜袭雷击", "敌方全体暴露 2 回合", 0, 0, 0, 0, 2),
            "leg.south", new ShipActiveSkill("观测网", "敌方全体暴露 1 回合 · 抽 2 张",
                    0, 2, 0, 0, 1));

    /** 舰队里第一艘带主动技能的船（没有返回 NONE）。 */
    public static ShipActiveSkill ofFleet(Fleet fleet) {
        for (FleetUnit u : fleet.units()) {
            ShipActiveSkill skill = BY_SHIP.get(u.id());
            if (skill != null) {
                return skill;
            }
        }
        return NONE;
    }
}
