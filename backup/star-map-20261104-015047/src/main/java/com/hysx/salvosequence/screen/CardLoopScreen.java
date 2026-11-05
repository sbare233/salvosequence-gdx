package com.hysx.salvosequence.screen;

import com.hysx.salvosequence.audio.Music;
import com.hysx.salvosequence.audio.Sound;
import com.hysx.salvosequence.combat.Battle;
import com.hysx.salvosequence.combat.Enemy;
import com.hysx.salvosequence.combat.EnemyCatalog;
import com.hysx.salvosequence.combat.EnemyFormation;
import com.hysx.salvosequence.core.Backdrops;
import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.core.PixelFont;
import com.hysx.salvosequence.deck.Card;
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
    private static final int BOARD_Y = 18;
    private static final int OUR_BOARD_X = 2;
    private static final int ENEMY_BOARD_X = 262;

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

    private static final int STATUS_X = 524;
    private static final int STATUS_Y = 22;
    private static final int STATUS_PITCH = 10;

    private static final int BTN_X = 524;
    private static final int BTN_W = 108;
    private static final int BTN_H = 16;
    private static final int SKILL_BTN_Y = 58;
    private static final int TURN_BTN_Y = 80;

    private static final int CARD_X = 2;
    private static final int CARD_Y = 288;
    private static final int CARD_SIZE = 44;
    private static final int CARD_GAP = 6;
    private static final int CARD_COLS = 6;
    private static final int CARD_MAX = 12;

    /** 描述弹窗宽度：跟随鼠标半屏换边（左半 → 右侧弹出，右半 → 左侧弹出）。 */
    private static final int DESC_W = 232;

    // ---------------------------------------------------------------- 星图

    /** 恒星名标签字号：像素字体原生 12px 点阵，再小就丢字形。 */
    private static final double STAR_NAME_SIZE = 12;
    /** 节点类型标签（战斗/精英/…）比名字小一号，避免抢视线。 */
    private static final double STAR_TYPE_SIZE = 12;
    /** 滚轮缩放范围：最小 1.0 = 原大小原位置（不许再缩），最大 3 倍看局部。 */
    private static final double STAR_ZOOM_MIN = 1.0;
    private static final double STAR_ZOOM_MAX = 3.0;
    /** 星系层半宽/半高（560x350）——平移时以它为基准，别把盘面推出原始取景框。 */
    private static final double GALAXY_HALF_W = 280;
    private static final double GALAXY_HALF_H = 175;

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
    private int hoveredOption = -1;
    private boolean hoveredSkill;
    private int hoveredOurShip = -1;
    private int hoveredFoe = -1;
    private int spacePortTab = 0;
    /** 融合 tab：光标与已选的第一艘船（-1 = 未选）。 */
    private int fuseCursor;
    private int fuseFrom = -1;
    /** 星图全屏模式（太空港星图 tab 里按 M 或点放大进入）。 */
    private boolean fullStarMap;
    /** 星图缩放（滚轮，1.0~3.0），以指针为锚。 */
    private double starZoom = 1.0;
    /** 星图视口中心：滚轮锚定后平移；默认就是星系中心（320,186）。 */
    private double starCenterX = 320;
    private double starCenterY = 186;
    /** 上一帧拖动位置（设计坐标）：整屏拖拽平移用。 */
    private double starPanX;
    private double starPanY;
    /** 太阳标记（按当天真实赤经/赤纬投影到星图，F10 隐藏/显示）。 */
    private boolean sunVisible = true;
    /** 设置面板：战斗/星图里按 ESC 打开，含音乐/音效开关、返回标题、退出游戏。 */
    private boolean settingsOpen;
    private int settingsCursor;
    /** 设置项：0=继续 1=音乐 2=音效 3=扫描线 4=扫描线时机 5=风格化 6=语言 7=返回标题 8=退出游戏。 */
    private static final int SETTINGS_ITEMS = 9;
    /** 渐变星云背景（640x360 ARGB，生成一次缓存——NASA 深空照风格的像素化近似）。 */
    private int[] nebulaCache;
    /** 主题背景静态层（Backdrops：10 主题 × 4 变体，按节点确定性选播）。 */
    private int[] backdropLayer;
    private int backdropKey = -1;
    /** 星系层缓存（560x350 ARGB）：渐变盘 + 沿臂辉光，一次生成。
     * 星尘不烘进来——放大时缓存会变成 3x3 色块，改成逐帧粒子（见 buildDust）。 */
    private int[] galaxyCache;
    /** 星系上层缓存（560x350 ARGB）：星际尘埃云 + 核球光晕，带 alpha，画在星尘之上。 */
    private int[] galaxyOverlay;
    /** 星尘粒子表：盘内相对坐标 + ARGB 颜色 + 最大形状编号（0..6），盘外只留少量晕星。 */
    private int[] dustDx;
    private int[] dustDy;
    private int[] dustArgb;
    private byte[] dustShape;
    /** 实际星尘粒子数（盘外星剔除后 <= DUST_COUNT）。 */
    private int dustCount;
    /** 星尘粒子数上限：与旧缓存里的星尘数量一致。 */
    private static final int DUST_COUNT = 8000;
    /** 星星“最大缩放形状”：7 种形状 × 边长 1..5，格值 0=空 / 1=暗 / 2=中 / 3=亮。
     * 每种形状只在最大倍率下取完整边长，低倍率时按 z 缩小采样，形状连续缩放、不切换图层。 */
    private static final int STAR_SHAPE_COUNT = 7;
    private static final int STAR_SHAPE_MAX = 5;
    private static final byte[][][] STAR_SHAPES = buildStarShapes();
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
        if (prop != null && !prop.isBlank()) {
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

    @Override
    public boolean consumeHotspotDirty() {
        boolean dirty = hotspotsDirty;
        hotspotsDirty = false;
        return dirty;
    }

    // ---------------------------------------------------------------- 交互

    private Run.Stage stage() {
        return run.stage();
    }

    private boolean overlayStage() {
        return switch (stage()) {
            case PICK_LOADOUT, REWARD, SLOT, WON, LOST -> true;
            case BATTLE, SPACE_PORT, EVENT, MAP -> false;
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
        if (settingsOpen) {
            return settingsKey(code);
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
                if (settingsCursor >= 1 && settingsCursor <= 6) {
                    activateSettings(settingsCursor);
                }
            }
            case "ENTER", "SPACE" -> {
                return activateSettings(settingsCursor);
            }
            default -> {
                // 其它键在面板里不生效
            }
        }
        return null;
    }

    /** 0=继续 1=音乐 2=音效 3=扫描线 4=扫描线时机 5=风格化 6=语言 7=返回标题 8=退出游戏。 */
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
                VideoSettings.toggleScanlines();
                touch();
                return null;
            }
            case 4 -> {
                VideoSettings.cycleScanlineOrder();
                touch();
                return null;
            }
            case 5 -> {
                VideoSettings.cycleStyle();
                touch();
                return null;
            }
            case 6 -> {
                Language.toggle();
                touch();
                return null;
            }
            case 7 -> {
                settingsOpen = false;
                return ScreenContext.go(ScreenContext.Target.START);
            }
            case 8 -> {
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
        // 【移植版诊断】去掉 0x99000000 全屏压暗：暗幕会把盘底座的边"藏"掉
        // （开设置边缘变透明、关设置不透明的假象就来自它），需要时恢复本行：
        // art.rect(0, 0, 640, 360, PixelArt.fromArgb(0x99000000));
        int w = 268;
        int h = 292;
        int x = (640 - w) / 2;
        int y = (360 - h) / 2;
        art.rect(x, y, w, h, Palette.PANEL);
        art.frame(x, y, w, h, Palette.CYAN);
        art.hline(x + 2, x + w - 2, y + 26, Palette.PANEL_EDGE);
        zh(buffer, Language.t("设置", "SETTINGS"), x + 14, y + 8, 14, Palette.CYAN);

        String[] labels = settingLabels();
        for (int i = 0; i < SETTINGS_ITEMS; i++) {
            int ry = y + 40 + i * 24;
            boolean sel = i == settingsCursor;
            if (sel) {
                art.frame(x + 8, ry - 6, w - 16, 22, Palette.WARN);
                art.line(x + 14, ry + 5, x + 17, ry + 2, Palette.WARN);
                art.line(x + 17, ry + 2, x + 14, ry - 1, Palette.WARN);
            }
            zh(buffer, labels[i], x + 26, ry - 1, 12, sel ? Palette.WARN : Palette.LINE);
        }
        zh(buffer, Language.t("↑↓ 选择    ←→ 切换    Esc 关闭",
                "UP/DOWN SELECT   LEFT/RIGHT CHANGE   ESC CLOSE"),
                x + 14, y + h - 22, 10, Palette.LINE_FAINT);
    }

    /** 动态文案：音乐/音效/扫描线显示开关，时机/风格化/语言显示当前值。 */
    private String[] settingLabels() {
        return new String[]{
                Language.t("继续游戏", "RESUME"),
                Language.t("音乐：", "MUSIC: ") + Language.t(Music.isMuted() ? "关" : "开",
                        Music.isMuted() ? "OFF" : "ON"),
                Language.t("音效：", "SOUND: ") + Language.t(Sound.isMuted() ? "关" : "开",
                        Sound.isMuted() ? "OFF" : "ON"),
                Language.t("扫描线：", "SCANLINES: ") + Language.t(VideoSettings.scanlines() ? "开" : "关",
                        VideoSettings.scanlines() ? "ON" : "OFF"),
                Language.t("扫描线时机：", "SCANLINE TIMING: ")
                        + Language.t(VideoSettings.scanlineOrder().label(),
                        VideoSettings.scanlineOrder().labelEn()),
                Language.t("风格化：", "STYLE: ")
                        + Language.t(VideoSettings.style().label(), VideoSettings.style().labelEn()),
                Language.t("语言：", "LANGUAGE: ") + Language.t("中文", "English"),
                Language.t("返回标题", "BACK TO TITLE"),
                Language.t("退出游戏", "QUIT GAME")
        };
    }

    /** 设置项的鼠标热区。 */
    private void settingsHotspots(HotspotSink sink) {
        int w = 268;
        int h = 292;
        int x = (640 - w) / 2;
        int y = (360 - h) / 2;
        for (int i = 0; i < SETTINGS_ITEMS; i++) {
            int ry = y + 40 + i * 24;
            int index = i;
            sink.add(x + 8, ry - 6, w - 16, 22, () -> {
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
            sink.draw(OUR_BOARD_X, BOARD_Y, BOARD_SPAN, BOARD_SPAN,
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
            // 整屏拖动平移：先登记，压在恒星热区下面——按在恒星上仍然是跃迁，不会被拖走。
            // 同时用它整屏跟踪指针：走过的恒星没有热区，也靠这里更新坐标才能悬浮出名字/类型。
            sink.draw(0, 0, 640, 360, this::noteMouse,
                    this::dragStarMap, this::beginStarPan, null, null);
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
            // 敌方棋盘：悬浮看船，点一艘换目标
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
            sink.add(BTN_X, TURN_BTN_Y, BTN_W, BTN_H, this::endTurn);
            sink.track(BTN_X, SKILL_BTN_Y, BTN_W, BTN_H,
                    (mx, my) -> hoveredSkill = true,
                    this::useSkill,
                    () -> hoveredSkill = true,
                    () -> hoveredSkill = false);
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

    /** 滚轮缩放星图：以指针为锚，指针下的星图坐标缩放前后保持不动。 */
    @Override
    public void onScroll(double delta, double x, double y) {
        // 以前只在 fullStarMap 时收滚轮，可 MAP 阶段画的就是全屏星图（fullStarMap 还是 false），
        // 于是"滚轮没反应"。判断条件必须跟 draw() 用的是同一个可见性。
        if (!starMapVisible()) {
            return;
        }
        double next = Math.max(STAR_ZOOM_MIN,
                Math.min(STAR_ZOOM_MAX, starZoom * Math.pow(1.0025, delta)));
        if (Math.abs(next - starZoom) < 1e-4) {
            return;
        }
        // 以指针为锚：指针下的星图坐标缩放前后保持不动，放大时想看哪儿就指哪儿。
        double wx = (x - starCenterX) / starZoom;
        double wy = (y - starCenterY) / starZoom;
        starCenterX = x - wx * next;
        starCenterY = y - wy * next;
        starZoom = next;
        clampStarCenter();
        touch();
    }

    /** 星图是否正在全屏显示：MAP 阶段整屏就是星图；其它阶段靠 fullStarMap 覆盖层。 */
    private boolean starMapVisible() {
        return fullStarMap || stage() == Run.Stage.MAP;
    }

    /**
     * 夹住星图视口中心：缩到 1.0 时锁死原始位置 (320,186)，不能再平移/偏心；
     * 放大后的平移量最多让星系边缘退到原始取景框边上——不会露出比原图更多的空背景，
     * 也不会把整张星图拖出屏幕。
     */
    private void clampStarCenter() {
        double maxX = GALAXY_HALF_W * (starZoom - 1.0);
        double maxY = GALAXY_HALF_H * (starZoom - 1.0);
        starCenterX = Math.max(320 - maxX, Math.min(320 + maxX, starCenterX));
        starCenterY = Math.max(186 - maxY, Math.min(186 + maxY, starCenterY));
    }

    /** 开始拖星图：记住按下的位置，移动时按增量平移。 */
    private void beginStarPan(double mx, double my) {
        starPanX = mx;
        starPanY = my;
    }

    /** 拖动星图：内容跟着指针走（starCenter 是星系中心的屏幕位置，同向移动），
     * 到边就夹住；原大小时没得拖。 */
    private void dragStarMap(double mx, double my) {
        noteMouse(mx, my);   // 星图上唯一的移动回调：悬浮标签靠它更新指针位置
        if (starZoom <= 1.001) {
            return;
        }
        starCenterX += mx - starPanX;
        starCenterY += my - starPanY;
        starPanX = mx;
        starPanY = my;
        clampStarCenter();
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
        boolean canDrag = stage() == Run.Stage.SPACE_PORT || stage() == Run.Stage.SLOT;
        if (canDrag) {
            int index = shipIndexAt(mx, my);
            if (index >= 0 && index < ourUnits().size()) {
                FleetUnit unit = ourUnits().get(index);
                dragShipId = unit.id();
                dragOffX = mx - (OUR_BOARD_X + unit.x() * BOARD_CELL);
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
        int cx = (int) Math.floor((mx - OUR_BOARD_X) / BOARD_CELL);
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
        int col = index % CARD_COLS;
        int row = index / CARD_COLS;
        return new int[]{CARD_X + col * (CARD_SIZE + CARD_GAP),
                CARD_Y + row * (CARD_SIZE + CARD_GAP), CARD_SIZE, CARD_SIZE};
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
            drawSettingsOverlay(buffer, buffer.art());
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
        drawBackdrop(art, !starMapVisible());
        // 全屏星图自带页眉：顶栏画了也会被星图背景盖掉像素，只剩文字浮在图上
        if (starMapVisible()) {
            drawFullScreenStarMap(buffer, art);
            drawToast(buffer);
            return;
        }
        drawTopBar(buffer, art);
        if (stage() == Run.Stage.EVENT) {
            drawEventScreen(buffer, art);
            drawFooter(buffer);
            drawToast(buffer);
            return;
        }
        if (stage() == Run.Stage.SPACE_PORT) {
            drawOurBoard(buffer, art);
            drawAuraCoverage(art);
            drawSpacePortScreen(buffer, art);
            drawFooter(buffer);
            drawToast(buffer);
            drawSidePanel(buffer, art);   // 棋盘悬浮描述：太空港也能看船的数据
            return;
        }
        drawOurBoard(buffer, art);
        drawAuraCoverage(art);   // 战斗/编入：悬浮船的光环罩住谁，黄框画在棋盘上
        drawEnemyBoard(buffer, art);
        boolean overlay = overlayStage();
        // 覆盖层阶段（选舰队 / 战利品 / 编入 / 结束）不画日志与读数：
        // 它们和战利品行挤在同一片右栏，字会叠字
        if (!overlay) {
            drawLog(buffer, art);
            drawStatusLines(buffer, art);
        }
        drawControlButtons(buffer, art);
        if (overlay) {
            drawOverlay(buffer, art);
        } else {
            drawHand(buffer, art);
        }
        drawFooter(buffer);
        drawToast(buffer);
        // 描述弹窗永远最后画：footer/调试层/toast 都不许盖住它
        if (!overlay) {
            drawSidePanel(buffer, art);
        }
    }

    private void drawBackdrop(PixelArt art, boolean twinkles) {
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
        art.blitArgbTile(backdropLayer, 640, 360, 0, 0, 0, 0, 640, 360, true, false, false, 1.0);
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

    private int[] buildGalaxy() {
        int w = 560;
        int h = 350;
        int cx = w / 2;
        int cy = h / 2;
        double rx = 280.0;
        double ry = 172.0;
        int[] px = new int[w * h];
        // 1) 径向渐变盘（椭圆归一距离 d；柔边由 alpha 淡出，不加 1px 噪声）
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = (x - cx) / rx;
                double dy = (y - cy) / ry;
                double d = Math.sqrt(dx * dx + dy * dy);
                // 边界不再加逐像素噪声：1px 噪声在 3 倍放大时会变成 3x3 花斑。
                // 盘的柔边由 0.90→1.0 的 alpha 淡出负责，本来就够软。
                // 盘缘要罩住旋臂臂尖（臂最远 r≈259 → d≈0.93）：外带 alpha 保持可见，
                // 否则臂伸出"看得见的蓝"之外，像悬在黑底上。
                int r;
                int g;
                int b;
                if (d < 0.55) {
                    // 盘面蓝紫（延伸到中心，中心微提亮做辉光的底）
                    double t = d / 0.55;
                    r = (int) (64 - 46 * t);
                    g = (int) (58 - 42 * t);
                    b = (int) (110 - 66 * t);
                } else if (d < 0.90) {
                    // 暗外缘：起步色必须精确等于内带末值 (18,16,44,255)——接缝跳变
                    // 在 3 倍放大下就是一道亮暗分界（曾 +2,+2,+6 的台阶）。
                    double t = (d - 0.55) / 0.35;
                    r = (int) (18 - 8 * t);
                    g = (int) (16 - 7 * t);
                    b = (int) (44 - 16 * t);
                } else if (d < 1.0) {
                    // 盘外淡出：起步值接住外带末值 (10,9,28,160)，颜色不再变。
                    double t = (d - 0.90) / 0.10;
                    r = 10;
                    g = 9;
                    b = 28;
                } else {
                    continue;   // 完全透明
                }
                // 【移植版口径】底座 alpha 单边正态钟形：d=0.55 起淡、sigma=0.24、
                // 图层边界（d=1.0）处只剩 ~8/255——底座在层内真正渐变到透明，
                // 不再出现"实体盘+切边"（F7/F8 关滤镜后暴露的问题）；
                // 盘缘观感由臂雾（glow 循环 fog alpha）与星尘（buildDust 衰减）接管。
                int a = (int) (255 * discEdgeBell(d));
                px[y * w + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        // 2) 星尘不烘进缓存：见 buildDust() + drawFullScreenStarMap 里的逐帧粒子绘制。
        double squash = ry / rx;
        // 3) 沿臂辉光（每臂 60 块 + 零星亮青节：暗蓝青雾勾出发光带，一路缠到盘缘）。
        //    用矩形内线性衰减代替 dither：dither 的 1px 网点在放大后会变成马赛克。
        for (int arm = 0; arm < 2; arm++) {
            for (int t = 0; t < 60; t++) {
                double tt = 0.5 + t * 0.21;
                double r = 16.0 * Math.exp(ARM_WIND * tt);
                double sigmaD = 13.0 + r * 0.07;
                // 辉光带在臂带内低频游走（与尘埃丝错位——雾气包着丝，不是套在丝上）
                double off = sigmaD * 0.7 * (vnoise(tt * 1.6, arm * 5.5, 611) - 0.5) * 2.0;
                double th = arm * Math.PI + tt + off / Math.max(r, 20.0);
                int bx = cx + (int) (Math.cos(th) * r);
                int by = cy + (int) (Math.sin(th) * r * squash);
                boolean knot = t % 4 == 1;                     // 亮青节：恒星形成区观感
                int bw = Math.max(14, (int) (r * 0.34));
                int bh = Math.max(10, (int) (bw * 0.62));
                if (knot) {
                    bw = Math.max(8, bw * 55 / 100);
                    bh = Math.max(6, bh * 55 / 100);
                }
                int glow = knot ? 0x742E5E8C                   // 亮青（更小更亮）
                        : t % 3 == 0 ? 0x5C283A78              // 暗蓝（1/3）
                        : 0x5C1E4A5C;                          // 暗青（2/3）
                int ga = glow >>> 24;
                int gr = (glow >> 16) & 0xFF;
                int gg = (glow >> 8) & 0xFF;
                int gb = glow & 0xFF;
                for (int yy = by - bh / 2; yy < by + bh / 2; yy++) {
                    if (yy < 0 || yy >= h) { continue; }
                    double wy = 1.0 - Math.abs(yy + 0.5 - by) / (bh * 0.5);
                    if (wy <= 0) { continue; }
                    for (int xx = bx - bw / 2; xx < bx + bw / 2; xx++) {
                        if (xx < 0 || xx >= w) { continue; }
                        double wx = 1.0 - Math.abs(xx + 0.5 - bx) / (bw * 0.5);
                        if (wx <= 0) { continue; }
                        int idx = yy * w + xx;
                        int base = px[idx];
                        double wgt = wx * wy;
                        // 【移植版口径】臂雾自带透明度：alpha 取"底座 / 雾"较大者
                        // （max 不叠加、不饱和），空底座区按雾自身强度落雾；雾在盘缘
                        // 0.75→1.0 线性收尾，避免图层边界切出直边。其余加法口径原样。
                        double dd = discNorm(xx - cx, yy - cy);
                        double rim = dd < 0.75 ? 1.0 : Math.max(0.0, (1.0 - dd) / 0.25);
                        int outA = Math.max(base >>> 24, (int) (ga * wgt * 0.30 * rim));
                        if (outA == 0) { continue; }
                        int nr = Math.min(255, ((base >> 16) & 0xFF) + (int) (gr * ga * wgt / 255));
                        int ng = Math.min(255, ((base >> 8) & 0xFF) + (int) (gg * ga * wgt / 255));
                        int nb = Math.min(255, (base & 0xFF) + (int) (gb * ga * wgt / 255));
                        px[idx] = (outA << 24) | (nr << 16) | (ng << 8) | nb;
                    }
                }
            }
        }
        // 4) 核球边缘亮星不再烘：12 个白点放大成 3x3 方块太扎眼。
        //    核球轮廓交给逐帧的 90 颗核球星团。
        // 5) 星际尘埃云和核球光晕不在这层：它们在 buildGalaxyOverlay() 里，
        //    逐帧画在星尘之上——保持“星尘在云和光晕下面”的旧图层顺序。
        return px;
    }

    /**
     * 星系上层（560x350，一次生成）：星际尘埃云 + 核球光晕，带 alpha。
     * 它在逐帧星尘之后绘制，恢复旧版缓存里的图层顺序：尘埃带压住星点、
     * 核球光晕把中心糊成一片，而不是让星尘浮在最上面。
     */
    private int[] buildGalaxyOverlay() {
        int w = 560;
        int h = 350;
        int cx = w / 2;
        int cy = h / 2;
        double rx = 262.0;
        double ry = 162.0;
        int[] px = new int[w * h];
        // 1) 星际物质：树状尘埃丝——沿臂脊一条粗主丝（低频游走：扭曲、浓淡不匀），
        //    从主丝往两侧随机游走出细枝（渐细渐淡、细处偏红的 Hα 边缘），偶尔再分叉。
        //    笔画式烘焙（软圆斑 src-over 叠加），比带内调制噪声更像真实丝缕。
        double squash = ry / rx;
        blotDustTrunk(px, w, h, cx, cy, squash);
        // 2) 核球光晕：alpha 高斯；按“光晕 over 云”合成到同一层。
        double sigma2 = 2.0 * 0.17 * 0.17;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double ddx = (x - cx) / rx;
                double ddy = (y - cy) / ry;
                double dd = Math.sqrt(ddx * ddx + ddy * ddy);
                if (dd >= 0.6) { continue; }        // 高斯尾部趋 0，只算到 0.6
                int alpha = (int) (255 * Math.exp(-dd * dd / sigma2));
                if (alpha <= 2) { continue; }
                // 颜色：白心 → 金黄（0.4 处到金黄后不再变）
                double ct = Math.min(1.0, dd / 0.4);
                int cr = (int) (255 - 17 * ct);
                int cg = (int) (250 - 66 * ct);
                int cb = (int) (232 - 128 * ct);
                int idx = y * w + x;
                int prev = px[idx];
                if (prev == 0) {
                    px[idx] = (alpha << 24) | (cr << 16) | (cg << 8) | cb;
                    continue;
                }
                // 光晕盖在云上：标准 source-over
                int pa = prev >>> 24;
                int outA = alpha + pa * (255 - alpha) / 255;
                if (outA == 0) { continue; }
                int pr = (prev >> 16) & 0xFF;
                int pg = (prev >> 8) & 0xFF;
                int pb = prev & 0xFF;
                int or = (cr * alpha + pr * pa * (255 - alpha) / 255) / outA;
                int og = (cg * alpha + pg * pa * (255 - alpha) / 255) / outA;
                int ob = (cb * alpha + pb * pa * (255 - alpha) / 255) / outA;
                px[idx] = (outA << 24) | (or << 16) | (og << 8) | ob;
            }
        }
        return px;
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

    /**
     * 星尘粒子表（一次生成，逐帧复用）：86% 对数臂蓝白年轻星 + 14% 均匀盘暗暖老年星，
     * 坐标是相对星系中心的偏移。每颗星固定一种“最大缩放形状”，绘制时整套形状随 z 缩放，
     * 不做倍率分层的重绘——缩小时不会有额外的星忽然冒出来或消失。
     */
    private void buildDust() {
        dustDx = new int[DUST_COUNT];
        dustDy = new int[DUST_COUNT];
        dustArgb = new int[DUST_COUNT];
        dustShape = new byte[DUST_COUNT];
        double squash = 162.0 / 262.0;
        java.util.Random rnd = new java.util.Random(0x6A1L);
        int n = 0;
        for (int i = 0; i < DUST_COUNT; i++) {
            double ddx;
            double ddy;
            int v;
            if (i < DUST_COUNT * 86 / 100) {
                int arm = rnd.nextInt(2);
                double t = 0.15 + rnd.nextDouble() * 13.1;
                double th = arm * Math.PI + t;
                double r = 16.0 * Math.exp(ARM_WIND * t);
                double sigma = 7.0 + r * 0.07;
                double rr = r + rnd.nextGaussian() * sigma;
                double aa = th + rnd.nextGaussian() * (10.0 / Math.max(8.0, r));
                ddx = Math.cos(aa) * rr;
                ddy = Math.sin(aa) * rr * squash;
                int roll = rnd.nextInt(100);
                v = roll < 45 ? rnd.nextInt(60) + 175      // 蓝白
                        : roll < 85 ? rnd.nextInt(50) + 150  // 纯白
                        : rnd.nextInt(60) + 120;             // 蓝
            } else {
                double th = rnd.nextDouble() * Math.PI * 2;
                double r = Math.pow(rnd.nextDouble(), 0.62) * 250.0;
                ddx = Math.cos(th) * r;
                ddy = Math.sin(th) * r * squash;
                v = rnd.nextInt(45) + 60;                    // 暗暖（覆盖在渐变上偏暗）
            }
            int shapeRoll = rnd.nextInt(100);
            // 【移植版口径】盘外尘粒不再 12 选 1 硬丢弃（密度悬崖就是肉眼看到的盘缘）：
            // 改成按椭圆外距离指数衰减的保留率 + 亮度同步衰减，边界处密度连续、向外溶解。
            double nx = ddx / 262.0;
            double ny = ddy / 162.0;
            double ellipse = nx * nx + ny * ny;
            if (ellipse > 1.0) {
                double keep = Math.exp(-(ellipse - 1.0) * 4.0);
                if (rnd.nextDouble() > keep) {
                    continue;
                }
                v = (int) (v * Math.max(0.25, keep));
            }
            dustDx[n] = (int) ddx;
            dustDy[n] = (int) ddy;
            dustArgb[n] = 0xFF000000 | (v << 16) | (v << 8) | Math.min(255, v + 35);
            dustShape[n] = (byte) (shapeRoll < 18 ? 0 : shapeRoll < 30 ? 1
                    : shapeRoll < 52 ? 2 : shapeRoll < 76 ? 3 : shapeRoll < 88 ? 4
                    : shapeRoll < 94 ? 5 : 6);
            n++;
        }
        dustCount = n;
    }

    /** 预生成 5 种“最大形状”在边长 1..5 下的灰度模板（构建期一次，绘制时只查表）。 */
    private static byte[][][] buildStarShapes() {
        byte[][][] shapes = new byte[STAR_SHAPE_COUNT][STAR_SHAPE_MAX + 1][];
        for (int id = 0; id < STAR_SHAPE_COUNT; id++) {
            for (int size = 1; size <= STAR_SHAPE_MAX; size++) {
                shapes[id][size] = rasterizeStarShape(id, size);
            }
        }
        return shapes;
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

    /** 星点边长：原尺寸 1px → 最大倍率 5px，形状随 z 连续缩放，不做图层切换。 */
    private static int starShapeSize(double z) {
        return Math.max(1, Math.min(STAR_SHAPE_MAX, (int) Math.round(1 + (z - 1.0) * 2.0)));
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

    /** 每颗星按自己的相位闪烁；相位顺带决定速度档，避免满屏星星同呼吸。 */
    private static int twinkleAt(int phase, double time) {
        int step = (int) (time * (48 + (phase & 3) * 10));
        return TWINKLE[(phase + step) & 255];
    }

    /** 单像素加法混合（按目标索引，边界已在调用处检查）。 */
    private static void addPixelAt(int[] px, int idx, int r, int g, int b) {
        int d = px[idx];
        int nr = Math.min(255, ((d >> 16) & 0xFF) + r);
        int ng = Math.min(255, ((d >> 8) & 0xFF) + g);
        int nb = Math.min(255, (d & 0xFF) + b);
        px[idx] = 0xFF000000 | (nr << 16) | (ng << 8) | nb;
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
     * 渐变星云背景（一次性生成）：垂直渐变深空底 + 三块径向星云斑（暖橙/深紫/青蓝,
     * 带 noise 抖动的软边）+ 静态微星尘。NASA 深空照的像素画近似。
     */
    private int[] buildNebula() {
        int[] px = new int[640 * 360];
        java.util.Random rnd = new java.util.Random(20261026L);
//        // 底：垂直渐变 (10,8,26) → (2,3,10)
//        for (int y = 0; y < 360; y++) {
//            double t = y / 359.0;
//            int r = (int) (10 - 8 * t);
//            int g = (int) (8 - 5 * t);
//            int b = (int) (26 - 16 * t);
//            int row =  (r << 16) | (g << 8) | b;
//            for (int x = 0; x < 640; x++) {
//                px[y * 640 + x] = row;
//            }
//        }
//        // 三块径向星云斑：软边（指数衰减 + noise）
//        int[][] blobs = {
//                {110, 90, 150, 52, 24, 18},     // 暖橙（左上）
//                {500, 120, 170, 18, 12, 44},    // 深紫（右上）
//                {330, 300, 190, 10, 34, 46},    // 青蓝（下方）
//        };
//        for (int[] blob : blobs) {
//            int cx = blob[0];
//            int cy = blob[1];
//            int radius = blob[2];
//            int cr = blob[3];
//            int cg = blob[4];
//            int cb = blob[5];
//            for (int y = Math.max(0, cy - radius); y < Math.min(360, cy + radius); y++) {
//                for (int x = Math.max(0, cx - radius); x < Math.min(640, cx + radius); x++) {
//                    double d = Math.hypot(x - cx, y - cy) / radius;
//                    if (d >= 1.0) { continue; }
//                    double falloff = Math.exp(-3.2 * d * d) * (0.75 + rnd.nextDouble() * 0.25);
//                    int i = y * 640 + x;
//                    int base = px[i];
//                    int br = (base >> 16) & 0xFF;
//                    int bg = (base >> 8) & 0xFF;
//                    int bb = base & 0xFF;
//                    int nr = Math.min(255, br + (int) (cr * falloff));
//                    int ng = Math.min(255, bg + (int) (cg * falloff));
//                    int nb = Math.min(255, bb + (int) (cb * falloff));
//                    px[i] = 0xFF000000 | (nr << 16) | (ng << 8) | nb;
//                }
//            }
//        }
        // 静态星尘：3000 颗暗档远景（星系层另有渐变盘 + 8000 颗逐帧星尘，远景只在盘外可见）
        for (int i = 0; i < 3000; i++) {
            int x = rnd.nextInt(640);
            int y = rnd.nextInt(360);
            int roll = rnd.nextInt(100);
            int v = roll < 70 ? rnd.nextInt(24) + 38             // 暗档为主
                    : roll < 94 ? rnd.nextInt(30) + 68           // 中暗
                    : rnd.nextInt(40) + 105;                     // 少数中亮
            px[y * 640 + x] = 0xFF000000 | (v << 16) | (v << 8) | Math.min(255, v + 22);
        }
        // 中亮星：300 颗 2x2 块（近景层，占视觉重量）
        for (int i = 0; i < 300; i++) {
            int x = rnd.nextInt(639);
            int y = rnd.nextInt(359);
            int v = rnd.nextInt(60) + 120;
            int c = 0xFF000000 | (v << 16) | (v << 8) | Math.min(255, v + 35);
            px[y * 640 + x] = c;
            px[y * 640 + x + 1] = c;
            px[(y + 1) * 640 + x] = c;
            px[(y + 1) * 640 + x + 1] = c;
        }
        // 亮星：40 颗带十字衍射芒（烧进缓存）
        for (int i = 0; i < 40; i++) {
            int x = rnd.nextInt(620) + 10;
            int y = rnd.nextInt(340) + 10;
            int v = rnd.nextInt(70) + 170;
            int c = 0xFF000000 | (v << 16) | (v << 8) | Math.min(255, v + 40);
            px[y * 640 + x] = c;
            int len = 2 + rnd.nextInt(3);
            for (int d = 1; d <= len; d++) {
                int dim = d * 2 > len ? (v - 90) : v - 40;
                int cd = 0xFF000000 | (dim << 16) | (dim << 8) | dim;
                if (x + d < 640) { px[y * 640 + x + d] = cd; }
                if (x - d >= 0) { px[y * 640 + x - d] = cd; }
                if (y + d < 360) { px[(y + d) * 640 + x] = cd; }
                if (y - d >= 0) { px[(y - d) * 640 + x] = cd; }
            }
        }
        return px;
    }

    private void drawTopBar(CanvasBuffer buffer, PixelArt art) {
        art.rect(BAR_X, BAR_Y, 632, BAR_H, Palette.PANEL);
        art.frame(BAR_X, BAR_Y, 632, BAR_H, Palette.LINE_FAINT);

        // 通栏自上而下排：所有位置都按实测字宽往后推，写死坐标一换数字就压字
        double x = BAR_X + 4;
        String route = Language.t("航程 ", "ROUTE ") + (run.node() + 1) + "/" + run.routeLength();
        zh(buffer, route, x, BAR_Y + 2, 12, Palette.CYAN_DIM);
        x += textWidth(route, 12) + 6;
        for (int i = 0; i < run.routeLength(); i++) {
            boolean done = i < run.node();
            boolean here = i == run.node();
            art.rect((int) x + i * 9, BAR_Y + 6, 6, 5,
                    done ? Palette.OK : here ? Palette.CYAN : Palette.PANEL_ALT);
        }
        x += run.routeLength() * 9 + 10;

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
        x += font.measure(hpText) + 12;

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
        String foeLabel = Language.t("敌方 ", "ENEMY ") + alive + "/" + foes.size();
        zh(buffer, foeLabel, x, BAR_Y + 2, 12, Palette.FOE_GLOW);
        x += textWidth(foeLabel, 12) + 4;
        bar(art, (int) x, BAR_Y + 6, 70, 5, ratio(total, max), Palette.FOE_GLOW);
        x += 70 + 4;
        String foeText = total + "/" + max;
        en(art, foeText, (int) x, BAR_Y + 5, Palette.LINE_DIM);

        // 能量（右对齐）
        int energy = run.battle() == null ? 0 : run.battle().energy();
        int maxEnergy = run.battle() == null ? 0 : run.battle().maxEnergy();
        int pips = Math.max(1, maxEnergy);
        int pipsX = 632 - (pips * 9 - 2);
        if (Language.isEnglish()) {
            en(art, "EN", pipsX - 16, BAR_Y + 5, Palette.WARN);
        } else {
            zh(buffer, "能量", pipsX - 28, BAR_Y + 2, 12, Palette.WARN);
        }
        for (int i = 0; i < pips; i++) {
            art.rect(pipsX + i * 9, BAR_Y + 5, 7, 8, i < energy ? Palette.WARN : Palette.PANEL_ALT);
        }
    }

    // ---------------------------------------------------------------- 两块棋盘

    private void drawOurBoard(CanvasBuffer buffer, PixelArt art) {
        drawBoardBase(buffer, art, OUR_BOARD_X, "我方舰队", "OUR FLEET",
                Palette.CYAN_DIM, Palette.CYAN);
        List<OurView> views = ourViews();
        if (views.isEmpty()) {
            zh(buffer, stage() == Run.Stage.PICK_LOADOUT ? "还没选舰队" : "舰队为空",
                    OUR_BOARD_X + 8, BOARD_Y + 8, 12, Palette.LINE_FAINT);
        }
        for (int i = 0; i < views.size(); i++) {
            OurView view = views.get(i);
            FleetUnit unit = view.unit();
            int x = OUR_BOARD_X + unit.x() * BOARD_CELL;
            int y = BOARD_Y + unit.y() * BOARD_CELL;
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

        // 敌方锁定：我方前排（x 最大）披红菱形 + 四角箭矢，被换船吃掉时重播
        if (stage() == Run.Stage.BATTLE) {
            Battle battle = run.battle();
            var front = battle == null ? null : battle.fleetRoster().pick(false);
            if (front != null && front.ship() != null) {
                FleetUnit unit = front.ship();
                drawLockMarker(art,
                        OUR_BOARD_X + unit.x() * BOARD_CELL,
                        BOARD_Y + unit.y() * BOARD_CELL,
                        unit.w(), unit.h(),
                        foeLockStart, Palette.DANGER, Palette.LOCK_HOT, Palette.LOCK_DIM);
            }
        }

        // 编入新船：只把当前预览画实，其余候选点一个小角标（134 个都铺底会糊成一片）
        if (stage() == Run.Stage.SLOT) {
            FleetUnit spot = previewSpot();
            for (FleetUnit cand : candidates()) {
                int x = OUR_BOARD_X + cand.x() * BOARD_CELL;
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
                int x = OUR_BOARD_X + spot[0] * BOARD_CELL;
                int y = BOARD_Y + spot[1] * BOARD_CELL;
                art.pixel(x, y, Palette.CYAN_DIM);
                art.pixel(x + 1, y, Palette.CYAN_DIM);
                art.pixel(x, y + 1, Palette.CYAN_DIM);
            }
        }
        if (!overlayStage()) {
            zh(buffer, "我方舰队 16×16", OUR_BOARD_X + 2, BOARD_Y + BOARD_SPAN + 3, 12,
                    Palette.LINE_FAINT);
        }
    }

    private void drawEnemyBoard(CanvasBuffer buffer, PixelArt art) {
        drawBoardBase(buffer, art, ENEMY_BOARD_X, "敌方舰队", "ENEMY",
                Palette.FOE_DEEP, Palette.FOE_GLOW);
        List<FoeView> foes = foeViews();
        for (int i = 0; i < foes.size(); i++) {
            FoeView foe = foes.get(i);
            FleetUnit unit = foe.unit();
            int x = ENEMY_BOARD_X + unit.x() * BOARD_CELL;
            int y = BOARD_Y + unit.y() * BOARD_CELL;
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
        // 悬浮敌舰的光环提示：和我方棋盘同款——被这艘敌舰光环罩到的敌舰描黄框
        drawFoeAuraCoverage(art, foes);
        // 锁定框压在所有敌舰之上：菱形 + 四角箭矢，打沉/换目标时重播一次
        if (stage() == Run.Stage.BATTLE) {
            Battle battle = run.battle();
            int ti = battle == null ? -1 : battle.targetIndex();
            if (ti >= 0 && ti < foes.size() && foes.get(ti).alive()) {
                FleetUnit unit = foes.get(ti).unit();
                drawLockMarker(art,
                        ENEMY_BOARD_X + unit.x() * BOARD_CELL,
                        BOARD_Y + unit.y() * BOARD_CELL,
                        unit.w(), unit.h());
            }
        }
        if (!overlayStage()) {
            zh(buffer, "敌方舰队 16×16", ENEMY_BOARD_X + 2, BOARD_Y + BOARD_SPAN + 3, 12,
                    Palette.LINE_FAINT);
        }
    }

    private void drawBoardBase(CanvasBuffer buffer, PixelArt art, int x0, String tagZh,
                               String tagEn, com.hysx.salvosequence.core.Color unusedDeep,
                               com.hysx.salvosequence.core.Color tagColor) {
        // 半透明棋盘底：渐变星空透上来（fillRectArgb 支持 alpha 混合）
        art.rect(x0, BOARD_Y, BOARD_SPAN, BOARD_SPAN, PixelArt.fromArgb(0x880A0E16));
        art.frame(x0, BOARD_Y, BOARD_SPAN, BOARD_SPAN, PixelArt.fromArgb(0xAA44527A));
        for (int cy = 0; cy < FleetBoard.SIZE; cy++) {
            for (int cx = 0; cx < FleetBoard.SIZE; cx++) {
                if (cx % 4 == 0 && cy % 4 == 0) {
                    art.pixel(x0 + cx * BOARD_CELL, BOARD_Y + cy * BOARD_CELL,
                            PixelArt.fromArgb(0x80546A80));
                }
            }
        }
        boardTag(buffer, art, Language.t(tagZh, tagEn), x0 + 3, BOARD_Y + 3, tagColor);
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
     */
    private void drawLockMarker(PixelArt art, int x, int y, int w, int h) {
        drawLockMarker(art, x, y, w, h, lockAnimStart,
                Palette.LOCK, Palette.LOCK_HOT, Palette.LOCK_DIM);
    }

    /**
     * 同一套锁定动画的通用版：敌方锁我方时换成警示红，动画起点单独计时。
     */
    private void drawLockMarker(PixelArt art, int x, int y, int w, int h, double start,
                                com.hysx.salvosequence.core.Color edgeColor,
                                com.hysx.salvosequence.core.Color hotColor,
                                com.hysx.salvosequence.core.Color dimColor) {
        int cx = x + w * BOARD_CELL / 2;
        int cy = y + h * BOARD_CELL / 2;
        int base = Math.max(w, h) * BOARD_CELL / 2 + 7;
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
        int px = ENEMY_BOARD_X;
        int pw = 636 - px;
        art.rect(px, BOARD_Y, pw, BOARD_SPAN, Palette.PANEL);
        art.frame(px, BOARD_Y, pw, BOARD_SPAN, Palette.LINE_DIM);
        String capName = run.captain() != null ? run.captain().protagonist().nameZh() : "";
        zh(buffer, "太空港", px + 6, BOARD_Y + 4, 12, Palette.CYAN);
        zh(buffer, "合金 " + run.alloy() + "  舰长 " + capName, px + 90, BOARD_Y + 5, 12, Palette.WARN);
        String[] tabs = {"编组", "定制", "融合", "培训", "星图"};
        int tabW = (pw - 16) / 5;
        for (int i = 0; i < 5; i++) {
            int tx = px + 6 + i * (tabW + 2);
            boolean on = spacePortTab == i;
            art.rect(tx, BOARD_Y + 22, tabW - 2, 14, on ? Palette.PANEL_ALT : Palette.PANEL);
            art.frame(tx, BOARD_Y + 22, tabW - 2, 14, on ? Palette.CYAN : Palette.LINE_FAINT);
            zh(buffer, tabs[i], tx + 4, BOARD_Y + 24, 12, on ? Palette.CYAN : Palette.LINE_DIM);
        }
        int contentY = BOARD_Y + 42;
        int contentH = BOARD_SPAN - 42 - 26;
        art.rect(px + 4, contentY, pw - 12, contentH, Palette.PANEL_ALT);
        // 出发键先画：它在下缘固定位置，而 tab 内的悬浮描述可能盖到这里，
        // 描述必须压在按钮上面（否则购买/强化提示会被绿条切掉）。
        int departY = BOARD_Y + BOARD_SPAN - 22;
        boolean canDepart = run.pendingBuyShip() == null;
        art.rect(px + 6, departY, pw - 16, 16, canDepart ? Palette.OK : Palette.PANEL_EDGE);
        zh(buffer, canDepart ? "出发 → 星图" : "先放好新船",
                px + pw / 2 - 44, departY + 3, 12, canDepart ? Palette.BACKDROP : Palette.WARN);
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
                    art.frame(OUR_BOARD_X + other.x() * BOARD_CELL - 1,
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

    /** 星图 tab：程序生成的漩涡星系（借鉴开源 galaxy generator 的通用做法——
     * 对数螺旋臂、指数盘面、核球、臂星高斯散布、尘埃带、亮星衍射光芒）。
     * 主线 5 关恒星沿主臂旋出（走过的暗、当前脉冲、未来空心），候选围绕下一站。 */
    /** （旧面板星图已由全屏星图取代，保留空壳防外部调用。） */
    private void drawSpStarTab(CanvasBuffer buffer, PixelArt art, int px, int cy, int pw, int ch) {
    }

    /** 主线第 {@code node} 关恒星的屏幕坐标：贴主臂的对数螺线（r = 13·e^(0.25θ)），带缩放。 */
    private int[] starPos(int node, int px, int cy, int pw, int ch) {
        double z = fullStarMap ? starZoom : 1.0;
        double gx = px + 62 * z;
        double gy = cy + ch / 2.0;
        double theta = 0.85 + node * 1.75;
        double r = 13.0 * Math.exp(0.25 * theta) * z;
        return new int[]{(int) (gx + Math.cos(theta) * r), (int) (gy + Math.sin(theta) * r * 0.52)};
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
        art.fillRect(0, 0, 640, 360, PixelArt.fromArgb(0xB3000410));
        art.rect(130, 60, 380, 260, Palette.PANEL);
        art.frame(130, 60, 380, 260, Palette.CYAN_DIM);
        zh(buffer, Language.t("遭遇", "ENCOUNTER"), 142, 68, 12, Palette.OK);
        zh(buffer, card.title(), 142, 86, 14, Palette.CYAN);
        int y = 114;
        for (String line : wrap(card.desc(), 20)) {
            zh(buffer, line, 142, y, 12, Palette.LINE_DIM);
            y += 13;
        }
        int oy = 180;
        for (int i = 0; i < card.options().size(); i++) {
            var opt = card.options().get(i);
            art.rect(140, oy, 360, 22, Palette.PANEL_ALT);
            art.frame(140, oy, 360, 22, Palette.LINE_DIM);
            zh(buffer, (i + 1) + ". " + opt.label(), 150, oy + 5, 12, Palette.LINE);
            oy += 26;
        }
        zh(buffer, Language.t("数字键或点击选择", "NUMBER KEYS OR CLICK TO CHOOSE"),
                142, 300, 12, Palette.LINE_FAINT);
    }

    /** 全屏星图（黑流树海式自由航行）：星系 + 散布恒星，跃迁半径内的可点。 */
    private void drawFullScreenStarMap(CanvasBuffer buffer, PixelArt art) {
        double z = starZoom;
        int gx = (int) Math.rint(starCenterX);
        int gy = (int) Math.rint(starCenterY);
        // 整屏星图会重画背景：先把本帧之前记下的文字（顶栏"航程/我方/敌方"）
        // 一起清掉，否则它们会浮在星图上叠字。
        // 星图里的星全部改成逐帧闪烁，背景那层固定位置的闪烁星在这里不再画，避免两套闪法打架。
        // 星图背景：固定用原版星野（nebula），不跟主题背景切换
        if (nebulaCache == null) { nebulaCache = buildNebula(); }
        art.blitArgbTile(nebulaCache, 640, 360, 0, 0, 0, 0, 640, 360, true, false, false, 1.0);
        java.util.Random rnd = new java.util.Random(20261026L);
        // 星系分两层缓存：底层 = 渐变盘 + 沿臂辉光；上层 = 尘埃云 + 核球光晕（带 alpha）。
        // 星尘逐帧粒子画在两层之间，保持旧版“星尘在云和光晕下面”的图层顺序。
        // 倍率必须和恒星用同一个 z：比例一旦不同，滚轮时银河和关卡就会脱节，鼠标锚点也对不上。
        if (galaxyCache == null) {
            galaxyCache = buildGalaxy();
            galaxyOverlay = buildGalaxyOverlay();
            buildDust();
        }
        int gw = (int) Math.rint(560 * z);
        int gh = (int) Math.rint(350 * z);
        // replaceTransparent=false：盘外透明像素跳过，不擦背景。
        // 拜耳量化 14：渐变盘在任意缩放下都渲染成像素画抖动网（阈值随屏幕像素走）
        if (com.hysx.salvosequence.ui.VideoSettings.bayer()) {
            art.blitArgbTileDithered(galaxyCache, 560, 350, 0, 0, gx - gw / 2, gy - gh / 2,
                    gw, gh, false, 1.0, 14);
        } else {
            // 拜耳关（F8）：同源直贴，诊断对照
            art.blitArgbTile(galaxyCache, 560, 350, 0, 0, gx - gw / 2, gy - gh / 2,
                    gw, gh, false, false, false, 1.0);
        }
        // 每颗星按自己的“最大缩放形状”绘制，整套形状随 z 缩放：低倍率缩成点、放大后展开，
        // 不再按倍率切换图层，所以不会忽然多出一批星。形状用加法混合，核球亮区也不会发黑。
        int[] dustPixels = art.buffer();
        int dw = art.width();
        int dh = art.height();
        double boost = Math.min(1.45, 1.0 + 0.2 * (z - 1.0));
        int baseSize = starShapeSize(z);
        for (int i = 0; i < dustCount; i++) {
            int sx = (int) Math.rint(gx + dustDx[i] * z);
            int sy = (int) Math.rint(gy + dustDy[i] * z);
            int c = dustArgb[i];
            int v = (c >> 16) & 0xFF;
            int tw = twinkleAt((i * 73 + 19) & 255, time);
            // 亮的星长到最大，暗的星小一号：同一倍率下也有大小层次；
            // 闪烁到低谷时再收一圈，变暗的同时也变小（点光源的观测直觉）。
            int shapeSize = Math.min(baseSize, 2 + v * 3 / 255);
            if (tw < 82 && shapeSize > 2) {
                shapeSize--;
            }
            int halfShape = shapeSize / 2;
            int left = sx - halfShape;
            int top = sy - halfShape;
            if (left >= dw || top >= dh || left + shapeSize <= 0 || top + shapeSize <= 0) {
                continue;
            }
            int r = Math.min(255, (int) (v * boost));
            int g = Math.min(255, (int) (v * boost));
            int b = Math.min(255, (int) ((v + 35) * boost));
            // 每颗星按自己的相位闪：亮的时候提亮，暗的时候压到六成左右。
            r = Math.min(255, r * tw / 100);
            g = Math.min(255, g * tw / 100);
            b = Math.min(255, b * tw / 100);
            byte[] mask = STAR_SHAPES[dustShape[i]][shapeSize];
            for (int py = 0; py < shapeSize; py++) {
                int dy = top + py;
                if (dy < 0 || dy >= dh) {
                    continue;
                }
                int row = dy * dw;
                int mrow = py * shapeSize;
                for (int px = 0; px < shapeSize; px++) {
                    int lv = mask[mrow + px];
                    if (lv == 0) {
                        continue;
                    }
                    int dx = left + px;
                    if (dx < 0 || dx >= dw) {
                        continue;
                    }
                    int f = lv == 3 ? 255 : lv == 2 ? 175 : 95;
                    addPixelAt(dustPixels, row + dx, r * f / 255, g * f / 255, b * f / 255);
                }
            }
        }
        // 尘埃云 + 核球光晕压在星尘之上（恢复旧版缓存里的图层顺序）：云把星点盖进去，
        // 核球光晕再把中心糊成一片，而不是让星尘浮在云和光晕表面。
        // 拜耳量化 10：银心高斯和尘埃丝加一层细颗粒（比底座细，丝缕不糊）
        if (com.hysx.salvosequence.ui.VideoSettings.bayer()) {
            art.blitArgbTileDithered(galaxyOverlay, 560, 350, 0, 0, gx - gw / 2, gy - gh / 2,
                    gw, gh, false, 1.0, 10);
        } else {
            art.blitArgbTile(galaxyOverlay, 560, 350, 0, 0, gx - gw / 2, gy - gh / 2,
                    gw, gh, false, false, false, 1.0);
        }
        // 前景星层：画在星系之上，同样按 z 缩放；每颗按自己的相位闪，暗相位直接不画。
        java.util.Random fg = new java.util.Random(0xF6L);
        for (int i = 0; i < 1500; i++) {
            int bx = fg.nextInt(640);
            int by = fg.nextInt(360);
            int roll = fg.nextInt(100);
            // 【移植版口径】盘外星同款指数衰减保留率（与 buildDust 一致）替代 8 选 1。
            double nx = (bx - 320) / 262.0;
            double ny = (by - 186) / 162.0;
            double ellipse = nx * nx + ny * ny;
            if (ellipse > 1.0 && fg.nextDouble() > Math.exp(-(ellipse - 1.0) * 4.0)) {
                continue;
            }
            int sx = (int) ((bx - 320) * z + gx);
            int sy = (int) ((by - 186) * z + gy);
            if (sx < 2 || sx > 637 || sy < 2 || sy > 357) {
                continue;
            }
            int tw = twinkleAt((i * 53 + 7) & 255, time);
            if (tw < 72) {
                continue;
            }
            // 前景星也按亮度收放：亮的时候多画一颗，暗的时候缩回一颗，再暗就不画。
            if (roll >= 96) {
                art.pixel(sx, sy, tw > 96 ? Palette.STAR_NEAR : Palette.STAR_MID);
                if (tw > 100) {
                    art.pixel(sx + 1, sy, Palette.STAR_MID);
                    art.pixel(sx, sy + 1, Palette.STAR_MID);
                } else if (tw > 86) {
                    art.pixel(sx + 1, sy, Palette.STAR_FAR);
                }
            } else if (roll >= 78) {
                art.pixel(sx, sy,
                        tw > 105 ? Palette.STAR_NEAR : tw > 90 ? Palette.STAR_MID : Palette.STAR_FAR);
                if (tw > 112) {
                    art.pixel(sx + 1, sy + 1, Palette.STAR_MID);
                }
            } else {
                art.pixel(sx, sy,
                        tw > 108 ? Palette.STAR_NEAR : tw > 88 ? Palette.STAR_MID : Palette.STAR_FAR);
            }
        }
        // 核球星团：位置固定（rnd 每帧同种子），亮度跟着闪烁走
        for (int i = 0; i < 90; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double rad = Math.abs(rnd.nextGaussian()) * 13.0 * z;
            int tw = twinkleAt((i * 31 + 5) & 255, time);
            if (tw < 76) {
                continue;
            }
            art.pixel((int) (gx + Math.cos(ang) * rad), (int) (gy + Math.sin(ang) * rad * 0.7),
                    rad < 8 * z && tw > 100 ? Palette.LINE : Palette.STAR_NEAR);
        }

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
            int sparkPhase = st.id() * 17;
            // 太阳常驻标记自带名字：悬浮不再叠任何字
            boolean isSun = "太阳".equals(st.nameZh());
            // 核心屏障内未解锁的节点：画灰色框 + "锁" 字，不走彩色分支
            boolean coreLocked = !run.gatePassed() && st.radius() < 30;
            if (coreLocked) {
                drawNodeSparkle(art, pos[0], pos[1], Palette.LINE_FAINT, 0, time, sparkPhase);
                art.frame(pos[0] - 4, pos[1] - 4, 9, 9, Palette.LINE_FAINT);
            } else if (isCurrent) {
                int pulse = (int) (3 + 2 * Math.abs(Math.sin(time * 3)));
                drawStarGlow(art, pos[0], pos[1], Palette.CYAN, Palette.LINE, pulse);
                drawNodeSparkle(art, pos[0], pos[1], Palette.CYAN, 3, time, sparkPhase);
                if (!isSun) {
                    drawStarLabelHover(buffer, st.nameZh(), pos[0], pos[1], pos[1] + 8,
                            STAR_NAME_SIZE, Palette.CYAN);
                    drawStarLabelHover(buffer, starTypeLabel(st.type()), pos[0], pos[1],
                            pos[1] - 7 - STAR_TYPE_SIZE, STAR_TYPE_SIZE, Palette.LINE_FAINT);
                }
            } else if (st.visited()) {
                drawNodeSparkle(art, pos[0], pos[1], Palette.LINE_DIM, 0, time, sparkPhase);
                art.rect(pos[0] - 1, pos[1] - 1, 2, 2, Palette.STAR_FAR);
                if (!isSun) {
                    drawStarLabelHover(buffer, st.nameZh(), pos[0], pos[1], pos[1] + 8,
                            STAR_NAME_SIZE, Palette.LINE_DIM);
                    // 走过的节点也要能看类型：名字 + 上方关卡类型一起悬浮出现
                    drawStarLabelHover(buffer, starTypeLabel(st.type()), pos[0], pos[1],
                            pos[1] - 7 - STAR_TYPE_SIZE, STAR_TYPE_SIZE, Palette.LINE_DIM);
                }
                if (canJump) {
                    // 回头路：圈内已访问的星可折返（暗框提示）
                    art.frame(pos[0] - 5, pos[1] - 5, 11, 11, Palette.LINE_FAINT);
                }
            } else if (canJump && !coreLocked) {
                drawStarGlow(art, pos[0], pos[1], typeColor, typeColor, 3);
                drawNodeSparkle(art, pos[0], pos[1], typeColor, 2, time, sparkPhase);
                art.frame(pos[0] - 6, pos[1] - 6, 13, 13, Palette.CYAN_DIM);
                if (!isSun) {
                    drawStarLabelHover(buffer, st.nameZh(), pos[0], pos[1], pos[1] + 8,
                            STAR_NAME_SIZE, typeColor);
                    drawStarLabelHover(buffer, starTypeLabel(st.type()), pos[0], pos[1],
                            pos[1] - 7 - STAR_TYPE_SIZE, STAR_TYPE_SIZE, Palette.LINE_FAINT);
                }
            } else {
                drawNodeSparkle(art, pos[0], pos[1], Palette.LINE_FAINT, 0, time, sparkPhase);
                art.frame(pos[0] - 2, pos[1] - 2, 5, 5, Palette.STAR_FAR);
                if (st.type() == Run.NodeType.BOSS) {
                    art.frame(pos[0] - 4, pos[1] - 4, 9, 9, Palette.FOE_SHADE);
                }
            }
        }
        // 星区标签：五个扇区各标一个势力名（淡色，不干扰节点阅读）
        String[] zoneNames = com.hysx.salvosequence.fleet.FactionCatalog.ZONES;
        for (int zi = 0; zi < 5; zi++) {
            double midTheta = (zi + 0.5) * Math.PI * 2 / 5;
            int zx = (int) (starCenterX + Math.cos(midTheta) * 210 * starZoom);
            int zy = (int) (starCenterY + Math.sin(midTheta) * 210 * starZoom
                    * Run.GALAXY_SQUASH);
            // 字体下限 12px：宽度按 12 号量（曾按 9 量 → 居中偏移 + 防裁剪判断失效）
            int lw = (int) Math.ceil(textWidth(zoneNames[zi], 12));
            if (zx - lw / 2 > 4 && zx + lw / 2 < 636 && zy > 24 && zy < 340) {
                zh(buffer, zoneNames[zi], zx - lw / 2, zy, 12, Palette.LINE_FAINT);
            }
        }
        // 核心屏障圈：虚线画在 CORE_BARRIER 半径处——让玩家看见屏障在哪
        if (!run.gatePassed()) {
            int barrierR = (int) (30 * z);
            drawDashedCircle(art, gx, gy, barrierR, (int) (barrierR * Run.GALAXY_SQUASH));
        }
        // 核心区节点锁定视觉：未过太阳系舰队前，屏障内节点画为灰色锁定态
        boolean gateOk = run.gatePassed();
        for (var st : run.starMap()) {
            if (st.radius() >= 30 || gateOk) { continue; }
            int[] lp = starScreenPos(st);
            art.frame(lp[0] - 2, lp[1] - 2, 5, 5, Palette.LINE_FAINT);
        }
        if (sunVisible) {
            drawSunMarker(art, buffer, time, taken);
        }
        zh(buffer, Language.t("星图  探索 " + run.visitedCount() + "/" + run.starMap().size()
                        + "  合金 " + run.alloy(),
                "STAR MAP  EXPLORED " + run.visitedCount() + "/" + run.starMap().size()
                        + "  ALLOY " + run.alloy()), 8, 6, 12, Palette.CYAN);
        String zoomHint = starZoom > 1.001
                ? Language.t(" · 拖动平移", " · DRAG TO PAN")
                : Language.t(" · 已到最小", " · MIN ZOOM");
        zh(buffer, Language.t("滚轮缩放 x" + String.format("%.1f", starZoom),
                        "SCROLL ZOOM x" + String.format("%.1f", starZoom))
                + zoomHint
                + (stage() == Run.Stage.MAP
                        ? Language.t(" · 点击邻近恒星跃迁", " · CLICK A NEARBY STAR TO JUMP")
                        : Language.t(" · M 返回", " · M BACK"))
                + Language.t(" · F10 太阳 · ESC 设置", " · F10 SUN · ESC SETTINGS"), 8, 344, 12,
                Palette.LINE_DIM);
    }

    /** 恒星屏幕坐标：极坐标 -> 全屏（视口中心 + 半径乘缩放，y 压扁成星系）。 */
    private int[] starScreenPos(Run.StarNode st) {
        double z = starZoom;
        return new int[]{
                (int) Math.rint(starCenterX + Math.cos(st.theta()) * st.radius() * z),
                (int) Math.rint(starCenterY
                        + Math.sin(st.theta()) * st.radius() * Run.GALAXY_SQUASH * z)};
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
        return new int[]{
                (int) Math.rint(starCenterX + x * starZoom),
                (int) Math.rint(starCenterY + y * starZoom)};
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
            case ELITE -> Palette.WARN;
            case EVENT -> Palette.OK;
            case REST -> Palette.BLUE;
            case SHOP -> Palette.SHIP_GLOW;
            case BOSS -> Palette.DANGER;
            default -> Palette.LINE;
        };
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
        for (int i = 2; i <= horiz; i++) {
            var c = i >= horiz - 1 ? Palette.LINE_FAINT : pulse > 0.72 ? Palette.LINE : color;
            art.pixel(x + i, y, c);
            art.pixel(x - i, y, c);
        }
        for (int i = 2; i <= vert; i++) {
            var c = i >= vert - 1 ? Palette.LINE_FAINT : pulse > 0.72 ? Palette.LINE : color;
            art.pixel(x, y + i, c);
            art.pixel(x, y - i, c);
        }
        int diag = Math.min(2 + (int) Math.round(pulse * 2 + strength), 6);
        for (int i = 2; i <= diag; i++) {
            var c = i >= diag ? Palette.LINE_FAINT : color;
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
        String left = Language.t(
                "回合 " + battle.turn() + "  格挡 " + battle.block() + "  瞄准 " + battle.aim(),
                "TURN " + battle.turn() + "  BLK " + battle.block() + "  AIM " + battle.aim());
        String right = Language.t(
                "装填 " + battle.loadingZone().size() + "  牌库 " + battle.drawPile().size()
                        + "  弃牌 " + battle.discard().size(),
                "LOAD " + battle.loadingZone().size() + "  DRAW " + battle.drawPile().size()
                        + "  DISC " + battle.discard().size());
        zh(buffer, left, STATUS_X, STATUS_Y + 2, 12, Palette.LINE_DIM);
        zh(buffer, right, STATUS_X, STATUS_Y + 2 + STATUS_PITCH, 12, Palette.LINE_DIM);
        zh(buffer, Language.t(
                        battle.freeFire() ? "自由开火 ×1.2" : "集火",
                        battle.freeFire() ? "FREE FIRE x1.2" : "FOCUS FIRE"),
                STATUS_X, STATUS_Y + 2 + STATUS_PITCH * 2, 12,
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
        String skillLabel = !signature
                ? Language.t("舰长技·校射 (S)", "CAPTAIN SKILL · FOCUS (S)")
                : Language.t("舰长技·" + sig.nameZh() + " (S)",
                        "CAPTAIN · " + sig.nameZh() + " (S)");
        button(art, buffer, BTN_X, SKILL_BTN_Y, BTN_W, BTN_H, skillLabel,
                hoveredSkill || skillReady ? Palette.CYAN : Palette.LINE_FAINT);
        if (battle.shipSkillAvailable()) {
            button(art, buffer, BTN_X, TURN_BTN_Y - 22, BTN_W, BTN_H,
                    Language.t("舰船技 (X)", "SHIP SKILL (X)"), Palette.WARN);
        }
        button(art, buffer, BTN_X, TURN_BTN_Y, BTN_W, BTN_H,
                Language.t("结束回合 (空格)", "END TURN (SPACE)"), Palette.OK);
        zh(buffer, Language.t("G 火力  ←→ 换目标  Z 放大",
                        "G FIRE  ←→ TARGET  Z ZOOM"),
                BTN_X, TURN_BTN_Y + BTN_H + 4, 12, Palette.LINE_FAINT);
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
        int rowY = CARD_Y + CARD_SIZE + 4;
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
        com.hysx.salvosequence.core.Color edge = hovered ? Palette.CYAN : Palette.LINE_DIM;
        art.rect(x, y, size, size, Palette.PANEL_ALT);
        art.frame(x, y, size, size, edge);
        // 费用徽标
        art.rect(x + 1, y + 1, 9, 9, playable ? Palette.WARN : Palette.PANEL_EDGE);
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
        // 1) 悬浮的战利品/手牌
        int option = hoveredOption;
        List<int[]> boxes = optionRects();
        if (option >= 0 && option < boxes.size() && stage() == Run.Stage.BATTLE
                && option < hand().size()) {
            Card card = hand().get(option);
            Battle battle = run.battle();
            List<String> lines = new ArrayList<>(wrap(card.textZh(), 15));
            if (battle != null) {
                lines.add("费用 " + card.cost() + "  倍率 x" + battle.multiplierOfCardId(card.id())
                        + "  装填 " + card.reload());
            }
            renderDesc(buffer, art, Language.t(card.nameZh(), card.nameEn()), Palette.CYAN, lines);
            return;
        }
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
        art.rect(x, y, DESC_W, h, Palette.PANEL);
        art.frame(x, y, DESC_W, h, Palette.LINE_DIM);
        zh(buffer, title, x + 6, y + 4, 12, titleColor);
        int ty = y + 20;
        for (String line : lines) {
            zh(buffer, line, x + 6, ty, 12, Palette.LINE);
            ty += 14;
        }
    }

    // ---------------------------------------------------------------- 覆盖层

    private void drawOverlay(CanvasBuffer buffer, PixelArt art) {
        art.rect(0, 0, 640, 360, PixelArt.fromArgb(0x99000000));
        String title = switch (stage()) {
            case PICK_LOADOUT -> Language.t("选择主角：每人一套起始舰队",
                    "CHOOSE CAPTAIN: EACH WITH A STARTER FLEET");
            case SPACE_PORT -> Language.t("太空港", "SPACE PORT");
            case EVENT -> Language.t("事件", "EVENT");
            case MAP -> Language.t("星图", "STAR MAP");
            case REWARD -> Language.t("战利品：一艘船 / 一条强化 / 一张卡",
                    "REWARD: A SHIP / AN UPGRADE / A CARD");
            case SLOT -> Language.t("编入「" + run.pending().ship().nameZh() + "」：挑个位置",
                    "ADD \"" + run.pending().ship().nameEn() + "\": PICK A SPOT");
            case WON -> Language.t("通关：航程走完，舰队活下来了",
                    "VICTORY: ROUTE CLEARED, FLEET SURVIVED");
            case LOST -> Language.t("舰队全灭", "FLEET DESTROYED");
            case BATTLE -> "";
        };
        zh(buffer, title, OV_X, OV_Y - 20, 12, Palette.CYAN);

        List<int[]> boxes = optionRects();
        for (int i = 0; i < boxes.size(); i++) {
            int[] box = boxes.get(i);
            boolean hovered = i == hoveredOption;
            art.rect(box[0], box[1], box[2], box[3], Palette.BACKDROP);
            art.frame(box[0], box[1], box[2], box[3], hovered ? Palette.CYAN : Palette.PANEL_EDGE);
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
                ShipCatalog.Starter starter = run.starters().get(index);
                Fleet fleet = starter.fleet();
                String head = (index + 1) + "  "
                        + Language.t(starter.nameZh(), starter.nameEn());
                zh(buffer, head, x + 6, y + 3, 12, Palette.LINE);
                double hx = x + 6 + textWidth(head, 12) + 10;
                String countText = Language.t(fleet.size() + " 艘", fleet.size() + " SHIPS");
                zh(buffer, countText, (int) hx, y + 2, 12, Palette.LINE_FAINT);
                String desc = starter.desc() == null ? "" : starter.desc();
                int cap = Math.max(1, (w - 14) / 12);
                if (desc.length() <= cap) {
                    zh(buffer, fitted(desc, w - 14), x + 6, y + 19, 10, Palette.LINE_DIM);
                } else {
                    zh(buffer, fitted(desc.substring(0, cap), w - 14), x + 6, y + 19, 10,
                            Palette.LINE_DIM);
                    zh(buffer, fitted(desc.substring(cap), w - 14), x + 6, y + 30, 10,
                            Palette.LINE_DIM);
                }
                String stats = Language.t(
                        "结构 " + fleet.maxHp() + " 护甲 " + fleet.armor()
                                + " 能量 " + fleet.maxEnergy() + " 牌组 " + fleet.deckSize(),
                        "HP " + fleet.maxHp() + " AR " + fleet.armor()
                                + " EN " + fleet.maxEnergy() + " DECK " + fleet.deckSize());
                zh(buffer, stats, x + 6, y + 41, 12, Palette.OK);
            }
            case REWARD -> {
                Reward reward = run.rewards().get(index);
                // 左边一列缩略图（2 格 = 36 点），文字一律从 x+48 起，别压到图上
                if (reward.ship() != null) {
                    ShipPainter.drawIcon(art, reward.ship(), x + 6, y + 4, 2, time);
                } else {
                    art.rect(x + 6, y + 4, 36, 20, Palette.PANEL_ALT);
                    art.frame(x + 6, y + 4, 36, 20, Palette.CYAN_DIM);
                    zh(buffer, Language.t("卡牌", "CARD"), x + 10, y + 8, 12, Palette.CYAN);
                }
                int textX = x + 48;
                String rewardTitle = Language.t((index + 1) + "  " + reward.titleZh(),
                        (index + 1) + "  " + reward.subtitleEn());
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
                zh(buffer, (index + 1) + "  " + spot.x() + "/" + spot.y(), x + 6, y + 3, 12,
                        Palette.LINE);
                zh(buffer, Language.t(spot.w() + "×" + spot.h(), spot.w() + "x" + spot.h()),
                        x + 70, y + 3, 12, Palette.CYAN_DIM);
                zh(buffer, Language.t("空位", "EMPTY"), x + 120, y + 3, 12, Palette.LINE_DIM);
                en(art, index == slotPreview ? "<--" : "", x + w - 30, y + 5,
                        index == slotPreview ? Palette.CYAN : Palette.LINE_FAINT);
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
        } else if (stage() == Run.Stage.BATTLE) {
            hint = Language.t(
                    "1-6 / 点卡 出牌 · 空格 结束回合 · S 技能 · ←→ 换目标 · 拖船变阵 · G 火力 · Z 放大 · ESC 设置",
                    "1-6 / CLICK CARD · SPACE END TURN · S SKILL · ←→ TARGET · G FIRE · Z ZOOM · ESC SETTINGS");
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
        zh(buffer, hint, 8, 348, 12, Palette.LINE_FAINT);
        if (!debug) {
            return;
        }
        Battle battle = run.battle();
        if (battle != null) {
            zh(buffer, Language.t("G:自由 F:", "G:FREE F:") + foesAlive(),
                    500, 348, 12, Palette.LINE_FAINT);
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
        art.rect(x, 322, w, 16, Palette.BACKDROP);
        art.frame(x, 322, w, 16, Palette.CYAN_DIM);
        zh(buffer, toast, x + 6, 324, 12, Palette.CYAN);
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
        art.polygon(new double[]{x, x + w, x + w - 6, x - 6},
                new double[]{y, y, y + h, y + h}, Palette.PANEL_ALT);
        art.polygonOutline(new double[]{x, x + w, x + w - 6, x - 6},
                new double[]{y, y, y + h, y + h}, color);
        zh(buffer, label, x + 8, y + 3, 12, color);
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
        return new int[]{OUR_BOARD_X, BOARD_Y, BOARD_CELL};
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
