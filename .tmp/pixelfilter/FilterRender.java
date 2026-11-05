import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.core.PixelArtFilter;
import com.hysx.salvosequence.live.nebula.NebulaLive;
import com.hysx.salvosequence.live.star.StarLive;
import com.hysx.salvosequence.tools.PngWriter;

import java.io.File;

/** 对比「原帧 / 像素滤镜」两版，各自 2 倍最近邻放大，方便肉眼看格子。 */
public final class FilterRender {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("filter");
        cfg.setWindowedMode(320, 180);
        cfg.setInitialVisible(false);
        cfg.useVsync(false);
        cfg.setForegroundFPS(0);
        new Lwjgl3Application(new ApplicationAdapter() {
            private NebulaLive nebula;
            private StarLive star;
            private int step;

            @Override
            public void render() {
                try {
                    if (step == 0) {
                        nebula = new NebulaLive();
                        star = new StarLive();
                    }
                    if (step == 1) {
                        shot("nebula", nebula.render(6.0));
                        shot("star", star.render(4.0));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                step++;
                if (step > 2) {
                    System.exit(0);
                }
            }
        }, cfg);
    }

    private static void shot(String name, int[] img) throws Exception {
        write(name + "-raw", img);
        PixelArtFilter.setEnabled(true);
        // a) 只用色阶量化，保留原色相
        PixelArtFilter.setParams(new PixelArtFilter.Params(8, 0, 0.35f, 0.5f, 0.28f, 0.5f, 1f));
        write(name + "-quant", PixelArtFilter.apply(img, 320, 180));
        // b) 暖色调色板
        PixelArtFilter.cyclePalette(0);
        PixelArtFilter.setParams(new PixelArtFilter.Params(8, 1, 0.35f, 0.5f, 0.28f, 0.5f, 1f));
        write(name + "-warm", PixelArtFilter.apply(img, 320, 180));
        // c) 冷色调色板
        PixelArtFilter.cyclePalette(1);
        write(name + "-cold", PixelArtFilter.apply(img, 320, 180));
    }

    private static void write(String name, int[] img) throws Exception {
        PixelArt art = new PixelArt(640, 360);
        for (int y = 0; y < 360; y++) {
            for (int x = 0; x < 640; x++) {
                art.buffer()[y * 640 + x] = img[(y / 2) * 320 + (x / 2)];
            }
        }
        PngWriter.write(art.buffer(), 640, 360, new File(".tmp/shots/" + name + ".png"));
    }
}
