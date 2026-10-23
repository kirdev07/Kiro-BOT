package com.vkbot.manager.botbrain.independent

/** Validates media references as data; does not download, fetch or execute them. */
internal object KiroSafeAttachments {
    private val vkObject = Regex("^[a-z]+-?\\d+_\\d+(?:_\\w+)?$")
    private val allowedTypes = setOf("photo", "video", "audio", "doc", "wall", "market", "poll")

    private fun isLocalHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") ||
            host.endsWith(".local") || host.endsWith(".internal")) return true
        if (host == "0.0.0.0" || host == "::1" || host.startsWith("127.") ||
            host.startsWith("10.") || host.startsWith("192.168.") ||
            host.startsWith("169.254.")) return true
        val octets = host.split('.')
        if (octets.size == 4 && octets.all { it.toIntOrNull() in 0..255 }) {
            val first = octets[0].toInt()
            val second = octets[1].toInt()
            return first == 0 || first == 10 || first == 127 ||
                first >= 224 || (first == 172 && second in 16..31) ||
                (first == 192 && second == 168) ||
                (first == 169 && second == 254) ||
                (first == 100 && second in 64..127) ||
                (first == 198 && second in 18..19)
        }
        return false
    }

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
            val host = uri.host.lowercase().trimEnd('.')
            if (isLocalHost(host)) return null
            result.add(item)
        }
        return result
    }
}
