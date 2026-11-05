package com.hysx.salvosequence.tools;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.hysx.salvosequence.gleba.GlebaPlanet;

/** 临时探针：无头验证 Gargantua 着色器编译与一帧渲染（隐形窗口，立即退出）。 */
public final class GargantuaProbe {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("gargantua-probe");
        config.setWindowedMode(64, 64);
        config.setInitialVisible(false);
        new Lwjgl3Application(new ApplicationAdapter() {
            @Override
            public void create() {
                try {
                    GlebaPlanet planet = new GlebaPlanet();
                    planet.ensureGl();
                    planet.update(0.016);
                    // 复现游戏状态：先跑一遍 SpriteBatch 2D 呈现（残留程序/属性/纹理绑定）
                    SpriteBatch sb = new SpriteBatch();
                    Pixmap pm2 = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
                    Texture decoy = new Texture(pm2);
                    sb.begin();
                    sb.draw(decoy, 0, 0);
                    sb.end();
                    decoy.dispose();
                    pm2.dispose();
                    System.out.println("[probe] SpriteBatch 状态后渲染一帧…");
                    int[] frame = planet.renderFrame();
                    long sum = 0;
                    for (int v : frame) sum += (v & 0xFFFFFF);
                    System.out.println("[probe] 帧像素均值=" + (sum / frame.length));
                    com.hysx.salvosequence.tools.PngWriter.write(frame, 320, 180,
                            new java.io.File("target/gargantua-probe.png"));
                    System.out.println("[probe] -> target/gargantua-probe.png");
                    planet.dispose();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                Gdx.app.exit();
            }
        }, config);
        Runtime.getRuntime().halt(0);
    }
}
