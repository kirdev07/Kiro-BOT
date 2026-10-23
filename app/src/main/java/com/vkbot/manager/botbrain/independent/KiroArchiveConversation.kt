package com.vkbot.manager.botbrain.independent

/**
 * Experimental archive-backed session. Each instance belongs to one conversation.
 * Unsupported attachments and patterns are deliberately not executed.
 */
internal class KiroArchiveConversation(rows: List<KiroAnswerArchive.Row>) {
    private val eligible = rows.filter {
        it.question.isNotBlank() && it.answer.isNotBlank() &&
            it.attachments.isBlank() &&
            !it.question.contains("*") &&
            !it.question.startsWith("regex:", ignoreCase = true)
    }
    private var context = ""
    private val usage = mutableMapOf<Int, Int>()
    private var lastLine: Int? = null
    private var consecutive = 0

    @Synchronized
    fun respond(message: String): String? {
        if (message.isBlank()) return null
        val query = KiroTextPreparation.words(message)
        if (query.isEmpty()) return null
        val candidate = eligible.firstOrNull { row ->
            val required = row.requiredContext.trim()
            val matchesContext = required.isEmpty() || required.equals(context, ignoreCase = true)
            val matchesQuestion = KiroTextPreparation.words(row.question) == query
            val limit = row.repetitionLimit.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
            val repeatAllowed = limit == 0 || row.lineNumber != lastLine || consecutive < limit
            matchesContext && matchesQuestion && repeatAllowed
        } ?: return null

        usage[candidate.lineNumber] = (usage[candidate.lineNumber] ?: 0) + 1
        consecutive = if (lastLine == candidate.lineNumber) consecutive + 1 else 1
        lastLine = candidate.lineNumber
        if (candidate.resultContext.isNotBlank()) context = candidate.resultContext.trim()
        return candidate.answer
    }

    @Synchronized
    fun currentContext(): String = context

    @Synchronized
    fun usageForSourceLine(lineNumber: Int): Int = usage[lineNumber] ?: 0

    @Synchronized
    fun reset() {
        context = ""
        usage.clear()
        lastLine = null
        consecutive = 0
    }
}
