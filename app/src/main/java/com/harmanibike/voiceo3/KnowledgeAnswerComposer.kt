package com.harmanibike.voiceo3

/**
 * Combines only approved local knowledge entries.
 * This is evidence aggregation, not free-form factual generation.
 */
object KnowledgeAnswerComposer {
    fun compose(query: String, entries: List<KnowledgeEntry>): String? {
        if (query.isBlank() || entries.isEmpty()) return null
        val unique = entries
            .filter { it.question.isNotBlank() && it.answer.isNotBlank() }
            .distinctBy { normalize(it.answer) }
            .take(3)
        if (unique.isEmpty()) return null
        if (unique.size == 1) return unique.first().answer.trim()

        return buildString {
            append("Berdasarkan pengetahuan Tata yang sudah disetujui, berikut informasi yang relevan:\n")
            unique.forEachIndexed { index, entry ->
                append("\n")
                append(index + 1)
                append(". ")
                append(entry.question.trim())
                append("\n")
                append(entry.answer.trim())
                append("\n")
            }
            append("\nInformasi di atas berasal dari beberapa catatan pengetahuan lokal. Jika ada bagian yang belum sesuai, Anda dapat mengoreksinya melalui Learning Lab.")
        }
    }

    private fun normalize(value: String): String =
        value.lowercase(java.util.Locale("id", "ID"))
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
