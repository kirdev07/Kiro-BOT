package com.vkbot.manager.botbrain

import java.io.Serializable
import java.util.regex.Pattern

/**
 * Вложение ответа.
 * - Объект VK: type{owner_id}_{id}_{access_key} (photo, video, audio, doc, wall...).
 * - Внешняя ссылка ([url]): картинка из интернета загружается в VK при отправке,
 *   остальные ссылки (YouTube и т.п.) добавляются в текст — VK покажет превью.
 */
data class Attachment(
    val type: String = "",
    val id: String = "",
    val ownerId: String = "",
    val accessKey: String = "",
    val url: String = ""
) : Serializable {

    val isExternal: Boolean get() = url.isNotEmpty()

    fun toVkString(): String {
        val base = "${type}${ownerId}_$id"
        return if (accessKey.isEmpty()) base else "${base}_$accessKey"
    }

    /** Как вложение хранится в answer.txt. */
    fun toStorageString(): String = if (isExternal) url else toVkString()

    /** Как вложение показывается в редакторе. */
    fun toDisplayString(): String = if (isExternal) url else "https://vk.com/${toVkString()}"

    override fun toString(): String = toStorageString()

    companion object {
        private val VK_STRING_PATTERN = Pattern.compile("([a-z]+)(-?\\d+)_(\\d+)(?:_(\\w+))?")
        private val VK_HOSTS = listOf("vk.com", "vk.ru", "vkvideo.ru", "m.vk.com")

        /** Клипы в ссылках VK — это видео для messages.send. */
        private val TYPE_ALIASES = mapOf("clip" to "video")

        @JvmStatic
        fun parse(value: String?): Attachment? {
            val text = value?.trim().orEmpty()
            if (text.isEmpty()) return null

            val isLink = text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)
            if (isLink && !isVkLink(text)) return Attachment(url = text)

            val matcher = VK_STRING_PATTERN.matcher(text)
            if (matcher.find()) {
                val type = matcher.group(1) ?: ""
                return Attachment(
                    type = TYPE_ALIASES[type] ?: type,
                    ownerId = matcher.group(2) ?: "",
                    id = matcher.group(3) ?: "",
                    accessKey = matcher.group(4) ?: ""
                )
            }
            // Ссылка VK не на медиа (профиль, группа) — отправим как ссылку
            return if (isLink) Attachment(url = text) else null
        }

        private fun isVkLink(url: String): Boolean {
            val host = url.substringAfter("://").substringBefore("/").substringBefore("?").lowercase()
            return VK_HOSTS.any { host == it || host.endsWith(".$it") }
        }
    }
}
