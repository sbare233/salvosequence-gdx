package com.hysx.salvosequence.shipyard;

import java.util.Locale;

/**
 * 一种武器的定义（纯数据，不可变）。
 *
 * <h2>武器不是模块</h2>
 * <p>“武器座”是模块（占格、有耐久、能被摧毁）；“炮”是挂在武器座挂点上的东西
 * （挂点见 {@link MountRules#slotsFor}）。这样拆的好处：被打掉一个挂点只哑一门炮，船还在；
 * 而武器本身可以随便加种类，不影响舰体生成与蒙皮——见 {@code docs/SHIP-BUILDING.md} §5.1
 * 「武器种类算内容，不算舱室种类」。</p>
 *
 * <h2>尺寸与 {@link ModuleShape} 不是一回事</h2>
 * <p>{@link ModuleShape} 是「占几个格」；这里的 {@code mountW x mountH} 是
 * <b>炮塔贴图占几个格</b>，两者刻意分开：一门 3x4 的三联装炮塔可以挂在一个 5x5 的武器座上，
 * 也可以挂在 2x4 的窄座上（只要挂点数够、格子包得住）。</p>
 *
 * <p>绘制时炮塔走 {@link ShipLayer#TURRET}，永远压在模块之上——这是用户点名要的规则。</p>
 *
 * @param id           稳定标识
 * @param name         显示名（大写 ASCII）
 * @param mountW       炮塔贴图宽（格）
 * @param mountH       炮塔贴图高（格）
 * @param barrels      炮管数（1 单装 / 2 双联 / 3 三联 …）
 * @param damage       单发伤害
 * @param rangePx      射程（设计像素）
 * @param cooldown     冷却（秒）
 * @param projectileId 弹种标识，交给渲染与战斗侧取弹道贴图
 * @param cost         造价
 * @param description  一句话说明
 */
public record Weapon(
        String id,
        String name,
        int mountW,
        int mountH,
        int barrels,
        double damage,
        double rangePx,
        double cooldown,
        String projectileId,
        int cost,
        String description) {

    public Weapon {
        if (id == null || id.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("武器必须有 id 与显示名");
        }
        if (mountW <= 0 || mountH <= 0) {
            throw new IllegalArgumentException("武器 " + id + " 的炮塔尺寸必须是正整数格");
        }
        if (barrels <= 0) {
            throw new IllegalArgumentException("武器 " + id + " 的炮管数必须为正");
        }
        if (cooldown <= 0) {
            throw new IllegalArgumentException("武器 " + id + " 的冷却必须为正，收到 " + cooldown);
        }
        if (projectileId == null || projectileId.isBlank()) {
            throw new IllegalArgumentException("武器 " + id + " 必须指定弹种");
        }
        if (description == null) {
            description = "";
        }
    }

    /** 炮塔占几格。 */
    public int cells() {
        return mountW * mountH;
    }

    /** 炮塔贴图尺寸串，形如 {@code 3x4}。 */
    public String mountLabel() {
        return mountW + "x" + mountH;
    }

    /** 连装名：1=单装 2=双联 3=三联 4=四联。 */
    public String barrelLabel() {
        return switch (barrels) {
            case 1 -> "SINGLE";
            case 2 -> "TWIN";
            case 3 -> "TRIPLE";
            case 4 -> "QUAD";
            default -> barrels + "-BARREL";
        };
    }

    /** 每秒理论输出（DPS），用来横向比武器，不参与战斗计算。 */
    public double dps() {
        return damage * barrels / cooldown;
    }

    public String summary() {
        return String.format(Locale.ROOT, "%-14s %-22s turret=%-4s %-6s dmg=%-4.1f barrels=%d range=%-5.0f cd=%-4.2f dps=%-5.1f cost=%-3d ammo=%s",
                id, name, mountLabel(), barrelLabel(), damage, barrels, rangePx, cooldown, dps(), cost, projectileId);
    }
}
