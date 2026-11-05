package com.hysx.salvosequence.core;

import java.io.File;

/**
 * 运行时的工作目录。
 *
 * <p>桌面端就是进程的当前目录（{@code .}）；Android 没有可写的当前目录，
 * 启动时把它指到 {@code getExternalFilesDir(null)}（也就是
 * {@code Android/data/com.hysx.salvosequence/files/}），这样
 * {@code backdrops-src/}、{@code backdrops/custom/}、{@code backdrops/batch/}
 * 在两个平台上是同一套相对路径。</p>
 */
public final class WorkDir {

    private static File base = new File(".");

    private WorkDir() {
    }

    public static File base() {
        return base;
    }

    public static void setBase(File dir) {
        if (dir != null) {
            base = dir;
        }
    }

    /** 相对工作目录的一个路径。 */
    public static File of(String relative) {
        return new File(base, relative);
    }
}
