from pathlib import Path
p = Path('src/main/java/com/hysx/salvosequence/ui/PixelGameView.java')
s = p.read_text(encoding='utf-8')

old = '''    /** 原画上屏：整幅设计缓冲按 alpha 混合铺到视口里的目标矩形。 */
    private void drawFrame(double ox, double oy, double cw, double ch) {
        // 开着孔径格栅时先加一次束流增益：格栅只留下 1/3 的通道，不补的话整屏会暗成一片。
        boolean beam = screens.isApertureGrilleEnabled() && beamShader != null;
        if (beam) {
            beamShader.bind();
            beamShader.setUniformf("u_gain", BEAM_GAIN);
            batch.setShader(beamShader);
        } else {
            batch.setShader(null);
        }
'''

new = '''    /** 模糊/辉光时对输入的预压：先把底画压暗压平，后处理才不会糊成一片白。 */
    private static final float EFFECT_BRIGHTNESS = 0.8f;
    private static final float EFFECT_CONTRAST = 0.9f;
    /** 孔径格栅会挡掉约 2/3 的通道，开格栅时再压一道亮度。 */
    private static final float GRILLE_BRIGHTNESS = 0.9f;

    /**
     * 原画上屏：整幅设计缓冲按 alpha 混合铺到视口里的目标矩形。
     *
     * <p>上屏前按画质风格做一次"输入调色"：模糊/辉光会整体提亮并摊开，
     * 先把输入压到 80% 亮度、90% 对比，亮部不会顶到白；孔径格栅再挡掉
     * 2/3 的通道，所以再压 10% 亮度。任一系数不为 1 时走 {@link #beamShader}
     * 做一次 contrast → brightness → 束流软膝，省一次额外全屏 pass。</p>
     */
    private void drawFrame(double ox, double oy, double cw, double ch) {
        boolean grille = screens.isApertureGrilleEnabled();
        float bright = 1.0f;
        float contrast = 1.0f;
        if (appliedStyle == VideoSettings.Style.BLUR || appliedStyle == VideoSettings.Style.BLOOM) {
            bright *= EFFECT_BRIGHTNESS;
            contrast *= EFFECT_CONTRAST;
        }
        if (grille) {
            bright *= GRILLE_BRIGHTNESS;
        }
        boolean grade = beamShader != null
                && (grille || Math.abs(bright - 1.0f) > 1.0e-4f || Math.abs(contrast - 1.0f) > 1.0e-4f);
        if (grade) {
            beamShader.bind();
            // 格栅的束流增益只在真的开格栅时给，否则 1.0 = 直通
            beamShader.setUniformf("u_gain", grille ? BEAM_GAIN : 1.0f);
            beamShader.setUniformf("u_bright", bright);
            beamShader.setUniformf("u_contrast", contrast);
            batch.setShader(beamShader);
        } else {
            batch.setShader(null);
        }
'''

assert old in s, 'drawFrame head missing'
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
print('drawFrame patched')
