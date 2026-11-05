package com.hysx.salvosequence.fleet;

import java.util.Locale;
import java.util.Set;

/**
 * 舰队的战斗口径常量与"哪些卡罩全队"的判定。
 *
 * <p>改造前这里还有一大块"卡 → 参与舰种"的映射（{@code vulcan.* → 僚机}、
 * {@code main.* → 战列舰+巡洋舰}…），因为那时候"谁跟着打"要按舰级去查表。
 * 改造后不需要了：<b>带这艘船就它开火</b>，参与者在 {@link Fleet#carriers(String)} 里，
 * 所以映射整段删掉。</p>
 */
public final class FleetOrders {

    /** 自由开火的伤害加成（溢出续打、但会被残骸吃掉余量）。 */
    public static final double FREE_FIRE_BONUS = 1.2;

    /** 罩住整支舰队的护盾卡。 */
    private static final Set<String> FLEET_SHIELD_CARDS =
            Set.of("armor.shield", "armor.brace", "buff.warp");

    private FleetOrders() {
    }

    /** 这张卡的护盾是不是罩全队（而不是只给指挥舰）。 */
    public static boolean isFleetShield(String cardId) {
        return cardId != null && FLEET_SHIELD_CARDS.contains(cardId.toLowerCase(Locale.ROOT));
    }
}
