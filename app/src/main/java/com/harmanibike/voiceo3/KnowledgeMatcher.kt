package com.harmanibike.voiceo3

import java.util.Locale

data class KnowledgeMatch(
    val entry: KnowledgeEntry,
    val score: Double
)

/**
 * Conservative local retrieval over approved knowledge entries.
 * It ranks textual overlap only; it does not infer truth or create new facts.
 */
object KnowledgeMatcher {
    private val stopWords = setOf(
        "apa", "siapa", "kapan", "dimana", "di", "ke", "dari", "yang", "dan",
        "atau", "itu", "ini", "adalah", "ialah", "dengan", "untuk", "pada",
        "bagaimana", "mengapa", "kenapa", "tolong", "jelaskan", "sebutkan",
        "the", "a", "an", "is", "are", "of", "to", "and", "in"
    )

    fun rank(query: String, entries: List<KnowledgeEntry>, limit: Int = 3): List<KnowledgeMatch> {
        val queryNormalized = normalize(query)
        val queryTokens = tokens(query)
        if (queryNormalized.isBlank() || limit <= 0) return emptyList()

        return entries.mapNotNull { entry ->
            val questionNormalized = normalize(entry.question)
            val allTerms = listOf(entry.question) + entry.keywords
            val entryTokens = allTerms.flatMap(::tokens).toSet()
            val overlap = queryTokens.intersect(entryTokens)
            val exactPhrase = questionNormalized == queryNormalized
            val containsPhrase = questionNormalized.contains(queryNormalized) ||
                queryNormalized.contains(questionNormalized).takeIf { questionNormalized.length >= 8 } == true
            val coverage = overlap.size.toDouble() / queryTokens.size.toDouble()
            val specificity = overlap.sumOf { token -> if (token.length >= 7) 1.5 else 1.0 }
            val score = when {
                exactPhrase -> 100.0
                containsPhrase -> 20.0 + coverage * 10.0
                overlap.isEmpty() -> 0.0
                else -> coverage * 10.0 + specificity
            }
            val enoughEvidence = exactPhrase ||
                (queryTokens.isNotEmpty() && containsPhrase) ||
                (overlap.size >= 2 && coverage >= 0.5) ||
                (queryTokens.size == 1 && overlap.size == 1 && queryTokens.first().length >= 6)
            if (enoughEvidence && score > 0.0) KnowledgeMatch(entry, score) else null
        }.sortedByDescending { it.score }.take(limit)
    }

    private fun tokens(value: String): Set<String> =
        normalize(value).split(' ')
            .filter { it.length >= 3 && it !in stopWords }
            .toSet()

    private fun normalize(value: String): String =
        value.lowercase(Locale("id", "ID"))
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
