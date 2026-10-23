package com.vkbot.manager.botbrain.independent

/**
 * A new conversation coordinator for explicitly supplied response records.
 * It does not load legacy bundled answers or call external services.
 */
internal class KiroConversationEngine(records: List<KiroReplyIndex.Record>) {
    private val index = KiroReplyIndex(records)

    sealed class Result {
        data class Answer(val text: String, val recordId: String) : Result()
        object NoMatch : Result()
    }

    fun respond(message: String): Result {
        if (message.isBlank()) return Result.NoMatch
        val match = index.find(message) ?: return Result.NoMatch
        return Result.Answer(match.reply, match.id)
    }
}
