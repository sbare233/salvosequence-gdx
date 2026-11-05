package com.hysx.salvosequence.core;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 背景工坊的批量导出：拿当前参数把 {@code backdrops-src/} 里的每张图都拜耳像素化一遍，
 * 写进一个按时间戳新建的文件夹。
 *
 * <p>刻意和界面解耦（不碰 PixelArt/CanvasBuffer），这样能直接单测：
 * 目录扫描、等比缩放口径、文件命名都在这里。</p>
 */
public final class BackdropBatch {

    /** 批量结果的根目录；每次导出在它下面新建一个时间戳文件夹。 */
    public static final String ROOT = "backdrops/batch";

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private BackdropBatch() {
    }

    /** 一次批量导出的结果：文件夹 + 成功/跳过张数。 */
    public record Result(File folder, int written, int skipped) {
    }

    /** 目录里能当源图用的文件（png/jpg/jpeg，按文件名排序，和工坊里 <-> 的顺序一致）。 */
    public static File[] sources(File dir) {
        File[] found = dir.listFiles((d, n) -> {
            String lower = n.toLowerCase();
            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        });
        if (found == null) {
            return new File[0];
        }
        Arrays.sort(found);
        return found;
    }

    /**
     * 按 {@code params} 处理 {@code srcDir} 里的所有图，输出到
     * {@code root/yyyyMMdd-HHmmss/}。
     *
     * @param srcDir 源图目录（通常是 {@code backdrops-src}）
     * @param root   批量根目录（通常是 {@link #ROOT}）
     * @param params 当前预览用的参数
     * @param now    时间戳（传进来而不是取系统时间，方便测试固定文件夹名）
     */
    public static Result exportAll(File srcDir, File root, BayerPixelator.Params params,
                                   LocalDateTime now) throws java.io.IOException {
        return exportAll(srcDir, root, params, now,
                com.hysx.salvosequence.ui.VideoSettings.backdropFilter());
    }

    /**
     * 同 {@link #exportAll(File, File, BayerPixelator.Params, LocalDateTime)}，但显式指定滤镜
     * （拜耳 / 像素），这样批量导出跟工坊里当前选的滤镜一致，测试也不用去动全局设置。
     */
    public static Result exportAll(File srcDir, File root, BayerPixelator.Params params,
                                   LocalDateTime now,
                                   com.hysx.salvosequence.ui.VideoSettings.BackdropFilter filter)
            throws java.io.IOException {
        File[] files = sources(srcDir);
        File folder = new File(root, now.format(STAMP));
        if (files.length == 0) {
            return new Result(folder, 0, 0);
        }
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new java.io.IOException("建不了导出文件夹: " + folder.getPath());
        }
        List<String> used = new ArrayList<>();
        int written = 0;
        int skipped = 0;
        for (File file : files) {
            try {
                Loaded loaded = load(file);
                if (loaded == null) {
                    skipped++;
                    continue;
                }
                int[] out = BackdropFiltering.render(loaded.argb(), loaded.w(), loaded.h(),
                        params, filter);
                File target = new File(folder, uniqueName(used, baseName(file.getName()),
                        BackdropFiltering.suffix(filter, params)));
                write(target, out);
                written++;
            } catch (Exception e) {
                // 单张坏了不该拖垮整批：计数后继续
                skipped++;
                System.out.println("[backdrop-batch] 跳过 " + file.getName() + ": " + e);
            }
        }
        return new Result(folder, written, skipped);
    }

    /** 一图读进来的结果：ARGB 像素 + 缩放后尺寸。 */
    private record Loaded(int[] argb, int w, int h) {
    }

    /** 读一张源图并等比缩放进 1600x900（口径同工坊预览/导出）。 */
    private static Loaded load(File file) {
        ImageCodec.Backend codec = ImageCodec.backend();
        if (codec == null) {
            return null;
        }
        ImageCodec.Loaded img = codec.loadScaled(file, 1600, 900);
        return img == null ? null : new Loaded(img.argb(), img.width(), img.height());
    }

    private static void write(File target, int[] argb) {
        ImageCodec.Backend codec = ImageCodec.backend();
        if (codec == null || !codec.writePng(argb, BayerPixelator.W, BayerPixelator.H, target)) {
            throw new IllegalStateException("写图失败: " + target.getName());
        }
    }

    /** 文件名：{@code 原名-x{像素块}-l{色阶}.png}，重名时补 -2、-3。 */
    static String uniqueName(List<String> used, String base, BayerPixelator.Params params) {
        return uniqueName(used, base, "-x" + params.cell() + "-l" + params.levels());
    }

    /** 同上，但后缀由调用方给（像素滤镜没有像素块/色阶，用 {@code -px-s8} 这种）。 */
    static String uniqueName(List<String> used, String base, String suffix) {
        String stem = base + suffix;
        String name = stem + ".png";
        for (int n = 2; used.contains(name); n++) {
            name = stem + "-" + n + ".png";
        }
        used.add(name);
        return name;
    }

    static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : "backdrop";
    }
}
