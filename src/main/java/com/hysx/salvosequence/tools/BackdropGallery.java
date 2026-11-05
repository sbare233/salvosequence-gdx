package com.hysx.salvosequence.tools;

import com.hysx.salvosequence.core.Backdrops;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** 背景主题画廊：10 主题 × 4 变体导出 PNG（target/backdrops/），另拼 4 张 10 连目检板。 */
public final class BackdropGallery {
    public static void main(String[] args) throws Exception {
        File dir = new File("target/backdrops");
        dir.mkdirs();
        BufferedImage[][] sheets = new BufferedImage[4][10];
        for (int theme = 0; theme < Backdrops.THEMES; theme++) {
            for (int variant = 0; variant < Backdrops.VARIANTS; variant++) {
                int[] layer = Backdrops.staticLayer(theme, variant, 1000L + theme * 17);
                BufferedImage bi = new BufferedImage(Backdrops.W, Backdrops.H, BufferedImage.TYPE_INT_ARGB);
                bi.setRGB(0, 0, Backdrops.W, Backdrops.H, layer, 0, Backdrops.W);
                ImageIO.write(bi, "png", new File(dir, String.format("t%02d-v%d.png", theme, variant)));
                sheets[variant][theme] = bi;
            }
        }
        // 目检板：每张 2x5 排布，半尺寸
        for (int v = 0; v < Backdrops.VARIANTS; v++) {
            BufferedImage sheet = new BufferedImage(640 * 2, 360 * 5 / 2 + 40, BufferedImage.TYPE_INT_ARGB);
            for (int t = 0; t < Backdrops.THEMES; t++) {
                int col = t / 5;
                int row = t % 5;
                java.awt.Graphics2D g = sheet.createGraphics();
                g.drawImage(sheets[v][t], col * 320, row * 200, 320, 180, null);
                g.dispose();
            }
            ImageIO.write(sheet, "png", new File(dir, "sheet-v" + v + ".png"));
        }
        System.out.println("gallery: " + (Backdrops.THEMES * Backdrops.VARIANTS) + " backgrounds + "
                + Backdrops.VARIANTS + " sheets -> " + dir.getAbsolutePath());
    }
}
