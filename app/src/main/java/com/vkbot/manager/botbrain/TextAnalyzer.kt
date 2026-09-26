package com.vkbot.manager.botbrain

/**
 * Разбор текста для поиска: слова, синонимы, основы слов, части длинного сообщения.
 */
internal object TextAnalyzer {

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val CLAUSE_SPLIT = Regex("[.!?;,\\n]+")
    private const val MIN_STEM = 3

    /** Окончания русских слов, от длинных к коротким (упрощённый стеммер). */
    private val ENDINGS = listOf(
        "иями", "ями", "ами", "ого", "его", "ому", "ему", "ыми", "ими",
        "ешь", "ете", "ишь", "ите", "ает", "яет", "ует", "ать", "ять", "ить", "еть", "уть",
        "ала", "али", "ила", "или", "аю", "яю", "ал", "ил",
        "ой", "ей", "ий", "ый", "ая", "яя", "ое", "ее", "ые", "ие", "ую", "юю",
        "ах", "ях", "ам", "ям", "ов", "ев", "ом", "ем", "ым", "им", "ию", "ия",
        "а", "я", "о", "е", "и", "ы", "у", "ю", "ь", "й"
    )

    /** Слова в нижнем регистре, ё → е, синонимы заменены основным словом. */
    fun tokens(text: String, synonyms: Map<String, String>): List<String> =
        normalize(text).split(NON_WORD)
            .filter { it.isNotEmpty() }
            .map { synonyms[it] ?: it }

    fun normalize(text: String): String = text.lowercase().replace('ё', 'е')

    /** Основа слова: «работе», «работа», «работу» → «работ». Латиницу и цифры не трогаем. */
    fun stem(word: String): String {
        if (word.length <= MIN_STEM || word.first() !in 'а'..'я') return word
        for (ending in ENDINGS) {
            if (word.endsWith(ending) && word.length - ending.length >= MIN_STEM) {
                return word.dropLast(ending.length)
            }
        }
        return word
    }

    /** Части длинного сообщения (по знакам препинания и переносам строк). */
    fun clauses(text: String): List<String> =
        text.split(CLAUSE_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }

    /** Строки вида «основное = вариант1, вариант2» → карта вариант → основное. */
    fun parseSynonyms(lines: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (raw in lines) {
            val line = raw.removePrefix(Char(0xFEFF).toString()).trim()
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue
            val main = normalize(line.substringBefore("=").trim())
            if (main.isEmpty() || main.contains(' ')) continue
            line.substringAfter("=").split(",")
                .map { normalize(it.trim()) }
                .filter { it.isNotEmpty() && !it.contains(' ') && it != main }
                .forEach { map[it] = main }
        }
        return map
    }
}
