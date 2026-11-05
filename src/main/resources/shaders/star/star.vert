attribute vec3 a_pos;

uniform mat4 u_projTrans;
uniform mat4 u_model;

varying vec3 v_dir;
varying vec3 v_world;

void main() {
    v_dir = a_pos;
    vec4 w = u_model * vec4(a_pos, 1.0);
    v_world = w.xyz;
    gl_Position = u_projTrans * w;
}
