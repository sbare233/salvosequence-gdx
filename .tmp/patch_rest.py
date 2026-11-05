import io

p = 'src/main/java/com/hysx/salvosequence/screen/CardLoopScreen.java'
s = io.open(p, encoding='utf-8').read()
block = io.open('.tmp/rest_block.java.txt', encoding='utf-8').read()

anchor = '    // ---------------------------------------------------------------- 覆盖层'
if 'collectRestHotspots' not in s:
    assert anchor in s
    s = s.replace(anchor, block + anchor, 1)
    io.open(p, 'w', encoding='utf-8', newline='').write(s)
    print('inserted')
else:
    print('already there')
