package com.hysx.salvosequence.screen;

import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.core.PixelFont;
import com.hysx.salvosequence.fleet.AppearanceDesign;
import com.hysx.salvosequence.fleet.Fleet;
import com.hysx.salvosequence.fleet.FleetBoard;
import com.hysx.salvosequence.fleet.FleetUnit;
import com.hysx.salvosequence.fleet.Perk;
import com.hysx.salvosequence.fleet.ShipArtStore;
import com.hysx.salvosequence.fleet.ShipCatalog;
import com.hysx.salvosequence.render.HotspotSink;
import com.hysx.salvosequence.ui.Language;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 舰队编排界面（改造前的「模块建造」）。
 *
 * <p>改造前这一屏是"把模块拖到船体的功能区里"——模块只能在自己那片区域里挪。
 * 现在模块变成了船：<b>棋盘上摆的是船</b>，一格就是 16×16 阵里的一格，
 * 任何船摆哪儿都行，只要不出界、不压住别的船。</p>
 *
 * <p>摆位会改变相邻加成（装甲舰贴指挥舰 +结构值、炮舰贴装甲舰 +齐射、
 * 炮舰贴动力舰 +护盾），所以这一屏同时把"加成吃到了多少"写在右边。</p>
 *
 * <p>四支起始舰队各有一份编排（{@code ships/<舰队 id>.fleet.txt}），
 * {@code [ ]} 或面板上的四行切换。</p>
 */
public final class FleetArrangementScreen implements Screen {

    public static final String SCREEN_NAME = "FLEET";

    private static final int CANVAS_X = 10;
    private static final int CANVAS_Y = 34;
    private static final int CELL = 16;
    private static final int PANEL_X = 308;
    private static final int PANEL_W = 322;
    private static final int PANEL_Y = 34;
    private static final int ROW_H = 20;
    private static final int SHIP_ROW_H = 15;
    private static final int FLEET_ROW_Y = 72;
    private static final int BTN_Y = 304;
    private static final int CONFIRM_X = 308;
    private static final int CONFIRM_W = 150;
    private static final int RESET_X = 466;
    private static final int RESET_W = 164;
    private static final int BTN_H = 22;

    private final PixelFont font = new PixelFont();

    private int fleetIndex;
    private Fleet fleet;
    /** 从关卡内「战术休整」进来时绑定的舰队；非 null 表示不是菜单里的编排界面。 */
    private final Fleet boundFleet;
    private int selected;
    private int dragOffsetX;
    private int dragOffsetY;
    private boolean dragging;
    private String status = "";
    private double statusLife;
    private double time;
    private boolean hotspotsDirty = true;
    private ScreenContext pending;

    public FleetArrangementScreen() {
        this.boundFleet = null;
        reload();
    }

    /**
     * 关卡内「战术休整」用的构造器：直接编排这一局正在用的舰队。
     * 返回时回的是那局关卡本身（{@link ScreenContext.Target#CARD_RESUME}），不会重开一局。
     */
    public FleetArrangementScreen(Fleet fleet) {
        this.boundFleet = fleet;
        this.fleet = fleet;
        this.selected = 0;
        ShipPainter.reload();
        hotspotsDirty = true;
    }

    /** 是否是从关卡里进来改编队的。 */
    public boolean forRun() {
        return boundFleet != null;
    }

    @Override
    public String name() {
        return SCREEN_NAME;
    }

    /** 读当前舰队的编排（有存档用存档，没有用出厂自动铺位）。 */
    private void reload() {
        if (boundFleet != null) {
            // 关卡内：编排的就是这一局舰队，别用存档覆盖它
            fleet = boundFleet;
            selected = 0;
            dragging = false;
            ShipPainter.reload();
            hotspotsDirty = true;
            return;
        }
        fleet = ShipCatalog.starter(fleetIndex).fleet();
        selected = 0;
        dragging = false;
        ShipPainter.reload();
        hotspotsDirty = true;
    }

    /** 现在编排的是第几支舰队。 */
    public int fleetIndex() {
        return fleetIndex;
    }

    /** 当前舰队。 */
    public Fleet fleet() {
        return fleet;
    }

    /** 当前选中的第几艘。 */
    public int selectedIndex() {
        return selected;
    }

    // ---------------------------------------------------------------- 交互

    public List<FleetUnit> units() {
        return fleet.units();
    }

    private void select(int index) {
        if (index >= 0 && index < fleet.units().size()) {
            selected = index;
            hotspotsDirty = true;
        }
    }

    private void selectShipAt(int cx, int cy) {
        List<FleetUnit> units = fleet.units();
        for (int i = 0; i < units.size(); i++) {
            if (units.get(i).covers(cx, cy)) {
                selected = i;
                return;
            }
        }
    }

    /**
     * 把第 {@code index} 艘挪到 (x,y)。非法（出界/压住别人）返回 {@code false} 并留在原处。
     */
    public boolean moveTo(int index, int x, int y) {
        List<FleetUnit> units = fleet.units();
        if (index < 0 || index >= units.size()) {
            return false;
        }
        Fleet moved = fleet.movedUnit(units.get(index).id(), x, y);
        if (moved == null) {
            return false;
        }
        fleet = moved;
        return true;
    }

    /** 键盘微调：先试目标格，不行按步长往两边找（"贴着走"）。 */
    private void nudge(int dx, int dy) {
        List<FleetUnit> units = fleet.units();
        if (selected < 0 || selected >= units.size()) {
            return;
        }
        FleetUnit current = units.get(selected);
        if (moveTo(selected, current.x() + dx, current.y() + dy)) {
            return;
        }
        for (int step = 1; step <= 8; step++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                int tx = current.x() + dx * step * sign;
                int ty = current.y() + dy * step * sign;
                if (moveTo(selected, tx, ty)) {
                    return;
                }
            }
        }
    }

    private void pressAt(double mx, double my) {
        int cx = (int) ((mx - CANVAS_X) / CELL);
        int cy = (int) ((my - CANVAS_Y) / CELL);
        if (cx < 0 || cy < 0 || cx >= FleetBoard.SIZE || cy >= FleetBoard.SIZE) {
            return;
        }
        FleetUnit under = fleet.board().at(cx, cy);
        if (under == null) {
            return;
        }
        selectShipAt(cx, cy);
        dragging = true;
        dragOffsetX = cx - under.x();
        dragOffsetY = cy - under.y();
    }

    private void dragTo(double mx, double my) {
        if (!dragging) {
            return;
        }
        int cx = (int) ((mx - CANVAS_X) / CELL) - dragOffsetX;
        int cy = (int) ((my - CANVAS_Y) / CELL) - dragOffsetY;
        moveTo(selected, cx, cy);   // 不合法就留在原处，不打断手
    }

    private void release() {
        dragging = false;
    }

    /** 存盘并进设计器（Enter/空格）。 */
    public void confirm() {
        if (boundFleet != null) {
            // 关卡内改编队：只改这一局，不写盘、不跳外观设计，直接回战斗界面
            say("编队已调整");
            pending = ScreenContext.go(ScreenContext.Target.CARD_RESUME);
            return;
        }
        boolean saved = ShipArtStore.trySaveFleet(fleet);
        Path path = ShipArtStore.fleetPath(fleet).toAbsolutePath();
        say(saved ? "编排已存盘：" + path + " → 接着画外观" : "存盘失败（目录可能只读），先退出");
        pending = ScreenContext.go(ScreenContext.Target.DESIGNER);
    }

    private void resetArrangement() {
        if (boundFleet != null) {
            say("关卡内不能恢复自动铺位：会影响这局的摆位");
            return;
        }
        ShipArtStore.resetFleet(fleet.id());
        reload();
        say("已恢复成自动铺位");
    }

    private void shiftFleet(int delta) {
        if (boundFleet != null) {
            say("关卡内不能换舰队");
            return;
        }
        int count = ShipCatalog.starterCount();
        fleetIndex = Math.floorMod(fleetIndex + delta, count);
        reload();
        say("换舰队：" + ShipCatalog.starter(fleetIndex).nameZh());
    }

    private void say(String message) {
        status = message;
        statusLife = 4.0;
    }

    // ---------------------------------------------------------------- 帧循环

    @Override
    public void onEnter() {
        reload();
    }

    @Override
    public void update(double dt) {
        time += dt;
        if (statusLife > 0) {
            statusLife -= dt;
        }
    }

    @Override
    public ScreenContext onKey(String code) {
        switch (code) {
            case "DIGIT1", "DIGIT2", "DIGIT3", "DIGIT4", "DIGIT5", "DIGIT6",
                 "DIGIT7", "DIGIT8", "DIGIT9" -> {
                select(code.charAt(5) - '1');
                return null;
            }
            case "UP" -> {
                nudge(0, -1);
                return null;
            }
            case "DOWN" -> {
                nudge(0, 1);
                return null;
            }
            case "LEFT" -> {
                nudge(-1, 0);
                return null;
            }
            case "RIGHT" -> {
                nudge(1, 0);
                return null;
            }
            case "ENTER", "SPACE" -> {
                confirm();
                return null;
            }
            case "R" -> {
                resetArrangement();
                return null;
            }
            case "OPEN_BRACKET", "COMMA" -> {
                shiftFleet(-1);
                return null;
            }
            case "CLOSE_BRACKET", "PERIOD" -> {
                shiftFleet(1);
                return null;
            }
            case "ESCAPE" -> {
                if (boundFleet != null) {
                    return ScreenContext.go(ScreenContext.Target.CARD_RESUME);
                }
                return ScreenContext.go(ScreenContext.Target.START);
            }
            default -> {
                return null;
            }
        }
    }

    @Override
    public ScreenContext pollTransition() {
        ScreenContext next = pending;
        pending = null;
        return next;
    }

    @Override
    public boolean consumeHotspotDirty() {
        boolean dirty = hotspotsDirty;
        hotspotsDirty = false;
        return dirty;
    }

    @Override
    public void collectHotspots(HotspotSink sink) {
        int span = FleetBoard.SIZE * CELL;
        sink.drag(CANVAS_X, CANVAS_Y, span, span,
                (mx, my) -> dragTo(mx, my),
                (mx, my) -> {
                    pressAt(mx, my);
                    // 按下即松（单击）也要能选中：管理器没有单独的 release 回调，
                    // 所以这里把 press 当作一次点击的开始，拖动的结束由下一帧 update 兜底。
                });
        // 注意：不要再给每艘船单独 add 热区。它们会压在画布那块 drag 热区上面，
        // 把 mousePressed 吞掉 → pressAt 收不到 → 拖不动。选中在 pressAt 里做。
        if (boundFleet == null) {
            for (int i = 0; i < ShipCatalog.starterCount(); i++) {
                int index = i;
                sink.add(PANEL_X, FLEET_ROW_Y + i * ROW_H, PANEL_W - 20, ROW_H - 2, () -> {
                    fleetIndex = index;
                    reload();
                    say("换舰队：" + ShipCatalog.starter(index).nameZh());
                });
            }
        }
        sink.add(CONFIRM_X, BTN_Y, CONFIRM_W, BTN_H, this::confirm);
        sink.add(RESET_X, BTN_Y, RESET_W, BTN_H, this::resetArrangement);
    }

    // ---------------------------------------------------------------- 绘制

    @Override
    public void draw(CanvasBuffer buffer) {
        PixelArt art = buffer.art();
        art.fillRect(0, 0, 640, 360, Palette.BACKDROP);
        drawCanvasFrame(art);
        drawTopBar(buffer, art);
        drawCanvas(buffer, art);
        drawPanel(buffer, art);
        drawFooter(buffer);
        drawStatus(buffer);
    }

    private void drawCanvasFrame(PixelArt art) {
        int span = FleetBoard.SIZE * CELL;
        art.rect(CANVAS_X - 6, CANVAS_Y - 6, span + 12, span + 12, Palette.PANEL);
        art.frame(CANVAS_X - 6, CANVAS_Y - 6, span + 12, span + 12, Palette.LINE_FAINT);
        art.rect(PANEL_X - 10, PANEL_Y - 10, PANEL_W + 16, 310, Palette.PANEL);
        art.frame(PANEL_X - 10, PANEL_Y - 10, PANEL_W + 16, 310, Palette.LINE_FAINT);
    }

    private void drawTopBar(CanvasBuffer buffer, PixelArt art) {
        // 一行排版：每一段都按实测字宽往后推，别再写死 x（换舰队名长度一变就压字）
        String title = forRun()
                ? Language.t("战术休整 · 编队", "LULL · FLEET")
                : Language.t("舰队编排", "FLEET LAYOUT");
        zh(buffer, title, 8, 5, 14, Palette.CYAN);
        if (forRun()) {
            zh(buffer, Language.t(fleet.size() + " 艘 · 摆好按「回战场」",
                            fleet.size() + " SHIPS · PRESS BACK TO BATTLE"),
                    8 + textWidth(title, 14) + 22, 6, 12, Palette.LINE_DIM);
            return;
        }
        double x = 8 + textWidth(title, 14) + 14;
        zh(buffer, "<", (int) x, 6, 14, Palette.CYAN_DIM);
        x += 14;
        ShipCatalog.Starter starter = ShipCatalog.starter(fleetIndex);
        zh(buffer, Language.t(starter.nameZh(), starter.nameEn()), x, 5, 12, Palette.LINE);
        x += textWidth(Language.t(starter.nameZh(), starter.nameEn()), 12) + 10;
        zh(buffer, ">", (int) x, 6, 14, Palette.CYAN_DIM);
        x += 18;
        zh(buffer, Language.t(fleet.size() + " 艘 · 摆好按「确定」",
                fleet.size() + " SHIPS · PRESS OK"), x, 6, 12, Palette.LINE_DIM);
    }

    private void drawCanvas(CanvasBuffer buffer, PixelArt art) {
        int span = FleetBoard.SIZE * CELL;
        // 底：暗格 + 每 4 格一道引导线
        for (int cy = 0; cy < FleetBoard.SIZE; cy++) {
            for (int cx = 0; cx < FleetBoard.SIZE; cx++) {
                int x = CANVAS_X + cx * CELL;
                int y = CANVAS_Y + cy * CELL;
                art.rect(x, y, CELL - 1, CELL - 1, Palette.PANEL_ALT);
                if (cx % 4 == 0 || cy % 4 == 0) {
                    art.pixel(x, y, Palette.LINE_FAINT);
                }
            }
        }
        // 船：按自己的外观图铺进去（和我方/敌方棋盘同一套画法）
        List<FleetUnit> units = fleet.units();
        for (int i = 0; i < units.size(); i++) {
            FleetUnit unit = units.get(i);
            AppearanceDesign look = ShipPainter.lookOf(unit.ship());
            int x = CANVAS_X + unit.x() * CELL;
            int y = CANVAS_Y + unit.y() * CELL;
            if (look == null || look.isBlank()) {
                ShipPainter.drawPlaceholder(art, unit.ship(), x, y, CELL, Palette.SHIP_SHADE);
            } else {
                ShipPainter.draw(art, look, x, y, CELL, time);
            }
            if (i == selected) {
                art.frame(x - 1, y - 1, unit.w() * CELL + 1, unit.h() * CELL + 1,
                        dragging ? Palette.OK : Palette.CYAN);
                framePerkRange(art, unit);
            }
            // 名字：小船上不写（会糊），3 格以上才写格子编号
            if (unit.w() * unit.h() >= 6) {
                en(art, String.valueOf(i + 1), x + 2, y + 2, Palette.BACKDROP);
                en(art, String.valueOf(i + 1), x + 1, y + 1, Palette.LINE);
            }
        }
        art.frame(CANVAS_X, CANVAS_Y, span, span, Palette.LINE_DIM);
        // 选中船的光环读数（生效范围已经在棋盘上用黄框画出）
        if (selected >= 0 && selected < units.size()) {
            FleetUnit sel = units.get(selected);
            String perks = Perk.labelsOf(sel.ship().perks());
            zh(buffer, Language.t(sel.nameZh() + " 光环 ",
                            sel.ship().nameEn() + " AURA ")
                    + (perks.isEmpty() ? "—" : perks)
                    + Language.t("（生效范围已框出）", " (RANGE HIGHLIGHTED)"),
                    CANVAS_X, CANVAS_Y + span + 6, 12, Palette.WARN);
        }
    }

    /** 把选中船的光环生效范围框出来：每艘被罩到的船描一圈黄框。 */
    private void framePerkRange(PixelArt art, FleetUnit source) {
        for (FleetUnit other : fleet.units()) {
            if (other.id().equals(source.id())) {
                continue;
            }
            boolean covered = false;
            for (Perk perk : source.ship().perks()) {
                if (FleetBoard.inRange(source, other, perk.range())) {
                    covered = true;
                    break;
                }
            }
            if (covered) {
                art.frame(CANVAS_X + other.x() * CELL - 1,
                        CANVAS_Y + other.y() * CELL - 1,
                        other.w() * CELL + 1, other.h() * CELL + 1, Palette.WARN);
            }
        }
    }

    private void drawPanel(CanvasBuffer buffer, PixelArt art) {
        int y = FLEET_ROW_Y - 18;
        if (!forRun()) {
            zh(buffer, Language.t("舰队（[ ] 或点一行切换）", "FLEET ([ ] OR CLICK A ROW TO SWITCH)"),
                    PANEL_X, y, 12, Palette.LINE_DIM);
        }
        for (int i = 0; i < (forRun() ? 0 : ShipCatalog.starterCount()); i++) {
            ShipCatalog.Starter starter = ShipCatalog.starter(i);
            int rowY = FLEET_ROW_Y + i * ROW_H;
            boolean on = i == fleetIndex;
            if (on) {
                art.rect(PANEL_X - 6, rowY - 3, PANEL_W - 14, ROW_H - 2, Palette.PANEL_ALT);
                art.frame(PANEL_X - 6, rowY - 3, PANEL_W - 14, ROW_H - 2, Palette.CYAN_DIM);
            }
            en(art, String.valueOf(i + 1), PANEL_X, rowY + 4, on ? Palette.CYAN : Palette.LINE_FAINT);
            zh(buffer, Language.t(starter.nameZh(), starter.nameEn()), PANEL_X + 14, rowY, 12,
                    on ? Palette.LINE : Palette.LINE_DIM);
            zh(buffer, Language.t(starter.fleet().size() + " 艘", starter.fleet().size() + " SHIPS"),
                    PANEL_X + 150, rowY, 12, Palette.LINE_FAINT);
        }

        // 船列表（关卡内没有上面的舰队列表，直接顶到面板开头）
        int listY = forRun()
                ? FLEET_ROW_Y - 4
                : FLEET_ROW_Y + ShipCatalog.starterCount() * ROW_H + 12;
        zh(buffer, Language.t("船（1-9 选中，方向键微调）", "SHIPS (1-9 SELECT, ARROWS NUDGE)"),
                PANEL_X, listY, 12, Palette.LINE_DIM);
        listY += 15;
        List<FleetUnit> units = fleet.units();
        for (int i = 0; i < units.size() && i < 8; i++) {
            FleetUnit unit = units.get(i);
            int rowY = listY + i * SHIP_ROW_H;
            boolean on = i == selected;
            if (on) {
                art.rect(PANEL_X - 6, rowY - 3, PANEL_W - 14, SHIP_ROW_H - 1, Palette.PANEL_ALT);
                art.frame(PANEL_X - 6, rowY - 3, PANEL_W - 14, SHIP_ROW_H - 1, Palette.CYAN_DIM);
            }
            en(art, String.valueOf(i + 1), PANEL_X, rowY + 1, on ? Palette.CYAN : Palette.LINE_FAINT);
            zh(buffer, unit.nameZh(), PANEL_X + 14, rowY - 2, 12, on ? Palette.LINE : Palette.LINE_DIM);
            zh(buffer, Language.t(unit.w() + "×" + unit.h() + "  @" + unit.x() + ","
                                    + unit.y() + "  结构 " + unit.maxHp()
                                    + "  级" + unit.level(),
                            unit.w() + "x" + unit.h() + "  @" + unit.x() + ","
                                    + unit.y() + "  HP " + unit.maxHp()
                                    + "  Lv" + unit.level()),
                    PANEL_X + 118, rowY - 2, 12, Palette.LINE_FAINT);
        }

        // 合计与相邻加成（一行合计、一行加成：两者都要留在按钮上方）
        int sumY = listY + Math.min(units.size(), 8) * SHIP_ROW_H + 7;
        zh(buffer, Language.t("合计 结构 " + fleet.maxHp()
                        + "  护甲 " + fleet.armor()
                        + "  能量 " + fleet.maxEnergy()
                        + "  手牌 " + fleet.handSize()
                        + "  牌组 " + fleet.deckSize(),
                "TOTAL HP " + fleet.maxHp()
                        + "  AR " + fleet.armor()
                        + "  EN " + fleet.maxEnergy()
                        + "  DRAW " + fleet.handSize()
                        + "  DECK " + fleet.deckSize()),
                PANEL_X, sumY, 12, Palette.CYAN_DIM);
        StringBuilder near = new StringBuilder(Language.t("相邻 齐射+", "ADJ VOLLEY+"))
                .append(fleet.adjacencyVolleyBonusTotal())
                .append(Language.t(" 护盾+", " ARMOR+")).append(fleet.adjacencyArmorBonusTotal())
                .append(Language.t(" 结构+", " HP+")).append(fleet.adjacencyHpBonusTotal());
        if (fleet.hasAxial()) {
            near.append(Language.t(" ｜ 轴炮 管", " | AXIAL BARRELS "))
                    .append(fleet.axialBarrels())
                    .append(Language.t(" 口", " MUZZLES "))
                    .append(fleet.axialMuzzles())
                    .append(Language.t(" 连", " LINKS "))
                    .append(fleet.axialLinkPairs())
                    .append(" ×").append(fleet.axialMultiplier());
        }
        zh(buffer, near.toString(), PANEL_X, sumY + 13, 12,
                fleet.hasAxial() ? Palette.WARN : Palette.OK);

        if (forRun()) {
            button(art, buffer, CONFIRM_X, BTN_Y, PANEL_W - 20, BTN_H,
                    Language.t("回战场（Enter）", "BACK TO BATTLE (ENTER)"), Palette.OK);
            return;
        }
        button(art, buffer, CONFIRM_X, BTN_Y, CONFIRM_W, BTN_H,
                Language.t("确定（Enter）", "OK (ENTER)"), Palette.OK);
        button(art, buffer, RESET_X, BTN_Y, RESET_W, BTN_H,
                Language.t("恢复自动铺位（R）", "AUTO LAYOUT (R)"), Palette.CYAN_DIM);
    }

    private void drawFooter(CanvasBuffer buffer) {
        if (forRun()) {
            zh(buffer, Language.t("拖动船 · 方向键微调 · 1-9 选择 · Enter 回战场 · ESC 返回",
                            "DRAG SHIP · ARROWS NUDGE · 1-9 SELECT · ENTER BATTLE · ESC BACK"),
                    8, 340, 12, Palette.LINE_DIM);
            return;
        }
        zh(buffer, Language.t(
                "拖动船 · 方向键微调 · 1-9 选择 · R 复位 · [ ] 换舰队 · Enter 确定 · ESC 返回",
                "DRAG SHIP · ARROWS NUDGE · 1-9 SELECT · R RESET · [ ] FLEET · ENTER OK · ESC BACK"),
                8, 340, 12, Palette.LINE_DIM);
    }

    private void drawStatus(CanvasBuffer buffer) {
        if (statusLife > 0 && !status.isEmpty()) {
            zh(buffer, status, 8, 324, 12, Palette.CYAN);
        }
    }

    private void button(PixelArt art, CanvasBuffer buffer, int x, int y, int w, int h,
                        String label, com.hysx.salvosequence.core.Color color) {
        art.polygon(new double[]{x, x + w, x + w - 6, x - 6},
                new double[]{y, y, y + h, y + h}, Palette.PANEL_ALT);
        art.polygonOutline(new double[]{x, x + w, x + w - 6, x - 6},
                new double[]{y, y, y + h, y + h}, color);
        zh(buffer, label, x + 8, y + 5, 12, color);
    }

    private void zh(CanvasBuffer buffer, String text, double x, double top, double size,
                    com.hysx.salvosequence.core.Color color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        buffer.text(text, x, top + size * 0.92, size, color);
    }

    private void en(PixelArt art, String text, int x, int y, com.hysx.salvosequence.core.Color color) {
        font.draw(art, text, x, y, color);
    }

    /** 估算一段文字的宽度（中文 1em、ASCII 0.56em），用来把后面的东西推开。 */
    private static double textWidth(String text, double size) {
        double w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += text.charAt(i) < 0x2E80 ? size * 0.56 : size;
        }
        return w;
    }

    // ---------------------------------------------------------------- 核对稿件用

    /** 核对稿件用：当前棋盘的字符画。 */
    public String ascii() {
        return fleet.ascii();
    }

    /** 核对稿件用：直接挪一艘（不经过鼠标）。 */
    public void moveForTest(int index, int x, int y) {
        moveTo(index, x, y);
    }

    /** 核对稿件用：换一支舰队。 */
    public void selectFleetForTest(int index) {
        fleetIndex = Math.max(0, Math.min(ShipCatalog.starterCount() - 1, index));
        reload();
    }

    /** 还没有被用到的字段兜底（保持"选中项一定在范围内"）。 */
    void clampSelection() {
        List<FleetUnit> units = fleet.units();
        if (selected >= units.size()) {
            selected = Math.max(0, units.size() - 1);
        }
    }

    /** 绘制用的单位列表快照。 */
    List<FleetUnit> snapshot() {
        return new ArrayList<>(fleet.units());
    }
}
