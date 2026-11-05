package com.hysx.salvosequence.run;

import java.util.List;
import java.util.Map;

/**
 * 舰长（全舰队一名）：三主角各带一棵<b>专属</b>技能树（3 枝 × 3 层）。
 *
 * <p>差异化思路（参考杀戮尖塔的角色专属体系）：</p>
 * <ul>
 *   <li><b>瓦尔娜「铁砧」</b>（装甲官）：守势——结构、再生、荆棘，越挨打越硬；</li>
 *   <li><b>卡列布「燎原」</b>（炮术官）：斩杀——伤害、残血收割、首击爆发，见面就是一套；</li>
 *   <li><b>伊赫拉「星图」</b>（侦察官）：节奏——抽牌、合金、弱点扫描，资源滚雪球。</li>
 * </ul>
 *
 * <p>技能效果是被动修正，在 Battle 构造时由 Run 注入（一次注入整场生效）。
 * 枝内逐层解锁（先点满前层），消耗合金。</p>
 */
public final class Captain {

    // ---------------------------------------------------------------- 主角

    /** 四位可选主角：每一位绑定一支起始舰队，并带一个专属主动技。 */
    public enum Protagonist {
        ECHO("echo", "「领航员」岚",
                "破障者船团的持证领航员，跑了七年边贸航线。她信船，不信运气。",
                "starter.gunship",
                new Signature("航线预判", "抽 2 张 · 能量 +1：提前把接下来两步算好。",
                        0, 2, 1, 0, 0),
                List.of(
                        new SkillBranch("ihr.alloy", "后勤", new String[]{"后勤优化", "拆解回收", "合金精炼"},
                                new String[]{"合金获取 +15%", "+25%", "+40%"}, new int[]{30, 60, 100}),
                        new SkillBranch("ihr.datalink", "数据链", new String[]{"广域侦听", "链路加密", "全频接管"},
                                new String[]{"每回合抽牌 +1", "抽牌 +1 且能量 +1", "每回合抽牌 +2"},
                                new int[]{40, 80, 140}),
                        new SkillBranch("ihr.scan", "弱点", new String[]{"弱点扫描", "结构透视", "一击必洞"},
                                new String[]{"无盾敌舰受伤 +10%", "+20%", "+30%"}, new int[]{40, 80, 140}))),
        VERA("vera", "「叛逃者」薇拉",
                "铁冕帝国前封锁舰队军官。真人，不是重构体——死了就真的死，所以每一局都更重。",
                "starter.broadsider",
                new Signature("铁幕", "全队护盾 +10：最高纯度因瓦短暂锁定了周围的时空。",
                        10, 0, 0, 0, 0),
                List.of(
                        new SkillBranch("vard.armor", "守势", new String[]{"装甲掌握", "结构强化", "固若金汤"},
                                new String[]{"全舰结构 +3", "全舰结构 +6", "全舰结构 +10"}, new int[]{30, 60, 100}),
                        new SkillBranch("vard.regen", "再生", new String[]{"应急维修", "战地抢修", "全自动船坞"},
                                new String[]{"每回合全队修 1", "每回合全队修 2", "每回合全队修 4"}, new int[]{40, 80, 140}),
                        new SkillBranch("vard.thorns", "荆棘", new String[]{"反应装甲", "尖刺外板", "以牙还牙"},
                                new String[]{"每回合开局护盾 +2", "每回合开局护盾 +4", "每回合开局护盾 +7"},
                                new int[]{40, 80, 140}))),
        BEACON("beacon", "「核心信标」零",
                "主控 AI 的全息投影。它知道答案，却被程序限制只能暗示——所以它选择了你。",
                "starter.charger",
                new Signature("信标脉冲", "全队火控 +1（本场持续）· 抽 1 张：AI 把观测数据借给每一门炮。",
                        0, 1, 0, 0, 1),
                List.of(
                        new SkillBranch("kal.gun", "炮术", new String[]{"火力协同", "穿甲学", "总攻序列"},
                                new String[]{"全舰队伤害 +4%", "全舰队伤害 +8%", "全舰队伤害 +13%"},
                                new int[]{30, 60, 100}),
                        new SkillBranch("kal.execute", "斩杀", new String[]{"伤处补刀", "致命读数", "处决命令"},
                                new String[]{"残血敌舰(30%)受伤 +10%", "+20%", "+30%"}, new int[]{40, 80, 140}),
                        new SkillBranch("kal.first", "首击", new String[]{"先发校射", "首发齐射", "闪电开局"},
                                new String[]{"每回合首张攻击卡 +3", "+6", "+10"}, new int[]{40, 80, 140}))),
        HOMEWARD("homeward", "「归航者」夏初",
                "蓝星（旧地球）远征队的后代。第一次突围就死了，只留在因瓦结晶的记忆里——"
                        + "如今借你的手，带着启航时那批旧船归航。",
                "starter.bluestar",
                new Signature("最后信号", "本场全队伤害 +20%：把真相向全银河广播。",
                        0, 0, 0, 20, 0),
                List.of(
                        new SkillBranch("bh.trail", "航迹", new String[]{"旧航图", "归航坐标", "母港信标"},
                                new String[]{"全舰结构 +3", "全舰结构 +6", "全舰结构 +10"}, new int[]{30, 60, 100}),
                        new SkillBranch("bh.broadcast", "广播", new String[]{"短波中继", "全频段", "银河广播"},
                                new String[]{"合金获取 +15%", "+25%", "+40%"}, new int[]{30, 60, 100}),
                        new SkillBranch("bh.resonance", "共鸣", new String[]{"因瓦共振", "记忆回响", "齐射序列"},
                                new String[]{"全舰队伤害 +4%", "+8%", "+13%"}, new int[]{40, 80, 140})));

        private final String id;
        private final String nameZh;
        private final String desc;
        private final String starterId;
        private final Signature signature;
        private final List<SkillBranch> branches;

        Protagonist(String id, String nameZh, String desc, String starterId,
                    Signature signature, List<SkillBranch> branches) {
            this.id = id;
            this.nameZh = nameZh;
            this.desc = desc;
            this.starterId = starterId;
            this.signature = signature;
            this.branches = List.copyOf(branches);
        }

        public String id() { return id; }
        public String nameZh() { return nameZh; }
        public String desc() { return desc; }
        public String starterId() { return starterId; }
        public Signature signature() { return signature; }
        public List<SkillBranch> branches() { return branches; }

        /** 舰队绑定：哪支起始舰队归哪位主角。 */
        public static Protagonist forStarter(String starterId) {
            for (Protagonist p : values()) {
                if (p.starterId.equals(starterId)) {
                    return p;
                }
            }
            return values()[0];
        }
    }

    /** 主角专属主动技（每场战斗一次，S 键）。 */
    public record Signature(String nameZh, String desc, int shieldAll, int draw,
                            int energy, int damagePct, int spot) {
        public static final Signature NONE = new Signature("", "", 0, 0, 0, 0, 0);
    }

    /** 一条技能枝：3 层，名称/效果/费用逐层。 */
    public record SkillBranch(String id, String nameZh, String[] tierNames,
                              String[] tierEffects, int[] costs) {
        public int maxTier() { return 3; }
        public String tierName(int tier) { return tierNames[tier]; }
        public String tierEffect(int tier) { return tierEffects[tier]; }
        public int cost(int tier) { return costs[tier]; }
    }

    /**
     * 全部被动效果的汇总（Battle 一次注入）。
     */
    public record Effects(int damagePct, int structure, int energy, int alloyPct,
                          int regen, int thorns, int executePct, int firstStrike,
                          int draw, int weakpointPct) {
        public static final Effects NONE = new Effects(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    // ---------------------------------------------------------------- 状态

    private final Protagonist protagonist;
    /** 三条枝的已解锁层数（0~3）。 */
    private final int[] branchLevels = new int[3];
    /** 因瓦合金。 */
    private int alloy;

    public Captain(Protagonist protagonist, int startingAlloy) {
        this.protagonist = protagonist;
        this.alloy = startingAlloy;
    }

    // ---------------------------------------------------------------- 合金

    public int alloy() { return alloy; }
    public void addAlloy(int amount) { alloy = Math.max(0, alloy + amount); }
    public boolean spendAlloy(int amount) {
        if (alloy < amount) { return false; }
        alloy -= amount;
        return true;
    }

    // ---------------------------------------------------------------- 技能

    public Protagonist protagonist() { return protagonist; }
    public List<SkillBranch> branches() { return protagonist.branches(); }
    public int branchLevel(int branchIndex) { return branchLevels[branchIndex]; }

    /** 能不能解锁下一层（枝内顺序 + 合金够）。 */
    public boolean canUnlock(int branchIndex) {
        if (branchIndex < 0 || branchIndex >= 3) { return false; }
        SkillBranch br = branches().get(branchIndex);
        int cur = branchLevels[branchIndex];
        return cur < br.maxTier() && alloy >= br.cost(cur);
    }

    /** 解锁下一层。 */
    public boolean unlock(int branchIndex) {
        if (!canUnlock(branchIndex)) { return false; }
        SkillBranch br = branches().get(branchIndex);
        alloy -= br.cost(branchLevels[branchIndex]);
        branchLevels[branchIndex]++;
        return true;
    }

    /** 汇总全部被动效果（按当前主角的枝 id 归位）。 */
    public Effects effects() {
        int structure = 0, regen = 0, thorns = 0, damagePct = 0, executePct = 0,
                firstStrike = 0, alloyPct = 0, draw = 0, weakpointPct = 0, energy = 0;
        for (int i = 0; i < 3; i++) {
            int lv = branchLevels[i];
            if (lv == 0) { continue; }
            String bid = branches().get(i).id();
            switch (bid) {
                case "vard.armor" -> structure = new int[]{3, 6, 10}[lv - 1];
                case "vard.regen" -> regen = new int[]{1, 2, 4}[lv - 1];
                case "vard.thorns" -> thorns = new int[]{2, 4, 7}[lv - 1];
                case "kal.gun" -> damagePct = new int[]{4, 8, 13}[lv - 1];
                case "kal.execute" -> executePct = new int[]{10, 20, 30}[lv - 1];
                case "kal.first" -> firstStrike = new int[]{3, 6, 10}[lv - 1];
                case "ihr.alloy" -> alloyPct = new int[]{15, 25, 40}[lv - 1];
                case "ihr.datalink" -> {
                    draw = new int[]{1, 1, 2}[lv - 1];
                    energy = lv >= 2 ? 1 : 0;
                }
                case "ihr.scan" -> weakpointPct = new int[]{10, 20, 30}[lv - 1];
                case "bh.trail" -> structure = new int[]{3, 6, 10}[lv - 1];
                case "bh.broadcast" -> alloyPct = new int[]{15, 25, 40}[lv - 1];
                case "bh.resonance" -> damagePct = new int[]{4, 8, 13}[lv - 1];
                default -> { }
            }
        }
        return new Effects(damagePct, structure, energy, alloyPct,
                regen, thorns, executePct, firstStrike, draw, weakpointPct);
    }

    /** 三条枝的等级快照（存档/显示用）。 */
    public Map<String, Integer> snapshot() {
        return Map.of(
                branches().get(0).id(), branchLevels[0],
                branches().get(1).id(), branchLevels[1],
                branches().get(2).id(), branchLevels[2]);
    }

    @Override
    public String toString() {
        return protagonist.nameZh() + " 合金:" + alloy;
    }
}
