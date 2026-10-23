package com.vkbot.manager.botbrain.independent

/** Validates media references as data; does not download, fetch or execute them. */
internal object KiroSafeAttachments {
    private val vkObject = Regex("^[a-z]+-?\\d+_\\d+(?:_\\w+)?$")
    private val allowedTypes = setOf("photo", "video", "audio", "doc", "wall", "market", "poll")

    fun parse(value: String): List<String>? {
        if (value.isBlank()) return emptyList()
        val pieces = value.split(',')
        if (pieces.size > 10) return null
        val result = ArrayList<String>(pieces.size)
        for (raw in pieces) {
            val item = raw.trim()
            if (item.isEmpty() || item.length > 2048 || item.any { it.isISOControl() }) return null
            if (vkObject.matches(item)) {
                if (item.substringBefore('-').substringBefore('_').takeWhile { it.isLetter() } !in allowedTypes) return null
                result.add("https://vk.com/$item")
                continue
            }
            val uri = try { java.net.URI(item) } catch (_: Exception) { return null }
            if (uri.scheme?.lowercase() != "https" || uri.host.isNullOrBlank() ||
                uri.userInfo != null || uri.fragment != null) return null
            result.add(item)
        }
        return result
    }
}
