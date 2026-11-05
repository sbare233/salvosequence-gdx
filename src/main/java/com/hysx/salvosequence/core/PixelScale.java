package com.hysx.salvosequence.core;

/**
 * 缩放管理：把固定的设计分辨率（设计像素）映射到窗口/屏幕像素。
 *
 * <p>策略：整屏优先使用<b>整数倍</b>缩放（作者机器为 3 倍），这样每个设计像素恰好落在
 * N x N 个设备像素上，配合 Nearest 纹理采样得到完全锐利的像素画面；窗口长宽比
 * 与设计比例不一致时，退回按窗口等比缩放（浮点），画面填满窗口但会出现非整数像素。</p>
 *
 * <p>libGDX 版去掉了 JavaFX 的属性/变换包装，只留纯数学；缩放结果由视图
 * 直接换算成纹理的绘制矩形。</p>
 */
public final class PixelScale {

    /** 设计分辨率宽（设计像素）。 */
    public static final double DESIGN_WIDTH = 640;
    /** 设计分辨率高（设计像素）。 */
    public static final double DESIGN_HEIGHT = 360;

    /** 相对设计分辨率的浮点缩放比例，用于把窗口铺满。 */
    private double rawScale = 1;
    /** 取整后的缩放倍数；rawScale 与整数差距过大时为 0（表示按浮点缩放渲染）。 */
    private double integerScale = 1;

    /** 图层实际使用的缩放（整数倍优先，否则浮点）。 */
    private double effective = 1;
    /** 画面居中留边（屏幕像素）。 */
    private double offsetX;
    private double offsetY;

    private double viewportWidth = DESIGN_WIDTH;
    private double viewportHeight = DESIGN_HEIGHT;

    /**
     * 整数倍判定的容差（相对值）。
     *
     * <p>为什么需要容差而不是"精确相等"：窗口模式下的视口要减掉边框和标题栏
     * （实测 1920x1080 的窗口，视口只有 1904x1041，纵向偏差 3.6%）。
     * 此时缩放倍数算出 2.89，若因为"不是整数"就退回浮点缩放，整屏像素都会错位。
     * 反过来，只要偏差在容差内就取该整数倍：多出来的部分裁掉、不足的部分留黑边，
     * 但<b>所有像素都落在整数网格上</b>，观感才是干净的点阵。</p>
     */
    private static final double INTEGER_TOLERANCE = 0.06;

    /**
     * 视口尺寸变化时重新计算缩放。
     *
     * @param width  视口宽（屏幕像素）
     * @param height 视口高（屏幕像素）
     */
    public void updateViewport(double width, double height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        this.viewportWidth = width;
        this.viewportHeight = height;

        double fit = Math.min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT);
        rawScale = fit;

        // 以"最近的整数倍"为基准做吸附：视口比整数倍略小（窗口去掉边框）或略大
        // 都算合格 —— 略小时会裁掉最外圈几个像素，略大时留黑边，两者都保住像素网格。
        // 只有偏差明显超过容差（例如 1600x900 的 2.5 倍）才退回浮点缩放。
        double nearest = Math.max(1, Math.rint(fit));
        double deviation = Math.abs(fit - nearest) / nearest;
        boolean snap = fit >= 1.0 && deviation <= INTEGER_TOLERANCE;
        integerScale = snap ? nearest : 0;

        effective = effectiveScale();
        offsetX = Math.rint((width - DESIGN_WIDTH * effective) / 2.0);
        offsetY = Math.rint((height - DESIGN_HEIGHT * effective) / 2.0);
    }

    /** 实际用于绘制图层的缩放比例。 */
    public double effectiveScale() {
        return integerScale > 0 ? integerScale : rawScale;
    }

    /** 是否处于精确整数倍缩放（像素完全锐利）。 */
    public boolean isIntegerScale() {
        return integerScale > 0;
    }

    /** 文本/矢量覆盖层使用整数缩放以保持字形锐利。 */
    public double textScale() {
        return rawScale >= 1 ? Math.max(1, Math.rint(rawScale)) : rawScale;
    }

    /** 浮点缩放比例（未吸附）。 */
    public double rawScale() {
        return rawScale;
    }

    /** 吸附后的整数倍；0 表示按浮点缩放渲染。 */
    public double integerScale() {
        return integerScale;
    }

    /** 画面水平留边（屏幕像素）。 */
    public double offsetX() {
        return offsetX;
    }

    /** 画面垂直留边（屏幕像素）。 */
    public double offsetY() {
        return offsetY;
    }

    public double viewportWidth() {
        return viewportWidth;
    }

    public double viewportHeight() {
        return viewportHeight;
    }

    /** 设计像素 -> 屏幕像素。 */
    public double toScreen(double designPixels) {
        return designPixels * effective;
    }

    /** 当前可见的设计区域 {minX, minY, width, height}（居中裁剪时的实际可见范围）。 */
    public double[] visibleDesignBounds() {
        return new double[]{-offsetX / effective, -offsetY / effective,
                viewportWidth / effective, viewportHeight / effective};
    }
}
