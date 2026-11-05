#version 150

in vec3 a_center;
in vec2 a_corner;
in float a_size;
in vec3 a_color;
in float a_glare;

uniform mat4  u_viewProj;
uniform vec3  u_camRight;
uniform vec3  u_camUp;

out vec2  v_corner;
out vec3  v_color;
out float v_glare;

void main() {
    vec3 pos = a_center + (u_camRight * a_corner.x + u_camUp * a_corner.y) * a_size;
    v_corner = a_corner;
    v_color = a_color;
    v_glare = a_glare;
    gl_Position = u_viewProj * vec4(pos, 1.0);
}
