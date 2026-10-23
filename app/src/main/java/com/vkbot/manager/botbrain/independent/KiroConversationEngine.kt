package com.vkbot.manager.botbrain.independent

/**
 * Isolated coordinator for user-authored response records.
 * Create one engine instance per chat; never share state across conversations.
 */
internal class KiroConversationEngine(
    records: List<KiroReplyIndex.Record>,
    historyLimit: Int = 8,
    followUps: List<KiroFollowUpRules.Rule> = emptyList()
) {
    private val index = KiroReplyIndex(records)
    private val history = KiroDialogContext(historyLimit)
    private val followUpIndex = KiroFollowUpRules(followUps)
    private var lastRecordId: String? = null

    sealed class Result {
        data class Answer(val text: String, val recordId: String) : Result()
        object NoMatch : Result()
    }

    @Synchronized
    fun respond(message: String): Result {
        if (message.isBlank()) return Result.NoMatch
        val followUp = followUpIndex.find(lastRecordId, message)
        if (followUp != null) {
            lastRecordId = followUp.id
            history.record(message, followUp.reply)
            return Result.Answer(followUp.reply, followUp.id)
        }
        val match = index.find(message)
        if (match != null) {
            lastRecordId = match.id
            history.record(message, match.reply)
            return Result.Answer(match.reply, match.id)
        }
        lastRecordId = null
        return Result.NoMatch
    }

    fun recentTurns(): List<KiroDialogContext.Turn> = history.recent()

    @Synchronized
    fun resetSession() {
        lastRecordId = null
        history.clear()
    }
}
