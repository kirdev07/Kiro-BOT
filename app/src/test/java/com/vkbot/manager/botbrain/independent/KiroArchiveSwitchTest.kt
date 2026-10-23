package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KiroArchiveSwitchTest {
    private val rows = KiroAnswerArchive.importLegacy(
        "привет|здравствуй|||||ready\nкак дела|хорошо||||ready|"
    )

    @Test fun isolatesConversations() {
        val router = KiroArchiveSwitch(rows)
        assertEquals("здравствуй", router.reply(1L, "привет"))
        assertNull(router.reply(2L, "как дела"))
        assertEquals("хорошо", router.reply(1L, "как дела"))
    }

    @Test fun evictsOldestSession() {
        val router = KiroArchiveSwitch(rows, maxSessions = 1)
        router.reply(1L, "привет")
        router.reply(2L, "привет")
        assertNull(router.reply(1L, "как дела"))
    }

    @Test fun forgetClearsConversationContext() {
        val router = KiroArchiveSwitch(rows)
        router.reply(1L, "привет")
        router.forget(1L)
        assertNull(router.reply(1L, "как дела"))
    }

    @Test fun sharedIndexDoesNotShareContextBetweenSessions() {
        val router = KiroArchiveSwitch(rows, maxSessions = 2)
        assertEquals("здравствуй", router.reply(101L, "привет"))
        assertEquals("здравствуй", router.reply(202L, "привет"))
        assertEquals("хорошо", router.reply(101L, "как дела"))
        assertEquals("хорошо", router.reply(202L, "как дела"))
        router.forget(101L)
        assertNull(router.reply(101L, "как дела"))
        assertEquals("хорошо", router.reply(202L, "как дела"))
    }

    @Test fun migratedArchiveRepliesAcrossLargeDataset() {
        val legacy = (1..14_993).joinToString("\n") { n -> "тестовая фраза номер $n|ответ номер $n" }
        val archive = KiroAnswerArchive.export(KiroAnswerArchive.importLegacy(legacy))
        val engine = KiroArchiveSwitch(KiroAnswerArchive.parse(archive))
        assertEquals("ответ номер 1", engine.reply(101L, "тестовая фраза номер 1"))
        assertEquals("ответ номер 7497", engine.reply(101L, "тестовая фраза номер 7497"))
        assertEquals("ответ номер 14993", engine.reply(202L, "тестовая фраза номер 14993"))
        assertNull(engine.reply(101L, "совершенно неизвестная фраза"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroSessionLimit() {
        KiroArchiveSwitch(rows, maxSessions = 0)
    }
}
