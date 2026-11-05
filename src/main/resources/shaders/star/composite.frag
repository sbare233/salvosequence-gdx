#ifdef GL_ES
precision highp float;
#endif

varying vec2 v_uv;

uniform sampler2D u_scene;
uniform sampler2D u_bloom;

uniform float u_bloomStrength;
uniform float u_spikes;
uniform float u_dispersion;
uniform vec2  u_starScreen;   // 星心在屏幕上的 uv
uniform float u_exposure;

void main() {
    vec2 uv = v_uv;

    // ---------- 轻微色散（径向色差） ----------
    vec2 d = uv - u_starScreen;
    float r2 = dot(d, d);
    vec2 off = d * (u_dispersion * 0.0012) * (0.3 + r2 * 6.0);
    vec3 scene;
    scene.r = texture2D(u_scene, uv + off).r;
    scene.g = texture2D(u_scene, uv).g;
    scene.b = texture2D(u_scene, uv - off).b;

    vec3 bloom = texture2D(u_bloom, uv).rgb;

    // ---------- 衍射星芒 ----------
    float spikes = 0.0;
    if (u_spikes > 0.001) {
        float ang = atan(d.y, d.x);
        float r = length(d);
        for (int i = 0; i < 3; i++) {
            float a = float(i) * 1.04719755 + 0.26;   // 6 芒，每 60°
            float ca = cos(a), sa = sin(a);
            float along = d.x * ca + d.y * sa;
            float perp  = -d.x * sa + d.y * ca;
            spikes += exp(-abs(perp) * 190.0) * exp(-abs(along) * 2.6);
        }
        spikes *= u_spikes;
    }

    vec3 col = scene + bloom * u_bloomStrength;
    // 星芒只用 bloom 的亮度调制，避免把地表也点亮
    col += bloom * spikes * 0.55;

    // ---------- 色调映射 ----------
    // 软膝上移到 1.15：亮度 <= 1.15 原样通过（盘面最亮处会顶到 1.0 = 纯白），
    // 只有 bloom 叠出来的超亮部分才被压缩，避免整片糊成一片白。
    col = col / (1.0 + max(col - 1.15, 0.0) * 1.00);
    col = pow(clamp(col, 0.0, 1.0), vec3(1.02));

    gl_FragColor = vec4(col, 1.0);
}
