attribute vec2 a_pos;
attribute vec2 a_uv;

uniform float u_z;   // 0 = 正常后处理；1 = 远平面（日冕，需要被实心球挡住）

varying vec2 v_uv;

void main() {
    v_uv = a_uv;
    gl_Position = vec4(a_pos, u_z, 1.0);
}
