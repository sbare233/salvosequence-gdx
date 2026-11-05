package com.hysx.salvosequence.render;

/**
 * 可点区域的收集器。
 *
 * <p>整屏只有一个画布，按钮/菜单不能再靠节点树表达。单元用<b>设计坐标</b>
 * （640x360 坐标系）把可点矩形登记出来，视图再换算成屏幕坐标摆放透明热区。</p>
 */
@FunctionalInterface
public interface HotspotSink {

    /**
     * 登记一个可点区域。
     *
     * @param x      左边界（设计像素）
     * @param y      上边界（设计像素）
     * @param w      宽度（设计像素）
     * @param h      高度（设计像素）
     * @param action 点击回调
     */
    void add(double x, double y, double w, double h, Runnable action);

    /**
     * 登记一个带<b>悬浮</b>回调的可点区域：鼠标进入 / 离开时各调一次。
     *
     * <p>为什么需要：整屏画布没有节点树，界面要"鼠标指到哪一格就预览哪一格"，
     * 只能靠热区把进入/离开事件转成 {@link Runnable}。默认实现退化成纯点击，
     * 所以老界面不用改。</p>
     */
    default void hover(double x, double y, double w, double h, Runnable action,
                       Runnable onEnter, Runnable onExit) {
        add(x, y, w, h, action);
    }

    /**
     * 登记一个<b>跟踪鼠标位置</b>的区域：鼠标在里面移动时，把设计坐标回调出去。
     *
     * <p>整屏画布没有节点树，而"悬浮到某一块模块上显示它的数值"需要知道指针落在哪一格，
     * 光有进入/离开两个事件不够。回调参数是**设计坐标**（相对 640x360 画布），
     * 所以界面可以直接拿它跟自己的版面常量对齐。默认实现退化成纯点击。</p>
     */
    default void track(double x, double y, double w, double h,
                       java.util.function.BiConsumer<Double, Double> onMove,
                       Runnable action, Runnable onEnter, Runnable onExit) {
        hover(x, y, w, h, action, onEnter, onExit);
    }
    /**
     * 按下/拖动：只处理"按住左键"这一种情形，悬浮不会触发。
     * 画布上色、滑杆调节都走这里——用 mouse-moved 做这些事会变成"一悬浮就动手"。
     */
    default void drag(double x, double y, double w, double h,
                      java.util.function.BiConsumer<Double, Double> onDrag,
                      java.util.function.BiConsumer<Double, Double> onPress) {
        add(x, y, w, h, () -> {
        });
    }

    /**
     * 移动 + 拖动 + 按下，三件事挂在**同一块**区域上。
     *
     * <p>像素画布需要这样：悬浮时要知道指针在哪（放大镜跟着走），落笔却只能
     * 按下才动手。而热区层是"后者压前者"——在同一块矩形上登记两次（一次
     * {@link #track} 看移动、一次 {@link #drag} 补按下），后登记的那个会把前一个
     * 吃掉（手牌和建造界面都栽过）。</p>
     */
    default void draw(double x, double y, double w, double h,
                      java.util.function.BiConsumer<Double, Double> onMove,
                      java.util.function.BiConsumer<Double, Double> onDrag,
                      java.util.function.BiConsumer<Double, Double> onPress,
                      Runnable onEnter, Runnable onExit) {
        drag(x, y, w, h, onDrag, onPress);
    }

    /**
     * 同 {@link #draw}，但松手时多回调一次 {@code onRelease}。
     *
     * <p>拖拽类交互（棋盘上挪船）需要一个"松手"的点来收尾：把拖动状态清掉、
     * 把临时高亮撤掉。热区层拿得到 {@code MOUSE_RELEASED}，界面拿不到，
     * 所以由这里转出来。默认实现忽略 {@code onRelease}。</p>
     */
    default void draw(double x, double y, double w, double h,
                      java.util.function.BiConsumer<Double, Double> onMove,
                      java.util.function.BiConsumer<Double, Double> onDrag,
                      java.util.function.BiConsumer<Double, Double> onPress,
                      Runnable onEnter, Runnable onExit, Runnable onRelease) {
        draw(x, y, w, h, onMove, onDrag, onPress, onEnter, onExit);
    }
}