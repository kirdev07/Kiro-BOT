package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** End-to-end archive import, session isolation, media-only replies and reload behavior. */
class KiroArchiveSwitchIntegrationTest {
    @Test fun archiveRoundTripKeepsMediaAndContextPerSession() {
        val legacy = "привет|здравствуй|||||ready\nфото||https://example.com/image.jpg|||ready|"
        val rows = KiroAnswerArchive.parse(KiroAnswerArchive.export(KiroAnswerArchive.importLegacy(legacy)))
        val engine = KiroArchiveSwitch(rows)
        assertNull(engine.replyRich(2L, "фото"))
        assertEquals("здравствуй", engine.reply(1L, "привет"))
        val media = engine.replyRich(1L, "фото")
        assertEquals("", media?.text)
        assertEquals(listOf("https://example.com/image.jpg"), media?.attachments)
        assertNull(engine.replyRich(2L, "фото"))
        assertEquals("здравствуй", engine.reply(2L, "привет"))
        assertEquals(listOf("https://example.com/image.jpg"), engine.replyRich(2L, "фото")?.attachments)
    }

    @Test fun forgettingSessionRemovesContextAndRepeatCounters() {
        val engine = KiroArchiveSwitch(KiroAnswerArchive.importLegacy(
            "привет|здравствуй||1|||ready\nдальше|продолжение||||ready|"
        ))
        assertEquals("здравствуй", engine.reply(9L, "привет"))
        assertNull(engine.reply(9L, "привет"))
        assertEquals("продолжение", engine.reply(9L, "дальше"))
        engine.forget(9L)
        assertNull(engine.reply(9L, "дальше"))
        assertEquals("здравствуй", engine.reply(9L, "привет"))
    }

    @Test fun wildcardCandidateMergePreservesArchivePriority() {
        val rows = KiroAnswerArchive.importLegacy(
            "*мир|общий ранний\nпривет*|общий поздний\nпривет*|контекстный||||ready|"
        )
        val engine = KiroArchiveSwitch(rows)
        assertEquals("общий ранний", engine.reply(1L, "привет мир"))
        val contextual = KiroArchiveSwitch(KiroAnswerArchive.importLegacy(
            "старт|готово|||||ready\n*мир|общий\nпривет*|особый||||ready|"
        ))
        assertEquals("готово", contextual.reply(2L, "старт"))
        assertEquals("особый", contextual.reply(2L, "привет мир"))
    }

    @Test fun leastRecentlyUsedSessionEvictionResetsOldestOnly() {
        val engine = KiroArchiveSwitch(KiroAnswerArchive.importLegacy("привет|ответ||1"), maxSessions = 2)
        assertEquals("ответ", engine.reply(1L, "привет"))
        assertEquals("ответ", engine.reply(2L, "привет"))
        assertNull(engine.reply(1L, "привет")) // touch session 1
        assertEquals("ответ", engine.reply(3L, "привет")) // evict session 2
        assertNull(engine.reply(1L, "привет"))
        assertEquals("ответ", engine.reply(2L, "привет")) // new session
    }
}
