package com.hysx.salvosequence.screen;

/**
 * 界面跳转请求。
 *
 * <p>界面自己不负责"怎么切"，它只声明"我要去哪"；真正的切换（含过渡动画、
 * 旧界面 {@code onExit}、新界面 {@code onEnter}）由 {@code ScreenManager} 执行。</p>
 */
public final class ScreenContext {

    /** 目标界面。 */
    public enum Target {
        /** 开始界面。 */
        START,
        /** 组舰者玩法循环验证界面（选舰队/战斗/战利品/编入一屏到底）。 */
        CARD,
        /** 战舰外观设计器（每艘船一张外观图，按帧播）。 */
        DESIGNER,
        /** 舰队编排界面：把船摆到 16×16 阵里（再进外观设计）。 */
        ARRANGE,
        /** 关卡内「战术休整」里调整编队：用同一套编排界面，改的是当前这局的舰队。 */
        ARRANGE_RUN,
        /** 回到还在进行的那局关卡（从编排界面回来时用，避免重开一局）。 */
        CARD_RESUME,
        /** 背景工坊：原图拜耳像素化成游戏背景（F6 / 标题菜单进入）。 */
        BACKDROP_LAB,
        /** Gleba 星球：3D 行星建模实时渲染（F5 进入），可捕获送入背景工坊。 */
        GLEBA,
        /** 退出程序。 */
        QUIT,
    }

    private final Target target;

    private ScreenContext(Target target) {
        this.target = target;
    }

    /** 跳到指定界面。 */
    public static ScreenContext go(Target target) {
        return new ScreenContext(target);
    }

    /** 退出程序。 */
    public static ScreenContext quit() {
        return go(Target.QUIT);
    }

    public Target target() {
        return target;
    }
}
