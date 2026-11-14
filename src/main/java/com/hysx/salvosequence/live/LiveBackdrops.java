package com.hysx.salvosequence.live;

import com.hysx.salvosequence.live.nebula.AlroNebulaLive;
import com.hysx.salvosequence.live.nebula.CrabNebulaLive;
import com.hysx.salvosequence.live.nebula.IntergalacticCloudsLive;
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
    public static final String[] IDS = {"nebula", "star", "gleba", "pulsar", "blackhole",
            "intergalactic", "crab", "alro"};
    private static final String[] NAMES_ZH = {"星云", "恒星", "星球", "脉冲星", "黑洞",
            "星际云", "蟹状星云", "体积星云"};
    private static final String[] NAMES_EN = {"NEBULA", "STAR", "PLANET", "PULSAR", "BLACK HOLE",
            "INTERGALACTIC", "CRAB", "VOLUMETRIC"};

    private static LiveBackdrop[] scenes = new LiveBackdrop[IDS.length];
    private static int[][] lastFrame = new int[IDS.length][];
    private static double[] lastTime = new double[IDS.length];
    private static boolean[] lastValid = new boolean[IDS.length];
    /** 像素滤镜的缓存：原始帧 + 上一次过滤结果，避免 60fps 每帧重算整张（57.6k 像素）。 */
    private static int[][] lastPost = new int[IDS.length][];
    private static int[] lastPostStamp = new int[IDS.length];
    private static double[] lastPostTime = new double[IDS.length];

    private static int selected = -1;

    /** 星球场景用哪种类型（地球/海洋/荒漠/冰封/岩质）；每场战斗掷一次。 */
    private static com.hysx.salvosequence.gleba.gen.PlanetTheme planetTheme =
            com.hysx.salvosequence.gleba.gen.PlanetTheme.EARTH;

    public static void setPlanetTheme(com.hysx.salvosequence.gleba.gen.PlanetTheme t) {
        planetTheme = t == null ? com.hysx.salvosequence.gleba.gen.PlanetTheme.EARTH : t;
    }

    public static com.hysx.salvosequence.gleba.gen.PlanetTheme planetTheme() {
        return planetTheme;
    }

    /** 星球场景上的巨构（原始 / 巢都 / 矿业星 / 加速器 / 太空电梯），下标同 PlanetBuild。 */
    private static int planetBuild;

    public static void setPlanetBuild(int index) {
        planetBuild = Math.floorMod(index, com.hysx.salvosequence.gleba.gen.PlanetBuild.count());
    }

    public static int planetBuild() {
        return planetBuild;
    }

    /** 当前生效的机位（战斗开始时抽的那条），供调试键/工坊显示。 */
    private static CameraPresets.Preset activePreset;

    public static CameraPresets.Preset preset() {
        return activePreset;
    }

    /**
     * 套一条机位：设相机（含视场）并顺带切换星球类型。
     *
     * <p>机位是"每场仗随机抽一次"的，所以这里只在换关/换节点或调试键切换时调用，
     * 不要每帧调——否则刚拖好的视角会被立刻拽回去。</p>
     */
    public static void applyPreset(CameraPresets.Preset p) {
        activePreset = p;
        if (p == null) {
            return;
        }
        LiveBackdrop s = scene(p.scene);
        if (s != null) {
            // 星球统一拉近一档：原来那组距离放在 320×180 的战斗底图里太空，
            // 行星只占中间一小块；2.3R 是近地轨道的极限（再近球体就被裁掉了）
            float d = p.dist;
            if (p.scene == 2) {
                d = Math.max(2.3f, p.dist * 0.78f);
            }
            s.setCamera(p.yaw, p.pitch, d);
            s.setLens(p.fov);
            // 恒星：光谱类型与构图（只改距离的话，太阳永远钉在同一个角上）
            if (p.starType >= 0) {
                s.setStarType(p.starType);
            }
            if (p.frameX >= 0) {
                s.setFraming(p.frameX, p.frameY);
            }
            // 星球：太阳方位与机位的关系是实测出来的（sun = 90° - yaw 时正对镜头），
            // 再偏 40° 让晨昏线落进画面——正对镜头会拍成一张没有立体感的"证件照"
            s.setSunLight(50.0 - p.yaw);
        }
        if (p.theme >= 0) {
            setPlanetTheme(com.hysx.salvosequence.gleba.gen.PlanetTheme.byIndex(p.theme));
        }
    }

    /**
     * 按场景掷一次"随机外观"：中子星的转速、星云的配色。
     *
     * <p>这两项和机位不同——机位是预设表里挑的，而转速/配色是连续量，
     * 每场仗掷一次才有"同一颗星今天转得快"的感觉；调用方传同一个随机源，
     * 同一次航程可复现。</p>
     */
    public static void randomizeForBattle(int scene, java.util.Random rng) {
        if (rng == null) {
            return;
        }
        if ("pulsar".equals(id(scene))) {
            LiveBackdrop s = scene(scene);
            if (s != null) {
                // 0.35–2.35：从"几乎不转"到"转得发虚"，肉眼能分辨
                s.setSpinSpeed(0.35 + rng.nextDouble() * 2.0);
            }
        } else if ("nebula".equals(id(scene))) {
            LiveBackdrop s = scene(scene);
            if (s != null) {
                // 6 套配色随机 + ±18° 色相抖动：既有整块换色，也有同色系的冷暖差异
                s.setPalette(rng.nextInt(
                        com.hysx.salvosequence.live.nebula.Params.PALETTE_THIN.length));
                s.setHue((rng.nextDouble() * 2 - 1) * 18.0);
            }
        } else if ("gleba".equals(id(scene))) {
            LiveBackdrop s = scene(scene);
            if (s != null) {
                // 环只偶尔出现：带环的行星最好认，但每场都挂就成了"每局都一样"的底噪，
                // 大部分战斗让行星自己撑住画面（约 1/3 的场次带环）
                boolean ringed = rng.nextFloat() < 0.34f;
                s.setRings(ringed);
                if (!ringed) {
                    return;
                }
                // 环系每场换一套：5 种剖面 + 半径/透明度/倾角都掷一遍，同一颗星球
                // 两次战斗里也不会是同一条环（环是所有背景里最好认的身份标识）
                int style = rng.nextInt(RING_SPANS.length);
                float[] span = RING_SPANS[style];
                s.setRingStyle(style);
                s.setRingSpan(span[0],
                        span[1] * (0.92f + rng.nextFloat() * 0.16f),
                        span[2] * (0.78f + rng.nextFloat() * 0.44f));
                // 倾角：多数只是贴着赤道斜一点，偶尔来一颗躺着转的（极环）
                s.setRingTilt(rng.nextFloat() < 0.18f
                        ? (rng.nextBoolean() ? 1f : -1f) * (56f + rng.nextFloat() * 14f)
                        : (rng.nextFloat() * 2f - 1f) * 34f);
                // 同一种剖面里再掷一条具体的环：带的位置/宽窄/亮度/细纹相位全变
                s.setRingSeed(rng.nextFloat() * 97f, 2 + rng.nextInt(3));
            }
        }
    }

    /** 每种环系剖面的半径范围与基础透明度，和 Rings 着色器里的 profile 分支一一对应。 */
    private static final float[][] RING_SPANS = {
            {1.34f, 2.20f, 0.46f},   // 0 土星式主环
            {1.44f, 2.62f, 0.56f},   // 1 细亮丝
            {1.46f, 2.10f, 0.34f},   // 2 双环
            {1.26f, 2.44f, 0.30f},   // 3 宽尘环
            {1.38f, 2.32f, 0.40f},   // 4 断续弧环
    };

    static {
        // 截图/调参钩子：-Dsalvo.backdrop=gleba 之类可以钉死战斗底图，不受随机掷骰影响
        String forced = System.getProperty("salvo.backdrop", "");
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(forced)) {
                selected = i;
            }
        }
    }

    /** 自动漂移开关：战斗里开，背景工坊里关（见 {@link #setDriftEnabled(boolean)}）。 */
    private static boolean driftEnabled = true;

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
     * 只算下一个索引、不动全局 {@link #selected}——背景工坊的预览用它，
     * 这样在工坊里翻场景不会把战斗底图钉死（战斗仍然按场随机）。
     */
    public static int cyclePreview(int current, int direction) {
        int n = IDS.length + 1;
        return Math.floorMod(current + 1 + direction, n) - 1;
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
     * 每场战斗随机挑一个天体当底图：调用方（战斗界面）在换关/换节点时掷一次，
     * 掷完把结果存住，别每帧都掷——否则背景会一秒换一个。
     *
     * <p>用调用方传进来的随机源，这样同一次航程里可以复现；背景工坊里手动指定的
     * 场景优先于随机。</p>
     */
    public static int randomScene(java.util.Random rng) {
        if (selected >= 0) {
            return selected;
        }
        if (rng == null) {
            return Math.floorMod((int) (Math.random() * 1024), IDS.length);
        }
        return rng.nextInt(IDS.length);
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
            // 恒星/脉冲星压得太狠时，新加的深空星点会整片消失，主体也糊成一团；
            // 46 还能看出星空层次，又不至于亮到抢棋盘上的读数。
            case "star" -> 80;
            // 脉冲星是全场最亮的那个天体，压到 44 会读成一颗灰月亮；100 才像"曝光的白球"。
            case "pulsar" -> 100;
            // 合成视口修好之后星云帧是全幅的（以前窗口一大就只剩放大的一个角），
            // 亮度要压得更低才不会透过半透明棋盘跟棋子抢读：66% 还能看出云的结构。
            // 棋盘更透之后星云透上来的比重变大，压暗相应回落到 56，保持原来那层"暗云"。
            case "nebula" -> 56;
            case "intergalactic" -> 70;
            case "crab" -> 90;
            // 体积星云本身偏暗（深蓝底 + 局部亮云），压暗太多会整片变黑，先按 78 试。
            case "alro" -> 78;
            // 星球那张有亮面的球体，压到 70 仍然会在棋盘后面晃眼，58 才退成"远景"。
            // 星球没有黑洞天空当第二光源后整颗都暗了，压暗回到 88 才读得出大气与夜面。
            case "gleba" -> 88;
            // 黑洞本体是纯黑，只有吸积盘一圈亮；94 才让那圈暖光真的发亮。
            case "blackhole" -> 94;
            default -> 70;
        };
    }

    /** 调试：最近一次渲染出的原始帧（过滤前），用来判断杂色来自着色器还是像素滤镜。 */
    public static int[] lastRaw(int index) {
        if (index < 0 || index >= lastFrame.length) {
            return null;
        }
        return lastFrame[index];
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
                    case "blackhole" -> new BlackHoleLiveBackdrop();
                    case "intergalactic" -> new IntergalacticLiveBackdrop();
                    case "crab" -> new CrabLiveBackdrop();
                    case "alro" -> new AlroLiveBackdrop();
                    default -> null;
                };
            }
            if (scenes[index] == null) {
                return null;
            }
            int[] f = scenes[index].frame(time);
            if (f != null) {
                // 恒星/星球/脉冲星自己只画主体，背景是空的：垫一层 3D 深空
                // （星云场景的星点由着色器出，不能重复叠）。这一层挂在世界坐标的
                // 天球上：银河带是一条大圆，星是无限远天球上的方向向量，全部按
                // 场景相机朝向投影。镜头绕行星转时整片星空跟着转，行星（包括夜面）
                // 背后的星和银河带一颗都不会透出来。
                SkyView view = scenes[index].skyView(time);
                if (DeepSky.has(index) && view != null) {
                    DeepSky.sky(index, f, time, view);
                }
                if (Boolean.getBoolean("salvo.disc")) {
                    float[] disc = scenes[index].screenDisc();
                    System.out.println("[disc] " + IDS[index] + " "
                            + (disc == null ? "null"
                            : String.format("%.1f,%.1f r=%.1f", disc[0], disc[1], disc[2])));
                }
                lastFrame[index] = f;
                lastTime[index] = time;
                lastValid[index] = true;
            }
            return f == null
                    ? (lastFrame[index] != null ? post(index, lastFrame[index], time) : null)
                    : post(index, f, time);
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

    /**
     * 自动水平漂移（星云机位绕云缓慢转圈）。
     *
     * <p>战斗里开着，画面才是活的；背景工坊里必须关掉——时间一直在走，自动漂移会把用户刚
     * 拖到的角度持续带走，看起来就像"拖拽根本抓不住星云"。</p>
     */
    public static void setDriftEnabled(boolean on) {
        driftEnabled = on;
    }

    /** 滚轮：notches 正数放大。 */
    public static void zoom(int index, double notches) {
        LiveBackdrop scene = scene(index);
        if (scene != null) {
            scene.zoom(notches);
        }
    }

    /**
     * 调试/探针用：把某个场景的星环钉成指定样式（不经过随机掷骰）。
     *
     * @param style 0 土星式主环 / 1 细亮丝 / 2 双环 / 3 宽尘环 / 4 断续弧环
     */
    public static void debugRings(int index, int style, float inner, float outer,
                                  float opacity, float tilt) {
        debugRings(index, style, inner, outer, opacity, tilt, 0f, 3);
    }

    /** 同上，外加环系种子与细丝条数（调参/探针用）。 */
    public static void debugRings(int index, int style, float inner, float outer,
                                  float opacity, float tilt, float seed, int bands) {
        LiveBackdrop s = scene(index);
        if (s == null) {
            return;
        }
        s.setRingStyle(style);
        s.setRings(true);
        s.setRingSpan(inner, outer, opacity);
        s.setRingTilt(tilt);
        s.setRingSeed(seed, bands);
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
                case "blackhole" -> new BlackHoleLiveBackdrop();
                case "intergalactic" -> new IntergalacticLiveBackdrop();
                case "crab" -> new CrabLiveBackdrop();
                case "alro" -> new AlroLiveBackdrop();
                default -> null;
            };
        }
        return scenes[index];
    }

    /** 调试/截图用：直接给星云指定配色与色相偏移。 */
    public static void scenePalette(int palette, double hueDeg) {
        LiveBackdrop s = scene(0);
        if (s != null) {
            s.setPalette(palette);
            s.setHue(hueDeg);
        }
    }

    /** 调试/截图用：直接给中子星指定转速。 */
    public static void sceneSpin(double speed) {
        LiveBackdrop s = scene(3);
        if (s != null) {
            s.setSpinSpeed(speed);
        }
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
        public void setPalette(int index) {
            live.params().palette = Math.floorMod(index,
                    com.hysx.salvosequence.live.nebula.Params.PALETTE_THIN.length);
        }

        @Override
        public void setHue(double degrees) {
            live.params().hueDeg = (float) degrees;
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
            live.setDriftEnabled(driftEnabled);
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

    /** 星际云：sebastien durand 的螺旋噪声云，和原版星云并列。 */
    private static final class IntergalacticLiveBackdrop implements LiveBackdrop {
        private final IntergalacticCloudsLive live = new IntergalacticCloudsLive();

        @Override
        public String id() {
            return "intergalactic";
        }

        @Override
        public String nameZh() {
            return "星际云";
        }

        @Override
        public String nameEn() {
            return "INTERGALACTIC";
        }

        @Override
        public int[] frame(double time) {
            live.setDriftEnabled(driftEnabled);
            return live.render(time);
        }

        @Override
        public Object params() {
            return null;
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
        public void setLens(double fov) {
            live.setLens((float) fov);
        }

        @Override
        public void dispose() {
            live.dispose();
        }
    }

    /** 蟹状星云：Duke 的超新星遗迹，70 步 raymarch。 */
    private static final class CrabLiveBackdrop implements LiveBackdrop {
        private final CrabNebulaLive live = new CrabNebulaLive();

        @Override
        public String id() {
            return "crab";
        }

        @Override
        public String nameZh() {
            return "蟹状星云";
        }

        @Override
        public String nameEn() {
            return "CRAB";
        }

        @Override
        public int[] frame(double time) {
            live.setDriftEnabled(driftEnabled);
            return live.render(time);
        }

        @Override
        public Object params() {
            return null;
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
        public void setLens(double fov) {
            live.setLens((float) fov);
        }

        @Override
        public void dispose() {
            live.dispose();
        }
    }

    /** 体积星云：al-ro 的 gyroid 气壳 + 大气散射，72 步 raymarch，光沿太阳方向再步进一次。 */
    private static final class AlroLiveBackdrop implements LiveBackdrop {
        private final AlroNebulaLive live = new AlroNebulaLive();

        @Override
        public String id() {
            return "alro";
        }

        @Override
        public String nameZh() {
            return "体积星云";
        }

        @Override
        public String nameEn() {
            return "VOLUMETRIC";
        }

        @Override
        public int[] frame(double time) {
            live.setDriftEnabled(driftEnabled);
            return live.render(time);
        }

        @Override
        public Object params() {
            // 体积星云有配色档位（冷调 / 原版），工坊和 F2 菜单都靠它读写
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
        public void setLens(double fov) {
            live.setLens((float) fov);
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
        public void setSpinSpeed(double speed) {
            live.params().spinSpeed = (float) Math.max(0.15, Math.min(3.0, speed));
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
        public SkyView skyView(double time) {
            // 脉冲星相机瞄的是 -2.1,-0.9,0（喷流偏轴），不是原点，所以不带主体半径：
            // 星星按这个朝向投影，遮挡交给黑洞/脉冲星自己的画面层次，不在这里抠圆
            double[] cam = live.camera();
            return new SkyView(cam[0], cam[1], 45.0, cam[2], 0.0);
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

    /** 黑洞：独立的 Gargantua 远场渲染，不挂行星（行星是另一个场景）。 */
    private static final class BlackHoleLiveBackdrop implements LiveBackdrop {
        private final com.hysx.salvosequence.celestial.BlackHoleLive live =
                new com.hysx.salvosequence.celestial.BlackHoleLive();

        @Override
        public String id() {
            return "blackhole";
        }

        @Override
        public String nameZh() {
            return "黑洞";
        }

        @Override
        public String nameEn() {
            return "BLACK HOLE";
        }

        @Override
        public int[] frame(double time) {
            return live.render(time);
        }

        @Override
        public Object params() {
            return live.params;
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
        public void setLens(double fov) {
            if (fov > 0) {
                live.params.fov = (float) Math.max(25.0, Math.min(90.0, fov));
            }
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
        /** 上一帧的场景时钟（秒），用来算真实步长；负数表示还没跑过。 */
        private double lastPlanetTime = -1;
        private com.hysx.salvosequence.gleba.gen.PlanetTheme theme;
        /** 当前太阳方位（度）：换主题会重建行星，重建后要把这个方位重新摆回去。 */
        private float sunAzimuth = 78f;
        /** 掷过的环系：主题重建会把环参数重置回预设，用它盖回去。 */
        private boolean ringPinned;
        private boolean ringOn = true;
        private int ringStyle;
        private float ringInner = 1.34f, ringOuter = 2.20f, ringOpacity = 0.46f, ringTilt;
        private float ringSeed;
        private int ringBands = 3;

        GlebaLiveBackdrop() {
            // 行星场景只画行星：黑洞是独立的场景（见 BlackHoleLiveBackdrop），
            // 以前行星背后挂着 Gargantua 天空，看着像"黑洞长了一颗星球"。
            planet.showSky = false;
            // 没了吸积盘当第二光源，把太阳摆到镜头这一侧，行星才不会只剩一条亮边。
            planet.setSun(78f, 16f);
        }

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
            // 星球类型由战斗开始时掷出（LiveBackdrops.setPlanetTheme），换类型要重新生成
            com.hysx.salvosequence.gleba.gen.PlanetTheme want = planetTheme;
            String forced = System.getProperty("salvo.theme");
            if (forced != null) {
                try {
                    want = com.hysx.salvosequence.gleba.gen.PlanetTheme
                            .valueOf(forced.trim().toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // 名字写错就按随机的那颗来
                }
            }
            if (theme != want) {
                theme = want;
                planet.setTheme(want);
                // setTheme 会按预设重建行星（含太阳方位），重建后把机位给的光照摆回去
                planet.setSun(sunAzimuth, 16f);
                // 环也是 setTheme 的牺牲品：掷过骰子的环要再盖一次
                reapplyRings();
                started = true;
            } else if (!started) {
                started = true;
                planet.params.build = planetBuild;
                planet.regen();
                planet.setSun(sunAzimuth, 16f);
                reapplyRings();
            } else if (planet.params.build != planetBuild) {
                // 换巨构不用重新生成地形：着色器的地表改造和轨道几何都是每帧现算的，
                // 但矿业星的坑轴是在 regen 里掷的，所以还是走一次 regen（气态星球那次是空跑）
                planet.params.build = planetBuild;
                planet.regen();
            }
            // update() 是"问生成线程好了没 + 传纹理"的地方，生成中也要调，
            // 否则永远等不到 isGenerating() 变 false
            // 用场景时钟的真实间隔：固定 1/30 会让轨道和俯仰按半速走
            double step = lastPlanetTime < 0 ? 1.0 / 30.0 : Math.min(0.25, time - lastPlanetTime);
            lastPlanetTime = time;
            planet.update(Math.max(1.0 / 120.0, step));
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
        public float[] screenDisc() {
            return planet.screenDisc();
        }

        @Override
        public SkyView skyView(double time) {
            // 行星半径按 1 计，相机距离 dist：星方向落进 asin(1/dist) 的圆锥里就是被挡住
            double[] cam = planet.camera();
            return new SkyView(cam[0], cam[1], planet.fovDeg(), cam[2], 1.0);
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
        public void setSunLight(double azimuthDeg) {
            // 太阳转到镜头侧偏 45°：机位绕一圈也不会抽到全黑的夜面
            sunAzimuth = (float) azimuthDeg;
            planet.setSun(sunAzimuth, 16f);
        }

        @Override
        public void setRingStyle(int style) {
            ringStyle = Math.max(0, Math.min(4, style));
            ringPinned = true;
            planet.setRingStyle(ringStyle);
        }

        @Override
        public void setRings(boolean on) {
            ringPinned = true;
            ringOn = on;
            planet.rings = on;
        }

        @Override
        public void setRingSpan(float inner, float outer, float opacity) {
            ringInner = Math.max(1.05f, inner);
            ringOuter = Math.max(ringInner + 0.10f, outer);
            ringOpacity = Math.max(0.05f, Math.min(0.95f, opacity));
            ringPinned = true;
            planet.ringInner = ringInner;
            planet.ringOuter = ringOuter;
            planet.ringOpacity = ringOpacity;
        }

        @Override
        public void setRingTilt(double degrees) {
            ringTilt = Math.max(-75f, Math.min(75f, (float) degrees));
            ringPinned = true;
            planet.setRingTilt(ringTilt);
        }

        @Override
        public void setRingSeed(float seed, int bands) {
            ringSeed = seed;
            ringBands = Math.max(2, Math.min(4, bands));
            ringPinned = true;
            planet.setRingSeed(ringSeed);
            planet.setRingBands(ringBands);
        }

        /** setTheme 会按主题把环参数重建回预设，掷过骰子的环要再盖回去。 */
        private void reapplyRings() {
            if (!ringPinned) {
                return;
            }
            planet.rings = ringOn;
            planet.setRingStyle(ringStyle);
            planet.ringInner = ringInner;
            planet.ringOuter = ringOuter;
            planet.ringOpacity = ringOpacity;
            planet.setRingTilt(ringTilt);
            planet.setRingSeed(ringSeed);
            planet.setRingBands(ringBands);
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
        public void setStarType(int type) {
            live.setType(com.hysx.salvosequence.live.star.StarType.byIndex(type));
        }

        @Override
        public void setFraming(double x, double y) {
            live.setFraming(x, y);
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
        public SkyView skyView(double time) {
            // 恒星球半径按 1 计。setFraming 把相机沿右/上轴平移了，lookAt 还在原点，
            // 所以恒星的圆面被推到画面 (W·offsetX, H·offsetY) 而不是正中（默认
            // 0.74/0.28 → 236,50）。星层的遮挡圆要跟着挪，否则会在画面正中抠出一个
            // 根本不存在的黑洞。
            double[] cam = live.camera();
            return new SkyView(cam[0], cam[1], 45.0, cam[2], 1.0,
                    SkyView.W * live.offsetX, SkyView.H * live.offsetY);
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
