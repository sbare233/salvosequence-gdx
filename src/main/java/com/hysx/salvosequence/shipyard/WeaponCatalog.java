package com.hysx.salvosequence.shipyard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 武器总表。
 *
 * <h2>三种炮的定位</h2>
 * <ul>
 *   <li><b>机炮</b>（VULCAN / TWIN / TRIPLE / QUAD）：冷却短、单发低、靠连发。射程中。</li>
 *   <li><b>导弹</b>：冷却长、单发高、射程远，弹道可追尾。</li>
 *   <li><b>轨道炮</b>：最远、最疼、最慢，用来逼玩家走位。</li>
 * </ul>
 *
 * <p>三联装 {@link #CANNON_TRIPLE} 是用户点名的「3x4 三联装炮」，炮塔贴图占 3x4 格，
 * 正好配 {@link ModuleCatalog#MOUNT_TRIPLE} 那个 3x4 竖座。</p>
 *
 * <h2>炮塔贴图是可以比挂点大的</h2>
 * <p>挂点是 1 格坐标，炮塔贴图 2x2~3x4 都行；贴图会以挂点为中心铺开，然后<b>整层压在模块之上</b>
 * （{@link ShipLayer#TURRET}）。所以「炮管伸到旁边的装甲板上面」是预期效果，不是错误。</p>
 */
public final class WeaponCatalog {

    private WeaponCatalog() {
    }

    // ===== 机炮 =====
    public static final Weapon VULCAN = new Weapon(
            "gun.vulcan", "VULCAN", 1, 2, 1,
            2.5, 150.0, 0.14, "bolt.light", 6,
            "最低配。单管，泼弹。");

    public static final Weapon CANNON_TWIN = new Weapon(
            "gun.twin", "TWIN CANNON", 2, 2, 2,
            7.0, 190.0, 0.55, "bolt.heavy", 14,
            "双联装。细节框里点名的那门炮。");

    /** 用户点名的 3x4 三联装。 */
    public static final Weapon CANNON_TRIPLE = new Weapon(
            "gun.triple", "TRIPLE CANNON", 3, 4, 3,
            8.0, 210.0, 0.72, "bolt.heavy", 26,
            "三联装。3x4 炮塔，齐射很爽，装填也慢。");

    public static final Weapon CANNON_QUAD = new Weapon(
            "gun.quad", "QUAD CANNON", 4, 3, 4,
            6.5, 200.0, 0.80, "bolt.heavy", 34,
            "四联装。横向 4x3，需要 5x5 大座。");

    // ===== 导弹 =====
    public static final Weapon MISSILE_MICRO = new Weapon(
            "missile.micro", "MICRO MISSILE", 2, 2, 2,
            12.0, 260.0, 1.60, "missile", 18,
            "微型导弹巢。射程远，追尾。");

    public static final Weapon MISSILE_SALVO = new Weapon(
            "missile.salvo", "MISSILE SALVO", 4, 3, 4,
            16.0, 320.0, 2.40, "missile", 38,
            "四发齐射。一轮能把护卫舰打残。");

    // ===== 轨道炮 =====
    public static final Weapon LANCE_RAIL = new Weapon(
            "lance.rail", "RAIL LANCE", 3, 6, 1,
            46.0, 420.0, 3.20, "lance", 42,
            "轨道矛。一格宽、六格长的长条炮塔，最远最疼最慢。");

    private static final List<Weapon> ALL = List.of(
            VULCAN, CANNON_TWIN, CANNON_TRIPLE, CANNON_QUAD,
            MISSILE_MICRO, MISSILE_SALVO,
            LANCE_RAIL);

    private static final Map<String, Weapon> BY_ID = buildIndex();

    private static Map<String, Weapon> buildIndex() {
        Map<String, Weapon> map = new LinkedHashMap<>();
        for (Weapon w : ALL) {
            if (map.put(w.id(), w) != null) {
                throw new IllegalStateException("武器 id 重复: " + w.id());
            }
        }
        return Map.copyOf(map);
    }

    public static List<Weapon> all() {
        return ALL;
    }

    public static Weapon byId(String id) {
        Weapon w = BY_ID.get(id);
        if (w == null) {
            throw new IllegalArgumentException("没有这种武器: " + id);
        }
        return w;
    }

    public static boolean has(String id) {
        return BY_ID.containsKey(id);
    }

    /**
     * 这个模块能装的武器。<b>这是船坞界面「选中一个空挂点后弹出的那 3~5 个选项」的数据来源</b>
     * （见 {@code docs/SHIP-BUILDING.md} §5.2）。
     *
     * <p>规则：炮塔贴图必须能被模块包住（宽高都不超过），否则会伸到船体外面去。
     * 单门炮的射界不受限——俯视 2D 不做转向角。</p>
     */
    public static List<Weapon> compatibleWith(Module module) {
        if (module == null || !module.canMount()) {
            return List.of();
        }
        List<Weapon> out = new ArrayList<>();
        for (Weapon w : ALL) {
            if (w.mountW() <= module.shape().w() && w.mountH() <= module.shape().h()) {
                out.add(w);
            }
        }
        return List.copyOf(out);
    }

    /** 所有出现过的炮塔尺寸档（去重）。 */
    public static List<ModuleShape> distinctMountShapes() {
        List<ModuleShape> out = new ArrayList<>();
        for (Weapon w : ALL) {
            ModuleShape s = new ModuleShape(w.mountW(), w.mountH());
            if (!out.contains(s)) {
                out.add(s);
            }
        }
        return List.copyOf(out);
    }
}
