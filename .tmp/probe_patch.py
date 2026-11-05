import io, re

p = 'src/main/java/com/hysx/salvosequence/ui/PixelGameView.java'
s = io.open(p, encoding='utf-8').read()

if 'SMOKE_PROBE' not in s:
    # insert probes
    s = s.replace(
        "        drawFrame(ox, oy, cw, ch, !bloomActive);",
        "        drawFrame(ox, oy, cw, ch, !bloomActive);\n        probe(\"base\");", 1)
    s = s.replace(
        "        if (profiling) { profVfx += System.nanoTime() - t0; t0 = System.nanoTime(); }",
        "        probe(\"fx\");\n        if (profiling) { profVfx += System.nanoTime() - t0; t0 = System.nanoTime(); }", 1)
    s = s.replace(
        "        if (scanAfter) {\n            drawScanlines(ox, oy, cw, ch, scale);\n        }",
        "        if (scanAfter) {\n            drawScanlines(ox, oy, cw, ch, scale);\n        }\n        probe(\"scan\");", 1)
    s = s.replace(
        "            drawApertureGrille(ox, oy, cw, ch, scale);\n        }",
        "            drawApertureGrille(ox, oy, cw, ch, scale);\n        }\n        probe(\"grille\");", 1)

    helper = '''
    private static final java.nio.ByteBuffer PROBE_BUF =
            com.badlogic.gdx.utils.BufferUtils.newByteBuffer(16);

    /** 冒烟调试：打印若干屏幕点的像素值，确认每一层到底画没画上去。 */
    private void probe(String tag) {
        if (System.getenv("SMOKE_PROBE") == null) {
            return;
        }
        int w = Gdx.graphics.getBackBufferWidth();
        int h = Gdx.graphics.getBackBufferHeight();
        int[] pts = { w / 2, h / 2, w / 2, h * 3 / 4, w / 4, h / 4 };
        StringBuilder sb = new StringBuilder("[probe] ").append(tag);
        for (int i = 0; i < 3; i++) {
            PROBE_BUF.clear();
            Gdx.gl.glReadPixels(pts[i * 2], pts[i * 2 + 1], 1, 1,
                    GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, PROBE_BUF);
            sb.append(" (").append(PROBE_BUF.get(0) & 0xFF).append(',')
              .append(PROBE_BUF.get(1) & 0xFF).append(',')
              .append(PROBE_BUF.get(2) & 0xFF).append(',')
              .append(PROBE_BUF.get(3) & 0xFF).append(')');
        }
        sb.append(" graded=").append(lastGraded)
          .append(" bright=").append(lastBright)
          .append(" contrast=").append(lastContrast)
          .append(" grille=").append(lastGrille)
          .append(" bloom=").append(lastBloom);
        System.err.println(sb);
    }
'''
    s = s.replace("    private void tick(double dt) {", helper + "\n    private void tick(double dt) {", 1)

    # record grading state
    s = s.replace(
        "        boolean graded = beamShader != null",
        "        lastGraded = beamShader != null", 1)
    s = s.replace(
        '        if (graded) {\n            beamShader.bind();',
        '        if (lastGraded) {\n            beamShader.bind();', 1)
    s = s.replace(
        "        batch.draw(frameTexture, (float) ox, (float) oy, (float) cw, (float) ch);\n        batch.end();\n        batch.setShader(null);",
        "        batch.draw(frameTexture, (float) ox, (float) oy, (float) cw, (float) ch);\n        batch.end();\n        batch.setShader(null);\n        lastBright = bright; lastContrast = contrast; lastGrille = grille;", 1)
    s = s.replace(
        "    private static final float EFFECT_BRIGHTNESS = 0.8f;",
        "    private boolean lastGraded; private float lastBright = 1f; private float lastContrast = 1f;\n    private boolean lastGrille; private boolean lastBloom;\n    private static final float EFFECT_BRIGHTNESS = 0.8f;", 1)
    s = s.replace(
        "        drawFrame(ox, oy, cw, ch, !bloomActive);\n        probe(\"base\");",
        "        lastBloom = bloomActive;\n        drawFrame(ox, oy, cw, ch, !bloomActive);\n        probe(\"base\");", 1)

    io.open(p, 'w', encoding='utf-8').write(s)
    print("patched")
else:
    print("already patched")
