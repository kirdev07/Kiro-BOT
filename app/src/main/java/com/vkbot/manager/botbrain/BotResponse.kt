package com.vkbot.manager.botbrain

import java.io.Serializable

/** Reply text and optional media links, consumed by Telegram delivery. */
data class BotResponse(
    val text: String = "",
    val attachments: List<Attachment> = emptyList()
) : Serializable {
    val isEmpty: Boolean get() = text.isEmpty() && attachments.isEmpty()

    override fun toString(): String = "BotResponse { text='$text', atts=${attachments.size} }"

    class Builder {
        private var responseText: String = ""
        private val pendingAttachments = ArrayList<Attachment>()

        fun text(text: String?): Builder = apply {
            responseText = text.orEmpty()
        }

        fun addAttachments(attachments: List<Attachment>?): Builder = apply {
            if (attachments != null) pendingAttachments.addAll(attachments)
        }

        fun build(): BotResponse = BotResponse(responseText, pendingAttachments.toList())
    }
}
