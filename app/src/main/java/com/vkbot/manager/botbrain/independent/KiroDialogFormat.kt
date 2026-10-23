package com.vkbot.manager.botbrain.independent

/**
 * Versioned, standalone format for user-authored contextual dialog rules.
 * Does not import or modify any legacy response data.
 */
internal object KiroDialogFormat {
    private const val header = "# KIRO-DIALOGS/1"

    fun export(rules: List<KiroFollowUpRules.Rule>): String = buildString {
        append(header).append('\n')
        val ids = mutableSetOf<String>()
        for (rule in rules) {
            require(rule.id.isNotBlank() && rule.afterRecordId.isNotBlank() &&
                rule.message.isNotBlank() && rule.reply.isNotBlank())
            require(ids.add(rule.id)) { "Duplicate rule id" }
            append(listOf(rule.id, rule.afterRecordId, rule.message, rule.reply)
                .joinToString("\t", transform = ::encode)).append('\n')
        }
    }

    fun parse(input: String): List<KiroFollowUpRules.Rule> {
        val lines = input.lineSequence().toList()
        require(lines.firstOrNull() == header) { "Unsupported dialog format" }
        val ids = mutableSetOf<String>()
        return lines.drop(1).filter(String::isNotBlank).mapIndexed { offset, line ->
            val fields = line.split('\t')
            require(fields.size == 4) { "Malformed dialog rule at row ${offset + 2}" }
            val decoded = fields.map(::decode)
            require(decoded.all(String::isNotBlank)) { "Blank dialog field" }
            require(ids.add(decoded[0])) { "Duplicate dialog rule id" }
            KiroFollowUpRules.Rule(decoded[0], decoded[1], decoded[2], decoded[3])
        }
    }

    private fun encode(value: String): String = buildString {
        for (char in value) {
            when (char) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(char)
            }
        }
    }

    private fun decode(value: String): String = buildString {
        var offset = 0
        while (offset < value.length) {
            val char = value[offset++]
            if (char != '\\') {
                append(char)
                continue
            }
            require(offset < value.length) { "Incomplete escape" }
            append(when (value[offset++]) {
                '\\' -> '\\'
                't' -> '\t'
                'n' -> '\n'
                'r' -> '\r'
                else -> throw IllegalArgumentException("Invalid escape")
            })
        }
    }
}
