package com.vkbot.manager.botbrain

import java.io.Serializable

/**
 * Модель сообщения для мозга бота (Kotlin data class).
 */
data class BotMessage(
    val text: String = "",
    val authorId: String = "",
    val authorName: String = "",
    /** Группа, а не личные сообщения. */
    val isGroupChat: Boolean = false,
    /** Чат Telegram (chat.id): история диалога ведётся отдельно в каждом чате. */
    val chatId: String = ""
) : Serializable {

    override fun toString(): String = "Msg $authorName: $text"

}
