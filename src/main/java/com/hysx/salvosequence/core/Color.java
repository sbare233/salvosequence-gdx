package com.hysx.salvosequence.core;

import java.util.Objects;

/**
 * 颜色（libGDX 版）：接口口径沿用原 JavaFX {@code javafx.scene.paint.Color}，
 * 所以游戏代码里的取色/混色/调色板逻辑一行没改，只换了实现。
 *
 * <p>不可变、按值比较（equals/hashCode 覆盖）——调色板映射、
 * {@link PixelArt#pick(Map, Color, Color)}、文字光栅缓存都以 Color 为 key。</p>
 */
public final class Color {

    public static final Color BLACK = rgb(0, 0, 0);
    public static final Color WHITE = rgb(255, 255, 255);
    public static final Color GRAY = rgb(128, 128, 128);
    public static final Color RED = rgb(255, 0, 0);
    public static final Color GREEN = rgb(0, 255, 0);
    public static final Color BLUE = rgb(0, 0, 255);
    public static final Color YELLOW = rgb(255, 255, 0);
    public static final Color CYAN = rgb(0, 255, 255);
    public static final Color ORANGE = rgb(255, 200, 0);
    public static final Color LIME = rgb(50, 205, 50);
    public static final Color TRANSPARENT = color(0, 0, 0, 0);

    private final double red;
    private final double green;
    private final double blue;
    private final double opacity;

    private Color(double red, double green, double blue, double opacity) {
        this.red = clamp01(red);
        this.green = clamp01(green);
        this.blue = clamp01(blue);
        this.opacity = clamp01(opacity);
    }

    /** 0~255 整数三原色，不透明。 */
    public static Color rgb(int red, int green, int blue) {
        return rgb(red, green, blue, 1.0);
    }

    /** 0~255 整数三原色 + 0~1 不透明度。 */
    public static Color rgb(int red, int green, int blue, double opacity) {
        return new Color(red / 255.0, green / 255.0, blue / 255.0, opacity);
    }

    /** 0~1 浮点三原色，不透明。 */
    public static Color color(double red, double green, double blue) {
        return new Color(red, green, blue, 1.0);
    }

    /** 0~1 浮点三原色 + 不透明度。 */
    public static Color color(double red, double green, double blue, double opacity) {
        return new Color(red, green, blue, opacity);
    }

    /** {@code #RRGGBB} / {@code #RRGGBBAA} / {@code 0xRRGGBBAA} 十六进制取色（工具代码用）。 */
    public static Color web(String hex) {
        String s = hex.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        } else if (s.startsWith("0x")) {
            s = s.substring(2);
        }
        long v = Long.parseLong(s, 16);
        if (s.length() <= 6) {
            return rgb((int) ((v >> 16) & 0xFF), (int) ((v >> 8) & 0xFF), (int) (v & 0xFF));
        }
        return rgb((int) ((v >> 24) & 0xFF), (int) ((v >> 16) & 0xFF), (int) ((v >> 8) & 0xFF),
                ((v & 0xFF) / 255.0));
    }

    public double getRed() {
        return red;
    }

    public double getGreen() {
        return green;
    }

    public double getBlue() {
        return blue;
    }

    public double getOpacity() {
        return opacity;
    }

    /** 变暗 30%（口径同原 JavaFX darker）。 */
    public Color darker() {
        return new Color(red * 0.7, green * 0.7, blue * 0.7, opacity);
    }

    /** 提亮（口径同原 JavaFX brighter：除以 0.7 后钳制）。 */
    public Color brighter() {
        double f = 1.0 / 0.7;
        return new Color(clamp01(red * f), clamp01(green * f), clamp01(blue * f), opacity);
    }

    /** 线性插值到目标色（本类没有的旧 JavaFX 方法，给移植代码补齐用）。 */
    public Color interpolate(Color target, double t) {
        if (target == null) {
            return this;
        }
        return new Color(
                red + (target.red - red) * t,
                green + (target.green - green) * t,
                blue + (target.blue - blue) * t,
                opacity + (target.opacity - opacity) * t);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Color other)) {
            return false;
        }
        return Double.compare(red, other.red) == 0
                && Double.compare(green, other.green) == 0
                && Double.compare(blue, other.blue) == 0
                && Double.compare(opacity, other.opacity) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(red, green, blue, opacity);
    }

    @Override
    public String toString() {
        return String.format("0x%02X%02X%02X%02X",
                (int) Math.round(opacity * 255), (int) Math.round(red * 255),
                (int) Math.round(green * 255), (int) Math.round(blue * 255));
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
