import io

p = 'src/main/java/com/hysx/salvosequence/ui/PixelGameView.java'
s = io.open(p, encoding='utf-8').read()

old_shot = '''            String shot = System.getProperty("salvosequence.smoke.png");
            if (shot != null && !shot.isBlank()) {
                // 冒烟截图：直接抓默认帧缓冲（y 翻转后写盘），用来核对后处理链的输出
                com.badlogic.gdx.graphics.Pixmap px =
                        com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(
                                0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                com.badlogic.gdx.graphics.PixmapIO.writePNG(
                        Gdx.files.absolute(shot), px, -1, true);
                px.dispose();
                System.out.println("[smoke] 截图 -> " + shot);
            }
'''
new_shot = '''            // 截图不能在这里抓：tick() 跑在 GL 绘制之前，抓到的会是上一帧（甚至只剩
            // 3D 背景、没有界面）。打标记，交给 render() 画完整帧之后再抓。
            smokeShotPending = true;
'''
assert old_shot in s
s = s.replace(old_shot, new_shot, 1)

# declare the flag next to smokeFrames
s = s.replace(
    '    private final int smokeFrames = Integer.getInteger("salvosequence.smoke.frames", -1);',
    '    private final int smokeFrames = Integer.getInteger("salvosequence.smoke.frames", -1);\n'
    '    private boolean smokeShotPending;', 1)

# capture at the end of render(), after the grille pass
old_grille = '''            drawApertureGrille(ox, oy, cw, ch, scale);
        }
        probe("grille");'''
new_grille = '''            drawApertureGrille(ox, oy, cw, ch, scale);
        }
        probe("grille");

        // 冒烟截图：整帧画完再抓默认帧缓冲（y 翻转后写盘），核对后处理链输出
        if (smokeShotPending) {
            smokeShotPending = false;
            String shot = System.getProperty("salvosequence.smoke.png");
            if (shot != null && !shot.isBlank()) {
                com.badlogic.gdx.graphics.Pixmap px =
                        com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(
                                0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                com.badlogic.gdx.graphics.PixmapIO.writePNG(
                        Gdx.files.absolute(shot), px, -1, true);
                px.dispose();
                System.out.println("[smoke] 截图 -> " + shot);
            }
        }'''
assert old_grille in s
s = s.replace(old_grille, new_grille, 1)

io.open(p, 'w', encoding='utf-8').write(s)
print('patched')
