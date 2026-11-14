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
    /** 浮动层：一组"同一高度"的面板和按钮先画到这张透明图上，再整块平移贴回底图。 */
    private PixelArt layer;
    private boolean layerActive;

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
        return layerActive ? layer : art;
    }

    /**
     * 开始画一层"浮动内容"。
     *
     * <p>这一层和棋盘那类贴底的平面不在同一个高度上，所以先画到透明层里，
     * 收尾时按 {@link UiDepth} 算出来的位移整块贴回去 —— 层里的面板、按钮、文字
     * 一起平移，彼此之间的间距不会变形（这点和逐控件偏移不同，间距不会越拉越开）。</p>
     *
     * <p>注意：层里的坐标仍然是整屏设计坐标，不用改任何绘制代码。</p>
     */
    public void beginLayer() {
        if (layer == null) {
            layer = new PixelArt(art.width(), art.height());
        } else {
            layer.clear();
        }
        layerActive = true;
    }

    /**
     * 结束当前浮动层，按 (dx, dy) 整块贴回底图。
     *
     * @param dx 像素整数位移，一般来自 {@link UiDepth#shiftX(int)}
     * @param dy 像素整数位移，一般来自 {@link UiDepth#shiftY(int)}
     */
    public void endLayer(int dx, int dy) {
        if (!layerActive) {
            return;
        }
        layerActive = false;
        art.blitArgb(layer.buffer(), layer.width(), layer.height(), dx, dy, false);
    }

    /**
     * 结束当前浮动层，按 {@code plane} 的透视姿态和平移量把整块贴回底图。
     *
     * <p>透视是三维的：层像一块悬在星幕里的板子，上沿往屏幕深处倒过去。层里所有东西
     * （面板、按钮、文字）一起被投影，互相之间的间距和相对位置不变。贴图用<b>逆矩阵
     * 反向采样</b>：每个目标像素都能在层里找到唯一来源，所以整片填满、不漏点；最近邻取样，
     * 不做插值，保持像素画硬边。只处理 {@code (x,y,w,h)} 这块区域。</p>
     *
     * @param plane 透视平面（俯仰/偏航 + 平移深度），绘制与热区共用同一个
     */
    public void endLayer(UiTilt.Plane plane, int x, int y, int w, int h) {
        if (!layerActive) {
            return;
        }
        layerActive = false;
        int[] src = layer.buffer();
        int sw = layer.width();
        int sh = layer.height();
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(art.width(), x + w);
        int y1 = Math.min(art.height(), y + h);
        int dx = plane.shiftX();
        int dy = plane.shiftY();
        if (!plane.tilted()) {
            // 没有透视：纯平移，直接整块贴回去（省掉每像素的除法）
            for (int sy = y0; sy < y1; sy++) {
                int py = sy - dy;
                if (py < 0 || py >= sh) {
                    continue;
                }
                for (int sx = x0; sx < x1; sx++) {
                    int px = sx - dx;
                    if (px < 0 || px >= sw) {
                        continue;
                    }
                    int argb = src[py * sw + px];
                    if ((argb >>> 24) == 0) {
                        continue;
                    }
                    art.blendArgb(sx, sy, argb);
                }
            }
            return;
        }
        // 屏幕 → 层内坐标，用同一个平面的逆矩阵（和热区反变换完全一致）
        double[] inv = plane.inverse();
        for (int sy = y0; sy < y1; sy++) {
            double rowC = inv[1] * (sy + 0.5) + inv[2];
            double rowF = inv[4] * (sy + 0.5) + inv[5];
            double rowW = inv[7] * (sy + 0.5) + inv[8];
            for (int sx = x0; sx < x1; sx++) {
                double cx = sx + 0.5;
                double wgt = inv[6] * cx + rowW;
                if (wgt == 0.0) {
                    continue;
                }
                double lx = (inv[0] * cx + rowC) / wgt;
                double ly = (inv[3] * cx + rowF) / wgt;
                int px = (int) Math.floor(lx);
                int py = (int) Math.floor(ly);
                if (px < 0 || py < 0 || px >= sw || py >= sh) {
                    continue;
                }
                int argb = src[py * sw + px];
                if ((argb >>> 24) == 0) {
                    continue;
                }
                art.blendArgb(sx, sy, argb);
            }
        }
    }

    /** 现在画的是不是浮动层（调试/断言用）。 */
    public boolean layerActive() {
        return layerActive;
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
        layerActive = false;
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
            art().blitArgb(image.argb(), image.width(), image.height(),
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

    /**
     * 按墨迹水平居中的文本：光栅位图左右各留有一两像素内边距，按估计字宽居中会整体偏右。
     *
     * <p>{@code baselineY} 口径与 {@link #text} 一致（不是墨迹中心）。</p>
     */
    public void textInkCentered(String content, double centerX, double baselineY,
                                double designSize, Color color) {
        texts.add(new TextRun(content, centerX, baselineY, designSize, color));
        CjkRasterizer.Rendered image = CjkRasterizer.render(content, designSize, color);
        if (image == null) {
            return;
        }
        int w = image.width();
        int h = image.height();
        int left = -1;
        int right = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((image.argb()[y * w + x] >>> 24) != 0) {
                    if (left < 0 || x < left) {
                        left = x;
                    }
                    if (x > right) {
                        right = x;
                    }
                }
            }
        }
        double inkCenter = left < 0 ? w / 2.0 : (left + right + 1) / 2.0;
        int top = (int) Math.round(baselineY) - (int) Math.round(designSize * 1.2);
        art().blitArgb(image.argb(), w, h, (int) Math.rint(centerX - inkCenter), top, false);
    }


    public List<TextRun> texts() {
        return List.copyOf(texts);
    }
}
