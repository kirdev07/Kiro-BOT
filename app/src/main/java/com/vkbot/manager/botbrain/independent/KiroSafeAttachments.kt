package com.vkbot.manager.botbrain.independent

/** Validates media references as data; does not download, fetch or execute them. */
internal object KiroSafeAttachments {
    private val vkObject = Regex("^[a-z]+-?\\d+_\\d+(?:_\\w+)?$")
    private val allowedTypes = setOf("photo", "video", "audio", "doc", "wall", "market", "poll")

    // Pure, offline validation. The downloader must separately validate DNS and redirects.
    private fun isLocalHost(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (h == "localhost" || h.endsWith(".localhost") ||
            h.endsWith(".local") || h.endsWith(".internal")) return true
        // Reject IPv6 literals (including IPv4-mapped IPv6) until transport validation exists.
        if (h.contains(':')) return true
        // Numeric aliases such as 2130706433 and 0x7f000001 are unsafe.
        if (h.all { it.isDigit() } || h.startsWith("0x")) return true
        val octets = h.split('.')
        if (octets.size != 4) return false
        // Only canonical decimal IPv4 is allowed; reject octal/hex/shortened forms.
        if (octets.any { it.isEmpty() || it.any { c -> !c.isDigit() } ||
                (it.length > 1 && it.startsWith("0")) ||
                it.toIntOrNull() !in 0..255 }) return true
        val first = octets[0].toInt()
        val second = octets[1].toInt()
        return first == 0 || first == 10 || first == 127 ||
            first >= 224 || (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            (first == 169 && second == 254) ||
            (first == 100 && second in 64..127) ||
            (first == 198 && second in 18..19)
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
