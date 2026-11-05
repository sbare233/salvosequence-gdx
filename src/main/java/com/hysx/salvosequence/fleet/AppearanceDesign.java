package com.hysx.salvosequence.fleet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 一艘船的<b>外观图</b>（涂装）。
 *
 * <h2>改造前后最大的不同</h2>
 * <p>改造前外观是<b>绑在船体上的</b>：一架船体（{@code HullKind}）一张 288×288 的图，
 * 一格模块 = 图上的 18×18，玩家画的是"整艘船长什么样"，换部件不改涂装。</p>
 *
 * <p>现在外观<b>绑在船上</b>（也就是改造前的"模块"）：每种船一张自己的图，
 * 尺寸 = {@link #CELL} × 船的占地格数（2×2 的机炮舰是 36×36，5×5 的重炮舰是 90×90）。
 * 于是"往棋盘上多编一艘船"就是"多一块自己画过的涂装"，
 * 而棋盘的拼贴感正是舰队系统想要的效果。</p>
 *
 * <p>一格 = {@link #CELL} 个外观像素这条映射没变，所以画布上的一笔在战斗里仍然是同一个像素点。</p>
 */
public final class AppearanceDesign {

    /** 一格船占多少外观像素。 */
    public static final int CELL = 18;

    /** 最多几帧。 */
    public static final int MAX_FRAMES = 8;

    /** 帧率下限。 */
    public static final int MIN_FPS = 1;

    /** 帧率上限。 */
    public static final int MAX_FPS = 30;

    /** 存档文件头。 */
    public static final String HEADER = "# hullbreaker look v3";

    /** 透明。 */
    public static final int TRANSPARENT = 0;

    /** 透明像素在文本里的写法。 */
    public static final String TRANSPARENT_TOKEN = "......";

    private final String shipId;
    private final String nameZh;
    private final int w;
    private final int h;
    private final List<int[]> frames;
    private final int fps;

    private AppearanceDesign(String shipId, String nameZh, int w, int h, List<int[]> frames, int fps) {
        if (shipId == null || shipId.isBlank()) {
            throw new IllegalArgumentException("外观图必须绑在某种船上");
        }
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("外观图尺寸必须为正: " + w + "x" + h);
        }
        if (frames == null || frames.isEmpty() || frames.size() > MAX_FRAMES) {
            throw new IllegalArgumentException("外观图帧数必须在 1.." + MAX_FRAMES + " 之间");
        }
        List<int[]> copy = new ArrayList<>(frames.size());
        for (int[] frame : frames) {
            if (frame == null || frame.length != w * h) {
                throw new IllegalArgumentException("外观图每帧必须是 " + w + "x" + h + " 像素");
            }
            copy.add(frame.clone());
        }
        if (fps < MIN_FPS || fps > MAX_FPS) {
            throw new IllegalArgumentException("帧率必须在 " + MIN_FPS + ".." + MAX_FPS + " 之间");
        }
        this.shipId = shipId;
        this.nameZh = nameZh == null ? "" : nameZh;
        this.w = w;
        this.h = h;
        this.frames = List.copyOf(copy);
        this.fps = fps;
    }

    // ---------------------------------------------------------------- 尺寸

    /** 这型船外观图的宽度（像素）。 */
    public static int widthOf(Ship ship) {
        return ship.w() * CELL;
    }

    /** 这型船外观图的高度（像素）。 */
    public static int heightOf(Ship ship) {
        return ship.h() * CELL;
    }

    // ---------------------------------------------------------------- 构造

    /** 从零建一张。 */
    public static AppearanceDesign of(Ship ship, String nameZh, List<int[]> frames, int fps) {
        return new AppearanceDesign(ship.id(), nameZh, widthOf(ship), heightOf(ship), frames, fps);
    }

    /** 从零建一张（直接给尺寸，导入 PNG 用）。 */
    public static AppearanceDesign of(String shipId, String nameZh, int w, int h, List<int[]> frames, int fps) {
        return new AppearanceDesign(shipId, nameZh, w, h, frames, fps);
    }

    /** 一帧空白。 */
    public static int[] blank(Ship ship) {
        return new int[widthOf(ship) * heightOf(ship)];
    }

    /**
     * 程序生成的出厂涂装：按船的职能配色 + 板缝 + 上缘受光 + 闪烁灯/引擎焰。
     *
     * <p>4 帧循环：基色 / 灯亮 / 焰暗 / 焰亮。</p>
     */
    public static AppearanceDesign builtin(Ship ship) {
        int w = widthOf(ship);
        int h = heightOf(ship);
        int[] base = new int[w * h];
        int plate = plateColor(ship.role());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = plate;
                // 板缝：每格之间压一道暗线
                if (x % CELL == 0 || y % CELL == 0) {
                    argb = shade(argb, 0.80);
                }
                // 横缝中段留一条更暗的线，读起来像分段的船壳
                if (y % CELL == CELL / 2) {
                    argb = shade(argb, 0.92);
                }
                // 上缘受光
                if (y < 2 || y % CELL == 1) {
                    argb = shade(argb, 1.16);
                }
                // 下缘压暗，给一点厚度
                if (y >= h - 2 || y % CELL == CELL - 2) {
                    argb = shade(argb, 0.86);
                }
                base[y * w + x] = argb;
            }
        }
        // 外轮廓：最外一圈压暗、四个角切掉，一眼能看出是艘船而不是一块格子
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean edge = x == 0 || y == 0 || x == w - 1 || y == h - 1;
                if (edge) {
                    base[y * w + x] = shade(base[y * w + x], 0.58);
                }
                if ((x == 0 || x == w - 1) && (y == 0 || y == h - 1)) {
                    base[y * w + x] = TRANSPARENT;
                }
            }
        }
        // 中心一盏灯（指挥/战术船才有）
        boolean hasLight = ship.role() == ShipRole.COMMAND || ship.role() == ShipRole.TACTICS;
        if (hasLight) {
            int cx = w / 2;
            int cy = h / 2;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int x = cx + dx;
                    int y = cy + dy;
                    if (x >= 0 && y >= 0 && x < w && y < h) {
                        base[y * w + x] = (dx == 0 && dy == 0) ? 0xFF7FE3F2 : shade(0xFF7FE3F2, 0.72);
                    }
                }
            }
        }
        // 引擎喷口：动力船左缘一条
        boolean hasThruster = ship.role() == ShipRole.DRIVE;
        List<int[]> flames = new ArrayList<>();
        if (hasThruster) {
            for (int y = 2; y < h - 2; y++) {
                flames.add(new int[]{0, y});
                flames.add(new int[]{1, y});
            }
        }
        int[] f0 = base.clone();
        int[] f1 = base.clone();
        if (hasLight) {
            int cx = w / 2;
            int cy = h / 2;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int x = cx + dx;
                    int y = cy + dy;
                    if (x >= 0 && y >= 0 && x < w && y < h) {
                        f1[y * w + x] = 0xFFE8FFFF;
                    }
                }
            }
        }
        int[] f2 = base.clone();
        int[] f3 = base.clone();
        if (hasThruster) {
            for (int[] p : flames) {
                f2[p[1] * w + p[0]] = 0xFF8A4A12;
                f3[p[1] * w + p[0]] = 0xFFFFD27A;
            }
        }
        return new AppearanceDesign(ship.id(), ship.nameZh(), w, h, List.of(f0, f1, f2, f3), 4);
    }

    /** 职能 → 船壳基色。 */
    private static int plateColor(ShipRole role) {
        return switch (role) {
            case COMMAND -> 0xFF6A788C;
            case MAIN_GUN -> 0xFF5C6C86;
            case SECONDARY_GUN -> 0xFF566A80;
            case ARMOR -> 0xFF5F7068;
            case DRIVE -> 0xFF4A5869;
            case TACTICS -> 0xFF4E5A6E;
        };
    }

    // ---------------------------------------------------------------- 读

    /** 绑定的船型 id。 */
    public String shipId() {
        return shipId;
    }

    /** 显示名。 */
    public String nameZh() {
        return nameZh;
    }

    /** 宽（像素）。 */
    public int width() {
        return w;
    }

    /** 高（像素）。 */
    public int height() {
        return h;
    }

    /** 几帧。 */
    public int frameCount() {
        return frames.size();
    }

    /** 帧率。 */
    public int fps() {
        return fps;
    }

    /** 取一帧（拷贝）。 */
    public int[] frame(int index) {
        return frames.get(Math.floorMod(index, frames.size())).clone();
    }

    /** 取一帧（不拷贝，绘制用）。 */
    public int[] frameRaw(int index) {
        return frames.get(Math.floorMod(index, frames.size()));
    }

    /** 取一个像素。 */
    public int pixel(int frameIndex, int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return TRANSPARENT;
        }
        return frames.get(Math.floorMod(frameIndex, frames.size()))[y * w + x];
    }

    /** 整张图是不是全透明。 */
    public boolean isBlank() {
        for (int[] frame : frames) {
            for (int argb : frame) {
                if ((argb >>> 24) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- 改

    /** 改一个像素。 */
    public AppearanceDesign withPixel(int frameIndex, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return this;
        }
        List<int[]> next = new ArrayList<>(frames.size());
        for (int i = 0; i < frames.size(); i++) {
            int[] frame = frames.get(i).clone();
            if (i == Math.floorMod(frameIndex, frames.size())) {
                frame[y * w + x] = argb;
            }
            next.add(frame);
        }
        return new AppearanceDesign(shipId, nameZh, w, h, next, fps);
    }

    /** 改帧数（多的帧复制最后一帧，少的截断）。 */
    public AppearanceDesign withFrameCount(int count) {
        int n = Math.max(1, Math.min(MAX_FRAMES, count));
        List<int[]> next = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            next.add(frames.get(Math.min(i, frames.size() - 1)).clone());
        }
        return new AppearanceDesign(shipId, nameZh, w, h, next, fps);
    }

    /** 改帧率。 */
    public AppearanceDesign withFps(int newFps) {
        int f = Math.max(MIN_FPS, Math.min(MAX_FPS, newFps));
        return new AppearanceDesign(shipId, nameZh, w, h, frames, f);
    }

    /** 改名。 */
    public AppearanceDesign renamed(String newNameZh) {
        return new AppearanceDesign(shipId, newNameZh, w, h, frames, fps);
    }

    /** 清空成一张透明图（保留尺寸与帧数）。 */
    public AppearanceDesign cleared() {
        List<int[]> next = new ArrayList<>(frames.size());
        for (int i = 0; i < frames.size(); i++) {
            next.add(new int[w * h]);
        }
        return new AppearanceDesign(shipId, nameZh, w, h, next, fps);
    }

    // ---------------------------------------------------------------- 文本

    /**
     * 存档文本。
     *
     * <pre>
     * # hullbreaker look v3
     * ship gun.main
     * name 主炮舰
     * size 72x54
     * fps 4
     * frame 1
     * 1a1a2c......（每行 w 个 6 位十六进制，或 "......" 表示透明）
     * </pre>
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        sb.append("ship ").append(shipId).append('\n');
        sb.append("name ").append(nameZh).append('\n');
        sb.append("size ").append(w).append('x').append(h).append('\n');
        sb.append("fps ").append(fps).append('\n');
        for (int f = 0; f < frames.size(); f++) {
            sb.append("frame ").append(f + 1).append('\n');
            int[] pixels = frames.get(f);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = pixels[y * w + x];
                    if ((argb >>> 24) == 0) {
                        sb.append(TRANSPARENT_TOKEN);
                    } else {
                        sb.append(String.format(Locale.ROOT, "%02x%02x%02x",
                                (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF));
                    }
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * 读存档。
     *
     * @param ship 这张图要贴在哪种船上（尺寸必须对得上）
     * @throws IllegalArgumentException 文件坏了或尺寸对不上
     */
    public static AppearanceDesign parse(String text, Ship ship) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("外观图是空的");
        }
        String[] lines = text.split("\\R");
        String shipId = ship.id();
        String nameZh = ship.nameZh();
        int expectW = widthOf(ship);
        int expectH = heightOf(ship);
        int w = expectW;
        int h = expectH;
        int fps = 4;
        List<int[]> frames = new ArrayList<>();
        int[] current = null;
        int row = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("ship ")) {
                shipId = line.substring(5).strip();
                continue;
            }
            if (line.startsWith("name ")) {
                nameZh = line.substring(5).strip();
                continue;
            }
            if (line.startsWith("size ")) {
                String[] parts = line.substring(5).strip().split("x");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("第 " + (i + 1) + " 行 size 写法不对：" + line);
                }
                w = parsePositive(parts[0], i + 1);
                h = parsePositive(parts[1], i + 1);
                continue;
            }
            if (line.startsWith("fps ")) {
                fps = parsePositive(line.substring(4).strip(), i + 1);
                continue;
            }
            if (line.startsWith("frame ")) {
                if (current != null) {
                    frames.add(current);
                }
                current = new int[w * h];
                row = 0;
                continue;
            }
            if (current == null) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行不是合法的外观图内容：" + line);
            }
            if (line.length() != w * 6) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行该有 " + w
                        + " 个像素（" + (w * 6) + " 个字符），实际 " + line.length());
            }
            if (row >= h) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行超出了 " + h + " 行的图高");
            }
            for (int x = 0; x < w; x++) {
                String token = line.substring(x * 6, x * 6 + 6);
                current[row * w + x] = TRANSPARENT_TOKEN.equals(token)
                        ? TRANSPARENT
                        : 0xFF000000 | Integer.parseInt(token, 16);
            }
            row++;
        }
        if (current != null) {
            frames.add(current);
        }
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("外观图里一帧都没有");
        }
        if (w != expectW || h != expectH) {
            throw new IllegalArgumentException("外观图是 " + w + "x" + h + "，而「" + ship.nameZh()
                    + "」占 " + ship.w() + "x" + ship.h() + " 格，应该是 " + expectW + "x" + expectH);
        }
        return new AppearanceDesign(shipId, nameZh, w, h, frames, fps);
    }

    private static int parsePositive(String raw, int lineNo) {
        try {
            int v = Integer.parseInt(raw.strip());
            if (v <= 0) {
                throw new IllegalArgumentException("第 " + lineNo + " 行的数必须为正：" + raw);
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("第 " + lineNo + " 行不是整数：" + raw);
        }
    }

    /** 明度缩放。 */
    public static int shade(int argb, double factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = clampChannel((int) Math.round(((argb >> 16) & 0xFF) * factor));
        int g = clampChannel((int) Math.round(((argb >> 8) & 0xFF) * factor));
        int b = clampChannel((int) Math.round((argb & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clampChannel(int v) {
        return Math.max(0, Math.min(255, v));
    }

    @Override
    public String toString() {
        return "AppearanceDesign[" + shipId + " " + w + "x" + h + " " + frames.size() + "帧 "
                + fps + "fps]";
    }
}
