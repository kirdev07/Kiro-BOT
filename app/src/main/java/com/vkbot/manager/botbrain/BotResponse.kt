package com.vkbot.manager.botbrain

import java.io.Serializable

/**
 * Модель ответа бота.
 */
data class BotResponse(
    val text: String = "",
    val attachments: List<Attachment> = emptyList()
) : Serializable {

    val isEmpty: Boolean get() = text.isEmpty() && attachments.isEmpty()

    override fun toString(): String {
        return "BotResponse { text='$text', atts=${attachments.size} }"
    }

    class Builder {
        private var text: String = ""
        private val attachments = mutableListOf<Attachment>()

        fun text(text: String?): Builder {
            this.text = text ?: ""
            return this
        }

        fun addAttachments(attachments: List<Attachment>?): Builder {
            if (attachments != null) this.attachments.addAll(attachments)
            return this
        }

        fun build(): BotResponse {
            return BotResponse(text, attachments.toList())
        }
    }

}
