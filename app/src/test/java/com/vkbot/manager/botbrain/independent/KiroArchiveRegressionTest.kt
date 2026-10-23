package com.vkbot.manager.botbrain.independent

import org.junit.Assert.*
import org.junit.Test

class KiroArchiveRegressionTest {
    @Test fun supportedMediaAndMasksCountAsReady() {
        val rows = KiroAnswerArchive.importLegacy(
            "привет|ответ|https://example.com/a.jpg\nпривет *|маска\nregex:.*|опасный"
        )
        val report = KiroArchiveAudit.inspect(rows)
        assertEquals(3, report.total)
        assertEquals(2, report.ready)
        assertEquals(1, report.media)
        assertEquals(2, report.patterns)
    }

    @Test fun invalidRepetitionLimitIsNotExecuted() {
        val rows = KiroAnswerArchive.importLegacy("привет|ошибка||invalid\nпривет|работает")
        assertEquals("работает", KiroArchiveConversation(rows).respond("привет"))
        assertEquals(1, KiroArchiveAudit.inspect(rows).invalidLimits)
    }

    @Test fun unsupportedMediaIsNotExecuted() {
        val rows = KiroAnswerArchive.importLegacy("привет|опасный|file:///etc/passwd\nпривет|безопасный")
        assertEquals("безопасный", KiroArchiveConversation(rows).respond("привет"))
    }
}
