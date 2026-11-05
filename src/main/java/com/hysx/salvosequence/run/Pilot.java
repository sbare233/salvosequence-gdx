package com.hysx.salvosequence.run;

import com.hysx.salvosequence.combat.Battle;
import com.hysx.salvosequence.fleet.FleetUnit;
import com.hysx.salvosequence.fleet.Ship;

import java.util.List;

/**
 * 一套"玩法"——用它来跑验证。
 *
 * <p>同一个 {@link Run} 换四种 Pilot：
 * <ul>
 *   <li>{@code SimplePilot} 三种风格：莽 / 稳 / 算——验证"换策略会不会赢"；</li>
 *   <li>界面里是<b>人类玩家</b>——验证"人玩起来有没有得选"。</li>
 * </ul>
 * 机器人不读界面、不做动画，只调 {@link Battle} 与 {@link Run} 的公共方法，
 * 所以它验证的是规则本身，而不是某个界面实现。</p>
 */
public interface Pilot {

    /** 中文名（报告用 ASCII 另配 {@link #nameEn()}）。 */
    String nameZh();

    /** 报告里用的短名（ASCII）。 */
    String nameEn();

    /**
     * 从手牌里挑一张打。
     *
     * @return 手牌下标；{@code -1} = 结束回合
     */
    int chooseCard(Battle battle);

    /** 是否用舰长技能。 */
    boolean useSkill(Battle battle);

    /**
     * 从战利品里挑一个。
     *
     * @return {@link Run#rewards()} 的下标；{@code -1} = 跳过
     */
    int chooseReward(Run run, List<Reward> options);

    /**
     * 一艘新船编到哪个格位。
     *
     * <p>候选是棋盘上所有放得下的位置（改造后不再有"顶掉某件已装件"，
     * 编入是净增一艘船——所以这里只需要挑空位）。</p>
     *
     * @param fleet      当前舰队
     * @param incoming   要编入的船
     * @param candidates 候选落位
     * @return {@code candidates} 的下标；{@code -1} = 放弃这艘船
     */
    int choosePlacement(com.hysx.salvosequence.fleet.Fleet fleet, Ship incoming, List<FleetUnit> candidates);
}
