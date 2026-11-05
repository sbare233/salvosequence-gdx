package com.hysx.salvosequence.screen;

import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.render.HotspotSink;

/**
 * 一个界面（屏幕）。
 *
 * <p>界面是"绘制单元的集合"：它拥有若干 {@code Renderer}、自己推进动画，并可以在
 * 需要时请求跳转到另一个界面（返回非 null 的 {@link ScreenContext} 即表示要切换）。</p>
 *
 * <p>它<b>没有节点</b>：所有内容都由 {@link #draw} 画进共享缓冲，层级由绘制顺序表达。
 * 界面之间的切换、过渡效果、热区挂载由 {@code ScreenManager} 统一处理。</p>
 */
public interface Screen {

    /** 界面名（调试/核对用）。 */
    String name();

    /** 推进动画（不改像素）。 */
    void update(double dt);

    /** 把自己画进共享缓冲。 */
    void draw(CanvasBuffer buffer);

    /**
     * 处理按键。
     *
     * @param code 按键名（如 {@code "ENTER"}、{@code "UP"}、{@code "ESCAPE"}）
     * @return 请求跳转的界面；返回 null 表示不切换
     */
    /**
     * 这个键是不是被界面自己接管了（默认没有）。
     *
     * <p>{@link #onKey} 返回 {@code null} 表示"不跳转"，分不出"吃掉了"和"没理它"；
     * 个别功能键（比如标题页的 F12 彩蛋）需要在全局热键之前先问一句，就用这个。</p>
     */
    default boolean consumesKey(String code) {
        return false;
    }

    default ScreenContext onKey(String code) {
        return null;
    }

    /**
     * 登记可点区域（设计坐标）。
     *
     * <p>整屏只有一个画布，按钮不能靠节点树表达；界面给出矩形 + 回调即可，
     * 由 {@code ScreenManager} 换算成屏幕坐标摆放透明热区。</p>
     */
    default void collectHotspots(HotspotSink sink) {
    }

    /**
     * 滚轮（设计坐标，delta 正 = 滚轮向上 = 放大；星图以指针为锚）。
     */
    default void onScroll(double delta, double x, double y) {
    }

    /**
     * 取走由"热区点击"产生的跳转请求（键盘产生的跳转直接由 {@link #onKey} 返回）。
     *
     * @return 跳转请求；没有则返回 null
     */
    default ScreenContext pollTransition() {
        return null;
    }

    /**
     * 取走"我的可点区域需要重建"这个标记。
     *
     * <p>热区默认只在界面切换时重建一次。可有些界面的可点区域会<b>随内部状态变化</b>
     * （打出卡牌后手牌少了一张、选完战利品后列表换了），那种界面在自己的状态变化处
     * 把这个标记置上，管理器下一帧就会重建。用"取走"而不是"查询"，是为了让标记
     * 只生效一次——否则就变成每帧重建一遍热区。</p>
     *
     * @return true 表示需要重建
     */
    default boolean consumeHotspotDirty() {
        return false;
    }

    /** 界面首次进入时调用（可用于重置动画计时）。 */
    default void onEnter() {
    }

    /** 界面离开时调用。 */
    default void onExit() {
    }
}
