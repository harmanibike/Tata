package com.harmanibike.voiceo3

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class TransferLearningRecord(
    val question: String,
    val correction: String,
    val source: String = "",
    val createdAt: Long = 0L
)

data class LearningImportPreview(
    val newRecords: List<TransferLearningRecord>,
    val duplicateCount: Int,
    val conflictRecords: List<TransferLearningRecord>
) {
    val importCount: Int get() = newRecords.size + conflictRecords.size
    val conflictCount: Int get() = conflictRecords.size
}

/**
 * File pertukaran Learning Lab. Tidak membawa chat, memori pengguna, atau pengetahuan aktif.
 * Semua catatan dari file impor tetap harus ditinjau ulang di perangkat penerima.
 */
object LearningLabTransfer {
    const val FORMAT = "tata-learning-lab"
    const val SCHEMA_VERSION = 2
    const val MAX_RECORDS = 5000
    const val MAX_FILE_BYTES = 5 * 1024 * 1024

    fun encode(records: List<LearningRecord>, exportedAt: Long = System.currentTimeMillis()): String {
        require(records.size <= MAX_RECORDS) { "Maksimal $MAX_RECORDS catatan per file." }
        val array = JSONArray()
        records.forEach { record ->
            val createdAt = record.createdAt.takeIf { it > 0L } ?: exportedAt
            array.put(
                JSONObject()
                    .put("knowledgeId", questionIdentity(record.question))
                    .put("contentHash", fingerprint(record.question, record.correction))
                    .put("question", record.question)
                    .put("correction", record.correction)
                    .put("source", record.source)
                    .put("createdAt", createdAt)
            )
        }
        val payload = array.toString()
        return JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("exportedAt", exportedAt)
            .put("recordCount", records.size)
            .put("payloadHash", sha256(payload))
            .put("records", array)
            .toString(2)
    }

    fun decode(json: String): List<TransferLearningRecord> {
        val root = JSONObject(json)
        require(root.optString("format") == FORMAT) { "Ini bukan file Learning Lab Tata AI." }
        require(root.optInt("schemaVersion", -1) == SCHEMA_VERSION) { "Versi format file tidak didukung." }
        val exportedAt = root.optLong("exportedAt", 0L)
        require(exportedAt > 0L && exportedAt <= System.currentTimeMillis() + 86_400_000L) {
            "Waktu ekspor file tidak valid."
        }
        val array = root.optJSONArray("records") ?: throw IllegalArgumentException("Daftar catatan tidak ditemukan.")
        require(array.length() <= MAX_RECORDS) { "Jumlah catatan melebihi batas $MAX_RECORDS." }
        require(root.optInt("recordCount", -1) == array.length()) { "Jumlah catatan tidak cocok." }
        require(root.optString("payloadHash") == sha256(array.toString())) { "Pemeriksaan integritas file gagal. File mungkin berubah atau rusak." }
        val result = mutableListOf<TransferLearningRecord>()
        val seen = HashSet<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
                ?: throw IllegalArgumentException("Catatan ke-${index + 1} tidak valid.")
            val question = item.optString("question").trim()
            val correction = item.optString("correction").trim()
            val source = item.optString("source").trim()
            val createdAt = item.optLong("createdAt", 0L)
            require(question.isNotBlank() && correction.isNotBlank()) {
                "Konteks dan koreksi pada catatan ke-${index + 1} wajib diisi."
            }
            require(question.length <= 2000 && correction.length <= 6000 && source.length <= 1000) {
                "Catatan ke-${index + 1} melebihi batas panjang."
            }
            require(item.optString("knowledgeId") == questionIdentity(question)) {
                "Identitas pengetahuan pada catatan ke-${index + 1} tidak cocok."
            }
            require(item.optString("contentHash") == fingerprint(question, correction)) {
                "Sidik jari isi pada catatan ke-${index + 1} tidak cocok."
            }
            require(createdAt > 0L && createdAt <= exportedAt + 86_400_000L) {
                "Waktu pembuatan catatan ke-${index + 1} tidak valid."
            }
            val record = TransferLearningRecord(question, correction, source, createdAt)
            require(seen.add(fingerprint(record))) {
                "File berisi catatan duplikat pada urutan ke-${index + 1}."
            }
            result += record
        }
        return result
    }

    fun preview(
        incoming: List<TransferLearningRecord>,
        existing: List<LearningRecord>
    ): LearningImportPreview {
        val existingFingerprints = existing.map { fingerprint(it.question, it.correction) }.toHashSet()
        val seenQuestions = existing.map { questionIdentity(it.question) }.toHashSet()
        val seenIncoming = HashSet<String>()
        val newRecords = mutableListOf<TransferLearningRecord>()
        val conflicts = mutableListOf<TransferLearningRecord>()
        var duplicates = 0
        incoming.forEach { record ->
            val fingerprint = fingerprint(record)
            if (fingerprint in existingFingerprints || !seenIncoming.add(fingerprint)) {
                duplicates++
            } else if (!seenQuestions.add(questionIdentity(record.question))) {
                conflicts += record
            } else {
                newRecords += record
            }
        }
        return LearningImportPreview(newRecords, duplicates, conflicts)
    }

    fun fingerprint(record: TransferLearningRecord): String =
        fingerprint(record.question, record.correction)

    fun fingerprint(question: String, correction: String): String =
        sha256(normalize(question) + "\u0000" + normalize(correction))

    fun questionIdentity(question: String): String = sha256(normalize(question))

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("\\s+"), " ").trim()
}
