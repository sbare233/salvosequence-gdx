import io, sys
p = 'src/main/java/com/hysx/salvosequence/screen/CardLoopScreen.java'
s = io.open(p, encoding='utf-8').read()
start = s.index("    /** 把选中的船整体前/后挪一格（前线在右侧，右边先挨打）。 */")
end = s.index("    private void toggleRestCard() {")
removed = s[start:end]
assert "shuffleRestShip" in removed and len(removed) < 1200, (len(removed), removed[:80])
s = s[:start] + s[end:]
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print("removed shuffleRestShip, %d chars" % len(removed))
