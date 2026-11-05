package com.hysx.salvosequence.shipyard;

import java.util.ArrayList;
import java.util.List;

/**
 * 从模块推出它的武器挂点。
 *
 * <h2>为什么不是手工给每个模块写一堆挂点坐标</h2>
 * <p>手工写的话，加一种新尺寸的武器座就要重新配一遍坐标，而且很容易配到格子外面去
 * （{@link MountSlot#fitsInside} 就是为此存在的兜底）。<b>规则化</b>之后，
 * 挂点数直接由模块的 {@code socketCount} 与尺寸算出来，加尺寸档不用改代码。</p>
 *
 * <h2>规则</h2>
 * <ol>
 *   <li>只有 {@link ModuleKind#WEAPON_MOUNT} 长挂点；其余模块 {@code socketCount} 必须是 0
 *       （数据错了就抛，不要静默忽略）。</li>
 *   <li>挂点沿模块<b>顶边内侧</b>排开，横向等距。这是俯视舰船的常规做法：
 *       炮塔贴图向上（舷外）画，压在相邻的装甲板上，视觉上就是「架在甲板上」。</li>
 *   <li>挂点的 x 会 clamp 到 {@code [0, w-1]}，所以 2x2 的小座只能挂 1 门，5x5 的大座能挂 3 门。</li>
 * </ol>
 *
 * <p>注意：<b>挂点是格坐标，炮塔贴图可以比一格大得多</b>，两者不冲突。
 * 一门 3x4 的三联装炮塔挂在 (1,1) 上，会向右下各铺 3 格、4 格。</p>
 */
public final class MountRules {

    private MountRules() {
    }

    /** 挂点所在的甲板行（从模块顶边往里数第几格）。 */
    public static final int SLOT_ROW = 1;

    /**
     * 算出模块的挂点表。
     *
     * @throws IllegalArgumentException 非武器座却声明了挂点数
     */
    public static List<MountSlot> slotsFor(Module module) {
        if (module == null) {
            throw new IllegalArgumentException("模块不能为空");
        }
        int count = module.socketCount();
        if (count == 0) {
            return List.of();
        }
        if (!module.isMount()) {
            throw new IllegalArgumentException("模块 " + module.id()
                    + " 是 " + module.kind() + "，不该有挂点（socketCount=" + count + "）");
        }
        ModuleShape shape = module.shape();
        List<MountSlot> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // count=1 → 居中；count=2 → 左右各一个；count=3 → 左中右。
            double t = count == 1 ? 0.5 : (double) i / (count - 1);
            int x = (int) Math.round(t * (shape.w() - 1));
            int y = Math.min(SLOT_ROW, shape.h() - 1);
            MountSlot slot = MountSlot.at(clamp(x, 0, shape.w() - 1), y);
            if (!slot.fitsInside(shape)) {
                throw new IllegalStateException("挂点 " + slot.summary()
                        + " 落在模块 " + module.id() + " " + shape.label() + " 外面");
            }
            out.add(slot);
        }
        return List.copyOf(out);
    }

    /** 模块最多能挂几门炮（= 挂点数）。 */
    public static int socketCount(Module module) {
        return slotsFor(module).size();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
