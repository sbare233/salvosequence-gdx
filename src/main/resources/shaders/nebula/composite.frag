#version 150

in vec2 v_uv;
out vec4 fragColor;

uniform sampler2D u_scene;
uniform sampler2D u_bloom;
uniform float u_strength;

void main() {
    vec3 c = texture(u_scene, v_uv).rgb;
    vec3 b = texture(u_bloom, v_uv).rgb;
    c += b * u_strength;
    fragColor = vec4(c, 1.0);
}
