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

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroSessionLimit() {
        KiroArchiveSwitch(rows, maxSessions = 0)
    }
}
