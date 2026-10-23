package com.vkbot.manager.botbrain.independent

/**
 * Fast, independent exact-match index for archived answers.
 * Patterns and media are kept in the archive, but not executed by this index.
 */
internal class KiroArchiveReplyIndex(rows: List<KiroAnswerArchive.Row>) {
    private val entries = rows.asSequence().filter {
        it.question.isNotBlank() && it.answer.isNotBlank() &&
            it.attachments.isBlank() && !it.question.contains("*") &&
            !it.question.startsWith("regex:", ignoreCase = true)
    }.groupBy { KiroTextPreparation.words(it.question) }

    fun find(message: String, context: String, allowed: (KiroAnswerArchive.Row) -> Boolean): KiroAnswerArchive.Row? {
        val tokens = KiroTextPreparation.words(message)
        if (tokens.isEmpty()) return null
        val candidates = entries[tokens].orEmpty().filter { row ->
            (row.requiredContext.isBlank() || row.requiredContext.trim().equals(context, ignoreCase = true)) &&
                allowed(row)
        }
        // Context-specific replies take priority over general replies.
        return candidates.firstOrNull { it.requiredContext.isNotBlank() } ?: candidates.firstOrNull()
    }
}
