package com.vkbot.manager.botbrain.independent

/** Opt-in archive-backed replies with bounded per-chat conversation state. */
internal class KiroArchiveSwitch(
    rows: List<KiroAnswerArchive.Row>,
    private val maxSessions: Int = 256
) {
    init { require(maxSessions > 0) }
    private val records = rows.toList()
    private val sessions = LinkedHashMap<Long, KiroArchiveConversation>(16, 0.75f, true)

    @Synchronized
    fun reply(sessionId: Long, message: String): String? {
        if (message.isBlank()) return null
        val session = sessions[sessionId] ?: KiroArchiveConversation(records).also {
            if (sessions.size >= maxSessions) {
                val oldest = sessions.keys.iterator()
                if (oldest.hasNext()) { oldest.next(); oldest.remove() }
            }
            sessions[sessionId] = it
        }
        return session.respond(message)
    }

    @Synchronized
    fun forget(sessionId: Long) { sessions.remove(sessionId) }

    @Synchronized
    fun clear() { sessions.clear() }
}
