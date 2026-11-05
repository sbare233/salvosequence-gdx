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

patch('src/main/java/com/hysx/salvosequence/ui/ScreenManager.java', [(
"""    private Screen screenBehind;""",
"""    private Screen screenBehind;
    /** 当前这一局的关卡界面：休整时去编排舰队再回来，要复用同一个实例，Run 才不会被重开。 */
    private CardLoopScreen loopScreen;""")])

patch('src/main/java/com/hysx/salvosequence/screen/FleetArrangementScreen.java', [(
"""    private int fleetIndex;
    private Fleet fleet;""",
"""    private int fleetIndex;
    private Fleet fleet;
    /** 从关卡内「战术休整」进来时绑定的舰队；非 null 表示不是菜单里的编排界面。 */
    private final Fleet boundFleet;"""),
("""    public FleetArrangementScreen() {
        reload();
    }""",
"""    public FleetArrangementScreen() {
        this.boundFleet = null;
        reload();
    }

    /**
     * 关卡内「战术休整」用的构造器：直接编排这一局正在用的舰队。
     * 返回时回的是那局关卡本身（{@link ScreenContext.Target#CARD_RESUME}），不会重开一局。
     */
    public FleetArrangementScreen(Fleet fleet) {
        this.boundFleet = fleet;
        this.fleet = fleet;
        this.selected = 0;
        ShipPainter.reload();
        hotspotsDirty = true;
    }

    /** 是否是从关卡里进来改编队的。 */
    public boolean forRun() {
        return boundFleet != null;
    }"""),
("""    private void reload() {
        fleet = ShipCatalog.starter(fleetIndex).fleet();""",
"""    private void reload() {
        if (boundFleet != null) {
            // 关卡内：编排的就是这一局舰队，别用存档覆盖它
            fleet = boundFleet;
            selected = 0;
            dragging = false;
            ShipPainter.reload();
            hotspotsDirty = true;
            return;
        }
        fleet = ShipCatalog.starter(fleetIndex).fleet();"""),
("""    public void confirm() {
        boolean saved = ShipArtStore.trySaveFleet(fleet);""",
"""    public void confirm() {
        if (boundFleet != null) {
            // 关卡内改编队：只改这一局，不写盘、不跳外观设计，直接回战斗界面
            say("编队已调整");
            pending = ScreenContext.go(ScreenContext.Target.CARD_RESUME);
            return;
        }
        boolean saved = ShipArtStore.trySaveFleet(fleet);"""),
("""            case "ESCAPE" -> {
                return ScreenContext.go(ScreenContext.Target.START);""",
"""            case "ESCAPE" -> {
                if (boundFleet != null) {
                    return ScreenContext.go(ScreenContext.Target.CARD_RESUME);
                }
                return ScreenContext.go(ScreenContext.Target.START);"""),
("""    private void shiftFleet(int delta) {
        int count = ShipCatalog.starterCount();""",
"""    private void shiftFleet(int delta) {
        if (boundFleet != null) {
            say("关卡内不能换舰队");
            return;
        }
        int count = ShipCatalog.starterCount();""")])
