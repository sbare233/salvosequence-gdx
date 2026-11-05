package com.hysx.salvosequence.shipyard;

import com.hysx.salvosequence.core.Palette;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.core.Color;

/**
 * 占位贴图：<b>随便糊弄的</b>程序化图形，等真素材换掉。
 *
 * <h2>这份代码的定位</h2>
 * <p>用户原话：「主要是数据类，贴图随便搞点糊弄一下，后面我自己画」。
 * 所以这里的图形刻意做得简单、可预测、一眼能看出是哪个模块：</p>
 * <ul>
 *   <li>每种 {@link ModuleKind} 一个主色 + 一个暗色（装甲亮、骨架暗、引擎青、核心金）；</li>
 *   <li>外框 + 四角倒角 + 内部对角细线，看得出边界在哪、能不能拼上；</li>
 *   <li>炮塔单独画：圆底座 + 若干根朝外的炮管，<b>管子会伸出格子</b>——
 *       这正是「炮塔层高于模块」要展示的效果。</li>
 * </ul>
 *
 * <h2>要换成真素材时</h2>
 * <p>把 {@link #module} 与 {@link #turret} 换成读 {@code Sprite} 即可，调用点不用动：
 * 两者都只依赖「画在 (x,y) 起、尺寸 w x h 格」这个约定。</p>
 */
public final class PlaceholderArt {

    private PlaceholderArt() {
    }

    /** 一格的像素数（与 {@link ShipComposer#DEFAULT_CELL_PX} 保持一致）。 */
    public static final int CELL_PX = ShipComposer.DEFAULT_CELL_PX;

    /** 模块主色。 */
    public static Color colorOf(ModuleKind kind) {
        return switch (kind) {
            case CORE -> Palette.WARN;
            case HULL -> Palette.LINE_FAINT;
            case ARMOR -> Palette.LINE_DIM;
            case WEAPON_MOUNT -> Palette.BLUE;
            case ENGINE -> Palette.CYAN_DIM;
        };
    }

    /** 模块暗色（内部线、倒角阴影）。 */
    public static Color shadeOf(ModuleKind kind) {
        return switch (kind) {
            case CORE -> Palette.FOE_DEEP;
            case HULL -> Palette.PANEL_EDGE;
            case ARMOR -> Palette.SHIP_DEEP;
            case WEAPON_MOUNT -> Palette.BLUE_DIM;
            case ENGINE -> Palette.PANEL_EDGE;
        };
    }

    /**
     * 按格坐标画一个模块占位图。
     *
     * @param x    左上角（设计像素）
     * @param y    左上角（设计像素）
     * @param w    宽（像素）
     * @param h    高（像素）
     * @param kind 决定配色
     * @param core 是不是核心（核心多画一圈内框，一眼认得出全船唯一件）
     */
    public static void module(PixelArt art, int x, int y, int w, int h, ModuleKind kind, boolean core) {
        if (art == null || w <= 0 || h <= 0) {
            return;
        }
        Color main = colorOf(kind);
        Color shade = shadeOf(kind);

        // 底：两档抖动，让「这是占位图」一眼可见，同时不喧宾夺主
        art.ditherRect(x, y, w, h, shade, 1);
        art.ditherRect(x, y, w, h, main, 3);

        // 外框 + 倒角
        art.frame(x, y, w, h, main);
        int cut = Math.min(2, Math.min(w, h) / 3);
        if (cut > 0) {
            art.fillRect(x, y, cut, 1, Palette.PANEL);
            art.fillRect(x, y, 1, cut, Palette.PANEL);
            art.fillRect(x + w - cut, y + h - 1, cut, 1, Palette.PANEL);
            art.fillRect(x + w - 1, y + h - cut, 1, cut, Palette.PANEL);
        }

        // 内部结构线：斜对角，看得出朝向
        art.line(x + 1, y + h - 2, x + w - 2, y + 1, shade);

        // 武器座：画一排挂点小方块，数量和 socketCount 无关（那要模块对象），
        // 所以这里只画「有挂点」的记号，具体挂点由 ShipComposer 标。
        if (kind == ModuleKind.WEAPON_MOUNT) {
            art.frame(x + 1, y + 1, Math.max(1, w - 2), Math.max(1, h - 2), shade);
        }

        // 引擎：尾侧（右边缘）画两道排气口
        if (kind == ModuleKind.ENGINE) {
            art.fillRect(x + w - 3, y + h / 3, 2, 1, Palette.CYAN);
            art.fillRect(x + w - 3, y + h - h / 3 - 1, 2, 1, Palette.CYAN);
        }

        // 核心：内框 + 中心点
        if (core) {
            art.frame(x + 2, y + 2, Math.max(1, w - 4), Math.max(1, h - 4), main);
            art.fillRect(x + w / 2 - 1, y + h / 2 - 1, 2, 2, Palette.WARN);
        }
    }

    /**
     * 画一个炮塔占位图。
     *
     * <p>炮塔朝<b>上</b>（舷外）画：圆底座落在挂点上，炮管向上伸出 {@code barrels} 根。
     * 炮管长度固定 6 像素，所以小炮塔的管子会明显伸到模块之外——这正是要看的层序效果。</p>
     *
     * @param cx     挂点中心（设计像素）
     * @param cy     挂点中心（设计像素）
     * @param barrels 炮管数
     * @param tone   主色（玩家青、敌人红）
     */
    public static void turret(PixelArt art, int cx, int cy, int barrels, Color tone) {
        if (art == null || barrels <= 0) {
            return;
        }
        Color dark = tone.darker();
        int baseR = 3;

        // 炮管：朝上，等距排开
        int gap = 2;
        int span = (barrels - 1) * gap;
        for (int i = 0; i < barrels; i++) {
            int bx = cx - span / 2 + i * gap;
            art.fillRect(bx, cy - baseR - 6, 1, 7, dark);
            art.pixel(bx, cy - baseR - 6, Palette.LINE);
        }

        // 底座：椭圆 + 芯
        art.ellipse(cx, cy, baseR, baseR, dark);
        art.ellipse(cx, cy, baseR - 1, baseR - 1, tone);
        art.pixel(cx, cy, Palette.LINE);
    }
}
