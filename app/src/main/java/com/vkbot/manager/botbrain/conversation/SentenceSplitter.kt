package com.vkbot.manager.botbrain.conversation

/**
 * Делит сообщение на предложения по «.», «!», «?», «…» и переносам строк.
 * Не режет ссылки, числа («3.14», «1,5»), сокращения («т.е.», «и т.д.») и многоточия внутри слова.
 */
object SentenceSplitter {

    private val PROTECTED = listOf(
        Regex("(?i)\\b(?:https?://|www\\.)\\S+"),
        // Не \b: в Java он не видит границ кириллических слов («сайт.рф»)
        Regex("(?i)(?<![\\p{L}\\p{N}-])[\\p{L}\\p{N}-]+\\.(?:ru|com|net|org|io|me|su|рф|by|kz|ua|info|dev|app|gg|tv)(?![\\p{L}\\p{N}])(?:/\\S*)?"),
        Regex("\\d+(?:[.,:]\\d+)+"),
        Regex("(?i)(?<![\\p{L}])(?:т\\.\\s?е|т\\.\\s?к|т\\.\\s?д|т\\.\\s?п|и\\.\\s?о|др|см|напр|стр|руб|тыс|млн|млрд|г|ул|д|кв|рис|им)\\.")
    )
    private val SPLIT = Regex("(?:[.!?…]+|\\n+)")
    private const val MARK = '\u0000'

    fun split(text: String): List<String> {
        // Защищённые места временно без точек, чтобы по ним не резать
        val chars = text.toCharArray()
        for (p in PROTECTED) {
            p.findAll(text).forEach { m -> for (i in m.range) if (chars[i] in ".!?…") chars[i] = MARK }
        }
        val masked = String(chars)
        val parts = mutableListOf<String>()
        var start = 0
        for (m in SPLIT.findAll(masked)) {
            parts += text.substring(start, m.range.last + 1)
            start = m.range.last + 1
        }
        parts += text.substring(start)
        return parts.map { it.trim() }.filter { part -> part.any { it.isLetterOrDigit() } }
    }
}
