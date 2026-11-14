// 来源：gleba-libgdx-mvp（蓝色大肥鱼）——Gleba 星球生成核心，包名归入本项目。
package com.hysx.salvosequence.gleba.gen;

/**
 * 可调参数：生成部分是"自然"的（海陆、气候、地形），人文那一层只剩一个
 * {@link #build} 开关——巨构建筑（巢都 / 矿业星 / 加速器 / 太空电梯）不改生成流水线，
 * 只在地表着色与轨道几何上加一层。
 */
public final class PlanetParams {
    public long seed = 20261030L;
    public int faceRes = 148;             // 立方球每面边长：6×148² ≈ 131k 格（≈ 512×256）
    /** 六边形渲染网格的细分频率（对应 MVP 的"网格密度"）：格数 ≈ 10*freq²+2。 */
    public int meshFreq = 64;
    /** 渲染网格密度：每个 tile 再细分成 N×N 个四边形（1..3，受 16 位索引限制）。 */
    public int meshDensity = 3;
    public int plateCount = 14;           // 板块数量（super+major+minor 混合）
    public float continentalFraction = 0.30f; // 大陆地壳占比目标
    public float plateSpeed = 5.0f;       // 平均板块速度 cm/yr（影响造山强度）
    public float crustWarping = 0.35f;    // 构造扭曲：越大板块越不规则
    public float seaLevelOffset = 0f;     // 海平面偏移（米）
    public int erosionIterations = 10;    // 热侵蚀迭代（原版硬编码 10）
    public boolean erosionEnabled = true;
    public boolean climateEnabled = false; // 默认走 Climate.quick（解析气候）；true 才跑风场平流
    public int advectionSteps = 24;       // 气候平流步数（原版 128，MVP 降采样）
    public float noiseStrength = 1.0f;    // 高程噪声强度
    /** 星球类型预设：大陆占比 / 温度 / 降水 / 配色都从这里来。 */
    public PlanetTheme theme = PlanetTheme.EARTH;
    /** 巨构类型（{@link PlanetBuild} 的下标）：0 原始 / 1 巢都 / 2 矿业星 / 3 加速器 / 4 太空电梯。 */
    public int build = 0;

    public PlanetParams copy() {
        PlanetParams p = new PlanetParams();
        p.seed = seed; p.faceRes = faceRes; p.plateCount = plateCount;
        p.meshFreq = meshFreq;
        p.meshDensity = meshDensity;
        p.continentalFraction = continentalFraction; p.plateSpeed = plateSpeed;
        p.crustWarping = crustWarping; p.seaLevelOffset = seaLevelOffset;
        p.erosionIterations = erosionIterations; p.erosionEnabled = erosionEnabled;
        p.climateEnabled = climateEnabled; p.advectionSteps = advectionSteps;
        p.noiseStrength = noiseStrength;
        p.theme = theme;
        p.build = build;
        return p;
    }
}
