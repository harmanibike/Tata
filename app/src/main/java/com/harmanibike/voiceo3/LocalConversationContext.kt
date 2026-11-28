package com.harmanibike.voiceo3

import java.util.ArrayDeque
import java.util.Locale

/**
 * Short-lived context for local generation. Nothing is persisted.
 * Previous turns are attached only when the new message looks like a follow-up.
 */
class LocalConversationContext(
    private val maxTurns: Int = 2,
    private val promptLimit: Int = 1400
) {
    private val turns = ArrayDeque<Pair<String, String>>()

    fun buildPrompt(rawQuestion: String): String {
        val question = rawQuestion.trim().take(700)
        if (question.isBlank()) return ""
        val recent = if (isFollowUp(question)) turns.toList().takeLast(maxTurns.coerceAtLeast(0)) else emptyList()
        if (recent.isEmpty()) return "Pertanyaan pengguna: $question"

        val history = recent.joinToString("\n") { pair ->
            "Pengguna: ${pair.first.take(180)}\nTata: ${pair.second.take(260)}"
        }
        return ("Riwayat percakapan sebelumnya (gunakan hanya bagian yang relevan):\n" +
            history + "\n\nPertanyaan terbaru pengguna: " + question).take(promptLimit.coerceAtLeast(200))
    }

    fun remember(question: String, answer: String) {
        val cleanQuestion = question.trim().take(180)
        val cleanAnswer = answer.trim().take(260)
        if (cleanQuestion.isBlank() || cleanAnswer.isBlank()) return
        turns.addLast(cleanQuestion to cleanAnswer)
        while (turns.size > maxTurns.coerceAtLeast(1)) turns.removeFirst()
    }

    fun clear() {
        turns.clear()
    }

    fun size(): Int = turns.size

    private fun isFollowUp(rawText: String): Boolean {
        val text = rawText.lowercase(Locale("id", "ID"))
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.length > 100) return false
        val phrases = listOf(
            "lanjutkan", "lanjut", "teruskan", "terus",
            "jelaskan lagi", "perjelas", "lebih rinci", "lebih detail",
            "contohnya", "contoh lainnya", "contoh lain",
            "apa maksudnya", "maksudnya apa", "mengapa demikian",
            "kenapa begitu", "bagaimana dengan itu", "bagaimana tadi",
            "yang tadi", "hal tadi", "topik tadi", "itu bagaimana",
            "itu maksudnya", "lalu bagaimana", "selanjutnya bagaimana"
        )
        return phrases.any { text == it || text.startsWith("$it ") } ||
            text in setOf("itu", "tersebut", "dia", "yang mana", "berapa tadi")
    }
}
