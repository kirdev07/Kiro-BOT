package com.vkbot.manager.botbrain.independent

/**
 * Isolated coordinator for user-authored response records.
 * Conversation state belongs to one instance/session; never share it across chats.
 */
internal class KiroConversationEngine(
    records: List<KiroReplyIndex.Record>,
    historyLimit: Int = 8
) {
    private val index = KiroReplyIndex(records)
    private val history = KiroDialogContext(historyLimit)

    sealed class Result {
        data class Answer(val text: String, val recordId: String) : Result()
        object NoMatch : Result()
    }

    @Synchronized
    fun respond(message: String): Result {
        if (message.isBlank()) return Result.NoMatch
        val match = index.find(message)
        if (match != null) {
            history.record(message, match.reply)
            return Result.Answer(match.reply, match.id)
        }
        return Result.NoMatch
    }

    fun recentTurns(): List<KiroDialogContext.Turn> = history.recent()

    fun resetSession() = history.clear()
}
