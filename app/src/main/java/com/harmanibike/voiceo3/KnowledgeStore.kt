package com.harmanibike.voiceo3

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

data class KnowledgeEntry(
    val question: String,
    val keywords: List<String>,
    val answer: String
)

data class NewsSource(
    val id: Long,
    val name: String,
    val feedUrl: String,
    val enabled: Boolean
)

/**
 * Penyimpanan lokal baru Tata AI.
 * Database dibuat bersih pada instalasi baru; data contoh/JSON lama tidak dimigrasikan.
 */
class KnowledgeStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    init {
        // Reset penyimpanan lama sesuai permintaan: tidak mengimpor data knowledge.json.
        context.applicationContext.deleteFile("knowledge.json")
        writableDatabase
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE knowledge (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                question TEXT NOT NULL,
                keywords TEXT NOT NULL DEFAULT '',
                answer TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                role TEXT NOT NULL,
                message TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX idx_knowledge_question ON knowledge(question)")
        db.execSQL("CREATE INDEX idx_chat_created ON chat_messages(created_at)")
        db.execSQL(
            """CREATE TABLE app_settings (
                setting_key TEXT PRIMARY KEY,
                setting_value TEXT NOT NULL
            )""".trimIndent()
        )
        createDynamicCapabilityTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migrasi bersifat aditif: seluruh pengetahuan dan riwayat lama tetap dipertahankan.
        if (oldVersion < 2) createDynamicCapabilityTables(db)
    }

    private fun createDynamicCapabilityTables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS assistant_capabilities (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                capability_key TEXT NOT NULL UNIQUE,
                display_name TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                version INTEGER NOT NULL DEFAULT 1,
                enabled INTEGER NOT NULL DEFAULT 1,
                config_json TEXT NOT NULL DEFAULT '{}',
                updated_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS news_sources (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                feed_url TEXT NOT NULL UNIQUE,
                enabled INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL(
            "INSERT OR IGNORE INTO assistant_capabilities " +
                "(capability_key, display_name, description, version, enabled, config_json, updated_at) " +
                "VALUES ('news_reader', 'Pembaca berita', 'Mengambil dan membacakan berita dari sumber RSS yang diaktifkan.', 1, 1, '{}', ?)",
            arrayOf(System.currentTimeMillis())
        )
        db.execSQL(
            "INSERT OR IGNORE INTO news_sources (name, feed_url, enabled, created_at) VALUES (?, ?, 1, ?)",
            arrayOf("ANTARA", "https://www.antaranews.com/rss/terkini.xml", System.currentTimeMillis())
        )
        db.execSQL(
            "INSERT OR IGNORE INTO news_sources (name, feed_url, enabled, created_at) VALUES (?, ?, 1, ?)",
            arrayOf("CNA Indonesia", "https://www.cna.id/api/v1/rss-outbound-feed?_format=xml", System.currentTimeMillis())
        )
    }

    @Synchronized
    fun getAll(): List<KnowledgeEntry> {
        val result = mutableListOf<KnowledgeEntry>()
        readableDatabase.rawQuery(
            "SELECT question, keywords, answer FROM knowledge ORDER BY id ASC", null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += KnowledgeEntry(
                    cursor.getString(0),
                    cursor.getString(1).split("|").filter { it.isNotBlank() },
                    cursor.getString(2)
                )
            }
        }
        return result
    }

    @Synchronized
    fun search(query: String): String? {
        val normalized = normalize(query)
        if (normalized.isBlank()) return null
        var bestScore = 0
        var bestAnswer: String? = null
        getAll().forEach { entry ->
            val terms = listOf(entry.question) + entry.keywords
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
                bestAnswer = entry.answer
            }
        }
        return bestAnswer
    }

    @Synchronized
    fun add(question: String, keywords: List<String>, answer: String) {
        val values = ContentValues().apply {
            put("question", question.trim())
            put("keywords", keywords.joinToString("|") { it.trim() }.trim('|'))
            put("answer", answer.trim())
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insertOrThrow("knowledge", null, values)
    }

    @Synchronized
    fun update(index: Int, question: String, keywords: List<String>, answer: String) {
        val id = getIdAt(index) ?: return
        val values = ContentValues().apply {
            put("question", question.trim())
            put("keywords", keywords.joinToString("|") { it.trim() }.trim('|'))
            put("answer", answer.trim())
        }
        writableDatabase.update("knowledge", values, "id=?", arrayOf(id.toString()))
    }

    @Synchronized
    fun delete(index: Int) {
        val id = getIdAt(index) ?: return
        writableDatabase.delete("knowledge", "id=?", arrayOf(id.toString()))
    }

    @Synchronized
    fun getSetting(key: String): String? {
        readableDatabase.rawQuery(
            "SELECT setting_value FROM app_settings WHERE setting_key=?", arrayOf(key)
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    @Synchronized
    fun setSetting(key: String, value: String) {
        val values = ContentValues().apply {
            put("setting_key", key)
            put("setting_value", value)
        }
        writableDatabase.insertWithOnConflict(
            "app_settings", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    @Synchronized
    fun getKnowledgeCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM knowledge", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /** Menghapus hanya pengetahuan yang dipelajari; riwayat chat dan pengaturan tidak disentuh. */
    @Synchronized
    fun clearAllLearnedKnowledge(): Int {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val count = getKnowledgeCount()
            db.delete("knowledge", null, null)
            db.setTransactionSuccessful()
            return count
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun getAllNewsSources(): List<NewsSource> {
        val sources = mutableListOf<NewsSource>()
        readableDatabase.rawQuery(
            "SELECT id, name, feed_url, enabled FROM news_sources ORDER BY id ASC", null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                sources += NewsSource(
                    cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getInt(3) == 1
                )
            }
        }
        return sources
    }

    @Synchronized
    fun addNewsSource(name: String, feedUrl: String) {
        val normalizedUrl = feedUrl.trim()
        require(normalizedUrl.startsWith("https://") || normalizedUrl.startsWith("http://")) {
            "Alamat sumber harus menggunakan HTTP atau HTTPS."
        }
        val values = ContentValues().apply {
            put("name", name.trim())
            put("feed_url", normalizedUrl)
            put("enabled", 1)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insertOrThrow("news_sources", null, values)
    }

    @Synchronized
    fun setNewsSourceEnabled(id: Long, enabled: Boolean) {
        val values = ContentValues().apply { put("enabled", if (enabled) 1 else 0) }
        writableDatabase.update("news_sources", values, "id=?", arrayOf(id.toString()))
    }

    @Synchronized
    fun deleteNewsSource(id: Long) {
        writableDatabase.delete("news_sources", "id=?", arrayOf(id.toString()))
    }

    @Synchronized
    fun getEnabledNewsSources(): List<Pair<String, String>> {
        val sources = mutableListOf<Pair<String, String>>()
        readableDatabase.rawQuery(
            "SELECT name, feed_url FROM news_sources WHERE enabled=1 ORDER BY id ASC", null
        ).use { cursor ->
            while (cursor.moveToNext()) sources += cursor.getString(0) to cursor.getString(1)
        }
        return sources
    }

    @Synchronized
    fun saveChat(role: String, message: String) {
        val values = ContentValues().apply {
            put("role", role.take(24))
            put("message", message)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("chat_messages", null, values)
        writableDatabase.execSQL(
            "DELETE FROM chat_messages WHERE id NOT IN (SELECT id FROM chat_messages ORDER BY id DESC LIMIT 300)"
        )
    }

    @Synchronized
    fun getRecentChat(limit: Int = 60): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        readableDatabase.rawQuery(
            "SELECT role, message FROM chat_messages ORDER BY id DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 300).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) rows += cursor.getString(0) to cursor.getString(1)
        }
        return rows.asReversed()
    }

    @Synchronized
    fun clearChat() {
        writableDatabase.delete("chat_messages", null, null)
    }

    @Synchronized
    fun exportJson(): String {
        val array = JSONArray()
        getAll().forEach { array.put(toJson(it.question, it.keywords, it.answer)) }
        return array.toString(2)
    }

    @Synchronized
    fun importJson(json: String): Int {
        val source = JSONArray(json)
        val validated = mutableListOf<KnowledgeEntry>()
        for (i in 0 until source.length()) {
            val item = source.optJSONObject(i)
                ?: throw IllegalArgumentException("Format data ke-${i + 1} tidak valid.")
            val question = item.optString("question").trim()
            val answer = item.optString("answer").trim()
            require(question.isNotBlank() && answer.isNotBlank()) {
                "Pertanyaan dan jawaban pada data ke-${i + 1} wajib diisi."
            }
            val raw = item.optJSONArray("keywords") ?: JSONArray()
            val keywords = (0 until raw.length()).map { raw.optString(it).trim() }.filter { it.isNotBlank() }
            validated += KnowledgeEntry(question, keywords, answer)
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("knowledge", null, null)
            validated.forEach { entry ->
                val values = ContentValues().apply {
                    put("question", entry.question)
                    put("keywords", entry.keywords.joinToString("|"))
                    put("answer", entry.answer)
                    put("created_at", System.currentTimeMillis())
                }
                db.insertOrThrow("knowledge", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return validated.size
    }

    private fun getIdAt(index: Int): Long? {
        if (index < 0) return null
        readableDatabase.rawQuery(
            "SELECT id FROM knowledge ORDER BY id ASC LIMIT 1 OFFSET ?", arrayOf(index.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    private fun toJson(question: String, keywords: List<String>, answer: String) =
        JSONObject().put("question", question).put("keywords", JSONArray(keywords)).put("answer", answer)

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    companion object {
        private const val DATABASE_NAME = "tata_ai.db"
        private const val DATABASE_VERSION = 2
    }
}
