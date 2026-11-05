package com.hysx.salvosequence.live;

import com.hysx.salvosequence.live.nebula.NebulaLive;
import com.hysx.salvosequence.live.star.StarLive;

/**
 * 实时背景目录：背景工坊里能选、战斗中能直接当底图的那几个 3D 场景。
 *
 * <p>约定：{@code selected == -1} 表示"按节点自动挑"（战斗用），背景工坊里则是"程序化背景"。
 * 场景实例懒创建、渲染结果按时间缓存到 30fps，避免同一个场景一帧里被取两次。</p>
 */
public final class LiveBackdrops {

    /** 场景 id，稳定、可存配置。 */
    public static final String[] IDS = {"nebula", "star", "gleba", "pulsar"};
    private static final String[] NAMES_ZH = {"星云", "恒星", "星球", "脉冲星"};
    private static final String[] NAMES_EN = {"NEBULA", "STAR", "PLANET", "PULSAR"};

    private static LiveBackdrop[] scenes = new LiveBackdrop[IDS.length];
    private static int[][] lastFrame = new int[IDS.length][];
    private static double[] lastTime = new double[IDS.length];
    private static boolean[] lastValid = new boolean[IDS.length];
    /** 像素滤镜的缓存：原始帧 + 上一次过滤结果，避免 60fps 每帧重算整张（57.6k 像素）。 */
    private static int[][] lastPost = new int[IDS.length][];
    private static int[] lastPostStamp = new int[IDS.length];
    private static double[] lastPostTime = new double[IDS.length];

    private static int selected = -1;

    private LiveBackdrops() {
    }

    public static int count() {
        return IDS.length;
    }

    public static String id(int index) {
        return IDS[Math.max(0, Math.min(IDS.length - 1, index))];
    }

    public static String nameZh(int index) {
        return NAMES_ZH[Math.max(0, Math.min(NAMES_ZH.length - 1, index))];
    }

    public static String nameEn(int index) {
        return NAMES_EN[Math.max(0, Math.min(NAMES_EN.length - 1, index))];
    }

    /** -1 = 不指定（战斗按节点自动挑；工坊里表示程序化背景）。 */
    public static int selected() {
        return selected;
    }

    public static void setSelected(int index) {
        selected = index < 0 ? -1 : Math.min(index, IDS.length - 1);
    }

    public static int cycle(int direction) {
        int n = IDS.length + 1;   // +1 是"程序化/自动"
        int cur = selected + 1;
        selected = Math.floorMod(cur + direction, n) - 1;
        return selected;
    }

    /**
     * 战斗底图：用户指定了就用指定的，否则按节点 + 访问进度确定性地挑一个，
     * 保证同一个节点反复进出是同一张背景。
     */
    public static int sceneForBattle(int starId, int visitedCount) {
        if (selected >= 0) {
            return selected;
        }
        long h = starId * 0x9E3779B97F4A7C15L + visitedCount * 0x100000001B3L;
        int mixed = (int) ((h >>> 33) ^ (h >>> 17));
        return Math.floorMod(mixed, IDS.length);
    }

    /**
     * 取一帧：内部按 ~30fps 缓存，GL 还没就绪/出异常时返回 null，调用方退回程序化背景。
     */
    /** 这个场景当战斗底图时要压到原亮度的百分之多少（恒星/脉冲星太亮，压得狠一点）。 */
    public static int dimPercent(int index) {
        if (index < 0 || index >= IDS.length) {
            return 70;
        }
        return switch (IDS[index]) {
            case "star" -> 32;
            case "pulsar" -> 30;
            default -> 70;
        };
    }

    public static int[] frame(int index, double time) {
        if (index < 0 || index >= IDS.length) {
            return null;
        }
        // 背景没必要时时重算：15fps 已经比手绘的动画流畅，整帧耗时能省一半
        int[] cached = lastFrame[index];
        if (cached != null && lastValid[index] && Math.abs(time - lastTime[index]) < 1.0 / 15.0) {
            return post(index, cached, time);
        }
        try {
            if (scenes[index] == null) {
                scenes[index] = switch (IDS[index]) {
                    case "nebula" -> new NebulaLiveBackdrop();
                    case "star" -> new StarLiveBackdrop();
                    case "gleba" -> new GlebaLiveBackdrop();
                    case "pulsar" -> new PulsarLiveBackdrop();
                    default -> null;
                };
            }
            if (scenes[index] == null) {
                return null;
            }
            int[] f = scenes[index].frame(time);
            if (f != null) {
                lastFrame[index] = f;
                lastTime[index] = time;
                lastValid[index] = true;
            }
            return f == null ? null : post(index, f, time);
        } catch (RuntimeException e) {
            // GL 上下文出问题（例如安卓上的驱动差异）时静默退回程序化背景
            lastValid[index] = false;
            return null;
        }
    }

    /** 拜耳网点（实时帧只有 320×180，用同尺寸量化，不再 cover 缩放到 640×360）。 */
    private static final com.hysx.salvosequence.core.BayerPixelator.Params BAYER_PARAMS =
            new com.hysx.salvosequence.core.BayerPixelator.Params(1, 12, 70, 0, 0, 110, false);

    /**
     * 出图后处理：把 3D 场景的平滑输出压成程序化像素画。
     *
     * <p>走哪一种由 {@code VideoSettings.backdropFilter()} 决定——拜耳网点（项目原方案）
     * 或从 Godot 翻译来的像素滤镜（亮度分档 + Sobel 描边）。结果按
     * 「场景 + 原始帧时刻 + 滤镜状态戳」缓存，同一帧不重复算。</p>
     */
    private static int[] post(int index, int[] raw, double time) {
        com.hysx.salvosequence.ui.VideoSettings.BackdropFilter which =
                com.hysx.salvosequence.ui.VideoSettings.backdropFilter();
        int stamp = which.ordinal() * 1000003 + com.hysx.salvosequence.core.PixelArtFilter.stamp();
        if (lastPost[index] != null && lastPostStamp[index] == stamp
                && Math.abs(lastPostTime[index] - time) < 1.0e-9) {
            return lastPost[index];
        }
        int[] out = which == com.hysx.salvosequence.ui.VideoSettings.BackdropFilter.PIXEL
                ? com.hysx.salvosequence.core.PixelArtFilter.apply(raw, 320, 180)
                : com.hysx.salvosequence.core.BayerPixelator.quantizeInPlace(raw, 320, 180, BAYER_PARAMS);
        lastPost[index] = out;
        lastPostStamp[index] = stamp;
        lastPostTime[index] = time;
        return out;
    }

    // ------------------------------------------------------------ 调参与存档

    /** 场景的可调参数对象；场景还没建出来时先建实例（调参面板要读值）。 */
    public static Object params(int index) {
        LiveBackdrop scene = scene(index);
        return scene == null ? null : scene.params();
    }

    public static double[] camera(int index) {
        LiveBackdrop scene = scene(index);
        return scene == null ? new double[]{0, 0, 1} : scene.camera();
    }

    public static void setCamera(int index, double yaw, double pitch, double dist) {
        LiveBackdrop scene = scene(index);
        if (scene != null) {
            scene.setCamera(yaw, pitch, dist);
        }
    }

    /** 指针拖拽：dx/dy 是像素位移。 */
    public static void orbit(int index, double dx, double dy) {
        LiveBackdrop scene = scene(index);
        if (scene != null) {
            scene.orbit(dx, dy);
        }
    }

    /** 滚轮：notches 正数放大。 */
    public static void zoom(int index, double notches) {
        LiveBackdrop scene = scene(index);
        if (scene != null) {
            scene.zoom(notches);
        }
    }

    private static LiveBackdrop scene(int index) {
        if (index < 0 || index >= IDS.length) {
            return null;
        }
        if (scenes[index] == null) {
            scenes[index] = switch (IDS[index]) {
                case "nebula" -> new NebulaLiveBackdrop();
                case "star" -> new StarLiveBackdrop();
                case "gleba" -> new GlebaLiveBackdrop();
                case "pulsar" -> new PulsarLiveBackdrop();
                default -> null;
            };
        }
        return scenes[index];
    }

    public static void disposeAll() {
        for (int i = 0; i < scenes.length; i++) {
            if (scenes[i] != null) {
                scenes[i].dispose();
                scenes[i] = null;
            }
            lastFrame[i] = null;
            lastValid[i] = false;
        }
    }

    /** 星云：直接转调移植过来的渲染核心。 */
    private static final class NebulaLiveBackdrop implements LiveBackdrop {
        private final NebulaLive live = new NebulaLive();

        @Override
        public String id() {
            return "nebula";
        }

        @Override
        public String nameZh() {
            return "星云";
        }

        @Override
        public String nameEn() {
            return "NEBULA";
        }

        @Override
        public int[] frame(double time) {
            return live.render(time);
        }

        @Override
        public Object params() {
            return live.params();
        }

        @Override
        public void orbit(double dx, double dy) {
            live.orbit(dx, dy);
        }

        @Override
        public void zoom(double notches) {
            live.zoom(notches);
        }

        @Override
        public double[] camera() {
            return live.camera();
        }

        @Override
        public void setCamera(double yaw, double pitch, double dist) {
            live.setCamera(yaw, pitch, dist);
        }

        @Override
        public void dispose() {
            live.dispose();
        }
    }

    /** 脉冲星：中子星 + 磁极喷流，粒子较多，只在真的选到它时才建。 */
    private static final class PulsarLiveBackdrop implements LiveBackdrop {
        private final com.hysx.salvosequence.live.pulsar.PulsarLive live =
                new com.hysx.salvosequence.live.pulsar.PulsarLive();

        @Override
        public String id() {
            return "pulsar";
        }

        @Override
        public String nameZh() {
            return "脉冲星";
        }

        @Override
        public String nameEn() {
            return "PULSAR";
        }

        @Override
        public int[] frame(double time) {
            return live.render(time);
        }

        @Override
        public Object params() {
            return live.params();
        }

        @Override
        public void orbit(double dx, double dy) {
            live.orbit(dx, dy);
        }

        @Override
        public void zoom(double notches) {
            live.zoom(notches);
        }

        @Override
        public double[] camera() {
            return live.camera();
        }

        @Override
        public void setCamera(double yaw, double pitch, double dist) {
            live.setCamera(yaw, pitch, dist);
        }

        @Override
        public void dispose() {
            live.dispose();
        }
    }

    /** 星球：复用游戏里已有的 Gleba 3D 行星渲染器（生成中先返回 null，让战斗退回程序化背景）。 */
    private static final class GlebaLiveBackdrop implements LiveBackdrop {
        private final com.hysx.salvosequence.gleba.GlebaPlanet planet =
                new com.hysx.salvosequence.gleba.GlebaPlanet();
        private boolean started;

        @Override
        public String id() {
            return "gleba";
        }

        @Override
        public String nameZh() {
            return "星球";
        }

        @Override
        public String nameEn() {
            return "PLANET";
        }

        @Override
        public int[] frame(double time) {
            planet.ensureGl();
            if (!started) {
                started = true;
                planet.regen();
            }
            // update() 是"问生成线程好了没 + 传纹理"的地方，生成中也要调，
            // 否则永远等不到 isGenerating() 变 false
            planet.update(1.0 / 30.0);
            if (planet.isGenerating()) {
                return null;
            }
            return planet.renderFrame();
        }

        @Override
        public Object params() {
            return planet.params;
        }

        @Override
        public void orbit(double dx, double dy) {
            planet.orbitBy(dx, dy);
        }

        @Override
        public void zoom(double notches) {
            planet.zoomBy(notches);
        }

        @Override
        public double[] camera() {
            return planet.camera();
        }

        @Override
        public void setCamera(double yaw, double pitch, double dist) {
            planet.setCamera(yaw, pitch, dist);
        }

        @Override
        public void dispose() {
            planet.dispose();
        }
    }

    /** 恒星：相机拉远、恒星推到左上角，当"远处一颗太阳"。 */
    private static final class StarLiveBackdrop implements LiveBackdrop {
        private final StarLive live = new StarLive();

        @Override
        public String id() {
            return "star";
        }

        @Override
        public String nameZh() {
            return "恒星";
        }

        @Override
        public String nameEn() {
            return "STAR";
        }

        @Override
        public int[] frame(double time) {
            return live.render(time);
        }

        @Override
        public Object params() {
            return live.params();
        }

        @Override
        public void orbit(double dx, double dy) {
            live.orbit(dx, dy);
        }

        @Override
        public void zoom(double notches) {
            live.zoom(notches);
        }

        @Override
        public double[] camera() {
            return live.camera();
        }

        @Override
        public void setCamera(double yaw, double pitch, double dist) {
            live.setCamera(yaw, pitch, dist);
        }

        @Override
        public void dispose() {
            live.dispose();
        }
    }
}
