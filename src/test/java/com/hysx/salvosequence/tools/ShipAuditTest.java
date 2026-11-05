package com.hysx.salvosequence.tools;

import com.hysx.salvosequence.shipyard.HullGrid;
import com.hysx.salvosequence.shipyard.ShipComposer;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 一次性：把设计图上每个格子的占用者打出来，露出重叠与空洞。 */
class ShipAuditTest {

    @Test
    void dump() {
        List<ShipComposer.Placement> mods = ShipDemo.modules();
        System.out.println("=== 模块清单 ===");
        for (ShipComposer.Placement p : mods) {
            System.out.printf("%-16s %-5s (%2d,%2d)-(%2d,%2d)%n",
                    p.module().id(), p.module().shape().label(),
                    p.cellX(), p.cellY(), p.right(), p.bottom());
        }

        System.out.println("=== 重叠对 ===");
        for (int i = 0; i < mods.size(); i++) {
            for (int j = i + 1; j < mods.size(); j++) {
                if (mods.get(i).overlaps(mods.get(j))) {
                    ShipComposer.Placement a = mods.get(i);
                    ShipComposer.Placement b = mods.get(j);
                    int x0 = Math.max(a.cellX(), b.cellX());
                    int x1 = Math.min(a.right(), b.right());
                    int y0 = Math.max(a.cellY(), b.cellY());
                    int y1 = Math.min(a.bottom(), b.bottom());
                    System.out.printf("%-16s (%d,%d) x %-16s (%d,%d) 重叠区 x=%d..%d y=%d..%d%n",
                            a.module().id(), a.cellX(), a.cellY(),
                            b.module().id(), b.cellX(), b.cellY(),
                            x0, x1 - 1, y0, y1 - 1);
                }
            }
        }

        System.out.println("=== 占用图（. = 空, # = 实心） ===");
        HullGrid g = ShipDemo.grid();
        StringBuilder ruler = new StringBuilder("    ");
        for (int x = 0; x < g.width(); x++) {
            ruler.append(x % 10);
        }
        System.out.println(ruler);
        for (int y = 0; y < g.height(); y++) {
            StringBuilder row = new StringBuilder(String.format("%2d  ", y));
            for (int x = 0; x < g.width(); x++) {
                row.append(g.occupied(x, y) ? '#' : '.');
            }
            System.out.println(row);
        }

        System.out.println("=== 统计 ===");
        System.out.println("格数=" + g.cellCount() + " 连通=" + g.isConnected());
        System.out.println("原始轮廓 " + ShipDemo.rawOutline().rings().length + " 环 / "
                + ShipDemo.rawOutline().pointCount() + " 点");
        System.out.println("简化轮廓 " + ShipDemo.outline().rings().length + " 环 / "
                + ShipDemo.outline().pointCount() + " 点");
        System.out.println(ShipDemo.summary());
    }
}
