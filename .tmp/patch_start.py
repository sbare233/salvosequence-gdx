from pathlib import Path
p = Path('src/main/java/com/hysx/salvosequence/screen/StartScreen.java')
s = p.read_text(encoding='utf-8')

s = s.replace(
    '    /** 设置页的条目数：扫描线 / 扫描线形状 / 扫描线时机 / 孔径格栅 / 风格化 / 语言。 */\n'
    '    private static final int SETTINGS_ITEMS = 6;',
    '    /** 设置页的条目数：扫描线 / 形状 / 时机 / 孔径格栅 / 风格化 / 背景滤镜 / 语言。 */\n'
    '    private static final int SETTINGS_ITEMS = 7;', 1)

s = s.replace(
    '    /** 设置页：扫描线 / 扫描线形状 / 扫描线时机 / 孔径格栅 / 风格化 / 语言，键盘与鼠标都能改，Esc 返回。 */',
    '    /** 设置页：扫描线 / 形状 / 时机 / 孔径格栅 / 风格化 / 背景滤镜 / 语言，键盘与鼠标都能改，Esc 返回。 */', 1)

s = s.replace('        int h = 212;', '        int h = 218;', 1)

s = s.replace(
    '''                Language.t("风格化：", "STYLE: ")
                        + Language.t(VideoSettings.style().label(), VideoSettings.style().labelEn()),
''',
    '''                Language.t("风格化：", "STYLE: ")
                        + Language.t(VideoSettings.style().label(), VideoSettings.style().labelEn()),
                Language.t("背景滤镜：", "BACKDROP FILTER: ")
                        + Language.t(VideoSettings.backdropFilter().label(),
                        VideoSettings.backdropFilter().labelEn()),
''', 1)

s = s.replace('            int ry = y + 40 + i * 24;', '            int ry = y + 40 + i * 22;', 1)
s = s.replace('"UP/DOWN SELECT   LEFT/RIGHT CHANGE"), x + 12, y + 178, 12, Palette.LINE_FAINT);',
              '"UP/DOWN SELECT   LEFT/RIGHT CHANGE"), x + 12, y + 186, 12, Palette.LINE_FAINT);', 1)
s = s.replace('Language.t("F11 全屏", "F11 FULLSCREEN"), x + 12, y + 194, 12, Palette.LINE_FAINT);',
              'Language.t("F11 全屏", "F11 FULLSCREEN"), x + 12, y + 202, 12, Palette.LINE_FAINT);', 1)

s = s.replace(
    '    /** 0=扫描线开关 1=扫描线形状 2=扫描线时机 3=孔径格栅开关 4=风格化轮换 5=界面语言。 */',
    '    /** 0=扫描线开关 1=形状 2=时机 3=孔径格栅 4=风格化 5=背景滤镜 6=界面语言。 */', 1)

s = s.replace('''            case 4 -> VideoSettings.cycleStyle();
            default -> Language.toggle();''',
              '''            case 4 -> VideoSettings.cycleStyle();
            case 5 -> VideoSettings.cycleBackdropFilter();
            default -> Language.toggle();''', 1)

p.write_text(s, encoding='utf-8')
print('StartScreen patched')
