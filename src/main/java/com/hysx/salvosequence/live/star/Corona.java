package com.hysx.salvosequence.live.star;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * 日冕辉光 + 日珥 + 耀斑：全屏射线步进的一趟加法绘制。
 * 日珥是沿偶极场线的二次 Bezier 弧，CPU 端按种子生成，逐帧把端点/弧顶上传。
 */
public class Corona {
    private static final int ARCS = 8;

    private final FullscreenQuad quad;
    private final ShaderProgram shader;

    private final Vector3[] a = new Vector3[ARCS];
    private final Vector3[] b = new Vector3[ARCS];
    private final Vector3[] c = new Vector3[ARCS];
    private final float[] arcA = new float[ARCS * 3];
    private final float[] arcB = new float[ARCS * 3];
    private final float[] arcC = new float[ARCS * 3];
    private final float[] arcOn = new float[ARCS];
    private final float[] arcFlare = new float[ARCS];
    private final float[] arcPeriod = new float[ARCS];
    private final float[] arcPhase = new float[ARCS];

    public Corona(long seed, int count) {
        quad = new FullscreenQuad();
        shader = new ShaderProgram(
                Gdx.files.internal("shaders/star/quad.vert"),
                Gdx.files.internal("shaders/star/corona.frag"));
        if (!shader.isCompiled()) {
            throw new IllegalStateException("corona shader: " + shader.getLog());
        }

        java.util.Random rnd = new java.util.Random(seed * 6364136223846793005L + 1442695040888963407L);
        for (int i = 0; i < ARCS; i++) {
            // 随机单位向量 A、B，角距 18°–46°
            a[i] = randUnit(rnd);
            double sep = Math.toRadians(18 + rnd.nextDouble() * 28);
            Vector3 tangent = new Vector3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f);
            tangent.mulAdd(a[i], -tangent.dot(a[i]));
            if (tangent.len2() < 1e-6f) tangent.set(0, 1, 0).mulAdd(a[i], -a[i].y);
            tangent.nor();
            b[i] = new Vector3(a[i]).scl((float) Math.cos(sep)).mulAdd(tangent, (float) Math.sin(sep)).nor();

            float height = 0.16f + rnd.nextFloat() * 0.34f;
            c[i] = new Vector3(a[i]).add(b[i]).nor().scl(1f + height);

            arcOn[i] = i < count ? 1f : 0f;
            arcPeriod[i] = 3.5f + rnd.nextFloat() * 5.0f;
            arcPhase[i] = rnd.nextFloat();
        }
        upload();
    }

    private static Vector3 randUnit(java.util.Random rnd) {
        double z = rnd.nextDouble() * 2 - 1;
        double t = rnd.nextDouble() * Math.PI * 2;
        double r = Math.sqrt(Math.max(0, 1 - z * z));
        return new Vector3((float) (r * Math.cos(t)), (float) z, (float) (r * Math.sin(t)));
    }

    private void upload() {
        for (int i = 0; i < ARCS; i++) {
            arcA[i * 3] = a[i].x; arcA[i * 3 + 1] = a[i].y; arcA[i * 3 + 2] = a[i].z;
            arcB[i * 3] = b[i].x; arcB[i * 3 + 1] = b[i].y; arcB[i * 3 + 2] = b[i].z;
            arcC[i * 3] = c[i].x; arcC[i * 3 + 1] = c[i].y; arcC[i * 3 + 2] = c[i].z;
        }
    }

    /** 每帧更新耀斑脉冲。 */
    public void update(float time, float flareRate) {
        for (int i = 0; i < ARCS; i++) {
            float ph = (time / arcPeriod[i] + arcPhase[i]) % 1f;
            float pulse = (float) Math.exp(-Math.pow((ph - 0.5) * 15.0, 2));
            arcFlare[i] = arcOn[i] > 0.5f ? pulse * flareRate : 0f;
        }
    }

    public void render(com.badlogic.gdx.graphics.Camera cam, float time, float prom, float exposure,
                       float tanHalf, float aspect, Vector3 seed) {
        shader.begin();
        shader.setUniform3fv("u_arcA", arcA, 0, ARCS * 3);
        shader.setUniform3fv("u_arcB", arcB, 0, ARCS * 3);
        shader.setUniform3fv("u_arcC", arcC, 0, ARCS * 3);
        shader.setUniform1fv("u_arcOn", arcOn, 0, ARCS);
        shader.setUniform1fv("u_arcFlare", arcFlare, 0, ARCS);

        shader.setUniformf("u_camPos", cam.position);
        Vector3 right = new Vector3(cam.direction).crs(cam.up).nor();
        Vector3 up = new Vector3(right).crs(cam.direction).nor();
        shader.setUniformf("u_camRight", right);
        shader.setUniformf("u_camUp", up);
        shader.setUniformf("u_camFwd", cam.direction);
        shader.setUniformf("u_tanHalf", tanHalf);
        shader.setUniformf("u_aspect", aspect);
        shader.setUniformf("u_time", time);
        shader.setUniform3fv("u_seed", new float[]{seed.x, seed.y, seed.z}, 0, 3);
        shader.setUniformf("u_prom", prom);
        shader.setUniformf("u_exposure", exposure);

        // 放到远平面 + 开深度测试：恒星实心球（和光球外壳）已经写好深度，
        // 这一趟在盘面内会被整段挡掉，包括跑到背面去的日珥弧
        shader.setUniformf("u_z", 0.9999f);   // 远平面内侧：比清屏值 1.0 小，才能通过 GL_LESS
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        quad.render(shader);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDepthMask(true);
        shader.end();
    }

    public void dispose() {
        quad.dispose();
        shader.dispose();
    }
}
