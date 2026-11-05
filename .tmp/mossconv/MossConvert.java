import com.hysx.salvosequence.core.AwtImageCodec;
import com.hysx.salvosequence.core.BayerPixelator;
import com.hysx.salvosequence.core.ImageCodec;
import com.hysx.salvosequence.core.PngLoader;
import com.hysx.salvosequence.tools.PngWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * 把 gen/moss 里 MOSS 出的图落到游戏里：
 *   1) bg-*.png（1024x576）→ 双线性缩到 1600x900 再 Bayer 像素化 → backdrops/custom/moss-<名字>.png（640x360）
 *   2) ship-*.png（1024x1024）→ 面积平均降到 该船型的 w*18 x h*18 → ships/import/<船 id>.png
 *
 * 用法：java -cp "target/classes;.tmp/mossconv;<deps>" MossConvert
 */
public final class MossConvert {

    private static final int BIG_W = 1600;
    private static final int BIG_H = 900;

    public static void main(String[] args) throws Exception {
        ImageCodec.setBackend(new AwtImageCodec());
        Path gen = Path.of("gen", "moss");
        File backdropDir = new File("backdrops", "custom");
        backdropDir.mkdirs();

        File[] images = gen.toFile().listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
        if (images == null) {
            System.out.println("gen/moss 里没有图");
            return;
        }
        Arrays.sort(images);

        for (File file : images) {
            String name = file.getName().toLowerCase();
            if (name.startsWith("bg-")) {
                convertBackdrop(file, backdropDir);
            }
        }
        System.out.println("backdrops/custom 现在有 " + backdropDir.listFiles().length + " 张");
    }

    /** 背景：先按 16:9 缩到 1600x900（双线性），再走游戏自己的 Bayer 像素化。 */
    private static void convertBackdrop(File file, File outDir) throws IOException {
        ImageCodec.Loaded loaded = ImageCodec.backend().loadScaled(file, BIG_W, BIG_H);
        if (loaded == null) {
            System.out.println("读不出 " + file.getName());
            return;
        }
        int[] big = resize(loaded, BIG_W, BIG_H);
        int[] px = BayerPixelator.pixelate(big, BIG_W, BIG_H, BayerPixelator.Params.DEFAULT);
        File out = new File(outDir, "moss-" + file.getName());
        PngWriter.write(px, BayerPixelator.W, BayerPixelator.H, out);
        System.out.println("背景 " + out.getName() + " ← " + file.getName());
    }

    /** 双线性缩放（面积平均在上面那步之后由 Bayer 的格子采样完成，这里只要平滑底图）。 */
    private static int[] resize(ImageCodec.Loaded img, int w, int h) {
        int[] out = new int[w * h];
        int sw = img.width();
        int sh = img.height();
        for (int y = 0; y < h; y++) {
            double sy = (y + 0.5) * sh / h - 0.5;
            int y0 = Math.max(0, Math.min(sh - 1, (int) Math.floor(sy)));
            int y1 = Math.max(0, Math.min(sh - 1, y0 + 1));
            double fy = Math.max(0, Math.min(1, sy - y0));
            for (int x = 0; x < w; x++) {
                double sx = (x + 0.5) * sw / w - 0.5;
                int x0 = Math.max(0, Math.min(sw - 1, (int) Math.floor(sx)));
                int x1 = Math.max(0, Math.min(sw - 1, x0 + 1));
                double fx = Math.max(0, Math.min(1, sx - x0));
                int p00 = img.argb()[y0 * img.width() + x0];
                int p10 = img.argb()[y0 * img.width() + x1];
                int p01 = img.argb()[y1 * img.width() + x0];
                int p11 = img.argb()[y1 * img.width() + x1];
                out[y * w + x] = blend(p00, p10, p01, p11, fx, fy);
            }
        }
        return out;
    }

    private static int blend(int p00, int p10, int p01, int p11, double fx, double fy) {
        double w00 = (1 - fx) * (1 - fy);
        double w10 = fx * (1 - fy);
        double w01 = (1 - fx) * fy;
        double w11 = fx * fy;
        int a = (int) Math.round(((p00 >>> 24) & 0xFF) * w00 + ((p10 >>> 24) & 0xFF) * w10
                + ((p01 >>> 24) & 0xFF) * w01 + ((p11 >>> 24) & 0xFF) * w11);
        int r = (int) Math.round(((p00 >> 16) & 0xFF) * w00 + ((p10 >> 16) & 0xFF) * w10
                + ((p01 >> 16) & 0xFF) * w01 + ((p11 >> 16) & 0xFF) * w11);
        int g = (int) Math.round(((p00 >> 8) & 0xFF) * w00 + ((p10 >> 8) & 0xFF) * w10
                + ((p01 >> 8) & 0xFF) * w01 + ((p11 >> 8) & 0xFF) * w11);
        int b = (int) Math.round((p00 & 0xFF) * w00 + (p10 & 0xFF) * w10
                + (p01 & 0xFF) * w01 + (p11 & 0xFF) * w11);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
