package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KiroAnswerArchiveTest {
    @Test fun preservesAllSevenLegacyFields() {
        val original = "вопрос|ответ|photo:abc|3|9|before|after"
        val rows = KiroAnswerArchive.importLegacy(original)
        assertEquals(1, rows.size)
        assertEquals("photo:abc", rows[0].attachments)
        assertEquals("3", rows[0].repetitionLimit)
        assertEquals("9", rows[0].usageCount)
        assertEquals("before", rows[0].requiredContext)
        assertEquals("after", rows[0].resultContext)
        assertEquals(rows, KiroAnswerArchive.parse(KiroAnswerArchive.export(rows)))
    }

    @Test fun keepsRegexAndMasksAsText() {
        val rows = KiroAnswerArchive.importLegacy("regex:привет.*|ответ\nкак *|ответ2")
        assertEquals("regex:привет.*", rows[0].question)
        assertEquals("как *", rows[1].question)
    }

    @Test fun skipsCommentsAndTracksSourceLine() {
        val rows = KiroAnswerArchive.importLegacy("# header\n\nпривет|ответ")
        assertEquals(3, rows.single().lineNumber)
    }

    @Test fun preservesEscapedControlCharacters() {
        val rows = listOf(KiroAnswerArchive.Row(1, "что\tэто", "ответ\nдва", "", "", "", "", ""))
        assertEquals(rows, KiroAnswerArchive.parse(KiroAnswerArchive.export(rows)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedRows() {
        KiroAnswerArchive.importLegacy("invalid")
    }
}
