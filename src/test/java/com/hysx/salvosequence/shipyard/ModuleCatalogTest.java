package com.hysx.salvosequence.shipyard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模块／武器数据表的守卫测试。
 *
 * <p>这些断言防的不是「算法写错」，而是<b>数据表被改坏</b>：加模块时手滑写了 0 宽、
 * id 撞车、挂点配到格子外面、武器塔比座还大——这些都不会让游戏崩，
 * 只会让新手拿到一艘拼不起来的船。所以卡在这里。</p>
 */
class ModuleCatalogTest {

    @Test
    void catalogIdsAreUniqueAndLookupWorks() {
        Set<String> ids = new HashSet<>();
        for (Module m : ModuleCatalog.all()) {
            assertTrue(ids.add(m.id()), "模块 id 重复: " + m.id());
        }
        Set<String> weaponIds = new HashSet<>();
        for (Weapon w : WeaponCatalog.all()) {
            assertTrue(weaponIds.add(w.id()), "武器 id 重复: " + w.id());
        }
        assertNotNull(ModuleCatalog.byId("core.command"));
        assertNotNull(WeaponCatalog.byId("gun.triple"));
        assertThrows(IllegalArgumentException.class, () -> ModuleCatalog.byId("没有这个"));
    }

    /** 只有武器座能挂炮——这是 {@link MountRules} 的前提。 */
    @Test
    void onlyWeaponMountsCarrySockets() {
        for (Module m : ModuleCatalog.all()) {
            if (m.socketCount() > 0) {
                assertTrue(m.isMount(), m.id() + " 有挂点却不是武器座");
            }
            if (m.isMount()) {
                assertTrue(m.socketCount() > 0, m.id() + " 是武器座却没有挂点");
            }
        }
    }

    /** 用户点名要的：不同大小、长方形也要有。 */
    @Test
    void catalogCoversSeveralSizesIncludingRectangles() {
        List<ModuleShape> shapes = ModuleCatalog.distinctShapes();
        assertTrue(shapes.size() >= 8, "尺寸档太少，实际 " + shapes.size());

        long squares = shapes.stream().filter(ModuleShape::isSquare).count();
        long wides = shapes.stream().filter(ModuleShape::isWide).count();
        long talls = shapes.stream().filter(ModuleShape::isTall).count();
        assertTrue(squares >= 3, "方形档至少要有 3 种，实际 " + squares);
        assertTrue(wides >= 2, "横的尺寸档至少要有 2 种，实际 " + wides);
        assertTrue(talls >= 2, "竖的尺寸档至少要有 2 种，实际 " + talls);

        assertNotNull(ModuleCatalog.byId("core.command"), "5x5 核心舱应当在表里");
        assertEquals(5, ModuleCatalog.byId("core.command").shape().w());
        assertEquals(5, ModuleCatalog.byId("core.command").shape().h());
        assertEquals(3, ModuleCatalog.byId("mount.triple").shape().w(), "3x4 三联装座");
        assertEquals(4, ModuleCatalog.byId("mount.triple").shape().h());
    }

    @Test
    void shapeRejectsNonPositiveSizes() {
        assertThrows(IllegalArgumentException.class, () -> new ModuleShape(0, 3));
        assertThrows(IllegalArgumentException.class, () -> new ModuleShape(3, -1));
        assertEquals(6, new ModuleShape(3, 2).cells());
        assertEquals(new ModuleShape(4, 3), new ModuleShape(3, 4).rotated());
    }

    /** 挂点必须落在模块内，数量等于 socketCount，且互不重合。 */
    @Test
    void mountSlotsStayInsideTheModule() {
        for (Module m : ModuleCatalog.mounts()) {
            List<MountSlot> slots = MountRules.slotsFor(m);
            assertEquals(m.socketCount(), slots.size(), m.id() + " 的挂点数与声明不符");
            Set<String> seen = new HashSet<>();
            for (MountSlot s : slots) {
                assertTrue(s.fitsInside(m.shape()),
                        m.id() + " 的挂点 " + s.summary() + " 跑到 " + m.shape().label() + " 外面了");
                assertTrue(seen.add(s.dx() + "," + s.dy()), m.id() + " 有两个挂点重合在 " + s.summary());
            }
        }
    }

    /** 非武器座不能有挂点，声明了就是数据错，要当场炸。 */
    @Test
    void slotsForRejectsNonMountModules() {
        assertTrue(MountRules.slotsFor(ModuleCatalog.ARMOR_PLATE).isEmpty());
        assertTrue(MountRules.slotsFor(ModuleCatalog.CORE_COMMAND).isEmpty(),
                "核心不装炮：要装就在旁边贴武器座。这条断言把那个设计决定钉住");
        assertTrue(MountRules.slotsFor(ModuleCatalog.ENGINE_MAIN).isEmpty());

        // 真有模块被配错（非武器座却声明挂点）时，必须抛而不是静默忽略
        Module broken = new Module("test.broken", "BROKEN", ModuleShape.square(3),
                ModuleKind.ARMOR, 1.0, 1.0, 1, ShipLayer.MODULE, 2, false, "故意配错");
        assertThrows(IllegalArgumentException.class, () -> MountRules.slotsFor(broken));
    }

    /** 可装武器的筛选：炮塔贴图必须能被模块包住。 */
    @Test
    void weaponCompatibilityRespectsTurretFootprint() {
        Module small = ModuleCatalog.MOUNT_TWIN;          // 2x2
        Module big = ModuleCatalog.MOUNT_BROADSIDE;       // 5x5

        List<Weapon> smallFits = WeaponCatalog.compatibleWith(small);
        for (Weapon w : smallFits) {
            assertTrue(w.mountW() <= 2 && w.mountH() <= 2, w.id() + " 装不进 2x2");
        }
        assertTrue(smallFits.contains(WeaponCatalog.VULCAN), "1x2 的机炮应当能装进 2x2");
        assertFalse(smallFits.contains(WeaponCatalog.CANNON_TRIPLE), "3x4 三联装不该装进 2x2");

        List<Weapon> bigFits = WeaponCatalog.compatibleWith(big);
        assertTrue(bigFits.size() > smallFits.size(), "大座可选武器应当更多");
        assertTrue(bigFits.contains(WeaponCatalog.CANNON_QUAD), "5x5 应当能装 4x3 四联装");
        assertFalse(bigFits.contains(WeaponCatalog.LANCE_RAIL), "3x6 的轨道矛比 5x5 高，装不下");

        assertTrue(WeaponCatalog.compatibleWith(ModuleCatalog.ARMOR_PLATE).isEmpty(),
                "装甲板不能装炮");
    }

    /** 三联装就是用户点名的那门：3x4、三根管。 */
    @Test
    void tripleCannonIsThreeByFourWithThreeBarrels() {
        Weapon w = WeaponCatalog.CANNON_TRIPLE;
        assertEquals(3, w.mountW());
        assertEquals(4, w.mountH());
        assertEquals(3, w.barrels());
        assertEquals("TRIPLE", w.barrelLabel());
        assertTrue(ModuleCatalog.byId("mount.triple").shape().w() >= w.mountW());
        assertTrue(ModuleCatalog.byId("mount.triple").shape().h() >= w.mountH());
    }

    /** 层序：炮塔与武器座都必须画在模块之上。 */
    @Test
    void turretLayerIsAboveModules() {
        assertTrue(ShipLayer.TURRET.aboveModules(), "炮塔必须在模块之上");
        assertTrue(ShipLayer.MOUNT.aboveModules(), "武器座本体也在模块之上");
        assertTrue(ShipLayer.EFFECT.ordinal() > ShipLayer.TURRET.ordinal(), "特效在炮塔之上");
        assertFalse(ShipLayer.MODULE.aboveModules(), "模块自己不算「之上」");
        assertTrue(ShipLayer.HULL_BASE.ordinal() < ShipLayer.MODULE.ordinal(), "舰体底色在最下");

        for (Module m : ModuleCatalog.mounts()) {
            assertTrue(m.layer().aboveModules(), m.id() + " 的层不对");
        }
        for (Weapon w : WeaponCatalog.all()) {
            assertTrue(w.mountW() > 0 && w.mountH() > 0);
        }
    }

    /** 摆放重叠检测——船坞界面靠它给出「这个位置放不下」。 */
    @Test
    void overlapDetectionFindsConflicts() {
        List<ShipComposer.Placement> ok = new ArrayList<>();
        ok.add(new ShipComposer.Placement(ModuleCatalog.CORE_COMMAND, 0, 0));      // 0..5
        ok.add(new ShipComposer.Placement(ModuleCatalog.ARMOR_BAY, 5, 0));        // 5..10，边界相接不算重叠
        assertNull(ShipComposer.firstOverlap(ok), "边界相接不算重叠");

        List<ShipComposer.Placement> bad = new ArrayList<>(ok);
        bad.add(new ShipComposer.Placement(ModuleCatalog.ARMOR_TILE, 4, 4));
        assertNotNull(ShipComposer.firstOverlap(bad), "压到核心上应当被抓出来");
    }

    /** 真实拼一艘：挂点都落在模块里，且炮塔装得下。 */
    @Test
    void hardpointsLandInsideTheirOwner() {
        ShipComposer.Placement owner =
                new ShipComposer.Placement(ModuleCatalog.MOUNT_BROADSIDE, 10, 6);
        List<MountSlot> slots = MountRules.slotsFor(owner.module());
        List<Weapon> avail = WeaponCatalog.compatibleWith(owner.module());
        assertTrue(avail.size() >= slots.size(),
                "5x5 大座的可选武器至少要够每个挂点分一门，实际 " + avail.size() + " < " + slots.size());

        for (int i = 0; i < slots.size(); i++) {
            ShipComposer.Hardpoint hp =
                    new ShipComposer.Hardpoint(avail.get(i), owner, slots.get(i));
            int cx = hp.cellX();
            int cy = hp.cellY();
            assertTrue(cx >= owner.cellX() && cx < owner.right(),
                    "挂点 " + i + " 的 x=" + cx + " 不在模块横向范围内");
            assertTrue(cy >= owner.cellY() && cy < owner.bottom(),
                    "挂点 " + i + " 的 y=" + cy + " 不在模块纵向范围内");
        }
    }

    /** 不能挂炮的模块当挂载点，必须在构造期炸掉。 */
    @Test
    void hardpointRejectsNonMountOwner() {
        ShipComposer.Placement plate =
                new ShipComposer.Placement(ModuleCatalog.ARMOR_PLATE, 0, 0);
        assertThrows(IllegalArgumentException.class, () ->
                new ShipComposer.Hardpoint(WeaponCatalog.VULCAN, plate, MountSlot.at(0, 0)));
    }
}
