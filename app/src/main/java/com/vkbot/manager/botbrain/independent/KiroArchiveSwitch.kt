package com.vkbot.manager.botbrain.independent

/** Opt-in archive-backed replies with bounded per-chat conversation state. */
internal class KiroArchiveSwitch(
    rows: List<KiroAnswerArchive.Row>,
    private val maxSessions: Int = 256
) {
    init { require(maxSessions > 0) }
    private val index = KiroArchiveReplyIndex(rows.toList())
    private val sessions = LinkedHashMap<Long, KiroArchiveConversation>(16, 0.75f, true)

    @Synchronized
    fun reply(sessionId: Long, message: String): String? = replyRich(sessionId, message)?.text

    @Synchronized
    fun replyRich(sessionId: Long, message: String): KiroArchiveConversation.Reply? {
        if (message.isBlank()) return null
        val session = sessions[sessionId] ?: KiroArchiveConversation(index).also {
            if (sessions.size >= maxSessions) {
                val oldest = sessions.keys.iterator()
                if (oldest.hasNext()) { oldest.next(); oldest.remove() }
            }
            sessions[sessionId] = it
        }
        return session.respondRich(message)
    }

    @Synchronized
    fun forget(sessionId: Long) { sessions.remove(sessionId) }

    @Synchronized
    fun clear() { sessions.clear() }
}
