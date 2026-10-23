package com.vkbot.manager.botbrain.independent

/**
 * Loads only explicitly provided, newly authored Kiro data.
 * Does not read historical packaged answer resources or change production bots.
 */
internal class KiroBrainBundle private constructor(
    val replies: List<KiroReplyIndex.Record>,
    val followUps: List<KiroFollowUpRules.Rule>
) {
    fun newSession(historyLimit: Int = 8): KiroConversationEngine =
        KiroConversationEngine(replies, historyLimit, followUps)

    companion object {
        fun load(replyFile: String, dialogFile: String): KiroBrainBundle {
            val replies = KiroReplyFormat.parse(replyFile)
            val rules = KiroDialogFormat.parse(dialogFile)
            val knownIds = replies.map { it.id }.toMutableSet()
            val pending = rules.toMutableList()
            // Follow-ups may refer to a reply or an earlier follow-up rule.
            while (pending.isNotEmpty()) {
                val reachable = pending.filter { it.afterRecordId in knownIds }
                require(reachable.isNotEmpty()) { "Unreachable or cyclic follow-up rules" }
                reachable.forEach { knownIds.add(it.id) }
                pending.removeAll(reachable.toSet())
            }
            require(replies.map { it.id }.toSet().intersect(rules.map { it.id }.toSet()).isEmpty()) {
                "Reply and follow-up identifiers must be unique"
            }
            return KiroBrainBundle(replies, rules)
        }
    }
}
