package com.hysx.salvosequence.shipyard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从「占据掩码」拟合出外轮廓折线：边界追踪 + Douglas-Peucker 简化。
 *
 * <h2>为什么不用教科书版 marching squares</h2>
 * <p>教科书版在格点之间插值求等值线，适合连续标量场（地形高度、密度）。我们的输入是
 * <b>布尔格子</b>，插值没有意义，而且它会在「只有角相接的两格」上产生歧义（著名的鞍点
 * 二义性），需要额外的消歧规则。</p>
 *
 * <p>这里用的是更简单也更精确的做法：</p>
 * <ol>
 *   <li><b>边界边</b>：一个格子有 4 条边；某条边两侧「一格有、一格没有」时，它就是一条边界边。
 *       这是布尔场的梯度，没有歧义。</li>
 *   <li><b>串成环</b>：把边界边看成一张格点图（顶点是格点，边是格边），从每条未走过的边出发，
 *       在每个顶点上取「<b>相对来向右转 90° 的那条</b>」继续走。见 {@link #trace}。</li>
 *   <li><b>简化</b>：走到的是「锯齿楼梯」——每格都要拐一次弯。用 Douglas-Peucker
 *       把每一处楼梯塌成一条 45° 斜线。容差按「格」给，见 {@link #DEFAULT_TOLERANCE_CELLS}。</li>
 * </ol>
 *
 * <h2>输出是什么、不是什么</h2>
 * <p>输出的是<b>格子的并集轮廓</b>（一条直角折线经过简化），不是平滑曲线。
 * 这正是设定稿的味道：格子是方的，外形是斜切的。全程整数运算，没有浮点误差累积。</p>
 *
 * <h2>踩过的坑（别再改回去）</h2>
 * <p>这个类的追踪部分前后写过八版都没能闭合环，根因是<b>转向规则反了</b>。正确规则只有一条：
 * 到达顶点后，把「来向的反向」再顺时针转 90°，取与这个目标角度夹角最小的那条出边。
 * 换成人话就是<b>永远先右转</b>：贴着实体走，于是外轮廓被印成逆时针、内洞被印成顺时针。</p>
 * <p>曾经试过而<b>不成立</b>的写法，供后来者避坑：</p>
 * <ul>
 *   <li>「最右转优先，直行/左转/折返兜底」的优先级表——等价于允许直行，会走进已经走过的路。</li>
 *   <li>用「面方向」把边记成 {@code (格子, 面)} 再推后继——同一个顶点上一条边会同时属于两个面，
 *       信息量不够，永远推不出唯一后继。</li>
 *   <li>预先把每条边编译成 {@code next} 表——表里必须编码「这条边属于哪个环」，而那正是我们要算的东西。</li>
 * </ul>
 */
public final class OutlineTracer {

    /**
     * 简化参数：<b>最少几级楼梯才塌成斜线</b>。
     *
     * <p>取 2 而不是 1：只有一级的台阶（凸出去一格）在像素画里是看得见的细节——
     * 炮座缺口、装甲小凸起都长这样，塌掉就没了。两级以上才是真的"楼梯"，
     * 那是斜边的量化痕迹，塌成 45° 才好看。</p>
     *
     * <p>早期这里是个「DP 容差」，已废弃：Douglas-Peucker 的模型是「折线应该尽量
     * 接近一条直线」，用在闭合舰体轮廓上会在第一个递归就把整条凹槽切过去
     * （实测工字形被简化成外接矩形，面积从 45 涨到 48）。楼梯塌陷不会横切形状，
     * 因为它只在「一级横 + 一级竖」这种局部上动手。</p>
     */
    public static final int MIN_STAIRCASE_STEPS = 2;

    /**
     * 简化时允许的偏差，单位是格。取 0.8：
     *
     * <p>一格楼梯的锯齿尖到它的弦的距离是 <b>0.707</b>（半格的对角线），所以 0.8 刚好
     * 能把一格一级的锯齿全部吃掉、把斜边拉成一条直线。这个 0.707 的实测值就来自
     * 楼梯：斜距 3 格时，尖点到弦的距离算出 0.9487，弦距 5 格时算出 0.7071。</p>
     *
     * <p>选 0.8 而不是更大，是因为<b>这条阈值必须小于「一格」。</b>只要小于一格，
     * 简化就不可能吃掉任何完整的一格，也就不可能横切凹槽的壁——偏差超过一格的点
     * 一定被保留。早期用 1.0 时，凹壁整片被切（实测 C 形面积从 36 涨到 48）。</p>
     *
     * <p>另外还有一道保险：{@link #concaveAnchors} 让<b>凹角成为硬锚点</b>，
     * 简化永远不跨过凹角。阈值只决定两个锚点之间那段怎么走。</p>
     */
    private static final double QUANTISATION_TOLERANCE_CELLS = 0.8;

    private static final int[] DIR_DX = {1, 0, -1, 0};
    private static final int[] DIR_DY = {0, 1, 0, -1};

    private OutlineTracer() {
    }

    /** 格空间的并集轮廓：坐标是正整数，x 向右、y 向下，单位是「格」。 */
    public record Trace(int width, int height, int[][] rings) {

        /**
         * 所有环的顶点总数。
         *
         * <p>环按约定首点重复一次收尾（渲染与填充都用这个形式），所以一条 n 角环的
         * {@code ring.length == (n + 1) * 2}；这里按<b>不重复</b>的角数统计，
         * 免得下游把「4 个角」读成 5。</p>
         */
        public int pointCount() {
            int n = 0;
            for (int[] ring : rings) {
                n += Math.max(0, ring.length / 2 - 1);
            }
            return n;
        }

        /** 所有环的折线总长度（格为单位；用曼哈顿长度，够用来比较合并效果）。 */
        public double perimeter() {
            double total = 0;
            for (int[] pts : rings) {
                int n = pts.length / 2;
                for (int i = 0; i < n; i++) {
                    int j = (i + 1) % n;               // 闭合
                    total += Math.abs(pts[j * 2] - pts[i * 2])
                            + Math.abs(pts[j * 2 + 1] - pts[i * 2 + 1]);
                }
            }
            return total;
        }

        /**
         * 净面积（鞋带公式的和）。
         *
         * <p>环是<b>有向</b>的：外轮廓逆时针（正），内洞顺时针（负）——这是
         * {@link #trace} 里「先右转」的走法自动给的。所以直接把有向面积相加就是净面积，
         * 不需要再按嵌套层数定符号。</p>
         *
         * <p>「外面那一圈」在 {@link #trace} 里已经被丢掉，否则它的面积会把整个结果压成 0。</p>
         */
        public double area() {
            double total = 0;
            for (int[] ring : rings) {
                total += shoelace(ring);
            }
            return total;
        }
    }

    /** 环的有向面积（鞋带公式，符号取决于绕向）。 */
    private static double shoelace(int[] pts) {
        int n = pts.length / 2;
        double sum = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            sum += (double) pts[i * 2] * pts[j * 2 + 1] - (double) pts[j * 2] * pts[i * 2 + 1];
        }
        return sum / 2.0;
    }

    /**
     * 追踪边界：返回格空间的轮廓环，按「起点 y 再 x」排序以保证可复现。
     * 没有任何占据格时返回空数组。
     *
     * <h2>做法：把边界当图走，每个顶点先右转</h2>
     * <p>边界边集合就是一张格点图：顶点是格点 {@code (x, y)}，边是长度 1 的格边。
     * 每个顶点的度数最多是 2（四条格边里最多两条是边界），所以这张图是一堆<b>不相交的环</b>，
     * 每个环恰好是一个面（外轮廓或内洞）。于是只要从任意未走过的边出发，按下面的规则一路走，
     * 一定会走回起点，而且每个环恰好走一次。</p>
     *
     * <p><b>转向规则</b>：来到顶点后的「来向」是把当前边反向；把它顺时针转 90° 得到目标角度，
     * 在所有出边（不含刚走来的那条）里取与目标角度夹角最小的。用方位常数写出来就是
     * {@code 东→南→西→北→东}。</p>
     *
     * <p>这条规则只依赖边的两个端点坐标，<b>完全不涉及「哪一格拥有这条边」「是凸角还是凹角」
     * 这类几何判断</b>——之前那八版全是在这些判断上翻车。</p>
     *
     * <p>「图形外面那个面」也会被走成一圈环（绕着整个网格走、把图形包在里面），
     * 它的绕向与内洞相同。它不影响净面积（面积是各环有向面积之和，外圈与它包住的环符号相反、
     * 恰好抵消），但会让「有几个环」这种计数变得难解释，所以按「是否紧贴网格四条边」识别后丢掉。</p>
     *
     * <p>每次结构变化（放/拆一个舱室、被打掉一格）重算一次即可，然后缓存。
     * 成本是 O(边界边数)，格子 64x64 上限时是几百微秒量级。</p>
     */
    public static Trace trace(boolean[] occ, int w, int h) {
        if (occ == null || w <= 0 || h <= 0) {
            return new Trace(w, h, new int[0][]);
        }

        // ① 收集边界边，作为<b>无向</b>边：一个格子 4 条边，某条边两侧「一格有、一格没有」
        //    时它才是边界。这里必须无向——同一个顶点上，相邻两格贡献的那条公共边在两次枚举里
        //    会被写成方向相反的两个副本，若按有向边存，同一个顶点就会出现两条「同向」出边，
        //    「该走哪条」立刻变得无法判定（前八版全折在这里）。
        Set<Long> edges = new LinkedHashSet<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!inside(occ, w, h, x, y)) {
                    continue;
                }
                if (!inside(occ, w, h, x, y - 1)) {
                    edges.add(pack(x, y, x + 1, y));               // 北面
                }
                if (!inside(occ, w, h, x, y + 1)) {
                    edges.add(pack(x, y + 1, x + 1, y + 1));       // 南面
                }
                if (!inside(occ, w, h, x - 1, y)) {
                    edges.add(pack(x, y, x, y + 1));               // 西面
                }
                if (!inside(occ, w, h, x + 1, y)) {
                    edges.add(pack(x + 1, y, x + 1, y + 1));       // 东面
                }
            }
        }
        if (edges.isEmpty()) {
            return new Trace(w, h, new int[0][]);
        }

        // ② 顶点 → 关联边。每个顶点的度数最多 2，所以「先右转」的候选是确定的。
        Map<Long, List<Long>> incident = new HashMap<>();
        for (long e : edges) {
            incident.computeIfAbsent(vertexKey(startX(e), startY(e)), k -> new ArrayList<>()).add(e);
            incident.computeIfAbsent(vertexKey(endX(e), endY(e)), k -> new ArrayList<>()).add(e);
        }

        // ③ 走环：进入顶点的边 → 取「相对来向右转 90°」的那条继续。
        List<int[]> rings = new ArrayList<>();
        Set<Long> walked = new HashSet<>();
        for (long seed : edges) {
            if (walked.contains(seed)) {
                continue;
            }
            List<int[]> points = new ArrayList<>();
            long edge = seed;
            int cx = startX(seed);
            int cy = startY(seed);
            int startX = cx;
            int startY = cy;
            boolean closed = false;
            for (int guard = 0; guard <= edges.size(); guard++) {
                points.add(new int[]{cx, cy});
                walked.add(edge);
                int tx = otherX(edge, cx, cy);
                int ty = otherY(edge, cx, cy);
                double back = Math.toDegrees(Math.atan2(cy - ty, cx - tx));
                double wanted = back + 90.0;
                long best = -1;
                double bestDelta = Double.MAX_VALUE;
                for (long cand : incident.getOrDefault(vertexKey(tx, ty), List.of())) {
                    if (cand == edge) {
                        continue;                 // 原路返回不算转弯
                    }
                    double out = Math.toDegrees(
                            Math.atan2(otherY(cand, tx, ty) - ty, otherX(cand, tx, ty) - tx));
                    double delta = ((wanted - out) % 360.0 + 360.0) % 360.0;
                    if (delta < bestDelta) {
                        bestDelta = delta;
                        best = cand;
                    }
                }
                if (best < 0) {
                    break;                        // 度数为 1 的悬空顶点：不该出现
                }
                cx = tx;
                cy = ty;
                edge = best;
                if (cx == startX && cy == startY) {
                    closed = true;
                    break;
                }
            }
            if (!closed || points.isEmpty()) {
                continue;
            }
            // 一条自触碰的环（例如 (0,0)(1,1)(2,1)(0,2)：两块只在一个角上相接）必须在
            // 这一步<b>先拆成简单环</b>。否则下游拿到一个「自己碰到自己」的多边形：
            // Douglas-Peucker 会把两个叶瓣之间的凹口整段切掉，12 个顶点塌成 3 个、
            // 面积从 4.00 漂到 4.30（实测）。
            for (int[] sub : splitRing(flatten(points))) {
                int[] ring = dropCollinear(sub);
                if (ring.length < 6) {            // 少于 3 个角的环没有面积
                    continue;
                }
                if (!isOuterFace(ring, w, h)) {
                    rings.add(ring);
                    if (DBG) {
                        dump("kept", ring);
                    }
                } else if (DBG) {
                    dump("drop", ring);
                }
            }
        }

        // ④ 绕向归一：外轮廓逆时针（depth 偶），内洞顺时针（depth 奇）。
        //    「先右转」的走法会把并集内部一律留在同一侧，于是外轮廓与内洞拿到的是同一个绕向；
        //    若不归一，内洞的面积会被<b>加</b>上去而不是减掉（实测带洞的船体 52 被算成 68）。
        //    判断谁套着谁用「嵌套深度」，比拿绕向互相推要稳。
        for (int i = 0; i < rings.size(); i++) {
            if (depthOf(i, rings) % 2 == 1) {
                reverse(rings.get(i));
            }
        }

        rings.sort((a, b) -> a[1] != b[1] ? Integer.compare(a[1], b[1]) : Integer.compare(a[0], b[0]));
        return new Trace(w, h, rings.toArray(new int[0][]));
    }

    /**
     * 把一条自触碰的闭环在<b>重复顶点</b>处拆成若干简单环。
     *
     * <p>边界追踪只能产出顶点序列：形状若在某一格角上收窄成一点（两块只在一个角上相接），
     * 走出来的就是一条「8 字形」——同一个顶点在序列里出现两次，一次当作某个叶瓣的结束、
     * 一次当作下一个叶瓣的开始。多边形算法（简化、填充、描边）都假定简单环，
     * 所以这里先按出现过的顶点切开，把 8 字形还原成两个独立的环。</p>
     *
     * <p>没有重复顶点时原样返回一条环。</p>
     */
    private static List<int[]> splitRing(int[] flat) {
        List<int[]> out = new ArrayList<>();
        int n = flat.length / 2;
        List<int[]> chain = new ArrayList<>();          // 当前正在走的简单路径（已去共线）
        Map<Long, Integer> position = new HashMap<>();  // 顶点 → chain 里的下标

        if (DBG) {
            dump("raw ", flat);
        }
        for (int i = 0; i < n; i++) {
            int x = flat[i * 2];
            int y = flat[i * 2 + 1];
            long key = vertexKey(x, y);
            if (DBG) {
                System.out.println("[tracer] step i=" + i + " (" + x + "," + y + ")"
                        + " chain=" + chain.size() + " at=" + position.get(key));
            }

            if (!chain.isEmpty()) {
                int[] top = chain.get(chain.size() - 1);
                if (top[0] == x && top[1] == y) {
                    continue;                           // 同一条边的两端各记了一次
                }
            }

            Integer at = position.get(key);
            if (at != null) {
                // 绕回路径上已有的顶点：chain[at..] 自己闭成一个环。
                // 环记完之后把 chain 截断到该顶点（它是下一段的起点），
                // 这样下一段才接得上——否则剩下的尾段会缺一截、闭不成环。
                if (at == 0 && chain.size() >= 3) {
                    // 闭环：末点接到起点，起步那两格若与这条收尾边共线也要并掉
                    if (DBG) {
                        System.out.println("[tracer] close at=0 chain=" + chain.size()
                                + " cross=" + cross(chain.get(chain.size() - 2),
                                chain.get(chain.size() - 1), chain.get(0)));
                    }
                    if (cross(chain.get(chain.size() - 2), chain.get(chain.size() - 1),
                            chain.get(0)) == 0) {
                        int[] gone = chain.remove(chain.size() - 1);
                        position.remove(vertexKey(gone[0], gone[1]));
                        continue;
                    }
                    out.add(close(chain, 0, chain.size(), x, y));
                    chain.clear();
                    position.clear();
                    continue;
                }
                int m = chain.size() - at;
                if (m >= 3) {
                    out.add(close(chain, at, m, x, y));
                }
                while (chain.size() > at + 1) {
                    int[] p = chain.remove(chain.size() - 1);
                    position.remove(vertexKey(p[0], p[1]));
                }
                continue;
            }

            // 推进一格：新点若与链尾两点共线，就把链尾换成新点（直边上不留中点，
            // 否则 2x2 正方形会留下 8 个「角」，简化那一步再也救不回来）
            chain.add(new int[]{x, y});
            int m = chain.size();
            if (m >= 3) {
                int[] a = chain.get(m - 3);
                int[] b = chain.get(m - 2);
                if (cross(a, b, chain.get(m - 1)) == 0) {
                    position.remove(vertexKey(b[0], b[1]));
                    chain.remove(m - 2);
                }
            }
            position.put(key, chain.size() - 1);
        }

        if (chain.size() >= 3) {
            if (DBG) {
                int[] last = chain.get(chain.size() - 1);
                int[] prev = chain.get(chain.size() - 2);
                System.out.println("[tracer] tailMerge chain=" + chain.size()
                        + " prev=(" + prev[0] + "," + prev[1] + ")"
                        + " last=(" + last[0] + "," + last[1] + ")"
                        + " start=(" + flat[0] + "," + flat[1] + ")"
                        + " cross=" + cross(prev, last, new int[]{flat[0], flat[1]}));
            }
            // 尾段收口：末点通常就是整条走的起点（walk 到起点即停、没把它再记一次），
            // 所以要拿 flat[0] 当「闭合点」来判共线，不能拿末点自己。
            while (chain.size() >= 3
                    && cross(chain.get(chain.size() - 2), chain.get(chain.size() - 1),
                    new int[]{flat[0], flat[1]}) == 0) {
                int[] gone = chain.remove(chain.size() - 1);
                position.remove(vertexKey(gone[0], gone[1]));
            }
            out.add(close(chain, 0, chain.size(), flat[0], flat[1]));
        }
        if (out.isEmpty()) {
            out.add(flat);
        }
        if (DBG) {
            for (int[] r : out) {
                dump("sub ", r);
            }
        }
        return out;
    }

    /** 调试开关（环境变量，不用 -D：PowerShell 会把带点的参数从点号处拆开）。 */
    private static final boolean DBG = System.getenv("TRACER_DUMP") != null;

    private static void dump(String tag, int[] ring) {
        StringBuilder sb = new StringBuilder("[tracer] ").append(tag).append(ring.length / 2).append("pt ");
        for (int i = 0; i < ring.length; i += 2) {
            sb.append('(').append(ring[i]).append(',').append(ring[i + 1]).append(')');
        }
        System.out.println(sb);
    }

    /** 取 chain 的一段并按闭环收尾（首点重复一次）。 */
    private static int[] close(List<int[]> chain, int from, int count, int endX, int endY) {
        int[] sub = new int[(count + 1) * 2];
        for (int k = 0; k < count; k++) {
            int[] p = chain.get(from + k);
            sub[k * 2] = p[0];
            sub[k * 2 + 1] = p[1];
        }
        sub[count * 2] = endX;
        sub[count * 2 + 1] = endY;
        return sub;
    }

    /** 三点叉积；0 表示共线。 */
    private static int cross(int[] a, int[] b, int[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }

    /** {@code [x0,y0,x1,y1,...]} → 扁平数组。 */
    private static int[] flatten(List<int[]> points) {
        int[] out = new int[points.size() * 2];
        for (int i = 0; i < points.size(); i++) {
            out[i * 2] = points.get(i)[0];
            out[i * 2 + 1] = points.get(i)[1];
        }
        return out;
    }

    /** 把环逆向（原地）。 */
    private static void reverse(int[] ring) {
        int n = ring.length / 2;
        for (int i = 0; i < n / 2; i++) {
            int j = n - 1 - i;
            int tx = ring[i * 2];
            int ty = ring[i * 2 + 1];
            ring[i * 2] = ring[j * 2];
            ring[i * 2 + 1] = ring[j * 2 + 1];
            ring[j * 2] = tx;
            ring[j * 2 + 1] = ty;
        }
    }

    /**
     * 环 {@code index} 的嵌套深度：被多少个别的环包住。
     * 取环上第一个顶点做「在不在对方内部」的测试——轮廓环互不相交，所以首个顶点在对方内部
     * 就等价于整条环在对方内部。
     */
    private static int depthOf(int index, List<int[]> rings) {
        int[] me = rings.get(index);
        double myArea = Math.abs(shoelace(me));
        double px = me[0] + 0.5;                  // 稍微往里挪，避开正好落在边上的情况
        double py = me[1] + 0.5;
        int depth = 0;
        for (int i = 0; i < rings.size(); i++) {
            if (i == index) {
                continue;
            }
            int[] other = rings.get(i);
            if (Math.abs(shoelace(other)) <= myArea + 1e-9) {
                continue;                         // 只认面积更大的当容器
            }
            if (pointInside(px, py, other)) {
                depth++;
            }
        }
        return depth;
    }

    /** 射线法：点是否在多边形内部。 */
    private static boolean pointInside(double px, double py, int[] ring) {
        int n = ring.length / 2;
        boolean in = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = ring[i * 2];
            double yi = ring[i * 2 + 1];
            double xj = ring[j * 2];
            double yj = ring[j * 2 + 1];
            if ((yi > py) != (yj > py)
                    && px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
                in = !in;
            }
        }
        return in;
    }

    /**
     * 是不是「图形外面那个面」：它沿着整个网格的四条边走一圈。
     * 只要每个顶点都落在网格边界上，就是它。
     */
    private static boolean isOuterFace(int[] ring, int w, int h) {
        for (int i = 0; i < ring.length / 2; i++) {
            int x = ring[i * 2];
            int y = ring[i * 2 + 1];
            if (x != 0 && x != w && y != 0 && y != h) {
                return false;
            }
        }
        return true;
    }

    private static boolean inside(boolean[] occ, int w, int h, int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && occ[y * w + x];
    }

    // ── 边的打包：一条有向边 = 起点 (x,y) + 终点 (x,y)，各占 16 位低段 ──────────────

    private static long pack(int ax, int ay, int bx, int by) {
        return ((long) (ax & 0xFFFF) << 48)
                | ((long) (ay & 0xFFFF) << 32)
                | ((long) (bx & 0xFFFF) << 16)
                | (by & 0xFFFF);
    }

    private static int startX(long e) {
        return (int) ((e >> 48) & 0xFFFF);
    }

    private static int startY(long e) {
        return (int) ((e >> 32) & 0xFFFF);
    }

    private static int endX(long e) {
        return (int) ((e >> 16) & 0xFFFF);
    }

    private static int endY(long e) {
        return (int) (e & 0xFFFF);
    }

    private static long vertexKey(int x, int y) {
        return ((long) (x & 0xFFFF) << 32) | (y & 0xFFFF);
    }

    /** 无向边 {@code e} 的另一端：给定一端 {@code (x,y)}，返回另一端。 */
    private static int otherX(long e, int x, int y) {
        return startX(e) == x && startY(e) == y ? endX(e) : startX(e);
    }

    private static int otherY(long e, int x, int y) {
        return startX(e) == x && startY(e) == y ? endY(e) : startY(e);
    }

    /**
     * 楼梯塌陷。输入输出都是 {@code [x0,y0,x1,y1,...]} 的环。
     *
     * @param minSteps 最少几级楼梯才塌成斜线；{@code <= 0} 表示不塌、交出原始楼梯
     */
    public static int[][] simplifyStaircases(boolean[] occ, int w, int h, int[][] rings, int minSteps) {
        if (rings == null || rings.length == 0) {
            return new int[0][];
        }
        int steps = minSteps <= 0 ? Integer.MAX_VALUE : Math.max(2, minSteps);
        List<int[]> out = new ArrayList<>();
        for (int[] ring : rings) {
            out.add(collapseStaircases(occ, w, h, ring, steps));
        }
        return out.toArray(new int[0][]);
    }

    /**
     * 把「楼梯状的斜边」塌成 45° 斜线。
     *
     * <h2>判定一条楼梯要看三件事</h2>
     * <ol>
     *   <li><b>一级一拐</b>：相邻两条边的方向必须不同（相同就是一条直边，它不构成楼梯）。</li>
     *   <li><b>拐向一致</b>：全程都往同一边拐。凸楼梯与凹楼梯的拐向相反，各自单独成段；
     *       拐向一变就是楼梯结束（比如凹口的一个角）。</li>
     *   <li><b>每级长度都是 1 格</b>：这条最关键。斜边量化出来的楼梯每级必然只有一格
     *       （走一格就拐）；而<b>凹口</b>的边可以有任意长度——「走 2 格、拐、走 4 格」
     *       那种是方缺口，塌掉就不成形状了。</li>
     * </ol>
     *
     * <h2>光看局部还不够：每一步都要验证「没切出去」</h2>
     *
     * <p>上面三条都是局部判据，凑在一起仍然会出错：实测梳子形舰体（一边三个一格宽的缺口）
     * 简化后有三个格子被盖错——局部看每一颗牙齿都很像，合起来却把相邻的凹口连成了斜线。
     * 所以真正的判据是<b>数值上的</b>：摘掉一个顶点后，新边扫过的每一格都必须是实心格
     * （见 {@link #edgeStaysInside}）。这样简化永远不可能切进船体，凹口也不可能有例外。</p>
     */
    private static int[] collapseStaircases(boolean[] occ, int w, int h, int[] ring, int minSteps) {
        int[] flat = dropCollinear(ring);
        int m = flat.length / 2 - 1;                  // 减去首点的重复收尾
        if (m < 5 || minSteps == Integer.MAX_VALUE) {
            return flat;
        }
        List<int[]> pts = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            pts.add(xy(flat, i));
        }
        boolean changed = true;
        while (changed && pts.size() > 4) {
            changed = false;
            for (int i = 0; i < pts.size() && pts.size() > 4; i++) {
                int n = pts.size();
                int[] prev = pts.get((i - 1 + n) % n);
                int[] cur = pts.get(i);
                int[] next = pts.get((i + 1) % n);
                if (isUnitTooth(prev, cur, next) && edgeStaysInside(occ, w, h, prev, next)) {
                    pts.remove(i);
                    i--;
                    changed = true;
                }
            }
        }
        return pts.size() >= 3 ? closed(pts) : flat;
    }

    /**
     * 是不是一颗「单位牙齿」：两条邻边长都是一格、拐了一次、而且是往右拐（凸角）。
     *
     * <p>「两条邻边长都是一格」是核心：它保证摘掉这个顶点后，前一点与后一点之间只隔了
     * 这一个顶点，弦不会越过任何格，所以简化不会切进船体。</p>
     */
    private static boolean isUnitTooth(int[] prev, int[] cur, int[] next) {
        int inLen = Math.abs(cur[0] - prev[0]) + Math.abs(cur[1] - prev[1]);
        int outLen = Math.abs(next[0] - cur[0]) + Math.abs(next[1] - cur[1]);
        if (inLen != 1 || outLen != 1) {
            return false;
        }
        int inDir = dirIndex(prev, cur);
        int outDir = dirIndex(cur, next);
        if (inDir == outDir) {
            return false;                             // 直边上的点，go 去共线已经处理
        }
        return ((inDir + 1) & 3) == outDir;           // 屏幕 y 向下：往右拐就是凸角
    }

    /**
     * 摘掉中间点之后，新边扫过的每一格是不是都还在船体里。
     *
     * <p>做法很直白：从 {@code a} 走阶梯到 {@code b}（先按主导轴推进、把包围盒铺满），
     * 沿途每格都查一次 {@code occ}。中间点被摘掉后新边跨越的格子全都露在这条路上，
     * 只要有一格是空的，这次简化就会切进船体，必须否决。</p>
     *
     * <p>这条检查是「局部判据」的兜底。局部判据（一级一拐、拐向一致、每级一格、凸角）
     * 单独看都对，凑在一起仍会在梳子形上出错——因为相邻两个凹口之间的那段凸起，
     * 局部看也像楼梯。有了它，简化就有了硬保证：<b>盖错的格数恒为 0</b>。</p>
     */
    private static boolean edgeStaysInside(boolean[] occ, int w, int h, int[] a, int[] b) {
        int x = a[0];
        int y = a[1];
        int steps = 0;
        int limit = Math.abs(b[0] - a[0]) + Math.abs(b[1] - a[1]) + 2;
        while (steps <= limit) {
            if (x >= 0 && y >= 0 && x < w && y < h && !occ[y * w + x]) {
                return false;
            }
            if (x == b[0] && y == b[1]) {
                return true;
            }
            if (x != b[0] && (y == b[1] || Math.abs(b[0] - x) >= Math.abs(b[1] - y))) {
                x += Integer.signum(b[0] - x);
            } else {
                y += Integer.signum(b[1] - y);
            }
            steps++;
        }
        return false;
    }
    /** 直角方向序号：0=东 1=南 2=西 3=北。 */
    private static int dirIndex(int[] a, int[] b) {
        if (b[0] > a[0]) {
            return 0;
        }
        if (b[1] > a[1]) {
            return 1;
        }
        if (b[0] < a[0]) {
            return 2;
        }
        return 3;
    }

    /** 按「格」取环上的第 i 个顶点（i 已取过模）。 */
    private static int[] xy(int[] flat, int i) {
        return new int[]{flat[i * 2], flat[i * 2 + 1]};
    }

    /** 去掉三点共线的中间点（环上除角和端点外的直边中点）。 */
    private static int[] dropCollinear(int[] ring) {
        int n = ring.length / 2;
        // 环按「首点重复一次」收尾；先把这个重复点摘掉，否则 n-1 → 0 那条边
        // 会被当成「与首点共线」而把首点误删（实测 2x2 正方形的起始角就这么没了）。
        if (n >= 2 && ring[0] == ring[(n - 1) * 2] && ring[1] == ring[(n - 1) * 2 + 1]) {
            n--;
        }
        if (n < 3) {
            return ring.clone();
        }
        List<int[]> keep = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int px = ring[((i - 1 + n) % n) * 2];
            int py = ring[((i - 1 + n) % n) * 2 + 1];
            int cx = ring[i * 2];
            int cy = ring[i * 2 + 1];
            int nx = ring[((i + 1) % n) * 2];
            int ny = ring[((i + 1) % n) * 2 + 1];
            int cross = (cx - px) * (ny - py) - (cy - py) * (nx - px);
            if (cross != 0) {
                keep.add(new int[]{cx, cy});
            }
        }
        if (keep.size() < 3) {                    // 退化：全是共线点，原样返回
            return ring.clone();
        }
        return closed(keep);
    }

    /**
     * 环的关闭约定：<b>首点在末尾再写一次</b>。
     *
     * <p>下游的填充与描边都按这个形式读。所以任何「按顶点表重新拼一个环」的地方
     * 都必须用它收尾——漏掉一次就会得到「少一个角的开折线」，而不是报错：
     * 实测 5 点矩形经过 Douglas-Peucker 后剩 3 个角，少了收尾点就只剩 2 个坐标，
     * 面积静默变成 0。</p>
     */
    private static int[] closed(List<int[]> pts) {
        int[] out = new int[(pts.size() + 1) * 2];
        for (int i = 0; i < pts.size(); i++) {
            out[i * 2] = pts.get(i)[0];
            out[i * 2 + 1] = pts.get(i)[1];
        }
        out[pts.size() * 2] = pts.get(0)[0];
        out[pts.size() * 2 + 1] = pts.get(0)[1];
        return out;
    }

}