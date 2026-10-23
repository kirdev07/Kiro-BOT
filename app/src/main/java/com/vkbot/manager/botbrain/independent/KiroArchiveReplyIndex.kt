package com.vkbot.manager.botbrain.independent

/**
 * Fast, independent exact-match index for archived answers.
 * Patterns and media are kept in the archive, but not executed by this index.
 */
internal class KiroArchiveReplyIndex(rows: List<KiroAnswerArchive.Row>) {
    private val entries = rows.asSequence().filter {
        it.question.isNotBlank() && it.answer.isNotBlank() &&
            KiroSafeAttachments.parse(it.attachments) != null && !it.question.contains("*") &&
            !it.question.startsWith("regex:", ignoreCase = true)
    }.groupBy { KiroTextPreparation.words(decodeLegacyText(it.question)) }

    private fun decodeLegacyText(value: String): String =
        value.replace("<br>", "\n").replace("<pipe>", "|")

    private val masks = rows.filter {
        it.question.contains("*") && !it.question.startsWith("regex:", ignoreCase = true) &&
            KiroSafeAttachments.parse(it.attachments) != null && it.answer.isNotBlank() &&
            it.question.length <= 2048 && it.question.count { c -> c == '*' } <= 16
    }

    fun find(message: String, context: String, allowed: (KiroAnswerArchive.Row) -> Boolean): KiroAnswerArchive.Row? {
        val tokens = KiroTextPreparation.words(message)
        if (tokens.isEmpty()) return null
        val candidates = entries[tokens].orEmpty().filter { row ->
            (row.requiredContext.isBlank() || row.requiredContext.trim().equals(context, ignoreCase = true)) &&
                allowed(row)
        }
        // Context-specific replies take priority over general replies.
        val exact = candidates.firstOrNull { it.requiredContext.isNotBlank() } ?: candidates.firstOrNull()
        if (exact != null) return exact
        val matches = masks.filter { row ->
            (row.requiredContext.isBlank() || row.requiredContext.trim().equals(context, ignoreCase = true)) &&
                allowed(row) && KiroSafeMask.matches(decodeLegacyText(row.question), message)
        }
        return matches.firstOrNull { it.requiredContext.isNotBlank() } ?: matches.firstOrNull()
    }
}
