package com.hysx.salvosequence.screen;

import com.hysx.salvosequence.audio.Music;
import com.hysx.salvosequence.audio.MusicTrack;
import com.hysx.salvosequence.audio.Sound;
import com.hysx.salvosequence.audio.SoundId;
import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.core.Renderer;
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

    /** 菜单块几何（设计坐标）。 */
    private static final int ITEM_X = 232;
    private static final int ITEM_W = 176;
    private static final int ITEM_H = 30;
    private static final int ITEM_Y0 = 164;
    private static final int ITEM_GAP = 36;

    /** 设置页的条目数：扫描线 / 形状 / 时机 / 孔径格栅 / 风格化 / 背景滤镜 / 语言。 */
    private static final int SETTINGS_ITEMS = 7;

    /** 背景复用战斗界面的星野单元，两个界面风格一致。 */
    private final List<Renderer> backdrop = new ArrayList<>();

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
        settingsOpen = false;
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

        // 2) 标题
        drawTitle(art, buffer);

        // 3) 菜单
        if (settingsOpen) {
            drawSettings(art, buffer);
        } else {
            drawMenu(art, buffer);
        }

        // 4) 底部提示与版本（设置面板铺到下面，别再叠字上去）
        if (!settingsOpen) {
            drawFooter(buffer);
        }
    }

    /** 标题：大号点阵字 + 一条装饰分隔线。 */
    private void drawTitle(PixelArt art, CanvasBuffer buffer) {
        // 标题背板
        art.polygon(new double[]{150, 490, 486, 154},
                new double[]{60, 60, 124, 124}, Palette.PANEL);
        art.polygonOutline(new double[]{150, 490, 486, 154},
                new double[]{60, 60, 124, 124}, Palette.LINE);

        // 主标题：24 号（12 网格的整 2 倍，边缘干净），四向偏移叠出描边。
        // 中文每字 1em，英文每字半 em，按实际字宽居中。
        String title = Language.t("齐射序列", "SALVO SEQUENCE");
        double titleW = width(title, 24);
        double tx = 320 - titleW / 2.0 - 3;
        double ty = 98;
        buffer.text(title, tx - 1, ty, 24, Palette.CYAN_DIM);
        buffer.text(title, tx + 1, ty, 24, Palette.CYAN_DIM);
        buffer.text(title, tx, ty - 1, 24, Palette.CYAN_DIM);
        buffer.text(title, tx, ty + 1, 24, Palette.CYAN_DIM);
        buffer.text(title, tx, ty, 24, Palette.LINE);

        // 分隔线 + 闪烁游标
        art.hline(160, 480, 134, Palette.LINE_FAINT);
        if (((int) (time * 2)) % 2 == 0) {
            art.rect(156, 131, 4, 6, Palette.CYAN);
            art.rect(480, 131, 4, 6, Palette.CYAN);
        }
    }

    /** 菜单：选中项高亮 + 左右箭头 + 单行标签。 */
    private void drawMenu(PixelArt art, CanvasBuffer buffer) {
        for (int i = 0; i < ITEMS.length; i++) {
            int y = ITEM_Y0 + i * ITEM_GAP;
            boolean active = i == selected;
            Color frame = active ? Palette.CYAN : Palette.LINE_DIM;
            Color label = active ? Palette.LINE : Palette.LINE_DIM;

            // 斜切条目
            double[] xs = {ITEM_X, ITEM_X + ITEM_W, ITEM_X + ITEM_W - 4, ITEM_X - 4};
            double[] ys = {y, y, y + ITEM_H, y + ITEM_H};
            art.polygon(xs, ys, active ? Palette.PANEL_ALT : Palette.PANEL);
            art.polygonOutline(xs, ys, frame);
            if (active) {
                art.hline(ITEM_X, ITEM_X + ITEM_W - 4, y + 1, Palette.CYAN_DIM);
            }

            String text = Language.t(ITEMS[i].labelZh(), ITEMS[i].labelEn());
            buffer.textCentered(text, ITEM_X + 88, y + 13, 12, label);

            // 选中指示：左右三角 + 一个跳动的小方块
            if (active) {
                int bob = (int) Math.rint(Math.sin(time * 6) * 1.5);
                for (int d = 0; d < 5; d++) {
                    art.pixel(ITEM_X - 14 + d, y + 12 + bob + d, Palette.CYAN);
                    art.pixel(ITEM_X - 14 + d, y + 20 + bob - d, Palette.CYAN);
                    art.pixel(ITEM_X + ITEM_W + 10 - d, y + 12 + bob + d, Palette.CYAN);
                    art.pixel(ITEM_X + ITEM_W + 10 - d, y + 20 + bob - d, Palette.CYAN);
                }
            }
        }
    }

    /** 底部提示与版本：中文界面不出现英文句子。 */
    private void drawFooter(CanvasBuffer buffer) {
        String hint = Language.t("↑↓ 选择   回车确认   也可以直接点",
                "↑↓ SELECT   ENTER CONFIRM   OR CLICK");
        buffer.text(hint, 320 - width(hint, 12) / 2.0 - 3, 344, 12, Palette.LINE_FAINT);

        String build = Language.t("构筑 1.0", "BUILD 1.0");
        // 往右让出左下角的全局返回键
        buffer.text(build, 56, 358, 12, Palette.LINE_FAINT);

        String tag = Language.t("单画布 · 无图层", "ONE CANVAS / NO LAYERS");
        buffer.text(tag, 632 - width(tag, 12) - 3, 358, 12, Palette.LINE_FAINT);
    }

    /** 设置页：扫描线 / 形状 / 时机 / 孔径格栅 / 风格化 / 背景滤镜 / 语言，键盘与鼠标都能改，Esc 返回。 */
    private void drawSettings(PixelArt art, CanvasBuffer buffer) {
        int x = 176;
        int y = 132;
        int w = 288;
        int h = 224;
        double[] xs = {x, x + w, x + w, x + 6, x};
        double[] ys = {y, y, y + h - 6, y + h, y + h};
        art.polygon(xs, ys, Palette.PANEL);
        art.polygonOutline(xs, ys, Palette.CYAN);
        art.hline(x + 2, x + w - 2, y + 18, Palette.PANEL_EDGE);

        buffer.text(Language.t("设置", "SETTINGS"), x + 10, y + 14, 12, Palette.CYAN);
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
                Language.t("语言：", "LANGUAGE: ") + Language.t("中文", "English"),
        };
        for (int i = 0; i < labels.length; i++) {
            int ry = y + 40 + i * 22;
            boolean sel = i == settingsCursor;
            if (sel) {
                // buffer.text 用的是基线：12px 字大约占 ry-14..ry，框给上下各留 2px
                art.frame(x + 10, ry - 16, w - 20, 19, Palette.WARN);
            }
            buffer.text(labels[i], x + 18, ry, 12, sel ? Palette.WARN : Palette.LINE);
        }
        buffer.text(Language.t("↑↓ 选择   ←→ 切换   Esc 返回",
                "UP/DOWN SELECT   LEFT/RIGHT CHANGE"), x + 12, y + 192, 12, Palette.LINE_FAINT);
        buffer.text(Language.t("F11 全屏   F12 立绘", "F11 FULLSCREEN   F12 PORTRAIT"),
                x + 12, y + 208, 12, Palette.LINE_FAINT);
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

    /** 0=扫描线开关 1=形状 2=时机 3=孔径格栅 4=风格化 5=背景滤镜 6=界面语言。 */
    private void toggleSetting(int index) {
        switch (index) {
            case 0 -> VideoSettings.toggleScanlines();
            case 1 -> VideoSettings.cycleScanlineShape();
            case 2 -> VideoSettings.cycleScanlineOrder();
            case 3 -> VideoSettings.toggleApertureGrille();
            case 4 -> VideoSettings.cycleStyle();
            case 5 -> VideoSettings.cycleBackdropFilter();
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
            int y = 150;
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
            int y = ITEM_Y0 + i * ITEM_GAP;
            final int index = i;
            sink.add(ITEM_X - 16, y - 2, ITEM_W + 32, ITEM_H + 4, () -> {
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
