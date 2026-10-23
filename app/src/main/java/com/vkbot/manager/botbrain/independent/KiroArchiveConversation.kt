package com.vkbot.manager.botbrain.independent

/**
 * Experimental archive-backed session. Each instance belongs to one conversation.
 * Unsupported attachments and patterns are deliberately not executed.
 */
internal class KiroArchiveConversation(rows: List<KiroAnswerArchive.Row>) {
    private val index = KiroArchiveReplyIndex(rows)
    private var context = ""
    private val usage = mutableMapOf<Int, Int>()
    private var lastLine: Int? = null
    private var consecutive = 0

    data class Reply(val text: String, val attachments: List<String>)

    @Synchronized
    fun respond(message: String): String? = respondRich(message)?.text

    @Synchronized
    fun respondRich(message: String): Reply? {
        if (message.isBlank()) return null
        val candidate = index.find(message, context) { row ->
            val limit = row.repetitionLimit.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
            limit == 0 || row.lineNumber != lastLine || consecutive < limit
        } ?: return null

        usage[candidate.lineNumber] = (usage[candidate.lineNumber] ?: 0) + 1
        consecutive = if (lastLine == candidate.lineNumber) consecutive + 1 else 1
        lastLine = candidate.lineNumber
        if (candidate.resultContext.isNotBlank()) context = candidate.resultContext.trim()
        return Reply(candidate.answer.replace("<br>", "\n").replace("<pipe>", "|"),
            KiroSafeAttachments.parse(candidate.attachments) ?: emptyList())
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
