package com.vkbot.manager.botbrain.independent

/**
 * Explicit, user-authored follow-up rules. A rule is active only immediately
 * after its required response record and never guesses from unrelated history.
 */
internal class KiroFollowUpRules(rules: List<Rule>) {
    data class Rule(
        val id: String,
        val afterRecordId: String,
        val message: String,
        val reply: String
    )

    private val validRules = rules.filter {
        it.id.isNotBlank() && it.afterRecordId.isNotBlank() &&
            it.message.isNotBlank() && it.reply.isNotBlank()
    }

    fun find(afterRecordId: String?, message: String): Rule? {
        if (afterRecordId == null) return null
        val words = KiroTextPreparation.words(message)
        if (words.isEmpty()) return null
        return validRules.firstOrNull {
            it.afterRecordId == afterRecordId &&
                KiroTextPreparation.words(it.message) == words
        }
    }
}
