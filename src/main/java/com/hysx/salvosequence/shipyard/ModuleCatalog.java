package com.hysx.salvosequence.shipyard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块总表。
 *
 * <h2>这份表就是「玩家能拿到什么」的全部</h2>
 * <p>掉落、船坞、敌人拼装、存档都只认这里的 id。加模块 = 在这里加一行，别处不用改。</p>
 *
 * <h2>尺寸档是刻意的</h2>
 * <p>用户要求「要点不同大小的，长方形的也要」。所以装甲给了 8x6 / 5x5 / 4x2 / 3x4 / 2x3 / 2x2 / 1x1
 * ——<b>横的、竖的、方的都有</b>。这不违反 §5.1 的「种类要少」：它们仍然是<b>一种</b>装甲，
 * 只是尺寸档不同；玩家一次只需在「装甲」这个词下面挑大小，不需要理解七种新功能。</p>
 *
 * <h2>数值口径</h2>
 * <ul>
 *   <li>{@code durability} 大致是「格数 × 系数」的量级，装甲最高、武器座最低。</li>
 *   <li>{@code mass} 影响推重比；装甲重、骨架轻，这是「装甲与机动二选一」的来源。</li>
 *   <li>{@code cost} 是掉落折算用的相对值。</li>
 * </ul>
 */
public final class ModuleCatalog {

    private ModuleCatalog() {
    }

    // ===== 核心 =====
    /** 指挥核心：全船唯一，5x5。用户点名的尺寸。 */
    public static final Module CORE_COMMAND = new Module(
            "core.command", "COMMAND CORE", ModuleShape.square(5), ModuleKind.CORE,
            260.0, 12.0, 0, ShipLayer.MODULE, 0, true,
            "全船唯一。被打掉就结束。想装炮得在旁边贴武器座。");

    /** 小型舵机核心：给小型护卫舰用，3x3。 */
    public static final Module CORE_PINNACE = new Module(
            "core.pinnace", "PINNACE CORE", ModuleShape.square(3), ModuleKind.CORE,
            120.0, 6.0, 0, ShipLayer.MODULE, 0, true,
            "轻量核心。耐久低，换来更小的靶子和更低的造价。");

    // ===== 骨架 =====
    public static final Module HULL_BEAM = new Module(
            "hull.beam", "HULL BEAM", new ModuleShape(6, 1), ModuleKind.HULL,
            40.0, 2.0, 4, ShipLayer.MODULE, 0, false,
            "最便宜的长条。撑出船的长度。");

    public static final Module HULL_SPAR = new Module(
            "hull.spar", "HULL SPAR", new ModuleShape(8, 2), ModuleKind.HULL,
            70.0, 3.5, 7, ShipLayer.MODULE, 0, false,
            "加宽的纵梁。中段承力。");

    public static final Module HULL_FRAME = new Module(
            "hull.frame", "HULL FRAME", ModuleShape.square(2), ModuleKind.HULL,
            30.0, 1.5, 3, ShipLayer.MODULE, 0, false,
            "2x2 方块。万能填充，连接两段船体。");

    // ===== 装甲（尺寸档最多的那一类）=====
    public static final Module ARMOR_PLATE = new Module(
            "armor.plate", "ARMOR PLATE", new ModuleShape(8, 6), ModuleKind.ARMOR,
            200.0, 16.0, 22, ShipLayer.MODULE, 0, true,
            "最大块的装甲。贴在正对敌人的那一面。");

    public static final Module ARMOR_BAY = new Module(
            "armor.bay", "ARMOR BAY", ModuleShape.square(5), ModuleKind.ARMOR,
            140.0, 11.0, 15, ShipLayer.MODULE, 0, true,
            "5x5 方形装甲。放核心周围最划算。");

    public static final Module ARMOR_BELT = new Module(
            "armor.belt", "ARMOR BELT", new ModuleShape(4, 2), ModuleKind.ARMOR,
            90.0, 7.0, 9, ShipLayer.MODULE, 0, true,
            "横向装甲带。舷侧一排，包住中段。");

    public static final Module ARMOR_SPALL = new Module(
            "armor.spall", "SPALL LINER", new ModuleShape(3, 4), ModuleKind.ARMOR,
            80.0, 6.0, 8, ShipLayer.MODULE, 0, false,
            "竖向衬层。轻一点，耐久少一点，挡住碎片。");

    public static final Module ARMOR_PATCH = new Module(
            "armor.patch", "ARMOR PATCH", new ModuleShape(2, 3), ModuleKind.ARMOR,
            55.0, 4.5, 6, ShipLayer.MODULE, 0, false,
            "小板。补在缺口上，不用为了一格买整块。");

    public static final Module ARMOR_TILE = new Module(
            "armor.tile", "ARMOR TILE", ModuleShape.square(2), ModuleKind.ARMOR,
            40.0, 3.5, 5, ShipLayer.MODULE, 0, false,
            "2x2 装甲。肉鸽里最常见的掉落。");

    public static final Module ARMOR_SCALE = new Module(
            "armor.scale", "ARMOR SCALE", ModuleShape.single(), ModuleKind.ARMOR,
            22.0, 2.0, 3, ShipLayer.MODULE, 0, false,
            "单格。只能补一格的洞。");

    // ===== 武器座 =====
    /** 三联装炮座：用户点名的 3x4。 */
    public static final Module MOUNT_TRIPLE = new Module(
            "mount.triple", "TRIPLE MOUNT", new ModuleShape(3, 4), ModuleKind.WEAPON_MOUNT,
            60.0, 5.0, 14, ShipLayer.MOUNT, 1, false,
            "3x4 竖座。专为三联装炮塔设计。");

    public static final Module MOUNT_TWIN = new Module(
            "mount.twin", "TWIN MOUNT", ModuleShape.square(2), ModuleKind.WEAPON_MOUNT,
            35.0, 3.0, 8, ShipLayer.MOUNT, 1, false,
            "2x2 小座。只能挂一门轻炮。");

    public static final Module MOUNT_BROADSIDE = new Module(
            "mount.broadside", "BROADSIDE MOUNT", new ModuleShape(5, 5), ModuleKind.WEAPON_MOUNT,
            110.0, 9.0, 26, ShipLayer.MOUNT, 3, false,
            "5x5 大座，三个挂点。舷侧齐射的核心。");

    public static final Module MOUNT_SPONSON = new Module(
            "mount.sponson", "SPONSON", new ModuleShape(4, 2), ModuleKind.WEAPON_MOUNT,
            45.0, 4.0, 11, ShipLayer.MOUNT, 2, false,
            "4x2 横向炮廓。伸出舷外，射界好、被打中概率也高。");

    // ===== 引擎 =====
    public static final Module ENGINE_MAIN = new Module(
            "engine.main", "MAIN DRIVE", new ModuleShape(4, 5), ModuleKind.ENGINE,
            95.0, 8.0, 18, ShipLayer.MODULE, 0, true,
            "主推进。竖向安装，尾喷口朝后。");

    public static final Module ENGINE_BOOSTER = new Module(
            "engine.booster", "BOOSTER", ModuleShape.square(2), ModuleKind.ENGINE,
            45.0, 5.0, 9, ShipLayer.MODULE, 0, false,
            "2x2 辅助推进。加推力，也加被点燃的风险。");

    public static final Module ENGINE_VECTOR = new Module(
            "engine.vector", "VECTOR NOZZLE", new ModuleShape(3, 3), ModuleKind.ENGINE,
            55.0, 4.5, 12, ShipLayer.MODULE, 0, false,
            "3x3 矢量喷口。转向快，直线推力一般。");

    private static final List<Module> ALL = List.of(
            CORE_COMMAND, CORE_PINNACE,
            HULL_BEAM, HULL_SPAR, HULL_FRAME,
            ARMOR_PLATE, ARMOR_BAY, ARMOR_BELT, ARMOR_SPALL, ARMOR_PATCH, ARMOR_TILE, ARMOR_SCALE,
            MOUNT_TRIPLE, MOUNT_TWIN, MOUNT_BROADSIDE, MOUNT_SPONSON,
            ENGINE_MAIN, ENGINE_BOOSTER, ENGINE_VECTOR);

    private static final Map<String, Module> BY_ID = buildIndex();

    private static Map<String, Module> buildIndex() {
        Map<String, Module> map = new LinkedHashMap<>();
        for (Module m : ALL) {
            if (map.put(m.id(), m) != null) {
                throw new IllegalStateException("模块 id 重复: " + m.id());
            }
            // 数据错要在启动时炸，不要等到玩家拼出一艘非法船才发现。
            if (m.socketCount() > 0 && !m.isMount()) {
                throw new IllegalStateException("只有武器座能挂炮: " + m.id());
            }
            if (m.socketCount() > m.shape().cells()) {
                throw new IllegalStateException("挂点比格子还多: " + m.id());
            }
        }
        return Map.copyOf(map);
    }

    /** 全部模块（顺序即列表顺序）。 */
    public static List<Module> all() {
        return ALL;
    }

    /** 按 id 取；找不到抛异常——静默返回 null 会让掉落表悄悄少东西。 */
    public static Module byId(String id) {
        Module m = BY_ID.get(id);
        if (m == null) {
            throw new IllegalArgumentException("没有这个模块: " + id);
        }
        return m;
    }

    public static boolean has(String id) {
        return BY_ID.containsKey(id);
    }

    /** 某一类的全部模块。 */
    public static List<Module> ofKind(ModuleKind kind) {
        List<Module> out = new ArrayList<>();
        for (Module m : ALL) {
            if (m.kind() == kind) {
                out.add(m);
            }
        }
        return List.copyOf(out);
    }

    /** 能装炮的模块，按挂点数从少到多。 */
    public static List<Module> mounts() {
        return ofKind(ModuleKind.WEAPON_MOUNT);
    }

    /** 所有出现过的尺寸档（去重，按格数升序）。用于核对「横的竖的方的都有」。 */
    public static List<ModuleShape> distinctShapes() {
        List<ModuleShape> out = new ArrayList<>();
        for (Module m : ALL) {
            if (!out.contains(m.shape())) {
                out.add(m.shape());
            }
        }
        out.sort((a, b) -> a.cells() != b.cells()
                ? Integer.compare(a.cells(), b.cells())
                : Integer.compare(a.w(), b.w()));
        return List.copyOf(out);
    }
}
