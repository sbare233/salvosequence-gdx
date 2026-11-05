from pathlib import Path
p = Path('src/test/java/com/hysx/salvosequence/screen/StartScreenTest.java')
s = p.read_text(encoding='utf-8')

s = s.replace(
    '''        // 设置游标移到「语言」（扫描线 / 形状 / 时机 / 孔径格栅 / 风格化之后）并切换
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("ENTER");''',
    '''        // 设置游标移到「语言」（扫描线 / 形状 / 时机 / 孔径格栅 / 风格化 / 背景滤镜之后）并切换
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("ENTER");''', 1)

s = s.replace(
    '''        screen.onKey("ENTER");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("ENTER");
        screen.onKey("ESCAPE");''',
    '''        screen.onKey("ENTER");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("DOWN");
        screen.onKey("ENTER");
        screen.onKey("ESCAPE");''', 1)

s = s.replace('''    @Test
    void settingsHasAScanlineShapeRowRightAfterTheScanlineSwitch() {''',
              '''    @Test
    void settingsHasABackdropFilterRowThatCyclesBayerAndPixel() {
        StartScreen screen = openSettings();
        List<String> rows = draw(screen).stream()
                .filter(t -> t.startsWith("背景滤镜：")).toList();
        assertEquals(1, rows.size(), "设置页应当有一行背景滤镜");
        assertTrue(rows.get(0).endsWith("拜耳"), "默认拜耳：" + rows.get(0));

        // 扫描线(0) -> 形状(1) -> 时机(2) -> 孔栅(3) -> 风格化(4) -> 背景滤镜(5)
        for (int i = 0; i < 5; i++) {
            screen.onKey("DOWN");
        }
        screen.onKey("ENTER");
        assertEquals(VideoSettings.BackdropFilter.PIXEL, VideoSettings.backdropFilter());
        assertTrue(draw(screen).stream().filter(t -> t.startsWith("背景滤镜："))
                .findFirst().orElseThrow().endsWith("像素"));

        screen.onKey("ENTER");
        assertEquals(VideoSettings.BackdropFilter.BAYER, VideoSettings.backdropFilter(),
                "第二轮回到拜耳");
    }

    @Test
    void settingsHasAScanlineShapeRowRightAfterTheScanlineSwitch() {''', 1)

p.write_text(s, encoding='utf-8')
print('test patched')
