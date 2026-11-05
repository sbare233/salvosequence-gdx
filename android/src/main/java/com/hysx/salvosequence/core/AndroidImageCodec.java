package com.hysx.salvosequence.core;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;

/**
 * Android 端的图片编解码：BitmapFactory 读、filter 缩放、Bitmap.compress 写。
 *
 * <p>同样按"等比缩进框里 + 平滑"的口径，先按 2 的幂做 inSampleSize 粗缩，
 * 再用 {@code createScaledBitmap(..., filter=true)} 精缩到目标尺寸，避免解码
 * 一张 4000px 的原图时把内存撑爆。</p>
 */
public final class AndroidImageCodec implements ImageCodec.Backend {

    @Override
    public ImageCodec.Loaded loadScaled(File file, int maxW, int maxH) {
        try {
            String path = file.getAbsolutePath();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= maxW && bounds.outHeight / (sample * 2) >= maxH) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            opts.inPremultiplied = false;
            Bitmap decoded = BitmapFactory.decodeFile(path, opts);
            if (decoded == null) {
                return null;
            }
            // 只缩不放（原 JavaFX 的 preserveRatio 也不放大）
            double scale = Math.min(1.0,
                    Math.min(maxW / (double) decoded.getWidth(), maxH / (double) decoded.getHeight()));
            int w = Math.max(1, (int) Math.round(decoded.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(decoded.getHeight() * scale));
            Bitmap scaled = (w == decoded.getWidth() && h == decoded.getHeight())
                    ? decoded
                    : Bitmap.createScaledBitmap(decoded, w, h, true);
            int[] argb = new int[w * h];
            scaled.getPixels(argb, 0, w, 0, 0, w, h);
            if (scaled != decoded) {
                scaled.recycle();
            }
            decoded.recycle();
            return new ImageCodec.Loaded(argb, w, h);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean writePng(int[] argb, int width, int height, File target) {
        try {
            Bitmap bmp = Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888);
            try (java.io.OutputStream out = new java.io.FileOutputStream(target)) {
                boolean ok = bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                return ok;
            } finally {
                bmp.recycle();
            }
        } catch (Throwable t) {
            return false;
        }
    }
}
