package com.vkbot.manager.botbrain.independent

/**
 * Opt-in, read-only migration of plain answer.txt rows to KIRO-REPLIES/1.
 * Unsupported legacy features are reported rather than silently discarded.
 * Never writes over the original database.
 */
internal object KiroAnswerMigration {
    data class Preview(
        val converted: String,
        val imported: Int,
        val skipped: Int,
        val warnings: List<String>
    )

    fun preview(source: String): Preview {
        val records = mutableListOf<KiroReplyIndex.Record>()
        val warnings = mutableListOf<String>()
        var skipped = 0
        source.lineSequence().forEachIndexed { index, raw ->
            val line = raw.removePrefix("\uFEFF").trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val parts = line.split("|", limit = 7)
            val number = index + 1
            if (parts.size < 2) {
                skipped++
                warnings.add("Line $number: missing answer")
                return@forEachIndexed
            }
            val prompt = parts[0].trim()
            val reply = parts[1].trim()
            val hasMetadata = parts.drop(2).any { it.isNotBlank() && it != "0" }
            val unsupportedPattern = prompt.contains("*") || prompt.startsWith("regex:", ignoreCase = true)
            if (prompt.isBlank() || reply.isBlank() || hasMetadata || unsupportedPattern) {
                skipped++
                warnings.add("Line $number: unsupported fields, pattern or empty text")
                return@forEachIndexed
            }
            records.add(KiroReplyIndex.Record("legacy-line-$number", prompt, reply))
        }
        return Preview(KiroReplyFormat.export(records), records.size, skipped, warnings)
    }
}
