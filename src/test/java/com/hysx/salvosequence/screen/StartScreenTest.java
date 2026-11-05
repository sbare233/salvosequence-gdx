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
        VideoSettings.setScanlineOrder(VideoSettings.ScanlineOrder.AFTER_EFFECT);
        VideoSettings.setApertureGrille(false);
        VideoSettings.setScanlineShape(VideoSettings.ScanlineShape.JAGGED);
        VideoSettings.setBackdropFilter(VideoSettings.BackdropFilter.BAYER);
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
    void settingsHasAnApertureGrilleRowRightAfterTheScanlineTiming() {
        StartScreen screen = openSettings();
        List<String> settings = draw(screen);
        List<String> rows = settings.stream().filter(t -> t.startsWith("孔径格栅：")).toList();
        assertEquals(1, rows.size(), "设置页应当有一行孔径格栅：" + settings);
        assertTrue(rows.get(0).endsWith("关"), "默认关着：" + rows.get(0));

        // 扫描线(0) -> 形状(1) -> 时机(2) -> 孔径格栅(3)
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("ENTER");
        assertTrue(VideoSettings.apertureGrille(), "回车打开孔径格栅");
        settings = draw(screen);
        assertTrue(settings.stream().filter(t -> t.startsWith("孔径格栅：")).findFirst()
                .orElseThrow().endsWith("开"));
        screen.onKey("ENTER");
        assertFalse(VideoSettings.apertureGrille(), "再按一次关掉");
    }

    @Test
    void settingsHasALanguageRowThatSwitchesTheWholeUi() {
        StartScreen screen = openSettings();
        List<String> settings = draw(screen);
        assertTrue(settings.contains("设置"));
        assertTrue(settings.stream().anyMatch(t -> t.startsWith("语言：")),
                "设置里有独立的语言项");

        // 设置游标移到「语言」（扫描线 / 形状 / 时机 / 孔径格栅 / 风格化 / 背景滤镜之后）并切换
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
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
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("ENTER");
        screen.onKey("ESCAPE");
        assertTrue(draw(screen).contains("舰队出发"));
    }

    @Test
    void settingsHasABackdropFilterRowThatCyclesBayerAndPixel() {
        StartScreen screen = openSettings();
        List<String> rows = draw(screen).stream()
                .filter(t -> t.startsWith("背景滤镜：")).toList();
        assertEquals(1, rows.size(), "设置页应当有一行背景滤镜");
        assertTrue(rows.get(0).endsWith("拜耳"), "默认拜耳：" + rows.get(0));

        // 扫描线(0) -> 形状(1) -> 时机(2) -> 孔栅(3) -> 风格化(4) -> 背景滤镜(5)
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
    void settingsHasAScanlineShapeRowRightAfterTheScanlineSwitch() {
        StartScreen screen = openSettings();
        List<String> rows = draw(screen).stream()
                .filter(t -> t.startsWith("扫描线形状：")).toList();
        assertEquals(1, rows.size(), "设置页应当有一行扫描线形状");
        assertTrue(rows.get(0).endsWith("锯齿"), "默认锯齿：" + rows.get(0));

        // 扫描线(0) -> 形状(1)
        screen.onKey("DOWN");
        screen.onKey("ENTER");
        assertEquals(VideoSettings.ScanlineShape.STRAIGHT, VideoSettings.scanlineShape());
        assertTrue(draw(screen).stream().filter(t -> t.startsWith("扫描线形状："))
                .findFirst().orElseThrow().endsWith("直条"));
    }

    @Test
    void settingsHasAScanlineTimingRowBeforeAfterEffects() {
        StartScreen screen = openSettings();
        List<String> texts = draw(screen);
        boolean before = VideoSettings.scanlineOrder() == VideoSettings.ScanlineOrder.BEFORE_EFFECT;
        assertTrue(texts.stream().anyMatch(t -> t.startsWith("扫描线时机：")
                        && t.endsWith(before ? "特效前" : "特效后")),
                "当前时机行显示的是实际设置");

        // 扫描线(0) -> 形状(1) -> 时机(2)
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("ENTER");
        VideoSettings.ScanlineOrder flipped = before
                ? VideoSettings.ScanlineOrder.AFTER_EFFECT
                : VideoSettings.ScanlineOrder.BEFORE_EFFECT;
        assertEquals(flipped, VideoSettings.scanlineOrder());
        assertTrue(draw(screen).stream().anyMatch(t -> t.startsWith("扫描线时机：")
                        && t.endsWith(before ? "特效后" : "特效前")),
                "切换后行文案跟着变");

        screen.onKey("ENTER");
        assertEquals(before ? VideoSettings.ScanlineOrder.BEFORE_EFFECT
                : VideoSettings.ScanlineOrder.AFTER_EFFECT, VideoSettings.scanlineOrder());
    }
}
