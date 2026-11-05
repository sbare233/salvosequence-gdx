package com.hysx.salvosequence.ui;

import com.hysx.salvosequence.core.CanvasBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 界面管理器的两条全局约定：返回键一直画得出来、也点得到；触摸模式下轻点不建立悬浮。
 *
 * <p>这里不碰 Graphics：{@link ScreenManager} 的绘制只往 {@link CanvasBuffer} 里画，
 * 指针换算本来就守了 {@code Gdx.app == null}，所以能在无头环境里跑。</p>
 */
final class ScreenManagerTest {

    @Test
    void everyScreenCarriesABackButton() {
        ScreenManager screens = new ScreenManager();
        CanvasBuffer buffer = new CanvasBuffer(640, 360);

        screens.draw(buffer);

        assertTrue(buffer.texts().stream()
                        .anyMatch(run -> run.content().equals("返回") || run.content().equals("BACK")),
                "返回键的标签应该画在缓冲里");
    }

    @Test
    void theBackButtonIsTheBottomLeftHotspot() {
        ScreenManager screens = new ScreenManager();

        // 左下角返回键的矩形是 6,334 48x20（设计坐标 y 向下）
        assertTrue(screens.mouseMoved(28, 344), "指针落在返回键上应该命中热区");
        assertFalse(screens.mouseMoved(600, 40), "右上角空白处不该有热区");
    }

    @Test
    void thePortraitHotkeyIsClaimedOnEveryScreen() {
        ScreenManager screens = new ScreenManager();

        // PixelGameView 先把 F12 问一遍界面管理器：不管当前在哪一屏都得认领，
        // 否则它会被窗口级的诊断导出抢走（那是 F10 的事）。
        assertTrue(screens.consumesKey("F12"), "F12 应被全局认领");
        assertFalse(screens.consumesKey("F10"), "F10 留给窗口级诊断导出");
    }

    @Test
    void aTapOnEmptySpaceIsNotConsumed() {
        ScreenManager screens = new ScreenManager();
        screens.setTouchHover(true);

        assertFalse(screens.touchDown(600, 40));
        assertFalse(screens.touchUp(600, 40));
    }
}
