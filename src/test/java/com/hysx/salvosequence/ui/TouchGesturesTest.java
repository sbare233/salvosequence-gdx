package com.hysx.salvosequence.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 触摸手势：点击、长按悬浮、双指缩放。用假 sink 记录派发到界面层的调用序列，
 * 全程不碰 libGDX / 图形环境。
 */
class TouchGesturesTest {

    /** 记录手势派发到界面层的调用序列。 */
    private static final class Recorder implements TouchGestures.PointerSink {

        final List<String> calls = new ArrayList<>();
        double zoomDelta = Double.NaN;
        double zoomX;
        double zoomY;

        @Override
        public boolean touchDown(double x, double y) {
            calls.add("down@" + pt(x, y));
            return true;
        }

        @Override
        public boolean touchUp(double x, double y) {
            calls.add("up@" + pt(x, y));
            return true;
        }

        @Override
        public boolean touchDragged(double x, double y) {
            calls.add("drag@" + pt(x, y));
            return true;
        }

        @Override
        public boolean mouseMoved(double x, double y) {
            calls.add("hover@" + pt(x, y));
            return true;
        }

        @Override
        public void handleScroll(double delta, double x, double y) {
            zoomDelta = delta;
            zoomX = x;
            zoomY = y;
            calls.add("zoom");
        }

        @Override
        public void cancelPointer() {
            calls.add("cancel");
        }

        String last() {
            return calls.isEmpty() ? "" : calls.get(calls.size() - 1);
        }

        private static String pt(double x, double y) {
            return String.format("%.0f,%.0f", x, y);
        }
    }

    @Test
    void aTapIsADownFollowedByAnUp() {
        Recorder sink = new Recorder();
        TouchGestures g = new TouchGestures(sink, true);

        g.touchDown(0, 100, 100);
        g.touchUp(0, 100, 100);

        assertEquals(List.of("down@100,100", "up@100,100"), sink.calls);
    }

    @Test
    void holdingStillTurnsIntoHoverAndSwallowsTheClick() {
        Recorder sink = new Recorder();
        TouchGestures g = new TouchGestures(sink, true);

        g.touchDown(0, 120, 160);
        g.update(TouchGestures.LONG_PRESS_SECONDS / 2);
        assertFalse(g.isHovering(), "半程不该已经触发悬浮");

        g.update(TouchGestures.LONG_PRESS_SECONDS);
        assertTrue(g.isHovering(), "按住超过阈值 = 悬浮");
        assertEquals("hover@120,160", sink.last());

        // 看过之后松手：只作废按下，不算点击
        g.touchUp(0, 120, 160);
        assertFalse(sink.calls.contains("up@120,160"), "长按之后的松手不能点按钮");
        assertEquals("cancel", sink.last());
    }

    @Test
    void movingCancelsTheLongPressAndBecomesADrag() {
        Recorder sink = new Recorder();
        TouchGestures g = new TouchGestures(sink, true);

        g.touchDown(0, 100, 100);
        g.touchDragged(0, 100 + TouchGestures.SLOP_DESIGN + 4, 100);
        g.update(TouchGestures.LONG_PRESS_SECONDS * 4);

        assertFalse(g.isHovering(), "抖出容差就是在拖动，不该再触发悬浮");
        assertTrue(sink.calls.contains("drag@" + Recorder.pt(112, 100)));

        g.touchUp(0, 112, 100);
        assertTrue(sink.calls.contains("up@112,100"), "拖动之后松手仍是正常点击收尾");
    }

    @Test
    void aTinyJitterStillCountsAsHolding() {
        Recorder sink = new Recorder();
        TouchGestures g = new TouchGestures(sink, true);

        g.touchDown(0, 100, 100);
        g.touchDragged(0, 103, 102);
        g.update(TouchGestures.LONG_PRESS_SECONDS * 2);

        assertTrue(g.isHovering(), "容差内的抖动不影响长按");
    }

    @Test
    void desktopHoldingDoesNotBecomeHover() {
        Recorder sink = new Recorder();
        TouchGestures g = new TouchGestures(sink, false);

        g.touchDown(0, 10, 10);
        g.update(5.0);

        assertFalse(g.isHovering(), "桌面鼠标按住不动不该变成悬浮");
        g.touchUp(0, 10, 10);
        assertTrue(sink.calls.contains("up@10,10"));
    }

    @Test
    void twoFingersZoomByTheSpanRatioAroundTheirMidpoint() {
        Recorder sink = new Recorder();
        TouchGestures g = new TouchGestures(sink, true);

        g.touchDown(0, 300, 180);
        g.touchDown(1, 340, 180);
        assertTrue(g.isPinching());
        assertEquals("cancel", sink.last(), "第二根手指落下时单指那一次按下要作废");

        g.touchDragged(1, 380, 180);
        double expected = TouchGestures.scrollDeltaForSpanRatio(40, 80);
        assertEquals(expected, sink.zoomDelta, 1e-6);
        assertEquals(340, sink.zoomX, 1e-6, "锚点是两指中点");
        assertEquals(180, sink.zoomY, 1e-6);
        assertTrue(expected > 0, "两指张开 = 放大（delta 为正）");
    }

    @Test
    void pinchingInZoomsOut() {
        Recorder sink = new Recorder();
        TouchGestures g = new TouchGestures(sink, true);

        g.touchDown(0, 300, 180);
        g.touchDown(1, 340, 180);
        g.touchDragged(1, 320, 180);

        assertTrue(sink.zoomDelta < 0, "两指收拢 = 缩小（delta 为负）");
    }

    @Test
    void endingAPinchDoesNotClickAndSwallowsTheLeftoverDrag() {
        Recorder sink = new Recorder();
        TouchGestures g = new TouchGestures(sink, true);

        g.touchDown(0, 300, 180);
        g.touchDown(1, 340, 180);
        g.touchDragged(1, 380, 180);
        g.touchUp(1, 380, 180);

        g.touchDragged(0, 320, 200);
        assertFalse(sink.calls.contains("drag@320,200"), "缩放刚结束，残指不能再拖画面");

        g.touchUp(0, 320, 200);
        assertFalse(sink.calls.contains("up@320,200"), "双指手势不能以点击收尾");
        assertFalse(g.isTouching());
    }

    @Test
    void oneWheelNotchEqualsTheNotchScale() {
        double delta = TouchGestures.scrollDeltaForSpanRatio(100, 100 * TouchGestures.NOTCH_SCALE);
        assertEquals(40.0, delta, 1e-6, "双指张开一个「滚轮格」应当就是 delta=40");
    }
}
