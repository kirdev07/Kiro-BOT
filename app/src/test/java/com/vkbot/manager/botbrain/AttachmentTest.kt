package com.vkbot.manager.botbrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentTest {

    @Test fun vkPhotoLink() {
        val a = Attachment.parse("https://vk.com/photo-123_456_abc")!!
        assertFalse(a.isExternal)
        assertEquals("photo-123_456_abc", a.toVkString())
    }

    @Test fun vkVideoFromNewDomain() =
        assertEquals("video-1_2", Attachment.parse("https://vkvideo.ru/video-1_2")!!.toVkString())

    @Test fun clipBecomesVideo() =
        assertEquals("video-5_6", Attachment.parse("https://vk.com/clip-5_6")!!.toVkString())

    @Test fun bareVkString() = assertEquals("doc1_2", Attachment.parse("doc1_2")!!.toVkString())

    @Test fun externalImageLink() {
        val a = Attachment.parse("https://example.com/pics/anime_123_456.jpg")!!
        assertTrue("цифры в чужой ссылке не должны читаться как фото VK", a.isExternal)
        assertEquals("https://example.com/pics/anime_123_456.jpg", a.toStorageString())
    }

    @Test fun youtubeLinkIsExternal() = assertTrue(Attachment.parse("https://youtu.be/dQw4w9WgXcQ")!!.isExternal)

    @Test fun vkGroupLinkIsSentAsLink() = assertTrue(Attachment.parse("https://vk.com/kirdev_07")!!.isExternal)

    @Test fun garbageIsIgnored() = assertEquals(null, Attachment.parse("просто текст"))

    @Test fun storageRoundTrip() {
        for (s in listOf("photo-1_2_key", "https://example.com/a.png")) {
            assertEquals(s, Attachment.parse(s)!!.toStorageString())
        }
    }
}
