package com.hysx.salvosequence.live;

import com.hysx.salvosequence.core.WorkDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTuneTest {

    /** 一个最小的"参数对象"，字段类型覆盖 float/int/long/boolean。 */
    public static final class FakeParams {
        public float gain = 1.5f;
        public int passes = 3;
        public long seed = 42L;
        public boolean enabled = true;
        public static final String IGNORED = "静态字段不算参数";
    }

    @Test
    void onlyInstancePrimitiveFieldsAreListed() {
        List<Field> fields = LiveTune.fields(new FakeParams());
        assertEquals(List.of("gain", "passes", "seed", "enabled"),
                fields.stream().map(Field::getName).toList());
        assertFalse(fields.stream().anyMatch(f -> f.getName().equals("IGNORED")));
    }

    @Test
    void writesReadsAndFormatsBack() {
        FakeParams p = new FakeParams();
        p.gain = 0.25f;
        p.passes = 7;
        p.seed = 20261105L;
        p.enabled = false;

        StringBuilder sb = new StringBuilder();
        LiveTune.write(sb, "fake", p);
        String text = sb.toString();
        assertTrue(text.contains("fake.gain=0.25"), text);
        assertTrue(text.contains("fake.passes=7"), text);
        assertTrue(text.contains("fake.seed=20261105"), text);
        assertTrue(text.contains("fake.enabled=false"), text);

        FakeParams back = new FakeParams();
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            values.put(line.substring(0, eq), line.substring(eq + 1));
        }
        LiveTune.read("fake", values, back);
        assertEquals(0.25f, back.gain, 1e-6);
        assertEquals(7, back.passes);
        assertEquals(20261105L, back.seed);
        assertFalse(back.enabled);
    }

    @Test
    void stepsAndLabelsMatchTheValueScale() {
        FakeParams p = new FakeParams();
        Field gain = LiveTune.fields(p).get(0);
        Field passes = LiveTune.fields(p).get(1);
        assertEquals(0.1, LiveTune.step(p, gain), 1e-9);   // 1.5 这种量级给 0.1
        assertEquals(1, LiveTune.step(p, passes), 1e-9);
        assertEquals("1.50", LiveTune.text(p, gain));
        assertEquals("3", LiveTune.text(p, passes));
    }

    @Test
    void cameraLinesRoundTripThroughTheSettingsFile(@TempDir Path tmp) {
        File old = WorkDir.base();
        WorkDir.setBase(tmp.toFile());
        try {
            StringBuilder sb = new StringBuilder();
            LiveTune.writeCamera(sb, "nebula", new double[]{-118, 9, 1.3});
            String text = sb.toString();
            assertTrue(text.contains("nebula.cam.yaw=-118.0"), text);

            java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
            for (String line : text.trim().split("\n")) {
                int eq = line.indexOf('=');
                values.put(line.substring(0, eq), line.substring(eq + 1));
            }
            double[] cam = LiveTune.readCamera("nebula", values, new double[]{0, 0, 1});
            assertEquals(-118, cam[0], 1e-9);
            assertEquals(9, cam[1], 1e-9);
            assertEquals(1.3, cam[2], 1e-6);
        } finally {
            WorkDir.setBase(old);
        }
    }

    @Test
    void aSavedCameraComesBackOnLoad(@TempDir Path tmp) throws Exception {
        File old = WorkDir.base();
        WorkDir.setBase(tmp.toFile());
        try {
            Files.write(LiveSettings.file().toPath(),
                    java.util.List.of("nebula.cam.yaw=42.5",
                            "nebula.cam.pitch=-7",
                            "nebula.cam.dist=2.25"),
                    StandardCharsets.UTF_8);
            LiveSettings.load();
            double[] cam = LiveBackdrops.camera(0);
            assertEquals(42.5, cam[0], 1e-6);
            assertEquals(-7, cam[1], 1e-6);
            assertEquals(2.25, cam[2], 1e-6);
        } finally {
            // 别把测试用的角度漏给别的用例
            LiveBackdrops.setCamera(0, -118, 9, 1.3);
            WorkDir.setBase(old);
        }
    }

    @Test
    void nothingIsWrittenWhenThereIsNoFileYet(@TempDir Path tmp) throws Exception {
        File old = WorkDir.base();
        WorkDir.setBase(tmp.toFile());
        try {
            LiveSettings.load();   // 不该抛，也不该建文件
            assertFalse(LiveSettings.file().exists());
            LiveSettings.save();
            assertTrue(LiveSettings.file().isFile());
            String text = Files.readString(LiveSettings.file().toPath(), StandardCharsets.UTF_8);
            assertTrue(text.contains("nebula.cam.yaw="), text);
            assertNotNull(text);
        } finally {
            WorkDir.setBase(old);
        }
    }
}
