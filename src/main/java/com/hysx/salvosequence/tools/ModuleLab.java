package com.hysx.salvosequence.tools;

import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.core.PixelFont;
import com.hysx.salvosequence.shipyard.Module;
import com.hysx.salvosequence.shipyard.ModuleCatalog;
import com.hysx.salvosequence.shipyard.ModuleKind;
import com.hysx.salvosequence.shipyard.MountRules;
import com.hysx.salvosequence.shipyard.MountSlot;
import com.hysx.salvosequence.shipyard.ShipComposer;
import com.hysx.salvosequence.shipyard.Weapon;
import com.hysx.salvosequence.shipyard.WeaponCatalog;
import com.hysx.salvosequence.core.Color;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 模块与武器的核对图。
 *
 * <h2>为什么要有这个</h2>
 * 模块表是数据，数据错了不会崩，只会让玩家拼出一艘奇怪的船。所以这里只做一件事：
 * <b>把数据画出来，让人一眼看出不对</b>。完整数值照旧打在 stdout，图只负责「看得见」。
 *
 * <h2>四格</h2>
 * <ol>
 *   <li><b>MODULES</b>：19 种模块各画一次占位图 + 名字 + 尺寸。</li>
 *   <li><b>WEAPONS</b>：7 种武器，炮塔占地 / 管数 / 伤害 / 射程 / 冷却。</li>
 *   <li><b>LAYER CHECK</b>：同一批模块摆三次——只有模块 / 加武器座 / 再加炮塔。
 *       第三格必须能看出<b>炮塔压在模块之上</b>（用户点名要求）。</li>
 *   <li><b>DEMO SHIP</b>：拿模块真拼一艘船，用来判断占位图在游戏尺度下认不认得出。</li>
 * </ol>
 *
 * <h2>环境变量</h2>
 * <ul>
 *   <li>{@code MODULE_OUT} — 输出目录，默认 {@code target}。</li>
 *   <li>{@code MODULE_SCALE} — 演示船的每格像素数，默认 2。</li>
 * </ul>
 */
public final class ModuleLab {

    private static final int CANVAS_W = 640;
    private static final int CANVAS_H = 360;

    public void start() throws Exception {
        File outDir = new File(System.getenv().getOrDefault("MODULE_OUT", "target"));
        int demoScale = readInt("MODULE_SCALE", 2);

        PixelArt art = new PixelArt(CANVAS_W, CANVAS_H);
        art.fill(Palette.SPACE_DEEP);
        PixelFont font = new PixelFont();
        font.draw(art, "MODULE + WEAPON LAB", 6, 8, Palette.CYAN);
        font.drawRight(art, "PLACEHOLDER ART", 634, 8, Palette.LINE_FAINT);

        printTables();

        moduleSheet(art, font);
        weaponSheet(art, font);
        layerCheck(art, font);
        demoShip(art, font, demoScale);

        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IllegalStateException("建不了输出目录: " + outDir);
        }
        File out = new File(outDir, "module-lab.png");
        art.savePng(out);
        System.out.println("[module-lab] 写出 " + out.getAbsolutePath()
                + "  模块 " + ModuleCatalog.all().size() + " 种 / 武器 " + WeaponCatalog.all().size() + " 种"
                + " / 尺寸档 " + ModuleCatalog.distinctShapes().size() + " 种");
        System.exit(0);
    }

    // ===== 数值表打到 stdout，核对图只负责「看得见」=====

    private static void printTables() {
        System.out.println("[module-lab] === 模块 " + ModuleCatalog.all().size() + " 种 ===");
        for (Module m : ModuleCatalog.all()) {
            System.out.println("[module-lab] " + m.summary());
        }
        System.out.println("[module-lab] === 武器 " + WeaponCatalog.all().size() + " 种 ===");
        for (Weapon w : WeaponCatalog.all()) {
            System.out.println("[module-lab] " + w.summary());
        }
        System.out.println("[module-lab] === 尺寸档 ===");
        for (var s : ModuleCatalog.distinctShapes()) {
            System.out.println("[module-lab]   " + s.label() + "  " + s.cells() + " 格"
                    + (s.isSquare() ? " 方形" : s.isWide() ? " 横" : " 竖"));
        }
        System.out.println("[module-lab] === 挂点 ===");
        for (Module m : ModuleCatalog.mounts()) {
            List<MountSlot> slots = MountRules.slotsFor(m);
            StringBuilder sb = new StringBuilder();
            for (MountSlot s : slots) {
                sb.append(' ').append(s.summary());
            }
            System.out.println("[module-lab]   " + m.id() + " " + m.shape().label()
                    + " -> " + slots.size() + " 个挂点" + sb);
            StringBuilder names = new StringBuilder();
            for (Weapon w : WeaponCatalog.compatibleWith(m)) {
                names.append(' ').append(w.id());
            }
            System.out.println("[module-lab]       可装:" + names);
        }
    }

    // ===== 格 1：模块总表 =====

    /**
     * 一格一种模块：形状画在左，名字与尺寸在它右边。
     *
     * <p>排版要点（都是实测踩出来的）：</p>
     * <ul>
     *   <li>形状与文字<b>不共用 y</b>——早先把名字画在「形状底边 + 7」，
     *       8x6 的大模块就把名字挤到下一格去了。</li>
     *   <li>字号固定 6px，所以列宽是数出来的：形状列 30px、名字列 42px（7 字符）、
     *       尺寸右对齐在名字列之后。任何一处估计错都会看起来像串行。</li>
     *   <li>尺寸靠右对齐画，不紧跟名字——名字长度不一，紧跟会让尺寸列参差不齐。</li>
     * </ul>
     */
    private static void moduleSheet(PixelArt art, PixelFont font) {
        panelFrame(art, font, 4, 16, 308, 166, "MODULES");

        final int cols = 2;                          // 两列，每列 10 行，19 种刚好
        final int colW = 152;
        final int rowH = 15;
        final int rows = 10;
        final int labelDx = 30;                      // 名字距形状左边
        final int sizeRight = 58;                    // 尺寸右对齐（相对形状左边）

        for (int col = 0; col < cols; col++) {
            int x = 8 + col * colW;
            // 表头写 "MOD" 不写 "MODULE"：面板标题已经写了 MODULES（占 y=21..29），
            // 表头挤在 y=30 会紧贴它的下沿，"MODULE" 又会顶到 SIZE 列（都实测过）。
            font.draw(art, "MOD", x + labelDx, 26, Palette.LINE_FAINT);
            font.drawRight(art, "SIZE", x + sizeRight, 26, Palette.LINE_FAINT);
            for (int r = 0; r < rows; r++) {
                int i = col * rows + r;
                if (i >= ModuleCatalog.all().size()) {
                    break;
                }
                Module m = ModuleCatalog.all().get(i);
                int ry = 38 + r * rowH;              // 15px 一行：形状 7px + 说明文字，不挤
                ShipComposer.drawModules(art, 1,
                        List.of(new ShipComposer.Placement(m, 0, 0)), x, ry + 2);
                font.draw(art, shortName(m), x + labelDx, ry + 2, kindColor(m.kind()));
                font.drawRight(art, m.shape().label(), x + sizeRight, ry + 2, Palette.LINE_DIM);
            }
        }
    }

    // ===== 格 2：武器总表 =====

    private static void weaponSheet(PixelArt art, PixelFont font) {
        panelFrame(art, font, 316, 16, 320, 340, "WEAPONS");

        int y = 28;
        for (Weapon w : WeaponCatalog.all()) {
            // 每门两行：第一行「名字 + 占地 + 管数」，第二行「伤害 + 射程 + 冷却」。
            // 一行塞四个字段会互相压字（字体 6px 一字，列宽是数出来的）。
            font.draw(art, shortenWeapon(w.id()), 322, y, Palette.WARN);
            font.draw(art, w.mountW() + "X" + w.mountH(), 388, y, Palette.LINE);
            font.draw(art, w.barrelLabel(), 424, y, Palette.BLUE);
            y += 10;
            font.draw(art, "DMG " + String.format("%.1f", w.damage()), 322, y, Palette.LINE_DIM);
            font.draw(art, "R" + String.format("%.0f", w.rangePx())
                    + " CD" + String.format("%.2f", w.cooldown()), 406, y, Palette.LINE_FAINT);
            font.drawRight(art, "DPS " + String.format("%.0f", w.dps()), 632, y, Palette.LINE_DIM);
            y += 12;
        }
        // 7 门 x 22px 正好顶到面板底部，这句不画——画了必然压住最后一行（实测过）
    }

    // ===== 格 3：层序对照 =====

    /**
     * 三小格：只有模块 / 模块+武器座 / 全部+炮塔。
     *
     * <p>刻意让炮管压在相邻模块上：要是炮塔的绘制遍写在模块之前，第三格的炮管会断掉。
     * 三格输入完全相同，只有绘制遍不同。</p>
     */
    private static void layerCheck(PixelArt art, PixelFont font) {
        panelFrame(art, font, 4, 188, 306, 168, "LAYER CHECK   TURRET ON TOP");

        List<ShipComposer.Placement> mods = demoModules();
        List<ShipComposer.Hardpoint> guns = demoWeapons(mods);

        int cell = 3;
        int oy = 236;
        int ox1 = 14;
        int ox2 = 116;
        int ox3 = 216;

        font.draw(art, "MODULES", ox1, 216, Palette.LINE_FAINT);
        font.draw(art, "+MOUNTS", ox2, 216, Palette.BLUE);
        font.draw(art, "+TURRETS", ox3, 216, Palette.WARN);

        ShipComposer.drawModules(art, cell, mods, ox1, oy);
        ShipComposer.draw(art, cell, mods, List.of(), ox2, oy, false);
        // 第三格不画底色，否则看不出炮塔与模块谁在上面
        ShipComposer.draw(art, cell, mods, guns, ox3, oy, false);

        font.draw(art, "SAME INPUT ORDER.", 14, 296, Palette.LINE_FAINT);
        font.draw(art, "ONLY THE DRAW PASS DIFFERS.", 14, 306, Palette.LINE_FAINT);
        font.draw(art, "IF A BARREL IS CUT OFF", 14, 326, Palette.WARN);
        font.draw(art, "THE LAYER ORDER IS WRONG.", 14, 336, Palette.WARN);
    }

    // ===== 格 4：真拼一艘 =====

    private static void demoShip(PixelArt art, PixelFont font, int cell) {
        panelFrame(art, font, 316, 188, 320, 168, "DEMO SHIP   x" + cell);

        List<ShipComposer.Placement> mods = demoShipModules();
        List<ShipComposer.Hardpoint> guns = demoShipWeapons(mods);

        font.draw(art, "COST " + costOf(mods) + "  CELLS " + cellsOf(mods)
                + "  MOUNTS " + guns.size(), 322, 212, Palette.LINE_DIM);

        int ox = 324;
        int oy = 232;
        ShipComposer.draw(art, cell, mods, guns, ox, oy, true);

        int[] b = ShipComposer.boundsCells(mods);
        font.draw(art, "GRID " + b[0] + "X" + b[1] + " CELLS = "
                + (b[0] * cell) + "X" + (b[1] * cell) + " PX", 322, 326, Palette.LINE_FAINT);
        font.draw(art, "EVERY MODULE HERE IS A PLACEHOLDER", 322, 340, Palette.LINE_FAINT);
    }

    // ===== 形状数据 =====

    /** 层序对照用的 12x9 小舰体：大小尺寸与横竖都有。 */
    private static List<ShipComposer.Placement> demoModules() {
        List<ShipComposer.Placement> out = new ArrayList<>();
        out.add(new ShipComposer.Placement(ModuleCatalog.CORE_COMMAND, 0, 2));          // 5x5
        out.add(new ShipComposer.Placement(ModuleCatalog.ARMOR_PLATE, 5, 2));          // 8x6
        out.add(new ShipComposer.Placement(ModuleCatalog.HULL_SPAR, 0, 7));            // 8x2
        out.add(new ShipComposer.Placement(ModuleCatalog.ARMOR_BELT, 8, 8));           // 4x2
        out.add(new ShipComposer.Placement(ModuleCatalog.ARMOR_SPALL, 13, 2));         // 3x4
        out.add(new ShipComposer.Placement(ModuleCatalog.MOUNT_BROADSIDE, 8, 3));      // 5x5
        out.add(new ShipComposer.Placement(ModuleCatalog.ENGINE_MAIN, 16, 3));         // 4x5
        return out;
    }

    private static List<ShipComposer.Hardpoint> demoWeapons(List<ShipComposer.Placement> mods) {
        List<ShipComposer.Hardpoint> out = new ArrayList<>();
        for (ShipComposer.Placement p : mods) {
            if (!p.module().canMount()) {
                continue;
            }
            List<MountSlot> slots = MountRules.slotsFor(p.module());
            List<Weapon> avail = WeaponCatalog.compatibleWith(p.module());
            for (int i = 0; i < slots.size() && i < avail.size(); i++) {
                out.add(new ShipComposer.Hardpoint(avail.get(i), p, slots.get(i)));
            }
        }
        return out;
    }

    /** 演示船：21x13 格，船型随便拼的，只要尺寸档够杂。 */
    private static List<ShipComposer.Placement> demoShipModules() {
        List<ShipComposer.Placement> out = new ArrayList<>();
        out.add(new ShipComposer.Placement(ModuleCatalog.HULL_BEAM, 6, 3));            // 6x1 中轴
        out.add(new ShipComposer.Placement(ModuleCatalog.CORE_COMMAND, 6, 4));         // 5x5 核心
        out.add(new ShipComposer.Placement(ModuleCatalog.ARMOR_PLATE, 1, 4));          // 8x6 舰首装甲
        out.add(new ShipComposer.Placement(ModuleCatalog.ARMOR_BAY, 11, 4));           // 5x5
        out.add(new ShipComposer.Placement(ModuleCatalog.MOUNT_BROADSIDE, 11, 9));     // 5x5 武器座
        out.add(new ShipComposer.Placement(ModuleCatalog.MOUNT_TRIPLE, 16, 5));        // 3x4 三联装座
        out.add(new ShipComposer.Placement(ModuleCatalog.ARMOR_SPALL, 16, 9));         // 3x4
        out.add(new ShipComposer.Placement(ModuleCatalog.ENGINE_MAIN, 19, 5));         // 4x5 主机
        out.add(new ShipComposer.Placement(ModuleCatalog.ENGINE_VECTOR, 19, 10));      // 3x3
        out.add(new ShipComposer.Placement(ModuleCatalog.ARMOR_BELT, 6, 9));           // 4x2
        out.add(new ShipComposer.Placement(ModuleCatalog.HULL_FRAME, 4, 10));          // 2x2
        out.add(new ShipComposer.Placement(ModuleCatalog.ARMOR_TILE, 0, 10));          // 2x2
        return out;
    }

    private static List<ShipComposer.Hardpoint> demoShipWeapons(List<ShipComposer.Placement> mods) {
        List<ShipComposer.Hardpoint> out = new ArrayList<>();
        for (ShipComposer.Placement p : mods) {
            switch (p.module().id()) {
                case "mount.triple" -> out.add(new ShipComposer.Hardpoint(
                        WeaponCatalog.CANNON_TRIPLE, p, MountSlot.at(1, 1)));
                case "mount.broadside" -> {
                    out.add(new ShipComposer.Hardpoint(WeaponCatalog.CANNON_QUAD, p, MountSlot.at(0, 1)));
                    out.add(new ShipComposer.Hardpoint(WeaponCatalog.MISSILE_SALVO, p, MountSlot.at(2, 1)));
                    out.add(new ShipComposer.Hardpoint(WeaponCatalog.CANNON_TWIN, p, MountSlot.at(4, 1)));
                }
                default -> {
                }
            }
        }
        return out;
    }

    private static int cellsOf(List<ShipComposer.Placement> mods) {
        int c = 0;
        for (ShipComposer.Placement p : mods) {
            c += p.module().cells();
        }
        return c;
    }

    private static int costOf(List<ShipComposer.Placement> mods) {
        int c = 0;
        for (ShipComposer.Placement p : mods) {
            c += p.module().cost();
        }
        return c;
    }

    // ===== 小工具 =====

    private static void panelFrame(PixelArt art, PixelFont font, int x, int y, int w, int h, String title) {
        art.frame(x, y, w, h, Palette.PANEL_EDGE);
        font.draw(art, title, x + 4, y + 5, Palette.LINE);
    }

    /** 武器 id 的尾段，"gun.triple" -> "TRIPLE"。武器名那一列只有 48px = 8 字符。 */
    private static String shortenWeapon(String id) {
        int dot = id.indexOf('.');
        String tail = dot >= 0 ? id.substring(dot + 1) : id;
        return (tail.length() > 7 ? tail.substring(0, 7) : tail).toUpperCase();
    }

    /** 模块 id 的尾段，"armor.plate" -> "PLATE"。名字列 42px = 7 字符。 */
    private static String shortName(Module m) {
        String n = m.id();
        int dot = n.indexOf('.');
        String tail = dot >= 0 ? n.substring(dot + 1) : n;
        return (tail.length() > 7 ? tail.substring(0, 7) : tail).toUpperCase();
    }

    private static Color kindColor(ModuleKind kind) {
        return switch (kind) {
            case CORE -> Palette.WARN;
            case HULL -> Palette.LINE_FAINT;
            case ARMOR -> Palette.LINE_DIM;
            case WEAPON_MOUNT -> Palette.BLUE;
            case ENGINE -> Palette.CYAN;
        };
    }

    private static int readInt(String env, int fallback) {
        String raw = System.getenv(env);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            System.out.println("[module-lab] " + env + "=" + raw + " 不是整数，用默认 " + fallback);
            return fallback;
        }
    }
}
