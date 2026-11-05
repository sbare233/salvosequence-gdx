#version 150

in vec2 v_uv;
out vec4 fragColor;

uniform sampler2D u_tex;
uniform float u_threshold;

void main() {
    vec3 c = texture(u_tex, v_uv).rgb;
    float l = max(c.r, max(c.g, c.b));
    float f = max(l - u_threshold, 0.0) / max(l, 1e-4);
    fragColor = vec4(c * f, 1.0);
}
