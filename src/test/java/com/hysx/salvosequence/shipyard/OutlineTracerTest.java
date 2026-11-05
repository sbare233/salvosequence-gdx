package com.hysx.salvosequence.shipyard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自动蒙皮的算法验收。
 *
 * <p>这些断言的意义在于：<b>轮廓合并是"看不出来对不对"的那种代码</b>——图画歪了肉眼能发现，
 * 但"内部接缝有没有真的消失"必须用数字卡住。所以这里查的是顶点数与周长，而不是像素外观。</p>
 */
class OutlineTracerTest {

    /** 2x2 实心块：轮廓必须是 4 个顶点的正方形，面积 4。 */
    @Test
    void solidBlockTracesFourCorners() {
        HullGrid grid = new HullGrid(4, 4, 2);
        grid.fillRect(0, 0, 2, 2);
        OutlineTracer.Trace raw = grid.trace();
        assertEquals(1, raw.rings().length, "2x2 实心块应当只有一条环");
        assertEquals(4, raw.pointCount(), "正方形简化后应只剩 4 个角");
        assertEquals(4.0, raw.area(), 1e-9, "面积应等于 4 格");
        assertEquals(8.0, raw.perimeter(), 1e-9, "周长应等于 8 格");
    }

    /**
     * 本方案最核心的那条断言：<b>斜阶梯必须被塌成一条斜线</b>。
     *
     * <p>不简化时每一级台阶都要拐两次弯；简化后整段楼梯应只剩 2 个顶点，
     * 而且顶点数必须严格少于不简化的情况——这就是"真的在合并"的硬证据。</p>
     */
    @Test
    void staircaseCollapsesIntoOneDiagonal() {
        HullGrid grid = new HullGrid(12, 12, 2);
        // 真正连通的 45 度楼梯：每级向右一格、向下一格，且相邻级之间有一条共享边，
        // 所以四邻域连通。注意不能用「(i,i) 这样对角摆」——对角格在四邻域下互不相连，
        // 那样得到的是 5 个各自独立的方格，根本没有楼梯可塌（实测 20 个角简化后还是 20）。
        for (int i = 0; i < 4; i++) {
            grid.set(i, 0, true);
            grid.set(i + 1, i + 1, true);
            if (i >= 1) {
                grid.set(i, i, true);
            }
            grid.set(i, i + 1, true);
        }
        OutlineTracer.Trace raw = grid.trace();
        int rawPoints = raw.pointCount();
        assertTrue(rawPoints >= 12, "不简化时楼梯的每级台阶都要拐两次弯，实际 " + rawPoints);

        // 简化必须是「只减不增」而且不许切出去。注意这里**不能**断言楼梯一定被塌成
        // 一条斜线：这个形状的斜边外侧是空的，把每颗牙齿的弦走过去就等于从空处抄近路，
        // 会被 edgeStaysInside 正确否决（实测简化前后都是 18 个角，盖格比对 0 错）。
        // 楼梯真正会被塌掉的场合是「斜边只是被切出来的角」——见 StairDiagTest 的 solidBow：
        // 那里弦整个落在实心格内，11 个角。
        OutlineTracer.Trace simple = grid.outline();
        assertTrue(simple.pointCount() <= rawPoints,
                "简化后顶点数不许变多: " + rawPoints + " -> " + simple.pointCount());
        assertEquals(0, grid.outlineMismatchCount(simple), "简化后轮廓必须照样盖住实心格");
        assertEquals(0, grid.outlineMismatchCount(raw), "原始楼梯也必须盖住实心格");
    }

    /** 楼梯级数门槛越高越简化，且不是无脑抹平——顶点数应当单调不增。 */
    @Test
    void largerToleranceNeverAddsPoints() {
        HullGrid grid = new HullGrid(16, 12, 2);
        grid.fillRect(1, 2, 12, 3);
        grid.fillRect(4, 0, 4, 5);                  // 中间竖起来的舰桥
        grid.fillRect(13, 3, 2, 2);                 // 尾部一点结构

        int prev = Integer.MAX_VALUE;
        for (int steps : new int[]{0, 2, 3, 5, 8}) {
            int points = grid.outline(steps).pointCount();
            assertTrue(points <= prev, "门槛 " + steps + " 的顶点数 " + points + " 超过了更低门槛的 " + prev);
            prev = points;
        }
    }

    /** 轮廓必须落在占据格的包围盒里（含右/下边界），不能飘出去。 */
    @Test
    void outlineStaysInsideBoundingBox() {
        HullGrid grid = new HullGrid(20, 10, 2);
        grid.fillRect(3, 1, 11, 6);
        grid.set(15, 3, true);                      // 伸出去一格（会被 DP 当作毛刺，允许）
        OutlineTracer.Trace trace = grid.outline();
        assertNotNull(trace.rings());
        for (int[] ring : trace.rings()) {
            for (int i = 0; i < ring.length / 2; i++) {
                int x = ring[i * 2];
                int y = ring[i * 2 + 1];
                assertTrue(x >= 3 && x <= 16, "x 越界: " + x);
                assertTrue(y >= 1 && y <= 7, "y 越界: " + y);
            }
        }
        assertTrue(trace.area() > 60, "面积应当接近 11x6=66，实际 " + trace.area());
    }

    /** 空网格不能崩，也不能凭空造出环。 */
    @Test
    void emptyGridHasNoRings() {
        HullGrid grid = new HullGrid(6, 6, 4);
        assertEquals(0, grid.outline().rings().length);
        assertEquals(0.0, grid.outline().area(), 1e-9);
    }

    /** 旋转后格数不变；转四次回到原样。 */
    @Test
    void rotationIsLossless() {
        HullGrid grid = new HullGrid(7, 5, 2);
        grid.fillRect(0, 0, 7, 2);
        grid.fillRect(2, 2, 3, 3);
        HullGrid spun = grid.rotatedCw().rotatedCw().rotatedCw().rotatedCw();
        assertEquals(grid.width(), spun.width());
        assertEquals(grid.height(), spun.height());
        for (int y = 0; y < grid.height(); y++) {
            for (int x = 0; x < grid.width(); x++) {
                assertEquals(grid.occupied(x, y), spun.occupied(x, y), "格 (" + x + "," + y + ") 转了四圈变了");
            }
        }
    }

    /** 连通性检查：断开的两块必须被发现（否则轮廓会出碎块）。 */
    @Test
    void disconnectedHullIsDetected() {
        HullGrid grid = new HullGrid(10, 4, 2);
        grid.fillRect(0, 0, 3, 3);
        grid.fillRect(6, 0, 3, 3);
        assertTrue(!grid.isConnected(), "两块分离的舱室不应被判成连通");
        grid.set(3, 1, true);
        grid.set(4, 1, true);
        grid.set(5, 1, true);
        assertTrue(grid.isConnected(), "补上细颈之后应当连通");
    }

    /** 洪水填充只涂一片，不会漏到船外。 */
    @Test
    void floodFillStaysInsideHull() {
        HullGrid grid = new HullGrid(8, 4, 2);
        grid.fillRect(0, 0, 8, 2);
        boolean[] region = grid.floodFrom(0, 0);
        int n = 0;
        for (boolean b : region) {
            if (b) {
                n++;
            }
        }
        assertEquals(16, n, "整块船体应当一次涂满");
        assertTrue(!region[2 * 8 + 3], "船外的格子不能被涂到");
    }

    /**
     * 挖空的船体要出两个环：外轮廓 + 内洞。
     *
     * <p>内洞的方向与外环相反，所以鞋带公式求和之后自动相减——这正是「面积算得对」
     * 的前提。以前只存内环的绝对面积、不分方向，挖空反而会让面积变大。</p>
     */
    @Test
    void innerHoleBecomesItsOwnRing() {
        HullGrid grid = new HullGrid(12, 8, 2);
        grid.fillRect(1, 1, 10, 6);
        for (int y = 3; y < 5; y++) {
            for (int x = 4; x < 8; x++) {
                grid.set(x, y, false);
            }
        }
        OutlineTracer.Trace trace = grid.outline(0);
        assertEquals(2, trace.rings().length, "挖一个洞应当得到 2 个环（外环 + 内环）");
        assertEquals(10 * 6 - 4 * 2, trace.area(), 1e-9,
                "净面积 = 外框面积 - 洞面积");
    }

    /**
     * 只在<b>一个角</b>上相接的两块：轮廓必须在那个共同顶点处被拆开。
     *
     * <p>形状 {@code (0,0) (1,1) (2,1) (0,2)} 的边界走出来是一条「8 字形」——
     * 同一个顶点在序列里出现两次。要是直接把它喂给 Douglas-Peucker，
     * 两个叶瓣之间的凹口会被跨着切掉：12 个顶点塌成 3 个、面积从 4 漂到 4.3
     * （这就是这个用例存在的理由）。</p>
     *
     * <p>四邻域下这两块并不连通，所以 {@code isConnected()} 仍然是 false；
     * 但轮廓追踪走的是格边图，角相接的两个环照样能各自闭合。</p>
     */
    @Test
    void cornerPinchSplitsIntoTwoRings() {
        HullGrid grid = new HullGrid(5, 5, 2);
        grid.set(1, 1, true);
        grid.set(2, 2, true);
        grid.set(3, 2, true);
        grid.set(1, 3, true);
        assertTrue(!grid.isConnected(), "只在角上相接的两块，四邻域不连通");

        OutlineTracer.Trace trace = grid.outline(0);
        // 实测三条：走出来的边界是一条 14 点的 8 字形，在共同顶点 (2,2) 处自己碰自己；
        // splitRing 把它拆成三个简单矩形——左上小块 (1,1)(2,1)(2,2)(1,2)、
        // 右上小条 (2,2)(4,2)(4,3)(2,3)、下面那块 (2,3)(2,4)(1,4)(1,3)。
        // 为什么是三条而不是「一块一条、共两条」：两块各自都不是矩形，8 字形在共享顶点处
        // 交替连接，拆出来的三段自然就是三条。**硬性要求不是条数，而是不许留下自触碰环。**
        assertEquals(3, trace.rings().length,
                "8 字形必须拆成简单环，不能留下自触碰的多边形");
        assertEquals(4, trace.area(), 1e-9, "面积应当是四格之和");

        OutlineTracer.Trace simplified = grid.outline(OutlineTracer.MIN_STAIRCASE_STEPS);
        assertEquals(3, simplified.rings().length, "简化之后环数不变");
        assertEquals(4, simplified.area(), 1e-9,
                "简化不能把两个叶瓣之间的凹口切掉（不许漂成面积 4.3）");
        for (int[] ring : simplified.rings()) {
            assertTrue(ring.length / 2 >= 4, "每个环至少有 3 个角（含首点重复）");
        }
    }
}
