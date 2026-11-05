from pathlib import Path
p = Path('src/main/java/com/hysx/salvosequence/ui/PixelGameView.java')
s = p.read_text(encoding='utf-8')
BS = chr(92)
nl = BS + 'n'
bad = []
for frag in [
    '  vec3 v = (c.rgb - 0.5) * u_contrast + 0.5;',
    '  v = clamp(v * u_bright, 0.0, 1.0);',
    '  vec3 g = v * u_gain / (vec3(1.0) + v * (u_gain - 1.0));',
]:
    broken = '+ "' + frag + '\n"\n'
    fixed = '+ "' + frag + nl + '"\n'
    if broken in s:
        s = s.replace(broken, fixed)
        bad.append(frag)
p.write_text(s, encoding='utf-8')
print('fixed', len(bad))
