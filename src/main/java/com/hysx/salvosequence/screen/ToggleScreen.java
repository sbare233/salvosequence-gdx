package com.hysx.salvosequence.screen;

/**
 * 覆盖式界面标记。
 *
 * <p>被 {@code ScreenManager} 用 F4 翻出来的"实验室"类界面实现这个接口。
 * 语义和普通界面不同：它<b>不参与</b>正常的界面流转，
 * 退出时回到进入前那一屏（而不是回到开始界面）——
 * 这样在战斗中随时能翻出来核对素材，再回去接着打。</p>
 */
public interface ToggleScreen extends Screen {
}
