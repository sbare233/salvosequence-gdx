from pathlib import Path
p = Path('src/main/java/com/hysx/salvosequence/ui/PixelGameView.java')
s = p.read_text(encoding='utf-8')

# 1) drawFrame 增加 grade 参数
old_sig = '''    private void drawFrame(double ox, double oy, double cw, double ch) {
        boolean grille = screens.isApertureGrilleEnabled();
        float bright = 1.0f;
        float contrast = 1.0f;
        if (appliedStyle == VideoSettings.Style.BLUR || appliedStyle == VideoSettings.Style.BLOOM) {
            bright *= EFFECT_BRIGHTNESS;
            contrast *= EFFECT_CONTRAST;
        }'''
new_sig = '''    private void drawFrame(double ox, double oy, double cw, double ch) {
        drawFrame(ox, oy, cw, ch, true);
    }

    /**
     * @param grade 是否叠加"特效前输入预压"。辉光模式下这一帧会画两次（屏幕铺底层 +
     *              后处理的输入捕获层），两层在屏幕上相加，预压只能压一次，否则
     *              0.8×0.8 会把画面直接压到一半亮度；所以铺底层传 false、捕获层传 true。
     */
    private void drawFrame(double ox, double oy, double cw, double ch, boolean grade) {
        boolean grille = screens.isApertureGrilleEnabled();
        float bright = 1.0f;
        float contrast = 1.0f;
        if (grade
                && (appliedStyle == VideoSettings.Style.BLUR
                || appliedStyle == VideoSettings.Style.BLOOM)) {
            bright *= EFFECT_BRIGHTNESS;
            contrast *= EFFECT_CONTRAST;
        }'''
assert old_sig in s
s = s.replace(old_sig, new_sig, 1)

# 2) 变量名冲突：方法参数 grade 与局部 boolean grade 重名 -> 局部改名
old_local = '''        boolean grade = beamShader != null
                && (grille || Math.abs(bright - 1.0f) > 1.0e-4f || Math.abs(contrast - 1.0f) > 1.0e-4f);
        if (grade) {'''
new_local = '''        boolean graded = beamShader != null
                && (grille || Math.abs(bright - 1.0f) > 1.0e-4f || Math.abs(contrast - 1.0f) > 1.0e-4f);
        if (graded) {'''
assert old_local in s
s = s.replace(old_local, new_local, 1)

# 3) 渲染顺序：bloomActive 提前，铺底层不预压，捕获层预压
old_render = '''        drawFrame(ox, oy, cw, ch);
        if (profiling) { profFrame += System.nanoTime() - t0; t0 = System.nanoTime(); }'''
new_render = '''        boolean bloomActive = appliedStyle == VideoSettings.Style.BLOOM && vfxManager != null;
        // 辉光模式下这一层只是铺底，预压留给输入捕获层做（两层相加，压两次会过暗）
        drawFrame(ox, oy, cw, ch, !bloomActive);
        if (profiling) { profFrame += System.nanoTime() - t0; t0 = System.nanoTime(); }'''
assert old_render in s
s = s.replace(old_render, new_render, 1)

old_style = '''        VideoSettings.Style style = appliedStyle;
        boolean bloomActive = style == VideoSettings.Style.BLOOM && vfxManager != null;
        if (scanBefore && !bloomActive) {'''
new_style = '''        VideoSettings.Style style = appliedStyle;
        if (scanBefore && !bloomActive) {'''
assert old_style in s
s = s.replace(old_style, new_style, 1)

old_cap = '''            drawFrame(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT);'''
new_cap = '''            drawFrame(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT, true);'''
assert old_cap in s
s = s.replace(old_cap, new_cap, 1)

p.write_text(s, encoding='utf-8')
print('grading once per screen')
