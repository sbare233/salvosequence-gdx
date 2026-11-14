package com.hysx.salvosequence.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.ui.Language;
import com.hysx.salvosequence.ui.VideoSettings;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 标题界面与设置页：默认全中文，设置里能把界面切成英文再切回来。 */
class StartScreenTest {

    @AfterEach
    void reset() {
        Language.set(Language.ZH);
        VideoSettings.setCrtFilter(VideoSettings.CrtFilter.SCANLINE_GRILLE);
        VideoSettings.setGrilleStyle(VideoSettings.GrilleStyle.APERTURE);
        VideoSettings.setScreenWarp(false);
        VideoSettings.setBloom(true);
        VideoSettings.setBackdropFilter(VideoSettings.BackdropFilter.BAYER);
        VideoSettings.resetMixK();
    }

    private static List<String> draw(StartScreen screen) {
        CanvasBuffer buffer = new CanvasBuffer(640, 360);
        screen.draw(buffer);
        return buffer.texts().stream().map(CanvasBuffer.TextRun::content).toList();
    }

    private static StartScreen openSettings() {
        StartScreen screen = new StartScreen();
        // 菜单第三项是「设置」
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("ENTER");
        return screen;
    }

    @Test
    void chineseModeHasNoEnglishDecorations() {
        StartScreen screen = new StartScreen();
        List<String> texts = draw(screen);
        assertTrue(texts.contains("舰队出发"), "中文菜单");
        assertTrue(texts.contains("齐射序列"), "中文标题");
        assertFalse(texts.stream().anyMatch(t -> t.contains("SORTIE")), "不再显示英文副标题");
    }

    @Test
    void settingsHasACrtRowThatCyclesTheFilterModes() {
        VideoSettings.setCrtFilter(VideoSettings.CrtFilter.SCANLINE_GRILLE);
        StartScreen screen = openSettings();
        List<String> rows = draw(screen).stream()
                .filter(t -> t.startsWith("CRT 滤镜：")).toList();
        assertEquals(1, rows.size(), "设置页应当有一行 CRT 滤镜：" + draw(screen));
        assertTrue(rows.get(0).endsWith("扫描线+格栅"), "默认完整 CRT：" + rows.get(0));

        // 游标默认在 CRT（0）：关 → 扫描线 → 孔径格栅 → 扫描线+格栅
        screen.onKey("ENTER");
        assertEquals(VideoSettings.CrtFilter.OFF, VideoSettings.crtFilter());
        assertTrue(crtRow(screen).endsWith("关"), crtRow(screen));
        screen.onKey("ENTER");
        assertEquals(VideoSettings.CrtFilter.SCANLINE, VideoSettings.crtFilter(),
                "只开扫描线");
        assertTrue(crtRow(screen).endsWith("扫描线"), crtRow(screen));
        screen.onKey("ENTER");
        assertEquals(VideoSettings.CrtFilter.GRILLE, VideoSettings.crtFilter(),
                "只开孔径格栅");
        assertTrue(crtRow(screen).endsWith("孔径格栅"), crtRow(screen));
        screen.onKey("ENTER");
        assertEquals(VideoSettings.CrtFilter.SCANLINE_GRILLE, VideoSettings.crtFilter(),
                "再按一次叠加两种");
        assertTrue(crtRow(screen).endsWith("扫描线+格栅"), crtRow(screen));
    }

    private static String crtRow(StartScreen screen) {
        return draw(screen).stream().filter(t -> t.startsWith("CRT 滤镜："))
                .findFirst().orElseThrow();
    }

    @Test
    void settingsHasABackdropFilterRowThatCyclesBayerAndPixel() {
        StartScreen screen = openSettings();
        List<String> rows = draw(screen).stream()
                .filter(t -> t.startsWith("背景滤镜：")).toList();
        assertEquals(1, rows.size(), "设置页应当有一行背景滤镜");
        assertTrue(rows.get(0).endsWith("拜耳"), "默认拜耳：" + rows.get(0));

        // CRT(0) -> 格栅图案(1) -> 屏幕扭曲(2) -> 混合柔化(3) -> 混合 K(4) -> 背景滤镜(5)
        for (int i = 0; i < 5; i++) {
            screen.onKey("DOWN");
        }
        screen.onKey("ENTER");
        assertEquals(VideoSettings.BackdropFilter.PIXEL, VideoSettings.backdropFilter());
        assertTrue(draw(screen).stream().filter(t -> t.startsWith("背景滤镜："))
                .findFirst().orElseThrow().endsWith("像素"));

        screen.onKey("ENTER");
        assertEquals(VideoSettings.BackdropFilter.BAYER, VideoSettings.backdropFilter(),
                "第二轮回到拜耳");
    }

    @Test
    void settingsHasAGrilleStyleRowThatCyclesApertureAndShadow() {
        StartScreen screen = openSettings();
        assertTrue(grilleRow(screen).endsWith("栅条"), grilleRow(screen));

        // CRT(0) -> 格栅图案(1)
        screen.onKey("DOWN");
        screen.onKey("ENTER");
        assertEquals(VideoSettings.GrilleStyle.SHADOW, VideoSettings.grilleStyle());
        assertTrue(grilleRow(screen).endsWith("荫罩"), grilleRow(screen));

        screen.onKey("ENTER");
        assertEquals(VideoSettings.GrilleStyle.APERTURE, VideoSettings.grilleStyle(),
                "第二轮回到纵向栅条");
    }

    @Test
    void settingsHasAScreenWarpRowThatTogglesIndependently() {
        StartScreen screen = openSettings();
        assertTrue(warpRow(screen).endsWith("关"), "默认关：" + warpRow(screen));

        // CRT(0) -> 格栅图案(1) -> 屏幕扭曲(2)
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("ENTER");
        assertTrue(VideoSettings.screenWarp());
        assertTrue(warpRow(screen).endsWith("开"), warpRow(screen));
        // 只开扭曲：CRT 和格栅都保持原样
        assertEquals(VideoSettings.CrtFilter.SCANLINE_GRILLE, VideoSettings.crtFilter());
        assertEquals(VideoSettings.GrilleStyle.APERTURE, VideoSettings.grilleStyle());

        screen.onKey("ENTER");
        assertFalse(VideoSettings.screenWarp());
    }

    @Test
    void settingsHasASoftMixRowThatTogglesIndependently() {
        StartScreen screen = openSettings();
        assertTrue(bloomRow(screen).endsWith("开"), "默认开：" + bloomRow(screen));

        // CRT(0) -> 格栅图案(1) -> 屏幕扭曲(2) -> 混合柔化(3)
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("ENTER");
        assertFalse(VideoSettings.bloom());
        assertTrue(bloomRow(screen).endsWith("关"), bloomRow(screen));
        assertEquals(VideoSettings.CrtFilter.SCANLINE_GRILLE, VideoSettings.crtFilter(),
                "只关混合，CRT 滤镜不变");
        assertEquals(VideoSettings.GrilleStyle.APERTURE, VideoSettings.grilleStyle());

        screen.onKey("ENTER");
        assertTrue(VideoSettings.bloom());
    }

    @Test
    void settingsHasAMixRowThatNudgesWithTheArrowKeys() {
        StartScreen screen = openSettings();
        assertTrue(draw(screen).stream().anyMatch(t -> t.startsWith("混合 K：")),
                "设置里有混合 K 滑条");

        // CRT(0) -> 格栅图案(1) -> 屏幕扭曲(2) -> 混合柔化(3) -> 混合 K(4)
        for (int i = 0; i < 4; i++) {
            screen.onKey("DOWN");
        }
        screen.onKey("RIGHT");
        assertEquals(1.05, VideoSettings.mixK(), 1e-9, "右方向键加一档");
        screen.onKey("LEFT");
        screen.onKey("LEFT");
        assertEquals(0.95, VideoSettings.mixK(), 1e-9, "左方向键减一档");

        // 回车把滑条归位，不影响开关本身
        screen.onKey("ENTER");
        assertEquals(1.0, VideoSettings.mixK(), 1e-9);
        assertTrue(VideoSettings.bloom(), "归位只动 k，不开关混合");

        // 量程两端要钳住
        for (int i = 0; i < 40; i++) {
            screen.onKey("RIGHT");
        }
        assertEquals(VideoSettings.MIX_K_MAX, VideoSettings.mixK(), 1e-9);
        for (int i = 0; i < 80; i++) {
            screen.onKey("LEFT");
        }
        assertEquals(VideoSettings.MIX_K_MIN, VideoSettings.mixK(), 1e-9);
    }

    @Test
    void draggingTheMixSliderFollowsThePointer() {
        StartScreen screen = openSettings();
        VideoSettings.resetMixK();
        SpotSink sink = new SpotSink();
        screen.collectHotspots(sink);

        // 混合 K 是第 5 行：横幅顶边 y = 52 + 40 + 4*22 - 15 = 169，高 19
        assertTrue(sink.press(290, 178), "滑条行应当按得下");
        assertEquals(VideoSettings.MIX_K_MIN, VideoSettings.mixK(), 0.001,
                "轨道左端就是量程下限");
        assertTrue(sink.press(394, 178));
        assertEquals(VideoSettings.MIX_K_MAX, VideoSettings.mixK(), 0.001,
                "轨道右端就是量程上限");
        assertTrue(sink.press(342, 178));
        assertEquals(1.625, VideoSettings.mixK(), 0.03, "正中是一半量程");
    }

    @Test
    void settingsHotspotsLineUpWithTheDrawnRows() {
        StartScreen screen = openSettings();
        SpotSink sink = new SpotSink();
        screen.collectHotspots(sink);

        // 屏幕扭曲（第 3 行）的横幅顶边 y = 52 + 40 + 2*22 - 15 = 121，高 19；点它的正中
        assertTrue(sink.click(250, 130), "第 3 行应当可以点中");
        assertTrue(VideoSettings.screenWarp(), "点到的就是屏幕扭曲，不是下一行");
        assertEquals(VideoSettings.BackdropFilter.BAYER, VideoSettings.backdropFilter(),
                "没有误点到背景滤镜");

        // 语言（第 9 行）的横幅顶边 y = 52 + 40 + 8*22 - 15 = 253；点正中切英文
        assertTrue(sink.click(250, 262), "第 9 行应当可以点中");
        assertEquals(Language.EN, Language.current());
    }

    private static String grilleRow(StartScreen screen) {
        return draw(screen).stream().filter(t -> t.startsWith("格栅图案："))
                .findFirst().orElseThrow();
    }

    private static String warpRow(StartScreen screen) {
        return draw(screen).stream().filter(t -> t.startsWith("屏幕扭曲："))
                .findFirst().orElseThrow();
    }

    private static String bloomRow(StartScreen screen) {
        return draw(screen).stream().filter(t -> t.startsWith("混合柔化："))
                .findFirst().orElseThrow();
    }

    /** 只关心设置页的 track 热区：记录成可点矩形；拖动行另记一份按下回调。 */
    private static final class SpotSink implements com.hysx.salvosequence.render.HotspotSink {
        private record Spot(double x, double y, double w, double h, Runnable action) {
            boolean contains(double px, double py) {
                return px >= x && py >= y && px < x + w && py < y + h;
            }
        }

        private record DragSpot(double x, double y, double w, double h,
                                java.util.function.BiConsumer<Double, Double> onPress) {
            boolean contains(double px, double py) {
                return px >= x && py >= y && px < x + w && py < y + h;
            }
        }

        private final java.util.List<Spot> spots = new java.util.ArrayList<>();
        private final java.util.List<DragSpot> drags = new java.util.ArrayList<>();

        @Override
        public void add(double x, double y, double w, double h, Runnable action) {
            spots.add(new Spot(x, y, w, h, action));
        }

        @Override
        public void track(double x, double y, double w, double h,
                          java.util.function.BiConsumer<Double, Double> onMove,
                          Runnable action, Runnable onEnter, Runnable onExit) {
            spots.add(new Spot(x, y, w, h, action));
        }

        @Override
        public void drag(double x, double y, double w, double h,
                         java.util.function.BiConsumer<Double, Double> onDrag,
                         java.util.function.BiConsumer<Double, Double> onPress) {
            drags.add(new DragSpot(x, y, w, h, onDrag));
        }

        boolean click(double px, double py) {
            for (int i = spots.size() - 1; i >= 0; i--) {
                if (spots.get(i).contains(px, py)) {
                    spots.get(i).action().run();
                    return true;
                }
            }
            return false;
        }

        /** 在可拖动区域上按下（滑条按下的那一刻就跟着指针走）。 */
        boolean press(double px, double py) {
            for (int i = drags.size() - 1; i >= 0; i--) {
                if (drags.get(i).contains(px, py)) {
                    drags.get(i).onPress().accept(px, py);
                    return true;
                }
            }
            return false;
        }
    }

    @Test
    void settingsHasALanguageRowThatSwitchesTheWholeUi() {
        StartScreen screen = openSettings();
        List<String> settings = draw(screen);
        assertTrue(settings.contains("设置"));
        assertTrue(settings.stream().anyMatch(t -> t.startsWith("语言：")),
                "设置里有独立的语言项");

        // CRT(0) / 格栅图案(1) / 屏幕扭曲(2) / 混合柔化(3) / 混合 K(4) / 背景滤镜(5) / 视差(6) / 透视(7) / 语言(8)
        for (int i = 0; i < 8; i++) {
            screen.onKey("DOWN");
        }
        screen.onKey("ENTER");
        List<String> english = draw(screen);
        assertTrue(english.contains("SETTINGS"), "设置标题切成英文");
        assertTrue(english.stream().anyMatch(t -> t.contains("SALVO SEQUENCE")),
                "标题切成英文");

        // 关掉设置回菜单，英文菜单项应该出现
        screen.onKey("ESCAPE");
        List<String> menu = draw(screen);
        assertTrue(menu.contains("SORTIE"));
        assertFalse(menu.contains("舰队出发"));

        // 再切回中文：菜单光标仍停在「设置」上，直接进场、下移到语言项切换
        screen.onKey("ENTER");
        for (int i = 0; i < 8; i++) {
            screen.onKey("DOWN");
        }
        screen.onKey("ENTER");
        screen.onKey("ESCAPE");
        assertTrue(draw(screen).contains("舰队出发"));
    }
}
