#ifdef GL_ES
precision highp float;
#endif

varying vec2 v_uv;

uniform sampler2D u_tex;
uniform float u_scale;

void main() {
    gl_FragColor = vec4(texture2D(u_tex, v_uv).rgb * u_scale, 1.0);
}
