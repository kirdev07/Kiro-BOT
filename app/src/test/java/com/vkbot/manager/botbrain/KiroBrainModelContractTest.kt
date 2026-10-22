package com.vkbot.manager.botbrain

import org.junit.Assert.*
import org.junit.Test
import java.util.Date

/**
 * Public behavioral contracts for the planned independent Kiro Brain model layer.
 * These tests do not establish the provenance of the implementation.
 */
class KiroBrainModelContractTest {
    @Test fun messageDefaultsToPrivateChatAndRetainsTelegramChatIdentity() {
        val message = BotMessage(text = "Привет", authorId = "42", authorName = "User", chatId = "123")
        assertFalse(message.isGroupChat)
        assertEquals("42", message.authorId)
        assertEquals("123", message.chatId)
        assertEquals("Привет", message.copy(text = "Привет").text)
    }

    @Test fun emptyResponseDependsOnTextAndAttachments() {
        assertTrue(BotResponse().isEmpty)
        assertFalse(BotResponse(text = "ok").isEmpty)
        assertFalse(BotResponse(attachments = listOf(Attachment("https://example.org/a.png"))).isEmpty)
    }

    @Test fun responseBuilderCopiesAttachmentList() {
        val input = mutableListOf(Attachment("https://example.org/a.png"))
        val response = BotResponse.Builder().text(null).addAttachments(input).build()
        input.clear()
        assertEquals("", response.text)
        assertEquals(1, response.attachments.size)
    }

    @Test fun answerDateIsDefensivelyCopiedOnRead() {
        val answer = AnswerElement(id = 7L, _createdDate = Date(1_000L))
        val returned = answer.createdDate
        returned.time = 9_000L
        assertEquals(1_000L, answer.createdDate.time)
    }

    @Test fun answerUsageCountDoesNotChangeContentEquality() {
        val a = AnswerElement(id = 7L, questionText = "Q", answerText = "A")
        val b = AnswerElement(id = 7L, questionText = "Q", answerText = "A")
        a.incrementUsageCount()
        assertEquals(1, a.usageCount)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test fun searchResultRetainsCaptureGroupsAndRanking() {
        val answer = AnswerElement(id = 1L)
        val result = SearchResult(answer, listOf("один"), tier = 2, score = 0.75f)
        assertTrue(result.hasGroups())
        assertEquals("один", result.capturedGroups.single())
        assertEquals(2, result.tier)
        assertEquals(0.75f, result.score, 0f)
        assertFalse(SearchResult(answer).hasGroups())
    }

    @Test fun attachmentParsingAcceptsUrlsAndRejectsUnrelatedText() {
        assertEquals("https://example.org/a.png", Attachment.parse(" https://example.org/a.png ")?.url)
        assertEquals("https://vk.com/photo-12_34", Attachment.parse("photo-12_34")?.url)
        assertNull(Attachment.parse(""))
        assertNull(Attachment.parse("just some text"))
    }
}
