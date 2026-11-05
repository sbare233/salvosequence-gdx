package com.hysx.salvosequence.shipyard;

/**
 * 模块的功能种类。
 *
 * <h2>为什么只有 4 种（外加核心）</h2>
 * <p>见 {@code docs/SHIP-BUILDING.md} §5.1：撑起船型多样性的是「同一种零件用很多个、摆法不同」，
 * 不是种类多。Reassembly 全游戏所有派系加起来也就 8 类方块。这里刻意压到 5 类，
 * 其中玩家在船坞里能主动选的是 4 类（核心是全船唯一、预设的）。</p>
 *
 * <p><b>尺寸档不算新种类</b>：装甲是 8x6 / 5x5 / 3x4 / 2x3 / 2x2 五档，但那仍是「装甲」一种。</p>
 */
public enum ModuleKind {

    /** 核心舱：全船唯一，被打掉就完蛋。高耐久、带内墙。 */
    CORE("CORE"),

    /** 骨架：便宜、轻、连接用。撑出船的走向。 */
    HULL("HULL"),

    /** 装甲：重、耐久高、贵。贴在受弹面。 */
    ARMOR("ARMOR"),

    /** 武器座：耐久低但能挂炮。挂点数决定能装几门。 */
    WEAPON_MOUNT("MOUNT"),

    /** 引擎：决定推力。放在尾部。 */
    ENGINE("ENGINE");

    private final String label;

    ModuleKind(String label) {
        this.label = label;
    }

    /** 点阵字体只认大写 ASCII，所以显示名另给一个短标签。 */
    public String label() {
        return label;
    }

    /** 是不是结构件（骨架与装甲合起来算「壳体」）。 */
    public boolean isStructure() {
        return this == HULL || this == ARMOR;
    }
}
