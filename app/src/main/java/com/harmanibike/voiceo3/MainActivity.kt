package com.harmanibike.voiceo3

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var router: CommandRouter
    private lateinit var knowledgeStore: KnowledgeStore
    private lateinit var logView: LinearLayout
    private lateinit var messageInput: EditText
    private lateinit var listenButton: Button
    private var ttsReady = false
    private var recognizerReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(22, 134, 91)
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        knowledgeStore = KnowledgeStore(this)
        router = CommandRouter(this, knowledgeStore, NetworkHelper(this, knowledgeStore))
        textToSpeech = TextToSpeech(this, this)
        buildInterface()
        requestAudioPermission()
        setupSpeechRecognizer()
        appendLog("Tata", "Halo, saya Tata. Tekan Bicara untuk memberi perintah. Ucapkan bantuan untuk melihat kemampuan saya.")
    }

    private fun buildInterface() {
        val ink = Color.rgb(25, 37, 52)
        val muted = Color.rgb(119, 132, 148)
        val accent = Color.rgb(38, 105, 230)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(246, 248, 252))
            setPadding(dp(16), dp(10), dp(16), dp(12))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(12))
        }
        val brandColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "Tata AI"
            textSize = 23f
            setTextColor(ink)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "Asisten pribadi cerdas"
            textSize = 12f
            setTextColor(muted)
        }
        brandColumn.addView(title)
        brandColumn.addView(subtitle)
        header.addView(brandColumn, LinearLayout.LayoutParams(0, -2, 1f))
        val status = TextView(this).apply {
            text = "● Siap membantu"
            textSize = 11f
            setTextColor(Color.rgb(28, 153, 103))
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setBackgroundColor(Color.rgb(229, 247, 239))
        }
        header.addView(status)
        root.addView(header)

        val welcome = TextView(this).apply {
            text = "Halo! Saya Tata.\nApa yang ingin kamu lakukan hari ini?"
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(accent)
        }
        root.addView(welcome, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })

        val quickActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun quickAction(label: String, command: String) {
            val chip = Button(this).apply {
                text = label
                textSize = 11f
                isAllCaps = false
                setTextColor(Color.rgb(49, 81, 132))
                setOnClickListener { processCommand(command) }
                minHeight = dp(40)
                setPadding(dp(8), 0, dp(8), 0)
            }
            quickActions.addView(chip, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginEnd = dp(5)
            })
        }
        quickAction("Bantuan", "bantuan")
        quickAction("Jam", "jam berapa")
        quickAction("Baterai", "cek baterai")
        quickAction("Berita", "berita hari ini")
        root.addView(quickActions)

        val conversationTitle = TextView(this).apply {
            text = "Percakapan"
            textSize = 13f
            setTextColor(ink)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(2), dp(12), dp(2), dp(8))
        }
        root.addView(conversationTitle)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, dp(8))
        }
        logView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(4), dp(2), dp(12))
        }
        scroll.addView(logView)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val toolsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun toolButton(label: String, action: () -> Unit) {
            val button = Button(this).apply {
                text = label
                textSize = 10f
                isAllCaps = false
                setOnClickListener { action() }
                minHeight = dp(38)
                setPadding(dp(4), 0, dp(4), 0)
            }
            toolsRow.addView(button, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                marginEnd = dp(4)
            })
        }
        toolButton("Pengetahuan", ::showKnowledgeManager)
        toolButton("Suara", ::showVoiceSettings)
        toolButton("Cadangan", ::showKnowledgeBackupOptions)
        root.addView(toolsRow)

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(4), dp(4))
            setBackgroundColor(Color.WHITE)
        }
        messageInput = EditText(this).apply {
            hint = "Tulis pesan untuk Tata..."
            textSize = 14f
            setTextColor(ink)
            setHintTextColor(muted)
            background = null
            maxLines = 4
            minLines = 1
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        composer.addView(messageInput, LinearLayout.LayoutParams(0, -2, 1f))
        val sendButton = Button(this).apply {
            text = "Kirim"
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(accent)
            setOnClickListener {
                val message = messageInput.text.toString().trim()
                if (message.isNotEmpty()) {
                    messageInput.setText("")
                    processCommand(message)
                }
            }
        }
        composer.addView(sendButton, LinearLayout.LayoutParams(dp(76), dp(46)))
        root.addView(composer, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })

        listenButton = Button(this).apply {
            text = "🎙  Bicara dengan Tata"
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(24, 157, 111))
            setOnClickListener { startListening() }
        }
        root.addView(listenButton, LinearLayout.LayoutParams(-1, dp(50)).apply {
            topMargin = dp(8)
        })
        setContentView(root)
    }

    private fun availableIndonesianVoices(): List<android.speech.tts.Voice> {
        if (!::textToSpeech.isInitialized) return emptyList()
        return textToSpeech.voices.orEmpty()
            .filter { it.locale.language.equals("id", ignoreCase = true) }
            .sortedBy { it.name }
    }

    private fun showVoiceSettings() {
        if (!ttsReady) {
            Toast.makeText(this, "Mesin suara Tata belum siap. Periksa mesin Text-to-Speech Android.", Toast.LENGTH_LONG).show()
            return
        }
        val voices = availableIndonesianVoices()
        if (voices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Suara Tata")
                .setMessage("Belum ada pilihan suara bahasa Indonesia dari mesin Text-to-Speech di HP ini. Tata akan memakai suara bawaan Android dengan karakter lebih lembut.")
                .setPositiveButton("Pengaturan suara Android") { _, _ ->
                    runCatching {
                        startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                    }.onFailure {
                        Toast.makeText(this, "Pengaturan Text-to-Speech tidak tersedia.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Tutup", null)
                .show()
            return
        }

        val prefs = getSharedPreferences(PREFS_VOICE, MODE_PRIVATE)
        val savedName = prefs.getString(KEY_VOICE_NAME, null)
        val selected = voices.indexOfFirst { it.name == savedName }.let { if (it >= 0) it else 0 }
        val labels = voices.map { voice ->
            val quality = if (voice.quality >= android.speech.tts.Voice.QUALITY_HIGH) " • kualitas tinggi" else ""
            "${voice.name} (${voice.locale.displayName})$quality"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Pilih suara Tata")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                val voice = voices[which]
                val result = textToSpeech.setVoice(voice)
                if (result == TextToSpeech.SUCCESS) {
                    prefs.edit().putString(KEY_VOICE_NAME, voice.name).apply()
                    applyFriendlyVoiceStyle()
                    textToSpeech.speak(
                        "Halo, saya Tata. Senang bisa membantu kamu.",
                        TextToSpeech.QUEUE_FLUSH, null, "tata-voice-preview"
                    )
                    Toast.makeText(this, "Suara Tata diperbarui.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Suara ini tidak dapat digunakan.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setPositiveButton("Selesai", null)
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun applyFriendlyVoiceStyle() {
        if (!::textToSpeech.isInitialized) return
        // Pitch sedikit lebih tinggi dan tempo sedikit lebih pelan untuk kesan lembut.
        textToSpeech.setPitch(1.12f)
        textToSpeech.setSpeechRate(0.94f)
    }

    private fun restorePreferredVoice() {
        if (!::textToSpeech.isInitialized) return
        val voices = availableIndonesianVoices()
        val savedName = getSharedPreferences(PREFS_VOICE, MODE_PRIVATE)
            .getString(KEY_VOICE_NAME, null)
        val savedVoice = voices.firstOrNull { it.name == savedName }
        val suggestedFemaleVoice = voices.firstOrNull { voice ->
            val description = (voice.name + " " + voice.features.joinToString(" ")).lowercase(Locale.ROOT)
            listOf("female", "woman", "perempuan", "wanita").any { description.contains(it) }
        }
        val preferred = savedVoice ?: suggestedFemaleVoice
        if (preferred != null && textToSpeech.setVoice(preferred) == TextToSpeech.SUCCESS) {
            getSharedPreferences(PREFS_VOICE, MODE_PRIVATE).edit()
                .putString(KEY_VOICE_NAME, preferred.name).apply()
        }
        applyFriendlyVoiceStyle()
    }

    private fun showKnowledgeManager() {
        val entries = knowledgeStore.getAll()
        val labels = entries.map { entry ->
            val keys = entry.keywords.take(3).joinToString(", ")
            if (keys.isBlank()) entry.question else "${entry.question}\nKata kunci: $keys"
        }.toTypedArray()

        val builder = AlertDialog.Builder(this)
            .setTitle("Pengetahuan Tata")
            .setItems(labels) { _, which -> showKnowledgeEditor(which) }
            .setPositiveButton("Tambah data", null)
            .setNegativeButton("Tutup", null)

        if (entries.isEmpty()) builder.setMessage("Belum ada data pengetahuan.")
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.dismiss()
                showKnowledgeEditor(null)
            }
        }
        dialog.show()
    }

    private fun showKnowledgeEditor(index: Int?) {
        val existing = index?.let { knowledgeStore.getAll().getOrNull(it) }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        fun field(hintText: String, value: String = "", multiline: Boolean = false): EditText {
            return EditText(this).apply {
                hint = hintText
                setText(value)
                textSize = 15f
                if (multiline) {
                    minLines = 3
                    maxLines = 5
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    gravity = Gravity.TOP
                } else {
                    setSingleLine(true)
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                }
            }
        }
        val question = field("Pertanyaan, contoh: siapa pemilik toko?", existing?.question.orEmpty())
        val keywords = field("Kata kunci, pisahkan dengan koma", existing?.keywords?.joinToString(", ").orEmpty())
        val answer = field("Jawaban yang akan disampaikan Tata", existing?.answer.orEmpty(), true)
        form.addView(question)
        form.addView(keywords)
        form.addView(answer)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (index == null) "Tambah pengetahuan" else "Edit pengetahuan")
            .setView(form)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .apply {
                if (index != null) setNeutralButton("Hapus", null)
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val q = question.text.toString().trim()
                val a = answer.text.toString().trim()
                if (q.isBlank() || a.isBlank()) {
                    Toast.makeText(this, "Pertanyaan dan jawaban wajib diisi.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val words = keywords.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() }
                if (index == null) knowledgeStore.add(q, words, a)
                else knowledgeStore.update(index, q, words, a)
                dialog.dismiss()
                Toast.makeText(this, "Pengetahuan Tata berhasil disimpan.", Toast.LENGTH_SHORT).show()
            }
            if (index != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Hapus pengetahuan?")
                        .setMessage("Data ini akan dihapus dari Tata.")
                        .setPositiveButton("Hapus") { _, _ ->
                            knowledgeStore.delete(index)
                            dialog.dismiss()
                            Toast.makeText(this, "Data pengetahuan dihapus.", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                }
            }
        }
        dialog.show()
    }

    private fun showKnowledgeBackupOptions() {
        AlertDialog.Builder(this)
            .setTitle("Cadangan Pengetahuan Tata")
            .setItems(arrayOf("Ekspor / simpan cadangan JSON", "Impor dari file JSON")) { _, which ->
                if (which == 0) exportKnowledgeJson() else importKnowledgeJson()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun exportKnowledgeJson() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "pengetahuan-tata.json")
        }
        startActivityForResult(intent, REQUEST_EXPORT_JSON)
    }

    private fun importKnowledgeJson() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_IMPORT_JSON)
    }

    @Deprecated("Use document picker result handling")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_EXPORT_JSON -> runCatching {
                val json = knowledgeStore.exportJson()
                contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("Tidak dapat menyimpan file.")
            }.onSuccess {
                Toast.makeText(this, "Cadangan pengetahuan berhasil disimpan.", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(this, "Gagal mengekspor: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
            }
            REQUEST_IMPORT_JSON -> runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Tidak dapat membaca file.")
                require(bytes.size <= 2 * 1024 * 1024) { "Ukuran file maksimal 2 MB." }
                knowledgeStore.importJson(String(bytes, Charsets.UTF_8))
            }.onSuccess { count ->
                Toast.makeText(this, "${count} data pengetahuan berhasil diimpor.", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(this, "Impor dibatalkan: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            appendLog("Sistem", "Layanan pengenalan suara tidak tersedia di perangkat.")
            listenButton.isEnabled = false
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerReady = true
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listenButton.text = "MENDENGARKAN..."
            }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { listenButton.text = "MEMPROSES..." }
            override fun onError(error: Int) {
                listenButton.text = "🎙  BICARA"
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Ucapan tidak dikenali. Coba bicara lebih jelas."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Pengenalan suara gagal. Pastikan paket bahasa Indonesia offline sudah diunduh."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin mikrofon belum diberikan."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Pengenal suara sedang sibuk. Coba lagi."
                    else -> "Terjadi kendala pengenalan suara (kode $error)."
                }
                appendLog("Sistem", message)
            }
            override fun onResults(results: Bundle?) {
                listenButton.text = "🎙  BICARA"
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (spoken.isNotBlank()) processCommand(spoken)
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    private fun startListening() {
        if (!recognizerReady) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermission()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        runCatching { speechRecognizer.startListening(intent) }
            .onFailure { appendLog("Sistem", "Tidak dapat memulai mikrofon: ${it.localizedMessage}") }
    }

    private fun processCommand(spoken: String) {
        appendLog("Anda", spoken)
        activityScope.launch {
            val answer = router.handle(spoken)
            if (answer == CommandRouter.CONFIRM_CLEAR_KNOWLEDGE) {
                showClearKnowledgeConfirmation()
            } else {
                appendLog("Tata AI", answer)
                if (ttsReady) textToSpeech.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "tata-ai-response")
            }
        }
    }

    private fun showClearKnowledgeConfirmation() {
        val count = knowledgeStore.getKnowledgeCount()
        val message = if (count == 0) {
            "Saat ini belum ada pengetahuan tersimpan yang perlu dihapus."
        } else {
            "Tata akan menghapus $count data pengetahuan yang tersimpan. Riwayat percakapan, pengaturan aplikasi, dan konfigurasi sumber berita tidak ikut dihapus. Tindakan ini tidak dapat dibatalkan."
        }
        AlertDialog.Builder(this)
            .setTitle("Konfirmasi hapus seluruh pengetahuan")
            .setMessage(message)
            .setPositiveButton(if (count == 0) "Mengerti" else "Ya, hapus pengetahuan") { dialog, _ ->
                if (count > 0) {
                    val deleted = knowledgeStore.clearAllLearnedKnowledge()
                    val result = "Pengetahuan tersimpan berhasil dihapus sebanyak $deleted data. Riwayat percakapan dan pengaturan tetap dipertahankan."
                    appendLog("Tata AI", result)
                    if (ttsReady) textToSpeech.speak(result, TextToSpeech.QUEUE_FLUSH, null, "tata-clear-knowledge")
                }
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun appendLog(speaker: String, message: String) {
        if (!::logView.isInitialized) return
        val isUser = speaker == "Anda"
        val bubble = TextView(this).apply {
            text = "$speaker\n$message"
            textSize = 14f
            setTextColor(if (isUser) Color.WHITE else Color.rgb(37, 49, 66))
            setPadding(dp(14), dp(11), dp(14), dp(11))
            setBackgroundColor(if (isUser) Color.rgb(48, 111, 224) else Color.WHITE)
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (isUser) Gravity.END else Gravity.START
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }
        row.addView(bubble, LinearLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.78f).toInt(), -2
        ))
        logView.addView(row)
        if (::knowledgeStore.isInitialized) knowledgeStore.saveChat(speaker, message)
    }

    private fun requestAudioPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Izin mikrofon diperlukan untuk fitur Bicara.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale("id", "ID"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ttsReady) appendLog("Sistem", "Data suara bahasa Indonesia belum tersedia di perangkat.")
            else restorePreferredVoice()
        } else {
            appendLog("Sistem", "Text-to-Speech gagal diinisialisasi.")
        }
    }

    override fun onDestroy() {
        if (recognizerReady) {
            speechRecognizer.cancel()
            speechRecognizer.destroy()
        }
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        activityScope.cancel()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_AUDIO = 301
        private const val REQUEST_EXPORT_JSON = 302
        private const val REQUEST_IMPORT_JSON = 303
private const val PREFS_VOICE = "tata_voice_preferences"
private const val KEY_VOICE_NAME = "voice_name"
    }
}
