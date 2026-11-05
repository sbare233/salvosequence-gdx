package com.hysx.salvosequence.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 星图背景：<code>mapback/</code> 里的 1440 张星系盘角度帧。
 *
 * <p>这组图是同一个星系盘（两臂螺旋 + 核球）绕盘法线每旋转 0.25° 渲染一帧，
 * 640×360、已经过拜耳量化，<b>直接整幅贴，不要再抖一次</b>。文件名形如
 * <code>frame_000_000.0deg-x1-l12.png</code>：序号零填充到 3 位、角度按 1 位小数四舍五入。</p>
 *
 * <p>1440 张全解成 ARGB 要 1.3GB，这里只保留最近用过的
 * {@value #CACHE_LIMIT} 帧（约 15MB），拖动时按角度取最近的一帧解码。
 * 解码失败（缺图 / 坏图）的帧记进黑名单，不会每帧重试。</p>
 */
public final class StarDisk {

    /** 帧图尺寸。 */
    public static final int IMAGE_W = 640;
    public static final int IMAGE_H = 360;
    /** 一共 1440 帧，一圈 360°。 */
    public static final int FRAMES = 1440;
    /** 每帧之间的角度（度）。 */
    public static final double STEP_DEG = 360.0 / FRAMES;
    /** 星系盘中心（像素）：第 0 帧核球亮区质心量得 177.4，当前按肉眼校准取 179。 */
    public static final double CENTER_X = 320;
    public static final double CENTER_Y = 179;
    /** 相机与盘面的夹角（度）：30° 时盘面中心压扁成 sin30° = 0.5。 */
    public static final double ELEVATION_DEG = 30.0;
    /**
     * 消失点（屏幕像素）：盘面越往远侧延伸，投影越收敛到这一点。背景帧实测落在 y = -40 ~ -50 之间，
     * 到底哪一档不确定，所以 Y 做成运行时可调：星图里按 [ / ] 每次挪 1px，按 \ 复位到默认值。
     */
    public static final double VANISH_X = 320;
    /** 消失点 Y 的默认值（当前按肉眼校准）。 */
    public static final double DEFAULT_VANISH_Y = -54;
    private static double vanishY = DEFAULT_VANISH_Y;

    /** 当前消失点 Y：越往上（越负）相机越远、透视越平；越靠近盘心，相机越近、透视越夸张。 */
    public static double vanishY() {
        return vanishY;
    }

    public static void setVanishY(double y) {
        vanishY = y;
    }

    /**
     * 相机到盘心的距离（盘面像素）：由消失点反推 —— 消失点比盘心高 {@code D·tan(俯角)}，
     * 所以 {@code D = (CENTER_Y - vanishY) / tan(30°)}：消失点越往上（越负），D 越大、透视越平。
     */
    public static double cameraDistance() {
        return (CENTER_Y - vanishY) / Math.tan(Math.toRadians(ELEVATION_DEG));
    }
    /**
     * 盘面坐标的整体缩放：节点的半径坐标最大 246，而按下面的相机参数，246 的盘面半径在
     * 近侧会被推到屏幕外（y ≈ 420），所以整体缩到 0.76，最外圈节点正好压在星系旋臂上。
     */
    public static final double PLANE_SCALE = 0.76;
    /**
     * 帧序号与旋转角的符号。实测这批图的旋臂图案（m=2 分量）在序号增大时是<b>逆时针</b>转，
     * 而节点盘的角度增大是屏幕上的顺时针，所以取 -1 让两边同向。
     */
    public static final int FRAME_SIGN = -1;

    /**
     * 缓存帧数。一帧 640x360 的 ARGB 是 0.92MB，48 帧约 44MB——留得比"当前帧"多，
     * 拖动时方向一变也不会立刻把刚用过的帧挤掉。
     */
    private static final int CACHE_LIMIT = 48;
    /** 相邻未就绪时最多退多少档去借一张已缓存的帧（12 档 = 3°，肉眼几乎看不出跳）。 */
    private static final int NEAREST_FALLBACK = 12;
    private static final String DIR = "mapback";

    /** 最近一次真正返回给画面的帧：目标帧没就绪时拿它顶着，避免同步解码卡渲染线程。 */
    private static volatile PngLoader.Bitmap lastShown;

    private static final Map<Integer, PngLoader.Bitmap> CACHE =
            new LinkedHashMap<Integer, PngLoader.Bitmap>(CACHE_LIMIT * 2, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, PngLoader.Bitmap> eldest) {
                    return size() > CACHE_LIMIT;
                }
            };
    private static final Map<Integer, Boolean> BROKEN = new HashMap<>();
    /**
     * 当前真正要显示的那一帧。快速拖动时目标每帧都在跳，排着的旧角度已经没人要了——
     * 工作线程只认这个值，解完一张发现目标又变了就接着解新的，绝不把时间花在过期帧上。
     */
    private static volatile int latestWanted = -1;

    /** 后台只有一个解码任务在跑；已经在追就不重复排（见 {@link #requestLatest(int)}）。 */
    private static final java.util.concurrent.atomic.AtomicBoolean WORKER_RUNNING =
            new java.util.concurrent.atomic.AtomicBoolean();

    /** 后台解码线程（守护线程，随进程退出）。 */
    private static final java.util.concurrent.ExecutorService LOADER =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "star-disk-loader");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });

    private StarDisk() {
    }

    /**
     * 把盘面坐标（像素，u 向右、v 向下为正）投影到屏幕：俯角 {@link #ELEVATION_DEG} 的<b>透视</b>投影。
     *
     * <p>相机在盘心下方 {@code D·cos(e)}、上方 {@code D·sin(e)} 处看向盘心，于是近侧（v &gt; 0）
     * 离相机更近、被放大，远侧被压缩——这就是「3D 圆形平面」和纯压扁的区别。</p>
     *
     * @return {@code {屏幕 x, 屏幕 y}}，盘心落在 {@link #CENTER_X}/{@link #CENTER_Y}
     */
    public static double[] project(double u, double v) {
        double su = u * PLANE_SCALE;
        double sv = v * PLANE_SCALE;
        double sin = Math.sin(Math.toRadians(ELEVATION_DEG));
        double k = depthScale(v);
        return new double[]{CENTER_X + su * k, CENTER_Y + sv * sin * k};
    }

    /**
     * 盘面横坐标 {@code v}（像素，未乘 {@link #PLANE_SCALE}）处的透视放大系数：近侧 &gt; 1、远侧 &lt; 1。
     * 画盘面上的小星星时用它决定点的大小——近处的星星看着更大更亮。
     */
    public static double depthScale(double v) {
        double cos = Math.cos(Math.toRadians(ELEVATION_DEG));
        double distance = cameraDistance();
        double depth = distance - v * PLANE_SCALE * cos;
        if (depth < 1) {
            depth = 1;
        }
        return distance / depth;
    }

    /** 角度（弧度）→ 最近的一帧序号；负角与超出一圈都先归一化。 */
    public static int frameIndexFor(double radians) {
        double twoPi = Math.PI * 2;
        double a = radians % twoPi;
        if (a < 0) {
            a += twoPi;
        }
        int index = (int) Math.round(Math.toDegrees(a) / STEP_DEG) * FRAME_SIGN;
        return ((index % FRAMES) + FRAMES) % FRAMES;
    }

    /**
     * 取某个角度对应的帧。目标帧还在后台解码时，返回<b>最近一张已就绪</b>的帧
     * （{@link #NEAREST_FALLBACK} 档以内），这样快速拖动不会一卡一卡地空白。
     */
    public static PngLoader.Bitmap frame(double radians) {
        int index = frameIndexFor(radians);
        PngLoader.Bitmap exact = frameAt(index);
        if (exact != null) {
            lastShown = exact;
            return exact;
        }
        for (int d = 1; d <= NEAREST_FALLBACK; d++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                PngLoader.Bitmap near = cached(normalize(index + d * sign));
                if (near != null) {
                    lastShown = near;
                    return near;
                }
            }
        }
        // 冷启动（一帧都没解过）：同步解一次，否则第一帧是黑屏
        if (CACHE.isEmpty()) {
            PngLoader.Bitmap loaded = loadNow(index);
            if (loaded != null) {
                lastShown = loaded;
            }
            return loaded;
        }
        // 快速拖动跳得太远、附近 12 档都没就绪：退到缓存里"角度最近"的那一张。
        // 不能同步解码（渲染线程上 3ms 的洞），也不能随便拿最后一张：那可能落后几十度，
        // 等目标帧解出来再跳回去，看上去就是"突一下"。取最近的一档，误差最小、跳得最轻。
        PngLoader.Bitmap nearest = nearestCached(index);
        if (nearest != null) {
            lastShown = nearest;
        }
        return nearest != null ? nearest : lastShown;
    }

    /**
     * 预告一个即将用到的角度：把对应帧和左右各两档排进后台解码队列。
     *
     * <p>拖动时节流后要等 0.1 秒才换图，这段时间正好够后台把目标帧解出来，
     * 真正换图时就不会退到邻近帧（画面看着像跳了一下）。</p>
     */
    public static void prefetch(double radians) {
        requestLatest(frameIndexFor(radians));
    }

    /**
     * 按序号取帧：命中就返回，未命中就丢给后台线程解码并返回 null。
     *
     * <p>解码一张 640x360 PNG 约 3ms；拖动时每帧要换好几档，同步解会直接卡住。
     * 后台单线程 + 预取邻近几档，把这段时间藏到渲染间隙里。</p>
     */
    public static PngLoader.Bitmap frameAt(int index) {
        int key = normalize(index);
        PngLoader.Bitmap cached = cached(key);
        if (cached != null) {
            return cached;
        }
        if (broken(key)) {
            return null;
        }
        requestLatest(key);
        return null;
    }

    private static int normalize(int index) {
        return ((index % FRAMES) + FRAMES) % FRAMES;
    }

    /** 缓存里角度上离 {@code index} 最近的一帧（命中不了精确帧时的兜底）。 */
    private static PngLoader.Bitmap nearestCached(int index) {
        PngLoader.Bitmap best = null;
        int bestD = Integer.MAX_VALUE;
        synchronized (CACHE) {
            for (Map.Entry<Integer, PngLoader.Bitmap> e : CACHE.entrySet()) {
                int d = Math.abs(e.getKey() - index);
                d = Math.min(d, FRAMES - d);
                if (d < bestD) {
                    bestD = d;
                    best = e.getValue();
                }
            }
        }
        return best;
    }

    private static PngLoader.Bitmap cached(int key) {
        synchronized (CACHE) {
            return CACHE.get(key);
        }
    }

    private static boolean broken(int key) {
        synchronized (BROKEN) {
            return BROKEN.containsKey(key);
        }
    }

    /**
     * 记住"现在要看哪一帧"，并确保后台正在追它。
     *
     * <p>不做排队：解码一张要 3ms，而快速拖动时每帧都可能指向完全不同的角度。
     * 排一堆旧帧只会把最新那帧堵在后面，于是画面先停住、等新帧解出来后猛地跳到
     * 目标角度——看上去就是"突变"。这里改成"永远只解最新的目标"。</p>
     */
    private static void requestLatest(int key) {
        latestWanted = key;
        if (WORKER_RUNNING.get()) {
            return;   // 已经在追了，它每解完一张会自己再看一眼最新目标
        }
        if (cached(key) != null || broken(key)) {
            return;
        }
        if (!WORKER_RUNNING.compareAndSet(false, true)) {
            return;
        }
        LOADER.execute(StarDisk::drainLatest);
    }

    /** 工作线程主体：解一张 → 目标若又变了接着解，直到追平。 */
    private static void drainLatest() {
        try {
            while (true) {
                int key = latestWanted;
                if (key < 0 || cached(key) != null || broken(key)) {
                    return;
                }
                loadNow(key);
                if (latestWanted == key) {
                    return;   // 追平了收工；下次要新角度会重新排
                }
            }
        } finally {
            WORKER_RUNNING.set(false);
            // 收工瞬间目标可能又变了：补排一次，别让画面停在旧帧上
            int key = latestWanted;
            if (key >= 0 && cached(key) == null && !broken(key)) {
                requestLatest(key);
            }
        }
    }

    /** 同步解码一张（冷启动与后台线程共用）。 */
    private static PngLoader.Bitmap loadNow(int key) {
        if (broken(key)) {
            return null;
        }
        PngLoader.Bitmap loaded;
        try (InputStream in = Assets.open(fileName(key))) {
            loaded = PngLoader.load(in);
        } catch (IOException | RuntimeException e) {
            synchronized (BROKEN) {
                BROKEN.put(key, Boolean.TRUE);
            }
            return null;
        }
        synchronized (CACHE) {
            CACHE.put(key, loaded);
        }
        return loaded;
    }

    /** 序号 → 资源路径：<code>frame_007_001.8deg-x1-l12.png</code>（角度占 5 格、.1f 四舍五入）。 */
    static String fileName(int index) {
        double deg = index * STEP_DEG;
        return DIR + "/frame_" + String.format("%03d", index)
                + "_" + String.format("%05.1f", deg) + "deg-x1-l12.png";
    }

    /** 清空缓存（单测用）。 */
    static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
        synchronized (BROKEN) {
            BROKEN.clear();
        }
        lastShown = null;
        latestWanted = -1;
    }
}
