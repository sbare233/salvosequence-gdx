package com.hysx.salvosequence.ui;

/**
 * 画质设置：CRT 扫描线与风格化滤镜。
 * <p>由设置面板读写，不再占用 F2/F3 功能键。</p>
 */
public final class VideoSettings {

    /** 风格化：原画 / 模糊叠加 / 辉光（JavaFX Bloom）。 */
    public enum Style {
        RAW("原画", "RAW"),
        BLUR("模糊", "BLUR"),
        BLOOM("辉光", "BLOOM");

        private final String label;
        private final String labelEn;

        Style(String label, String labelEn) {
            this.label = label;
            this.labelEn = labelEn;
        }

        public String label() {
            return label;
        }

        public String labelEn() {
            return labelEn;
        }

        public Style next() {
            Style[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    /**
     * 扫描线绘制时机（相对画质特效）。
     *
     * <ul>
     *   <li>{@link #AFTER_EFFECT}：先做风格化，最后画扫描线 —— 线条压在辉光/模糊之上，
     *       锐利干净，是历史行为（默认）；</li>
     *   <li>{@link #BEFORE_EFFECT}：扫描线先画进画面，再让风格化处理 —— 辉光的亮部
     *       扩散与模糊会把线一起晕开，观感更像真实 CRT 的信号发光。</li>
     * </ul>
     *
     * <p>顺序由 {@code PixelGameView.render} 决定：模糊/原画风格直接在屏幕上调整
     * 绘制顺序；辉光风格则把扫描线画进 gdx-vfx 的输入捕获，让后处理链真正"看到"线。</p>
     */
    public enum ScanlineOrder {
        AFTER_EFFECT("特效后", "AFTER FX"),
        BEFORE_EFFECT("特效前", "BEFORE FX");

        private final String label;
        private final String labelEn;

        ScanlineOrder(String label, String labelEn) {
            this.label = label;
            this.labelEn = labelEn;
        }

        public String label() {
            return label;
        }

        public String labelEn() {
            return labelEn;
        }

        public ScanlineOrder next() {
            ScanlineOrder[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    /**
     * 扫描线的形状。
     *
     * <ul>
     *   <li>{@link #STRAIGHT}：一条贯穿整行的黑条，规整、最像"信号层面的行消隐"。</li>
     *   <li>{@link #JAGGED}：把黑条切成一个个方块、相邻两块上下错开半条高度，
     *       形成锯齿/错位感。真实显像管受会聚误差与荫罩网格影响，行与行之间本来
     *       就不是严丝合缝的一条直线。</li>
     * </ul>
     */
    public enum ScanlineShape {
        STRAIGHT("直条", "STRAIGHT"),
        JAGGED("锯齿", "JAGGED");

        private final String label;
        private final String labelEn;

        ScanlineShape(String label, String labelEn) {
            this.label = label;
            this.labelEn = labelEn;
        }

        public String label() {
            return label;
        }

        public String labelEn() {
            return labelEn;
        }

        public ScanlineShape next() {
            ScanlineShape[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    /**
     * 背景滤镜：实时 3D 背景（星云/恒星/星球/脉冲星）出图后用哪种方式"像素化"。
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

    private static Style style = Style.BLUR;
    private static boolean scanlines = true;
    /**
     * 孔径格栅（aperture grille）：荫栅式显像管（Sony Trinitron 一系）在玻璃后面
     * 竖着的 RGB 荧光条，条纹之间是黑矩阵。和扫描线是同一个年代的两件"屏幕物理
     * 纹理"——扫描线是行，格栅是列，所以代码里并列成两个开关，可以同时开。
     *
     * <p>与荫罩（shadow mask，孔洞式）的区别在图形上：孔径格栅是<b>连续竖条</b>，
     * 荫罩是<b>点状孔</b>；这里画竖条，因为竖条在小尺寸像素画布上不会产生摩尔纹。</p>
     */
    private static boolean apertureGrille = false;
    private static ScanlineOrder scanlineOrder = ScanlineOrder.BEFORE_EFFECT;
    private static ScanlineShape scanlineShape = ScanlineShape.STRAIGHT;
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

    public static Style style() {
        return style;
    }

    public static void setStyle(Style value) {
        style = value == null ? Style.RAW : value;
    }

    public static Style cycleStyle() {
        style = style.next();
        return style;
    }

    public static boolean scanlines() {
        return scanlines;
    }

    public static void setScanlines(boolean value) {
        scanlines = value;
    }

    public static boolean toggleScanlines() {
        scanlines = !scanlines;
        return scanlines;
    }

    /** 孔径格栅是否叠加（与扫描线并列的显像管物理纹理，默认关）。 */
    public static boolean apertureGrille() {
        return apertureGrille;
    }

    public static void setApertureGrille(boolean value) {
        apertureGrille = value;
    }

    public static boolean toggleApertureGrille() {
        apertureGrille = !apertureGrille;
        return apertureGrille;
    }

    /** 扫描线画在风格化特效之前还是之后。 */
    public static ScanlineOrder scanlineOrder() {
        return scanlineOrder;
    }

    public static void setScanlineOrder(ScanlineOrder value) {
        scanlineOrder = value == null ? ScanlineOrder.AFTER_EFFECT : value;
    }

    public static ScanlineOrder cycleScanlineOrder() {
        scanlineOrder = scanlineOrder.next();
        return scanlineOrder;
    }

    public static ScanlineShape scanlineShape() {
        return scanlineShape;
    }

    public static void setScanlineShape(ScanlineShape value) {
        scanlineShape = value == null ? ScanlineShape.STRAIGHT : value;
    }

    public static ScanlineShape cycleScanlineShape() {
        scanlineShape = scanlineShape.next();
        return scanlineShape;
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
