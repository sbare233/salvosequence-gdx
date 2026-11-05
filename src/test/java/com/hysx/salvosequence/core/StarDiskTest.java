package com.hysx.salvosequence.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** 星图角度帧的取帧规则：0.25° 一档、负角归一化、文件名格式。 */
class StarDiskTest {

    /** 期望值：先按 0.25° 取最近的档，再乘符号（见 {@link StarDisk#FRAME_SIGN}），最后归一化。 */
    private static int expected(double deg) {
        int index = (int) Math.round(deg / StarDisk.STEP_DEG) * StarDisk.FRAME_SIGN;
        return ((index % StarDisk.FRAMES) + StarDisk.FRAMES) % StarDisk.FRAMES;
    }

    @Test
    void theFrameIndexIsTheNearestQuarterDegree() {
        for (double deg : new double[]{0, 0.12, 0.13, 1.0, 90, 180, 359.9}) {
            assertEquals(expected(deg), StarDisk.frameIndexFor(Math.toRadians(deg)),
                    "角度 " + deg + "° 应当取 " + expected(deg) + " 号帧");
        }
    }

    @Test
    void negativeAndOverflowAnglesWrapAroundTheDisk() {
        for (double deg : new double[]{-0.25, -90, -360, 360, 361, 720.5}) {
            assertEquals(expected(deg), StarDisk.frameIndexFor(Math.toRadians(deg)),
                    "角度 " + deg + "° 应当取 " + expected(deg) + " 号帧");
        }
        // 每 0.25° 一档：转一圈回到第 0 帧
        assertEquals(StarDisk.frameIndexFor(0), StarDisk.frameIndexFor(Math.PI * 2));
    }

    @Test
    void theFramesAreNamedAfterTheirAngleWithOneDecimal() {
        assertEquals("mapback/frame_000_000.0deg-x1-l12.png", StarDisk.fileName(0));
        assertEquals("mapback/frame_001_000.3deg-x1-l12.png", StarDisk.fileName(1));
        assertEquals("mapback/frame_007_001.8deg-x1-l12.png", StarDisk.fileName(7));
        assertEquals("mapback/frame_1439_359.8deg-x1-l12.png", StarDisk.fileName(1439));
    }

    @Test
    void theFirstFrameLoadsFromTheClasspath() {
        PngLoader.Bitmap frame = StarDisk.frame(0);
        assertNotNull(frame, "mapback 第 0 帧应当能解码");
        assertEquals(StarDisk.IMAGE_W, frame.width());
        assertEquals(StarDisk.IMAGE_H, frame.height());
    }

    @Test
    void theDiskCentreProjectsToTheCentreOfTheScreen() {
        double[] p = StarDisk.project(0, 0);
        assertEquals(StarDisk.CENTER_X, p[0], 1e-9);
        assertEquals(StarDisk.CENTER_Y, p[1], 1e-9);
    }

    @Test
    void theNearSideOfTheDiskIsMagnifiedAndTheFarSideCompressed() {
        double near = StarDisk.project(0, 200)[1] - StarDisk.CENTER_Y;
        double far = StarDisk.CENTER_Y - StarDisk.project(0, -200)[1];
        // 30° 俯角：无透视时两侧都该是 200*0.5=100；有透视时近侧更大、远侧更小
        assertTrue(near > 100, "近侧应当被放大: " + near);
        assertTrue(far < 100, "远侧应当被压缩: " + far);
        assertTrue(near > far);
    }

    @Test
    void theHorizontalAxisGrowsTowardsTheViewer() {
        double[] nearEdge = StarDisk.project(200, 200);
        double[] farEdge = StarDisk.project(200, -200);
        assertTrue(nearEdge[0] - StarDisk.CENTER_X > farEdge[0] - StarDisk.CENTER_X,
                "近侧的横向偏移应当更大");
    }

    @Test
    void theFarEdgeOfTheDiskConvergesOnTheVanishingPoint() {
        double[] p = StarDisk.project(0, -1_000_000);
        assertEquals(StarDisk.VANISH_X, p[0], 0.5);
        assertEquals(StarDisk.vanishY(), p[1], 0.5);
    }

    @Test
    void theDepthScaleIsAboveOneNearTheViewerAndBelowOneFarAway() {
        assertTrue(StarDisk.depthScale(200) > 1.1, "近侧星星要更大");
        assertTrue(StarDisk.depthScale(-200) < 0.9, "远侧星星要更小");
        assertEquals(1.0, StarDisk.depthScale(0), 1e-9, "盘心处不放大也不缩小");
    }

    @Test
    void theVanishingPointCanBeNudgedAtRuntime() {
        double before = StarDisk.vanishY();
        try {
            StarDisk.setVanishY(-65);
            double[] p = StarDisk.project(0, -1_000_000);
            assertEquals(-65, p[1], 0.5, "挪了消失点后远侧极限应当跟着走");
            assertTrue(StarDisk.cameraDistance() > 400, "消失点往上挪 = 相机更远 = 透视更平");
        } finally {
            StarDisk.setVanishY(before);
        }
    }
}
