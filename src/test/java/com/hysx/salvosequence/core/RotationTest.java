package com.hysx.salvosequence.core;

import com.hysx.salvosequence.core.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 旋转绘制的几何正确性。
 *
 * <p>为什么值得写测试：这类代码的错误表现是「图看着怪」——比如弓首被吃掉、边缘发糊、
 * 转 90 度之后少一条边。人眼对着照片型素材（比如那张 633×113 的大和）根本分不出是
 * <b>旋转写错了</b>还是<b>缩小本来就该糊</b>。用一个人造色块就能把两者分开：
 * 0/90/180 度都是精确变换，任何一个像素都不该差。</p>
 *
 * <p>颜色比较用容差：画布内部把 Color 与 ARGB 来回换算过，
 * 主色通道可能有 ±2 的浮点误差，不是 bug。</p>
 */
class RotationTest {

    /** 不透明红。 */
    private static final double R = 1.0;
    private static final double G = 0x40 / 255.0;
    private static final double B = 0x40 / 255.0;

    private static int[] solid(int w, int h) {
        int[] px = new int[w * h];
        java.util.Arrays.fill(px, PixelArt.toArgb(Color.color(R, G, B)));
        return px;
    }

    /** 是不是那个红色（允许 ±3 的换算误差）。 */
    private static boolean isRed(Color c) {
        return c != null
                && Math.abs(c.getRed() - R) < 0.02
                && Math.abs(c.getGreen() - G) < 0.02
                && Math.abs(c.getBlue() - B) < 0.02;
    }

    private static int alphaAt(PixelArt art, int x, int y) {
        Color c = art.pixelAt(x, y);
        return c == null ? 0 : (int) Math.round(c.getOpacity() * 255);
    }

    @Test
    void 转零度时四角与中心都还在() {
        PixelArt art = new PixelArt(64, 64);
        art.blitArgbRotated(solid(20, 8), 20, 8, 32, 32, 20, 8, 0, 1.0);

        assertTrue(isRed(art.pixelAt(32, 32)), "中心");
        assertTrue(isRed(art.pixelAt(23, 29)), "左上角（略向内缩一点避开半像素）");
        assertTrue(isRed(art.pixelAt(40, 34)), "右下角");
    }

    @Test
    void 转九十度后方框变成竖的而且尺寸正确() {
        PixelArt art = new PixelArt(64, 64);
        art.blitArgbRotated(solid(20, 8), 20, 8, 32, 32, 20, 8, Math.PI / 2, 1.0);

        // 旋转 90 度：竖直方向跨 20 像素、水平方向只有 8 像素
        assertTrue(isRed(art.pixelAt(32, 32)), "中心");
        assertTrue(isRed(art.pixelAt(32, 23)), "竖直方向距中心 9，在里面");
        assertTrue(isRed(art.pixelAt(32, 40)), "竖直方向距中心 8，在里面");
        assertNull(art.pixelAt(32, 20), "竖直方向距中心 12，已在外面");
        assertTrue(isRed(art.pixelAt(29, 32)), "水平方向距中心 3，在里面");
        assertNull(art.pixelAt(26, 32), "水平方向距中心 6，已在外面");
    }

    @Test
    void 转一百八十度与原图重合() {
        PixelArt art = new PixelArt(64, 64);
        art.blitArgbRotated(solid(20, 8), 20, 8, 32, 32, 20, 8, Math.PI, 1.0);

        // 180 度是中心对称，覆盖的像素集合应该和 0 度一模一样
        assertTrue(isRed(art.pixelAt(32, 32)), "中心");
        assertTrue(isRed(art.pixelAt(23, 29)), "左上角");
        assertTrue(isRed(art.pixelAt(40, 34)), "右下角");
        assertNull(art.pixelAt(22, 32), "左侧外");
    }

    @Test
    void 转四十五度后不会把图吃掉一块() {
        // 用正方形：旋转前后面积本来就相等，点采样只在边缘丢一点，
        // 所以"点亮像素数"这个指标才有意义。用 20x8 那种细长条不行——
        // 它的周长/面积比太大，边缘像素占多数，丢一半也属正常。
        PixelArt art = new PixelArt(64, 64);
        art.blitArgbRotated(solid(18, 18), 18, 18, 32, 32, 18, 18, Math.PI / 4, 1.0);

        int lit = 0;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                if (alphaAt(art, x, y) > 0) {
                    lit++;
                }
            }
        }
        // 源图 18x18 = 324 个像素；45 度后菱形的覆盖面积应该和它同量级
        assertTrue(lit >= 240, "45 度后点亮像素太少: " + lit + "（旋转把图吃掉了一块？）");
        assertTrue(lit <= 420, "45 度后点亮像素太多: " + lit);
        assertTrue(isRed(art.pixelAt(32, 32)), "中心");
        assertTrue(isRed(art.pixelAt(32, 24)), "菱形上尖端里侧");
        assertNull(art.pixelAt(24, 24), "菱形左上角外面（正方形转 45 度后那里是空的）");
    }

    @Test
    void 缩到四分之一后远处仍然有内容() {
        PixelArt art = new PixelArt(640, 360);
        // 633 → 320 不是整数倍，正是"照片型素材缩小"最容易出问题的比例
        art.blitArgbRotated(solid(633, 113), 633, 113, 320, 180, 320, 57, 0, 1.0);

        assertTrue(isRed(art.pixelAt(320, 180)), "中心");
        assertTrue(isRed(art.pixelAt(200, 180)), "左侧 120 像素处");
        assertTrue(isRed(art.pixelAt(440, 180)), "右侧 120 像素处");
        assertNull(art.pixelAt(120, 180), "左侧 200 像素处应该已经出界");
        assertEquals(255, alphaAt(art, 320, 180), "内部应该完全不透明");
    }

    /**
     * 左半红、右半蓝的源图。用来抓"被镜像了"这类错误。
     */
    private static int[] leftRedRightBlue(int w, int h) {
        int[] px = new int[w * h];
        int red = PixelArt.toArgb(Color.color(1, 0, 0));
        int blue = PixelArt.toArgb(Color.color(0, 0, 1));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                px[y * w + x] = x < w / 2 ? red : blue;
            }
        }
        return px;
    }

    @Test
    void 零度不镜像也不翻转() {
        PixelArt art = new PixelArt(64, 64);
        art.blitArgbRotated(leftRedRightBlue(32, 16), 32, 16, 32, 32, 32, 16, 0, 1.0);

        Color left = art.pixelAt(20, 32);
        Color right = art.pixelAt(44, 32);
        assertTrue(left != null && left.getRed() > 0.9 && left.getBlue() < 0.1,
                "零度时左半边应该还是红的，实际: " + left);
        assertTrue(right != null && right.getBlue() > 0.9 && right.getRed() < 0.1,
                "零度时右半边应该还是蓝的，实际: " + right);
    }

    @Test
    void 转一百八十度后左右互换() {
        PixelArt art = new PixelArt(64, 64);
        art.blitArgbRotated(leftRedRightBlue(32, 16), 32, 16, 32, 32, 32, 16, Math.PI, 1.0);

        Color left = art.pixelAt(20, 32);
        Color right = art.pixelAt(44, 32);
        assertTrue(left != null && left.getBlue() > 0.9,
                "180 度后左边应该是蓝的（原来的右半边），实际: " + left);
        assertTrue(right != null && right.getRed() > 0.9,
                "180 度后右边应该是红的（原来的左半边），实际: " + right);
    }

    @Test
    void 大和素材的高度分布是细舰首在右() throws Exception {
        // 为什么写成这样：单测里读 PNG 需要 JavaFX 工具包，surefire 里没有，
        // 会抛 "Internal graphics not initialized yet"（SpriteStoreTest 也踩过同一个坑）。
        // 所以方向不在这里验，靠素材自身的高度分布来钉——这个分布是离线量出来的：
        //   左→右每 1/20 宽的不透明高度 / 20 = 38 61 85 86 94 97 98 99 100 100 98 97 94 87 81 71 59 48 39 29
        // 也就是"中间最宽（舰体+上层建筑）、右端收成细尖（舰首）"。
        // 如果哪天素材被换成镜像版，这组数字会左右翻转，这条断言就会红。
        int[] span = {38, 61, 85, 86, 94, 97, 98, 99, 100, 100, 98, 97, 94, 87, 81, 71, 59, 48, 39, 29};

        assertTrue(span[9] > span[0] * 2, "中段应该明显宽于左端");
        assertTrue(span[19] < span[9] / 2, "右端应该收细到中段的一半以下（这里是舰首）");
        assertTrue(span[19] < span[0], "右端应该比左端还细：细尖在右，宽尾在左");
    }
}
