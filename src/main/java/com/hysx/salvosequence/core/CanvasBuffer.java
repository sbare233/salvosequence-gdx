package com.hysx.salvosequence.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 整屏唯一的像素缓冲。
 *
 * <p>改造目标：<b>不再有"图层节点"</b>。所有内容都画进这一张 640x360 的设计像素缓冲，
 * 谁先画谁在下、后画的覆盖先画的 —— 层级关系由"绘制顺序"表达，而不是由 Pane 树表达。
 * 画完一次性上屏。</p>
 *
 * <p>缓冲本身就是普通的 {@link PixelArt}，所以原有的绘制原语（pixel/line/polygon/
 * blit/ditherRect…）全部照用，各层的绘制代码几乎不用改。</p>
 */
public final class CanvasBuffer {

    private final PixelArt art;
    /** 本帧画过的中文文本（同样已就地贴进像素；留档供测试断言与诊断）。 */
    private final List<TextRun> texts = new ArrayList<>();

    /**
     * 一段中文文本。
     *
     * @param content    文字
     * @param x          左边界（设计像素）
     * @param baselineY  基线位置（设计像素）
     * @param designSize 设计字号
     * @param color      颜色
     */
    public record TextRun(String content, double x, double baselineY, double designSize, Color color) {
    }

    public CanvasBuffer(int designWidth, int designHeight) {
        this.art = new PixelArt(designWidth, designHeight);
    }

    /** 底层绘制原语。 */
    public PixelArt art() {
        return art;
    }

    public int width() {
        return art.width();
    }

    public int height() {
        return art.height();
    }

    /** 开始新的一帧：清空像素与文本列表。 */
    public void beginFrame() {
        // PixelArt 内部是 int[] 缓冲，像素要显式提交给上屏层；
        // 上一帧的绘制到这一刻一定已经结束，所以顺手把上一帧推上去，
        // 避免有谁直接读纹理之外的东西时看到未提交的旧画面。
        art.flush();
        art.clear();
        texts.clear();
    }

    /**
     * 画一段中文文本：就地渲染成位图并按当前绘制顺序贴进像素缓冲。
     *
     * <p>中文和其它像素内容走同一条画家算法——面板后画就盖住它，特效罩画在最后
     * 就会作用到它。同时留一条 TextRun 记录（测试断言/诊断用）。字体光栅器不可用的
     * 环境（无头单测）只记录不贴图。</p>
     */
    public void text(String content, double x, double baselineY, double designSize, Color color) {
        texts.add(new TextRun(content, x, baselineY, designSize, color));
        CjkRasterizer.Rendered image = CjkRasterizer.render(content, designSize, color);
        if (image != null) {
            int top = (int) Math.round(baselineY) - (int) Math.round(designSize * 1.2);
            art.blitArgb(image.argb(), image.width(), image.height(),
                    (int) Math.rint(x), top, false);
        }
    }

    /** 居中的中文文本。 */
    public void textCentered(String content, double centerX, double capCenterY,
                             double designSize, Color color) {
        // 设计字号下 CJK 的墨迹宽约等于 字号 x 字数，够用即可
        double width = designSize * content.length();
        text(content, centerX - width / 2.0,
                capCenterY + designSize * 0.36, designSize, color);
    }

    /** 本帧收集到的文本（渲染完像素后由视图统一交给覆盖层）。 */
    public List<TextRun> texts() {
        return List.copyOf(texts);
    }
}
