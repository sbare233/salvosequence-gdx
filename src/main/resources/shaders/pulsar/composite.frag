#ifdef GL_ES
precision highp float;
#endif

varying vec2 v_uv;

uniform sampler2D u_scene;
uniform sampler2D u_bloom;

uniform float u_bloomStrength;
uniform float u_exposure;

void main() {
    vec3 scene = texture2D(u_scene, v_uv).rgb;
    vec3 bloom = texture2D(u_bloom, v_uv).rgb;

    // 彗尾是加法混合的，本身已经很亮；bloom 只负责让亮处往外溢出，不整体提亮
    vec3 col = (scene + bloom * u_bloomStrength) * u_exposure;

    // 软膝：1.0 以下原样通过，超亮部分（彗核边缘、彗发中心）才被压回来
    col = col / (1.0 + max(col - 1.0, 0.0) * 0.85);
    gl_FragColor = vec4(pow(clamp(col, 0.0, 1.0), vec3(1.02)), 1.0);
}
