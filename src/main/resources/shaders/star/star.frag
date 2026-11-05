#ifdef GL_ES
precision highp float;
#endif

varying vec3 v_dir;
varying vec3 v_world;

uniform vec3  u_camPos;
uniform float u_time;
uniform vec3  u_seed;

uniform float u_gran;      // 颗粒密度
uniform float u_spot;      // 黑子强度
uniform float u_limb;      // 边缘变暗
uniform float u_exposure;
uniform float u_dbg;      // 调试：1=显示 cell，2=显示 gran

// ---------- 噪声 ----------
vec3 hash33(vec3 p) {
    p = vec3(dot(p, vec3(127.1, 311.7, 74.7)),
             dot(p, vec3(269.5, 183.3, 246.1)),
             dot(p, vec3(113.5, 271.9, 124.6)));
    return fract(sin(p) * 43758.5453123);
}

// Worley / cellular：返回 F1（细胞中心处 ≈ 0，细胞边界处变大）
float worley(vec3 p) {
    vec3 ip = floor(p);
    vec3 fp = fract(p);
    float d = 1e9;
    for (int z = -1; z <= 1; z++)
    for (int y = -1; y <= 1; y++)
    for (int x = -1; x <= 1; x++) {
        vec3 g = vec3(float(x), float(y), float(z));
        vec3 o = hash33(ip + g);
        vec3 r = g + o - fp;
        d = min(d, dot(r, r));
    }
    return sqrt(d);
}

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
    vec3 d = normalize(v_dir);
    float t = u_time;

    // ---------- 光球：米粒组织 ----------
    // 慢速域扭曲，让细胞随对流缓慢流动
    float warp = fbm(d * 3.0 + u_seed, 3) - 0.5;
    vec3  p    = d * u_gran + vec3(warp * 1.1) + vec3(0.0, t * 0.030, t * 0.012) + u_seed;

    float f1 = worley(p);                 // 主米粒
    float f2 = worley(p * 2.35 + 5.1);    // 次级米粒
    float superg = fbm(d * 6.5 + u_seed * 2.3, 3) - 0.5;
    float cell = f1;
    float lanes = smoothstep(0.60, 0.98, f1);          // 细胞间隙（f1 大）→ 暗
    float gbase = fbm(p * 0.35, 4);                     // 低频起伏，均值≈0.5
    float gran  = 0.55 + 0.45 * (gbase * 2.0 - 1.0);    // 0.10 .. 1.00
    gran *= 1.0 - 0.55 * lanes;                         // 叠上细胞网纹
    gran *= 0.92 + 0.16 * f2;
    gran *= 1.0 + superg * 0.30;
    gran = clamp(gran, 0.0, 1.2);

    // ---------- 黑子 ----------
    float sLow = fbm(d * 1.5 + u_seed * 1.7, 4);
    float thrS = mix(0.86, 0.52, clamp(u_spot, 0.0, 1.0));
    float spot  = smoothstep(thrS, thrS + 0.10, sLow);
    float umbra = smoothstep(thrS + 0.10, thrS + 0.20, sLow);
    float fib = fbm(vec3(d * 44.0) + 9.3, 2);
    float pen = 0.42 + 0.58 * smoothstep(0.32, 0.68, fib);
    float spotDim = mix(1.0, mix(pen * 0.60, 0.30, clamp(umbra, 0.0, 1.0)), clamp(spot, 0.0, 1.0));
    float facula = smoothstep(thrS - 0.05, thrS + 0.02, sLow) * (1.0 - smoothstep(thrS + 0.02, thrS + 0.10, sLow));

    // ---------- 温度 → 颜色 ----------
    float heat = clamp(gran, 0.0, 1.0);
    vec3 cCool = vec3(1.00, 0.80, 0.56);
    vec3 cMid  = vec3(1.00, 0.97, 0.90);
    vec3 cHot  = vec3(1.00, 1.00, 1.00);   // 最热的米粒直接到纯白
    vec3 col = mix(cCool, cMid, smoothstep(0.10, 0.90, heat));
    col = mix(col, cHot, smoothstep(0.80, 1.05, heat));
    col *= spotDim;
    col *= 1.0 + facula * 0.35;

    // ---------- 边缘变暗 + 边缘偏红 ----------
    vec3 viewDir = normalize(u_camPos - v_world);
    // 注意：v_world 是模型变换后的世界坐标，d 是模型空间方向；边缘变暗必须在世界空间算
    vec3 worldNormal = normalize(v_world);

    // 只保留朝向相机的半球：用几何朝向判断，和三角形绕序无关，
    // 所以不会像 glCullFace 那样因为绕序反了把正面剔掉；也不会让背面
    // 和正面在轮廓处几乎同深度而互相 z-fighting（之前那道锯齿黑边）。
    if (dot(worldNormal, viewDir) <= 0.0) discard;

    float mu = clamp(dot(worldNormal, viewDir), 0.0, 1.0);
    float ld = 1.0 - u_limb * (1.0 - pow(mu, 0.55));
    col *= ld;
    col = mix(col, col * vec3(1.00, 0.72, 0.50), (1.0 - mu) * 0.25);

    col = pow(max(col, vec3(0.0)), vec3(1.18));   // 压暗中间调，只让最热的米粒顶到纯白
    vec3 outCol = col * u_exposure;
    if (u_dbg > 0.5 && u_dbg < 1.5) outCol = vec3(clamp(cell, 0.0, 1.0));
    if (u_dbg > 1.5 && u_dbg < 2.5) outCol = vec3(clamp(gran, 0.0, 1.0));
    if (u_dbg > 2.5) outCol = col;
    gl_FragColor = vec4(outCol, 1.0);
}
