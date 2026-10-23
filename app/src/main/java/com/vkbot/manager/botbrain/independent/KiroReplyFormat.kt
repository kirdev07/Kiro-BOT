package com.vkbot.manager.botbrain.independent

/**
 * Minimal versioned UTF-8 reply format for newly authored user data.
 * The format does not load or transform the historical bundled answer corpus.
 *
 * Header: # KIRO-REPLIES/1
 * Rows: id<TAB>prompt<TAB>reply, with backslash escaping.
 */
internal object KiroReplyFormat {
    private const val header = "# KIRO-REPLIES/1"

    fun export(records: List<KiroReplyIndex.Record>): String = buildString {
        append(header).append('\n')
        records.forEach { record ->
            require(record.id.isNotBlank() && record.prompt.isNotBlank() && record.reply.isNotBlank())
            append(escape(record.id)).append('\t')
                .append(escape(record.prompt)).append('\t')
                .append(escape(record.reply)).append('\n')
        }
    }

    fun parse(input: String): List<KiroReplyIndex.Record> {
        val lines = input.lineSequence().toList()
        require(lines.firstOrNull() == header) { "Unsupported reply file format" }
        val ids = mutableSetOf<String>()
        return lines.drop(1).filter(String::isNotEmpty).mapIndexed { index, line ->
            val parts = line.split('\t')
            require(parts.size == 3) { "Invalid record on line ${index + 2}" }
            val record = KiroReplyIndex.Record(unescape(parts[0]), unescape(parts[1]), unescape(parts[2]))
            require(record.id.isNotBlank() && record.prompt.isNotBlank() && record.reply.isNotBlank()) {
                "Empty field on line ${index + 2}"
            }
            require(ids.add(record.id)) { "Duplicate record id on line ${index + 2}" }
            record
        }
    }

    private fun escape(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(char)
            }
        }
    }

    private fun unescape(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            val char = value[i++]
            if (char != '\\') {
                append(char)
                continue
            }
            require(i < value.length) { "Trailing escape character" }
            append(when (value[i++]) {
                '\\' -> '\\'
                't' -> '\t'
                'n' -> '\n'
                'r' -> '\r'
                else -> throw IllegalArgumentException("Unknown escape sequence")
            })
        }
    }
}
