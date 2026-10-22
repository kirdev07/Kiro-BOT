package com.vkbot.manager.botbrain

import java.io.Serializable

/** A media URL stored alongside a bot answer. */
data class Attachment(val url: String) : Serializable {
    fun toStorageString(): String = url
    override fun toString(): String = url

    companion object {
        private val legacyObjectPattern = Regex("^[a-z]+-?\\d+_\\d+(?:_\\w+)?$")

        @JvmStatic
        fun parse(value: String?): Attachment? {
            val candidate = value?.trim().orEmpty()
            if (candidate.isEmpty()) return null
            if (candidate.startsWith("http://", ignoreCase = true)) return Attachment(candidate)
            if (candidate.startsWith("https://", ignoreCase = true)) return Attachment(candidate)
            if (legacyObjectPattern.matches(candidate)) return Attachment("https://vk.com/$candidate")
            return null
        }
    }
}
