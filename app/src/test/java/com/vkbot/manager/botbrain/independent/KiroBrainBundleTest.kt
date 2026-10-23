package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KiroBrainBundleTest {
    private val replies = KiroReplyFormat.export(listOf(
        KiroReplyIndex.Record("hello", "Привет", "Как дела?")
    ))

    @Test fun loadsRepliesAndContextRulesTogether() {
        val dialogs = KiroDialogFormat.export(listOf(
            KiroFollowUpRules.Rule("good", "hello", "Хорошо", "Рад слышать!")
        ))
        val session = KiroBrainBundle.load(replies, dialogs).newSession()
        assertEquals(KiroConversationEngine.Result.Answer("Как дела?", "hello"), session.respond("Привет"))
        assertEquals(KiroConversationEngine.Result.Answer("Рад слышать!", "good"), session.respond("Хорошо"))
    }

    @Test fun sessionsRemainIndependent() {
        val bundle = KiroBrainBundle.load(replies, KiroDialogFormat.export(emptyList()))
        val first = bundle.newSession()
        val second = bundle.newSession()
        first.respond("Привет")
        assertTrue(second.recentTurns().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownFollowUpParent() {
        KiroBrainBundle.load(replies, KiroDialogFormat.export(listOf(
            KiroFollowUpRules.Rule("bad", "missing", "Хорошо", "Ответ")
        )))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateReplyAndRuleIds() {
        KiroBrainBundle.load(replies, KiroDialogFormat.export(listOf(
            KiroFollowUpRules.Rule("hello", "hello", "Хорошо", "Ответ")
        )))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCircularRules() {
        KiroBrainBundle.load(replies, KiroDialogFormat.export(listOf(
            KiroFollowUpRules.Rule("a", "b", "один", "ответ"),
            KiroFollowUpRules.Rule("b", "a", "два", "ответ")
        )))
    }
}
