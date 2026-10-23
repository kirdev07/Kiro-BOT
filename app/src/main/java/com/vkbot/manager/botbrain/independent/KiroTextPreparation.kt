package com.vkbot.manager.botbrain.independent

import java.text.Normalizer
import java.util.Locale

/** Standalone text utilities for a new response engine. */
internal object KiroTextPreparation {
    private val wordRegex = Regex("[\\p{L}\\p{N}]+")

    fun normalize(input: String): String =
        Normalizer.normalize(input, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .trim()

    fun words(input: String): List<String> =
        wordRegex.findAll(normalize(input)).map { it.value }.toList()
}
