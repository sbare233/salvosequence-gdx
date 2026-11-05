package com.hysx.salvosequence.tools;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * 极简 PNG 编码器（32 位 RGBA，无隔行）。
 *
 * <p>存在的意义：开发和验收时需要把像素界面导成图片来比对。自己写 PNG 只需要
 * JDK 自带的 {@link Deflater}，输入直接是项目通用的非预乘 ARGB {@code int[]}，
 * 不依赖任何图形工具箱，无头环境也能导图。</p>
 */
public final class PngWriter {

    private static final byte[] SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    private PngWriter() {
    }

    /** 读取 PNG 的宽高（只解析签名后的第一个 IHDR，不依赖 java.desktop）。 */
    public static int[] readSize(File file) throws IOException {
        try (java.io.DataInputStream in =
                     new java.io.DataInputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(file)))) {
            byte[] signature = new byte[8];
            in.readFully(signature);
            for (int i = 0; i < SIGNATURE.length; i++) {
                if (signature[i] != SIGNATURE[i]) {
                    throw new IOException("不是 PNG 文件: " + file);
                }
            }
            int length = in.readInt();
            in.readFully(new byte[4]); // chunk type: IHDR
            if (length < 8) {
                throw new IOException("IHDR 长度异常: " + length);
            }
            int width = in.readInt();
            int height = in.readInt();
            return new int[]{width, height};
        }
    }

    /** 把图像写出为 PNG 文件（必要时创建父目录）。 */
    public static void write(int[] argb, int width, int height, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建目录: " + parent);
        }
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            write(argb, width, height, out);
        }
    }

    /** 便捷入口：{@link com.hysx.salvosequence.core.PngLoader.Bitmap} -> PNG 文件。 */
    public static void write(com.hysx.salvosequence.core.PngLoader.Bitmap bitmap, File file)
            throws IOException {
        write(bitmap.argb(), bitmap.width(), bitmap.height(), file);
    }

    /** 把图像写出为 PNG 到任意输出流。 */
    public static void write(int[] argb, int width, int height, OutputStream out) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream(height * (width * 4 + 1));
        byte[] row = new byte[width * 4];
        for (int y = 0; y < height; y++) {
            raw.write(0); // filter type: None
            for (int x = 0; x < width; x++) {
                int px = argb[y * width + x];
                int alpha = (px >>> 24) & 0xFF;
                int red = (px >> 16) & 0xFF;
                int green = (px >> 8) & 0xFF;
                int blue = px & 0xFF;
                if (alpha == 0) {
                    // 全透明像素统一成黑色，避免与黑底合成时出现彩边
                    red = 0;
                    green = 0;
                    blue = 0;
                }
                row[x * 4] = (byte) red;
                row[x * 4 + 1] = (byte) green;
                row[x * 4 + 2] = (byte) blue;
                row[x * 4 + 3] = (byte) alpha;
            }
            raw.write(row, 0, row.length);
        }

        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw.toByteArray());
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        while (!deflater.finished()) {
            int n = deflater.deflate(buffer);
            compressed.write(buffer, 0, n);
        }
        deflater.end();

        DataOutputStream data = new DataOutputStream(out);
        data.write(SIGNATURE);

        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream headerData = new DataOutputStream(header);
        headerData.writeInt(width);
        headerData.writeInt(height);
        headerData.writeByte(8);  // bit depth
        headerData.writeByte(6);  // color type: RGBA
        headerData.writeByte(0);  // compression
        headerData.writeByte(0);  // filter
        headerData.writeByte(0);  // interlace
        writeChunk(data, "IHDR", header.toByteArray());
        writeChunk(data, "IDAT", compressed.toByteArray());
        writeChunk(data, "IEND", new byte[0]);
        data.flush();
    }

    private static void writeChunk(DataOutputStream out, String type, byte[] payload) throws IOException {
        out.writeInt(payload.length);
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.write(typeBytes);
        out.write(payload);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(payload);
        out.writeInt((int) crc.getValue());
    }
}
