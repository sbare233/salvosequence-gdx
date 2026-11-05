// 来源：gleba-libgdx-mvp（蓝色大肥鱼）——Gleba 星球渲染（网格位移 / 大气散射）。
package com.hysx.salvosequence.gleba.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * 单次散射瑞利大气（Rayleigh single scattering）。
 *
 * <p>做法是经典的全屏射线步进：对每个像素从相机发出射线，求它与大气外球、行星球的交点，
 * 在两者之间的可见段上按 {@code sqrt(u)} 采样（靠近地表采样更密），每个采样点再沿太阳方向
 * 积一次光学厚度得到透射率，最后按瑞利相函数加权累加。
 *
 * <p>合成上分成两部分，避免「整颗星球蒙一层灰蓝」：
 * <ul>
 *   <li><b>环带 ring</b>：碰撞参数 b 接近 1 的位置（贴近地平线的一圈 + 球外那圈气体），
 *       散射光全量叠加、alpha 也接近 1 —— 这才是肉眼看到的蓝色大气光晕。</li>
 *   <li><b>空气透视 haze</b>：盘面内部只给很淡的一层（约 20% 强度），
 *       用来交代「这颗星球有大气」，但不会把大陆颜色洗掉。</li>
 * </ul>
 *
 * <p>所有长度都以「行星半径 = 1」为单位，因此散射系数用 {@code β_real × R_planet} 折算：
 * 绿光 ≈ 86 /行星半径，正好对应真实地球 550 nm 处约 0.098 的天顶光学厚度。
 */
public class Atmosphere {

    /** 行星半径（渲染单位，固定为 1） */
    public static final float PLANET_RADIUS = 1.0f;
    /**
     * 大气外球半径。
     *
     * <p>真实地球约 1.028（80 km），但那样在屏幕上只有半径的 2.8%、光晕太薄看不出来；
     * 这里放到 1.055（约 350 km 的视觉当量）让环带有约 5.5% 半径的厚度。
     * 地形位移硬上限只有 0.010，仍远小于这一层，不会穿模。
     */
    public static final float ATMO_RADIUS = 1.100f;

    private static final String VERT =
            "attribute vec2 a_position;\n" +
            "varying vec2 v_ndc;\n" +
            "void main() {\n" +
            "  v_ndc = a_position;\n" +
            "  gl_Position = vec4(a_position, 0.0, 1.0);\n" +
            "}\n";

    private static final String FRAG =
            "varying vec2 v_ndc;\n" +
            "uniform mat4 u_invVP;\n" +
            "uniform vec3 u_camPos;\n" +
            "uniform vec3 u_sunDir;\n" +
            "uniform float u_Rg;\n" +
            "uniform float u_Rt;\n" +
            "uniform float u_sunIntensity;\n" +
            "uniform float u_exposure;\n" +
            "\n" +
            "// 瑞利散射系数（每「行星半径」长度的消光），R 通道最弱、B 通道最强\n" +
            "const vec3 BETA = vec3(5.9, 13.9, 34.1);\n" +
            "// 标高（行星半径单位）。真实地球约 0.0013，这里略厚一点让梯度更柔和\n" +
            "const float SCALE_H = 0.018;\n" +
            "\n" +
            "// 射线与球求交；相机在球外，返回最近与最远交点\n" +
            "bool hitSphere(vec3 o, vec3 d, float r, out float t0, out float t1) {\n" +
            "  float b = dot(o, d);\n" +
            "  float c = dot(o, o) - r * r;\n" +
            "  float disc = b * b - c;\n" +
            "  if (disc < 0.0) return false;\n" +
            "  float s = sqrt(disc);\n" +
            "  t0 = -b - s;\n" +
            "  t1 = -b + s;\n" +
            "  return true;\n" +
            "}\n" +
            "\n" +
            "// 从 p 沿太阳方向到大气顶的光学厚度（8 步）\n" +
            "float sunOpticalDepth(vec3 p) {\n" +
            "  float t0, t1;\n" +
            "  if (!hitSphere(p, u_sunDir, u_Rt, t0, t1)) return 1e9;\n" +
            "  float te = max(t1, 0.0001);\n" +
            "  float sum = 0.0;\n" +
            "  for (int i = 0; i < 8; i++) {\n" +
            "    float f = (float(i) + 0.5) / 8.0;\n" +
            "    vec3 q = p + u_sunDir * (te * f);\n" +
            "    float h = max(length(q) - u_Rg, 0.0);\n" +
            "    sum += exp(-h / SCALE_H) * (te / 8.0);\n" +
            "  }\n" +
            "  return sum;\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "  vec4 pn = u_invVP * vec4(v_ndc, -1.0, 1.0); pn /= pn.w;\n" +
            "  vec4 pf = u_invVP * vec4(v_ndc,  1.0, 1.0); pf /= pf.w;\n" +
            "  vec3 rd = normalize(pf.xyz - pn.xyz);\n" +
            "  vec3 ro = u_camPos;\n" +
            "\n" +
            "  float ta0, ta1;\n" +
            "  if (!hitSphere(ro, rd, u_Rt, ta0, ta1)) { gl_FragColor = vec4(0.0); return; }\n" +
            "  float tStart = max(ta0, 0.0);\n" +
            "  float tEnd = ta1;\n" +
            "\n" +
            "  // 如果视线打到行星，就只积到地表\n" +
            "  float tp0, tp1;\n" +
            "  bool hitPlanet = hitSphere(ro, rd, u_Rg, tp0, tp1) && tp0 > 0.0;\n" +
            "  if (hitPlanet) tEnd = min(tEnd, tp0);\n" +
            "  if (tEnd <= tStart) { gl_FragColor = vec4(0.0); return; }\n" +
            "\n" +
            "  float mu = dot(rd, u_sunDir);\n" +
            "  float phase = 0.0596831 * (1.0 + mu * mu);  // 3/(16π)\n" +
            "\n" +
            "  vec3 sumR = vec3(0.0);\n" +
            "  float odView = 0.0;\n" +
            "  const int STEPS = 24;\n" +
            "  for (int i = 0; i < STEPS; i++) {\n" +
            "    float u0 = float(i) / float(STEPS);\n" +
            "    float u1 = float(i + 1) / float(STEPS);\n" +
            "    float s0 = sqrt(u0);\n" +
            "    float s1 = sqrt(u1);\n" +
            "    float tA = tStart + (tEnd - tStart) * s0;\n" +
            "    float tB = tStart + (tEnd - tStart) * s1;\n" +
            "    float dt = tB - tA;\n" +
            "    vec3 p = ro + rd * (tA + dt * 0.5);\n" +
            "    float h = max(length(p) - u_Rg, 0.0);\n" +
            "    float dens = exp(-h / SCALE_H);\n" +
            "    odView += dens * dt;\n" +
            "    float odSun = sunOpticalDepth(p);\n" +
            "    vec3 trans = exp(-BETA * (odView + odSun));\n" +
            "    sumR += dens * dt * trans;\n" +
            "  }\n" +
            "\n" +
            "  vec3 scatter = sumR * BETA * phase * u_sunIntensity * 0.95;\n" +
            "  scatter = vec3(1.0) - exp(-scatter * u_exposure);\n" +
            "  // 昼夜项：用「所贴住的那个地表点」是否朝向太阳来压暗夜侧光晕，\n" +
            "  // 否则只有 odSun 的软阴影，夜侧边缘仍会发亮\n" +
            "  vec3 ringPoint = ro + rd * (hitPlanet ? tp0 : mix(tStart, tEnd, 0.5));\n" +
            "  float facing = dot(normalize(ringPoint), u_sunDir);\n" +
            "  float dayl = smoothstep(-0.18, 0.30, facing);\n" +
            "\n" +
            "  // 碰撞参数 b = |ro_dir × rd|：盘面中心 ≈ 0，地平线 ≈ 1，球外气体 > 1\n" +
            "  // 碰撞参数 = 相机指向球心的矢量与视线方向的叉积模长（ro 不能归一化，\n" +
            "  // 否则会被相机距离除一次，b 永远到不了 1，环带判定就失效了）\n" +
            "  float b = length(cross(ro, rd));\n" +
            "  // 空气质量：视线穿过的大气厚度。天顶（盘面中心）≈1，地平线附近急剧变厚\n" +
            "  float mu0 = sqrt(max(1.0 - b * b, 0.0));\n" +
            "  float airmass = 1.0 / max(mu0, 0.12);\n" +
            "  // 瑞利内散射的色调：短波段散射得更多，叠加后整体发蓝、发白\n" +
            "  const vec3 SKY_TINT = vec3(0.34, 0.58, 1.00);\n" +
            "  float alpha;\n" +
            "  vec3 col;\n" +
            "  float strength = clamp(u_sunIntensity / 24.0, 0.0, 2.0);\n" +
            "  if (hitPlanet) {\n" +
            "    // 垂直光学厚度 = BETA * SCALE_H，斜视按 airmass 放大\n" +
            "    vec3 tau = BETA * SCALE_H * airmass;\n" +
            "    alpha = clamp(1.0 - exp(-tau.g), 0.0, 1.0) * strength;\n" +
            "    col = min(SKY_TINT * alpha * u_sunIntensity * 0.045, vec3(0.85, 0.92, 1.0));\n" +
            "  } else {\n" +
            "    // 球外那圈气体：用积分出来的真实散射亮度\n" +
            "    alpha = clamp(1.0 - exp(-BETA.g * odView * 1.5), 0.0, 1.0) * strength;\n" +
            "    col = scatter * alpha;\n" +
            "  }\n" +
            "  col *= mix(0.05, 1.0, dayl);\n" +
            "  gl_FragColor = vec4(col, alpha);\n" +
            "}\n";

    private final ShaderProgram shader;
    private final Mesh quad;
    private final Matrix4 invVP = new Matrix4();

    public Atmosphere() {
        ShaderProgram.pedantic = false;
        shader = new ShaderProgram(VERT, FRAG);
        if (!shader.isCompiled()) {
            Gdx.app.error("atmosphere", shader.getLog());
        }
        quad = new Mesh(true, 4, 6,
                new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"));
        quad.setVertices(new float[]{-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f});
        quad.setIndices(new short[]{0, 1, 2, 0, 2, 3});
    }

    /**
     * @param camCombined 相机的 view*projection 矩阵
     * @param camPos      相机世界位置
     * @param sunDir      指向太阳的单位向量
     */
    public void render(Matrix4 camCombined, Vector3 camPos, Vector3 sunDir, float sunIntensity) {
        invVP.set(camCombined).inv();

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        // 预乘 alpha：dst*T + scatter，等价于「地表被大气衰减 + 散射光叠加」
        Gdx.gl.glBlendFuncSeparate(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA,
                GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shader.bind();
        shader.setUniformMatrix("u_invVP", invVP);
        shader.setUniformf("u_camPos", camPos);
        shader.setUniformf("u_sunDir", sunDir);
        shader.setUniformf("u_Rg", PLANET_RADIUS);
        shader.setUniformf("u_Rt", ATMO_RADIUS);
        shader.setUniformf("u_sunIntensity", sunIntensity);
        shader.setUniformf("u_exposure", 1.15f);
        quad.render(shader, GL20.GL_TRIANGLES);

        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    public void dispose() {
        quad.dispose();
        shader.dispose();
    }
}
