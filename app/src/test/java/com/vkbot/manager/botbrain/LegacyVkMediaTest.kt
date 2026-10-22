package com.vkbot.manager.botbrain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyVkMediaTest {
    @Test fun recognizesLegacyVkObjectPages() {
        for (type in listOf("photo", "video", "audio", "clip")) {
            assertTrue(LegacyVkMedia.isLegacyObjectPage("https://vk.com/${type}-188633896_457264520"))
        }
    }

    @Test fun doesNotBlockNormalMediaOrOtherWebLinks() {
        assertFalse(LegacyVkMedia.isLegacyObjectPage("https://example.org/image.jpg"))
        assertFalse(LegacyVkMedia.isLegacyObjectPage("https://vk.com/kirdev_07"))
        assertFalse(LegacyVkMedia.isLegacyObjectPage("https://cdn.example.org/photo-188633896_457264520.jpg"))
    }
}
