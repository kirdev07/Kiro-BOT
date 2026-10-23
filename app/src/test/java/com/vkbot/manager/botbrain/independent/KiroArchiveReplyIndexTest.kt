package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KiroArchiveReplyIndexTest {
    @Test fun prefersContextSpecificAnswer() {
        val index = KiroArchiveReplyIndex(KiroAnswerArchive.importLegacy(
            "привет|общий\nпривет|особый||||ready|"
        ))
        assertEquals("общий", index.find("привет", "") { true }?.answer)
        assertEquals("особый", index.find("привет", "ready") { true }?.answer)
    }

    @Test fun skipsMediaAndPatterns() {
        val index = KiroArchiveReplyIndex(KiroAnswerArchive.importLegacy(
            "regex:.*|bad\nпривет|media|photo\nпривет|ok"
        ))
        assertEquals("ok", index.find("привет", "") { true }?.answer)
        assertNull(index.find("что-то", "") { true })
    }

    @Test fun appliesCallerRepetitionFilter() {
        val index = KiroArchiveReplyIndex(KiroAnswerArchive.importLegacy("привет|one\nпривет|two"))
        assertEquals("two", index.find("привет", "") { it.lineNumber != 1 }?.answer)
    }
}
