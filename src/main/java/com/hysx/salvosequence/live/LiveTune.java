package com.hysx.salvosequence.live;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实时背景参数的读写工具：反射遍历 {@code Params} 的公开数值字段，供背景工坊的调参面板和
 * {@link LiveSettings} 的存档共用。
 *
 * <p>只收 {@code float / int / long / boolean} 这几种原始类型——它们既是渲染参数的全部形态，
 * 也能用一行 {@code 名称=数值} 无损往返。</p>
 */
public final class LiveTune {

    /** 相机对应的键（不叫 camYaw 之类，和场景字段区分开）。 */
    public static final String KEY_YAW = "cam.yaw";
    public static final String KEY_PITCH = "cam.pitch";
    public static final String KEY_DIST = "cam.dist";

    private static final Map<Class<?>, List<Field>> CACHE = new LinkedHashMap<>();

    private LiveTune() {
    }

    /** 可调字段（按声明顺序，输出稳定）。 */
    public static List<Field> fields(Object params) {
        if (params == null) {
            return List.of();
        }
        return CACHE.computeIfAbsent(params.getClass(), type -> {
            List<Field> out = new ArrayList<>();
            for (Field f : type.getFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                Class<?> t = f.getType();
                if (t == float.class || t == int.class || t == long.class || t == boolean.class) {
                    out.add(f);
                }
            }
            return out;
        });
    }

    /** 字段的当前数值（布尔按 0/1 返回）。 */
    public static double get(Object params, Field f) {
        try {
            if (f.getType() == boolean.class) {
                return f.getBoolean(params) ? 1 : 0;
            }
            if (f.getType() == float.class) {
                return f.getFloat(params);
            }
            if (f.getType() == int.class) {
                return f.getInt(params);
            }
            return f.getLong(params);
        } catch (IllegalAccessException e) {
            return 0;
        }
    }

    public static void set(Object params, Field f, double value) {
        try {
            if (f.getType() == boolean.class) {
                f.setBoolean(params, value >= 0.5);
            } else if (f.getType() == float.class) {
                f.setFloat(params, (float) value);
            } else if (f.getType() == int.class) {
                f.setInt(params, (int) Math.round(value));
            } else if (f.getType() == long.class) {
                f.setLong(params, Math.round(value));
            }
        } catch (IllegalAccessException ignored) {
            // 公开字段不会走到这里
        }
    }

    /** 一步的步长：小数参数按量级给 1%，整型给 1。 */
    public static double step(Object params, Field f) {
        Class<?> t = f.getType();
        if (t == boolean.class) {
            return 1;
        }
        if (t == int.class || t == long.class) {
            return 1;
        }
        double v = Math.abs(get(params, f));
        if (v >= 50) {
            return Math.max(1, Math.round(v * 0.02));
        }
        if (v >= 1) {
            return 0.1;
        }
        return 0.01;
    }

    /** 面板上显示的值：小数保留两位，整型直出，布尔显示开/关。 */
    public static String text(Object params, Field f) {
        if (f.getType() == boolean.class) {
            return get(params, f) >= 0.5 ? "开" : "关";
        }
        double v = get(params, f);
        if (f.getType() == int.class || f.getType() == long.class) {
            return String.valueOf(Math.round(v));
        }
        if (Math.abs(v) >= 10) {
            return String.format("%.1f", v);
        }
        return String.format("%.2f", v);
    }

    // ------------------------------------------------------------ 存档

    static void write(StringBuilder sb, String scene, Object params) {
        for (Field f : fields(params)) {
            sb.append(scene).append('.').append(f.getName()).append('=')
                    .append(raw(params, f)).append('\n');
        }
    }

    static void writeCamera(StringBuilder sb, String scene, double[] cam) {
        sb.append(scene).append('.').append(KEY_YAW).append('=').append(cam[0]).append('\n');
        sb.append(scene).append('.').append(KEY_PITCH).append('=').append(cam[1]).append('\n');
        sb.append(scene).append('.').append(KEY_DIST).append('=').append(cam[2]).append('\n');
    }

    static void read(String scene, Map<String, String> values, Object params) {
        if (params == null) {
            return;
        }
        for (Field f : fields(params)) {
            String v = values.get(scene + "." + f.getName());
            if (v == null) {
                continue;
            }
            if (f.getType() == boolean.class) {
                set(params, f, "true".equalsIgnoreCase(v) ? 1 : 0);
                continue;
            }
            try {
                set(params, f, Double.parseDouble(v));
            } catch (NumberFormatException ignored) {
                // 坏行跳过，保留默认值
            }
        }
    }

    static double[] readCamera(String scene, Map<String, String> values, double[] fallback) {
        double[] cam = fallback.clone();
        cam[0] = num(values, scene + "." + KEY_YAW, cam[0]);
        cam[1] = num(values, scene + "." + KEY_PITCH, cam[1]);
        cam[2] = num(values, scene + "." + KEY_DIST, cam[2]);
        return cam;
    }

    private static double num(Map<String, String> values, String key, double fallback) {
        String v = values.get(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String raw(Object params, Field f) {
        if (f.getType() == boolean.class) {
            return get(params, f) >= 0.5 ? "true" : "false";
        }
        if (f.getType() == int.class || f.getType() == long.class) {
            return String.valueOf(Math.round(get(params, f)));
        }
        return String.valueOf((float) get(params, f));
    }
}
