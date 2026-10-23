package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Test

class KiroReplyFormatTest {
    @Test fun roundTripEscapedText() {
        val records = listOf(KiroReplyIndex.Record("one", "Привет\tмир", "Ответ\nсо второй строки \\"))
        assertEquals(records, KiroReplyFormat.parse(KiroReplyFormat.export(records)))
    }

    @Test fun emptyDatabaseRoundTrip() {
        assertEquals(emptyList<KiroReplyIndex.Record>(), KiroReplyFormat.parse(KiroReplyFormat.export(emptyList())))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownFormat() {
        KiroReplyFormat.parse("question|answer")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateIds() {
        KiroReplyFormat.parse("# KIRO-REPLIES/1\na\tone\tone\na\ttwo\ttwo\n")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedRow() {
        KiroReplyFormat.parse("# KIRO-REPLIES/1\ninvalid\n")
    }
}
