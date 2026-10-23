package com.vkbot.manager.botbrain.independent

/**
 * Safe opt-in routing for future Telegram integration.
 *
 * No production Telegram handler calls this yet. Disabled by default.
 * A null result means the existing caller must handle the message normally.
 */
internal class KiroBrainSwitch(
    private val bundle: KiroBrainBundle,
    private var enabled: Boolean = false
) {
    private val sessions = mutableMapOf<Long, KiroConversationEngine>()

    @Synchronized
    fun setEnabled(value: Boolean) {
        if (enabled != value) {
            sessions.clear()
            enabled = value
        }
    }

    @Synchronized
    fun isEnabled(): Boolean = enabled

    @Synchronized
    fun reply(chatId: Long, message: String): String? {
        if (!enabled) return null
        val session = sessions.getOrPut(chatId) { bundle.newSession() }
        return when (val result = session.respond(message)) {
            is KiroConversationEngine.Result.Answer -> result.text
            KiroConversationEngine.Result.NoMatch -> null
        }
    }

    @Synchronized
    fun forgetChat(chatId: Long) {
        sessions.remove(chatId)
    }
}
