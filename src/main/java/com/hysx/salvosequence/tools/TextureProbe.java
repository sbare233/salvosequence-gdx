package com.hysx.salvosequence.tools;

import com.hysx.salvosequence.core.PngLoader;

/** 临时探针：验证 Gargantua 两张纹理在 Pixmap 解码链路上是否可用。 */
public final class TextureProbe {
    public static void main(String[] args) {
        String[] paths = args.length > 0 ? args : new String[]{
                "textures/rgba_noise_medium_256.png", "textures/organic_1_1024.png"};
        {
            for (String path : paths) {
            PngLoader.Bitmap bm = PngLoader.loadFromAssets(path);
            System.out.println("[tex] " + path + " -> " + (bm == null ? "NULL" : bm.width() + "x" + bm.height()));
            }
        }
        Runtime.getRuntime().halt(0);
    }
}
