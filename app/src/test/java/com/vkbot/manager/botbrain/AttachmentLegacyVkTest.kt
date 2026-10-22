package com.vkbot.manager.botbrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttachmentLegacyVkTest {
    @Test fun photoCodeBecomesVkPageLink() {
        assertEquals("https://vk.com/photo-188633896_457264520", Attachment.parse("photo-188633896_457264520")?.url)
    }

    @Test fun otherLegacyMediaTypesBecomePageLinks() {
        for (code in listOf("video-123_456", "audio-123_456", "clip-123_456")) {
            assertEquals("https://vk.com/$code", Attachment.parse(code)?.url)
        }
    }

    @Test fun directHttpsUrlIsPreserved() {
        assertEquals("https://example.org/image.jpg", Attachment.parse("https://example.org/image.jpg")?.url)
    }

    @Test fun invalidAndBlankInputsAreIgnored() {
        assertNull(Attachment.parse(null))
        assertNull(Attachment.parse(""))
        assertNull(Attachment.parse("not-an-attachment"))
    }
}
