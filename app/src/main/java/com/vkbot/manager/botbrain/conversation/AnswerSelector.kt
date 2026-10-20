package com.vkbot.manager.botbrain.conversation

import com.vkbot.manager.botbrain.SearchResult
import kotlin.random.Random

/**
 * Выбор одного ответа из найденных в базе:
 * 1) лучший уровень совпадения и почти равные по оценке (случайность не перебивает релевантность);
 * 2) без ответов, превысивших свой лимит повторов;
 * 3) без текстов, которые бот недавно говорил в этом диалоге (на любой вопрос), пока есть другие;
 * 4) редко звучавшие варианты чуть вероятнее.
 */
object AnswerSelector {

    /** Точные ответы и команды: варианты с оценкой не ниже 90% от лучшей равноценны. */
    const val NEAR_TIE = 0.9f
    /**
     * Совпадения по смыслу: только почти равные (95%) — иначе общий ответ на «объясни» (0.57)
     * перебивал точный «что такое фотосинтез» (0.63).
     */
    const val SMART_NEAR_TIE = 0.95f
    /** Если все равноценные недавно звучали — можно взять вариант того же уровня с оценкой от 75% от лучшей. */
    const val RELAXED_TIE = 0.75f
    /** Уровни 0–3 — точное совпадение и regex: все варианты равноправны (команда с 10 картинками). */
    const val LAST_EXACT_TIER = 3
    /** Для совпадений по смыслу выбираем из стольких лучших. */
    const val MAX_SMART_POOL = 3
    /** Бонус редкому ответу: вес = 1 + RARE_BONUS / (1 + число использований). */
    const val RARE_BONUS = 1.0

    /** Из чего выбирается ответ: лучший уровень совпадения и почти равные по оценке. */
    fun selectionPool(candidates: List<SearchResult>, tie: Float = NEAR_TIE): List<SearchResult> {
        if (candidates.isEmpty()) return emptyList()
        val bestTier = candidates.minOf { it.tier }
        val best = candidates.filter { it.tier == bestTier }
        val topScore = best.maxOf { it.score }
        val pool = best.filter { it.score >= topScore * if (bestTier <= LAST_EXACT_TIER) tie else maxOf(tie, SMART_NEAR_TIE) }
        return if (bestTier <= LAST_EXACT_TIER) pool else pool.take(MAX_SMART_POOL)
    }

    fun select(
        candidates: List<SearchResult>,
        dialog: DialogHistory.Dialog,
        random: Random,
        exclude: Set<String> = emptySet()
    ): SearchResult? {
        val allowed = candidates.filter { sr ->
            val limit = sr.answer.repetitionLimit
            (limit <= 0 || dialog.repetitionCount(sr.answer.id) < limit) && sr.answer.answerText !in exclude
        }
        if (allowed.isEmpty()) return null

        val pool = selectionPool(allowed)
        val fresh = pool.filter { !dialog.recentlySaid(it.answer.answerText) }.ifEmpty {
            val bestTier = pool.first().tier
            val topScore = pool.maxOf { it.score }
            allowed.filter { it.tier == bestTier && it.score >= topScore * RELAXED_TIE && !dialog.recentlySaid(it.answer.answerText) }
                .take(if (bestTier <= LAST_EXACT_TIER) Int.MAX_VALUE else MAX_SMART_POOL)
        }
        if (fresh.isNotEmpty()) return weightedRandom(fresh, random)
        // Все варианты недавно звучали — тот, что звучал давнее всего
        return pool.maxByOrNull { dialog.turnsSinceSaid(it.answer.answerText) ?: Int.MAX_VALUE }
    }

    private fun weightedRandom(list: List<SearchResult>, random: Random): SearchResult {
        if (list.size == 1) return list[0]
        val weights = list.map { 1.0 + RARE_BONUS / (1 + it.answer.usageCount.coerceAtLeast(0)) }
        var r = random.nextDouble() * weights.sum()
        for ((i, w) in weights.withIndex()) {
            r -= w
            if (r <= 0) return list[i]
        }
        return list.last()
    }
}
