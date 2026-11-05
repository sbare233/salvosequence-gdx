package com.hysx.salvosequence.tools;

import com.hysx.salvosequence.shipyard.ShipComposer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拼起来的那艘船本身要是合法的。
 *
 * <p>这些断言看起来琐碎，但它们挡的是「设计图改错了却没人发现」：模块重叠、越界、
 * 舰体断成两截，在核对图上都可能一眼看不出来（尤其重叠时后画的会把先画的盖掉）。</p>
 */
class ShipDemoTest {

    @Test
    void 模块之间不许重叠() {
        String overlap = ShipDemo.firstOverlap();
        assertNull(overlap, "模块重叠了：" + overlap);
    }

    @Test
    void 模块不许超出网格() {
        String outside = ShipDemo.outOfBounds();
        assertNull(outside, "模块越界：" + outside);
    }

    @Test
    void 舰体必须连成一片() {
        assertTrue(ShipDemo.connected(), "四邻域下舰体断成了两截");
    }

    @Test
    void 舰体内部不许有空洞() {
        // 凹口是开口朝外的，不算洞；这条查的是「被实心格围住」的空格。
        assertEquals(0, ShipDemo.holeCount(), "舰体内部有洞：" + ShipDemo.holeList());
    }

    @Test
    void 自动蒙皮的轮廓必须盖住每一个实心格() {
        // 凹口是这里的关键：简化算法一旦在凹处抄近路，这里立刻不为 0。
        assertEquals(0, ShipDemo.outlineMismatch(), "简化后的轮廓切到实心格外面了");
    }

    @Test
    void 未简化的轮廓也必须正好覆盖实心格() {
        int mismatch = ShipDemo.grid().outlineMismatchCount(ShipDemo.rawOutline());
        assertEquals(0, mismatch, "原始轮廓与占用格不一致");
    }

    @Test
    void 至少要有几个凹口否则测不到凹形() {
        // 并集轮廓的顶点数明显多于凸包时，说明外形里确实有凹。
        // 4 个模块角点在外轮廓上是最少的情况（一个矩形）；只要 > 8 就必然有凹腔。
        int verts = ShipDemo.outline().pointCount();
        assertTrue(verts > 8, "舰体轮廓只有 " + verts + " 个角，太接近矩形了，测不到凹形");
    }

    @Test
    void 有炮塔而且都挂在合法的座上() {
        assertTrue(ShipDemo.guns().size() >= 4, "炮塔太少：" + ShipDemo.guns().size());
        for (ShipComposer.Hardpoint h : ShipDemo.guns()) {
            assertNotNull(h.weapon(), "挂载缺武器");
            assertTrue(h.owner().module().isMount(),
                    h.owner().module().id() + " 不是武器座却挂了炮");
        }
    }

    @Test
    void 至少有三联装与舷侧齐射两种规模() {
        int barrels = 0;
        for (ShipComposer.Hardpoint h : ShipDemo.guns()) {
            barrels += h.weapon().barrels();
        }
        assertTrue(barrels >= 8, "炮管总数只有 " + barrels + "，看不出大小炮的区别");
    }

    @Test
    void 尺寸与造价是合理量级() {
        // 26x16 的网格，实际占掉一半以上；造价别是 0（说明模块表没读到）。
        int cells = ShipDemo.cellCount();
        assertTrue(cells > 180 && cells < ShipDemo.GRID_W * ShipDemo.GRID_H,
                "占用格数 " + cells + " 不像一艘填过的船");
        assertTrue(ShipDemo.totalCost() > 100, "造价 " + ShipDemo.totalCost() + " 太低了");
    }

    @Test
    void 模块坐标落点与注释一致() {
        // 抽查两块，防止以后挪坐标时注释没跟着改（注释错比代码错更难发现）。
        boolean found = false;
        for (ShipComposer.Placement p : ShipDemo.modules()) {
            if (p.module().id().equals("core.command")) {
                assertEquals(5, p.cellX(), "指挥核心的 x 挪了，注释要跟着改");
                assertEquals(5, p.cellY(), "指挥核心的 y 挪了，注释要跟着改");
                found = true;
            }
        }
        assertTrue(found, "设计图里找不到指挥核心");
    }
}
