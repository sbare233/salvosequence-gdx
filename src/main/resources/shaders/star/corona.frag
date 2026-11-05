#ifdef GL_ES
precision highp float;
#endif

varying vec2 v_uv;

uniform vec3  u_camPos;
uniform vec3  u_camRight;
uniform vec3  u_camUp;
uniform vec3  u_camFwd;
uniform float u_tanHalf;
uniform float u_aspect;

uniform float u_time;
uniform vec3  u_seed;
uniform float u_prom;      // 日珥强度
uniform float u_exposure;

uniform vec3  u_arcA[8];
uniform vec3  u_arcB[8];
uniform vec3  u_arcC[8];
uniform float u_arcOn[8];
uniform float u_arcFlare[8];

// ---------- 噪声 ----------
float hash13(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float vnoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash13(i + vec3(0,0,0)), hash13(i + vec3(1,0,0)), f.x),
                   mix(hash13(i + vec3(0,1,0)), hash13(i + vec3(1,1,0)), f.x), f.y),
               mix(mix(hash13(i + vec3(0,0,1)), hash13(i + vec3(1,0,1)), f.x),
                   mix(hash13(i + vec3(0,1,1)), hash13(i + vec3(1,1,1)), f.x), f.y), f.z);
}

float fbm(vec3 p, int oct) {
    float a = 0.5, s = 0.0, n = 0.0;
    for (int i = 0; i < 6; i++) {
        if (i >= oct) break;
        s += a * vnoise(p);
        n += a;
        p = p * 2.03 + 11.7;
        a *= 0.5;
    }
    return s / max(n, 1e-5);
}

void main() {
    // 从相机基向量重建射线
    vec2 ndc = v_uv * 2.0 - 1.0;
    vec3 rd = normalize(u_camFwd + u_camRight * (ndc.x * u_tanHalf * u_aspect) + u_camUp * (ndc.y * u_tanHalf));
    vec3 ro = u_camPos;

    float tca = dot(ro, rd);
    float d2  = dot(ro, ro) - tca * tca;     // 视线到星心的垂距平方
    float b   = sqrt(max(d2, 0.0));          // 碰撞参数
    float disc = 1.0 - d2;

    // 射线是否打中球面（打中则被恒星本体挡住，不叠加日冕辉光）
    float hit = 0.0;
    if (disc > 0.0) {
        float t0 = tca - sqrt(disc);
        if (t0 > 0.0) hit = 1.0;
    }

    vec3 col = vec3(0.0);

    // ---------- 日冕辉光 ----------
    float x = max(b - 1.0, 0.0);
    float glow = exp(-x * 7.5) * 0.34 + exp(-x * 2.4) * 0.10;
    vec3 cdir = normalize(ro + rd * tca);
    float n = fbm(cdir * 7.5 + u_seed + vec3(u_time * 0.02), 4);
    glow *= 0.45 + 1.05 * n;
    // 色球环（贴着边缘的一圈亮）
    float ring = exp(-pow((b - 1.0) * 70.0, 2.0)) * 0.30;
    vec3 coronaCol = mix(vec3(1.0, 0.34, 0.08), vec3(1.0, 0.55, 0.22), n * 0.6);
    col += coronaCol * (glow * (1.0 - hit) + ring * (1.0 - hit * 0.85));

    // ---------- 日珥：沿偶极弧的参数化结构 ----------
    if (u_prom > 0.01) {
        for (int i = 0; i < 8; i++) {
            if (u_arcOn[i] < 0.5) continue;
            vec3 A = u_arcA[i];
            vec3 C = u_arcC[i];
            vec3 B = u_arcB[i];

            float best = 1e9;
            float bestS = 0.0;
            for (int k = 0; k < 40; k++) {
                float s = (float(k) + 0.5) / 40.0;
                vec3 P = (1.0 - s) * (1.0 - s) * A + 2.0 * s * (1.0 - s) * C + s * s * B;
                float dseg = length(cross(P - ro, rd));
                if (dseg < best) { best = dseg; bestS = s; }
            }

            float arch = sin(bestS * 3.14159265);
            float width = 0.010 + 0.014 * arch;
            float dens = exp(-pow(best / width, 2.0)) * arch * arch;
            // 沿弧的纤维状结构
            dens *= 0.45 + 1.10 * fbm(A * 9.0 + vec3(bestS * 4.0) + u_seed, 3);
            dens *= u_prom;

            vec3 pc = mix(vec3(1.00, 0.12, 0.06), vec3(1.00, 0.42, 0.14), arch);
            col += pc * dens * 1.15;

            // ---------- 耀斑：弧顶的短时脉冲 ----------
            float fl = u_arcFlare[i];
            if (fl > 0.01) {
                float da = length(cross(C - ro, rd));
                float blob = exp(-pow(da / 0.055, 2.0));
                float core = exp(-pow(da / 0.020, 2.0));
                col += vec3(1.0, 0.88, 0.62) * (blob * 1.6 + core * 3.4) * fl;
            }
        }
    }

    col *= (1.0 - hit);   // 恒星本体挡住的部分不叠加
    gl_FragColor = vec4(col * u_exposure, 1.0);
}
