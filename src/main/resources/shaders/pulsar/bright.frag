#ifdef GL_ES
precision highp float;
#endif

varying vec2 v_uv;

uniform sampler2D u_scene;
uniform float u_threshold;

void main() {
    vec3 c = texture2D(u_scene, v_uv).rgb;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    float k = max(l - u_threshold, 0.0) / max(l, 1e-4);
    gl_FragColor = vec4(c * k, 1.0);
}
