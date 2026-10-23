package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KiroFollowUpRulesTest {
    private fun engine() = KiroConversationEngine(
        listOf(KiroReplyIndex.Record("hello", "Привет", "Как дела?")),
        followUps = listOf(
            KiroFollowUpRules.Rule("good", "hello", "Хорошо", "Рад слышать!")
        )
    )

    @Test fun followUpWorksAfterMatchingPrompt() {
        val bot = engine()
        bot.respond("Привет")
        assertEquals(KiroConversationEngine.Result.Answer("Рад слышать!", "good"), bot.respond("ХОРОШО!"))
    }

    @Test fun followUpDoesNotWorkWithoutPriorPrompt() {
        assertTrue(engine().respond("Хорошо") is KiroConversationEngine.Result.NoMatch)
    }

    @Test fun resetDisablesOldContext() {
        val bot = engine()
        bot.respond("Привет")
        bot.resetSession()
        assertTrue(bot.respond("Хорошо") is KiroConversationEngine.Result.NoMatch)
    }

    @Test fun unknownMessageBreaksFollowUpChain() {
        val bot = engine()
        bot.respond("Привет")
        bot.respond("что-то постороннее")
        assertTrue(bot.respond("Хорошо") is KiroConversationEngine.Result.NoMatch)
    }

    @Test fun independentChatsDoNotShareFollowUpState() {
        val first = engine()
        val second = engine()
        first.respond("Привет")
        assertTrue(second.respond("Хорошо") is KiroConversationEngine.Result.NoMatch)
    }
}
