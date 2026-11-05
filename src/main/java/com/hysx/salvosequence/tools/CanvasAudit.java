package com.hysx.salvosequence.tools;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.ScreenUtils;
import com.hysx.salvosequence.audio.Music;
import com.hysx.salvosequence.audio.Sound;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.core.PixelScale;
import com.hysx.salvosequence.screen.ScreenContext;
import com.hysx.salvosequence.ui.PixelGameView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 单画布版核对工具（libGDX 版）。
 *
 * <p>架构是"整屏一张缓冲、按顺序绘制、界面可切换"，所以核对内容为：</p>
 * <ol>
 *   <li><b>每个界面</b>各导出一张设计分辨率稿件（640x360）—— 这是所有单元依次画完的结果，
 *       未经缩放，用来查布局与遮挡；</li>
 *   <li>整屏截图（物理像素，读 GL 帧缓冲）+ 块状分析：查每个设计像素是否落成干净的 N x N 方块；</li>
 *   <li>放大裁切：直接看像素结构。</li>
 * </ol>
 *
 * <p>可用系统属性：{@code audit.fullscreen}、{@code audit.scanline}、{@code audit.debug}、
 * {@code audit.delay}、{@code audit.outdir}（导出目录）、{@code audit.lab}（只导精灵实验室）、
 * {@code audit.sprites}（战斗中按 F5 切到图片素材再导，文件名带 -sprites）。</p>
 *
 * <p>命令行注意：{@code -Daudit.outdir=...} 要整段加引号（{@code "-Daudit.outdir=C:\path"}），
 * 否则 Windows 的 PowerShell 会把 {@code -Dxxx.yyy=value} 拆成 {@code -Dxxx .yyy=value}，
 * Maven 报 "Unknown lifecycle phase"。属性名里出现 {@code .out} 的写法尤其容易被吞。</p>
 */
public final class CanvasAudit {

    /** 逐帧调度队列：targetFrame 到点就执行（libGDX 逐帧回调替代 Platform.runLater 轮询）。 */
    private final List<long[]> schedule = new ArrayList<>();   // {targetFrame, actionIndex}
    private final List<Runnable> actions = new ArrayList<>();

    private PixelGameView view;
    private File outDir;
    private boolean layoutReport;
    private boolean started;

    public void start() {
        // 注意：属性名不要用 audit.out —— Windows 的 PowerShell 会把形如
        // -Dxxx.out=value 的参数吞成 ".out=value"，Maven 直接报"未知生命周期阶段"。
        // 换成 audit.outdir 之后，命令行给值就正常生效了。
        outDir = new File(System.getProperty("audit.outdir", "target"));
        boolean fullscreen = Boolean.parseBoolean(System.getProperty("audit.fullscreen", "true"));
        double delay = Double.parseDouble(System.getProperty("audit.delay", "0.5"));

        view = new PixelGameView();
        // 核对稿件要的是"内容结构"，横纹是后处理，默认关掉
        view.screens().setScanlineEnabled(
                Boolean.parseBoolean(System.getProperty("audit.scanline", "false")));
        view.screens().setDebugVisible(
                Boolean.parseBoolean(System.getProperty("audit.debug", "false")));

        // 字体宽度表：改字库/字距之后先看这张表，比对着图数像素靠谱
        if (Boolean.parseBoolean(System.getProperty("audit.font", "false"))) {
            printFontReport();
        }
        // 版面量测：打印几行关键文字的墨迹区间（开关用环境变量传，
        // 因为命令行不会把 -Daudit.xxx 透给被拉起的 JVM）
        layoutReport = Boolean.parseBoolean(System.getenv("AUDIT_LAYOUT"));
        if (layoutReport) {
            printFontReport();
        }

        view.frameListener = (frame, v) -> {
            if (!started) {
                started = true;
                long warmup = Math.max(4, (long) Math.rint(delay * 60));
                at(frame + warmup, () -> runScript(v));
                return;
            }
            for (int i = 0; i < schedule.size(); i++) {
                if (frame >= schedule.get(i)[0]) {
                    Runnable action = actions.get((int) schedule.get(i)[1]);
                    schedule.remove(i);
                    action.run();
                    return;
                }
            }
        };

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("salvosequence - canvas audit");
        if (fullscreen) {
            config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        } else {
            config.setWindowedMode(1920, 1080);
        }
        config.setInitialVisible(false);
        new Lwjgl3Application(view, config);
    }

    private void at(long targetFrame, Runnable action) {
        schedule.add(new long[]{targetFrame, actions.size()});
        actions.add(action);
    }

    private void runScript(PixelGameView view) {
        try {
            // 只核对精灵实验室时，进来先按 F4 翻到那一屏
            boolean labOnly = Boolean.parseBoolean(System.getProperty("audit.lab", "false"));
            if (labOnly) {
                view.screens().handleKey("F4");
                view.screens().finishTransition();
                at(view.frameCount() + 12, () -> {
                    try {
                        exportDesign(view, outDir);
                        System.out.println("[audit] 已导出界面: "
                                + view.screens().currentName().toLowerCase());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        System.exit(2);
                    }
                    quit();
                });
                return;
            }

            // 1) 当前界面（默认开始界面）
            exportDesign(view, outDir);
            String startName = view.screens().currentName().toLowerCase();

            // 2) 切到卡牌界面再导出一张，确认两个界面都画得出来
            view.go(ScreenContext.Target.CARD);
            view.screens().finishTransition();
            at(view.frameCount() + 10, () -> {
                try {
                    exportDesign(view, outDir);

                    // 3) 覆盖式界面：按 F4 翻出精灵实验室
                    view.screens().handleKey("F4");
                    view.screens().finishTransition();
                    at(view.frameCount() + 12, () -> {
                        try {
                            exportDesign(view, outDir);
                            afterBothScreens(view, outDir, layoutReport);
                            System.out.println("[audit] 已导出界面: " + startName
                                    + ", cardloop, "
                                    + view.screens().currentName().toLowerCase());
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            System.exit(2);
                        }
                        quit();
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    System.exit(2);
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(2);
        }
    }

    /**
     * 收工并<b>真的退出去</b>。
     *
     * <p>只让 libGDX 停下不够：{@code Music} 的 MIDI 合成器与 {@code Mixer} 的混音线程
     * 都是非守护线程，只要还有一条活着，JVM 就不退出。表现是「窗口没了、音乐还在响、
     * 进程挂在后台」。所以收工时按顺序关掉两条音频链路，再用 {@code System.exit(0)} 兜底。</p>
     */
    private static void quit() {
        try {
            Music.shutdown();
        } catch (Throwable ignored) {
            // 退出路径上不再抛
        }
        try {
            Sound.shutdown();
        } catch (Throwable ignored) {
            // 同上
        }
        System.exit(0);
    }

    /**
     * 导出当前界面的设计分辨率稿件（文件名带界面名）。
     */
    private static void exportDesign(PixelGameView view, File outDir) throws Exception {
        String name = view.screens().currentName().toLowerCase();
        // 开着图片素材模式时，战斗稿件的文件名加后缀，避免和几何版互相覆盖
        if (Boolean.parseBoolean(System.getProperty("audit.sprites", "false"))
                && "battle".equals(name)) {
            name = name + "-sprites";
        }
        File file = new File(outDir, "audit-" + name + ".png");
        PngWriter.write(view.buffer().art().buffer(),
                view.buffer().width(), view.buffer().height(), file);
        System.out.println(String.format("[audit] 界面 %-7s 稿件 %dx%d -> %s",
                name, view.buffer().width(), view.buffer().height(), file.getAbsolutePath()));
    }

    /** 整屏截图 + 缩放读数 + 块状分析 + 放大裁切。 */
    private static void afterBothScreens(PixelGameView view, File outDir,
                                         boolean layoutReport) throws Exception {
        int w = com.badlogic.gdx.Gdx.graphics.getWidth();
        int h = com.badlogic.gdx.Gdx.graphics.getHeight();
        Pixmap shot = ScreenUtils.getFrameBufferPixmap(0, 0, w, h);
        FullFrame full = toArgb(shot, w, h);
        shot.dispose();
        File fullFile = new File(outDir, "audit-fullscreen.png");
        PngWriter.write(full.argb, full.width, full.height, fullFile);
        System.out.println(String.format("[audit] 整屏截图 %dx%d -> %s",
                full.width, full.height, fullFile.getAbsolutePath()));

        System.out.println(String.format(
                "[audit] 视口=%dx%d  缩放=%.3f  设计->物理=%.3f",
                w, h, view.scale(), view.scale()));
        analyzeBlocks(full.argb, full.width, full.height, "整屏截图");

        if (layoutReport) {
            // 关键文字带：顶栏（品牌+菜单+右上角）/ 详情框（标题、血量行、武器行、进度条、注释行）
            for (int y : new int[]{2, 8, 14, 20, 36, 42, 50, 58, 66, 174, 182, 192, 202, 212, 222, 232}) {
                printRowRuns(view, y, 8);
            }
        }

        int block = Math.max(1, (int) Math.rint(full.width / (double) PixelScale.DESIGN_WIDTH));
        exportCrop(full, outDir, 232, 176, 60, 20, Math.max(2, 8 / block), block);
    }

    /** GL 帧缓冲像素（RGBA8888 字节序）-> 非预乘 ARGB int[]。 */
    private record FullFrame(int[] argb, int width, int height) {
    }

    private static FullFrame toArgb(Pixmap pm, int w, int h) {
        java.nio.ByteBuffer bytes = pm.getPixels();
        int[] argb = new int[w * h];
        for (int i = 0; i < argb.length; i++) {
            int r = bytes.get(i * 4) & 0xFF;
            int g = bytes.get(i * 4 + 1) & 0xFF;
            int b = bytes.get(i * 4 + 2) & 0xFF;
            int a = bytes.get(i * 4 + 3) & 0xFF;
            argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        return new FullFrame(argb, w, h);
    }

    /** 采样质量：同色连续段长度分布（整数倍才说明是硬像素）。 */
    private static void analyzeBlocks(int[] argb, int w, int h, String label) {
        int expected = Math.max(1, (int) Math.rint(w / (double) PixelScale.DESIGN_WIDTH));

        int[] histogram = new int[24];
        long runs = 0;
        long cleanRows = 0;
        long rows = 0;
        for (int y = 0; y < h; y += 2) {
            boolean clean = true;
            int run = 1;
            int previous = argb[y * w];
            for (int x = 1; x < w; x++) {
                int px = argb[y * w + x];
                if (px == previous) {
                    run++;
                    continue;
                }
                if (run < histogram.length) {
                    histogram[run]++;
                }
                runs++;
                if (run % expected != 0) {
                    clean = false;
                }
                previous = px;
                run = 1;
            }
            rows++;
            if (clean) {
                cleanRows++;
            }
        }
        long exact = 0;
        for (int m = 1; m * expected < histogram.length; m++) {
            exact += histogram[m * expected];
        }
        StringBuilder top = new StringBuilder();
        for (int len = 1; len < Math.min(histogram.length, 13); len++) {
            if (histogram[len] > 0) {
                top.append(String.format("%dpx=%.1f%% ",
                        len, 100.0 * histogram[len] / Math.max(1, runs)));
            }
        }
        System.out.println(String.format("[block] %s 尺寸=%dx%d 期望块宽=%d", label, w, h, expected));
        System.out.println("[block]   连续段分布: " + top);
        System.out.println(String.format(
                "[block]   整行都是整数倍的行占比=%.1f%%   整数倍段占比=%.1f%%",
                100.0 * cleanRows / Math.max(1, rows), 100.0 * exact / Math.max(1, runs)));
    }

    private static void exportCrop(FullFrame image, File outDir, int dx, int dy,
                                   int dw, int dh, int zoom, int block) {
        int cw = dw * block * zoom;
        int ch = dh * block * zoom;
        int[] crop = new int[cw * ch];
        for (int y = 0; y < dh * block; y++) {
            for (int x = 0; x < dw * block; x++) {
                int argb = image.argb[(dy * block + y) * image.width + (dx * block + x)];
                for (int zy = 0; zy < zoom; zy++) {
                    for (int zx = 0; zx < zoom; zx++) {
                        crop[(y * zoom + zy) * cw + (x * zoom + zx)] = argb;
                    }
                }
            }
        }
        try {
            File file = new File(outDir, "audit-crop.png");
            PngWriter.write(crop, cw, ch, file);
            System.out.println("[audit] 放大裁切 -> " + file.getAbsolutePath());
        } catch (Exception ex) {
            System.err.println("[audit] 裁切导出失败: " + ex);
        }
    }

    /**
     * 打印某一"文字带"的墨迹区间：把 y..y+h-1 这几行做垂直投影，再合并成横向区间。
     *
     * <p>单扫一行只能碰到笔画碰巧经过的那些列，看不出整行文字占多宽，所以要看一个带。</p>
     */
    private static void printRowRuns(PixelGameView view, int y, int bandHeight) {
        int[] argb = view.buffer().art().buffer();
        int w = view.buffer().width();
        int h = view.buffer().height();
        boolean[] lit = new boolean[w];
        for (int row = y; row < y + bandHeight && row < h; row++) {
            for (int x = 0; x < w; x++) {
                if (isLit(argb[row * w + x])) {
                    lit[x] = true;
                }
            }
        }
        // 把相隔 <=2 像素的列并成一段，避免字形内部的空隙把一段文字切成几十段
        StringBuilder sb = new StringBuilder("[layout] y=" + y + ".." + (y + bandHeight - 1) + " 墨迹区间: ");
        int start = -1;
        int last = -1;
        for (int x = 0; x < w; x++) {
            if (lit[x]) {
                if (start < 0) {
                    start = x;
                }
                last = x;
            } else if (start >= 0 && x - last > 2) {
                sb.append(start).append('-').append(last).append("  ");
                start = -1;
            }
        }
        if (start >= 0) {
            sb.append(start).append('-').append(last);
        }
        System.out.println(sb);
    }

    /** 判断某个 ARGB 像素算不算"有墨"：够亮且不太透明。 */
    private static boolean isLit(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return a > 120 && (r + g + b) > 150;
    }

    /** 打印点阵字库的宽度表，供"文字有没有变宽"这类问题核对。 */
    private static void printFontReport() {
        com.hysx.salvosequence.core.PixelFont font = new com.hysx.salvosequence.core.PixelFont();
        // 逐字符实测：画一个字量墨迹宽度，和 charWidth 对照（两者不一致就说明前进距离算错了）
        PixelArt probe = new PixelArt(64, 16);
        StringBuilder table = new StringBuilder();
        for (char c : new char[]{'I', '1', ',', '.', 'A', 'M', 'W', '0', ' '}) {
            probe.clear();
            int advance = font.draw(probe, String.valueOf(c), 0, 0, com.hysx.salvosequence.core.Color.WHITE);
            int ink = 0;
            for (int x = 0; x < probe.width(); x++) {
                for (int y = 0; y < 16; y++) {
                    if (probe.pixelAt(x, y) != null) {
                        ink = Math.max(ink, x + 1);
                    }
                }
            }
            table.append('\'').append(c).append("'=").append(ink).append('/').append(advance).append("  ");
        }
        System.out.println("[font] ink/advance: " + table);
        System.out.println("[font] 字距 tracking=" + font.tracking()
                + "  字形高度=" + com.hysx.salvosequence.core.PixelFont.GLYPH_HEIGHT);
        StringBuilder widths = new StringBuilder();
        for (char c = 'A'; c <= 'Z'; c++) {
            widths.append(c).append('=').append(font.charWidth(c)).append(' ');
        }
        System.out.println("[font] 大写宽度: " + widths);
        String[] samples = {
                "HULL", "62%", "TWIN CANNON", "MICRO MISSILE", "ASSAULT WEDGE",
                "HOSTILE FLEET", "LEAD SHIP OF THE HOSTILE ECHELON",
                "MISSION", "ARROWS + ENTER   OR   CLICK", "ONE CANVAS / NO LAYERS",
                "SALVOSEQUENCE", "SETTINGS PAGE IS A PLACEHOLDER",
        };
        for (String s : samples) {
            System.out.println("[font] " + font.measure(s) + "px  \"" + s + "\"");
        }
    }

    public static void main(String[] args) {
        new CanvasAudit().start();
    }
}
