package com.vkbot.manager.botbrain.independent

/**
 * Independent lossless archive for migration review. All seven raw columns are
 * preserved, including attachments, repetition, usage and context metadata.
 * This is a transport format, not an executable rule or regex parser.
 */
internal object KiroAnswerArchive {
    private const val header = "# KIRO-ANSWER-ARCHIVE/1"

    data class Row(
        val lineNumber: Int,
        val question: String,
        val answer: String,
        val attachments: String,
        val repetitionLimit: String,
        val usageCount: String,
        val requiredContext: String,
        val resultContext: String
    )

    fun importLegacy(source: String): List<Row> =
        source.lineSequence().mapIndexedNotNull { index, raw ->
            val line = raw.removePrefix("\uFEFF").trim()
            if (line.isEmpty() || line.startsWith("#")) return@mapIndexedNotNull null
            val fields = line.split("|", limit = 7)
            require(fields.size >= 2) { "Invalid answer row at line ${index + 1}" }
            val padded = fields + List(7 - fields.size) { "" }
            Row(index + 1, padded[0], padded[1], padded[2], padded[3],
                padded[4], padded[5], padded[6])
        }.toList()

    fun export(rows: List<Row>): String = buildString {
        append(header).append('\n')
        rows.forEach { row ->
            append(listOf(
                row.lineNumber.toString(), row.question, row.answer,
                row.attachments, row.repetitionLimit, row.usageCount,
                row.requiredContext, row.resultContext
            ).joinToString("\t", transform = ::escape)).append('\n')
        }
    }

    fun parse(text: String): List<Row> {
        val lines = text.lineSequence().iterator()
        require(lines.hasNext() && lines.next() == header) { "Unsupported archive format" }
        val seenLineNumbers = HashSet<Int>()
        val result = ArrayList<Row>()
        while (lines.hasNext()) {
            val line = lines.next()
            if (line.isEmpty()) continue
            val parts = line.split('\t').map(::unescape)
            require(parts.size == 8) { "Invalid archive row" }
            val lineNumber = parts[0].toInt()
            require(lineNumber > 0) { "Invalid source line number" }
            require(seenLineNumbers.add(lineNumber)) { "Duplicate source line number" }
            result.add(Row(lineNumber, parts[1], parts[2], parts[3], parts[4],
                parts[5], parts[6], parts[7]))
        }
        return result
    }

    private fun escape(value: String): String = buildString {
        value.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
    }

    private fun unescape(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            val c = value[i++]
            if (c != '\\') { append(c); continue }
            require(i < value.length) { "Trailing escape" }
            append(when (value[i++]) {
                '\\' -> '\\'
                't' -> '\t'
                'n' -> '\n'
                'r' -> '\r'
                else -> throw IllegalArgumentException("Invalid escape")
            })
        }
    }
}
