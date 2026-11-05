package com.hysx.salvosequence.shipyard;

/**
 * 模块上的一个武器挂点（相对模块左上角的格坐标）。
 *
 * <h2>为什么挂点要单独存在</h2>
 * <p>见 {@code docs/SHIP-BUILDING.md} §5.2：玩家的操作必须是「点一个空位 → 从合法列表里选一个」，
 * <b>而不是</b>「放哪个、贴哪条边、转多少度、会不会插进去」。挂点就是那个「空位」：
 * 位置与朝向在设计期就定死了，玩家不需要旋转、对齐，也不可能装重叠。</p>
 *
 * @param dx     相对模块左上角的 x（格）
 * @param dy     相对模块左上角的 y（格）
 * @param xScale 横向缩放倍率：1.0 = 正常，负数 = 镜像（朝左）
 * @param allows 允许装在这一位的武器种类数上限（0 表示不限，用 {@link MountRules} 的规则推）
 */
public record MountSlot(int dx, int dy, double xScale, int allows) {

    public MountSlot {
        if (xScale == 0) {
            throw new IllegalArgumentException("挂点缩放不能是 0（那是不可见的炮塔）");
        }
        if (allows < 0) {
            throw new IllegalArgumentException("挂点武器上限不能是负数");
        }
    }

    public static MountSlot at(int dx, int dy) {
        return new MountSlot(dx, dy, 1.0, 0);
    }

    public static MountSlot mirrored(int dx, int dy) {
        return new MountSlot(dx, dy, -1.0, 0);
    }

    public boolean mirroredX() {
        return xScale < 0;
    }

    /** 这个挂点能不能装在 {@code shape} 这么大的模块上。 */
    public boolean fitsInside(ModuleShape shape) {
        return dx >= 0 && dy >= 0 && dx < shape.w() && dy < shape.h();
    }

    public String summary() {
        return "(" + dx + "," + dy + ")" + (mirroredX() ? " mirror" : "");
    }
}
