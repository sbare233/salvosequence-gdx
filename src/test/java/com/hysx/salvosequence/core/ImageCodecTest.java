package com.hysx.salvosequence.core;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台图片后端门面 + 工作目录：背景工坊在桌面/安卓共用同一份界面代码的接口约定。
 */
class ImageCodecTest {

    @BeforeAll
    static void installDesktopCodec() {
        ImageCodec.setBackend(new AwtImageCodec());
    }

    @AfterAll
    static void restoreDesktopCodec() {
        ImageCodec.setBackend(new AwtImageCodec());
    }

    @Test
    void aPngSurvivesTheWriteReadRoundTrip(@TempDir Path tmp) {
        int w = 6;
        int h = 4;
        int[] argb = new int[w * h];
        for (int i = 0; i < argb.length; i++) {
            argb[i] = 0xFF000000 | (i * 0x0A0B0C);
        }
        File out = tmp.resolve("round.png").toFile();
        assertTrue(ImageCodec.backend().writePng(argb, w, h, out), "写 PNG 失败");

        ImageCodec.Loaded back = ImageCodec.backend().loadScaled(out, 1600, 900);
        assertEquals(w, back.width());
        assertEquals(h, back.height());
        for (int i = 0; i < argb.length; i++) {
            assertEquals(argb[i], back.argb()[i], "第 " + i + " 个像素变了");
        }
    }

    @Test
    void loadingScalesIntoTheBoxKeepingTheAspectRatio(@TempDir Path tmp) {
        File out = tmp.resolve("wide.png").toFile();
        assertTrue(ImageCodec.backend().writePng(new int[40 * 10], 40, 10, out));

        ImageCodec.Loaded half = ImageCodec.backend().loadScaled(out, 20, 900);
        assertEquals(20, half.width());
        assertEquals(5, half.height(), "等比缩放后高度应跟着减半");
    }

    @Test
    void aMissingFileIsJustNull(@TempDir Path tmp) {
        assertNull(ImageCodec.backend().loadScaled(tmp.resolve("nope.png").toFile(), 1600, 900));
    }

    @Test
    void relativePathsHangOffTheWorkingDirectory(@TempDir Path tmp) {
        File old = WorkDir.base();
        try {
            WorkDir.setBase(tmp.toFile());
            assertEquals(tmp.resolve("backdrops-src").toFile(), WorkDir.of("backdrops-src"));
        } finally {
            WorkDir.setBase(old);
        }
    }
}
