package com.harmanibike.voiceo3

/**
 * Jawaban identitas yang singkat dan dipilih berdasarkan maksud pertanyaan.
 * Tidak membaca database kemampuan agar jawaban dasar tidak melebar.
 */
object TataIdentity {
    private fun normalize(command: String): String =
        command.lowercase()
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun isIdentityQuestion(command: String): Boolean {
        val text = normalize(command)
        val phrases = listOf(
            "siapa tata", "siapa tata ai", "tata ai itu siapa", "siapa kamu",
            "siapa nama kamu", "siapa namamu", "nama kamu", "identitas kamu",
            "tentang dirimu", "informasi tentang dirimu", "ceritakan tentang dirimu",
            "perkenalkan dirimu", "apa itu tata ai", "apa arti ai", "apa itu ai",
            "jelaskan ai", "versi berapa", "versi tata", "kamu versi apa",
            "apa kemampuanmu", "kemampuan kamu", "kamu bisa apa", "fitur apa yang kamu punya"
        )
        val padded = " $text "
        return phrases.any { padded.contains(" $it ") }
    }

    fun answer(command: String, versionName: String = ""): String {
        val text = normalize(command)
        return when {
            text.contains("versi") ->
                "Saya Tata AI, asisten digital untuk Android. Versi aplikasi saat ini " +
                    versionName.ifBlank { "belum diketahui" } + "."
            text.contains("kemampuan") || text.contains("bisa apa") || text.contains("fitur apa") ->
                "Saya Tata AI. Saya dapat membantu dengan pengetahuan lokal, perhitungan sederhana, pencarian web saat online, dan model AI lokal jika sudah dipasang serta diaktifkan."
            text.contains("arti ai") || text == "apa itu ai" || text == "jelaskan ai" ->
                "AI adalah singkatan dari Artificial Intelligence, yaitu kecerdasan buatan."
            else ->
                "Saya Tata AI, asisten digital Android yang sedang dikembangkan untuk membantu Anda."
        }
    }
}
