package com.hysx.salvosequence.live;

import com.hysx.salvosequence.core.WorkDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实时背景的调参存档：把每个场景 {@code Params} 里的公开数值字段和相机角度写成一行一项的文本，
 * 放在 {@link WorkDir}（桌面是工作目录，安卓是 App 私有目录）里的 {@code live-params.conf}。
 *
 * <p>用反射按"字段名"读写，不依赖每个场景自带序列化；新增字段缺省时用代码里的默认值，
 * 所以老配置文件不会因为参数表变化而失效。</p>
 */
public final class LiveSettings {

    private static final String FILE_NAME = "live-params.conf";

    private LiveSettings() {
    }

    public static File file() {
        return WorkDir.of(FILE_NAME);
    }

    /** 读盘并套到各场景上；文件不存在就什么都不做（保持代码默认值）。 */
    public static void load() {
        File f = file();
        if (!f.isFile()) {
            return;
        }
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq <= 0 || line.startsWith("#")) {
                    continue;
                }
                values.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        } catch (IOException e) {
            return;
        }
        for (int i = 0; i < LiveBackdrops.count(); i++) {
            String id = LiveBackdrops.id(i);
            LiveTune.read(id, values, LiveBackdrops.params(i));
            double[] cam = LiveTune.readCamera(id, values, LiveBackdrops.camera(i));
            LiveBackdrops.setCamera(i, cam[0], cam[1], cam[2]);
        }
    }

    /** 全量重写（参数只有几十个，直接覆盖比增量对齐省事）。 */
    public static void save() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Salvo Sequence - live backdrop parameters\n");
        for (int i = 0; i < LiveBackdrops.count(); i++) {
            String id = LiveBackdrops.id(i);
            LiveTune.write(sb, id, LiveBackdrops.params(i));
            LiveTune.writeCamera(sb, id, LiveBackdrops.camera(i));
        }
        try {
            Files.write(file().toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // 存档失败不影响渲染，下次改动再试
        }
    }
}
