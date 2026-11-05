package com.hysx.salvosequence;

import android.content.res.AssetManager;
import android.os.Bundle;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.hysx.salvosequence.core.AndroidClasspathAssets;
import com.hysx.salvosequence.core.AndroidImageCodec;
import com.hysx.salvosequence.core.Assets;
import com.hysx.salvosequence.core.ImageCodec;
import com.hysx.salvosequence.core.WorkDir;
import com.hysx.salvosequence.core.CjkRasterizer;
import com.hysx.salvosequence.ui.PixelGameView;

/** Android 入口：把桌面版同一套 PixelGameView 挂到 AndroidApplication 上。 */
public final class AndroidLauncher extends AndroidApplication {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CrashLogger.install(this);

        final AssetManager assets = getAssets();
        CjkRasterizer.install(assets);
        // APK 里没有 jar：在 create() 开头把 classpath 资源（gdxvfx/shaders/* 等）重定向到 assets
        Assets.setStartupHook(() -> Gdx.files = new AndroidClasspathAssets(Gdx.files));
        // Android 上 assets/ 不能走类路径，给 Assets 注册 AssetManager 加载器
        Assets.setPlatformLoader(path -> {
            try {
                return assets.open(path);
            } catch (Exception e) {
                return null;
            }
        });

        // 背景工坊在手机上也要能用：图片编解码换成 BitmapFactory，工作目录指到
        // Android/data/com.hysx.salvosequence/files（backdrops-src/、backdrops/custom/ 都在那儿）
        ImageCodec.setBackend(new AndroidImageCodec());
        WorkDir.setBase(getExternalFilesDir(null));

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        config.useAccelerometer = false;
        config.useCompass = false;
        config.useGyroscope = false;
        config.useWakelock = true;
        // libGDX 默认的 GdxNativeLoader 会走 GdxNativesLoader.load()，而它依赖
        // gdx-jnigen-loader 里的 SharedLibraryLoader（只在桌面 classpath 上，
        // 没有打进 APK），真机上会 NoClassDefFoundError 闪退。
        // Android 的 .so 已经由系统从 lib/<abi>/ 解压加载，直接 loadLibrary 即可。
        config.nativeLoader = () -> System.loadLibrary("gdx");

        initialize(new PixelGameView(), config);
    }
}
