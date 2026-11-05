package com.hysx.salvosequence.graphics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 素材库「不依赖 JavaFX 工具包」的那部分行为核对。
 *
 * <p>为什么只测兜底：{@code javafx.scene.image.Image} 必须等 JavaFX 工具包起来之后才能用，
 * 在 surefire 里读 PNG 会抛 {@code RuntimeException: Internal graphics not initialized yet}，
 * 于是 {@link SpriteStore} 会合理地退化成程序化占位图。也就是说
 * <b>「类路径上那张 PNG 有没有被用上」在单元测试里测不出来</b>，那件事要用游戏里的
 * {@code mvn -Paudit javafx:run -Daudit.sprites=true} 或者在精灵实验室里看"程序化/PNG"标记。</p>
 *
 * <p>点阵肉眼核对用 {@link SpriteDumpTest}。</p>
 */
class SpriteStoreTest {

    @Test
    void 每个角色都能拿到非空精灵表() {
        for (String role : SpriteFactory.ALL_ROLES) {
            SpriteSheet sheet = SpriteStore.get().sheet(role);
            assertNotNull(sheet, "角色 " + role + " 没有精灵表");
            assertFalse(sheet.frames().isEmpty(), "角色 " + role + " 帧数为 0");
        }
    }

    @Test
    void 帧数与文件名约定一致() {
        // 这些数字来自 SpriteFactory 里的生成代码，改生成代码时这里要一起改
        assertEquals(4, SpriteStore.get().sheet(SpriteFactory.PLAYER_FIGHTER).frameCount());
        assertEquals(6, SpriteStore.get().sheet(SpriteFactory.FX_EXPLOSION).frameCount());
        assertEquals(1, SpriteStore.get().sheet(SpriteFactory.FOE_WEDGE).frameCount());
    }

    @Test
    void 单帧尺寸与角色定义一致() {
        SpriteSheet bolt = SpriteStore.get().sheet(SpriteFactory.PROJECTILE_PLAYER_BOLT);
        assertEquals(6, bolt.frameWidth());
        assertEquals(1, bolt.frameHeight());
    }

    @Test
    void 未知角色兜底成告警块而不是抛异常() {
        SpriteSheet sheet = SpriteStore.get().sheet("no_such_role");
        assertNotNull(sheet, "未知角色也必须给一张图，不能返回 null");
        assertTrue(sheet.frameCount() >= 1);
    }

    @Test
    void 帧索引越界会自动取模() {
        SpriteSheet sheet = SpriteStore.get().sheet(SpriteFactory.PLAYER_FIGHTER);
        assertEquals(sheet.frame(0).name(), sheet.frame(sheet.frameCount()).name());
    }
}
