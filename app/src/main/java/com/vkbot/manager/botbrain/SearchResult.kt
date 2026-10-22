package com.vkbot.manager.botbrain

import java.io.Serializable

/** A ranked database answer with optional captured text groups. */
data class SearchResult(
    val answer: AnswerElement,
    val capturedGroups: List<String> = emptyList(),
    val tier: Int = 0,
    val score: Float = 1f
) : Serializable {
    fun hasGroups(): Boolean = capturedGroups.isNotEmpty()
}
