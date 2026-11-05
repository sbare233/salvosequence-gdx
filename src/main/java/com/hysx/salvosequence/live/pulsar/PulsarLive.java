package com.hysx.salvosequence.live.pulsar;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * 脉冲星（中子星 + 磁极喷流）的离屏渲染器：整幅画进 320×180 的帧缓冲，读回 ARGB。
 *
 * <p>和星云的体积渲染不同，这里是一套普通的 3D 场景：中子星本体（自定义球面着色）+
 * 粒子束流 + 磁力线，最后走一遍 Bloom 合成。相机保持固定轨道，只让星体自转，
 * 这样放进战斗底图时不会晃来晃去。
 */
public final class PulsarLive
{
    public static final int FBW = 320;
    public static final int FBH = 180;

    private final Params params = new Params();

    private boolean ready;
    private int frames;
    private float spin;

    private PerspectiveCamera cam;
    private final Vector3 camPos = new Vector3();
    /** 手动相机：偏航/俯仰/距离，不随时间自己转。 */
    private double yawDeg = 0.0;
    private double pitchDeg = 0.0;
    private double orbitDist = 7.4;
    private final Vector3 up = new Vector3(0f, 1f, 0f);
    private final Matrix4 viewProj = new Matrix4();
    private final Matrix4 magModel = new Matrix4();

    private final Vector3 magAxisModel = new Vector3();
    private final Vector3 magAxisWorld = new Vector3();
    private final Vector3 beamColor = new Vector3(0.78f, 0.90f, 1f);
    private final float[] spotDirs = new float[24];

    private NeutronStar star;
    private BeamParticles beams;
    private FieldLines lines;
    private Bloom bloom;
    /** 最终合成目标：bloom 的 composite 落这里，再读回 ARGB。 */
    private com.badlogic.gdx.graphics.glutils.FrameBuffer out;
    private ShapeRenderer shapes;

    public void ensureGl()
    {
        if (ready)
        {
            return;
        }
        ready = true;

        cam = new PerspectiveCamera(45f, FBW, FBH);
        cam.near = 0.05f;
        cam.far = 200f;
        // 相机远一点，整颗星带束流都收在画面里（参考 MVP 的初始轨道）
        camPos.set(0f, 0f, 7.4f);

        for (int i = 0; i < 8; i++)
        {
            double y = 1.0 - 2.0 * (i + 0.5) / 8.0;
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double ph = i * 2.399963229728653;
            spotDirs[i * 3] = (float) (Math.cos(ph) * r);
            spotDirs[i * 3 + 1] = (float) y;
            spotDirs[i * 3 + 2] = (float) (Math.sin(ph) * r);
        }

        star = new NeutronStar();
        beams = new BeamParticles();
        lines = new FieldLines();
        bloom = new Bloom(FBW, FBH);
        out = new com.badlogic.gdx.graphics.glutils.FrameBuffer(
                com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888, FBW, FBH, false);
        shapes = new ShapeRenderer();
        shapes.setAutoShapeType(true);
    }

    /** 渲染一帧，返回 320×180 的非预乘 ARGB（y 向下）。 */
    public int[] render(double time)
    {
        ensureGl();

        float t = (float) time;
        spin = t * params.spinSpeed * 360f;

        updateCamera();

        float tilt = params.tiltDeg * com.badlogic.gdx.math.MathUtils.degreesToRadians;
        magAxisModel.set(0f, com.badlogic.gdx.math.MathUtils.cos(tilt),
                com.badlogic.gdx.math.MathUtils.sin(tilt)).nor();
        magModel.idt().rotate(Vector3.Y, spin).rotate(Vector3.X, params.tiltDeg);
        magAxisWorld.set(magAxisModel).rotate(Vector3.Y, spin).nor();

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        out.begin();
        Gdx.gl.glViewport(0, 0, FBW, FBH);
        bloom.beginScene();

        star.render(cam, spin * com.badlogic.gdx.math.MathUtils.degreesToRadians, magAxisWorld,
                spotDirs, params.starRadius, params.temp, params.capBright, params.limb,
                params.exposure);

        if (params.beams)
        {
            float beamSpin = spin * com.badlogic.gdx.math.MathUtils.degreesToRadians * 2f;
            beams.render(cam, camPos, params.starRadius, magAxisWorld, params.starRadius * 0.92f,
                    params.starRadius * 0.16f, params.beamLength,
                    params.beamWidthDeg * com.badlogic.gdx.math.MathUtils.degreesToRadians,
                    params.beamBright, beamColor, 1.7f, beamSpin, 0.075f, t, 1.9f,
                    params.beamParticles);
        }

        if (params.fieldLines)
        {
            lines.render(viewProj, magModel, 0.32f);
        }

        bloom.endSceneAndComposite(params.bloomStrength, params.bloomThreshold, 1f);

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glViewport(0, 0, FBW, FBH);

        // 合成结果落在 out（scene.end() 会把绑定还原成 out），从这里读回
        Pixmap src = Pixmap.createFromFrameBuffer(0, 0, FBW, FBH);
        out.end();
        int[] argb = new int[FBW * FBH];
        java.nio.ByteBuffer bb = src.getPixels();
        for (int y = 0; y < FBH; y++)
        {
            int srcY = FBH - 1 - y;
            for (int x = 0; x < FBW; x++)
            {
                int i = (srcY * FBW + x) * 4;
                int r = bb.get(i) & 0xFF;
                int g = bb.get(i + 1) & 0xFF;
                int b = bb.get(i + 2) & 0xFF;
                argb[y * FBW + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        src.dispose();
        frames++;
        return argb;
    }

    private void updateCamera()
    {
        double y = Math.toRadians(yawDeg);
        double p = Math.toRadians(pitchDeg);
        camPos.set((float) (orbitDist * Math.cos(p) * Math.sin(y)),
                (float) (orbitDist * Math.sin(p)),
                (float) (orbitDist * Math.cos(p) * Math.cos(y)));
        cam.position.set(camPos);
        cam.up.set(up);
        // 瞄点往左下偏：中子星连同喷流就落到画面右上角，不压在棋盘正中
        cam.lookAt(-2.1f, -0.9f, 0f);
        cam.update();
        viewProj.set(cam.combined);
    }

    public Params params()
    {
        return params;
    }

    public void orbit(double dx, double dy)
    {
        yawDeg -= dx * 0.3;
        pitchDeg = Math.max(-70, Math.min(70, pitchDeg + dy * 0.3));
    }

    public void zoom(double notches)
    {
        orbitDist = Math.max(3.0, Math.min(30.0, orbitDist * Math.pow(1.0015, -notches * 40.0)));
    }

    public double[] camera()
    {
        return new double[]{yawDeg, pitchDeg, orbitDist};
    }

    public void setCamera(double yaw, double pitch, double dist)
    {
        yawDeg = yaw;
        pitchDeg = Math.max(-70, Math.min(70, pitch));
        orbitDist = Math.max(3.0, Math.min(30.0, dist));
    }

    public void dispose()
    {
        if (!ready)
        {
            return;
        }
        ready = false;
        star.dispose();
        beams.dispose();
        lines.dispose();
        bloom.dispose();
        out.dispose();
        shapes.dispose();
    }
}
