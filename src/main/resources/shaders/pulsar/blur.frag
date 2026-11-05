#ifdef GL_ES
precision highp float;
#endif

varying vec2 v_uv;

uniform sampler2D u_tex;
uniform vec2  u_dir;      // (1,0) 或 (0,1)
uniform vec2  u_texel;

void main() {
    // 9 tap 高斯
    float w[5];
    w[0] = 0.2270270270;
    w[1] = 0.1945945946;
    w[2] = 0.1216216216;
    w[3] = 0.0540540541;
    w[4] = 0.0162162162;

    vec2 stepv = u_dir * u_texel;
    vec3 c = texture2D(u_tex, v_uv).rgb * w[0];
    for (int i = 1; i < 5; i++) {
        vec2 o = stepv * float(i);
        c += texture2D(u_tex, v_uv + o).rgb * w[i];
        c += texture2D(u_tex, v_uv - o).rgb * w[i];
    }
    gl_FragColor = vec4(c, 1.0);
}
