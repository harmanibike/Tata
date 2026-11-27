package com.harmanibike.voiceo3

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
        entries.put(
            JSONObject()
                .put("question", question)
                .put("keywords", JSONArray(keywords))
                .put("answer", answer)
        )
        file.writeText(entries.toString(2), Charsets.UTF_8)
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
}
