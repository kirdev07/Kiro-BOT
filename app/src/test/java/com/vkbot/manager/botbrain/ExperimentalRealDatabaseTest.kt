package com.vkbot.manager.botbrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Offline evaluation against the shipped corpus. Does not affect the running bot. */
class ExperimentalRealDatabaseTest {
    companion object {
        private lateinit var corpus: List<AnswerElement>
        private lateinit var synonyms: Map<String, String>

        @BeforeClass @JvmStatic fun loadCorpus() {
            var nextId = 1L
            corpus = File("src/main/assets/answer.txt").readLines(Charsets.UTF_8)
                .map { it.removePrefix(Char(0xFEFF).toString()).trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val parts = line.split("|")
                    if (parts.size < 2) null else AnswerElement(nextId++, parts[0].trim(), parts[1].trim())
                }
            synonyms = TextAnalyzer.parseSynonyms(File("src/main/assets/synonyms.txt").readLines(Charsets.UTF_8))
        }
    }

    @Test fun sampleExactQuestionsAreRankedFirst() {
        val questions = corpus.asSequence()
            .map { it.questionText }
            .filter { it.isNotBlank() && !it.contains("*") && !it.startsWith("regex:", ignoreCase = true) }
            .distinct()
            .take(40)
            .toList()
        assertTrue("Corpus must contain ordinary questions", questions.isNotEmpty())
        for (question in questions) {
            val top = ExperimentalAnswerRanker.rank(question, corpus, synonyms, limit = 1)
            assertTrue("No match for: $question", top.isNotEmpty())
            assertEquals("Incorrect top match for: $question", 1.0, top.first().score, 0.000001)
        }
    }

    @Test fun knownQuestionHasNonEmptyCandidatePool() {
        val query = corpus.first { it.questionText.isNotBlank() && !it.questionText.contains("*") && !it.questionText.startsWith("regex:", true) }.questionText
        val matches = ExperimentalAnswerRanker.rank(query, corpus, synonyms, limit = 10)
        assertTrue(matches.isNotEmpty())
        assertTrue(matches.size <= 10)
    }
}
