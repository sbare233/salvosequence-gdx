package com.hysx.salvosequence.core;

import com.hysx.salvosequence.core.Color;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 点阵字体与字符网格小图的单元测试。
 *
 * <p>点阵字库是"隐藏的资产"，改动一个字形的列宽或行数不会报错但会让 HUD 文字变丑，
 * 所以这里把关键不变量固定下来。</p>
 */
class PixelFontTest {

    @Test
    void glyphsHaveStandardHeightAndSaneWidth() {
        PixelFont font = new PixelFont();
        for (char c = 'A'; c <= 'Z'; c++) {
            assertTrue(font.supports(c), "缺少字形: " + c);
            // 字宽 = 真实墨迹跨度 + 字距；7 列墨迹 + 1 列字距 = 8，是这套字库的正常值。
            // 注意这个值必须 >= 墨迹跨度，否则后面的字会压到前面的字上（见下面那条测试）。
            int width = font.charWidth(c);
            assertTrue(width >= 3 && width <= 8, c + " 字宽异常: " + width);
        }
        for (char c = '0'; c <= '9'; c++) {
            assertTrue(font.supports(c), "缺少数字字形: " + c);
        }
    }

    /**
     * 回归测试：字距必须容得下整个字形的墨迹。
     *
     * <p>曾经把字形的"设计宽度"（5 列）当成前进距离，而点阵实际点亮了 7 列，
     * 于是每个字母都盖掉下一个字母的左半边。这条测试直接量墨迹，堵住这类问题。</p>
     */
    @Test
    void everyGlyphFitsInsideItsOwnAdvance() {
        PixelFont font = new PixelFont();
        PixelArt canvas = new PixelArt(256, PixelFont.GLYPH_HEIGHT);
        for (char c = 32; c < 127; c++) {
            if (!font.supports(c)) {
                continue;
            }
            canvas.clear();
            int right = font.draw(canvas, String.valueOf(c), 0, 0, Color.WHITE);
            int ink = 0;
            for (int x = 0; x < canvas.width(); x++) {
                for (int y = 0; y < PixelFont.GLYPH_HEIGHT; y++) {
                    if (canvas.pixelAt(x, y) != null) {
                        ink = Math.max(ink, x + 1);
                    }
                }
            }
            assertTrue(ink <= right,
                    "'" + c + "' 的墨迹宽 " + ink + " 超过前进距离 " + right + "，会压到下一个字");
        }
    }

    /**
     * 版面预算：这几处是"两列并排"或"写死的长句子"，点阵字一旦变宽就会互相压字。
     * 断言里保留少量余量，够改一个字的写法，但挡住"整体变宽 30%"这种回归。
     */
    @Test
    void 关键文本宽度在版面预算内() {
        PixelFont font = new PixelFont();

        // 详情框左列：起点 BOX_X+8=344，数值右对齐到 344+108-42=410
        assertTrue(font.measure("HULL") + 6 <= 46, "HULL 标签太宽");
        assertTrue(font.measure("SHLD") + 6 <= 46, "SHLD 标签太宽");
        assertTrue(font.measure("100%") <= 42, "百分比太宽");
        // 详情框右列：起点 BOX_X+144=480，框右边界 568，实测预算 88px
        assertTrue(font.measure("TWIN CANNON") <= 88, "TWIN CANNON 放不进右列");
        assertTrue(font.measure("MICRO MISSILE") <= 88, "MICRO MISSILE 放不进右列");

        // 顶栏菜单：每格 94px，标签居中，中文占用约 20px
        assertTrue(font.measure("MISSION") <= 58, "MISSION 放不进 94px 菜单格");
        // 作战记录行：起点 396，面板右边界 636
        assertTrue(font.measure("HOSTILE ECHELON DETECTED") <= 232, "作战记录行太长");
    }

    @Test
    void measureAccountsForTracking() {
        PixelFont font = new PixelFont(1);
        // 5 + 字距 1 的两倍，再减去末尾一个字距
        assertEquals(font.charWidth('A') * 2 - 1, font.measure("AA"));
        assertEquals(0, font.measure(""));
    }

    @Test
    void drawPaintsInsideRequestedBox() {
        PixelArt canvas = new PixelArt(64, 16);
        PixelFont font = new PixelFont();
        int right = font.draw(canvas, "ABC", 2, 4, Color.WHITE);

        assertTrue(right > 2);
        // 墨迹不能落在请求范围之外
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 64; x++) {
                boolean painted = canvas.pixelAt(x, y) != null;
                if (painted) {
                    assertTrue(x >= 2 && x < right, "越界像素 x=" + x);
                    assertTrue(y >= 4 && y < 4 + PixelFont.GLYPH_HEIGHT, "越界像素 y=" + y);
                }
            }
        }
    }

    @Test
    void unsupportedCharacterIsSkippedWithoutCrash() {
        PixelArt canvas = new PixelArt(32, 16);
        PixelFont font = new PixelFont();
        // 中文不在点阵字库内，应当跳过而不是抛异常（中文由 CanvasBuffer.text 交给视图渲染）
        int right = font.draw(canvas, "机A", 0, 0, Color.WHITE);
        assertTrue(right > 0);
    }

    @Test
    void paletteSpriteRejectsRaggedGrid() {
        Map<Character, Color> palette = PaletteSprite.palette('X', Color.WHITE);
        // 少打一个字符的网格必须立刻报错，否则图案会被静默改成错形状
        assertThrows(IllegalArgumentException.class,
                () -> new PaletteSprite(new String[]{"XXX", "XX"}, palette));
    }

    @Test
    void paletteSpriteMirrorAndRecolor() {
        Map<Character, Color> palette = PaletteSprite.palette('X', Color.WHITE, 'o', Color.RED);
        PaletteSprite sprite = new PaletteSprite(new String[]{"Xo.", "..X"}, palette);

        assertEquals(3, sprite.width());
        assertEquals(2, sprite.height());
        assertEquals(Color.WHITE, sprite.colorAt(0, 0));
        assertNull(sprite.colorAt(2, 0));

        PaletteSprite mirrored = sprite.mirrored();
        assertEquals(Color.WHITE, mirrored.colorAt(2, 0));
        assertEquals(Color.RED, mirrored.colorAt(1, 0));

        PaletteSprite recolored = sprite.remap(Map.of(Color.WHITE, Color.LIME));
        assertEquals(Color.LIME, recolored.colorAt(0, 0));
        assertEquals(Color.RED, recolored.colorAt(1, 0));
    }
}
