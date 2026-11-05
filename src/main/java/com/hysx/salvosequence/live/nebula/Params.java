package com.hysx.salvosequence.live.nebula;

/**
 * Nebula generator parameters, mirroring the controls the original
 * Nebula Generator add-on exposes on its Blender panel.
 *
 * <p>Original node values are noted next to each field, e.g. {@code // Noise Texture.009 Scale = 0.11}.
 */
public final class Params {
    /** Blender: geometry-nodes "Seed" socket on Nebula Distortion + star modifiers. */
    public long seed = 20260122L;

    // ---- Noise (add-on "Noise" panel: Overall Noise / Scale Variation / Distortion Variation) ----
    /** Overall FBM scale (Blender drives this from a second noise, we expose it directly). */
    public float noiseScale = 1.0f;
    /** Blender Noise Texture Detail = 16 (we map 0..1 -> 3..7 octaves). */
    public float noiseDetail = 0.86f;
    /** Blender Noise Texture Roughness = 0.5743. */
    public float noiseRoughness = 0.574f;
    /** Blender "Distortion" input = Noise.008 * 0.5; distortion amount slider = 0.35. */
    public float distortion = 0.42f;

    // ---- Density (add-on "Density" panel) ----
    /** Math.002 Density Multiplier = 50 (main cloud). */
    public float dustDensity = 50f;
    /** Math.004 Density Multiplier = 0.015 (cloud gas). */
    public float gasDensity = 0.012f;
    /** Main Cloud Density Ramp threshold, CONSTANT interpolation at 0.3944. */
    public float dustThreshold = 0.500f;
    /** Cloud Gas Density Ramp, LINEAR 0.3513 -> 1.0, 0.4932 -> 0.0. */
    public float gasInner = 0.440f;
    public float gasOuter = 0.660f;

    // ---- Shape ----
    /** Map Range "From Max" = Transparency Sphere Size = 1.39 (object units). */
    public float sphereSize = 1.55f;
    /** Nebula Distortion modifier "Distortion Amount". */
    public float boxDistortion = 0.38f;

    // ---- Colours (add-on "Colors" panel) ----
    /** Global Hue/Saturation/Value group (ColorControlsNodeGroup). */
    public float hue = 0f;
    public float saturation = 1.0f;
    public float value = 1.0f;
    /** Main Light colour (1.0, 0.268, 0.029), energy 40. */
    public float lightR = 1.0f, lightG = 0.268f, lightB = 0.029f;
    public float lightEnergy = 1.2f;
    /** Core "Sun" point light at the origin, colour (1.0, 0.499, 0.131). */
    public float coreEnergy = 0.30f;
    public float starEnergy = 1.0f;
    /** 0 = single scattering only, 1 = full three-octave multiple scattering. */
    public float multiScatter = 1.0f;
    /** Voxels per axis of the baked noise volume (96 fast .. 192 sharp). */
    public int bakeRes = 144;
    /** >1 exaggerates the shadow contrast (physically a "dustier" cloud). */
    public float shadowPower = 2.0f;
    /** 0 = 团块 (clumps), 1 = 棉絮 (wisps), 2 = 关闭. */
    public int darkMode = 0;
    public float darkAmount = 0.45f;
    public float darkSep = 0.46f;
    public float darkAlpha = 0.10f;
    public final float[] starColor = {1.0f, 0.84f, 0.70f};
    /** Cloud Gas colour ramp (ColorRamp.010): 0.5934 dark blue, 0.692 orange, 0.748 black. */
    public float gasHueShift = 0.0f;
    /** Emission gain applied on top of the gas albedo. */
    public float emission = 0.60f;

    // ---- Stars (add-on "Stars" panel) ----
    public int largeStars = 90;
    public float largeStarMin = 0.010f;
    public float largeStarMax = 0.048f;
    public int smallStars = 2600;
    public float smallStarMin = 0.0012f;
    public float smallStarMax = 0.0060f;
    /** Small Stars "Star Coverage" (noise compare threshold). */
    public float starCoverage = 0.42f;
    /** Small Stars "Star Density" 0..1 -> fraction of volume points kept. */
    public float starDensity = 0.45f;

    // ---- Quality / camera ----
    public int steps = 64;
    public float renderScale = 1.0f;
    public float exposure = 1.55f;
    public float bloomThreshold = 0.55f;
    public float bloomStrength = 0.85f;
    public float fov = 78f;
    public boolean starsEnabled = true;
    public boolean bloomEnabled = true;

    public Params copy() {
        Params p = new Params();
        p.seed = seed;
        p.noiseScale = noiseScale;
        p.noiseDetail = noiseDetail;
        p.noiseRoughness = noiseRoughness;
        p.distortion = distortion;
        p.dustDensity = dustDensity;
        p.gasDensity = gasDensity;
        p.dustThreshold = dustThreshold;
        p.gasInner = gasInner;
        p.gasOuter = gasOuter;
        p.sphereSize = sphereSize;
        p.boxDistortion = boxDistortion;
        p.hue = hue;
        p.saturation = saturation;
        p.value = value;
        p.lightR = lightR;
        p.lightG = lightG;
        p.lightB = lightB;
        p.lightEnergy = lightEnergy;
        p.coreEnergy = coreEnergy;
        p.starEnergy = starEnergy;
        p.darkMode = darkMode;
        p.darkAmount = darkAmount;
        p.gasHueShift = gasHueShift;
        p.emission = emission;
        p.largeStars = largeStars;
        p.largeStarMin = largeStarMin;
        p.largeStarMax = largeStarMax;
        p.smallStars = smallStars;
        p.smallStarMin = smallStarMin;
        p.smallStarMax = smallStarMax;
        p.starCoverage = starCoverage;
        p.starDensity = starDensity;
        p.steps = steps;
        p.renderScale = renderScale;
        p.exposure = exposure;
        p.bloomThreshold = bloomThreshold;
        p.bloomStrength = bloomStrength;
        p.fov = fov;
        p.starsEnabled = starsEnabled;
        p.bloomEnabled = bloomEnabled;
        return p;
    }

    /** Colour schemes: thin gas, medium gas, dense gas / lit dust. */
    public static final String[] PALETTE_NAMES = {
            "青蓝·锈橙", "品红·青", "翡翠·琥珀", "紫罗兰·玫红", "冰蓝·金", "深红·橙"
    };
    public static final float[][] PALETTE_THIN = {
            {0.02f, 0.52f, 0.90f}, {0.55f, 0.05f, 0.75f}, {0.02f, 0.62f, 0.34f},
            {0.30f, 0.08f, 0.85f}, {0.15f, 0.45f, 0.85f}, {0.62f, 0.05f, 0.18f}
    };
    public static final float[][] PALETTE_MID = {
            {0.09f, 0.72f, 0.78f}, {0.88f, 0.16f, 0.58f}, {0.34f, 0.85f, 0.26f},
            {0.70f, 0.25f, 0.95f}, {0.55f, 0.80f, 0.95f}, {0.95f, 0.28f, 0.16f}
    };
    public static final float[][] PALETTE_WARM = {
            {1.00f, 0.33f, 0.07f}, {0.05f, 0.85f, 0.95f}, {1.00f, 0.62f, 0.08f},
            {1.00f, 0.35f, 0.55f}, {1.00f, 0.78f, 0.20f}, {1.00f, 0.55f, 0.10f}
    };

    public int palette = 0;
    public float hueDeg = 0f;
}
