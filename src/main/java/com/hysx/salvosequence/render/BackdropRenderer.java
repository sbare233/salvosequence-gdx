package com.hysx.salvosequence.render;

import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.core.Renderer;
import com.hysx.salvosequence.core.Color;

import java.util.Random;

/**
 * 星野背景（最先绘制，压在整屏最底下）。
 *
 * <p>三层视差星点 + 抖动的尘埃带 + 右上角轨道弧 + 极暗的舰队剪影 + 四边暗角。
 * 全部直接画进共享的整屏缓冲，没有任何自己的节点。</p>
 *
 * <p>尘埃与星点必须"先清干净再画"：整屏缓冲每帧由视图统一清空过一次，所以这里
 * 只管往上画即可；但尘埃用抖动点，如果需要"擦除"就得靠帧首的整体清空。</p>
 */
public final class BackdropRenderer implements Renderer {

    private static final int W = 640;
    private static final int H = 360;

    private static final int FAR_COUNT = 190;
    private static final int MID_COUNT = 70;
    private static final int NEAR_COUNT = 22;

    /** 尘埃带轴心的横向滚动量（设计像素/秒）。 */
    private static final double DUST_SPEED = 1.6;

    private final Random rnd = new Random(20240917L);

    private final double[] farX = new double[FAR_COUNT];
    private final double[] farY = new double[FAR_COUNT];
    private final double[] farPhase = new double[FAR_COUNT];
    private final double[] midX = new double[MID_COUNT];
    private final double[] midY = new double[MID_COUNT];
    private final double[] midPhase = new double[MID_COUNT];
    private final double[] nearX = new double[NEAR_COUNT];
    private final double[] nearY = new double[NEAR_COUNT];
    private final double[] nearPhase = new double[NEAR_COUNT];

    private final Color farDim = Palette.STAR_FAR;
    private final Color farBright = PixelArt.shade(Palette.STAR_FAR, 1.45);
    private final Color midDim = PixelArt.shade(Palette.STAR_MID, 0.8);
    private final Color nearBright = Palette.STAR_NEAR;
    private final Color dustBlue = PixelArt.shade(Palette.BLUE_DIM, 0.85);
    private final Color dustCyan = PixelArt.shade(Palette.CYAN_DIM, 0.75);
    private final Color dustRed = PixelArt.shade(Palette.DANGER, 0.22);
    private final Color arcColor = PixelArt.shade(Palette.LINE_FAINT, 0.55);

    private double time;
    private double dustOffset;

    public BackdropRenderer() {
        for (int i = 0; i < FAR_COUNT; i++) {
            farX[i] = rnd.nextDouble() * W;
            farY[i] = rnd.nextDouble() * H;
            farPhase[i] = rnd.nextDouble() * Math.PI * 2;
        }
        for (int i = 0; i < MID_COUNT; i++) {
            midX[i] = rnd.nextDouble() * W;
            midY[i] = rnd.nextDouble() * H;
            midPhase[i] = rnd.nextDouble() * Math.PI * 2;
        }
        for (int i = 0; i < NEAR_COUNT; i++) {
            nearX[i] = rnd.nextDouble() * W;
            nearY[i] = rnd.nextDouble() * H;
            nearPhase[i] = rnd.nextDouble() * Math.PI * 2;
        }
    }

    @Override
    public void update(double dt) {
        time += dt;
        dustOffset += DUST_SPEED * dt;
    }

    /** 底色层（极暗太空 + 1/6 抖动颗粒）是常量，算一次整幅拷进缓冲。 */
    private int[] baseLayer;

    @Override
    public void draw(CanvasBuffer buffer) {
        PixelArt art = buffer.art();

        // 底色：极暗太空 + 1/6 抖动颗粒（取 2 会变成整齐棋盘格，一眼假）。
        // 【性能】这层每帧都一样，之前每帧都要扫 23 万像素，改成建一次表再整幅
        // arraycopy（实测量级 0.3ms -> 0.03ms）。
        if (baseLayer == null || baseLayer.length != W * H) {
            baseLayer = buildBaseLayer();
        }
        System.arraycopy(baseLayer, 0, art.buffer(), 0, baseLayer.length);

        drawDust(art);
        drawArcs(art);
        drawStars(art);
        drawFleet(art);
        drawVignette(art);
    }

    /** 离线攒一遍底色（临时 PixelArt 上没有上传器，不会触发纹理上传）。 */
    private static int[] buildBaseLayer() {
        PixelArt scratch = new PixelArt(W, H);
        scratch.fill(Palette.SPACE_DEEP);
        scratch.ditherRect(0, 0, W, H, Palette.SPACE, 6);
        return scratch.buffer().clone();
    }

    /** 两三条低频团块的斜向尘埃带（用有序抖动堆颗粒，而不是平滑渐变）。 */
    private void drawDust(PixelArt art) {
        drawDustBand(art, 0.35, 62, 0.16, dustBlue, 0.0);
        drawDustBand(art, 0.48, 28, 0.22, dustCyan, 1.7);
        drawDustBand(art, 0.55, 40, 0.13, dustRed, 3.1);
    }

    /** 预计算的三角函数表：x 方向的两组 sin/cos，避免每个像素都调 4 次超越函数。 */
    private static final double[] XS1 = new double[W];
    private static final double[] XC1 = new double[W];
    private static final double[] XS2 = new double[W];
    private static final double[] XC2 = new double[W];

    static {
        for (int x = 0; x < W; x++) {
            XS1[x] = Math.sin(x * 0.0121);
            XC1[x] = Math.cos(x * 0.0121);
            XS2[x] = Math.sin(x * 0.0391);
            XC2[x] = Math.cos(x * 0.0391);
        }
    }

    private void drawDustBand(PixelArt art, double slope, int width, double coverage,
                              Color color, double seed) {
        int shift = (int) Math.rint(dustOffset);
        int argb = PixelArt.toArgb(color);
        // 只扫带子覆盖到的列（宽度 62/28/40，过去固定扫满 640 列，多跑 5~20 倍）
        for (int y = 0; y < H; y++) {
            double axis = slope * y + 2.0 * Math.sin(y * 0.016 + seed) - shift + seed * 90;
            int x0 = Math.max(0, (int) Math.ceil(axis - width));
            int x1 = Math.min(W - 1, (int) Math.floor(axis + width));
            // 逐行的两组角度（x 分量查表，y 分量这里各算一次），用和角公式展开
            double ay = y * 0.0317 + seed;
            double by = -y * 0.0143 + seed * 1.7;
            double sinAy = Math.sin(ay);
            double cosAy = Math.cos(ay);
            double sinBy = Math.sin(by);
            double cosBy = Math.cos(by);
            for (int x = x0; x <= x1; x++) {
                double distance = Math.abs(x - axis);
                if (distance >= width) {
                    continue;
                }
                double nx = distance / width;
                double fall = 1.0 - nx * nx;
                // 低频噪声：让覆盖率起落，形成团块与空洞，避免出现摩尔纹条纹
                double clump = clamp(0.55
                        + 0.20 * (XS1[x] * cosAy + XC1[x] * sinAy)
                        + 0.13 * (XC2[x] * cosBy - XS2[x] * sinBy), 0.05, 1.05);
                // 4x4 有序抖动阈值
                int threshold = BAYER_4X4[((y & 3) << 2) | (x & 3)];
                if ((threshold + 0.5) / 16.0 < coverage * fall * fall * clump) {
                    art.pixelArgb(x, y, argb);
                }
            }
        }
    }

    /** 右上角的轨道弧：圆心挪到画布外，只露一段缓和的大弧。 */
    private void drawArcs(PixelArt art) {
        art.ellipse(760, -140, 320, 250, arcColor);
        art.ellipse(760, -140, 368, 292, PixelArt.shade(arcColor, 0.75));
    }

    /** 三层视差星点（远 -> 近），近景 2x2 带十字星芒。 */
    private void drawStars(PixelArt art) {
        for (int i = 0; i < FAR_COUNT; i++) {
            int x = (int) Math.rint(wrap(farX[i] - time * 2.0, W));
            int y = (int) Math.rint(farY[i]);
            boolean twinkle = Math.sin(time * 1.7 + farPhase[i]) > 0.4;
            art.pixel(x, y, twinkle ? farBright : farDim);
        }
        for (int i = 0; i < MID_COUNT; i++) {
            int x = (int) Math.rint(wrap(midX[i] - time * 4.5, W));
            int y = (int) Math.rint(midY[i]);
            art.pixel(x, y, Math.sin(time * 2.3 + midPhase[i]) > 0.0 ? Palette.STAR_MID : midDim);
        }
        for (int i = 0; i < NEAR_COUNT; i++) {
            int x = (int) Math.rint(wrap(nearX[i] - time * 9.0, W));
            int y = (int) Math.rint(nearY[i]);
            art.rect(x, y, 2, 2, nearBright);
            if (Math.sin(time * 1.3 + nearPhase[i]) > 0.6) {
                art.pixel(x - 1, y, PixelArt.shade(nearBright, 0.4));
                art.pixel(x + 2, y, PixelArt.shade(nearBright, 0.4));
                art.pixel(x, y - 1, PixelArt.shade(nearBright, 0.4));
                art.pixel(x, y + 2, PixelArt.shade(nearBright, 0.4));
            }
        }
    }

    /** 远景舰队剪影：几块极暗的楔形，读出"下面有东西"。 */
    private void drawFleet(PixelArt art) {
        double drift = Math.sin(time * 0.06) * 3.0;
        for (int i = 0; i < 4; i++) {
            double cx = 150 + i * 118 + drift;
            double cy = 332 + Math.sin(time * 0.05 + i) * 1.5;
            double[] xs = {cx - 26, cx - 6, cx + 30, cx + 8};
            double[] ys = {cy + 4, cy - 3, cy + 2, cy + 6};
            art.polygon(xs, ys, Palette.SPACE_DEEP);
            art.polygonOutline(xs, ys, Palette.LINE_FAINT);
        }
    }

    /** 四边暗角：越靠边越暗，用递减密度的抖动实现。 */
    private void drawVignette(PixelArt art) {
        art.ditherRect(0, 0, W, 20, Palette.SPACE_DEEP, 3);
        art.ditherRect(0, 0, W, 12, Palette.SPACE_DEEP, 2);
        art.ditherRect(0, H - 30, W, 30, Palette.SPACE_DEEP, 3);
        art.ditherRect(0, H - 18, W, 18, Palette.SPACE_DEEP, 2);
        art.ditherRect(0, 0, 28, H, Palette.SPACE_DEEP, 4);
        art.ditherRect(W - 28, 0, 28, H, Palette.SPACE_DEEP, 4);
    }

    private static final int[] BAYER_4X4 = {
            0, 8, 2, 10,
            12, 4, 14, 6,
            3, 11, 1, 9,
            15, 7, 13, 5,
    };

    private static double wrap(double value, double period) {
        double v = value % period;
        return v < 0 ? v + period : v;
    }

    private static double clamp(double value, double lo, double hi) {
        return value < lo ? lo : (value > hi ? hi : value);
    }
}
