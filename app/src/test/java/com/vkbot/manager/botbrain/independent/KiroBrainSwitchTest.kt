package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KiroBrainSwitchTest {
    private fun router(): KiroBrainSwitch {
        val replies = KiroReplyFormat.export(listOf(
            KiroReplyIndex.Record("hello", "Привет", "Как дела?")
        ))
        val dialogs = KiroDialogFormat.export(listOf(
            KiroFollowUpRules.Rule("good", "hello", "Хорошо", "Отлично!")
        ))
        return KiroBrainSwitch(KiroBrainBundle.load(replies, dialogs))
    }

    @Test fun disabledByDefault() {
        val router = router()
        assertFalse(router.isEnabled())
        assertNull(router.reply(1, "Привет"))
    }

    @Test fun enabledRouterReplies() {
        val router = router()
        router.setEnabled(true)
        assertTrue(router.isEnabled())
        assertEquals("Как дела?", router.reply(1, "Привет"))
    }

    @Test fun sessionsAreSeparatedByChatId() {
        val router = router()
        router.setEnabled(true)
        router.reply(1, "Привет")
        assertNull(router.reply(2, "Хорошо"))
        assertEquals("Отлично!", router.reply(1, "Хорошо"))
    }

    @Test fun disablingResetsConversationState() {
        val router = router()
        router.setEnabled(true)
        router.reply(1, "Привет")
        router.setEnabled(false)
        router.setEnabled(true)
        assertNull(router.reply(1, "Хорошо"))
    }

    @Test fun forgetChatResetsOnlySpecifiedChat() {
        val router = router()
        router.setEnabled(true)
        router.reply(1, "Привет")
        router.reply(2, "Привет")
        router.forgetChat(1)
        assertNull(router.reply(1, "Хорошо"))
        assertEquals("Отлично!", router.reply(2, "Хорошо"))
    }
}
