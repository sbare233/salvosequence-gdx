package com.hysx.salvosequence.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;

/**
 * 资源定位：外部 {@code res/} 优先，jar 内兜底。
 *
 * <p>发行目录结构（与 blendefine 一致，资源放 exe/jar 旁边而不是只埋在包里）：</p>
 * <pre>
 *   SalvoSequence-x.y/
 *     start.bat
 *     app/salvosequence.jar
 *     runtime/
 *     res/fonts/ZLabsPixel_12px_M_CN.ttf
 *     res/music/...
 * </pre>
 *
 * <p>查找顺序：{@code <base>/res/<path>} → classpath {@code /<path>} → 当前目录 {@code <path>}。
 * 这样玩家/开发者可以直接替换字体、音乐而不必重新打包；jar 里仍保留一份兜底，
 * 干净构建（例如只有 jar）也能跑。</p>
 */
public final class Assets {

    /** 找不到任何副本时的空资源，避免调用方到处判 null。 */
    public static final InputStream EMPTY = new java.io.ByteArrayInputStream(new byte[0]);

    private static String baseDir;

    /** 平台资源回退：Android 上没有类路径资源，启动时注册一个走 AssetManager 的加载器。 */
    private static java.util.function.Function<String, InputStream> platformLoader;

    private Assets() {
    }

    /** 注册平台资源加载器（桌面保持 null；Android 启动时注册）。 */
    public static void setPlatformLoader(java.util.function.Function<String, InputStream> loader) {
        platformLoader = loader;
    }

    /** 首帧前的引导钩子（Android 用来在加载 gdx-vfx 着色器之前接管 Gdx.files）。 */
    private static Runnable startupHook;

    /** 注册引导钩子；桌面不注册，调用即为空操作。 */
    public static void setStartupHook(Runnable hook) {
        startupHook = hook;
    }

    /** 由 create() 最开头调用一次，执行完即清空。 */
    public static void runStartupHook() {
        Runnable hook = startupHook;
        startupHook = null;
        if (hook != null) {
            hook.run();
        }
    }

    /** 应用根目录：发行包里是 start.bat 所在目录；开发时是 classes/ 目录。 */
    public static String baseDir() {
        if (baseDir == null) {
            String dir = null;
            try {
                File loc = new File(Assets.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                File base = loc.isFile() ? loc.getParentFile() : loc;
                dir = base.getPath();
                // 发行布局 <root>/app/salvosequence.jar -> 外部资源在 <root>/res
                if (loc.isFile() && base.getParentFile() != null
                        && "app".equalsIgnoreCase(base.getName())) {
                    dir = base.getParentFile().getPath();
                }
            } catch (Exception ignored) {
                // 沙箱/非常规类加载器：退回工作目录
            }
            if (dir == null || dir.isBlank()) {
                dir = System.getProperty("user.dir", ".");
            }
            baseDir = dir;
        }
        return baseDir;
    }

    /** 外部 res/ 下的路径（不存在也返回 File，便于调用方判断）。 */
    public static File external(String path) {
        return new File(new File(baseDir(), "res"), clean(path));
    }

    /** 打开资源：外部 res/ 优先，其次 jar 内，最后当前目录；全都没有时返回 {@link #EMPTY}。 */
    public static InputStream open(String path) {
        String p = clean(path);
        File ext = external(p);
        try {
            if (ext.isFile()) {
                return new FileInputStream(ext);
            }
        } catch (Exception ignored) {
            // 继续走 classpath
        }
        InputStream in = Assets.class.getResourceAsStream("/" + p);
        if (in != null) {
            return in;
        }
        if (platformLoader != null) {
            try {
                InputStream platform = platformLoader.apply(p);
                if (platform != null) {
                    return platform;
                }
            } catch (Exception ignored) {
                // 回退到工作目录
            }
        }
        try {
            return new FileInputStream(p);
        } catch (Exception ignored) {
            return EMPTY;
        }
    }

    /** 资源 URL（Media/AudioClip 等只收 URL 的接口用）。 */
    public static URL url(String path) {
        String p = clean(path);
        File ext = external(p);
        try {
            if (ext.isFile()) {
                return ext.toURI().toURL();
            }
        } catch (Exception ignored) {
            // 继续走 classpath
        }
        URL in = Assets.class.getResource("/" + p);
        if (in != null) {
            return in;
        }
        try {
            return new File(p).toURI().toURL();
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 该资源实际来自哪里（日志用）：res/…、jar/… 或 working-dir/…。 */
    public static String source(String path) {
        String p = clean(path);
        if (external(p).isFile()) {
            return "res/" + p;
        }
        if (Assets.class.getResource("/" + p) != null) {
            return "classpath:/" + p;
        }
        return new File(p).isFile() ? p : "missing:" + p;
    }

    private static String clean(String path) {
        String p = path == null ? "" : path.replace('\\', '/');
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        return p;
    }
}
