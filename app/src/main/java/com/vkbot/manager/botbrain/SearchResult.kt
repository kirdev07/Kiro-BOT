package com.vkbot.manager.botbrain

import java.io.Serializable

/**
 * Класс для хранения результатов поиска ответа (Kotlin data class).
 * Содержит найденный элемент базы и список захваченных групп (из Regex или масок).
 */
data class SearchResult(
    val answer: AnswerElement,
    val capturedGroups: List<String> = emptyList(),
    /** Уровень уверенности: 0 — лучший (точное совпадение), больше — хуже. */
    val tier: Int = 0,
    /** Оценка совпадения внутри уровня: 1 — полное, меньше — частичное. */
    val score: Float = 1f
) : Serializable {
    
    fun hasGroups(): Boolean = capturedGroups.isNotEmpty()

}
