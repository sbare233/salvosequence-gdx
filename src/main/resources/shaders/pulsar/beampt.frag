varying float v_alpha;
varying float v_t;
varying float v_rnd;

uniform vec3  u_color;
uniform float u_bright;
uniform float u_density;   // 按粒子数归一：多了更密更亮，但不会一片死白

void main()
{
    // 圆形软点：中心亮、边缘平滑衰减（和星云里用的软点精灵同一套思路）
    vec2 q = gl_PointCoord * 2.0 - 1.0;
    float r2 = dot(q, q);
    if (r2 > 1.0) discard;

    float soft = exp(-r2 * 2.6) * (1.0 - smoothstep(0.55, 1.0, r2));

    // 每颗粒子的亮度略有差异，堆叠起来才有颗粒感而不是一片糊
    float a = soft * v_alpha * u_bright * 0.105 * u_density;

    gl_FragColor = vec4(u_color * a, a);
}
