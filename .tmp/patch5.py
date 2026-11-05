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

patch('src/main/java/com/hysx/salvosequence/screen/FleetArrangementScreen.java', [
("""    private void resetArrangement() {
        ShipArtStore.resetFleet(fleet.id());""",
"""    private void resetArrangement() {
        if (boundFleet != null) {
            say("关卡内不能恢复自动铺位：会影响这局的摆位");
            return;
        }
        ShipArtStore.resetFleet(fleet.id());"""),
("""        for (int i = 0; i < ShipCatalog.starterCount(); i++) {
            int index = i;
            sink.add(PANEL_X, FLEET_ROW_Y + i * ROW_H, PANEL_W - 20, ROW_H - 2, () -> {
                fleetIndex = index;
                reload();
                say("换舰队：" + ShipCatalog.starter(index).nameZh());
            });
        }""",
"""        if (boundFleet == null) {
            for (int i = 0; i < ShipCatalog.starterCount(); i++) {
                int index = i;
                sink.add(PANEL_X, FLEET_ROW_Y + i * ROW_H, PANEL_W - 20, ROW_H - 2, () -> {
                    fleetIndex = index;
                    reload();
                    say("换舰队：" + ShipCatalog.starter(index).nameZh());
                });
            }
        }"""),
("""        String title = Language.t("舰队编排", "FLEET LAYOUT");
        zh(buffer, title, 8, 5, 14, Palette.CYAN);
        double x = 8 + textWidth(title, 14) + 14;""",
"""        String title = forRun()
                ? Language.t("战术休整 · 编队", "LULL · FLEET")
                : Language.t("舰队编排", "FLEET LAYOUT");
        zh(buffer, title, 8, 5, 14, Palette.CYAN);
        if (forRun()) {
            zh(buffer, Language.t(fleet.size() + " 艘 · 摆好按「回战场」",
                            fleet.size() + " SHIPS · PRESS BACK TO BATTLE"),
                    8 + textWidth(title, 14) + 14, 6, 12, Palette.LINE_DIM);
            return;
        }
        double x = 8 + textWidth(title, 14) + 14;"""),
("""    private void drawPanel(CanvasBuffer buffer, PixelArt art) {
        int y = FLEET_ROW_Y - 18;
        zh(buffer, Language.t("舰队（[ ] 或点一行切换）", "FLEET ([ ] OR CLICK A ROW TO SWITCH)"),
                PANEL_X, y, 12, Palette.LINE_DIM);
        for (int i = 0; i < ShipCatalog.starterCount(); i++) {""",
"""    private void drawPanel(CanvasBuffer buffer, PixelArt art) {
        int y = FLEET_ROW_Y - 18;
        if (!forRun()) {
            zh(buffer, Language.t("舰队（[ ] 或点一行切换）", "FLEET ([ ] OR CLICK A ROW TO SWITCH)"),
                    PANEL_X, y, 12, Palette.LINE_DIM);
        }
        for (int i = 0; i < (forRun() ? 0 : ShipCatalog.starterCount()); i++) {"""),
("""        // 船列表
        int listY = FLEET_ROW_Y + ShipCatalog.starterCount() * ROW_H + 12;""",
"""        // 船列表（关卡内没有上面的舰队列表，直接顶到面板开头）
        int listY = forRun()
                ? FLEET_ROW_Y - 4
                : FLEET_ROW_Y + ShipCatalog.starterCount() * ROW_H + 12;"""),
("""        button(art, buffer, CONFIRM_X, BTN_Y, CONFIRM_W, BTN_H,
                Language.t("确定（Enter）", "OK (ENTER)"), Palette.OK);
        button(art, buffer, RESET_X, BTN_Y, RESET_W, BTN_H,
                Language.t("恢复自动铺位（R）", "AUTO LAYOUT (R)"), Palette.CYAN_DIM);""",
"""        if (forRun()) {
            button(art, buffer, CONFIRM_X, BTN_Y, PANEL_W - 20, BTN_H,
                    Language.t("回战场（Enter）", "BACK TO BATTLE (ENTER)"), Palette.OK);
            return;
        }
        button(art, buffer, CONFIRM_X, BTN_Y, CONFIRM_W, BTN_H,
                Language.t("确定（Enter）", "OK (ENTER)"), Palette.OK);
        button(art, buffer, RESET_X, BTN_Y, RESET_W, BTN_H,
                Language.t("恢复自动铺位（R）", "AUTO LAYOUT (R)"), Palette.CYAN_DIM);"""),
("""    private void drawFooter(CanvasBuffer buffer) {
        zh(buffer, Language.t(
                "拖动船 · 方向键微调 · 1-9 选择 · R 复位 · [ ] 换舰队 · Enter 确定 · ESC 返回",
                "DRAG SHIP · ARROWS NUDGE · 1-9 SELECT · R RESET · [ ] FLEET · ENTER OK · ESC BACK"),
                8, 340, 12, Palette.LINE_DIM);""",
"""    private void drawFooter(CanvasBuffer buffer) {
        if (forRun()) {
            zh(buffer, Language.t("拖动船 · 方向键微调 · 1-9 选择 · Enter 回战场 · ESC 返回",
                            "DRAG SHIP · ARROWS NUDGE · 1-9 SELECT · ENTER BATTLE · ESC BACK"),
                    8, 340, 12, Palette.LINE_DIM);
            return;
        }
        zh(buffer, Language.t(
                "拖动船 · 方向键微调 · 1-9 选择 · R 复位 · [ ] 换舰队 · Enter 确定 · ESC 返回",
                "DRAG SHIP · ARROWS NUDGE · 1-9 SELECT · R RESET · [ ] FLEET · ENTER OK · ESC BACK"),
                8, 340, 12, Palette.LINE_DIM);""")])
