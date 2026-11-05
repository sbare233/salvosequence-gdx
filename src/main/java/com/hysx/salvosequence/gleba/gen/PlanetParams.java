// 来源：gleba-libgdx-mvp（蓝色大肥鱼）——Gleba 星球生成核心，包名归入本项目。
package com.hysx.salvosequence.gleba.gen;

/** 可调参数：只保留"自然"部分，人文/文明相关参数一律不做。 */
public final class PlanetParams {
    public long seed = 20261030L;
    public int faceRes = 48;              // 每个立方球面的边长（tile 数），48 → 13,824 tiles
    public int plateCount = 14;           // 板块数量（super+major+minor 混合）
    public float continentalFraction = 0.30f; // 大陆地壳占比目标
    public float plateSpeed = 5.0f;       // 平均板块速度 cm/yr（影响造山强度）
    public float crustWarping = 0.35f;    // 构造扭曲：越大板块越不规则
    public float seaLevelOffset = 0f;     // 海平面偏移（米）
    public int erosionIterations = 10;    // 热侵蚀迭代（原版硬编码 10）
    public boolean erosionEnabled = true;
    public boolean climateEnabled = true;
    public int advectionSteps = 24;       // 气候平流步数（原版 128，MVP 降采样）
    public float noiseStrength = 1.0f;    // 高程噪声强度

    public PlanetParams copy() {
        PlanetParams p = new PlanetParams();
        p.seed = seed; p.faceRes = faceRes; p.plateCount = plateCount;
        p.continentalFraction = continentalFraction; p.plateSpeed = plateSpeed;
        p.crustWarping = crustWarping; p.seaLevelOffset = seaLevelOffset;
        p.erosionIterations = erosionIterations; p.erosionEnabled = erosionEnabled;
        p.climateEnabled = climateEnabled; p.advectionSteps = advectionSteps;
        p.noiseStrength = noiseStrength;
        return p;
    }
}
