package com.hysx.salvosequence;

import com.hysx.salvosequence.ui.SalvoSequenceApp;

/**
 * 根包里的便捷入口（等价于 {@link SalvoSequenceApp}）。
 *
 * <p>保留根包入口是为了几种启动方式都能用同一句命令：</p>
 * <ul>
 *   <li>{@code mvn compile exec:java}（pom 里配置的 mainClass 指向本类）</li>
 *   <li>IDE 里直接运行本类</li>
 *   <li>{@code java -jar target/SalvoSequenceGdx.jar}（shade fat jar）</li>
 * </ul>
 *
 * <p>真正的界面在 {@code com.hysx.salvosequence.ui.PixelGameView}，
 * 本类只转发启动（静态 main 从父类继承）。</p>
 */
public class PixelHudApp extends SalvoSequenceApp {
}
