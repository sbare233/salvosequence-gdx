#version 150

in vec2 v_uv;
out vec4 fragColor;

uniform sampler2D u_tex;
uniform vec2 u_dir;      // (1/w, 0) or (0, 1/h)

void main() {
    float w[5];
    w[0] = 0.2270270;
    w[1] = 0.1945946;
    w[2] = 0.1216216;
    w[3] = 0.0540540;
    w[4] = 0.0162162;

    vec3 sum = texture(u_tex, v_uv).rgb * w[0];
    for (int i = 1; i < 5; i++) {
        vec2 o = u_dir * float(i) * 1.6;
        sum += texture(u_tex, v_uv + o).rgb * w[i];
        sum += texture(u_tex, v_uv - o).rgb * w[i];
    }
    fragColor = vec4(sum, 1.0);
}
