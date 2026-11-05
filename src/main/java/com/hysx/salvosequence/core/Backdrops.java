package com.hysx.salvosequence.core;

import java.io.File;
import java.util.Arrays;

/**
 * 战斗/太空港背景主题库：10 主题 × 4 变体，纯程序化像素画。
 *
 * <p>技法（参考开源像素星球生成器的通用做法：分层值噪声 + 色带 + 抖动）：</p>
 * <ul>
 *   <li><b>网点渐变</b>：渐变区域不做插值混色，相邻色档之间按拜耳 8×8 阈值矩阵
 *       决定每个像素落在哪一档——颜色的过渡是点的密度变化，不是平滑渐变；</li>
 *   <li><b>值噪声云</b>：整数 hash 平滑噪声叠 3 层（fbm），星云/尘埃/极光全靠它塑形；</li>
 *   <li><b>变体</b>：同一主题用不同种子 + 水平镜像 + 星点密度 + 部分主题换色带，
 *       4 个变体是"同景不同拍"；</li>
 *   <li><b>动态</b>：静态层烧进缓存，{@link #animate} 每帧叠几十个确定性粒子
 *       （轨道/闪烁/光束/坠落），无状态、可复现。</li>
 * </ul>
 */
public final class Backdrops {

    public static final int THEMES = 10;
    public static final int VARIANTS = 4;
    public static final int W = 640;
    public static final int H = 360;

    /** 拜耳 8×8 阈值矩阵（0..63）。包内共享（PixelArt 的动态拜耳 blit 复用同一张）。 */
    static final int[] BAYER = {
            0, 32, 8, 40, 2, 34, 10, 42,
            48, 16, 56, 24, 50, 18, 58, 26,
            12, 44, 4, 36, 14, 46, 6, 38,
            60, 28, 52, 20, 62, 30, 54, 22,
            3, 35, 11, 43, 1, 33, 9, 41,
            51, 19, 59, 27, 49, 17, 57, 25,
            15, 47, 7, 39, 13, 45, 5, 37,
            63, 31, 55, 23, 61, 29, 53, 21,
    };

    /** 背景工坊导出的自定义背景（backdrops/custom/*.png），懒加载缓存。 */
    private static int[][] customs;
    private static String[] customNames;
    private static boolean customsLoaded;

    private Backdrops() {
    }

    /** 自定义背景数量（工坊导出的，重启或再次进入时刷新）。 */
    public static int customCount() {
        loadCustoms();
        return customs == null ? 0 : customs.length;
    }

    public static String customName(int index) {
        loadCustoms();
        return customNames[index];
    }

    /** 自定义背景像素（只读，勿改）。 */
    public static int[] customLayer(int index) {
        loadCustoms();
        return customs[index];
    }

    private static synchronized void loadCustoms() {
        if (customsLoaded) {
            return;
        }
        customsLoaded = true;
        try {
            File dir = WorkDir.of("backdrops/custom");
            File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
            if (files == null) {
                return;
            }
            Arrays.sort(files);
            java.util.List<int[]> layers = new java.util.ArrayList<>();
            java.util.List<String> names = new java.util.ArrayList<>();
            for (File f : files) {
                try (java.io.InputStream in = new java.io.FileInputStream(f)) {
                    PngLoader.Bitmap img = PngLoader.load(in);
                    if (img == null || img.width() <= 0) {
                        continue;
                    }
                    int[] argb = img.argb();
                    if (img.width() != W || img.height() != H) {
                        // 原版用 Image 的请求尺寸参数缩放；这里保持同样口径：最近邻到 W x H
                        argb = resample(argb, img.width(), img.height(), W, H);
                    }
                    layers.add(argb);
                    names.add(f.getName());
                } catch (RuntimeException | java.io.IOException e) {
                    System.out.println("custom backdrop load failed: " + f.getName() + " " + e);
                }
            }
            customs = layers.toArray(new int[0][]);
            customNames = names.toArray(new String[0]);
        } catch (Throwable t) {
            System.out.println("custom backdrops unavailable: " + t);
        }
    }

    /** 最近邻缩放（自定义背景尺寸不齐时归一到 W x H，保持像素硬边）。 */
    private static int[] resample(int[] src, int sw, int sh, int dw, int dh) {
        int[] out = new int[dw * dh];
        for (int y = 0; y < dh; y++) {
            int sy = Math.min(sh - 1, (int) ((long) y * sh / dh));
            for (int x = 0; x < dw; x++) {
                int sx = Math.min(sw - 1, (int) ((long) x * sw / dw));
                out[y * dw + x] = src[sy * sw + sx];
            }
        }
        return out;
    }

    public static String nameZh(int theme) {
        if (theme >= THEMES) {
            int i = theme - THEMES;
            return "自定义 " + (i + 1) + (customNames != null && i < customNames.length
                    ? " " + customNames[i] : "");
        }
        return switch (theme) {
            case 0 -> "深空星野";
            case 1 -> "发射星云";
            case 2 -> "黑洞";
            case 3 -> "近地表轨道";
            case 4 -> "侧缘星系";
            case 5 -> "超新星遗迹";
            case 6 -> "冰晶星云";
            case 7 -> "恒星摇篮";
            case 8 -> "脉冲星";
            default -> "尘埃带";
        };
    }

    // ================================================================ 静态层

    /** 渲染一张静态背景（不透明，覆盖全屏）。同一组参数永远得到同一张图。 */
    public static int[] staticLayer(int theme, int variant, long seed) {
        if (theme >= THEMES) {
            // 工坊导出的自定义背景：index = theme - THEMES；越界回落到深空星野
            loadCustoms();
            int i = theme - THEMES;
            if (customs != null && i >= 0 && i < customs.length) {
                return customs[i].clone();
            }
            return staticLayer(0, variant, seed);
        }
        int[] buf = new int[W * H];
        long s = seed * 31 + variant * 7919L;
        boolean flip = (variant & 1) != 0;
        java.util.Random r = new java.util.Random(s);
        switch (Math.floorMod(theme, THEMES)) {
            case 0 -> deepField(buf, r);
            case 1 -> emissionNebula(buf, r, (variant & 2) != 0);
            case 2 -> blackHole(buf, r);
            case 3 -> lowOrbit(buf, r);
            case 4 -> edgeOnGalaxy(buf, r);
            case 5 -> supernova(buf, r);
            case 6 -> iceNebula(buf, r);
            case 7 -> nursery(buf, r);
            case 8 -> pulsar(buf, r);
            default -> dustLane(buf, r);
        }
        if (flip) {
            mirrorX(buf);
        }
        return buf;
    }

    // ================================================================ 动态层

    /** 每帧在背景上叠主题动态粒子（确定性：同一 time 永远同一画面）。 */
    public static void animate(PixelArt art, int theme, int variant, long seed, double time) {
        if (theme >= THEMES) {
            return;   // 自定义背景是照片像素化，没有主题粒子（游戏自身的闪烁星仍会叠加）
        }
        long s = seed * 31 + variant * 7919L;
        java.util.Random r = new java.util.Random(s);
        switch (Math.floorMod(theme, THEMES)) {
            case 0 -> animateDeepField(art, r, time);
            case 1 -> animateSparks(art, r, time, 0xffa8485e, 14, true);
            case 2 -> animateBlackHole(art, r, time);
            case 3 -> animateLowOrbit(art, r, time);
            case 4 -> animateSparks(art, r, time, 0x9fc4ff, 10, false);
            case 5 -> animateSupernova(art, r, time);
            case 6 -> animateIce(art, r, time);
            case 7 -> animateNursery(art, r, time);
            case 8 -> animatePulsar(art, r, time);
            default -> animateDust(art, r, time);
        }
    }

    // ================================================================ 主题 0：深空星野

    private static void deepField(int[] buf, java.util.Random r) {
        fill(buf, argb(6, 7, 15));
        ditherEllipse(buf, 200, 120, 210, 120, 0, 1, ramp(argb(6, 7, 15), argb(24, 52, 60), argb(56, 44, 92)));
        ditherEllipse(buf, 470, 250, 190, 110, 0, 1, ramp(argb(6, 7, 15), argb(38, 32, 74), argb(28, 56, 62)));
        scatterStars(buf, r, 230, 3);
        // 遥远的侧视小星系群
        for (int i = 0; i < 8; i++) {
            int cx = 40 + r.nextInt(560);
            int cy = 30 + r.nextInt(300);
            int rx = 6 + r.nextInt(7);
            ditherEllipse(buf, cx, cy, rx, Math.max(2, rx / 3), 0, 1,
                    ramp(argb(6, 7, 15), argb(52, 66, 88), argb(120, 134, 152)));
            buf[clampY(cy) * W + clampX(cx)] = argb(220, 230, 245);
        }
    }

    private static void animateDeepField(PixelArt art, java.util.Random r, double time) {
        // 一颗流星：每 9 秒划过一次
        double cycle = time % 9;
        if (cycle < 0.9) {
            double p = cycle / 0.9;
            int x = (int) (80 + p * 480);
            int y = (int) (50 + p * 130);
            for (int k = 0; k < 9; k++) {
                art.pixel(x - k * 3, y - k, k < 3 ? Palette.STAR_NEAR : Palette.STAR_FAR);
            }
        }
    }

    // ================================================================ 主题 1：发射星云

    private static void emissionNebula(int[] buf, java.util.Random r, boolean alt) {
        fill(buf, argb(10, 5, 16));
        int[] stops = alt
                ? ramp(argb(10, 5, 16), argb(9, 42, 44), argb(22, 96, 92), argb(120, 200, 170))
                : ramp(argb(10, 5, 16), argb(58, 13, 30), argb(140, 31, 58), argb(224, 86, 110), argb(255, 217, 160));
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double v = fbm(x * 0.010, y * 0.014, 77, 3);
                double wisp = fbm(x * 0.03 + 40, y * 0.03, 91, 2);
                double t = Math.max(0, (v - 0.38) / 0.5) * (0.7 + 0.6 * wisp);
                if (t > 0.05) {
                    buf[y * W + x] = rampPixel(t, x, y, stops);
                }
            }
        }
        scatterStars(buf, r, 60, 2);
        // 星云核心的亮星
        for (int i = 0; i < 7; i++) {
            glowDot(buf, 80 + r.nextInt(480), 60 + r.nextInt(240), 3 + r.nextInt(3), argb(255, 240, 220));
        }
    }

    // ================================================================ 主题 2：黑洞

    private static void blackHole(int[] buf, java.util.Random r) {
        fill(buf, argb(5, 6, 13));
        scatterStars(buf, r, 110, 2);
        int cx = 320;
        int cy = 185;
        // 吸积盘（前半在洞下方，后半被"透镜"拉到上方——简化为上下两瓣椭圆环）
        annulus(buf, cx, cy + 6, 152, 43, 47, 146,
                ramp(argb(255, 246, 224), argb(255, 177, 78), argb(226, 88, 42), argb(122, 31, 20)));
        // 光子环
        ditherEllipseRing(buf, cx, cy, 40, 11, 0, 5, ramp(argb(255, 214, 150), argb(255, 246, 224)));
        // 视界
        fillEllipse(buf, cx, cy, 36, 9, argb(2, 2, 4));
        // 透镜星弧：洞上缘一道被弯折的亮星
        for (int i = 0; i < 26; i++) {
            double a = Math.PI + i / 25.0 * Math.PI;
            int x = (int) (cx + Math.cos(a) * (48 + r.nextInt(6)));
            int y = (int) (cy - Math.abs(Math.sin(a)) * (16 + r.nextInt(5)));
            buf[Math.max(0, Math.min(H - 1, y)) * W + clampX(x)] = argb(230, 236, 255);
        }
    }

    private static void animateBlackHole(PixelArt art, java.util.Random r, double time) {
        // 吸积盘差速旋转粒子：内快外慢
        for (int i = 0; i < 46; i++) {
            double base = r.nextDouble();
            double radius = 50 + base * 96;
            double speed = 1.6 / Math.sqrt(radius / 50);
            double a = r.nextDouble() * Math.PI * 2 + time * speed;
            double squash = 0.30;
            int x = (int) (320 + Math.cos(a) * radius);
            int y = (int) (191 + Math.sin(a) * radius * squash);
            int c = base < 0.3 ? 0xFFFFF2DC : 0xFFB16038;
            art.pixel(clampX(x), clampY(y), color(c));
        }
        // 光子环闪烁
        if (Math.sin(time * 7) > 0.2) {
            art.pixel(320 + (int) (Math.sin(time * 3) * 30), 185 - 13, Palette.STAR_NEAR);
        }
    }

    // ================================================================ 主题 3：近地表轨道

    private static void lowOrbit(int[] buf, java.util.Random r) {
        fill(buf, argb(4, 6, 14));
        scatterStars(buf, r, 130, 2);
        // 行星：大圆心在屏幕下方外，只露上缘
        int cx = 320;
        int cy = 560;
        int radius = 430;
        fillEllipse(buf, cx, cy, radius, radius, argb(9, 17, 30));
        // 大气辉光：肢线上缘一条网点过渡
        ditherEllipseRing(buf, cx, cy, radius + 2, radius + 2, 0, 26,
                ramp(argb(4, 6, 14), argb(26, 62, 92), argb(120, 190, 220), argb(210, 240, 255)));
        // 表面云层（暗面，fbm 起伏）
        for (int y = 150; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double d = Math.hypot(x - cx, y - cy);
                if (d > radius - 3 || d < radius - 90) {
                    continue;
                }
                double v = fbm(x * 0.02, y * 0.02, 55, 3);
                if (v > 0.52) {
                    buf[y * W + x] = rampPixel((v - 0.52) / 0.4, x, y,
                            ramp(argb(9, 17, 30), argb(22, 40, 58), argb(44, 70, 92)));
                }
            }
        }
        // 夜面城市灯：贴肢线内侧一带，暖色点簇
        for (int i = 0; i < 90; i++) {
            double a = Math.PI * (0.18 + r.nextDouble() * 0.64);
            int d = radius - 6 - r.nextInt(26);
            int x = clampX((int) (cx + Math.cos(a + Math.PI) * d));
            int y = (int) (cy - Math.sin(a) * d);
            if (y > 140 && y < H) {
                buf[y * W + clampX(x)] = argb(255, 210, 122);
                if (r.nextInt(3) == 0) {
                    buf[Math.min(H - 1, y + 1) * W + clampX(x)] = argb(190, 150, 90);
                }
            }
        }
    }

    private static void animateLowOrbit(PixelArt art, java.util.Random r, double time) {
        // 城市灯闪烁 + 一颗掠过的卫星
        java.util.Random lights = new java.util.Random(99);
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * (0.18 + lights.nextDouble() * 0.64);
            int d = 430 - 6 - lights.nextInt(26);
            int x = clampX((int) (320 - Math.cos(a) * d));
            int y = (int) (560 - Math.sin(a) * d);
            if (Math.sin(time * (1.3 + lights.nextDouble()) + i) > 0.55) {
                art.pixel(x, y, Palette.WARN);
            }
        }
        double p = (time * 0.05) % 1.2;
        int sx = (int) (-20 + p * 680);
        int sy = (int) (120 + Math.sin(p * 6) * 6);
        if (sx >= 0 && sx < W) {
            art.pixel(sx, sy, Palette.STAR_NEAR);
            art.pixel(sx - 1, sy, Palette.STAR_MID);
        }
    }

    // ================================================================ 主题 4：侧缘星系

    private static void edgeOnGalaxy(int[] buf, java.util.Random r) {
        fill(buf, argb(5, 6, 14));
        scatterStars(buf, r, 120, 2);
        int cx = 320;
        int cy = 175;
        ditherEllipse(buf, cx, cy, 285, 48, 0, 1,
                ramp(argb(5, 6, 14), argb(36, 54, 94), argb(127, 168, 217), argb(255, 233, 200), argb(255, 244, 226)));
        // 尘埃暗带横穿腰部
        for (int x = 40; x < 600; x++) {
            int wob = (int) (Math.sin(x * 0.03) * 5 + fbm(x * 0.02, 0, 33, 2) * 8 - 4);
            for (int y = cy - 4 + wob; y < cy + 7 + wob; y++) {
                buf[y * W + x] = argb(8, 9, 18);
            }
        }
        // 核心辉光
        ditherEllipse(buf, cx, cy, 46, 14, 0, 1, ramp(argb(8, 9, 18), argb(255, 220, 170), argb(255, 246, 230)));
    }

    // ================================================================ 主题 5：超新星遗迹

    private static void supernova(int[] buf, java.util.Random r) {
        fill(buf, argb(6, 8, 18));
        scatterStars(buf, r, 100, 2);
        int cx = 320;
        int cy = 180;
        wobbleRing(buf, cx, cy, 173, 130, 23, 0.16, 3,
                ramp(argb(6, 8, 18), argb(122, 74, 159), argb(217, 122, 58)));
        wobbleRing(buf, cx, cy, 125, 102, 23, 0.13, 3,
                ramp(argb(6, 8, 18), argb(42, 143, 143), argb(160, 220, 200)));
        wobbleRing(buf, cx, cy, 70, 56, 32, 0.12, 2,
                ramp(argb(6, 8, 18), argb(217, 122, 58), argb(255, 210, 140)));
        // 放射纤维
        for (int i = 0; i < 16; i++) {
            double a = i / 16.0 * Math.PI * 2 + 0.2;
            for (int d = 24; d < 165; d += 3) {
                int x = clampX((int) (cx + Math.cos(a) * d + Math.sin(d * 0.2) * 4));
                int y = clampY((int) (cy + Math.sin(a) * d * 0.8));
                buf[y * W + x] = argb(196, 122, 82);
            }
        }
        glowDot(buf, cx, cy, 7, argb(255, 250, 240));
    }

    private static void animateSupernova(PixelArt art, java.util.Random r, double time) {
        // 冲击环缓慢胀缩 + 中心脉冲星频闪
        double pulse = Math.sin(time * 0.9);
        int ringR = (int) (128 + pulse * 9);
        for (int i = 0; i < 60; i++) {
            double a = i / 60.0 * Math.PI * 2;
            int x = clampX((int) (320 + Math.cos(a) * (ringR + Math.sin(i * 2.3) * 5)));
            int y = clampY((int) (180 + Math.sin(a) * (ringR * 0.75 + Math.cos(i * 1.7) * 4)));
            art.pixel(x, y, pulse > 0 ? Palette.CYAN : color(0xD97A3A));
        }
        if (Math.sin(time * 11) > 0.75) {
            art.pixel(320, 180, Palette.STAR_NEAR);
        }
    }

    // ================================================================ 主题 6：冰晶星云

    private static void iceNebula(int[] buf, java.util.Random r) {
        fill(buf, argb(6, 10, 22));
        int[] stops = ramp(argb(6, 10, 22), argb(14, 28, 58), argb(39, 74, 122), argb(127, 168, 201), argb(232, 244, 255));
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double v = fbm(x * 0.011, y * 0.013, 61, 3);
                double t = Math.max(0, (v - 0.42) / 0.45);
                if (t > 0.03) {
                    buf[y * W + x] = rampPixel(t, x, y, stops);
                }
            }
        }
        scatterStars(buf, r, 70, 2);
        // 冰晶闪点：亮白 + 十字冰针
        for (int i = 0; i < 34; i++) {
            int x = 20 + r.nextInt(600);
            int y = 15 + r.nextInt(320);
            buf[y * W + x] = argb(240, 250, 255);
            if (r.nextInt(3) == 0) {
                buf[y * W + clampX(x - 1)] = argb(160, 200, 230);
                buf[y * W + clampX(x + 1)] = argb(160, 200, 230);
            }
        }
    }

    private static void animateIce(PixelArt art, java.util.Random r, double time) {
        // 缓慢飘落的冰晶 + 顶部极光帘波动
        for (int i = 0; i < 26; i++) {
            double bx = r.nextDouble() * W;
            double speed = 6 + r.nextDouble() * 10;
            double y = (r.nextDouble() * H + time * speed) % H;
            double sway = Math.sin(time * 1.2 + i) * 5;
            art.pixel(clampX((int) (bx + sway)), clampY((int) y), color(0xDCEBFF));
        }
        for (int x = 60; x < 580; x += 2) {
            double wave = Math.sin(x * 0.05 + time * 1.6) * 7 + Math.sin(x * 0.013 - time) * 5;
            int top = 26 + (int) wave;
            for (int y = top; y < top + 16; y += 2) {
                if (((x + y) & 3) == 0) {
                    art.pixel(x, y, color(0x6FD9C9));
                }
            }
        }
    }

    // ================================================================ 主题 7：恒星摇篮

    private static void nursery(int[] buf, java.util.Random r) {
        fill(buf, argb(13, 10, 20));
        // 背景：青到橙的辉光斑
        ditherEllipse(buf, 200, 140, 190, 120, 0, 1, ramp(argb(13, 10, 20), argb(18, 58, 68), argb(46, 110, 100)));
        ditherEllipse(buf, 430, 220, 180, 120, 0, 1, ramp(argb(13, 10, 20), argb(96, 52, 28), argb(168, 90, 40)));
        // 尘埃柱：剪影 + 边缘轮照
        int[][] pillars = {{150, 24, 26, 210}, {260, 60, 20, 240}, {390, 10, 30, 250}, {470, 120, 16, 200}};
        for (int[] pil : pillars) {
            int px = pil[0];
            int top = pil[1];
            int pw = pil[2];
            int bot = pil[3];
            for (int y = top; y < bot; y++) {
                int wobble = (int) (Math.sin(y * 0.05 + px) * 4 + fbm(px * 0.05, y * 0.03, 88, 2) * 8 - 4);
                for (int x = px + wobble; x < px + pw + wobble; x++) {
                    int cx = clampX(x);
                    buf[clampY(y) * W + cx] = argb(6, 4, 9);
                    // 左缘轮照（光源在左上）
                    if (x == px + wobble) {
                        for (int k = 0; k < 2; k++) {
                            if (((y + k) & 1) == 0) {
                                buf[clampY(y) * W + clampX(x + 1 + k)] = argb(232, 160, 90);
                            }
                        }
                    }
                }
            }
            // 柱顶原恒星
            glowDot(buf, px + pw / 2, top - 3, 4, argb(255, 236, 200));
        }
        scatterStars(buf, r, 60, 2);
    }

    private static void animateNursery(PixelArt art, java.util.Random r, double time) {
        // 原恒星脉动 + 柱内向下漂落的气体粒
        int[][] tips = {{163, 21}, {270, 57}, {405, 7}, {478, 117}};
        for (int i = 0; i < tips.length; i++) {
            if (Math.sin(time * (2 + i * 0.7) + i * 2) > 0.3) {
                art.pixel(tips[i][0], tips[i][1], Palette.WARN);
                art.pixel(tips[i][0] + 1, tips[i][1], color(0xE8A05A));
            }
        }
        for (int i = 0; i < 16; i++) {
            int px = tips[i % tips.length][0] + r.nextInt(30) - 15;
            double y = (r.nextDouble() * 240 + time * (8 + i)) % 240 + 20;
            art.pixel(clampX(px), clampY((int) y), color(0x8A5A3A));
        }
    }

    // ================================================================ 主题 8：脉冲星

    private static void pulsar(int[] buf, java.util.Random r) {
        fill(buf, argb(7, 5, 18));
        ditherEllipse(buf, 320, 180, 270, 160, 0, 1, ramp(argb(7, 5, 18), argb(38, 28, 70), argb(66, 52, 112)));
        scatterStars(buf, r, 90, 2);
        // 静态预置的波束锥（淡），动态层再画旋转亮束
        ditherWedge(buf, 320, 180, 1.1, 0.5, 250, argb(84, 70, 140));
        ditherWedge(buf, 320, 180, 1.1 + Math.PI, 0.5, 250, argb(84, 70, 140));
        // 回波环
        wobbleRing(buf, 320, 180, 118, 118, 12, 0.10, 7,
                ramp(argb(7, 5, 18), argb(70, 80, 140), argb(120, 134, 200)));
        glowDot(buf, 320, 180, 5, argb(240, 244, 255));
    }

    private static void animatePulsar(PixelArt art, java.util.Random r, double time) {
        // 双波束旋转 + 扫过"朝向镜头"时的频闪
        double a = time * 1.1;
        drawBeam(art, 320, 180, a);
        drawBeam(art, 320, 180, a + Math.PI);
        double flash = Math.cos(a);
        if (flash > 0.86) {
            glowDotArt(art, 320, 180, 4, 0xFFEAF0FF);
            for (int i = 0; i < 24; i++) {
                double aa = i / 24.0 * Math.PI * 2;
                int rr = (int) ((time % 2.4) / 2.4 * 130);
                art.pixel(clampX(320 + (int) (Math.cos(aa) * rr)),
                        clampY(180 + (int) (Math.sin(aa) * rr)), color(0x8FA8E8));
            }
        }
    }

    private static void drawBeam(PixelArt art, int cx, int cy, double a) {
        for (int d = 12; d < 250; d += 3) {
            double spread = 0.10 + d / 250.0 * 0.24;
            for (double da = -spread; da <= spread; da += 0.035) {
                if (((int) (d * 7 + da * 40) & 3) != 0) {
                    continue;   // 网点稀疏
                }
                int x = clampX((int) (cx + Math.cos(a + da) * d));
                int y = clampY((int) (cy + Math.sin(a + da) * d));
                art.pixel(x, y, color(d < 90 ? 0xB8C8F0 : 0x6478B8));
            }
        }
    }

    // ================================================================ 主题 9：尘埃带

    private static void dustLane(int[] buf, java.util.Random r) {
        fill(buf, argb(8, 7, 12));
        scatterStars(buf, r, 210, 2);
        // 斜向尘埃带剪影：near-black + 棕色絮
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double dist = Math.abs(y - (300 - x * 0.42));
                double half = 36 + fbm(x * 0.016, y * 0.016, 71, 3) * 20;
                if (dist < half - 7) {
                    double v = fbm(x * 0.03 + 9, y * 0.03, 17, 2);
                    buf[y * W + x] = v > 0.55 ? argb(26, 18, 16) : argb(5, 4, 8);
                } else if (dist < half) {
                    // 背光轮照：暖色网点镶边（窄）
                    buf[y * W + x] = rampPixel(1 - (dist - half + 7) / 7.0, x, y,
                            ramp(argb(8, 7, 12), argb(140, 100, 58), argb(214, 178, 118)));
                }
            }
        }
        // 轮照附近的亮星
        for (int i = 0; i < 14; i++) {
            int x = 30 + r.nextInt(580);
            int y = (int) (300 - x * 0.42) - 44 - r.nextInt(10);
            if (y > 4 && y < H) {
                buf[y * W + x] = argb(255, 244, 224);
            }
        }
    }

    private static void animateDust(PixelArt art, java.util.Random r, double time) {
        // 沿带漂移的尘粒 + 轮照闪点
        for (int i = 0; i < 20; i++) {
            double p = (r.nextDouble() + time * (0.008 + i * 0.0007)) % 1;
            int x = (int) (p * W);
            int y = (int) (300 - x * 0.42) + r.nextInt(52) - 26;
            art.pixel(clampX(x), clampY(y), color(0x4A382C));
        }
        if (Math.sin(time * 2.2) > 0.6) {
            int x = 120 + (int) (Math.sin(time * 0.4) * 200);
            art.pixel(clampX(x), clampY((int) (300 - x * 0.42) - 3), Palette.WARN);
        }
    }

    // ================================================================ 通用绘制助手

    private static int argb(int r, int g, int b) {
        return 0xFF000000 | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    private static int argbOf(int rgb) {
        return 0xFF000000 | rgb;
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int clampX(int x) {
        return Math.max(0, Math.min(W - 1, x));
    }

    private static int clampY(int y) {
        return Math.max(0, Math.min(H - 1, y));
    }

    private static int[] ramp(int... argb) {
        return argb;
    }

    /** 网点渐变：按拜耳阈值在相邻色档之间散点，不做插值混色。 */
    private static int rampPixel(double t, int x, int y, int[] stops) {
        double v = Math.max(0, Math.min(0.9999, t));
        double scaled = v * (stops.length - 1);
        int i = (int) scaled;
        double f = scaled - i;
        double b = BAYER[(y & 7) * 8 + (x & 7)] / 64.0;
        return stops[f > b ? Math.min(i + 1, stops.length - 1) : i];
    }

    private static void fill(int[] buf, int argb) {
        java.util.Arrays.fill(buf, argb);
    }

    private static void mirrorX(int[] buf) {
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W / 2; x++) {
                int a = buf[y * W + x];
                buf[y * W + x] = buf[y * W + (W - 1 - x)];
                buf[y * W + (W - 1 - x)] = a;
            }
        }
    }

    /** 值噪声：整数 hash + 平滑插值。 */
    private static double vnoise(double x, double y, long seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double xf = x - xi;
        double yf = y - yi;
        double u = xf * xf * (3 - 2 * xf);
        double v = yf * yf * (3 - 2 * yf);
        double a = hash(xi, yi, seed);
        double b = hash(xi + 1, yi, seed);
        double c = hash(xi, yi + 1, seed);
        double d = hash(xi + 1, yi + 1, seed);
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }

    private static double fbm(double x, double y, long seed, int octaves) {
        double sum = 0;
        double amp = 0.55;
        double norm = 0;
        for (int o = 0; o < octaves; o++) {
            sum += vnoise(x, y, seed + o * 131) * amp;
            norm += amp;
            x *= 2.1;
            y *= 2.1;
            amp *= 0.55;
        }
        return sum / norm;
    }

    private static double hash(int x, int y, long seed) {
        long h = x * 374761393L + y * 668265263L + seed * 1274126177L;
        h = (h ^ (h >> 13)) * 1274126177L;
        return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0;
    }

    /** 椭圆网点渐变：inner..outer 之间由 base 过渡到 ramp（t: 0 内 → 1 外）。 */
    private static void ditherEllipse(int[] buf, double cx, double cy, double rx, double ry,
                                      double inner, double outer, int[] stops) {
        int x0 = clampX((int) (cx - rx - 2));
        int x1 = clampX((int) (cx + rx + 2));
        int y0 = clampY((int) (cy - ry - 2));
        int y1 = clampY((int) (cy + ry + 2));
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double d = Math.hypot((x - cx) / rx, (y - cy) / ry);
                double t = 1 - (d - inner) / Math.max(0.0001, outer - inner);
                if (t <= 0) {
                    continue;
                }
                buf[y * W + x] = rampPixel(t, x, y, stops);
            }
        }
    }

    /** 椭圆环：d 在 inner..outer 之间才画（ramp 按 |d-环心| 归一）。 */
    private static void ditherEllipseRing(int[] buf, double cx, double cy, double rx, double ry,
                                          double inner, double outer, int[] stops) {
        int x0 = clampX((int) (cx - rx - 2));
        int x1 = clampX((int) (cx + rx + 2));
        int y0 = clampY((int) (cy - ry - 2));
        int y1 = clampY((int) (cy + ry + 2));
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double d = Math.hypot((x - cx) / rx * rx / rx, (y - cy) / ry * ry / rx);
                d = Math.hypot((x - cx) / rx, (y - cy) / ry) * rx;
                if (d < inner || d > outer) {
                    continue;
                }
                double t = 1 - Math.abs(d - (inner + outer) / 2) / ((outer - inner) / 2 + 0.0001);
                buf[y * W + x] = rampPixel(t * 1.4 - 0.2, x, y, stops);
            }
        }
    }

    /** 带角向扰动的环：归一椭圆距离 d≈1 的环带，半径按 fbm(a) 扰动——超新星残余不是正圆。 */
    private static void wobbleRing(int[] buf, double cx, double cy, double rx, double ry,
                                   double thick, double wobble, long seed, int[] stops) {
        double norm = Math.min(rx, ry);
        double thickN = thick / norm;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double dx = (x - cx) / rx;
                double dy = (y - cy) / ry;
                double d = Math.hypot(dx, dy);
                if (d < 0.2 || d > 1.8) {
                    continue;
                }
                double a = Math.atan2(dy, dx);
                double wob = (fbm(Math.cos(a) * 2 + 5, Math.sin(a) * 2 + 5, seed, 2) - 0.5)
                        * 2 * wobble;
                double from = Math.abs(d - (1 + wob));
                if (from > thickN) {
                    continue;
                }
                double t = 1 - from / thickN;
                buf[y * W + x] = rampPixel(t, x, y, stops);
            }
        }
    }

    /** 椭圆环带：inner..outer 之间由 ramp[0]（内缘）单调衰减到 ramp[end]（外缘），网点过渡。 */
    private static void annulus(int[] buf, double cx, double cy, double rx, double ry,
                                double inner, double outer, int[] stops) {
        double innerN = inner / Math.min(rx, ry);
        double outerN = outer / Math.min(rx, ry);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double d = Math.hypot((x - cx) / rx, (y - cy) / ry);
                if (d < innerN || d > outerN) {
                    continue;
                }
                double t = 1 - (d - innerN) / (outerN - innerN);
                buf[y * W + x] = rampPixel(t, x, y, stops);
            }
        }
    }

    private static void fillEllipse(int[] buf, double cx, double cy, double rx, double ry, int argb) {
        int x0 = clampX((int) (cx - rx));
        int x1 = clampX((int) (cx + rx));
        int y0 = clampY((int) (cy - ry));
        int y1 = clampY((int) (cy + ry));
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                if (Math.hypot((x - cx) / rx, (y - cy) / ry) <= 1) {
                    buf[y * W + x] = argb;
                }
            }
        }
    }

    /** 从核心向外的光点（网点衰减）。 */
    private static void glowDot(int[] buf, int cx, int cy, int r, int core) {
        for (int dy = -r - 2; dy <= r + 2; dy++) {
            for (int dx = -r - 2; dx <= r + 2; dx++) {
                double d = Math.hypot(dx, dy);
                if (d > r + 2) {
                    continue;
                }
                int x = clampX(cx + dx);
                int y = clampY(cy + dy);
                if (d <= r * 0.4) {
                    buf[y * W + x] = core;
                } else if (BAYER[((cy + dy) & 7) * 8 + ((cx + dx) & 7)] / 64.0 < 1 - (d - r * 0.4) / (r + 2 - r * 0.4)) {
                    buf[y * W + x] = core;
                }
            }
        }
    }

    private static void glowDotArt(PixelArt art, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Math.hypot(dx, dy) <= r) {
                    art.pixel(clampX(cx + dx), clampY(cy + dy), color(argb));
                }
            }
        }
    }

    private static void scatterStars(int[] buf, java.util.Random r, int n, int minBright) {
        for (int i = 0; i < n; i++) {
            int x = r.nextInt(W);
            int y = r.nextInt(H);
            int tier = minBright + r.nextInt(3);
            int c = switch (tier) {
                case 0 -> argb(70, 80, 110);
                case 1 -> argb(126, 140, 166);
                case 2 -> argb(224, 236, 255);
                default -> argb(255, 255, 255);
            };
            buf[y * W + x] = c;
            if (tier >= 2 && r.nextInt(4) == 0) {
                buf[clampY(y + 1) * W + x] = argb(126, 140, 166);
            }
        }
    }

    /** 楔形波束（静态淡版）。 */
    private static void ditherWedge(int[] buf, int cx, int cy, double angle, double spread,
                                    int length, int argb) {
        for (int d = 20; d < length; d += 2) {
            for (double da = -spread * (0.3 + d / (double) length); da <= spread * (0.3 + d / (double) length); da += 0.05) {
                if (((int) (d * 5 + da * 30) & 1) != 0) {
                    continue;
                }
                int x = clampX((int) (cx + Math.cos(angle + da) * d));
                int y = clampY((int) (cy + Math.sin(angle + da) * d));
                buf[y * W + x] = argb;
            }
        }
    }

    /** 通用粒子色（animate 用 javafx Color 走 art.pixel）。 */
    private static com.hysx.salvosequence.core.Color color(int argb) {
        return com.hysx.salvosequence.core.Color.rgb((argb >> 16) & 255, (argb >> 8) & 255, argb & 255,
                ((argb >>> 24) & 255) / 255.0);
    }

    private static void animateSparks(PixelArt art, java.util.Random r, double time, int rgb,
                                      int n, boolean rise) {
        for (int i = 0; i < n; i++) {
            double bx = r.nextDouble() * W;
            double by = r.nextDouble() * H;
            double phase = r.nextDouble() * 10;
            double t = (time * (rise ? -1 : 1) * (8 + r.nextDouble() * 10)) + phase * 30;
            int x = clampX((int) (bx + Math.sin(time * 0.9 + i) * 8));
            int y = clampY((int) ((by + t) % H + H) % H);
            if (Math.sin(time * 3 + i * 2.1) > -0.2) {
                art.pixel(x, y, color(rgb));
            }
        }
    }
}
