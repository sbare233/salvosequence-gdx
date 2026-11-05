varying vec2 v_uv;

uniform vec3  u_camPos;
uniform vec3  u_camRight;
uniform vec3  u_camUp;
uniform vec3  u_camFwd;
uniform float u_tanHalf;
uniform float u_aspect;

uniform float u_spinRad;
uniform vec3  u_magAxis;
uniform vec3  u_spots[8];

uniform float u_radius;
uniform float u_temp;
uniform float u_spot;
uniform float u_limb;
uniform float u_exposure;
uniform mat4  u_viewProj;

float hash(vec3 p)
{
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

float vnoise(vec3 p)
{
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash(i);
    float n100 = hash(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
               mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

void main()
{
    // 直接与球求交：没有网格，就没有 UV 接缝、没有绕序问题、球面精确
    vec2 ndc = v_uv * 2.0 - 1.0;
    vec3 dir = normalize(u_camFwd
                       + u_camRight * (ndc.x * u_tanHalf * u_aspect)
                       + u_camUp    * (ndc.y * u_tanHalf));

    float b = dot(u_camPos, dir);
    float c = dot(u_camPos, u_camPos) - u_radius * u_radius;
    float disc = b * b - c;
    if (disc < 0.0) discard;

    float t = -b - sqrt(disc);
    if (t < 0.0) discard;

    vec3 p = u_camPos + dir * t;
    vec3 nw = normalize(p);
    vec3 v = normalize(u_camPos - p);

    // 边缘变暗只取决于视线夹角（不随自转）
    float mu = clamp(dot(nw, v), 0.0, 1.0);
    float limb = mix(1.0 - u_limb, 1.0, pow(mu, 0.55));

    // 世界法线转回模型空间（绕 Y 反向旋转），得到贴在星体表面、跟着转的坐标
    float cs = cos(-u_spinRad);
    float sn = sin(-u_spinRad);
    vec3 nm = normalize(vec3(cs * nw.x + sn * nw.z, nw.y, -sn * nw.x + cs * nw.z));

    vec3 base = mix(vec3(0.70, 0.82, 1.00), vec3(1.00), clamp(u_temp, 0.0, 1.0));

    // 磁极热冠：世界空间磁轴 × 世界空间法线（跟着星体转，因为磁轴随模型转）
    float d = abs(dot(nw, normalize(u_magAxis)));
    float cap = smoothstep(0.90, 0.995, d);
    base += vec3(0.40, 0.62, 1.00) * cap * u_spot;

    // 表面热斑（模型空间，跟着转）
    float spots = 0.0;
    spots += exp(-dot(nm - u_spots[0], nm - u_spots[0]) * 14.0);
    spots += exp(-dot(nm - u_spots[1], nm - u_spots[1]) * 14.0);
    spots += exp(-dot(nm - u_spots[2], nm - u_spots[2]) * 14.0);
    spots += exp(-dot(nm - u_spots[3], nm - u_spots[3]) * 14.0);
    spots += exp(-dot(nm - u_spots[4], nm - u_spots[4]) * 14.0);
    spots += exp(-dot(nm - u_spots[5], nm - u_spots[5]) * 14.0);
    spots += exp(-dot(nm - u_spots[6], nm - u_spots[6]) * 14.0);
    spots += exp(-dot(nm - u_spots[7], nm - u_spots[7]) * 14.0);
    base = mix(base, vec3(1.45), clamp(spots * 1.6, 0.0, 1.0));

    // 大尺度斑驳：模型空间的值噪声，自转时在盘面上明显移动
    float mottle = vnoise(nm * 3.4) * 0.65 + vnoise(nm * 8.0) * 0.35;
    base *= 0.72 + 0.62 * mottle;

    // 半球级明暗（用 nm.x，避免经度接缝）
    base *= 0.90 + 0.20 * smoothstep(-0.90, 0.90, nm.x);

    // 壳层颗粒
    base *= 0.94 + 0.12 * hash(floor(nm * 160.0));

    // 把命中点写进深度缓冲：喷流/磁力线之后再用深度测试，就能区分
    // "在球面前面"（看得见）和"在球面后面"（被挡住）。
    vec4 cp = u_viewProj * vec4(p, 1.0);
    gl_FragDepth = clamp(0.5 + 0.5 * (cp.z / cp.w), 0.0, 1.0);
    gl_FragColor = vec4(base * limb * u_exposure, 1.0);
}
