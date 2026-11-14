package com.hysx.salvosequence.screen;

import com.hysx.salvosequence.audio.Music;
import com.hysx.salvosequence.audio.Sound;
import com.hysx.salvosequence.combat.Battle;
import com.hysx.salvosequence.combat.Enemy;
import com.hysx.salvosequence.combat.EnemyCatalog;
import com.hysx.salvosequence.combat.EnemyFormation;
import com.hysx.salvosequence.core.Backdrops;
import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.core.Color;
import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.core.PixelFont;
import com.hysx.salvosequence.core.PngLoader;
import com.hysx.salvosequence.core.StarDisk;
import com.hysx.salvosequence.core.UiChrome;
import com.hysx.salvosequence.core.UiTilt;
import com.hysx.salvosequence.deck.Card;
import com.hysx.salvosequence.deck.CardKind;
import com.hysx.salvosequence.deck.CardCatalog;
import com.hysx.salvosequence.fleet.FactionCatalog;
import com.hysx.salvosequence.fleet.Fleet;
import com.hysx.salvosequence.fleet.FleetBoard;
import com.hysx.salvosequence.fleet.FleetRoster;
import com.hysx.salvosequence.fleet.FleetUnit;
import com.hysx.salvosequence.fleet.Perk;
import com.hysx.salvosequence.fleet.Ship;
import com.hysx.salvosequence.fleet.ShipCatalog;
import com.hysx.salvosequence.render.HotspotSink;
import com.hysx.salvosequence.run.Pilot;
import com.hysx.salvosequence.run.Reward;
import com.hysx.salvosequence.run.Captain;
import com.hysx.salvosequence.run.Run;
import com.hysx.salvosequence.ui.Language;
import com.hysx.salvosequence.ui.ScreenManager;
import com.hysx.salvosequence.ui.VideoSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * 组舰者主循环：一支舰队从第一关打到最后一关。
 *
 * <h2>版面（改造后）</h2>
 * <p>顶部通栏读数，下面<b>左右两块等大的 16×16 棋盘</b>：<b>左 = 我方舰队，右 = 敌方舰队</b>。
 * 两块用的是同一个画法（{@link ShipPainter}）：我方读玩家画的外观图，敌方读同一套
 * 出厂涂装的红系重映射，所以"敌方看起来和我方一样"是结构上的，不是两套代码。</p>
 *
 * <p>改造前这里是"左边一张只有方块、没有外观的敌方棋盘 + 右边一块模块面板"，
 * 而模块面板画的是一艘 16×16 船体上的零件。现在两边都是舰队，各自把船画在自己的格子里。</p>
 *
 * <p>右列自上而下：敌方编队列表、日志、读数、技能/结束回合按钮；底部左侧是方形卡格，
 * 右下是说明面板。</p>
 */
public final class CardLoopScreen implements Screen {

    public static final String SCREEN_NAME = "CARDLOOP";

    // ---------------------------------------------------------------- 版面

    private static final int BAR_X = 4;
    private static final int BAR_Y = 3;
    private static final int BAR_H = 16;

    private static final int BOARD_CELL = 16;
    private static final int BOARD_SPAN = FleetBoard.SIZE * BOARD_CELL;   // 256

    /**
     * 全局版面偏移：战斗 / 战术休整 / 太空港三屏的棋盘和面板都从这一组值推出来，
     * 想让整块版面一起挪，只改这两个数就够了。
     */
    private static final int LAYOUT_DX = 0;
    private static final int LAYOUT_DY = 0;

    /**
     * 棋盘的顶边不贴屏幕顶：整块棋盘要绕画布中心倾斜，留出这段余量，
     * 左上角转上去才不会撞到屏幕外被切掉。
     */
    private static final int BOARD_Y = 6 + LAYOUT_DY;
    /** 我方棋盘（三屏同一个位置）：左边 0~126 留给面板、技能、跳过。 */
    private static final int OUR_BOARD_X = 126 + LAYOUT_DX;
    /** 敌方棋盘（战斗）／面板（休整、太空港）的左缘：右缘统一留 2px。 */
    private static final int ENEMY_BOARD_X = 383 + LAYOUT_DX;
    /** 战术休整/太空港只有一块盘面，用的就是战斗里我方棋盘的位置。 */
    private static final int PORT_BOARD_X = OUR_BOARD_X;
    /** 战术休整/太空港的面板占的是战斗里敌方棋盘那一块。 */
    private static final int PORT_PANEL_X = ENEMY_BOARD_X;
    private static final int PORT_PANEL_W = 640 - 2 - PORT_PANEL_X;

    private static final int FOE_PANEL_X = 428;
    private static final int FOE_PANEL_Y = 20;
    private static final int FOE_PANEL_W = 208;
    private static final int FOE_PANEL_H = 102;
    private static final int FOE_ROW_H = 32;
    private static final int FOE_ROW_GAP = 2;

    private static final int LOG_X = 300;
    private static final int LOG_Y = 280;
    private static final int LOG_LINES = 7;
    private static final int LOG_PITCH = 11;

    /** 状态栏挪到左栏（棋盘挪到右边之后，左栏正好在人物立绘前面）。 */
    private static final int STATUS_X = 6;
    private static final int STATUS_Y = 22;
    private static final int STATUS_PITCH = 11;

    private static final int BTN_X = 6;
    private static final int BTN_W = 110;
    private static final int BTN_H = 16;
    private static final int SKILL_BTN_Y = 72;
    private static final int TURN_BTN_Y = 94;

    // ---- 新版战斗界面（按参考图重排）----------------------------------
    /** 左上信息框（航程 / 双方结构值 / 设置齿轮）。 */
    private static final int PANEL_W = 142;
    private static final int PANEL_H = 67;
    /** 梯形面板右下角的顶点（上缘通到 PANEL_W，下缘收到这里）。 */
    private static final int PANEL_TRAP_X = 75;
    private static final int GEAR_X = 94;
    private static final int GEAR_Y = 6;
    private static final int GEAR_S = 18;
    private static final int ROUTE_Y = 65;
    /** 左上角两条结构条的第一行（8px 拉丁小字的顶端）。 */
    private static final int VITALS_Y = 24;

    /** 舰长技能：底部指令台左侧操作轨上的大菱形按钮。 */
    private static final int SKILL_CX = 21;
    private static final int SKILL_CY = 288;
    private static final int SKILL_R = 17;
    /** 舰船技：技能菱形右边的小菱形（有舰船技时才画）。 */
    private static final int SHIP_CX = 70;
    private static final int SHIP_CY = 296;
    private static final int SHIP_R = 13;

    /** 跳过回合：菱形按钮，与舰长技同一套结构（外框是 32×30 的热区，菱形 r=14）。
     *  放在操作轨最下面一格，能量点因此挪到中间。 */
    private static final int SKIP_X = 5;
    private static final int SKIP_Y = 327;
    private static final int SKIP_W = 32;
    private static final int SKIP_H = 30;
    private static final int SKIP_R = 14;

    /** 剩余能量：操作轨中间一排菱形点，水平按 {@link #RAIL_X} 居中。 */
    private static final int ENERGY_CX = 21;
    private static final int ENERGY_Y = 317;

    /** 底部指令台：整块板托住操作轨和手牌，不再让卡片浮在星云上。 */
    private static final int CONSOLE_X = 0;
    private static final int CONSOLE_Y = 268;
    private static final int CONSOLE_W = 332;
    private static final int CONSOLE_H = 92;
    /** 操作轨与手牌区之间的竖隔断。 */
    private static final int RAIL_X = 41;

    /** 指令卡：长条，3 列 × 3 行。 */
    private static final int CARD_X = 45;
    private static final int CARD_Y = 285;
    private static final int CARD_W = 93;
    private static final int CARD_H = 21;
    /**
     * 卡牌的位置：指令台里排成三列三行（左边 41px 是操作轨，卡片从 45 起）。
     * 三列 93px、列距 3px，正好落在 45~330 之间。
     */
    private static final int[][] CARD_SLOTS = {
            {45, 285}, {141, 285}, {237, 285},
            {45, 309}, {141, 309}, {237, 309},
            {45, 333}, {141, 333}, {237, 333},
    };
    private static final int CARD_MAX = CARD_SLOTS.length;
    /** 四角斜切：每个角切掉腰长 6 的三角形（21px 高的条不能切太深，否则两头收成尖）。 */
    private static final int CARD_CHAMFER = 6;

    /** 右下读数据：双方结构条 + 一行属性 + 航程 + 战报（没有铭牌）。 */
    private static final int INFO_X = 334;
    private static final int INFO_Y = 268;
    private static final int INFO_W = 304;
    private static final int INFO_H = 92;

    /** 描述弹窗宽度：跟随鼠标半屏换边（左半 → 右侧弹出，右半 → 左侧弹出）。 */
    private static final int DESC_W = 232;

    // ---------------------------------------------------------------- 星图

    /** 恒星名标签字号：像素字体原生 12px 点阵，再小就丢字形。 */
    private static final double STAR_NAME_SIZE = 12;
    /** 节点类型标签（战斗/精英/…）比名字小一号，避免抢视线。 */
    private static final double STAR_TYPE_SIZE = 12;
    /** 左右拖动灵敏度：1 设计像素转多少度。0.5° 时拖 720px 正好转一圈。 */
    private static final double STAR_DRAG_DEG_PER_PIXEL = 0.5;

    /** 盘面上闪烁星的个数与最大盘面半径（半径 246 是节点最外圈，星星铺满整个盘面）。 */
    private static final int DISK_TWINKLE_COUNT = 220;
    private static final double DISK_TWINKLE_RADIUS = 252;
    /** 核心屏障半径（盘面像素）：屏障内是太阳系，不放闪烁星，免得盖住起始星与太阳标记。 */
    private static final double CORE_BARRIER_R = 30;
    /** 星区引线的锚点半径（盘面像素）：落在星盘外缘，引线从盘边伸出来而不是穿过亮核。 */
    private static final double ZONE_ANCHOR_R = 236;
    /** 星区名在左右两侧的位置：文字靠边、引线停在文字外侧 5px，两组常量绘制和居中共用。 */
    private static final int ZONE_TEXT_LEFT = 100;
    private static final int ZONE_WIRE_LEFT = 112;
    private static final int ZONE_TEXT_RIGHT = 540;
    private static final int ZONE_WIRE_RIGHT = 528;
    /** 引线标签的上下边界与最小行距：星盘转过来时两枚标签不许叠在一起。 */
    private static final int ZONE_LABEL_TOP = 44;
    private static final int ZONE_LABEL_BOTTOM = 300;
    private static final int ZONE_LABEL_GAP = 16;
    /** 引线几何：出盘短桩 10px，45° 折 4px，折后水平跑 18px（整条线读起来像电路走线）。 */
    private static final int ZONE_STUB = 10;
    private static final int ZONE_JOG = 4;
    private static final int ZONE_JOG_RUN = 18;
    /** 拖动方向：+1 = 向右拖时旋转角增大，-1 = 反向。 */
    private static final double STAR_DRAG_DIRECTION = -1.0;
    /**
     * 快速拖动时星盘底图最短换图间隔（秒）。
     *
     * <p>背景是 {@code mapback/} 里 1440 张 640×360 的 PNG，角度一变就得换一张：
     * 后台线程解码（约 3ms/张）+ 整幅重贴。按住拖动时指针事件比帧还密，
     * 每帧换一张会把解码队列压满、拖动明显发涩。这里把「显示角度」的提交
     * 限到 10 次/秒（松手时强制对齐），背景最多换 10 次，拖动跟手不受影响。</p>
     */

    private static final int OV_X = 96;
    private static final int OV_Y = 76;
    private static final int OV_W = 448;
    private static final int OV_ROW_H = 30;
    private static final int OV_ROW_GAP = 5;
    /** 选舰队行更高：要塞一句主角/舰队描述。 */
    private static final int PICK_ROW_H = 56;

    // ---------------------------------------------------------------- 状态

    private final Run run;
    private final PixelFont font = new PixelFont();
    private boolean debug = !"off".equalsIgnoreCase(System.getProperty("card.debug", "on"));

    private int cursor;
    private int slotPreview;
    private boolean hotspotsDirty;
    private int hoveredCard = -1;
    /** 说明浮牌这次翻到了卡片下方（上方放不下时）。 */
    private boolean cardPopupBelow;
    private int hoveredOption = -1;
    private boolean hoveredSkill;
    /** 左上设置齿轮 / 左下跳过回合 是否被指到。 */
    private boolean hoveredGear;
    private boolean hoveredTurn;
    private int hoveredOurShip = -1;
    private int hoveredFoe = -1;
    private int spacePortTab = 0;
    /** 融合 tab：光标与已选的第一艘船（-1 = 未选）。 */
    private int fuseCursor;
    private int fuseFrom = -1;
    /** 星图全屏模式（太空港星图 tab 里按 M 或点放大进入）。 */
    private boolean fullStarMap;
    /** 星盘旋转角（弧度，0~2π）：左右拖动改变它，背景按 0.25° 一档换帧。 */
    /** 实时旋转角：节点、连线、角度读数都用它，每个拖动事件都更新。 */
    private double starDiskAngle;

    /** 实时背景压暗后的 640×360 合成帧，以及它对应的源帧/压暗系数（源帧没换就整幅复用）。 */
    private int[] liveBackdropDrawn;
    private int[] liveBackdropSource;
    private int liveBackdropDim = -1;

    /**
     * 这一场战斗随机到的天体底图（0..3）与掷骰时的关卡/节点：换关或进新节点才重掷，
     * 一场仗从头到尾只换一次背景。{@code -1} = 还没掷过。
     */
    private int liveScene = -1;
    private int liveSceneNode = Integer.MIN_VALUE;
    private int liveSceneVisits = -1;
    private java.util.Random liveSceneRng;

    /** 盘面闪烁星：{theta, radius, phase, speed, brightness} × N，构造一次，之后只按时间求亮度。 */
    private double[][] diskTwinkles;
    /** 上一帧指针位置（设计坐标）：拖动旋转星盘用。 */
    private double starPanX;
    private double starPanY;
    /** 太阳标记（按当天真实赤经/赤纬投影到星图，F10 隐藏/显示）。 */
    private boolean sunVisible = true;
    /** 设置面板：战斗/星图里按 ESC 打开，含音乐/音效开关、返回标题、退出游戏。 */
    private boolean settingsOpen;
    private int settingsCursor;
    /** 设置项：0=继续 1=音乐 2=音效 3=CRT 4=格栅图案 5=屏幕扭曲 6=辉光 7=混合 K 滑条 8=背景滤镜 9=视差 10=透视 11=语言 12=返回标题 13=退出游戏。 */
    private static final int SETTINGS_ITEMS = 14;
    /** 混合 K 那一行：唯一一根滑条，点/拖/左右键都改 {@link VideoSettings#mixK()}。 */
    private static final int SETTINGS_MIX_ROW = 7;
    /** 设置面板与行距：绘制和热区共用，之前两边面板高度不同（300 / 340），热区整体错开一行。 */
    private static final int SETTINGS_W = 268;
    private static final int SETTINGS_H = 340;
    private static final int SETTINGS_ROW_TOP = 40;
    private static final int SETTINGS_ROW_H = 20;
    /** 滑条轨道：行内左让出标签、右边留给数值和行号，轨道本身 96px 宽。 */
    private static final int SETTINGS_SLIDER_X = 104;
    private static final int SETTINGS_SLIDER_W = 96;
    /** F2 背景菜单：8 个场景一屏列完，行高与面板宽沿用设置面板的节奏。 */
    private static final int BACKDROP_MENU_W = 328;
    private static final int BACKDROP_MENU_ROW_H = 22;
    private boolean backdropMenuOpen;
    private int backdropMenuScene;
    private int backdropMenuPreset;
    /** 打开菜单前的机位下标（-1 = 随机），Esc 关掉时恢复原样。 */
    private int backdropMenuSavedPreset = -1;
    private int backdropMenuSavedScene;

    private static int settingsX() {
        return (640 - SETTINGS_W) / 2;
    }

    private static int settingsY() {
        return (360 - SETTINGS_H) / 2;
    }
    /** 主题背景静态层（Backdrops：10 主题 × 4 变体，按节点确定性选播）。 */
    private int[] backdropLayer;
    private int backdropKey = -1;
    /** 星系层缓存（560x350 ARGB）：渐变盘 + 沿臂辉光，一次生成。
     * 星尘不烘进来——放大时缓存会变成 3x3 色块，改成逐帧粒子（见 buildDust）。 */
    /** 星尘粒子数上限：与旧缓存里的星尘数量一致。 */
    private static final int DUST_COUNT = 8000;
    /** 星星“最大缩放形状”：7 种形状 × 边长 1..5，格值 0=空 / 1=暗 / 2=中 / 3=亮。
     * 每种形状只在最大倍率下取完整边长，低倍率时按 z 缩小采样，形状连续缩放、不切换图层。 */
    private static final int STAR_SHAPE_COUNT = 7;
    private static final int STAR_SHAPE_MAX = 5;
    /** 闪烁亮度表：0..255 相位映射 60~125 的亮度百分比，按时间步进查表，避免每颗星算 sin。 */
    private static final int[] TWINKLE = buildTwinkleTable();
    /** 闪烁星缓存：旋臂分布的屏幕坐标 {x, y, bright}（远景+近景两层）。 */
    private java.util.List<int[]> twinkleCache;
    /** 闪烁星两层档量：远景暗星打底，近景亮星（前景）更密、随时间闪烁。 */
    private static final int TWINKLE_FAR = 140;
    private static final int TWINKLE_NEAR = 70;
    /** 鼠标最近位置（设计坐标）：描述弹窗跟它换边、贴它垂直居中。 */
    private double mouseX = 320;
    private double mouseY = 180;
    /** 正在拖的那艘船（战斗中变阵）；null 表示手上没抓着东西。 */
    private String dragShipId;
    private double dragOffX;
    private double dragOffY;
    /** 拖动起点：按下时的指针设计坐标（与所在格），松手时用来算"船挪几格 vs 鼠标挪几号"。 */
    private double dragFromMx;
    private double dragFromMy;
    private double lastDragMx;
    private double lastDragMy;
    private int dragFromCellX;
    private int dragFromCellY;
    /** 指针当前压在哪一格（拖动时画一个小框，让"吸附到哪"一眼可见）。 */
    private int dragCellX = -1;
    private int dragCellY = -1;
    private String toast = "";
    private double toastLife;
    /** 本场战斗抽中的机位（场景 + 相机 + 星球类型），换节点时重掷一次。 */
    private com.hysx.salvosequence.live.CameraPresets.Preset livePreset;
    /** 调试键 F2 钉死的机位下标（-1 = 没钉）。 */
    private int debugPresetIndex = -1;
    private double time;
    /** 锁定动画：目标一变（进战斗 / 打沉换目标 / 手动切换）就从四角射一次箭矢。 */
    private Battle lockBattle;
    private int lockAnimTarget = -1;
    private double lockAnimStart = -1000;
    /** 被敌方锁定的我方船：前排（x 最大）换人时重播一次被锁定动画。 */
    private String foeLockShipId;
    private double foeLockStart = -1000;
    private boolean zoomShip;
    private ScreenContext pendingTransition;

    public CardLoopScreen() {
        this(new Run(initialSeed()));
    }

    public CardLoopScreen(Run run) {
        this.run = run;
    }

    private static long initialSeed() {
        String prop = System.getProperty("card.seed", System.getenv("CARD_SEED"));
        if (prop != null && !prop.trim().isEmpty()) {
            try {
                return Long.parseLong(prop.trim());
            } catch (NumberFormatException e) {
                // 参数坏了就用随机种子
            }
        }
        return System.nanoTime() & 0x7FFF_FFFFL;
    }

    @Override
    public String name() {
        return SCREEN_NAME;
    }

    /** 这一局。 */
    public Run run() {
        return run;
    }

    @Override
    public void onEnter() {
        ScreenManager.traceReset();
        hotspotsDirty = true;
        ShipPainter.reload();
    }

    private Run.Stage lastStage;

    @Override
    public void update(double dt) {
        time += dt;
        syncLockAnimation();
        if (toastLife > 0) {
            toastLife -= dt;
        }
        // 阶段切换时重建热区（EVENT→MAP 等场景，旧选项热区不残留）
        if (lastStage != run.stage()) {
            lastStage = run.stage();
            touch();
        }
    }

    @Override
    public ScreenContext pollTransition() {
        ScreenContext next = pendingTransition;
        pendingTransition = null;
        return next;
    }

    /** 战斗界面不显示全局返回键：左下角那个位置是"跳过回合"的三角按钮。 */
    @Override
    public boolean wantsGlobalBackButton() {
        return stage() != Run.Stage.BATTLE;
    }

    /**
     * 画了棋盘的那几种版面，立绘由本界面在棋盘之后、其余 UI 之前画（棋盘在最底下）。
     *
     * <p>星图整屏、放大舰船、事件界面不画棋盘，交回 {@code ScreenManager} 画在最上层。</p>
     */
    @Override
    public boolean drawsPortraitUnderUi() {
        return !zoomShip && !starMapVisible() && stage() != Run.Stage.EVENT;
    }

    @Override
    public boolean consumeHotspotDirty() {
        boolean dirty = hotspotsDirty;
        hotspotsDirty = false;
        return dirty;
    }

    // ---------------------------------------------------------------- 交互

    /** 战术休整面板里键盘选中的那张牌。 */
    private int lullCursor;

    private Run.Stage stage() {
        return run.stage();
    }

    private boolean overlayStage() {
        return switch (stage()) {
            case PICK_LOADOUT, REWARD, SLOT, WON, LOST -> true;
            case BATTLE, SPACE_PORT, EVENT, MAP, LULL -> false;
        };
    }

    /** 当前阶段能点几项。 */
    /** 测试用：切太空港 tab（0 编组/1 定制/2 培训/3 星图）。 */
    void debugSpacePortTab(int tab) {
        this.spacePortTab = tab;
    }

    int optionCount() {
        return switch (stage()) {
            case PICK_LOADOUT -> run.starters().size();
            case BATTLE -> Math.min(CARD_MAX, hand().size());
            case SPACE_PORT -> 0;
            case EVENT -> 0;
            case MAP -> 0;
            case LULL -> 0;
            case REWARD -> run.rewards().size();
            case SLOT, WON, LOST -> 0;
        };
    }

    private List<Card> hand() {
        return run.battle() == null ? List.of() : run.battle().hand();
    }

    private void activate(int index) {
        switch (stage()) {
            case PICK_LOADOUT -> {
                if (index >= 0 && index < run.starters().size()) {
                    String name = run.starters().get(index).nameZh();
                    run.pickLoadout(index);
                    say("出发：" + name);
                    touch();
                }
            }
            case BATTLE -> {
                if (index >= 0 && index < hand().size()) {
                    boolean ok = run.playCard(index);
                    if (!ok) {
                        say("能量不够，或者这张牌现在打不出去");
                    }
                    cursor = Math.max(0, Math.min(cursor, hand().size() - 1));
                    touch();
                }
            }
            case REWARD -> {
                if (index >= 0 && index < run.rewards().size()) {
                    Reward reward = run.rewards().get(index);
                    run.chooseReward(index);
                    switch (reward.kind()) {
                        case SHIP -> {
                            slotPreview = 0;
                            say("「" + reward.ship().nameZh() + "」编入舰队，挑个位置");
                        }
                        case UPGRADE -> say("「" + reward.ship().nameZh() + "」强化成功");
                        case CARD -> say("卡牌「" + reward.card().nameZh() + "」进卡组");
                    }
                    touch();
                }
            }
            case SLOT -> {
                if (index >= 0 && index < candidates().size()) {
                    slotPreview = index;
                }
            }
            case SPACE_PORT -> { }
            case EVENT -> run.chooseEventOption(index);
            case MAP -> { }
            case WON, LOST -> restart();
        }
    }

    private void departSpacePort() {
        if (stage() == Run.Stage.SPACE_PORT) {
            run.departSpacePort();
            fullStarMap = true;
            touch();
        }
    }

    private void endTurn() {
        if (stage() == Run.Stage.BATTLE) {
            run.endTurn();
            cursor = 0;
            touch();
        }
    }

    private void useSkill() {
        if (stage() == Run.Stage.BATTLE) {
            boolean ok = run.useSkill();
            if (!ok) {
                Battle b = run.battle();
                if (b != null && b.signature() != Captain.Signature.NONE) {
                    say("专属技本场已经用过了");
                } else {
                    say(b != null && b.skillUsed() ? "这一回合已经用过了" : "能量不够用舰长技能");
                }
            }
            touch();
        }
    }

    private void decline() {
        if (stage() == Run.Stage.REWARD || stage() == Run.Stage.SLOT) {
            run.skipReward();
            slotPreview = 0;
            touch();
        }
    }

    private void cycleTarget(int delta) {
        if (stage() == Run.Stage.BATTLE) {
            run.cycleTarget(delta);
            touch();
        }
    }

    private void selectFoe(int index) {
        if (stage() == Run.Stage.BATTLE) {
            run.selectTarget(index);
            touch();
        }
    }

    /**
     * 目标一变就重启锁定动画：进入战斗、打沉后自动换目标、手动点选/左右切换都走这里。
     * 每帧调用，只在 targetIndex 变化时重置计时。
     */
    private void syncLockAnimation() {
        Battle battle = stage() == Run.Stage.BATTLE ? run.battle() : null;
        if (battle != lockBattle) {
            lockBattle = battle;
            lockAnimTarget = -1;
            foeLockShipId = null;
        }
        int target = battle == null ? -1 : battle.targetIndex();
        if (target != lockAnimTarget) {
            lockAnimTarget = target;
            lockAnimStart = time;
        }
        // 敌方锁定的是我方前排（x 最大）：换船（打进下一艘）时重播被锁定动画
        var front = battle == null ? null : battle.fleetRoster().pick(false);
        String frontId = front == null ? null : front.id();
        if (!java.util.Objects.equals(frontId, foeLockShipId)) {
            foeLockShipId = frontId;
            foeLockStart = time;
        }
    }

    private void restart() {
        pendingTransition = ScreenContext.go(ScreenContext.Target.CARD);
    }

    private void say(String message) {
        toast = message;
        toastLife = 2.6;
    }

    private void touch() {
        hotspotsDirty = true;
        hoveredOption = -1;
    }

    // ---------------------------------------------------------------- 落位（编入新船）

    /** 待编入的船的候选落点。 */
    private List<FleetUnit> candidates() {
        return stage() == Run.Stage.SLOT ? run.pendingPlacements() : List.of();
    }

    private FleetUnit previewSpot() {
        List<FleetUnit> spots = candidates();
        if (spots.isEmpty()) {
            return null;
        }
        slotPreview = Math.max(0, Math.min(slotPreview, spots.size() - 1));
        return spots.get(slotPreview);
    }

    /** 点我方棋盘上的一格：落在候选里就选它。 */
    private void clickCell(int cx, int cy) {
        if (stage() != Run.Stage.SLOT) {
            return;
        }
        List<FleetUnit> spots = candidates();
        for (int i = 0; i < spots.size(); i++) {
            if (spots.get(i).covers(cx, cy)) {
                slotPreview = i;
                placePreview();
                return;
            }
        }
        say("这一格放不下「" + run.pending().ship().nameZh() + "」");
    }

    private void placePreview() {
        if (stage() != Run.Stage.SLOT) {
            return;
        }
        FleetUnit spot = previewSpot();
        if (spot == null) {
            return;
        }
        String name = spot.nameZh();
        run.placeAt(slotPreview);
        slotPreview = 0;
        cursor = 0;
        say("「" + name + "」编入舰队");
        touch();
    }

    /** 方向键在候选之间挪预览（优先沿那个方向更近的）。 */
    private void movePreview(int dx, int dy) {
        List<FleetUnit> spots = candidates();
        if (spots.isEmpty()) {
            return;
        }
        FleetUnit current = previewSpot();
        int best = slotPreview;
        int bestScore = Integer.MAX_VALUE;
        for (int i = 0; i < spots.size(); i++) {
            FleetUnit spot = spots.get(i);
            int ox = spot.x() - current.x();
            int oy = spot.y() - current.y();
            if (dx != 0 && Integer.signum(ox) != dx) {
                continue;
            }
            if (dy != 0 && Integer.signum(oy) != dy) {
                continue;
            }
            int score = Math.abs(ox) * Math.abs(dx == 0 ? 1 : 1) + Math.abs(oy) + (i == slotPreview ? 1000 : 0);
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        if (best == slotPreview) {
            best = Math.floorMod(slotPreview + (dx + dy >= 0 ? 1 : -1), spots.size());
        }
        slotPreview = best;
        touch();
    }

    @Override
    public ScreenContext onKey(String code) {
        // 调试键：F2 弹背景菜单（背景一多，逐个翻机位不如直接点名），F3 换星球类型
        if ("F2".equals(code)) {
            if (backdropMenuOpen) {
                closeBackdropMenu(true);
            } else {
                openBackdropMenu();
            }
            return null;
        }
        if (backdropMenuOpen) {
            return backdropMenuKey(code);
        }
        if ("F3".equals(code)) {
            var themes = com.hysx.salvosequence.gleba.gen.PlanetTheme.values();
            int next = (com.hysx.salvosequence.live.LiveBackdrops.planetTheme().ordinal() + 1) % themes.length;
            // 当前不在星球场景时，先换到"星球·全貌"那条机位，再套主题
            if (com.hysx.salvosequence.live.LiveBackdrops.preset() == null
                    || com.hysx.salvosequence.live.LiveBackdrops.preset().scene != 2) {
                for (int i = 0; i < com.hysx.salvosequence.live.CameraPresets.count(); i++) {
                    if (com.hysx.salvosequence.live.CameraPresets.get(i).scene == 2) {
                        debugPresetIndex = i;
                        com.hysx.salvosequence.live.LiveBackdrops.applyPreset(
                                com.hysx.salvosequence.live.CameraPresets.get(i));
                        break;
                    }
                }
            }
            com.hysx.salvosequence.live.LiveBackdrops.setPlanetTheme(themes[next]);
            liveScene = 2;
            liveSceneNode = run.node();
            liveSceneVisits = run.visitedCount();
            say("星球类型：" + themes[next].zh);
            return null;
        }
        if (settingsOpen) {
            return settingsKey(code);
        }
        if (stage() == Run.Stage.LULL) {
            return lullKey(code);
        }
        switch (code) {
            case "DIGIT1", "DIGIT2", "DIGIT3", "DIGIT4", "DIGIT5", "DIGIT6",
                 "DIGIT7", "DIGIT8", "DIGIT9" -> {
                activate(code.charAt(5) - '1');
                return null;
            }
            case "SPACE", "ENTER" -> {

                if (stage() == Run.Stage.SPACE_PORT && spacePortTab == 2) {
                    var units = run.fleet() == null ? java.util.List.<FleetUnit>of()
                            : run.fleet().units();
                    if (fuseFrom < 0 && fuseCursor < units.size()) {
                        fuseFrom = fuseCursor;
                    } else if (fuseFrom >= 0 && fuseCursor < units.size()
                            && fuseFrom != fuseCursor) {
                        String err = run.fuseShip(
                                units.get(fuseFrom).id(), units.get(fuseCursor).id());
                        if (err != null) {
                            say(err);
                        } else {
                            fuseFrom = -1;
                            fuseCursor = Math.min(fuseCursor, units.size() - 1);
                            touch();
                        }
                    }
                    return null;
                }
                switch (stage()) {
                    case BATTLE -> endTurn();
                    case SLOT -> placePreview();
                    case WON, LOST -> restart();
                    default -> {
                    }
                }
                return null;
            }
            case "S" -> {
                useSkill();
                return null;
            }
            case "T" -> {
                decline();
                return null;
            }
            case "R" -> {
                restart();
                return null;
            }
            case "G" -> {
                if (run.battle() != null && stage() == Run.Stage.BATTLE) {
                    run.battle().toggleFreeFire();
                    say(run.battle().freeFire() ? "自由开火：溢出续打" : "集火：溢出浪费");
                    touch();
                }
                return null;
            }
            case "OPEN_BRACKET", "CLOSE_BRACKET", "BACK_SLASH" -> {
                // 星图消失点微调：[ 抬高（透视更强）、] 压低、\ 复位到默认值
                if (starMapVisible()) {
                    if (code.equals("BACK_SLASH")) {
                        StarDisk.setVanishY(StarDisk.DEFAULT_VANISH_Y);
                    } else {
                        double step = code.equals("OPEN_BRACKET") ? -1 : 1;
                        StarDisk.setVanishY(StarDisk.vanishY() + step);
                    }
                    say("消失点 y = " + (int) Math.rint(StarDisk.vanishY()));
                    touch();
                }
                return null;
            }
            case "Z" -> {
                zoomShip = !zoomShip;
                releaseDrag();
                return null;
            }
            case "X" -> {
                if (stage() == Run.Stage.BATTLE && run.battle() != null
                        && run.battle().shipSkillAvailable()) {
                    run.battle().useShipSkill();
                    say("舰船技能已启动");
                }
                return null;
            }
            case "LEFT", "RIGHT" -> {
                if (stage() == Run.Stage.SLOT) {
                    movePreview(code.equals("LEFT") ? -1 : 1, 0);
                } else if (stage() == Run.Stage.BATTLE) {
                    cycleTarget(code.equals("LEFT") ? -1 : 1);
                }
                return null;
            }
            case "UP", "DOWN" -> {
                if (stage() == Run.Stage.SPACE_PORT && spacePortTab == 2) {
                    int n = run.fleet() == null ? 0 : run.fleet().units().size();
                    if (n > 0) {
                        fuseCursor = Math.floorMod(
                                fuseCursor + (code.equals("UP") ? -1 : 1), n);
                    }
                    return null;
                }
                if (stage() == Run.Stage.SLOT) {
                    movePreview(0, code.equals("UP") ? -1 : 1);
                } else if (stage() == Run.Stage.BATTLE && !hand().isEmpty()) {
                    int size = Math.min(CARD_MAX, hand().size());
                    cursor = Math.floorMod(cursor + (code.equals("UP") ? -1 : 1), size);
                    touch();
                }
                return null;
            }
            case "ESCAPE" -> {
                // 放大看船只是子视图：先退出来
                if (zoomShip) {
                    zoomShip = false;
                    releaseDrag();
                    touch();
                    return null;
                }
                // 战斗里 / 星图里：ESC = 打开设置（含退出键）
                if (stage() == Run.Stage.BATTLE || stage() == Run.Stage.MAP) {
                    openSettings();
                    return null;
                }
                // 太空港里的全屏星图只是"看"：先收起它
                if (fullStarMap && stage() != Run.Stage.MAP) {
                    fullStarMap = false;
                    touch();
                    return null;
                }
                // 其它节点的界面：退掉当前面板，回星图
                if (stage() == Run.Stage.SPACE_PORT) {
                    if (run.pendingBuyShip() != null) {
                        run.cancelBuy();
                        touch();
                        return null;
                    }
                    run.departSpacePort();
                    touch();
                    return null;
                }
                if (stage() == Run.Stage.EVENT) {
                    if (run.leaveEvent()) {
                        touch();
                    }
                    return null;
                }
                // 选舰队 / 通关 / 失败：回标题
                return ScreenContext.go(ScreenContext.Target.START);
            }
            case "F10" -> {
                if (starMapVisible()) {
                    sunVisible = !sunVisible;
                    say(sunVisible ? "太阳标记：开" : "太阳标记：关");
                    touch();
                }
                return null;
            }
            case "M" -> {
                if (stage() == Run.Stage.SPACE_PORT || stage() == Run.Stage.MAP) {
                    fullStarMap = !fullStarMap;
                    touch();
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    // ---------------------------------------------------------------- 设置面板

    /** 打开设置（战斗 / 星图里按 ESC）。 */
    private void openSettings() {
        // 两个浮层互斥：背景菜单开着时先收起来，免得 F2 菜单压在设置面板上
        backdropMenuOpen = false;
        settingsOpen = true;
        settingsCursor = 0;
        touch();
    }

    /** 设置面板里的按键：上下选择，回车确认，ESC 关闭。 */
    private ScreenContext settingsKey(String code) {
        switch (code) {
            case "ESCAPE" -> {
                settingsOpen = false;
                touch();
            }
            case "UP", "W" -> {
                settingsCursor = Math.floorMod(settingsCursor - 1, SETTINGS_ITEMS);
                touch();
            }
            case "DOWN", "S" -> {
                settingsCursor = Math.floorMod(settingsCursor + 1, SETTINGS_ITEMS);
                touch();
            }
            case "LEFT", "RIGHT", "A", "D" -> {
                if (settingsCursor == SETTINGS_MIX_ROW) {
                    // 滑条行：左右键按步进推 k，不走"切换"那一套
                    VideoSettings.nudgeMixK("LEFT".equals(code) || "A".equals(code)
                            ? -VideoSettings.MIX_K_STEP : VideoSettings.MIX_K_STEP);
                    touch();
                } else if (settingsCursor >= 1 && settingsCursor <= 11) {
                    activateSettings(settingsCursor);
                }
            }
            case "ENTER", "SPACE" -> {
                if (settingsCursor == SETTINGS_MIX_ROW) {
                    // 回车把滑条归位到调参时的原始手感
                    VideoSettings.resetMixK();
                    touch();
                    return null;
                }
                return activateSettings(settingsCursor);
            }
            default -> {
                // 其它键在面板里不生效
            }
        }
        return null;
    }

    /** 0=继续 1=音乐 2=音效 3=CRT 4=格栅图案 5=屏幕扭曲 6=辉光 7=混合 K（滑条） 8=背景滤镜 9=视差 10=透视 11=语言 12=返回标题 13=退出游戏。 */
    private ScreenContext activateSettings(int index) {
        switch (index) {
            case 0 -> {
                settingsOpen = false;
                touch();
                return null;
            }
            case 1 -> {
                Music.setMuted(!Music.isMuted());
                touch();
                return null;
            }
            case 2 -> {
                Sound.setMuted(!Sound.isMuted());
                touch();
                return null;
            }
            case 3 -> {
                VideoSettings.cycleCrtFilter();
                touch();
                return null;
            }
            case 4 -> {
                VideoSettings.cycleGrilleStyle();
                touch();
                return null;
            }
            case 5 -> {
                VideoSettings.toggleScreenWarp();
                touch();
                return null;
            }
            case 6 -> {
                VideoSettings.toggleBloom();
                touch();
                return null;
            }
            case 7 -> {
                // 滑条行：回车已在 settingsKey 里拦掉（归位 1.00），这里不做切换
                return null;
            }
            case 8 -> {
                VideoSettings.cycleBackdropFilter();
                touch();
                return null;
            }
            case 9 -> {
                UiTilt.toggleParallax();
                touch();
                return null;
            }
            case 10 -> {
                UiTilt.toggleTilt();
                touch();
                return null;
            }
            case 11 -> {
                Language.toggle();
                touch();
                return null;
            }
            case 12 -> {
                settingsOpen = false;
                return ScreenContext.go(ScreenContext.Target.START);
            }
            case 13 -> {
                settingsOpen = false;
                return ScreenContext.quit();
            }
            default -> {
                return null;
            }
        }
    }

    /** 设置面板：居中面板（战斗/星图状态原样保留在底下）。 */
    private void drawSettingsOverlay(CanvasBuffer buffer, PixelArt art) {
        // 暗幕保持关闭（诊断过：它会把盘底座的边藏掉）；改用不透明面板压住底图
        int w = SETTINGS_W;
        int h = SETTINGS_H;
        int x = settingsX();
        int y = settingsY();
        UiChrome.panel(art, x, y, w, h);
        UiChrome.plate(buffer, art, x + 10, y - 8, 56, 17, Language.t("设置", "SETUP"));
        UiChrome.latin(art, "BATTLE CONFIG", x + w - 8 - UiChrome.measureLatin("BATTLE CONFIG"),
                y + 5, Palette.STEEL_DIM);
        UiChrome.divider(art, x + 8, y + 22, w - 16);

        String[] labels = settingLabels();
        for (int i = 0; i < SETTINGS_ITEMS; i++) {
            int ry = y + SETTINGS_ROW_TOP + i * SETTINGS_ROW_H;
            boolean sel = i == settingsCursor;
            boolean slider = i == SETTINGS_MIX_ROW;
            if (sel) {
                // 滑条行只借横幅的琥珀底色，文字自己排——中间得让出轨道
                UiChrome.banner(buffer, art, x + 8, ry - 6, w - 16, 20, slider ? "" : labels[i]);
            } else {
                UiChrome.chamferFrame(art, x + 8, ry - 6, w - 16, 20, 2, Palette.STEEL_FAINT);
            }
            if (slider) {
                drawMixSlider(buffer, art, x, ry, sel, labels[i]);
            } else if (!sel) {
                UiChrome.zh(buffer, labels[i], x + 26, ry - 1, 12, Palette.LINE_DIM);
            }
            UiChrome.sectionMark(art, x + 14, ry + 4, sel ? Palette.INK : Palette.STEEL_DIM);
            UiChrome.latin(art, String.format("%02d", i + 1), x + w - 22, ry + 1,
                    sel ? Palette.INK : Palette.LINE_FAINT);
        }
        UiChrome.zh(buffer, Language.t("↑↓ 选择    ←→ 调整    Esc 关闭",
                "UP/DOWN SELECT   LEFT/RIGHT CHANGE   ESC CLOSE"),
                x + 14, y + h - 22, 12, Palette.LINE_FAINT);
    }

    /**
     * 混合 K 的滑条：标签在左、轨道在中、两位小数在右。
     *
     * <p>轨道 6px 高（1px 框 + 4px 槽），已填充段亮一档，拖柄 3×10 压住当前值。
     * 选中行底色是实心琥珀，所以框线和文字在选中时换成 {@code INK}，否则浅色字压在
     * 橙条上读不出来；未选中时框用 {@code STEEL_DIM}、填充用 {@code STEEL}，免得在
     * 深色面板上淡成一团。</p>
     */
    private void drawMixSlider(CanvasBuffer buffer, PixelArt art, int x, int ry,
                               boolean sel, String label) {
        UiChrome.zh(buffer, label, x + 26, ry - 1, 12, sel ? Palette.INK : Palette.LINE_DIM);
        int sx = x + SETTINGS_SLIDER_X;
        int sw = SETTINGS_SLIDER_W;
        int ty = ry + 2;
        art.frame(sx, ty, sw, 6, sel ? Palette.INK : Palette.STEEL_DIM);
        art.fillRect(sx + 1, ty + 1, sw - 2, 4, Palette.PANEL);
        int inner = sw - 2;
        int fw = (int) Math.round(inner * mixKRatio());
        if (fw > 0) {
            art.fillRect(sx + 1, ty + 1, fw, 4, sel ? Palette.LINE : Palette.STEEL);
        }
        int kx = Math.max(0, Math.min(inner, fw));
        if (sel) {
            art.frame(sx + kx - 2, ty - 3, 5, 12, Palette.INK);
        }
        art.fillRect(sx + kx - 1, ty - 2, 3, 10, Palette.LINE);
        // 数值右对齐到行号左边，别和 "08" 挤成一串
        String value = String.format("%.2f", VideoSettings.mixK());
        UiChrome.latin(art, value, x + SETTINGS_W - 40 - UiChrome.measureLatin(value),
                ry + 1, sel ? Palette.INK : Palette.LINE_DIM);
    }

    /** 当前 k 在量程上的位置（0..1），绘制和热区共用。 */
    private static double mixKRatio() {
        double span = VideoSettings.MIX_K_MAX - VideoSettings.MIX_K_MIN;
        return Math.max(0.0, Math.min(1.0, (VideoSettings.mixK() - VideoSettings.MIX_K_MIN) / span));
    }

    /** 指针横坐标 → k：轨道两端就是量程两端，按下即定位，拖动继续跟手。 */
    private void setMixKFromPointer(double mx) {
        double sx = settingsX() + SETTINGS_SLIDER_X;
        double t = (mx - sx) / SETTINGS_SLIDER_W;
        t = Math.max(0.0, Math.min(1.0, t));
        VideoSettings.setMixK(VideoSettings.MIX_K_MIN
                + t * (VideoSettings.MIX_K_MAX - VideoSettings.MIX_K_MIN));
        touch();
    }

    // ---------------------------------------------------------------- F2 背景菜单

    /**
     * 打开背景菜单：光标落在当前场景上，机位落在当前那条上。
     *
     * <p>F2 原来是"逐条翻机位"的调试键——机位表长到 50 多条以后，想看某个场景得连按
     * 十几次，所以改成弹菜单：↑↓ 选场景、←→ 该场景下的机位、P 切配色（体积星云）、
     * Enter 留下、Esc 还原。</p>
     */
    private void openBackdropMenu() {
        backdropMenuOpen = true;
        settingsOpen = false;
        backdropMenuSavedPreset = debugPresetIndex;
        backdropMenuScene = debugPresetIndex >= 0
                ? com.hysx.salvosequence.live.CameraPresets.get(debugPresetIndex).scene
                : battleScene();
        backdropMenuSavedScene = backdropMenuScene;
        backdropMenuPreset = 0;
        int[] presets = backdropPresets(backdropMenuScene);
        for (int i = 0; i < presets.length; i++) {
            if (presets[i] == debugPresetIndex) {
                backdropMenuPreset = i;
            }
        }
        releaseDrag();
        touch();
    }

    /** 关菜单：{@code keep=false}（Esc）把机位退回打开前那条。 */
    private void closeBackdropMenu(boolean keep) {
        backdropMenuOpen = false;
        if (keep) {
            say("背景：" + com.hysx.salvosequence.live.LiveBackdrops.nameZh(backdropMenuScene)
                    + "  机位 " + (backdropMenuPreset + 1) + "/"
                    + Math.max(1, backdropPresets(backdropMenuScene).length));
        } else {
            debugPresetIndex = backdropMenuSavedPreset;
            liveScene = backdropMenuSavedScene;
            if (debugPresetIndex >= 0) {
                com.hysx.salvosequence.live.LiveBackdrops.applyPreset(
                        com.hysx.salvosequence.live.CameraPresets.get(debugPresetIndex));
            }
        }
        touch();
    }

    private ScreenContext backdropMenuKey(String code) {
        int scenes = com.hysx.salvosequence.live.LiveBackdrops.count();
        int presets = Math.max(1, backdropPresets(backdropMenuScene).length);
        switch (code) {
            case "UP", "W" -> {
                backdropMenuScene = Math.floorMod(backdropMenuScene - 1, scenes);
                backdropMenuPreset = 0;
                applyBackdropMenuChoice();
            }
            case "DOWN", "S" -> {
                backdropMenuScene = Math.floorMod(backdropMenuScene + 1, scenes);
                backdropMenuPreset = 0;
                applyBackdropMenuChoice();
            }
            case "LEFT", "A" -> {
                backdropMenuPreset = Math.floorMod(backdropMenuPreset - 1, presets);
                applyBackdropMenuChoice();
            }
            case "RIGHT", "D" -> {
                backdropMenuPreset = Math.floorMod(backdropMenuPreset + 1, presets);
                applyBackdropMenuChoice();
            }
            case "P" -> toggleBackdropPalette();
            case "R" -> rerollBackdropSeed();
            // 巨构：巢都 / 矿业星 / 轨道加速器 / 太空电梯，只在 Gleba 那颗星球上生效
            case "B" -> cyclePlanetBuild();
            case "ENTER", "SPACE" -> closeBackdropMenu(true);
            case "ESC", "ESCAPE" -> closeBackdropMenu(false);
            default -> {
            }
        }
        return null;
    }

    /** 把光标所在的"场景 + 机位"套到战斗背景上（打开菜单时就已经能看到效果）。 */
    private void applyBackdropMenuChoice() {
        int[] presets = backdropPresets(backdropMenuScene);
        if (presets.length == 0) {
            return;
        }
        backdropMenuPreset = Math.floorMod(backdropMenuPreset, presets.length);
        int index = presets[backdropMenuPreset];
        com.hysx.salvosequence.live.CameraPresets.Preset p =
                com.hysx.salvosequence.live.CameraPresets.get(index);
        // 工坊里点过"手动选场景"时会压住调试机位，这里先松开手动选择
        com.hysx.salvosequence.live.LiveBackdrops.setSelected(-1);
        debugPresetIndex = index;
        com.hysx.salvosequence.live.LiveBackdrops.applyPreset(p);
        livePreset = p;
        liveScene = p.scene;
        liveSceneNode = run.node();
        liveSceneVisits = run.visitedCount();
        touch();
    }

    /** 巨构档位：0 原始 → 1 巢都 → 2 矿业星 → 3 轨道加速器 → 4 太空电梯，切一次重生成一次星球。 */
    private void cyclePlanetBuild() {
        int next = Math.floorMod(com.hysx.salvosequence.live.LiveBackdrops.planetBuild() + 1,
                com.hysx.salvosequence.gleba.gen.PlanetBuild.count());
        com.hysx.salvosequence.live.LiveBackdrops.setPlanetBuild(next);
        com.hysx.salvosequence.gleba.gen.PlanetBuild build =
                com.hysx.salvosequence.gleba.gen.PlanetBuild.byIndex(next);
        say(Language.t("巨构：" + build.zh, "MEGA: " + build.en));
        touch();
    }

    /** 体积星云的配色档位：0 冷调 → 1 原版 → 2 玫瑰红 → 3 面纱青 → 4 猎户橙 → 5 反射紫，循环。 */
    private void toggleBackdropPalette() {        Object params = com.hysx.salvosequence.live.LiveBackdrops.params(backdropMenuScene);
        for (java.lang.reflect.Field f : com.hysx.salvosequence.live.LiveTune.fields(params)) {
            if (!"palette".equals(f.getName())) {
                continue;
            }
            int n = com.hysx.salvosequence.live.nebula.AlroNebulaLive.PALETTE_COUNT;
            int next = ((int) Math.round(com.hysx.salvosequence.live.LiveTune.get(params, f)) + 1) % n;
            com.hysx.salvosequence.live.LiveTune.set(params, f, next);
            com.hysx.salvosequence.live.LiveSettings.save();
            say("配色：" + com.hysx.salvosequence.live.nebula.AlroNebulaLive.paletteNameZh(next));
            touch();
            return;
        }
        say("这个场景没有配色档位");
    }

    /** 体积星云的云形种子：0 原样，1..11 各是一团不同的云，按 R 循环。 */
    private void rerollBackdropSeed() {
        Object params = com.hysx.salvosequence.live.LiveBackdrops.params(backdropMenuScene);
        for (java.lang.reflect.Field f : com.hysx.salvosequence.live.LiveTune.fields(params)) {
            if (!"seed".equals(f.getName())) {
                continue;
            }
            int n = com.hysx.salvosequence.live.nebula.AlroNebulaLive.SEED_COUNT;
            int next = ((int) Math.round(com.hysx.salvosequence.live.LiveTune.get(params, f)) + 1) % n;
            com.hysx.salvosequence.live.LiveTune.set(params, f, next);
            com.hysx.salvosequence.live.LiveSettings.save();
            say(next == 0 ? "种子：0（原样）" : "种子：" + next);
            touch();
            return;
        }
        say("这个场景没有云形种子");
    }

    /** 某场景在机位表里的全部下标（机位表按场景分组的顺序）。 */
    private static int[] backdropPresets(int scene) {
        int total = com.hysx.salvosequence.live.CameraPresets.count();
        int n = 0;
        for (int i = 0; i < total; i++) {
            if (com.hysx.salvosequence.live.CameraPresets.get(i).scene == scene) {
                n++;
            }
        }
        int[] out = new int[n];
        int k = 0;
        for (int i = 0; i < total; i++) {
            if (com.hysx.salvosequence.live.CameraPresets.get(i).scene == scene) {
                out[k++] = i;
            }
        }
        return out;
    }

    /** 画 F2 背景菜单：一行一个场景，右边是该场景的机位数与配色档位。 */
    private void drawBackdropMenu(CanvasBuffer buffer, PixelArt art) {
        int scenes = com.hysx.salvosequence.live.LiveBackdrops.count();
        int w = BACKDROP_MENU_W;
        int top = 34;
        int h = top + scenes * BACKDROP_MENU_ROW_H + 44;
        int x = (640 - w) / 2;
        int y = (360 - h) / 2;
        UiChrome.panel(art, x, y, w, h);
        UiChrome.plate(buffer, art, x + 10, y - 8, 64, 17, Language.t("背景", "SCENE"));
        String en = "F2 SCENE PICKER";
        UiChrome.latin(art, en, x + w - 8 - UiChrome.measureLatin(en), y + 5, Palette.STEEL_DIM);
        UiChrome.divider(art, x + 8, y + 22, w - 16);

        for (int i = 0; i < scenes; i++) {
            int ry = y + top + i * BACKDROP_MENU_ROW_H;
            boolean sel = i == backdropMenuScene;
            if (sel) {
                UiChrome.banner(buffer, art, x + 8, ry - 6, w - 16, 20, "");
            } else {
                UiChrome.chamferFrame(art, x + 8, ry - 6, w - 16, 20, 2, Palette.STEEL_FAINT);
            }
            UiChrome.sectionMark(art, x + 14, ry + 4, sel ? Palette.INK : Palette.STEEL_DIM);
            UiChrome.latin(art, String.format("%02d", i + 1), x + 22, ry + 1,
                    sel ? Palette.INK : Palette.LINE_FAINT);
            UiChrome.zh(buffer, com.hysx.salvosequence.live.LiveBackdrops.nameZh(i),
                    x + 40, ry - 1, 12, sel ? Palette.INK : Palette.LINE_DIM);
            int presets = backdropPresets(i).length;
            String counter = sel
                    ? Language.t("机位 ", "CAM ") + (backdropMenuPreset + 1) + "/" + presets
                    : Language.t("机位 ", "CAM ") + presets;
            String tag = sel ? backdropPaletteLabel(i) : null;
            String right = tag == null ? counter : tag + "  " + counter;
            UiChrome.zhRight(buffer, right, x + w - 14, ry - 1, 12,
                    sel ? Palette.INK : Palette.LINE_FAINT);
        }
        UiChrome.zh(buffer, Language.t("↑↓ 场景   ←→ 机位   P 配色   R 种子   B 巨构",
                        "UP/DOWN SCENE   LEFT/RIGHT CAM   P PALETTE   R SEED   B BUILD"),
                x + 14, y + h - 40, 12, Palette.LINE_FAINT);
        UiChrome.zh(buffer, Language.t("Enter 应用    Esc 还原",
                        "ENTER APPLY   ESC REVERT"),
                x + 14, y + h - 22, 12, Palette.LINE_FAINT);
    }

    /** 行尾那截"配色 玫瑰红·种3 巨构 巢都"，只给带对应档位的场景显示。 */
    private static String backdropPaletteLabel(int scene) {
        Object params = com.hysx.salvosequence.live.LiveBackdrops.params(scene);
        String label = null;
        int seed = 0;
        int build = -1;
        for (java.lang.reflect.Field f : com.hysx.salvosequence.live.LiveTune.fields(params)) {
            if ("palette".equals(f.getName())) {
                int idx = (int) Math.round(com.hysx.salvosequence.live.LiveTune.get(params, f));
                label = "配色 " + Language.t(
                        com.hysx.salvosequence.live.nebula.AlroNebulaLive.paletteNameZh(idx),
                        com.hysx.salvosequence.live.nebula.AlroNebulaLive.paletteNameEn(idx));
            } else if ("seed".equals(f.getName())) {
                seed = (int) Math.round(com.hysx.salvosequence.live.LiveTune.get(params, f));
            } else if ("build".equals(f.getName())) {
                build = (int) Math.round(com.hysx.salvosequence.live.LiveTune.get(params, f));
            }
        }
        if (label != null && seed != 0) {
            label = label + "·" + Language.t("种", "S") + seed;
        }
        if (build >= 0) {
            com.hysx.salvosequence.gleba.gen.PlanetBuild b =
                    com.hysx.salvosequence.gleba.gen.PlanetBuild.byIndex(build);
            String mega = Language.t("巨构 " + b.zh, "BUILD " + b.en);
            label = label == null ? mega : label + "  " + mega;
        }
        return label;
    }

    /** 动态文案：音乐/音效/CRT 滤镜模式/格栅图案/屏幕扭曲/辉光，背景滤镜/视差/透视/语言显示当前值。 */
    private String[] settingLabels() {
        return new String[]{
                Language.t("继续游戏", "RESUME"),
                Language.t("音乐：", "MUSIC: ") + Language.t(Music.isMuted() ? "关" : "开",
                        Music.isMuted() ? "OFF" : "ON"),
                Language.t("音效：", "SOUND: ") + Language.t(Sound.isMuted() ? "关" : "开",
                        Sound.isMuted() ? "OFF" : "ON"),
                Language.t("CRT 滤镜：", "CRT FILTER: ")
                        + Language.t(VideoSettings.crtFilter().label(),
                        VideoSettings.crtFilter().labelEn()),
                Language.t("格栅图案：", "GRILLE: ")
                        + Language.t(VideoSettings.grilleStyle().label(),
                        VideoSettings.grilleStyle().labelEn()),
                Language.t("屏幕扭曲：", "SCREEN WARP: ")
                        + Language.t(VideoSettings.screenWarp() ? "开" : "关",
                        VideoSettings.screenWarp() ? "ON" : "OFF"),
                Language.t("混合柔化：", "SOFT MIX: ")
                        + Language.t(VideoSettings.bloom() ? "开" : "关",
                        VideoSettings.bloom() ? "ON" : "OFF"),
                Language.t("混合 K：", "MIX K: "),
                Language.t("背景滤镜：", "BACKDROP FILTER: ")
                        + Language.t(VideoSettings.backdropFilter().label(),
                        VideoSettings.backdropFilter().labelEn()),
                Language.t("界面视差：", "PARALLAX: ")
                        + Language.t(UiTilt.parallaxEnabled() ? "开" : "关",
                        UiTilt.parallaxEnabled() ? "ON" : "OFF"),
                Language.t("界面透视：", "UI PERSPECTIVE: ")
                        + Language.t(UiTilt.tiltEnabled() ? "开" : "关",
                        UiTilt.tiltEnabled() ? "ON" : "OFF"),
                Language.t("语言：", "LANGUAGE: ") + Language.t("中文", "English"),
                Language.t("返回标题", "BACK TO TITLE"),
                Language.t("退出游戏", "QUIT GAME")
        };
    }

    /** 设置项的鼠标热区：与绘制共用 SETTINGS_* 与行高，命中框就是选中横幅本身。 */
    private void settingsHotspots(HotspotSink sink) {
        int w = SETTINGS_W;
        int x = settingsX();
        int y = settingsY();
        // 面板是斜着贴回去的（draw() 里 endLayer(UiTilt.UI)），命中测试也要按同一个平面反算，
        // 否则越靠边点得越偏——这里之前既少了平面，行距又和绘制差 20px。
        sink.plane(UiTilt.UI);
        for (int i = 0; i < SETTINGS_ITEMS; i++) {
            int ry = y + SETTINGS_ROW_TOP + i * SETTINGS_ROW_H;
            int index = i;
            if (i == SETTINGS_MIX_ROW) {
                // 滑条行：按下就定位、按住继续跟手，走 drag 而不是 add
                sink.drag(x + 8, ry - 6, w - 16, 20,
                        (mx, my) -> setMixKFromPointer(mx),
                        (mx, my) -> {
                            settingsCursor = index;
                            setMixKFromPointer(mx);
                        });
                continue;
            }
            sink.add(x + 8, ry - 6, w - 16, 20, () -> {
                settingsCursor = index;
                ScreenContext ctx = activateSettings(index);
                pendingTransition = ctx;
                touch();
            });
        }
    }

    @Override
    public void collectHotspots(HotspotSink sink) {
        if (settingsOpen) {
            settingsHotspots(sink);
            return;
        }
        // 背景菜单是键盘独占的浮层：底下的棋盘/按钮一概不登记，免得点穿
        if (backdropMenuOpen) {
            return;
        }
        boolean overlay = overlayStage();
        // 放大视图（Z）只是"看"：那边一格 18 点、棋盘一格 13 点，热区却还是棋盘那套，
        // 于是拖起来船会比鼠标跑得快（约 1.4 倍）。干脆整块不登记。
        if (zoomShip) {
            return;
        }
        boolean placing = stage() == Run.Stage.SLOT;
        boolean battle = stage() == Run.Stage.BATTLE;
        boolean spacePort = stage() == Run.Stage.SPACE_PORT;
        // 顺序就是层级：HotspotSink 里后注册的压住先注册的。棋盘画在底下，覆盖层的行画在最上面，
        // 所以棋盘要先登记、行要最后登记——顺序反了就会「只有行尾能点到」（棋盘把行吞掉）。
        if (!overlay || placing || spacePort) {
            // 棋盘那一层斜得比面板多：热区跟着棋盘平面反变换，指针才能正好落在格子上
            sink.plane(UiTilt.BOARD);
            sink.draw(ourBoardX(), BOARD_Y, BOARD_SPAN, BOARD_SPAN,
                    (mx, my) -> hoverOurBoard(mx, my),
                    (mx, my) -> dragShipTo(mx, my),
                    (mx, my) -> pressOurBoard(mx, my),
                    () -> {
                    },
                    () -> {
                        // 只清悬浮。拖船中途热区会整层重建（落位一改就 touch），
                        // 旧热区被摘下来时 JavaFX 会送一次 MOUSE_EXITED——在这里清 dragShipId
                        // 就等于「拖一格以后断手」，指针也退回默认箭头。
                        hoveredOurShip = -1;
                    },
                    this::releaseDrag);
        }
        if (starMapVisible()) {
            // 星图画在底板上（不倾斜），热区也按原始设计坐标算
            sink.plane(UiTilt.NONE);
            // 整屏拖动平移：先登记，压在恒星热区下面——按在恒星上仍然是跃迁，不会被拖走。
            // 同时用它整屏跟踪指针：走过的恒星没有热区，也靠这里更新坐标才能悬浮出名字/类型。
            sink.draw(0, 0, 640, 360, this::noteMouse,
                    this::dragStarMap, this::beginStarPan, null, null, null);
            for (var st : run.reachableStars()) {
                int[] pos = starScreenPos(st);
                int sid = st.id();
                // track 而非 add：恒星热区恰好盖住悬浮半径，move 不落在底层星图上，
                // 缺它的话 mouseX/Y 停在上一次非恒星位置——名字永远不显示
                sink.track(pos[0] - 14, pos[1] - 14, 28, 28, this::noteMouse, () -> {
                    if (run.travelTo(sid)) {
                        fullStarMap = stage() == Run.Stage.MAP;
                        touch();
                    }
                }, null, null);
            }
            if (stage() != Run.Stage.MAP) {
                sink.add(4, 4, 120, 14, () -> {
                    fullStarMap = false;
                    touch();
                });
            }
            sink.plane(UiTilt.UI);
            return;
        }
        if (stage() == Run.Stage.LULL) {
            collectLullHotspots(sink);
            return;
        }
        if (stage() == Run.Stage.EVENT && run.eventCard() != null) {
            var card = run.eventCard();
            for (int i = 0; i < card.options().size(); i++) {
                int oi = i;
                sink.add(140, 180 + i * 26, 360, 22, () -> run.chooseEventOption(oi));
            }
            return;
        }
        if (spacePort) {
            int spX = ENEMY_BOARD_X;
            // 面板级鼠标跟踪：悬浮描述读的是 mouseX/mouseY，右侧面板得有 region
            // 在更新坐标（先登记压底，tab/行的热区注册在后会盖住它）
            sink.draw(spX, BOARD_Y, 636 - spX, BOARD_SPAN,
                    this::noteMouse, null, null, null, null);
            int tabW = (636 - spX - 16) / 5;
            for (int i = 0; i < 5; i++) {
                int tx = spX + 6 + i * (tabW + 2);
                int ti = i;
                sink.track(tx, BOARD_Y + 22, tabW - 2, 14,
                        this::noteMouse,
                        () -> {
                            spacePortTab = ti;
                            touch();
                        }, null, null);
            }
            // 融合：每行一个热区，点击=选中A/融合B
            if (spacePortTab == 2 && run.fleet() != null) {
                var fuseUnits = run.fleet().units();
                int fy = BOARD_Y + 42 + 26;
                for (int i = 0; i < fuseUnits.size() && i < 14; i++) {
                    int fi = i;
                    sink.track(spX + 6, fy + i * 15, 636 - spX - 22, 14,
                            this::noteMouse,
                            () -> {
                                if (fuseFrom < 0) {
                                    fuseFrom = fi;
                                    fuseCursor = fi;
                                } else if (fuseFrom == fi) {
                                    fuseFrom = -1;
                                } else {
                                    String e = run.fuseShip(
                                            fuseUnits.get(fuseFrom).id(),
                                            fuseUnits.get(fi).id());
                                    if (e != null) { say(e); }
                                    fuseFrom = -1;
                                    touch();
                                }
                            }, null, null);
                }
            }
            // 定制：强化行（整行可点）+ 采购行——行距/行数必须和绘制同款（15/6），否则点错行
            if (spacePortTab == 1 && run.fleet() != null) {
                List<FleetUnit> units = run.fleet().units();
                int cy = BOARD_Y + 42 + 4 + 14;
                for (int i = 0; i < units.size() && i < 6; i++) {
                    int ui = i;
                    sink.track(spX + 6, cy + i * 15, 636 - spX - 22, 14,
                            this::noteMouse,
                            () -> {
                                run.upgradeShip(units.get(ui).id());
                                touch();
                            }, null, null);
                }
                List<Ship> shop = run.shopInventory();
                int by = BOARD_Y + 42 + 110 + 14;
                for (int i = 0; i < shop.size(); i++) {
                    int si = i;
                    sink.track(spX + 6, by + i * 15, 636 - spX - 22, 14,
                            this::noteMouse,
                            () -> {
                                run.beginBuy(shop.get(si));
                                touch();
                            }, null, null);
                }
            }
            // 培训：9 个技能节点
            if (spacePortTab == 3) {
                for (int bi = 0; bi < 3; bi++) {
                    for (int tier = 0; tier < 3; tier++) {
                        int[] rect = skillNodeRect(bi, tier);
                        int bIdx = bi;
                        sink.track(rect[0], rect[1], rect[2], rect[3],
                                this::noteMouse,
                                () -> {
                                    run.trainSkill(bIdx);
                                    touch();
                                }, null, null);
                    }
                }
            }
            // 出发
            sink.track(spX + 6, BOARD_Y + BOARD_SPAN - 22, 636 - spX - 22, 16,
                    this::noteMouse, this::departSpacePort, null, null);
        }
        if (battle) {
            // 敌方棋盘：悬浮看船，点一艘换目标（同样跟着棋盘平面走）
            sink.plane(UiTilt.BOARD);
            sink.draw(ENEMY_BOARD_X, BOARD_Y, BOARD_SPAN, BOARD_SPAN,
                    (mx, my) -> {
                        noteMouse(mx, my);
                        hoveredFoe = foeIndexAt(mx, my);
                    },
                    (mx, my) -> {
                        int foe = foeIndexAt(mx, my);
                        if (foe >= 0) {
                            selectFoe(foe);
                        }
                    },
                    (mx, my) -> {
                        int foe = foeIndexAt(mx, my);
                        if (foe >= 0) {
                            selectFoe(foe);
                        }
                    },
                    () -> {
                    },
                    () -> hoveredFoe = -1);
            // 新版版面：左上齿轮 = 设置，左下三角 = 跳过回合，立绘下大菱形 = 舰长技
            sink.track(GEAR_X, GEAR_Y, GEAR_S, GEAR_S,
                    (mx, my) -> hoveredGear = true,
                    this::openSettings,
                    () -> hoveredGear = true,
                    () -> hoveredGear = false);
            sink.track(SKIP_X, SKIP_Y, SKIP_W, SKIP_H,
                    (mx, my) -> hoveredTurn = true,
                    this::endTurn,
                    () -> hoveredTurn = true,
                    () -> hoveredTurn = false);
            sink.track(SKILL_CX - SKILL_R, SKILL_CY - SKILL_R, SKILL_R * 2, SKILL_R * 2,
                    (mx, my) -> hoveredSkill = true,
                    this::useSkill,
                    () -> hoveredSkill = true,
                    () -> hoveredSkill = false);
            sink.plane(UiTilt.UI);
        }
        if (stage() == Run.Stage.REWARD || placing) {
            int[] skip = skipRect();
            sink.add(skip[0], skip[1], skip[2], skip[3], this::decline);
        }
        if (stage() == Run.Stage.WON || stage() == Run.Stage.LOST) {
            sink.add(OV_X, OV_Y + OV_ROW_H + 12, OV_W, OV_ROW_H, this::restart);
        }
        // 覆盖层的行（起始舰队 / 战利品 / 落点候选）：最后登记，压在最上层
        List<int[]> boxes = optionRects();
        for (int i = 0; i < boxes.size(); i++) {
            int index = i;
            int[] box = boxes.get(i);
            sink.track(box[0], box[1], box[2], box[3],
                    (mx, my) -> {
                        noteMouse(mx, my);
                        hoveredOption = index;
                    },
                    () -> activate(index),
                    () -> hoveredOption = index,
                    () -> hoveredOption = -1);
        }
    }

    /** 星图是否正在全屏显示：MAP 阶段整屏就是星图；其它阶段靠 fullStarMap 覆盖层。 */
    private boolean starMapVisible() {
        return fullStarMap || stage() == Run.Stage.MAP;
    }

    /** 星盘当前旋转角（弧度，0~2π）。 */
    private double starDiskRad() {
        double twoPi = Math.PI * 2;
        double a = starDiskAngle % twoPi;
        return a < 0 ? a + twoPi : a;
    }

    /** 开始拖星图：记住按下的位置，移动时按水平增量旋转星盘。 */
    private void beginStarPan(double mx, double my) {
        starPanX = mx;
        starPanY = my;
    }

    /** 拖动星图：左右拖动旋转星盘（0.5°/px），上下拖动忽略。 */
    private void dragStarMap(double mx, double my) {
        noteMouse(mx, my);   // 星图上唯一的移动回调：悬浮标签靠它更新指针位置
        double dx = mx - starPanX;
        starPanX = mx;
        starPanY = my;
        if (Math.abs(dx) < 1e-6) {
            return;
        }
        // 角度每帧跟手。拖动发涩/突变的原因不在"换图太快"，而在解码队列被旧角度塞满：
        // 现在 StarDisk 只解码"当前要显示的那一帧"，过期的排队任务直接跳过。
        starDiskAngle += Math.toRadians(dx * STAR_DRAG_DEG_PER_PIXEL * STAR_DRAG_DIRECTION);
        StarDisk.prefetch(starDiskAngle);
        touch();
    }

    private void noteMouse(double mx, double my) {
        mouseX = mx;
        mouseY = my;
    }

    private void hoverOurBoard(double mx, double my) {
        noteMouse(mx, my);
        hoveredOurShip = shipIndexAt(mx, my);
        if (stage() == Run.Stage.SLOT) {
            int[] cell = cellAt(mx, my);
            if (cell != null) {
                hoverCell(cell[0], cell[1]);
            }
        }
    }

    /** 我方棋盘上按下：按住自己的船就是拖拽变阵（战斗中与编入阶段都行），其余情况交给点击。 */
    private void pressOurBoard(double mx, double my) {
        // 太空港采购落位：点空格放下新船
        if (stage() == Run.Stage.SPACE_PORT && run.pendingBuyShip() != null) {
            int[] cell = cellAt(mx, my);
            if (cell != null && run.placeBoughtShip(cell[0], cell[1])) {
                touch();
            }
            return;
        }
        dragShipId = null;
        // v5：战斗中不变阵——拖拽只在太空港（编组调整）和编入阶段可用
        boolean canDrag = stage() == Run.Stage.SPACE_PORT || stage() == Run.Stage.SLOT
                || stage() == Run.Stage.LULL;
        if (canDrag) {
            int index = shipIndexAt(mx, my);
            if (index >= 0 && index < ourUnits().size()) {
                FleetUnit unit = ourUnits().get(index);
                dragShipId = unit.id();
                dragOffX = mx - (ourBoardX() + unit.x() * BOARD_CELL);
                dragOffY = my - (BOARD_Y + unit.y() * BOARD_CELL);
                dragFromMx = mx;
                dragFromMy = my;
                lastDragMx = mx;
                lastDragMy = my;
                dragFromCellX = unit.x();
                dragFromCellY = unit.y();
                ScreenManager.trace(String.format("  board-press ship=%s at %d,%d anchor %.2f,%.2f",
                        dragShipId, unit.x(), unit.y(), dragOffX, dragOffY));
                return;
            }
        }
        clickOurBoard(mx, my);
    }

    /** 拖着船走：整格吸附，落点合法就直接挪过去（相邻加成当场重算，血量不掉）。 */
    private void dragShipTo(double mx, double my) {
        if (dragShipId == null || run.fleet() == null) {
            return;
        }
        FleetUnit unit = run.fleet().unit(dragShipId);
        if (unit == null) {
            dragShipId = null;
            return;
        }
        lastDragMx = mx;
        lastDragMy = my;
        // 只认"指针相对按下时挪了几格"：不用会随每次成功移动重算的锚点，
        // 这样无论上游给的是哪条坐标路径（热区节点 / 场景），位移都是 1:1。
        int dCellsX = (int) Math.round((mx - dragFromMx) / BOARD_CELL);
        int dCellsY = (int) Math.round((my - dragFromMy) / BOARD_CELL);
        int tx = dragFromCellX + dCellsX;
        int ty = dragFromCellY + dCellsY;
        if (tx == unit.x() && ty == unit.y()) {
            return;
        }
        boolean moved = run.moveShip(dragShipId, tx, ty);
        ScreenManager.trace(String.format("  board-drag %s -> %d,%d (was %d,%d) ok=%s  from %.1f,%.1f -> %.1f,%.1f"
                        + "  dCells %d,%d  cell %d  anchor %.1f,%.1f",
                dragShipId, tx, ty, unit.x(), unit.y(), moved,
                dragFromMx, dragFromMy, mx, my, dCellsX, dCellsY, BOARD_CELL, dragOffX, dragOffY));
        if (moved) {
            touch();
        }
    }

    /** 松手：收掉拖动状态，并把"船挪了几格 vs 鼠标挪了几点"记一行。 */
    private void releaseDrag() {
        if (dragShipId != null) {
            FleetUnit unit = run.fleet() == null ? null : run.fleet().unit(dragShipId);
            if (unit != null) {
                double dMx = lastDragMx - dragFromMx;
                double dMy = lastDragMy - dragFromMy;
                double shipPx = Math.hypot((unit.x() - dragFromCellX) * (double) BOARD_CELL,
                        (unit.y() - dragFromCellY) * (double) BOARD_CELL);
                double pointerPx = Math.hypot(dMx, dMy);
                ScreenManager.trace(String.format(
                        "board-release ship=%s cell %d,%d -> %d,%d  pointer %.1f,%.1f  shipPx %.1f  pointerPx %.1f"
                                + "  ratio %.2f  (一格 %d 点)",
                        dragShipId, dragFromCellX, dragFromCellY, unit.x(), unit.y(), dMx, dMy,
                        shipPx, pointerPx, pointerPx < 1e-6 ? 0 : shipPx / pointerPx, BOARD_CELL));
            }
        }
        dragShipId = null;
    }
    private int[] cellAt(double mx, double my) {
        int cx = (int) Math.floor((mx - ourBoardX()) / BOARD_CELL);
        int cy = (int) Math.floor((my - BOARD_Y) / BOARD_CELL);
        if (cx < 0 || cy < 0 || cx >= FleetBoard.SIZE || cy >= FleetBoard.SIZE) {
            return null;
        }
        return new int[]{cx, cy};
    }

    private int shipIndexAt(double mx, double my) {
        int[] cell = cellAt(mx, my);
        if (cell == null) {
            return -1;
        }
        List<FleetUnit> units = ourUnits();
        for (int i = 0; i < units.size(); i++) {
            if (units.get(i).covers(cell[0], cell[1])) {
                return i;
            }
        }
        return -1;
    }

    /** 编入阶段：鼠标压在哪一格，就把候选落点挪到最贴那一格的候选上。 */
    private void hoverCell(int cx, int cy) {
        if (stage() != Run.Stage.SLOT) {
            return;
        }
        List<FleetUnit> spots = candidates();
        if (spots.isEmpty()) {
            return;
        }
        int best = -1;
        int bestScore = Integer.MAX_VALUE;
        for (int i = 0; i < spots.size(); i++) {
            FleetUnit spot = spots.get(i);
            if (spot.covers(cx, cy)) {
                slotPreview = i;
                return;
            }
            int dx = Math.max(0, Math.max(spot.x() - cx, cx - spot.x2()));
            int dy = Math.max(0, Math.max(spot.y() - cy, cy - spot.y2()));
            int score = dx * dx + dy * dy;
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        if (best >= 0) {
            slotPreview = best;
        }
    }

    /** 点我方棋盘：编入阶段落子，其余阶段只是看（换目标在敌方棋盘）。 */
    private void clickOurBoard(double mx, double my) {
        int[] cell = cellAt(mx, my);
        if (cell == null) {
            return;
        }
        clickCell(cell[0], cell[1]);
    }

    private int foeIndexAt(double mx, double my) {
        int cx = (int) Math.floor((mx - ENEMY_BOARD_X) / BOARD_CELL);
        int cy = (int) Math.floor((my - BOARD_Y) / BOARD_CELL);
        if (cx < 0 || cy < 0 || cx >= FleetBoard.SIZE || cy >= FleetBoard.SIZE) {
            return -1;
        }
        List<FoeView> foes = foeViews();
        for (int i = 0; i < foes.size(); i++) {
            if (foes.get(i).unit().covers(cx, cy)) {
                return i;
            }
        }
        return -1;
    }

    private List<int[]> optionRects() {
        List<int[]> out = new ArrayList<>();
        if (stage() == Run.Stage.BATTLE) {
            List<Card> hand = hand();
            for (int i = 0; i < Math.min(CARD_MAX, hand.size()); i++) {
                out.add(cardRect(i));
            }
            return out;
        }
        int count = optionCount();
        if (count <= 0) {
            return out;
        }
        int y0 = OV_Y;
        int rowH = stage() == Run.Stage.PICK_LOADOUT ? PICK_ROW_H : OV_ROW_H;
        for (int i = 0; i < count; i++) {
            out.add(new int[]{OV_X, y0 + i * (rowH + OV_ROW_GAP), OV_W, rowH});
        }
        return out;
    }

    private int[] cardRect(int index) {
        return cardBarRect(index);
    }

    private int[] skipRect() {
        return new int[]{OV_X + OV_W - 96, OV_Y + 4 * (OV_ROW_H + OV_ROW_GAP) + 2, 96, 18};
    }

    // ---------------------------------------------------------------- 数据视图

    /** 我方棋盘上的一艘（含运行时血量）。 */
    private record OurView(FleetUnit unit, int hp, int maxHp, int block, boolean alive) {
    }

    /** 敌方棋盘上的一艘（战斗中就取实时值，没开打就取模板值）。 */
    private record FoeView(FleetUnit unit, Ship ship, String nameZh, int hp, int maxHp,
                           int block, int plate, int flak, boolean alive, boolean target) {
    }

    private List<FleetUnit> ourUnits() {
        Fleet fleet = run.fleet();
        return fleet == null ? List.of() : fleet.units();
    }

    private List<OurView> ourViews() {
        List<FleetUnit> units = ourUnits();
        if (units.isEmpty()) {
            return List.of();
        }
        FleetRoster roster = stage() == Run.Stage.BATTLE && run.battle() != null
                ? run.battle().fleetRoster() : null;
        List<OurView> out = new ArrayList<>(units.size());
        for (FleetUnit unit : units) {
            FleetRoster.Unit state = roster == null ? null : roster.byId(unit.id());
            int maxHp = state != null ? state.maxHp() : run.maxHpOf(unit);
            int hp = state != null ? state.hp() : run.hpOf(unit);
            int block = state != null ? state.block() : unit.armor();
            out.add(new OurView(unit, hp, maxHp, block, hp > 0));
        }
        return out;
    }

    private List<FoeView> foeViews() {
        Battle battle = run.battle();
        if (battle != null) {
            List<FoeView> out = new ArrayList<>(battle.foes().size());
            for (int i = 0; i < battle.foes().size(); i++) {
                Battle.Foe foe = battle.foes().get(i);
                out.add(new FoeView(foe.unit(), foe.hull(), foe.nameZh(), foe.hp(), foe.maxHp(),
                        foe.block(), foe.plate(), foe.flak(), foe.alive(),
                        i == battle.targetIndex()));
            }
            return out;
        }
        // 星图模式下没有"下一场"预览——下一场取决于跳哪颗星
        return List.of();
    }

    // ---------------------------------------------------------------- 绘制

    @Override
    public void draw(CanvasBuffer buffer) {
        drawGame(buffer);
        if (settingsOpen) {
            // 设置面板是最高的一块浮板：整屏把它画进一层再斜着贴回去
            buffer.beginLayer();
            drawSettingsOverlay(buffer, buffer.art());
            buffer.endLayer(UiTilt.UI, 0, 0, 640, 360);
        }
        if (backdropMenuOpen) {
            // 背景菜单是调试用的，压在最上面且不平贴（像素菜单不需要透视）
            buffer.beginLayer();
            drawBackdropMenu(buffer, buffer.art());
            buffer.endLayer(UiTilt.NONE, 0, 0, 640, 360);
        }
    }

    /** 画当前玩法画面（设置面板打开时也照常画在底下，只被压暗）。 */
    private void drawGame(CanvasBuffer buffer) {
        PixelArt art = buffer.art();
        // 先对一次锁定目标：进战斗/换目标后的第一帧就开始射箭
        syncLockAnimation();
        if (zoomShip) {
            drawZoomShip(buffer, art);
            drawToast(buffer);
            return;
        }
        // 全屏星图整屏盖住背景（星盘图 640×360 整幅不透明直贴），先画背景等于白画：
        // 实时 3D 背景那一路每帧都要渲染到 FBO 再 glReadPixels 读回、还要过一遍滤镜，
        // 白白吃 GPU（另一路程序化主题背景也是整幅重贴）。所以星图可见时直接跳过背景。
        if (starMapVisible()) {
            drawFullScreenStarMap(buffer, art);
            drawToast(buffer);
            return;
        }
        drawBackdrop(art, true);
        if (stage() == Run.Stage.EVENT) {
            // 事件屏自带标题，不再叠顶栏
            buffer.beginLayer();
            drawEventScreen(buffer, buffer.art());
            buffer.endLayer(UiTilt.UI, 0, 0, 640, 300);
            buffer.beginLayer();
            drawFooter(buffer);
            buffer.endLayer(UiTilt.UI.withDepth(1), 0, 300, 640, 60);
            buffer.beginLayer();
            drawToast(buffer);
            buffer.endLayer(UiTilt.UI.withDepth(3), 0, 200, 640, 160);
            return;
        }
        if (stage() == Run.Stage.LULL) {
            // 和太空港同一套版面：左边棋盘照样拖船变阵，右栏（原来敌人的位置）换成休整面板
            buffer.beginLayer();
            drawOurBoard(buffer, buffer.art());
            drawAuraCoverage(buffer.art());
            buffer.endLayer(UiTilt.BOARD, PORT_BOARD_X - 8, BOARD_Y, BOARD_SPAN + 16, BOARD_SPAN + 18);
            drawOurShips(buffer, art);
            buffer.beginLayer();
            drawPortraitFrame(buffer, buffer.art());
            PortraitLayer.draw(buffer.art());   // 立绘压在棋盘上、UI 下面
            drawPortraitFrameEdge(buffer.art());
            buffer.endLayer(UiTilt.UI, 0, 0, 130, 270);
            buffer.beginLayer();
            drawLullPanel(buffer, buffer.art());
            buffer.endLayer(UiTilt.UI, 0, 0, 640, 360);
            buffer.beginLayer();
            drawFooter(buffer);
            buffer.endLayer(UiTilt.UI.withDepth(1), 0, 300, 640, 60);
            buffer.beginLayer();
            drawToast(buffer);
            buffer.endLayer(UiTilt.UI.withDepth(3), 0, 200, 640, 160);
            drawSidePanel(buffer, art);
            return;
        }
        if (stage() == Run.Stage.SPACE_PORT) {
            buffer.beginLayer();
            drawOurBoard(buffer, buffer.art());
            drawAuraCoverage(buffer.art());
            buffer.endLayer(UiTilt.BOARD, PORT_BOARD_X - 8, BOARD_Y, BOARD_SPAN + 16, BOARD_SPAN + 18);
            drawOurShips(buffer, art);
            buffer.beginLayer();
            drawPortraitFrame(buffer, buffer.art());
            PortraitLayer.draw(buffer.art());   // 立绘压在棋盘上、UI 下面
            drawPortraitFrameEdge(buffer.art());
            buffer.endLayer(UiTilt.UI, 0, 0, 130, 270);
            buffer.beginLayer();
            drawSpacePortScreen(buffer, buffer.art());
            buffer.endLayer(UiTilt.UI, 0, 0, 640, 360);
            buffer.beginLayer();
            drawFooter(buffer);
            buffer.endLayer(UiTilt.UI.withDepth(1), 0, 300, 640, 60);
            buffer.beginLayer();
            drawToast(buffer);
            buffer.endLayer(UiTilt.UI.withDepth(3), 0, 200, 640, 160);
            drawSidePanel(buffer, art);   // 棋盘悬浮描述：太空港也能看船的数据
            return;
        }
        // 棋盘是整个画面最低、也是斜得最多的那个平面：一张浮在星幕里的战术台
        buffer.beginLayer();
        drawOurBoard(buffer, buffer.art());
        drawAuraCoverage(buffer.art());   // 战斗/编入：悬浮船的光环罩住谁，黄框画在棋盘上
        drawEnemyBoard(buffer, buffer.art());
        buffer.endLayer(UiTilt.BOARD, OUR_BOARD_X - 8, BOARD_Y, 640 - OUR_BOARD_X + 8, BOARD_SPAN + 18);
        drawOurShips(buffer, art);
        drawFoeShips(buffer, art);
        drawLockMarkers(art);   // 锁定框最后画：压在棋子之上，才不会被棋子的像素盖住
        // 战斗不打顶栏：血量/敌方/航程下方信息栏已经全有了，顶栏纯重复
        buffer.beginLayer();
        drawPortraitFrame(buffer, buffer.art());
        PortraitLayer.draw(buffer.art());   // 棋盘在最底下，立绘压在棋盘上、其余 UI 下面
        drawPortraitFrameEdge(buffer.art());
        buffer.endLayer(UiTilt.UI, 0, 0, 130, 270);
        boolean overlay = overlayStage();
        // 底部这一整块（控制台 + 左栏 + 双按钮 + 信息栏 + 手牌/选项）是同一块"浮板"：
        // 一起画进同一层再整块偏移，块内间距一点不变 —— 面板之间靠得近，浮起来也还是一块。
        buffer.beginLayer();
        if (stage() == Run.Stage.BATTLE) {
            drawCommandConsole(buffer, buffer.art());
            drawLeftPanel(buffer, buffer.art());
            drawCaptainControls(buffer, buffer.art());
            drawInfoBox(buffer, buffer.art());
        }
        if (overlay) {
            drawOverlay(buffer, buffer.art());
        } else {
            drawHandBars(buffer, buffer.art());
        }
        buffer.endLayer(UiTilt.UI, 0, 0, 640, 360);
        // 提示条再高一层（它是"弹出来的"，比固定面板更靠前）
        buffer.beginLayer();
        drawToast(buffer);
        buffer.endLayer(UiTilt.UI.withDepth(3), 0, 200, 640, 160);
        // 描述弹窗永远最后画、且不跟着视差走：它要贴着光标，位移会让人觉得"框跟手不同步"
        if (!overlay) {
            drawSidePanel(buffer, art);
        }
    }

    /**
     * 本场战斗用哪个天体当底图。每换一关、或同一关进了新的节点（visitedCount 变了）
     * 才随机一次，随机源跟着航程种子走，同一次航程可复现；背景工坊里手动指定的场景优先。
     */
    private int battleScene() {
        int manual = com.hysx.salvosequence.live.LiveBackdrops.selected();
        if (manual >= 0) {
            return manual;
        }
        // 调试键（F2）钉死的机位优先于随机，方便逐个看效果
        if (debugPresetIndex >= 0) {
            com.hysx.salvosequence.live.CameraPresets.Preset p =
                    com.hysx.salvosequence.live.CameraPresets.get(debugPresetIndex);
            return p.scene;
        }
        if (liveSceneRng == null) {
            liveSceneRng = new java.util.Random(run.seed() ^ 0x5EEDB00CL);
        }
        if (livePreset == null || run.node() != liveSceneNode || run.visitedCount() != liveSceneVisits) {
            liveSceneNode = run.node();
            liveSceneVisits = run.visitedCount();
            // 机位表覆盖近地轨道、恒星附近、远望等 48 种取景；一场仗抽一条，
            // 连星球类型一起绑定，重进同一节点不会换脸。
            livePreset = com.hysx.salvosequence.live.CameraPresets.pick(liveSceneRng);
            com.hysx.salvosequence.live.LiveBackdrops.applyPreset(livePreset);
            // 转速/配色这类连续量每场再掷一次（同一随机源，进同一节点可复现）
            com.hysx.salvosequence.live.LiveBackdrops.randomizeForBattle(
                    livePreset.scene, liveSceneRng);
            liveScene = livePreset.scene;
        }
        return liveScene;
    }

    private void drawBackdrop(PixelArt art, boolean twinkles) {
        // 实时 3D 背景优先：星云/恒星这类会动的场景每帧渲染 320×180 再 2 倍贴回来；
        // 没选、GL 没就绪或出错时退回程序化主题背景
        int live = battleScene();
        int[] liveFrame = com.hysx.salvosequence.live.LiveBackdrops.frame(live, time);
        if (liveFrame != null) {
            // 压暗：3D 场景当底图时不能抢棋盘和文字的对比度。各场景亮度差别很大
            // （星云本来就暗，恒星/脉冲星有大片高光），所以压暗系数从场景那里取
            int dim = com.hysx.salvosequence.live.LiveBackdrops.dimPercent(live);
            // 源帧是 15fps 才换一次的同一张 320×180（LiveBackdrops 自己缓存），
            // 每帧把 23 万像素「2 倍放大 + 压暗」重算一遍是纯浪费：
            // 只在源帧或压暗系数变了时合成一次，其余帧整幅行拷贝。
            if (liveFrame != liveBackdropSource || dim != liveBackdropDim || liveBackdropDrawn == null) {
                liveBackdropSource = liveFrame;
                liveBackdropDim = dim;
                if (liveBackdropDrawn == null) {
                    liveBackdropDrawn = new int[640 * 360];
                }
                int[] out = liveBackdropDrawn;
                for (int y = 0; y < 360; y++) {
                    int row = (y >> 1) * 320;
                    int dst = y * 640;
                    for (int x = 0; x < 640; x++) {
                        int c = liveFrame[row + (x >> 1)];
                        int r = ((c >> 16) & 0xFF) * dim / 100;
                        int g = ((c >> 8) & 0xFF) * dim / 100;
                        int b = (c & 0xFF) * dim / 100;
                        out[dst + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
                    }
                }
            }
            art.copyFrame(liveBackdropDrawn, 640, 360);
            return;
        }
        // 主题背景：战斗/太空港按（节点+访问+界面）确定性选播 10 主题 × 4 变体，
        // 静态层缓存一次，动态粒子（轨道/闪烁/光束）每帧叠加
        long pick = run.node() * 7919L + run.visitedCount() * 104729L
                + (stage() == Run.Stage.SPACE_PORT ? 13 : 0);
        java.util.Random pr = new java.util.Random(pick);
        int pool = Backdrops.THEMES + Backdrops.customCount();
        int theme = pr.nextInt(pool);
        int variant = theme < Backdrops.THEMES ? pr.nextInt(Backdrops.VARIANTS) : 0;
        if (backdropLayer == null || backdropKey != (int) (pick & 0x7fffffff)) {
            backdropKey = (int) (pick & 0x7fffffff);
            backdropLayer = Backdrops.staticLayer(theme, variant, pick);
        }
        // 主题背景是 640×360 整幅不透明，走 copyFrame 的行拷贝（通用 blitArgbTile
        // 要逐像素做整除缩放换算，整屏每次白算 23 万次）
        art.copyFrame(backdropLayer, 640, 360);
        Backdrops.animate(art, theme, variant, pick, time);
        if (!twinkles) {
            return;
        }
        // 闪烁星：旋臂分布（与星系同一密度场——臂上星为主），位置固定只闪亮度
        if (twinkleCache == null) {
            twinkleCache = buildTwinkles();
        }
        for (int i = 0; i < twinkleCache.size(); i++) {
            int[] t = twinkleCache.get(i);
            boolean far = i < TWINKLE_FAR;   // 前段远景
            if (far) {
                art.pixel(t[0], t[1], t[2] == 1 ? Palette.STAR_MID : Palette.STAR_FAR);
            } else {
                boolean bright = Math.sin(time * 2 + i * 1.7) > 0;
                art.pixel(t[0], t[1], bright ? Palette.STAR_NEAR : Palette.STAR_MID);
            }
        }
    }

    /** 旋臂分布的闪烁星坐标（远景 140 暗档 + 近景 70 亮档，同一对数臂密度场）。 */
    private java.util.List<int[]> buildTwinkles() {
        java.util.List<int[]> out = new ArrayList<>(TWINKLE_FAR + TWINKLE_NEAR);
        java.util.Random rnd = new java.util.Random(0x77L);
        double gx = 320;
        double gy = 186;
        double squash = Run.GALAXY_SQUASH;
        for (int i = 0; i < TWINKLE_FAR + TWINKLE_NEAR; i++) {
            int arm = rnd.nextInt(2);
            double t = 0.3 + rnd.nextDouble() * 12.8;
            double th = arm * Math.PI + t;
            double r = 16.0 * Math.exp(ARM_WIND * t);
            double sigma = 10.0 + r * 0.075;
            double rr = r + rnd.nextGaussian() * sigma;
            double aa = th + rnd.nextGaussian() * (12.0 / Math.max(8.0, r));
            int x = (int) (gx + Math.cos(aa) * rr);
            int y = (int) (gy + Math.sin(aa) * rr * squash);
            if (x < 4 || x > 635 || y < 4 || y > 355) { i--; continue; }
            int bright = rnd.nextInt(100) < 30 ? 1 : 0;
            out.add(new int[]{x, y, bright});
        }
        return out;
    }

    /**
     * 星系底层（560x350，一次生成烧缓存）：椭圆距离场的<b>放射渐变</b>盘面
     * （核球暖白光晕 → 蓝紫中带 → 暗外缘 → 盘外透明淡出，无硬边）和沿臂辉光。
     * 星尘逐帧粒子、尘埃云和核球光晕在其它层（见 buildDust / buildGalaxyOverlay）。
     * 缩放时整层视差 blit。
     */
    /** 底座边缘的单边正态钟形：d≤0.55 满幅，d=1.0（图层边界）处 ≈3%。 */
    private static double discEdgeBell(double d) {
        if (d <= 0.55) {
            return 1.0;
        }
        if (d >= 1.05) {
            return 0.0;
        }
        double f = (d - 0.55) / 0.24;
        return Math.exp(-f * f);
    }

    /** 椭圆归一距离（盘面 rx=280 / ry=172 口径）。 */
    private static double discNorm(double dxPx, double dyPx) {
        double dx = dxPx / 280.0;
        double dy = dyPx / 172.0;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 树状尘埃主丝：沿臂脊从内向外走一遍，位置在臂带内低频游走（扭曲）。
     * 厚段碎、薄段连——密度高的地方主丝碎成一节节独立粗团块（绸缎感断开），
     * 密度低的地方保持连续细丝，全臂墨量因此均匀。有墨的地方往两侧随机游走出
     * 细枝（渐细、渐淡、细处偏红），细枝上偶发二级分叉；另撒一批独立团块。
     */
    private void blotDustTrunk(int[] px, int w, int h, double cx, double cy,
                               double squash) {
        // 两条臂各走各的主丝（相位差 π）：随机流按臂分离——两臂的断口/细枝分布各自独立
        for (int arm = 0; arm < 2; arm++) {
            java.util.Random rnd = new java.util.Random(0xD057L + arm * 0x9E37L);
            double t0 = 1.2;
            double t1 = 13.0;
            int steps = 214;
            double prevX = 0;
            double prevY = 0;
            boolean wasPresent = false;
            for (int i = 0; i <= steps; i++) {
                double t = t0 + (t1 - t0) * i / steps;
                double r = 16.0 * Math.exp(ARM_WIND * t);
                double sigmaD = 13.0 + r * 0.07;
                // 主丝在带内游走：低频大摆（扭曲）+ 高频小抖（粗糙感）；噪声种子按臂错开
                double wander = sigmaD * 1.1 * (vnoise(t * 0.5, 7.7, 911 + arm * 53) - 0.5)
                        + sigmaD * 0.32 * (vnoise(t * 1.9, 3.3, 912 + arm * 47) - 0.5);
                double th = arm * Math.PI + t + wander / Math.max(r, 20.0);
                double x = cx + Math.cos(th) * r;
                double y = cy + Math.sin(th) * r * squash;
                double rad = 3.2 + t * 0.42;                   // 主丝向外渐粗
                double dens = 0.55 + 0.45 * vnoise(t * 0.8, 11.4, 913 + arm * 41);
                // 厚段碎、薄段连：闸门与密度反相关——越厚越碎成团块，越薄越连续
                double gate = Math.max(0.0, Math.min(1.0, (0.82 - dens) / 0.24));
                boolean present;
                if (gate > 0.05) {
                    dustBlot(px, w, h, x, y, rad, 150 * dens * gate, 18, 12, 11);
                    present = true;
                } else if (rnd.nextInt(4) == 0) {
                    // 厚段：碎成沿脊错开的粗团块（随机间隔、横向微偏，不是点线）
                    double chunkWander = sigmaD * 0.5 * (vnoise(t * 1.3, 5.5, 915) - 0.5);
                    double cth = th + chunkWander / Math.max(r, 20.0);
                    x = cx + Math.cos(cth) * r;
                    y = cy + Math.sin(cth) * r * squash;
                    dustBlot(px, w, h, x, y, rad * 1.2, 135 * dens, 20, 13, 12);
                    present = true;
                } else {
                    present = false;
                }
                if (present && wasPresent && i >= 4 && i % 3 == 1) {
                    double heading = Math.atan2(y - prevY, x - prevX);
                    int n = rnd.nextInt(100) < 35 ? 2 : 1;
                    for (int b = 0; b < n; b++) {
                        double side = rnd.nextBoolean() ? 1.0 : -1.0;
                        double hd = heading
                                + side * (Math.PI / 2 + (rnd.nextDouble() - 0.5) * 1.1);
                        blotDustBranch(px, w, h, x, y, hd,
                                rad * (0.45 + rnd.nextDouble() * 0.2),
                                10 + rnd.nextInt(16), side, rnd);
                    }
                }
                wasPresent = present;
                prevX = x;
                prevY = y;
            }
        }
        // 独立团块：与主丝不相连的斑状云块，散在两条臂的臂带两侧（含偏外侧），旧版质感
        for (int arm = 0; arm < 2; arm++) {
            java.util.Random rnd = new java.util.Random(0x17D1L + arm * 0x9E37L);
            for (int c = 0; c < 18; c++) {
                double t = 2.0 + rnd.nextDouble() * 10.5;
                double r = 16.0 * Math.exp(ARM_WIND * t);
                double sigmaD = 13.0 + r * 0.07;
                double side = rnd.nextBoolean() ? 1.0 : -1.0;
                double cross = side * sigmaD * (0.2 + rnd.nextDouble() * 0.4);
                double th = arm * Math.PI + t + cross / r;
                double x = cx + Math.cos(th) * r;
                double y = cy + Math.sin(th) * r * squash;
                blotDustClump(px, w, h, x, y, 4.0 + rnd.nextDouble() * 4.0, rnd);
            }
        }
    }

    /** 独立尘埃团块：5~9 个软斑叠成一朵，絮心深褐、絮缘一点暗红。 */
    private void blotDustClump(int[] px, int w, int h, double x, double y,
                               double rad, java.util.Random rnd) {
        int blobs = 5 + rnd.nextInt(5);
        for (int b = 0; b < blobs; b++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = rnd.nextDouble() * rad;
            double br = rad * (0.45 + rnd.nextDouble() * 0.5);
            dustBlot(px, w, h, x + Math.cos(a) * d, y + Math.sin(a) * d * 0.7,
                    br, 55 + rnd.nextInt(55),
                    18 + rnd.nextInt(40), 12 + rnd.nextInt(14), 11 + rnd.nextInt(12));
        }
        double ra = rnd.nextDouble() * Math.PI * 2;
        dustBlot(px, w, h, x + Math.cos(ra) * rad * 0.9,
                y + Math.sin(ra) * rad * 0.63, rad * 0.5,
                40 + rnd.nextInt(30), 138, 45, 41);            // 絮缘暗红一笔
    }

    /** 尘埃细枝：随机游走（方向扰动 + 缓慢外偏），渐细渐淡，细处透出暗红 Hα 边缘。 */
    private void blotDustBranch(int[] px, int w, int h, double x, double y,
                                double heading, double width, int steps, double side,
                                java.util.Random rnd) {
        double hd = heading;
        double w0 = width;
        for (int s = 0; s < steps && width > 0.8; s++) {
            hd += rnd.nextGaussian() * 0.30 + side * 0.045;
            x += Math.cos(hd) * 2.6;
            y += Math.sin(hd) * 2.6;
            width *= 0.94;
            double fade = Math.pow(width / w0, 0.6);
            int cr = (int) (18 + 122 * (1 - fade));            // 粗端黑褐 ↔ 细端暗红
            int cg = (int) (12 + 34 * (1 - fade));
            int cb = (int) (11 + 31 * (1 - fade));
            dustBlot(px, w, h, x, y, width, 120 * fade, cr, cg, cb);
            if (s > 4 && rnd.nextInt(100) < 12) {
                blotDustBranch(px, w, h, x, y,
                        hd + side * (0.6 + rnd.nextDouble() * 0.5),
                        width * 0.55, Math.max(3, steps / 2), side, rnd);
            }
        }
    }

    /** 尘埃软笔触：二次缓出圆斑，src-over 合成（重叠处自然变实，不会突变）。 */
    private static void dustBlot(int[] px, int w, int h, double bx, double by,
                                 double rad, double alpha, int cr, int cg, int cb) {
        int x0 = Math.max(0, (int) Math.floor(bx - rad));
        int x1 = Math.min(w - 1, (int) Math.ceil(bx + rad));
        int y0 = Math.max(0, (int) Math.floor(by - rad));
        int y1 = Math.min(h - 1, (int) Math.ceil(by + rad));
        if (x0 > x1 || y0 > y1 || alpha <= 0) { return; }
        double sa = Math.min(1.0, alpha / 255.0);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double d = Math.hypot(x + 0.5 - bx, y + 0.5 - by) / rad;
                if (d >= 1.0) { continue; }
                double wa = sa * (1.0 - d) * (1.0 - d);
                int i = y * w + x;
                double da = ((px[i] >>> 24) & 0xFF) / 255.0;
                double oa = wa + da * (1.0 - wa);
                if (oa < 0.004) { px[i] = 0; continue; }
                int pr = (px[i] >> 16) & 0xFF;
                int pg = (px[i] >> 8) & 0xFF;
                int pb = px[i] & 0xFF;
                int nr = (int) Math.round((cr * wa + pr * da * (1.0 - wa)) / oa);
                int ng = (int) Math.round((cg * wa + pg * da * (1.0 - wa)) / oa);
                int nb = (int) Math.round((cb * wa + pb * da * (1.0 - wa)) / oa);
                px[i] = (((int) Math.round(oa * 255)) << 24) | (nr << 16) | (ng << 8) | nb;
            }
        }
    }

    /** 把形状函数按覆盖面积平均到 size×size 的格子里（1~2px 时也能保住星心）。 */
    private static byte[] rasterizeStarShape(int id, int size) {
        final int master = 21;
        byte[] out = new byte[size * size];
        boolean any = false;
        boolean anyBright = false;
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int m0x = px * master / size;
                int m1x = Math.max(m0x + 1, (px + 1) * master / size);
                int m0y = py * master / size;
                int m1y = Math.max(m0y + 1, (py + 1) * master / size);
                int sum = 0;
                int cnt = 0;
                for (int my = m0y; my < m1y && my < master; my++) {
                    for (int mx = m0x; mx < m1x && mx < master; mx++) {
                        double nx = (mx + 0.5) / master * 2 - 1;
                        double ny = (my + 0.5) / master * 2 - 1;
                        sum += starShapeLevel(id, nx, ny);
                        cnt++;
                    }
                }
                int lv = cnt == 0 ? 0 : (int) Math.round(sum / (double) cnt);
                out[py * size + px] = (byte) lv;
                any |= lv > 0;
                anyBright |= lv >= 3;
            }
        }
        if (!any) {
            out[(size / 2) * size + size / 2] = 3;
        } else if (!anyBright) {
            // 偶边长/纯光晕形状可能摊不出最亮格，强制给中心补一个亮核，
            // 避免放大后出现“中间一格是暗的”星。
            int c = (size / 2) * size + (size / 2);
            out[c] = 3;
        }
        return out;
    }

    /** 形状函数：归一化坐标 [-1,1] → 格子亮度 0..3（3 最亮）。 */
    private static int starShapeLevel(int id, double nx, double ny) {
        double ax = Math.abs(nx);
        double ay = Math.abs(ny);
        double r = Math.sqrt(nx * nx + ny * ny);
        switch (id) {
            case 0: // 四角星：十字细芒 + 亮核，星芒越靠外越暗
                if (r < 0.28) {
                    return 3;
                }
                if ((ax < 0.10 && ay < 1.0) || (ay < 0.10 && ax < 1.0)) {
                    return Math.max(ax, ay) < 0.5 ? 2 : 1;
                }
                return 0;
            case 1: // 八芒：十字 + 斜向短芒
                if (r < 0.26) {
                    return 3;
                }
                if ((ax < 0.09 && ay < 1.0) || (ay < 0.09 && ax < 1.0)) {
                    return Math.max(ax, ay) < 0.5 ? 2 : 1;
                }
                if (Math.abs(ax - ay) < 0.11 && ax > 0.24 && ax < 0.68) {
                    return 1;
                }
                return 0;
            case 2: { // 菱形
                double d = ax + ay;
                if (d < 0.36) {
                    return 3;
                }
                if (d < 0.7) {
                    return 2;
                }
                if (d < 0.95) {
                    return 1;
                }
                return 0;
            }
            case 3: // 圆点光晕
                if (r < 0.33) {
                    return 3;
                }
                if (r < 0.63) {
                    return 2;
                }
                if (r < 0.92) {
                    return 1;
                }
                return 0;
            case 4: // 长芒：横向长、纵向短，像望远镜里的星芒
                if (r < 0.24) {
                    return 3;
                }
                if (ax < 0.09 && ay < 1.0) {
                    return ay < 0.42 ? 2 : 1;
                }
                if (ay < 0.09 && ax < 0.78) {
                    return ax < 0.3 ? 2 : 1;
                }
                return 0;
            case 5: // 四点星：亮核 + 上下左右四颗分离的小星
                if (r < 0.24) {
                    return 3;
                }
                if ((ax < 0.12 && ay > 0.55 && ay < 0.95)
                        || (ay < 0.12 && ax > 0.55 && ax < 0.95)) {
                    return 2;
                }
                return 0;
            case 6: // 斜芒：只有两条斜向星芒
                if (r < 0.22) {
                    return 3;
                }
                if (Math.abs(ax - ay) < 0.10 && r > 0.28 && r < 0.95) {
                    return r < 0.55 ? 2 : 1;
                }
                return 0;
            default:
                return 0;
        }
    }

    /** 闪烁亮度表：0..255 相位 → 60..125 的亮度百分比，均值约 92，整体亮度基本不变。 */
    private static int[] buildTwinkleTable() {
        int[] out = new int[256];
        for (int i = 0; i < 256; i++) {
            double wave = Math.sin(i * Math.PI * 2 / 256);
            out[i] = (int) Math.round(100 * (0.92 + 0.33 * wave));
        }
        return out;
    }

    /** 整数坐标 hash → [0,1)：确定性 value noise 的网格值（不依赖调用顺序）。 */
    private static double hash01(int ix, int iy, int seed) {
        int h = (ix * 374761393 + iy * 668265263) ^ seed;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    /** 平滑 value noise：网格角随机值 + smoothstep 双线性插值。 */
    private static double vnoise(double x, double y, int seed) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        double fx = x - ix;
        double fy = y - iy;
        double sx = fx * fx * (3 - 2 * fx);
        double sy = fy * fy * (3 - 2 * fy);
        double v00 = hash01(ix, iy, seed);
        double v10 = hash01(ix + 1, iy, seed);
        double v01 = hash01(ix, iy + 1, seed);
        double v11 = hash01(ix + 1, iy + 1, seed);
        return (v00 * (1 - sx) + v10 * sx) * (1 - sy) + (v01 * (1 - sx) + v11 * sx) * sy;
    }

    /** 臂坐标系参考半径：臂公式 r=16·e^(b·θ) 以设计单位计，尘埃云在归一椭圆坐标里
     * 采样时用 rxRef 把归一距离还原回设计距离（squash 使归一空间里臂呈圆形）。 */
    private static final double rxRef = 262.0;
    /** 对数旋臂的缠紧程度：r = 16·e^(b·θ) 的 b。0.29（pitch≈16°）整臂只绕 1.3 圈，
     * 视觉上"旋度不够"；0.21（pitch≈12°，接近真实星系）绕满 2 圈多才到盘缘。 */
    private static final double ARM_WIND = 0.21;

    /**
     * 顶栏：<b>已停用</b>，保留代码不再调用。
     *
     * <p>战斗里血量/敌方/航程下方信息栏都有，太空港和休整又各有自己的标题条，
     * 顶栏叠上去纯属重复还压字；能量挪进了指令台标题行（见 drawCommandConsole）。
     * 留着这块只为以后真需要一条通栏时改起来快。</p>
     */
    @Deprecated
    private void drawTopBarLayer(CanvasBuffer buffer) {
        buffer.beginLayer();
        drawTopBar(buffer, buffer.art());
        buffer.endLayer(UiTilt.UI.withDepth(1), 0, 0, 640, 22);
    }

    private void drawTopBar(CanvasBuffer buffer, PixelArt art) {
        // 切角通栏：暗底 + 双层钢蓝框 + 左端琥珀刻线。星幕协议的所有栏位都是这个形状，
        // 不再是"一条 1px 白线横在白字下面"。
        UiChrome.chamferFill(art, BAR_X, BAR_Y, 632, BAR_H, 3, Palette.PANEL);
        UiChrome.chamferFrame(art, BAR_X, BAR_Y, 632, BAR_H, 3, Palette.STEEL_DIM);
        art.hline(BAR_X + 4, BAR_X + 627, BAR_Y + 1, Palette.STEEL_FAINT);
        art.fillRect(BAR_X + 3, BAR_Y + BAR_H / 2 - 1, 2, 3, Palette.AMBER);
        art.fillRect(BAR_X + 630, BAR_Y + BAR_H / 2 - 1, 2, 3, Palette.AMBER);

        // 通栏自上而下排：所有位置都按实测字宽往后推，写死坐标一换数字就压字
        double x = BAR_X + 9;
        String route = Language.t("航程 ", "ROUTE ") + (run.node() + 1) + "/" + run.routeLength();
        zh(buffer, route, x, BAR_Y + 2, 12, Palette.STEEL);
        x += textWidth(route, 12) + 6;
        for (int i = 0; i < run.routeLength(); i++) {
            boolean done = i < run.node();
            boolean here = i == run.node();
            int px = (int) x + i * 9;
            // 航程刻度：走过的亮青绿、当前的实心琥珀（带一格高光）、未到的暗蓝
            art.fillRect(px, BAR_Y + 6, 6, 5,
                    done ? Palette.TEAL : here ? Palette.AMBER : Palette.STEEL_FAINT);
            if (here) {
                art.hline(px, px + 5, BAR_Y + 5, Palette.WARN);
            }
            if (!done && !here) {
                art.hline(px, px + 5, BAR_Y + 10, Palette.PANEL_EDGE);
            }
        }
        x += run.routeLength() * 9 + 8;
        UiChrome.vRule(art, (int) x, BAR_Y + 3, BAR_H - 6);
        x += 6;

        // 我方结构值（全队合计）
        String ourLabel = Language.t("我方", "OURS");
        zh(buffer, ourLabel, x, BAR_Y + 2, 12, Palette.LINE_DIM);
        x += textWidth(ourLabel, 12) + 4;
        boolean hasFleet = stage() != Run.Stage.PICK_LOADOUT;
        bar(art, (int) x, BAR_Y + 6, 88, 5,
                hasFleet ? ratio(run.hp(), run.maxHp()) : 0,
                hasFleet ? hpColor(run.hp(), run.maxHp()) : Palette.PANEL_ALT);
        x += 88 + 4;
        String hpText = hasFleet ? run.hp() + "/" + run.maxHp() : "-/-";
        en(art, hpText, (int) x, BAR_Y + 5, Palette.LINE);
        x += font.measure(hpText) + 8;
        UiChrome.vRule(art, (int) x - 4, BAR_Y + 3, BAR_H - 6);

        // 敌方编队（只在战斗中显示——星图模式下"下一场"取决于跳哪颗星，没有预览）
        List<FoeView> foes = stage() == Run.Stage.BATTLE ? foeViews() : List.of();
        int alive = 0;
        int total = 0;
        int max = 0;
        for (FoeView foe : foes) {
            if (foe.alive()) {
                alive++;
            }
            total += Math.max(0, foe.hp());
            max += foe.maxHp();
        }
        if (stage() == Run.Stage.LULL) {
            // 休整时场上没有敌人：这一格改成"下一波"的预告，别显示 0/0
            String next = Language.t("下一波 " + run.wave() + "/" + run.waveCount() + " · 战术休整",
                    "NEXT WAVE " + run.wave() + "/" + run.waveCount() + " · LULL");
            zh(buffer, next, x, BAR_Y + 2, 12, Palette.WARN);
            x += textWidth(next, 12) + 4;
            bar(art, (int) x, BAR_Y + 6, 70, 5, 0, Palette.PANEL_ALT);
        }
        if (stage() != Run.Stage.LULL) {
            String foeLabel = Language.t("敌方 ", "ENEMY ") + alive + "/" + foes.size();
            zh(buffer, foeLabel, x, BAR_Y + 2, 12, Palette.FOE_GLOW);
            x += textWidth(foeLabel, 12) + 4;
            bar(art, (int) x, BAR_Y + 6, 70, 5, ratio(total, max), Palette.CRIMSON);
            x += 70 + 4;
            String foeText = total + "/" + max;
            en(art, foeText, (int) x, BAR_Y + 5, Palette.LINE_DIM);
        }

        // 能量（右对齐）；休整时没有战斗，不画
        if (stage() == Run.Stage.LULL) {
            return;
        }
        int energy = run.battle() == null ? 0 : run.battle().energy();
        int maxEnergy = run.battle() == null ? 0 : run.battle().maxEnergy();
        int pips = Math.max(1, maxEnergy);
        int pipsX = 632 - (pips * 9 - 2);
        if (Language.isEnglish()) {
            en(art, "EN", pipsX - 16, BAR_Y + 5, Palette.TEAL);
        } else {
            zh(buffer, "能量", pipsX - 28, BAR_Y + 2, 12, Palette.TEAL);
        }
        // 能量格：满格是青绿实心、空槽是暗蓝，格子上缘留一条高光当作"发光"
        for (int i = 0; i < pips; i++) {
            int px = pipsX + i * 9;
            if (i < energy) {
                art.fillRect(px, BAR_Y + 5, 7, 8, Palette.TEAL);
                art.hline(px, px + 6, BAR_Y + 5, Palette.WARN);
                art.hline(px, px + 6, BAR_Y + 12, Palette.TEAL_DIM);
            } else {
                art.fillRect(px, BAR_Y + 5, 7, 8, Palette.STEEL_FAINT);
                art.hline(px, px + 6, BAR_Y + 12, Palette.PANEL_EDGE);
            }
        }
    }

    /** 我方棋盘这一帧画在哪：战斗在左中（左边留给状态栏）；战术休整/太空港挪到最右，中间让给面板。 */
    private int ourBoardX() {
        return stage() == Run.Stage.LULL || stage() == Run.Stage.SPACE_PORT
                ? PORT_BOARD_X : OUR_BOARD_X;
    }

    // ---------------------------------------------------------------- 两块棋盘

    private void drawOurBoard(CanvasBuffer buffer, PixelArt art) {
        drawBoardBase(buffer, art, ourBoardX(), "我方舰队", "OUR FLEET",
                Palette.CYAN_DIM, Palette.CYAN);
        List<OurView> views = ourViews();
        if (views.isEmpty()) {
            zh(buffer, stage() == Run.Stage.PICK_LOADOUT ? "还没选舰队" : "舰队为空",
                    ourBoardX() + 8, BOARD_Y + 8, 12, Palette.LINE_FAINT);
        }

        // 敌方锁定：我方前排（x 最大）披红菱形 + 四角箭矢。标记本身在棋子之后画
        // （见 drawLockMarkers），否则会被棋子盖掉一半。

        // 编入新船：只把当前预览画实，其余候选点一个小角标（134 个都铺底会糊成一片）
        if (stage() == Run.Stage.SLOT) {
            FleetUnit spot = previewSpot();
            for (FleetUnit cand : candidates()) {
                int x = ourBoardX() + cand.x() * BOARD_CELL;
                int y = BOARD_Y + cand.y() * BOARD_CELL;
                boolean on = spot != null && spot.x() == cand.x() && spot.y() == cand.y();
                if (on) {
                    art.ditherRect(x, y, cand.w() * BOARD_CELL - 1, cand.h() * BOARD_CELL - 1,
                            Palette.CYAN, 1);
                    art.frame(x - 1, y - 1, cand.w() * BOARD_CELL + 1,
                            cand.h() * BOARD_CELL + 1, Palette.CYAN);
                } else {
                    art.pixel(x, y, Palette.CYAN_DIM);
                    art.pixel(x + 1, y, Palette.CYAN_DIM);
                    art.pixel(x, y + 1, Palette.CYAN_DIM);
                }
            }
        }
        // 太空港采购：画候选落点角标
        if (stage() == Run.Stage.SPACE_PORT && run.pendingBuyShip() != null) {
            Ship incoming = run.pendingBuyShip();
            for (int[] spot : run.fleet().placementsFor(incoming)) {
                int x = ourBoardX() + spot[0] * BOARD_CELL;
                int y = BOARD_Y + spot[1] * BOARD_CELL;
                art.pixel(x, y, Palette.CYAN_DIM);
                art.pixel(x + 1, y, Palette.CYAN_DIM);
                art.pixel(x, y + 1, Palette.CYAN_DIM);
            }
        }
        if (!overlayStage()) {

        }
    }

    private void drawEnemyBoard(CanvasBuffer buffer, PixelArt art) {
        drawBoardBase(buffer, art, ENEMY_BOARD_X, "敌方舰队", "ENEMY",
                Palette.FOE_DEEP, Palette.FOE_GLOW);
        List<FoeView> foes = foeViews();
        // 悬浮敌舰的光环提示：和我方棋盘同款——被这艘敌舰光环罩到的敌舰描黄框
        drawFoeAuraCoverage(art, foes);
        // 锁定框不在这里画：它要在棋子之后覆盖上去，见 drawLockMarkers
        if (!overlayStage()) {

        }
    }

    /**
     * 两个棋盘上的锁定标记：敌方锁我方的红菱形、我方锁的敌方菱形。
     *
     * <p>必须在棋子<b>之后</b>画：棋子是 16px 一格的实心像素图，先画标记会被整个盖住，
     * 只露出四个角的一点点箭矢。</p>
     */
    private void drawLockMarkers(PixelArt art) {
        if (stage() != Run.Stage.BATTLE) {
            return;
        }
        Battle battle = run.battle();
        if (battle == null) {
            return;
        }
        // 我方前排：敌方的下一次攻击落点，红菱形 + 箭矢（被换船吃掉时重播）
        var front = battle.fleetRoster().pick(false);
        if (front != null && front.ship() != null) {
            FleetUnit unit = front.ship();
            // 位置必须和棋子走同一套透视投影（shipLeft/shipTop），否则棋盘倾斜后
            // 框会贴在格子左上角、跟棋子错开
            double bx = ourBoardX() + unit.x() * BOARD_CELL;
            double by = BOARD_Y + unit.y() * BOARD_CELL;
            int x = shipLeft(bx, by, unit.w(), unit.h());
            int y = shipTop(bx, by, unit.w(), unit.h());
            var look = ShipPainter.lookOf(unit.ship());
            drawLockMarker(art, x, y,
                    shipSpanW(look, unit.w()), shipSpanH(look, unit.h()),
                    foeLockStart, Palette.DANGER, Palette.LOCK_HOT, Palette.LOCK_DIM);
        }
        // 敌方：我方当前点名的那艘，打沉/换目标时重播一次
        int ti = battle.targetIndex();
        if (ti >= 0 && ti < battle.foes().size()) {
            Battle.Foe foe = battle.foes().get(ti);
            if (foe.alive()) {
                FleetUnit unit = foe.unit();
                double bx = ENEMY_BOARD_X + unit.x() * BOARD_CELL;
                double by = BOARD_Y + unit.y() * BOARD_CELL;
                int x = shipLeft(bx, by, unit.w(), unit.h());
                int y = shipTop(bx, by, unit.w(), unit.h());
                var look = ShipPainter.lookOfEnemy(unit.ship());
                drawLockMarker(art, x, y,
                        shipSpanW(look, unit.w()), shipSpanH(look, unit.h()));
            }
        }
    }

    /** 棋子实际画出来的像素宽（外观图格数 × 格宽），取不到时退回占格宽。 */
    private static int shipSpanW(com.hysx.salvosequence.fleet.AppearanceDesign look, int wCells) {
        int span = ShipPainter.drawnWidth(look, BOARD_CELL);
        return span > 0 ? span : wCells * BOARD_CELL;
    }

    /** 同 {@link #shipSpanW}，纵向。 */
    private static int shipSpanH(com.hysx.salvosequence.fleet.AppearanceDesign look, int hCells) {
        int span = ShipPainter.drawnHeight(look, BOARD_CELL);
        return span > 0 ? span : hCells * BOARD_CELL;
    }

    /**
     * 棋子的屏幕位置：<b>位置</b>走棋盘的透视，<b>像素</b>不参与变形。
     *
     * <p>棋子是 16px 一格的像素图，跟着棋盘一起透视会被重采样，边缘和舷窗全糊，
     * 后续还要在这上面叠血条、光环、选中框，缩放一下就全错位。所以棋盘的框、网格、铭牌
     * 照常透视，棋子单独按透视算出的坐标正着画——位置贴合棋盘的透视，形不变。</p>
     *
     * <p>落点取<b>投影后格子的中心</b>：棋盘近端比远端大 2% 左右，棋子保持原尺寸时
     * 这点误差摊在两边，比贴左上角、误差全堆在远侧要正。</p>
     */
    private static int shipLeft(double bx, double by, int wCells, int hCells) {
        double left = UiTilt.BOARD.toX(bx, by);
        double right = UiTilt.BOARD.toX(bx + wCells * BOARD_CELL, by + hCells * BOARD_CELL);
        return (int) Math.round((left + right) / 2 - wCells * BOARD_CELL / 2.0);
    }

    private static int shipTop(double bx, double by, int wCells, int hCells) {
        double top = UiTilt.BOARD.toY(bx, by);
        double bottom = UiTilt.BOARD.toY(bx + wCells * BOARD_CELL, by + hCells * BOARD_CELL);
        return (int) Math.round((top + bottom) / 2 - hCells * BOARD_CELL / 2.0);
    }

    /** 我方棋子（正着画）+ 血条 + 格挡框 + 悬浮框。 */
    private void drawOurShips(CanvasBuffer buffer, PixelArt art) {
        List<OurView> views = ourViews();
        for (int i = 0; i < views.size(); i++) {
            OurView view = views.get(i);
            FleetUnit unit = view.unit();
            double bx = ourBoardX() + unit.x() * BOARD_CELL;
            double by = BOARD_Y + unit.y() * BOARD_CELL;
            int x = shipLeft(bx, by, unit.w(), unit.h());
            int y = shipTop(bx, by, unit.w(), unit.h());
            drawShip(art, unit.ship(), x, y, unit.w(), unit.h(), view.alive(), false, time);
            if (view.alive()) {
                bar(art, x + 1, y + unit.h() * BOARD_CELL - 3, unit.w() * BOARD_CELL - 2, 2,
                        ratio(view.hp(), view.maxHp()), hpColor(view.hp(), view.maxHp()));
                if (view.block() > 0) {
                    art.frame(x - 1, y - 1, unit.w() * BOARD_CELL + 1, unit.h() * BOARD_CELL + 1,
                            Palette.BLUE_DIM);
                }
            }
            if (hoveredOurShip == i) {
                art.frame(x - 2, y - 2, unit.w() * BOARD_CELL + 3, unit.h() * BOARD_CELL + 3,
                        Palette.CYAN);
            }
        }
    }

    /** 敌方棋子，同款处理。 */
    private void drawFoeShips(CanvasBuffer buffer, PixelArt art) {
        List<FoeView> foes = foeViews();
        for (int i = 0; i < foes.size(); i++) {
            FoeView foe = foes.get(i);
            FleetUnit unit = foe.unit();
            double bx = ENEMY_BOARD_X + unit.x() * BOARD_CELL;
            double by = BOARD_Y + unit.y() * BOARD_CELL;
            int x = shipLeft(bx, by, unit.w(), unit.h());
            int y = shipTop(bx, by, unit.w(), unit.h());
            drawShip(art, foe.ship(), x, y, unit.w(), unit.h(), foe.alive(), true, time);
            if (foe.alive()) {
                bar(art, x + 1, y + unit.h() * BOARD_CELL - 3, unit.w() * BOARD_CELL - 2, 2,
                        ratio(foe.hp(), foe.maxHp()), hpColor(foe.hp(), foe.maxHp()));
                if (foe.block() > 0) {
                    art.frame(x - 1, y - 1, unit.w() * BOARD_CELL + 1, unit.h() * BOARD_CELL + 1,
                            Palette.BLUE_DIM);
                }
            }
            if (hoveredFoe == i) {
                art.frame(x - 3, y - 3, unit.w() * BOARD_CELL + 5, unit.h() * BOARD_CELL + 5,
                        Palette.FOE_GLOW);
            }
        }
    }

    private void drawBoardBase(CanvasBuffer buffer, PixelArt art, int x0, String tagZh,
                               String tagEn, com.hysx.salvosequence.core.Color unusedDeep,
                               com.hysx.salvosequence.core.Color tagColor) {
        // 棋盘容器：切角钢框板 + 四角琥珀括号（星幕的"视窗"）；底色不透明，网点只是底噪。
        // 用「凹陷」而不是「浮起」：棋盘是全场最低的一层，不能有投影，靠内外明暗读成嵌进台面。
        UiChrome.panelInset(art, x0, BOARD_Y, BOARD_SPAN, BOARD_SPAN);
        // 盘面是半透明玻璃：底下星云一透上来，交点暗点就不够读了，所以先压一层很淡的格线。
        // 0.42 的暗色叠在星云上是「刻在玻璃上的格」，既压得住底噪，又不至于把云闷死。
        com.hysx.salvosequence.core.Color cellLine = com.hysx.salvosequence.core.Color.rgb(6, 9, 14, 0.42);
        for (int g = 1; g < BOARD_SPAN / BOARD_CELL; g++) {
            art.vline(BOARD_Y + 1, BOARD_Y + BOARD_SPAN - 2, x0 + g * BOARD_CELL, cellLine);
            art.hline(x0 + 1, x0 + BOARD_SPAN - 2, BOARD_Y + g * BOARD_CELL, cellLine);
        }
        // 格子交点上的暗点：空盘也有战术网格感；船画上去会把点盖住，不抢船
        for (int gx = 1; gx < BOARD_SPAN / BOARD_CELL; gx++) {
            for (int gy = 1; gy < BOARD_SPAN / BOARD_CELL; gy++) {
                art.pixel(x0 + gx * BOARD_CELL, BOARD_Y + gy * BOARD_CELL,
                        Palette.STEEL_FAINT);
            }
        }
        // 四沿的量程刻度：每 4 格（64px）一道长刻度，空盘也读得出"这是一块有刻度的屏"
        for (int g = 4; g < BOARD_SPAN / BOARD_CELL; g += 4) {
            int gx = x0 + g * BOARD_CELL;
            int gy = BOARD_Y + g * BOARD_CELL;
            art.vline(BOARD_Y + 4, BOARD_Y + 6, gx, Palette.STEEL_DIM);
            art.hline(x0 + 4, x0 + 6, gy, Palette.STEEL_DIM);
            art.vline(BOARD_Y + BOARD_SPAN - 7, BOARD_Y + BOARD_SPAN - 5, gx, Palette.STEEL_DIM);
            art.hline(x0 + BOARD_SPAN - 7, x0 + BOARD_SPAN - 5, gy, Palette.STEEL_DIM);
        }
        // 归属铭牌：挂在棋盘下沿外侧的一小块底板。以前 8px 小字直接压在星云上，
        // 对比度不够，还紧贴指令台顶边、读起来像指令台自己的标签。
        if (!tagEn.isEmpty()) {
            int tw = UiChrome.measureLatin(tagEn) + 13;
            int tx = x0 + 6;
            // 压在棋盘下沿内侧 1px：读作"挂在视窗下沿的铭牌"，又不至于被下方指令台切掉半行字
            int ty = BOARD_Y + BOARD_SPAN - 9;
            art.fillRect(tx, ty, tw, 9, Palette.PANEL);
            art.hline(tx, tx + tw - 1, ty, Palette.PANEL_EDGE);
            art.fillRect(tx + 2, ty + 3, 2, 4, tagColor);
            UiChrome.latin(art, tagEn, tx + 7, ty + 1, Palette.STEEL_DIM);
        }
    }

    private void boardTag(CanvasBuffer buffer, PixelArt art, String text, int x, int y,
                          com.hysx.salvosequence.core.Color color) {
        double w = textWidth(text, 12);
        art.rect(x - 1, y - 2, (int) Math.ceil(w) + 4, 14, Palette.BACKDROP);
        zh(buffer, text, x + 2, y - 1, 12, color);
    }

    private void drawShip(PixelArt art, Ship ship, int x, int y, int w, int h,
                          boolean alive, boolean enemy, double now) {
        if (!alive) {
            // 沉了：画一片暗色残骸，不再画外观图
            art.rect(x + 1, y + 1, w * BOARD_CELL - 2, h * BOARD_CELL - 2,
                    enemy ? Palette.FOE_DEEP : Palette.SHIP_DEEP);
            art.line(x + 2, y + 2, x + w * BOARD_CELL - 3, y + h * BOARD_CELL - 3, Palette.LINE_FAINT);
            art.line(x + 2, y + h * BOARD_CELL - 3, x + w * BOARD_CELL - 3, y + 2, Palette.LINE_FAINT);
            return;
        }
        if (enemy) {
            ShipPainter.drawEnemy(art, ship, x, y, BOARD_CELL, now);
        } else {
            ShipPainter.draw(art, ship, x, y, BOARD_CELL, now);
        }
    }

    /**
     * 目标锁定标记：四角箭矢射向菱形，命中后只剩红色菱形框（常驻，随呼吸微胀）。
     * 动画由 {@link #lockAnimStart} 驱动，{@link #syncLockAnimation()} 在目标变化时重置。
     *
     * <p>尺寸收的是<b>棋子实际画出来的像素宽高</b>（外观图的格数 × {@link #BOARD_CELL}），
     * 不是占格数——船体常画得比占格宽，用占格居中会让菱形偏到船的一侧。</p>
     */
    private void drawLockMarker(PixelArt art, int x, int y, int spanW, int spanH) {
        drawLockMarker(art, x, y, spanW, spanH, lockAnimStart,
                Palette.LOCK, Palette.LOCK_HOT, Palette.LOCK_DIM);
    }

    /**
     * 同一套锁定动画的通用版：敌方锁我方时换成警示红，动画起点单独计时。
     */
    private void drawLockMarker(PixelArt art, int x, int y, int spanW, int spanH, double start,
                                com.hysx.salvosequence.core.Color edgeColor,
                                com.hysx.salvosequence.core.Color hotColor,
                                com.hysx.salvosequence.core.Color dimColor) {
        int cx = x + spanW / 2;
        int cy = y + spanH / 2;
        int base = Math.max(spanW, spanH) / 2 + 7;
        double t = time - start;
        // 1) 四角箭矢：0–0.30s 飞到菱形边上，留 0.15s 做"命中"亮一档
        if (t >= 0 && t < 0.45) {
            double p = Math.min(1.0, t / 0.30);
            double ease = p * p * (3 - 2 * p);
            int hitR = base + 8;
            int[][] corners = {{0, 0}, {639, 0}, {0, 359}, {639, 359}};
            for (int[] c : corners) {
                double dx = cx - c[0];
                double dy = cy - c[1];
                double len = Math.hypot(dx, dy);
                if (len < 1) {
                    continue;
                }
                double ux = dx / len;
                double uy = dy / len;
                double ex = c[0] + (cx - ux * hitR - c[0]) * ease;
                double ey = c[1] + (cy - uy * hitR - c[1]) * ease;
                drawLockArrow(art, ex, ey, ux, uy, p >= 0.999, hotColor, edgeColor, dimColor);
            }
        }
        // 2) 菱形框：0.18s 起从中心张开到常驻尺寸，之后轻微呼吸
        double grow = Math.min(1.0, Math.max(0.0, (t - 0.18) / 0.22));
        int r = (int) Math.rint(base * (0.30 + 0.70 * grow))
                + (int) Math.rint(Math.sin(time * 5.0) * 0.6);
        boolean flash = t < 0.55;
        com.hysx.salvosequence.core.Color edge = flash ? hotColor : edgeColor;
        art.line(cx, cy - r, cx + r, cy, edge);
        art.line(cx + r, cy, cx, cy + r, edge);
        art.line(cx, cy + r, cx - r, cy, edge);
        art.line(cx - r, cy, cx, cy - r, edge);
        // 四个顶点的小刺：比普通高亮框更"凶"，也压住菱形接缝
        com.hysx.salvosequence.core.Color tick = flash ? edgeColor : dimColor;
        for (int k = 1; k <= 3; k++) {
            art.pixel(cx, cy - r - k, tick);
            art.pixel(cx, cy + r + k, tick);
            art.pixel(cx - r - k, cy, tick);
            art.pixel(cx + r + k, cy, tick);
        }
    }

    /** 一枚锁定箭矢：亮尾 + 白热箭尖 + 两翼，2px 宽，红云背景上也看得清。 */
    private void drawLockArrow(PixelArt art, double ex, double ey, double ux, double uy, boolean hit,
                               com.hysx.salvosequence.core.Color hotColor,
                               com.hysx.salvosequence.core.Color edgeColor,
                               com.hysx.salvosequence.core.Color dimColor) {
        int wx = (int) Math.rint(-uy);
        int wy = (int) Math.rint(ux);
        for (int k = 1; k <= 13; k++) {
            int x = (int) Math.rint(ex - ux * k);
            int y = (int) Math.rint(ey - uy * k);
            art.pixel(x, y, k < 4 ? hotColor : edgeColor);
            if (k <= 9) {
                art.pixel(x + wx, y + wy, dimColor);
            }
        }
        int tx = (int) Math.rint(ex);
        int ty = (int) Math.rint(ey);
        art.pixel(tx, ty, com.hysx.salvosequence.core.Color.WHITE);
        int bx = (int) Math.rint(ex - ux * 3);
        int by = (int) Math.rint(ey - uy * 3);
        com.hysx.salvosequence.core.Color wing = hit ? hotColor : edgeColor;
        art.pixel(bx + wx, by + wy, wing);
        art.pixel(bx - wx, by - wy, wing);
        art.pixel(bx, by, wing);
        // 尾羽：让它在屏幕上读起来像“箭”而不是一条线
        int fx = (int) Math.rint(ex - ux * 13);
        int fy = (int) Math.rint(ey - uy * 13);
        art.pixel(fx + wx * 2, fy + wy * 2, dimColor);
        art.pixel(fx - wx * 2, fy - wy * 2, dimColor);
    }

    // ---------------------------------------------------------------- 右列

    private void drawSpacePortScreen(CanvasBuffer buffer, PixelArt art) {
        int px = PORT_PANEL_X;
        int pw = PORT_PANEL_W;
        UiChrome.panel(art, px, BOARD_Y, pw, BOARD_SPAN);
        String capName = run.captain() != null ? run.captain().protagonist().nameZh() : "";
        UiChrome.plate(buffer, art, px + 5, BOARD_Y - 7, 58, 17, Language.t("太空港", "PORT"));
        UiChrome.latin(art, "DOCK 07", px + 70, BOARD_Y + 3, Palette.STEEL_DIM);
        UiChrome.zh(buffer, Language.t("合金 ", "ALLOY ") + run.alloy(), px + 70,
                BOARD_Y + 9, 12, Palette.WARN);
        UiChrome.zhRight(buffer, capName, px + pw - 8, BOARD_Y + 9, 12, Palette.LINE_DIM);
        String[] tabs = {"编组", "定制", "融合", "培训", "星图"};
        int tabW = (pw - 16) / 5;
        for (int i = 0; i < 5; i++) {
            int tx = px + 6 + i * (tabW + 2);
            boolean on = spacePortTab == i;
            if (on) {
                UiChrome.banner(buffer, art, tx, BOARD_Y + 22, tabW - 2, 14, tabs[i]);
            } else {
                UiChrome.chamferFill(art, tx, BOARD_Y + 22, tabW - 2, 14, 2, Palette.PANEL_ALT);
                UiChrome.chamferFrame(art, tx, BOARD_Y + 22, tabW - 2, 14, 2, Palette.STEEL_DIM);
                zh(buffer, tabs[i], tx + (tabW - 2) / 2 - 9, BOARD_Y + 24, 12, Palette.LINE_DIM);
            }
        }
        int contentY = BOARD_Y + 42;
        int contentH = BOARD_SPAN - 42 - 26;
        UiChrome.chamferFill(art, px + 4, contentY, pw - 12, contentH, 3, Palette.PANEL_ALT);
        art.hline(px + 6, px + pw - 9, contentY, Palette.STEEL_FAINT);
        // 出发键先画：它在下缘固定位置，而 tab 内的悬浮描述可能盖到这里，
        // 描述必须压在按钮上面（否则购买/强化提示会被绿条切掉）。
        int departY = BOARD_Y + BOARD_SPAN - 22;
        boolean canDepart = run.pendingBuyShip() == null;
        if (canDepart) {
            UiChrome.banner(buffer, art, px + 6, departY, pw - 18, 16,
                    Language.t("出发 → 星图", "DEPART → MAP"));
        } else {
            UiChrome.chamferFill(art, px + 6, departY, pw - 18, 16, 3, Palette.PANEL_ALT);
            UiChrome.chamferFrame(art, px + 6, departY, pw - 18, 16, 3, Palette.WARN);
            UiChrome.zhCentered(buffer, Language.t("先放好新船", "PLACE THE NEW SHIP"),
                    px + 6 + (pw - 18) / 2.0, departY + 2, 12, Palette.WARN);
        }
        if (spacePortTab == 0) {
            drawSpArrangeTab(buffer, art, px, contentY, pw);
        } else if (spacePortTab == 1) {
            drawSpRefitTab(buffer, art, px, contentY, pw);
        } else if (spacePortTab == 2) {
            drawSpFuseTab(buffer, art, px, contentY, pw);
        } else if (spacePortTab == 3) {
            drawSpSkillTab(buffer, art, px, contentY, pw, contentH);
        } else if (spacePortTab == 4) {
            zh(buffer, "按 M 打开航行星图", px + 10, contentY + 6, 12, Palette.LINE);
            zh(buffer, "跃迁到邻近恒星：战斗 / 精英 / 事件 / 休息 / 商店", px + 10, contentY + 22,
                    12, Palette.LINE_DIM);
            zh(buffer, "走到星核打赢首领即通关", px + 10, contentY + 36, 12, Palette.LINE_DIM);
            zh(buffer, "滚轮缩放，只能跳到圆圈内的星", px + 10, contentY + 50, 12,
                    Palette.LINE_DIM);
        }
    }

    /** 编组 tab。 */
    private void drawSpArrangeTab(CanvasBuffer buffer, PixelArt art, int px, int cy, int pw) {
        if (run.pendingBuyShip() != null) {
            zh(buffer, "采购中：点棋盘空位放下", px + 10, cy + 6, 12, Palette.WARN);
            zh(buffer, "ESC 取消（合金退回）", px + 10, cy + 22, 12, Palette.LINE_DIM);
            return;
        }
        zh(buffer, "在左侧棋盘拖船变阵", px + 10, cy + 6, 12, Palette.LINE);
        zh(buffer, "站位决定光环协同和承伤次序", px + 10, cy + 22, 12, Palette.LINE_DIM);
        zh(buffer, "我方朝右：舰体右边缘最靠外的先挨打", px + 10, cy + 36, 12, Palette.LINE_DIM);
        zh(buffer, "舰队战力 " + run.playerPower() + "（含舰长技能）", px + 10, cy + 50, 12, Palette.WARN);
    }

    /**
     * 悬浮船的光环生效位置：被罩到的船描黄框（与组舰者同款，太空港编组用）。
     * 弹窗里只写"光环 X"，这里是"光环罩到谁"——因果画在棋盘上，不用心算。
     */
    private void drawAuraCoverage(PixelArt art) {
        List<FleetUnit> units = ourUnits();
        if (hoveredOurShip < 0 || hoveredOurShip >= units.size()) {
            return;
        }
        FleetUnit source = units.get(hoveredOurShip);
        for (FleetUnit other : units) {
            if (other.id().equals(source.id())) {
                continue;
            }
            for (Perk perk : source.ship().perks()) {
                if (FleetBoard.inRange(source, other, perk.range())) {
                    art.frame(ourBoardX() + other.x() * BOARD_CELL - 1,
                            BOARD_Y + other.y() * BOARD_CELL - 1,
                            other.w() * BOARD_CELL + 1, other.h() * BOARD_CELL + 1,
                            Palette.WARN);
                    break;
                }
            }
        }
    }

    /**
     * 敌方版光环提示：悬浮一艘敌舰时，把它光环罩到的敌舰描黄框。
     * 口径与我方完全一致（{@link FleetBoard#inRange}，纯几何），只是坐标系在敌方棋盘。
     */
    private void drawFoeAuraCoverage(PixelArt art, List<FoeView> foes) {
        if (hoveredFoe < 0 || hoveredFoe >= foes.size() || !foes.get(hoveredFoe).alive()) {
            return;
        }
        FleetUnit source = foes.get(hoveredFoe).unit();
        for (int i = 0; i < foes.size(); i++) {
            FoeView other = foes.get(i);
            if (i == hoveredFoe || !other.alive()) {
                continue;
            }
            for (Perk perk : source.ship().perks()) {
                if (FleetBoard.inRange(source, other.unit(), perk.range())) {
                    FleetUnit u = other.unit();
                    art.frame(ENEMY_BOARD_X + u.x() * BOARD_CELL - 1,
                            BOARD_Y + u.y() * BOARD_CELL - 1,
                            u.w() * BOARD_CELL + 1, u.h() * BOARD_CELL + 1,
                            Palette.WARN);
                    break;
                }
            }
        }
    }

    /** 鼠标是否落在行带里（文本 top = y，行高按 15 点算）。 */
    private boolean mouseInRow(double x, double y, double w) {
        return mouseX >= x && mouseX < x + w && mouseY >= y - 3 && mouseY < y + 12;
    }

    /**
     * 战斗悬浮面板的公共属性行：敌我同款字段/顺序，避免一边有齐射、一边只有防御。
     *
     * <p>结构/护盾/齐射、防御/防空、卡、占地位置、光环、舰船说明；调用方各自补标题。</p>
     */
    private List<String> combatStatLines(int hp, int maxHp, int block, int volley,
                                         int plate, int flak, int w, int h, int x, int y,
                                         List<String> cardIds,
                                         List<Perk> perks, String lore) {
        List<String> lines = new ArrayList<>();
        lines.add("结构 " + hp + "/" + maxHp + "  护盾 " + block + "  齐射 " + volley);
        lines.add("防御 " + plate + "  防空 " + flak);
        StringBuilder cards = new StringBuilder();
        for (String id : cardIds) {
            if (cards.length() >= 14) {
                break;
            }
            Card card = CardCatalog.byId(id);
            cards.append(card == null ? id : card.nameZh()).append(' ');
        }
        lines.add("卡：" + cards);
        lines.add("占地 " + w + "x" + h + "  位置 " + x + "/" + y);
        String labels = Perk.labelsOf(perks);
        lines.add(labels.isEmpty() ? "光环：—" : "光环 " + labels);
        for (String row : wrap(lore, 17)) {
            lines.add(row);
        }
        return lines;
    }

    /** 一艘船的完整数据行（商店模板船，没有等级/光环）。 */
    private List<String> shipStatLines(Ship ship) {
        List<String> lines = new ArrayList<>();
        // 采购预览也按当前舰长结构加成显示，买下后的真实上限一致
        lines.add("结构 " + (ship.hp() + run.structureBonus()) + "  护盾 " + ship.armor()
                + "  齐射 " + ship.volley());
        lines.add("防御 " + ship.plate() + "  防空 " + ship.flak()
                + "  能量 +" + ship.energy() + "  抽牌 +" + ship.draw());
        lines.add("占地 " + ship.w() + "x" + ship.h());
        StringBuilder cards = new StringBuilder();
        for (String id : ship.cardIds()) {
            if (cards.length() >= 14) {
                break;
            }
            Card card = CardCatalog.byId(id);
            cards.append(card == null ? id : card.nameZh()).append(' ');
        }
        lines.add("卡：" + cards);
        String perks = Perk.labelsOf(ship.perks());
        lines.add(perks.isEmpty() ? "光环：—" : "光环 " + perks);
        for (String lore : wrap(ship.textZh(), 17)) {
            lines.add(lore);
        }
        return lines;
    }

    /** 定制 tab：上半强化已有船，下半采购新船；两列行都带悬浮描述。 */
    private void drawSpRefitTab(CanvasBuffer buffer, PixelArt art, int px, int cy, int pw) {
        FleetUnit hoverUnit = null;
        Ship hoverShip = null;
        int y = cy + 4;
        zh(buffer, "— 强化 —", px + 10, y, 12, Palette.CYAN_DIM);
        y += 14;
        if (run.fleet() != null) {
            for (FleetUnit unit : run.fleet().units()) {
                int cost = 30 * unit.level();
                boolean can = run.alloy() >= cost;
                zh(buffer, unitLabel(unit) + "  "
                        + run.maxHpOf(unit) + "血  齐射" + run.fleet().volleyOf(unit),
                        px + 12, y, 12, Palette.LINE);
                zh(buffer, "升级 " + cost, px + pw - 60, y, 12,
                        can ? Palette.WARN : Palette.LINE_FAINT);
                if (mouseInRow(px + 6, y, 636 - px - 22)) {
                    hoverUnit = unit;
                }
                y += 15;
                if (y > cy + 100) { break; }
            }
        }
        y = cy + 110;
        zh(buffer, "— 采购 —", px + 10, y, 12, Palette.CYAN_DIM);
        y += 14;
        for (Ship ship : run.shopInventory()) {
            int cost = run.shipCost(ship);
            boolean can = run.alloy() >= cost;
            zh(buffer, ship.nameZh() + "  " + ship.hp() + "血", px + 12, y, 12, Palette.LINE);
            zh(buffer, "买 " + cost, px + pw - 60, y, 12,
                    can ? Palette.WARN : Palette.LINE_FAINT);
            if (mouseInRow(px + 6, y, 636 - px - 22)) {
                hoverShip = ship;
            }
            y += 15;
        }
        if (hoverUnit != null) {
            Fleet fleet = run.fleet();
            List<String> lines = combatStatLines(run.hpOf(hoverUnit), run.maxHpOf(hoverUnit),
                    fleet.armorOf(hoverUnit), fleet.volleyOf(hoverUnit),
                    fleet.plateOf(hoverUnit), fleet.flakOf(hoverUnit),
                    hoverUnit.w(), hoverUnit.h(), hoverUnit.x(), hoverUnit.y(),
                    hoverUnit.ship().cardIds(),
                    hoverUnit.ship().perks(), hoverUnit.ship().textZh());
            lines.add("升级 " + 30 * hoverUnit.level()
                    + " 合金：等级+1（倍率/结构跟着涨）");
            renderDesc(buffer, art, unitLabel(hoverUnit),
                    Palette.CYAN, lines);
        } else if (hoverShip != null) {
            List<String> lines = shipStatLines(hoverShip);
            lines.add("合金 " + run.shipCost(hoverShip)
                    + " · 买下后去编组 tab 摆位");
            renderDesc(buffer, art, Language.t(hoverShip.nameZh(), hoverShip.nameEn()),
                    Palette.WARN, lines);
        }
    }

    /** 融合 tab：选两艘同国同型的船，合成高一梯度的船（等级取低者）。 */
    private void drawSpFuseTab(CanvasBuffer buffer, PixelArt art, int px, int cy, int pw) {
        var units = run.fleet() == null ? java.util.List.<FleetUnit>of() : run.fleet().units();
        if (units.size() < 2) {
            zh(buffer, "编组至少两艘船才能融合", px + 10, cy + 6, 12, Palette.WARN);
            return;
        }
        if (fuseCursor >= units.size()) {
            fuseCursor = units.size() - 1;
        }
        zh(buffer, "选两艘同国同型的船 · ENTER 选中/融合", px + 10, cy + 6, 12, Palette.LINE);
        int y = cy + 26;
        for (int i = 0; i < units.size() && y < cy + contentHeight(px); i++, y += 15) {
            FleetUnit unit = units.get(i);
            boolean cursor = i == fuseCursor;
            boolean isA = i == fuseFrom;
            boolean eligible = fuseFrom >= 0 && fuseFrom != i
                    && fuseTarget(units.get(fuseFrom), unit) != null;
            if (cursor || isA) {
                art.rect(px + 6, y - 3, pw - 16, 14,
                        cursor ? Palette.PANEL_EDGE : Palette.PANEL);
            }
            zh(buffer, unitLabel(unit) + "  "
                    + FactionCatalog.factionOf(unit.ship()), px + 12, y, 12,
                    isA ? Palette.WARN : eligible ? Palette.CYAN : Palette.LINE);
            zh(buffer, Language.t("阶", "T") + (unit.ship().tier() + 1), px + pw - 74, y, 12,
                    isA ? Palette.WARN : Palette.LINE_DIM);
            if (isA) {
                zh(buffer, Language.t("选", "A"), px + pw - 30, y, 12, Palette.WARN);
            } else if (eligible) {
                zh(buffer, Language.t("可融", "FUSE"), px + pw - 40, y, 12, Palette.OK);
            }
        }
        // 结果预览
        if (fuseFrom >= 0 && fuseFrom < units.size()) {
            FleetUnit a = units.get(fuseFrom);
            FleetUnit b = fuseCursor < units.size() ? units.get(fuseCursor) : a;
            Ship target = fuseFrom != fuseCursor ? fuseTarget(a, b) : null;
            if (target != null) {
                int cost = 80 * target.tier();
                zh(buffer, Language.t("→ 「" + target.nameZh() + "」级",
                                "→ \"" + target.nameEn() + "\" Lv")
                        + Math.min(a.level(), b.level()) + "  合金 " + cost,
                        px + 10, cy + contentHeight(px) - 14, 12, Palette.OK);
            } else if (fuseFrom != fuseCursor) {
                zh(buffer, "这两艘不能融合（需同国同型、且该国该型有更高梯度）",
                        px + 10, cy + contentHeight(px) - 14, 12, Palette.WARN);
            }
        }
    }

    private int contentHeight(int px) {
        return BOARD_SPAN - 42 - 26;
    }

    /** 融合目标：同国同型、梯度 = min+1。找不到返回 null。 */
    private Ship fuseTarget(FleetUnit a, FleetUnit b) {
        int tier = Math.min(a.ship().tier(), b.ship().tier()) + 1;
        if (tier > 5) {
            return null;
        }
        var country = FactionCatalog.factionOf(a.ship());
        if (!country.equals(FactionCatalog.factionOf(b.ship())) || a.role() != b.role()) {
            return null;
        }
        return ShipCatalog.all().stream()
                .filter(x -> x.tier() == tier && x.role() == a.role()
                        && FactionCatalog.factionOf(x).equals(country))
                .findFirst().orElse(null);
    }

    /** 培训 tab：横向技能树——三行枝，每行 3 个节点箭头连接。 */
    private void drawSpSkillTab(CanvasBuffer buffer, PixelArt art, int px, int cy, int pw, int ch) {
        List<Captain.SkillBranch> branches = run.captain() != null
                ? run.captain().branches() : java.util.List.of();
        int nodeSize = 22;
        int gap = 26;
        int startX = px + 60;
        int rowH = 40;
        for (int bi = 0; bi < branches.size(); bi++) {
            var br = branches.get(bi);
            int lvl = run.captain() != null ? run.captain().branchLevel(bi) : 0;
            int rowY = cy + 18 + bi * rowH;
            zh(buffer, br.nameZh(), px + 10, rowY + 6, 12, Palette.CYAN_DIM);
            for (int tier = 0; tier < br.maxTier(); tier++) {
                int nx = startX + tier * (nodeSize + gap);
                boolean unlocked = tier < lvl;
                boolean next = tier == lvl;
                var color = unlocked ? Palette.CYAN : next ? Palette.WARN : Palette.LINE_FAINT;
                art.rect(nx, rowY, nodeSize, nodeSize, unlocked ? Palette.PANEL_EDGE : Palette.PANEL);
                art.frame(nx, rowY, nodeSize, nodeSize, color);
                en(art, String.valueOf(tier + 1), nx + 8, rowY + 7,
                        unlocked ? Palette.CYAN : next ? Palette.WARN : Palette.LINE_FAINT);
                if (tier < br.maxTier() - 1) {
                    int lineY = rowY + nodeSize / 2;
                    art.line(nx + nodeSize + 2, lineY, nx + nodeSize + gap - 4, lineY,
                            tier < lvl ? Palette.CYAN_DIM : Palette.LINE_FAINT);
                }
            }
            int infoX = startX + 3 * (nodeSize + gap) + 8;
            if (lvl < br.maxTier()) {
                zh(buffer, br.tierName(lvl) + " " + br.cost(lvl), infoX, rowY + 2, 12,
                        run.alloy() >= br.cost(lvl) ? Palette.LINE : Palette.LINE_FAINT);
                zh(buffer, br.tierEffect(lvl), infoX, rowY + 13, 12, Palette.LINE_DIM);
            } else {
                zh(buffer, Language.t("满级", "MAX"), infoX, rowY + 2, 12, Palette.CYAN);
            }
        }
        if (run.captain() != null) {
            var sig = run.captain().protagonist().signature();
            int sy = cy + 18 + branches.size() * rowH + 2;
            if (sy + 12 <= cy + ch) {
                zh(buffer, fitted("专属技「" + sig.nameZh() + "」 " + sig.desc(), pw - 20),
                        px + 10, sy, 10, Palette.WARN);
            }
        }
    }



    /** 技能树节点矩形（热区用）。 */
    private int[] skillNodeRect(int branch, int tier) {
        int nodeSize = 22;
        int gap = 26;
        int startX = ENEMY_BOARD_X + 60;
        int rowY = BOARD_Y + 42 + 18 + branch * 40;
        return new int[]{startX + tier * (nodeSize + gap), rowY, nodeSize, nodeSize};
    }



    /** 事件遭遇：全屏卡片。 */
    private void drawEventScreen(CanvasBuffer buffer, PixelArt art) {
        var card = run.eventCard();
        if (card == null) { return; }
        UiChrome.scrim(art, 0, 0, 640, 360);
        UiChrome.alertPanel(art, 130, 60, 380, 260);
        UiChrome.windowTitle(buffer, art, 320, 68, Language.t("遭遇", "ENCOUNTER"),
                "ANOMALOUS CONTACT", Palette.CRIMSON);
        zh(buffer, card.title(), 146, 88, 14, Palette.AMBER);
        UiChrome.divider(art, 146, 106, 348);
        int y = 114;
        for (String line : wrap(card.desc(), 20)) {
            zh(buffer, line, 146, y, 12, Palette.LINE_DIM);
            y += 13;
        }
        int oy = 180;
        for (int i = 0; i < card.options().size(); i++) {
            var opt = card.options().get(i);
            UiChrome.chamferFill(art, 140, oy, 360, 22, 3, Palette.PANEL_ALT);
            UiChrome.chamferFrame(art, 140, oy, 360, 22, 3, Palette.STEEL_DIM);
            UiChrome.chamferFill(art, 144, oy + 4, 14, 14, 2, Palette.AMBER);
            UiChrome.latinCentered(art, Integer.toString(i + 1), 151, oy + 7, Palette.INK);
            zh(buffer, opt.label(), 164, oy + 5, 12, Palette.LINE);
            oy += 26;
        }
        UiChrome.zh(buffer, Language.t("数字键或点击选择", "NUMBER KEYS OR CLICK TO CHOOSE"),
                146, 300, 12, Palette.LINE_FAINT);
    }

    /** 每个节点算出来的框色，定死后转星盘不再变（颜色本身已是两个角度的平均，与当前角度无关）。 */
    private final java.util.Map<Integer, Color> nodeFrameColors = new java.util.HashMap<>();

    /** 节点框的黑白分界：平均亮度高于它用黑框，否则用白框。 */
    private static final int FRAME_BLACK_ABOVE_LUM = 128;

    /**
     * 节点框的颜色：取这颗星在**星盘 0° 与 90° 两个姿态下**脚下背景亮度的平均值，
     * 大于 128 用纯黑框、否则纯白框。
     *
     * <p>为什么取两个角度：星盘会转，只按当前帧取色的话，节点掠过亮度阈值就会黑白
     * 来回跳；只按第一次取色又会让它转到别处后失去反差。0° 与 90° 是两个正交姿态，
     * 平均之后颜色与当前旋转角无关——既不会闪，也不会因为转了半圈而糊进背景。</p>
     *
     * <p>采样各取 3x3 平均，避开拜耳网点造成的单像素噪点；星系帧还没解码出来时
     * 先给默认白且不写缓存，等帧到了再定。</p>
     */
    private Color nodeFrameColor(Run.StarNode st) {
        Color cached = nodeFrameColors.get(st.id());
        if (cached != null) {
            return cached;
        }
        PngLoader.Bitmap frame0 = StarDisk.frame(0.0);
        PngLoader.Bitmap frame90 = StarDisk.frame(Math.PI / 2);
        if (frame0 == null || frame90 == null) {
            return Color.WHITE;
        }
        double u = Math.cos(st.theta()) * st.radius();
        double v = Math.sin(st.theta()) * st.radius();
        // 0° 姿态：节点就在 (u, v)；90° 姿态：整块盘转了 90°，节点在盘上换到 (-v, u)
        int lum0 = backdropLuminance(frame0, StarDisk.project(u, v));
        int lum90 = backdropLuminance(frame90, StarDisk.project(-v, u));
        Color color = (lum0 + lum90) / 2 > FRAME_BLACK_ABOVE_LUM ? Color.BLACK : Color.WHITE;
        nodeFrameColors.put(st.id(), color);
        return color;
    }

    /** 3x3 平均亮度（0~255），采样点用盘面投影后的设计坐标。 */
    private static int backdropLuminance(PngLoader.Bitmap disk, double[] point) {
        int x = (int) Math.rint(point[0]);
        int y = (int) Math.rint(point[1]);
        int w = disk.width();
        int h = disk.height();
        int sumR = 0;
        int sumG = 0;
        int sumB = 0;
        int n = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int sx = x + dx;
                int sy = y + dy;
                if (sx < 0 || sy < 0 || sx >= w || sy >= h) {
                    continue;
                }
                int argb = disk.argb()[sy * w + sx];
                sumR += (argb >>> 16) & 0xFF;
                sumG += (argb >>> 8) & 0xFF;
                sumB += argb & 0xFF;
                n++;
            }
        }
        if (n == 0) {
            return 0;
        }
        return ((sumR / n) * 299 + (sumG / n) * 587 + (sumB / n) * 114) / 1000;
    }

    /** 全屏星图（黑流树海式自由航行）：背景是 mapback 的 1440 张星系盘角度帧，
     *  左右拖动旋转星盘，节点画在同一倾角的盘面上。 */
    private void drawFullScreenStarMap(CanvasBuffer buffer, PixelArt art) {
        drawStarDisk(art);
        // 顶部投影器的光：扇面边缘取星盘左右两侧到灯口的角度，光强按球面距离衰减；
        // 闪烁星点留到光之后再画，免得一颗亮星把某一束光顶得忽明忽暗。
        drawProjectorLight(art);
        drawDiskTwinkles(art);
        drawProjectorFixture(art);
        // 节点框的颜色是每颗星定死一次、与当前角度无关（见 nodeFrameColor），
        // 这里不用再按当前角度取一帧——那一下是白查，缓存没命中时还会同步解码卡一帧。
        var reachable = run.reachableStars();
        // 标签防叠：先记下所有会画出来的恒星名字框，类型标签压到别人的名字上就不画。
        // 30 颗星最近间距 34px，名字与"上方 16px 的类型标签"在垂直 34px 时只差 1px。
        java.util.List<int[]> taken = new java.util.ArrayList<>();
        for (var st : reachable) {
            int[] p = starScreenPos(st);
            int w = (int) Math.ceil(textWidth(st.nameZh(), STAR_NAME_SIZE));
            taken.add(new int[]{p[0] - w / 2, p[1] + 8, w, (int) STAR_NAME_SIZE});
        }
        int[] curPos = starScreenPos(run.currentStar());
        int curW = (int) Math.ceil(textWidth(run.currentStar().nameZh(), STAR_NAME_SIZE));
        taken.add(new int[]{curPos[0] - curW / 2, curPos[1] + 8, curW, (int) STAR_NAME_SIZE});
        // 走过的星也有名字了：名字框一并登记，别处的类型标签不许压上来
        for (var st : run.starMap()) {
            if (!st.visited() || st.id() == run.currentStar().id()) { continue; }
            int[] p = starScreenPos(st);
            int w = (int) Math.ceil(textWidth(st.nameZh(), STAR_NAME_SIZE));
            taken.add(new int[]{p[0] - w / 2, p[1] + 8, w, (int) STAR_NAME_SIZE});
        }
        // 星图航路：只画玩家当前节点相连的边，其余航路隐藏，避免整张网糊在银河上
        int[][] links = run.starGraph();
        int curId = run.currentStar().id();
        for (int a = 0; a < links.length; a++) {
            for (int b : links[a]) {
                if (b <= a || (a != curId && b != curId)) {
                    continue;
                }
                int[] pa = starScreenPos(run.starMap().get(a));
                int[] pb = starScreenPos(run.starMap().get(b));
                art.line(pa[0], pa[1], pb[0], pb[1], Palette.CYAN_DIM);
            }
        }
        for (var st : run.starMap()) {
            int[] pos = starScreenPos(st);
            boolean isCurrent = st.id() == run.currentStar().id();
            boolean canJump = false;
            for (var r2 : reachable) {
                if (r2.id() == st.id()) { canJump = true; break; }
            }
            var typeColor = starTypeColor(st.type());
            Color nodeFrame = nodeEdgeColor(st, typeColor);
            int sparkPhase = st.id() * 17;
            // 太阳常驻标记自带名字：悬浮不再叠任何字
            boolean isSun = "太阳".equals(st.nameZh());
            // 核心屏障内未解锁的节点：画灰色框 + "锁" 字，不走彩色分支
            boolean coreLocked = !run.gatePassed() && st.radius() < 30;
            if (coreLocked) {
                // 锁定态不参与类型配色：灰星 + 黑白框，读作"还没解锁"
                drawNodeSparkle(art, pos[0], pos[1], Palette.LINE_FAINT, 0, time, sparkPhase);
                art.frame(pos[0] - 4, pos[1] - 4, 9, 9, nodeFrameColor(st));
            } else if (isCurrent) {
                int pulse = (int) (3 + 2 * Math.abs(Math.sin(time * 3)));
                drawStarGlow(art, pos[0], pos[1], Palette.CYAN, Palette.LINE, pulse);
                drawNodeSparkle(art, pos[0], pos[1], Palette.CYAN, 3, time, sparkPhase);
                if (!isSun) {
                    drawStarLabelHover(buffer, st.nameZh(), pos[0], pos[1], pos[1] + 8,
                            STAR_NAME_SIZE, Palette.CYAN);
                    drawStarLabelHover(buffer, starTypeLabel(st.type()), pos[0], pos[1],
                            pos[1] - 7 - STAR_TYPE_SIZE, STAR_TYPE_SIZE, typeColor);
                }
            } else if (st.visited()) {
                drawNodeSparkle(art, pos[0], pos[1], shade(typeColor, 0.72), 0, time, sparkPhase);
                art.rect(pos[0] - 1, pos[1] - 1, 2, 2, Palette.STAR_FAR);
                if (!isSun) {
                    drawStarLabelHover(buffer, st.nameZh(), pos[0], pos[1], pos[1] + 8,
                            STAR_NAME_SIZE, Palette.LINE_DIM);
                    // 走过的节点也要能看类型：名字 + 上方关卡类型一起悬浮出现
                    drawStarLabelHover(buffer, starTypeLabel(st.type()), pos[0], pos[1],
                            pos[1] - 7 - STAR_TYPE_SIZE, STAR_TYPE_SIZE, shade(typeColor, 0.8));
                }
                if (canJump) {
                    // 回头路：圈内已访问的星可折返（暗框提示）
                    art.frame(pos[0] - 5, pos[1] - 5, 11, 11, nodeFrame);
                }
            } else if (canJump && !coreLocked) {
                drawStarGlow(art, pos[0], pos[1], typeColor, typeColor, 3);
                drawNodeSparkle(art, pos[0], pos[1], typeColor, 2, time, sparkPhase);
                art.frame(pos[0] - 6, pos[1] - 6, 13, 13, nodeFrame);
                if (!isSun) {
                    drawStarLabelHover(buffer, st.nameZh(), pos[0], pos[1], pos[1] + 8,
                            STAR_NAME_SIZE, typeColor);
                    drawStarLabelHover(buffer, starTypeLabel(st.type()), pos[0], pos[1],
                            pos[1] - 7 - STAR_TYPE_SIZE, STAR_TYPE_SIZE, typeColor);
                }
            } else {
                // 远处的节点也保留类型色（压暗）：星图一眼能分出精英/事件/商店，
                // 而不是一整片同色的星点
                drawNodeSparkle(art, pos[0], pos[1], shade(typeColor, 0.55), 0, time, sparkPhase);
                art.frame(pos[0] - 2, pos[1] - 2, 5, 5, nodeFrame);
                if (st.type() == Run.NodeType.BOSS) {
                    art.frame(pos[0] - 4, pos[1] - 4, 9, 9, nodeFrame);
                }
            }
        }
        // 星区标签：五个扇区各标一个势力名。不再压在星盘上——引线拉到左右两侧，
        // 星盘转起来时引线跟着摆，文字始终停在画面边缘，既不挡节点也一眼能认。
        drawZoneCallouts(buffer);
        // 核心屏障圈：虚线画在 CORE_BARRIER 半径处——让玩家看见屏障在哪
        if (!run.gatePassed()) {
            drawDiskCircle(art, CORE_BARRIER_R, Palette.LINE_FAINT);
        }
        // 核心区节点锁定视觉：未过太阳系舰队前，屏障内节点画为灰色锁定态
        boolean gateOk = run.gatePassed();
        for (var st : run.starMap()) {
            if (st.radius() >= 30 || gateOk) { continue; }
            int[] lp = starScreenPos(st);
            art.frame(lp[0] - 2, lp[1] - 2, 5, 5, nodeFrameColor(st));
        }
        if (sunVisible) {
            drawSunMarker(art, buffer, time, taken);
        }
        // 星图也是"一个视窗"：顶部读数槽 + 四角括号 + 底部通栏控制台。
        // 底色只压两档，星盘本身要透出来，所以用极暗的槽而不是厚面板。
        UiChrome.chamferFill(art, 0, 0, 640, 21, 3, Palette.PANEL);
        art.hline(0, 639, 21, Palette.STEEL_DIM);
        art.hline(8, 631, 22, Palette.STEEL_FAINT);
        UiChrome.plate(buffer, art, 6, 2, 44, 16, Language.t("星图", "MAP"));
        UiChrome.zh(buffer, Language.t("探索 ", "EXPLORED ")
                + run.visitedCount() + "/" + run.starMap().size(), 58, 4, 12, Palette.LINE_DIM);
        UiChrome.vRule(art, 176, 4, 13);
        UiChrome.zh(buffer, Language.t("合金 ", "ALLOY ") + run.alloy(), 186, 4, 12, Palette.WARN);
        UiChrome.latin(art, "ASTROGRID", 640 - UiChrome.measureLatin("ASTROGRID") - 8, 6,
                Palette.STEEL_DIM);
        UiChrome.brackets(art, 0, 23, 640, 311, 3, Palette.STEEL_DIM, 9);

        UiChrome.chamferFill(art, 0, 331, 640, 29, 3, Palette.PANEL);
        art.hline(0, 639, 331, Palette.STEEL_DIM);
        art.hline(64, 631, 332, Palette.STEEL_FAINT);
        UiChrome.zh(buffer, Language.t("拖动左右旋转星盘 "
                        + String.format("%.1f", Math.toDegrees(starDiskRad())) + "°",
                        "DRAG TO SPIN "
                                + String.format("%.1f", Math.toDegrees(starDiskRad())) + "°")
                + (stage() == Run.Stage.MAP
                        ? Language.t(" · 点击邻近恒星跃迁", " · CLICK A NEARBY STAR")
                        : Language.t(" · M 返回", " · M BACK"))
                + Language.t(" · F10 太阳 · ESC 设置", " · F10 SUN · ESC SETTINGS"),
                64, 341, 12, Palette.LINE_DIM);
    }

    /** 贴当前旋转角对应的星系盘帧：图像本身已经过拜耳处理，这里整幅直贴，不再二次抖动。 */
    private void drawStarDisk(PixelArt art) {
        PngLoader.Bitmap frame = StarDisk.frame(starDiskRad());
        if (frame == null) {
            art.fillRect(0, 0, StarDisk.IMAGE_W, StarDisk.IMAGE_H, Palette.SPACE_DEEP);
            return;
        }
        // 一整幅 640x360、全不透明、1:1 直贴：走 copyFrame（只做行拷贝）。通用 blitArgbTile
        // 会逐像素做整除缩放换算 + alpha 判定，整屏 23 万像素实测多花 2~4ms/帧。
        art.copyFrame(frame.argb(), frame.width(), frame.height());
    }

    /** 盘面上的闪烁星：数量固定、位置由种子定（不是每帧随机），随星盘一起转、按透视缩放大小。 */
    private void drawDiskTwinkles(PixelArt art) {
        if (diskTwinkles == null) {
            diskTwinkles = new double[DISK_TWINKLE_COUNT][5];
            java.util.Random rnd = new java.util.Random(0x5EED_C0DEL);
            for (int i = 0; i < diskTwinkles.length; i++) {
                // sqrt 让点在圆面上均匀分布（直接用半径会都挤在中心）；
                // 屏障圈（太阳系）里不放星星，所以从 CORE_BARRIER_R 起算
                double radius = Math.sqrt(CORE_BARRIER_R * CORE_BARRIER_R / (DISK_TWINKLE_RADIUS * DISK_TWINKLE_RADIUS)
                        + rnd.nextDouble() * (1 - CORE_BARRIER_R * CORE_BARRIER_R / (DISK_TWINKLE_RADIUS * DISK_TWINKLE_RADIUS)))
                        * DISK_TWINKLE_RADIUS;
                diskTwinkles[i] = new double[]{
                        rnd.nextDouble() * Math.PI * 2,
                        radius,
                        rnd.nextDouble() * Math.PI * 2,
                        0.7 + rnd.nextDouble() * 2.1,
                        0.55 + rnd.nextDouble() * 0.45};
            }
        }
        double rot = starDiskRad();
        for (double[] st : diskTwinkles) {
            double a = st[0] + rot;
            double u = Math.cos(a) * st[1];
            double v = Math.sin(a) * st[1];
            double[] p = StarDisk.project(u, v);
            int x = (int) Math.rint(p[0]);
            int y = (int) Math.rint(p[1]);
            if (x < 1 || x > StarDisk.IMAGE_W - 2 || y < 1 || y > StarDisk.IMAGE_H - 2) {
                continue;
            }
            // 呼吸亮度：一半时间暗到看不见，一半时间亮起来，就是"一闪一闪"
            double twinkle = 0.5 + 0.5 * Math.sin(time * st[3] * 2.0 + st[2]);
            double lum = st[4] * Math.pow(twinkle, 1.5);
            if (lum < 0.07) {
                continue;
            }
            float k = (float) StarDisk.depthScale(v);
            Color c = lum > 0.55 ? Palette.STAR_NEAR : lum > 0.28 ? Palette.STAR_MID : Palette.STAR_FAR;
            art.pixel(x, y, c);
            if (lum > 0.62) {
                // 近处亮星多一个像素，透视上"近大远小"
                if (k > 1.15f) {
                    art.pixel(x + 1, y, c);
                    art.pixel(x, y + 1, c);
                }
                if (lum > 0.86) {
                    art.pixel(x - 1, y, c);
                    art.pixel(x, y - 1, c);
                }
            }
        }
    }

    // ------------------------------------------------------------ 顶部投影器

    /** 投影器灯口在屏幕上的位置：顶栏下方正中，灯体像从 HUD 底下伸出来。 */
    private static final double PROJ_X = 320.0;
    private static final double PROJ_Y = 38.0;
    /** 球形衰减半径：灯口到星盘中心约 141px，取 260 让盘心仍亮、盘底自然收暗。 */
    private static final double LIGHT_RADIUS = 260.0;
    /** 扇面半角固定 56.5°（星盘转一圈实测的最宽边缘），不再逐帧量角度。 */
    private static final double PROJ_HALF_ANGLE = Math.toRadians(56.5);
    /** 灯罩逐行宽度（y=26 起）：顶盖最宽 48px，往下每行两侧各收 1px，收出 45° 斜边，y=34 起收成 32px 的灯口圆筒。 */
    private static final int[] PROJ_SPAN = {
            48, 46, 44, 42, 40, 38, 36, 34, 32, 32, 32, 32, 32};
    /** 扇面边缘的柔化宽度（弧度）：约 2.6°，够软但不会切出生硬的边。 */
    private static final float FAN_SOFT = 0.045f;
    /** 悬臂外渐隐：盘面亮度低于 COVER_LO 全黑，到 COVER_HI 完全受光。 */
    private static final float COVER_LO = 0.010f;
    private static final float COVER_HI = 0.070f;

    /** 每个屏幕像素相对灯口的角度（atan2(dx, dy)，向下为 0）与球形衰减，建一次复用。 */
    private float[] projAngle;
    private float[] projSph;
    /** 灯口附近的独立光晕，和球形衰减分开，避免整盘被灯口提亮。 */
    private float[] projLamp;
    /** 调试/探针用：本帧每像素的球面受光量。 */
    private float[] lightBuf;

    private void ensureProjectorFields() {
        if (projAngle != null) {
            return;
        }
        int w = StarDisk.IMAGE_W;
        int h = StarDisk.IMAGE_H;
        projAngle = new float[w * h];
        projSph = new float[w * h];
        projLamp = new float[w * h];
        lightBuf = new float[w * h];
        for (int y = 0; y < h; y++) {
            double dy = y + 0.5 - PROJ_Y;
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                double dx = x + 0.5 - PROJ_X;
                double d2 = dx * dx + dy * dy;
                projAngle[i] = (float) Math.atan2(dx, dy);
                projSph[i] = (float) (1.0 / (1.0 + d2 / (LIGHT_RADIUS * LIGHT_RADIUS)));
                // 灯口光晕只留灯口周围一圈，sigma≈26px；盘心处早已衰减到看不见
                projLamp[i] = (float) (0.18 * Math.exp(-d2 / (2.0 * 26.0 * 26.0)));
            }
        }
    }

    /**
     * 顶部投影器：球面衰减的照明，扇面固定在星盘最宽的 ±56.5°。
     *
     * <p>角度不再逐帧扫描，避免悬臂旋转时扇面忽宽忽窄。光强只按到灯口的球面距离
     * 衰减（1/(1+d²/R²)）。盘面亮度低于悬臂外阈值的地方按原实现的思路渐隐到黑，
     * 不用硬角度边把悬臂切掉；也没有体积积分、尘埃吸收、亮脊分析或丁达尔光柱。</p>
     */
    private void drawProjectorLight(PixelArt art) {
        ensureProjectorFields();
        int w = art.width();
        int h = art.height();
        int[] px = art.buffer();
        float thL = (float) -PROJ_HALF_ANGLE;
        float thR = (float) PROJ_HALF_ANGLE;
        for (int y = 0; y < h; y++) {
            double dy = y + 0.5 - PROJ_Y;
            int row = y * w;
            // 盘面下缘以下没有散射介质，空气光到这里收住，不会一路拖到屏幕底角
            float hazeFade = 1f - smooth01((y - 300f) / 60f);
            for (int x = 0; x < w; x++) {
                int i = row + x;
                // 灯口以上不投光：只保留灯体本身，背景压黑
                if (dy <= 0.0) {
                    lightBuf[i] = 0f;
                    px[i] = 0xFF000000;
                    continue;
                }
                float a = projAngle[i];
                float sph = projSph[i];
                // 扇面：thL..thR 内为 1，向外 FAN_SOFT 宽度内平滑到 0
                float fanL = smooth01((a - thL + FAN_SOFT) / FAN_SOFT);
                float fanR = smooth01((thR + FAN_SOFT - a) / FAN_SOFT);
                float fan = fanL * fanR;
                float illum = fan * sph;

                int old = px[i];
                // 超出悬臂的渐隐：亮的地方完全受光，盘外黑处渐隐到 0
                float cover = smooth01((luma(old) - COVER_LO) / (COVER_HI - COVER_LO));
                float lit = illum * cover;
                lightBuf[i] = lit;

                float r = ((old >> 16) & 0xFF) / 255f;
                float g = ((old >> 8) & 0xFF) / 255f;
                float b = (old & 0xFF) / 255f;
                float lamp = projLamp[i];
                // 扇面外仍保留一层压暗的盘面（参考旧实现只压盘外），扇面内再按球面衰减提亮
                float base = cover * (0.30f + 0.70f * illum);
                float haze = lit * hazeFade;
                float nr = r * base + haze * 0.070f + lamp * 0.55f;
                float ng = g * base + haze * 0.085f + lamp * 0.78f;
                float nb = b * base + haze * 0.105f + lamp;
                px[i] = 0xFF000000
                        | (clamp8((int) (nr * 255f + 0.5f)) << 16)
                        | (clamp8((int) (ng * 255f + 0.5f)) << 8)
                        | clamp8((int) (nb * 255f + 0.5f));
            }
        }
        art.flush(0, 0, w, h);
    }

    private static float smooth01(float v) {
        if (v <= 0f) {
            return 0f;
        }
        if (v >= 1f) {
            return 1f;
        }
        return v * v * (3f - 2f * v);
    }

    private static float luma(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        return 0.30f * r + 0.55f * g + 0.15f * b;
    }

    private static int clamp8(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    /**
     * 投影器本体：吊架 + 梯形灯罩 + 椭圆灯口，画在光场之上。
     *
     * <p>分三段立结构：吊架把灯头挂到顶栏下（不然它就是黑带里浮着的一块），灯罩靠
     * 逐行收窄的斜边、顶面受光、侧棱倒角立出厚度，最后在灯口压一块钢色灯板、嵌一枚
     * 压扁的椭圆镜片（核心冷白 → 蓝 → 钢青，径向衰减）。旧版是三条硬直角矩形叠成
     * 一条横贯的亮条，既不像灯也不像镜头，看着像贴纸。</p>
     */
    private void drawProjectorFixture(PixelArt art) {
        // 吊架：顶栏下沿的挂板 + 两根轭臂
        art.fillRect(306, 23, 28, 1, Palette.PANEL_EDGE);
        art.fillRect(313, 24, 3, 2, Palette.STEEL_FAINT);
        art.fillRect(324, 24, 3, 2, Palette.STEEL_FAINT);

        // 灯罩：外张的顶盖 + 45° 锥面 + 灯口圆筒。不用 polygon —— 斜边会落成半像素锯齿
        for (int i = 0; i < PROJ_SPAN.length; i++) {
            int y = 26 + i;
            int x0 = 320 - PROJ_SPAN[i] / 2;
            int x1 = x0 + PROJ_SPAN[i] - 1;
            // 顶盖接天光最亮，锥面往下压暗，灯口圆筒被自己的灯照亮一档
            if (y == 26) {
                art.hline(x0, x1, y, Palette.STEEL);
            } else if (y <= 30) {
                art.hline(x0, x1, y, Palette.STEEL_DIM);
            } else if (y <= 34) {
                art.hline(x0, x1, y, Palette.STEEL_FAINT);
            } else {
                art.hline(x0, x1, y, Palette.PANEL_EDGE);
            }
            art.pixel(x0, y, Palette.PANEL);
            art.pixel(x1, y, Palette.PANEL);
        }

        // 镜片：压扁的椭圆，发光体得有径向衰减才不像一条横贯的荧光灯管
        double cx = 319.5;
        double cy = 37.0;
        double rx = 9.5;
        double ry = 2.0;
        for (int y = 35; y <= 38; y++) {
            for (int x = 309; x <= 330; x++) {
                double dx = (x + 0.5 - cx) / rx;
                double dy = (y + 0.5 - cy) / ry;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d > 1.0) {
                    continue;
                }
                art.pixel(x, y, d < 0.38 ? Palette.LINE
                        : d < 0.78 ? Palette.BLUE
                        : Palette.STEEL_DIM);
            }
        }
    }

    /** 恒星屏幕坐标：极坐标按星盘旋转角转到盘面，再做 30° 俯角的透视投影。 */
    private int[] starScreenPos(Run.StarNode st) {
        double a = st.theta() + starDiskRad();
        double[] p = StarDisk.project(Math.cos(a) * st.radius(), Math.sin(a) * st.radius());
        return new int[]{(int) Math.rint(p[0]), (int) Math.rint(p[1])};
    }

    /** 盘面上半径 {@code r} 的圆（虚线）：逐段投影，所以是透视椭圆而不是压扁椭圆。 */
    private void drawDiskCircle(PixelArt art, double r,
                                com.hysx.salvosequence.core.Color color) {
        for (int deg = 0; deg < 360; deg += 8) {
            double rad = Math.toRadians(deg);
            double[] p = StarDisk.project(Math.cos(rad) * r, Math.sin(rad) * r);
            art.pixel((int) Math.rint(p[0]), (int) Math.rint(p[1]), color);
        }
    }

    /**
     * 五个星区的势力名：引线出盘、拉到左右两侧写。
     *
     * <p>锚点跟着星盘转，文字停在画面边缘的固定槽位；同一侧最多三枚，按锚点高度排开后
     * 强制至少 {@link #ZONE_LABEL_GAP} 的间距，所以转起来也不会两枚叠字。</p>
     */
    private void drawZoneCallouts(CanvasBuffer buffer) {
        String[] names = com.hysx.salvosequence.fleet.FactionCatalog.ZONES;
        int n = names.length;
        double[] ax = new double[n];
        double[] ay = new double[n];
        for (int i = 0; i < n; i++) {
            double theta = (i + 0.5) * Math.PI * 2 / n + starDiskRad();
            double[] p = StarDisk.project(Math.cos(theta) * ZONE_ANCHOR_R,
                    Math.sin(theta) * ZONE_ANCHOR_R);
            ax[i] = p[0];
            ay[i] = p[1];
        }
        for (int side = 0; side < 2; side++) {
            boolean left = side == 0;
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if ((ax[i] < StarDisk.CENTER_X) == left) {
                    order.add(i);
                }
            }
            order.sort((a, b) -> Double.compare(ay[a], ay[b]));
            int count = order.size();
            int[] labelY = new int[count];
            for (int k = 0; k < count; k++) {
                labelY[k] = (int) Math.rint(Math.max(ZONE_LABEL_TOP,
                        Math.min(ZONE_LABEL_BOTTOM, ay[order.get(k)])));
            }
            // 先向下撑开，再向上压回，两遍就能把间距和边界一起满足
            for (int k = 1; k < count; k++) {
                labelY[k] = Math.max(labelY[k], labelY[k - 1] + ZONE_LABEL_GAP);
            }
            for (int k = count - 2; k >= 0; k--) {
                labelY[k] = Math.min(labelY[k], labelY[k + 1] - ZONE_LABEL_GAP);
            }
            for (int k = 0; k < count; k++) {
                int i = order.get(k);
                drawZoneCallout(buffer, names[i], ax[i], ay[i], labelY[k], left, i);
            }
        }
    }

    /**
     * 一条星区引线：盘内接口点 → 出盘短桩 → 45° 折一折 → 横拉到竖轨 → 竖轨 → 5px 入线。
     *
     * <p>那个 45° 小折不是装饰：有了它整条线才有"电路走线"的样子，否则只有一根横杠。
     * 折向左下还是左上按星区序号奇偶固定，转盘时不会忽上忽下。</p>
     */
    private void drawZoneCallout(CanvasBuffer buffer, String name, double ax, double ay,
                                 int labelY, boolean left, int zoneIndex) {
        PixelArt art = buffer.art();
        int wireX = left ? ZONE_WIRE_LEFT : ZONE_WIRE_RIGHT;
        int textEdge = left ? ZONE_TEXT_LEFT : ZONE_TEXT_RIGHT;
        int y0 = (int) Math.rint(ay);
        int x0 = (int) Math.rint(ax);
        int dirX = left ? -1 : 1;
        // 锚点：5×5 深色底 + 3×3 钢灰 + 亮点，压在星尘上也不会糊掉
        art.rect(x0 - 2, y0 - 2, 5, 5, Palette.PANEL);
        art.rect(x0 - 1, y0 - 1, 3, 3, Palette.STEEL);
        art.pixel(x0, y0, Palette.LINE_DIM);
        int stubX = x0 + dirX * ZONE_STUB;
        art.hline(Math.min(x0, stubX), Math.max(x0, stubX), y0, Palette.STEEL_DIM);
        int runEnd = stubX + dirX * (ZONE_JOG * 2 + ZONE_JOG_RUN);
        if (Math.abs(runEnd - wireX) < 6) {
            // 锚点已经贴到竖轨附近，折线挤不下，直接拉直
            wireH(art, stubX, wireX, y0);
        } else {
            int jog = ((zoneIndex & 1) == 0) ? 1 : -1;
            int jy = y0 + jog * ZONE_JOG;
            for (int k = 1; k <= ZONE_JOG; k++) {
                art.pixel(stubX + dirX * k, y0 + jog * k, Palette.LINE_FAINT);
            }
            int x1 = stubX + dirX * ZONE_JOG;
            int x2 = x1 + dirX * ZONE_JOG_RUN;
            wireH(art, x1, x2, jy);
            for (int k = 1; k <= ZONE_JOG; k++) {
                art.pixel(x2 + dirX * k, jy - jog * k, Palette.LINE_FAINT);
            }
            wireH(art, x2 + dirX * ZONE_JOG, wireX, y0);
        }
        // 竖轨引到标签高度，再横 5px 顶到文字边上
        wireV(art, y0, labelY, wireX);
        int tickX = left ? textEdge + 5 : textEdge - 5;
        wireH(art, wireX, tickX, labelY);
        // 末端小三角：让引线有方向感，而不是断在半空
        art.pixel(tickX + dirX, labelY, Palette.LINE_DIM);
        art.pixel(tickX + dirX * 2, labelY - 1, Palette.LINE_DIM);
        art.pixel(tickX + dirX * 2, labelY + 1, Palette.LINE_DIM);
        int w = (int) Math.ceil(textWidth(name, 12));
        zh(buffer, name, left ? textEdge - w : textEdge, labelY - 6, 12, Palette.LINE_DIM);
    }

    /** 引线横段：先垫一像素深色阴影，线压在星尘和节点上才不糊。 */
    private void wireH(PixelArt art, int xa, int xb, int y) {
        int a = Math.min(xa, xb);
        int b = Math.max(xa, xb);
        art.hline(a, b, y + 1, Palette.PANEL);
        art.hline(a, b, y, Palette.LINE_FAINT);
    }

    /** 引线竖段：阴影放在右侧一像素。 */
    private void wireV(PixelArt art, int ya, int yb, int x) {
        int a = Math.min(ya, yb);
        int b = Math.max(ya, yb);
        art.vline(a, b, x + 1, Palette.PANEL);
        art.vline(a, b, x, Palette.LINE_FAINT);
    }

    /** 以 {@code centerX} 为水平中心写一段中文（按字宽算，2 字/3 字名都不会偏）。 */
    private void drawStarLabel(CanvasBuffer buffer, String text, double centerX, double top,
                               double size, com.hysx.salvosequence.core.Color color) {
        zh(buffer, text, centerX - textWidth(text, size) / 2.0, top, size, color);
    }

    /** 悬浮标签：鼠标距节点中心 14px 内才显示（名字/类型都随悬浮出现）。 */
    private void drawStarLabelHover(CanvasBuffer buffer, String text,
                                    double nodeX, double nodeY, double labelTop,
                                    double size, com.hysx.salvosequence.core.Color color) {
        if (Math.hypot(mouseX - nodeX, mouseY - nodeY) < 14) {
            drawStarLabel(buffer, text, nodeX, labelTop, size, color);
        }
    }

    /** 太阳当天的赤经/赤纬（低精度太阳位置公式，误差约 0.02°，星图上完全够用）。 */
    private static double[] sunRaDec() {
        long n = java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.of(2000, 1, 1), java.time.LocalDate.now());
        double meanLon = 280.460 + 0.9856474 * n;
        double meanAnom = Math.toRadians(357.528 + 0.9856003 * n);
        double lambda = Math.toRadians(meanLon + 1.915 * Math.sin(meanAnom)
                + 0.020 * Math.sin(2 * meanAnom));
        double eps = Math.toRadians(23.439 - 0.0000004 * n);
        double ra = Math.toDegrees(Math.atan2(Math.cos(eps) * Math.sin(lambda),
                Math.cos(lambda)));
        double dec = Math.toDegrees(Math.asin(Math.sin(eps) * Math.sin(lambda)));
        return new double[]{(ra / 15.0 + 24.0) % 24.0, dec};
    }

    /** 赤经/赤纬 → 星图屏幕坐标（{@link Run#skyCoordOf} 的逆变换，跟着缩放/平移走）。 */
    private int[] sunScreenPos(double raHours, double decDeg) {
        double x = (raHours / 12.0 - 1.0) * Run.MAP_RX;
        double y = -(decDeg / Run.MAP_DEC_SPAN) * (Run.MAP_RX * Run.GALAXY_SQUASH);
        // 太阳在银河里的真实位置：距核心约 53% 半径（太阳距银心 ~8kpc / 盘半径 ~15kpc）。
        // 钳制到星图中心的固定半径，防止太阳被 RA 映射甩到银河边缘。
        double dist = Math.hypot(x, y);
        double maxR = Run.MAP_RX * 0.53;
        if (dist > maxR) {
            x = x / dist * maxR;
            y = y / dist * maxR;
        }
        // 盘面坐标先按旋转角转过去，再透视投影——太阳标记跟着星盘一起转
        double ang = Math.atan2(y, x) + starDiskRad();
        double rad = Math.hypot(x, y);
        double[] p = StarDisk.project(Math.cos(ang) * rad, Math.sin(ang) * rad);
        return new int[]{(int) Math.rint(p[0]), (int) Math.rint(p[1])};
    }

    /** 太阳标记：暖金核心 + 呼吸的八向光芒 + “太阳”标签（F10 隐藏/显示）。 */
    private void drawSunMarker(PixelArt art, CanvasBuffer buffer, double time,
                               java.util.List<int[]> taken) {
        double[] radec = sunRaDec();
        int[] p = sunScreenPos(radec[0], radec[1]);
        int sx = p[0];
        int sy = p[1];
        if (sx < -14 || sx > 654 || sy < -14 || sy > 374) {
            return;
        }
        double pulse = 0.5 + 0.5 * Math.sin(time * 2.2);
        int longLen = 4 + (int) Math.round(pulse * 2);      // 4..6
        art.rect(sx - 1, sy - 1, 3, 3, Palette.SUN);
        for (int i = 3; i <= longLen + 1; i++) {
            var c = i > longLen ? Palette.SUN_DIM : Palette.WARN;
            art.pixel(sx + i, sy, c);
            art.pixel(sx - i, sy, c);
            art.pixel(sx, sy + i, c);
            art.pixel(sx, sy - i, c);
        }
        int diag = 2 + (int) Math.round(pulse);
        for (int i = 2; i <= diag + 1; i++) {
            art.pixel(sx + i, sy + i, Palette.SUN_DIM);
            art.pixel(sx - i, sy + i, Palette.SUN_DIM);
            art.pixel(sx + i, sy - i, Palette.SUN_DIM);
            art.pixel(sx - i, sy - i, Palette.SUN_DIM);
        }
        // 标签：优先放下面，被恒星名/类型标签挡住就放上面；都挡就只留图标
        int w = (int) Math.ceil(textWidth("太阳", STAR_NAME_SIZE));
        if (starLabelFits(taken, sx - w / 2, sy + 8, w, (int) STAR_NAME_SIZE)) {
            drawStarLabel(buffer, "太阳", sx, sy + 8, STAR_NAME_SIZE, Palette.SUN);
        } else if (starLabelFits(taken, sx - w / 2, sy - 8 - (int) STAR_NAME_SIZE,
                w, (int) STAR_NAME_SIZE)) {
            drawStarLabel(buffer, "太阳", sx, sy - 8 - STAR_NAME_SIZE,
                    STAR_NAME_SIZE, Palette.SUN);
        }
    }

    /** 小标签防叠：与已占框不重叠才占位并返回 true（留 1px 余量，避免居中取整后贴边）。 */
    private static boolean starLabelFits(java.util.List<int[]> taken,
                                         int x, int y, int w, int h) {
        for (int[] b : taken) {
            if (x - 1 < b[0] + b[2] && b[0] < x + w + 1
                    && y - 1 < b[1] + b[3] && b[1] < y + h + 1) {
                return false;
            }
        }
        taken.add(new int[]{x, y, w, h});
        return true;
    }

    private com.hysx.salvosequence.core.Color starTypeColor(Run.NodeType t) {
        return switch (t) {
            case ELITE -> Palette.CRIMSON;
            case EVENT -> Palette.TEAL;
            case REST -> Palette.BLUE;
            case SHOP -> Palette.VIOLET;
            case BOSS -> Palette.DANGER;
            default -> Palette.SHIP_HULL;
        };
    }

    /** 把颜色整体压暗到 k（0~1）：远处/走过的节点星保留色相，只降亮度。 */
    private static com.hysx.salvosequence.core.Color shade(
            com.hysx.salvosequence.core.Color c, double k) {
        return com.hysx.salvosequence.core.Color.color(
                c.getRed() * k, c.getGreen() * k, c.getBlue() * k);
    }

    /**
     * 节点框的颜色：背景亮就用类型色的深版本（借黑框的反差），背景暗就用类型色本身。
     * 这样方框也带类型信息，而不是每颗星都套一个同样的白/黑框。
     */
    private Color nodeEdgeColor(Run.StarNode st, Color typeColor) {
        boolean brightBackground = Color.BLACK.equals(nodeFrameColor(st));
        return brightBackground ? shade(typeColor, 0.45) : shade(typeColor, 0.92);
    }

    private String starTypeLabel(Run.NodeType t) {
        return switch (t) {
            case START -> "起点";
            case ELITE -> "精英"; case EVENT -> "事件"; case REST -> "休息";
            case SHOP -> "商店"; case BOSS -> "星核"; default -> "战斗";
        };
    }

    /** 虚线椭圆（跃迁半径圈）。 */
    private void drawDashedCircle(PixelArt art, int cx, int cy, int rx, int ry) {
        for (int a = 0; a < 360; a += 8) {
            double rad = Math.toRadians(a);
            art.pixel((int) (cx + Math.cos(rad) * rx), (int) (cy + Math.sin(rad) * ry),
                    Palette.LINE_FAINT);
        }
    }

    /** 亮星：核心 3x3 + 四向衍射光芒（尖梢渐暗）。 */
    private void drawStarGlow(PixelArt art, int x, int y,
                              com.hysx.salvosequence.core.Color core,
                              com.hysx.salvosequence.core.Color spike, int len) {
        art.rect(x - 1, y - 1, 3, 3, core);
        for (int i = 2; i <= len; i++) {
            var c = i * 2 > len ? Palette.LINE_FAINT : spike;
            art.pixel(x + i, y, c);
            art.pixel(x - i, y, c);
            art.pixel(x, y + i, c);
            art.pixel(x, y - i, c);
        }
    }

    /**
     * 关卡星的闪烁：比背景星尘多两层——随时间呼吸的十字/斜向光芒、绕星旋转的虚线星环，
     * 最亮的一瞬再甩出尖梢星芒。亮度/长度按 {@code strength} 分档，
     * 每个节点用 id 错开相位，不会整齐划一地一起亮。
     */
    private void drawNodeSparkle(PixelArt art, int x, int y,
                                 com.hysx.salvosequence.core.Color color, int strength,
                                 double time, int phase) {
        double ph = phase * 0.37;
        double pulse = 0.5 + 0.5 * Math.sin(time * 2.6 + ph);
        double spin = time * 1.1 + ph;
        int horiz = 3 + strength + (int) Math.round(pulse * (2 + strength));
        int vert = Math.min(horiz, 6);
        // 芒尖用同色压暗，不再统一换成 LINE/白：一旦换成白，所有星都被拉成
        // 同一种暖白，类型色只剩中间一两个像素，星图就会糊成"一片橙"。
        for (int i = 2; i <= horiz; i++) {
            var c = i >= horiz - 1 ? shade(color, 0.55) : color;
            art.pixel(x + i, y, c);
            art.pixel(x - i, y, c);
        }
        for (int i = 2; i <= vert; i++) {
            var c = i >= vert - 1 ? shade(color, 0.55) : color;
            art.pixel(x, y + i, c);
            art.pixel(x, y - i, c);
        }
        int diag = Math.min(2 + (int) Math.round(pulse * 2 + strength), 6);
        for (int i = 2; i <= diag; i++) {
            var c = i >= diag ? shade(color, 0.55) : color;
            art.pixel(x + i, y + i, c);
            art.pixel(x - i, y + i, c);
            art.pixel(x + i, y - i, c);
            art.pixel(x - i, y - i, c);
        }
        if (strength >= 1) {
            int rr = strength >= 2 ? 8 : 7;
            for (int k = 0; k < 8; k++) {
                double a = spin + k * Math.PI / 4;
                art.pixel((int) Math.rint(x + Math.cos(a) * rr),
                        (int) Math.rint(y + Math.sin(a) * rr * 0.72),
                        k % 2 == 0 ? color : Palette.LINE_FAINT);
            }
        }
        // 可跃迁的关卡再甩出三颗 2x2 的环绕亮点，像卫星一样绕关卡转：
        // 它们比背景星尘大一圈、还会移动，缩放后也能一眼认出这是关卡。
        if (strength >= 2) {
            int orbit = strength >= 3 ? 10 : 8;
            for (int k = 0; k < 3; k++) {
                double a = spin * 0.9 + k * 2.0944;
                int ox = (int) Math.rint(x + Math.cos(a) * orbit);
                int oy = (int) Math.rint(y + Math.sin(a) * orbit * 0.62);
                art.rect(ox, oy, 2, 2, k == 0 ? Palette.LINE : color);
            }
        }
        // 尖梢闪光：最亮的一瞬在长芒尖端点一颗白星，看起来像“眨了一下”。
        if (strength >= 2 && pulse > 0.9) {
            art.pixel(x + horiz, y, Palette.LINE);
            art.pixel(x - horiz, y, Palette.LINE);
            art.pixel(x, y + vert, Palette.LINE);
            art.pixel(x, y - vert, Palette.LINE);
        }
    }

    private void drawLog(CanvasBuffer buffer, PixelArt art) {
        if (stage() != Run.Stage.BATTLE) {
            return;
        }
        List<String> lines = run.battle() == null
                ? run.recentLog(LOG_LINES)
                : run.battle().recentLog(LOG_LINES);
        for (int i = 0; i < lines.size(); i++) {
            boolean last = i == lines.size() - 1;
            zh(buffer, lines.get(i), LOG_X, LOG_Y + i * LOG_PITCH, 12,
                    last ? Palette.LINE : Palette.LINE_DIM);
        }
    }

    private void drawStatusLines(CanvasBuffer buffer, PixelArt art) {
        Battle battle = run.battle();
        if (battle == null) {
            return;
        }
        // 左栏只有 BTN_W 那么宽：原来两条长行在这里会被右边缘裁掉，拆成四行短句，
        // 每行两个字段，读起来还是"回合/格挡、瞄准/装填、牌库/弃牌、开火状态"的老顺序
        zh(buffer, Language.t("回合 " + battle.turn() + "  格挡 " + battle.block(),
                        "TURN " + battle.turn() + "  BLK " + battle.block()),
                STATUS_X, STATUS_Y, 12, Palette.LINE_DIM);
        zh(buffer, Language.t("瞄准 " + battle.aim() + "  装填 " + battle.loadingZone().size(),
                        "AIM " + battle.aim() + "  LOAD " + battle.loadingZone().size()),
                STATUS_X, STATUS_Y + STATUS_PITCH, 12, Palette.LINE_DIM);
        zh(buffer, Language.t("牌库 " + battle.drawPile().size() + "  弃牌 " + battle.discard().size(),
                        "DRAW " + battle.drawPile().size() + "  DISC " + battle.discard().size()),
                STATUS_X, STATUS_Y + STATUS_PITCH * 2, 12, Palette.LINE_DIM);
        zh(buffer, Language.t(
                        battle.freeFire() ? "自由开火 ×1.2" : "集火",
                        battle.freeFire() ? "FREE FIRE x1.2" : "FOCUS FIRE"),
                STATUS_X, STATUS_Y + STATUS_PITCH * 3, 12,
                battle.freeFire() ? Palette.OK : Palette.WARN);
    }

    private void drawControlButtons(CanvasBuffer buffer, PixelArt art) {
        if (stage() != Run.Stage.BATTLE) {
            return;
        }
        Battle battle = run.battle();
        var sig = battle.signature();
        boolean signature = sig != Captain.Signature.NONE;
        boolean skillReady = signature
                ? !battle.signatureUsed()
                : battle.energy() >= Battle.SKILL_COST && !battle.skillUsed();
        // 技能名太长，塞不进 110px 的按钮（button() 不做裁剪）；按钮只写"舰长技 (S)"，
        // 具体是哪一招放在按钮下面一行
        button(art, buffer, BTN_X, SKILL_BTN_Y, BTN_W, BTN_H,
                Language.t("舰长技 (S)", "SKILL (S)"),
                hoveredSkill || skillReady ? Palette.CYAN : Palette.LINE_FAINT);
        zh(buffer, signature ? sig.nameZh() : Language.t("校射", "FOCUS"),
                BTN_X + 4, SKILL_BTN_Y + BTN_H + 1, 12, Palette.CYAN_DIM);
        button(art, buffer, BTN_X, TURN_BTN_Y, BTN_W, BTN_H,
                Language.t("结束回合", "END TURN"), Palette.OK);
        if (battle.shipSkillAvailable()) {
            button(art, buffer, BTN_X, TURN_BTN_Y + BTN_H + 6, BTN_W, BTN_H,
                    Language.t("舰船技 (X)", "SHIP SKILL (X)"), Palette.WARN);
        }
        // 快捷键说明压在按钮下面：一行放不下三条，拆成三行小字
        int hintY = TURN_BTN_Y + BTN_H + (battle.shipSkillAvailable() ? 30 : 6);
        zh(buffer, Language.t("空格 结束回合", "SPACE END TURN"), BTN_X, hintY, 12,
                Palette.LINE_FAINT);
        zh(buffer, Language.t("G 火力 · Z 放大", "G FIRE · Z ZOOM"), BTN_X, hintY + 11, 12,
                Palette.LINE_FAINT);
        zh(buffer, Language.t("←→ 换目标", "←→ TARGET"), BTN_X, hintY + 22, 12,
                Palette.LINE_FAINT);
    }

    // ------------------------------------------------------------ 新版战斗界面

    /** 菱形（描边）。 */
    private void diamond(PixelArt art, int cx, int cy, int r,
                         com.hysx.salvosequence.core.Color color) {
        art.line(cx, cy - r, cx + r, cy, color);
        art.line(cx + r, cy, cx, cy + r, color);
        art.line(cx, cy + r, cx - r, cy, color);
        art.line(cx - r, cy, cx, cy - r, color);
    }

    /** 菱形（填充）。 */
    private void fillDiamond(PixelArt art, int cx, int cy, int r,
                             com.hysx.salvosequence.core.Color color) {
        art.polygon(new double[]{cx, cx + r, cx, cx - r+1},
                new double[]{cy - r+1, cy, cy + r, cy}, color);
    }

    /** 左上信息框：设置齿轮 + 航程 + 双方结构值。 */
    private void drawLeftPanel(CanvasBuffer buffer, PixelArt art) {
        // 底板由 drawPortraitFrame() 负责（那一块是立绘的框），这里只放压在框里的零件：
        // 右上角齿轮、下面一条航程。
        if (!drawAseTexture(art, "gear")) {
            UiChrome.chamferFill(art, GEAR_X, GEAR_Y, GEAR_S, GEAR_S, 2, Palette.PANEL_ALT);
            UiChrome.chamferFrame(art, GEAR_X, GEAR_Y, GEAR_S, GEAR_S, 2, Palette.STEEL_DIM);
        }
        if (hoveredGear) {
            UiChrome.chamferFrame(art, GEAR_X, GEAR_Y, GEAR_S, GEAR_S, 2, Palette.AMBER);
        }
        int gx = GEAR_X + GEAR_S / 2;
        int gy = GEAR_Y + GEAR_S / 2;
        if (!drawAseTexture(art, "gear")) {
            com.hysx.salvosequence.core.Color ink =
                    hoveredGear ? Palette.AMBER : Palette.LINE_DIM;
            // 齿轮：四根轴向齿 + 一圈 7×7 齿盘 + 3×3 轴孔，18px 里能读出"设置"
            art.fillRect(gx - 1, gy - 6, 2, 3, ink);
            art.fillRect(gx - 1, gy + 4, 2, 3, ink);
            art.fillRect(gx - 6, gy - 1, 3, 2, ink);
            art.fillRect(gx + 4, gy - 1, 3, 2, ink);
            art.rect(gx - 3, gy - 3, 7, 7, ink);
            art.fillRect(gx - 1, gy - 1, 3, 3, Palette.PANEL_ALT);
        }

        // 航程挪进右下读数据里了（那里横着排得下八颗菱形），左栏只留立绘框和这个齿轮。
    }

    private void hpBar(CanvasBuffer buffer, PixelArt art, int y, String label,
                       int hp, int max, com.hysx.salvosequence.core.Color color) {
        zh(buffer, label, 4, y, 12, Palette.LINE_DIM);
        int barX = 30;
        int barW = PANEL_W - barX - 26;
        double ratio = Math.max(0, Math.min(1, hp / (double) max));
        UiChrome.segBar(art, barX, y + 4, barW, 5, ratio, color);
        en(art, hp + "/" + max, PANEL_W - 24, y + 2, Palette.LINE_DIM);
    }

    /**
     * 底部指令台：一整块板托住左边的操作轨（舰长技 / 跳过 / 能量）和三列手牌。
     *
     * <p>装饰只放在边缘：竖隔断、三粒铆钉、标题和底缘散热缝。中间留给卡片，
     * 不抢字。</p>
     */
    private void drawCommandConsole(CanvasBuffer buffer, PixelArt art) {
        UiChrome.panel(art, CONSOLE_X, CONSOLE_Y, CONSOLE_W, CONSOLE_H);
        // 指令台与右侧读数据之间那 2px 缝：不填深色，星云的红色会从缝里透出来像裂开
        art.fillRect(CONSOLE_X + CONSOLE_W, CONSOLE_Y,
                INFO_X - (CONSOLE_X + CONSOLE_W), CONSOLE_H, Palette.SPACE_DEEP);
        // 操作轨 / 手牌区之间的竖隔断：一根钢蓝线 + 一条暗缝
        art.vline(CONSOLE_Y + 5, CONSOLE_Y + CONSOLE_H - 6, RAIL_X, Palette.STEEL_FAINT);
        art.vline(CONSOLE_Y + 5, CONSOLE_Y + CONSOLE_H - 6, RAIL_X + 1, Palette.SPACE_DEEP);
        // 隔断上的三粒铆钉
        for (int y = CONSOLE_Y + 12; y < CONSOLE_Y + CONSOLE_H - 10; y += 22) {
            art.fillRect(RAIL_X - 3, y, 2, 2, Palette.PANEL_EDGE);
        }
        // 标题：琥珀小方块 + 「指令」+ 8px 拉丁副题
        art.fillRect(47, CONSOLE_Y + 6, 3, 3, Palette.AMBER);
        zh(buffer, "指令", 53, CONSOLE_Y + 2, 12, Palette.LINE);
        UiChrome.latin(art, "COMMAND", 87, CONSOLE_Y + 6, Palette.STEEL_DIM);
        // 手牌计数靠右，压着卡片右缘
        UiChrome.zhRight(buffer, "手牌 " + hand().size() + "/" + CARD_MAX,
                CONSOLE_X + CONSOLE_W - 8, CONSOLE_Y + 2, 12, Palette.LINE_DIM);
        // 底缘散热缝：一排短横线，落在最后一行卡片下面的空带里
        for (int x = CONSOLE_X + CONSOLE_W - 74; x < CONSOLE_X + CONSOLE_W - 10; x += 7) {
            art.hline(x, x + 4, CONSOLE_Y + CONSOLE_H - 4, Palette.STEEL_FAINT);
        }
        // 结构细节：副题与手牌计数之间的空带塞一块警示斜纹、一组散热槽和出厂编号
        UiChrome.hazard(art, CONSOLE_X + 150, CONSOLE_Y + 5, 20, 4);
        UiChrome.vents(art, CONSOLE_X + 176, CONSOLE_Y + 4, 30, 2);
        UiChrome.serial(art, "CN-02", CONSOLE_X + CONSOLE_W - 78, CONSOLE_Y + 6);
    }

    /** 舰长技（菱形按钮）/ 跳过回合（箭头板）/ 剩余能量。 */
    private void drawCaptainControls(CanvasBuffer buffer, PixelArt art) {
        Battle battle = run.battle();
        if (battle == null) {
            return;
        }
        boolean signature = battle.signature() != Captain.Signature.NONE;
        boolean used = signature ? battle.signatureUsed() : battle.skillUsed();
        boolean ready = signature
                ? !used
                : battle.energy() >= Battle.SKILL_COST && !used;
        com.hysx.salvosequence.core.Color edge =
                ready ? (hoveredSkill ? Palette.WARN : Palette.AMBER)
                        : (hoveredSkill ? Palette.STEEL : Palette.PANEL_EDGE);
        com.hysx.salvosequence.core.Color icon =
                ready ? (hoveredSkill ? Palette.WARN : Palette.AMBER) : Palette.LINE_FAINT;

        int cx = SKILL_CX;
        int cy = SKILL_CY;
        int r = SKILL_R;
        // 1) 投影：右下错 1px，菱形像从板面上浮起来
        fillDiamond(art, cx + 1, cy + 2, r, Palette.SPACE_DEEP);
        // 2) 外圈斜面：左上受光、右下背光（琥珀=可用，钢蓝=不可用）
        fillDiamond(art, cx, cy, r, ready ? Palette.AMBER_DEEP : Palette.STEEL_FAINT);
        art.line(cx - r, cy, cx, cy - r, edge);
        art.line(cx, cy - r, cx + r, cy,
                ready ? Palette.AMBER_DIM : Palette.STEEL_DIM);
        art.line(cx + r, cy, cx, cy + r, Palette.SPACE_DEEP);
        art.line(cx, cy + r, cx - r, cy, Palette.STEEL_DIM);
        // 3) 内面：再收一圈细线，按钮才有厚度
        fillDiamond(art, cx, cy, r - 4, ready ? Palette.PANEL_ALT : Palette.PANEL);
        diamond(art, cx, cy, r - 4, ready ? Palette.AMBER_DIM : Palette.PANEL_EDGE);
        // 4) 中心徽记：八芒罗盘星——四根锥形长芒 + 四粒斜向短芒。
        //    粗十字在 34px 的按钮里会读成"加号"，锥形星芒才像舰长的徽记。
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] d : dirs) {
            double[] xs = {cx + d[0] * 10,
                    cx + d[0] * 3 - d[1] * 3,
                    cx + d[0] * 4,
                    cx + d[0] * 3 + d[1] * 3};
            double[] ys = {cy + d[1] * 10,
                    cy + d[1] * 3 + d[0] * 3,
                    cy + d[1] * 4,
                    cy + d[1] * 3 - d[0] * 3};
            art.polygon(xs, ys, icon);
        }
        fillDiamond(art, cx, cy, 2, icon);
        art.fillRect(cx - 6, cy - 6, 2, 2, icon);
        art.fillRect(cx + 5, cy - 6, 2, 2, icon);
        art.fillRect(cx - 6, cy + 5, 2, 2, icon);
        art.fillRect(cx + 5, cy + 5, 2, 2, icon);
        if (used) {
            // 已用掉：斜着划一道，和"能量不够"的暗态区分开
            art.line(cx - 8, cy + 8, cx + 8, cy - 8, Palette.LINE_FAINT);
        }
        // 5) 外圈细描边：悬停时整颗亮起来
        diamond(art, cx, cy, r + 2, hoveredSkill ? Palette.WARN : Palette.PANEL_EDGE);
        // 6) 悬停说明：图标按钮不写字，技能名放到上方的临时小牌上
        if (hoveredSkill) {
            String label = signature ? "舰长技·" + battle.signature().nameZh()
                    : Language.t("舰长技", "SKILL");
            // textWidth 按 12px 估宽，实际字面比 advance 宽 1px 左右，留 9px 内边距才不会吃字
            int tw = (int) Math.round(textWidth(label, 12)) + 18;
            int tx = Math.max(2, cx - tw / 2);
            int ty = cy - r - 21;
            art.fillRect(tx, ty, tw, 17, Palette.PANEL);
            art.frame(tx, ty, tw, 17, Palette.AMBER_DIM);
            zh(buffer, label, tx + 9, ty + 2, 12, Palette.LINE);
        }

        // 跳过回合：与舰长技同一套菱形「模拟按钮」——投影 + 四边斜面 + 内嵌面 + 悬停外圈。
        // 原来右端收成尖角的三角板，38px 宽里既塞不下"跳过"两个字，也不像按钮；
        // 菱形里放不下 12px 的两个字，所以只留双人字图标，名字交给悬停小牌。
        int scx = SKIP_X + SKIP_W / 2;
        int scy = SKIP_Y + SKIP_H / 2;
        int sr = SKIP_R;
        fillDiamond(art, scx + 1, scy + 2, sr, Palette.SPACE_DEEP);
        fillDiamond(art, scx, scy, sr, Palette.STEEL_FAINT);
        com.hysx.salvosequence.core.Color skipEdge =
                hoveredTurn ? Palette.OK : Palette.STEEL;
        art.line(scx - sr, scy, scx, scy - sr, skipEdge);
        art.line(scx, scy - sr, scx + sr, scy, Palette.STEEL_DIM);
        art.line(scx + sr, scy, scx, scy + sr, Palette.SPACE_DEEP);
        art.line(scx, scy + sr, scx - sr, scy, Palette.STEEL_DIM);
        fillDiamond(art, scx, scy, sr - 4, Palette.PANEL);
        diamond(art, scx, scy, sr - 4, hoveredTurn ? Palette.OK : Palette.PANEL_EDGE);
        com.hysx.salvosequence.core.Color skipInk =
                hoveredTurn ? Palette.OK : Palette.LINE_DIM;
        // 跳过图标：实心双人字（两枚右向三角叠在一起），比原来的细线人字更有分量。
        // 收在内嵌菱形面里（顶点 |dx|+|dy| ≤ 9），整体右移 2px，别压到斜面；
        // 左三角比右三角矮一档、并且让右三角压住它的尖，读起来才不是一大一小两块。
        art.polygon(new double[]{scx - 3, scx - 3, scx + 2},
                new double[]{scy - 4, scy + 6, scy}, skipInk);
        art.polygon(new double[]{scx + 1, scx + 1, scx + 6},
                new double[]{scy - 4, scy + 6, scy}, skipInk);
        diamond(art, scx, scy, sr + 2, hoveredTurn ? Palette.WARN : Palette.PANEL_EDGE);
        if (hoveredTurn) {
            String label = Language.t("跳过回合", "END TURN");
            int tw = (int) Math.round(textWidth(label, 12)) + 18;
            int tx = Math.max(2, scx - tw / 2);
            int ty = 246;
            art.fillRect(tx, ty, tw, 17, Palette.PANEL);
            art.frame(tx, ty, tw, 17, Palette.STEEL_DIM);
            zh(buffer, label, tx + 9, ty + 2, 12, Palette.LINE);
        }

        // 剩余能量：操作轨中间一排小菱形，整排以操作轨中线对齐（数量变了也居中）
        int energy = battle.energy();
        int maxEnergy = Math.max(1, battle.maxEnergy());
        int pips = Math.min(maxEnergy, 5);
        int startX = ENERGY_CX - (pips - 1) * 3;
        for (int i = 0; i < pips; i++) {
            int px = startX + i * 6;
            if (i < energy) {
                fillDiamond(art, px, ENERGY_Y, 3, Palette.WARN);
            } else {
                diamond(art, px, ENERGY_Y, 3, Palette.PANEL_EDGE);
            }
        }
    }

    /** 右下信息框：回合/格挡/装填 + 战报 + 选中与击毁。 */
    /** 信息框左上角斜切后，第 y 行文字要缩进多少（贴着斜边排）。 */
    private int indentAt(double y) {
        double dy = y - INFO_Y;
        return (int) Math.round(INFO_H - dy) + 4;
    }

    /**
     * 有 Aseprite 贴图就贴上去（按切片位置），并返回 true；没有返回 false 让调用方画代码图形。
     *
     * <p>贴图来自 {@code -Dsalvo.ase=<json>} 那份导出，图层名 {@code img.<id>}、切片名 {@code <id>}。</p>
     */
    private boolean drawAseTexture(PixelArt art, String id) {
        com.hysx.salvosequence.core.AseUi ui = com.hysx.salvosequence.core.AseUi.active();
        if (ui == null || ui.texture(id) == null) {
            return false;
        }
        ui.drawTexture(art, id);
        return true;
    }

    private void drawInfoBox(CanvasBuffer buffer, PixelArt art) {
        // 战斗读数视窗：不带铭牌（左上那块地留给立绘），内容自上而下——
        // 两条横放的结构条 / 一行属性 / 一条分割线 / 战报 / 航程刻度。
        if (!drawAseTexture(art, "info.box")) {
            UiChrome.panel(art, INFO_X, INFO_Y, INFO_W, INFO_H);
        }
        // 结构细节：右缘一条竖向量程，读数区因此读成一台仪器而不是一块黑板
        UiChrome.gaugeV(art, INFO_X + INFO_W - 6, INFO_Y + 10, INFO_H - 24, 6, 18,
                Palette.STEEL_FAINT);
        Battle battle = run.battle();
        if (battle == null) {
            return;
        }
        int col = INFO_X + 10;
        int right = INFO_X + INFO_W - 10;
        int half = (right - col - 12) / 2;
        // 上半：我方在左、敌方在右，各占一半；数值右对齐到各自半区
        UiChrome.latin(art, "OURS", col, INFO_Y + 4, Palette.TEAL);
        String ours = run.hp() + "/" + run.maxHp();
        en(art, ours, col + half - (int) font.measure(ours), INFO_Y + 4, Palette.LINE);
        UiChrome.segBar(art, col, INFO_Y + 14, half, 4,
                ratio(run.hp(), run.maxHp()), hpColor(run.hp(), run.maxHp()));

        List<FoeView> foes = foeViews();
        int alive = 0;
        int hpSum = 0;
        int maxSum = 0;
        for (FoeView foe : foes) {
            if (foe.alive()) {
                alive++;
                hpSum += foe.hp();
                maxSum += foe.maxHp();
            }
        }
        int fx = col + half + 12;
        UiChrome.latin(art, "FOE", fx, INFO_Y + 4, Palette.CRIMSON);
        String foesText = alive + "/" + foes.size() + " " + hpSum + "/" + Math.max(1, maxSum);
        en(art, foesText, right - (int) font.measure(foesText), INFO_Y + 4, Palette.LINE_DIM);
        UiChrome.segBar(art, fx, INFO_Y + 14, right - fx, 4,
                maxSum <= 0 ? 0 : ratio(hpSum, maxSum), Palette.CRIMSON);

        // 属性一行：四列等距，数字跟着中文一起排
        int ay = INFO_Y + 21;
        zh(buffer, Language.t("回合 " + battle.turn(), "TURN " + battle.turn()),
                col, ay, 12, Palette.LINE);
        zh(buffer, Language.t("格挡 " + battle.block(), "BLK " + battle.block()),
                col + 56, ay, 12, Palette.CYAN_DIM);
        zh(buffer, Language.t("牌库 " + battle.drawPile().size(),
                        "DRAW " + battle.drawPile().size()),
                col + 112, ay, 12, Palette.LINE_DIM);
        zh(buffer, Language.t("弃牌 " + battle.discard().size(),
                        "DISC " + battle.discard().size()),
                col + 168, ay, 12, Palette.LINE_FAINT);

        // 分割线：把读数区和战报分开
        UiChrome.divider(art, col, INFO_Y + 36, INFO_W - 20);

        List<String> lines = battle.recentLog(2);
        for (int i = 0; i < lines.size(); i++) {
            double ly = INFO_Y + 40 + i * 14;
            zh(buffer, fit(lines.get(i), 12, INFO_W - 20), col, ly, 12,
                    i == lines.size() - 1 ? Palette.LINE : Palette.LINE_DIM);
        }

        // 航程刻度：菱形串 + 右下角节点读数
        int ry = INFO_Y + 80;
        zh(buffer, "航程", col, ry - 6, 12, Palette.LINE_FAINT);
        int rx = col + 34;
        int total = run.routeLength();
        if (total > 1) {
            art.hline(rx, rx + Math.min(total, 8) * 11 - 7, ry, Palette.STEEL_FAINT);
        }
        for (int i = 0; i < total && i < 8; i++) {
            int cx = rx + i * 11;
            if (i < run.node()) {
                fillDiamond(art, cx, ry, 3, Palette.TEAL);
            } else if (i == run.node()) {
                fillDiamond(art, cx, ry, 3, Palette.PANEL_ALT);
                diamond(art, cx, ry, 4, Palette.AMBER);
                UiChrome.tick(art, cx, ry, Palette.WARN);
            } else {
                diamond(art, cx, ry, 4, Palette.STEEL_DIM);
            }
        }
        String nodeText = (run.node() + 1) + "/" + Math.max(1, total);
        en(art, nodeText, right - (int) font.measure(nodeText), ry - 4, Palette.STEEL);
    }

    /**
     * 战斗仪表盘：左栏 92~258 这一大片原本是空的（状态栏/舰长技按钮那套早就是死代码），
     * 战斗时又故意不画顶栏，于是"我方还剩多少结构值"在盘面上根本读不到。
     * 这里把它补成一块读数视窗：双方结构值 + 六个回合读数，位置就在立绘上方。
     */
    /**
     * 战斗读数：只留最左上角的两条结构条。
     *
     * <p>原来这里是一整块「战况」仪表盘（回合/格挡/瞄准/装填/牌库/弃牌六行）——那块地是留给
     * F12 立绘的（见 {@link #drawPortraitFrame}），读数回到底盘自带的信息框里。结构值必须
     * 一直看得见，所以缩成两条窄条钉在左上角。</p>
     */
    private void drawVitals(CanvasBuffer buffer, PixelArt art) {
        Battle battle = run.battle();
        if (battle == null) {
            return;
        }
        final int bx = 8;
        final int bw = 108;

        UiChrome.latin(art, "OURS", bx, VITALS_Y, Palette.TEAL);
        String ours = run.hp() + "/" + run.maxHp();
        en(art, ours, bx + bw - (int) font.measure(ours), VITALS_Y, Palette.LINE);
        UiChrome.segBar(art, bx, VITALS_Y + 12, bw, 5,
                ratio(run.hp(), run.maxHp()), hpColor(run.hp(), run.maxHp()));

        List<FoeView> foes = foeViews();
        int alive = 0;
        int hpSum = 0;
        int maxSum = 0;
        for (FoeView foe : foes) {
            if (foe.alive()) {
                alive++;
                hpSum += foe.hp();
                maxSum += foe.maxHp();
            }
        }
        UiChrome.latin(art, "FOE " + alive + "/" + foes.size(), bx, VITALS_Y + 18, Palette.CRIMSON);
        String foesText = hpSum + "/" + Math.max(1, maxSum);
        en(art, foesText, bx + bw - (int) font.measure(foesText), VITALS_Y + 18, Palette.LINE_DIM);
        UiChrome.segBar(art, bx, VITALS_Y + 30, bw, 5,
                maxSum <= 0 ? 0 : ratio(hpSum, maxSum), Palette.CRIMSON);
    }

    /**
     * 左栏立绘框：F12 立绘站在这块板子里。
     *
     * <p>板子在立绘之前画（{@code PortraitLayer.draw} 压在上面），所以立绘不会被框盖住；
     * 框沿用同一套语汇：切角底板 + 羊皮纸铭牌 + 四角括号 + 一道分隔线。</p>
     */
    private void drawPortraitFrame(CanvasBuffer buffer, PixelArt art) {
        // 空白处铺一层和棋子盘同款的方格网：口径一致（16px 一格 + 交点暗点），
        // 空窗口读成一块待机的战术屏，而不是一块没画完的透明洞。
        drawPortraitGrid(art);
        UiChrome.chamferFrame(art, 0, 0, 120, 268, UiChrome.CUT, Palette.STEEL_DIM);
        UiChrome.chamferFrame(art, 2, 2, 116, 264,
                Math.max(1, UiChrome.CUT - 2), Palette.STEEL_FAINT);
        UiChrome.brackets(art, 0, 0, 120, 268, 2, Palette.AMBER, 5);
        // 左上身份条：右上角是齿轮，左侧留一个 CAPT 短板，空窗口也不像没画完
        art.fillRect(8, 7, 2, 6, Palette.AMBER_DIM);
        UiChrome.latin(art, "CAPT", 12, 7, Palette.STEEL_DIM);
        // 仪表细节：左缘量程、底部散热槽、出厂编号——立绘没开时窗口也是台舰长终端
        UiChrome.gaugeV(art, 6, 24, 210, 8, 32, Palette.STEEL_FAINT);
        UiChrome.vents(art, 14, 236, 46, 3);
        art.fillRect(66, 236, 2, 9, Palette.STEEL_FAINT);
        UiChrome.serial(art, "TERM-04", 110, 252);
        // 立绘未开启时的待机画面：中心准星 + STANDBY，空窗口读成一台待机的舰长终端
        int cx = 60;
        int cy = 122;
        art.hline(cx - 11, cx - 5, cy, Palette.STEEL_FAINT);
        art.hline(cx + 5, cx + 11, cy, Palette.STEEL_FAINT);
        art.vline(cy - 11, cy - 5, cx, Palette.STEEL_FAINT);
        art.vline(cy + 5, cy + 11, cx, Palette.STEEL_FAINT);
        art.pixel(cx, cy, Palette.AMBER_DIM);
        art.pixel(cx - 1, cy, Palette.STEEL_DIM);
        art.pixel(cx + 1, cy, Palette.STEEL_DIM);
        art.pixel(cx, cy - 1, Palette.STEEL_DIM);
        art.pixel(cx, cy + 1, Palette.STEEL_DIM);
        UiChrome.latinCentered(art, "STANDBY", cx, cy + 18, Palette.AMBER);
    }

    /**
     * 立绘窗的空白方格网：和棋盘同一套口径——{@link #BOARD_CELL} 一格、格线是压在背景上的
     * 半透明暗线、交点上一点钢灰。
     *
     * <p>棋盘那块玻璃底噪很强，所以格线用 0.42 的暗色压住星云；立绘窗背后是同一片星幕，
     * 直接沿用这个值，两块屏的网格才对得上、读成同一套仪器。</p>
     *
     * <p>画在框线之前，四角切角处整块跳过，不往切掉的那块三角里伸。</p>
     */
    private void drawPortraitGrid(PixelArt art) {
        int w = 120;
        int h = 268;
        int cut = UiChrome.CUT;
        // 棋盘那块玻璃的底色：半透明深压一层，星幕还透得出来，但格线和斜纹才有东西可压。
        // 立绘窗和棋盘同一块"屏"，所以直接用同一个 BOARD_FILL，不另调一个近似值。
        UiChrome.chamferFill(art, 0, 0, w, h, cut, UiChrome.BOARD_FILL);
        // 棋盘那块是亮玻璃，格线用暗色"刻"进去；立绘窗背后是深空，暗线读不出来，
        // 所以这里反过来——用很淡的钢色提亮，两头（深空/亮行星）都能看见。
        com.hysx.salvosequence.core.Color cellLine =
                com.hysx.salvosequence.core.Color.rgb(126, 158, 182, 0.20);
        for (int gx = BOARD_CELL; gx < w; gx += BOARD_CELL) {
            for (int y = 1; y < h - 1; y++) {
                if (inCutCorner(gx, y, w, h, cut)) {
                    continue;
                }
                art.pixel(gx, y, cellLine);
            }
        }
        for (int gy = BOARD_CELL; gy < h; gy += BOARD_CELL) {
            for (int x = 1; x < w - 1; x++) {
                if (inCutCorner(x, gy, w, h, cut)) {
                    continue;
                }
                art.pixel(x, gy, cellLine);
            }
        }
        // 交点暗点：空窗口也有战术网格感，画面里不抢 STANDBY 和准星
        for (int gx = BOARD_CELL; gx < w; gx += BOARD_CELL) {
            for (int gy = BOARD_CELL; gy < h; gy += BOARD_CELL) {
                art.pixel(gx, gy, Palette.STEEL_FAINT);
            }
        }
        // 棋盘那种 45° 斜点线：和面板底纹同一个口径（ditherRect 的 density=2），
        // 立绘窗背后是活的星幕，压一层斜纹才读成"一块屏"而不是透明洞。
        // 线色仍是提亮而不是压暗：面板底 HALFTONE 是深蓝，压在深空上根本看不见。
        com.hysx.salvosequence.core.Color hatch =
                com.hysx.salvosequence.core.Color.rgb(126, 158, 182, 0.13);
        for (int y = 2; y < h - 2; y++) {
            for (int x = 2 + Math.floorMod(-y, 3); x < w - 2; x += 3) {
                if (!inCutCorner(x, y, w, h, cut)) {
                    art.pixel(x, y, hatch);
                }
            }
        }
    }

    /** 点是否落在被切角切掉的那四个角块里（切角是三角，按整块方角跳更保守）。 */
    private static boolean inCutCorner(int x, int y, int w, int h, int cut) {
        boolean side = x < cut || x >= w - cut;
        boolean edge = y < cut || y >= h - cut;
        return side && edge;
    }

    /** 立绘压上来之后补回窗口框线：让人物被"裁"在窗口里，而不是糊到屏幕边缘。 */
    private void drawPortraitFrameEdge(PixelArt art) {        UiChrome.chamferFrame(art, 0, 0, 120, 268, UiChrome.CUT, Palette.STEEL_DIM);
        UiChrome.chamferFrame(art, 2, 2, 116, 264,
                Math.max(1, UiChrome.CUT - 2), Palette.STEEL_FAINT);
        UiChrome.brackets(art, 0, 0, 120, 268, 2, Palette.AMBER, 5);
    }

    /** 指令卡：长条，左能量、中图标、右装填。 */
    private void drawHandBars(CanvasBuffer buffer, PixelArt art) {
        Battle battle = run.battle();
        List<Card> hand = hand();
        int count = Math.min(CARD_MAX, hand.size());
        // 悬停那张卡最后画：说明框压在它下面，只有卡片自己的两个切角露出一点说明
        int hovered = hoveredOption >= 0 && hoveredOption < count ? hoveredOption : -1;
        // 空卡槽：托盘里留一圈几乎看不见的轮廓，手牌不满时也看得出还剩几个位置
        for (int i = count; i < CARD_MAX; i++) {
            drawEmptyCardSlot(art, i);
        }
        for (int i = 0; i < count; i++) {
            if (i == hovered) {
                continue;
            }
            int[] box = cardBarRect(i);
            boolean playable = battle != null && battle.canPlay(i);
            drawCardBar(buffer, art, hand.get(i), box[0], box[1], box[2], box[3],
                    false, playable, i + 1);
        }
        if (hand.isEmpty()) {
            zh(buffer, Language.t("手牌空了 —— 空格结束回合", "HAND EMPTY - SPACE ENDS TURN"),
                    CARD_X, CARD_Y + 4, 12, Palette.LINE_FAINT);
            return;
        }
        // 描述弹在悬停那张卡的正上方，盖住上面那张卡（不跟鼠标跑，指针乱晃也不会跳）；
        // 弹窗本身画在悬停卡下面——底边虽然伸到卡片中线，实际只有卡片切角处露出来
        drawCardPopup(buffer, art, hovered);
        if (hovered >= 0) {
            int[] box = cardBarRect(hovered);
            boolean playable = battle != null && battle.canPlay(hovered);
            drawCardBar(buffer, art, hand.get(hovered), box[0], box[1], box[2], box[3],
                    true, playable, hovered + 1, true);
        }
    }

    /**
     * 指令卡说明：比卡片宽一圈（156px）的浮牌，横向居中压在卡片上。
     *
     * <p>最上面那一行没有"上面"可用（会顶出画布），改成往下弹；
     * 文字按浮牌宽度折行，最多 6 行，超出的截断。</p>
     */
    private void drawCardPopup(CanvasBuffer buffer, PixelArt art, int index) {
        List<Card> hand = hand();
        if (index < 0 || index >= hand.size() || index >= CARD_MAX) {
            return;
        }
        Card card = hand.get(index);
        Battle battle = run.battle();
        // 说明牌固定一个够宽的宽度（约 13 个汉字），不跟卡片同宽：
        // 卡片缩到 93 后按卡宽折行会把整句切碎、末尾全是省略号
        int w = 156;
        List<String> lines = new ArrayList<>(wrap(card.textZh(), 12));
        if (battle != null) {
            // 费用/装填/倍率压成一行，号码之间只留空格；倍数不再写 ×，直接写数字
            lines.add("费用 " + card.cost() + "  装填 " + card.reload()
                    + "  倍率 " + battle.multiplierOfCardId(card.id()));
        }
        while (lines.size() > 6) {
            lines.remove(lines.size() - 1);
        }
        // 12px 中文字形几乎没有下缘留白，行距必须给到 13，否则两行会贴在一起
        int textH = 20 + lines.size() * 13;
        int[] box = cardBarRect(index);
        int x = box[0] + (box[2] - w) / 2;
        x = Math.max(2, Math.min(640 - w - 2, x));
        // 底边压到这张卡的竖直中线（这一截藏在卡片下面），文字那部分整体留在卡片上沿之上
        int hidden = box[3] / 2;
        int h = textH + hidden;
        int cardMid = box[1] + hidden;
        int y = cardMid - h;
        boolean below = false;
        if (y < BOARD_Y) {
            // 上面放不下就翻到卡片下方：顶边落在中线，文字从卡片下沿之外开始
            below = true;
            y = cardMid;
        }
        y = Math.max(0, Math.min(360 - h, y));
        cardPopupBelow = below;
        // 四角和卡片一样切掉 45° 三角（八边形），上下都切，不再只切"露在外面"的两角
        int c = CARD_CHAMFER;
        double[] bx = {x + c, x + w - c, x + w, x + w, x + w - c, x + c, x, x};
        double[] by = {y, y, y + c, y + h - c, y + h, y + h, y + h - c, y + c};
        art.polygon(bx, by, Palette.PANEL);
        art.polygonOutline(bx, by, Palette.CYAN);
        int textTop = below ? y + hidden : y;
        zh(buffer, fit(card.nameZh(), 12, w - 10), x + 5, textTop + 4, 12, Palette.CYAN);
        int ty = textTop + 20;
        for (int i = 0; i < lines.size(); i++) {
            // 最后一行是费用/装填/倍率，压暗一档，和卡面说明分开
            boolean stat = i == lines.size() - 1;
            zh(buffer, fit(lines.get(i), 12, w - 10), x + 5, ty, 12,
                    stat ? Palette.LINE_DIM : Palette.LINE);
            ty += 13;
        }
    }

    /** 空卡槽：只描一圈暗八边形，不填底、不写字——像空着的硬件插槽。 */
    private void drawEmptyCardSlot(PixelArt art, int index) {
        int[] box = cardBarRect(index);
        int x = box[0];
        int y = box[1];
        int w = box[2];
        int h = box[3];
        int c = CARD_CHAMFER;
        double[] xs = {x + c, x + w - c, x + w, x + w, x + w - c, x + c, x, x};
        double[] ys = {y, y, y + c, y + h - c - 1, y + h - 1, y + h - 1,
                y + h - c - 1, y + c};
        art.polygonOutline(xs, ys, Palette.PANEL_EDGE);
    }

    private void drawCardBar(CanvasBuffer buffer, PixelArt art, Card card, int x, int y,
                             int w, int h, boolean hovered, boolean playable, int slot) {
        drawCardBar(buffer, art, card, x, y, w, h, hovered, playable, slot, false);
    }

    /**
     * @param popupOpen 这张卡上方正压着说明浮牌：悬停的琥珀顶线照画，画完在同位置用背景色
     *                  盖一道——只想让"信息底部的黄线"露出来，卡牌上缘不要多出一条黄线。
     */
    private void drawCardBar(CanvasBuffer buffer, PixelArt art, Card card, int x, int y,
                             int w, int h, boolean hovered, boolean playable, int slot,
                             boolean popupOpen) {
        com.hysx.salvosequence.core.Color edge = hovered ? Palette.CYAN
                : playable ? Palette.LINE_DIM : Palette.LINE_FAINT;
        // 四角各切掉腰长 CARD_CHAMFER 的三角形（八边形），左右两端再挂消耗/装填菱形
        int c = CARD_CHAMFER;
        double[] xs = {x + c, x + w - c, x + w, x + w, x + w - c, x + c, x, x};
        double[] ys = {y, y, y + c, y + h - c-1, y + h-1, y + h-1, y + h - c-1, y + c};
        // 卡片压在指令台上：先铺一块右下 2px 的投影，悬停再多 1px —— 手牌"抬起来"的手感
        int lift = hovered ? 3 : 2;
        double[] sxs = new double[xs.length];
        double[] sys = new double[ys.length];
        for (int i = 0; i < xs.length; i++) {
            sxs[i] = xs[i] + lift;
            sys[i] = ys[i] + lift;
        }
        art.polygon(sxs, sys, com.hysx.salvosequence.core.Color.rgb(0, 0, 0, 0.55));
        art.polygon(xs, ys, Palette.PANEL_ALT);
        art.polygonOutline(xs, ys, edge);
        // 内层再收一道暗线，卡片才有厚度；悬停时上缘补一条琥珀高亮
        art.polygonOutline(new double[]{x + c - 1, x + w - c + 1, x + w - 1, x + w - 1,
                x + w - c + 1, x + c - 1, x + 1, x + 1},
                new double[]{y + 1, y + 1, y + c - 1, y + h - c, y + h - 2, y + h - 2,
                        y + h - c, y + c - 1}, Palette.STEEL_FAINT);
        if (hovered) {
            art.hline(x + c, x + w - c - 1, y + 1, Palette.AMBER);
            if (popupOpen) {
                // 说明浮牌压着这张卡：同位置再描一条卡面背景色，把这条琥珀线遮掉
                art.hline(x + c, x + w - c - 1, y + 1, Palette.PANEL_ALT);
            }
        }
        if (popupOpen) {
            // 浮牌在卡片下面一层，遮不住卡片自己的边框——只能在卡片上、贴着浮牌的那条边
            // 用浮牌底色再描一条，让卡面上缘不露出第二道黄线（浮牌底边的黄线在下面，保留）
            int edgeY = cardPopupBelow ? y + h - 1 : y;
            art.hline(x + c, x + w - c, edgeY, Palette.PANEL);
        }
        drawCostDiamond(art, x + 12, y + h / 2, 6, card.cost(), playable);
        // 图标位置先用卡名顶着：图标还没设计，空着看不出这张卡是什么
        int nameW = w - 22 - 18;
        String iconText = fit(Language.t(card.nameZh(), card.nameEn()), 12, nameW);
        double textW = textWidth(iconText, 12);
        zh(buffer, iconText, (int) Math.round(x + 20 + (nameW - textW) / 2), y + h / 2 - 6,
                12, playable ? Palette.LINE : Palette.LINE_DIM);
        drawReloadDiamond(art, card, x + w - 11, y + h / 2, 6);
    }

    /**
     * 消耗菱形：外圈描边，里面每个能量一颗小菱形——"几颗菱形就是几点能量"，
     * 和右下角的能量点用同一套读数语言，扫一眼就能对账。
     */
    private void drawCostDiamond(PixelArt art, int cx, int cy, int r, int cost, boolean playable) {
        com.hysx.salvosequence.core.Color edge = playable ? Palette.WARN : Palette.LINE_FAINT;
        diamond(art, cx, cy, r, edge);
        drawDiamondPips(art, cx, cy, cost, edge);
    }

    /**
     * 把 0~4 颗小菱形摆进框里：1 颗正中、2 颗左右、3 颗三角、4 颗四向。
     * 小菱形只有 3×3，中心间距 ≥2，数量数得清，也不会顶到外框。
     */
    private void drawDiamondPips(PixelArt art, int cx, int cy, int count,
                                 com.hysx.salvosequence.core.Color color) {
        int n = Math.min(4, Math.max(0, count));
        int[][] spots = switch (n) {
            case 1 -> new int[][]{{0, 0}};
            case 2 -> new int[][]{{-3, 0}, {3, 0}};
            case 3 -> new int[][]{{0, -3}, {-3, 0}, {3, 0}};
            case 4 -> new int[][]{{0, -3},  {-3, 0}, {3, 0}, {0, 3}};
            default -> new int[0][];
        };
        for (int[] spot : spots) {
            fillDiamond(art, cx + spot[0], cy + spot[1], 3, color);
        }
    }

    /**
     * 装填菱形：和费用菱形同一套读数——几颗小菱形就是几回合装填，不再写数字。
     * 底色/描边/小菱形都按卡型分色：红=伤害、蓝=格挡/回复、琥珀=特殊。
     */
    private void drawReloadDiamond(PixelArt art, Card card, int cx, int cy, int r) {
        // 装填 0 的卡也保留这颗菱形：右端空着会被当成排版漏了一格，
        // 一律画出外框，只是里面不放小菱形——"这卡不用装填"本身也是信息
        int damage = card.baseDamage();
        int block = card.blockValue();
        boolean special = card.kind() == CardKind.SPECIAL;
        com.hysx.salvosequence.core.Color base = special ? PixelArt.fromArgb(0x66665010)
                : damage > 0 ? PixelArt.fromArgb(0x66402A26)
                : block > 0 ? PixelArt.fromArgb(0x66202E4A)
                : Palette.PANEL_ALT;
        com.hysx.salvosequence.core.Color edge = special ? Palette.WARN
                : damage > 0 ? Palette.LOCK
                : block > 0 ? Palette.BLUE
                : Palette.LINE_DIM;
        fillDiamond(art, cx, cy, r, base);
        diamond(art, cx, cy, r, edge);
        drawDiamondPips(art, cx, cy, card.reload(), edge);
    }

    /**
     * 卡片图标：攻击=光矛，格挡=盾，特殊=十字星，其余=方点。
     *
     * <p>暂时没有启用——卡面中间那一格现在直接写卡名（见 {@code drawCardBar}）。
     * 真正的图标画出来后，把那一行文字换回这里的调用即可。</p>
     */
    private void drawCardIcon(PixelArt art, Card card, int x, int cy, int w, int h,
                              boolean playable) {
        int ix = x + (w - 13) / 2;
        int iy = cy - 7;
        drawCardIcon(art, card, ix, iy, playable);
    }

    private void drawCardIcon(PixelArt art, Card card, int x, int y, boolean playable) {
        com.hysx.salvosequence.core.Color c = playable ? Palette.LINE : Palette.LINE_FAINT;
        if (card.isAttack()) {
            art.line(x, y + 7, x + 12, y + 7, c);
            art.line(x + 12, y + 7, x + 9, y + 4, c);
            art.line(x + 12, y + 7, x + 9, y + 10, c);
            art.line(x, y + 7, x + 3, y + 4, Palette.LINE_FAINT);
            art.line(x, y + 7, x + 3, y + 10, Palette.LINE_FAINT);
        } else if (card.blockValue() > 0) {
            squareIcon(art, x + 3, y + 2, 7, c);
            art.line(x + 3, y + 9, x + 9, y + 9, Palette.LINE_FAINT);
        } else if (card.kind() == CardKind.SPECIAL) {
            art.line(x + 6, y + 1, x + 6, y + 13, Palette.WARN);
            art.line(x + 1, y + 7, x + 11, y + 7, Palette.WARN);
        } else {
            squareIcon(art, x + 4, y + 5, 5, c);
        }
    }

    private void squareIcon(PixelArt art, int x, int y, int s,
                            com.hysx.salvosequence.core.Color c) {
        art.line(x, y, x + s, y, c);
        art.line(x + s, y, x + s, y + s, c);
        art.line(x + s, y + s, x, y + s, c);
        art.line(x, y + s, x, y, c);
    }


    /** 把文字裁到 maxW 宽，末尾加省略号（信息框/长条按钮里用）。 */
    private String fit(String text, double size, double maxW) {
        if (text == null) {
            return "";
        }
        if (textWidth(text, size) <= maxW) {
            return text;
        }
        StringBuilder b = new StringBuilder();
        double w = 0;
        for (int i = 0; i < text.length(); i++) {
            double cw = text.charAt(i) < 0x2E80 ? size * 0.56 : size;
            if (w + cw > maxW - size) {
                break;
            }
            b.append(text.charAt(i));
            w += cw;
        }
        return b + "…";
    }
    /** 第 i 张指令卡的长条位置（3 列 × 3 行）。 */
    /**
     * 第 index 张手牌的矩形。绘制和点击热区都走这里，所以两者永远一致。
     *
     * <p>优先读界面绘制器导出的 {@code .uilab}（启动参数 {@code -Dsalvo.ui}）：
     * 文件里 {@code card.slot.N} 有就用手摆的位置，没有就退回代码里的
     * {@link #CARD_SLOTS}。这样"用编辑器挪一挪"不需要改代码重编译。</p>
     */
    private int[] cardBarRect(int index) {
        if (index >= 0) {
            // 第一优先：Aseprite 切出来的界面数据（-Dsalvo.ase），图层/切片名就是元素 id
            com.hysx.salvosequence.core.AseUi ase = com.hysx.salvosequence.core.AseUi.active();
            int[] fromAse = ase == null ? null : ase.slice("card.slot." + index);
            if (fromAse != null && fromAse[2] > 0 && fromAse[3] > 0) {
                return fromAse;
            }
            int[] fromFile = com.hysx.salvosequence.core.UiLayout.active()
                    .rect("card.slot." + index);
            if (fromFile != null && fromFile[2] > 0 && fromFile[3] > 0) {
                return fromFile;
            }
        }
        if (index < 0 || index >= CARD_SLOTS.length) {
            return new int[]{CARD_X, CARD_Y, CARD_W, CARD_H};
        }
        return new int[]{CARD_SLOTS[index][0], CARD_SLOTS[index][1], CARD_W, CARD_H};
    }

    private void drawHand(CanvasBuffer buffer, PixelArt art) {
        List<Card> hand = hand();
        Battle battle = run.battle();
        for (int i = 0; i < Math.min(CARD_MAX, hand.size()); i++) {
            Card card = hand.get(i);
            int[] box = cardRect(i);
            drawCardSquare(buffer, art, card, box[0], box[1], box[2],
                    i == hoveredOption,
                    battle != null ? battle.multiplierOfCard(i) : 1, i + 1,
                    battle != null && battle.canPlay(i));
        }
        if (hand.isEmpty() && battle != null) {
            zh(buffer, Language.t("手牌空了 —— 空格结束回合", "HAND EMPTY - SPACE ENDS TURN"),
                    CARD_X + 4, CARD_Y + 4, 12, Palette.LINE_FAINT);
        }
        int rowY = CARD_Y + CARD_H + 4;
        zh(buffer, Language.t(
                        "牌库 " + battle.drawPile().size()
                                + "  弃牌 " + battle.discard().size()
                                + "  装填 " + battle.loadingZone().size()
                                + "  已出 " + playedThisTurn(),
                        "DECK " + battle.drawPile().size()
                                + "  DISC " + battle.discard().size()
                                + "  LOAD " + battle.loadingZone().size()
                                + "  PLAYED " + playedThisTurn()),
                CARD_X, rowY + 2, 12, Palette.LINE_FAINT);
    }

    private int playedThisTurn() {
        Battle battle = run.battle();
        return battle == null ? 0 : battle.handSizeLimit() - battle.hand().size();
    }

    private void drawCardSquare(CanvasBuffer buffer, PixelArt art, Card card, int x, int y,
                                int size, boolean hovered, int multiplier, int slot,
                                boolean playable) {
        // 手牌：悬浮换成琥珀高亮板，平时是普通切角板
        if (hovered) {
            UiChrome.hotPanel(art, x, y, size, size);
        } else {
            UiChrome.panel(art, x, y, size, size, Palette.PANEL_ALT,
                    playable ? Palette.STEEL : Palette.STEEL_DIM, false);
        }
        // 费用徽标
        art.rect(x + 1, y + 1, 9, 9, playable ? Palette.AMBER : Palette.PANEL_EDGE);
        en(art, String.valueOf(card.cost()), x + 3, y + 2, playable ? Palette.BACKDROP : Palette.LINE_FAINT);
        // 名字（中文 3 字一行 / 英文 5 字一行，最多两行；徽标右侧只有 32 点宽）
        List<String> lines = wrap(Language.t(card.nameZh(), card.nameEn()),
                Language.isEnglish() ? 5 : 3);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            zh(buffer, lines.get(i), x + 11, y + 2 + i * 9, 12,
                    playable ? Palette.LINE : Palette.LINE_FAINT);
        }
        // 摘要
        String summary = cardSummary(card, multiplier);
        zh(buffer, summary, x + 2, y + size - 14, 12, Palette.CYAN_DIM);
        // 右下角：装填牌写 R{n}，普通牌写格号（都往右对齐，别压名字）
        if (card.reload() > 0) {
            en(art, "R" + card.reload(), x + size - 11, y + size - 11, Palette.WARN);
        } else {
            en(art, String.valueOf(slot), x + size - 6, y + size - 11, Palette.LINE_FAINT);
        }
    }

    private String cardSummary(Card card, int multiplier) {
        int damage = card.baseDamage();
        int block = card.blockValue();
        StringBuilder sb = new StringBuilder();
        if (damage > 0) {
            sb.append(Language.t("攻", "A")).append(damage * Math.max(1, multiplier));
        } else if (block > 0) {
            sb.append(Language.t("防", "B")).append(block * Math.max(1, multiplier));
        } else {
            sb.append(Language.t("辅助", "UTIL"));
        }
        return sb.toString();
    }

    /**
     * 悬浮描述弹窗：悬浮手牌/敌舰/我方船时弹出，无悬浮时收回。
     * 位置跟鼠标半屏换边（左半→右侧弹出，右半→左侧弹出），锚点贴鼠标 ±14px；
     * 垂直先贴鼠标上方，顶到棋盘上缘就挪到下方。
     */
    private void drawSidePanel(CanvasBuffer buffer, PixelArt art) {
        // 正在拖棋子变阵：这一下不弹描述。弹窗跟着指针走，正好盖住要摆的那几格，
        // 松手（dragShipId 清空）后自然恢复。
        if (dragShipId != null) {
            return;
        }
        // 悬浮手牌的说明改由 drawCardPopup 贴着卡片画（见 drawHandBars），这里不再跟鼠标弹
        // 2) 悬浮的敌舰
        List<FoeView> foes = foeViews();
        if (hoveredFoe >= 0 && hoveredFoe < foes.size()) {
            FoeView foe = foes.get(hoveredFoe);
            // 与玩家舰船同一套字段/顺序：结构/护盾/齐射、防御/防空、卡、占地位置、光环、说明
            List<String> lines = combatStatLines(foe.hp(), foe.maxHp(), foe.block(),
                    foe.unit().volley(), foe.plate(), foe.flak(),
                    foe.unit().w(), foe.unit().h(), foe.unit().x(), foe.unit().y(),
                    foe.ship().cardIds(), foe.ship().perks(), foe.ship().textZh());
            // 敌我同款：描述跟指针走，半屏换边
            renderDesc(buffer, art, foe.nameZh(), Palette.FOE_GLOW, lines);
            return;
        }
        // 3) 悬浮的我方船
        List<OurView> ours = ourViews();
        Fleet fleet = run.fleet();
        if (hoveredOurShip >= 0 && hoveredOurShip < ours.size() && fleet != null) {
            OurView view = ours.get(hoveredOurShip);
            FleetUnit unit = view.unit();
            List<String> lines = combatStatLines(view.hp(), view.maxHp(), view.block(),
                    fleet.volleyOf(unit), fleet.plateOf(unit), fleet.flakOf(unit),
                    unit.w(), unit.h(), unit.x(), unit.y(),
                    unit.ship().cardIds(), unit.ship().perks(), unit.ship().textZh());
            renderDesc(buffer, art, unitLabel(unit), Palette.CYAN, lines);
        }
        // 无悬浮 → 弹窗收回（什么都不画）
    }

    /** 弹窗渲染：左半鼠标向右弹、右半向左弹，都贴鼠标 ±14px；框先清掉底下的字再画。 */
    private void renderDesc(CanvasBuffer buffer, PixelArt art, String title,
                            com.hysx.salvosequence.core.Color titleColor, List<String> lines) {
        int h = 18 + lines.size() * 14;
        int x;
        if (mouseX < 320) {
            x = Math.max(2, Math.min(636 - DESC_W, (int) mouseX + 14));
        } else {
            x = Math.max(2, Math.min(636 - DESC_W, (int) mouseX - 14 - DESC_W));
        }
        int y = (int) mouseY - h - 8;
        if (y < BOARD_Y) {
            y = (int) mouseY + 16;
        }
        y = Math.max(BOARD_Y, Math.min(358 - h, y));
        // 浮窗贴在最上面一层：投影 + 左上亮边，跟其他面板同一套体积语言
        UiChrome.dropShadow(art, x, y, DESC_W, h, 3, UiChrome.LIFT_POPUP);
        art.fillRect(x, y, DESC_W, h, Palette.PANEL);
        art.frame(x, y, DESC_W, h, Palette.LINE_DIM);
        art.hline(x + 1, x + DESC_W - 2, y, Palette.STEEL);
        art.vline(y + 1, y + h - 2, x, Palette.STEEL);
        zh(buffer, title, x + 6, y + 4, 12, titleColor);
        int ty = y + 20;
        for (String line : lines) {
            zh(buffer, fitLine(line, DESC_W - 12), x + 6, ty, 12, Palette.LINE);
            ty += 14;
        }
    }

    /** 一行装不下就截断加省略号——描述里有整句台词，不能让它压到框外去。 */
    private String fitLine(String line, double maxWidth) {
        if (textWidth(line, 12) <= maxWidth) {
            return line;
        }
        int cut = line.length();
        while (cut > 1 && textWidth(line.substring(0, cut) + "…", 12) > maxWidth) {
            cut--;
        }
        return line.substring(0, cut) + "…";
    }

    // ------------------------------------------------------------ 战术休整（波间）

    private static final int LULL_ROW_H = 14;
    private static final int LULL_ROWS = 11;
    private static final int LULL_COLS = 3;
    /** 一屏能摆下的牌库张数（3 列 × 11 行）。 */
    private static final int LULL_CAP = LULL_ROWS * LULL_COLS;

    /**
     * 牌库第 {@code index} 张在面板里的位置：3 列，列内从上往下排。
     *
     * <p>绘制和热区都用这一个函数——以前两边各写一份 y 起点（62 和 78），
     * 于是点到的行和看到的行差着一整行。</p>
     */
    private int[] lullCardRect(int index) {
        int colW = (PORT_PANEL_W - 10) / LULL_COLS;
        // 逐行填：先从左到右占满一行，再下一行——这样十几张牌也能摊成三列，
        // 而不是把第一列塞满、右边两列空着
        int col = index % LULL_COLS;
        int row = index / LULL_COLS;
        return new int[]{PORT_PANEL_X + 4 + col * colW, BOARD_Y + 56 + row * LULL_ROW_H,
                colW - 2, LULL_ROW_H - 2};
    }

    /**
     * 战术休整面板：不另开页面，直接占右栏——原来敌方棋盘的位置空出来了，
     * 和太空港的右栏panel一个套路。左边棋盘照旧拖船变阵（见 pressOurBoard），
     * 这里只放"禁牌"和"出发下一波"。
     */
    private void drawLullPanel(CanvasBuffer buffer, PixelArt art) {
        int px = PORT_PANEL_X;
        int pw = PORT_PANEL_W;
        art.rect(px, BOARD_Y, pw, BOARD_SPAN, Palette.PANEL);
        art.frame(px, BOARD_Y, pw, BOARD_SPAN, Palette.LINE_DIM);
        zh(buffer, Language.t("战术休整", "TACTICAL LULL"), px + 6, BOARD_Y + 4, 12, Palette.CYAN);
        zh(buffer, Language.t("第 " + run.wave() + "/" + run.waveCount() + " 波已清空",
                        "WAVE " + run.wave() + "/" + run.waveCount() + " CLEAR"),
                px + 108, BOARD_Y + 5, 12, Palette.WARN);
        zh(buffer, fit(Language.t("拖左边的船变阵（和太空港一样）",
                        "DRAG SHIPS ON THE LEFT BOARD"), 12, pw - 12),
                px + 6, BOARD_Y + 22, 12, Palette.LINE);
        zh(buffer, fit(Language.t("最靠右的先挨打；点一行牌库禁掉，本节点不抽它",
                        "RIGHTMOST TAKES HITS; TAP A CARD TO BAN IT"), 12, pw - 12),
                px + 6, BOARD_Y + 38, 12, Palette.LINE_FAINT);

        var deck = restDeck();
        for (int i = 0; i < deck.size() && i < LULL_CAP; i++) {
            var card = deck.get(i);
            int[] box = lullCardRect(i);
            boolean on = i == lullCursor;
            boolean banned = run.isCardBanned(card.id());
            if (on) {
                art.fillRect(box[0], box[1], box[2], box[3] + 1, Palette.PANEL_ALT);
            }
            zh(buffer, fit(card.nameZh(), 12, box[2] - (banned ? 16 : 6)),
                    box[0] + 3, box[1] + 1, 12,
                    banned ? Palette.LINE_FAINT : (on ? Palette.CYAN : Palette.LINE));
            if (banned) {
                zh(buffer, Language.t("禁", "BAN"), box[0] + box[2] - 14, box[1] + 1, 12, Palette.WARN);
            }
        }
        if (deck.size() > LULL_CAP) {
            zh(buffer, Language.t("… 还有 " + (deck.size() - LULL_CAP) + " 张",
                            "+" + (deck.size() - LULL_CAP) + " MORE"),
                    px + 8, BOARD_Y + 56 + LULL_ROWS * LULL_ROW_H, 12, Palette.LINE_FAINT);
        }

        int departY = BOARD_Y + BOARD_SPAN - 22;
        art.rect(px + 6, departY, pw - 16, 16, Palette.OK);
        zh(buffer, Language.t("出发：下一波 (空格)", "NEXT WAVE (SPACE)"),
                px + 10, departY + 3, 12, Palette.BACKDROP);
    }

    private ScreenContext lullKey(String code) {
        var deck = restDeck();
        switch (code) {
            case "UP" -> lullCursor = Math.max(0, lullCursor - LULL_COLS);
            case "DOWN" -> lullCursor = Math.min(Math.max(0, deck.size() - 1), lullCursor + LULL_COLS);
            case "LEFT" -> lullCursor = Math.max(0, lullCursor - 1);
            case "RIGHT" -> lullCursor = Math.min(Math.max(0, deck.size() - 1), lullCursor + 1);
            case "ENTER" -> toggleRestCard();
            case "SPACE" -> departNextWave();
            default -> {
                return null;
            }
        }
        touch();
        return null;
    }

    /** 牌库里出现的牌（按 id 去重，顺序稳定）。 */
    private List<com.hysx.salvosequence.deck.Card> restDeck() {
        if (run.fleet() == null) {
            return java.util.List.of();
        }
        java.util.LinkedHashMap<String, com.hysx.salvosequence.deck.Card> byId =
                new java.util.LinkedHashMap<>();
        for (var card : run.fleet().deck()) {
            byId.putIfAbsent(card.id(), card);
        }
        return java.util.List.copyOf(byId.values());
    }

    private void toggleRestCard() {
        var deck = restDeck();
        if (deck.isEmpty() || lullCursor >= deck.size()) {
            return;
        }
        var card = deck.get(lullCursor);
        if (run.toggleBannedCard(card.id())) {
            say(run.isCardBanned(card.id())
                    ? "本节点不再抽到「" + card.nameZh() + "」"
                    : "「" + card.nameZh() + "」回到牌库");
            touch();
        }
    }

    private void departNextWave() {
        run.startNextWave();
        lullCursor = 0;
        touch();
    }

    private void collectLullHotspots(HotspotSink sink) {
        int px = PORT_PANEL_X;
        int pw = PORT_PANEL_W;
        var deck = restDeck();
        for (int i = 0; i < deck.size() && i < LULL_CAP; i++) {
            int[] box = lullCardRect(i);
            int row = i;
            sink.add(box[0], box[1], box[2], box[3] + 1, () -> {
                lullCursor = row;
                toggleRestCard();
            });
        }
        sink.add(px + 6, BOARD_Y + BOARD_SPAN - 22, pw - 16, 16, this::departNextWave);
    }

    // ---------------------------------------------------------------- 覆盖层

    private void drawOverlay(CanvasBuffer buffer, PixelArt art) {
        UiChrome.scrim(art, 0, 0, 640, 360);
        String title = switch (stage()) {
            case PICK_LOADOUT -> Language.t("选择主角：每人一套起始舰队",
                    "CHOOSE CAPTAIN: EACH WITH A STARTER FLEET");
            case SPACE_PORT -> Language.t("太空港", "SPACE PORT");
            case EVENT -> Language.t("事件", "EVENT");
            case MAP -> Language.t("星图", "STAR MAP");
            case LULL -> Language.t("战术休整", "TACTICAL LULL");
            case REWARD -> Language.t("战利品：一艘船 / 一条强化 / 一张卡",
                    "REWARD: A SHIP / AN UPGRADE / A CARD");
            case SLOT -> Language.t("编入「" + run.pending().ship().nameZh() + "」：挑个位置",
                    "ADD \"" + run.pending().ship().nameEn() + "\": PICK A SPOT");
            case WON -> Language.t("通关：航程走完，舰队活下来了",
                    "VICTORY: ROUTE CLEARED, FLEET SURVIVED");
            case LOST -> Language.t("舰队全灭", "FLEET DESTROYED");
            case BATTLE -> "";
        };
        // 整块选项区装进一个切角视窗：羊皮纸牌标功能名，后面跟一句正文说明
        List<int[]> boxes = optionRects();
        int bx0 = OV_X;
        int bx1 = OV_X + OV_W;
        int by0 = OV_Y;
        int by1 = OV_Y + OV_ROW_H;
        if (!boxes.isEmpty()) {
            bx0 = 640;
            bx1 = 0;
            by0 = 360;
            by1 = 0;
            for (int[] b : boxes) {
                bx0 = Math.min(bx0, b[0]);
                by0 = Math.min(by0, b[1]);
                bx1 = Math.max(bx1, b[0] + b[2]);
                by1 = Math.max(by1, b[1] + b[3]);
            }
        }
        int px = Math.max(4, bx0 - 14);
        int pw = Math.min(632 - px, bx1 - px + 14);
        int py = Math.max(4, by0 - 36);
        int ph = Math.min(356 - py, by1 - py + 14);
        UiChrome.panel(art, px, py, pw, ph);
        String plateText = switch (stage()) {
            case PICK_LOADOUT -> Language.t("选择主角", "CAPTAIN");
            case REWARD -> Language.t("战利品", "SALVAGE");
            case SLOT -> Language.t("编入船位", "DEPLOY");
            case WON -> Language.t("通关", "VICTORY");
            case LOST -> Language.t("全灭", "DEFEAT");
            default -> Language.t("星图", "STAR MAP");
        };
        int plateW = (int) Math.ceil(UiChrome.textWidth(plateText, 12)) + 18;
        UiChrome.plate(buffer, art, px + 10, py + 6, plateW, 17, plateText);
        String caption = title.contains("：") ? title.substring(title.indexOf("：") + 1) : "";
        if (!caption.isEmpty()) {
            UiChrome.zh(buffer, caption, px + 10 + plateW + 8, py + 8, 12, Palette.LINE_DIM);
        }
        UiChrome.divider(art, px + 8, py + 27, pw - 16);

        for (int i = 0; i < boxes.size(); i++) {
            int[] box = boxes.get(i);
            boolean hovered = i == hoveredOption;
            if (hovered) {
                UiChrome.hotPanel(art, box[0], box[1], box[2], box[3]);
            } else {
                UiChrome.chamferFill(art, box[0], box[1], box[2], box[3], 3, Palette.BACKDROP);
                UiChrome.chamferFrame(art, box[0], box[1], box[2], box[3], 3, Palette.STEEL_DIM);
                UiChrome.brackets(art, box[0], box[1], box[2], box[3], 1, Palette.STEEL_FAINT, 4);
            }
            UiChrome.latin(art, String.format("%02d", i + 1),
                    box[0] + box[2] - 16, box[1] + 3,
                    hovered ? Palette.INK : Palette.LINE_FAINT);
            drawOption(buffer, art, i, box[0], box[1], box[2], box[3]);
        }
        if (stage() == Run.Stage.SLOT) {
        }
        if (stage() == Run.Stage.REWARD || stage() == Run.Stage.SLOT) {
            int[] skip = skipRect();
            button(art, buffer, skip[0], skip[1], skip[2], skip[3],
                    Language.t("放弃 (T)", "SKIP (T)"), Palette.DANGER);
        }
        if (stage() == Run.Stage.WON || stage() == Run.Stage.LOST) {
            button(art, buffer, OV_X, OV_Y + OV_ROW_H + 12, OV_W, OV_ROW_H,
                    Language.t("再来一局 (空格)", "PLAY AGAIN (SPACE)"), Palette.OK);
        }
    }

    private void drawOption(CanvasBuffer buffer, PixelArt art, int index, int x, int y, int w, int h) {
        switch (stage()) {
            case PICK_LOADOUT -> {
                var starter = run.starters().get(index);
                Fleet fleet = starter.fleet();
                String head = Language.t(starter.nameZh(), starter.nameEn());
                zh(buffer, head, x + 8, y + 4, 12, Palette.LINE);
                double hx = x + 8 + textWidth(head, 12) + 10;
                String countText = Language.t(fleet.size() + " 艘", fleet.size() + " SHIPS");
                zh(buffer, countText, (int) hx, y + 4, 12, Palette.LINE_FAINT);
                String desc = starter.desc() == null ? "" : starter.desc();
                // 说明只留一行（12px 光栅下两行会压到下面的读数），读数单独一行
                zh(buffer, fitted(desc, w - 18), x + 8, y + 21, 12, Palette.LINE_DIM);
                String stats = Language.t(
                        "结构 " + fleet.maxHp() + " 护甲 " + fleet.armor()
                                + " 能量 " + fleet.maxEnergy() + " 牌组 " + fleet.deckSize(),
                        "HP " + fleet.maxHp() + " AR " + fleet.armor()
                                + " EN " + fleet.maxEnergy() + " DECK " + fleet.deckSize());
                zh(buffer, stats, x + 8, y + 37, 12, Palette.OK);
            }
            case REWARD -> {
                Reward reward = run.rewards().get(index);
                // 左边一列缩略图（2 格 = 36 点），文字一律从 x+48 起，别压到图上
                if (reward.ship() != null) {
                    ShipPainter.drawIcon(art, reward.ship(), x + 6, y + 4, 2, time);
                } else {
                    UiChrome.chamferFill(art, x + 6, y + 4, 36, 20, 3, Palette.PANEL_ALT);
                    UiChrome.chamferFrame(art, x + 6, y + 4, 36, 20, 3, Palette.AMBER_DIM);
                    zh(buffer, Language.t("卡牌", "CARD"), x + 10, y + 8, 12, Palette.AMBER);
                }
                int textX = x + 48;
                String rewardTitle = Language.t(reward.titleZh(), reward.subtitleEn());
                zh(buffer, rewardTitle, textX, y + 3, 12, Palette.LINE);
                // 读数在第二行的右端，描述占满整行宽度（别再给它让位，中文一句话会被截断）
                int statW = 0;
                zh(buffer, fitted(reward.textZh(), x + w - 10 - textX - statW), textX, y + 17,
                        12, Palette.LINE_DIM);
                if (reward.ship() != null) {
                    String stat = Language.t(reward.w() + "×" + reward.h()
                                    + "  结构 " + reward.ship().hp(),
                            reward.w() + "x" + reward.h() + "  HP " + reward.ship().hp());
                    zh(buffer, stat, x + w - 10 - (int) Math.round(textWidth(stat, 12)),
                            y + 4, 12, Palette.LINE_FAINT);
                }
            }
            case SLOT -> {
                List<FleetUnit> spots = candidates();
                FleetUnit spot = spots.get(index);
                zh(buffer, spot.x() + "/" + spot.y(), x + 8, y + 4, 12, Palette.LINE);
                zh(buffer, Language.t(spot.w() + "×" + spot.h(), spot.w() + "x" + spot.h()),
                        x + 64, y + 4, 12, Palette.CYAN_DIM);
                zh(buffer, Language.t("空位", "EMPTY"), x + 116, y + 4, 12, Palette.LINE_DIM);
                en(art, index == slotPreview ? "<--" : "", x + w - 30, y + 5,
                        index == slotPreview ? Palette.AMBER : Palette.LINE_FAINT);
            }
            default -> {
            }
        }
    }

    private void drawZoomShip(CanvasBuffer buffer, PixelArt art) {
        art.fillRect(0, 0, 640, 360, Palette.SPACE_DEEP);
        int cell = Math.max(6, Math.min(18, 300 / FleetBoard.SIZE));
        int span = cell * FleetBoard.SIZE;
        int ox = (640 - span) / 2;
        int oy = (360 - span) / 2 - 6;
        art.rect(ox - 4, oy - 4, span + 8, span + 8, Palette.PANEL);
        art.frame(ox - 4, oy - 4, span + 8, span + 8, Palette.LINE_DIM);
        for (OurView view : ourViews()) {
            FleetUnit unit = view.unit();
            int x = ox + unit.x() * cell;
            int y = oy + unit.y() * cell;
            drawShip(art, unit.ship(), x, y, unit.w(), unit.h(), view.alive(), false, time);
            // 每艘船描一圈：放大看时涂装糊在一起，靠边框才分得出谁是谁
            art.frame(x - 1, y - 1, unit.w() * cell + 1, unit.h() * cell + 1,
                    view.alive() ? Palette.PANEL_EDGE : Palette.LINE_FAINT);
            if (view.alive()) {
                bar(art, x + 1, y + unit.h() * cell - 3, unit.w() * cell - 2, 2,
                        ratio(view.hp(), view.maxHp()), hpColor(view.hp(), view.maxHp()));
            }
        }
        zh(buffer, Language.t("我方舰队", "OUR FLEET"), 8, 8, 12, Palette.CYAN);
        zh(buffer, Language.t("结构 " + run.hp() + "/" + run.maxHp() + " · 船 "
                        + run.fleet().size() + " 艘",
                "HULL " + run.hp() + "/" + run.maxHp() + " · SHIPS "
                        + run.fleet().size()), 8, 24, 12, Palette.LINE_DIM);
        zh(buffer, Language.t("Z 关闭", "Z CLOSE"), 8, 336, 12, Palette.LINE_DIM);
    }

    // ---------------------------------------------------------------- 底栏与小件

    private void drawFooter(CanvasBuffer buffer) {
        String hint;
        if (zoomShip) {
            hint = Language.t("Z 返回战斗视图 · 放大视图只读",
                    "Z BACK TO BATTLE · READ ONLY");
        } else if (stage() == Run.Stage.MAP) {
            // 星图自己的底部通栏已经写了操作提示，这里不再叠一行
            return;
        } else if (stage() == Run.Stage.BATTLE) {
            hint = Language.t(
                    "1-6 / 点卡 出牌 · 空格 结束回合 · S 技能 · ←→ 换目标 · 拖船变阵 · G 火力 · Z 放大 · ESC 设置",
                    "1-6 / CLICK CARD · SPACE END TURN · S SKILL · ←→ TARGET · G FIRE · Z ZOOM · ESC SETTINGS");
        } else if (stage() == Run.Stage.LULL) {
            hint = Language.t("拖左边的船变阵 · 点牌禁掉（本节点） · 空格 出发下一波 · ESC 设置",
                    "DRAG SHIPS · CLICK A CARD TO BAN · SPACE NEXT WAVE · ESC SETTINGS");
        } else if (stage() == Run.Stage.PICK_LOADOUT) {
            hint = Language.t("1-4 选主角 · 点一行也行 · Z 看棋盘",
                    "1-4 PICK CAPTAIN · CLICK A ROW · Z BOARD");
        } else if (stage() == Run.Stage.REWARD) {
            hint = Language.t("1-4 挑战利品 · 点一行也行 · T 放弃",
                    "1-4 PICK REWARD · CLICK A ROW · T SKIP");
        } else if (stage() == Run.Stage.SLOT) {
            hint = Language.t("点空格落位 · 拖船变阵腾地方 · 方向键挑候选 · 空格确认 · T 放弃",
                    "CLICK A SPOT · ARROWS CHOOSE · SPACE CONFIRM · T SKIP");
        } else {
            hint = Language.t("空格 / R 再来一局 · ESC 回主菜单",
                    "SPACE / R RESTART · ESC MAIN MENU");
        }
        zh(buffer, hint, 64, 344, 12, Palette.LINE_FAINT);
        if (!debug) {
            return;
        }
        Battle battle = run.battle();
        if (battle != null) {
            zh(buffer, Language.t("G:自由 F:", "G:FREE F:") + foesAlive(),
                    500, 344, 12, Palette.LINE_FAINT);
        }
    }

    private PixelArt art0(CanvasBuffer buffer) {
        return buffer.art();
    }

    private int foesAlive() {
        int alive = 0;
        for (FoeView foe : foeViews()) {
            if (foe.alive()) {
                alive++;
            }
        }
        return alive;
    }

    private void drawToast(CanvasBuffer buffer) {
        if (toastLife <= 0 || toast.isEmpty()) {
            return;
        }
        int w = (int) textWidth(toast, 12) + 12;
        int x = (640 - w) / 2;
        PixelArt art = buffer.art();
        art.rect(x, 180, w, 16, Palette.BACKDROP);
        art.frame(x, 180, w, 16, Palette.CYAN_DIM);
        zh(buffer, toast, x + 6, 182, 12, Palette.CYAN);
    }

    private void zh(CanvasBuffer buffer, String text, double x, double top, double size,
                    com.hysx.salvosequence.core.Color color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        buffer.text(text, x, top + size * 0.92, size, color);
    }

    private void en(PixelArt art, String text, int x, int y, com.hysx.salvosequence.core.Color color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        font.draw(art, text, x, y, color);
    }

    /** 舰队单位显示名：中文「名 级n」/ 英文「Name Lvn」。 */
    private static String unitLabel(FleetUnit unit) {
        return Language.t(unit.nameZh() + " 级" + unit.level(),
                unit.ship().nameEn() + " Lv" + unit.level());
    }

    private double textWidth(String text, double size) {
        double w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += text.charAt(i) < 0x2E80 ? size * 0.56 : size;
        }
        return w;
    }

    private void bar(PixelArt art, int x, int y, int w, int h, double ratio,
                     com.hysx.salvosequence.core.Color color) {
        art.rect(x, y, w, h, Palette.PANEL_EDGE);
        int fill = (int) Math.round(Math.max(0, Math.min(1, ratio)) * w);
        if (fill > 0) {
            art.rect(x, y, fill, h, color);
        }
    }

    private static double ratio(int value, int max) {
        if (max <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, (double) value / max));
    }

    private com.hysx.salvosequence.core.Color hpColor(int hp, int max) {
        double r = ratio(hp, max);
        return r > 0.5 ? Palette.OK : r > 0.25 ? Palette.WARN : Palette.DANGER;
    }

    private void button(PixelArt art, CanvasBuffer buffer, int x, int y, int w, int h,
                        String label, com.hysx.salvosequence.core.Color color) {
        double[] bx = {x, x + w, x + w - 6, x - 6};
        double[] by = {y, y, y + h, y + h};
        art.polygon(bx, by, Palette.PANEL_ALT);
        art.polygonOutline(bx, by, color);
        art.polygonOutline(new double[]{x + 1, x + w - 1, x + w - 7, x - 5},
                new double[]{y + 1, y + 1, y + h - 1, y + h - 1}, Palette.STEEL_FAINT);
        zh(buffer, label, x + 9, y + 3, 12, color);
    }

    private List<String> wrap(String text, int perLine) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            line.append(text.charAt(i));
            if (line.length() >= perLine) {
                out.add(line.toString());
                line.setLength(0);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    private String fitted(String text, double maxWidth) {
        if (text == null) {
            return "";
        }
        if (textWidth(text, 12) <= maxWidth) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        double w = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            double cw = c < 0x2E80 ? 5.6 : 10;
            if (w + cw > maxWidth - 10) {
                break;
            }
            sb.append(c);
            w += cw;
        }
        return sb.append("…").toString();
    }

    // ---------------------------------------------------------------- 核对/测试钩子

    /** 手牌。 */
    public List<Card> debugHand() {
        return hand();
    }

    /** 直接把局推进到某个阶段。 */
    public void debugAdvanceTo(Run.Stage target) {
        int guard = 0;
        while (run.stage() != target && guard++ < 4000) {
            switch (run.stage()) {
                case PICK_LOADOUT -> run.pickLoadout(0);
                case BATTLE -> {
                    // 能出牌就出：只结束回合会被对面打死，走不到战利品/编入
                    if (!playSomething()) {
                        run.endTurn();
                    }
                }
                case REWARD -> {
                    if (!run.rewards().isEmpty()) {
                        run.chooseReward(0);
                    } else {
                        run.skipReward();
                    }
                }
                case SLOT -> run.placeAt(0);
                case WON, LOST -> {
                    return;
                }
            }
        }
    }

    /** 快进到第 node 关。 */
    public void debugFastForward(int targetNode) {
        int guard = 0;
        while (run.node() < targetNode && guard++ < 4000 && !run.finished()) {
            switch (run.stage()) {
                case PICK_LOADOUT -> run.pickLoadout(0);
                case BATTLE -> {
                    // 能出牌就出：只结束回合会被对面打死，走不到战利品/编入
                    if (!playSomething()) {
                        run.endTurn();
                    }
                }
                case REWARD -> {
                    if (!run.rewards().isEmpty()) {
                        run.chooseReward(0);
                    } else {
                        run.skipReward();
                    }
                }
                case SLOT -> run.placeAt(0);
                case WON, LOST -> {
                    return;
                }
                default -> {
                }
            }
        }
    }

    /**
     * 调试：从星图跃迁到第一颗能打的星，把战斗盘面直接摆出来。
     *
     * <p>核对稿件要的是"真正的战斗界面"——{@link #debugFastForward(int)} 停在星图上，
     * 盘面（顶栏/左面板/手牌/信息框）根本没被渲染过。</p>
     */
    public void debugJumpToBattle() {
        int guard = 0;
        while (run.stage() != Run.Stage.BATTLE && guard++ < 60 && !run.finished()) {
            if (run.stage() == Run.Stage.PICK_LOADOUT) {
                run.pickLoadout(0);
                continue;
            }
            if (run.stage() != Run.Stage.MAP) {
                // 跳到事件/休息/商店就退出来，别再往下推
                return;
            }
            boolean jumped = false;
            for (var st : run.reachableStars()) {
                if (st.type() == Run.NodeType.BATTLE || st.type() == Run.NodeType.ELITE) {
                    if (run.travelTo(st.id())) {
                        jumped = true;
                        break;
                    }
                }
            }
            if (!jumped) {
                for (var st : run.reachableStars()) {
                    if (run.travelTo(st.id())) {
                        break;
                    }
                }
            }
        }
    }

    /**
     * 随便打一张打得出去的牌（优先伤害最高的）。
     *
     * <p>调试/核对用的"机器人"：一张都打不起就返回 {@code false}，让调用方去结束回合。</p>
     */
    private boolean playSomething() {
        Battle battle = run.battle();
        if (battle == null) {
            return false;
        }
        List<Card> hand = battle.hand();
        int best = -1;
        int bestScore = -1;
        for (int i = 0; i < hand.size(); i++) {
            if (!battle.canPlay(i)) {
                continue;
            }
            Card card = hand.get(i);
            int score = card.isAttack() ? card.baseDamage() + 1 : 1;
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best >= 0 && run.playCard(best);
    }

    /** 棋盘几何。 */
    public int[] debugGridGeometry() {
        return new int[]{ourBoardX(), BOARD_Y, BOARD_CELL};
    }

    /** 敌方棋盘左上角 x（核对「敌方棋盘在我方右边、同一套格子」用）。 */
    public int debugEnemyBoardX() {
        return ENEMY_BOARD_X;
    }

    /** 某格里有没有船。 */
    public boolean debugCellFilled(int cx, int cy) {
        for (FleetUnit unit : ourUnits()) {
            if (unit.covers(cx, cy)) {
                return true;
            }
        }
        return false;
    }

    /** 悬浮一张手牌。 */
    public void debugHoverCard(int index) {
        hoveredOption = index;
    }

    /** 核对稿件用：抹掉提示条（快照是静止的，2.6 秒的 toast 会被冻在画面上）。 */
    public void debugClearToast() {
        toast = "";
        toastLife = 0;
    }

    /**
     * 把这一屏推进到「编入一艘船、挑落点」那一步：战利品里优先选船，没有船就随手挑一项。
     *
     * <p>核对稿件要一张「SLOT 阶段」的画面，而掉落是随机的——种子固定时它也是固定的，
     * 但不能假设第一个战利品就是船，所以从前往后找第一项 SHIP。</p>
     */
    public boolean debugEnlist() {
        for (int guard = 0; guard < 4000 && !run.finished(); guard++) {
            if (run.stage() == Run.Stage.SLOT) {
                return true;
            }
            switch (run.stage()) {
                case PICK_LOADOUT -> run.pickLoadout(0);
                case BATTLE -> {
                    // 能出牌就出：只结束回合会被对面打死，走不到战利品/编入
                    if (!playSomething()) {
                        run.endTurn();
                    }
                }
                case REWARD -> {
                    int shipPick = -1;
                    List<Reward> options = run.rewards();
                    for (int i = 0; i < options.size(); i++) {
                        if (options.get(i).kind() == Reward.Kind.SHIP) {
                            shipPick = i;
                            break;
                        }
                    }
                    run.chooseReward(shipPick >= 0 ? shipPick : 0);
                }
                case SLOT -> {
                    return true;
                }
                case WON, LOST -> {
                    return false;
                }
            }
        }
        return false;
    }

    /** 当前悬浮的手牌下标。 */
    public int debugHoveredCard() {
        return hoveredOption;
    }

    /** 悬浮技能按钮。 */
    public void debugHoverSkill(boolean on) {
        hoveredSkill = on;
    }

    /** 技能按钮悬浮状态。 */
    public boolean debugHoveredSkill() {
        return hoveredSkill;
    }

    /** 当前落位预览。 */
    public FleetUnit debugPreview() {
        return previewSpot();
    }

    /** 棋盘上的我方船数。 */
    public int debugOurShips() {
        return ourUnits().size();
    }

    /** 棋盘上的敌方船数。 */
    public int debugEnemyShips() {
        return foeViews().size();
    }

    /** 放大视图开关。 */
    public void debugZoomShip(boolean on) {
        zoomShip = on;
    }

    /** 放大视图开着吗。 */
    public boolean debugZoomShipOn() {
        return zoomShip;
    }

    /** 让机器人走一步。 */
    public void pilotStep(Pilot pilot) {
        switch (run.stage()) {
            case PICK_LOADOUT -> run.pickLoadout(0);
            case BATTLE -> {
                if (pilot.useSkill(run.battle())) {
                    run.useSkill();
                }
                int index = pilot.chooseCard(run.battle());
                if (index >= 0) {
                    run.playCard(index);
                } else {
                    run.endTurn();
                }
            }
            case REWARD -> {
                int index = pilot.chooseReward(run, run.rewards());
                if (index >= 0) {
                    run.chooseReward(index);
                } else {
                    run.skipReward();
                }
            }
            case SLOT -> {
                int index = pilot.choosePlacement(run.fleet(), run.pending().ship(),
                        run.pendingPlacements());
                if (index >= 0) {
                    run.placeAt(index);
                } else {
                    run.skipReward();
                }
            }
            case WON, LOST -> {
            }
        }
        touch();
    }
}
