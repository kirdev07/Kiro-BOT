package com.vkbot.manager.botbrain

import java.io.Serializable

/** Telegram message passed to the Kiro Brain response engine. */
data class BotMessage(
    val text: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val isGroupChat: Boolean = false,
    val chatId: String = ""
) : Serializable {
    override fun toString(): String = buildString {
        append("Msg ")
        append(authorName)
        append(": ")
        append(text)
    }
}
