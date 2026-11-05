package com.hysx.salvosequence.core;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 桌面端的图片编解码：ImageIO 读、Graphics2D 双线性缩、ImageIO 写。
 *
 * <p>口径同原 JavaFX 版的 {@code Image(file, 1600, 900, preserveRatio, smooth)}：
 * 等比缩进框里、双线性平滑。Android 构建里这个文件被排除（没有 java.awt）。</p>
 */
public final class AwtImageCodec implements ImageCodec.Backend {

    @Override
    public ImageCodec.Loaded loadScaled(File file, int maxW, int maxH) {
        try {
            BufferedImage loaded = ImageIO.read(file);
            if (loaded == null) {
                return null;
            }
            // 只缩不放（原 JavaFX 的 preserveRatio 也不放大）
            double scale = Math.min(1.0,
                    Math.min(maxW / (double) loaded.getWidth(), maxH / (double) loaded.getHeight()));
            int w = Math.max(1, (int) Math.round(loaded.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(loaded.getHeight() * scale));
            BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(loaded, 0, 0, w, h, null);
            } finally {
                g.dispose();
            }
            int[] argb = new int[w * h];
            scaled.getRGB(0, 0, w, h, argb, 0, w);
            return new ImageCodec.Loaded(argb, w, h);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean writePng(int[] argb, int width, int height, File target) {
        try {
            BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            bi.setRGB(0, 0, width, height, argb, 0, width);
            return ImageIO.write(bi, "png", target);
        } catch (Exception e) {
            return false;
        }
    }
}
