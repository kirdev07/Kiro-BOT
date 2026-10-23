package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Test

class KiroArchiveAuditTest {
    @Test fun countsReadyAndUnsupportedRows() {
        val rows = KiroAnswerArchive.importLegacy(
            "привет|ответ\nregex:.*|шаблон\nфото|снимок|photo\nпривет|ответ"
        )
        val report = KiroArchiveAudit.inspect(rows)
        assertEquals(4, report.total)
        assertEquals(2, report.ready)
        assertEquals(1, report.patterns)
        assertEquals(1, report.media)
        assertEquals(1, report.duplicatePairs)
    }

    @Test fun countsContextAndLimitsSeparately() {
        val report = KiroArchiveAudit.inspect(KiroAnswerArchive.importLegacy(
            "привет|ответ||2||previous|next"
        ))
        assertEquals(1, report.contextual)
        assertEquals(1, report.repeatLimits)
        assertEquals(1, report.ready)
    }

    @Test fun rejectsInvalidLimitsFromReadyCount() {
        val report = KiroArchiveAudit.inspect(KiroAnswerArchive.importLegacy(
            "привет|ответ||bad\nпока|ответ||-1"
        ))
        assertEquals(2, report.invalidLimits)
        assertEquals(0, report.ready)
    }
    @Test fun mediaOnlyRowIsReadyButUnsearchableRowIsNot() {
        val rows = KiroAnswerArchive.importLegacy("фото||https://example.com/a.jpg\n!!!|ответ")
        val report = KiroArchiveAudit.inspect(rows)
        assertEquals(2, report.total)
        assertEquals(1, report.ready)
        assertEquals(1, report.media)
    }
    @Test fun sameQuestionDifferentMediaOrContextIsNotDuplicate() {
        val rows = KiroAnswerArchive.importLegacy(
            "фото|вот|https://example.com/a.jpg\\n" +
                "фото|вот|https://example.com/b.jpg\\n" +
                "фото|вот|https://example.com/a.jpg|||after|"
        )
        assertEquals(0, KiroArchiveAudit.inspect(rows).duplicatePairs)
    }

}
