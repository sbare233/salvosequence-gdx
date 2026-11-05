package com.hysx.salvosequence.tools;

import com.hysx.salvosequence.core.CanvasBuffer;
import com.hysx.salvosequence.core.PixelArt;
import com.hysx.salvosequence.run.Run;
import com.hysx.salvosequence.screen.CardLoopScreen;

import java.io.File;
import java.lang.reflect.Field;

/**
 * 星图盘缘诊断（无窗口、纯 CPU）：把星图一帧画进 CanvasBuffer，
 * 导出合成帧 PNG，并打印 galaxyCache 源层 alpha 剖面与合成帧亮度剖面，
 * 用来定位"盘缘硬边"到底出在源层 alpha、拜耳抖动还是合成观感。
 *
 * <p>跑法：{@code mvn -q compile exec:java -Dexec.mainClass=com.hysx.salvosequence.tools.MapEdgeProbe}
 * （探针主体跑在守护线程上，主线程 90 秒看门狗强制退出，绝不可能挂住。）</p>
 */
public final class MapEdgeProbe {

    public static void main(String[] args) throws Exception {
        Thread worker = new Thread(MapEdgeProbe::run, "map-edge-probe");
        worker.setDaemon(true);
        worker.start();
        // 看门狗：无论如何 90 秒后强杀 JVM（非守护线程再也拖不住进程）
        long deadline = System.currentTimeMillis() + 90_000;
        while (worker.isAlive() && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        if (worker.isAlive()) {
            System.out.println("[probe] 超时强退（90s）");
        }
        System.out.flush();
        Runtime.getRuntime().halt(0);
    }

    private static void log(String line) {
        System.out.println("[probe] " + line);
        System.out.flush();
    }

    private static void run() {
        try {
            System.setProperty("card.seed", "20240601");
            System.setProperty("card.trace", "off");

            log("1/6 构造 CardLoopScreen ...");
            CardLoopScreen screen = new CardLoopScreen();
            log("2/6 推进到星图 ...");
            screen.debugAdvanceTo(Run.Stage.MAP);
            log("2/6 完成，stage = " + screen.run().stage());
            // 与游戏最低缩放一致（滚轮提示 x1.3 已到最小），保证截图口径同玩家所见
            Field zoomSet = CardLoopScreen.class.getDeclaredField("starZoom");
            zoomSet.setAccessible(true);
            zoomSet.setDouble(screen, 1.3);

            log("3/6 CPU 画一帧 ...");
            CanvasBuffer buffer = new CanvasBuffer(640, 360);
            buffer.beginFrame();
            screen.draw(buffer);
            int[] frame = buffer.art().buffer();
            log("3/6 完成");

            log("4/6 导出合成帧 PNG ...");
            File png = new File("target/probe-frame.png");
            PngWriter.write(frame, buffer.width(), buffer.height(), png);
            log("4/6 -> " + png.getAbsolutePath());

            log("5/6 读 galaxyCache ...");
            Field galF = CardLoopScreen.class.getDeclaredField("galaxyCache");
            galF.setAccessible(true);
            int[] gal = (int[]) galF.get(screen);
            if (gal == null) {
                log("galaxyCache 未生成（星图没画？）");
                return;
            }
            int gw = 560;
            int cx = gw / 2;
            int cy = gh() / 2;

            Field zoomF = CardLoopScreen.class.getDeclaredField("starZoom");
            zoomF.setAccessible(true);
            double zoom = zoomF.getDouble(screen);
            Field fcx = CardLoopScreen.class.getDeclaredField("starCenterX");
            fcx.setAccessible(true);
            double sx = fcx.getDouble(screen);
            Field fcy = CardLoopScreen.class.getDeclaredField("starCenterY");
            fcy.setAccessible(true);
            double sy = fcy.getDouble(screen);
            log(String.format("zoom=%.2f  center=(%.1f, %.1f)", zoom, sx, sy));

            // 1) 源层 alpha 剖面：过盘心的水平线，从盘心向右
            log("galaxyCache 源层 ARGB 剖面（d = 椭圆归一距离）:");
            StringBuilder line = new StringBuilder();
            line = new StringBuilder();
            for (double d = 0.50; d < 1.05; d += 0.05) {
                int x = cx + (int) Math.rint(d * 280.0);
                if (x >= gw) { break; }
                int p = gal[cy * gw + x];
                line.append(String.format("%.2f:[%02X%02X%02X%02X] ", d,
                        (p >>> 24) & 0xFF, (p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF));
            }
            log(line.toString());

            Field ovF = CardLoopScreen.class.getDeclaredField("galaxyOverlay");
            ovF.setAccessible(true);
            int[] ov = (int[]) ovF.get(screen);
            if (ov != null) {
                log("galaxyOverlay ARGB 剖面（同行）:");
                line = new StringBuilder();
                for (double d = 0.50; d < 1.05; d += 0.05) {
                    int x = cx + (int) Math.rint(d * 280.0);
                    if (x >= gw) { break; }
                    int p = ov[cy * gw + x];
                    line.append(String.format("%.2f:[%02X%02X%02X%02X] ", d,
                            (p >>> 24) & 0xFF, (p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF));
                }
                log(line.toString());
            }

            // 2) 合成帧像素：星图中心那一行，逐点十六进制
            int row = Math.max(0, Math.min(359, (int) Math.rint(sy)));
            log("合成帧 ARGB 剖面 y=" + row + ":");
            line = new StringBuilder();
            for (double d = 0.50; d < 1.10; d += 0.05) {
                int x = (int) Math.rint(sx + d * 280.0 * zoom);
                if (x >= 640) { break; }
                int p = frame[row * 640 + x];
                line.append(String.format("%.2f:[%02X%02X%02X%02X] ", d,
                        (p >>> 24) & 0xFF, (p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF));
            }
            log(line.toString());

            // 2b) 原生逐像素显示亮度剖面（带 alpha 权重，不平均——断崖若在数据里，
            //     这里会以台阶形式现形）
            row = Math.max(0, Math.min(359, (int) Math.rint(sy)));
            log("原生逐像素显示亮度 y=" + row + "（cmax×alpha/255，每 3px）:");
            line = new StringBuilder();
            for (int x = (int) sx; x < 640; x += 3) {
                int pp = frame[row * 640 + x];
                int cmax = Math.max(pp >> 16 & 0xFF, Math.max(pp >> 8 & 0xFF, pp & 0xFF));
                double dd = (x - sx) / (280.0 * 1.3);
                line.append(String.format("%.2f:%d ", dd, cmax * ((pp >>> 24) & 0xFF) / 255));
            }
            log(line.toString());

            // 3) 带状平均显示亮度剖面：上下各 20 行平均掉斑点噪声；
            //    显示亮度 = max(R,G,B) × alpha/255（缓冲是非预乘的，黑底直显）
            row = Math.max(0, Math.min(359, (int) Math.rint(sy)));
            log("带状平均显示亮度剖面 y=" + row + "±20（每 5px 一格，0~255）:");
            line = new StringBuilder();
            for (int x = (int) sx; x < 640; x += 5) {
                double dd = Math.hypot((x - sx) / (280.0 * zoom), 0) ;
                long acc = 0;
                int cnt = 0;
                for (int ry = row - 20; ry <= row + 20; ry++) {
                    if (ry < 0 || ry >= 360) {
                        continue;
                    }
                    int p = frame[ry * 640 + x];
                    int cmax = Math.max(p >> 16 & 0xFF, Math.max(p >> 8 & 0xFF, p & 0xFF));
                    acc += cmax * ((p >>> 24) & 0xFF) / 255;
                    cnt++;
                }
                line.append(String.format("%.2f:%d ", dd, cnt == 0 ? 0 : acc / cnt));
            }
            log(line.toString());

            // 4) 盘缘放大图：右缘区域 3 倍最近邻，看渐隐的实际观感
            int cropX = (int) Math.rint(sx + 0.62 * 280 * zoom);
            int cropY = (int) Math.rint(sy - 95);
            int cropW = 130;
            int cropH = 190;
            PixelArt crop = new PixelArt(cropW * 3, cropH * 3);
            crop.fillRect(0, 0, cropW * 3, cropH * 3, com.hysx.salvosequence.core.Color.BLACK);
            for (int y = 0; y < cropH; y++) {
                for (int x = 0; x < cropW; x++) {
                    int sxp = Math.min(639, cropX + x);
                    int syp = Math.min(359, Math.max(0, cropY + y));
                    crop.blitArgbTile(frame, 640, 360, sxp, syp,
                            x * 3, y * 3, 3, 3, true, false, false, 1.0);
                }
            }
            File cropPng = new File("target/map-edge-probe-rim3x.png");
            crop.savePng(cropPng);
            log("4b/6 盘缘 3x 放大 -> " + cropPng.getAbsolutePath());

            // 5) 纯盘面层（黑底直贴 galaxyCache）： attribution——边是否由盘层自带
            PixelArt layer = new PixelArt(gw, gh());
            layer.fillRect(0, 0, gw, gh(), com.hysx.salvosequence.core.Color.BLACK);
            layer.blitArgbTile(gal, gw, gh(), 0, 0, 0, 0, gw, gh(), true, false, false, 1.0);
            File layerPng = new File("target/map-edge-probe-layer.png");
            layer.savePng(layerPng);
            log("4c/6 纯盘面层 -> " + layerPng.getAbsolutePath());
            log("6/6 完成");
        } catch (Throwable t) {
            log("出错: " + t);
            t.printStackTrace(System.out);
        } finally {
            System.out.flush();
            Runtime.getRuntime().halt(0);
        }
    }

    private static int gh() {
        return 350;
    }
}
