package com.vkbot.manager.botbrain

import kotlin.math.sqrt

/**
 * Standalone token-overlap scorer for the future Kiro Brain search pipeline.
 * This component does not read answer files or depend on Android APIs.
 * It is not wired into AnswerDatabase yet.
 */
internal object WeightedTokenMatcher {
    /**
     * Returns a normalized overlap score between 0.0 and 1.0.
     * Duplicate tokens do not increase relevance. Non-positive weights are ignored.
     */
    fun score(queryTokens: Collection<String>, candidateTokens: Collection<String>, weights: Map<String, Double> = emptyMap()): Double {
        val query = queryTokens.filter(String::isNotBlank).toSet()
        val candidate = candidateTokens.filter(String::isNotBlank).toSet()
        if (query.isEmpty() || candidate.isEmpty()) return 0.0
        fun weight(token: String): Double = (weights[token] ?: 1.0).takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val common = query.intersect(candidate).sumOf(::weight)
        if (common <= 0.0) return 0.0
        val queryWeight = query.sumOf(::weight)
        val candidateWeight = candidate.sumOf(::weight)
        if (queryWeight <= 0.0 || candidateWeight <= 0.0) return 0.0
        return (common / sqrt(queryWeight * candidateWeight)).coerceIn(0.0, 1.0)
    }
}
