package com.hysx.salvosequence.graphics;

import com.hysx.salvosequence.core.PixelArt;

import java.util.List;

/**
 * 一个角色的全部帧（精灵表切好的结果）。
 *
 * <p>帧一律是等尺寸的若干张小图，索引 0 起。之所以把「表」和「帧」分开，
 * 是因为动画只需要换索引，不需要每帧去重新切图。</p>
 */
public final class SpriteSheet {

    /** 没有登记表信息时的默认文件名。 */
    public static final String TABLE_FILE = "sprites.txt";

    private final String role;
    private final List<Sprite> frames;
    private final int frameWidth;
    private final int frameHeight;
    /** 这一套是从哪个文件来的（占位图时为 null）。 */
    private final String source;
    /** 是否程序生成的占位图（真的美术资源还没放进来）。 */
    private final boolean placeholder;

    public SpriteSheet(String role, List<Sprite> frames, String source, boolean placeholder) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("帧不能为空: " + role);
        }
        this.role = role;
        this.frames = List.copyOf(frames);
        this.frameWidth = frames.get(0).width();
        this.frameHeight = frames.get(0).height();
        for (Sprite f : this.frames) {
            if (f.width() != frameWidth || f.height() != frameHeight) {
                throw new IllegalArgumentException(
                        "同一套帧尺寸必须一致: " + role + " 出现 " + f.width() + "x" + f.height()
                                + "，期望 " + frameWidth + "x" + frameHeight);
            }
        }
        this.source = source;
        this.placeholder = placeholder;
    }

    /** 单体图（只有一帧）的便捷构造。 */
    public static SpriteSheet single(String role, Sprite sprite) {
        return new SpriteSheet(role, List.of(sprite), null, true);
    }

    public String role() {
        return role;
    }

    public int frameCount() {
        return frames.size();
    }

    public int frameWidth() {
        return frameWidth;
    }

    public int frameHeight() {
        return frameHeight;
    }

    public String source() {
        return source;
    }

    public boolean isPlaceholder() {
        return placeholder;
    }

    /** 取一帧（索引自动取模，动画循环时不用自己做边界判断）。 */
    public Sprite frame(int index) {
        int i = Math.floorMod(index, frames.size());
        return frames.get(i);
    }

    public List<Sprite> frames() {
        return frames;
    }

    /** 循环一遍的总时长（秒）。 */
    public double loopDuration(double fps) {
        return fps <= 0 ? 0 : frames.size() / fps;
    }

    @Override
    public String toString() {
        return "SpriteSheet[" + role + " " + frames.size() + "x" + frameWidth + "x" + frameHeight
                + (placeholder ? " 占位" : " 来自 " + source) + "]";
    }

    // ------------------------------------------------------------- 绘制出口

    /** 画指定帧（1:1）。 */
    public void draw(PixelArt art, int frameIndex, int dx, int dy) {
        frame(frameIndex).draw(art, dx, dy);
    }

    /**
     * 画指定帧并水平翻转。
     *
     * <p>翻转在绘制阶段做（{@code blitArgbTile} 的 flipX），不预先镜像成新图，
     * 省一份内存也省一次拷贝。</p>
     */
    public void draw(PixelArt art, int frameIndex, int dx, int dy, boolean flipX, double scale) {
        frame(frameIndex).draw(art, dx, dy, scale, flipX, 1.0);
    }

    /** 以 (centerX, centerY) 为中心画某一帧（子弹/爆炸这类对称素材好用）。 */
    public void drawCentered(PixelArt art, int frameIndex, int centerX, int centerY) {
        frame(frameIndex).draw(art, centerX - frameWidth / 2, centerY - frameHeight / 2);
    }
}
