// Volumetric nebula: single scattering raymarch through the "Nebula Box" volume.
//
// The fractal cloud is *precomputed* per seed into a 2D atlas of slices (NebulaNoise.java)
// instead of being evaluated inside the march loop: the shader does two bilinear taps
// (two adjacent slices, lerped) per lookup, which is what makes 96+ steps affordable.
//
// Mirrors the Blender material "Nebula Material":
//   Noise Texture (detail, distortion) -> Main Cloud Ramp (constant @ .3944) -> dust x50
//                                     -> Cloud Gas Ramp (linear .3513-.4932)  -> gas x0.015
//   spherical transparency falloff (Map Range From Max = Transparency Sphere Size)
// The dust is a black absorber lit by the warm "Main Light"; the gas is self luminous.

varying vec2 v_ndc;

uniform vec2  u_res;
uniform float u_time;

uniform vec3  u_camPos;
uniform vec3  u_camRight;
uniform vec3  u_camUp;
uniform vec3  u_camFwd;
uniform float u_tanHalfFov;
uniform float u_aspect;

uniform float u_seed;
uniform float u_noiseScale;
uniform int   u_darkMode;    // 0 = clumps, 1 = wisps, 2 = off
uniform float u_darkAmount;
uniform float u_darkAlpha;  // 0 = fully black, 1 = fully see-through
uniform float u_darkSep;    // 0..1, one threshold: above = glowing gas, below = black matter
uniform float u_octaves;      // 0..1, amount of procedural high frequency detail
uniform float u_roughness;
uniform float u_distortion;

uniform sampler2D u_noise;
uniform vec3  u_noiseCfg;     // (slices, tile size, tiles per row)

uniform float u_dustDensity;
uniform float u_gasDensity;
uniform float u_dustThreshold;
uniform float u_gasInner;
uniform float u_gasOuter;
uniform float u_sphereSize;

uniform vec3  u_lightDir;     // direction TO the light
uniform vec3  u_lightColor;
uniform float u_lightEnergy;
uniform float u_coreEnergy;
uniform vec3  u_starColor;    // point light sitting at the origin
uniform float u_starEnergy;
uniform float u_multi;
uniform float u_dbg;
uniform float u_shadow;    // >1 deepens the shadow contrast      // >0.5: visualise the star shadow term       // 0 = single scattering, 1 = three-octave multiple scattering
uniform float u_emission;

uniform vec3  u_colThin;      // thin hydrogen
uniform vec3  u_colMid;       // medium gas
uniform vec3  u_colWarm;      // dense gas / lit dust
uniform vec3  u_warmDir;      // which side of the cloud runs warm

uniform int   u_steps;
uniform float u_exposure;
uniform vec3  u_hsv;          // hue (0..1 shift), saturation, value

const float PI = 3.14159265;

// ---------------------------------------------------------------- baked volume
vec3 sliceSample(float s, vec2 xy) {
    float grid = u_noiseCfg.z;
    float tile = u_noiseCfg.y;
    float atlas = grid * tile;
    float gx = mod(s, grid);
    float gy = floor(s / grid);
    vec2 origin = vec2(gx, gy) * tile + 1.0;
    return texture2D(u_noise, (origin + xy + 0.5) / atlas).rgb;
}

vec3 noiseAt(vec3 p) {
    float n = u_noiseCfg.x;
    vec3 c = clamp(p, 0.0, 1.0) * n;
    float z0 = clamp(floor(c.z), 0.0, n - 1.0);
    float z1 = mod(z0 + 1.0, n);
    vec3 a = sliceSample(z0, c.xy);
    vec3 b = sliceSample(z1, c.xy);
    return mix(a, b, c.z - z0);
}

// one cheap procedural octave on top of the baked field, for crisp filament edges
float hash13(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float vnoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash13(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));
    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);
    float nxy0 = mix(nx00, nx10, f.y);
    float nxy1 = mix(nx01, nx11, f.y);
    return mix(nxy0, nxy1, f.z);
}

// ---------------------------------------------------------------- colour
vec3 hsvShift(vec3 c, vec3 hsv) {
    float a = hsv.x * 2.0 * PI;
    float ca = cos(a), sa = sin(a);
    vec3 k = vec3(0.57735);
    vec3 rot = c * ca + cross(k, c) * sa + k * dot(k, c) * (1.0 - ca);
    float g = dot(rot, vec3(0.299, 0.587, 0.114));
    rot = mix(vec3(g), rot, hsv.y);
    return rot * hsv.z;
}

vec3 gasColour(float n, float warmZone) {
    float t = clamp((n - u_gasInner) / max(u_gasOuter - u_gasInner, 1e-4), 0.0, 1.0);
    // two full ramps: the cool lobe runs thin->mid, the warm lobe runs mid->dust colour,
    // and the low frequency field decides which lobe a voxel belongs to
    vec3 cool = mix(u_colThin, u_colMid, smoothstep(0.02, 0.85, t));
    vec3 warm = mix(u_colMid, u_colWarm, smoothstep(0.10, 0.90, 1.0 - t));
    return mix(cool, warm, warmZone);
}

// ---------------------------------------------------------------- lighting
float phaseHG(float cosT, float g) {
    float g2 = g * g;
    return (1.0 - g2) / (4.0 * PI * pow(max(1.0 + g2 - 2.0 * g * cosT, 1e-4), 1.5));
}

// scatter weight: keep an isotropic floor so the shadowed side is not pitch black
float sidereal(float phase) {
    // low isotropic floor: unlit gas should actually read as dark, only the forward lobe is bright
    return 0.16 + 1.90 * phase;
}

// Atlas lookup coordinate: u_noiseScale zooms the whole baked cloud
// (1.0 = the volume as baked, 0.01 = one huge blob, 3.0 = three repeats).
// The fract keeps every scale inside the atlas tiles instead of bleeding into neighbours.
float hashStep(float i, float k) {
    return fract(sin(i * 12.9898 + k * 78.233) * 43758.5453);
}

// The baked volume maps exactly once onto the nebula ball, so it never repeats: the ray only
// ever samples points inside the sphere, which land inside [0,1]^3, and no wrap is needed.
// (Tiling it with fract() is what drew the cube-edge seam planes across the cloud.)
vec3 atlasCoord(vec3 p) {
    return p * (0.5 / (u_sphereSize * 1.02)) + 0.5;
}

// Detail tap: the baked volume *is* periodic, so tiling it is seamless. The detail channel is
// allowed to repeat (that is what keeps the cloud crisp when you zoom in); the silhouette above
// is not, because it is sampled exactly once.
vec3 atlasCoordTiled(vec3 p, float scale) {
    vec3 q = p * (0.5 / (u_sphereSize * 1.02)) * scale + 0.5;
    return q - floor(q);
}

// A fixed rotation decorrelates the detail tap from the shape tap without leaving the ball,
// so the two never sample the same voxel.
const mat3 DETAIL_ROT = mat3(0.36, 0.48, -0.80,
                             -0.80, 0.60, 0.00,
                             0.48, 0.64, 0.60);

// Optical density used by the shadow rays. One atlas lookup only: a shadow ray needs the
// bulk of the medium, not the fine detail, and this runs up to 6x per march step.
/**
 * Non emissive absorbing matter ("black smoke") that lives inside the cloud.
 *
 * It is deliberately built from the low and mid frequency bands only: a narrow band field
 * varies faster than a shadow ray's step, and its per pixel phase then reads as crawling
 * black speckle. Coarse shapes stay coherent along both the view ray and the light rays, so
 * the occlusion is stable while still being a real volumetric term.
 */
float darkField(vec3 p) {
    if (u_darkMode == 2 || u_darkAmount <= 0.001) return 0.0;
    vec3 s = noiseAt(atlasCoord(p));
    if (u_darkMode == 0) {
        // Knots are an *independent* occlusion field, not the complement of the gas: a black
        // body that only ever fills the holes in the glow is invisible against a black sky -
        // it has to sit in FRONT of lit gas to read as black matter.
        // The threshold rides the 阈值 slider so the same knob still decides how much survives.
        float knots = s.g * 0.65 + s.b * 0.35;
        return smoothstep(u_darkSep + 0.10, u_darkSep + 0.24, knots);
    }
    float w = vnoise(p * 2.6 + u_seed * 1.7);
    return smoothstep(0.44, 0.64, s.g * 0.55 + s.b * 0.45 + (w - 0.5) * 0.18);
}

float shadowDensity(vec3 p) {
    // sampled finer than the visible cloud so dust filaments read as thin occluders and cut
    // distinct lanes into the glow behind them, instead of one smooth grey blob
    // The occluder field must be *low pass*: sampling the fine channel along a 12 step shadow ray
    // undersamples it (roughly 1.5 samples per cycle), and the per-pixel phase of that aliasing is
    // what showed up as black dots crawling over the cloud.
    vec3 s = noiseAt(atlasCoord(p));
    float n = pow(clamp((s.b * 0.55 + s.g * 0.45) * 1.35, 0.0, 1.0), 1.15);
    float dust = 1.0 - smoothstep(u_dustThreshold - 0.06, u_dustThreshold + 0.06, n);
    float gas = 1.0 - smoothstep(u_gasInner, u_gasOuter, n);
    float dense = smoothstep(u_gasInner, mix(u_gasInner, u_gasOuter, 0.75), n);
    float dark = darkField(p) * (0.25 + 0.75 * dense);
    return u_dustDensity * dust * 0.090 + u_gasDensity * gas * 1.20
         + dark * (1.0 - u_darkAlpha) * min(1.0, u_darkAmount * 2.0) * 1.20;
}

// Light reaching p along dir over dist: transmittance through the gas AND the dust between.
float shadowT(vec3 p, vec3 dir, float dist, int steps) {
    float ds = dist / float(steps);
    float tau = 0.0;
    for (int i = 0; i < 6; i++) {
        if (i >= steps) break;
        tau += shadowDensity(p + dir * (ds * (float(i) + 0.5))) * ds;
    }
    return exp(-tau);
}

// the directional sun: shadow march toward it over a fixed distance
float lightTransmittance(vec3 p) {
    return shadowT(p, u_lightDir, 0.85, 12);
}

void main() {
    vec2 uv = v_ndc;
    vec3 rd = normalize(u_camFwd
                        + u_camRight * uv.x * u_tanHalfFov * u_aspect
                        + u_camUp * uv.y * u_tanHalfFov);
    vec3 ro = u_camPos;

    float R = u_sphereSize * 0.98;

    float tEnter = 0.0;
    float tExit = 1e9;
    float b = dot(ro, rd);
    float cc = dot(ro, ro) - R * R;
    float disc = b * b - cc;
    if (disc > 0.0) {
        float sq = sqrt(disc);
        tEnter = max(-b - sq, 0.0);
        tExit = -b + sq;
    }
    if (tExit <= tEnter) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    float span = tExit - tEnter;
    // The cloud no longer tiles, so there is no repetition phase to resolve: the slider only
    // changes grain size now and the sample count stays where the user set it.
    int steps = int(clamp(float(u_steps), 8.0, 240.0));
    float dt = span / float(steps);

    float jitter = hash13(vec3(gl_FragCoord.xy, u_time));
    float detail = 0.14 + 0.18 * u_octaves;

    vec3 col = vec3(0.0);
    float transmit = 1.0;
    float t1 = 1.0;   // multi scattering octaves: full / half / quarter extinction
    float t2 = 1.0;
    float t3 = 1.0;
    float phase = phaseHG(dot(rd, u_lightDir), 0.25);
    float tBase = tEnter + jitter * dt;
    vec3 dkDbg = vec3(0.0);

    for (int i = 0; i < 256; i++) {
        if (i >= steps) break;
        float t = tBase + float(i) * dt;
        vec3 p = ro + rd * t;

        float r = length(p);
        float shape = 1.0 - smoothstep(R * 0.62, R, r);
        if (shape <= 0.002) continue;

        // No per-step offset jitter: it decorrelates neighbours in screen space, which reads as
        // crawling black speckle the moment anything (camera, render scale) changes. Stratified
        // sampling (tBase already offsets by 0.5 * dt) is stable and banding free.
        vec3 pj = p;

        // the silhouette always comes from the volume sampled at its own scale, so raising
        // the slider adds grain instead of tiling the whole cloud into wallpaper
        vec3 s3 = noiseAt(atlasCoord(pj));
        vec3 sDet = noiseAt(atlasCoordTiled(DETAIL_ROT * pj, 2.2 + clamp(u_noiseScale, 0.5, 6.0) * 0.75));
        float lod = smoothstep(1.0, 6.0, u_noiseScale);

        float nBase = clamp(s3.r * 0.55 + s3.g * 0.45, 0.0, 1.0);
        float nDet = clamp(sDet.r * 0.70 + sDet.g * 0.30, 0.0, 1.0);
        // the detail channel used to be off below noise scale 1, which is exactly why the default
        // view read as a few smooth blobs; it now always contributes a share of the density
        // Detail is added as a small *bounded* perturbation around the silhouette field. Mixing
        // the two fields outright made the density vary at a frequency finer than a ray step,
        // which aliased into salt-and-pepper speckle that crawled whenever the frame changed.
        // Detail weight peaks at a moderate tiling factor. Past it the tap outruns the ray
        // step spacing and turns into aliasing, which is the crawling black speckle people see
        // when they push 噪声尺度 up; beyond the peak the coarse field carries the look instead.
        float fineBand = smoothstep(1.0, 3.0, u_noiseScale) * (1.0 - smoothstep(4.5, 9.0, u_noiseScale));
        float lodD = 0.08 + 0.30 * fineBand + 0.08 * lod;
        float n = clamp(nBase + (nDet - 0.5) * lodD, 0.0, 1.0);
        // Grain is a small additive term only: letting it dominate at high slider values was what
        // produced the regular, wall-papered blobs (high frequency lattice aliasing on top of a
        // smooth field), not the texture atlas itself.
        float grainFreq = clamp(u_noiseScale, 0.5, 8.0) * 5.0;
        float micro = vnoise(pj * grainFreq + u_seed);
        n += (micro - 0.5) * detail * 0.10;
        // The periodic bake is close to Gaussian around 0.5 (a periodic field has less large
        // scale contrast than the old one), so stretch around the mean before the ramps bite.
        n = pow(clamp(n * 1.35, 0.0, 1.0), 1.15);

        float voidMask = smoothstep(0.40, 0.63, s3.b);
        float warmZone = smoothstep(0.40, 0.70, s3.b);
        warmZone = max(warmZone, 0.85 * smoothstep(-0.10, 0.60, dot(normalize(p), u_warmDir)));
        warmZone = max(warmZone, smoothstep(0.55, -0.45, uv.x) * (0.55 + 0.45 * s3.b));

        // dust is broken up by a finer field: a smooth blob only dims everything evenly, while
        // filaments leave gaps that read as dark lanes cut into the glow behind them
        float dustFilaments = smoothstep(0.34, 0.72, vnoise(pj * 6.5 + u_seed));
        float mc = (1.0 - smoothstep(u_dustThreshold - 0.05, u_dustThreshold + 0.05, n))
                 * (0.25 + 0.75 * dustFilaments);
        // One field, one threshold: above it the same noise is glowing gas, below it that noise
        // is black matter. The two phases never share a sample, so gas can no longer glow
        // "through" the inside of a dark knot - the boundary is exactly where the threshold is,
        // and the cloud ends up sheeting around the dark cores instead of swallowing them.
        // The gas/black boundary must follow the *low pass* field: taking it from the full
        // density (detail + grain included) makes the boundary ragged at single sample scale,
        // which showed up as brown speckle along every edge.
        float nPhase = pow(clamp(nBase * 1.35, 0.0, 1.0), 1.15);
        float gasPhase = smoothstep(u_darkSep - 0.075, u_darkSep + 0.075, nPhase);
        float gc = gasPhase;
        mc *= shape * voidMask * gasPhase;
        gc *= shape * (0.10 + 0.90 * voidMask);

        float dens = u_dustDensity * mc * 0.060 + u_gasDensity * gc;
        // the black matter is allowed to exist where there is no gas, so it can silhouette
        // against the glow behind it instead of only dimming the clump it sits in
        // u_darkAlpha is the transparency of the black matter itself: 0 = fully opaque,
        // 1 = invisible. It scales everything the dark matter does at once (blocks emission,
        // adds extinction, occludes the light rays) so the look changes coherently.
        // Two different materials, not one material with one opacity:
        //   * inside the dense lobes the knots are compressed and nearly opaque (sigma ~8)
        //   * in the thin outskirts the same field is only a faint veil (sigma ~1.2, 12% coverage)
        // Without this split the outskirts got the same solid black as the core and the cloud
        // read as if it were wrapped in a black shell.
        float denseBias = smoothstep(u_gasInner, mix(u_gasInner, u_gasOuter, 0.75), n);
        float dk = darkField(pj) * min(1.0, u_darkAmount * 2.0) * (1.0 - u_darkAlpha);
        // Dark matter no longer multiplies the emission down per sample: that hard per-sample
        // mask was what made it read as a cut-out instead of a medium. It now only adds optical
        // depth, so the ordinary transmission term dims a ray smoothly in proportion to how much
        // of it the ray crossed, and everything in front of it still adds up.
        float dkSigma = dk * (1.60 + 7.60 * denseBias);
        vec3 sDbg = noiseAt(atlasCoord(pj));
        float knotsDbg = smoothstep(0.62, 0.74, sDbg.g * 0.65 + sDbg.b * 0.35);
        dkDbg = max(dkDbg, vec3(dkSigma / 8.0, darkField(pj), denseBias));
        if (dens < 1e-5 && dk < 0.01) continue;

        // ---- lighting ----------------------------------------------------------
        // Two sources, both shadowed by the medium in between:
        //   * a point star at the origin (1/r^2 falloff, warm)
        //   * the directional sun (constant, forward scattering)
        // Gas and dust are two emissive media that ADD. Where they overlap the palette
        // interpolates into an in-between hue instead of one masking the other, which is what
        // turns the two hard colour bands into a gradient.
        vec3  albedo = gasColour(n, warmZone);
        // Only a gentle tint towards the warm band where dust dominates: the additive sum of
        // the two emissions already produces the gradient, a hard re-colour on top of it just
        // flattens the palette.
        float dustShare = mc / (mc + gc * 0.75 + 1e-4);
        albedo = mix(albedo, mix(albedo, u_colWarm, 0.55), clamp(dustShare * 0.6, 0.0, 1.0));

        vec3  toStar = normalize(-p + 1e-6);
        float dStar  = max(length(p), 0.06);
        float tStar  = pow(shadowT(p, toStar, dStar, 12), u_shadow);
        vec3  starL  = u_starColor * u_starEnergy * (0.40 / (0.15 + 2.0 * dStar * dStar));
        float phStar = phaseHG(dot(-rd, toStar), 0.40);

        float tSun  = pow(lightTransmittance(p), u_shadow);
        vec3  sunL  = u_lightColor * u_lightEnergy * tSun;
        float phSun = phaseHG(dot(rd, -u_lightDir), 0.62);

        // the gas keeps its own palette: it only takes a fraction of the star light,
        // while the dust is allowed to take the full warm beam and glow as a silhouette
        vec3 gasLight  = starL * tStar * sidereal(phStar) * 1.10 + sunL * sidereal(phSun) * 0.35;
        vec3 dustLight = starL * tStar * sidereal(phStar) * 1.60 + sunL * sidereal(phSun) * 0.55;

        // gas: self luminous plasma + single scattering of both lights
        // The plasma only glows where starlight actually reaches it, so a dust column between the
        // star and the gas carves a real shadow instead of every clump glowing on its own.
        float excite = mix(0.06, 1.0, tStar);
        vec3 emit = albedo * u_emission * (0.10 + 1.50 * gc) * 2.1 * excite;
        vec3 emitDust = vec3(0.0);
        emit += albedo * u_colWarm * pow(gc * (1.0 - gc) * 2.0, 2.0) * 2.0 * excite;
        emit += albedo * gasLight * (0.55 + 1.45 * gc);

        // dust: warm scattering, self shadowed, strong forward lobe
        if (mc > 0.004) {
            float lt = lightTransmittance(p);
            vec3 dustAlbedo = u_colWarm * 0.26 + 0.02;
            emitDust += dustAlbedo * lt * u_lightColor * u_lightEnergy * (0.25 + phase * 3.0) * 1.10;
            emitDust += dustAlbedo * dustLight * 0.35;
        }

        // central star core bleeding through the volume
        float dc = length(p);
        emit += u_starColor * u_coreEnergy * (0.10 / (1.0 + dc * dc * 9.0)) * (0.4 + 0.6 * tStar);

        float sigma = u_dustDensity * mc * 0.055 + u_gasDensity * gc * 1.2;

        // ---- multiple scattering ------------------------------------------------
        // Three octaves with progressively smaller extinction (k = 1, 1/2, 1/4). Thin gas
        // sees all three the same (T ~ 1) and the weights sum to 1, so nothing gets washed
        // out; inside the dense core only the low-k octaves survive, which is exactly why a
        // thick cloud keeps glowing in the middle instead of going black.
        // Light that scatters more than once stays trapped inside the optically thick part and
        // leaks back out, so a dense core keeps a glow instead of collapsing to black. Thin gas
        // has t1 ~ 1 and is left untouched, which is what keeps the empty sky properly dark.
        if (u_dbg > 0.5) { col += dens * dt * 3.0 * vec3(tStar); t1 = 1.0; }
        float trapped = (1.0 - t1) * (0.55 + 0.45 * t2);
        // multiple scattering must not rescue light from inside the black matter: that was
        // why a thick slab only looked one step darker instead of progressively darker
        float weight = t1 + u_multi * 0.55 * trapped;
        // the dark matter emits nothing and is merely denser per unit length, so it eats the
        // light behind it: emission is scaled down, extinction up
        col += dens * dt * 2.4 * (emit * weight + emitDust * t1) * (1.0 - step(0.5, u_dbg));
        sigma += dkSigma;
        t1 *= exp(-sigma * dt * 1.00);
        t2 *= exp(-sigma * dt * 0.50);
        t3 *= exp(-sigma * dt * 0.25);
        transmit = t1;
        if (t1 < 0.004 && t2 < 0.02) break;
    }

    vec3 rgb = col * u_exposure;
    rgb = clamp(hsvShift(rgb, u_hsv), 0.0, 8.0);
    rgb = rgb / (1.0 + rgb * 0.70);
    // keep the palette readable after the tonemap (it used to wash out to white)
    float luma = dot(rgb, vec3(0.299, 0.587, 0.114));
    rgb = clamp(mix(vec3(luma), rgb, 1.35), 0.0, 1.0);
    // contrast curve: pulls the murky midtones down so genuinely empty sky reads as black
    rgb = pow(rgb, vec3(1.14));
    // Premultiplied alpha-over instead of additive: a purely additive pass can only ever ADD
    // light, so the black matter had no way to appear - it could only "not glow". Now the
    // medium reports how much of the sky/stars behind it survives (1 - transmit), and anything
    // opaque with zero emission actually comes out black.
    float alpha = clamp(1.0 - transmit, 0.0, 1.0);
    if (u_dbg > 2.5) { gl_FragColor = vec4(dkDbg, 1.0); return; }
    gl_FragColor = vec4(rgb, alpha);
}
