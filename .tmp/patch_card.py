from pathlib import Path
p = Path('src/main/java/com/hysx/salvosequence/screen/CardLoopScreen.java')
s = p.read_text(encoding='utf-8')

s = s.replace(
    '    /** 设置项：0=继续 1=音乐 2=音效 3=扫描线 4=扫描线形状 5=扫描线时机 6=孔径格栅 7=风格化 8=语言 9=返回标题 10=退出游戏。 */\n'
    '    private static final int SETTINGS_ITEMS = 11;',
    '    /** 设置项：0=继续 1=音乐 2=音效 3=扫描线 4=形状 5=时机 6=格栅 7=风格化 8=背景滤镜 9=语言 10=返回标题 11=退出游戏。 */\n'
    '    private static final int SETTINGS_ITEMS = 12;', 1)

s = s.replace('                if (settingsCursor >= 1 && settingsCursor <= 8) {',
              '                if (settingsCursor >= 1 && settingsCursor <= 9) {', 1)

s = s.replace('''            case 7 -> {
                VideoSettings.cycleStyle();
                touch();
                return null;
            }
            case 8 -> {
                Language.toggle();
                touch();
                return null;
            }
            case 9 -> {
                settingsOpen = false;
                return ScreenContext.go(ScreenContext.Target.START);
            }
            case 10 -> {
                settingsOpen = false;
                return ScreenContext.quit();
            }''',
              '''            case 7 -> {
                VideoSettings.cycleStyle();
                touch();
                return null;
            }
            case 8 -> {
                VideoSettings.cycleBackdropFilter();
                touch();
                return null;
            }
            case 9 -> {
                Language.toggle();
                touch();
                return null;
            }
            case 10 -> {
                settingsOpen = false;
                return ScreenContext.go(ScreenContext.Target.START);
            }
            case 11 -> {
                settingsOpen = false;
                return ScreenContext.quit();
            }''', 1)

s = s.replace('''                Language.t("风格化：", "STYLE: ")
                        + Language.t(VideoSettings.style().label(), VideoSettings.style().labelEn()),
                Language.t("语言：", "LANGUAGE: ")''',
              '''                Language.t("风格化：", "STYLE: ")
                        + Language.t(VideoSettings.style().label(), VideoSettings.style().labelEn()),
                Language.t("背景滤镜：", "BACKDROP FILTER: ")
                        + Language.t(VideoSettings.backdropFilter().label(),
                        VideoSettings.backdropFilter().labelEn()),
                Language.t("语言：", "LANGUAGE: ")''', 1)

# 12 行要收一点行距，两个面板同改
s = s.replace('                int ry = y + 40 + i * 24;\n            boolean sel = i == settingsCursor;',
              '                int ry = y + 40 + i * 22;\n            boolean sel = i == settingsCursor;', 1)
s = s.replace('            int ry = y + 40 + i * 24;\n            int index = i;',
              '            int ry = y + 40 + i * 22;\n            int index = i;', 1)
s = s.replace('''        int w = 268;
        int h = 328;
        int x = (640 - w) / 2;
        int y = (360 - h) / 2;
        art.rect(x, y, w, h, Palette.PANEL);''',
              '''        int w = 268;
        int h = 340;
        int x = (640 - w) / 2;
        int y = (360 - h) / 2;
        art.rect(x, y, w, h, Palette.PANEL);''', 1)
s = s.replace('''        int w = 268;
        int h = 328;
        int x = (640 - w) / 2;
        int y = (360 - h) / 2;
        for (int i = 0; i < SETTINGS_ITEMS; i++) {''',
              '''        int w = 268;
        int h = 340;
        int x = (640 - w) / 2;
        int y = (360 - h) / 2;
        for (int i = 0; i < SETTINGS_ITEMS; i++) {''', 1)
s = s.replace('    /** 动态文案：音乐/音效/扫描线显示开关，时机/风格化/语言显示当前值。 */',
              '    /** 动态文案：音乐/音效/扫描线显示开关，时机/风格化/滤镜/语言显示当前值。 */', 1)
p.write_text(s, encoding='utf-8')
print('CardLoopScreen patched')
