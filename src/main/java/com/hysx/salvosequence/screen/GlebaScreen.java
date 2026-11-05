package com.hysx.salvosequence.screen;

import com.badlogic.gdx.math.MathUtils;
import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.gleba.GlebaPlanet;
import com.hysx.salvosequence.render.HotspotSink;
import com.hysx.salvosequence.ui.Language;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Gleba 星球（libGDX 3D 建模实时渲染，接入自 gleba-libgdx-mvp）。
 *
 * <p>3D 场景（地形位移网格 + 瑞利散射大气 + 全方位角太阳光照）渲染进 320×180
 * FBO 后最近邻 2 倍贴进设计像素缓冲，吃满像素化/扫描线/后处理全管线。</p>
 *
 * <p>操作全部是屏幕按钮（左侧面板）：新种子 / 重生成 / 球体大小 / 陆地比例 /
 * 太阳方位与高度 / 送入背景工坊（星球模式实时联动拜耳滤镜）/ 返回标题。
 * 指针拖拽 = 轨道旋转，滚轮 = 缩放，Esc = 返回。</p>
 */
public final class GlebaScreen implements Screen {

    private final GlebaPlanet planet = new GlebaPlanet();

    /** 按钮点击产生的界面跳转（pollTransition 消费）。 */
    private ScreenContext pendingSwitch;

    // ------------------------------------------------------------ 按钮布局

    private static final int BX = 6;
    private static final int BW = 104;
    private static final int BH = 16;
    private static final int ROW = 19;
    private static final int BY0 = 24;

    /** 一条按钮：矩形 + 文案 + 动作。标签动态（带当前值），矩形固定。 */
    private record Btn(int x, int y, int w, int h, String label, Runnable action) {
    }

    private List<Btn> buttons() {
        List<Btn> out = new ArrayList<>();
        int r = 0;
        out.add(new Btn(BX, by(r++), BW, BH, Language.t("新种子", "NEW SEED"),
                planet::newSeed));
        out.add(new Btn(BX, by(r++), BW, BH, Language.t("重生成", "REGEN"),
                planet::regen));
        out.add(new Btn(BX, by(r++), BW, BH, Language.t("球体 " + planet.faceRes(), "SIZE " + planet.faceRes()),
                planet::cycleFaceRes));
        int y = by(r++);
        out.add(new Btn(BX, y, 49, BH, Language.t("陆地 -", "LAND -"), () -> planet.adjustLand(-0.05f)));
        out.add(new Btn(BX + 55, y, 49, BH, Language.t("陆地 +", "LAND +"), () -> planet.adjustLand(0.05f)));
        y = by(r++);
        out.add(new Btn(BX, y, 49, BH, Language.t("方位 -", "AZ -"), () -> planet.adjustAzimuth(-15f)));
        out.add(new Btn(BX + 55, y, 49, BH, Language.t("方位 +", "AZ +"), () -> planet.adjustAzimuth(15f)));
        y = by(r++);
        out.add(new Btn(BX, y, 49, BH, Language.t("高度 -", "EL -"), () -> planet.adjustElevation(-10f)));
        out.add(new Btn(BX + 55, y, 49, BH, Language.t("高度 +", "EL +"), () -> planet.adjustElevation(10f)));
        y = by(r++);
        out.add(new Btn(BX, y, 49, BH, Language.t("盘亮 " + String.format(Locale.ROOT, "%.1f", planet.skyBrightness()), "SKY " + String.format(Locale.ROOT, "%.1f", planet.skyBrightness())),
                () -> planet.adjustSkyBrightness(-0.1f)));
        out.add(new Btn(BX + 55, y, 49, BH, Language.t("盘亮 " + String.format(Locale.ROOT, "%.1f", planet.skyBrightness() + 0.1f), "SKY " + String.format(Locale.ROOT, "%.1f", planet.skyBrightness() + 0.1f)),
                () -> planet.adjustSkyBrightness(0.1f)));
        y = by(r++);
        out.add(new Btn(BX, y, 49, BH, Language.t("盘光 " + String.format(Locale.ROOT, "%.2f", Math.max(0f, planet.diskGlow() - 0.05f)), "GLOW " + String.format(Locale.ROOT, "%.2f", Math.max(0f, planet.diskGlow() - 0.05f))),
                () -> planet.adjustDiskGlow(-0.05f)));
        out.add(new Btn(BX + 55, y, 49, BH, Language.t("盘光 " + String.format(Locale.ROOT, "%.2f", Math.min(1f, planet.diskGlow() + 0.05f)), "GLOW " + String.format(Locale.ROOT, "%.2f", Math.min(1f, planet.diskGlow() + 0.05f))),
                () -> planet.adjustDiskGlow(0.05f)));
        out.add(new Btn(BX, by(r++), BW, BH, Language.t("送入背景工坊", "TO BAYER LAB"),
                () -> {
                    BackdropLabScreen.planetModeOnEntry = true;
                    pendingSwitch = ScreenContext.go(ScreenContext.Target.BACKDROP_LAB);
                }));
        out.add(new Btn(BX, by(r++), BW, BH, Language.t("返回标题", "BACK TO TITLE"),
                () -> pendingSwitch = ScreenContext.go(ScreenContext.Target.START)));
        return out;
    }

    private static int by(int row) {
        return BY0 + row * ROW;
    }

    // ------------------------------------------------------------ Screen

    @Override
    public String name() {
        return "gleba";
    }

    @Override
    public void update(double dt) {
        planet.update(dt);
    }

    @Override
    public void draw(CanvasBuffer buffer) {
        PixelArt art = buffer.art();
        planet.renderInto(art);
        drawHud(buffer);
        drawButtons(buffer, art);
    }

    private void drawHud(CanvasBuffer buffer) {
        buffer.text("GLEBA 星球  " + planet.status(), 120, 14, 12, Palette.CYAN);
        buffer.text(Language.t(
                        String.format(Locale.ROOT, "太阳 方位 %.0f° 高度 %.0f°",
                                planet.sunAzimuthDeg(), planet.sunElevDeg()),
                        String.format(Locale.ROOT, "SUN AZ %.0f ELEV %.0f",
                                planet.sunAzimuthDeg(), planet.sunElevDeg())),
                120, 352, 10, Palette.LINE);
        buffer.text(Language.t("拖动 旋转 · 滚轮 缩放",
                "DRAG ORBIT · WHEEL ZOOM"), 640 - 190, 352, 10, Palette.LINE_DIM);
    }

    private void drawButtons(CanvasBuffer buffer, PixelArt art) {
        List<Btn> btns = buttons();
        int panelH = btns.get(btns.size() - 1).y() + BH - BY0 + 8;
        art.rect(BX - 2, BY0 - 6, BW + 4, panelH, PixelArt.fromArgb(0xB4000000));
        art.frame(BX - 2, BY0 - 6, BW + 4, panelH, Palette.LINE_DIM);
        for (Btn b : btns) {
            art.rect(b.x(), b.y(), b.w(), b.h(), Palette.PANEL_ALT);
            art.frame(b.x(), b.y(), b.w(), b.h(), Palette.PANEL_EDGE);
            double tx = b.x() + (b.w() - textW(b.label())) / 2.0;
            buffer.text(b.label(), tx, b.y() + BH - 4, 12, Palette.LINE);
        }
    }

    private double textW(String s) {
        return s.length() * 12.0;
    }

    @Override
    public void collectHotspots(HotspotSink sink) {
        // 全屏轨道旋转先登记（压底）；按钮后登记（压顶，点按钮不触发旋转拖拽）
        sink.draw(0, 0, 640, 360,
                (mx, my) -> { /* 悬浮无额外行为 */ },
                planet::orbitDrag,
                (mx, my) -> planet.orbitReset(),
                null, null);
        for (Btn b : buttons()) {
            sink.add(b.x(), b.y(), b.w(), b.h(), b.action());
        }
    }

    @Override
    public ScreenContext onKey(String code) {
        if ("ESCAPE".equals(code)) {
            pendingSwitch = ScreenContext.go(ScreenContext.Target.START);
        }
        return null;
    }

    @Override
    public ScreenContext pollTransition() {
        ScreenContext out = pendingSwitch;
        pendingSwitch = null;
        return out;
    }

    @Override
    public void onScroll(double delta, double x, double y) {
        planet.scrollZoom(delta);
    }

    @Override
    public void onEnter() {
        planet.orbitReset();
    }

    @Override
    public void onExit() {
        planet.dispose();
    }
}
