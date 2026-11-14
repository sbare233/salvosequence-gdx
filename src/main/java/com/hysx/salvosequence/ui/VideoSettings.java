package com.hysx.salvosequence.ui;

/**
 * 画质设置：CRT 全屏滤镜模式、背景滤镜与诊断开关。
 *
 * <p>扫描线和孔径格栅是两种不同的显像管结构，观感也不一样：扫描线是<b>横向</b>的行消隐
 * 暗带，孔径格栅是<b>纵向</b>的红/绿/蓝荧光条。这里用一个四态循环把两者分开，也可以叠加；
 * 具体像素计算在 {@code PixelGameView} 的单个 CRT pass 里完成。</p>
 *
 * <p>格栅图案另有一项 {@link GrilleStyle}：格栅2.txt 的纵向栅条，或 crt.txt 的旋转荫罩
 * （shadow mask）。玻璃桶形扭曲则用 {@link #screenWarp()} 单独开关，默认关；邻像素混合
 * 用 {@link #bloom()} 单独开关，默认开。</p>
 */
public final class VideoSettings {

    /**
     * CRT 滤镜模式（按这个顺序循环）。
     *
     * <ul>
     *   <li>{@link #OFF} 关：直通，不跑 CRT pass。</li>
     *   <li>{@link #SCANLINE} 扫描线：一个设计像素一行，行尾压暗一条设备像素线。</li>
     *   <li>{@link #GRILLE} 孔径格栅：纵向荧光条，每条一个设备像素宽，三条一组（红/绿/蓝），
     *       固定在设备像素上、不跟设计像素放大。</li>
     *   <li>{@link #SCANLINE_GRILLE} 扫描线+格栅：两者叠加，最接近 crt.txt 右侧那三分之一的完整效果。</li>
     * </ul>
     */
    public enum CrtFilter {
        OFF("关", "OFF"),
        SCANLINE("扫描线", "SCANLINE"),
        GRILLE("孔径格栅", "APERTURE GRILLE"),
        SCANLINE_GRILLE("扫描线+格栅", "SCANLINE+GRILLE");

        private final String label;
        private final String labelEn;

        CrtFilter(String label, String labelEn) {
            this.label = label;
            this.labelEn = labelEn;
        }

        public String label() {
            return label;
        }

        public String labelEn() {
            return labelEn;
        }

        public CrtFilter next() {
            CrtFilter[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        /** 这个模式要不要画横向扫描线。 */
        public boolean hasScanline() {
            return this == SCANLINE || this == SCANLINE_GRILLE;
        }

        /** 这个模式要不要画纵向孔径格栅。 */
        public boolean hasGrille() {
            return this == GRILLE || this == SCANLINE_GRILLE;
        }
    }

    /**
     * 格栅图案：同样是红/绿/蓝三色荧光条，排列方式不同。
     *
     * <ul>
     *   <li>{@link #APERTURE} 栅条：格栅2.txt 里启用的孔径格栅，纵向三条一组，
     *       每条一个设备像素宽，亮暗配比 1.5 / 0.5。</li>
     *   <li>{@link #SHADOW} 荫罩：crt.txt 的旋转 shadow mask——格栅斜着走
     *       （{@code x + y * (6 / u_scale)}），一组 6 个设备像素、每条 2 像素宽，
     *       放大看是斜纹。斜率跟着 2×/3× 走，一个设计像素行正好一组，
     *       3× 时不会出现奇偶行差半组的错位。</li>
     * </ul>
     */
    public enum GrilleStyle {
        APERTURE("栅条", "APERTURE GRILLE"),
        SHADOW("荫罩", "SHADOW MASK");

        private final String label;
        private final String labelEn;

        GrilleStyle(String label, String labelEn) {
            this.label = label;
            this.labelEn = labelEn;
        }

        public String label() {
            return label;
        }

        public String labelEn() {
            return labelEn;
        }

        public GrilleStyle next() {
            GrilleStyle[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    /**
     * 背景滤镜：实时 3D 背景（星云/恒星/星球/脉冲星）出图后用哪种方式“像素化”。
     *
     * <ul>
     *   <li>{@link #BAYER} 拜耳：项目原有的 {@code BayerPixelator}，8×8 网点有序抖动，
     *       块对齐、色彩增益/饱和/深空化一起做——星云这类大面积渐变最像老游戏的网点。</li>
     *   <li>{@link #PIXEL} 像素滤镜：从 Godot {@code PixelArt.gdshader} 翻译来的
     *       {@code PixelArtFilter}，亮度分档 + Sobel 描边，边缘更硬、块面更干净。</li>
     * </ul>
     */
    public enum BackdropFilter {
        BAYER("拜耳", "BAYER"),
        PIXEL("像素", "PIXEL");

        private final String label;
        private final String labelEn;

        BackdropFilter(String label, String labelEn) {
            this.label = label;
            this.labelEn = labelEn;
        }

        public String label() {
            return label;
        }

        public String labelEn() {
            return labelEn;
        }

        public BackdropFilter next() {
            BackdropFilter[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    /** 当前 CRT 模式：默认扫描线 + 孔径格栅（crt.txt 第三块的完整效果）。 */
    private static CrtFilter crtFilter = CrtFilter.OFF;
    /** 最近一次开着的模式；从"关"切回来时恢复它，而不是一律回到最轻的扫描线。 */
    private static CrtFilter lastCrtOn = CrtFilter.SCANLINE_GRILLE;
    /** 格栅图案：默认纵向栅条（格栅2.txt 启用的那个版本）。 */
    private static GrilleStyle grilleStyle = GrilleStyle.APERTURE;
    /** 显像管玻璃的桶形扭曲；独立开关，默认关，关掉后画面四边是直的。 */
    private static boolean screenWarp = false;
    /** 邻像素混合（原「辉光弥散」）：按设备像素在设计像素里的位置给邻居加权，越靠边权重越大，
     *  中心几乎不混；横向始终参与，纵向只在格栅开着时参与。k 随缩放归一化，亮度守恒。 */
    private static boolean bloom = true;
    /** 混合强度 k 的系数：shader 里 {@code float k = u_mix * s * s}（u_mix 既是开关也是
     *  这个系数，关时传 0 走直通）。1.0 是调参时的原始手感，调大往中心收（边缘渗色更少、
     *  更锐），调小往邻居摊（更糊）。设置页有滑条可拖。 */
    private static double mixK = 1.0;
    /** 滑条量程：0.25 已经很糊，3.0 几乎只剩像素本身。 */
    public static final double MIX_K_MIN = 0.25;
    public static final double MIX_K_MAX = 3.0;
    /** 滑条最小步进（键盘一次 ±STEP，拖鼠标也吸附到这个格）。 */
    public static final double MIX_K_STEP = 0.05;
    private static BackdropFilter backdropFilter = BackdropFilter.BAYER;
    // 诊断开关（F7/F8）：像素化上屏（关 = Linear 双线性，不放大像素）与拜耳抖动。
    private static boolean pixelated = true;
    private static boolean bayer = true;
    // 输出抖动（F9，默认开）：上屏时对 RGB 加 ±1 LSB 的 4x4 有序抖动。
    // 近黑渐变（盘缘纱 3~17/255）在 8bit 信号 + 显示器 gamma 下会量化出色带
    // 断崖（F12 帧缓冲截图正确、实机可见即此机理），空间抖动让眼睛积分掉台阶。
    private static boolean outputDither = true;

    private VideoSettings() {
    }

    public static CrtFilter crtFilter() {
        return crtFilter;
    }

    public static void setCrtFilter(CrtFilter value) {
        crtFilter = value == null ? CrtFilter.OFF : value;
        if (crtFilter != CrtFilter.OFF) {
            lastCrtOn = crtFilter;
        }
    }

    /** 设置页按一次切换：关 → 扫描线 → 孔径格栅 → 扫描线+格栅 → 关。 */
    public static CrtFilter cycleCrtFilter() {
        setCrtFilter(crtFilter.next());
        return crtFilter;
    }

    /** 旧接口兼容：任意 CRT 效果开着就返回 true（工具/旧调用方用）。 */
    public static boolean scanlines() {
        return crtFilter != CrtFilter.OFF;
    }

    /** 旧接口兼容：false 关掉整个 CRT；true 恢复上一次开着的模式。 */
    public static void setScanlines(boolean value) {
        setCrtFilter(value ? lastCrtOn : CrtFilter.OFF);
    }

    /** 旧接口兼容：在"关"和上一次开着的模式之间切换。 */
    public static boolean toggleScanlines() {
        setScanlines(!scanlines());
        return scanlines();
    }

    public static GrilleStyle grilleStyle() {
        return grilleStyle;
    }

    public static void setGrilleStyle(GrilleStyle value) {
        grilleStyle = value == null ? GrilleStyle.APERTURE : value;
    }

    /** 设置页按一次切换：栅条 ⇄ 荫罩。 */
    public static GrilleStyle cycleGrilleStyle() {
        grilleStyle = grilleStyle.next();
        return grilleStyle;
    }

    /** 桶形扭曲开关；关掉后 CRT 只做扫描线/格栅，不再弯画面。 */
    public static boolean screenWarp() {
        return screenWarp;
    }

    public static void setScreenWarp(boolean value) {
        screenWarp = value;
    }

    public static boolean toggleScreenWarp() {
        screenWarp = !screenWarp;
        return screenWarp;
    }

    /** 邻像素混合开关；关掉后 CRT 只保留扫描线/格栅的硬边，像素之间不再混色。 */
    public static boolean bloom() {
        return bloom;
    }

    public static void setBloom(boolean value) {
        bloom = value;
    }

    public static boolean toggleBloom() {
        bloom = !bloom;
        return bloom;
    }

    /** 混合强度系数 k（shader 里乘在 (2*scale/(scale+1))² 上）。 */
    public static double mixK() {
        return mixK;
    }

    /** 设定混合强度，钳到量程并按步进吸附；返回吸附后的值。 */
    public static double setMixK(double value) {
        double snapped = Math.round(value / MIX_K_STEP) * MIX_K_STEP;
        snapped = Math.max(MIX_K_MIN, Math.min(MIX_K_MAX, snapped));
        // 0.05 这类小数在 double 里是 0.30000000000000004，统一收成两位
        mixK = Math.round(snapped * 100.0) / 100.0;
        return mixK;
    }

    /** 键盘 ←→ 一次改一格。 */
    public static double nudgeMixK(double delta) {
        return setMixK(mixK + delta);
    }

    /** 回到调参时的原始手感。 */
    public static double resetMixK() {
        return setMixK(1.0);
    }

    public static BackdropFilter backdropFilter() {
        return backdropFilter;
    }

    public static void setBackdropFilter(BackdropFilter value) {
        backdropFilter = value == null ? BackdropFilter.BAYER : value;
    }

    public static BackdropFilter cycleBackdropFilter() {
        backdropFilter = backdropFilter.next();
        return backdropFilter;
    }

    /** 像素化上屏（Nearest）；false = Linear 双线性平滑（诊断对照用，F7）。 */
    public static boolean pixelated() {
        return pixelated;
    }

    public static void setPixelated(boolean value) {
        pixelated = value;
    }

    public static boolean togglePixelated() {
        pixelated = !pixelated;
        return pixelated;
    }

    /** 拜耳抖动（星系盘/尘埃云的 blitArgbTileDithered）；诊断对照用，F8。 */
    public static boolean bayer() {
        return bayer;
    }

    public static void setBayer(boolean value) {
        bayer = value;
    }

    public static boolean toggleBayer() {
        bayer = !bayer;
        return bayer;
    }

    /** 输出抖动（±1 LSB 有序抖动，打散近黑渐变色带）；诊断对照用，F9。 */
    public static boolean outputDither() {
        return outputDither;
    }

    public static void setOutputDither(boolean value) {
        outputDither = value;
    }

    public static boolean toggleOutputDither() {
        outputDither = !outputDither;
        return outputDither;
    }
}
