package com.hysx.salvosequence.live;

/**
 * 实时背景：每帧在 GL 里画一遍场景，读回一帧 320×180 的 ARGB 交给软件画布。
 *
 * <p>和「背景工坊」导出的静态图不同，这类背景会随时间动，所以战斗中不是贴一张图，
 * 而是每帧重新渲染一遍（320×180 渲染 + 2 倍最近邻贴回 640×360，代价和辉光后处理同量级）。</p>
 */
public interface LiveBackdrop {

    /** 稳定标识（存档/配置里记这个）。 */
    String id();

    String nameZh();

    String nameEn();

    /** 渲染一帧，返回 320×180 的非预乘 ARGB（y 向下）；资源未就绪时返回 null。 */
    int[] frame(double time);

    /** 场景的可调参数对象（{@code Params}），没有可调项的返回 null。 */
    Object params();

    /**
     * 鼠标拖拽 = 轨道旋转。{@code dx/dy} 是相对上一次的像素位移，口径与场景自带的调参界面一致。
     *
     * <p>实时背景不再自己慢慢转：相机只有指针拖拽、滚轮缩放和存档里的角度会改它。</p>
     */
    void orbit(double dx, double dy);

    /** 滚轮缩放，{@code notches} 正数放大。 */
    void zoom(double notches);

    /** 当前相机 {偏航°, 俯仰°, 距离}，用于存盘与面板显示。 */
    double[] camera();

    /** 直接设相机（读档用）。 */
    void setCamera(double yaw, double pitch, double dist);

    /**
     * 设视场角（度）。只有没有"距离"概念的场景（黑洞）需要覆写；
     * 其余场景保持空实现，机位由 {@link #setCamera} 决定。
     */
    default void setLens(double fov) {
    }

    /**
     * 摆太阳方位（度）。只有星球场景需要：机位绕行星转一圈，如果太阳固定在同一个方位，
     * 有些机位就正好背光、行星变成黑盘；套机位时把太阳随手转到镜头侧 45°，
     * 无论抽到哪个角度都能看到被照亮的一半。
     */
    default void setSunLight(double azimuthDeg) {
    }

    /**
     * 恒星类型（0 = 黄矮星，1 = 蓝巨星，2 = 红巨星）。只有恒星场景需要覆写。
     */
    default void setStarType(int type) {
    }

    /**
     * 主体在屏幕上的圆盘（{cx, cy, r}，单位是场景帧的像素）。闪烁星要避开这块，
     * 否则会画到行星夜面/恒星圆面上。返回 null 表示不需要遮挡。
     */
    default float[] screenDisc() {
        return null;
    }

    /**
     * 3D 闪烁星要的相机视角；返回 {@code null}（默认）表示这个场景不叠星层。
     *
     * <p>星星是无限远天球上的点，只有拿到真实相机朝向才能正确投影、才能用射线
     * 判断"这颗星是不是在行星后面"。黑洞场景自己就有 raymarch 出来的 3D 星空，
     * 而且它的相机口径和轨道相机不同，所以不叠这一层。</p>
     */
    default SkyView skyView(double time) {
        return null;
    }

    /**
     * 构图：主体在画面里的位置（0..1，左上为原点）。恒星场景靠它把太阳摆到不同角落，
     * 不然"只调大小"的机位看起来都像同一张。
     */
    default void setFraming(double x, double y) {
    }

    /**
     * 自转/脉冲速度（每秒多少圈的量级）。中子星的转速每场随机，快慢差得很明显。
     */
    default void setSpinSpeed(double speed) {
    }

    /**
     * 配色预设下标（星云的 6 套配色）。{@link #setHue(double)} 再加一点点色相偏移，
     * 同一套配色也能有不同的冷暖。
     */
    default void setPalette(int index) {
    }

    default void setHue(double degrees) {
    }

    /**
     * 环系剖面（只对带环的行星有意义）：0 土星式主环 / 1 细亮丝 / 2 双环 /
     * 3 宽尘环 / 4 断续弧环。
     */
    default void setRingStyle(int style) {
    }

    /** 开关星环（只对行星有意义）。 */
    default void setRings(boolean on) {
    }

    /** 环的半径范围与透明度（只对带环的行星有意义）。 */
    default void setRingSpan(float inner, float outer, float opacity) {
    }

    /** 环面倾角（度）：0 贴赤道，±70 接近极环。 */
    default void setRingTilt(double degrees) {
    }

    /**
     * 环系随机种子与细丝条数：同一种剖面骨架下换一条环（带的位置、宽窄、亮度都变）。
     *
     * @param seed  0 表示用该剖面最标准的那条
     * @param bands 2..4，只有细丝剖面用得到
     */
    default void setRingSeed(float seed, int bands) {
    }

    void dispose();
}
