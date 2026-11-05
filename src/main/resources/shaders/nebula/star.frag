#version 150

in vec2  v_corner;
in vec3  v_color;
in float v_glare;

out vec4 fragColor;

void main() {
    float d = length(v_corner);
    if (d > 1.0) discard;

    float core = exp(-d * d * 26.0);
    float halo = exp(-d * 5.0) * 0.16;
    // diffraction spikes on the bright "large" stars
    float sx = exp(-abs(v_corner.x) * 90.0) * exp(-abs(v_corner.y) * 3.4);
    float sy = exp(-abs(v_corner.y) * 90.0) * exp(-abs(v_corner.x) * 3.4);
    float cross = (sx + sy) * v_glare;

    vec3 c = v_color * (core * 2.8 + halo * 1.2 + cross * 0.85);
    c *= smoothstep(1.0, 0.82, d);
    fragColor = vec4(c, 1.0);
}
