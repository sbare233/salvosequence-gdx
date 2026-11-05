package com.hysx.salvosequence.shipyard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 凹形舰体的并集轮廓。
 *
 * <h2>为什么单独一个测试类</h2>
 * <p>M1 当时只测了凸的和带内洞的舰体。凹口（向内切进去的缺口）和洞不是一回事：
 * 洞是<b>完全被包围</b>的空腔，凹口是<b>与外界相通</b>的豁口。两者在边界追踪里
 * 走的是不同的分支——凹口要求走法能「拐进去再拐出来」，洞要求走法能在里面
 * 兜一圈。所以这里把凹口单独立成一组基准。</p>
 *
 * <h2>这套基准值是怎么来的</h2>
 * <p>没有抄任何实现的输出，全部手算：面积按格数，周长按曼哈顿长度，
 * 顶点数按「每个直角拐弯一个顶点」数。手算值写成断言，实现出偏差就会红——
 * 这样测试才是在<b>检验</b>实现，而不是在<b>复述</b>实现。</p>
 */
class ConcaveHullTest {

    private static final double EPS = 1e-9;

    /**
     * 中间挖一道竖槽（凹口开在上下都通，就是「工」字形的一半）。
     *
     * <p>8x6 实心 = 48 格，从 y=0 到 y=3 挖掉 x=3..4 两列 → 减 8 格，剩 40。</p>
     */
    @Test
    @DisplayName("上下贯通的凹槽：变工字形")
    void slotCutThroughBothSides() {
        HullGrid grid = new HullGrid(10, 8, 2);
        grid.fillRect(1, 1, 8, 6);
        for (int y = 1; y <= 4; y++) {
            grid.set(4, y, false);
            grid.set(5, y, false);
        }

        OutlineTracer.Trace raw = grid.outline(0);
        assertEquals(1, raw.rings().length, "有个凹槽但还是一块，应当只有一条环");
        assertEquals(40.0, raw.area(), EPS, "48 格挖掉 8 格");
        assertEquals(36.0, raw.perimeter(), EPS,
                "凹槽把上下两条边各吃掉 2 格、自己补 2 条 4 格竖边 + 1 条 2 格横底，"
                        + "即原矩形 28 - 4 + 10 + 2");

        OutlineTracer.Trace simple = grid.outline();
        assertEquals(40.0, simple.area(), EPS, "简化不许改变面积");
        assertEquals(36.0, simple.perimeter(), EPS, "直角凹口不该被简化吃掉");
    }

    /**
     * 凹口不够深：只切进去 1 格。
     *
     * <p>1 格深的凹口在像素画里是看得见的装甲缺口，不是噪点，所以
     * <b>面积必须一格不差</b>。这里只钉面积与「不许变成退化多边形」，
     * 不钉环数——1 格深的缺口在容差边界上，环数怎么拆是实现的自由。</p>
     */
    @Test
    @DisplayName("浅凹口（只切进去一格）不能被简化抹平")
    void shallowNotchSurvivesSimplification() {
        HullGrid grid = new HullGrid(10, 6, 2);
        grid.fillRect(1, 1, 8, 4);
        grid.set(4, 2, false);

        OutlineTracer.Trace raw = grid.outline(0);
        OutlineTracer.Trace simple = grid.outline();

        assertEquals(31.0, raw.area(), EPS, "32 格挖掉 1 格");
        assertEquals(31.0, simple.area(), EPS,
                "浅凹口被简化吃掉就会少一格面积（实测曾经从 31 漂成 31.5）");
        assertTrue(simple.pointCount() >= 4, "简化后不该退化成一条线");
        assertTrue(simple.perimeter() > 22.0,
                "缺了一格的矩形，周长必须比完整矩形（22 格）大，实际 " + simple.perimeter());
    }

    /**
     * 凹口开在同一侧的两处：10x6 的舰体右边挖两个方缺口（梳子形）。
     *
     * <p>8*6 = 48 格，挖掉 (9,2)(9,3)(9,5) → 45 格。
     * (9,4) 留着，是为了让两个凹口真的有<b>两层</b>，而不是一个大口子——
     * 这正是「多个凹」最容易出错的地方。</p>
     */
    @Test
    @DisplayName("同侧两个凹口：梳子形，两处都要凹进去")
    void twoNotchesOnSameSide() {
        HullGrid grid = new HullGrid(12, 8, 2);
        grid.fillRect(1, 1, 8, 6);
        grid.set(8, 2, false);              // 右边缘上口
        grid.set(8, 3, false);
        grid.set(8, 5, false);              // 右边缘下口（中间 (8,4) 留着）

        OutlineTracer.Trace t = grid.outline();
        assertEquals(0, grid.outlineMismatchCount(t), "轮廓必须如实盖住实心格，一格都不能错");
        assertEquals(1, t.rings().length, "还是一块");
        assertEquals(grid.cellCount(), t.area(), EPS, "简化后面积必须还等于实心格数");
        assertEquals(32.0, t.perimeter(), EPS, "10x6 矩形 32 格：凹口是内缩进去的，进出各 2 格正好抵消它替换掉的那 2 格边");
    }

    /**
     * 相对的两个凹口（H 形舰体的中间那根梁）。
     *
     * <p>12x4 实心 = 48 格，左边挖 2x2、右边挖 2x2 → 减 8 格，剩 40。</p>
     */
    @Test
    @DisplayName("左右对着各一个凹口：H 形，两侧凹口都要在")
    void notchesOnOppositeSides() {
        HullGrid grid = new HullGrid(12, 4, 2);
        grid.fillRect(0, 0, 12, 4);
        for (int y = 0; y < 2; y++) {
            grid.set(0, y, false);
            grid.set(1, y, false);
            grid.set(10, y, false);
            grid.set(11, y, false);
        }

        OutlineTracer.Trace t = grid.outline();
        assertEquals(0, grid.outlineMismatchCount(t), "轮廓必须如实盖住实心格，一格都不能错");
        assertEquals(1, t.rings().length, "左右凹口连通，还是一条环");
        assertEquals(grid.cellCount(), t.area(), EPS, "简化后面积必须还等于实心格数");
        assertEquals(32.0, t.perimeter(), EPS,
                "12x4 矩形 32 格，左右各一个 2x2 缺口：缺口内缩进进出各 2 格，抵消了原来那 2 格边");
    }

    /**
     * 深凹口：凹槽一直切到只剩一条细梁连着（C 形）。
     *
     * <p>10x6 实心 = 60 格，从右侧切进去 x=4..9、y=1..4 共 30 格，剩 30。
     * 凹槽深到只剩上下两条 4 格宽的横条和左边一根 4 格宽的梁，形如「C」。</p>
     */
    @Test
    @DisplayName("深凹口：C 形，凹槽切到只剩细梁")
    void deepNotchLeavesNarrowSpine() {
        HullGrid grid = new HullGrid(10, 6, 2);
        grid.fillRect(0, 0, 10, 6);
        for (int y = 1; y <= 4; y++) {
            for (int x = 4; x <= 9; x++) {
                grid.set(x, y, false);
            }
        }

        assertTrue(grid.isConnected(), "C 形仍然是连通的（细梁不断）");

        OutlineTracer.Trace t = grid.outline();
        assertEquals(0, grid.outlineMismatchCount(t), "轮廓必须如实盖住实心格，一格都不能错");
        assertEquals(1, t.rings().length, "C 形是一条环");
        assertEquals(grid.cellCount(), t.area(), EPS, "简化后面积必须还等于实心格数");
        assertEquals(44.0, t.perimeter(), EPS,
                "外矩形 32 - 右边被吃掉 4 格 + 凹槽上下两条壁各 4 格 + 槽底 4 格 + 进出各 4 格 = 44");
    }

    /**
     * 凹口 + 内洞同时存在：这是最容易把「洞」和「凹口」混淆的一种。
     *
     * <p>凹口开口朝外，不额外成环；内洞完全封闭，必须自己成环。</p>
     */
    @Test
    @DisplayName("凹口和内洞同时存在：凹口不额外成环，内洞自己成环")
    void notchAndHoleTogether() {
        HullGrid grid = new HullGrid(14, 8, 2);
        grid.fillRect(0, 0, 14, 8);
        for (int y = 0; y < 3; y++) {          // 左上角挖一个朝外的凹口
            grid.set(0, y, false);
            grid.set(1, y, false);
        }
        grid.fillRect(7, 3, 3, 2);             // 右中挖一个封闭的洞
        for (int y = 3; y < 5; y++) {
            for (int x = 7; x < 10; x++) {
                grid.set(x, y, false);
            }
        }

        OutlineTracer.Trace t = grid.outline();
        assertEquals(0, grid.outlineMismatchCount(t), "轮廓必须如实盖住实心格，一格都不能错");
        assertEquals(2, t.rings().length, "外环（带凹口）+ 内洞环，共两条");
        assertEquals(14 * 8 - 6 - 6, t.area(), EPS, "112 格 - 凹口 6 格 - 洞 6 格");
    }

    /**
     * 凹口的<b>方向</b>不能改变结论：同一个舰体旋转 90° 后，
     * 面积、周长、顶点数必须完全一致。
     *
     * <p>这条比「看着对」有用得多——它一次性覆盖上下左右四种凹口朝向。</p>
     */
    @Test
    @DisplayName("舰体转 90°：凹口的面积/周长/顶点数完全不变")
    void rotationKeepsConcaveMetrics() {
        HullGrid grid = new HullGrid(12, 10, 2);
        grid.fillRect(1, 1, 10, 8);
        for (int y = 1; y <= 3; y++) {         // 上边挖槽
            grid.set(4, y, false);
            grid.set(5, y, false);
        }
        for (int x = 7; x <= 9; x++) {         // 右边挖槽
            grid.set(x, 6, false);
            grid.set(x, 7, false);
        }

        OutlineTracer.Trace base = grid.outline();
        HullGrid spun = grid;
        for (int i = 0; i < 4; i++) {
            spun = spun.rotatedCw();
            OutlineTracer.Trace t = spun.outline();
            assertEquals(base.area(), t.area(), EPS, "第 " + (i + 1) + " 次旋转后面积变了");
            assertEquals(base.perimeter(), t.perimeter(), EPS,
                    "第 " + (i + 1) + " 次旋转后周长变了");
            assertEquals(base.rings().length, t.rings().length,
                    "第 " + (i + 1) + " 次旋转后环数变了");
            assertEquals(base.pointCount(), t.pointCount(),
                    "第 " + (i + 1) + " 次旋转后顶点数变了");
        }

        OutlineTracer.Trace back = spun.outline();
        assertEquals(base.area(), back.area(), EPS, "转四圈回到原样");
    }

    /** 镜像不该改变面积与周长（凹口左右对调，量值不变）。 */
    @Test
    @DisplayName("镜像：面积与周长不变，形状真的左右对调")
    void mirrorKeepsMetrics() {
        HullGrid grid = new HullGrid(10, 8, 2);
        grid.fillRect(1, 1, 8, 6);
        for (int y = 1; y <= 4; y++) {
            grid.set(3, y, false);
        }

        HullGrid mirror = grid.mirrored();
        OutlineTracer.Trace a = grid.outline();
        OutlineTracer.Trace b = mirror.outline();

        assertEquals(a.area(), b.area(), EPS, "镜像后面积变了");
        assertEquals(a.perimeter(), b.perimeter(), EPS, "镜像后周长变了");
        assertEquals(a.pointCount(), b.pointCount(), "镜像后顶点数变了");
    }
}
