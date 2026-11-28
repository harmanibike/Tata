package com.harmanibike.voiceo3

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mesin percakapan lokal berbasis aturan.
 * Tidak mengirim teks pengguna ke internet dan tidak menyimpan pengetahuan otomatis.
 * Ini bukan model bahasa generatif; jawaban dibatasi pada pola yang dikenali.
 */
class OfflineConversationEngine {
    fun respond(rawText: String): String? {
        val text = normalize(rawText)
        if (text.isBlank()) return null

        return when {
            containsAny(text, "apa kabar", "bagaimana kabarmu", "kabar kamu") ->
                "Saya baik dan siap membantu. Kalau kamu sendiri bagaimana kabarnya?"
            containsAny(text, "terima kasih", "makasih", "thanks") ->
                "Sama-sama. Senang bisa membantu. Ada hal lain yang ingin kamu bahas?"
            containsAny(text, "aku sedih", "saya sedih", "lagi sedih", "merasa sedih") ->
                "Aku ikut prihatin kamu sedang merasa sedih. Kalau kamu mau, ceritakan apa yang terjadi. Aku akan mendengarkan."
            containsAny(text, "aku senang", "saya senang", "lagi bahagia", "merasa bahagia") ->
                "Wah, ikut senang mendengarnya. Apa yang membuat harimu terasa menyenangkan?"
            containsAny(text, "aku bosan", "saya bosan", "lagi bosan") ->
                "Kita bisa ngobrol, bermain tebak-tebakan sederhana, atau membahas hal yang kamu sukai. Mau mulai dari topik apa?"
            containsAny(text, "cerita lucu", "lelucon", "buat lelucon", "joke") ->
                "Kenapa komputer suka kedinginan? Karena terlalu banyak membuka Windows. Hehe."
            containsAny(text, "motivasi", "semangati aku", "beri semangat", "butuh semangat") ->
                "Pelan-pelan tidak apa-apa. Yang penting tetap bergerak. Satu langkah kecil hari ini tetap lebih baik daripada tidak mencoba sama sekali."
            containsAny(text, "siapa penciptamu", "siapa yang membuatmu", "siapa pembuatmu") ->
                "Saya adalah Tata AI, aplikasi asisten yang sedang dikembangkan. Saya berjalan dengan fitur lokal di perangkat ini."
            containsAny(text, "apakah kamu offline", "bisa tanpa internet", "tanpa internet") ->
                "Bisa. Perintah dasar, pengetahuan yang tersimpan, dan percakapan dengan pola lokal dapat berjalan tanpa internet. Membaca berita terbaru tetap membutuhkan koneksi."
            containsAny(text, "apa yang kamu ingat", "apa yang kamu pelajari", "pengetahuanmu") ->
                "Saya dapat mencari pengetahuan yang sudah disimpan dengan izin pengguna. Saya tidak menyimpan percakapan baru secara otomatis."
            containsAny(text, "jam berapa", "sekarang jam") ->
                "Sekarang pukul " + SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date()) + "."
            containsAny(text, "hari apa", "tanggal berapa", "tanggal hari ini") ->
                "Hari ini " + SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(Date()) + "."
            containsAny(text, "ayo ngobrol", "temani aku", "ngobrol yuk") ->
                "Tentu. Kita bisa ngobrol. Kamu ingin membahas kegiatan hari ini, teknologi, pengetahuan umum, atau hal lain?"
            else -> null
        }
    }

    private fun containsAny(text: String, vararg phrases: String): Boolean =
        phrases.any { text.contains(normalize(it)) }

    private fun normalize(value: String): String =
        value.lowercase(Locale("id", "ID"))
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
