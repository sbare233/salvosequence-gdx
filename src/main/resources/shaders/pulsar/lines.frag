varying vec3 v_col;
uniform float u_alpha;
void main()
{
    gl_FragColor = vec4(v_col * u_alpha, u_alpha);
}
