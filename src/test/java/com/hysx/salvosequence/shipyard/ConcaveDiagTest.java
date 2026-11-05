package com.hysx.salvosequence.shipyard;

import org.junit.jupiter.api.Test;

/** 一次性诊断：把凹形舰体的格数与量值打出来，定位断言差异在哪一侧。 */
class ConcaveDiagTest {

    private static void dump(String tag, HullGrid grid) {
        OutlineTracer.Trace raw = grid.outline(0);
        OutlineTracer.Trace t = grid.outline();
        System.out.println("[diag] " + tag + " cells=" + grid.cellCount()
                + " | RAW rings=" + raw.rings().length + " pts=" + raw.pointCount()
                + " area=" + raw.area() + " perim=" + raw.perimeter()
                + " | SIMP rings=" + t.rings().length + " pts=" + t.pointCount()
                + " area=" + t.area() + " perim=" + t.perimeter()
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
    void dumpConcaveShapes() {
        HullGrid comb = new HullGrid(12, 8, 2);
        comb.fillRect(1, 1, 8, 6);
        comb.set(8, 2, false);
        comb.set(8, 3, false);
        comb.set(8, 5, false);
        dump("comb", comb);

        HullGrid h = new HullGrid(12, 4, 2);
        h.fillRect(0, 0, 12, 4);
        for (int y = 0; y < 2; y++) {
            h.set(0, y, false);
            h.set(1, y, false);
            h.set(10, y, false);
            h.set(11, y, false);
        }
        dump("H", h);

        HullGrid c = new HullGrid(10, 6, 2);
        c.fillRect(0, 0, 10, 6);
        for (int y = 1; y <= 4; y++) {
            for (int x = 4; x <= 9; x++) {
                c.set(x, y, false);
            }
        }
        dump("C", c);

        HullGrid slot = new HullGrid(10, 8, 2);
        slot.fillRect(1, 1, 8, 6);
        for (int y = 1; y <= 4; y++) {
            slot.set(4, y, false);
            slot.set(5, y, false);
        }
        dump("slot", slot);
    }
}
