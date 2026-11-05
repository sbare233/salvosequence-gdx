from pathlib import Path
p = Path('src/main/java/com/hysx/salvosequence/ui/PixelGameView.java')
s = p.read_text(encoding='utf-8')
BS = chr(92)
nl = BS + 'n'
anchor = '                + "uniform float u_gain;' + nl + '"\n'
assert anchor in s, 'anchor missing'
addition = (
    '                + "uniform float u_bright;' + nl + '"\n'
    '                + "uniform float u_contrast;' + nl + '"\n'
)
s = s.replace(anchor, anchor + addition, 1)
p.write_text(s, encoding='utf-8')
print('uniforms added')
