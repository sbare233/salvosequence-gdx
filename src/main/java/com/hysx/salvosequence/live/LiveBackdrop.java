package com.hysx.salvosequence.live;

/**
 * 实时背景：每帧在 GL 里画一遍场景，读回一帧 320×180 的 ARGB 交给软件画布。
 *
 * <p>和「背景工坊」导出的静态图不同，这类背景会随时间动，所以战斗中不是贴一张图，
 * 而是每帧重新渲染一遍（320×180 渲染 + 2 倍最近邻贴回 640×360，代价和辉光后处理同量级）。</p>
 */
public interface LiveBackdrop {

    /** 稳定标识（存档/配置里记这个）。 */
    String id();

    String nameZh();

    String nameEn();

    /** 渲染一帧，返回 320×180 的非预乘 ARGB（y 向下）；资源未就绪时返回 null。 */
    int[] frame(double time);

    /** 场景的可调参数对象（{@code Params}），没有可调项的返回 null。 */
    Object params();

    /**
     * 鼠标拖拽 = 轨道旋转。{@code dx/dy} 是相对上一次的像素位移，口径与场景自带的调参界面一致。
     *
     * <p>实时背景不再自己慢慢转：相机只有指针拖拽、滚轮缩放和存档里的角度会改它。</p>
     */
    void orbit(double dx, double dy);

    /** 滚轮缩放，{@code notches} 正数放大。 */
    void zoom(double notches);

    /** 当前相机 {偏航°, 俯仰°, 距离}，用于存盘与面板显示。 */
    double[] camera();

    /** 直接设相机（读档用）。 */
    void setCamera(double yaw, double pitch, double dist);

    void dispose();
}
