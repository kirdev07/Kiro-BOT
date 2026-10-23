package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KiroAnswerMigrationTest {
    @Test fun convertsSimpleAnswerRows() {
        val preview = KiroAnswerMigration.preview("привет|здравствуй\nкак дела|отлично")
        assertEquals(2, preview.imported)
        assertEquals(0, preview.skipped)
        assertEquals(2, KiroReplyFormat.parse(preview.converted).size)
    }

    @Test fun skipsUnsupportedMetadataInsteadOfLosingIt() {
        val preview = KiroAnswerMigration.preview("привет|ответ|photo.jpg|2|1|ctx|next")
        assertEquals(0, preview.imported)
        assertEquals(1, preview.skipped)
        assertTrue(preview.warnings.isNotEmpty())
    }

    @Test fun skipsPatternsThatNeedDedicatedMatcher() {
        val preview = KiroAnswerMigration.preview("regex:привет.*|ответ\nкак *|ответ")
        assertEquals(2, preview.skipped)
    }

    @Test fun ignoresCommentsAndBlankLines() {
        val preview = KiroAnswerMigration.preview("# comment\n\nпривет|ответ")
        assertEquals(1, preview.imported)
        assertEquals(0, preview.skipped)
    }
}
