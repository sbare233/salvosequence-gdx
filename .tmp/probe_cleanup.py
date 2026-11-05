import io, re

p = 'src/main/java/com/hysx/salvosequence/ui/PixelGameView.java'
s = io.open(p, encoding='utf-8').read()

# drop probe calls
s = re.sub(r'\n\s*probe\("[a-z]+"\);', '', s)
# drop helper + fields
s = re.sub(r'\n    private static final java\.nio\.ByteBuffer PROBE_BUF =.*?\n    }\n', '\n', s, flags=re.S)
s = s.replace('        lastGraded = beamShader != null', '        boolean graded = beamShader != null', 1)
s = s.replace('        if (lastGraded) {\n            beamShader.bind();', '        if (graded) {\n            beamShader.bind();', 1)
s = s.replace('\n        lastBright = bright; lastContrast = contrast; lastGrille = grille;', '', 1)
s = s.replace('        lastBloom = bloomActive;\n', '', 1)

io.open(p, 'w', encoding='utf-8').write(s)
print('probe' in s, 'PROBE_BUF' in s, 'lastGraded' in s)
