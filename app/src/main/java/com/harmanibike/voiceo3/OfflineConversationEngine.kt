package com.harmanibike.voiceo3

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mesin percakapan lokal berbasis aturan.
 * Variasi dan konteks sesi disimpan di memori aplikasi saja.
 * Tidak mengirim teks pengguna ke internet dan tidak menyimpan pengetahuan otomatis.
 * Ini belum model bahasa generatif; jawaban dibatasi pada pola yang dikenali.
 */
class OfflineConversationEngine {
    private val responseIndexes = mutableMapOf<String, Int>()
    private var lastTopic: String? = null

    fun respond(rawText: String): String? {
        val text = normalize(rawText)
        if (text.isBlank()) return null

        if (containsAny(text, "lanjut", "terus", "ceritakan lebih lanjut", "jelaskan lebih lanjut", "lalu bagaimana", "terus bagaimana")) {
            return if (lastTopic == null) null else followUp()
        }

        return when {
            containsAny(text, "apa kabar", "bagaimana kabarmu", "kabar kamu") -> reply("greeting",
                "Saya baik, terima kasih sudah bertanya. Kamu sendiri bagaimana hari ini?",
                "Baik! Saya siap menemani obrolanmu. Bagaimana keadaanmu?",
                "Aku baik dan siap membantu. Semoga harimu juga menyenangkan."
            )
            containsAny(text, "terima kasih", "makasih", "thanks") -> reply("thanks",
                "Sama-sama. Senang kalau bisa membantu.",
                "Dengan senang hati! Kalau ada yang ingin dibahas lagi, kita lanjut saja.",
                "Sama-sama, ya. Semoga penjelasannya bermanfaat."
            )
            containsAny(text, "aku sedih", "saya sedih", "lagi sedih", "merasa sedih", "sedang sedih", "hatiku sedih", "hati saya sedih", "aku menangis", "saya menangis") -> reply("sad",
                "Aku ikut prihatin mendengarnya. Kalau kamu nyaman, ceritakan apa yang sedang terjadi. Aku akan mendengarkan tanpa menghakimi.",
                "Kedengarannya hari ini cukup berat. Tidak perlu buru-buru, kamu boleh cerita pelan-pelan.",
                "Maaf kamu sedang melalui hal yang tidak menyenangkan. Kita bisa membicarakannya satu per satu."
            )
            containsAny(text, "aku senang", "saya senang", "lagi bahagia", "merasa bahagia", "aku bahagia", "aku gembira", "saya gembira", "aku berhasil", "saya berhasil") -> reply("happy",
                "Wah, ikut senang mendengarnya! Apa hal paling menyenangkan yang terjadi hari ini?",
                "Asyik! Itu kabar baik. Cerita sedikit, yuk, apa yang membuatmu bahagia?",
                "Ikut merayakan kabar baikmu. Semoga suasana menyenangkan ini bertahan sepanjang hari."
            )
            containsAny(text, "aku marah sama kamu", "saya marah sama kamu", "aku marah padamu", "jawabanmu membuatku marah", "kamu membuatku marah", "aku kesal dengan kamu") -> reply("angry_at_tata",
                "Maaf kalau jawabanku membuatmu kesal. Aku tidak ingin memperburuk keadaan. Bisa tunjukkan bagian yang keliru atau tidak sesuai harapan? Aku akan mencoba memperbaikinya.",
                "Aku dengar kamu kecewa dengan jawabanku. Terima kasih sudah memberi tahu. Mari kita periksa lagi bagian yang membuatmu marah.",
                "Maaf, sepertinya aku belum membantu dengan baik. Ceritakan apa yang perlu kuperbaiki; aku akan menanggapinya dengan tenang."
            )
            containsAny(text, "aku marah", "saya marah", "lagi marah", "sedang marah", "aku kesal", "saya kesal", "jengkel", "naik darah", "emosi sekali", "aku emosi") -> reply("angry",
                "Aku menangkap kamu sedang marah. Perasaan itu bisa muncul saat sesuatu terasa tidak adil atau melelahkan. Kalau kamu bersedia, apa yang paling membuatmu kesal?",
                "Sepertinya ada hal yang benar-benar mengganggumu. Kita pelan-pelan saja. Mau cerita dulu, atau mengambil jeda sebentar untuk menenangkan diri?",
                "Aku akan mendengarkan tanpa membalas dengan kemarahan. Coba ceritakan apa yang terjadi; kita cari langkah yang aman dan masuk akal."
            )
            containsAny(text, "aku takut", "saya takut", "merasa takut", "cemas", "khawatir", "gelisah", "panik") -> reply("anxious",
                "Kedengarannya kamu sedang merasa takut atau cemas. Coba tarik napas perlahan jika nyaman, lalu ceritakan apa yang membuatmu khawatir.",
                "Rasa khawatir bisa terasa berat. Kita urai satu hal dulu: apa yang paling kamu takutkan saat ini?",
                "Aku bisa menemanimu menyusun langkah kecil. Apakah ada sesuatu yang sedang terasa mendesak atau membuatmu tidak aman?"
            )
            containsAny(text, "aku kecewa", "saya kecewa", "merasa kecewa", "gagal lagi", "putus asa") -> reply("disappointed",
                "Aku turut prihatin. Kecewa setelah berharap banyak itu tidak mudah. Mau cerita apa yang tidak berjalan sesuai harapan?",
                "Kita tidak perlu langsung mencari solusi. Aku bisa mendengarkan dulu, lalu kalau kamu siap kita pikirkan langkah berikutnya.",
                "Satu hasil yang buruk tidak menentukan seluruh kemampuanmu. Apa yang terjadi?"
            )
            containsAny(text, "aku kesepian", "saya kesepian", "merasa sendiri", "tidak punya teman") -> reply("lonely",
                "Merasa sendirian bisa berat. Aku bisa menemanimu berbicara di sini. Apakah ada orang tepercaya yang juga bisa kamu hubungi?",
                "Terima kasih sudah cerita. Mau ngobrol tentang apa yang sedang kamu rasakan? Kamu tidak harus memendam semuanya sendiri.",
                "Aku siap mendengarkan. Jika memungkinkan, menghubungi teman atau keluarga yang kamu percaya juga bisa membantu."
            )
            containsAny(text, "aku bosan", "saya bosan", "lagi bosan") -> reply("bored",
                "Kita cari kegiatan yang seru, yuk. Bisa tebak-tebakan, cerita, atau bahas teknologi.",
                "Kalau begitu, kita ubah suasana. Mau dengar fakta unik atau bermain teka-teki?",
                "Aku bisa menemanimu. Kita bisa ngobrol santai atau membahas sesuatu yang baru."
            )
            containsAny(text, "cerita lucu", "lelucon", "buat lelucon", "joke") -> reply("joke",
                "Kenapa komputer suka kedinginan? Karena terlalu banyak membuka Windows. Hehe.",
                "Ada dua Wi-Fi sedang ngobrol. Satu bilang, 'Kamu kok lemah?' Yang lain menjawab, 'Aku sedang mencari koneksi.'",
                "Kenapa kalender selalu tenang? Karena hari-harinya sudah terjadwal. Hehe."
            )
            containsAny(text, "motivasi", "semangati aku", "beri semangat", "butuh semangat") -> reply("motivation",
                "Pelan-pelan tidak apa-apa. Satu langkah kecil tetap membawa kamu lebih dekat ke tujuan.",
                "Tidak harus langsung sempurna. Terus belajar, evaluasi, lalu coba lagi. Prosesmu tetap berarti.",
                "Hari yang berat bukan berarti perjalananmu gagal. Istirahat sebentar boleh, menyerah bukan satu-satunya pilihan."
            )
            containsAny(text, "siapa penciptamu", "siapa yang membuatmu", "siapa pembuatmu") ->
                "Saya Tata AI, aplikasi asisten yang sedang dikembangkan. Fitur percakapan lokal saya terus ditingkatkan."
            containsAny(text, "apakah kamu offline", "bisa tanpa internet", "tanpa internet") ->
                "Bisa. Perintah dasar, pengetahuan tersimpan, dan percakapan berpola lokal dapat berjalan tanpa internet. Berita terbaru memerlukan koneksi."
            containsAny(text, "apa yang kamu ingat", "apa yang kamu pelajari", "pengetahuanmu") ->
                "Saya bisa mencari pengetahuan yang sudah disimpan dengan izinmu. Saya tidak menyimpan percakapan baru sebagai pengetahuan secara otomatis."
            containsAny(text, "jam berapa", "sekarang jam") ->
                "Sekarang pukul " + SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date()) + "."
            containsAny(text, "hari apa", "tanggal berapa", "tanggal hari ini") ->
                "Hari ini " + SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(Date()) + "."
            containsAny(text, "ayo ngobrol", "temani aku", "ngobrol yuk") -> reply("chat",
                "Tentu. Kita bisa ngobrol santai. Ada hal menarik yang terjadi hari ini?",
                "Siap, aku temani. Kita mulai dari cerita harimu atau topik yang sedang kamu pikirkan.",
                "Boleh! Aku siap mendengarkan. Mau ngobrol ringan atau membahas sesuatu yang lebih serius?"
            )
            else -> null
        }
    }

    fun unknownResponse(): String = reply("unknown",
        "Aku belum menangkap maksudmu. Bisa ceritakan dengan cara lain?",
        "Sepertinya aku belum memahami kalimat itu. Coba beri sedikit konteks, ya.",
        "Aku ingin membantu, tetapi maksudnya belum jelas bagiku. Kita coba susun pertanyaannya bersama.",
        "Bagian itu belum ada dalam kemampuan lokal yang kukenali. Kamu bisa bertanya dengan kalimat berbeda atau mengajariku melalui menu Pengetahuan."
    )

    private fun reply(topic: String, vararg options: String): String {
        lastTopic = topic
        val index = responseIndexes[topic] ?: 0
        responseIndexes[topic] = (index + 1) % options.size
        return options[index % options.size]
    }

    private fun followUp(): String {
        return when (lastTopic) {
            "sad" -> "Aku masih mendengarkan. Bagian mana yang paling mengganggu pikiranmu saat ini?"
            "happy" -> "Apa ada hal lain dari kejadian itu yang membuatmu merasa senang?"
            "angry_at_tata" -> "Bagian mana dari jawabanku yang paling perlu kuperbaiki?"
            "angry" -> "Aku masih mendengarkan. Apa yang kamu butuhkan sekarang: didengarkan, mencari solusi, atau mengambil jeda?"
            "anxious" -> "Mari fokus pada satu hal yang bisa kamu kendalikan sekarang. Apa langkah kecil yang terasa mungkin dilakukan?"
            "disappointed" -> "Apa bagian yang paling membuatmu kecewa? Kita bisa memisahkan fakta, harapan, dan pilihan berikutnya."
            "lonely" -> "Apakah ada seseorang yang membuatmu merasa aman untuk dihubungi hari ini?"
            "bored" -> "Kita pilih satu kegiatan dulu. Aku bisa mulai dengan teka-teki sederhana."
            "motivation" -> "Coba tentukan satu hal kecil yang realistis untuk kamu selesaikan hari ini."
            "joke" -> "Mau satu lagi? Aku punya beberapa tebak-tebakan ringan."
            "chat" -> "Ceritakan saja dari bagian yang paling ingin kamu mulai. Aku akan mengikuti alur ceritamu."
            "greeting" -> "Aku ingin tahu juga, bagaimana harimu sejauh ini?"
            "thanks" -> "Sama-sama. Kita bisa lanjut membahas hal lain kapan saja."
            else -> "Boleh. Bagian mana yang ingin kamu dalami? Beri sedikit petunjuk agar aku tidak salah menangkap maksudmu."
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
