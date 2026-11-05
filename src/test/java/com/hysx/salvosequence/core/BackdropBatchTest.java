package com.hysx.salvosequence.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 背景工坊的批量导出：整个文件夹按当前参数过一遍，落在时间戳文件夹里。
 */
class BackdropBatchTest {

    private static final BayerPixelator.Params PARAMS =
            new BayerPixelator.Params(2, 6, 70, 0, 10, 110, true);

    @BeforeAll
    static void installDesktopCodec() {
        ImageCodec.setBackend(new AwtImageCodec());
    }

    @Test
    void exportsEveryImageIntoATimestampedFolder(@TempDir Path tmp) throws Exception {
        File src = Files.createDirectories(tmp.resolve("backdrops-src")).toFile();
        writePng(new File(src, "alpha.png"), 40, 30, 0xFFFF8040);
        writePng(new File(src, "beta.png"), 64, 64, 0xFF204080);

        File root = tmp.resolve("batch").toFile();
        BackdropBatch.Result r = BackdropBatch.exportAll(src, root,
                PARAMS, LocalDateTime.of(2026, 11, 3, 21, 43, 7));

        assertEquals(2, r.written());
        assertEquals(0, r.skipped());
        assertEquals("20261103-214307", r.folder().getName());
        assertTrue(r.folder().isDirectory());

        File[] out = r.folder().listFiles((d, n) -> n.endsWith(".png"));
        assertEquals(2, out.length);
        // 文件名带当前参数，方便同名源图在不同参数批次里区分
        assertTrue(new File(r.folder(), "alpha-x2-l6.png").isFile());
        assertTrue(new File(r.folder(), "beta-x2-l6.png").isFile());
        for (File f : out) {
            BufferedImage img = ImageIO.read(f);
            assertEquals(BayerPixelator.W, img.getWidth());
            assertEquals(BayerPixelator.H, img.getHeight());
        }
    }

    @Test
    void everyRunGetsItsOwnFolder(@TempDir Path tmp) throws Exception {
        File src = Files.createDirectories(tmp.resolve("backdrops-src")).toFile();
        writePng(new File(src, "only.png"), 32, 32, 0xFF102030);
        File root = tmp.resolve("batch").toFile();

        BackdropBatch.Result first = BackdropBatch.exportAll(src, root, PARAMS,
                LocalDateTime.of(2026, 11, 3, 10, 0, 0));
        BackdropBatch.Result second = BackdropBatch.exportAll(src, root, PARAMS,
                LocalDateTime.of(2026, 11, 3, 10, 0, 0).plusSeconds(1));

        assertEquals("20261103-100000", first.folder().getName());
        assertEquals("20261103-100001", second.folder().getName());
        assertTrue(new File(first.folder(), "only-x2-l6.png").isFile());
        assertTrue(new File(second.folder(), "only-x2-l6.png").isFile());
    }

    @Test
    void aBrokenImageIsSkippedAndTheRestStillExports(@TempDir Path tmp) throws Exception {
        File src = Files.createDirectories(tmp.resolve("backdrops-src")).toFile();
        writePng(new File(src, "good.png"), 32, 32, 0xFF00FF00);
        Files.write(new File(src, "broken.png").toPath(), new byte[] {1, 2, 3, 4});

        BackdropBatch.Result r = BackdropBatch.exportAll(src, tmp.resolve("batch").toFile(),
                PARAMS, LocalDateTime.of(2026, 11, 3, 12, 0, 0));

        assertEquals(1, r.written());
        assertEquals(1, r.skipped());
        assertTrue(new File(r.folder(), "good-x2-l6.png").isFile());
    }

    @Test
    void anEmptySourceFolderWritesNoFolder(@TempDir Path tmp) throws Exception {
        File src = Files.createDirectories(tmp.resolve("backdrops-src")).toFile();
        File root = tmp.resolve("batch").toFile();

        BackdropBatch.Result r = BackdropBatch.exportAll(src, root, PARAMS,
                LocalDateTime.of(2026, 11, 3, 12, 0, 0));

        assertEquals(0, r.written());
        assertEquals(0, r.skipped());
        assertFalse(r.folder().exists());
    }

    @Test
    void duplicateBaseNamesDoNotOverwriteEachOther(@TempDir Path tmp) throws Exception {
        File src = Files.createDirectories(tmp.resolve("backdrops-src")).toFile();
        writePng(new File(src, "a.png"), 8, 8, 0xFF111111);
        writePng(new File(src, "a.jpg"), 8, 8, 0xFF222222);

        BackdropBatch.Result r = BackdropBatch.exportAll(src, tmp.resolve("batch").toFile(),
                PARAMS, LocalDateTime.of(2026, 11, 3, 12, 0, 0));

        assertEquals(2, r.written());
        File[] out = r.folder().listFiles((d, n) -> n.endsWith(".png"));
        assertEquals(2, out.length);
    }

    @Test
    void pixelFilterExportsWithItsOwnSuffix(@TempDir Path tmp) throws Exception {
        File src = Files.createDirectories(tmp.resolve("backdrops-src")).toFile();
        writePng(new File(src, "alpha.png"), 40, 30, 0xFFFF8040);

        // 像素滤镜的色阶是它自己的参数，先固定成默认值，免得别的用例改过
        PixelArtFilter.reset();
        for (int i = 0; i < 4 && PixelArtFilter.mode() != 0; i++) {
            PixelArtFilter.cycleMode(1);
        }

        BackdropBatch.Result r = BackdropBatch.exportAll(src, tmp.resolve("batch").toFile(),
                PARAMS, LocalDateTime.of(2026, 11, 3, 21, 43, 7),
                com.hysx.salvosequence.ui.VideoSettings.BackdropFilter.PIXEL);

        assertEquals(1, r.written());
        File out = new File(r.folder(), "alpha-px-s8.png");
        assertTrue(out.isFile(), "像素滤镜用 -px-s{色阶} 后缀");
        BufferedImage img = ImageIO.read(out);
        assertEquals(BayerPixelator.W, img.getWidth());
        assertEquals(BayerPixelator.H, img.getHeight());
    }

    private static void writePng(File file, int w, int h, int argb) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, argb);
            }
        }
        ImageIO.write(img, "png", file);
    }
}
