package com.hysx.salvosequence.graphics;

import org.junit.jupiter.api.Test;

/**
 * 把精灵帧按字符打印出来，用来肉眼核对点阵。
 *
 * <p>这些帧是 16x16、几十字的字符网格，直接读代码里的字符串很容易看错行
 * （本项目已经因为"错行"吃过一次亏）。这个测试会把目标帧打印成 n=0 可见的方块图，
 * 需要时把 {@link #SHOW} 改成要看的角色再跑：</p>
 *
 * <pre>mvn -o test -Dtest=SpriteDumpTest</pre>
 *
 * <p>默认只打印玩家机第 0 帧，避免刷屏。</p>
 */
class SpriteDumpTest {

    /** 要打印的角色；null = 打印全部角色的第 0 帧。 */
    private static final String SHOW = SpriteFactory.PLAYER_FIGHTER;

    @Test
    void 打印点阵() {
        // 先看素材库自己怎么报账：谁用了 PNG、谁退化成占位
        System.out.println(SpriteStore.get().report());
        System.out.println("资源根 = " + SpriteStore.class.getResource(SpriteStore.ROOT));
        System.out.println("玩家机精确名命中 = "
                + SpriteStore.class.getResource(SpriteStore.ROOT + SpriteFactory.PLAYER_FIGHTER + ".png"));
        String[] roles = SHOW == null
                ? SpriteFactory.ALL_ROLES.toArray(new String[0])
                : new String[]{SHOW};
        for (String role : roles) {
            SpriteSheet sheet = SpriteStore.get().sheet(role);
            System.out.println("=== " + role + "  (" + sheet.frameCount() + "F "
                    + sheet.frameWidth() + "x" + sheet.frameHeight()
                    + (sheet.isPlaceholder() ? " 程序化" : " PNG") + ") ===");
            for (int f = 0; f < sheet.frameCount(); f++) {
                System.out.println("--- frame " + f + " ---");
                System.out.println(ascii(sheet.frame(f)));
            }
        }
    }

    private static String ascii(Sprite sprite) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < sprite.height(); y++) {
            for (int x = 0; x < sprite.width(); x++) {
                int argb = sprite.argbAt(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) {
                    sb.append('.');
                    continue;
                }
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                sb.append(bright(r, g, b));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 按亮度和色相粗略分档，只求肉眼能看出结构。 */
    private static char bright(int r, int g, int b) {
        int lum = (r * 30 + g * 59 + b * 11) / 100;
        if (lum > 200) {
            return '#';      // 最亮：描线
        }
        if (lum > 120) {
            return '+';      // 中间调：机体
        }
        if (lum > 60) {
            return ':';      // 暗部
        }
        return ',';          // 很暗
    }
}
