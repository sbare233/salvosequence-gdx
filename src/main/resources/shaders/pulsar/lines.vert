attribute vec3 a_position;
attribute vec3 a_color;

uniform mat4 u_mvp;

varying vec3 v_col;

void main()
{
    v_col = a_color;
    gl_Position = u_mvp * vec4(a_position, 1.0);
}
