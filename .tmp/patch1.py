import io, sys

def patch(path, pairs):
    s = io.open(path, encoding='utf-8').read()
    for old, new in pairs:
        n = s.count(old)
        if n != 1:
            print("FAIL", path, "count=%d" % n, repr(old[:70]))
            sys.exit(1)
        s = s.replace(old, new)
    io.open(path, 'w', encoding='utf-8', newline='').write(s)
    print("ok", path)

patch('src/main/java/com/hysx/salvosequence/screen/ScreenContext.java', [(
"""        /** 舰队编排界面：把船摆到 16×16 阵里（再进外观设计）。 */
        ARRANGE,""",
"""        /** 舰队编排界面：把船摆到 16×16 阵里（再进外观设计）。 */
        ARRANGE,
        /** 关卡内「战术休整」里调整编队：用同一套编排界面，改的是当前这局的舰队。 */
        ARRANGE_RUN,
        /** 回到还在进行的那局关卡（从编排界面回来时用，避免重开一局）。 */
        CARD_RESUME,""")])

patch('src/main/java/com/hysx/salvosequence/ui/ScreenManager.java', [(
"""            case ARRANGE -> new FleetArrangementScreen();""",
"""            case ARRANGE -> new FleetArrangementScreen();
            // 关卡内休整时调编队：同一套界面，改的是当前这局的舰队
            case ARRANGE_RUN -> loopScreen == null ? null : new FleetArrangementScreen(loopScreen.run().fleet());
            // 从编排回到那局：复用同一个实例，Run 不会被重开
            case CARD_RESUME -> loopScreen;"""),
("""            case CARD -> new CardLoopScreen();""",
"""            case CARD -> loopScreen = new CardLoopScreen();""")])
