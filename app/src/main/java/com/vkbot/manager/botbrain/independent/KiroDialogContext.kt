package com.vkbot.manager.botbrain.independent

/**
 * Session-scoped context for an independently authored conversation engine.
 * No persistent storage, global user tracking, or legacy database dependency.
 */
internal class KiroDialogContext(private val capacity: Int = 8) {
    init { require(capacity > 0) }

    data class Turn(val userMessage: String, val botReply: String)

    private val history = ArrayDeque<Turn>()

    @Synchronized
    fun record(userMessage: String, botReply: String) {
        if (userMessage.isBlank() || botReply.isBlank()) return
        if (history.size == capacity) history.removeFirst()
        history.addLast(Turn(userMessage, botReply))
    }

    @Synchronized
    fun recent(): List<Turn> = history.toList()

    @Synchronized
    fun lastReply(): String? = history.lastOrNull()?.botReply

    @Synchronized
    fun clear() = history.clear()
}
