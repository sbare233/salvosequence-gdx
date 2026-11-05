package com.hysx.salvosequence.shipyard;

import com.badlogic.gdx.math.GridPoint2;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 一艘船的<b>功能网格</b>：每格「有 / 没有舱室」，没有几何、没有图形。
 *
 * <h2>为什么要这么朴素</h2>
 * <p>这是本方案最重要的一条设计决定——<b>把外观和功能分离</b>：</p>
 * <ul>
 *   <li>功能侧就是这个 {@code boolean[]}：肉鸽生成器、连通性检查、伤害结算、掉落折算
 *       全都按格子算，和网格类 roguelike 那套天然契合；</li>
 *   <li>外观侧由 {@link OutlineTracer} 从这张网格<b>推导</b>出来，不参与任何逻辑；</li>
 *   <li>两边只通过「格 → 区域编号」这一层薄薄的表耦合。</li>
 * </ul>
 * <p>于是生成算法里一行画图代码都不会有，也不会出现「算出来的轮廓」和「画出来的填充」
 * 不一致这种经典 bug——它们是同一个来源。</p>
 *
 * <h2>坐标</h2>
 * <p>本类是<b>格空间</b>：整数格，y 向下。转到设计像素是 {@code x * cellSize}，
 * 纯整数乘法，不累积半像素误差。旋转只允许 90 度的整数倍，直接在格空间转置，
 * 比变换多边形简单也更不容易错。</p>
 */
public final class HullGrid {

    private final int w;
    private final int h;
    private final int cellSize;
    private final boolean[] cells;

    public HullGrid(int w, int h, int cellSize) {
        if (w <= 0 || h <= 0 || cellSize <= 0) {
            throw new IllegalArgumentException("HullGrid 尺寸必须是正数: " + w + "x" + h + " @" + cellSize);
        }
        this.w = w;
        this.h = h;
        this.cellSize = cellSize;
        this.cells = new boolean[w * h];
    }

    public int width() {
        return w;
    }

    public int height() {
        return h;
    }

    /** 一格等于多少设计像素。 */
    public int cellSize() {
        return cellSize;
    }

    public boolean occupied(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && cells[y * w + x];
    }

    public void set(int x, int y, boolean value) {
        if (x >= 0 && y >= 0 && x < w && y < h) {
            cells[y * w + x] = value;
        }
    }

    public void fillRect(int x, int y, int rw, int rh) {
        for (int j = y; j < y + rh; j++) {
            for (int i = x; i < x + rw; i++) {
                set(i, j, true);
            }
        }
    }

    /** 已占据的格数——这就是「舱室数」，也是血量的天然来源。 */
    public int cellCount() {
        int n = 0;
        for (boolean c : cells) {
            if (c) {
                n++;
            }
        }
        return n;
    }

    /** 格空间的并集轮廓（未简化，仍是楼梯）。 */
    public OutlineTracer.Trace trace() {
        return OutlineTracer.trace(cells, w, h);
    }

    /**
     * 格空间的并集轮廓，已做楼梯塌陷（见 {@link OutlineTracer#MIN_STAIRCASE_STEPS}）。
     *
     * <p><b>不改变面积</b>：塌陷只在原来的楼梯上合并，不会横切凹口。这一点和早期的
     * Douglas-Peucker 版本不同——那一版会把工字形简化成外接矩形。</p>
     */
    public OutlineTracer.Trace outline() {
        return outline(OutlineTracer.MIN_STAIRCASE_STEPS);
    }

    /** 格空间的并集轮廓。{@code minSteps <= 0} 时不做任何简化，交出原始楼梯。 */
    public OutlineTracer.Trace outline(int minSteps) {
        OutlineTracer.Trace raw = trace();
        return new OutlineTracer.Trace(w, h, OutlineTracer.simplifyStaircases(cells, w, h, raw.rings(), minSteps));
    }

    /**
     * 轮廓能不能如实盖住这些格——<b>这是唯一可信的验收方式</b>。
     *
     * <p>做法：把轮廓多边形（外环填、内洞挖）按每格 8x8 个子样本做点在多边形内测试，
     * 逐个格子比对「盖到一半以上」与 {@link #occupied}。返回盖错的格数，0 就是完全一致。</p>
     *
     * <p>为什么不比面积：面积是个标量，两个互相抵消的错误（这边多切一格、那边多补一格）
     * 会抵成同一个数，看不出问题。逐格比对没有这个盲区。</p>
     */
    public int outlineMismatchCount(OutlineTracer.Trace trace) {
        int samples = 8;
        int bad = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int hit = 0;
                for (int sy = 0; sy < samples; sy++) {
                    for (int sx = 0; sx < samples; sx++) {
                        double px = x + (sx + 0.5) / samples;
                        double py = y + (sy + 0.5) / samples;
                        int inside = 0;
                        for (int[] ring : trace.rings()) {
                            if (pointInRing(ring, px, py)) {
                                inside ^= 1;
                            }
                        }
                        hit += inside;
                    }
                }
                boolean covered = hit * 2 >= samples * samples;
                if (covered != occupied(x, y)) {
                    bad++;
                }
            }
        }
        return bad;
    }

    /** 射线法：点是否在环内。环按「首点重复收尾」存放。 */
    private static boolean pointInRing(int[] ring, double px, double py) {
        int n = ring.length / 2 - 1;
        boolean in = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = ring[i * 2];
            double yi = ring[i * 2 + 1];
            double xj = ring[j * 2];
            double yj = ring[j * 2 + 1];
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
                in = !in;
            }
        }
        return in;
    }

    // ------------------------------------------------------------ 变换

    /**
     * 顺时针 90 度。在格空间做（转置 + 翻转），比变换多边形简单。
     *
     * <p>注意：旋转会改变长宽，所以返回的是新格子，不原地改。</p>
     */
    public HullGrid rotatedCw() {
        HullGrid out = new HullGrid(h, w, cellSize);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.set(h - 1 - y, x, occupied(x, y));
            }
        }
        return out;
    }

    public HullGrid mirrored() {
        HullGrid out = new HullGrid(w, h, cellSize);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.set(w - 1 - x, y, occupied(x, y));
            }
        }
        return out;
    }

    // ------------------------------------------------------------ 连通性

    /**
     * 舱室是否全部连成一片（四邻域）。肉鸽生成之后必须查这个：
     * 孤立的舱室没有意义，而且会让轮廓出现飘在旁边的碎块。
     */
    public boolean isConnected() {
        int total = cellCount();
        if (total == 0) {
            return false;
        }
        boolean[] seen = new boolean[w * h];
        Deque<GridPoint2> queue = new ArrayDeque<>();
        for (int y = 0; y < h && queue.isEmpty(); y++) {
            for (int x = 0; x < w; x++) {
                if (occupied(x, y)) {
                    queue.add(new GridPoint2(x, y));
                    seen[y * w + x] = true;
                    break;
                }
            }
        }
        int reached = 0;
        while (!queue.isEmpty()) {
            GridPoint2 p = queue.poll();
            reached++;
            for (int d = 0; d < 4; d++) {
                int nx = p.x + (d == 0 ? 1 : d == 2 ? -1 : 0);
                int ny = p.y + (d == 1 ? 1 : d == 3 ? -1 : 0);
                if (occupied(nx, ny) && !seen[ny * w + nx]) {
                    seen[ny * w + nx] = true;
                    queue.add(new GridPoint2(nx, ny));
                }
            }
        }
        return reached == total;
    }

    /**
     * 内部空洞的格坐标，一行一个「x,y」。
     *
     * <p>「洞」的定义是<b>四邻域走不到网格边缘的空格</b>。这是拼装时最容易犯又最难发现的错
     * （手工摆模块会漏格，而只有一个格宽的洞在核对图上就是一条线，肉眼分不出），
     * 所以给测试和核对图一个能直接数的接口。</p>
     *
     * <p>注意：<b>凹口不算洞</b>。开口朝着网格外面的空缺，水线是通的，永远不会被判成洞。</p>
     */
    public List<GridPoint2> holes() {
        boolean[] outside = new boolean[w * h];
        Deque<GridPoint2> queue = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            seedOutside(outside, queue, x, 0);
            seedOutside(outside, queue, x, h - 1);
        }
        for (int y = 0; y < h; y++) {
            seedOutside(outside, queue, 0, y);
            seedOutside(outside, queue, w - 1, y);
        }
        while (!queue.isEmpty()) {
            GridPoint2 p = queue.poll();
            for (int d = 0; d < 4; d++) {
                int nx = p.x + (d == 0 ? 1 : d == 2 ? -1 : 0);
                int ny = p.y + (d == 1 ? 1 : d == 3 ? -1 : 0);
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                    continue;
                }
                seedOutside(outside, queue, nx, ny);
            }
        }
        List<GridPoint2> out = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!occupied(x, y) && !outside[y * w + x]) {
                    out.add(new GridPoint2(x, y));
                }
            }
        }
        return out;
    }

    private void seedOutside(boolean[] outside, Deque<GridPoint2> queue, int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return;
        }
        if (outside[y * w + x] || occupied(x, y)) {
            return;
        }
        outside[y * w + x] = true;
        queue.add(new GridPoint2(x, y));
    }

    /** 从某一格出发洪水填充，返回同属一个「片区」的格子集合（用于涂色：点一下就涂一片）。 */
    public boolean[] floodFrom(int x, int y) {
        boolean[] region = new boolean[w * h];
        if (!occupied(x, y)) {
            return region;
        }
        Deque<GridPoint2> queue = new ArrayDeque<>();
        queue.add(new GridPoint2(x, y));
        region[y * w + x] = true;
        while (!queue.isEmpty()) {
            GridPoint2 p = queue.poll();
            for (int d = 0; d < 4; d++) {
                int nx = p.x + (d == 0 ? 1 : d == 2 ? -1 : 0);
                int ny = p.y + (d == 1 ? 1 : d == 3 ? -1 : 0);
                if (occupied(nx, ny) && !region[ny * w + nx]) {
                    region[ny * w + nx] = true;
                    queue.add(new GridPoint2(nx, ny));
                }
            }
        }
        return region;
    }
}
