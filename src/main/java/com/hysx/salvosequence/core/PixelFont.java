package com.hysx.salvosequence.core;

import com.hysx.salvosequence.core.Color;

import java.util.HashMap;
import java.util.Map;

/**
 * 内置 5x7 点阵字体。
 *
 * <p>为什么不用系统字体：HUD 数字必须跟着画布一起按整数倍放大，系统字体在
 * 3 倍缩放下会变成灰色抗锯齿边缘，破坏像素观感。这里每个字形都是手写点阵，
 * 放大后依然是硬边像素。</p>
 *
 * <p>每个字形固定按"恰好 8 个字符 = 1 行，共 8 行"描述，多余列/行留空即可。</p>
 */
public final class PixelFont {

    /** 标准字形高度。 */
    public static final int GLYPH_HEIGHT = 8;

    private record Glyph(int width, byte[] rows) {
    }

    private static final Map<Character, Glyph> GLYPHS = new HashMap<>();
    private static final Map<Character, Glyph> UPPER_FALLBACK = new HashMap<>();

    private static void put(char c, int width, String pattern) {
        GLYPHS.put(c, decode(width, pattern));
    }

    /**
     * 解码一个 8 列点阵。
     *
     * <p><b>为什么宽度要从点阵里算，而不是用调用方给的 {@code width}：</b>
     * 这里每个字形都描述在 8 列网格上，而字形本身的墨迹通常占 7 列（比如 {@code '0'} 的
     * {@code "..XXX..."} 从第 3 列画到第 7 列）。{@code put} 里给的宽度只是"设计意图"
     * （5 列宽的数字、3 列宽的 {@code 'I'}），它比实际点亮的列数小 2 列左右。
     * 早先光标按这个"意图宽度"前进，于是每个字母都会压到下一个字母身上
     * （"AB" 画出来 B 会盖掉 A 的右半边）。现在一律按<b>真实墨迹跨度</b>前进，
     * 点阵数据本身不用改。</p>
     */
    private static Glyph decode(int width, String pattern) {
        if (pattern.length() != 64) {
            throw new IllegalArgumentException("字形点阵必须是 8x8=64 个字符: " + pattern.length());
        }
        byte[] rows = new byte[GLYPH_HEIGHT];
        int span = width;
        for (int y = 0; y < GLYPH_HEIGHT; y++) {
            int value = 0;
            for (int x = 0; x < 8; x++) {
                if (pattern.charAt(y * 8 + x) == 'X') {
                    value |= 1 << (7 - x);
                    span = Math.max(span, x + 1);
                }
            }
            rows[y] = (byte) value;
        }
        return new Glyph(span, rows);
    }

    static {
        // ---- 数字 ----
        put('0', 5, "..XXX..." + ".XX.XX.." + "XX...XX." + "XX...XX." + "XX...XX." + "XX...XX." + ".XX.XX.." + "..XXX...");
        put('1', 3, "..X....." + ".XX....." + "..X....." + "..X....." + "..X....." + "..X....." + "..X....." + ".XXX....");
        put('2', 5, "..XXX..." + ".XX.XX.." + "XX...XX." + ".....XX." + "...XXX.." + "..XX...." + ".XX....." + "XXXXXXX.");
        put('3', 5, "XXXXXX.." + "....XX.." + "...XX..." + "..XXX..." + ".....XX." + ".....XX." + "XX...XX." + ".XXXXX..");
        put('4', 5, "...XXX.." + "..X.XX.." + ".X..XX.." + "X...XX.." + "XXXXXXX." + "....XX.." + "....XX.." + "....XX..");
        put('5', 5, "XXXXXXX." + "XX......" + "XXXXXX.." + ".....XX." + ".....XX." + ".....XX." + "XX...XX." + ".XXXXX..");
        put('6', 5, "..XXX..." + ".XX....." + "XX......" + "XXXXXX.." + "XX...XX." + "XX...XX." + "XX...XX." + ".XXXXX..");
        put('7', 5, "XXXXXXX." + "XX...XX." + ".....XX." + "....XX.." + "...XX..." + "...XX..." + "..XX...." + "..XX....");
        put('8', 5, ".XXXXX.." + "XX...XX." + "XX...XX." + ".XXXXX.." + "XX...XX." + "XX...XX." + "XX...XX." + ".XXXXX..");
        put('9', 5, ".XXXXX.." + "XX...XX." + "XX...XX." + ".XXXXXX." + ".....XX." + ".....XX." + "....XX.." + ".XXXX...");

        // ---- 大写字母 ----
        put('A', 5, ".XXXXX.." + "XX...XX." + "XX...XX." + "XXXXXXX." + "XX...XX." + "XX...XX." + "XX...XX." + "XX...XX.");
        put('B', 5, "XXXXXX.." + "XX...XX." + "XX...XX." + "XXXXXX.." + "XX...XX." + "XX...XX." + "XX...XX." + "XXXXXX..");
        put('C', 5, ".XXXXX.." + "XX...XX." + "XX......" + "XX......" + "XX......" + "XX......" + "XX...XX." + ".XXXXX..");
        put('D', 5, "XXXXX..." + "XX..XX.." + "XX...XX." + "XX...XX." + "XX...XX." + "XX...XX." + "XX..XX.." + "XXXXX...");
        put('E', 5, "XXXXXXX." + "XX......" + "XX......" + "XXXXXX.." + "XX......" + "XX......" + "XX......" + "XXXXXXX.");
        put('F', 5, "XXXXXXX." + "XX......" + "XX......" + "XXXXXX.." + "XX......" + "XX......" + "XX......" + "XX......");
        put('G', 5, ".XXXXX.." + "XX...XX." + "XX......" + "XX......" + "XX..XXX." + "XX...XX." + "XX...XX." + ".XXXXXX.");
        put('H', 5, "XX...XX." + "XX...XX." + "XX...XX." + "XXXXXXX." + "XX...XX." + "XX...XX." + "XX...XX." + "XX...XX.");
        put('I', 3, "XXX....." + ".X......" + ".X......" + ".X......" + ".X......" + ".X......" + ".X......" + "XXX.....");
        put('J', 5, "..XXXXX." + "...XX..." + "...XX..." + "...XX..." + "...XX..." + "XX.XX..." + "XX.XX..." + ".XXX....");
        put('K', 5, "XX...XX." + "XX..XX.." + "XX.XX..." + "XXXX...." + "XX.XX..." + "XX..XX.." + "XX...XX." + "XX...XX.");
        put('L', 5, "XX......" + "XX......" + "XX......" + "XX......" + "XX......" + "XX......" + "XX......" + "XXXXXXX.");
        put('M', 5, "XX...XX." + "XXX.XXX." + "XXXXXXX." + "XX.X.XX." + "XX...XX." + "XX...XX." + "XX...XX." + "XX...XX.");
        put('N', 5, "XX...XX." + "XXX..XX." + "XXXX.XX." + "XX.XXXX." + "XX..XXX." + "XX...XX." + "XX...XX." + "XX...XX.");
        put('O', 5, ".XXXXX.." + "XX...XX." + "XX...XX." + "XX...XX." + "XX...XX." + "XX...XX." + "XX...XX." + ".XXXXX..");
        put('P', 5, "XXXXXX.." + "XX...XX." + "XX...XX." + "XXXXXX.." + "XX......" + "XX......" + "XX......" + "XX......");
        put('Q', 5, ".XXXXX.." + "XX...XX." + "XX...XX." + "XX...XX." + "XX...XX." + "XX.XXXX." + "XX..XX.." + ".XXXXX..");
        put('R', 5, "XXXXXX.." + "XX...XX." + "XX...XX." + "XXXXXX.." + "XX.XX..." + "XX..XX.." + "XX...XX." + "XX...XX.");
        put('S', 5, ".XXXXX.." + "XX...XX." + "XX......" + ".XXXXX.." + ".....XX." + ".....XX." + "XX...XX." + ".XXXXX..");
        put('T', 5, "XXXXXXX." + "..XXX..." + "...X...." + "...X...." + "...X...." + "...X...." + "...X...." + "...X....");
        put('U', 5, "XX...XX." + "XX...XX." + "XX...XX." + "XX...XX." + "XX...XX." + "XX...XX." + "XX...XX." + ".XXXXX..");
        put('V', 5, "XX...XX." + "XX...XX." + "XX...XX." + "XX...XX." + "XX...XX." + ".XX.XX.." + ".XX.XX.." + "..XXX...");
        put('W', 5, "XX...XX." + "XX...XX." + "XX...XX." + "XX...XX." + "XX.X.XX." + "XXXXXXX." + "XXX.XXX." + "XX...XX.");
        put('X', 5, "XX...XX." + "XX...XX." + ".XX.XX.." + "..XXX..." + "..XXX..." + ".XX.XX.." + "XX...XX." + "XX...XX.");
        put('Y', 5, "XX...XX." + "XX...XX." + ".XX.XX.." + "..XXX..." + "...X...." + "...X...." + "...X...." + "...X....");
        put('Z', 5, "XXXXXXX." + ".....XX." + "....XX.." + "...XX..." + "..XX...." + ".XX....." + "XX......" + "XXXXXXX.");

        // ---- 标点与符号 ----
        put(' ', 4, "........" + "........" + "........" + "........" + "........" + "........" + "........" + "........");
        put('-', 5, "........" + "........" + "........" + "XXXXXXX." + "........" + "........" + "........" + "........");
        put('_', 5, "........" + "........" + "........" + "........" + "........" + "........" + "........" + "XXXXXXX.");
        put('.', 2, "........" + "........" + "........" + "........" + "........" + "........" + "XX......" + "XX......");
        put(',', 2, "........" + "........" + "........" + "........" + "........" + "..X....." + "..X....." + ".X......");
        put(':', 2, "........" + "XX......" + "XX......" + "........" + "........" + "XX......" + "XX......" + "........");
        put(';', 2, "........" + "XX......" + "XX......" + "........" + "..X....." + "..X....." + ".X......" + "........");
        put('/', 4, "....XX.." + "...XX..." + "...XX..." + "..XX...." + "..XX...." + ".XX....." + ".XX....." + "XX......");
        put('!', 2, "XX......" + "XX......" + "XX......" + "XX......" + "XX......" + "........" + "XX......" + "........");
        put('?', 5, ".XXXXX.." + "XX...XX." + ".....XX." + "...XXX.." + "...XX..." + "........" + "...XX..." + "........");
        put('%', 5, "XX...XX." + "XX..XX.." + "...XX..." + "..XX...." + ".XX....." + "XX..XX.." + "X...XX.." + "....XX..");
        put('+', 5, "........" + "...X...." + "...X...." + "XXXXXXX." + "...X...." + "...X...." + "........" + "........");
        put('=', 5, "........" + "........" + "XXXXXXX." + "........" + "........" + "XXXXXXX." + "........" + "........");
        put('*', 5, "........" + "X..X..X." + ".X.X.X.." + "..XXX..." + ".X.X.X.." + "X..X..X." + "........" + "........");
        put('#', 5, ".X..X..." + ".X..X..." + "XXXXXXX." + ".X..X..." + "XXXXXXX." + ".X..X..." + ".X..X..." + "........");
        put('(', 3, "..X....." + ".X......" + "X......." + "X......." + "X......." + "X......." + ".X......" + "..X.....");
        put(')', 3, "X......." + ".X......" + "..X....." + "..X....." + "..X....." + "..X....." + ".X......" + "X.......");
        put('[', 3, ".XX....." + ".X......" + ".X......" + ".X......" + ".X......" + ".X......" + ".X......" + ".XX.....");
        put(']', 3, "XX......" + ".X......" + ".X......" + ".X......" + ".X......" + ".X......" + ".X......" + "XX......");
        put('<', 4, "...X...." + "..X....." + ".X......" + "X......." + "X......." + ".X......" + "..X....." + "...X....");
        put('>', 4, "X......." + ".X......" + "..X....." + "...X...." + "...X...." + "..X....." + ".X......" + "X.......");
        put('|', 1, "X......." + "X......." + "X......." + "X......." + "X......." + "X......." + "X......." + "X.......");

        // 小写字母复用大写点阵
        for (Map.Entry<Character, Glyph> e : GLYPHS.entrySet()) {
            char c = e.getKey();
            if (c >= 'A' && c <= 'Z') {
                UPPER_FALLBACK.put(Character.toLowerCase(c), e.getValue());
            }
        }
    }

    /** 字距（像素）。 */
    private final int tracking;

    public PixelFont() {
        this(1);
    }

    public PixelFont(int tracking) {
        this.tracking = tracking;
    }

    public int tracking() {
        return tracking;
    }

    private Glyph glyph(char c) {
        Glyph g = GLYPHS.get(c);
        if (g == null) {
            g = UPPER_FALLBACK.get(c);
        }
        return g;
    }

    /** 单个字符的像素宽度，未收录返回 0。 */
    public int charWidth(char c) {
        Glyph g = glyph(c);
        return g == null ? 0 : g.width() + tracking;
    }

    /** 文本像素宽度。 */
    public int measure(CharSequence text) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += charWidth(text.charAt(i));
        }
        return Math.max(0, total - tracking);
    }

    /**
     * 绘制文本，{@code x} 为左边界，{@code y} 为字形顶部。
     *
     * @return 绘制后的右边界（含一个字距）
     */
    public int draw(PixelArt canvas, CharSequence text, int x, int y, Color color) {
        int cursor = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Glyph g = glyph(c);
            if (g == null) {
                cursor += 4;
                continue;
            }
            for (int ry = 0; ry < GLYPH_HEIGHT; ry++) {
                int bits = g.rows()[ry] & 0xFF;
                if (bits == 0) {
                    continue;
                }
                for (int rx = 0; rx < g.width(); rx++) {
                    if ((bits & (1 << (7 - rx))) != 0) {
                        canvas.pixel(cursor + rx, y + ry, color);
                    }
                }
            }
            cursor += g.width() + tracking;
        }
        return cursor;
    }

    /** 居中绘制，返回左边界。 */
    public int drawCentered(PixelArt canvas, CharSequence text, int centerX, int y, Color color) {
        int w = measure(text);
        int x = centerX - w / 2;
        draw(canvas, text, x, y, color);
        return x;
    }

    /** 右对齐绘制，返回左边界。 */
    public int drawRight(PixelArt canvas, CharSequence text, int rightX, int y, Color color) {
        int x = rightX - measure(text);
        draw(canvas, text, x, y, color);
        return x;
    }

    /**
     * 截断到指定像素宽度，超长时以 {@code ~} 结尾。
     *
     * <p>给"内容长度不可控"的地方用（作战记录、舰名）：字体一改，写死的字符串长度
     * 就可能戳出面板外，与其每处手数一遍字符数，不如让它们自己截。</p>
     */
    public String ellipsize(CharSequence text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (measure(text) <= maxWidth) {
            return text.toString();
        }
        int kept = 0;
        int used = 0;
        for (int i = 0; i < text.length(); i++) {
            int w = charWidth(text.charAt(i));
            if (used + w + charWidth('~') > maxWidth) {
                break;
            }
            used += w;
            kept = i + 1;
        }
        return text.subSequence(0, kept) + "~";
    }

    /** 是否收录了该字符。 */
    public boolean supports(char c) {
        return glyph(c) != null;
    }
}
