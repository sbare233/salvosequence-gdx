package com.hysx.salvosequence.graphics;

import com.hysx.salvosequence.core.PixelArt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 像素图的加载与缓存：按<b>命名约定</b>去类路径找 PNG，找不到就用
 * {@link SpriteFactory} 程序化生成一张占位图。
 *
 * <h2>命名约定</h2>
 * <pre>
 * sprites/&lt;角色&gt;.png                      静态图（1 帧）
 * sprites/&lt;角色&gt;_f&lt;帧数&gt;_&lt;宽&gt;x&lt;高&gt;.png      横向序列帧
 * </pre>
 * 例：{@code sprites/fx/explosion_f6_16x16.png} = 6 帧、每帧 16x16、横向排布。
 *
 * <p>三个关键设计：</p>
 * <ul>
 *   <li><b>角色名 = 类路径</b>：调用方只说 {@code "fx/explosion"}，
 *       不关心文件名里有没有帧数，也不关心换没换素材。</li>
 *   <li><b>帧数写在文件名里，所以永远不用读 {@code sprites.txt}</b>——
 *       素材丢了、表没更新，都不会导致加载失败（这是踩过的坑：
 *       图片和表一旦不同步，就会整批读错）。</li>
 *   <li><b>找不到就占位，不抛异常</b>：美术没到位时游戏照样跑得起来，
 *       有问题的素材名会记录在 {@link #report()} 里。</li>
 * </ul>
 *
 * <p>线程安全：所有读取都在 {@code synchronized} 内，加载是惰性的。</p>
 */
public final class SpriteStore {

    /** 资源根目录（类路径绝对路径）。 */
    public static final String ROOT = "/sprites/";

    /** 帧数标记：{@code _f4}。 */
    private static final Pattern FRAMES = Pattern.compile("_f(\\d+)(?![0-9])");

    /** 尺寸标记：{@code _16x16}。 */
    private static final Pattern SIZE = Pattern.compile("_(\\d+)x(\\d+)(?![0-9x])");

    /** 被解析器忽略的标记（给"这张图只是参考、不要切帧"之类的情况留后门）。 */
    private static final String IGNORE_MARK = "_noframes";

    private static final SpriteStore INSTANCE = new SpriteStore();

    private final Map<String, SpriteSheet> cache = new HashMap<>();
    private final List<String> warnings = new ArrayList<>();

    /** 资源缺失时是否打印一行日志（批量导出时关掉更清爽）。 */
    private boolean quiet;

    private SpriteStore() {
    }

    public static SpriteStore get() {
        return INSTANCE;
    }

    /** 静音模式（导出/测试里用）。 */
    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    /**
     * 取一套精灵表（带缓存）。
     *
     * @param role 角色名，如 "fx/explosion"
     * @return 精灵表；即使资源缺失也一定不为 null（会退化成占位图）
     */
    public synchronized SpriteSheet sheet(String role) {
        SpriteSheet cached = cache.get(role);
        if (cached != null) {
            return cached;
        }
        SpriteSheet loaded = loadFromClasspath(role);
        if (loaded == null) {
            loaded = SpriteFactory.create(role);
            if (loaded == null) {
                // 连程序化占位都没有：给一张 8x8 的告警方块，并在报告里留痕
                warn("[SpriteStore] 未知角色 \"" + role + "\"：既没有 PNG 也没有占位图，"
                        + "改用 8x8 告警块");
                loaded = SpriteSheet.single(role,
                        Sprite.filled(role, 8, 8, PixelArt.toArgb(
                                com.hysx.salvosequence.core.Palette.DANGER)));
            }
        }
        cache.put(role, loaded);
        return loaded;
    }

    /**
     * 取单帧。
     *
     * @param role  角色名
     * @param index 帧索引（自动取模）
     */
    public Sprite sprite(String role, int index) {
        return sheet(role).frame(index);
    }

    /**
     * 只取静态图角色的唯一一帧；如果意外是多帧素材，取第 0 帧并记一条警告。
     */
    public Sprite still(String role) {
        SpriteSheet s = sheet(role);
        if (s.frameCount() != 1) {
            warn("[SpriteStore] \"" + role + "\" 期望静态图，实际 " + s.frameCount() + " 帧，取第 0 帧");
        }
        return s.frame(0);
    }

    /** 资源是否已经在类路径上（用来判断该不该提示补素材）。 */
    public boolean has(String role) {
        return loadFromClasspath(role) != null;
    }

    /** 清空缓存（改完素材热重载用）。 */
    public synchronized void clear() {
        cache.clear();
        warnings.clear();
    }

    /**
     * 拉一遍所有已知角色，输出「谁用了真素材、谁还在占位」。
     *
     * <p>换素材之后最该看的就是这张表，能立刻发现文件名不合约定。</p>
     */
    public synchronized String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("角色                          帧数  尺寸      来源\n");
        sb.append("--------------------------------------------------------------\n");
        for (String role : SpriteFactory.ALL_ROLES) {
            SpriteSheet s = sheet(role);
            sb.append(String.format("%-28s %3d  %2dx%-5d %s%n",
                    role, s.frameCount(), s.frameWidth(), s.frameHeight(),
                    s.isPlaceholder() ? "占位（程序生成）" : s.source()));
        }
        if (!warnings.isEmpty()) {
            sb.append("\n警告:\n");
            for (String w : warnings) {
                sb.append("  ").append(w).append('\n');
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ 加载实现

    private SpriteSheet loadFromClasspath(String role) {
        List<String> candidates = findCandidates(role);
        if (candidates.isEmpty()) {
            return null;
        }
        // 1) 优先带帧数的表：一图多帧
        String best = null;
        int bestFrames = 1;
        for (String path : candidates) {
            Matcher m = FRAMES.matcher(path);
            if (m.find()) {
                int n = Integer.parseInt(m.group(1));
                if (n > bestFrames || best == null) {
                    best = path;
                    bestFrames = n;
                }
            } else if (best == null) {
                best = path;
            }
        }
        // 2) 都没写帧数但有多张图 -> 按文件名顺序当作逐帧序列
        List<String> plain = candidates.stream().filter(p -> !FRAMES.matcher(p).find()).toList();
        if (plain.size() > 1 && candidates.stream().noneMatch(p -> FRAMES.matcher(p).find())) {
            return loadSequence(role, plain);
        }
        if (best == null) {
            return null;
        }
        return loadSheet(role, best, readFrames(best));
    }

    /** 找类路径上所有 {@code /sprites/<role>*} 的候选文件。 */
    private List<String> findCandidates(String role) {
        List<String> out = new ArrayList<>();
        // 精确名（静态图）
        String exact = ROOT + role + ".png";
        if (resourceExists(exact)) {
            out.add(exact);
        }
        // 目录扫描：以 <文件名>_ 开头的都算候选
        int slash = role.lastIndexOf('/');
        String dir = slash < 0 ? ROOT : ROOT + role.substring(0, slash + 1);
        String prefix = (slash < 0 ? role : role.substring(slash + 1)) + "_";
        // 目录扫描：以 <文件名>_ 开头的都算候选。
        // 注意：打包进 jar 之后目录列举拿不到（协议不是 file），
        // 那时只剩上面的精确名可用——所以发布版建议用带帧数的那一个文件名。
        try {
            // 外部 res/sprites/ 优先：允许发布后直接替换美术资源
            java.io.File ext = com.hysx.salvosequence.core.Assets.external(dir);
            java.io.File[] files = ext.isDirectory()
                    ? ext.listFiles((dirFile, name) -> name.startsWith(prefix) && name.endsWith(".png")
                            && !name.contains(IGNORE_MARK))
                    : null;
            java.net.URL url = SpriteStore.class.getResource(dir);
            if (files == null && url != null && "file".equals(url.getProtocol())) {
                java.io.File d = new java.io.File(url.toURI());
                files = d.listFiles((dirFile, name) ->
                        name.startsWith(prefix) && name.endsWith(".png")
                                && !name.contains(IGNORE_MARK));
            }
            if (files != null) {
                java.util.Arrays.sort(files);
                for (java.io.File f : files) {
                    out.add(dir + f.getName());
                }
            }
        } catch (Exception ex) {
            warn("[SpriteStore] 扫描 " + dir + " 失败: " + ex.getMessage());
        }
        return out;
    }

    private boolean resourceExists(String path) {
        return com.hysx.salvosequence.core.Assets.url(path) != null;
    }

    /** 从文件名里取帧数，没写就是 1。 */
    private int readFrames(String path) {
        Matcher m = FRAMES.matcher(path);
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    /** 从文件名里取期望的帧尺寸，没写就是 0（表示"按整图 / 帧数"推算）。 */
    private int[] readSize(String path) {
        Matcher m = SIZE.matcher(path);
        if (m.find()) {
            return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        }
        return null;
    }

    private SpriteSheet loadSheet(String role, String path, int frames) {
        int[] argb;
        int imageW;
        int imageH;
        try (java.io.InputStream in = com.hysx.salvosequence.core.Assets.open(path)) {
            if (in == null || in == com.hysx.salvosequence.core.Assets.EMPTY) {
                warn("[SpriteStore] " + path + " 不存在");
                return null;
            }
            com.hysx.salvosequence.core.PngLoader.Bitmap img =
                    com.hysx.salvosequence.core.PngLoader.load(in);
            imageW = img.width();
            imageH = img.height();
            argb = img.argb();
        } catch (Exception ex) {
            warn("[SpriteStore] 读取 " + path + " 失败: " + ex);
            return null;
        }

        int[] expected = readSize(path);
        int fw;
        int fh;
        if (expected != null && expected[0] > 0 && expected[1] > 0
                && expected[0] * frames <= imageW && expected[1] <= imageH) {
            fw = expected[0];
            fh = expected[1];
        } else {
            fw = Math.max(1, imageW / frames);
            fh = imageH;
            if (expected != null) {
                warn("[SpriteStore] " + path + " 文件名写的是 " + expected[0] + "x" + expected[1]
                        + "，与图片实际 " + imageW + "x" + imageH + " 不符，按 "
                        + fw + "x" + fh + " 切");
            }
        }
        if (imageW % fw != 0) {
            warn("[SpriteStore] " + path + " 宽 " + imageW + " 不是帧宽 " + fw
                    + " 的整数倍，最右侧会有残缺帧");
        }

        Sprite whole = new Sprite(role, imageW, imageH, argb);
        List<Sprite> out = new ArrayList<>(frames);
        for (int i = 0; i < frames; i++) {
            out.add(whole.sub(role + "#" + i, i * fw, 0, fw, fh));
        }
        return new SpriteSheet(role, out, path, false);
    }

    private SpriteSheet loadSequence(String role, List<String> paths) {
        List<Sprite> frames = new ArrayList<>(paths.size());
        int fw = -1;
        int fh = -1;
        for (String path : paths) {
            SpriteSheet one = loadSheet(role, path, 1);
            if (one == null) {
                continue;
            }
            Sprite s = one.frame(0);
            if (fw < 0) {
                fw = s.width();
                fh = s.height();
            } else if (s.width() != fw || s.height() != fh) {
                warn("[SpriteStore] " + path + " 尺寸 " + s.width() + "x" + s.height()
                        + " 与首帧 " + fw + "x" + fh + " 不一致，已跳过");
                continue;
            }
            frames.add(s);
        }
        if (frames.isEmpty()) {
            return null;
        }
        return new SpriteSheet(role, frames, String.join(" + ", paths), false);
    }

    private void warn(String message) {
        warnings.add(message);
        if (!quiet) {
            System.out.println(message);
        }
    }
}
