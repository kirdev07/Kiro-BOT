package com.vkbot.manager.botbrain.independent

import org.junit.Assert.*
import org.junit.Test

class KiroSafeAttachmentsTest {
    @Test fun acceptsHttpsAndLegacyPhotoReference() {
        assertEquals(listOf("https://example.com/photo.jpg", "https://vk.com/photo-1_2"),
            KiroSafeAttachments.parse("https://example.com/photo.jpg,photo-1_2"))
    }

    @Test fun rejectsUnsafeReferences() {
        assertNull(KiroSafeAttachments.parse("file:///etc/passwd"))
        assertNull(KiroSafeAttachments.parse("http://example.com/test"))
        assertNull(KiroSafeAttachments.parse("javascript:alert(1)"))
        assertNull(KiroSafeAttachments.parse("https://user:pass@example.com/"))
        assertNull(KiroSafeAttachments.parse("https://example.com/a#fragment"))
    }

    @Test fun retainsMediaInRichReply() {
        val rows = KiroAnswerArchive.importLegacy("фото|вот|https://example.com/a.jpg")
        val reply = KiroArchiveConversation(rows).respondRich("фото")
        assertEquals("вот", reply?.text)
        assertEquals(listOf("https://example.com/a.jpg"), reply?.attachments)
    }

    @Test fun rejectsTooManyAttachments() {
        assertNull(KiroSafeAttachments.parse(List(11) { "https://example.com/$it" }.joinToString(",")))
    }
}
