package com.hysx.salvosequence.gleba;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.hysx.salvosequence.core.Assets;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.gleba.gen.PlanetData;
import com.hysx.salvosequence.gleba.gen.PlanetGenerator;
import com.hysx.salvosequence.gleba.gen.PlanetParams;
import com.hysx.salvosequence.gleba.gen.PlanetTheme;
import com.hysx.salvosequence.gleba.gen.SphereGrid;
import com.hysx.salvosequence.gleba.render.Atmosphere;
import com.hysx.salvosequence.gleba.render.PlanetMesh;
import com.hysx.salvosequence.ui.Language;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Random;

/**
 * Gleba 3D 行星渲染器（自含、可复用）：生成线程 + 地形位移网格 + 瑞利散射大气 +
 * 轨道相机 + 全方位角太阳。GlebaScreen（独立观看）与背景工坊（拜耳实时联动）共用。
 *
 * <p>用法：{@link #ensureGl()} 惰性建 GL 资源 → 每帧 {@link #update(double)} →
 * {@link #renderInto(PixelArt)} 把 3D 视图按最近邻 2 倍贴进设计像素缓冲 →
 * {@link #dispose()} 释放。控制全部走方法（按钮调用），无键位。</p>
 */
public final class GlebaPlanet {

    /** 3D 渲染分辨率（设计缓冲的 1/2，最近邻 2 倍贴回）。 */
    public static final int FBW = 320;
    public static final int FBH = 180;

    /** 球体大小档位（每面边长）：24 ≈ 0.2s，48 ≈ 1.7s，96 ≈ 2.7s 生成。 */
    public static final int[] FACE_RES = {48, 96, 148, 256};

    /** 行星着色（晨昏线/海面高光/菲涅尔），照搬 PlanetApp。 */
    private static final String VERT =
            "attribute vec3 a_position;\n" +
            "attribute vec3 a_normal;\n" +
            "attribute vec4 a_color;\n" +
            "uniform mat4 u_projTrans;\n" +
            "varying vec3 v_normal;\n" +
            "varying vec4 v_color;\n" +
            "varying vec3 v_pos;\n" +
            "void main() {\n" +
            "  v_normal = a_normal;\n" +
            "  v_color = a_color;\n" +
            "  v_pos = a_position;\n" +
            "  gl_Position = u_projTrans * vec4(a_position, 1.0);\n" +
            "}\n";

    private static final String FRAG =
            "uniform float u_dbg;\n" +
            "varying vec3 v_normal;\n" +
            "varying vec4 v_color;\n" +
            "varying vec3 v_pos;\n" +
            "uniform vec3 u_lightDir;\n" +
            "uniform vec3 u_cameraPos;\n" +
            // 巨构层：1 = 巢都、2 = 矿业星，其余类型靠几何体，着色器不动地表
            "uniform float u_build;\n" +
            "uniform vec3 u_mineAxis;\n" +
            "uniform float u_mineRadius;\n" +
            "uniform float u_citySeed;\n" +
            // 巨构投在行星表面的影子：一个有厚度的圆盘/环带 + 一根胶囊。尺寸由 Java 侧按
            // 真实网格给出，解析求交，不设距离窗口——巨构挪多远都还投影。
            "uniform vec3 u_shC;\n" +
            "uniform vec3 u_shN;\n" +
            "uniform vec3 u_shR;\n" +
            "uniform vec4 u_capA;\n" +
            "uniform vec3 u_capB;\n" +
            // 城市层：每个 vec4 = 单位方向 + cos(角半径)。点位在 Java 侧按陆地/植被挑好，
            // 着色器只做"这个像素离哪座城多近"。
            "uniform vec4 u_city[32];\n" +
            "uniform float u_cityN;\n" +
            "uniform float u_cityOn;\n" +
            "uniform float u_nightDbg;\n" +
            "uniform vec4 u_cap;\n" +
            "uniform float u_capOn;\n" +
            // 环带/圆盘：先取射线与"有厚度的盘"两侧盘面的近交点，再看交点是否落在半径区间。
            // 太阳几乎贴着盘面时交点会飞得很远，半径遮罩自己会把它筛掉——之前那个
            // abs(dn) > 0.02 的角度阈值，正是"太阳一贴近盘面投影就整块消失"的根源。
            "float shDiscShadow(vec3 p, vec3 L) {\n" +
            "  if (u_shR.y <= 0.001) {\n" +
            "    return 1.0;\n" +
            "  }\n" +
            "  float dn = dot(u_shN, L);\n" +
            "  if (abs(dn) < 1.0e-4) {\n" +
            "    return 1.0;\n" +
            "  }\n" +
            "  float h = max(u_shR.z, 0.0015);\n" +
            "  float pc = dot(u_shN, u_shC) - dot(u_shN, p);\n" +
            "  float s0 = (pc + h) / dn;\n" +
            "  float s1 = (pc - h) / dn;\n" +
            "  float s = min(s0, s1);\n" +
            "  if (s < 0.0) {\n" +
            "    s = max(s0, s1);\n" +
            "  }\n" +
            "  if (s < 0.0) {\n" +
            "    return 1.0;\n" +
            "  }\n" +
            "  vec3 q = p + L * s - u_shC;\n" +
            "  float r = length(q - u_shN * dot(u_shN, q));\n" +
            "  float soft = 0.015 + 0.030 * s;\n" +
            "  float a = 1.0 - smoothstep(u_shR.y - soft, u_shR.y + soft, r);\n" +
            "  if (u_shR.x > 0.001) {\n" +
            "    a *= smoothstep(u_shR.x - soft, u_shR.x + soft, r);\n" +
            "  }\n" +
            "  return 1.0 - 0.62 * a;\n" +
            "}\n" +
            // 胶囊（线段 + 半径）：射线与线段的最短距离小于半径就算遮住。距离不设上限，
            // 所以电梯拉到同步轨道、反应堆挂在 L1，都还照样往地表落一道影。
            "float shCapsuleShadow(vec3 p, vec3 L) {\n" +
            "  if (u_capA.w <= 0.001) {\n" +
            "    return 1.0;\n" +
            "  }\n" +
            "  vec3 u = u_capB - u_capA.xyz;\n" +
            "  vec3 w = p - u_capA.xyz;\n" +
            "  float uu = dot(u, u);\n" +
            "  if (uu < 1.0e-8) {\n" +
            "    return 1.0;\n" +
            "  }\n" +
            "  float uv = dot(u, L);\n" +
            "  float uw = dot(u, w);\n" +
            "  float vw = dot(L, w);\n" +
            "  float den = uu - uv * uv;\n" +
            "  float t = 0.0;\n" +
            "  if (den > 1.0e-8) {\n" +
            "    t = (uw - vw * uv) / den;\n" +
            "  }\n" +
            "  t = clamp(t, 0.0, 1.0);\n" +
            "  float s = max(t * uv - vw, 0.0);\n" +
            "  t = clamp((uw + s * uv) / uu, 0.0, 1.0);\n" +
            "  float d = length(w + L * s - u * t);\n" +
            "  float soft = 0.010 + 0.012 * s;\n" +
            "  float a = 1.0 - smoothstep(u_capA.w - soft, u_capA.w + soft, d);\n" +
            "  return 1.0 - 0.62 * a;\n" +
            "}\n" +
            "float structureShadow(vec3 p, vec3 L) {\n" +
            "  return min(shDiscShadow(p, L), shCapsuleShadow(p, L));\n" +
            "}\n" +
            "float h21(vec2 p) {\n" +
            "  p = fract(p * vec2(123.34, 456.21));\n" +
            "  p += dot(p, p + 45.32);\n" +
            "  return fract(p.x * p.y);\n" +
            "}\n" +
            // 城市：白天是混凝土街区（路网压暗），夜里是暖色窗光。q 是城市切平面里的
            // 归一化坐标（半径 = 1），所以一座城在屏幕上多大由 cR 决定，跟像素密度无关。
            "vec4 cityShade(vec3 n, vec3 dir, float rad, float id, float lit, float ndl) {\n" +
            "  vec3 off = n - dir * dot(n, dir);\n" +
            "  vec3 up0 = abs(dir.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);\n" +
            "  vec3 t1 = normalize(cross(up0, dir));\n" +
            "  vec3 t2 = cross(dir, t1);\n" +
            "  float rr = max(0.0001, rad);\n" +
            "  vec2 q = vec2(dot(off, t1), dot(off, t2)) / rr;\n" +
            "  float ang = atan(q.y, q.x);\n" +
            // 轮廓：三层不同频率的正弦叠起来，圆边被推成波浪形（相位由城市编号决定，
            // 同一座城每帧一样；不同城形状互不相同）。正圆在几个像素的尺度上太假了。
            "  float wv = 0.24 * sin(ang * 3.0 + id * 2.1)\n" +
            "           + 0.13 * sin(ang * 7.0 + id * 5.3)\n" +
            "           + 0.07 * sin(ang * 13.0 + id * 1.7);\n" +
            "  float rn = length(q) / (1.0 + wv);\n" +
            "  if (rn > 1.0) return vec4(0.0);\n" +
            // 城内就是一片灰噪点：一级是逐像素的碎点（建筑/屋顶），一级是大块的片区
            // 色调（新旧城区）。不再画方格路网——缩到几个像素后那就是一张贴纸。
            "  vec2 nq = q * 8.0 + id * 13.0;\n" +
            "  float n1 = h21(floor(nq));\n" +
            "  float n2 = h21(floor(q * 2.6 + id * 4.0));\n" +
            "  vec3 conc = mix(vec3(0.360, 0.356, 0.346), vec3(0.780, 0.762, 0.730),\n" +
            "                  clamp(n2 * 0.55 + n1 * 0.45, 0.0, 1.0));\n" +
            "  conc *= 1.0 - 0.38 * step(0.78, n1);\n" +
            // 边缘不封死：越靠外越淡，城市散进地表里，不会像剪出来的圆片
            "  float fall = smoothstep(1.0, 0.55, rn);\n" +
            "  vec3 dayC = conc * (0.64 + 0.54 * max(ndl, 0.0));\n" +
            // 夜里：一部分像素点亮成暖窗，密度跟着片区走（新区亮、老区暗）
            "  float hw = h21(floor(nq * 0.8) + 5.0);\n" +
            "  float on = step(0.52, hw) * (0.35 + 0.65 * step(0.35, n2));\n" +
            "  vec3 lite = mix(vec3(0.030, 0.033, 0.046), vec3(0.980, 0.720, 0.380), on);\n" +
            "  lite *= 0.55 + 0.75 * fall;\n" +
            "  lite += vec3(0.32, 0.21, 0.10) * fall * 0.30;\n" +
            "  return vec4(mix(lite, dayC, lit), fall * (0.88 + 0.12 * n1));\n" +
            "}\n" +
            // 首都：同一切平面坐标，但街区更密、外面套三圈环路，夜里中间一团亮
            "vec4 capShade(vec3 n, vec3 dir, float rad, float lit, float ndl) {\n" +
            "  vec3 off = n - dir * dot(n, dir);\n" +
            "  vec3 up0 = abs(dir.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);\n" +
            "  vec3 t1 = normalize(cross(up0, dir));\n" +
            "  vec3 t2 = cross(dir, t1);\n" +
            "  float rr = max(0.0001, rad);\n" +
            "  vec2 q = vec2(dot(off, t1), dot(off, t2)) / rr;\n" +
            "  float ang = atan(q.y, q.x);\n" +
            // 首都也走波浪轮廓，只是振幅小一点、更接近团块
            "  float wv = 0.16 * sin(ang * 2.0 + 1.3)\n" +
            "           + 0.09 * sin(ang * 5.0 + 4.1)\n" +
            "           + 0.05 * sin(ang * 11.0 + 2.7);\n" +
            "  float rn = length(q) / (1.0 + wv);\n" +
            "  if (rn > 1.0) return vec4(0.0);\n" +
            // 首都的灰噪点更密（频率高一级、暗点少一些），像一片连片的城区
            "  vec2 nq = q * 11.0 + 3.7;\n" +
            "  float n1 = h21(floor(nq));\n" +
            "  float n2 = h21(floor(q * 3.4 + 1.9));\n" +
            "  vec3 conc = mix(vec3(0.360, 0.356, 0.346), vec3(0.760, 0.740, 0.700),\n" +
            "                  clamp(n2 * 0.55 + n1 * 0.45, 0.0, 1.0));\n" +
            "  conc *= 1.0 - 0.30 * step(0.84, n1);\n" +
            // 两道环路：柔和压暗，不是几何正圆
            "  float ring = smoothstep(0.055, 0.014, abs(rn - 0.46))\n" +
            "             + smoothstep(0.055, 0.014, abs(rn - 0.80));\n" +
            "  conc *= 1.0 - 0.35 * clamp(ring, 0.0, 1.0);\n" +
            "  float fall = smoothstep(1.0, 0.70, rn);\n" +
            "  vec3 dayC = conc * (0.66 + 0.52 * max(ndl, 0.0));\n" +
            "  float hw = h21(floor(nq * 0.75) + 9.0);\n" +
            "  float on = step(0.46, hw) * (0.40 + 0.60 * step(0.28, n2));\n" +
            "  vec3 lite = mix(vec3(0.035, 0.038, 0.052), vec3(1.000, 0.760, 0.400), on);\n" +
            "  lite *= 0.60 + 0.70 * fall;\n" +
            "  lite += vec3(1.0, 0.80, 0.48) * smoothstep(0.34, 0.06, rn) * 0.42;\n" +
            "  return vec4(mix(lite, dayC, lit), fall * (0.94 + 0.06 * n1));\n" +
            "}\n" +
            "void main() {\n" +
            "  vec3 n = normalize(v_pos);\n" +
            "  vec3 L = normalize(u_lightDir);\n" +
            "  vec3 viewDir = normalize(u_cameraPos - v_pos);\n" +
            "  vec3 base = v_color.rgb * 0.90;\n" +
            "  float water = clamp((base.b - base.r) * 3.5, 0.0, 1.0);\n" +
            "  float ndl = dot(n, L);\n" +
            "  float lit = smoothstep(-0.10, 0.34, ndl) * u_nightDbg;\n" +
            "  vec3 day = base * (0.70 + 0.58 * max(ndl, 0.0));\n" +
            "  vec3 night = base * 0.10 + vec3(0.010, 0.016, 0.034);\n" +
            "  vec3 col = mix(night, day, lit);\n" +
            "  vec3 halfV = normalize(L + viewDir);\n" +
            "  float spec = pow(max(dot(n, halfV), 0.0), 48.0);\n" +
            "  col += vec3(0.85, 0.93, 1.0) * spec * water * lit * 0.50;\n" +
            "  float fres = pow(1.0 - max(dot(n, viewDir), 0.0), 3.0);\n" +
            "  col += vec3(0.18, 0.38, 0.72) * fres * water * lit * 0.22;\n" +
            // ---- 矿业星：以一个轴向的大圆坑挖穿地壳，坑壁是一圈圈开采台阶，
            //      坑底裸露地核（自发光），坑口有一圈被翻出来的亮边。
            "  if (u_build > 1.5 && u_build < 2.5) {\n" +
            "    float ang = acos(clamp(dot(n, u_mineAxis), -1.0, 1.0));\n" +
            // 坑口的局部正交基、以及坑沿那圈低频起伏，必须和 Java 侧 mineCarve 用的**完全一致**：
            // 只要两边差一点，坑边就会露出一圈"只染了色、没挖下去"的平面——坑转到球面边缘时
            // 那圈平面被拉长，看着就像矿坑被原来的地表盖住了
            "    vec3 mup = abs(u_mineAxis.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);\n" +
            "    vec3 mu = normalize(cross(mup, u_mineAxis));\n" +
            "    vec3 mv = cross(u_mineAxis, mu);\n" +
            "    float phi = atan(dot(n, mv), dot(n, mu));\n" +
            "    float t = ang / u_mineRadius;\n" +
            "    t *= 1.0 + 0.17 * sin(3.0 * phi + 0.7) + 0.09 * sin(7.0 * phi + 2.1);\n" +
            "    if (t < 1.0) {\n" +
            "      vec3 outer = col;\n" +
            "      float night = 1.0 - lit;\n" +
            // 台阶已经由网格几何挖出来了，这里只做"越深越暗"的明度梯度，
            // 不再画同心色环——色环叠在几何台阶上会读成一顶婚礼蛋糕
            "      float depth = smoothstep(0.0, 1.0, 1.0 - t);\n" +
            // 坑壁的朝向：网格法线是按格子中心方向给的（挖坑不改法线），照球的法线打光，
            // 碗形凹坑会读成一张平贴图。这里按"越靠坑沿、越朝坑心倾斜"造一个解析法线，
            // 向阳那侧坑壁亮、背阳那侧暗，才看得出是个碗
            "      float wall = (1.0 - smoothstep(0.92, 1.0, t)) * smoothstep(0.15, 0.70, t);\n" +
            "      vec3 wn = normalize(n - u_mineAxis * (1.20 * wall));\n" +
            "      float wdl = max(dot(wn, L), 0.0);\n" +
            "      vec3 rock = mix(vec3(0.34, 0.29, 0.245), vec3(0.125, 0.110, 0.098), depth);\n" +
            // 坑壁的漫反射系数比地表低得多，纯 Lambert 一背光就掉成纯黑——整颗星球会读成
            // 一块黑斑（用户说的"黑屏"）。这里把环境项垫高，暗面也留 0.55 的底色
            "      rock *= 0.58 + 0.62 * wdl;\n" +
            "      rock += vec3(0.045, 0.040, 0.036) * (1.0 - lit);\n" +
            // 每圈台阶的外沿提一点亮：几何挖出的台阶没有法线差，靠这条细棱才读得出层数
            "      float benchEdge = smoothstep(0.88, 0.99, fract(t * 4.0)) * (1.0 - depth * 0.45);\n" +
            "      rock += vec3(0.19, 0.165, 0.13) * benchEdge * (0.30 + 0.60 * lit);\n" +
            // 顺坡而下的运输道：三条细暗线，夜面缀运矿灯
            "      float road = smoothstep(0.985, 1.0, abs(sin(phi * 3.0)));\n" +
            "      rock *= 1.0 - 0.30 * road * (0.40 + 0.60 * t);\n" +
            "      rock += vec3(1.0, 0.55, 0.22) * road * (0.45 * (0.15 + 0.85 * night));\n" +
            // 坑底：地核裸露，熔融金属自带亮度
            "      float core = smoothstep(0.30, 0.05, t);\n" +
            "      float vein = h21(floor(vec2(atan(n.z, n.x) * 26.0, n.y * 40.0)));\n" +
            "      vec3 magma = mix(vec3(1.0, 0.20, 0.03), vec3(1.0, 0.78, 0.36), vein);\n" +
            "      col = mix(rock, magma, core * 0.95);\n" +
            "      col += vec3(1.00, 0.36, 0.08) * core * 0.60;\n" +
            // 坑口翻出来的亮色岩屑：一条细环，不盖住整圈台阶
            "      float rim = smoothstep(0.80, 0.93, t) * (1.0 - smoothstep(0.93, 1.0, t));\n" +
            "      col = mix(col, vec3(0.46, 0.40, 0.31) * (0.55 + 0.70 * lit), rim * 0.55);\n" +
            // 染色在坑沿收一点：网格是按格子中心挖的，多边形边界和这里的正圆差半个格子，
            // 不收就会在坑外留一圈"只染色没挖下去"的平环，坑转到球面边缘时特别明显
            "      col = mix(outer, col, smoothstep(1.0, 0.90, t));\n" +
            "    }\n" +
            // ---- 巢都：地表压成金属板，夜面铺满琥珀灯火，经纬主干道是暗红
            "  } else if (u_build > 0.5 && u_build < 1.5) {\n" +
            "    vec2 sc = vec2(atan(n.z, n.x), asin(clamp(n.y, -1.0, 1.0)));\n" +
            "    float night = 1.0 - lit;\n" +
            // 把海洋和陆地一起压成一块金属底板：巢都星球上没有海，只有楼顶
            "    float lum = dot(col, vec3(0.30, 0.45, 0.25));\n" +
            "    vec3 deck = mix(vec3(0.10, 0.10, 0.105), vec3(0.36, 0.35, 0.32),\n" +
            "                    clamp(lum * 1.1, 0.0, 1.0));\n" +
            "    vec2 g = sc * vec2(30.0, 15.0) + u_citySeed;\n" +
            "    vec2 cell = floor(g);\n" +
            "    vec2 f = fract(g) - 0.5;\n" +
            "    float h = h21(cell + u_citySeed);\n" +
            "    float h2 = h21(cell * 1.7 + u_citySeed + 11.3);\n" +
            // 市中心更高：两条低频波叠出一条"高密度带"
            "    float downtown = 0.55 + 0.45 * sin(sc.x * 2.0 + h2 * 6.28) * cos(sc.y * 1.5);\n" +
            // 楼块内缩，街道留在外面；街比楼暗一档
            "    float inside = step(abs(f.x), 0.36) * step(abs(f.y), 0.32);\n" +
            "    float blockH = mix(0.30, 1.0, h) * downtown;\n" +
            "    vec3 city = deck * (0.70 + 1.10 * blockH) * mix(0.35, 1.0, inside);\n" +
            "    city = mix(city, vec3(0.10, 0.095, 0.09), (1.0 - inside) * 0.85);\n" +
            // 昼夜：白天的楼顶是灰的，夜里只剩窗光——不加这一项整颗星球会像一张平涂贴图
            "    city *= 0.16 + 1.35 * lit;\n" +
            // 窗光：楼块内部随机点亮，夜面亮得多
            "    float win = inside * step(0.60, h2) * (0.10 + 1.60 * night);\n" +
            "    city += vec3(1.0, 0.62, 0.26) * win * 0.55;\n" +
            // 纬向主干道：六条发亮的环带，夜面是灯带、白面是浅色路桥
            "    float avenue = smoothstep(0.96, 1.0, abs(sin(sc.y * 6.0)));\n" +
            "    city += mix(vec3(0.16, 0.15, 0.14), vec3(1.0, 0.72, 0.34), night)\n" +
            "            * avenue * (0.35 + 0.90 * night) * (0.25 + 0.85 * lit);\n" +
            "    col = mix(col, city, 0.94);\n" +
            // ---- 大气调节网：整颗星球罩一层六边形节点，逐格调温。
            //      白天是哑光金属壳，夜面一格一格亮起，边棱是冷白的发光缝
            //      （历史原因：这是一串最近加上的巨构，编号见 PlanetBuild）
            "  } else if (u_build > 6.5 && u_build < 7.5) {\n" +
            "    vec2 sc = vec2(atan(n.z, n.x), asin(clamp(n.y, -1.0, 1.0)));\n" +
            "    vec2 hp = sc * vec2(17.0, 9.5) + u_citySeed;\n" +
            "    vec2 hr = vec2(1.0, 1.7320508);\n" +
            "    vec2 hh = hr * 0.5;\n" +
            "    vec2 qa = mod(hp, hr) - hh;\n" +
            "    vec2 qb = mod(hp - hh, hr) - hh;\n" +
            "    vec2 gv = dot(qa, qa) < dot(qb, qb) ? qa : qb;\n" +
            "    vec2 cell = floor(hp - gv);\n" +
            "    float cid = h21(cell + u_citySeed);\n" +
            "    float cid2 = h21(cell * 1.7 + u_citySeed + 5.3);\n" +
            // 六边形边：到格心的距离接近 0.5 的那一圈
            "    float edge = smoothstep(0.40, 0.50, length(gv));\n" +
            "    float night = 1.0 - lit;\n" +
            "    vec3 shell = mix(vec3(0.26, 0.29, 0.32), vec3(0.52, 0.55, 0.56), cid);\n" +
            "    shell *= 0.32 + 0.92 * lit;\n" +
            "    vec3 glow = mix(vec3(0.36, 0.86, 1.0), vec3(0.62, 1.0, 0.86), cid2);\n" +
            // 节点：格心一小块亮斑，夜里更亮
            "    float node = smoothstep(0.22, 0.0, length(gv));\n" +
            "    col = mix(shell, glow * (0.55 + 0.85 * cid2), edge * (0.55 + 1.00 * night));\n" +
            "    col += glow * node * (0.45 + 0.95 * night) * (0.55 + 0.45 * cid);\n" +
            "  }\n" +
            // 城市层：压在巨构影子之前，所以城市也会被巨构投下来的影子盖住。
            // 水面（water 高）上不画城——点位本来就挑在陆地上，这里只是兜一层海岸线。
            "  if (u_cityOn > 0.5) {\n" +
            "    float wet = smoothstep(0.18, 0.62, water);\n" +
            "    if (wet < 0.99) {\n" +
            "      for (int i = 0; i < 32; i++) {\n" +
            "        if (float(i) >= u_cityN) break;\n" +
            "        vec4 cd = u_city[i];\n" +
            // 用弦长而不是 dot 比较：城市半径只有 0.04~0.07 弧度，dot 在 1 附近的有效位
            // 全被吃掉（0.9983 和 0.9996 差不了几个 ulp），弦长是 0.04 量级的量，精度够。
            "        vec3 dv = n - cd.xyz;\n" +
            "        if (dot(dv, dv) > cd.w * cd.w) continue;\n" +
            "        vec4 cc = cityShade(n, cd.xyz, cd.w, float(i) * 0.731, lit, ndl);\n" +
            "        col = mix(col, cc.rgb, cc.a * (1.0 - wet));\n" +
            "      }\n" +
            "      if (u_capOn > 0.5) {\n" +
            "        vec3 dvc = n - u_cap.xyz;\n" +
            "        if (dot(dvc, dvc) <= u_cap.w * u_cap.w) {\n" +
            "          vec4 cp = capShade(n, u_cap.xyz, u_cap.w, lit, ndl);\n" +
            "          col = mix(col, cp.rgb, cp.a * (1.0 - wet));\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            // 巨构的影子：只压漫反射，留一点环境光，否则影子会像一块抠掉的黑洞
            "  col *= 0.14 + 0.86 * structureShadow(n, L);\n" +
            "  gl_FragColor = vec4(col, 1.0);\n" +
            "}\n";

    public final PlanetParams params = new PlanetParams();

    /** 矿业星坑口的中轴（单位向量）：由种子决定，换一颗星球坑就开在别的方向。 */
    private final Vector3 mineAxis = new Vector3(0.36f, 0.58f, 0.73f).nor();
    /** 坑口角半径（弧度）：0.92 ≈ 挖掉 19% 的球面，看着才像"挖穿了地壳"。 */
    private float mineRadius = 1.24f;

    // ------------------------------------------------------------------ 城市
    /** 城市点位上限：着色器里是 `uniform vec4 u_city[32]`，一个 vec4 = 方向 + cos(角半径)。 */
    public static final int MAX_CITIES = 32;
    /** 城市点位池（x,y,z 单位方向，w = cos(角半径)）：换星球时重建一次。 */
    private final float[] cityPool = new float[MAX_CITIES * 4];
    /** `u_city[i]` 的 uniform 名缓存：逐座上传，不用 setUniform4fv 的批量语义（容易踩坑）。 */
    private static final String[] CITY_UNIFORM = new String[MAX_CITIES];
    static {
        for (int i = 0; i < MAX_CITIES; i++) {
            CITY_UNIFORM[i] = "u_city[" + i + "]";
        }
    }
    private int cityPoolN;
    /** 这颗星球本身允不允许有城市（要有绿色植被、且地表没被巨构改写）。 */
    private boolean cityAllowed;
    /** 这颗星球的基准城市数（10–30，种子决定）；电梯/星门再多加几座。 */
    private int cityBase;
    /** 首都：只有城市够多时才出现，位置挑植被最好的那块陆地。 */
    private final float[] capVec = new float[4];
    private boolean capAllowed;
    private int cityCount;

    /** 黑洞天空（GargantuaSky，远场 raymarch），画在行星之前——光照互动的第二光源。 */
    public final com.hysx.salvosequence.celestial.GargantuaSky sky =
            new com.hysx.salvosequence.celestial.GargantuaSky();
    /** 吸积盘亮度（作用于黑洞天空本身）。 */
    private float skyBrightness = 1.0f;
    /** 吸积盘对行星的橙光照度（第二光源）。 */
    private float diskGlow = 0.45f;
    /**
     * 要不要在这颗行星背后画黑洞天空。行星场景默认关掉——黑洞已经是独立的背景场景，
     * 两者挤在同一张图里会分不清看的是哪个天体。
     */
    public boolean showSky = true;

    private ShaderProgram shader;
    private Atmosphere atmo;
    private FrameBuffer fbo;
    private PerspectiveCamera cam;
    private com.hysx.salvosequence.gleba.render.HexMesh.Cells mesh;
    /** 当前网格对应的地表数据：只换巨构类型时直接拿它重建几何，不重跑生成器。 */
    private PlanetData lastData;
    private long lastSeed = Long.MIN_VALUE;
    private PlanetTheme lastTheme;
    private int lastFaceRes = -1;
    /** 当前网格是按哪种巨构挖的（-1 = 还没有网格）。 */
    private int builtBuild = -1;
    /** 正在生成的那一轮几何是按哪个巨构类型挖的（-1 = 没有在生成）。 */
    private int pendingBuild = -1;
    /** 排查"黑屏"用：没有网格可画时只打印一次。 */
    private boolean diagNoMesh;
    /** 网格丢失后的自动重建次数（限 3 次，别变成死循环）。 */
    private int diagRetry;
    /** 气态行星的球面（非气态时为 null）。 */
    private com.hysx.salvosequence.gleba.render.GasGiant gasMesh;

    /** 太阳方向：方位角 + 高度角（全方位角）。默认对准 PlanetApp 原版光照。 */
    private float sunAzimuthDeg = 123f;
    private float sunElevDeg = 17f;
    private Vector3 sunDir = computeSun();

    /** 机位角度上限（弧度，60°）：轨道面要包含当前机位，俯仰再高轨道面就贴到极点上了。 */
    private static final float MAX_PITCH = 1.05f;

    private float yaw = 0.7f, pitch = 0.32f, dist = 3.6f;
    /** 轨道角速度（弧度/秒）：镜头沿一条倾斜圆轨道绕行星走，行星本体一动不动。 */
    private float orbitSpeed = 0.01f;
    /** 当前轨道相位（弧度）：0 = 预设机位。 */
    private float orbitPhase;
    /** 轨道基：相位 0 的机位方向、沿轨切向、轨道面法线（单位向量）。 */
    private final Vector3 orbitDir = new Vector3(0.643f, 0.315f, 0.7f).nor();
    private final Vector3 orbitTangent = new Vector3(1f, 0f, 0f);
    private final Vector3 orbitNormal = new Vector3(0f, 1f, 0f);
    /** 这一帧的相机方向（行星中心 → 相机，单位向量）：轨道相位在这里换算成角度。 */
    private final Vector3 viewDir = new Vector3(0.643f, 0.315f, 0.7f).nor();
    /** 连续方位角/俯仰（度）：给存档和星空视差用，避免 ±180° 回绕处背景跳一下。 */
    private float viewYawDeg = 40f;
    private float viewPitchDeg = 18f;
    private float orbitLastX = -1, orbitLastY = -1;

    public GlebaPlanet() {
        rebuildOrbit();
    }

    private volatile boolean generating;
    private volatile boolean gasDirty;
    private PlanetData pending;
    /** 后台线程算好的六边形几何，等渲染线程做一次上传。 */
    private com.hysx.salvosequence.gleba.render.HexMesh.Geo pendingGeo;
    private long pendingMs = -1;
    private int faceResIdx;
    private long genMs;
    private int tiles;

    private double time;

    // ------------------------------------------------------------ 星环

    /** 星环（移植自 gleba-libgdx-mvp）：内径、外径、浓度、环面倾角。 */
    public boolean rings = true;
    public float ringInner = 1.34f;
    public float ringOuter = 2.20f;
    public float ringOpacity = 0.46f;
    public float ringTilt = 0f;
    /** 环系剖面：0 土星式主环 / 1 细亮丝 / 2 双环 / 3 宽尘环 / 4 断续弧环。 */
    public int ringStyle = 0;
    /**
     * 环系种子：剖面骨架不变，带的位置/宽窄/亮度/细纹相位都随它变。
     * 每场战斗掷一次，0 表示用该剖面最标准的那条（主题默认）。
     */
    public float ringSeed = 0f;
    /** 细丝/弧环的条数（2..4），只有 1 号剖面用得到。 */
    public int ringBands = 3;

    private com.hysx.salvosequence.gleba.render.Rings ringMesh;
    private com.hysx.salvosequence.gleba.render.Clouds cloudMesh;
    /** 巨构（轨道加速器 / 太空电梯）：真网格，伸到大气层外，第一帧才建。 */
    private com.hysx.salvosequence.gleba.render.Megastructures megas;

    /** 云层：实拍卫星云图的球壳（等距圆柱，加载一次，不再按星球种子重算）。 */
    public boolean clouds = !Boolean.getBoolean("salvo.noclouds");
    public float cloudAmount = 0.62f;
    public float cloudHue = 0f;
    public float cloudBright = 1f;
    public float cloudContrast = 1f;
    /** 大气厚度倍率（0 = 真空，不画大气；1 = 默认；1.4+ = 厚壳）。每颗星球随机。 */
    public float atmoThickness = 1f;

    /** 星球类型：大陆占比 / 温度 / 降水 / 配色 / 云量 / 星环都从预设来。 */
    public com.hysx.salvosequence.gleba.gen.PlanetTheme theme =
            com.hysx.salvosequence.gleba.gen.PlanetTheme.EARTH;

    // ------------------------------------------------------------ 帧驱动

    /**
     * 挑城市点位。规则（对应需求）：只在有绿色植被的类地星球上出现；比例由种子定，
     * 一颗星球 10–30 座；只能落在陆地上（不进水、不落雪、不落极地）；点位之间保持
     * 最小角距，免得挤成一坨；城市够多时挑植被最好的一块陆地做圆形首都。
     *
     * <p>点位在 Java 侧算好、只把方向传给着色器——着色器里每像素只需判断"离哪座城
     * 多近"，不必重复做地表分类。</p>
     */
    private void buildCities(PlanetData d) {
        cityPoolN = 0;
        cityAllowed = false;
        capAllowed = false;
        cityBase = 0;
        if (d == null || d.theme == null || d.theme.gas) return;
        PlanetTheme th = d.theme;
        // 绿色植被：类地/海洋/紫晶三种才成片长城市，其余星球（荒漠、冰封、岩质、
        // 熔岩）连水都没有几滴，不点城市——这就是"有一定几率"的那道比例闸门。
        boolean verdant = th == PlanetTheme.EARTH || th == PlanetTheme.OCEAN
                || th == PlanetTheme.VIOLET;
        if (!verdant) return;

        int n = d.n;
        int[] cand = new int[n];
        int nc = 0;
        for (int t = 0; t < n; t++) {
            if (!d.land[t]) continue;               // 城市不下水
            if (d.snow[t] > 0.35f) continue;        // 冰盖上不建城
            float tc = d.tempC[t];
            if (tc < 1f || tc > 36f) continue;      // 太冷太热都不适合
            if (d.rainMm[t] < 320f) continue;       // 干旱带长不出绿色植被
            cand[nc++] = t;
        }
        if (nc < 8) return;                          // 陆地太少：这颗星球没有城市

        // 打乱候选，再贪心撒点：每个新点都要离已有点足够远
        Random rnd = new Random(params.seed * 2654435761L + 12345L);
        for (int i = nc - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = cand[i];
            cand[i] = cand[j];
            cand[j] = tmp;
        }

        // 基准 10–22 座（种子定），电梯/星门是交通枢纽再 +8 → 18–30 座
        int want = 10 + (int) Math.floor(hash01(params.seed * 31L + 7L) * 13.0);
        cityBase = want;
        int target = Math.min(MAX_CITIES, want + 8);

        SphereGrid g = d.grid;
        float[] sep = { 0.46f, 0.36f, 0.28f, 0.20f };
        int placed = 0;
        float[] px = cityPool;
        int bestCap = -1;
        float bestScore = -1f;
        for (int pass = 0; pass < sep.length && placed < target; pass++) {
            float cosSep = (float) Math.cos(sep[pass]);
            for (int i = 0; i < nc && placed < target; i++) {
                int t = cand[i];
                float x = g.px[t], y = g.py[t], z = g.pz[t];
                boolean ok = true;
                for (int k = 0; k < placed; k++) {
                    float dx = px[k * 4] - x, dy = px[k * 4 + 1] - y, dz = px[k * 4 + 2] - z;
                    if (dx * dx + dy * dy + dz * dz < (2f - 2f * cosSep)) {
                        ok = false;
                        break;
                    }
                }
                if (!ok) continue;
                float score = cityScore(d, t);
                if (score > bestScore) {
                    bestScore = score;
                    bestCap = placed;
                }
                float radius = 0.052f + 0.030f * hash01(t * 17L + 3L);
                px[placed * 4] = x;
                px[placed * 4 + 1] = y;
                px[placed * 4 + 2] = z;
                // 存弦长 2sin(r/2) 而不是 cos(r)：0.04 量级的数在着色器里精度才够
                px[placed * 4 + 3] = 2f * (float) Math.sin(radius * 0.5f);
                placed++;
            }
        }
        if (placed == 0) return;
        cityPoolN = placed;
        cityAllowed = true;

        // 城市够多（≥22 座）才配一个圆形首都：挑植被/气候最好的那块陆地，半径给到
        // 普通城市的 3 倍左右，着色器里画成同心环路
        if (want >= 18 && bestCap >= 0) {
            capVec[0] = px[bestCap * 4];
            capVec[1] = px[bestCap * 4 + 1];
            capVec[2] = px[bestCap * 4 + 2];
            capVec[3] = 2f * (float) Math.sin(0.105f * 0.5f);
            capAllowed = true;
        }
        if (Boolean.getBoolean("salvo.citydbg")) {
            System.out.printf("[city] theme=%s base=%d placed=%d capital=%s%n",
                    th, cityBase, cityPoolN, capAllowed ? "yes" : "no");
        }
    }

    /** 城市选址打分：温和多雨的地方最像"绿地方"，极端冷热/干燥都扣分。 */
    private static float cityScore(PlanetData d, int t) {
        float tc = d.tempC[t];
        float rain = Math.min(1f, d.rainMm[t] / 1400f);
        float mild = 1f - Math.min(1f, Math.abs(tc - 16f) / 26f);
        float lowland = 1f - Math.min(1f, Math.max(0f, d.elevation[t]) / 3200f);
        return mild * 0.55f + rain * 0.30f + lowland * 0.15f;
    }

    /** 与种子配合的确定性散列（0..1），不依赖 libGDX。 */
    private static float hash01(long x) {
        long h = x * 6364136223846793005L + 1442695040888963407L;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return (float) ((h >>> 11) & 0x1FFFFF) / (float) 0x1FFFFF;
    }

    public void update(double dt) {
        time += dt;
        // 轨道相机：行星和星环在世界坐标里完全静止，动的只有镜头。
        // 镜头沿一条过行星中心、带倾角的圆轨道走（见 rebuildOrbit），俯仰是轨道位置的
        // 函数，不再是一条独立的摇摆曲线——以前那种"方位角一直加 + 俯仰按正弦摆"的
        // 两条曲线周期不同，走出来的其实是条拧着的螺线，看着像星球/星环在原地晃。
        // 截图/对比时用 -Dsalvo.nospin=true 钉死相位。
        if (!Boolean.getBoolean("salvo.nospin")) {
            orbitPhase += dt * orbitSpeed;
        }
        syncView();
        if (gasDirty) {
            gasDirty = false;
            if (mesh != null) {
                mesh.dispose();
                mesh = null;
            }
            if (gasMesh != null) {
                gasMesh.dispose();
                gasMesh = null;
            }
            gasMesh = com.hysx.salvosequence.gleba.render.GasGiant.build(params.theme);
            tiles = 0;
            lastLand = 0;
            genMs = 0;
        }
        if (pending != null) {
            PlanetData d = pending;
            pending = null;
            generating = false;
            genMs = pendingMs;
            tiles = d.n;
            lastLand = d.landCount();
            // 记住这份地表数据：之后只换巨构类型时靠它重建几何，不必重跑生成器
            lastData = d;
            lastSeed = params.seed;
            lastTheme = params.theme;
            lastFaceRes = params.faceRes;
            // 记这一轮几何真正的巨构类型，而不是当前 params.build（生成期间可能已经改了）
            builtBuild = pendingBuild >= 0 ? pendingBuild : params.build;
            pendingBuild = -1;
            buildCities(d);
            // 先建新的、再拆旧的：换巨构时如果先把 mesh 置空，这一帧就没东西可画 = 黑屏。
            // 新网格建好（几十毫秒）才 dispose 旧网格，屏幕上永远是上一颗星球。
            if (d.theme != null && d.theme.gas) {
                // 气态行星不建地形网格：换成一张高密度经纬球，色带由 GasBands 逐顶点算
                com.hysx.salvosequence.gleba.render.GasGiant freshGas =
                        com.hysx.salvosequence.gleba.render.GasGiant.build(d.theme);
                if (mesh != null) {
                    mesh.dispose();
                    mesh = null;
                }
                if (gasMesh != null) {
                    gasMesh.dispose();
                }
                gasMesh = freshGas;
            } else {
                // 六边形球面网格（Gleba 那条路）：生成按 faceRes 最小档，渲染按 meshFreq 另建，
                // 所以大陆是大块像素、球面却是二十面体细分的六边形拼面（-Dsalvo.hex=N 覆盖）
                com.hysx.salvosequence.gleba.render.HexMesh.Cells fresh;
                if (pendingGeo != null) {
                    long t0 = System.nanoTime();
                    fresh = com.hysx.salvosequence.gleba.render.HexMesh.upload(pendingGeo);
                    pendingGeo = null;
                    if (Boolean.getBoolean("salvo.profile")) {
                        System.out.printf("[gleba] 六边形上传 %d ms%n", (System.nanoTime() - t0) / 1_000_000);
                    }
                } else {
                    int freq = Integer.getInteger("salvo.hex", params.meshFreq);
                    fresh = com.hysx.salvosequence.gleba.render.HexMesh.build(d, 0f, true, freq,
                            mineCarve(params.build));
                }
                if (mesh != null) {
                    mesh.dispose();
                }
                if (gasMesh != null) {
                    gasMesh.dispose();
                    gasMesh = null;
                }
                mesh = fresh;
            }
        }
        // 生成线程跑着的时候玩家切了巨构类型：那一轮的坑是按旧类型挖的（regen 开头的
        // generating 判断把新的请求吞掉了），这里补一次——只换类型走快路径，不重跑生成器
        if (!generating && lastData != null && builtBuild != params.build) {
            regen();
        }
        // 兜底：没网格、没在生成、也没有地表数据 —— 状态已经坏了（例如换巨构时重建失败），
        // 这里重来一次完整生成，免得永远停在一块黑屏上。限次，别变成每帧重试的死循环。
        if (!generating && mesh == null && gasMesh == null && lastData == null
                && builtBuild >= 0 && diagRetry < 3) {
            diagRetry++;
            System.out.println("[gleba] 网格丢失，重建一次（第 " + diagRetry + " 次）");
            regen();
        }
    }

    /**
     * 由当前机位反推一条<b>真实轨道</b>：一个过行星中心、包含当前机位方向的大圆。
     *
     * <p>轨道面倾角取「当前俯仰 + 18°」（夹在 24°–60°）：起始机位恰好落在面上，预设
     * 构图不会被改变；绕一圈时俯仰自然从 +坡 摆到 -坡（真实圆轨道就是这样），
     * 期间稳稳掠过赤道面两次——星环由张开到侧看成一条线，再张开。</p>
     */
    private void rebuildOrbit() {
        orbitPhase = 0f;
        float cp = MathUtils.cos(pitch), sp = MathUtils.sin(pitch);
        orbitDir.set(cp * MathUtils.sin(yaw), sp, cp * MathUtils.cos(yaw)).nor();

        double phi0 = Math.asin(MathUtils.clamp(orbitDir.y, -0.99f, 0.99f));
        double incl = Math.max(0.42, Math.min(1.05, Math.abs(phi0) + 0.32));
        double cosPhi0 = Math.cos(phi0);
        double cosI = Math.cos(incl);
        // 轨道面法线 n = aN cosψ + b sinψ：n⊥orbitDir，且与 +Y 夹角 = 倾角
        double cosPsi = Math.min(1.0, cosI / cosPhi0);
        double sinPsi = Math.sqrt(Math.max(0.0, 1.0 - cosPsi * cosPsi));
        Vector3 aN = new Vector3(0f, 1f, 0f).mulAdd(orbitDir, -orbitDir.y);
        if (aN.len2() < 1e-6f) {
            aN.set(1f, 0f, 0f);
        }
        aN.nor();
        Vector3 b = new Vector3(orbitDir).crs(aN).nor();
        orbitNormal.set(aN).scl((float) cosPsi).mulAdd(b, (float) sinPsi).nor();
        // 相位前进方向取"上升段"：镜头先从当前高度往上走，不是先栽下去
        orbitTangent.set(orbitNormal).crs(orbitDir).nor();
        if (orbitTangent.y < 0f) {
            orbitNormal.scl(-1f);
            orbitTangent.set(orbitNormal).crs(orbitDir).nor();
        }
        viewYawDeg = (float) Math.toDegrees(yaw);
        syncView();
    }

    /** 把轨道相位换算成这一帧的相机方向（并维护连续方位角，供视差/存档使用）。 */
    private void syncView() {
        float cp = MathUtils.cos(orbitPhase), sp = MathUtils.sin(orbitPhase);
        viewDir.set(orbitDir).scl(cp).mulAdd(orbitTangent, sp).nor();
        viewPitchDeg = (float) Math.toDegrees(Math.asin(MathUtils.clamp(viewDir.y, -1f, 1f)));
        float az = (float) Math.toDegrees(Math.atan2(viewDir.x, viewDir.z));
        float d = az - (viewYawDeg % 360f);
        if (d > 180f) {
            d -= 360f;
        } else if (d < -180f) {
            d += 360f;
        }
        viewYawDeg += d;
    }

    /** 3D 星空要的相机视场（度）。 */
    public float fovDeg() {
        return cam == null ? 50f : cam.fieldOfView;
    }

    public void ensureGl() {
        if (shader != null) {
            return;
        }
        shader = new ShaderProgram(VERT, FRAG);
        if (!shader.isCompiled()) {
            System.out.println("[gleba] shader 编译失败: " + shader.getLog());
        }
        atmo = new Atmosphere();
        ringMesh = new com.hysx.salvosequence.gleba.render.Rings();
        cloudMesh = new com.hysx.salvosequence.gleba.render.Clouds();
        loadCloudCovers();
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, FBW, FBH, true);
        cam = new PerspectiveCamera(50f, FBW, FBH);
        cam.near = 0.05f;
        cam.far = 400f;
        orbitLastX = -1;
    }

    /** 从 classpath / Android assets 读多张实拍云图，只在 GL 线程上传一次。 */
    private void loadCloudCovers() {
        String[] paths = {
                "textures/earth_clouds.jpg",
                "textures/earth_clouds_2h.jpg",
                "textures/earth_clouds_3h.jpg",
                "textures/earth_clouds_4i.jpg",
                "textures/earth_clouds_5h.jpg"
        };
        for (String path : paths) {
            try (InputStream in = Assets.open(path)) {
                byte[] data = in.readAllBytes();
                if (data.length == 0) {
                    throw new IllegalStateException("资源为空");
                }
                Pixmap map = new Pixmap(data, 0, data.length);
                cloudMesh.addCover(map);
                map.dispose();
                if (Boolean.getBoolean("salvo.profile")) {
                    System.out.println("[gleba] 云图 " + data.length + " bytes ← "
                            + Assets.source(path));
                }
            } catch (Exception e) {
                System.out.println("[gleba] 云图加载失败: " + e
                        + "（" + Assets.source(path) + "）");
            }
        }
        int forced = Integer.getInteger("salvo.cloudvariant", -1);
        cloudMesh.setVariant(forced >= 0 ? forced
                : (int) Math.floorMod(params.seed, Math.max(1, paths.length)));
    }

    /** 3D 渲染 → FBO → 读回像素（非预乘 ARGB，行距 FBW，已翻转成设计坐标）。 */
    public int[] renderFrame() {
        ensureGl();
        if (mesh == null && gasMesh == null && Boolean.getBoolean("salvo.diag") && !diagNoMesh) {
            // 真出现"黑屏"时把状态打出来：是还在生成，还是生成完却没建出网格
            diagNoMesh = true;
            System.out.printf("[gleba] 无网格可画：generating=%s builtBuild=%d build=%d lastData=%s%n",
                    generating, builtBuild, params.build, lastData != null);
        }
        fbo.begin();
        Gdx.gl.glViewport(0, 0, FBW, FBH);
        Gdx.gl.glClearColor(0.015f, 0.02f, 0.035f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        // 远场天空（黑洞）：最先画、不写深度，网格天体随后正确遮挡它
        if (sky != null && showSky) {
            sky.render(-(float) Math.toRadians(viewPitchDeg),
                    (float) Math.toRadians(viewYawDeg), (float) time, FBW, FBH);
        }

        // 相机沿倾斜圆轨道走：位置就是当前相位对应的方向 × 距离，up 固定世界 +Y，
        // 所以画面不滚转——行星的轴始终是竖直的，变化只有"从哪条轨道位置看过去"
        cam.position.set(viewDir).scl(dist);
        cam.up.set(0f, 1f, 0f);
        cam.lookAt(0f, 0f, 0f);
        cam.update();

        if (mesh != null) {
            // 明确关闭混合：上一帧大气留下的加法混合状态会把行星压暗
            Gdx.gl.glDisable(GL20.GL_BLEND);
            shader.bind();
            shader.setUniformMatrix("u_projTrans", cam.combined);
            shader.setUniformf("u_dbg", 0f);
            shader.setUniformf("u_lightDir", sunDir);
            shader.setUniformf("u_cameraPos", cam.position);
            shader.setUniformf("u_diskGlow", diskGlow);
            shader.setUniformf("u_build", (float) params.build);
            shader.setUniformf("u_mineAxis", mineAxis);
            shader.setUniformf("u_mineRadius", mineRadius);
            shader.setUniformf("u_citySeed", (float) Math.floorMod(params.seed, 997L));
            // 城市：点位在生成时按陆地/植被挑好，这里只按当前巨构类型决定亮几座——
            // 电梯/星门是交通枢纽，多 8 座；巢都/矿业星/气壳把地表整个改写掉了，不画城。
            int wantCities = cityBase + ((params.build == 4 || params.build == 9) ? 8 : 0);
            cityCount = cityAllowed ? Math.min(wantCities, cityPoolN) : 0;
            boolean cityOnNow = cityCount > 0 && params.build != 1 && params.build != 2
                    && params.build != 7 && !Boolean.getBoolean("salvo.nocity");
            shader.setUniformf("u_cityOn", cityOnNow ? 1f : 0f);
            // 调试开关：-Dsalvo.nighttest=true 把整颗星球压成夜面，用来检查城市灯光
            shader.setUniformf("u_nightDbg", Boolean.getBoolean("salvo.nighttest") ? 0f : 1f);
            shader.setUniformf("u_cityN", (float) cityCount);
            if (cityCount > 0) {
                for (int i = 0; i < cityCount; i++) {
                    shader.setUniformf(CITY_UNIFORM[i], cityPool[i * 4], cityPool[i * 4 + 1],
                            cityPool[i * 4 + 2], cityPool[i * 4 + 3]);
                }
            }
            boolean capOn = cityOnNow && capAllowed && cityCount >= 18;
            shader.setUniformf("u_capOn", capOn ? 1f : 0f);
            shader.setUniformf("u_cap", capVec[0], capVec[1], capVec[2], capVec[3]);
            // 巨构的影子：每帧都按当前类型重挑一次遮挡体，没巨构就清空——否则换一颗
            // 没有巨构的星球时，上一颗的影会一直留在地表上
            if (megas == null && params.build >= 3 && params.build != 7) {
                megas = new com.hysx.salvosequence.gleba.render.Megastructures();
            }
            if (megas != null) {
                if (params.build >= 3 && params.build != 7) {
                    megas.prepare(params.build, params.seed, new Vector3(cam.position).nor(), sunDir);
                }
                megas.applyShadowUniforms(shader, params.build);
            }
            mesh.render(shader);
        }
        if (gasMesh != null) {
            // 气态行星：同一套光照着色器，只是几何换成经纬球
            Gdx.gl.glDisable(GL20.GL_BLEND);
            shader.bind();
            shader.setUniformMatrix("u_projTrans", cam.combined);
            shader.setUniformf("u_dbg", 0f);
            shader.setUniformf("u_lightDir", sunDir);
            shader.setUniformf("u_cameraPos", cam.position);
            shader.setUniformf("u_diskGlow", 0f);
            // 气态行星没有地表可改：uniform 是上一个行星留下的，不清掉会整颗变巢都
            shader.setUniformf("u_build", 0f);
            // 气态行星没有地表，城市层必须关掉：uniform 是上一颗行星留下的
            shader.setUniformf("u_cityOn", 0f);
            shader.setUniformf("u_nightDbg", 1f);
            shader.setUniformf("u_capOn", 0f);
            com.hysx.salvosequence.gleba.render.Megastructures.clearShadowUniforms(shader);
            gasMesh.render(shader);
        }
        // 云层：贴在地表之上、大气之下的一层球壳，半透明混合。
        // 巢都是整颗星球盖满楼的星球，没有海也没有天气——云只留一层薄纱，不然楼顶全被糊掉
        // 巢都/矿业星的地表会被着色器整片改写，云留在上面只会挡住宿主结构
        float cloudMul = (params.build == 1 || params.build == 2 || params.build == 7) ? 0f : 1f;
        if (cloudMesh != null && clouds && cloudAmount * cloudMul > 0.01f) {
            cloudMesh.setGrade(theme.cloudHue + cloudHue, cloudBright, cloudContrast);
            cloudMesh.renderShadow(cam.combined, cam.position, sunDir, (float) time, cloudAmount * cloudMul);
            cloudMesh.render(cam.combined, cam.position, sunDir, (float) time, cloudAmount * cloudMul);
        }
        // 矿业星没有大气：坑口直接对着真空，加一层散射壳只会把坑沿糊掉，
        // 设定上也说不通（挖穿地壳的星球留不住气壳）
        if (atmo != null && params.build != 2) {
            // 大气颜色跟着星球类型走：紫色星球连晨昏线都是紫的
            String ov = System.getProperty("salvo.atmoTint");
            if (ov != null) {
                String[] p = ov.split(",");
                atmo.setTint(Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2]));
            } else if (params.build == 1) {
                // 巢都：整颗星球是楼，没有海，也没有蓝色大气——换成一圈暖灰的烟霾
                atmo.setTint(0.42f, 0.36f, 0.28f);
            } else if (params.build == 7) {
                // 大气调节网：人造壳层，几乎没有散射
                atmo.setTint(0.30f, 0.34f, 0.36f);
            } else {
                atmo.setTint(theme.atmoR, theme.atmoG, theme.atmoB);
            }
            // 厚度每颗星球随机：有的几乎没有大气（0 时整层不画），有的裹着厚云。
            // 巢都的烟霾薄一点，不然楼顶的网格会被糊成一片
            atmo.thickness = params.build == 1 ? atmoThickness * 0.55f : atmoThickness;
            atmo.render(cam.combined, cam.position, sunDir, 24f);
        } else if (atmo != null) {
            // 不画大气时也得把这层的 GL 状态补回来：大气渲染本身会关深度测试、开混合，
            // 跳过它以后状态就停在上一个 pass（云层）留下的样子，后面的星环会继承
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
        // 巨构：轨道加速器、太空电梯、拉格朗日反应堆、光帆阵列、生物圈环带、星门、博物馆。
        // 都是真网格，靠已经写好的深度缓冲被行星正确遮挡——转到星球背后就自然消失
        // （大气调节网是着色器改地表，不走这里）
        if (!theme.gas && params.build >= 3 && params.build != 7) {
            if (megas == null) {
                megas = new com.hysx.salvosequence.gleba.render.Megastructures();
            }
            megas.prepare(params.build, params.seed, new Vector3(cam.position).nor(), sunDir);
            megas.render(cam.combined, cam.position, sunDir, (float) time, params.build);
        }
        // 星环：半透明，深度测试开、深度写入关；行星的深度已经写好了，被挡住的那半自然消失
        if (ringMesh != null && rings) {
            ringMesh.render(cam.combined, cam.position, sunDir,
                    ringInner, ringOuter, ringOpacity, ringTilt, true, ringStyle,
                    ringSeed, ringBands);
        }
        // FBO 仍绑定时读回（此处读到的就是刚渲染完的行星画面）
        Pixmap src = Pixmap.createFromFrameBuffer(0, 0, FBW, FBH);
        fbo.end();

        int[] argb = new int[FBW * FBH];
        ByteBuffer bb = src.getPixels();
        for (int y = 0; y < FBH; y++) {
            int srcY = FBH - 1 - y;   // GL 自下而上 → 设计坐标 y 向下
            for (int x = 0; x < FBW; x++) {
                int i = (srcY * FBW + x) * 4;
                int r = bb.get(i) & 0xFF;
                int g = bb.get(i + 1) & 0xFF;
                int b = bb.get(i + 2) & 0xFF;
                argb[y * FBW + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        src.dispose();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        return argb;
    }

    /** 3D 视图最近邻 2 倍贴满设计缓冲（不透明整幅，黑空背景 + 行星 + 大气）。 */
    public void renderInto(PixelArt art) {
        art.blitArgbTile(renderFrame(), FBW, FBH, 0, 0, 0, 0, FBW * 2, FBH * 2,
                true, false, false, 1.0);
    }

    public void dispose() {
        if (gasMesh != null) {
            gasMesh.dispose();
            gasMesh = null;
        }
        if (mesh != null) {
            mesh.dispose();
            mesh = null;
        }
        if (fbo != null) {
            fbo.dispose();
            fbo = null;
        }
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
        if (atmo != null) {
            atmo.dispose();
            atmo = null;
        }
        if (ringMesh != null) {
            ringMesh.dispose();
            ringMesh = null;
        }
        if (cloudMesh != null) {
            cloudMesh.dispose();
            cloudMesh = null;
        }
        if (megas != null) {
            megas.dispose();
            megas = null;
        }
        pending = null;
        generating = false;
    }

    // ------------------------------------------------------------ 控制（按钮调用）

    public void newSeed() {
        params.seed = new Random().nextLong();
        if (cloudMesh != null) {
            cloudMesh.setVariant((int) Math.floorMod(params.seed,
                    Math.max(1, cloudMesh.coverCount())));
        }
        regen();
    }

    /**
     * 切换星球类型：大陆占比进生成参数，云量与星环进观感参数，然后重新生成。
     *
     * <p>类型差异来自生成参数（海陆比、温度、降水），不是后期滤镜——
     * 所以同一颗星球换成荒漠型，海岸线和山脉都还在，只是植被变成了荒漠。</p>
     */
    public void setTheme(com.hysx.salvosequence.gleba.gen.PlanetTheme t) {
        if (t == null) {
            t = com.hysx.salvosequence.gleba.gen.PlanetTheme.EARTH;
        }
        theme = t;
        params.theme = t;
        params.continentalFraction = t.continentalFraction;
        cloudAmount = t.cloudAmount;
        cloudHue = t.cloudHue;
        rings = t.rings;
        applyThemeRings(t);
        regen();
    }

    /**
     * 每种星球的环都不一样——环是最好认的身份标识，全都一套土星环最省事也最假：
     * 冰封型是一圈又细又亮的冰丝（天王星 ε 环那种），甲烷海是一层暖褐色尘，
     * 木星型是「宽内环 + 一道细外环」，冰巨星是近乎竖着、还被撞断成几段的弧环。
     * 倾角一起给：环面贴着赤道还是立成极环，一眼就能看出是另一个天体。
     */
    private void applyThemeRings(com.hysx.salvosequence.gleba.gen.PlanetTheme t) {
        switch (t) {
            case ICE -> {
                ringInner = 1.48f;
                ringOuter = 2.62f;
                ringOpacity = 0.58f;
                ringTilt = 24f;
                ringStyle = 1;
            }
            case LIQUID_METHANE -> {
                ringInner = 1.40f;
                ringOuter = 2.34f;
                ringOpacity = 0.30f;
                ringTilt = -18f;
                ringStyle = 3;
            }
            case GAS_JOVIAN -> {
                ringInner = 1.52f;
                ringOuter = 2.06f;
                ringOpacity = 0.30f;
                ringTilt = 26f;
                ringStyle = 2;
            }
            case GAS_ICE -> {
                ringInner = 1.56f;
                ringOuter = 1.98f;
                ringOpacity = 0.36f;
                ringTilt = 68f;
                ringStyle = 4;
            }
            default -> {
                ringInner = 1.34f;
                ringOuter = 2.20f;
                ringOpacity = 0.46f;
                ringTilt = 0f;
                ringStyle = 0;
            }
        }
        if (t.gas) {
            // 气态行星的环更薄更暗：又宽又亮的环是岩质/冰质行星的观感
            ringOpacity *= 0.8f;
        }
        // 主题只给「最标准的那一条」；真正的随机化在开战时由 LiveBackdrops 掷
        ringSeed = 0f;
        ringBands = ringStyle == 1 ? 4 : 3;
    }

    /** 环系剖面：0 土星式主环 / 1 细亮丝 / 2 双环 / 3 宽尘环 / 4 断续弧环。 */
    public void setRingStyle(int style) {
        ringStyle = Math.max(0, Math.min(4, style));
    }

    /** 环面倾角（度）：0 贴赤道，±70 就是极环。 */
    public void setRingTilt(float deg) {
        ringTilt = Math.max(-75f, Math.min(75f, deg));
    }

    /** 环系随机种子：换一个种子就是同一种剖面骨架下的另一条环。 */
    public void setRingSeed(float seed) {
        ringSeed = seed;
    }

    /** 细丝条数（2..4）。 */
    public void setRingBands(int bands) {
        ringBands = Math.max(2, Math.min(4, bands));
    }

    /**
     * 每颗星球掷一次大气厚度：真实天体里"薄如无物"和"厚到发光"都常见，
     * 全都裹一层标准蓝边才最假。岩质/熔岩偏向真空，冰封/海洋偏厚，气态必然厚。
     */
    private static float rollAtmosphere(long seed, com.hysx.salvosequence.gleba.gen.PlanetTheme t) {
        String ov = System.getProperty("salvo.atmo");
        if (ov != null) {
            return Float.parseFloat(ov);
        }
        java.util.Random rng = new java.util.Random(seed * 0x9E3779B97F4A7C15L ^ 0x5D2F1A3BL);
        float roll = rng.nextFloat();
        float v;
        if (roll < 0.18f) {
            v = 0f;                                          // 真空：整层不画
        } else if (roll < 0.46f) {
            v = 0.30f + rng.nextFloat() * 0.30f;             // 稀薄
        } else if (roll < 0.86f) {
            v = 0.80f + rng.nextFloat() * 0.40f;             // 正常
        } else {
            v = 1.20f + rng.nextFloat() * 0.40f;             // 厚壳
        }
        if (t != null) {
            switch (t) {
                case ROCK -> v *= 0.35f;
                case LAVA -> v *= 0.55f;
                case ICE, OCEAN, LIQUID_OCEAN -> v *= 1.15f;
                case GAS_JOVIAN, GAS_ICE -> v = Math.max(v, 0.85f);
                default -> { }
            }
        }
        return v;
    }

    public void regen() {
        if (generating) {
            return;
        }
        atmoThickness = rollAtmosphere(params.seed, params.theme);
        // 矿业星的坑开在哪个方向也随种子——不然每颗星球都挖同一个地方
        Random mr = new Random(params.seed * 31L + 7L);
        // 坑口朝向：以"重建那一刻相机所在方向"为基础加一点抖动。行星本身在慢慢自转，
        // 坑会自然转到背面去；但如果纯随机，经常一出生就整个藏在背面，玩家永远看不到开采区
        Vector3 camDir = new Vector3(cam.position);
        if (camDir.len2() < 1e-6f) {
            // 刚构造出来、相机还没摆位时 cam.position 是 (0,0,0)，nor() 会得到零向量，
            // 坑的方向就变成纯随机——用当前视向兜底，坑才总在看得见的那一侧
            camDir.set(viewDir);
        }
        camDir.nor();
        Vector3 lateral = new Vector3(mr.nextFloat() - 0.5f, mr.nextFloat() - 0.5f,
                mr.nextFloat() - 0.5f);
        lateral.mulAdd(camDir, -lateral.dot(camDir)).nor();
        // 偏角 32°–42°：坑半径 0.5 弧度（约 29°），太靠边（50° 以上）整个坑会贴着
        // 轮廓线被压成一条月牙，看着像"被地表盖住"；太居中又会顶在正面正中
        mineAxis.set(camDir).mulAdd(lateral, 0.62f + mr.nextFloat() * 0.30f).nor();
        // 坑整个落在夜面时就是一团纯黑，什么都看不出来——往向阳面推一点
        if (mineAxis.dot(sunDir) < 0.35f) {
            mineAxis.mulAdd(sunDir, 1.2f).nor();
        }
        mineRadius = 0.26f + mr.nextFloat() * 0.10f;
        if (Boolean.getBoolean("salvo.minedbg")) {
            System.out.printf("[mine] build=%d axis=(%.3f,%.3f,%.3f) radius=%.3f camDir=(%.3f,%.3f,%.3f)%n",
                    params.build, mineAxis.x, mineAxis.y, mineAxis.z, mineRadius,
                    camDir.x, camDir.y, camDir.z);
        }
        // 气态行星没有地形：不跑板块/侵蚀/气候，等下一帧直接建色带球（省掉一次完整生成）
        if (params.theme != null && params.theme.gas) {
            gasDirty = true;
            return;
        }
        // 只是换了巨构类型：板块/高程/气候跟巨构无关，重跑一遍生成器纯属浪费——
        // 而且那几秒里背景是空的，切"矿业星"时会黑一下。这里直接按新坑位重建几何。
        if (lastData != null && lastData.theme != null && !lastData.theme.gas
                && lastSeed == params.seed && lastTheme == params.theme
                && lastFaceRes == params.faceRes && builtBuild != params.build) {
            builtBuild = params.build;
            final int freqFast = Integer.getInteger("salvo.hex", params.meshFreq);
            final com.hysx.salvosequence.gleba.render.HexMesh.Carve carveFast = mineCarve(params.build);
            try {
                // 先建后换：旧的留着画，新的建好再 dispose——切巨构时不会出现一帧空白
                com.hysx.salvosequence.gleba.render.HexMesh.Cells fresh =
                        com.hysx.salvosequence.gleba.render.HexMesh
                                .build(lastData, 0f, true, freqFast, carveFast);
                if (mesh != null) {
                    mesh.dispose();
                }
                mesh = fresh;
            } catch (Throwable e) {
                // 重建失败就退回完整生成，别把 mesh 留在 null（那样整帧什么都不画 = 黑屏）
                System.out.println("[gleba] 换巨构重建失败，回退完整生成: " + e);
                builtBuild = -1;
                lastData = null;
                regen();
                return;
            }
            if (Boolean.getBoolean("salvo.profile")) {
                System.out.println("[gleba] 只换巨构：几何重建（不重跑生成器）");
            }
            return;
        }
        generating = true;
        final PlanetParams snapshot = params.copy();
        // 这一轮几何是给哪个巨构类型挖的坑：生成线程跑着的时候玩家可能又按了 F2，
        // params.build 已经变了，但这一轮的坑是按 snapshot 挖的——必须记住它，
        // 否则会误记成"已经为新的巨构建好了"，坑就永远挖不出来
        pendingBuild = snapshot.build;
        final int freq = Integer.getInteger("salvo.hex", params.meshFreq);
        final boolean gas = params.theme != null && params.theme.gas;
        // 矿业星：把地壳真挖下去（几何），不是只改颜色——剪影凹了才像被掏空
        final com.hysx.salvosequence.gleba.render.HexMesh.Carve carve = mineCarve(snapshot.build);
        Thread th = new Thread(() -> {
            try {
                // 六边形几何在后台算完，渲染线程只做一次上传
                PlanetGenerator.Result r = PlanetGenerator.generate(snapshot);
                pendingMs = r.stageMillis.getOrDefault("TOTAL", 0L);
                if (!gas && r.data.theme != null && !r.data.theme.gas) {
                    pendingGeo = com.hysx.salvosequence.gleba.render.HexMesh
                            .buildGeometry(r.data, 0f, true, freq, carve);
                }
                pending = r.data;
            } catch (Throwable e) {
                System.out.println("[gleba] 生成失败: " + e);
                generating = false;
            }
        }, "gleba-gen");
        th.setDaemon(true);
        th.start();
    }

    /**
     * 矿业星的地壳凹陷：以 {@link #mineAxis} 为中心的圆坑，坑壁切成十圈平台，
     * 中心挖到原半径的 84%。返回 null 表示不挖（其它巨构不动几何）。
     */
    private com.hysx.salvosequence.gleba.render.HexMesh.Carve mineCarve(int build) {
        if (build != 2) {
            return null;
        }
        final float ax = mineAxis.x, ay = mineAxis.y, az = mineAxis.z;
        final float radius = mineRadius;
        final float depth = 0.22f;
        // 坑口的局部正交基：用极角给坑沿加低频扰动，坑才不是一顶正圆的帽子
        Vector3 axis = new Vector3(ax, ay, az);
        Vector3 u = Math.abs(axis.y) < 0.9f ? new Vector3(0f, 1f, 0f) : new Vector3(1f, 0f, 0f);
        u.crs(axis).nor();
        Vector3 v = new Vector3(axis).crs(u).nor();
        final float ux = u.x, uy = u.y, uz = u.z;
        final float vx = v.x, vy = v.y, vz = v.z;
        return (nx, ny, nz) -> {
            float dot = nx * ax + ny * ay + nz * az;
            dot = Math.max(-1f, Math.min(1f, dot));
            float t = (float) Math.acos(dot) / radius;
            float phi = (float) Math.atan2(nx * vx + ny * vy + nz * vz, nx * ux + ny * uy + nz * uz);
            t *= 1f + 0.17f * (float) Math.sin(3f * phi + 0.7f) + 0.09f * (float) Math.sin(7f * phi + 2.1f);
            if (t >= 1f) {
                return 1f;
            }
            // 坑壁必须连续：网格是"一格一个平面片"，每格的半径各自取一个值，相邻两格半径
            // 差多少就裂开多少。原来那套五级台阶每级差 0.08 个行星半径，等于在坑壁上开了
            // 五道没有侧壁的缝——坑转到球面边缘时，视线穿过缝看到的是球内壁，看着就像
            // "矿坑被原来的地表盖住了"。这里换成连续剖面，只留一点不到 1 像素的起伏。
            float bowl = 1f - t;
            bowl = bowl * bowl * (3f - 2f * bowl);
            float ripple = 0.012f * (float) Math.sin(t * 17f) * bowl;
            return 1f - depth * bowl + ripple;
        };
    }

    public void cycleFaceRes() {
        // 以当前实际分辨率为准取档位，避免构造时索引与 conf 里的 faceRes 不同步、一按就跳档
        int idx = 0;
        for (int i = 0; i < FACE_RES.length; i++) {
            if (FACE_RES[i] == params.faceRes) {
                idx = i;
            }
        }
        idx = (idx + 1) % FACE_RES.length;
        faceResIdx = idx;
        params.faceRes = FACE_RES[idx];
        regen();
    }

    /** 当前档位索引（0 = 24，1 = 48，2 = 96），供界面显示。 */
    public int faceResIndex() {
        return faceResIdx;
    }

    public void adjustLand(float delta) {
        params.continentalFraction = Math.max(0.10f,
                Math.min(0.70f, params.continentalFraction + delta));
        regen();
    }

    public void adjustAzimuth(float delta) {
        sunAzimuthDeg = (sunAzimuthDeg + delta + 360f) % 360f;
        sunDir = computeSun();
    }

    public void adjustElevation(float delta) {
        sunElevDeg = Math.max(-80f, Math.min(80f, sunElevDeg + delta));
        sunDir = computeSun();
    }

    /** 指针拖拽 = 重新给轨道定锚点（yaw/pitch 是轨道起点，不是逐帧累加的角度）。 */
    public void orbitDrag(double mx, double my) {
        if (orbitLastX < 0) {
            orbitLastX = (float) mx;
            orbitLastY = (float) my;
            return;
        }
        yaw -= (mx - orbitLastX) * 0.012f;
        pitch = MathUtils.clamp(pitch + (float) (my - orbitLastY) * 0.012f, -MAX_PITCH, MAX_PITCH);
        orbitLastX = (float) mx;
        orbitLastY = (float) my;
        rebuildOrbit();
    }

    /** 相对拖拽（像素）：背景工坊里指针拖动直接调它，不依赖上一帧的绝对坐标。 */
    public void orbitBy(double dx, double dy) {
        yaw -= (float) dx * 0.012f;
        pitch = MathUtils.clamp(pitch + (float) dy * 0.012f, -MAX_PITCH, MAX_PITCH);
        rebuildOrbit();
    }

    /** 滚轮缩放（格数，正数放大）。 */
    public void zoomBy(double notches) {
        dist = MathUtils.clamp((float) (dist * Math.pow(1.0015, -notches * 40.0)), 1.25f, 12f);
    }

    public double[] camera() {
        syncView();
        return new double[]{viewYawDeg, viewPitchDeg, dist};
    }

    /**
     * 行星在画面上的圆盘（{cx, cy, r}，单位 FBW×FBH 像素）。
     * 背景的闪烁星要在这一块外面才画，否则星星会浮在行星夜面上。
     */
    public float[] screenDisc() {
        if (cam == null) {
            return null;
        }
        // 相机始终看向原点，行星就永远在画面正中；半径按「竖直半高 / tan(fov/2) / √(d²-1)」算。
        // （投影矩阵直接 project() 会混进相机当前的视口尺寸——实机是窗口大小，不是 320×180，
        //   之前遮罩套不上就是这个原因。）
        double f = (FBH * 0.5) / Math.tan(Math.toRadians(cam.fieldOfView * 0.5));
        double r = f / Math.sqrt(Math.max(0.01, (double) dist * dist - 1.0));
        return new float[]{FBW * 0.5f, FBH * 0.5f, (float) r};
    }

    public void setCamera(double yawDeg, double pitchDeg, double distance) {
        yaw = (float) Math.toRadians(yawDeg);
        pitch = MathUtils.clamp((float) Math.toRadians(pitchDeg), -MAX_PITCH, MAX_PITCH);
        dist = (float) Math.max(1.25, Math.min(12, distance));
        rebuildOrbit();
    }

    public void orbitReset() {
        orbitLastX = -1;
        orbitLastY = -1;
    }

    /** 滚轮缩放（delta 口径同屏幕滚轮：一格 ±40）。 */
    public void scrollZoom(double delta) {
        dist = MathUtils.clamp((float) (dist * (1.0 + (-delta / 40.0) * 0.12)), 1.25f, 12f);
    }

    // ------------------------------------------------------------ 读数

    public boolean isGenerating() {
        return generating;
    }

    /** 现在有没有可画的地形网格（生成中也会保留上一颗的网格，界面靠它决定要不要画黑）。 */
    public boolean hasMesh() {
        return mesh != null || gasMesh != null;
    }

    public String status() {
        return generating
                ? Language.t("生成中…", "generating…")
                : String.format(Locale.ROOT, "%d ms · %d 格 · 陆地 %.1f%%",
                        genMs, tiles, tiles == 0 ? 0 : 100.0 * lastLand / tiles);
    }

    public int faceRes() {
        return params.faceRes;
    }

    public float sunAzimuthDeg() {
        return sunAzimuthDeg;
    }

    public float sunElevDeg() {
        return sunElevDeg;
    }

    /** 直接指定太阳方位/高度角（度）。行星单独当背景时用它把朝阳面摆给镜头。 */
    public void setSun(float azimuthDeg, float elevationDeg) {
        sunAzimuthDeg = ((azimuthDeg % 360f) + 360f) % 360f;
        sunElevDeg = Math.max(-90f, Math.min(90f, elevationDeg));
        sunDir = computeSun();
    }

    public float skyBrightness() {
        return skyBrightness;
    }

    public void adjustSkyBrightness(float delta) {
        skyBrightness = Math.max(0.2f, Math.min(2.0f, skyBrightness + delta));
        sky.setBrightness(skyBrightness);
    }

    public float diskGlow() {
        return diskGlow;
    }

    public void adjustDiskGlow(float delta) {
        diskGlow = Math.max(0.0f, Math.min(1.0f, diskGlow + delta));
    }

    private int lastLand;

    private Vector3 computeSun() {
        double az = Math.toRadians(sunAzimuthDeg);
        double el = Math.toRadians(sunElevDeg);
        return new Vector3(
                (float) (Math.cos(el) * Math.cos(az)),
                (float) Math.sin(el),
                (float) (Math.cos(el) * Math.sin(az))).nor();
    }
}
