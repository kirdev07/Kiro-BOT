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
    @Test fun blocksLocalAndReservedMediaHosts() {
        val forbidden = listOf(
            "https://localhost/a.jpg",
            "https://127.0.0.1/a.jpg",
            "https://192.168.1.5/a.jpg",
            "https://10.0.0.8/a.jpg",
            "https://172.16.0.1/a.jpg",
            "https://169.254.169.254/latest/meta-data/",
            "https://service.internal/a.jpg",
            "https://service.local/a.jpg"
        )
        forbidden.forEach { url ->
            org.junit.Assert.assertNull("Unexpectedly accepted: $url", KiroSafeAttachments.parse(url))
        }
        org.junit.Assert.assertEquals(
            listOf("https://example.com/a.jpg"),
            KiroSafeAttachments.parse("https://example.com/a.jpg")
        )
    }

    @Test fun blocksIpv6AndNumericAliases() {
        val forbidden = listOf(
            "https://[::1]/a.jpg",
            "https://[::ffff:127.0.0.1]/a.jpg",
            "https://2130706433/a.jpg",
            "https://0x7f000001/a.jpg",
            "https://127.1/a.jpg",
            "https://0177.0.0.1/a.jpg",
            "https://10.0.0.1/a.jpg"
        )
        forbidden.forEach { url ->
            assertNull("Unexpectedly accepted: $url", KiroSafeAttachments.parse(url))
        }
    }

    @Test fun rejectsNonstandardPortsAndPrivateDnsSuffixes() {
        val forbidden = listOf(
            "https://example.com:8080/image.jpg",
            "https://example.com:8443/image.jpg",
            "https://router.home.arpa/image.jpg",
            "https://home.arpa/image.jpg",
            "https://internal/image.jpg"
        )
        forbidden.forEach { assertNull("Unexpectedly accepted: $it", KiroSafeAttachments.parse(it)) }
        assertEquals(listOf("https://example.com:443/image.jpg"),
            KiroSafeAttachments.parse("https://example.com:443/image.jpg"))
    }

}
