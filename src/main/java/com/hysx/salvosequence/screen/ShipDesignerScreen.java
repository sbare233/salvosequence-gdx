package com.hysx.salvosequence.screen;

import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.core.PixelFont;
import com.hysx.salvosequence.fleet.AppearanceDesign;
import com.hysx.salvosequence.fleet.Ship;
import com.hysx.salvosequence.fleet.ShipArtStore;
import com.hysx.salvosequence.fleet.ShipCatalog;
import com.hysx.salvosequence.render.HotspotSink;
import com.hysx.salvosequence.ui.Language;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 战舰外观设计器。
 *
 * <p>改造前这一屏画的是"整艘船的涂装"：一张 256×256 的图铺在 16×16 的船体上，
 * 换个船体就换一张图。现在<b>外观绑在船上</b>——每艘船（原模块）各自一张图，
 * 尺寸就是它的占地格数 × {@link AppearanceDesign#CELL}，所以画布大小随船变。
 *
 * <p>画布按整数倍放大铺进 256 点见方的区域（小船放大得多），落笔坐标
 * 反着除一次缩放就回到图上的像素，不会有半个像素的误差。</p>
 *
 * <p>没有炮塔画布了：炮塔绘制整套删掉。</p>
 */
public final class ShipDesignerScreen implements Screen {

    public static final String SCREEN_NAME = "DESIGNER";

    private static final int CANVAS_X = 12;
    private static final int CANVAS_Y = 34;
    /** 画布那一块是方的，边长 256 点（= 16 格 × 16，最大的船也铺得下）。 */
    public static final int CANVAS_SIDE = 256;

    private static final int PANEL_X = 312;
    private static final int PANEL_W = 316;
    private static final int SWATCH = 22;
    private static final int SWATCH_GAP = 4;
    private static final int COLS = 8;
    private static final int ROWS = 3;

    private static final int MAX_UNDO = 200;

    /** 16 色预设（外加上面三行灰阶与背景，够画像素画）。 */
    private static final int[] PRESETS = {
            argb(0x0E141B), argb(0x1B2530), argb(0x2C3A4A), argb(0x49586B),
            argb(0x65758A), argb(0x9AA9BB), argb(0xE6EEF7), argb(0xFFFFFF),
            argb(0x2B4F72), argb(0x3E8FB0), argb(0x7FE3F2), argb(0x36D6A8),
            argb(0x9BE86A), argb(0xE8D45A), argb(0xE8A33C), argb(0xD9603A),
            argb(0xB43A5E), argb(0x8046C8), argb(0x5C6BD8), argb(0x2A2FA0),
            argb(0x5B3A22), argb(0x8C6239), argb(0xC8A165), argb(0x3A2E4A),
    };

    private final PixelFont font = new PixelFont();

    private int shipIndex;
    private Ship ship;
    private AppearanceDesign look;
    private int frame;
    private int brush = PRESETS[3];
    private boolean erasing;
    private int channel;            // 0=R 1=G 2=B（滑杆一起动的话没必要选中，但读数要亮一条）
    private Deque<AppearanceDesign> undo = new ArrayDeque<>();
    private int hoverX = -1;
    private int hoverY = -1;
    private int lastPaintX = -1;
    private int lastPaintY = -1;
    private String status = "";
    private double statusLife;
    private double time;
    private boolean hotspotsDirty = true;
    private ScreenContext pending;

    public ShipDesignerScreen() {
        selectShipIndex(0);
    }

    @Override
    public String name() {
        return SCREEN_NAME;
    }

    // ---------------------------------------------------------------- 状态

    private void selectShipIndex(int index) {
        List<Ship> all = ShipCatalog.all();
        shipIndex = Math.floorMod(index, all.size());
        ship = all.get(shipIndex);
        look = ShipArtStore.loadLook(ship);
        frame = 0;
        undo.clear();
        hoverX = -1;
        hoverY = -1;
        hotspotsDirty = true;
    }

    /** 现在画的是哪艘船。 */
    public Ship ship() {
        return ship;
    }

    /** 当前的图（不可变，每笔都换新实例）。 */
    public AppearanceDesign look() {
        return look;
    }

    /** 画布缩放倍数（整数倍，按船的大小自动选）。 */
    public int zoom() {
        int span = Math.max(look.width(), look.height());
        return Math.max(1, Math.min(12, CANVAS_SIDE / span));
    }

    /** 画布左上角（居中铺进去）。 */
    public int canvasX() {
        return CANVAS_X + (CANVAS_SIDE - look.width() * zoom()) / 2;
    }

    public int canvasY() {
        return CANVAS_Y + (CANVAS_SIDE - look.height() * zoom()) / 2;
    }

    // ---------------------------------------------------------------- 笔画

    private void setPixel(int px, int py) {
        if (px < 0 || py < 0 || px >= look.width() || py >= look.height()) {
            return;
        }
        if (px == lastPaintX && py == lastPaintY) {
            return;   // 拖动时同一像素不重复记账（撤销栈会爆）
        }
        int argb = erasing ? AppearanceDesign.TRANSPARENT : brush;
        if (look.pixel(frame, px, py) == argb) {
            lastPaintX = px;
            lastPaintY = py;
            return;
        }
        pushUndo(look);
        look = look.withPixel(frame, px, py, argb);
        lastPaintX = px;
        lastPaintY = py;
        ShipPainter.reload(ship);
        hotspotsDirty = true;
    }

    private void pushUndo(AppearanceDesign previous) {
        if (undo.size() >= MAX_UNDO) {
            undo.removeLast();
        }
        undo.push(previous);
    }

    private void undo() {
        if (undo.isEmpty()) {
            say("没有可撤销的笔画");
            return;
        }
        look = undo.pop();
        ShipPainter.reload(ship);
        hotspotsDirty = true;
    }

    private void canvasPoint(double mx, double my) {
        int z = zoom();
        int px = (int) Math.floor((mx - canvasX()) / z);
        int py = (int) Math.floor((my - canvasY()) / z);
        hoverX = px;
        hoverY = py;
    }

    private void paintAt(double mx, double my) {
        canvasPoint(mx, my);
        setPixel(hoverX, hoverY);
    }

    /** 核对稿件用：把指针停到画布上的某一点（放大镜跟着走），-1/-1 收回。 */
    public void debugHoverAt(double mx, double my) {
        if (mx < 0 || my < 0) {
            hoverX = -1;
            hoverY = -1;
            return;
        }
        canvasPoint(mx, my);
    }

    // ---------------------------------------------------------------- 存档

    private void save() {
        boolean ok = ShipArtStore.trySaveLook(look);
        say(ok ? "已存盘：" + ShipArtStore.lookPath(ship).toAbsolutePath() : "存盘失败（目录可能只读）");
    }

    private void saveAndPlay() {
        if (ShipArtStore.trySaveLook(look)) {
            say("已存盘，开局");
        } else {
            say("存盘失败，直接开局");
        }
        pending = ScreenContext.go(ScreenContext.Target.CARD);
    }

    private void importPng() {
        try {
            AppearanceDesign imported = ShipArtStore.importLook(ship, ship.nameZh(), look.fps());
            pushUndo(look);
            look = imported;
            ShipPainter.reload(ship);
            say("导入：" + ShipArtStore.findImportPng(ship).getFileName());
        } catch (java.io.IOException | RuntimeException e) {
            say("导入失败：" + e.getMessage());
        }
    }

    private void resetToBuiltin() {
        pushUndo(look);
        look = AppearanceDesign.builtin(ship);
        ShipPainter.reload(ship);
        say("已恢复出厂涂装");
    }

    private void say(String message) {
        status = message;
        statusLife = 3.5;
    }

    // ---------------------------------------------------------------- 帧循环

    @Override
    public void onEnter() {
        look = ShipArtStore.loadLook(ship);
        ShipPainter.reload(ship);
        hoverX = -1;
        hoverY = -1;
        hotspotsDirty = true;
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
            case "OPEN_BRACKET", "COMMA" -> {
                selectShipIndex(shipIndex - 1);
                return null;
            }
            case "CLOSE_BRACKET", "PERIOD" -> {
                selectShipIndex(shipIndex + 1);
                return null;
            }
            case "LEFT" -> {
                if (frame > 0) {
                    frame--;
                }
                return null;
            }
            case "RIGHT" -> {
                if (frame + 1 < look.frameCount()) {
                    frame++;
                }
                return null;
            }
            case "UP" -> {
                look = look.withFrameCount(look.frameCount() + 1);
                frame = look.frameCount() - 1;
                ShipPainter.reload(ship);
                return null;
            }
            case "DOWN" -> {
                if (look.frameCount() > 1) {
                    look = look.withFrameCount(look.frameCount() - 1);
                    frame = Math.min(frame, look.frameCount() - 1);
                    ShipPainter.reload(ship);
                }
                return null;
            }
            case "SEMICOLON" -> {
                look = look.withFps(Math.max(AppearanceDesign.MIN_FPS, look.fps() - 1));
                return null;
            }
            case "APOSTROPHE" -> {
                look = look.withFps(Math.min(AppearanceDesign.MAX_FPS, look.fps() + 1));
                return null;
            }
            case "DIGIT0" -> {
                erasing = true;
                say("橡皮");
                return null;
            }
            case "Z" -> {
                undo();
                return null;
            }
            case "S" -> {
                save();
                return null;
            }
            case "I" -> {
                importPng();
                return null;
            }
            case "R" -> {
                resetToBuiltin();
                return null;
            }
            case "P" -> {
                saveAndPlay();
                return null;
            }
            case "ESCAPE" -> {
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
        sink.draw(canvasX(), canvasY(), look.width() * zoom(), look.height() * zoom(),
                (mx, my) -> canvasPoint(mx, my),
                (mx, my) -> paintAt(mx, my),
                (mx, my) -> paintAt(mx, my),
                () -> hotspotsDirty = true,
                () -> {
                    hoverX = -1;
                    hoverY = -1;
                });
        for (int i = 0; i < PRESETS.length; i++) {
            int index = i;
            int[] box = swatchRect(i);
            sink.add(box[0], box[1], box[2], box[3], () -> {
                brush = PRESETS[index];
                erasing = false;
            });
        }
        int[] eraser = eraserRect();
        sink.add(eraser[0], eraser[1], eraser[2], eraser[3], () -> {
            erasing = true;
            say("橡皮");
        });
        for (int ch = 0; ch < 3; ch++) {
            int channelIndex = ch;
            sink.drag(sliderX(), sliderY(ch), sliderW(), 12,
                    (mx, my) -> setChannelFromPointer(channelIndex, mx),
                    (mx, my) -> setChannelFromPointer(channelIndex, mx));
        }
        int[] save = buttonRect(0);
        sink.add(save[0], save[1], save[2], save[3], this::save);
        int[] importPng = buttonRect(1);
        sink.add(importPng[0], importPng[1], importPng[2], importPng[3], this::importPng);
        int[] undoBtn = buttonRect(2);
        sink.add(undoBtn[0], undoBtn[1], undoBtn[2], undoBtn[3], this::undo);
        int[] reset = buttonRect(3);
        sink.add(reset[0], reset[1], reset[2], reset[3], this::resetToBuiltin);
        int[] play = buttonRect(4);
        sink.add(play[0], play[1], play[2], play[3], this::saveAndPlay);
    }

    // ---------------------------------------------------------------- 界面几何

    private static int[] swatchRect(int index) {
        int col = index % COLS;
        int row = index / COLS;
        int x = PANEL_X + col * (SWATCH + SWATCH_GAP);
        int y = 52 + row * (SWATCH + SWATCH_GAP);
        return new int[]{x, y, SWATCH, SWATCH};
    }

    private static int[] eraserRect() {
        int y = 52 + ROWS * (SWATCH + SWATCH_GAP) + 6;
        return new int[]{PANEL_X, y, 92, 20};
    }

    private static int sliderX() {
        return PANEL_X + 46;
    }

    private static int sliderW() {
        return PANEL_W - 60;
    }

    private static int sliderY(int channel) {
        return 176 + channel * 20;
    }

    private static int[] buttonRect(int index) {
        int w = 60;
        return new int[]{PANEL_X + index * (w + 4), 288, w, 22};
    }

    private void setChannelFromPointer(int channelIndex, double mx) {
        int value = (int) Math.round((mx - sliderX()) * 255.0 / sliderW());
        value = Math.max(0, Math.min(255, value));
        int a = 0xFF000000;
        int r = (brush >>> 16) & 0xFF;
        int g = (brush >>> 8) & 0xFF;
        int b = brush & 0xFF;
        switch (channelIndex) {
            case 0 -> r = value;
            case 1 -> g = value;
            default -> b = value;
        }
        brush = a | (r << 16) | (g << 8) | b;
        channel = channelIndex;
        erasing = false;
    }

    // ---------------------------------------------------------------- 绘制

    @Override
    public void draw(CanvasBuffer buffer) {
        PixelArt art = buffer.art();
        art.fillRect(0, 0, 640, 360, Palette.BACKDROP);
        drawPanels(art);
        drawTopBar(buffer, art);
        drawCanvas(art);
        drawPalette(buffer, art);
        drawMagnifier(buffer, art);
        drawFooter(buffer);
        drawStatus(buffer);
    }

    private void drawPanels(PixelArt art) {
        art.rect(CANVAS_X - 4, CANVAS_Y - 4, CANVAS_SIDE + 8, CANVAS_SIDE + 8, Palette.PANEL);
        art.frame(CANVAS_X - 4, CANVAS_Y - 4, CANVAS_SIDE + 8, CANVAS_SIDE + 8, Palette.LINE_FAINT);
        art.rect(PANEL_X - 8, CANVAS_Y - 4, PANEL_W + 12, CANVAS_SIDE + 8, Palette.PANEL);
        art.frame(PANEL_X - 8, CANVAS_Y - 4, PANEL_W + 12, CANVAS_SIDE + 8, Palette.LINE_FAINT);
    }

    private void drawTopBar(CanvasBuffer buffer, PixelArt art) {
        String title = Language.t("战舰外观设计器", "SHIP PAINTER");
        zh(buffer, title, 8, 5, 12, Palette.CYAN);
        double x = 8 + textWidth(title, 12) + 12;
        String name = Language.t(ship.nameZh(), ship.nameEn());
        zh(buffer, name, (int) x, 6, 12, Palette.LINE);
        x += textWidth(name, 12) + 10;
        if (Language.isEnglish()) {
            zh(buffer, ship.id(), (int) x, 9, 12, Palette.LINE_DIM);
            x += textWidth(ship.id(), 12) + 10;
        }
        zh(buffer, "[" + (shipIndex + 1) + "/" + ShipCatalog.all().size() + "]", (int) x, 9,
                12, Palette.CYAN_DIM);
        // 读数右对齐到 632：帧/fps/尺寸/放大倍数，一行说完
        String info = Language.t(
                "帧 " + (frame + 1) + "/" + look.frameCount() + " · " + look.fps()
                        + "fps · " + look.width() + "×" + look.height() + " · " + zoom() + "×",
                "FRAME " + (frame + 1) + "/" + look.frameCount() + " · " + look.fps()
                        + "fps · " + look.width() + "x" + look.height() + " · " + zoom() + "x");
        zh(buffer, info, 632 - textWidth(info, 12), 6, 12, Palette.LINE_DIM);
    }

    private void drawCanvas(PixelArt art) {
        int z = zoom();
        int ox = canvasX();
        int oy = canvasY();
        int w = look.width() * z;
        int h = look.height() * z;
        // 透明像素的底：棋盘格，一眼看出哪块是空的
        for (int y = 0; y < h; y += 8) {
            for (int x = 0; x < w; x += 8) {
                boolean dark = ((x / 8) + (y / 8)) % 2 == 0;
                art.rect(ox + x, oy + y, Math.min(8, w - x), Math.min(8, h - y),
                        dark ? Palette.PANEL_ALT : Palette.PANEL);
            }
        }
        art.blitArgbTile(look.frameRaw(frame), look.width(), look.height(), 0, 0,
                ox, oy, w, h, false, false, false, 1.0);
        // 格子线（一格 = 棋盘上的一格），只在大到看得清时才画
        if (z >= 4) {
            for (int gx = 1; gx < look.width() / AppearanceDesign.CELL; gx++) {
                int x = ox + gx * AppearanceDesign.CELL * z;
                art.vline(oy, oy + h - 1, x, Palette.LINE_FAINT);
            }
            for (int gy = 1; gy < look.height() / AppearanceDesign.CELL; gy++) {
                int y = oy + gy * AppearanceDesign.CELL * z;
                art.hline(ox, ox + w - 1, y, Palette.LINE_FAINT);
            }
        }
        art.frame(ox - 1, oy - 1, w + 1, h + 1, Palette.LINE_DIM);
        // 指针所在那个像素
        if (hoverX >= 0 && hoverY >= 0 && hoverX < look.width() && hoverY < look.height()) {
            art.frame(ox + hoverX * z - 1, oy + hoverY * z - 1, z + 1, z + 1, Palette.CYAN);
        }
    }

    private void drawPalette(CanvasBuffer buffer, PixelArt art) {
        zh(buffer, Language.t("调色板（点一块选色，0 橡皮）", "PALETTE (CLICK A SWATCH, 0 ERASER)"),
                PANEL_X, 36, 12, Palette.LINE_DIM);
        for (int i = 0; i < PRESETS.length; i++) {
            int[] box = swatchRect(i);
            art.rect(box[0], box[1], box[2], box[3], PixelArt.fromArgb(PRESETS[i]));
            boolean on = !erasing && brush == PRESETS[i];
            art.frame(box[0] - 1, box[1] - 1, box[2] + 1, box[3] + 1,
                    on ? Palette.CYAN : Palette.LINE_FAINT);
        }
        int[] eraser = eraserRect();
        art.rect(eraser[0], eraser[1], eraser[2], eraser[3],
                erasing ? Palette.PANEL_ALT : Palette.PANEL);
        art.frame(eraser[0], eraser[1], eraser[2], eraser[3],
                erasing ? Palette.CYAN : Palette.LINE_FAINT);
        zh(buffer, Language.t("橡皮 (0)", "ERASER (0)"), eraser[0] + 6, eraser[1] + 5, 12,
                erasing ? Palette.CYAN : Palette.LINE_DIM);

        // 当前笔刷
        art.rect(PANEL_X, 158, 36, 14, PixelArt.fromArgb(erasing ? 0 : brush));
        art.frame(PANEL_X, 158, 36, 14, Palette.LINE_DIM);
        zh(buffer, erasing ? Language.t("透明", "TRANSPARENT")
                        : hex(brush) + "  RGB " + ((brush >> 16) & 0xFF) + ","
                        + ((brush >> 8) & 0xFF) + "," + (brush & 0xFF),
                PANEL_X + 42, 158, 12, Palette.LINE);

        // RGB 滑杆
        String[] names = {"R", "G", "B"};
        int[] values = {(brush >> 16) & 0xFF, (brush >>> 8) & 0xFF, brush & 0xFF};
        for (int ch = 0; ch < 3; ch++) {
            int y = sliderY(ch);
            en(art, names[ch], PANEL_X, y + 2, channel == ch ? Palette.CYAN : Palette.LINE_DIM);
            art.rect(sliderX(), y, sliderW(), 10, Palette.PANEL_ALT);
            art.rect(sliderX(), y, Math.max(1, values[ch] * sliderW() / 255), 10,
                    ch == 0 ? Palette.DANGER : ch == 1 ? Palette.OK : Palette.BLUE);
            art.frame(sliderX(), y, sliderW(), 10, Palette.LINE_FAINT);
            en(art, String.valueOf(values[ch]), sliderX() + sliderW() + 2, y + 2, Palette.LINE_DIM);
        }

        String[] labels = {
                Language.t("保存 S", "SAVE"),
                Language.t("导入 I", "IMPORT"),
                Language.t("撤销 Z", "UNDO"),
                Language.t("出厂 R", "RESET"),
                Language.t("开局 P", "PLAY")};
        for (int i = 0; i < labels.length; i++) {
            int[] box = buttonRect(i);
            button(art, buffer, box[0], box[1], box[2], box[3], labels[i],
                    i == 4 ? Palette.OK : Palette.CYAN_DIM);
        }
    }

    private void drawMagnifier(CanvasBuffer buffer, PixelArt art) {
        int px = hoverX;
        int py = hoverY;
        if (px < 0 || py < 0 || px >= look.width() || py >= look.height()) {
            return;
        }
        // 放在滑杆与按钮之间的空当里：56 见方，5×5 像素一目了然
        int boxSize = 56;
        int bx = PANEL_X;
        int by = 230;
        art.rect(bx, by, boxSize, boxSize, Palette.PANEL_ALT);
        art.frame(bx, by, boxSize, boxSize, Palette.LINE_DIM);
        int radius = 2;              // 5×5 像素
        int cell = boxSize / (radius * 2 + 1);
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int x = px + dx;
                int y = py + dy;
                int argb = (x < 0 || y < 0 || x >= look.width() || y >= look.height())
                        ? AppearanceDesign.TRANSPARENT
                        : look.pixel(frame, x, y);
                int sx = bx + (dx + radius) * cell;
                int sy = by + (dy + radius) * cell;
                if (argb == AppearanceDesign.TRANSPARENT) {
                    art.rect(sx, sy, cell, cell, Palette.BACKDROP);
                } else {
                    art.rect(sx, sy, cell, cell, PixelArt.fromArgb(argb));
                }
            }
        }
        art.frame(bx + radius * cell - 1, by + radius * cell - 1, cell + 1, cell + 1, Palette.CYAN);
        zh(buffer, Language.t("放大镜 " + cell + "×", "ZOOM " + cell + "x"),
                PANEL_X + 64, 232, 12, Palette.LINE_FAINT);
        zh(buffer, Language.t("像素 " + px + "," + py, "PIXEL " + px + "," + py),
                PANEL_X + 64, 244, 12, Palette.LINE_DIM);
        zh(buffer, Language.t("帧数 " + look.frameCount() + " · " + look.fps() + "fps",
                        "FRAMES " + look.frameCount() + " · " + look.fps() + "fps"),
                PANEL_X + 64, 256, 12, Palette.LINE_DIM);
    }

    private void drawFooter(CanvasBuffer buffer) {
        zh(buffer, Language.t(
                "左键画 · 拖动连续像素 · 调色板换色 · 0 橡皮 · Z 撤销 · S 保存"
                        + " · [ ] 换船 · 左右键换帧 · 上下键减增帧 · ; ' 调帧率 · ESC 返回",
                "LMB DRAW · DRAG PIXELS · PALETTE COLOR · 0 ERASER · Z UNDO · S SAVE"
                        + " · [ ] SHIP · LEFT/RIGHT FRAME · UP/DOWN ADD FRAME · ; ' FPS · ESC BACK"),
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
        if (text == null || text.isEmpty()) {
            return;
        }
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

    private static String hex(int argb) {
        return "#" + String.format("%06X", argb & 0xFFFFFF);
    }

    private static int argb(int rgb) {
        return 0xFF000000 | rgb;
    }

    // ---------------------------------------------------------------- 核对稿件用

    /** 核对稿件用：直接画一个像素。 */
    public void paintForTest(int px, int py) {
        // 拖动去重是给真实鼠标用的（同一像素不重复记账）；核对时要能连着画同一个像素
        lastPaintX = -1;
        lastPaintY = -1;
        setPixel(px, py);
    }

    /** 核对稿件用：换一艘船。 */
    public void selectShipForTest(String shipId) {
        Ship target = ShipCatalog.byId(shipId);
        if (target == null) {
            throw new IllegalArgumentException("没有这艘船：" + shipId);
        }
        selectShipIndex(ShipCatalog.all().indexOf(target));
    }

    /** 核对稿件用：画布几何。 */
    public int[] debugCanvas() {
        return new int[]{canvasX(), canvasY(), zoom(), look.width(), look.height()};
    }

    /** 核对稿件用：当前笔画颜色。 */
    public int brushArgb() {
        return erasing ? AppearanceDesign.TRANSPARENT : brush;
    }

    /** 核对稿件用：撤销栈深度。 */
    public int undoDepth() {
        return undo.size();
    }

    /** 核对稿件用：放大镜对准的像素（没对准时是负数）。 */
    public int[] debugHover() {
        return new int[]{hoverX, hoverY};
    }
}
