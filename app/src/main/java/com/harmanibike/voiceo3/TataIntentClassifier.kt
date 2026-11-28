package com.harmanibike.voiceo3

import java.util.Locale

enum class TataIntent {
    FOLLOW_UP,
    PLAN,
    COMPARE,
    DECISION,
    BRAINSTORM,
    UNKNOWN
}

/**
 * Lightweight Indonesian intent classifier. It recognizes paraphrases, not factual content.
 * A classification is only a routing hint; it must never be treated as evidence.
 */
class TataIntentClassifier {
    fun classify(rawText: String): TataIntent {
        val text = normalize(rawText)
        if (text.isBlank()) return TataIntent.UNKNOWN

        if (text in followUps || followUps.any { text.startsWith("$it ") }) {
            return TataIntent.FOLLOW_UP
        }
        if (matches(text, planPhrases)) return TataIntent.PLAN
        if (matches(text, comparePhrases)) return TataIntent.COMPARE
        if (matches(text, decisionPhrases)) return TataIntent.DECISION
        if (matches(text, brainstormPhrases)) return TataIntent.BRAINSTORM
        return TataIntent.UNKNOWN
    }

    private fun matches(text: String, phrases: Set<String>): Boolean =
        phrases.any { text.contains(it) }

    private fun normalize(value: String): String =
        value.lowercase(Locale("id", "ID"))
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private val followUps = setOf(
        "lanjut", "lanjutkan", "terus", "teruskan", "contohnya",
        "contoh lain", "jelaskan lagi", "lebih rinci", "lebih detail", "perjelas",
        "bagaimana selanjutnya", "apa langkah berikutnya", "lalu bagaimana"
    )
    private val planPhrases = setOf(
        "buatkan rencana", "buat rencana", "susun rencana", "bantu saya merencanakan",
        "bantu aku merencanakan", "susun langkah", "buatkan tahapan", "langkah apa saja",
        "bagaimana cara memulai", "bagaimana memulai", "mulai dari mana",
        "saya ingin belajar", "aku ingin belajar", "saya mau belajar", "aku mau belajar",
        "saya ingin membuat", "aku ingin membuat", "saya ingin mengembangkan",
        "aku ingin mengembangkan", "rencana saya adalah", "target saya adalah"
    )
    private val comparePhrases = setOf(
        "bandingkan", "apa perbedaan", "apa beda", "beda antara", "bedanya",
        "perbandingan antara", "mana lebih baik", "lebih bagus mana", "apa kelebihan dan kekurangan"
    )
    private val decisionPhrases = setOf(
        "menurutmu saya harus", "menurut kamu saya harus", "sebaiknya saya",
        "sebaiknya aku", "saya harus memilih", "aku harus memilih", "pilih yang mana",
        "lebih baik saya pilih", "mana yang sebaiknya dipilih"
    )
    private val brainstormPhrases = setOf(
        "beri ide", "berikan ide", "carikan ide", "ada ide", "punya ide",
        "kembangkan gagasan", "saran kreatif", "brainstorm", "ide untuk"
    )
}
