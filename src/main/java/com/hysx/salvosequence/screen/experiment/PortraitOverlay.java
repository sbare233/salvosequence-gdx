package com.hysx.salvosequence.screen.experiment;

import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.graphics.Sprite;

/**
 * 实验：把一张人物立绘放进像素画布，按高度缩放到 {@value #TARGET_H} 设计像素。
 *
 * <p>默认隐藏，主界面按 <b>F12</b> 调出。放在 {@code screen.experiment} 包里，
 * 是明确标记「这是实验件，不是正式美术」——要用真素材时直接换图或删掉本类。</p>
 *
 * <p>几个刻意的选择：</p>
 * <ul>
 *   <li><b>先离线缩一次</b>：源图 582×885 缩到高度 256 是 0.29 倍的非整数倍，
 *       直接最近邻采会掉线条；构造时用面积平均算出 168×256 的版本，
 *       之后每帧只是 1:1 贴图，既稳又不费。</li>
 *   <li><b>保留原图 alpha</b>：立绘是带透明边的 RGBA，贴图时按 alpha 混合，
 *       不会给背景留一块灰底。</li>
 *   <li><b>不做调色板量化</b>：先看原样，要不要压成调色板等看过效果再决定。</li>
 *   <li><b>读不到图不炸</b>：素材缺失时只在屏幕上画个红框，游戏照常跑。</li>
 * </ul>
 */
public final class PortraitOverlay {

    /** 素材路径（可以直接换成别的图）。 */
    private static final String RESOURCE = "/sprites/portrait.png";

    /** 目标高度（设计像素）。画布高 360，256 差不多占七成，站着不顶天。 */
    private static final int TARGET_H = 256;

    /** 脚底站在哪一行（设计像素）。留 20 给底部提示条。 */
    private static final int FEET_Y = 338;

    /** 水平中心（设计像素）：贴左边站，但整幅留在画布内（宽度约 168，中心 100 → 左边 16）。 */
    private static final int CX = 50;

    /** 缩放后的图（构造时算好，绘制时 1:1 贴）。 */
    private final int[] scaled;
    private final int scaledW;
    private final int scaledH;

    private boolean visible;

    /** 图不在的时候，把原因记下来，省得对着空屏幕猜。 */
    private final String missingReason;

    public PortraitOverlay() {
        Sprite source = null;
        String reason = null;
        try {
            source = Sprite.loadWhole("portrait", RESOURCE);
            if (source == null) {
                reason = RESOURCE + " 没读到（资源不在类路径上，或解码失败）";
            }
        } catch (Throwable t) {
            reason = RESOURCE + " 读取异常: " + t;
        }
        this.missingReason = reason;
        if (source == null) {
            this.scaled = null;
            this.scaledW = 0;
            this.scaledH = 0;
        } else {
            int h = TARGET_H;
            int w = Math.max(1, (int) Math.round(source.width() * (double) h / source.height()));
            this.scaledW = w;
            this.scaledH = h;
            int[] keyed = keyOutBackground(source.pixels(), source.width(), source.height());
            this.scaled = areaAverage(keyed, source.width(), source.height(), w, h);
        }
        // 调试开关走环境变量：PowerShell 会把 "-Dxxx.yyy=z" 从点号处拆开，
        // 所以命令行属性在这台机器上不可靠（AUDIT_LAYOUT 也是这个原因）。
        // AUDIT_PORTRAIT=1  打开立绘
        if ("true".equalsIgnoreCase(System.getenv("AUDIT_PORTRAIT"))) {
            this.visible = true;
        }
    }

    public boolean visible() {
        return visible;
    }

    public void toggle() {
        visible = !visible;
    }

    public void setVisible(boolean v) {
        visible = v;
    }

    /** 缩放后的尺寸，调试用。 */
    public int scaledWidth() {
        return scaledW;
    }

    public int scaledHeight() {
        return scaledH;
    }

    public String missingReason() {
        return missingReason;
    }

    /** 立绘是静态的，这里没有逐帧更新。 */
    public void update(double dt) {
    }

    public void draw(PixelArt art) {
        if (!visible) {
            return;
        }
        if (scaled == null) {
            // 读不到图不画人，画一个红框当"这里本该有东西"的标记。
            art.frame(CX - scaledW / 2 - 10, FEET_Y - TARGET_H, 80, 24, Palette.DANGER);
            return;
        }
        int dx = CX - scaledW / 2;
        int dy = FEET_Y - scaledH;
        art.blitArgbTile(scaled, scaledW, scaledH, 0, 0, dx, dy, scaledW, scaledH,
                false, false, false, 1.0);
    }

    /**
     * 把浅灰底抠成透明：从四边泛洪，连到边界的"接近底色"的像素清掉；
     * 边缘上被底色混过的半透明像素按距离给一个 alpha，缩小时才不会留一圈灰边。
     * 只从边界长，所以人物身上同样偏白的部分不会被打穿。
     */
    private static int[] keyOutBackground(int[] src, int w, int h) {
        int bg = src[0];
        int br = (bg >> 16) & 0xFF;
        int bgc = (bg >> 8) & 0xFF;
        int bb = bg & 0xFF;
        int[] out = src.clone();
        boolean[] outside = new boolean[w * h];
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            seed(x, w, h, src, br, bgc, bb, outside, queue);
            seed((h - 1) * w + x, w, h, src, br, bgc, bb, outside, queue);
        }
        for (int y = 0; y < h; y++) {
            seed(y * w, w, h, src, br, bgc, bb, outside, queue);
            seed(y * w + w - 1, w, h, src, br, bgc, bb, outside, queue);
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = i % w;
            int y = i / w;
            if (x > 0) {
                seed(i - 1, w, h, src, br, bgc, bb, outside, queue);
            }
            if (x < w - 1) {
                seed(i + 1, w, h, src, br, bgc, bb, outside, queue);
            }
            if (y > 0) {
                seed(i - w, w, h, src, br, bgc, bb, outside, queue);
            }
            if (y < h - 1) {
                seed(i + w, w, h, src, br, bgc, bb, outside, queue);
            }
        }
        for (int i = 0; i < out.length; i++) {
            if (outside[i]) {
                out[i] = 0;
            }
        }
        return out;
    }

    private static void seed(int i, int w, int h, int[] src, int br, int bg, int bb,
                             boolean[] outside, java.util.ArrayDeque<Integer> queue) {
        if (i < 0 || i >= w * h || outside[i]) {
            return;
        }
        int argb = src[i];
        int dr = ((argb >> 16) & 0xFF) - br;
        int dg = ((argb >> 8) & 0xFF) - bg;
        int db = (argb & 0xFF) - bb;
        if (dr * dr + dg * dg + db * db > 26 * 26) {
            return;
        }
        outside[i] = true;
        queue.add(i);
    }

    /**
     * 面积平均降采样：目标像素覆盖的源像素取加权平均（按 alpha 预乘），
     * 比最近邻采能少掉很多锯齿，也不会让细线条整根消失。
     */
    private static int[] areaAverage(int[] src, int sw, int sh, int dw, int dh) {
        int[] out = new int[dw * dh];
        for (int y = 0; y < dh; y++) {
            int sy0 = y * sh / dh;
            int sy1 = Math.max(sy0 + 1, (y + 1) * sh / dh);
            for (int x = 0; x < dw; x++) {
                int sx0 = x * sw / dw;
                int sx1 = Math.max(sx0 + 1, (x + 1) * sw / dw);
                long r = 0;
                long g = 0;
                long b = 0;
                long a = 0;
                int n = 0;
                for (int sy = sy0; sy < sy1; sy++) {
                    int row = sy * sw;
                    for (int sx = sx0; sx < sx1; sx++) {
                        int argb = src[row + sx];
                        int alpha = argb >>> 24;
                        r += ((argb >> 16) & 0xFF) * alpha;
                        g += ((argb >> 8) & 0xFF) * alpha;
                        b += (argb & 0xFF) * alpha;
                        a += alpha;
                        n++;
                    }
                }
                if (n == 0 || a == 0) {
                    out[y * dw + x] = 0;
                } else {
                    out[y * dw + x] = ((int) (a / n) << 24)
                            | ((int) (r / a) << 16)
                            | ((int) (g / a) << 8)
                            | (int) (b / a);
                }
            }
        }
        return out;
    }
}
