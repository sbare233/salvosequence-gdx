package com.hysx.salvosequence.shipyard;

/**
 * 模块的绘制层。
 *
 * <h2>这个枚举存在的唯一理由</h2>
 * <p>用户明确要求：<b>连装炮的炮塔图层要高于这些模块</b>。也就是说炮塔画完之后，
 * 不能有任何模块盖在它上面——哪怕那个模块在数据里排在后面、或者它的矩形跟炮塔重叠
 * （比如装甲板压到炮座边缘）。</p>
 *
 * <p>做法不是「按加入顺序画一遍」，而是把绘制<b>按层分组、层内再按顺序</b>：
 * 见 {@link ShipComposer#drawModules} 与 {@link ShipComposer#drawWeapons}。
 * 调用方无论如何排列输入列表，层序都不会被破坏。</p>
 *
 * <p>枚举的声明顺序<b>就是</b>从下到上的顺序，{@link #ordinal()} 直接当 z 值用。
 * 要插新层就加在合适的位置，别改已有常量的相对顺序。</p>
 */
public enum ShipLayer {

    /** 舰体底色：自动蒙皮拟合出的外轮廓填充。永远在最下面。 */
    HULL_BASE("hull-base"),

    /** 甲板细节：表面结构线、舷窗、涂装条纹。 */
    HULL_DETAIL("hull-detail"),

    /** 模块本体：装甲板、结构梁、核心舱、引擎。 */
    MODULE("module"),

    /** 武器座本体（不带炮管的那部分）。 */
    MOUNT("mount"),

    /** <b>炮塔</b>：炮管、炮座圆盘、多联装。压在一切模块之上。 */
    TURRET("turret"),

    /** 最上层特效：炮口火光、锁定框、命中火花。 */
    EFFECT("effect");

    private final String label;

    ShipLayer(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 这一层是否画在模块之上。 */
    public boolean aboveModules() {
        return ordinal() > MODULE.ordinal();
    }
}
