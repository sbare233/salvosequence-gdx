package com.hysx.salvosequence.graphics;

import com.hysx.salvosequence.core.PixelArt;

/**
 * 把一个 {@link SpriteSheet} 按时间推进成动画。
 *
 * <p>时间由调用方喂（{@code update(dt)}），不在内部读时钟 —— 这样暂停、
 * 倍速、逐帧调试都能用同一套代码，也让离屏核对（audit）可以一帧一帧地播出确定结果。</p>
 *
 * <p>两个易错点在这里一次性处理掉：</p>
 * <ul>
 *   <li><b>余数不丢</b>：一帧推进跨越多个帧间隔时保留余数，否则低帧率下动画会变慢；</li>
 *   <li><b>不循环时停在末帧</b>：爆炸这类一次性动画播完就静止，而不是回第 0 帧闪一下。</li>
 * </ul>
 */
public final class Anim {

    private final SpriteSheet sheet;
    /** 每秒播放多少帧。 */
    private final double fps;
    private final boolean loop;

    /** 当前帧（浮点，便于取余数）。 */
    private double cursor;
    private boolean finished;

    public Anim(SpriteSheet sheet, double fps, boolean loop) {
        if (sheet == null) {
            throw new IllegalArgumentException("精灵表不能为空");
        }
        this.sheet = sheet;
        this.fps = fps > 0 ? fps : 1;
        this.loop = loop;
    }

    /** 循环动画。 */
    public static Anim loop(SpriteSheet sheet, double fps) {
        return new Anim(sheet, fps, true);
    }

    /** 单次动画（播完停在末帧）。 */
    public static Anim once(SpriteSheet sheet, double fps) {
        return new Anim(sheet, fps, false);
    }

    /**
     * 推进时间。
     *
     * @param dt 经过的秒数（负数会被忽略）
     */
    public void update(double dt) {
        if (finished || dt <= 0) {
            return;
        }
        cursor += dt * fps;
        if (loop) {
            // 取模保留余数，避免长跑之后漂移
            cursor = cursor % sheet.frameCount();
        } else if (cursor >= sheet.frameCount() - 1) {
            cursor = sheet.frameCount() - 1;
            finished = true;
        }
    }

    /** 当前帧索引。 */
    public int index() {
        return (int) Math.floor(cursor);
    }

    /** 当前帧。 */
    public Sprite frame() {
        return sheet.frame(index());
    }

    public SpriteSheet sheet() {
        return sheet;
    }

    /** 单次动画是否已播完。 */
    public boolean isFinished() {
        return finished;
    }

    /** 是否循环。 */
    public boolean isLooping() {
        return loop;
    }

    /** 回到起点（复用于对象池：子弹/爆炸回收后重新播放）。 */
    public void reset() {
        cursor = 0;
        finished = false;
    }

    /** 跳到指定帧。 */
    public void seek(int frameIndex) {
        cursor = Math.max(0, Math.min(sheet.frameCount() - 1, frameIndex));
        finished = false;
    }

    // ------------------------------------------------------------ 绘制出口

    /** 在 (dx, dy) 画当前帧。 */
    public void draw(PixelArt art, int dx, int dy) {
        frame().draw(art, dx, dy);
    }

    /**
     * 在 (dx, dy) 画当前帧，可水平翻转 / 缩放 / 着色（受击闪白用 tint）。
     */
    public void draw(PixelArt art, int dx, int dy, boolean flipX, double scale, double tint) {
        frame().draw(art, dx, dy, scale, flipX, tint);
    }

    /** 以中心点画当前帧（爆炸、子弹这类素材对齐更方便）。 */
    public void drawCentered(PixelArt art, int centerX, int centerY) {
        Sprite f = frame();
        f.draw(art, centerX - f.width() / 2, centerY - f.height() / 2);
    }

    @Override
    public String toString() {
        return "Anim[" + sheet.role() + " " + (index() + 1) + "/" + sheet.frameCount()
                + (loop ? " 循环" : (finished ? " 已结束" : " 播放中")) + "]";
    }
}
