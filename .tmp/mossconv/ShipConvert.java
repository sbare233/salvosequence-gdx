import com.hysx.salvosequence.core.AwtImageCodec;
import com.hysx.salvosequence.core.ImageCodec;
import com.hysx.salvosequence.tools.PngWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把 gen/moss 的 side-view 战舰图落到 ships/import/<船 id>.png。
 *
 * 目标尺寸 = 船型的 w/h 格数 × AppearanceDesign.CELL(18)：
 *   4x3 → 72x54、4x2 → 72x36、5x5 → 90x90、2x2 → 36x36。
 * 纯黑背景从边缘泛洪抠成透明，中心保留船体暗部；最后每通道量化到 5 档，凑出像素味。
 */
public final class ShipConvert {

    private static final int CELL = 18;

    /** gen/moss 文件名 → 船 id、格数。 */
    private static final Map<String, String[]> JOBS = new LinkedHashMap<>();

    static {
        JOBS.put("ship-main-gun", new String[] {"gun.main", "4", "3"});
        JOBS.put("ship-broadside", new String[] {"gun.broadside", "4", "2"});
        JOBS.put("ship-armor-belt", new String[] {"armor.belt", "4", "2"});
        JOBS.put("ship-carrier", new String[] {"support.logistics", "3", "3"});
        JOBS.put("ship-dreadnought", new String[] {"gun.heavy", "5", "5"});
        JOBS.put("ship-corvette", new String[] {"gun.vulcan", "2", "2"});
    }

    public static void main(String[] args) throws Exception {
        ImageCodec.setBackend(new AwtImageCodec());
        File gen = new File("gen/moss");
        File out = new File("ships/import");
        out.mkdirs();
        for (Map.Entry<String, String[]> e : JOBS.entrySet()) {
            File src = new File(gen, e.getKey() + ".png");
            if (!src.isFile()) {
                System.out.println("跳过（还没有）" + src.getName());
                continue;
            }
            String[] spec = e.getValue();
            int w = Integer.parseInt(spec[1]) * CELL;
            int h = Integer.parseInt(spec[2]) * CELL;
            ImageCodec.Loaded img = ImageCodec.backend().loadScaled(src, 2048, 2048);
            if (img == null) {
                System.out.println("读不出 " + src.getName());
                continue;
            }
            int[] shrunk = areaAverage(img, w, h);
            int[] cut = knockoutBackground(shrunk, w, h);
            int[] quantized = quantize(cut, 5);
            File dst = new File(out, spec[0] + ".png");
            PngWriter.write(quantized, w, h, dst);
            System.out.println(dst.getPath() + "  " + w + "x" + h + "  ← " + src.getName());
        }
    }

    /** 面积平均降采样（保留 alpha 预乘，避免边缘发白发黑）。 */
    private static int[] areaAverage(ImageCodec.Loaded img, int w, int h) {
        int[] out = new int[w * h];
        int sw = img.width();
        int sh = img.height();
        for (int y = 0; y < h; y++) {
            int sy0 = y * sh / h;
            int sy1 = Math.max(sy0 + 1, (y + 1) * sh / h);
            for (int x = 0; x < w; x++) {
                int sx0 = x * sw / w;
                int sx1 = Math.max(sx0 + 1, (x + 1) * sw / w);
                long r = 0;
                long g = 0;
                long b = 0;
                int n = 0;
                for (int sy = sy0; sy < sy1; sy++) {
                    for (int sx = sx0; sx < sx1; sx++) {
                        int argb = img.argb()[sy * img.width() + sx];
                        r += (argb >> 16) & 0xFF;
                        g += (argb >> 8) & 0xFF;
                        b += argb & 0xFF;
                        n++;
                    }
                }
                out[y * w + x] = 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
            }
        }
        return out;
    }

    /** 从四边泛洪把"黑背景"抠成透明；船体内部的暗部因为连不到边界会留下。 */
    private static int[] knockoutBackground(int[] argb, int w, int h) {
        boolean[] dark = new boolean[w * h];
        for (int i = 0; i < argb.length; i++) {
            dark[i] = luminance(argb[i]) < 34;
        }
        boolean[] outside = new boolean[w * h];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            push(x, w, h, dark, outside, queue);
            push((h - 1) * w + x, w, h, dark, outside, queue);
        }
        for (int y = 0; y < h; y++) {
            push(y * w, w, h, dark, outside, queue);
            push(y * w + w - 1, w, h, dark, outside, queue);
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = i % w;
            int y = i / w;
            if (x > 0) {
                push(i - 1, w, h, dark, outside, queue);
            }
            if (x < w - 1) {
                push(i + 1, w, h, dark, outside, queue);
            }
            if (y > 0) {
                push(i - w, w, h, dark, outside, queue);
            }
            if (y < h - 1) {
                push(i + w, w, h, dark, outside, queue);
            }
        }
        int[] out = Arrays.copyOf(argb, argb.length);
        for (int i = 0; i < out.length; i++) {
            if (outside[i]) {
                out[i] = 0x00000000;
            }
        }
        return out;
    }

    private static void push(int i, int w, int h, boolean[] dark, boolean[] outside,
                             ArrayDeque<Integer> queue) {
        if (i < 0 || i >= w * h || outside[i] || !dark[i]) {
            return;
        }
        outside[i] = true;
        queue.add(i);
    }

    private static int luminance(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    /** 每通道量化到 levels 档，顺手把过暗的像素清成透明。 */
    private static int[] quantize(int[] argb, int levels) {
        int[] out = new int[argb.length];
        int step = 255 / (levels - 1);
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            if ((p >>> 24) == 0) {
                continue;
            }
            int r = round((p >> 16) & 0xFF, step);
            int g = round((p >> 8) & 0xFF, step);
            int b = round(p & 0xFF, step);
            if (r + g + b < 78) {
                continue;
            }
            out[i] = 0xFF000000 | r << 16 | g << 8 | b;
        }
        return out;
    }

    private static int round(int v, int step) {
        int q = Math.min(255, (int) Math.round(v / (double) step) * step);
        return q;
    }
}
