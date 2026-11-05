package com.hysx.salvosequence.live.star;

/** 近景恒星渲染参数（全部可在 UI 里调，也可用命令行覆盖）。 */
public class Params {
    public long seed = 20261030L;

    /** 光球颗粒的细胞密度（越大颗粒越细）。 */
    public float granulation = 140f;
    /** 黑子数量/覆盖度（0 = 没有黑子）。 */
    public float spots = 0.10f;
    /** 日珥数量系数（0 = 关）。 */
    public float prominences = 0.22f;
    /** 耀斑发生频率（0 = 关，1 = 每几秒一次）。 */
    public float flares = 0.0f;
    /** 边缘变暗强度（0 = 均匀亮盘，1 = 强变暗）。 */
    public float limb = 0.52f;
    /** 曝光。 */
    public float exposure = 1.15f;
    /** bloom 强度。 */
    public float bloom = 1.76f;
    /** 衍射星芒强度。 */
    public float spikes = 2.0f;
    /** 色散（色差）强度，像素。 */
    public float dispersion = 1.2f;
    /** 自动旋转。 */
    public boolean autoRotate = true;

    public Params copy() {
        Params p = new Params();
        p.seed = seed;
        p.granulation = granulation;
        p.spots = spots;
        p.prominences = prominences;
        p.flares = flares;
        p.limb = limb;
        p.exposure = exposure;
        p.bloom = bloom;
        p.spikes = spikes;
        p.dispersion = dispersion;
        p.autoRotate = autoRotate;
        return p;
    }
}
