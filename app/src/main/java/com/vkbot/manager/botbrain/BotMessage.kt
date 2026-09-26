package com.vkbot.manager.botbrain

import java.io.Serializable

/**
 * Модель сообщения для мозга бота (Kotlin data class).
 */
data class BotMessage(
    val text: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val platform: String = "vk",
    /** Беседа / группа, а не личные сообщения. */
    val isGroupChat: Boolean = false
) : Serializable {

    override fun toString(): String = "[$platform] Msg $authorName: $text"

}
