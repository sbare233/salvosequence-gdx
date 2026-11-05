package com.hysx.salvosequence.shipyard;

import java.util.Locale;

/**
 * 一个舰体模块的定义（纯数据，不可变）。
 *
 * <h2>为什么是「格子里的矩形」而不是任意多边形</h2>
 * <p>见 {@code docs/SHIP-BUILDING.md} §6：功能侧只有方格，外观由自动蒙皮拟合出来。
 * 模块因此只需要一个整数矩形，不需要顶点表——肉鸽生成器、掉落、存档都只跟这四个数打交道。</p>
 *
 * <h2>尺寸一律用格，不用像素</h2>
 * <p>{@link ModuleShape} 的单位是格。一格是多少像素由渲染侧决定（见
 * {@link ShipComposer#DEFAULT_CELL_PX}），数据层不掺像素量纲，换分辨率不用改数据。</p>
 *
 * <h2>绘制顺序</h2>
 * <p>每个模块声明自己属于哪一层（{@link ShipLayer}）。层与层之间的先后是硬约定，
 * 不是调用顺序决定的——<b>炮塔永远在模块之上</b>，这一点写在 {@link ShipComposer} 的注释里。</p>
 *
 * @param id          稳定标识（存档、掉落表、调试输出都用它，不许改）
 * @param name        显示名（英文大写，点阵字体只认 ASCII）
 * @param shape       占格尺寸
 * @param kind        功能种类
 * @param durability  耐久
 * @param mass        质量（影响转向与加速，暂存数据）
 * @param cost        造价
 * @param layer       画在哪一层
 * @param socketCount 武器挂点数（0 = 不能装炮）
 * @param innerWall   是否自带内墙（内墙是涂装分区的边界，见 §6.5）
 * @param description 一句话说明，给船坞界面用
 */
public record Module(
        String id,
        String name,
        ModuleShape shape,
        ModuleKind kind,
        double durability,
        double mass,
        int cost,
        ShipLayer layer,
        int socketCount,
        boolean innerWall,
        String description) {

    public Module {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("模块必须有 id");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("模块 " + id + " 必须有显示名");
        }
        if (shape == null || kind == null || layer == null) {
            throw new IllegalArgumentException("模块 " + id + " 的 shape/kind/layer 不能为空");
        }
        if (socketCount < 0) {
            throw new IllegalArgumentException("模块 " + id + " 的挂点数不能是负数");
        }
        if (description == null) {
            description = "";
        }
    }

    /** 占地格数。 */
    public int cells() {
        return shape.cells();
    }

    /** 能不能装武器。 */
    public boolean canMount() {
        return socketCount > 0;
    }

    /** 是不是武器座（挂点只长在它身上）。 */
    public boolean isMount() {
        return kind == ModuleKind.WEAPON_MOUNT;
    }

    /** 调试用的一行摘要。 */
    public String summary() {
        return String.format(Locale.ROOT, "%-14s %-12s %-10s w=%-2d h=%-2d cells=%-3d hp=%-6.1f mass=%-5.1f cost=%-3d layer=%-9s sock=%d%s",
                id, name, kind.label(), shape.w(), shape.h(), cells(),
                durability, mass, cost, layer.label(), socketCount,
                innerWall ? " wall" : "");
    }
}
