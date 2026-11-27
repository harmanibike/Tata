package com.harmanibike.voiceo3

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class KnowledgeEntry(
    val question: String,
    val keywords: List<String>,
    val answer: String
)

class KnowledgeStore(context: Context) {
    private val file = File(context.filesDir, "knowledge.json")

    init {
        if (!file.exists()) {
            val defaults = JSONArray().put(
                JSONObject()
                    .put("question", "alamat Harmani Bike")
                    .put("keywords", JSONArray(listOf("alamat", "lokasi", "harmani bike", "toko")))
                    .put("answer", "Alamat Harmani Bike: Jln. Pancasila No. 5, Sukosewu.")
            )
            file.writeText(defaults.toString(2), Charsets.UTF_8)
        }
    }

    @Synchronized
    fun getAll(): List<KnowledgeEntry> = runCatching {
        val entries = JSONArray(file.readText(Charsets.UTF_8))
        (0 until entries.length()).mapNotNull { index ->
            val item = entries.optJSONObject(index) ?: return@mapNotNull null
            val words = item.optJSONArray("keywords") ?: JSONArray()
            KnowledgeEntry(
                item.optString("question"),
                (0 until words.length()).map { words.optString(it) },
                item.optString("answer")
            )
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun search(query: String): String? {
        val normalized = normalize(query)
        if (normalized.isBlank()) return null
        return runCatching {
            val entries = JSONArray(file.readText(Charsets.UTF_8))
            var bestScore = 0
            var bestAnswer: String? = null
            for (i in 0 until entries.length()) {
                val item = entries.getJSONObject(i)
                val terms = mutableListOf(item.optString("question"))
                val keywords = item.optJSONArray("keywords") ?: JSONArray()
                for (k in 0 until keywords.length()) terms += keywords.optString(k)
                val score = terms.sumOf { term ->
                    val candidate = normalize(term)
                    when {
                        candidate.isNotEmpty() && normalized.contains(candidate) -> candidate.split(' ').size + 2
                        candidate.isNotEmpty() && candidate.split(' ').any { it.length > 3 && normalized.contains(it) } -> 1
                        else -> 0
                    }
                }
                if (score > bestScore) {
                    bestScore = score
                    bestAnswer = item.optString("answer").takeIf { it.isNotBlank() }
                }
            }
            bestAnswer
        }.getOrNull()
    }

    @Synchronized
    fun add(question: String, keywords: List<String>, answer: String) {
        val entries = runCatching { JSONArray(file.readText(Charsets.UTF_8)) }.getOrDefault(JSONArray())
        entries.put(toJson(question, keywords, answer))
        file.writeText(entries.toString(2), Charsets.UTF_8)
    }

    @Synchronized
    fun update(index: Int, question: String, keywords: List<String>, answer: String) {
        val entries = JSONArray(file.readText(Charsets.UTF_8))
        if (index !in 0 until entries.length()) return
        entries.put(index, toJson(question, keywords, answer))
        file.writeText(entries.toString(2), Charsets.UTF_8)
    }

    @Synchronized
    fun delete(index: Int) {
        val entries = JSONArray(file.readText(Charsets.UTF_8))
        if (index !in 0 until entries.length()) return
        val updated = JSONArray()
        for (i in 0 until entries.length()) if (i != index) updated.put(entries.get(i))
        file.writeText(updated.toString(2), Charsets.UTF_8)
    }

    @Synchronized
    fun exportJson(): String = file.readText(Charsets.UTF_8)

    @Synchronized
    fun importJson(json: String): Int {
        val source = JSONArray(json)
        val validated = JSONArray()
        for (i in 0 until source.length()) {
            val item = source.optJSONObject(i)
                ?: throw IllegalArgumentException("Format data ke-${i + 1} tidak valid.")
            val question = item.optString("question").trim()
            val answer = item.optString("answer").trim()
            require(question.isNotBlank() && answer.isNotBlank()) {
                "Pertanyaan dan jawaban pada data ke-${i + 1} wajib diisi."
            }
            val rawKeywords = item.optJSONArray("keywords") ?: JSONArray()
            val keywords = (0 until rawKeywords.length())
                .map { rawKeywords.optString(it).trim() }
                .filter { it.isNotBlank() }
            validated.put(toJson(question, keywords, answer))
        }
        file.writeText(validated.toString(2), Charsets.UTF_8)
        return validated.length()
    }

    private fun toJson(question: String, keywords: List<String>, answer: String) =
        JSONObject()
            .put("question", question.trim())
            .put("keywords", JSONArray(keywords.map { it.trim() }.filter { it.isNotBlank() }))
            .put("answer", answer.trim())

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
}
