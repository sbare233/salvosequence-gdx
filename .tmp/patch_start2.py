from pathlib import Path
p = Path('src/main/java/com/hysx/salvosequence/screen/StartScreen.java')
s = p.read_text(encoding='utf-8')
s = s.replace('        int h = 218;', '        int h = 224;', 1)
s = s.replace('"UP/DOWN SELECT   LEFT/RIGHT CHANGE"), x + 12, y + 186,',
              '"UP/DOWN SELECT   LEFT/RIGHT CHANGE"), x + 12, y + 192,', 1)
s = s.replace('Language.t("F11 全屏", "F11 FULLSCREEN"), x + 12, y + 202,',
              'Language.t("F11 全屏", "F11 FULLSCREEN"), x + 12, y + 208,', 1)
p.write_text(s, encoding='utf-8')
print('start panel tuned')
