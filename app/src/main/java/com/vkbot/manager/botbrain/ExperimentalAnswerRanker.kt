package com.vkbot.manager.botbrain

/**
 * Experimental ranking prototype. This is deliberately NOT called by AnswerDatabase:
 * the current exact/regex/context tiers stay authoritative until regression tests pass.
 */
internal object ExperimentalAnswerRanker {
    internal data class RankedAnswer(val answer: AnswerElement, val score: Double)

    fun rank(
        query: String,
        candidates: Collection<AnswerElement>,
        synonyms: Map<String, String> = emptyMap(),
        userContext: String? = null,
        limit: Int = 20
    ): List<RankedAnswer> {
        if (query.isBlank() || limit <= 0) return emptyList()
        val queryTokens = TextAnalyzer.tokens(query, synonyms)
        if (queryTokens.isEmpty()) return emptyList()
        return candidates.asSequence()
            .filter { it.requiredContext.isEmpty() || it.requiredContext.equals(userContext.orEmpty(), ignoreCase = true) }
            .map { answer ->
                RankedAnswer(answer, WeightedTokenMatcher.score(queryTokens, TextAnalyzer.tokens(answer.questionText, synonyms)))
            }
            .filter { it.score > 0.0 }
            .sortedWith(compareByDescending<RankedAnswer> { it.score }
                .thenByDescending { it.answer.usageCount }
                .thenBy { it.answer.id })
            .take(limit)
            .toList()
    }
}
