package com.harmanibike.voiceo3

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class TransferLearningRecord(
    val question: String,
    val correction: String,
    val source: String = ""
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
    const val SCHEMA_VERSION = 1
    const val MAX_RECORDS = 5000
    const val MAX_FILE_BYTES = 5 * 1024 * 1024

    fun encode(records: List<LearningRecord>, exportedAt: Long = System.currentTimeMillis()): String {
        require(records.size <= MAX_RECORDS) { "Maksimal $MAX_RECORDS catatan per file." }
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("question", record.question)
                    .put("correction", record.correction)
                    .put("source", record.source)
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("exportedAt", exportedAt)
            .put("records", array)
            .toString(2)
    }

    fun decode(json: String): List<TransferLearningRecord> {
        val root = JSONObject(json)
        require(root.optString("format") == FORMAT) { "Ini bukan file Learning Lab Tata AI." }
        require(root.optInt("schemaVersion", -1) == SCHEMA_VERSION) { "Versi format file tidak didukung." }
        val array = root.optJSONArray("records") ?: throw IllegalArgumentException("Daftar catatan tidak ditemukan.")
        require(array.length() <= MAX_RECORDS) { "Jumlah catatan melebihi batas $MAX_RECORDS." }
        val result = mutableListOf<TransferLearningRecord>()
        val seen = HashSet<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
                ?: throw IllegalArgumentException("Catatan ke-${index + 1} tidak valid.")
            val question = item.optString("question").trim()
            val correction = item.optString("correction").trim()
            val source = item.optString("source").trim()
            require(question.isNotBlank() && correction.isNotBlank()) {
                "Konteks dan koreksi pada catatan ke-${index + 1} wajib diisi."
            }
            require(question.length <= 2000 && correction.length <= 6000 && source.length <= 1000) {
                "Catatan ke-${index + 1} melebihi batas panjang."
            }
            val record = TransferLearningRecord(question, correction, source)
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
        val existingQuestions = existing.map { normalize(it.question) }.toHashSet()
        val seenIncoming = HashSet<String>()
        val newRecords = mutableListOf<TransferLearningRecord>()
        val conflicts = mutableListOf<TransferLearningRecord>()
        var duplicates = 0
        incoming.forEach { record ->
            val fingerprint = fingerprint(record)
            if (fingerprint in existingFingerprints || !seenIncoming.add(fingerprint)) {
                duplicates++
            } else if (normalize(record.question) in existingQuestions) {
                conflicts += record
            } else {
                newRecords += record
            }
        }
        return LearningImportPreview(newRecords, duplicates, conflicts)
    }

    fun fingerprint(record: TransferLearningRecord): String =
        fingerprint(record.question, record.correction)

    fun fingerprint(question: String, correction: String): String {
        val canonical = normalize(question) + "\u0000" + normalize(correction)
        val bytes = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("\\s+"), " ").trim()
}
