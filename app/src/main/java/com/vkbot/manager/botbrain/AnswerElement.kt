package com.vkbot.manager.botbrain

import java.io.Serializable
import java.util.Date

/** A stored question/answer record with separate runtime usage count. */
data class AnswerElement(
    val id: Long,
    val questionText: String = "",
    val answerText: String = "",
    val answerAttachments: List<Attachment> = emptyList(),
    private val _createdDate: Date = Date(),
    var usageCount: Int = 0,
    val repetitionLimit: Int = 0,
    val requiredContext: String = "",
    val resultContext: String = ""
) : Serializable {
    val createdDate: Date get() = Date(_createdDate.time)

    fun incrementUsageCount() {
        usageCount += 1
    }

    override fun toString(): String = "ID: $id | Q: $questionText"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnswerElement) return false
        return id == other.id && questionText == other.questionText &&
            answerText == other.answerText && answerAttachments == other.answerAttachments &&
            repetitionLimit == other.repetitionLimit && requiredContext == other.requiredContext &&
            resultContext == other.resultContext
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + questionText.hashCode()
        result = 31 * result + answerText.hashCode()
        result = 31 * result + repetitionLimit
        result = 31 * result + answerAttachments.hashCode()
        result = 31 * result + requiredContext.hashCode()
        result = 31 * result + resultContext.hashCode()
        return result
    }
}
