package com.hysx.salvosequence.screen;

import com.hysx.salvosequence.core.BackdropBatch;
import com.hysx.salvosequence.core.BackdropFiltering;
import com.hysx.salvosequence.core.Backdrops;
import com.hysx.salvosequence.core.BayerPixelator;
import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.core.ImageCodec;
import com.hysx.salvosequence.core.PixelArtFilter;
import com.hysx.salvosequence.core.WorkDir;
import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.core.PixelFont;
import com.hysx.salvosequence.gleba.GlebaPlanet;
import com.hysx.salvosequence.live.LiveBackdrops;
import com.hysx.salvosequence.live.LiveSettings;
import com.hysx.salvosequence.live.LiveTune;
import com.hysx.salvosequence.render.HotspotSink;
import com.hysx.salvosequence.ui.Language;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 背景工坊：把原图（星空/星云照片、网络艺术图）拜耳像素化成游戏背景。
 *
 * <p>原图放 {@code backdrops-src/}（PNG/JPG），F6 或标题菜单进入；
 * 键盘调参实时预览，ENTER 导出到 {@code backdrops/custom/}——
 * 那里的图会被 Backdrops 当作自定义主题，战斗/太空港自动轮播。</p>
 *
 * <p>P（或参数面板下方的按钮）是批量：{@code backdrops-src/} 里每张图都按当前参数
 * 像素化一遍，写进新建的 {@code backdrops/batch/<时间戳>/}（见 {@link BackdropBatch}）。</p>
 */
public final class BackdropLabScreen implements Screen {

    private final PixelFont font = new PixelFont();
    private final File dir = WorkDir.of("backdrops-src");
    private final File outDir = WorkDir.of("backdrops/custom");

    private File[] files = new File[0];
    private int fileIndex;
    private int[] src;
    private int srcW;
    private int srcH;
    private String srcName = "(空)";
    private String loadError = "";
    private BayerPixelator.Params params = BayerPixelator.Params.DEFAULT;
    private int[] preview;
    private boolean dirty = true;
    private String toast = "";
    private double toastLife;
    /** 星球模式：3D 行星实时渲染作为拜耳源（GlebaScreen B / 左上按钮开关）。 */
    private boolean planetMode;
    private GlebaPlanet planet;

    /** 外部请求进入星球模式（GlebaScreen 的"送入背景工坊"按钮置位）。 */
    public static boolean planetModeOnEntry;

    /** 实时 3D 场景（星云/恒星）：-1 = 关，否则是 LiveBackdrops 的序号。L 键循环。 */
    private int liveIndex = -1;

    /** 实时背景开关会改变"整屏拖拽热区"是否存在，置上让管理器下一帧重建热区。 */
    private boolean hotspotDirty;
    private double liveTime;
    /** 实时参数面板：第 0 页是相机，往后每页 6 个数值参数。 */
    private static final int LIVE_X = 468;
    private static final int LIVE_Y = 176;
    private static final int LIVE_W = 168;
    private static final int LIVE_ROWS = 6;
    private static final int LIVE_ROW_H = 14;
    private int livePage;
    private int liveCursor;
    private double liveDragX = -1;
    private double liveDragY = -1;
    private boolean paramsDirty;
    private double saveDelay;

    @Override
    public String name() {
        return "backdrop-lab";
    }

    @Override
    public void onEnter() {
        if (planetModeOnEntry) {
            planetMode = true;
        }
        planetModeOnEntry = false;
        // 过渡完成时 rebuildHotspots 先于第一次 update 执行：
        // planetMode 为真就必须先建好 planet，否则 collectHotspots 捕获 null 接收者
        if (planetMode) {
            ensurePlanet();
        }
        rescan();
    }

    @Override
    public void update(double dt) {
        liveTime += dt;
        if (toastLife > 0) {
            toastLife -= dt;
        }
        // 调参存档：改完停手 0.4 秒再写盘，拖动滑杆时不会每帧都存
        if (paramsDirty) {
            saveDelay -= dt;
            if (saveDelay <= 0) {
                paramsDirty = false;
                LiveSettings.save();
                toast = Language.t("参数已保存", "PARAMS SAVED");
                toastLife = 1.2;
            }
        }
        if (planetMode) {
            ensurePlanet();
            planet.update(dt);
        }
    }

    private void ensurePlanet() {
        if (planet == null) {
            planet = new GlebaPlanet();
        }
        planet.ensureGl();
    }

    private void setPlanetMode(boolean on) {
        if (planetMode == on) {
            return;
        }
        planetMode = on;
        if (on) {
            ensurePlanet();
        }
        dirty = true;
        // 星球模式会多出「新种子/重生成/方位/仰角」几个按钮：热区不重建就点不到
        hotspotDirty = true;
    }

    @Override
    public void draw(CanvasBuffer buffer) {
        PixelArt art = buffer.art();
        if (liveIndex >= 0) {
            // 实时场景：LiveBackdrops 出图时已按「背景滤镜」设置处理过，这里直接放大上屏
            int[] f = com.hysx.salvosequence.live.LiveBackdrops.frame(liveIndex, liveTime);
            if (f != null) {
                src = f;
                srcW = com.hysx.salvosequence.live.nebula.NebulaLive.FBW;
                srcH = com.hysx.salvosequence.live.nebula.NebulaLive.FBH;
                srcName = "live/" + com.hysx.salvosequence.live.LiveBackdrops.id(liveIndex);
                dirty = true;
            }
        }
        if (planetMode) {
            // 星球模式：3D 视图每帧作为拜耳源（320x180，cover 适配 640x360）
            ensurePlanet();
            src = planet.renderFrame();
            srcW = GlebaPlanet.FBW;
            srcH = GlebaPlanet.FBH;
            srcName = "gleba-seed" + planet.params.seed;
            dirty = true;
        }
        boolean liveFrame = liveIndex >= 0 && !planetMode;
        if (!liveFrame && (dirty || preview == null)) {
            // 静态图也走设置里的「背景滤镜」：拜耳一套参数，像素滤镜一套参数
            preview = BackdropFiltering.render(src, srcW, srcH, params, filter());
            dirty = false;
        }
        if (liveFrame) {
            art.blitArgbTile(src, srcW, srcH,
                    0, 0, 0, 0, BayerPixelator.W, BayerPixelator.H, true, false, false, 1.0);
        } else {
            art.blitArgbTile(preview, BayerPixelator.W, BayerPixelator.H,
                    0, 0, 0, 0, BayerPixelator.W, BayerPixelator.H, true, false, false, 1.0);
        }

        // 顶栏
        art.rect(0, 0, 640, 16, Palette.BACKDROP);
        zh(buffer, Language.t("背景工坊", "BACKDROP LAB"), 4, 3, 12, Palette.CYAN);
        zh(buffer, planetMode
                        ? srcName + "  " + planet.status()
                        : srcName + "  " + srcW + Language.t("×", "x") + srcH
                        + "  (" + (fileIndex + 1) + "/" + files.length + ")",
                76, 3, 12, Palette.LINE_DIM);
        // 当前滤镜（K 切换）：静态预览、ENTER 导出、P 批量都按它走
        com.hysx.salvosequence.ui.VideoSettings.BackdropFilter which = filter();
        zh(buffer, "[" + Language.t(which.label(), which.labelEn()) + "]",
                470, 3, 12, Palette.CYAN);
        if (!loadError.isEmpty()) {
            zh(buffer, loadError, 380, 3, 12, Palette.WARN);
        }

        // 星球模式按钮面板（左列）：模式开关 + 行星控制，全部按钮无键位
        art.rect(2, 18, 122, planetMode ? 118 : 20, PixelArt.fromArgb(0xB4000000));
        art.frame(2, 18, 122, planetMode ? 118 : 20, Palette.LINE_DIM);
        drawBtn(art, 4, 20, 118, 16,
                Language.t(planetMode ? "星球模式：开" : "星球模式：关",
                        planetMode ? "PLANET: ON" : "PLANET: OFF"), Palette.CYAN);
        // 实时背景按钮（星球模式按钮右边）：点了就切星云/恒星/关
        String liveLabel = liveIndex < 0
                ? Language.t("实时背景：关", "LIVE: OFF")
                : Language.t("实时背景：", "LIVE: ")
                + Language.t(com.hysx.salvosequence.live.LiveBackdrops.nameZh(liveIndex),
                com.hysx.salvosequence.live.LiveBackdrops.nameEn(liveIndex));
        drawBtn(art, 128, 20, 150, 16, liveLabel, liveIndex < 0 ? Palette.LINE_DIM : Palette.CYAN);

        if (planetMode) {
            drawBtn(art, 4, 40, 57, 16, Language.t("新种子", "SEED"), Palette.LINE);
            drawBtn(art, 65, 40, 57, 16, Language.t("重生成", "REGEN"), Palette.LINE);
            drawBtn(art, 4, 60, 57, 16, Language.t("方位 -", "AZ -"), Palette.LINE);
            drawBtn(art, 65, 60, 57, 16, Language.t("方位 +", "AZ +"), Palette.LINE);
            drawBtn(art, 4, 80, 57, 16, Language.t("高度 -", "EL -"), Palette.LINE);
            drawBtn(art, 65, 80, 57, 16, Language.t("高度 +", "EL +"), Palette.LINE);
            drawBtn(art, 4, 100, 118, 16, Language.t("新种子+重生成", "SEED+REGEN"),
                    Palette.LINE);
        }

        // 参数面板（右上）：拜耳和像素滤镜各显示自己那套参数
        art.rect(468, 20, 168, 148, PixelArt.fromArgb(0xB4000000));
        art.frame(468, 20, 168, 148, Palette.LINE_DIM);
        String[][] rows;
        if (pixelMode()) {
            PixelArtFilter.Params pp = PixelArtFilter.params();
            rows = new String[][]{
                    {"J 调色板 PALETTE", PixelArtFilter.modeName()},
                    {"Q/A 色阶 STEPS", String.valueOf(pp.steps())},
                    {"W/S 锐化 SHARP", String.format(java.util.Locale.ROOT, "%.2f", pp.sharpness())},
                    {"E/D 抖动 DITHER", String.format(java.util.Locale.ROOT, "%.2f", pp.ditherAmount())},
                    {"R/F 描边 OUTLINE", String.format(java.util.Locale.ROOT, "%.2f", pp.outlineMix())},
                    {"T/G 阈值 EDGE", String.format(java.util.Locale.ROOT, "%.2f", pp.edgeThreshold())},
                    {"K 滤镜 FILTER", Language.t("像素", "PIXEL")},
            };
        } else {
            rows = new String[][]{
                    {"Q/A 像素块 CELL", String.valueOf(params.cell())},
                    {"W/S 色阶 LEVELS", String.valueOf(params.levels())},
                    {"E/D 抖动 DITHER", params.ditherPct() + "%"},
                    {"R/F 亮度 BRIGHT", (params.brightness() >= 0 ? "+" : "") + params.brightness()},
                    {"T/G 对比 CONTRAST", (params.contrast() >= 0 ? "+" : "") + params.contrast()},
                    {"Y/H 饱和 SAT", params.saturationPct() + "%"},
                    {"B 深空化 DEEP SPACE",
                            Language.t(params.deepSpace() ? "开" : "关",
                                    params.deepSpace() ? "ON" : "OFF")},
            };
        }
        for (int i = 0; i < rows.length; i++) {
            zh(buffer, rows[i][0], 474, 28 + i * 18, 12, Palette.LINE_DIM);
            zh(buffer, rows[i][1], 600, 28 + i * 18, 12, Palette.CYAN);
        }

        // 批量导出按钮（参数面板下方）：把 backdrops-src/ 整个按当前参数过一遍
        drawBatchButton(buffer, art);

        if (liveIndex >= 0) {
            drawLiveParams(buffer, art);
        }

        // 底部提示
        art.rect(0, 344, 640, 16, Palette.BACKDROP);
        zh(buffer, Language.t("<->换图 · L实时 · K滤镜 · J调色板 · 拖转 · 滚轮远近 · 点行增减 · ENTER导出 · ESC返回",
                "<->IMG · L LIVE · K FILTER · J PALETTE · DRAG · SCROLL · CLICK ROWS +- · ENTER · ESC"),
                4, 347, 12, Palette.LINE_DIM);
        if (toastLife > 0) {
            int w = toast.length() * 12 + 12;
            art.rect(320 - w / 2, 322, w, 16, Palette.BACKDROP);
            art.frame(320 - w / 2, 322, w, 16, Palette.CYAN_DIM);
            zh(buffer, toast, 320 - w / 2 + 6, 324, 12, Palette.CYAN);
        }
    }

    @Override
    public ScreenContext onKey(String code) {
        switch (code) {
            case "LEFT" -> {
                fileIndex = Math.floorMod(fileIndex - 1, Math.max(1, files.length));
                loadCurrent();
            }
            case "RIGHT" -> {
                fileIndex = Math.floorMod(fileIndex + 1, Math.max(1, files.length));
                loadCurrent();
            }
            case "Q" -> paramRow(0, +1);
            case "A" -> paramRow(0, -1);
            case "W" -> paramRow(1, +1);
            case "S" -> paramRow(1, -1);
            case "E" -> paramRow(2, +1);
            case "D" -> paramRow(2, -1);
            case "R" -> paramRow(3, +1);
            case "F" -> paramRow(3, -1);
            case "T" -> paramRow(4, +1);
            case "G" -> paramRow(4, -1);
            case "Y" -> paramRow(5, +1);
            case "H" -> paramRow(5, -1);
            case "B" -> {
                if (!pixelMode()) {
                    params = setDeepSpace(params, !params.deepSpace());
                }
            }
            case "L" -> {
                liveIndex = com.hysx.salvosequence.live.LiveBackdrops.cycle(1);
                com.hysx.salvosequence.live.LiveBackdrops.setSelected(liveIndex);
                if (liveIndex >= 0) {
                    planetMode = false;
                }
                // 整屏拖拽热区只在 liveIndex >= 0 时登记：开关一变必须重建热区，
                // 否则鼠标拖动、点参数行全都落在空处（只有滚轮还灵）
                hotspotDirty = true;
                toast = liveIndex < 0
                        ? Language.t("实时背景：关（战斗按节点自动挑）", "LIVE: OFF (AUTO PER NODE)")
                        : Language.t("实时背景：", "LIVE: ")
                        + Language.t(com.hysx.salvosequence.live.LiveBackdrops.nameZh(liveIndex),
                        com.hysx.salvosequence.live.LiveBackdrops.nameEn(liveIndex));
                toastLife = 3.0;
            }
            case "UP" -> liveCursor = Math.max(0, liveCursor - 1);
            case "DOWN" -> liveCursor = Math.min(liveRowCount() - 1, liveCursor + 1);
            case "MINUS" -> liveAdjust(liveCursor, -1);
            case "EQUALS" -> liveAdjust(liveCursor, +1);
            case "K" -> {
                var which = com.hysx.salvosequence.ui.VideoSettings.cycleBackdropFilter();
                dirty = true;
                toast = Language.t("背景滤镜：" + which.label(), "BACKDROP FILTER: " + which.labelEn());
                toastLife = 2.5;
            }
            case "J" -> {
                com.hysx.salvosequence.core.PixelArtFilter.cycleMode(1);
                toast = Language.t("调色板：" + com.hysx.salvosequence.core.PixelArtFilter.modeName(),
                        "PALETTE: " + com.hysx.salvosequence.core.PixelArtFilter.modeName());
                toastLife = 2.5;
            }
            case "P" -> exportAll();
            case "ENTER" -> export();
            case "ESCAPE" -> {
                if (paramsDirty) {
                    // 退出前把没落盘的调参写完
                    paramsDirty = false;
                    LiveSettings.save();
                }
                return ScreenContext.go(ScreenContext.Target.START);
            }
            default -> {
                return null;
            }
        }
        dirty = true;
        return null;
    }

    // 每个参数一个命名 setter——不要共用重载，整型实参会撞进错误的那个

    /** 当前选的背景滤镜（设置面板和这里的 K 键共用同一个全局值）。 */
    private static com.hysx.salvosequence.ui.VideoSettings.BackdropFilter filter() {
        return com.hysx.salvosequence.ui.VideoSettings.backdropFilter();
    }

    private static boolean pixelMode() {
        return filter() == com.hysx.salvosequence.ui.VideoSettings.BackdropFilter.PIXEL;
    }

    /**
     * 参数面板的一行按 ±1 调整：拜耳调 {@link BayerPixelator.Params}，像素调
     * {@link PixelArtFilter.Params}，两边面板行是一一对应的（见 draw 里的 rows）。
     */
    private void paramRow(int row, int dir) {
        if (pixelMode()) {
            PixelArtFilter.Params p = PixelArtFilter.params();
            PixelArtFilter.Params q = switch (row) {
                case 0 -> new PixelArtFilter.Params(clampInt(p.steps() + dir, 2, 16), p.palette(),
                        p.sharpness(), p.edgeThreshold(), p.outlineMix(), p.ditherAmount(),
                        p.ditherBlend());
                case 1 -> new PixelArtFilter.Params(p.steps(), p.palette(),
                        clampF(p.sharpness() + dir * 0.05f), p.edgeThreshold(), p.outlineMix(),
                        p.ditherAmount(), p.ditherBlend());
                case 2 -> new PixelArtFilter.Params(p.steps(), p.palette(), p.sharpness(),
                        p.edgeThreshold(), p.outlineMix(), clampF(p.ditherAmount() + dir * 0.1f),
                        p.ditherBlend());
                case 3 -> new PixelArtFilter.Params(p.steps(), p.palette(), p.sharpness(),
                        p.edgeThreshold(), clampF(p.outlineMix() + dir * 0.05f), p.ditherAmount(),
                        p.ditherBlend());
                case 4 -> new PixelArtFilter.Params(p.steps(), p.palette(), p.sharpness(),
                        clampF(p.edgeThreshold() + dir * 0.1f), p.outlineMix(), p.ditherAmount(),
                        p.ditherBlend());
                default -> p;
            };
            PixelArtFilter.setParams(q);
            return;
        }
        switch (row) {
            case 0 -> params = setCell(params, params.cell() + dir);
            case 1 -> params = setLevels(params, params.levels() + dir);
            case 2 -> params = setDither(params, params.ditherPct() + dir * 10);
            case 3 -> params = setBrightness(params, params.brightness() + dir * 10);
            case 4 -> params = setContrast(params, params.contrast() + dir * 10);
            case 5 -> params = setSaturation(params, params.saturationPct() + dir * 10);
            default -> {
                // 没有第 7 项
            }
        }
    }

    private static int clampInt(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    /** 浮点参数按 0.05 取整，反复按也不漂。 */
    private static float clampF(float v) {
        float r = Math.round(v * 100f) / 100f;
        return r < 0f ? 0f : Math.min(r, 2f);
    }

    private BayerPixelator.Params setCell(BayerPixelator.Params p, int v) {
        return new BayerPixelator.Params(Math.max(1, Math.min(6, v)),
                p.levels(), p.ditherPct(), p.brightness(), p.contrast(), p.saturationPct(), p.deepSpace());
    }

    private BayerPixelator.Params setLevels(BayerPixelator.Params p, int v) {
        return new BayerPixelator.Params(p.cell(), Math.max(2, Math.min(12, v)),
                p.ditherPct(), p.brightness(), p.contrast(), p.saturationPct(), p.deepSpace());
    }

    private BayerPixelator.Params setDither(BayerPixelator.Params p, int v) {
        return new BayerPixelator.Params(p.cell(), p.levels(), Math.max(0, Math.min(100, v)),
                p.brightness(), p.contrast(), p.saturationPct(), p.deepSpace());
    }

    private BayerPixelator.Params setBrightness(BayerPixelator.Params p, int v) {
        return new BayerPixelator.Params(p.cell(), p.levels(), p.ditherPct(),
                Math.max(-80, Math.min(80, v)), p.contrast(), p.saturationPct(), p.deepSpace());
    }

    private BayerPixelator.Params setContrast(BayerPixelator.Params p, int v) {
        return new BayerPixelator.Params(p.cell(), p.levels(), p.ditherPct(), p.brightness(),
                Math.max(-60, Math.min(80, v)), p.saturationPct(), p.deepSpace());
    }

    private BayerPixelator.Params setSaturation(BayerPixelator.Params p, int v) {
        return new BayerPixelator.Params(p.cell(), p.levels(), p.ditherPct(), p.brightness(),
                p.contrast(), Math.max(0, Math.min(300, v)), p.deepSpace());
    }

    private BayerPixelator.Params setDeepSpace(BayerPixelator.Params p, boolean v) {
        return new BayerPixelator.Params(p.cell(), p.levels(), p.ditherPct(), p.brightness(),
                p.contrast(), p.saturationPct(), v);
    }

    // ---------------------------------------------------------------- 按钮面板

    /** 星球模式按钮的绘制与热区共用一套矩形（绘制在 draw，热区在这里登记）。 */
    @Override
    public void onScroll(double delta, double x, double y) {
        if (liveIndex < 0) {
            return;
        }
        LiveBackdrops.zoom(liveIndex, delta / 40.0);
        liveTime += 0.001;   // 强制下一帧重算预览
        dirty = true;
        markParamsDirty();
    }

    @Override
    public boolean consumeHotspotDirty() {
        boolean was = hotspotDirty;
        hotspotDirty = false;
        return was;
    }

    @Override
    public void collectHotspots(HotspotSink sink) {
        if (liveIndex >= 0) {
            // 整屏拖拽 = 轨道旋转（和 MVP 里鼠标操作一致）；先登记，按钮后登记压在上面
            sink.draw(0, 0, 640, 360, this::liveMove, this::liveDrag,
                    this::livePress, null, null);
        }
        sink.add(4, 20, 118, 16, () -> setPlanetMode(!planetMode));
        // 实时背景按钮：和 L 键同一条路
        sink.add(128, 20, 150, 16, () -> {
            liveIndex = com.hysx.salvosequence.live.LiveBackdrops.cycle(1);
            com.hysx.salvosequence.live.LiveBackdrops.setSelected(liveIndex);
            if (liveIndex >= 0) {
                planetMode = false;
            }
            hotspotDirty = true;
        });
        sink.add(BATCH_X, BATCH_Y, BATCH_W, BATCH_H, this::exportAll);
        if (planetMode && planet != null) {
            sink.add(4, 40, 57, 16, planet::newSeed);
            sink.add(65, 40, 57, 16, planet::regen);
            sink.add(4, 60, 57, 16, () -> planet.adjustAzimuth(-15f));
            sink.add(65, 60, 57, 16, () -> planet.adjustAzimuth(15f));
            sink.add(4, 80, 57, 16, () -> planet.adjustElevation(-10f));
            sink.add(65, 80, 57, 16, () -> planet.adjustElevation(10f));
            sink.add(4, 100, 118, 16, () -> {
                planet.newSeed();
                planet.regen();
            });
        }
    }

    private static final int BATCH_X = 468;
    private static final int BATCH_Y = 300;
    private static final int BATCH_W = 168;
    private static final int BATCH_H = 18;

    private void drawBatchButton(CanvasBuffer buffer, PixelArt art) {
        art.rect(BATCH_X, BATCH_Y, BATCH_W, BATCH_H, Palette.PANEL_ALT);
        art.frame(BATCH_X, BATCH_Y, BATCH_W, BATCH_H, Palette.PANEL_EDGE);
        buffer.textCentered(Language.t("P 批量导出整个文件夹", "P EXPORT WHOLE FOLDER"),
                BATCH_X + BATCH_W / 2.0, BATCH_Y + 9, 12, Palette.CYAN);
    }

    private void drawBtn(PixelArt art, int x, int y, int w, int h,
                         String label, com.hysx.salvosequence.core.Color color) {
        art.rect(x, y, w, h, Palette.PANEL_ALT);
        art.frame(x, y, w, h, Palette.PANEL_EDGE);
        // 按钮文字可能是中文：PixelFont 只有 ASCII 字形，中文得走 CjkRasterizer
        int tw = zhWidth(label, 12);
        zhArt(art, label, x + (w - tw) / 2, y + 5, color);
    }

    // ---------------------------------------------------------------- 源图管理

    private void rescan() {
        dir.mkdirs();
        files = BackdropBatch.sources(dir);
        fileIndex = Math.min(fileIndex, Math.max(0, files.length - 1));
        loadCurrent();
    }

    private void loadCurrent() {
        if (files.length == 0) {
            src = null;
            srcW = 0;
            srcH = 0;
            srcName = "(把图放进 backdrops-src/)";
            dirty = true;
            return;
        }
        File file = files[fileIndex];
        ImageCodec.Backend codec = ImageCodec.backend();
        ImageCodec.Loaded img = codec == null ? null : codec.loadScaled(file, 1600, 900);
        if (img == null) {
            src = null;
            loadError = codec == null ? "当前平台没有图片解码器" : "读图失败: " + file.getName();
        } else {
            // 口径同原 JavaFX Image(file, 1600, 900, preserveRatio=true, smooth=true)：
            // 等比缩放进 1600x900 的框里、双线性平滑，由平台后端负责
            src = img.argb();
            srcW = img.width();
            srcH = img.height();
            srcName = file.getName();
            loadError = "";
        }
        dirty = true;
    }

    /**
     * 批量导出：{@code backdrops-src/} 里每张图都按当前参数像素化，
     * 写进 {@code backdrops/batch/<时间戳>/}（每次导出新建一个文件夹，旧批次不覆盖）。
     */
    private void exportAll() {
        if (planetMode) {
            toast = Language.t("星球模式没有批量导出（请先关掉星球模式）",
                    "planet mode has no batch export");
            toastLife = 3.0;
            return;
        }
        try {
            BackdropBatch.Result r = BackdropBatch.exportAll(dir,
                    new File(BackdropBatch.ROOT), params, java.time.LocalDateTime.now(), filter());
            if (r.written() == 0 && r.skipped() == 0) {
                toast = Language.t("backdrops-src/ 里没有图片",
                        "no images in backdrops-src/");
            } else {
                String where = r.folder().getPath();
                toast = r.skipped() == 0
                        ? Language.t("批量导出 " + r.written() + " 张 → " + where,
                                "exported " + r.written() + " -> " + where)
                        : Language.t("批量导出 " + r.written() + " 张（跳过 " + r.skipped()
                                + "）→ " + where,
                                "exported " + r.written() + " (skipped " + r.skipped()
                                        + ") -> " + where);
            }
        } catch (Exception e) {
            toast = Language.t("批量导出失败: ", "batch export failed: ") + e.getMessage();
        }
        toastLife = 5.0;
    }

    private void export() {
        if (preview == null) {
            return;
        }
        outDir.mkdirs();
        String base = srcName.lastIndexOf('.') > 0
                ? srcName.substring(0, srcName.lastIndexOf('.')) : "backdrop";
        String name = base + BackdropFiltering.suffix(filter(), params) + ".png";
        File out = new File(outDir, name);
        ImageCodec.Backend codec = ImageCodec.backend();
        if (codec != null && codec.writePng(preview, BayerPixelator.W, BayerPixelator.H, out)) {
            toast = "已导出 backdrops/custom/" + name + "（重启生效）";
        } else {
            toast = "导出失败: " + name;
        }
        toastLife = 3.0;
    }

    // ---------------------------------------------------------------- 文本助手

    /** 中文串的像素宽度（按 12 号量；光栅化有缓存，按钮上量几次不心疼）。 */
    private static int zhWidth(String text, double size) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        com.hysx.salvosequence.core.CjkRasterizer.Rendered img =
                com.hysx.salvosequence.core.CjkRasterizer.render(text, size,
                        com.hysx.salvosequence.core.Palette.LINE);
        return img == null ? 0 : img.width();
    }

    /** 直接往像素画布刷一段中文（PixelFont 没有 CJK 字形）。 */
    private static void zhArt(PixelArt art, String text, int x, int top,
                              com.hysx.salvosequence.core.Color color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        com.hysx.salvosequence.core.CjkRasterizer.Rendered img =
                com.hysx.salvosequence.core.CjkRasterizer.render(text, 12, color);
        if (img != null) {
            art.blitArgb(img.argb(), img.width(), img.height(), x, top, false);
        }
    }

    private void zh(CanvasBuffer buffer, String text, double x, double y, double size,
                    com.hysx.salvosequence.core.Color color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        buffer.text(text, x, y + size * 0.92, size, color);
    }

    private void en(PixelArt art, String text, int x, int y, com.hysx.salvosequence.core.Color color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        font.draw(art, text, x, y, color);
    }

    @Override
    public ScreenContext pollTransition() {
        return null;
    }

    @Override
    public void onExit() {
        if (planet != null) {
            planet.dispose();
            planet = null;
        }
    }

    // ------------------------------------------------------------ 实时场景调参

    /** 面板行数：第 0 页是相机三行，其余页每页 LIVE_ROWS 个参数。 */
    private int liveRowCount() {
        if (liveIndex < 0) {
            return 0;
        }
        return livePage == 0 ? 3 : livePageFields().size();
    }

    private int livePageCount() {
        int n = LiveTune.fields(LiveBackdrops.params(liveIndex)).size();
        return 1 + (n + LIVE_ROWS - 1) / LIVE_ROWS;
    }

    private List<Field> livePageFields() {
        List<Field> all = LiveTune.fields(LiveBackdrops.params(liveIndex));
        int from = (livePage - 1) * LIVE_ROWS;
        if (from >= all.size()) {
            return List.of();
        }
        return all.subList(from, Math.min(all.size(), from + LIVE_ROWS));
    }

    private void liveCyclePage() {
        livePage = (livePage + 1) % livePageCount();
        liveCursor = 0;
    }

    private void markParamsDirty() {
        paramsDirty = true;
        saveDelay = 0.4;
    }

    /** 调一行：自己 ±1 步。相机三行按角度/距离给更合适的步长。 */
    private void liveAdjust(int row, int dir) {
        if (liveIndex < 0 || row < 0 || row >= liveRowCount()) {
            return;
        }
        double[] cam = LiveBackdrops.camera(liveIndex);
        if (livePage == 0) {
            if (row == 0) {
                cam[0] += dir * 5;
            } else if (row == 1) {
                cam[1] = Math.max(-80, Math.min(80, cam[1] + dir * 3));
            } else {
                cam[2] = Math.max(0.45, Math.min(40, cam[2] * (dir > 0 ? 0.92 : 1.087)));
            }
            LiveBackdrops.setCamera(liveIndex, cam[0], cam[1], cam[2]);
        } else {
            List<Field> fields = livePageFields();
            if (row >= fields.size()) {
                return;
            }
            Object p = LiveBackdrops.params(liveIndex);
            Field f = fields.get(row);
            LiveTune.set(p, f, LiveTune.get(p, f) + dir * LiveTune.step(p, f));
        }
        liveTime += 0.001;
        dirty = true;
        markParamsDirty();
    }

    private void drawLiveParams(CanvasBuffer buffer, PixelArt art) {
        int h = 18 + LIVE_ROWS * LIVE_ROW_H + 6;
        art.rect(LIVE_X, LIVE_Y, LIVE_W, h, PixelArt.fromArgb(0xB4000000));
        art.frame(LIVE_X, LIVE_Y, LIVE_W, h, Palette.LINE_DIM);
        String title = Language.t("实时参数 ", "LIVE PARAMS");
        zh(buffer, title, LIVE_X + 6, LIVE_Y + 4, 12, Palette.CYAN);
        zh(buffer, (livePage + 1) + "/" + livePageCount(), LIVE_X + 92, LIVE_Y + 4, 12,
                Palette.LINE_DIM);

        if (livePage == 0) {
            double[] cam = LiveBackdrops.camera(liveIndex);
            String[][] rows = {
                    {Language.t("偏航", "YAW"), String.format("%.0f°", cam[0])},
                    {Language.t("俯仰", "PITCH"), String.format("%.0f°", cam[1])},
                    {Language.t("距离", "DIST"), String.format("%.2f", cam[2])},
            };
            for (int i = 0; i < rows.length; i++) {
                int top = LIVE_Y + 22 + i * LIVE_ROW_H;
                if (i == liveCursor) {
                    art.fillRect(LIVE_X + 4, top - 2, LIVE_W - 8, LIVE_ROW_H - 2, Palette.PANEL_ALT);
                }
                zh(buffer, rows[i][0], LIVE_X + 8, top, 12,
                        i == liveCursor ? Palette.LINE : Palette.LINE_DIM);
                zh(buffer, rows[i][1], LIVE_X + LIVE_W - 8 - zhWidth(rows[i][1], 12), top, 12,
                        Palette.CYAN);
            }
        } else {
            Object p = LiveBackdrops.params(liveIndex);
            List<Field> fields = livePageFields();
            for (int i = 0; i < fields.size(); i++) {
                Field f = fields.get(i);
                int top = LIVE_Y + 22 + i * LIVE_ROW_H;
                if (i == liveCursor) {
                    art.fillRect(LIVE_X + 4, top - 2, LIVE_W - 8, LIVE_ROW_H - 2, Palette.PANEL_ALT);
                }
                zh(buffer, shortName(f.getName()), LIVE_X + 8, top, 12,
                        i == liveCursor ? Palette.LINE : Palette.LINE_DIM);
                String val = LiveTune.text(p, f);
                zh(buffer, val, LIVE_X + LIVE_W - 8 - zhWidth(val, 12), top, 12, Palette.CYAN);
            }
        }
    }

    /** 常用参数给个短中文名，其余用字段名截短（面板只有 168px 宽）。 */
    private static String shortName(String name) {
        String zh = switch (name) {
            case "seed" -> "种子";
            case "noiseScale" -> "噪声尺度";
            case "noiseDetail" -> "细节";
            case "noiseRoughness" -> "粗糙度";
            case "distortion" -> "扭曲";
            case "dustDensity" -> "尘埃密度";
            case "gasDensity" -> "气体密度";
            case "dustThreshold" -> "尘埃阈值";
            case "exposure" -> "曝光";
            case "bloom" -> "泛光";
            case "spikes" -> "星芒";
            case "dispersion" -> "色散";
            case "temperature" -> "色温";
            case "granulation" -> "米粒组织";
            case "spots" -> "黑子";
            case "limb" -> "临边昏暗";
            case "flares" -> "耀斑";
            case "prominences" -> "日珥";
            case "spinSpeed" -> "自转";
            case "tiltDeg" -> "倾角";
            case "starRadius" -> "星半径";
            case "bloomStrength" -> "泛光强度";
            case "bloomThreshold" -> "泛光阈值";
            default -> null;
        };
        if (zh != null) {
            return zh;
        }
        return name.length() <= 11 ? name : name.substring(0, 11);
    }

    private void livePress(double mx, double my) {
        liveDragX = mx;
        liveDragY = my;
        // 点标题翻页
        if (liveIndex >= 0 && my >= LIVE_Y && my <= LIVE_Y + 18 && mx >= LIVE_X + 4
                && mx <= LIVE_X + 90) {
            liveCyclePage();
            return;
        }
        // 点参数行：选中；再点同一行的数值列 = 加，左侧标签列 = 减
        if (liveIndex >= 0 && my > LIVE_Y + 22 && my <= LIVE_Y + 22 + LIVE_ROWS * LIVE_ROW_H
                && mx >= LIVE_X && mx <= LIVE_X + LIVE_W) {
            int row = (int) ((my - (LIVE_Y + 22)) / LIVE_ROW_H);
            if (row >= 0 && row < liveRowCount()) {
                // 一次点击就调：数值列加、标签列减。以前要"先点选中、再点同一行"才生效，
                // 鼠标用户基本发现不了，看着就像参数点不动。
                liveCursor = row;
                liveAdjust(row, mx >= LIVE_X + 100 ? +1 : -1);
            }
        }
    }

    private void liveDrag(double mx, double my) {
        if (liveIndex < 0 || liveDragX < 0) {
            return;
        }
        LiveBackdrops.orbit(liveIndex, mx - liveDragX, my - liveDragY);
        liveDragX = mx;
        liveDragY = my;
        liveTime += 0.001;
        dirty = true;
        markParamsDirty();
    }

    private void liveMove(double mx, double my) {
        // 只是把指针位置记下来，真正旋转在按下之后才开始
    }
}
