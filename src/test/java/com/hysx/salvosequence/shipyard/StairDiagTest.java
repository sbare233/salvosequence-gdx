package com.hysx.salvosequence.shipyard;

import org.junit.jupiter.api.Test;

/** 一次性诊断：真·连通的 45° 斜边（凸与凹两种）简化得对不对。 */
class StairDiagTest {

    private static void dump(String tag, HullGrid grid) {
        OutlineTracer.Trace raw = grid.outline(0);
        OutlineTracer.Trace t = grid.outline();
        System.out.println("[stair] " + tag + " cells=" + grid.cellCount()
                + " | RAW pts=" + raw.pointCount() + " area=" + raw.area()
                + " | SIMP pts=" + t.pointCount() + " area=" + t.area()
                + " | 盖格 raw=" + grid.outlineMismatchCount(raw)
                + " simp=" + grid.outlineMismatchCount(t));
        for (int[] ring : t.rings()) {
            StringBuilder sb = new StringBuilder("   ring:");
            for (int i = 0; i < ring.length; i += 2) {
                sb.append(" (").append(ring[i]).append(',').append(ring[i + 1]).append(')');
            }
            System.out.println(sb);
        }
    }

    @Test
    void dumpDiagonals() {
        // 实心块右上角切一刀 → 一条 45° 斜边（台阶式）
        HullGrid bow = new HullGrid(12, 8, 2);
        bow.fillRect(0, 0, 12, 8);
        for (int i = 0; i < 6; i++) {
            for (int y = 0; y <= i; y++) {
                bow.set(11 - i, y, false);
            }
        }
        dump("solidBow", bow);

        // 实心块右边挖一个 45° 的三角缺口 → 一条凹的 45° 斜边
        HullGrid cove = new HullGrid(12, 8, 2);
        cove.fillRect(0, 0, 12, 8);
        for (int i = 0; i < 6; i++) {
            for (int y = 0; y <= i; y++) {
                cove.set(11 - i, y, false);
            }
        }
        for (int i = 0; i < 3; i++) {
            for (int y = 3; y <= 3 + i; y++) {
                cove.set(0, y, false);
            }
        }
        dump("plusCove", cove);
    }
}
