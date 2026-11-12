package com.hysx.salvosequence.live.pulsar;

/** 中子星的全部可调参数。默认值即"看起来对"的那一版。 */
public final class Params
{
    public long seed = 20261031L;

    /** 星体半径：0.70 是"远处一颗小点"，再大就占满画面。 */
    public float starRadius = 0.70f;

    /** 自转速度，单位：圈/秒。中子星转得快：0.95 圈/秒时束流扫过的节奏才像灯塔。 */
    public float spinSpeed = 0.95f;

    /** 磁倾角：磁轴与自转轴的夹角（度）。脉冲就来自这个夹角。 */
    public float tiltDeg = 28f;

    /** 束流半张角（度）。 */
    public float beamWidthDeg = 4.2f;

    /** 束流长度（以星半径为单位）。 */
    public float beamLength = 7.0f;

    /** 束流亮度：1.00 让束流比纯白的星体略暗一档，别糊成一团白。 */
    public float beamBright = 1.00f;

    /** 每极的喷流粒子数（运行时只画前面这么多，不会重建网格）。 */
    public int beamParticles = 40000;
    public float beamSway = 0.22f;

    /** 极冠亮度（磁极被粒子轰击的亮斑）。 */
    public float capBright = 1.35f;

    /** 表面温度 0..1（0 偏冷蓝，1 偏炽白）。 */
    public float temp = 1.0f;

    /** 边缘暗化 0..1：0.30 只留一圈很淡的边，星体整体是纯白。 */
    public float limb = 0.30f;

    /** 曝光：1.12 让星体过曝成纯白（斑驳/热斑被削平），束流仍保留层次。 */
    public float exposure = 1.12f;

    /** 泛光：0.85 让白球外圈洇开一圈亮雾，隔着半透明棋盘也读得出"很亮"。 */
    public float bloomStrength = 0.85f;
    public float bloomThreshold = 0.62f;

    /** 脉冲束开关。 */
    public boolean beams = true;

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
        return p;
    }
}
