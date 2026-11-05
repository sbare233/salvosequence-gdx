package com.hysx.salvosequence.live.pulsar;

/** 中子星的全部可调参数。默认值即"看起来对"的那一版。 */
public final class Params
{
    public long seed = 20261031L;

    public float starRadius = 1.0f;

    /** 自转速度，单位：圈/秒。 */
    public float spinSpeed = 0.55f;

    /** 磁倾角：磁轴与自转轴的夹角（度）。脉冲就来自这个夹角。 */
    public float tiltDeg = 28f;

    /** 束流半张角（度）。 */
    public float beamWidthDeg = 4.2f;

    /** 束流长度（以星半径为单位）。 */
    public float beamLength = 7.0f;

    public float beamBright = 0.85f;

    /** 每极的喷流粒子数（运行时只画前面这么多，不会重建网格）。 */
    public int beamParticles = 40000;
    public float beamSway = 0.22f;

    /** 极冠亮度（磁极被粒子轰击的亮斑）。 */
    public float capBright = 1.0f;

    /** 表面温度 0..1（0 偏冷蓝，1 偏炽白）。 */
    public float temp = 0.78f;

    public float limb = 0.70f;

    public float exposure = 0.55f;

    public float bloomStrength = 0.45f;
    public float bloomThreshold = 0.62f;

    /** 脉冲束开关。 */
    public boolean beams = true;

    /** 磁力线开关。 */
    public boolean fieldLines = true;

    public Params copy()
    {
        Params p = new Params();
        p.seed = seed;
        p.starRadius = starRadius;
        p.spinSpeed = spinSpeed;
        p.tiltDeg = tiltDeg;
        p.beamWidthDeg = beamWidthDeg;
        p.beamLength = beamLength;
        p.beamBright = beamBright;
        p.beamParticles = beamParticles;
        p.capBright = capBright;
        p.temp = temp;
        p.limb = limb;
        p.exposure = exposure;
        p.bloomStrength = bloomStrength;
        p.bloomThreshold = bloomThreshold;
        p.beams = beams;
        p.fieldLines = fieldLines;
        return p;
    }
}
