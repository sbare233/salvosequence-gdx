package com.hysx.salvosequence.ui;

/**
 * 界面语言：中文 / English。
 *
 * <p>默认中文。和 {@link VideoSettings} 一样是进程内静态状态，不落盘；
 * 切换后下一帧重绘即生效，不需要重启。</p>
 *
 * <p>取词用 {@link #t(String, String)} 就地写中英两版，不另建 key 表：
 * 这个仓库的文案本来就贴着绘制代码，测试也直接断言中文字面量，
 * 多一层 key 只会让两处对不上。</p>
 */
public enum Language {

    /** 中文界面。 */
    ZH("中文"),
    /** 英文界面。 */
    EN("English");

    private final String label;

    private static Language current = ZH;

    Language(String label) {
        this.label = label;
    }

    /** 设置项里显示的名字，用各自语言书写。 */
    public String label() {
        return label;
    }

    public static Language current() {
        return current;
    }

    public static void set(Language value) {
        current = value == null ? ZH : value;
    }

    public static Language toggle() {
        current = current == ZH ? EN : ZH;
        return current;
    }

    public static boolean isChinese() {
        return current == ZH;
    }

    public static boolean isEnglish() {
        return current == EN;
    }

    /**
     * 双语取词：中文界面返回 {@code zh}，英文界面返回 {@code en}。
     *
     * <p>英文模式下如果某条文案没有英文版（内容正文、lore、战斗日志），
     * 仍然传中文进来当兜底，界面上保持可读，不会出现空标签。</p>
     */
    public static String t(String zh, String en) {
        return current == EN ? en : zh;
    }
}
