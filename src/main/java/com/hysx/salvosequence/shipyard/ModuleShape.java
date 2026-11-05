package com.hysx.salvosequence.shipyard;

/**
 * 模块的占格尺寸（单位：格）。
 *
 * <p>只允许正整数。0 或负数会让自动蒙皮与碰撞判断同时失去意义，所以在构造期就挡住。
 * 常见的形状都在 {@link ModuleCatalog} 里，散布的裸数字只在测试里出现。</p>
 */
public record ModuleShape(int w, int h) {

    public ModuleShape {
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("模块尺寸必须是正整数格，收到 " + w + "x" + h);
        }
    }

    /** 正方形的快捷构造：{@code ModuleShape.square(5)} = 5x5。 */
    public static ModuleShape square(int side) {
        return new ModuleShape(side, side);
    }

    /** 1x1。 */
    public static ModuleShape single() {
        return new ModuleShape(1, 1);
    }

    public int cells() {
        return w * h;
    }

    public boolean isSquare() {
        return w == h;
    }

    public boolean isWide() {
        return w > h;
    }

    public boolean isTall() {
        return h > w;
    }

    /** 旋转 90 度（宽高互换）。 */
    public ModuleShape rotated() {
        return new ModuleShape(h, w);
    }

    /** {@code 3x4} 这样的尺寸串，调试输出与图标角标都用它。 */
    public String label() {
        return w + "x" + h;
    }

    @Override
    public String toString() {
        return label();
    }
}
