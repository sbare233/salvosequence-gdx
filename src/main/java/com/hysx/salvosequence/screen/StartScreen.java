package com.hysx.salvosequence.screen;

import com.hysx.salvosequence.audio.Music;
import com.hysx.salvosequence.audio.MusicTrack;
import com.hysx.salvosequence.audio.Sound;
import com.hysx.salvosequence.audio.SoundId;
import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.core.PixelFont;
import com.hysx.salvosequence.core.Renderer;
import com.hysx.salvosequence.core.UiChrome;
import com.hysx.salvosequence.core.UiDepth;
import com.hysx.salvosequence.core.UiTilt;
import com.hysx.salvosequence.render.BackdropRenderer;
import com.hysx.salvosequence.render.HotspotSink;
import com.hysx.salvosequence.ui.Language;
import com.hysx.salvosequence.ui.VideoSettings;
import com.hysx.salvosequence.core.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * 开始界面。
 *
 * <p>构成：星野下落的背景（复用战斗界面的 {@link BackdropRenderer}，保证风格一致）
 * + 标题 + 菜单（舰队出发 / 舰队编排 / 背景工坊 / 设置 / 退出）+ 底部提示。</p>
 *
 * <p>菜单项用键盘（↑↓ + Enter）或鼠标点击都可以操作；鼠标热区用设计坐标登记，
 * 由 {@code ScreenManager} 换算成屏幕坐标摆放。</p>
 *
 * <p>画面里不再出现装饰性英文：中文界面全中文，英文界面的文字由
 * {@link Language} 切换，设置页里可以直接改。</p>
 */
public final class StartScreen implements Screen {

    /** 菜单项：中英两版，显示哪版由当前语言决定。 */
    private record Item(String labelZh, String labelEn, ScreenContext.Target target) {
    }

    private static final Item[] ITEMS = {
            new Item("舰队出发", "SORTIE", ScreenContext.Target.CARD),
            new Item("舰队编排", "FLEET LAYOUT", ScreenContext.Target.ARRANGE),
            new Item("背景工坊", "BACKDROP LAB", ScreenContext.Target.BACKDROP_LAB),
            new Item("设置", "SETTINGS", null),
            new Item("退出", "QUIT", ScreenContext.Target.QUIT),
    };

    /**
     * 菜单条几何（设计坐标）：星幕协议式的一整块横向控制台，五个条目等距铺在里面。
     *
     * <p>原来是竖排五条漂浮的单行按钮，各看各的；参考图的菜单是<b>一条通栏</b>，
     * 条目之间只靠间隙分开 —— 这样底部有重量，标题才镇得住。</p>
     */
    private static final int MENU_X = 84;
    private static final int MENU_Y = 254;
    private static final int MENU_W = 472;
    private static final int MENU_H = 44;
    private static final int ITEM_W = 87;
    private static final int ITEM_H = 30;
    private static final int ITEM_GAP = 5;

    /** 第 i 个菜单条目的左上角。 */
    private static int itemX(int index) {
        return MENU_X + 8 + index * (ITEM_W + ITEM_GAP);
    }

    private static int itemY() {
        return MENU_Y + (MENU_H - ITEM_H) / 2;
    }

    /** 设置页的条目数：扫描线 / 形状 / 时机 / 孔径格栅 / 风格化 / 背景滤镜 / 语言。 */
    private static final int SETTINGS_ITEMS = 9;

    /** 背景复用战斗界面的星野单元，两个界面风格一致。 */
    private final List<Renderer> backdrop = new ArrayList<>();

    /**
     * 8px 点阵英文。
     *
     * <p>中文光栅器会把小于 12 的设计字号钳到 12（见 {@code CjkRasterizer}），
     * 所以除标题外的拉丁小字都走 PixelFont，否则会涨成 12px 把排版顶开。</p>
     */
    private final PixelFont font = new PixelFont(1);

    private int selected;
    private double time;
    private boolean settingsOpen;
    private int settingsCursor;
    private boolean hotspotDirty;

    public StartScreen() {
        backdrop.add(new BackdropRenderer());
    }

    /** 设置面板是否开着（安卓返回键：开着就先按 ESC 关它，没开才退出游戏）。 */
    public boolean isSettingsOpen() {
        return settingsOpen;
    }

    @Override
    public String name() {
        return "START";
    }

    /** 主界面配乐：公有领域的卡农，类路径上的 .mid 文件。 */
    private static final String TITLE_MUSIC = "/music/canon-in-d.mid";

    @Override
    public void onEnter() {
        selected = 0;
        // 截图工具用：-Dsalvo.settings=open 直接以设置页出图
        settingsOpen = System.getProperty("salvo.settings") != null;
        // 标题曲：卡农（公有领域），走类路径加载 .mid。
        // 已经在放就不重头开始（从战斗退回来不会听到标题曲重新起一遍，如果它没被停过的话）
        if (!Music.playPath(TITLE_MUSIC, true)) {
            // 文件不在了就退回代码生成的那首，别变成没配乐
            Music.play(MusicTrack.TITLE);
        }
        Music.setIntensity(0.1);
    }

    @Override
    public void update(double dt) {
        time += dt;
        for (Renderer renderer : backdrop) {
            renderer.update(dt);
        }
    }

    @Override
    public void draw(CanvasBuffer buffer) {
        PixelArt art = buffer.art();

        // 1) 背景（先画，后面所有内容都压在它上面）
        for (Renderer renderer : backdrop) {
            renderer.draw(buffer);
        }
        // 压暗一点，让标题与菜单跳出来
        art.ditherRect(0, 0, 640, 360, Palette.SPACE_DEEP, 3);

        // 2) 左右导轨留在底板上（它们贴着画面边缘，跟着指针动会露边）
        drawRails(art);

        // 3) 标题：最高的一层，指针到边缘时偏 4px
        buffer.beginLayer();
        drawTitle(buffer.art(), buffer);
        buffer.endLayer(UiTilt.UI.withDepth(4), 0, 0, 640, 200);

        // 4) 菜单/设置：面板层，偏得比标题少一档
        if (settingsOpen) {
            // 幕布画在不倾斜的底板上：它是整屏压暗，跟着转会在四角露出没压到的边
            UiChrome.scrim(buffer.art(), 0, 0, 640, 360);
        }
        buffer.beginLayer();
        if (settingsOpen) {
            drawSettings(buffer.art(), buffer);
        } else {
            drawMenu(buffer.art(), buffer);
        }
        buffer.endLayer(UiTilt.UI, 0, 0, 640, 360);

        // 5) 底部提示与版本（设置面板铺到下面，别再叠字上去）——最贴底的一层
        if (!settingsOpen) {
            buffer.beginLayer();
            drawFooter(buffer);
            buffer.endLayer(UiTilt.UI.withDepth(1), 0, 300, 640, 60);
        }
    }

    /**
     * 标题块：徽记 + 主标题 + 拉丁副标题 + 红色破折号副线。
     *
     * <p>照星幕协议那套标题锁定的写法排：最上面一个几何徽记给出"这是什么组织"，
     * 中文主标题最大，拉丁小字字号拉开车距，最后一条红色破折号线把整块收住。</p>
     */
    private void drawTitle(PixelArt art, CanvasBuffer buffer) {
        int cx = 320;

        // 1) 徽记：三枚三角，中间琥珀、两侧砖红；两侧压低一像素，像展开的阵列
        for (int i = 0; i < 3; i++) {
            int x = cx - 34 + i * 23;
            int top = 34 + Math.abs(1 - i) * 2;
            double[] xs = {x, x + 21, x + 10.5};
            double[] ys = {top + 17, top + 17, top};
            art.polygon(xs, ys, i == 1 ? Palette.AMBER_DEEP : Palette.CRIMSON_DIM);
            art.polygonOutline(xs, ys, i == 1 ? Palette.AMBER : Palette.CRIMSON);
        }

        // 2) 中文主标题：暖白字，右下压一层琥珀当描边，缩放后仍立得住
        String title = Language.t("齐射序列", "SALVO SEQUENCE");
        double size = 24;
        double tw = width(title, size);
        double tx = cx - tw / 2.0;
        buffer.text(title, tx + 2, 96, size, Palette.AMBER_DEEP);
        buffer.text(title, tx + 1, 95, size, Palette.INK);
        buffer.text(title, tx, 94, size, Palette.LINE);

        // 3) 拉丁副标题：走 PixelFont（8px 点阵英文）。
        // 中文光栅器会把小于 12 的字号钳到 12，这里的英文如果也用 buffer.text 会涨成 12px 撞上下一行。
        String latin = "SALVO SEQUENCE";
        int latinW = font.measure(latin);
        font.draw(art, latin, cx - latinW / 2, 100, Palette.STEEL);
        art.hline(cx - latinW / 2 - 30, cx - latinW / 2 - 8, 103, Palette.STEEL_DIM);
        art.hline(cx + latinW / 2 + 8, cx + latinW / 2 + 30, 103, Palette.STEEL_DIM);

        // 4) 红色破折号副线：星幕协议每块标题下面都有这条
        String strap = Language.t("轨道齐射战术协议", "ORBITAL SALVO PROTOCOL");
        double sw = width(strap, 12);
        buffer.text(strap, cx - sw / 2.0, 126, 12, Palette.CRIMSON);
        double left = cx - sw / 2.0 - 10;
        double right = cx + sw / 2.0 + 10;
        art.hline((int) left - 12, (int) left - 4, 119, Palette.CRIMSON_DIM);
        art.hline((int) right + 4, (int) right + 12, 119, Palette.CRIMSON_DIM);

        // 5) 标题下方的等宽刻度：横向轨道刻度，暗示"齐射序列"的计时感
        for (int i = 0; i < 9; i++) {
            int tickX = cx - 72 + i * 18;
            boolean major = i % 2 == 0;
            art.vline(134, 134 + (major ? 4 : 2), tickX, major ? Palette.STEEL : Palette.STEEL_FAINT);
        }
        if (((int) (time * 2)) % 2 == 0) {
            art.fillRect(cx - 1, 136, 3, 3, Palette.AMBER);
        }
    }

    /** 左右两条竖向导轨：把整屏框住，空档里也有"设计过的刻度"而不是纯黑。 */
    private void drawRails(PixelArt art) {
        for (int side = 0; side < 2; side++) {
            int x = side == 0 ? 28 : 610;
            art.vline(20, 340, x, Palette.STEEL_FAINT);
            for (int y = 24; y < 340; y += 12) {
                boolean major = ((y - 24) % 48) == 0;
                art.hline(x, x + (major ? 5 : 2), y,
                        major ? Palette.STEEL_DIM : Palette.STEEL_FAINT);
                if (major) {
                    art.fillRect(side == 0 ? x - 3 : x + 3, y, 2, 2, Palette.AMBER_DIM);
                }
            }
        }
    }

    /** 菜单：一整块通栏控制台，条目在格子里，选中那一格是实心琥珀。 */
    private void drawMenu(PixelArt art, CanvasBuffer buffer) {
        UiChrome.panel(art, MENU_X, MENU_Y, MENU_W, MENU_H);
        // 区块名压在面板左上角，伸出去一点，像贴在控制台上的铭牌
        UiChrome.plate(buffer, art, MENU_X + 12, MENU_Y - 9,
                (int) UiChrome.textWidth(Language.t("主菜单", "MENU"), 12) + 22, 17,
                Language.t("主菜单", "MENU"));
        // 右上角的"就绪"小灯：挪到面板上缘的铭牌行，别再压住最后一个条目
        int lampX = MENU_X + MENU_W - 46;
        art.fillRect(lampX - 3, MENU_Y - 8, 49, 9, Palette.SPACE_DEEP);
        art.fillRect(lampX, MENU_Y - 6, 3, 3,
                ((int) (time * 2)) % 2 == 0 ? Palette.OK : Palette.STEEL_DIM);
        font.draw(art, "READY", lampX + 6, MENU_Y - 8, Palette.STEEL);

        // 结构细节：条目下方一条量程导轨（像控制台上的刻度槽）
        UiChrome.gaugeH(art, MENU_X + 10, MENU_Y + MENU_H - 4, MENU_W - 20, 8, 32,
                Palette.STEEL_FAINT);

        for (int i = 0; i < ITEMS.length; i++) {
            int x = itemX(i);
            int y = itemY();
            boolean active = i == selected;
            String text = Language.t(ITEMS[i].labelZh(), ITEMS[i].labelEn());
            if (active) {
                UiChrome.banner(buffer, art, x, y, ITEM_W, ITEM_H, text);
            } else {
                UiChrome.chamferFrame(art, x, y, ITEM_W, ITEM_H, 3, Palette.STEEL_FAINT);
                UiChrome.zhCentered(buffer, text, x + ITEM_W / 2.0, y + (ITEM_H - 12) / 2.0,
                        12, Palette.LINE_DIM);
            }
            // 序号：选中格是深色压橙，其余是暗灰（8px 英文走 PixelFont）
            font.draw(art, String.format("%02d", i + 1), x + 4, y + ITEM_H - 10,
                    active ? Palette.INK : Palette.LINE_FAINT);
            if (active) {
                // 选中格的左右游标
                int bob = (int) Math.rint(Math.sin(time * 6) * 1.0);
                for (int d = 0; d < 3; d++) {
                    art.pixel(x - 4 + d, y + 14 + bob + d, Palette.AMBER);
                    art.pixel(x - 4 + d, y + 16 - d + bob, Palette.AMBER);
                    art.pixel(x + ITEM_W + 3 - d, y + 14 + bob + d, Palette.AMBER);
                    art.pixel(x + ITEM_W + 3 - d, y + 16 - d + bob, Palette.AMBER);
                }
            }
        }
    }

    /** 底部提示与版本：中文界面不出现英文句子。 */
    private void drawFooter(CanvasBuffer buffer) {
        String hint = Language.t("↑↓ 选择   回车确认   也可以直接点",
                "↑↓ SELECT   ENTER CONFIRM   OR CLICK");
        buffer.text(hint, 320 - width(hint, 12) / 2.0 - 3, 318, 12, Palette.LINE_FAINT);

        // 底部一条贯穿的细线把它们兜住（原来是自己飘在角落里的两行小字）
        buffer.art().hline(24, 616, 330, Palette.STEEL_FAINT);
        buffer.art().hline(24, 616, 331, Palette.SPACE_DEEP);
        buffer.art().fillRect(24, 329, 3, 3, Palette.AMBER_DIM);

        String build = Language.t("构筑 1.0", "BUILD 1.0");
        // 往右让出左下角的全局返回键
        buffer.text(build, 56, 348, 12, Palette.LINE_FAINT);

        String tag = Language.t("单画布 · 无图层", "ONE CANVAS / NO LAYERS");
        buffer.text(tag, 632 - width(tag, 12) - 3, 348, 12, Palette.LINE_FAINT);
    }

    /** 设置页：扫描线 / 形状 / 时机 / 孔径格栅 / 风格化 / 背景滤镜 / 视差 / 倾斜 / 语言，键盘与鼠标都能改，Esc 返回。 */
    private void drawSettings(PixelArt art, CanvasBuffer buffer) {
        int x = 176;
        int y = 52;
        int w = 288;
        int h = 272;
        // 幕布已经在底板上压好（见 draw），这里只画面板
        UiChrome.panel(art, x, y, w, h);
        UiChrome.plate(buffer, art, x + 12, y - 9, 76, 17, Language.t("设置", "SETTINGS"));
        UiChrome.zh(buffer, Language.t("显示与语言", "DISPLAY & LANGUAGE"), x + w - 96,
                y + 8, 8, Palette.STEEL);
        art.hline(x + 3, x + w - 4, y + 26, Palette.STEEL_FAINT);

        String[] labels = {
                Language.t("扫描线：", "SCANLINES: ")
                        + Language.t(VideoSettings.scanlines() ? "开" : "关",
                        VideoSettings.scanlines() ? "ON" : "OFF"),
                Language.t("扫描线形状：", "SCANLINE SHAPE: ")
                        + Language.t(VideoSettings.scanlineShape().label(),
                        VideoSettings.scanlineShape().labelEn()),
                Language.t("扫描线时机：", "SCANLINE TIMING: ")
                        + Language.t(VideoSettings.scanlineOrder().label(),
                        VideoSettings.scanlineOrder().labelEn()),
                Language.t("孔径格栅：", "APERTURE GRILLE: ")
                        + Language.t(VideoSettings.apertureGrille() ? "开" : "关",
                        VideoSettings.apertureGrille() ? "ON" : "OFF"),
                Language.t("风格化：", "STYLE: ")
                        + Language.t(VideoSettings.style().label(), VideoSettings.style().labelEn()),
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
        };
        for (int i = 0; i < labels.length; i++) {
            int ry = y + 40 + i * 22;
            boolean sel = i == settingsCursor;
            if (sel) {
                // 选中行整行压成实心琥珀，深色字 —— 和菜单选中同一个读法
                UiChrome.banner(buffer, art, x + 8, ry - 15, w - 16, 19, labels[i]);
            } else {
                buffer.text(labels[i], x + 18, ry, 12, Palette.LINE_DIM);
            }
        }
        art.hline(x + 3, x + w - 4, y + h - 42, Palette.STEEL_FAINT);
        buffer.text(Language.t("↑↓ 选择   ←→ 切换   Esc 返回",
                "UP/DOWN SELECT   LEFT/RIGHT CHANGE"), x + 14, y + h - 30, 12,
                Palette.LINE_FAINT);
        buffer.text(Language.t("F11 全屏   F12 立绘", "F11 FULLSCREEN   F12 PORTRAIT"),
                x + 14, y + h - 14, 12, Palette.LINE_FAINT);
    }

    /** 键盘操作：上下选择、回车确认、Esc 返回。 */
    @Override
    public ScreenContext onKey(String code) {
        if (settingsOpen) {
            switch (code) {
                case "ESCAPE" -> {
                    settingsOpen = false;
                    hotspotDirty = true;
                    Sound.play(SoundId.UI_BACK);
                }
                case "UP", "W" -> {
                    settingsCursor = Math.floorMod(settingsCursor - 1, SETTINGS_ITEMS);
                    Sound.play(SoundId.UI_MOVE);
                }
                case "DOWN", "S" -> {
                    settingsCursor = Math.floorMod(settingsCursor + 1, SETTINGS_ITEMS);
                    Sound.play(SoundId.UI_MOVE);
                }
                case "LEFT", "RIGHT", "A", "D", "ENTER", "SPACE" -> toggleSetting(settingsCursor);
                default -> {
                    // 设置页里其它键不处理
                }
            }
            return null;
        }
        switch (code) {
            case "UP", "W" -> moveCursor((selected + ITEMS.length - 1) % ITEMS.length);
            case "DOWN", "S" -> moveCursor((selected + 1) % ITEMS.length);
            case "ENTER", "SPACE" -> {
                return activate(selected);
            }
            default -> {
                // 其它键不处理
            }
        }
        return null;
    }

    /** 移动光标；只有真的换了条目才出声。 */
    private void moveCursor(int index) {
        if (index == selected) {
            return;
        }
        selected = index;
        Sound.play(SoundId.UI_MOVE);
    }

    /** 0=扫描线开关 1=形状 2=时机 3=孔径格栅 4=风格化 5=背景滤镜 6=视差 7=倾斜 8=界面语言。 */
    private void toggleSetting(int index) {
        switch (index) {
            case 0 -> VideoSettings.toggleScanlines();
            case 1 -> VideoSettings.cycleScanlineShape();
            case 2 -> VideoSettings.cycleScanlineOrder();
            case 3 -> VideoSettings.toggleApertureGrille();
            case 4 -> VideoSettings.cycleStyle();
            case 5 -> VideoSettings.cycleBackdropFilter();
            case 6 -> UiTilt.toggleParallax();
            case 7 -> UiTilt.toggleTilt();
            default -> Language.toggle();
        }
        Sound.play(SoundId.UI_CLICK);
    }

    private ScreenContext activate(int index) {
        Item item = ITEMS[index];
        if (item.target() == null) {
            settingsOpen = true;
            settingsCursor = 0;
            hotspotDirty = true;
            Sound.play(SoundId.UI_CLICK);
            return null;
        }
        Sound.play(SoundId.UI_CLICK);
        return ScreenContext.go(item.target());
    }

    /** 鼠标热区：条目点击 = 选中并确认。 */
    @Override
    public void collectHotspots(HotspotSink sink) {
        if (settingsOpen) {
            int x = 176;
            int y = 52;
            int w = 288;
            for (int i = 0; i < SETTINGS_ITEMS; i++) {
                int ry = y + 40 + i * 22;
                int index = i;
                // 热区按整行铺开：文字基线在 ry，字形大致占 ry-9..ry+2，热区再把上下各放宽一点，
                // 点到文字本身也能命中。鼠标移进来顺便把光标挪到这一行。
                sink.track(x + 10, ry - 14, w - 20, 20, null,
                        () -> {
                            settingsCursor = index;
                            toggleSetting(index);
                        },
                        () -> settingsCursor = index, null);
            }
            return;
        }
        for (int i = 0; i < ITEMS.length; i++) {
            final int index = i;
            sink.add(itemX(i) - 2, itemY() - 3, ITEM_W + 4, ITEM_H + 6, () -> {
                selected = index;
                ScreenContext context = activate(index);
                pending = context;
            });
        }
    }

    /**
     * 文字墨迹宽度：中日韩整字约 1em，ASCII 约 0.5em。
     * 只用于居中/右对齐，宁可略宽也不要让文字压到边框。
     */
    private static double width(String text, double size) {
        double w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += text.charAt(i) < 0x2E80 ? size * 0.5 : size;
        }
        return w;
    }

    /** 热区点击产生的跳转请求（由 ScreenManager 取走）。 */
    private ScreenContext pending;

    @Override
    public boolean consumeHotspotDirty() {
        boolean dirty = hotspotDirty;
        hotspotDirty = false;
        return dirty;
    }

    @Override
    public ScreenContext pollTransition() {
        ScreenContext context = pending;
        pending = null;
        return context;
    }
}
