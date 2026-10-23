package com.vkbot.manager.botbrain.independent

/**
 * Matches literal text and '*' wildcards only. Never compiles user regex.
 * Anchored at both ends; caps input size and wildcard count.
 */
internal object KiroSafeMask {
    private const val MAX_LENGTH = 2048
    private const val MAX_WILDCARDS = 16

    fun matches(pattern: String, message: String): Boolean {
        if (pattern.length > MAX_LENGTH || message.length > MAX_LENGTH) return false
        if (pattern.count { it == '*' } !in 1..MAX_WILDCARDS) return false
        val p = KiroTextPreparation.normalize(pattern)
        val m = KiroTextPreparation.normalize(message)
        var pi = 0
        var mi = 0
        var star = -1
        var retry = 0
        while (mi < m.length) {
            when {
                pi < p.length && p[pi] == m[mi] -> { pi++; mi++ }
                pi < p.length && p[pi] == '*' -> { star = pi++; retry = mi }
                star >= 0 -> { pi = star + 1; mi = ++retry }
                else -> return false
            }
        }
        while (pi < p.length && p[pi] == '*') pi++
        return pi == p.length
    }
}
