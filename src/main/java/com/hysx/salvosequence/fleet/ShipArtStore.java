package com.hysx.salvosequence.fleet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.hysx.salvosequence.core.PngLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 磁盘存档。
 *
 * <p>改造前的存档是"船体"维度的：{@code ships/<船体>.modules.txt}（功能图）、
 * {@code ships/<船体>.look.txt}（外观图）、{@code ships/<船体>.layout.txt}（件的位置）、
 * {@code ships/turret-main.txt}（炮塔皮肤）。三份都以 {@link HullKind} 命名。</p>
 *
 * <p>改造后只剩两种，都以<b>舰队</b>或<b>船</b>命名：</p>
 * <ul>
 *   <li>{@code ships/<舰队>.fleet.txt} —— 一支舰队的编排（哪艘船、几级、摆哪）</li>
 *   <li>{@code ships/look/<船>.look.txt} —— 一种船的外观图（每艘船一张，不再共船体）</li>
 * </ul>
 *
 * <p>炮塔皮肤整类删除（{@code ships/turret-*.txt} 成为历史文件，不再读写）。</p>
 */
public final class ShipArtStore {

    /** 舰队编排文件后缀。 */
    public static final String FLEET_SUFFIX = ".fleet.txt";

    /** 外观图文件后缀。 */
    public static final String LOOK_SUFFIX = ".look.txt";

    /** 编排文件头。 */
    public static final String FLEET_HEADER = "# hullbreaker fleet v1";

    private static Path directory = Paths.get("ships");

    private ShipArtStore() {
    }

    /** 存档目录。 */
    public static Path directory() {
        return directory;
    }

    /** 换存档目录（测试用）。 */
    public static void useDirectory(Path path) {
        directory = path == null ? Paths.get("ships") : path;
    }

    // ---------------------------------------------------------------- 舰队编排

    /** 编排文件路径：{@code ships/<舰队 id>.fleet.txt}。 */
    public static Path fleetPath(String fleetId) {
        return directory.resolve(fileName(fleetId) + FLEET_SUFFIX);
    }

    /** 编排文件路径。 */
    public static Path fleetPath(Fleet fleet) {
        return fleetPath(fleet.id());
    }

    /**
     * 读存档的编排；没有/坏了、或存档里的船和这一支起始舰队对不上，都返回 {@code null}
     * （调用方退回自动摆位）。
     *
     * <p>"对不上"这条很要紧：航行中途编入了新船，那一份编排就会写进同一支舰队名下；
     * 如果原样读回来，下一次选这支舰队开局就会凭空多出几艘船。</p>
     */
    public static Fleet applySavedArrangement(String id, String nameZh, String nameEn,
                                             List<FleetUnit> fallback) {
        Path path = fleetPath(id);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            Fleet fleet = parseFleet(new String(Files.readAllBytes(path), StandardCharsets.UTF_8), id, nameZh, nameEn);
            if (fleet.isEmpty() || (!fallback.isEmpty() && !sameShips(fleet, fallback))) {
                return null;
            }
            return fleet;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** 两份舰队名册是不是同一批船（只比 id，不比位置与等级）。 */
    private static boolean sameShips(Fleet fleet, List<FleetUnit> fallback) {
        Set<String> saved = new TreeSet<>();
        for (FleetUnit unit : fleet.units()) {
            saved.add(unit.id());
        }
        Set<String> expected = new TreeSet<>();
        for (FleetUnit unit : fallback) {
            expected.add(unit.id());
        }
        return saved.equals(expected);
    }

    /** 解析编排文本（{@code ship <id> <level> <x> <y>}）。 */
    public static Fleet parseFleet(String text, String id, String nameZh, String nameEn) {
        List<FleetUnit> units = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("id ")
                    || line.startsWith("name ")) {
                continue;
            }
            if (!line.startsWith("ship ")) {
                continue;
            }
            String[] parts = line.substring(5).strip().split("\\s+");
            if (parts.length != 4) {
                throw new IllegalArgumentException("编排行写法不对：" + line);
            }
            Ship ship = ShipCatalog.byId(parts[0]);
            if (ship == null) {
                throw new IllegalArgumentException("编排里有不认识的船：" + parts[0]);
            }
            int level = Math.max(1, Integer.parseInt(parts[1]));
            int x = Integer.parseInt(parts[2]);
            int y = Integer.parseInt(parts[3]);
            units.add(new FleetUnit(ship, level, x, y));
        }
        return Fleet.of(id, nameZh, nameEn, units);
    }

    /** 写编排（失败抛）。 */
    public static void saveFleet(Fleet fleet) throws IOException {
        write(fleetPath(fleet), serializeFleet(fleet));
    }

    /** 写编排（失败只返回 false，界面用）。 */
    public static boolean trySaveFleet(Fleet fleet) {
        try {
            saveFleet(fleet);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * 恢复自动铺位：删掉这份编排存档，下次装配就退回
     * {@link Fleet#autoArrange} 的自动铺位。
     */
    public static boolean resetFleet(String fleetId) {
        try {
            return Files.deleteIfExists(fleetPath(fleetId));
        } catch (IOException e) {
            return false;
        }
    }

    /** 编排文本。 */
    public static String serializeFleet(Fleet fleet) {
        StringBuilder sb = new StringBuilder();
        sb.append(FLEET_HEADER).append('\n');
        sb.append("id ").append(fleet.id()).append('\n');
        sb.append("name ").append(fleet.nameZh()).append('\n');
        for (FleetUnit unit : fleet.units()) {
            sb.append("ship ").append(unit.id()).append(' ').append(unit.level()).append(' ')
                    .append(unit.x()).append(' ').append(unit.y()).append('\n');
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- 外观图

    /** 外观图目录：{@code ships/look/}。 */
    public static Path lookDirectory() {
        return directory.resolve("look");
    }

    /** 外观图路径：{@code ships/look/<船 id>.look.txt}。 */
    public static Path lookPath(Ship ship) {
        return lookDirectory().resolve(ShipCatalog.fileName(ship) + LOOK_SUFFIX);
    }

    /** 读外观图（没有/坏了返回出厂涂装）。 */
    public static AppearanceDesign loadLook(Ship ship) {
        Path path = lookPath(ship);
        try {
            if (Files.isRegularFile(path)) {
                return AppearanceDesign.parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8), ship);
            }
        } catch (IOException | RuntimeException e) {
            // 坏档退回出厂涂装，不让游戏打不开
        }
        return AppearanceDesign.builtin(ship);
    }

    /** 写外观图（失败抛）。 */
    public static void saveLook(AppearanceDesign look) throws IOException {
        Ship ship = ShipCatalog.byId(look.shipId());
        if (ship == null) {
            throw new IllegalArgumentException("没有这艘船：" + look.shipId());
        }
        write(lookPath(ship), look.serialize());
    }

    /** 写外观图（失败只返回 false）。 */
    public static boolean trySaveLook(AppearanceDesign look) {
        try {
            saveLook(look);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** 读全部外观图（调试/核对稿件用）。 */
    public static Map<String, AppearanceDesign> allLooks() {
        Map<String, AppearanceDesign> out = new LinkedHashMap<>();
        for (Ship ship : ShipCatalog.all()) {
            out.put(ship.id(), loadLook(ship));
        }
        return out;
    }

    /** 恢复出厂涂装。 */
    public static void resetToBuiltin(Ship ship) {
        try {
            saveLook(AppearanceDesign.builtin(ship));
        } catch (IOException e) {
            // 只读环境（打包后的 jar）静默失败：出厂图在内存里永远可用
        }
    }

    // ---------------------------------------------------------------- PNG 导入

    /** 导入目录：{@code ships/import/}。 */
    public static Path importDirectory() {
        return directory.resolve("import");
    }

    /**
     * 找要导入的 PNG：先看 {@code ships/import/<船 id>.png}，再退到导入目录里第一个 png。
     */
    public static Path findImportPng(Ship ship) {
        Path named = importDirectory().resolve(ShipCatalog.fileName(ship) + ".png");
        if (Files.isRegularFile(named)) {
            return named;
        }
        Path dir = importDirectory();
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted()
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 把 PNG 导入成某艘船的外观图（面积平均缩放到该船的画布尺寸）。
     *
     * @throws IOException 文件读不了
     */
    public static AppearanceDesign importLook(Ship ship, String nameZh, int fps) throws IOException {
        Path png = findImportPng(ship);
        if (png == null) {
            throw new IOException("ships/import 下没有 png");
        }
        PngLoader.Bitmap image;
        try (var in = Files.newInputStream(png)) {
            image = PngLoader.load(in);
        } catch (IOException e) {
            throw new IOException("读不出图片：" + png.getFileName(), e);
        }
        int w = AppearanceDesign.widthOf(ship);
        int h = AppearanceDesign.heightOf(ship);
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sx0 = x * image.width() / w;
                int sx1 = Math.max(sx0 + 1, (x + 1) * image.width() / w);
                int sy0 = y * image.height() / h;
                int sy1 = Math.max(sy0 + 1, (y + 1) * image.height() / h);
                long r = 0;
                long g = 0;
                long b = 0;
                long a = 0;
                int n = 0;
                for (int sy = sy0; sy < sy1; sy++) {
                    for (int sx = sx0; sx < sx1; sx++) {
                        int argb = image.argbAt(sx, sy);
                        int alpha = (argb >>> 24) & 0xFF;
                        r += ((argb >> 16) & 0xFF) * alpha;
                        g += ((argb >> 8) & 0xFF) * alpha;
                        b += (argb & 0xFF) * alpha;
                        a += alpha;
                        n++;
                    }
                }
                if (n == 0 || a == 0) {
                    pixels[y * w + x] = AppearanceDesign.TRANSPARENT;
                } else {
                    pixels[y * w + x] = ((int) (a / n) << 24)
                            | ((int) (r / a) << 16)
                            | ((int) (g / a) << 8)
                            | (int) (b / a);
                }
            }
        }
        return AppearanceDesign.of(ship, nameZh == null ? ship.nameZh() : nameZh, List.of(pixels), fps);
    }

    // ---------------------------------------------------------------- 工具

    /** 文件名片段（{@code gun.main} → {@code gun.main}）。 */
    private static String fileName(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("存档文件必须有名字");
        }
        StringBuilder sb = new StringBuilder();
        for (char c : id.toLowerCase(Locale.ROOT).toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' ? c : '_');
        }
        return sb.toString();
    }

    private static void write(Path path, String text) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
    }
}
