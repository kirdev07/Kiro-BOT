package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KiroDialogContextTest {
    @Test fun keepsOnlyMostRecentTurns() {
        val context = KiroDialogContext(2)
        context.record("one", "reply1")
        context.record("two", "reply2")
        context.record("three", "reply3")
        assertEquals(listOf("two", "three"), context.recent().map { it.userMessage })
        assertEquals("reply3", context.lastReply())
    }

    @Test fun ignoresBlankMessages() {
        val context = KiroDialogContext()
        context.record("", "reply")
        context.record("hello", "")
        assertEquals(emptyList<KiroDialogContext.Turn>(), context.recent())
    }

    @Test fun clearRemovesAllHistory() {
        val context = KiroDialogContext()
        context.record("hello", "hi")
        context.clear()
        assertNull(context.lastReply())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveCapacity() {
        KiroDialogContext(0)
    }
}
