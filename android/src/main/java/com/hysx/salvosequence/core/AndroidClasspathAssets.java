package com.hysx.salvosequence.core;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.files.FileHandle;

/**
 * 把「classpath 文件」重定向到 assets。
 *
 * <p>APK 里没有 jar，AndroidFileHandle 读 Classpath 类型时走
 * {@code Class.getResourceAsStream}，在 dex 环境下永远找不到。gdx-vfx 的
 * {@code gdxvfx/shaders/*.vert|frag} 和 libGDX 自带字体都是按 classpath 取的，
 * 所以启动时就会 {@code GdxRuntimeException: File not found: ... (Classpath)}。</p>
 *
 * <p>构建脚本已经把这些资源平铺进 assets（路径保持不变），这里把 classpath 映射成 internal
 * 即可。只包裹、不改其它行为。</p>
 */
public final class AndroidClasspathAssets implements Files {

    private final Files base;

    public AndroidClasspathAssets(Files base) {
        this.base = base;
    }

    @Override
    public FileHandle classpath(String path) {
        return base.internal(path);
    }

    @Override
    public FileHandle getFileHandle(String fileName, FileType type) {
        if (type == FileType.Classpath) {
            return base.internal(fileName);
        }
        return base.getFileHandle(fileName, type);
    }

    @Override
    public FileHandle internal(String path) {
        return base.internal(path);
    }

    @Override
    public FileHandle external(String path) {
        return base.external(path);
    }

    @Override
    public FileHandle absolute(String path) {
        return base.absolute(path);
    }

    @Override
    public FileHandle local(String path) {
        return base.local(path);
    }

    @Override
    public String getExternalStoragePath() {
        return base.getExternalStoragePath();
    }

    @Override
    public boolean isExternalStorageAvailable() {
        return base.isExternalStorageAvailable();
    }

    @Override
    public String getLocalStoragePath() {
        return base.getLocalStoragePath();
    }

    @Override
    public boolean isLocalStorageAvailable() {
        return base.isLocalStorageAvailable();
    }
}
