package com.vkbot.manager.botbrain

/**
 * Classifies legacy VK object-page links. These are NOT direct downloadable media URLs.
 * Kept separate from Attachment.parse so historical answer.txt records remain readable.
 */
object LegacyVkMedia {
    private val objectPage = Regex("^https?://(?:www\\.)?vk\\.com/(?:photo|video|audio|clip)-?\\d+_\\d+(?:_\\w+)?(?:[?#].*)?$", RegexOption.IGNORE_CASE)

    fun isLegacyObjectPage(url: String): Boolean = objectPage.matches(url.trim())
}
