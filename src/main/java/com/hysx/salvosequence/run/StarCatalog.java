package com.hysx.salvosequence.run;

import java.util.List;

/**
 * 真实亮星表（J2000）：中文星名 + 赤经（小时）+ 赤纬（度）。
 *
 * <p>星图是一张虚构的银河盘面，没有真正的天球坐标；为了让关卡名字有出处，
 * 盘面被当成一张等距圆柱投影的全天图（见 {@link Run} 里的 {@code skyCoordOf}）：
 * 横轴对应赤经 0–24h，纵轴对应赤纬 +60–-60°。生成关卡时按这颗星在投影里的
 * 赤经/赤纬找最近的、尚未用过的真实恒星命名，所以名字会随种子和落点变化。
 *
 * <p>只收亮星（肉眼易认），并且把星名控制在 2–3 个汉字：星图上相邻关卡最小
 * 间距 34px，11px 字号下 4 个字的名字会和邻星标签压在一起。
 */
public final class StarCatalog {

    /** 一颗真实恒星：中文名、J2000 赤经（0–24h）、赤纬（-90–+90°）。 */
    public record NamedStar(String nameZh, double raHours, double decDeg) {
    }

    private StarCatalog() {
    }

    /** 约 60 颗全天最亮的恒星，按赤经大致排序。 */
    public static final List<NamedStar> STARS = List.of(
            new NamedStar("王良一", 0.1530, 59.150),
            new NamedStar("壁宿二", 0.1398, 29.091),
            new NamedStar("王良四", 0.6751, 56.537),
            new NamedStar("奎宿九", 1.1622, 35.621),
            new NamedStar("水委一", 1.6286, -57.237),
            new NamedStar("阁道三", 1.9066, 63.670),
            new NamedStar("娄宿三", 2.1195, 23.463),
            new NamedStar("北极星", 2.5303, 89.264),
            new NamedStar("天苑四", 2.9710, -40.305),
            new NamedStar("天囷一", 3.0378, 4.090),
            new NamedStar("天船二", 3.0797, 53.506),
            new NamedStar("天船三", 3.4054, 49.861),
            new NamedStar("毕宿五", 4.5987, 16.509),
            new NamedStar("参宿七", 5.2423, -8.202),
            new NamedStar("五车二", 5.2782, 45.998),
            new NamedStar("参宿五", 5.4188, 6.350),
            new NamedStar("五车五", 5.4381, 28.608),
            new NamedStar("参宿三", 5.5334, -0.299),
            new NamedStar("参宿二", 5.6036, -1.202),
            new NamedStar("参宿六", 5.7959, -9.670),
            new NamedStar("参宿四", 5.9195, 7.407),
            new NamedStar("参宿一", 5.6793, -1.943),
            new NamedStar("老人星", 6.3992, -52.696),
            new NamedStar("井宿三", 6.6285, 16.399),
            new NamedStar("天狼星", 6.7525, -16.716),
            new NamedStar("弧矢七", 6.9767, -28.972),
            new NamedStar("南河三", 7.6550, 5.225),
            new NamedStar("北河二", 7.5763, 31.888),
            new NamedStar("北河三", 7.7553, 28.026),
            new NamedStar("南船五", 9.2200, -69.717),
            new NamedStar("星宿一", 9.4595, -8.658),
            new NamedStar("文昌四", 9.5477, 51.677),
            new NamedStar("天枢", 11.0621, 61.751),
            new NamedStar("天璇", 11.0307, 56.383),
            new NamedStar("天玑", 11.8972, 53.695),
            new NamedStar("天权", 12.2571, 57.033),
            new NamedStar("玉衡", 12.9005, 55.960),
            new NamedStar("常陈一", 12.9338, 38.318),
            new NamedStar("开阳", 13.3988, 54.925),
            new NamedStar("摇光", 13.7923, 49.313),
            new NamedStar("角宿一", 13.4199, -11.161),
            new NamedStar("马腹一", 14.0637, -60.373),
            new NamedStar("大角星", 14.2610, 19.182),
            new NamedStar("南门二", 14.6600, -60.834),
            new NamedStar("心宿二", 16.4901, -26.432),
            new NamedStar("尾宿八", 17.5601, -37.104),
            new NamedStar("尾宿五", 17.6219, -42.998),
            new NamedStar("箕宿三", 18.4029, -34.385),
            new NamedStar("织女一", 18.6156, 38.784),
            new NamedStar("斗宿四", 18.9211, -26.297),
            new NamedStar("天津二", 19.7495, 45.131),
            new NamedStar("河鼓三", 19.7710, 10.613),
            new NamedStar("河鼓二", 19.8464, 8.868),
            new NamedStar("河鼓一", 19.9219, 6.407),
            new NamedStar("天津一", 20.3705, 40.257),
            new NamedStar("天津四", 20.6905, 45.280),
            new NamedStar("天钩五", 21.3097, 62.586),
            new NamedStar("虚宿一", 21.5259, -5.571),
            new NamedStar("危宿一", 22.0964, -0.320),
            new NamedStar("室宿二", 23.0629, 28.083),
            new NamedStar("室宿一", 23.0794, 15.205));

    /** 两颗星之间的球面角距（度）：用于“最接近坐标”的比较。 */
    public static double angleDeg(double ra1, double dec1, double ra2, double dec2) {
        double d1 = Math.toRadians(dec1);
        double d2 = Math.toRadians(dec2);
        double dra = Math.toRadians((ra1 - ra2) * 15.0);
        double cos = Math.sin(d1) * Math.sin(d2) + Math.cos(d1) * Math.cos(d2) * Math.cos(dra);
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
    }
}
