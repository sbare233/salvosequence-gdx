package com.hysx.salvosequence.ui;

/**
 * 触摸手势 -> 指针事件：把安卓的一根/两根手指翻译成桌面鼠标那套语义。
 *
 * <p>界面层（{@link ScreenManager}）只认识"按下 / 拖动 / 松手 / 悬浮 / 滚轮缩放"，
 * 桌面上由鼠标和滚轮产生，安卓上没有鼠标也没有滚轮，所以要在这里补三件事：</p>
 *
 * <ol>
 *   <li><b>点击</b>：单指按下 = {@code touchDown}，抬起 = {@code touchUp}，
 *       命中判定仍交给热区（和鼠标左键完全同一条路径）；</li>
 *   <li><b>悬浮（长按）</b>：手指按住不动超过 {@link #LONG_PRESS_SECONDS} 秒，
 *       就当成"指针停在那里"，派发一次 {@code mouseMoved} —— 界面的 entered/
 *       onMove 回调（按钮高亮、星图行星标签）因此能显示出来；长按看过之后松手
 *       <b>不算点击</b>（看一眼不该顺手把按钮按下去）；</li>
 *   <li><b>双指缩放</b>：两根手指的距离比例换算成滚轮 delta 交给界面的
 *       {@code onScroll}，所以星图/行星的缩放代码一行都不用改。
 *       换算口径见 {@link #scrollDeltaForSpanRatio}：一轮滚轮（delta=40）
 *       与星图的 {@code 1.0025^40}（约 1.105 倍）对齐。</li>
 * </ol>
 *
 * <p>双指落下的瞬间会把单指那次按下作废（{@code cancelPointer}），否则星图会一边
 * 缩放一边被拖着平移；双指期间与收尾也吞掉残留的拖动事件。</p>
 *
 * <p>本类不碰 libGDX：坐标换算在调用方完成，时间由每帧 {@link #update} 推进，
 * 因此可以在无图形环境里直接单测（见 {@code TouchGesturesTest}）。</p>
 */
public final class TouchGestures {

    /** 长按多久算"悬浮"（秒）。太短会和点击撞车，太长会觉得没反应。 */
    public static final double LONG_PRESS_SECONDS = 0.45;

    /** 按住时的抖动容差（设计像素）：动了超过它就算拖动，长按计时作废。 */
    public static final double SLOP_DESIGN = 8.0;

    /**
     * 滚轮一格对应的缩放倍率。星图 {@code onScroll} 是 {@code starZoom *= 1.0025^delta}，
     * 桌面滚轮一格给 40 → 1.105 倍；双指缩放按这个倍率反推 delta，手感才一致。
     */
    public static final double NOTCH_SCALE = Math.pow(1.0025, 40.0);

    /** 手势要落到的界面层。{@link ScreenManager} 直接实现它。 */
    public interface PointerSink {

        boolean touchDown(double x, double y);

        boolean touchUp(double x, double y);

        boolean touchDragged(double x, double y);

        boolean mouseMoved(double x, double y);

        void handleScroll(double delta, double x, double y);

        /** 作废当前单指按下（第二根手指落下、长按看过再松手时用），不触发点击。 */
        void cancelPointer();
    }

    private final PointerSink sink;
    /** 长按悬浮只在触摸设备上开：桌面鼠标按住不动不该吞掉点击。 */
    private final boolean longPressEnabled;

    /** 第一根手指（主指针）与第二根手指的 id，-1 = 没按。 */
    private int pointerA = -1;
    private int pointerB = -1;
    private double ax;
    private double ay;
    private double bx;
    private double by;
    /** 单指按下的落点（判断是否抖动出容差）。 */
    private double downX;
    private double downY;

    private boolean holding;
    private double holdTime;
    private boolean hovering;
    private boolean pinching;
    private double pinchSpan;
    /** 双指结束后残留的那根手指不再派发拖动（避免画面跳一下）。 */
    private boolean swallowDrag;

    public TouchGestures(PointerSink sink, boolean longPressEnabled) {
        this.sink = sink;
        this.longPressEnabled = longPressEnabled;
    }

    // ------------------------------------------------------------------ 事件

    /**
     * 手指按下。
     *
     * @param pointer 触点 id（0、1…，同一根手指拖动时不变）
     * @param x       设计坐标 x
     * @param y       设计坐标 y
     * @return true 表示已消费
     */
    public boolean touchDown(int pointer, double x, double y) {
        if (pointerA < 0) {
            pointerA = pointer;
            ax = downX = x;
            ay = downY = y;
            holding = longPressEnabled;
            holdTime = 0;
            hovering = false;
            swallowDrag = false;
            sink.touchDown(x, y);
            return true;
        }
        if (pointerB < 0 && pointer != pointerA) {
            pointerB = pointer;
            bx = x;
            by = y;
            // 第二根手指 = 双指缩放：单指那次按下作废，星图不能一边缩一边被拖。
            holding = false;
            holdTime = 0;
            hovering = false;
            sink.cancelPointer();
            pinching = true;
            pinchSpan = span();
            return true;
        }
        return false;
    }

    /**
     * 手指移动（无论按着几根都在这里；调用方已把窗口坐标换成设计坐标）。
     */
    public boolean touchDragged(int pointer, double x, double y) {
        if (pointer == pointerB) {
            bx = x;
            by = y;
        } else if (pointer == pointerA) {
            ax = x;
            ay = y;
        } else {
            return false;
        }

        if (pinching) {
            double next = span();
            double delta = scrollDeltaForSpanRatio(pinchSpan, next);
            if (Math.abs(delta) > 1e-4) {
                // 锚点取两指中点：手指之间的那块星图内容缩放前后停在原处。
                sink.handleScroll(delta, (ax + bx) / 2.0, (ay + by) / 2.0);
            }
            pinchSpan = next;
            return true;
        }

        if (pointer != pointerA) {
            return false;
        }
        if (holding && Math.hypot(ax - downX, ay - downY) > SLOP_DESIGN) {
            holding = false;   // 抖出容差 = 拖动，长按计时作废
        }
        if (hovering) {
            // 长按之后手指在屏幕上滑：悬浮跟着走（tooltip/行星标签跟手）。
            sink.mouseMoved(x, y);
            return true;
        }
        if (swallowDrag) {
            return true;
        }
        return sink.touchDragged(x, y);
    }

    /**
     * 手指抬起。
     */
    public boolean touchUp(int pointer, double x, double y) {
        boolean wasPrimary = pointer == pointerA;
        boolean wasSecondary = pointer == pointerB;
        if (!wasPrimary && !wasSecondary) {
            return false;
        }
        if (wasSecondary) {
            pointerB = -1;
        }
        if (wasPrimary) {
            pointerA = -1;
        }

        if (pinching) {
            // 缩放手势结束：还剩一根手指也不接着拖（画面会突然跳一下），
            // 等它抬起、重新按下才算新的手势。
            pinching = false;
            pinchSpan = 0;
            swallowDrag = true;
            if (pointerA < 0 && pointerB < 0) {
                reset();
            }
            return true;
        }

        if (!wasPrimary) {
            return true;
        }

        ax = x;
        ay = y;
        // 长按看过再松手 / 双指刚收完的残指：都只收尾，不点按钮。
        boolean suppressed = hovering || swallowDrag;
        holding = false;
        hovering = false;
        if (suppressed) {
            sink.cancelPointer();
        } else {
            sink.touchUp(x, y);
        }
        reset();
        return true;
    }

    /** 每帧推进长按计时；界面层每帧调一次。 */
    public void update(double dt) {
        if (!holding || hovering || pinching || pointerA < 0 || pointerB >= 0) {
            return;
        }
        holdTime += dt;
        if (holdTime >= LONG_PRESS_SECONDS) {
            holding = false;
            hovering = true;
            sink.mouseMoved(ax, ay);
        }
    }

    /** 是否有手指按着（安卓返回键/暂停逻辑可能关心）。 */
    public boolean isTouching() {
        return pointerA >= 0 || pointerB >= 0;
    }

    /** 长按悬浮是否已经触发（测试与调试用）。 */
    public boolean isHovering() {
        return hovering;
    }

    /** 是否处于双指缩放中（测试与调试用）。 */
    public boolean isPinching() {
        return pinching;
    }

    // ------------------------------------------------------------------ 换算

    /**
     * 双指距离比例 → 滚轮 delta（正 = 放大，与滚轮向上同号）。
     *
     * <p>界面层缩放是按 {@code pow(NOTCH_SCALE, delta/40)} 吃 delta 的，所以反推
     * {@code delta = 40 * ln(比例) / ln(NOTCH_SCALE)}：两指张开一倍 → 星图放大一倍。</p>
     */
    public static double scrollDeltaForSpanRatio(double previousSpan, double currentSpan) {
        if (previousSpan <= 0 || currentSpan <= 0) {
            return 0;
        }
        return 40.0 * Math.log(currentSpan / previousSpan) / Math.log(NOTCH_SCALE);
    }

    private double span() {
        return Math.hypot(ax - bx, ay - by);
    }

    private void reset() {
        pointerA = -1;
        pointerB = -1;
        holding = false;
        hovering = false;
        pinching = false;
        pinchSpan = 0;
        swallowDrag = false;
        holdTime = 0;
    }
}
