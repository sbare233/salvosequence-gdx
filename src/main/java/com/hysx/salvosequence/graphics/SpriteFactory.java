package com.hysx.salvosequence.graphics;

import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PaletteSprite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 角色素材的<b>程序化占位图</b>。
 *
 * <p>用途有二：</p>
 * <ol>
 *   <li>{@link SpriteStore} 在类路径上找不到 PNG 时的兜底 —— 游戏永远不会因为
 *       "美术还没画"而崩，而是画一张可辨认的占位图。</li>
 *   <li>{@link com.hysx.salvosequence.tools.SpriteExport} 用它导出真正的 PNG 到
 *       {@code src/main/resources/sprites/}，之后换成手绘素材即可。</li>
 * </ol>
 *
 * <p>配色全部取自 {@link Palette}，所以占位图和现有矢量绘制风格一致。
 * 字符画的含义见 {@link PaletteSprite#palette(Object...)} 的键。</p>
 *
 * <h2>命名约定（和 SpriteStore 一致）</h2>
 * <pre>
 * sprites/&lt;角色&gt;.png                     静态图
 * sprites/&lt;角色&gt;_f&lt;帧数&gt;_&lt;宽&gt;x&lt;高&gt;.png    横向排列的序列帧
 * </pre>
 * 例如 {@code sprites/player/player_fighter_f4_16x16.png} 表示
 * 「4 帧、每帧 16x16、横向排布」。{@code _noframes} 结尾的图会被解析器忽略。
 */
public final class SpriteFactory {

    /** 玩家机 / 敌机的统一帧尺寸。 */
    public static final int FIGHTER_W = 16;
    public static final int FIGHTER_H = 16;

    /** 角色名（= 类路径资源名，不含扩展名）。 */
    public static final String PLAYER_FIGHTER = "player/player_fighter";
    public static final String FOE_FIGHTER = "foe/foe_fighter";
    public static final String FOE_WEDGE = "foe/foe_wedge";
    public static final String FX_EXPLOSION = "fx/explosion";
    public static final String PROJECTILE_PLAYER_BOLT = "projectile/player_bolt";
    public static final String PROJECTILE_FOE_BOLT = "projectile/foe_bolt";
    public static final String PROJECTILE_MISSILE = "projectile/missile";

    /** 所有能程序化生成的角色，导出工具按这个顺序跑。 */
    public static final List<String> ALL_ROLES = List.of(
            PLAYER_FIGHTER, FOE_FIGHTER, FOE_WEDGE,
            FX_EXPLOSION, PROJECTILE_PLAYER_BOLT, PROJECTILE_FOE_BOLT, PROJECTILE_MISSILE);

    private SpriteFactory() {
    }

    /**
     * 按角色名生成占位精灵表。
     *
     * @param role 角色名（见本类的常量）
     * @return 精灵表；角色名不认识时返回 null
     */
    public static SpriteSheet create(String role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case PLAYER_FIGHTER -> playerFighter();
            case FOE_FIGHTER -> foeFighter();
            case FOE_WEDGE -> foeWedge();
            case FX_EXPLOSION -> explosion();
            case PROJECTILE_PLAYER_BOLT -> projectile(role, 6, 2, '=', 'o');
            case PROJECTILE_FOE_BOLT -> projectile(role, 6, 2, '-', '*');
            case PROJECTILE_MISSILE -> missile();
            default -> null;
        };
    }

    // -------------------------------------------------------------- 机体

    /** 玩家机：机首朝右，尾焰 2 帧循环。 */
    private static SpriteSheet playerFighter() {
        String frame0 = String.join("\n",
                ".............#..",
                "............##..",
                "...........###..",
                "..........####..",
                ".....##..#####..",
                "......##########",
                "......##########",
                "......##########",
                "......##########",
                "......##########",
                ".....##..#####..",
                "..........####..",
                "...........###..",
                "............##..",
                ".............#..",
                "................");

        Map<Character, com.hysx.salvosequence.core.Color> palette = PaletteSprite.palette(
                '.', null,
                '#', Palette.SHIP_HULL,
                '=', Palette.SHIP_SHADE,
                'c', Palette.SHIP_GLOW,
                '1', Palette.SHIP_GLOW,
                '2', com.hysx.salvosequence.core.PixelArt.shade(Palette.SHIP_GLOW, 0.62),
                '3', com.hysx.salvosequence.core.PixelArt.shade(Palette.SHIP_GLOW, 0.34),
                '4', com.hysx.salvosequence.core.PixelArt.shade(Palette.SHIP_GLOW, 0.16));

        List<Sprite> frames = new ArrayList<>();
        frames.add(toSprite("player/player_fighter#0", frame0, palette));
        frames.add(toSprite("player/player_fighter#1", flame(frame0, "1"), palette));
        frames.add(toSprite("player/player_fighter#2", flame(frame0, "2"), palette));
        frames.add(toSprite("player/player_fighter#3", flame(frame0, "3"), palette));
        return new SpriteSheet(PLAYER_FIGHTER, frames, null, true);
    }

    /**
     * 把机体网格尾部换成尾焰形态。
     *
     * <p>机体本身不动，只改尾部 —— 这样 4 帧循环看起来像引擎在抖。
     * 尾焰占用第 11~14 列、第 4~11 行，也就是机体尾部那截方括号形状。</p>
     *
     * @param grid  机体网格（必须至少 15 列宽，否则这里会直接抛错而不是悄悄画歪）
     * @param shape 尾焰形态序号（'1' 最长 .. '4' 最短）
     */
    private static String flame(String grid, String shape) {
        String[] rows = grid.split("\n");
        String[] tail = switch (shape) {
            case "1" -> new String[]{"444.", "444.", "44..", "4...", "4...", "44..", "444.", "444."};
            case "2" -> new String[]{"44..", "444.", "44..", "4...", "4...", "44..", "444.", "44.."};
            case "3" -> new String[]{"44..", "44..", "4...", "....", "....", "4...", "44..", "44.."};
            default -> new String[]{"4...", "4...", "....", "....", "....", "....", "4...", "4..."};
        };
        requireGrid(rows, tail.length + 4, 15, "flame/" + shape);
        // 尾焰每段必须是 4 列，少一个点就会在 setCharAt 里越界
        for (int i = 0; i < tail.length; i++) {
            if (tail[i].length() != 4) {
                throw new IllegalStateException("尾焰形态 " + shape + " 第 " + (i + 1)
                        + " 段宽度是 " + tail[i].length() + "，必须是 4");
            }
        }

        for (int i = 0; i < tail.length; i++) {
            int row = 4 + i;
            StringBuilder sb = new StringBuilder(rows[row]);
            for (int c = 0; c < 4; c++) {
                sb.setCharAt(11 + c, tail[i].charAt(c));
            }
            rows[row] = sb.toString();
        }
        return String.join("\n", rows);
    }

    /**
     * 网格自检：行数够不够、每行是不是一样宽、够不够宽。
     *
     * <p>字符画最容易出的错就是某一行少打一个点，画出来只是轻微错位，
     * 肉眼很难发现。这里宁可当场抛错，也不要画出一张歪的图。</p>
     */
    private static void requireGrid(String[] rows, int minRows, int minWidth, String what) {
        if (rows.length < minRows) {
            throw new IllegalStateException("精灵网格 " + what + " 只有 " + rows.length
                    + " 行，至少需要 " + minRows + " 行");
        }
        for (int i = 0; i < rows.length; i++) {
            if (rows[i].length() < minWidth) {
                throw new IllegalStateException("精灵网格 " + what + " 第 " + (i + 1)
                        + " 行只有 " + rows[i].length() + " 列，至少需要 " + minWidth + " 列");
            }
            if (rows[i].length() != rows[0].length()) {
                throw new IllegalStateException("精灵网格 " + what + " 第 " + (i + 1)
                        + " 行宽度 " + rows[i].length() + " 与首行 " + rows[0].length() + " 不一致");
            }
        }
    }

    /** 敌长机：机首朝左，红色系。 */
    private static SpriteSheet foeFighter() {
        String frame = String.join("\n",
                "..#.............",
                ".##.............",
                "###.............",
                "####............",
                ".#####..##......",
                "##########......",
                "###########.....",
                "###########.....",
                "###########.....",
                "##########......",
                ".#####..##......",
                "####............",
                "###.............",
                "##..............",
                ".#..............",
                "................");
        Map<Character, com.hysx.salvosequence.core.Color> palette = PaletteSprite.palette(
                '.', null,
                '#', Palette.FOE_HULL,
                '=', Palette.FOE_SHADE,
                'r', Palette.FOE_GLOW);
        return new SpriteSheet(FOE_FIGHTER,
                List.of(toSprite("foe/foe_fighter#0", frame, palette)), null, true);
    }

    /** 楔形僚机：短粗的三角舰首，比长机小一圈。 */
    private static SpriteSheet foeWedge() {
        String frame = String.join("\n",
                "................",
                "................",
                "................",
                ".....##.........",
                "...####.........",
                "..######........",
                ".########.......",
                "##########......",
                "##########......",
                ".########.......",
                "..######........",
                "...####.........",
                ".....##.........",
                "................",
                "................",
                "................");
        Map<Character, com.hysx.salvosequence.core.Color> palette = PaletteSprite.palette(
                '.', null,
                '#', Palette.FOE_HULL,
                '=', Palette.FOE_SHADE);
        return new SpriteSheet(FOE_WEDGE,
                List.of(toSprite("foe/foe_wedge#0", frame, palette)), null, true);
    }

    // ------------------------------------------------------------ 爆炸

    /**
     * 爆炸：6 帧膨胀再收缩的火球。
     *
     * <p>用椭圆同心层画出「外壳 -> 中层 -> 核心」，再叠一圈冲击波。
     * 手点 96 个像素不如画同心圆来得稳。</p>
     */
    private static SpriteSheet explosion() {
        int size = 16;
        int cx = 8;
        int cy = 8;
        int[] radii = {2, 4, 6, 7, 6, 4};
        List<Sprite> frames = new ArrayList<>();
        for (int i = 0; i < radii.length; i++) {
            int r = radii[i];
            com.hysx.salvosequence.core.PixelArt art =
                    new com.hysx.salvosequence.core.PixelArt(size, size);
            // 外层：暗红
            art.ellipse(cx, cy, r, r, Palette.FOE_DEEP);
            // 中层：红橙
            art.ellipse(cx, cy, Math.max(1, r - 1), Math.max(1, r - 1), Palette.FOE_SHADE);
            // 核心：亮黄白
            art.ellipse(cx, cy, Math.max(0, r - 3), Math.max(0, r - 3), Palette.WARN);
            // 最内圈用告警红点缀，避免核心过曝成一坨
            if (r >= 5) {
                art.ellipse(cx, cy, r - 2, r - 2, Palette.DANGER);
                art.ellipse(cx, cy, Math.max(0, r - 3), Math.max(0, r - 3), Palette.WARN);
            }
            // 冲击波：偶数帧在火球外一圈画细边
            if (i % 2 == 1) {
                art.ellipse(cx, cy, r + 1, r + 1, com.hysx.salvosequence.core.PixelArt
                        .shade(Palette.DANGER, 0.55));
                art.ellipse(cx, cy, Math.max(1, r - 2), Math.max(1, r - 2), Palette.WARN);
                art.ellipse(cx, cy, Math.max(0, r - 3), Math.max(0, r - 3), Palette.WARN);
            }
            frames.add(toSprite("fx/explosion#" + i, art));
        }
        return new SpriteSheet(FX_EXPLOSION, frames, null, true);
    }

    // ---------------------------------------------------------- 弹药

    /**
     * 光弹：一段横置的短横线，最右一像素是更亮的弹头。
     *
     * @param role 角色名
     * @param w    宽
     * @param h    高（2 像素：上排描线 + 下排高光，看起来像有厚度）
     * @param tail 弹体字符
     * @param head 弹头字符
     */
    private static SpriteSheet projectile(String role, int w, int h, char tail, char head) {
        String grid = String.valueOf(tail).repeat(w - 1) + head;
        Map<Character, com.hysx.salvosequence.core.Color> palette = PaletteSprite.palette(
                '.', null,
                tail, Palette.CYAN_DIM,
                head, Palette.LINE);
        return new SpriteSheet(role,
                List.of(toSprite(role + "#0", grid, palette)), null, true);
    }

    /** 导弹：带弹翼的小弹体，尾部有喷口亮点。 */
    private static SpriteSheet missile() {
        String grid = String.join("\n",
                "........",
                "..#####.",
                ".#######",
                "########",
                "########",
                ".#######",
                "..#####.",
                "........");
        Map<Character, com.hysx.salvosequence.core.Color> palette = PaletteSprite.palette(
                '.', null,
                '#', Palette.LINE_DIM,
                '=', Palette.SHIP_DEEP);
        return new SpriteSheet(PROJECTILE_MISSILE,
                List.of(toSprite("projectile/missile#0", grid, palette)), null, true);
    }

    // ------------------------------------------------------------ 工具

    private static Sprite toSprite(String name, String grid,
                                   Map<Character, com.hysx.salvosequence.core.Color> palette) {
        PaletteSprite ps = new PaletteSprite(grid.split("\n"), palette);
        return toSprite(name, ps);
    }

    private static Sprite toSprite(String name, com.hysx.salvosequence.core.PixelArt art) {
        int[] argb = new int[art.width() * art.height()];
        for (int y = 0; y < art.height(); y++) {
            for (int x = 0; x < art.width(); x++) {
                com.hysx.salvosequence.core.Color c = art.pixelAt(x, y);
                argb[y * art.width() + x] = c == null ? 0 : com.hysx.salvosequence.core.PixelArt.toArgb(c);
            }
        }
        return new Sprite(name, art.width(), art.height(), argb);
    }

    private static Sprite toSprite(String name, PaletteSprite ps) {
        int[] argb = new int[ps.width() * ps.height()];
        for (int y = 0; y < ps.height(); y++) {
            for (int x = 0; x < ps.width(); x++) {
                com.hysx.salvosequence.core.Color c = ps.colorAt(x, y);
                argb[y * ps.width() + x] = c == null ? 0 : com.hysx.salvosequence.core.PixelArt.toArgb(c);
            }
        }
        return new Sprite(name, ps.width(), ps.height(), argb);
    }
}
