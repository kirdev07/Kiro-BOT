package com.vkbot.manager.botbrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttachmentTest {

    @Test fun linksAreKeptAsIs() {
        for (s in listOf("https://example.com/pics/anime_123_456.jpg", "https://youtu.be/dQw4w9WgXcQ", "http://site.ru/a.gif")) {
            assertEquals(s, Attachment.parse(s)!!.toStorageString())
        }
    }

    @Test fun legacyVkCodesBecomeLinks() {
        // Старые базы VK-версии не теряют вложения — они становятся обычными ссылками
        assertEquals("https://vk.com/photo-123_456_abc", Attachment.parse("photo-123_456_abc")!!.url)
        assertEquals("https://vk.com/doc1_2", Attachment.parse(" doc1_2 ")!!.url)
    }

    @Test fun garbageIsIgnored() {
        assertNull(Attachment.parse(""))
        assertNull(Attachment.parse("   "))
        assertNull(Attachment.parse("просто текст"))
    }
}
