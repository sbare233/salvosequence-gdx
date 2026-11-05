package com.hysx.salvosequence.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 语言开关：默认中文，可切换、可回退。 */
class LanguageTest {

    @AfterEach
    void reset() {
        Language.set(Language.ZH);
    }

    @Test
    void defaultsToChinese() {
        assertEquals(Language.ZH, Language.current());
        assertTrue(Language.isChinese());
        assertFalse(Language.isEnglish());
        assertEquals("齐射序列", Language.t("齐射序列", "SALVO SEQUENCE"));
    }

    @Test
    void togglesBetweenTheTwoLanguages() {
        Language.toggle();
        assertEquals(Language.EN, Language.current());
        assertTrue(Language.isEnglish());
        assertEquals("SALVO SEQUENCE", Language.t("齐射序列", "SALVO SEQUENCE"));
        Language.toggle();
        assertEquals(Language.ZH, Language.current());
    }

    @Test
    void nullFallsBackToChinese() {
        Language.set(Language.EN);
        Language.set(null);
        assertEquals(Language.ZH, Language.current());
    }
}
