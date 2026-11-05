import com.hysx.salvosequence.run.Run;

/** 跑一遍 reachSpacePort 的循环，把阶段序列打出来，看 default 到底撞上了什么。 */
public final class StageTrace {
    public static void main(String[] args) throws Exception {
        long seed = Long.parseLong(args.length > 0 ? args[0] : "20261105");
        Run run = new Run(seed);
        run.pickLoadout(1);
        run.pickCaptain(0);
        int guard = 0;
        StringBuilder seq = new StringBuilder();
        while (!run.finished() && guard++ < 40) {
            seq.append(run.stage()).append(' ');
            if (run.battle() != null) { System.out.println("  wave " + run.wave() + " foes=" + run.battle().squad().size()); }
            switch (run.stage()) {
                case BATTLE -> {
                    var f = com.hysx.salvosequence.combat.Battle.class.getDeclaredField("result");
                    f.setAccessible(true);
                    f.set(run.battle(), com.hysx.salvosequence.combat.Battle.Result.WON);
                    run.endTurn();
                }
                case LULL -> run.startNextWave();
                case SPACE_PORT -> {
                    System.out.println("SPACE_PORT after " + seq);
                    return;
                }
                case MAP -> {
                    var reach = run.reachableStars();
                    var pick = reach.stream().filter(st -> st.type() == Run.NodeType.BATTLE
                            || st.type() == Run.NodeType.ELITE).findFirst();
                    if (pick.isEmpty()) { pick = reach.stream().findFirst(); }
                    pick.ifPresent(st -> run.travelTo(st.id()));
                }
                case EVENT -> run.chooseEventOption(0);
                default -> {
                    System.out.println("DEFAULT stage=" + run.stage() + " seq=" + seq);
                    return;
                }
            }
        }
        System.out.println("ended guard=" + guard + " seq=" + seq);
    }
}
