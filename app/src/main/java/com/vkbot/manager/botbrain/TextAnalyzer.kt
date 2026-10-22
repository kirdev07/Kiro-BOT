package com.vkbot.manager.botbrain

/** Text normalization helpers used by Kiro Brain answer search. */
internal object TextAnalyzer {
    private val wordSeparator = Regex("[^\\p{L}\\p{N}]+")
    private val clauseSeparator = Regex("[.!?;,\\n]+")
    private const val minimumStemLength = 3

    private val russianEndings = listOf(
        "иями", "ями", "ами", "ого", "его", "ому", "ему", "ыми", "ими",
        "ешь", "ете", "ишь", "ите", "ает", "яет", "ует", "ать", "ять", "ить", "еть", "уть",
        "ала", "али", "ила", "или", "аю", "яю", "ал", "ил",
        "ой", "ей", "ий", "ый", "ая", "яя", "ое", "ее", "ые", "ие", "ую", "юю",
        "ах", "ях", "ам", "ям", "ов", "ев", "ом", "ем", "ым", "им", "ию", "ия",
        "а", "я", "о", "е", "и", "ы", "у", "ю", "ь", "й"
    )

    fun normalize(text: String): String = text.lowercase().replace('ё', 'е')

    fun tokens(text: String, synonyms: Map<String, String>): List<String> {
        return normalize(text).split(wordSeparator)
            .filter(String::isNotEmpty)
            .map { token -> synonyms[token] ?: token }
    }

    fun stem(word: String): String {
        if (word.length <= minimumStemLength || word.first() !in 'а'..'я') return word
        val ending = russianEndings.firstOrNull { suffix ->
            word.endsWith(suffix) && word.length - suffix.length >= minimumStemLength
        }
        return if (ending == null) word else word.dropLast(ending.length)
    }

    fun clauses(text: String): List<String> = text.split(clauseSeparator)
        .map(String::trim)
        .filter(String::isNotEmpty)

    fun parseSynonyms(lines: List<String>): Map<String, String> {
        val synonyms = mutableMapOf<String, String>()
        for (rawLine in lines) {
            val definition = rawLine.removePrefix(Char(0xFEFF).toString()).trim()
            if (definition.isEmpty() || definition.startsWith("#") || '=' !in definition) continue
            val canonical = normalize(definition.substringBefore('=').trim())
            if (canonical.isEmpty() || ' ' in canonical) continue
            for (variant in definition.substringAfter('=').split(',')) {
                val alias = normalize(variant.trim())
                if (alias.isNotEmpty() && ' ' !in alias && alias != canonical) {
                    synonyms[alias] = canonical
                }
            }
        }
        return synonyms
    }
}
