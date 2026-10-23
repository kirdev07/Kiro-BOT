package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Test

/** Corrupt imports must be rejected before replacing a live conversation engine. */
class KiroAnswerArchiveValidationTest {
    private fun rejects(content: String) {
        try {
            KiroAnswerArchive.parse(content)
            throw AssertionError("Malformed archive was accepted")
        } catch (_: IllegalArgumentException) {
            // Expected: BotService handles this without replacing the active engine.
        }
    }

    @Test fun rejectsWrongHeaderAndMalformedRows() {
        rejects("legacy|answer")
        rejects("# KIRO-ANSWER-ARCHIVE/1\n1\tquestion\tanswer")
        rejects("# KIRO-ANSWER-ARCHIVE/1\n0\tq\ta\t\t\t\t\t")
        rejects("# KIRO-ANSWER-ARCHIVE/1\nabc\tq\ta\t\t\t\t\t")
    }

    @Test fun rejectsCorruptEscapes() {
        rejects("# KIRO-ANSWER-ARCHIVE/1\n1\tq\\x\ta\t\t\t\t\t")
        rejects("# KIRO-ANSWER-ARCHIVE/1\n1\tq\\\ta\t\t\t\t\t")
    }

    @Test fun preservesMultilineAndEscapedLegacyText() {
        val rows = KiroAnswerArchive.importLegacy("привет|строка<br>два<pipe>три")
        val restored = KiroAnswerArchive.parse(KiroAnswerArchive.export(rows))
        assertEquals(rows, restored)
    }
}
