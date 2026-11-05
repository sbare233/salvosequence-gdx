import io, sys

def patch(path, pairs):
    s = io.open(path, encoding='utf-8').read()
    for old, new in pairs:
        n = s.count(old)
        if n != 1:
            print("FAIL", path, "count=%d" % n, repr(old[:80]))
            sys.exit(1)
        s = s.replace(old, new)
    io.open(path, 'w', encoding='utf-8', newline='').write(s)
    print("ok", path)

patch('src/main/java/com/hysx/salvosequence/screen/CardLoopScreen.java', [(
"""            case "LEFT", "RIGHT" -> {
                if (restTab == 0) {
                    shuffleRestShip(code.equals("RIGHT") ? 1 : -1);
                }
            }
            case "SPACE" -> {
                if (restTab == 1) {
                    toggleRestCard();
                }
            }
            case "ENTER" -> {
                if (restTab == 1) {
                    toggleRestCard();
                } else {
                    departNextWave();
                }
            }""",
"""            case "SPACE" -> departNextWave();
            case "ENTER" -> {
                if (restTab == 1) {
                    toggleRestCard();
                } else {
                    // 编队页不自己画拖拽：借关卡里那套编排界面，改完再回来
                    return ScreenContext.go(ScreenContext.Target.ARRANGE_RUN);
                }
            }"""),
("""        zh(buffer, Language.t("TAB 换页 · 上下选择 · 左右挪船 · 回车出发 / 禁牌 · ESC 设置",
                        "TAB SWITCH · UP/DOWN PICK · LEFT/RIGHT MOVE · ENTER GO / BAN · ESC"),
                20, REST_FOOT_TOP, 12, Palette.LINE_DIM);""",
"""        zh(buffer, Language.t("TAB 换页 · 上下选择 · 回车调编队 / 禁牌 · 空格出发 · ESC 设置",
                        "TAB SWITCH · UP/DOWN PICK · ENTER ARRANGE / BAN · SPACE GO · ESC"),
                20, REST_FOOT_TOP, 12, Palette.LINE_DIM);"""),
("""        zh(buffer, Language.t("编队：从上到下就是承伤顺序，最上面那艘先挨打",
                        "FLEET: TOP TAKES HITS FIRST"),
                20, REST_CAPTION_TOP, 12, Palette.LINE_DIM);""",
"""        zh(buffer, Language.t("编队：回车进编排界面拖船，回来接着打",
                        "FLEET: ENTER TO DRAG SHIPS IN THE LAYOUT SCREEN"),
                20, REST_CAPTION_TOP, 12, Palette.LINE_DIM);"""),
("""        zh(buffer, Language.t("← → 把选中的船整体挪一格：想让谁扛伤害，就把他往前挪",
                        "LEFT/RIGHT NUDGES THE SHIP: MOVE THE TANK FORWARD"),
                20, REST_NOTE_TOP, 12, Palette.LINE_FAINT);""",
"""        zh(buffer, Language.t("最上面那艘先挨打——想让谁扛伤害，就在编排界面把他挪到最前",
                        "TOP TAKES HITS FIRST: DRAG YOUR TANK TO THE FRONT"),
                20, REST_NOTE_TOP, 12, Palette.LINE_FAINT);"""),
("""            sink.add(16, top - 3, 604, REST_ROW_H - 2, () -> {
                restCursor = row;
                if (restTab == 1) {
                    toggleRestCard();
                }
                touch();
            });""",
"""            sink.add(16, top - 3, 604, REST_ROW_H - 2, () -> {
                restCursor = row;
                if (restTab == 1) {
                    toggleRestCard();
                } else {
                    pendingTransition = ScreenContext.go(ScreenContext.Target.ARRANGE_RUN);
                }
                touch();
            });"""),
("""        sink.add(REST_DEPART_X, REST_DEPART_Y, REST_DEPART_W, REST_DEPART_H, this::departNextWave);""",
"""        sink.add(REST_DEPART_X, REST_DEPART_Y, REST_DEPART_W, REST_DEPART_H, this::departNextWave);
        if (restTab == 0) {
            // 编队页底部的入口：点它也进编排界面
            sink.add(20, REST_NOTE_TOP - 6, 300, 22, () ->
                    pendingTransition = ScreenContext.go(ScreenContext.Target.ARRANGE_RUN));
        }""")])
