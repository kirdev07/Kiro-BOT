package com.vkbot.manager.botbrain.independent

/**
 * New opt-in reply index built around explicit user-authored records.
 * It deliberately does not read or bundle the historical answer database.
 */
internal class KiroReplyIndex(records: List<Record>) {
    data class Record(val id: String, val prompt: String, val reply: String)

    private val entries = records.filter { it.id.isNotBlank() && it.reply.isNotBlank() }
        .map { Indexed(it, KiroTextPreparation.words(it.prompt)) }
        .filter { it.tokens.isNotEmpty() }

    private data class Indexed(val record: Record, val tokens: List<String>)

    /** Deterministic exact match first; otherwise token overlap with a minimum score. */
    fun find(message: String): Record? {
        val query = KiroTextPreparation.words(message)
        if (query.isEmpty()) return null
        entries.firstOrNull { it.tokens == query }?.let { return it.record }
        val querySet = query.toSet()
        return entries.mapNotNull { item ->
            val candidate = item.tokens.toSet()
            val common = querySet.intersect(candidate).size
            val union = querySet.union(candidate).size
            val score = if (union == 0) 0.0 else common.toDouble() / union
            if (score >= 0.6) item.record to score else null
        }.sortedWith(compareByDescending<Pair<Record, Double>> { it.second }.thenBy { it.first.id })
            .firstOrNull()?.first
    }
}
