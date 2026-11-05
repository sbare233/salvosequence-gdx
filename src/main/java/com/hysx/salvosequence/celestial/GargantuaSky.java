package com.hysx.salvosequence.celestial;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.hysx.salvosequence.core.PngLoader;

/**
 * Gargantua 黑洞（接入自 Gargantua-main，原作者 sonicether 的 Shadertoy lstSRS）：
 * 引力透镜扭曲星空 + 体积吸积盘 + 盘面泛光，全屏 raymarching 片元着色器。
 *
 * <p>作为天体场景的<b>远场天空</b>最先绘制（不写深度），其后绘制的网格天体
 * 正确遮挡它。相机旋转由场景轨道相机传入（rotX = -pitch、rotY = yaw，
 * 推导自着色器 rotate(p, x, y, z) 的矩阵次序）。</p>
 *
 * <p>移植注记：WebGL2 (#version 300 es) → 桌面 GLSL 100——去掉版本/精度行、
 * textureLod/texture → texture2D、TAA（previousFrame/jitter）整块移除、
 * u_brightness 为本移植新增的吸积盘亮度旋钮。</p>
 */
public final class GargantuaSky {

    private ShaderProgram shader;
    private Mesh quad;
    private Texture noiseTex;   // iChannel0：噪声（iq hash 用）
    private Texture diskTex;    // iChannel1：吸积盘丝缕纹理
    private float brightness = 1.0f;
    private boolean ready;

    private static final String VERT =
            "attribute vec2 a_position;\n" +
            "varying vec2 v_texCoord;\n" +
            "void main() {\n" +
            "  v_texCoord = a_position * 0.5 + 0.5;\n" +
            "  gl_Position = vec4(a_position, 0.0, 1.0);\n" +
            "}\n";

    private static final String FRAG =
            "varying vec2 v_texCoord;\n" +
            "uniform vec2 iResolution;\n" +
            "uniform float iTime;\n" +
            "uniform sampler2D iChannel0;\n" +
            "uniform sampler2D iChannel1;\n" +
            "uniform float cameraRotX;\n" +
            "uniform float cameraRotY;\n" +
            "uniform float u_fovZ;\n" +
            "uniform float u_brightness;\n" +
            "\n" +
            "#define ITERATIONS 200\n" +
            "const vec3 MainColor = vec3(1.0);\n" +
            "\n" +
            "float noise( in vec3 x )\n" +
            "{\n" +
            "    vec3 p = floor(x);\n" +
            "    vec3 f = fract(x);\n" +
            "    f = f*f*(3.0-2.0*f);\n" +
            "    vec2 uv = (p.xy+vec2(37.0,17.0)*p.z) + f.xy;\n" +
            "    vec2 rg = texture2D( iChannel0, (uv+ 0.5)/256.0 ).yx;\n" +
            "    return -1.0+2.0*mix( rg.x, rg.y, f.z );\n" +
            "}\n" +
            "\n" +
            "float saturate(float x)\n" +
            "{\n" +
            "    return clamp(x, 0.0, 1.0);\n" +
            "}\n" +
            "\n" +
            "vec3 saturate(vec3 x)\n" +
            "{\n" +
            "    return clamp(x, vec3(0.0), vec3(1.0));\n" +
            "}\n" +
            "\n" +
            "float rand(vec2 coord)\n" +
            "{\n" +
            "    return saturate(fract(sin(dot(coord, vec2(12.9898, 78.223))) * (43758.5453)));\n" +
            "}\n" +
            "\n" +
            "float pcurve( float x, float a, float b )\n" +
            "{\n" +
            "    float k = pow(a+b,a+b) / (pow(a,a)*pow(b,b));\n" +
            "    return k * pow( x, a ) * pow( 1.0-x, b );\n" +
            "}\n" +
            "\n" +
            "const float pi = 3.14159265;\n" +
            "\n" +
            "float atan2(float y, float x)\n" +
            "{\n" +
            "    if (x > 0.0)\n" +
            "    {\n" +
            "        return atan(y / x);\n" +
            "    }\n" +
            "    else if (x == 0.0)\n" +
            "    {\n" +
            "        if (y > 0.0)\n" +
            "        {\n" +
            "            return pi / 2.0;\n" +
            "        }\n" +
            "        else if (y < 0.0)\n" +
            "        {\n" +
            "            return -(pi / 2.0);\n" +
            "        }\n" +
            "        else\n" +
            "        {\n" +
            "            return 0.0;\n" +
            "        }\n" +
            "    }\n" +
            "    else\n" +
            "    {\n" +
            "        if (y >= 0.0)\n" +
            "        {\n" +
            "            return atan(y / x) + pi;\n" +
            "        }\n" +
            "        else\n" +
            "        {\n" +
            "            return atan(y / x) - pi;\n" +
            "        }\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "float sdTorus(vec3 p, vec2 t)\n" +
            "{\n" +
            "    vec2 q = vec2(length(p.xz) - t.x, p.y);\n" +
            "    return length(q)-t.y;\n" +
            "}\n" +
            "\n" +
            "float sdSphere(vec3 p, float r)\n" +
            "{\n" +
            "    return length(p)-r;\n" +
            "}\n" +
            "\n" +
            "void Haze(inout vec3 color, vec3 pos, float alpha)\n" +
            "{\n" +
            "    vec2 t = vec2(1.0, 0.01);\n" +
            "\n" +
            "    float torusDist = length(sdTorus(pos + vec3(0.0, -0.05, 0.0), t));\n" +
            "\n" +
            "    float bloomDisc = 1.0 / (pow(torusDist, 2.0) + 0.001);\n" +
            "    vec3 col = MainColor;\n" +
            "    bloomDisc *= length(pos) < 0.5 ? 0.0 : 1.0;\n" +
            "\n" +
            "    color += col * bloomDisc * (2.9 / float(ITERATIONS)) * (1.0 - alpha * 1.0);\n" +
            "}\n" +
            "\n" +
            "vec3 intersectionWithXZPlane(vec3 pos, vec3 pre_pos) {\n" +
            "    if (pos.y == pre_pos.y) {\n" +
            "        return vec3(0.0);\n" +
            "    }\n" +
            "    float t = -pre_pos.y / (pos.y - pre_pos.y);\n" +
            "    return vec3(\n" +
            "        pre_pos.x + t * (pos.x - pre_pos.x),\n" +
            "        0.0,\n" +
            "        pre_pos.z + t * (pos.z - pre_pos.z)\n" +
            "    );\n" +
            "}\n" +
            "\n" +
            "void GasDisc(inout vec3 color, inout float alpha, vec3 pos, vec3 pre_pos)\n" +
            "{\n" +
            "    vec3 discNormal = normalize(vec3(0.0, 1.0, 0.0));\n" +
            "\n" +
            "    vec3 origin = vec3(0.0, 0.0, 0.0);\n" +
            "    float discRadius = 3.2;\n" +
            "    float discWidth = 5.3;\n" +
            "    float discInner = discRadius - discWidth * 0.5;\n" +
            "    float discOuter = discRadius + discWidth * 0.5;\n" +
            "    float discThickness = 0.1;\n" +
            "\n" +
            "    if (pos.y * pre_pos.y < 0.0) {\n" +
            "        vec3 inter_pos = intersectionWithXZPlane(pos, pre_pos);\n" +
            "        pos = inter_pos;\n" +
            "    }\n" +
            "    else if (pos.y == 0.0) {\n" +
            "        pos = pos;\n" +
            "    }\n" +
            "    else {\n" +
            "    }\n" +
            "\n" +
            "    float distFromCenter = length(vec3(pos.x, 0.0, pos.z));\n" +
            "    float distFromDisc = pos.y;\n" +
            "    float prevDistFromDisc = pre_pos.y;\n" +
            "\n" +
            "    float radialGradient = 1.0 - saturate((distFromCenter - discInner) / discWidth * 0.5);\n" +
            "\n" +
            "    float coverage = pcurve(radialGradient, 4.0, 0.9);\n" +
            "\n" +
            "    discThickness *= radialGradient;\n" +
            "    coverage *= saturate(1.0 - abs(distFromDisc) / discThickness);\n" +
            "\n" +
            "    vec3 dustColorLit = MainColor;\n" +
            "    vec3 dustColorDark = vec3(0.0, 0.0, 0.0);\n" +
            "\n" +
            "    float dustGlow = 1.0 / (pow(1.0 - radialGradient, 2.0) * 290.0 + 0.002);\n" +
            "    vec3 dustColor = dustColorLit * dustGlow * 8.2;\n" +
            "\n" +
            "    coverage = saturate(coverage * 0.7);\n" +
            "\n" +
            "    float fade = pow((abs(distFromCenter - discInner) + 0.4), 4.0) * 0.04;\n" +
            "    float bloomFactor = 1.0 / (pow(distFromDisc, 2.0) * 40.0 + fade + 0.00002);\n" +
            "    vec3 b = dustColorLit * pow(bloomFactor, 1.5);\n" +
            "\n" +
            "    b *= mix(vec3(1.7, 1.1, 1.0), vec3(0.5, 0.6, 1.0), vec3(pow(radialGradient, 2.0)));\n" +
            "    b *= mix(vec3(1.7, 0.5, 0.1), vec3(1.0), vec3(pow(radialGradient, 0.5)));\n" +
            "\n" +
            "    dustColor = mix(dustColor, b * 150.0, saturate(1.0 - coverage * 1.0));\n" +
            "    coverage = saturate(coverage + bloomFactor * bloomFactor * 0.1);\n" +
            "\n" +
            "    if (coverage < 0.01)\n" +
            "    {\n" +
            "        return;\n" +
            "    }\n" +
            "\n" +
            "    vec3 radialCoords;\n" +
            "    radialCoords.x = distFromCenter * 1.5 + 0.55;\n" +
            "    radialCoords.y = atan2(-pos.x, -pos.z) * 1.5;\n" +
            "    radialCoords.z = distFromDisc * 1.5;\n" +
            "\n" +
            "    radialCoords *= 0.95;\n" +
            "\n" +
            "    float speed = 0.0;\n" +
            "\n" +
            "    radialCoords.y += iTime * 0.1 * 0.5;\n" +
            "\n" +
            "    float noise1 = 1.0;\n" +
            "    vec3 rc = radialCoords + 0.0;               rc.y += iTime * speed;\n" +
            "    noise1 *= noise(rc * 3.0) * 0.5 + 0.5;      rc.y -= iTime * speed;\n" +
            "    noise1 *= noise(rc * 6.0) * 0.5 + 0.5;      rc.y += iTime * speed;\n" +
            "    noise1 *= noise(rc * 12.0) * 0.5 + 0.5;     rc.y -= iTime * speed;\n" +
            "    noise1 *= noise(rc * 24.0) * 0.5 + 0.5;     rc.y += iTime * speed;\n" +
            "\n" +
            "    float noise2 = 2.0;\n" +
            "    rc = radialCoords + 30.0;\n" +
            "    noise2 *= noise(rc * 3.0) * 0.5 + 0.5;      rc.y -= iTime * speed;\n" +
            "    noise2 *= noise(rc * 6.0) * 0.5 + 0.5;      rc.y += iTime * speed;\n" +
            "    noise2 *= noise(rc * 12.0) * 0.5 + 0.5;     rc.y -= iTime * speed;\n" +
            "    noise2 *= noise(rc * 24.0) * 0.5 + 0.5;     rc.y += iTime * speed;\n" +
            "    noise2 *= noise(rc * 48.0) * 0.5 + 0.5;     rc.y -= iTime * speed;\n" +
            "    noise2 *= noise(rc * 92.0) * 0.5 + 0.5;\n" +
            "\n" +
            "    dustColor *= noise1 * 0.998 + 0.002;\n" +
            "    coverage *= noise2;\n" +
            "\n" +
            "    dustColor *= pow(texture2D(iChannel1, radialCoords.yx * vec2(0.15, 0.27)).rgb, vec3(2.0)) * 4.0;\n" +
            "\n" +
            "    coverage = saturate(coverage * 1200.0 / float(ITERATIONS));\n" +
            "    dustColor = max(vec3(0.0), dustColor);\n" +
            "\n" +
            "    coverage *= pcurve(radialGradient, 4.0, 0.9);\n" +
            "\n" +
            "    color = (1.0 - alpha) * dustColor * coverage + color;\n" +
            "\n" +
            "    alpha = (1.0 - alpha) * coverage + alpha;\n" +
            "}\n" +
            "\n" +
            "vec3 rotate(vec3 p, float x, float y, float z)\n" +
            "{\n" +
            "    mat3 matx = mat3(1.0, 0.0, 0.0,\n" +
            "                    0.0, cos(x), sin(x),\n" +
            "                    0.0, -sin(x), cos(x));\n" +
            "\n" +
            "    mat3 maty = mat3(cos(y), 0.0, -sin(y),\n" +
            "                    0.0, 1.0, 0.0,\n" +
            "                    sin(y), 0.0, cos(y));\n" +
            "\n" +
            "    mat3 matz = mat3(cos(z), sin(z), 0.0,\n" +
            "                    -sin(z), cos(z), 0.0,\n" +
            "                    0.0, 0.0, 1.0);\n" +
            "\n" +
            "    p = matx * p;\n" +
            "    p = matz * p;\n" +
            "    p = maty * p;\n" +
            "\n" +
            "    return p;\n" +
            "}\n" +
            "\n" +
            "void RotateCamera(inout vec3 eyevec, inout vec3 eyepos)\n" +
            "{\n" +
            "    vec3 angle = vec3(cameraRotX, cameraRotY, -0.45);\n" +
            "\n" +
            "    eyevec = rotate(eyevec, angle.x, angle.y, angle.z);\n" +
            "    eyepos = rotate(eyepos, angle.x, angle.y, angle.z);\n" +
            "}\n" +
            "\n" +
            "void WarpSpace(inout vec3 eyevec, inout vec3 raypos)\n" +
            "{\n" +
            "    vec3 origin = vec3(0.0, 0.0, 0.0);\n" +
            "\n" +
            "    float singularityDist = distance(raypos, origin);\n" +
            "    float warpFactor = 1.0 / (pow(singularityDist, 2.0) + 0.000001);\n" +
            "\n" +
            "    vec3 singularityVector = normalize(origin - raypos);\n" +
            "\n" +
            "    float warpAmount = 5.0;\n" +
            "\n" +
            "    eyevec = normalize(eyevec + singularityVector * warpFactor * warpAmount / float(ITERATIONS));\n" +
            "}\n" +
            "\n" +
            "void mainImage( out vec4 fragColor, in vec2 fragCoord )\n" +
            "{\n" +
            "    vec2 uv = fragCoord.xy / iResolution.xy;\n" +
            "\n" +
            "    float aspect = iResolution.x / iResolution.y;\n" +
            "\n" +
            "    vec2 uveye = uv;\n" +
            "\n" +
            "    vec3 eyevec = normalize(vec3((uveye * 2.0 - 1.0) * vec2(aspect, 1.0), u_fovZ));\n" +
            "    vec3 eyepos = vec3(0.0, -0.0, -10.0);\n" +
            "\n" +
            "    const float far = 15.0;\n" +
            "\n" +
            "    RotateCamera(eyevec, eyepos);\n" +
            "\n" +
            "    vec3 color = vec3(0.0, 0.0, 0.0);\n" +
            "\n" +
            "    float dither = rand(uv) * 3.0;\n" +
            "\n" +
            "    float alpha = 0.;\n" +
            "    vec3 raypos = eyepos + eyevec * dither * far / float(ITERATIONS);\n" +
            "    vec3 pre_pos = raypos;\n" +
            "    for (int i = 0; i < ITERATIONS; i++)\n" +
            "    {        \n" +
            "        WarpSpace(eyevec, raypos);\n" +
            "        raypos += eyevec * far / float(ITERATIONS);\n" +
            "        GasDisc(color, alpha, raypos, pre_pos);\n" +
            "        Haze(color, raypos, alpha);\n" +
            "        pre_pos = raypos;\n" +
            "    }\n" +
            "\n" +
            "    color *= 0.03;\n" +
            "    color = pow(color, vec3(1.5));\n" +
            "    color = color / (1.0 + color);\n" +
            "    color = pow(color, vec3(1.0 / 1.5));\n" +
            "\n" +
            "    color = mix(color, color * color * (3.0 - 2.0 * color), vec3(1.0));\n" +
            "    color = pow(color, vec3(1.3, 1.20, 1.0));    \n" +
            "\n" +
            "    color = saturate(color * 1.01);\n" +
            "\n" +
            "    color = pow(color, vec3(0.7 / 2.2));\n" +
            "\n" +
            "    color *= u_brightness;\n" +
            "\n" +
            "    fragColor = vec4(color, 1.0);\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "  vec4 fragColor;\n" +
            "  mainImage(fragColor, gl_FragCoord.xy);\n" +
            "  gl_FragColor = fragColor;\n" +
            "}\n";

    public void setBrightness(float brightness) {
        this.brightness = Math.max(0.0f, Math.min(2.5f, brightness));
    }

    public float brightness() {
        return brightness;
    }

    private void ensureGl() {
        if (ready) {
            return;
        }
        shader = new ShaderProgram(VERT, FRAG);
        if (!shader.isCompiled()) {
            System.out.println("[gargantua] shader 编译失败: " + shader.getLog());
        }
        ShaderProgram.pedantic = false;
        quad = new Mesh(true, 4, 6,
                new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"));
        quad.setVertices(new float[]{-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f});
        quad.setIndices(new short[]{0, 1, 2, 0, 2, 3});

        noiseTex = loadTex("textures/rgba_noise_medium_256.png");
        diskTex = loadTex("textures/organic_1_1024.png");
        if (noiseTex != null) {
            noiseTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            noiseTex.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        }
        if (diskTex != null) {
            diskTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            diskTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        }
        ready = true;
    }

    private static Texture loadTex(String path) {
        PngLoader.Bitmap bm = PngLoader.loadFromAssets(path);
        if (bm == null) {
            System.out.println("[gargantua] 纹理缺失: " + path);
            return null;
        }
        Pixmap pm = new Pixmap(bm.width(), bm.height(), Pixmap.Format.RGBA8888);
        java.nio.ByteBuffer bytes = pm.getPixels();
        for (int i = 0; i < bm.argb().length; i++) {
            int p = bm.argb()[i];
            bytes.put(i * 4, (byte) ((p >> 16) & 0xFF));
            bytes.put(i * 4 + 1, (byte) ((p >> 8) & 0xFF));
            bytes.put(i * 4 + 2, (byte) (p & 0xFF));
            bytes.put(i * 4 + 3, (byte) ((p >> 24) & 0xFF));
        }
        Texture t = new Texture(pm);
        pm.dispose();
        return t;
    }

    /** 全屏绘制黑洞天空（远场）：在共享 FBO 内、网格天体之前调用。 */
    public void render(float rotX, float rotY, float time, int fbw, int fbh) {
        ensureGl();
        if (shader == null || !shader.isCompiled() || noiseTex == null || diskTex == null) {
            return;
        }
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        shader.bind();
        shader.setUniformf("iResolution", fbw, fbh);
        shader.setUniformf("iTime", time);
        shader.setUniformf("cameraRotX", rotX);
        shader.setUniformf("cameraRotY", rotY);
        // 与 3D 相机 fov(50°) 匹配的透视距离：1 / tan(fov/2)
        shader.setUniformf("u_fovZ", (float) (1.0 / Math.tan(Math.toRadians(25.0))));
        shader.setUniformf("u_brightness", brightness);
        noiseTex.bind(0);
        shader.setUniformi("iChannel0", 0);
        diskTex.bind(1);
        shader.setUniformi("iChannel1", 1);
        quad.render(shader, GL20.GL_TRIANGLES);
        // 【关键】把活动纹理单元重置回 0：bind(1) 会把 GL_ACTIVE_TEXTURE 留在
        // TEXTURE1，后续所有"绑定到当前单元"的调用（gdx-vfx 内部、SpriteBatch
        // 的部分路径）都会错绑到单元 1，而采样器读的是单元 0（本层泄漏的
        // 噪声纹理）——整屏雪花、且跨界面持久。
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    }

    public void dispose() {
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
        if (quad != null) {
            quad.dispose();
            quad = null;
        }
        if (noiseTex != null) {
            noiseTex.dispose();
            noiseTex = null;
        }
        if (diskTex != null) {
            diskTex.dispose();
            diskTex = null;
        }
        ready = false;
    }
}
