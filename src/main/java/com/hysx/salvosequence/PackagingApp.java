package com.hysx.salvosequence;

/**
 * 打包专用入口。
 *
 * <p>libGDX 版的分发走 maven-shade-plugin 的 fat jar（依赖与各平台原生库都打进
 * {@code target/SalvoSequenceGdx.jar}，任意装了 JRE 21+ 的机器上
 * {@code java -jar} 直接跑），不再需要原版为 JavaFX classpath 部署专门写的
 * jpackage 包装。这个类保留为分发脚本的稳定入口名。</p>
 */
public final class PackagingApp {

    private PackagingApp() {
    }

    public static void main(String[] args) {
        com.hysx.salvosequence.ui.SalvoSequenceApp.main(args);
    }
}
