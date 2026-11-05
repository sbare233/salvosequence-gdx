package com.hysx.salvosequence.core;

/**
 * 一个可绘制单元。
 *
 * <p>它<b>不是</b>节点、也<b>不是</b>图层：没有自己的 Pane、没有自己的缓冲、没有自己的画布。
 * 它只是"往共享的整屏缓冲上画一笔"的一段逻辑。绘制顺序由视图决定，后画的覆盖先画的。</p>
 */
public interface Renderer {

    /** 推进动画（不改像素）。 */
    default void update(double dt) {
    }

    /**
     * 把自己画到整屏缓冲上。
     *
     * <p>调用时缓冲里已经有之前 {@link Renderer} 画好的内容，所以这里可以放心地
     * 叠加（例如半透明面板、描线覆盖）。需要"擦掉自己上一帧"的单元应当在自己内部维护
     * 一块小缓冲，或者把整屏缓冲当作只增不改的层叠结果处理。</p>
     */
    void draw(CanvasBuffer buffer);

    /** 缩放变化通知（设计像素不变，主要用于文本字号）。 */
    default void onScaleChanged(double scale) {
    }
}
