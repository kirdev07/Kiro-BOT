package com.vkbot.manager.botbrain.independent

import org.junit.Assert.*
import org.junit.Test

class KiroSafeMaskTest {
    @Test fun matchesAnchoredWildcard() {
        assertTrue(KiroSafeMask.matches("привет *", "Привет друг"))
        assertFalse(KiroSafeMask.matches("привет *", "эй привет друг"))
        assertTrue(KiroSafeMask.matches("*привет*", "ну привет всем"))
    }

    @Test fun refusesOversizedInputAndPatterns() {
        assertFalse(KiroSafeMask.matches("*", "а".repeat(2049)))
        assertFalse(KiroSafeMask.matches("*".repeat(17), "тест"))
        assertFalse(KiroSafeMask.matches("regex:.*", "тест"))
    }

    @Test fun exactAnswersBeatWildcard() {
        val rows = KiroAnswerArchive.importLegacy("привет *|маска\nпривет друг|точный")
        assertEquals("точный", KiroArchiveConversation(rows).respond("привет друг"))
        assertEquals("маска", KiroArchiveConversation(rows).respond("привет мир"))
    }

    @Test fun legacyNewlineDecoded() {
        assertEquals("один\nдва", KiroArchiveConversation(
            KiroAnswerArchive.importLegacy("привет|один<br>два")
        ).respond("привет"))
    }
}
