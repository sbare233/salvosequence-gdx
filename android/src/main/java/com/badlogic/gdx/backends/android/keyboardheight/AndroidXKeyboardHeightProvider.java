package com.badlogic.gdx.backends.android.keyboardheight;

import android.app.Activity;

/**
 * 顶掉 libGDX 自带的同名实现。
 *
 * <p>libGDX 1.13 的 {@code AndroidApplication.init()} 在 SDK ≥ 30 时固定实例化
 * {@code AndroidXKeyboardHeightProvider}，而它依赖 AndroidX（{@code androidx.core.view.ViewCompat}
 * 等）。本工程用手写的 d8 流水线打包，不带 AndroidX，真机走这条路就是
 * {@code NoClassDefFoundError: androidx.core.view.OnApplyWindowInsetsListener} 闪退。</p>
 *
 * <p>这个游戏全程没有软键盘输入（点击/长按/双指就够了），所以这里给一个零高度的空实现，
 * 构建脚本会把它替换进 dex（并从 backend jar 里剔除原 class）。</p>
 */
public class AndroidXKeyboardHeightProvider implements KeyboardHeightProvider {

    private KeyboardHeightObserver observer;

    public AndroidXKeyboardHeightProvider(Activity activity) {
        // activity 仅用于原实现里的窗口 insets 监听，这里不需要
    }

    @Override
    public void start() {
        // 不需要测量
    }

    @Override
    public void close() {
        // 无需释放
    }

    @Override
    public void setKeyboardHeightObserver(KeyboardHeightObserver observer) {
        this.observer = observer;
    }

    @Override
    public int getKeyboardLandscapeHeight() {
        return 0;
    }

    @Override
    public int getKeyboardPortraitHeight() {
        return 0;
    }
}
