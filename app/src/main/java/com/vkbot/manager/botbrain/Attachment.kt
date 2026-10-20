package com.vkbot.manager.botbrain

import java.io.Serializable

/**
 * Вложение ответа — ссылка. Картинка или GIF скачивается и приходит в Telegram медиа,
 * видео и прочие ссылки идут текстом (Telegram покажет превью).
 */
data class Attachment(val url: String) : Serializable {

    /** Как вложение хранится в answer.txt и показывается в редакторе. */
    fun toStorageString(): String = url

    override fun toString(): String = url

    companion object {
        /** Код объекта из старых баз VK-версии: photo-123_456, video1_2_key… */
        private val LEGACY_VK_CODE = Regex("^[a-z]+-?\\d+_\\d+(?:_\\w+)?$")

        /** Ссылка → вложение. Коды объектов из старых баз превращаются в ссылку, чтобы не потеряться. */
        @JvmStatic
        fun parse(value: String?): Attachment? {
            val text = value?.trim().orEmpty()
            return when {
                text.isEmpty() -> null
                text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true) -> Attachment(text)
                LEGACY_VK_CODE.matches(text) -> Attachment("https://vk.com/$text")
                else -> null
            }
        }
    }
}
