package com.harmanibike.voiceo3

import java.util.Locale

/**
 * A lightweight, offline response composer.
 *
 * It generates structured conversational text from detected intent and user-provided
 * slots. It does not contain a pretrained language model and must not invent facts.
 * No network, database, or persistent memory is used.
 */
class TataGenerativeEngine {
    // Short-lived session state only. Never written to SQLite or exported.
    private var lastIntent: String? = null
    private var lastSubject: String? = null

    fun respond(rawText: String): String? {
        val text = normalize(rawText)
        if (text.isBlank()) return null

        if (isFollowUp(text)) return composeFollowUp()

        val result = when {
            isComparisonRequest(text) -> {
                lastIntent = "comparison"
                lastSubject = extractSubject(rawText, listOf("bandingkan", "apa perbedaan", "bedanya", "mana lebih baik"))
                composeComparison(rawText)
            }
            isPlanningRequest(text) -> {
                lastIntent = "plan"
                lastSubject = extractSubject(rawText, listOf(
                    "tolong buatkan rencana untuk", "buatkan rencana untuk", "buat rencana untuk",
                    "susun rencana untuk", "bagaimana cara memulai", "bagaimana memulai",
                    "saya ingin belajar", "aku ingin belajar", "saya mau belajar", "aku mau belajar",
                    "saya ingin membuat", "aku ingin membuat", "saya ingin mengembangkan",
                    "aku ingin mengembangkan", "rencana saya adalah", "target saya adalah"
                ))
                composePlan(rawText)
            }
            isDecisionRequest(text) -> {
                lastIntent = "decision"
                lastSubject = extractSubject(rawText, listOf(
                    "menurutmu", "menurut kamu", "sebaiknya saya", "sebaiknya aku", "saya harus", "aku harus"
                ))
                composeDecision(rawText)
            }
            isBrainstormRequest(text) -> {
                lastIntent = "ideas"
                lastSubject = extractSubject(rawText, listOf("beri ide untuk", "berikan ide untuk", "carikan ide untuk", "ide untuk"))
                composeIdeas(rawText)
            }
            else -> null
        }
        return result
    }

    fun clearSession() {
        lastIntent = null
        lastSubject = null
    }

    private fun isFollowUp(text: String): Boolean =
        text in setOf("lanjut", "lanjutkan", "terus", "teruskan", "contohnya", "contoh lain", "jelaskan lagi", "lebih rinci", "lebih detail", "perjelas")

    private fun composeFollowUp(): String? {
        val subject = lastSubject?.takeIf { it.isNotBlank() } ?: return null
        return when (lastIntent) {
            "plan" -> "Untuk $subject, kita mulai dari tindakan yang paling kecil. Pilih satu hasil yang bisa diperiksa, kerjakan dalam sesi singkat, catat kendala, lalu tentukan langkah berikutnya. Jika Anda beri tahu waktu dan sumber daya yang tersedia, saya bisa membuat jadwal yang lebih konkret."
            "comparison" -> "Mari kita isi perbandingan $subject satu per satu. Kita bisa membuat kolom untuk fungsi, kelebihan, kekurangan, biaya, risiko, dan kebutuhan Anda. Saya perlu informasi atau sumber untuk setiap pilihan agar tidak mengarang perbedaannya."
            "decision" -> "Untuk keputusan tentang $subject, tuliskan pilihan yang tersedia dan konsekuensi terpenting dari masing-masing. Kita kemudian bisa menilai mana yang paling sesuai dengan tujuan dan batasan Anda."
            "ideas" -> "Untuk mengembangkan ide tentang $subject, pilih satu gagasan yang paling mudah diuji. Buat versi sederhana, minta masukan dari calon pengguna, lalu perbaiki berdasarkan hasil uji."
            else -> null
        }
    }

    private fun composePlan(rawText: String): String {
        val goal = extractSubject(
            rawText,
            listOf(
                "tolong buatkan rencana untuk",
                "buatkan rencana untuk",
                "buat rencana untuk",
                "susun rencana untuk",
                "bagaimana cara memulai",
                "bagaimana memulai",
                "saya ingin belajar",
                "aku ingin belajar",
                "saya mau belajar",
                "aku mau belajar",
                "saya ingin membuat",
                "aku ingin membuat",
                "saya ingin mengembangkan",
                "aku ingin mengembangkan",
                "rencana saya adalah",
                "target saya adalah"
            )
        ).ifBlank { "tujuan tersebut" }

        return buildString {
            append("Baik, kita susun langkah awal untuk: ")
            append(goal)
            append(".\n\n")
            append("1. Tentukan hasil yang ingin dicapai dan batas waktunya.\n")
            append("2. Pecah tujuan menjadi beberapa tugas kecil yang bisa dilakukan.\n")
            append("3. Mulai dari tugas paling penting dengan sumber daya yang tersedia.\n")
            append("4. Periksa hasilnya secara berkala, lalu sesuaikan langkah berikutnya.\n\n")
            append("Supaya rencananya tidak terlalu umum, apa target pertama yang ingin Anda capai?")
        }
    }

    private fun composeComparison(rawText: String): String {
        val subject = extractSubject(
            rawText,
            listOf("bandingkan", "apa perbedaan", "bedanya", "mana lebih baik")
        ).ifBlank { "dua pilihan tersebut" }

        return "Untuk membandingkan $subject secara adil, kita perlu melihat beberapa hal: " +
            "fungsi dan tujuan, kelebihan, kekurangan, biaya atau sumber daya, serta risiko. " +
            "Saya tidak akan menebak fakta yang belum tersedia. Sebutkan dua pilihan yang ingin " +
            "dibandingkan dan hal yang paling penting bagi Anda."
    }

    private fun composeIdeas(rawText: String): String {
        val subject = extractSubject(rawText, listOf("beri ide untuk", "berikan ide untuk", "carikan ide untuk", "ide untuk"))
            .ifBlank { "topik tersebut" }
        return "Untuk $subject, berikut beberapa arah ide yang bisa dikembangkan: " +
            "1) selesaikan masalah kecil yang sering muncul, " +
            "2) sederhanakan proses yang sudah ada, " +
            "3) gabungkan dua kebutuhan yang saling berkaitan, " +
            "4) buat versi percobaan dengan biaya rendah, dan " +
            "5) uji gagasan bersama orang yang akan menggunakannya. " +
            "Ide mana yang ingin kita kembangkan lebih dahulu?"
    }

    private fun isBrainstormRequest(text: String): Boolean = containsAny(
        text, "beri ide untuk", "berikan ide untuk", "carikan ide untuk", "ide untuk"
    )

    private fun composeDecision(rawText: String): String {
        val subject = extractSubject(
            rawText,
            listOf("menurutmu", "menurut kamu", "sebaiknya saya", "sebaiknya aku", "saya harus", "aku harus")
        ).ifBlank { "pilihan yang sedang Anda pertimbangkan" }

        return "Kita bisa menimbang $subject dengan lebih terarah. " +
            "Pertama, tentukan hasil yang paling Anda inginkan. Kedua, bandingkan manfaat dan " +
            "risiko setiap pilihan. Ketiga, periksa waktu, biaya, dan kemampuan yang tersedia. " +
            "Saya belum punya cukup konteks untuk memilihkan satu jawaban. Apa saja pilihan " +
            "yang sedang Anda hadapi?"
    }

    private fun isPlanningRequest(text: String): Boolean = containsAny(
        text,
        "buatkan rencana", "buat rencana", "susun rencana",
        "bagaimana cara memulai", "bagaimana memulai",
        "saya ingin belajar", "aku ingin belajar", "saya mau belajar", "aku mau belajar",
        "saya ingin membuat", "aku ingin membuat",
        "saya ingin mengembangkan", "aku ingin mengembangkan",
        "rencana saya adalah", "target saya adalah"
    )

    private fun isComparisonRequest(text: String): Boolean = containsAny(
        text, "bandingkan", "apa perbedaan", "bedanya", "mana lebih baik"
    )

    private fun isDecisionRequest(text: String): Boolean = containsAny(
        text, "menurutmu saya harus", "menurut kamu saya harus",
        "sebaiknya saya", "sebaiknya aku", "saya harus memilih", "aku harus memilih"
    )

    private fun extractSubject(rawText: String, prefixes: List<String>): String {
        val normalized = normalize(rawText)
        val prefix = prefixes.firstOrNull { normalized.startsWith(it) } ?: return ""
        val original = rawText.trim()
        val index = original.lowercase(Locale("id", "ID")).indexOf(prefix)
        if (index < 0) return ""
        return original.substring(index + prefix.length)
            .trim()
            .trimStart(':', '-', ',', ' ')
            .trimEnd('?', '.', '!', ' ')
            .take(120)
    }

    private fun containsAny(text: String, vararg phrases: String): Boolean =
        phrases.any { text.contains(normalize(it)) }

    private fun normalize(value: String): String =
        value.lowercase(Locale("id", "ID"))
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
