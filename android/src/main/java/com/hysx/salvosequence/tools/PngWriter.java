package com.hysx.salvosequence.tools;

import android.graphics.Bitmap;

import com.hysx.salvosequence.core.PngLoader;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * PNG 编码器（Android 版）：桌面版手写 Deflater 只为摆脱 AWT；Android 上直接交给
 * {@link Bitmap#compress}，输入仍是项目通用的非预乘 ARGB {@code int[]}。
 */
public final class PngWriter {

    private PngWriter() {
    }

    /** 写文件；F12 截图、PixelArt 导出都走这里。 */
    public static void write(int[] argb, int width, int height, File file) throws IOException {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            write(argb, width, height, out);
        }
    }

    /** 写 {@link PngLoader.Bitmap}。 */
    public static void write(PngLoader.Bitmap bitmap, File file) throws IOException {
        write(bitmap.argb(), bitmap.width(), bitmap.height(), file);
    }

    /** 编码进任意输出流（不关闭它）。 */
    public static void write(int[] argb, int width, int height, OutputStream out) throws IOException {
        Bitmap bitmap = Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888);
        try {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new IOException("PNG 编码失败");
            }
        } finally {
            bitmap.recycle();
        }
    }
}
