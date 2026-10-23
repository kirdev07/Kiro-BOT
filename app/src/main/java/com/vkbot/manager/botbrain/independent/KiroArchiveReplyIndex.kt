package com.vkbot.manager.botbrain.independent

/**
 * Fast, independent exact-match index for archived answers.
 * Supports safe wildcard masks and validated media; never executes regex rules.
 */
internal class KiroArchiveReplyIndex(rows: List<KiroAnswerArchive.Row>) {
    private fun validLimit(row: KiroAnswerArchive.Row): Boolean {
        val raw = row.repetitionLimit.trim()
        return raw.isEmpty() || (raw.toIntOrNull()?.let { it >= 0 } == true)
    }

    private val entries = rows.asSequence().filter {
        it.question.isNotBlank() && (it.answer.isNotBlank() || it.attachments.isNotBlank()) && validLimit(it) &&
            KiroSafeAttachments.parse(it.attachments) != null && !it.question.contains("*") &&
            !it.question.startsWith("regex:", ignoreCase = true)
    }.groupBy { KiroTextPreparation.words(decodeLegacyText(it.question)) }

    private fun decodeLegacyText(value: String): String =
        value.replace("<br>", "\n").replace("<pipe>", "|")

    private val masks = rows.filter {
        it.question.contains("*") && !it.question.startsWith("regex:", ignoreCase = true) &&
            KiroSafeAttachments.parse(it.attachments) != null && (it.answer.isNotBlank() || it.attachments.isNotBlank()) && validLimit(it) &&
            it.question.length <= 2048 && it.question.count { c -> c == '*' } <= 16
    }

    // Literal-leading masks only need testing when the first normalized character matches.
    private val indexedMasks = masks.withIndex().toList()
    private val masksByFirstChar = indexedMasks.filter {
        !KiroTextPreparation.normalize(decodeLegacyText(it.value.question)).startsWith("*")
    }.groupBy { KiroTextPreparation.normalize(decodeLegacyText(it.value.question)).first() }
    private val leadingStarMasks = indexedMasks.filter {
        KiroTextPreparation.normalize(decodeLegacyText(it.value.question)).startsWith("*")
    }

    fun find(message: String, context: String, allowed: (KiroAnswerArchive.Row) -> Boolean): KiroAnswerArchive.Row? {
        val tokens = KiroTextPreparation.words(message)
        if (tokens.isEmpty() && message.isBlank()) return null
        val candidates = entries[tokens].orEmpty().filter { row ->
            (row.requiredContext.isBlank() || row.requiredContext.trim().equals(context, ignoreCase = true)) &&
                allowed(row)
        }
        // Context-specific replies take priority over general replies.
        val exact = candidates.firstOrNull { it.requiredContext.isNotBlank() } ?: candidates.firstOrNull()
        if (exact != null) return exact
        val first = KiroTextPreparation.normalize(message).firstOrNull()
        val possibleMasks = if (first == null) leadingStarMasks else
            masksByFirstChar[first].orEmpty() + leadingStarMasks
        val matches = possibleMasks.sortedBy { it.index }.map { it.value }.filter { row ->
            (row.requiredContext.isBlank() || row.requiredContext.trim().equals(context, ignoreCase = true)) &&
                allowed(row) && KiroSafeMask.matches(decodeLegacyText(row.question), message)
        }
        return matches.firstOrNull { it.requiredContext.isNotBlank() } ?: matches.firstOrNull()
    }
}
