package com.hysx.salvosequence.ui;

import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelFont;
import com.hysx.salvosequence.render.HotspotSink;
import com.hysx.salvosequence.screen.CardLoopScreen;
import com.hysx.salvosequence.screen.FleetArrangementScreen;
import com.hysx.salvosequence.screen.ShipDesignerScreen;
import com.hysx.salvosequence.screen.Screen;
import com.hysx.salvosequence.screen.ScreenContext;
import com.hysx.salvosequence.screen.SpriteLabScreen;
import com.hysx.salvosequence.screen.StartScreen;
import com.hysx.salvosequence.screen.ToggleScreen;
import com.hysx.salvosequence.screen.experiment.PortraitOverlay;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.Gdx;

import java.util.ArrayList;
import java.util.List;

/**
 * UI 控制类：界面管理器（libGDX 版）。
 *
 * <p>职责：</p>
 * <ol>
 *   <li>持有当前界面，逐帧驱动它（{@code update} + {@code draw}）；</li>
 *   <li>处理界面切换：界面通过 {@code onKey} 或 {@code pollTransition()} 声明目标，
 *       这里执行 {@code onExit} → 过渡 → 新界面 {@code onEnter}；</li>
 *   <li>统一做屏幕空间后处理（扫描线、调试读数）—— 这些与具体界面无关；</li>
 *   <li>把当前界面登记的可点区域做成<b>软件热区</b>：没有节点树，命中测试直接在
 *       设计坐标上做（原 JavaFX 版是一排透明 Region 挂在场景里，libGDX 没有
 *       场景图，改由视图把鼠标事件换算成设计坐标转发进来）。</li>
 * </ol>
 *
 * <p>它不持有纹理：画布由 {@link PixelGameView} 持有，这里只往共享缓冲上画。
 * 这样"几个界面共用一个画布"就成立了 —— 这正是单画布架构的意义。</p>
 */
public final class ScreenManager implements HotspotSink, TouchGestures.PointerSink {

    /** 界面切换的淡入淡出时长（秒）。 */
    private static final double FADE_TIME = 0.28;

    /** 热区类型：对应原 JavaFX 版四类 Region 挂的事件组合。 */
    private static final int KIND_CLICK = 0;   // add：只有点击
    private static final int KIND_HOVER = 1;   // hover：点击 + 进入/离开
    private static final int KIND_TRACK = 2;   // track：移动/拖动回调 + 点击 + 进入/离开
    private static final int KIND_DRAG = 3;    // drag：按下 + 拖动（手势抓取）
    private static final int KIND_DRAW = 4;    // draw：移动 + 拖动 + 按下 + 进入/离开 + 松手

    /**
     * 一块热区：矩形 + 回调（设计坐标）。
     *
     * <p>原版是 JavaFX {@code Region} + 事件处理器；libGDX 版是纯数据，
     * 命中测试在 {@link #hit} 里从后往前找（后登记的在上层，与节点树一致）。</p>
     */
    private record Hotspot(double x, double y, double w, double h, int kind,
                           Runnable action, Runnable onEnter, Runnable onExit,
                           java.util.function.BiConsumer<Double, Double> onMove,
                           java.util.function.BiConsumer<Double, Double> onDrag,
                           java.util.function.BiConsumer<Double, Double> onPress,
                           Runnable onRelease) {

        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    /** 当前界面的热区定义（设计坐标）。 */
    private final List<Hotspot> hotspots = new ArrayList<>();

    private final PixelFont font = new PixelFont();

    private Screen current;
    private Screen pending;
    /** 覆盖式界面（实验室）正在显示时，这里记着进入前那一屏。 */
    private Screen screenBehind;
    /** 当前这一局的关卡界面：休整时去编排舰队再回来，要复用同一个实例，Run 才不会被重开。 */
    private CardLoopScreen loopScreen;
    /** 覆盖式界面的实例，翻来翻去保留帧位置等状态。 */
    private final SpriteLabScreen spriteLab = new SpriteLabScreen();
    private double fade;          // 0 = 全黑，1 = 全亮
    private boolean fadingIn;
    private boolean debugVisible;
    private double time;
    private Runnable quitHandler = () -> System.exit(0);

    /** 缩放到屏幕的换算：设计像素 -> 窗口像素。 */
    private double scale = 3;
    private double originX;
    private double originY;

    /** 指针状态：悬浮与按下的热区（拖动手势期间"抓"在按下的那块上）。 */
    private int hovered = -1;
    private int pressed = -1;
    /**
     * 按下时抓走的热区快照。拖动手势必须能跨越热区整层重建——星图平移每帧
     * {@code touch()} 一次，重建后索引全部失效；原版 JavaFX 靠"拖动挂到场景上"
     * 活过重建（否则"拖一下动一格就断了"），这里用不可变快照等价实现。
     */
    private Hotspot activeDrag;

    /**
     * 触摸设备上的悬浮语义：轻点<b>不</b>建立悬浮，只有长按（手势层调 {@link #mouseMoved}）
     * 才把悬浮交出去——描述弹窗因此只在按住时出现，松手就收回去。
     */
    private boolean touchHover;

    /** 左下角全局返回键：所有界面共用一枚，行为等同安卓返回键（先当 ESC，标题页退出）。 */
    private static final int BACK_X = 6;
    private static final int BACK_W = 48;
    private static final int BACK_H = 20;
    /** 设计坐标 y 向下，所以底边是 360 减掉按钮高度和留白。 */
    private static final int BACK_Y = 360 - 6 - BACK_H;

    public ScreenManager() {
        current = new StartScreen();
        fade = 1;
        current.onEnter();
        rebuildHotspots();
    }

    /** 视图尺寸/缩放变化时调用：更新坐标换算基数。 */
    public void setViewport(double viewportWidth, double viewportHeight,
                            double canvasWidth, double canvasHeight) {
        this.scale = canvasWidth / 640.0;
        this.originX = Math.rint((viewportWidth - canvasWidth) / 2);
        this.originY = Math.rint((viewportHeight - canvasHeight) / 2);
        trace(String.format("layout viewport %.0fx%.0f  canvas %.0fx%.0f  scale %.4f  origin %.1f,%.1f",
                viewportWidth, viewportHeight, canvasWidth, canvasHeight, scale, originX, originY));
    }

    /**
     * 视图告诉这里当前是不是触摸设备。
     *
     * <p>是的话松手不再按指针位置重算悬浮：否则"轻点一下"就等于悬浮一下，卡片描述
     * 会一点就弹出来（触摸屏没有鼠标悬停，描述应该由长按触发）。</p>
     */
    public void setTouchHover(boolean touchHover) {
        this.touchHover = touchHover;
    }

    /** 每帧调用：驱动当前界面并做界面切换。 */
    public void update(double dt) {
        time += dt;

        // 过渡推进
        if (pending != null) {
            fade -= dt / FADE_TIME;
            if (fade <= 0) {
                fade = 0;
                current.onExit();
                current = pending;
                pending = null;
                current.onEnter();
                fadingIn = true;
                rebuildHotspots();
            }
        } else if (fadingIn) {
            fade += dt / FADE_TIME;
            if (fade >= 1) {
                fade = 1;
                fadingIn = false;
            }
        }

        current.update(dt);

        // 界面自己产生的跳转（例如热区点击）
        ScreenContext context = current.pollTransition();
        if (context != null) {
            handle(context);
        }

        // 界面内部状态变了（出牌、改装、选战利品），可点区域跟着变
        if (current.consumeHotspotDirty()) {
            rebuildHotspots();
        }
    }

    /**
     * 全屏人物立绘：任何界面按 F12 都能开关，画在所有界面内容之上（压暗、过渡之前）。
     *
     * <p>懒加载：只有真按下 F12 才去读 {@code /sprites/portrait.png}，没按过的会话
     * 一分钱不花，测试里也不会为了构造 ScreenManager 去解码图片。</p>
     */
    private PortraitOverlay portrait;

    {
        // 截图/自测：-Dsalvosequence.portrait=true 开局就显示（不设属性时不建，零开销）
        if (Boolean.getBoolean("salvosequence.portrait")) {
            portrait = new PortraitOverlay();
            portrait.setVisible(true);
        }
    }

    /**
     * F12：没有就建（建出来直接显示），有就翻转。
     *
     * <p>自测/截图用的 {@code -Dsalvosequence.portrait=true} 在字段初始化时就把立绘
     * 建好并显示——这个开关只影响"开局是否可见"，开关逻辑和 F12 完全一样。</p>
     */
    private void togglePortrait() {
        if (portrait == null) {
            portrait = new PortraitOverlay();
            portrait.setVisible(true);
        } else {
            portrait.toggle();
        }
    }

    /** 每帧调用：画当前界面 + 后处理。 */
    public void draw(CanvasBuffer buffer) {
        current.draw(buffer);
        drawBackButton(buffer);
        if (debugVisible) {
            drawDebug(buffer);
        }
        // 彩蛋画在界面内容之上：它是"站在场景里的人"，但过渡遮罩仍然盖住它
        if (portrait != null) {
            portrait.draw(buffer.art());
        }
        if (fade < 1) {
            drawFade(buffer);
        }
    }

    /** 当前界面是否要自己接管这个键（全局热键之前先问一句）。 */
    public boolean consumesKey(String code) {
        // F12 是全局彩蛋开关，不归任何界面管
        return "F12".equals(code) || current.consumesKey(code);
    }

    /** 处理按键；返回 true 表示该按键已被界面消费。 */
    public boolean handleKey(String code) {
        switch (code) {
            case "F1" -> {
                debugVisible = !debugVisible;
                return true;
            }
            case "F12" -> {
                // 全屏人物立绘，所有界面通用
                togglePortrait();
                return true;
            }
            case "F4" -> {
                toggleOverlay();
                return true;
            }
            case "F6" -> {
                go(ScreenContext.Target.BACKDROP_LAB);
                return true;
            }
            case "F5" -> {
                go(ScreenContext.Target.GLEBA);
                return true;
            }
            case "C" -> {
                // 组舰者玩法循环：任何界面按 C 都能进去（再按一次无效，因为同类界面不重进）
                go(ScreenContext.Target.CARD);
                return true;
            }
            default -> {
                // 继续交给界面
            }
        }
        ScreenContext context = current.onKey(code);
        if (context != null) {
            handle(context);
            return true;
        }
        return false;
    }

    private void handle(ScreenContext context) {
        if (context.target() == ScreenContext.Target.QUIT) {
            quitHandler.run();
            return;
        }
        requestSwitch(context.target());
    }

    /**
     * F4：在"当前界面"和"素材实验室"之间来回切。
     *
     * <p>这不是普通的界面跳转：实验室属于覆盖式界面，退出时要回到
     * 进来之前那一屏（战斗中打的仗不能被它打断），所以进出都记着 {@link #screenBehind}。
     * 实验室实例本身只建一次，这样反复翻看时选中的角色和帧位置都保留。</p>
     */
    private void toggleOverlay() {
        if (current instanceof ToggleScreen) {
            Screen back = screenBehind;
            screenBehind = null;
            if (back != null) {
                switchTo(back);
            }
            return;
        }
        screenBehind = current;
        switchTo(spriteLab);
    }

    /** 直接切到某个已有实例（用于覆盖式界面进出，不走 normalize）。 */
    private void switchTo(Screen next) {
        if (next == null || next == current) {
            return;
        }
        releasePointer();   // F4 翻覆盖层同理：手还按着也别把拖动带过去
        pending = next;
        fade = 1;   // 先淡出
    }

    private void requestSwitch(ScreenContext.Target target) {
        releasePointer();   // 换界面时手还按着也不能把拖动带到下一屏去
        Screen next = switch (target) {
            case START -> new StartScreen();
            case CARD -> loopScreen = new CardLoopScreen();
            case DESIGNER -> new ShipDesignerScreen();
            case ARRANGE -> new FleetArrangementScreen();
            // 关卡内休整时调编队：同一套界面，改的是当前这局的舰队
            case ARRANGE_RUN -> loopScreen == null ? null : new FleetArrangementScreen(loopScreen.run().fleet());
            // 从编排回到那局：复用同一个实例，Run 不会被重开
            case CARD_RESUME -> loopScreen;
            case BACKDROP_LAB -> new com.hysx.salvosequence.screen.BackdropLabScreen();
            case GLEBA -> new com.hysx.salvosequence.screen.GlebaScreen();
            case QUIT -> null;
        };
        if (next == null || next.getClass() == current.getClass()) {
            return;
        }
        pending = next;
        fade = 1;   // 先淡出
    }

    /** 把当前界面的热区定义重建一遍（换界面 / 界面状态变化时）。 */
    private void rebuildHotspots() {
        hotspots.clear();
        // 老界面随层一起消失：JavaFX 对被摘掉的 Region 不补发 exited，这里同样静默。
        // hovered 丢了无妨（下次 mouseMoved 重新建立）；【不能】清 pressed/activeDrag——
        // 星图平移每帧 touch() 重建热区，拖动手势必须跨越重建（等价原版场景级拖动）。
        hovered = -1;
        current.collectHotspots(this);
        // 全局返回键最后加：命中测试从后往前，界面自己在同一块地方登记了热区时以它为优先。
        add(BACK_X, BACK_Y, BACK_W, BACK_H, this::handleBack);
        updateCursor();
    }

    // ------------------------------------------------------------ 热区登记

    @Override
    public void add(double x, double y, double w, double h, Runnable action) {
        hotspots.add(new Hotspot(x, y, w, h, KIND_CLICK,
                action, null, null, null, null, null, null));
    }

    @Override
    public void hover(double x, double y, double w, double h, Runnable action,
                      Runnable onEnter, Runnable onExit) {
        hotspots.add(new Hotspot(x, y, w, h, KIND_HOVER,
                action, onEnter, onExit, null, null, null, null));
    }

    @Override
    public void track(double x, double y, double w, double h,
                      java.util.function.BiConsumer<Double, Double> onMove,
                      Runnable action, Runnable onEnter, Runnable onExit) {
        hotspots.add(new Hotspot(x, y, w, h, KIND_TRACK,
                action, onEnter, onExit, onMove, onMove, null, null));
    }

    @Override
    public void drag(double x, double y, double w, double h,
                     java.util.function.BiConsumer<Double, Double> onDrag,
                     java.util.function.BiConsumer<Double, Double> onPress) {
        hotspots.add(new Hotspot(x, y, w, h, KIND_DRAG,
                null, null, null, null, onDrag, onPress, null));
    }

    @Override
    public void draw(double x, double y, double w, double h,
                     java.util.function.BiConsumer<Double, Double> onMove,
                     java.util.function.BiConsumer<Double, Double> onDrag,
                     java.util.function.BiConsumer<Double, Double> onPress,
                     Runnable onEnter, Runnable onExit) {
        draw(x, y, w, h, onMove, onDrag, onPress, onEnter, onExit, null);
    }

    @Override
    public void draw(double x, double y, double w, double h,
                     java.util.function.BiConsumer<Double, Double> onMove,
                     java.util.function.BiConsumer<Double, Double> onDrag,
                     java.util.function.BiConsumer<Double, Double> onPress,
                     Runnable onEnter, Runnable onExit, Runnable onRelease) {
        hotspots.add(new Hotspot(x, y, w, h, KIND_DRAW,
                null, onEnter, onExit, onMove, onDrag, onPress, onRelease));
    }

    // ------------------------------------------------------------ 指针事件（设计坐标）

    /** 从后往前找最上层命中（后登记的在上层，与原节点树一致）。 */
    private int hit(double dx, double dy) {
        for (int i = hotspots.size() - 1; i >= 0; i--) {
            if (hotspots.get(i).contains(dx, dy)) {
                return i;
            }
        }
        return -1;
    }

    /** 鼠标移动（未按键）：推进 enter/exit，TRACK/DRAW 回调指针位置。 */
    public boolean mouseMoved(double dx, double dy) {
        int found = hit(dx, dy);
        if (found != hovered) {
            if (hovered >= 0 && hovered < hotspots.size()) {
                Runnable onExit = hotspots.get(hovered).onExit();
                if (onExit != null) {
                    onExit.run();
                }
            }
            hovered = found;
            if (hovered >= 0) {
                Runnable onEnter = hotspots.get(hovered).onEnter();
                if (onEnter != null) {
                    onEnter.run();
                }
            }
            updateCursor();
        }
        if (hovered >= 0) {
            Hotspot h = hotspots.get(hovered);
            if (h.onMove() != null && (h.kind() == KIND_TRACK || h.kind() == KIND_DRAW)) {
                h.onMove().accept(dx, dy);
            }
            return true;
        }
        return false;
    }

    /** 按下：DRAG/DRAW 的手势从这里开始，之后的所有拖动都归属这个热区。 */
    public boolean touchDown(double dx, double dy) {
        pressed = hit(dx, dy);
        if (pressed < 0) {
            activeDrag = null;
            return false;
        }
        activeDrag = hotspots.get(pressed);
        Hotspot h = hotspots.get(pressed);
        if ((h.kind() == KIND_DRAG || h.kind() == KIND_DRAW) && h.onPress() != null) {
            dragEvents = 0;
            pressDesignX = dx;
            pressDesignY = dy;
            trace(String.format("press  design %.2f,%.2f  scale %.4f  rect %.0f,%.0f",
                    dx, dy, scale, h.x(), h.y()));
            h.onPress().accept(dx, dy);
        }
        updateCursor();
        return true;
    }

    /** 拖动（有键按着的移动）：事件固定派给按下时抓走的热区快照——
     *  热区整层重建（星图平移每帧一次）也不断手。 */
    public boolean touchDragged(double dx, double dy) {
        Hotspot h = activeDrag;
        if (h == null) {
            return false;
        }
        if (h.onDrag() != null) {
            dragEvents++;
            trace(String.format("dragS%d  design %.2f,%.2f  Δdesign %.2f,%.2f  scale %.4f",
                    dragEvents, dx, dy, dx - pressDesignX, dy - pressDesignY, scale));
            h.onDrag().accept(dx, dy);
        }
        return true;
    }

    /** 松手：点击 = 按下与松开落在同一块热区（按快照矩形判定，重建不影响）；拖拽类在这里收尾。 */
    public boolean touchUp(double dx, double dy) {
        Hotspot h = activeDrag;
        pressed = -1;
        activeDrag = null;
        updateCursor();
        if (h == null) {
            return false;
        }
        trace(String.format("release events=%d  Δdesign %.2f,%.2f  (scale %.4f)",
                dragEvents, dx - pressDesignX, dy - pressDesignY, scale));
        if ((h.kind() == KIND_CLICK || h.kind() == KIND_HOVER || h.kind() == KIND_TRACK)
                && h.contains(dx, dy) && h.action() != null) {
            h.action().run();
        }
        if ((h.kind() == KIND_DRAW || h.kind() == KIND_DRAG) && h.onRelease() != null) {
            h.onRelease().run();
        }
        // 松手后按当前位置重算悬浮（JavaFX 会重新派发 entered/exited）。
        // 触摸设备例外：重算等于"轻点一下就悬浮"，描述会一点就弹出来——触摸屏上
        // 悬浮只能由长按（手势层直接调 mouseMoved）产生，所以这里把悬浮收回去。
        if (touchHover) {
            clearHover();
        } else {
            mouseMoved(dx, dy);
        }
        return true;
    }

    /** 滚轮转发给当前界面。 */
    public void handleScroll(double delta, double x, double y) {
        current.onScroll(delta, x, y);
    }

    /**
     * 作废当前按下：第二根手指落下（要开始双指缩放）、长按看过之后再松手。
     *
     * <p>和 {@link #touchUp} 的区别只有一个——<b>不触发点击</b>。拖动/绘制类手势
     * 仍然收尾（{@code onRelease}），否则设计器里的笔画会一直挂着。</p>
     */
    @Override
    public void cancelPointer() {
        Hotspot h = activeDrag;
        pressed = -1;
        activeDrag = null;
        clearHover();
        updateCursor();
        if (h != null && (h.kind() == KIND_DRAW || h.kind() == KIND_DRAG) && h.onRelease() != null) {
            h.onRelease().run();
        }
    }

    /**
     * 收回悬浮：给当前悬浮的热区补发 exited，弹窗/高亮随之消失。
     *
     * <p>和 {@link #mouseMoved} 里的退出逻辑同口径，只是不重新命中。</p>
     */
    private void clearHover() {
        if (hovered >= 0 && hovered < hotspots.size()) {
            Runnable onExit = hotspots.get(hovered).onExit();
            if (onExit != null) {
                onExit.run();
            }
        }
        hovered = -1;
        updateCursor();
    }

    /**
     * 安卓返回键：先当 ESC 用（关设置/返回上一屏）；标题页且设置没开就直接退出。
     *
     * <p>桌面没有这个键（{@code Input.Keys.BACK} 只在安卓/浏览器后端出现），
     * 所以不影响桌面手感。</p>
     */
    public boolean handleBack() {
        ScreenContext context = current.onKey("ESCAPE");
        if (context != null) {
            handle(context);
            return true;
        }
        if (current instanceof StartScreen start && !start.isSettingsOpen()) {
            quitHandler.run();
        }
        return true;
    }

    /** 换界面时把指针状态一并清掉（拖动手势不允许跨界面存活）。 */
    private void releasePointer() {
        hovered = -1;
        pressed = -1;
        activeDrag = null;
        updateCursor();
    }

    /** 光标口径：拖动手势期间十字（原 beginSceneDrag），悬浮可点用手，其余箭头。 */
    private void updateCursor() {
        if (Gdx.app == null || Gdx.graphics == null) {
            return;
        }
        int kind = activeDrag != null ? activeDrag.kind()
                : hovered >= 0 && hovered < hotspots.size() ? hotspots.get(hovered).kind() : -1;
        Cursor.SystemCursor want = switch (kind) {
            case KIND_CLICK, KIND_HOVER, KIND_TRACK, KIND_DRAG -> Cursor.SystemCursor.Hand;
            case KIND_DRAW -> Cursor.SystemCursor.Crosshair;
            default -> Cursor.SystemCursor.Arrow;
        };
        if (want != lastCursor) {
            Gdx.graphics.setSystemCursor(want);
            lastCursor = want;
        }
    }

    private Cursor.SystemCursor lastCursor = Cursor.SystemCursor.Arrow;

    // ---------------------------------------------------------------- 拖动诊断

    /** 拖动诊断：每次事件的坐标都记一行，同时打进 drag-trace.txt 与控制台（`-Dcard.trace=off` 关掉）。 */
    private static final java.nio.file.Path TRACE_FILE = java.nio.file.Path.of("drag-trace.txt");
    private static final boolean TRACE_ON = !"off".equalsIgnoreCase(System.getProperty("card.trace", "on"));
    private static int traceLines;
    private static double pressDesignX;
    private static double pressDesignY;
    private static int dragEvents;

    /**
     * 追一行拖动诊断到 {@code drag-trace.txt}。
     *
     * <p>拖船"跟手比例不对"这种反馈，光看代码看不出来，但把每次事件的
     * 坐标、缩放连起来一算，比例就一目了然。</p>
     *
     * @param line 一行文本
     */
    public static void trace(String line) {
        if (!TRACE_ON || traceLines > 6000) {
            return;
        }
        traceLines++;
        System.out.println("[drag] " + line);
        try {
            // 不用 Files.writeString：Android 的 libcore 里没有这个方法（真机 NoSuchMethodError）
            java.nio.file.Files.write(TRACE_FILE,
                    (line + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException ignored) {
            // 诊断用，写不了就算了
        }
    }

    /** 清空拖动诊断（进战斗界面时调一次）。 */
    public static void traceReset() {
        if (!TRACE_ON) {
            return;
        }
        traceLines = 0;
        try {
            java.nio.file.Files.write(TRACE_FILE, new byte[0],
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        } catch (java.io.IOException ignored) {
            // 诊断用，写不了就算了
        }
    }

    public double originX() { return originX; }
    public double originY() { return originY; }
    public double scale() { return scale; }

    // ------------------------------------------------------------------ 后处理

    /**
     * 左下角的全局返回键。
     *
     * <p>安卓上系统返回键随时可达，但全屏沉浸模式下没有手势条可摸；桌面版也没有
     * 统一的退出口。这里给所有界面铺一枚"返回"，看得见就点得到。它画在界面内容
     * 之上、调试读数与淡入淡出之下，所以换屏时跟着一起淡。</p>
     */
    private void drawBackButton(CanvasBuffer buffer) {
        buffer.art().fillRect(BACK_X, BACK_Y, BACK_W, BACK_H, Palette.PANEL);
        buffer.art().frame(BACK_X, BACK_Y, BACK_W, BACK_H, Palette.LINE_DIM);
        String label = Language.t("返回", "BACK");
        buffer.textCentered(label, BACK_X + BACK_W / 2.0, BACK_Y + 8, 12, Palette.LINE);
    }

    private void drawDebug(CanvasBuffer buffer) {
        int x = 8;
        int y = 44;
        buffer.art().rect(x - 3, y - 9, 236, 56, com.hysx.salvosequence.core.Color.color(0, 0, 0, 0.75));
        buffer.art().frame(x - 3, y - 9, 236, 56, Palette.CYAN);
        buffer.text(Language.t("界面 ", "SCREEN ") + current.name(), x, y + 4, 12, Palette.CYAN);
        buffer.text(String.format("scale %.2f  fade %.2f", scale, fade), x, y + 15, 10,
                Palette.LINE);
        buffer.text(Language.t("F1 调试 · 画质在设置里调", "F1 DEBUG · QUALITY IN SETTINGS"),
                x, y + 25, 10, Palette.LINE_DIM);
        buffer.text(Language.t("回车/点击 选择 · ESC 返回", "ENTER/CLICK SELECT · ESC BACK"),
                x, y + 35, 10, Palette.LINE_DIM);
    }

    /** 界面切换时的淡入淡出。 */
    private void drawFade(CanvasBuffer buffer) {
        double opacity = 1.0 - Math.max(0, Math.min(1, fade));
        if (opacity <= 0.001) {
            return;
        }
        com.hysx.salvosequence.core.Color black =
                com.hysx.salvosequence.core.Color.color(0, 0, 0, opacity);
        buffer.art().rect(0, 0, 640, 360, black);
    }

    // ------------------------------------------------------------------ 读数

    public boolean isScanlineEnabled() {
        return VideoSettings.scanlines();
    }

    public void setScanlineEnabled(boolean enabled) {
        VideoSettings.setScanlines(enabled);
    }

    public boolean isApertureGrilleEnabled() {
        return VideoSettings.apertureGrille();
    }

    public void setApertureGrilleEnabled(boolean enabled) {
        VideoSettings.setApertureGrille(enabled);
    }

    public boolean isDebugVisible() {
        return debugVisible;
    }

    public void setDebugVisible(boolean visible) {
        this.debugVisible = visible;
    }

    public double fade() {
        return fade;
    }

    /** 强制完成当前过渡（离屏核对时用，避免截到淡出中的画面）。 */
    public void finishTransition() {
        if (pending != null) {
            current.onExit();
            current = pending;
            pending = null;
            current.onEnter();
            rebuildHotspots();
        }
        fade = 1;
        fadingIn = false;
    }

    // ------------------------------------------------------------------ 对外接口

    /** 当前界面名。 */
    public String currentName() {
        return current.name();
    }

    /** 当前界面（只读，便于外部取数据）。 */
    public Screen current() {
        return current;
    }

    /** 主动切到某个界面。 */
    public void go(ScreenContext.Target target) {
        requestSwitch(target);
    }
}
