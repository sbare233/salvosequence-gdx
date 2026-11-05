package com.hysx.salvosequence.screen;

import com.hysx.salvosequence.audio.Sound;
import com.hysx.salvosequence.audio.SoundId;
import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelFont;
import com.hysx.salvosequence.graphics.SpriteFactory;
import com.hysx.salvosequence.graphics.SpriteSheet;
import com.hysx.salvosequence.ui.Language;

import java.util.ArrayList;
import java.util.List;

/**
 * 像素图管理界面（精灵实验室）。
 *
 * <p>游戏里按 <b>F4</b> 随时翻出来，用来核对素材链路：每行一个角色，
 * 左边是名字、帧数和单帧尺寸，右边是整段动画连排并在播放帧上打框；
 * 下半屏把同一批帧放大，用来看描线细节。</p>
 *
 * <p>存在的意义：现在素材是程序化生成的占位图，将来换成手绘 PNG 时，
 * 需要有地方能一眼看出"读到的帧数对不对、单帧尺寸对不对、动画速度对不对"。
 * 读错了在这一屏会立刻表现为残帧、错位或者不动。</p>
 */
public final class SpriteLabScreen implements ToggleScreen {

    /** 显示顺序。 */
    private static final String[] ROLES = {
            SpriteFactory.PLAYER_FIGHTER,
            SpriteFactory.FOE_FIGHTER,
            SpriteFactory.FOE_WEDGE,
            SpriteFactory.FX_EXPLOSION,
            SpriteFactory.PROJECTILE_PLAYER_BOLT,
            SpriteFactory.PROJECTILE_FOE_BOLT,
            SpriteFactory.PROJECTILE_MISSILE,
    };

    /** 每个角色在演示里用的播放速度（帧/秒）。 */
    private static double demoFps(String role) {
        return switch (role) {
            case SpriteFactory.PLAYER_FIGHTER -> 12.0;
            case SpriteFactory.FX_EXPLOSION -> 14.0;
            case SpriteFactory.PROJECTILE_PLAYER_BOLT, SpriteFactory.PROJECTILE_FOE_BOLT -> 8.0;
            default -> 6.0;
        };
    }

    private static final int NAME_SIZE = 12;
    private static final int CAPTION_SIZE = 12;
    private static final int ROW_Y0 = 40;
    /** 行的最小高度；更高的精灵图会把行撑高，避免帧串到下一行上去。 */
    private static final int ROW_H_MIN = 30;
    /** 行高 = 帧高 + 这段余量（给进度条和中文字留位置）。 */
    private static final int ROW_PAD = 12;
    private static final int LABEL_X = 18;
    private static final int STRIP_X = 108;
    private static final int ROW_RIGHT = 628;

    private final PixelFont font = new PixelFont();

    /** 每行状态。 */
    private static final class Row {
        final String role;
        final SpriteSheet sheet;
        final double fps;
        /** 本行占的高度：由帧高决定，见 ROW_PAD。 */
        final int height;
        int frame;
        double clock;

        Row(String role, SpriteSheet sheet) {
            this.role = role;
            this.sheet = sheet;
            this.fps = demoFps(role);
            this.height = Math.max(ROW_H_MIN, sheet.frameHeight() + ROW_PAD);
        }

        void step(double dt) {
            if (sheet.frameCount() <= 1) {
                return;
            }
            clock += dt * fps;
            while (clock >= 1.0) {
                clock -= 1.0;
                frame = (frame + 1) % sheet.frameCount();
            }
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private int cursor;
    private boolean paused;

    public SpriteLabScreen() {
        for (String role : ROLES) {
            SpriteSheet sheet = SpriteFactory.create(role);
            if (sheet != null) {
                rows.add(new Row(role, sheet));
            }
        }
    }

    @Override
    public String name() {
        return "SPRITE_LAB";
    }

    @Override
    public void update(double dt) {
        if (paused) {
            return;
        }
        for (Row row : rows) {
            row.step(dt);
        }
    }

    @Override
    public void draw(CanvasBuffer buffer) {
        drawHeader(buffer);

        // 行高按帧高动态算，先累计总高，再把整块列表垂直居中
        int listHeight = 0;
        for (Row row : rows) {
            listHeight += row.height;
        }
        int y = Math.max(ROW_Y0, ROW_Y0 + (buffer.height() - ROW_Y0 - listHeight) / 2);

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            drawRow(buffer, row, y, i == cursor);
            y += row.height;
        }

        drawFooter(buffer);
    }

    private void drawHeader(CanvasBuffer buffer) {
        int width = buffer.width();
        buffer.art().fillRect(0, 0, width, 30, Palette.SPACE_DEEP);
        buffer.art().hline(0, width - 1, 29, Palette.LINE_DIM);
        if (Language.isEnglish()) {
            font.draw(buffer.art(), "SPRITE LAB", 12, 7, Palette.CYAN);
            font.draw(buffer.art(), "PIXEL SHEETS / ASSET CHECK", 100, 9, Palette.CYAN_DIM);
        } else {
            buffer.text("精灵实验室", 12, 17, NAME_SIZE, Palette.CYAN);
            buffer.text("像素图管理 / 素材核对", 96, 17, NAME_SIZE, Palette.CYAN);
        }

        String right = Language.t(rows.size() + " 个角色", rows.size() + " ROLES");
        if (Language.isEnglish()) {
            font.drawRight(buffer.art(), right, width - 12, 7, Palette.LINE_FAINT);
        } else {
            buffer.text(right, width - 12 - textWidth(right, 10), 17, 10, Palette.LINE_FAINT);
        }
        if (paused) {
            String pausedText = Language.t("已暂停", "PAUSED");
            if (Language.isEnglish()) {
                font.drawRight(buffer.art(), pausedText, width - 12, 17, Palette.WARN);
            } else {
                buffer.text(pausedText, width - 12 - textWidth(pausedText, 12), 27, 12,
                        Palette.WARN);
            }
        }
    }

    private void drawRow(CanvasBuffer buffer, Row row, int y, boolean active) {
        int fh = row.sheet.frameHeight();
        // 精灵帧在行内垂直居中；行高已经按帧高撑开，不会串到下一行
        int spriteY = y + Math.max(1, (row.height - fh - 2) / 2);

        // 左列：角色名 + 帧数/尺寸
        // 中文走覆盖层，字号按设计像素给；基线压在帧下沿附近，免得和英文名打架
        if (Language.isEnglish()) {
            font.draw(buffer.art(), englishName(row.role), LABEL_X, spriteY + 4, Palette.LINE_FAINT);
        } else {
            buffer.text(shortName(row.role), LABEL_X, spriteY + fh + 2, NAME_SIZE,
                    active ? Palette.LINE : Palette.LINE_DIM);
        }
        String meta = Language.t(
                row.sheet.frameCount() + "帧 " + row.sheet.frameWidth() + "×" + fh
                        + (row.sheet.isPlaceholder() ? " 占位" : " 绘制"),
                row.sheet.frameCount() + "F " + row.sheet.frameWidth()
                        + "x" + fh + (row.sheet.isPlaceholder() ? " PH" : " RG"));
        String source = row.sheet.source() == null
                ? Language.t("程序化", "PROCEDURAL")
                : row.sheet.source();
        buffer.text(meta + "  " + source, LABEL_X, spriteY + 12, 10, Palette.LINE_FAINT);

        // 右列：整段动画逐帧横排
        int x = STRIP_X;
        for (int f = 0; f < row.sheet.frameCount(); f++) {
            int fw = row.sheet.frameWidth();
            int cellW = fw + 2;
            if (x + cellW > ROW_RIGHT) {
                font.draw(buffer.art(), "..", x, spriteY + fh / 2, Palette.LINE_FAINT);
                break;
            }
            boolean now = f == row.frame;
            buffer.art().frame(x, spriteY, cellW, fh + 2,
                    now ? Palette.CYAN : Palette.PANEL_EDGE);
            row.sheet.frame(f).draw(buffer.art(), x + 1, spriteY + 1);
            x += cellW + 3;
        }

        // 行底进度条：播放头走到哪一帧
        int barY = y + row.height - 4;
        buffer.art().fillRect(STRIP_X, barY, ROW_RIGHT - STRIP_X, 2, Palette.PANEL_ALT);
        if (!row.sheet.frames().isEmpty()) {
            int span = ROW_RIGHT - STRIP_X;
            double progress = (row.frame + row.clock) / Math.max(1, row.sheet.frameCount());
            int fill = (int) Math.round(span * Math.min(1.0, progress));
            buffer.art().fillRect(STRIP_X, barY, Math.max(1, fill), 2,
                    active ? Palette.CYAN : Palette.CYAN_DIM);
        }
    }

    private void drawFooter(CanvasBuffer buffer) {
        String hint = Language.t(
                "上下 选角色     左右 换帧     空格 暂停     F4 / ESC 返回",
                "UP/DOWN ROLE     LEFT/RIGHT FRAME     SPACE PAUSE     F4 / ESC BACK");
        buffer.text(hint, LABEL_X, buffer.height() - 8, CAPTION_SIZE, Palette.LINE_FAINT);
    }

    /** 估算字宽：中文 1em，ASCII 0.56em。 */
    private static double textWidth(String text, double size) {
        double w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += text.charAt(i) < 0x2E80 ? size * 0.56 : size;
        }
        return w;
    }

    private static String shortName(String role) {
        return switch (role) {
            case SpriteFactory.PLAYER_FIGHTER -> "我方战机";
            case SpriteFactory.FOE_FIGHTER -> "敌僚机";
            case SpriteFactory.FOE_WEDGE -> "敌楔形舰";
            case SpriteFactory.FX_EXPLOSION -> "爆炸";
            case SpriteFactory.PROJECTILE_PLAYER_BOLT -> "我方光弹";
            case SpriteFactory.PROJECTILE_FOE_BOLT -> "敌方光弹";
            case SpriteFactory.PROJECTILE_MISSILE -> "导弹";
            default -> role;
        };
    }

    private static String englishName(String role) {
        return switch (role) {
            case SpriteFactory.PLAYER_FIGHTER -> "FIGHTER";
            case SpriteFactory.FOE_FIGHTER -> "TARGET";
            case SpriteFactory.FOE_WEDGE -> "WEDGE";
            case SpriteFactory.FX_EXPLOSION -> "EXPLOSION";
            case SpriteFactory.PROJECTILE_PLAYER_BOLT -> "BOLT";
            case SpriteFactory.PROJECTILE_FOE_BOLT -> "FOE BOLT";
            case SpriteFactory.PROJECTILE_MISSILE -> "MISSILE";
            default -> "?";
        };
    }

    @Override
    public ScreenContext onKey(String code) {
        if (rows.isEmpty()) {
            return "ESCAPE".equals(code) ? ScreenContext.go(ScreenContext.Target.START) : null;
        }
        switch (code) {
            case "UP" -> {
                cursor = Math.floorMod(cursor - 1, rows.size());
                Sound.play(SoundId.UI_MOVE);
            }
            case "DOWN" -> {
                cursor = Math.floorMod(cursor + 1, rows.size());
                Sound.play(SoundId.UI_MOVE);
            }
            case "LEFT", "RIGHT" -> {
                Row row = rows.get(cursor);
                int dir = "RIGHT".equals(code) ? 1 : -1;
                row.frame = Math.floorMod(row.frame + dir, Math.max(1, row.sheet.frameCount()));
                Sound.play(SoundId.UI_MOVE);
            }
            case "SPACE" -> {
                paused = !paused;
                Sound.play(SoundId.UI_CLICK);
            }
            default -> {
                // 其它键交给 ScreenManager
            }
        }
        return null;
    }
}
