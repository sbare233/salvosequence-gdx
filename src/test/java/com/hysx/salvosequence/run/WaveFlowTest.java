package com.hysx.salvosequence.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hysx.salvosequence.combat.Battle;
import com.hysx.salvosequence.fleet.FleetUnit;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 波次流程：打空一波进「战术休整」，休整里禁掉的牌下一波不出现，打完第三波回到星图。
 */
class WaveFlowTest {

    private static final long SEED = 20261105L;

    @Test
    void clearingAWaveParksInTheLullAndBannedCardsSkipTheNextWave() throws Exception {
        Run run = intoBattle();

        assertEquals(1, run.wave(), "进战斗就是第一波");
        assertEquals(3, run.waveCount());

        win(run);
        assertEquals(Run.Stage.LULL, run.stage(), "清掉一波后进战术休整");
        assertEquals(2, run.wave(), "wave() 指向下一波");

        // 禁掉牌库里第一张：这一波不该再抽到它
        String banned = run.fleet().deck().get(0).id();
        assertTrue(run.toggleBannedCard(banned));
        assertTrue(run.isCardBanned(banned));

        // 编队：把最后一艘挪到第一艘的位置（第一艘先让开）也不该崩
        List<FleetUnit> ships = run.restShips();
        assertFalse(ships.isEmpty(), "休整界面至少要有一艘船");
        FleetUnit tail = ships.get(ships.size() - 1);
        assertNotNull(tail);

        run.startNextWave();
        assertEquals(Run.Stage.BATTLE, run.stage());
        assertEquals(2, run.wave());

        Battle battle = run.battle();
        assertNotNull(battle);
        assertTrue(battle.hand().stream().noneMatch(c -> c.id().equals(banned)),
                "禁掉的牌不该出现在手牌里");
        assertTrue(battle.drawPile().stream().noneMatch(c -> c.id().equals(banned)),
                "禁掉的牌也不该留在抽牌堆里");
    }

    @Test
    void theThirdWaveHandsBackToTheMap() throws Exception {
        Run run = intoBattle();
        win(run);
        assertEquals(Run.Stage.LULL, run.stage());
        run.startNextWave();
        win(run);
        assertEquals(Run.Stage.LULL, run.stage(), "第二波打完还要休整");
        assertEquals(3, run.wave());
        run.startNextWave();
        win(run);
        assertFalse(run.stage() == Run.Stage.LULL, "最后一波打完不再休整");
    }

    // ---------------------------------------------------------------- 工具

    /** 找一颗能打的星，跃迁触发战斗。 */
    private static Run intoBattle() {
        Run run = new Run(SEED);
        run.pickLoadout(0);
        for (int guard = 0; guard < 60 && run.stage() != Run.Stage.BATTLE; guard++) {
            switch (run.stage()) {
                case EVENT -> run.chooseEventOption(0);
                case SPACE_PORT -> run.departSpacePort();
                default -> { }
            }
            if (run.stage() != Run.Stage.MAP) {
                continue;
            }
            List<Run.StarNode> reachable = run.reachableStars();
            // 优先挑战斗节点：事件/商店会打断流程
            boolean moved = false;
            for (Run.StarNode star : reachable) {
                if (star.type() == Run.NodeType.BATTLE || star.type() == Run.NodeType.ELITE) {
                    moved = run.travelTo(star.id());
                    break;
                }
            }
            if (!moved) {
                for (Run.StarNode star : reachable) {
                    if (run.travelTo(star.id())) {
                        moved = true;
                        break;
                    }
                }
            }
            if (!moved) {
                throw new IllegalStateException("走不动了：" + run.stage());
            }
        }
        assertEquals(Run.Stage.BATTLE, run.stage(), "总该撞进一场战斗");
        return run;
    }

    /** 直接把本场判成胜利，再走一次真实结算。 */
    private static void win(Run run) throws Exception {
        Field result = Battle.class.getDeclaredField("result");
        result.setAccessible(true);
        result.set(run.battle(), Battle.Result.WON);
        run.endTurn();
    }
}
