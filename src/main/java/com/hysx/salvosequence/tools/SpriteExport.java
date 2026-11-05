package com.hysx.salvosequence.tools;

import com.hysx.salvosequence.graphics.SpriteFactory;
import com.hysx.salvosequence.graphics.SpriteSheet;
import com.hysx.salvosequence.graphics.SpriteSheetPacker;
import com.hysx.salvosequence.graphics.SpriteStore;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 素材导出：把 {@link SpriteFactory} 的程序化占位图写成符合命名约定的 PNG。
 *
 * <p>用法（在项目根目录）：</p>
 * <pre>
 * mvn compile exec:java -Dexec.mainClass=com.hysx.salvosequence.tools.SpriteExport
 *                                 导出到默认资源目录
 * 加 -Dsprites.out=target/sprites 导出到别处（试跑用）
 * </pre>
 *
 * <p>默认输出 {@code src/main/resources/sprites/}，因为那是游戏运行时
 * 真正会去类路径找的位置。导出后 {@link SpriteStore} 就会优先用这些 PNG，
 * 程序化占位图退化成兜底。</p>
 *
 * <p>换成手绘素材的流程：保持文件名里的「帧数」和「单帧尺寸」标记正确，
 * 直接覆盖同名文件即可；如果手绘尺寸和占位图不同，游戏会自动按新文件名里的
 * 尺寸切帧，不用改代码。</p>
 */
public final class SpriteExport {

    /** 默认输出目录（相对项目根）。 */
    private static final String DEFAULT_OUT = "src/main/resources/sprites";

    public void start() throws IOException {
        String outArg = System.getProperty("sprites.out", DEFAULT_OUT);
        Path outRoot = Path.of(outArg).toAbsolutePath().normalize();
        System.out.println("[SpriteExport] 输出目录: " + outRoot);

        int written = 0;
        int failed = 0;
        for (String role : SpriteFactory.ALL_ROLES) {
            SpriteSheet sheet = SpriteFactory.create(role);
            if (sheet == null) {
                System.out.println("  [跳过] " + role + "（没有程序化素材）");
                continue;
            }
            String rel = sheet.frameCount() == 1
                    ? SpriteSheetPacker.suggestedFileNameForStill(role,
                    sheet.frameWidth(), sheet.frameHeight())
                    : SpriteSheetPacker.suggestedFileName(sheet);
            Path target = outRoot.resolve(rel);
            try {
                Files.createDirectories(target.getParent());
                var image = SpriteSheetPacker.pack(sheet);
                PngWriter.write(image, new File(target.toString()));
                int kb = (int) Math.round(Files.size(target) / 1024.0);
                System.out.printf("  [写出] %-46s %d帧 %dx%d  %d KB%n",
                        rel, sheet.frameCount(), sheet.frameWidth(), sheet.frameHeight(), kb);
                written++;
            } catch (Exception ex) {
                System.out.println("  [失败] " + rel + " -> " + ex);
                failed++;
            }
        }
        System.out.println("[SpriteExport] 完成: 写出 " + written + " 个，失败 " + failed + " 个");

    }

    public static void main(String[] args) throws Exception {
        new SpriteExport().start();
    }
}
