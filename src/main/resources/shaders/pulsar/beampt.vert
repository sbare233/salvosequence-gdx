attribute vec2 a_ta;      // Position: x = 相位 (0..1), y = 方位角
attribute vec2 a_rr;      // Normal:   x = 半径比例 (0..1), y = 每粒子的随机数

uniform mat4  u_mvp;
uniform vec3  u_camPos;
uniform vec3  u_apex;
uniform vec3  u_axis;
uniform vec3  u_right;
uniform vec3  u_up;
uniform float u_length;
uniform float u_width;
uniform float u_base;
uniform float u_radius;   // 星体半径（遮挡用）
uniform float u_px;       // viewportHeight / (2*tan(fov/2))：把世界尺寸换算成像素
uniform float u_size;     // 粒子世界尺寸
uniform float u_roll;     // 自转相位
uniform float u_falloff;
uniform float u_time;     // 秒
uniform float u_speed;    // 每秒跑完几根柱子（越大越像"喷出"）

varying float v_alpha;
varying float v_t;
varying float v_rnd;

void main()
{
    // 从极冠喷出：每颗粒子有自己的相位与速度，时间推进时沿轴往外跑，
    // 跑到柱子末端就回到极冠重新开始（fract 循环；两端 alpha 都收到 0，看不出接缝）。
    float speed = u_speed * (0.65 + 0.70 * a_rr.y);
    float t = fract(a_ta.x + u_time * speed);
    float ang = a_ta.y;

    // 越往外柱子越粗（和真实喷流的张角一致）
    float R = u_base + tan(u_width) * u_length * t;
    float rad = sqrt(a_rr.x) * R;

    vec3 world = u_apex
               + u_axis * (t * u_length)
               + (u_right * cos(ang) + u_up * sin(ang)) * rad;

    vec4 vp = u_mvp * vec4(world, 1.0);
    gl_Position = vp;

    vec3 toCam = world - u_camPos;
    float dist = length(toCam);

    // 点精灵尺寸：世界尺寸 × 像素换算 / 距离
    gl_PointSize = clamp(u_size * (0.55 + 0.9 * a_rr.y) * u_px / max(dist, 0.05), 1.0, 96.0);

    // 遮挡：相机到粒子的射线若先撞到星体，这颗粒子就在星体背后
    vec3 rd = toCam / max(dist, 1e-4);
    float b = dot(u_camPos, rd);
    float c = dot(u_camPos, u_camPos) - u_radius * u_radius;
    float disc = b * b - c;
    float occ = 1.0;
    if (disc > 0.0)
    {
        // 近交点：粒子比它远（dist > tHit）说明被星体挡住，所以要用 1 - smoothstep
        float tHit = -b - sqrt(disc);
        occ = 1.0 - smoothstep(0.0, 0.06, dist - tHit);
    }

    // 生命曲线：刚从极冠出来就很亮，往外逐渐变暗，到末端淡出
    float alive = smoothstep(0.0, 0.035, t) * (1.0 - smoothstep(0.62, 1.0, t));
    float decay = exp(-t * u_falloff * 1.25);
    // 方位亮瓣跟着自转扫
    float lobe = 0.30 + 0.70 * pow(0.5 + 0.5 * cos(ang + u_roll), 1.4);

    v_alpha = alive * decay * lobe * occ * (0.55 + 0.75 * a_rr.y);
    v_t = t;
    v_rnd = a_rr.y;
}
