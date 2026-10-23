package com.vkbot.manager.botbrain.independent

/** Read-only audit. Categories overlap, so category counts need not sum to total. */
internal object KiroArchiveAudit {
    data class Report(
        val total: Int,
        val ready: Int,
        val media: Int,
        val patterns: Int,
        val contextual: Int,
        val repeatLimits: Int,
        val emptyFields: Int,
        val invalidLimits: Int,
        val duplicatePairs: Int
    )

    fun inspect(rows: List<KiroAnswerArchive.Row>): Report {
        val seen = HashSet<List<String>>()
        var ready = 0
        var media = 0
        var patterns = 0
        var contextual = 0
        var repeatLimits = 0
        var emptyFields = 0
        var invalidLimits = 0
        var duplicates = 0
        for (row in rows) {
            val hasMedia = row.attachments.isNotBlank()
            val hasPattern = row.question.contains("*") ||
                row.question.startsWith("regex:", ignoreCase = true)
            val hasContext = row.requiredContext.isNotBlank() || row.resultContext.isNotBlank()
            val limit = row.repetitionLimit.trim()
            val badLimit = limit.isNotEmpty() && (limit.toIntOrNull() == null || limit.toInt() < 0)
            val empty = row.question.isBlank() || (row.answer.isBlank() && row.attachments.isBlank())
            if (hasMedia) media++
            if (hasPattern) patterns++
            if (hasContext) contextual++
            if (limit.isNotEmpty() && limit != "0") repeatLimits++
            if (badLimit) invalidLimits++
            if (empty) emptyFields++
            if (!seen.add(listOf(
                row.question.trim().lowercase(),
                row.answer.trim(),
                row.attachments.trim(),
                row.repetitionLimit.trim(),
                row.requiredContext.trim().lowercase(),
                row.resultContext.trim().lowercase()
            ))) duplicates++
            val validMedia = KiroSafeAttachments.parse(row.attachments) != null
            val supportedPattern = !row.question.startsWith("regex:", ignoreCase = true) &&
                (!row.question.contains("*") ||
                    (row.question.length <= 2048 && row.question.count { it == '*' } <= 16))
            val searchable = row.question.contains("*") || KiroTextPreparation.words(row.question).isNotEmpty()
            if (!empty && searchable && !badLimit && validMedia && supportedPattern) ready++
        }
        return Report(rows.size, ready, media, patterns, contextual, repeatLimits,
            emptyFields, invalidLimits, duplicates)
    }
}
