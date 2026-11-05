package com.hysx.salvosequence.graphics;

/**
 * 把 {@link SpriteSheet} 的若干帧横着拼回一张大图。
 *
 * <p>和 {@link SpriteStore} 的拆帧是一对反向操作，用途：
 * 素材导出（程序化占位图 -> PNG 文件）、外部精灵表读进来再原样写回、
 * 离屏核对的对照图。</p>
 *
 * <p>布局只有一种：帧从左到右排列，所以
 * 宽 = 帧数 &times; 单帧宽，高 = 单帧高 —— 这正是 {@link SpriteStore}
 * 约定里的"横向拼接"。</p>
 */
public final class SpriteSheetPacker {

    private SpriteSheetPacker() {
    }

    /**
     * 拼成一张 ARGB 数组图。
     *
     * @param sheet 精灵表
     * @return 宽 = frameCount*frameWidth、高 = frameHeight 的新图
     */
    public static com.hysx.salvosequence.core.PngLoader.Bitmap pack(SpriteSheet sheet) {
        int fw = sheet.frameWidth();
        int fh = sheet.frameHeight();
        int stripW = fw * sheet.frameCount();
        int[] out = new int[stripW * fh];
        for (int f = 0; f < sheet.frameCount(); f++) {
            int[] src = sheet.frame(f).pixels();
            int xOffset = f * fw;
            for (int y = 0; y < fh; y++) {
                System.arraycopy(src, y * fw, out, y * stripW + xOffset, fw);
            }
        }
        return new com.hysx.salvosequence.core.PngLoader.Bitmap(out, stripW, fh);
    }

    /**
     * 按命名约定给出建议文件名。
     *
     * <p>例如角色 {@code fx/explosion} 加 6 帧 16x16，得到
     * {@code fx/explosion_f6_16x16.png}。</p>
     */
    public static String suggestedFileName(SpriteSheet sheet) {
        return sheet.role() + "_f" + sheet.frameCount()
                + "_" + sheet.frameWidth() + "x" + sheet.frameHeight() + ".png";
    }

    /** 单帧时不需要帧数标记，给一个更干净的名字。 */
    public static String suggestedFileNameForStill(String role, int width, int height) {
        return role + "_" + width + "x" + height + ".png";
    }
}
