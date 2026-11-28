package com.harmanibike.voiceo3

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.View
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Handler
import android.os.Looper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
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
    private lateinit var localModelStore: LocalModelStore
    private lateinit var localLlmEngine: LocalLlmEngine
    private lateinit var logView: LinearLayout
    private lateinit var messageInput: EditText
    private lateinit var listenButton: CircleSpectrumView
    private lateinit var conversationStatusView: TextView
    private var ttsReady = false
    private var recognizerReady = false
    private var conversationModeActive = false
    private val conversationHandler = Handler(Looper.getMainLooper())
    private lateinit var connectionStatusView: TextView
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallbackRegistered = false
    private var activityDestroyed = false
    private var selectedVoiceNetworkMode: Boolean? = null
    private var lastSpeechText: String? = null
    // Temporary research text exists only in this Activity session; never persisted.
    private var temporaryResearchText: String? = null
    // Context terakhir hanya dipakai untuk membuka formulir koreksi; tidak disimpan tersendiri.
    private var lastUserQuestionForCorrection: String = ""
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { refreshConnectionStatus() }
        override fun onLost(network: Network) { refreshConnectionStatus() }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { refreshConnectionStatus() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(22, 134, 91)
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        knowledgeStore = KnowledgeStore(this)
        localModelStore = LocalModelStore(this)
        localLlmEngine = LocalLlmEngine()
        router = CommandRouter(this, knowledgeStore, NetworkHelper(this, knowledgeStore), localLlmEngine)
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        textToSpeech = TextToSpeech(this, this)
        buildInterface()
        requestAudioPermission()
        setupSpeechRecognizer()
        appendLog("Tata", "Halo, saya Tata AI versi ${packageManager.getPackageInfo(packageName, 0).versionName}. AI adalah Artificial Intelligence atau kecerdasan buatan. Tekan lingkaran spektrum untuk mulai berbicara, atau ketik pesan. Ucapkan bantuan untuk melihat kemampuan saya.")
        requestChatStorageConsentIfNeeded()
    }

    private fun buildInterface() {
        val purple = Color.rgb(91, 38, 226)
        val violet = Color.rgb(174, 47, 235)
        val ink = Color.rgb(43, 43, 53)
        val muted = Color.rgb(115, 115, 128)
        val white = Color.WHITE
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(247, 247, 250))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(22))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(72, 35, 232), violet)
            ).apply { cornerRadii = floatArrayOf(0f,0f,0f,0f,dp(42).toFloat(),dp(42).toFloat(),dp(42).toFloat(),dp(42).toFloat()) }
        }
        val headerLine = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val menuButton = TextView(this).apply {
            text = "☰"
            textSize = 27f
            includeFontPadding = false
            gravity = Gravity.CENTER
            setTextColor(white)
            contentDescription = "Buka navigasi Tata AI"
            isClickable = true
            isFocusable = true
            setPadding(dp(8), dp(4), dp(14), dp(4))
            setOnClickListener { showNavigationMenu() }
        }
        headerLine.addView(menuButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        val title = TextView(this).apply {
            text = "Tata AI"
            textSize = 25f
            setTextColor(white)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        headerLine.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        connectionStatusView = TextView(this).apply {
            text = "● OFFLINE"
            textSize = 12f
            setTextColor(white)
            setPadding(dp(9), dp(5), dp(9), dp(5))
            background = rounded(Color.rgb(110, 110, 118), 18)
        }
        headerLine.addView(connectionStatusView)
        header.addView(headerLine)
        root.addView(header)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(14), dp(12), dp(14), dp(8))
            background = null
            elevation = 0f
        }
        val stateLabel = TextView(this).apply {
            text = "Siap mendengarkan"
            textSize = 16f
            setTextColor(ink)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        conversationStatusView = stateLabel
        panel.addView(stateLabel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        val micButton = CircleSpectrumView(this).apply {
            contentDescription = "Spektrum suara Tata. Ketuk untuk mulai atau menghentikan obrolan."
            background = rounded(Color.rgb(239, 234, 250), 30)
            elevation = 0f
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { toggleConversationMode() }
        }
        listenButton = micButton
        panel.addView(micButton, LinearLayout.LayoutParams(dp(148), dp(148)).apply {
            topMargin = dp(3); bottomMargin = dp(8)
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            setPadding(0, 0, 0, dp(8))
        }
        logView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(4), dp(2), dp(8))
        }
        scroll.addView(logView)
        scroll.minimumHeight = dp(90)
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val hint = TextView(this).apply {
            text = "Ketuk spektrum untuk mulai berbicara"
            textSize = 10f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, dp(5))
        }
        panel.addView(hint)
        root.addView(panel, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = -dp(18)
            leftMargin = dp(8); rightMargin = dp(8)
        })

        messageInput = EditText(this).apply {
            setHint("Tulis pesan untuk Tata...")
            textSize = 14f
            setTextColor(ink)
            background = rounded(white, 22)
            maxLines = 3
            minLines = 1
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val composer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(4), dp(10), dp(4)) }
        composer.addView(messageInput, LinearLayout.LayoutParams(0, -2, 1f))
        val send = Button(this).apply {
            text = "Kirim"; isAllCaps = false; setTextColor(white); background = rounded(purple, 18)
            setOnClickListener {
                val value = messageInput.text.toString().trim()
                if (value.isNotEmpty()) { messageInput.setText(""); processCommand(value) }
            }
        }
        composer.addView(send, LinearLayout.LayoutParams(dp(68), dp(42)).apply { leftMargin = dp(8) })
        root.addView(composer)

        setContentView(root)
    }

    private fun showNavigationMenu() {
        val items = arrayOf("Percakapan", "Mode suara", "Riwayat percakapan", "Pengetahuan", "Learning Lab", "Model AI lokal", "Pengaturan Tata")
        AlertDialog.Builder(this).setTitle("Navigasi Tata AI")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> messageInput.requestFocus()
                    1 -> toggleConversationMode()
                    2 -> showDataPrivacySettings()
                    3 -> showKnowledgeManager()
                    4 -> showLearningLab()
                    5 -> showLocalModelsManager()
                    6 -> showSettingsMenu()
                }
            }.setNegativeButton("Tutup", null).show()
    }

    private fun showSettingsMenu() {
        val items = arrayOf("Tentang Tata AI", "Suara Tata", "Cadangan data", "Privasi", "Sumber berita")
        AlertDialog.Builder(this).setTitle("Pengaturan Tata")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showAboutTata()
                    1 -> showVoiceSettings()
                    2 -> showKnowledgeBackupOptions()
                    3 -> showDataPrivacySettings()
                    4 -> showNewsSourcesManager()
                }
            }.setNegativeButton("Kembali") { _, _ -> showNavigationMenu() }.show()
    }

    private var isVoiceMuted = false

    override fun onStart() {
        super.onStart()
        if (!networkCallbackRegistered) {
            runCatching {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
                networkCallbackRegistered = true
            }
        }
        refreshConnectionStatus()
    }

    override fun onStop() {
        if (networkCallbackRegistered) {
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
        }
        // Lepas bobot model ketika aplikasi tidak terlihat agar RAM kembali tersedia.
        if (::localLlmEngine.isInitialized && localLlmEngine.isLoaded) {
            localLlmEngine.cancel()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching { localLlmEngine.unload() }
            }
        }
        super.onStop()
    }

    private fun isInternetValidated(): Boolean = runCatching {
        val active = connectivityManager.activeNetwork
        val caps = active?.let { connectivityManager.getNetworkCapabilities(it) }
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    private fun refreshConnectionStatus() {
        val connected = isInternetValidated()
        runOnUiThread {
            if (!::connectionStatusView.isInitialized) return@runOnUiThread
            connectionStatusView.text = if (connected) "● ONLINE" else "● OFFLINE"
            connectionStatusView.background = rounded(
                if (connected) Color.rgb(19, 184, 126) else Color.rgb(110, 110, 118),
                18
            )
            if (selectedVoiceNetworkMode != connected && ttsReady) {
                applyVoiceForConnection(connected)
            }
        }
    }

    private fun rounded(color: Int, radius: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
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
            val mode = if (voice.isNetworkConnectionRequired) " • online" else " • offline"
            "${voice.name} (${voice.locale.displayName})$quality$mode"
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

    private fun voiceGenderScore(voice: android.speech.tts.Voice): Int {
        val description = (voice.name + " " + voice.features.joinToString(" ")).lowercase(Locale.ROOT)
        return when {
            listOf("female", "woman", "perempuan", "wanita").any { description.contains(it) } -> 3
            listOf("child", "young", "youth", "girl").any { description.contains(it) } -> 2
            else -> 0
        }
    }

    /**
     * Uses the Android TTS engine's network voice when online and a locally
     * installed Indonesian voice when offline. Availability depends on the
     * TTS engine and downloaded voice packages on the device.
     */
    private fun applyVoiceForConnection(online: Boolean) {
        if (!ttsReady || !::textToSpeech.isInitialized) return
        val voices = availableIndonesianVoices()
        if (voices.isEmpty()) {
            selectedVoiceNetworkMode = online
            applyFriendlyVoiceStyle()
            return
        }

        val matchingMode = voices.filter { it.isNetworkConnectionRequired == online }
        val candidates = if (matchingMode.isNotEmpty()) matchingMode else voices.filter {
            !it.isNetworkConnectionRequired
        }
        val selected = candidates.sortedWith(
            compareByDescending<android.speech.tts.Voice> { voiceGenderScore(it) }
                .thenByDescending { it.quality }
                .thenBy { it.name }
        ).firstOrNull()

        if (selected != null && textToSpeech.setVoice(selected) == TextToSpeech.SUCCESS) {
            selectedVoiceNetworkMode = online
            applyFriendlyVoiceStyle()
            if (online && !selected.isNetworkConnectionRequired) {
                appendLog("Sistem", "Suara online khusus belum tersedia. Tata memakai suara offline sebagai cadangan.")
            }
        } else {
            selectedVoiceNetworkMode = online
            applyFriendlyVoiceStyle()
        }
    }

    private fun restorePreferredVoice() {
        if (!::textToSpeech.isInitialized) return
        applyVoiceForConnection(isInternetValidated())
    }

    private fun showLearningLab(filterStatus: String? = null) {
        val allRecords = knowledgeStore.getLearningRecords()
        val records = if (filterStatus == null) allRecords else allRecords.filter { it.status == filterStatus }
        val filterLabel = when (filterStatus) {
            "perlu_ditinjau" -> "Perlu ditinjau"
            "diterima" -> "Diterima"
            "ditolak" -> "Ditolak"
            "diterapkan" -> "Sudah menjadi pengetahuan aktif"
            "sudah_diuji" -> "Sudah diuji"
            else -> "Semua"
        }
        val labels = records.map { record ->
            val status = when (record.status) {
                "diterima" -> "Diterima untuk ditinjau sebagai pengetahuan"
                "ditolak" -> "Ditolak"
                "diterapkan" -> "Sudah menjadi pengetahuan aktif"
                "sudah_diuji" -> "Sudah diuji"
                else -> "Perlu ditinjau"
            }
            "${record.question.take(58)}\n$status"
        }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle("Learning Lab · $filterLabel (${records.size}/${allRecords.size})")
            .setItems(labels) { _, which -> showLearningRecordDetail(records[which]) }
            .setPositiveButton("Tambah koreksi", null)
            .setNegativeButton("Tutup", null)
            .setNeutralButton("File", null)
            .create()
        if (records.isEmpty()) dialog.setMessage(
            if (allRecords.isEmpty()) "Belum ada catatan pembelajaran. Catatan hanya disimpan setelah kamu menekan Simpan."
            else "Tidak ada catatan pada filter ini."
        )
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.dismiss()
                showLearningRecordEditor()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(this).setTitle("Pertukaran Learning Lab")
                    .setItems(arrayOf("Filter catatan", "Ekspor catatan ke file", "Gabungkan dari file lain")) { _, which ->
                        when (which) {
                            0 -> {
                                val filterLabels = arrayOf("Semua", "Perlu ditinjau", "Diterima", "Diterapkan", "Ditolak", "Sudah diuji")
                                val filterValues = arrayOf(null, "perlu_ditinjau", "diterima", "diterapkan", "ditolak", "sudah_diuji")
                                AlertDialog.Builder(this).setTitle("Filter catatan Learning Lab")
                                    .setItems(filterLabels) { _, selected -> showLearningLab(filterValues[selected]) }
                                    .setNegativeButton("Batal", null).show()
                            }
                            1 -> exportLearningLab()
                            2 -> importLearningLab()
                        }
                    }.setNegativeButton("Batal", null).show()
            }
        }
        dialog.show()
    }

    private fun showLearningRecordEditor(initialQuestion: String = "", previousAnswer: String = "", editingRecord: LearningRecord? = null) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(4)) }
        fun input(hintText: String, lines: Int): EditText = EditText(this).apply {
            hint = hintText; textSize = 15f; minLines = lines; maxLines = if (lines == 1) 2 else 5
            inputType = InputType.TYPE_CLASS_TEXT or (if (lines > 1) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0) or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = if (lines > 1) Gravity.TOP else Gravity.CENTER_VERTICAL
        }
        val question = input("Pertanyaan atau konteks yang dijawab Tata", 2).apply { setText(initialQuestion) }
        val correction = input("Koreksi atau jawaban yang lebih tepat", 4).apply { editingRecord?.let { setText(it.correction) } }
        val source = input("Sumber/referensi (opsional)", 1).apply { editingRecord?.let { setText(it.source) } }
        form.addView(question); form.addView(correction); form.addView(source)
        val explanation = buildString {
            append("Data hanya disimpan jika kamu menekan Simpan. Tidak dikirim dan tidak otomatis menjadi pengetahuan aktif.")
            if (previousAnswer.isNotBlank()) append("\n\nJawaban Tata sebelumnya:\n").append(previousAnswer.take(1800))
        }
        val dialog = AlertDialog.Builder(this).setTitle(if (editingRecord == null) "Koreksi jawaban Tata" else "Edit catatan pembelajaran")
            .setMessage(explanation)
            .setView(form).setPositiveButton("Simpan lokal", null).setNegativeButton("Batal", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val q = question.text.toString().trim(); val a = correction.text.toString().trim()
                if (q.isBlank() || a.isBlank()) { Toast.makeText(this, "Konteks dan koreksi wajib diisi.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                AlertDialog.Builder(this).setTitle("Simpan koreksi di HP ini?")
                    .setMessage("Koreksi dan sumber yang kamu masukkan akan disimpan ke Learning Lab lokal. Tidak dibagikan dan tidak melatih model secara otomatis.")
                    .setPositiveButton("Ya, simpan") { _, _ ->
                        if (editingRecord == null) {
                            knowledgeStore.addLearningRecord(q, a, source.text.toString())
                        } else {
                            knowledgeStore.updateLearningRecord(editingRecord.id, q, a, source.text.toString())
                        }
                        dialog.dismiss()
                        Toast.makeText(this, if (editingRecord == null) "Koreksi tersimpan secara lokal." else "Catatan diperbarui.", Toast.LENGTH_SHORT).show()
                    }.setNegativeButton("Periksa lagi", null).show()
            }
        }
        dialog.show()
    }

    private fun showLearningRecordDetail(record: LearningRecord) {
        val statusLabel = when (record.status) { "diterima" -> "Diterima untuk ditinjau sebagai pengetahuan"; "diterapkan" -> "Sudah menjadi pengetahuan aktif"; "ditolak" -> "Ditolak"; "sudah_diuji" -> "Sudah diuji"; else -> "Perlu ditinjau" }
        val message = buildString {
            append("Konteks:\n").append(record.question)
            append("\n\nKoreksi:\n").append(record.correction)
            if (record.source.isNotBlank()) append("\n\nSumber:\n").append(record.source)
            append("\n\nStatus: ").append(statusLabel)
            append(if (record.status == "diterapkan" || record.status == "sudah_diuji") "\n\nStatus pengetahuan: ${if (record.status == "diterapkan") "sudah aktif" else "sudah diuji"}." else "\n\nCatatan ini masih terpisah dari pengetahuan aktif Tata.")
        }
        val options = arrayOf("Edit catatan") + when (record.status) {
            "perlu_ditinjau" -> arrayOf("Terima sebagai pengetahuan", "Tandai ditolak", "Hapus catatan")
            "diterima" -> arrayOf("Masukkan ke pengetahuan Tata", "Tandai sudah diuji", "Hapus catatan")
            "diterapkan" -> arrayOf("Tandai sudah diuji", "Kembalikan untuk ditinjau", "Hapus catatan")
            "sudah_diuji" -> arrayOf("Masukkan ke pengetahuan Tata", "Kembalikan untuk ditinjau", "Hapus catatan")
            "ditolak" -> arrayOf("Kembalikan untuk ditinjau", "Hapus catatan")
            else -> arrayOf("Kembalikan untuk ditinjau", "Hapus catatan")
        }
        AlertDialog.Builder(this).setTitle("Detail pembelajaran").setMessage(message)
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Edit catatan" -> showLearningRecordEditor(record.question, editingRecord = record)
                    "Terima sebagai pengetahuan" -> { knowledgeStore.updateLearningStatus(record.id, "diterima"); Toast.makeText(this, "Catatan diterima untuk ditinjau.", Toast.LENGTH_SHORT).show() }
                    "Tandai ditolak" -> knowledgeStore.updateLearningStatus(record.id, "ditolak")
                    "Tandai sudah diuji" -> knowledgeStore.updateLearningStatus(record.id, "sudah_diuji")
                    "Kembalikan untuk ditinjau" -> knowledgeStore.updateLearningStatus(record.id, "perlu_ditinjau")
                    "Masukkan ke pengetahuan Tata" -> {
                        val matches = knowledgeStore.findKnowledgeByQuestion(record.question)
                        val exact = matches.any {
                            it.answer.trim().replace(Regex("\\s+"), " ")
                                .equals(record.correction.trim().replace(Regex("\\s+"), " "), ignoreCase = true)
                        }
                        when {
                            exact -> {
                                knowledgeStore.updateLearningStatus(record.id, "diterapkan")
                                Toast.makeText(this, "Jawaban yang sama sudah ada. Catatan ditandai sebagai pengetahuan aktif.", Toast.LENGTH_LONG).show()
                            }
                            matches.isNotEmpty() -> AlertDialog.Builder(this)
                                .setTitle("Ada pengetahuan dengan pertanyaan sama")
                                .setMessage("Jawaban aktif yang sudah ada berbeda dengan koreksi ini. Tata tidak akan menimpa atau menambahkan jawaban yang berpotensi membingungkan. Periksa dan selesaikan konflik dari menu Pengetahuan terlebih dahulu.")
                                .setPositiveButton("Mengerti", null).show()
                            else -> AlertDialog.Builder(this)
                                .setTitle("Tambahkan ke pengetahuan aktif?")
                                .setMessage("Koreksi ini akan menjadi jawaban yang dapat digunakan Tata secara offline. Pastikan isi dan sumbernya benar. Setelah ditambahkan, status menjadi 'Diterapkan', bukan otomatis 'Sudah diuji'.")
                                .setPositiveButton("Tambahkan") { _, _ ->
                                    knowledgeStore.add(record.question, emptyList(), record.correction)
                                    knowledgeStore.updateLearningStatus(record.id, "diterapkan")
                                    Toast.makeText(this, "Pengetahuan aktif ditambahkan. Silakan uji jawaban Tata.", Toast.LENGTH_SHORT).show()
                                }.setNegativeButton("Batal", null).show()
                        }
                    }
                    "Hapus catatan" -> AlertDialog.Builder(this).setTitle("Hapus catatan pembelajaran?")
                        .setMessage("Catatan lokal ini akan dihapus. Pengetahuan aktif tidak terpengaruh.")
                        .setPositiveButton("Hapus") { _, _ -> knowledgeStore.deleteLearningRecord(record.id) }
                        .setNegativeButton("Batal", null).show()
                }
            }.setNegativeButton("Tutup", null).show()
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
            .setNeutralButton("Riwayat", null)
            .setNegativeButton("Tutup", null)

        if (entries.isEmpty()) builder.setMessage("Belum ada data pengetahuan.")
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.dismiss()
                showKnowledgeEditor(null)
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                showKnowledgeRevisionHistory()
            }
        }
        dialog.show()
    }

    private fun showKnowledgeRevisionHistory() {
        val revisions = knowledgeStore.getKnowledgeRevisions()
        if (revisions.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Riwayat pengetahuan")
                .setMessage("Belum ada perubahan yang tercatat. Riwayat mulai dibuat saat pengetahuan diedit atau dihapus pada versi ini.")
                .setPositiveButton("Kembali") { _, _ -> showKnowledgeManager() }.show()
            return
        }
        val labels = revisions.map { it.question + " — " + it.revisionType.replace("_", " ") }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Riwayat perubahan")
            .setItems(labels) { _, which ->
                val item = revisions[which]
                AlertDialog.Builder(this).setTitle("Pulihkan versi ini?")
                    .setMessage("Pertanyaan: " + item.question + "\n\nJawaban sebelumnya:\n" + item.answer + "\n\nVersi aktif saat ini akan tetap dicadangkan.")
                    .setPositiveButton("Pulihkan") { _, _ ->
                        val ok = knowledgeStore.restoreKnowledgeRevision(item.id)
                        Toast.makeText(this, if (ok) "Versi dipulihkan." else "Versi tidak ditemukan.", Toast.LENGTH_LONG).show()
                        showKnowledgeRevisionHistory()
                    }
                    .setNegativeButton("Batal", null).show()
            }
            .setPositiveButton("Selesai") { _, _ -> showKnowledgeManager() }.show()
    }

    private fun showKnowledgeEditor(index: Int?, initialQuestion: String = "", initialAnswer: String = "") {
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
        val question = field("Pertanyaan, contoh: siapa pemilik toko?", existing?.question ?: initialQuestion)
        val keywords = field("Kata kunci, pisahkan dengan koma", existing?.keywords?.joinToString(", ").orEmpty())
        val answer = field("Jawaban yang akan disampaikan Tata", existing?.answer ?: initialAnswer, true)
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
            REQUEST_IMPORT_MODEL -> importLocalModel(uri)
            REQUEST_EXPORT_LEARNING -> runCatching {
                val json = LearningLabTransfer.encode(knowledgeStore.getLearningRecords())
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    ?: error("Tidak dapat menyimpan file.")
            }.onSuccess {
                Toast.makeText(this, "File Learning Lab berhasil diekspor. Percakapan dan memori pribadi tidak disertakan.", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(this, "Gagal mengekspor: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
            }
            REQUEST_IMPORT_LEARNING -> performImportLearningLab(uri)
            REQUEST_IMPORT_JSON -> {
                if (knowledgeStore.getKnowledgeCount() > 0) {
                    AlertDialog.Builder(this)
                        .setTitle("Ganti pengetahuan Tata?")
                        .setMessage("Impor JSON akan mengganti seluruh pengetahuan yang saat ini tersimpan. Lanjutkan hanya jika Anda mengizinkan penggantian data.")
                        .setPositiveButton("Ya, lanjutkan impor") { _, _ -> performImportKnowledgeJson(uri) }
                        .setNegativeButton("Batal", null)
                        .show()
                } else {
                    performImportKnowledgeJson(uri)
                }
            }
        }
    }

    private fun exportLearningLab() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "tata-learning-lab.json")
        }
        startActivityForResult(intent, REQUEST_EXPORT_LEARNING)
    }

    private fun importLearningLab() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_IMPORT_LEARNING)
    }

    private fun performImportLearningLab(uri: Uri) {
        runCatching {
            val bytes = contentResolver.openInputStream(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= LearningLabTransfer.MAX_FILE_BYTES) {
                        "Ukuran file maksimal 5 MB."
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: error("Tidak dapat membaca file.")
            LearningLabTransfer.decode(String(bytes, Charsets.UTF_8))
        }.onSuccess { incoming ->
            val preview = LearningLabTransfer.preview(incoming, knowledgeStore.getLearningRecords())
            val message = "Isi file: ${incoming.size} catatan\\n" +
                "Data baru: ${preview.newRecords.size}\\n" +
                "Konflik (pertanyaan sama, koreksi berbeda): ${preview.conflictCount}\\n" +
                "Duplikat identik: ${preview.duplicateCount}\\n\\n" +
                "Data baru dan konflik akan ditambahkan sebagai catatan 'Perlu ditinjau'. " +
                "Data lama tidak ditimpa. Duplikat identik dilewati. " +
                "Percakapan, memori pribadi, dan pengetahuan aktif tidak diimpor."
            if (preview.importCount == 0) {
                AlertDialog.Builder(this).setTitle("Tidak ada data baru")
                    .setMessage(message).setPositiveButton("Mengerti", null).show()
            } else {
                AlertDialog.Builder(this).setTitle("Periksa sebelum menggabungkan")
                    .setMessage(message)
                    .setPositiveButton("Gabungkan catatan") { _, _ ->
                        runCatching {
                            knowledgeStore.mergeLearningRecords(preview.newRecords + preview.conflictRecords)
                        }.onSuccess { count ->
                            Toast.makeText(this, "$count catatan ditambahkan ke Learning Lab untuk ditinjau.", Toast.LENGTH_LONG).show()
                            showLearningLab("perlu_ditinjau")
                        }.onFailure {
                            Toast.makeText(this, "Gagal menggabungkan: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }.setNegativeButton("Batal", null).show()
            }
        }.onFailure {
            AlertDialog.Builder(this).setTitle("File tidak dapat diimpor")
                .setMessage(it.localizedMessage ?: "Format file tidak valid.")
                .setPositiveButton("Tutup", null).show()
        }
    }

    private fun performImportKnowledgeJson(uri: android.net.Uri) {
        runCatching {
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

    private fun setupSpeechRecognizer() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                appendLog("Sistem", "Layanan pengenalan suara tidak tersedia di perangkat.")
                listenButton.isEnabled = false
                return
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    updateListenButton("MENDENGARKAN...")
                }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) {
                    if (::listenButton.isInitialized) listenButton.setAmplitude(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { updateListenButton("MEMPROSES...") }
                override fun onError(error: Int) {
                    updateListenButton(if (conversationModeActive) "■  Hentikan ngobrol" else "▶  Mulai ngobrol otomatis")
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Ucapan tidak dikenali. Coba bicara lebih jelas."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Pengenalan suara gagal. Periksa koneksi atau unduh paket bahasa Indonesia offline."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin mikrofon belum diberikan."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Pengenal suara sedang sibuk. Coba lagi."
                        else -> "Terjadi kendala pengenalan suara (kode $error)."
                    }
                    if (conversationModeActive && !activityDestroyed) {
                        conversationHandler.postDelayed({ if (conversationModeActive && !activityDestroyed) startListening() }, 1200)
                    } else if (!activityDestroyed) appendLog("Sistem", message)
                }
                override fun onResults(results: Bundle?) {
                    updateListenButton(if (conversationModeActive) "■  Hentikan ngobrol" else "▶  Mulai ngobrol otomatis")
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (spoken.isNotBlank() && !activityDestroyed) processCommand(spoken)
                    else if (conversationModeActive && !activityDestroyed) {
                        conversationHandler.postDelayed({ if (conversationModeActive && !activityDestroyed) startListening() }, 500)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            recognizerReady = true
        } catch (error: Exception) {
            recognizerReady = false
            updateListenButton("Mikrofon tidak tersedia")
            appendLog("Sistem", "Gagal menyiapkan pengenal suara: ${error.localizedMessage ?: "kesalahan perangkat"}")
        }
    }

    private fun updateListenButton(label: String) {
        if (activityDestroyed) return
        runOnUiThread {
            if (activityDestroyed) return@runOnUiThread
            if (::conversationStatusView.isInitialized) conversationStatusView.text = label
            if (::listenButton.isInitialized) {
                listenButton.setActive(label.contains("MENDENGARKAN", ignoreCase = true))
                if (!label.contains("MENDENGARKAN", ignoreCase = true) &&
                    !label.contains("BERBICARA", ignoreCase = true)
                ) listenButton.setAmplitude(0f)
            }
        }
    }

    private fun toggleConversationMode() {
        if (conversationModeActive) {
            conversationModeActive = false
            conversationHandler.removeCallbacksAndMessages(null)
            if (recognizerReady) runCatching { speechRecognizer.cancel() }
            if (::textToSpeech.isInitialized) textToSpeech.stop()
            updateListenButton("▶  Mulai ngobrol otomatis")
            appendLog("Tata", "Mode ngobrol dihentikan.")
            return
        }
        if (!recognizerReady) {
            Toast.makeText(this, "Pengenal suara belum tersedia di perangkat.", Toast.LENGTH_LONG).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermission()
            return
        }
        conversationModeActive = true
        updateListenButton("■  Hentikan ngobrol")
        appendLog("Tata", "Mode ngobrol aktif. Silakan bicara. Katakan Tata berhenti untuk mengakhiri.")
        speakAssistant("Halo, saya Tata. Saya siap ngobrol. Silakan bicara.")
    }

    private fun startListening() {
        if (!recognizerReady || activityDestroyed) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermission()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            // Utamakan pengenalan lokal saat offline; izinkan layanan jaringan saat online.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !isInternetValidated())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        runCatching { speechRecognizer.startListening(intent) }
            .onFailure {
                appendLog("Sistem", "Tidak dapat memulai mikrofon: ${it.localizedMessage ?: "layanan suara bermasalah"}.")
                if (conversationModeActive) {
                    conversationHandler.postDelayed({ if (conversationModeActive && !activityDestroyed) startListening() }, 1500)
                }
            }
    }

    private fun speakAssistant(message: String) {
        if (isVoiceMuted) {
            if (conversationModeActive) conversationHandler.postDelayed({ if (conversationModeActive) startListening() }, 250)
            return
        }
        if (!ttsReady) {
            if (conversationModeActive) conversationHandler.postDelayed({ if (conversationModeActive) startListening() }, 500)
            return
        }
        lastSpeechText = message
        textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "tata-conversation-" + System.nanoTime())
    }

    private fun processCommand(spoken: String) {
        val normalized = spoken.lowercase(Locale("id", "ID")).trim()
        if (TataIdentity.isIdentityQuestion(normalized)) {
            appendLog("Anda", spoken)
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
            val identity = TataIdentity.answer(normalized, versionName)
            appendLog("Tata AI", identity)
            speakAssistant(identity)
            return
        }
        if (conversationModeActive && listOf("tata berhenti", "berhenti tata", "akhiri obrolan", "hentikan ngobrol", "stop ngobrol").any { normalized.contains(it) }) {
            appendLog("Anda", spoken)
            conversationModeActive = false
            conversationHandler.removeCallbacksAndMessages(null)
            localLlmEngine.cancel()
            if (recognizerReady) runCatching { speechRecognizer.cancel() }
            speakAssistant("Baik, mode ngobrol saya hentikan. Sampai nanti.")
            updateListenButton("▶  Mulai ngobrol otomatis")
            return
        }
        activityScope.launch {
            val answer = router.handle(spoken)
            if (answer == CommandRouter.CONFIRM_CLEAR_KNOWLEDGE) {
                appendLog("Anda", spoken)
                showClearKnowledgeConfirmation()
            } else if (answer.startsWith(CommandRouter.UNKNOWN_QUERY_PREFIX)) {
                // Unknown questions and the research prompt remain visible only in this session.
                // They are deliberately excluded from both knowledge and chat-history storage.
                appendLog("Anda", spoken, persist = false)
                val question = answer.removePrefix(CommandRouter.UNKNOWN_QUERY_PREFIX).trim()
                showResearchOptions(question)
            } else {
                appendLog("Anda", spoken)
                appendLog("Tata AI", answer)
                speakAssistant(answer)
            }
        }
    }

    private fun showResearchOptions(question: String) {
        val message = "Saya belum menemukan jawaban yang cukup meyakinkan. Mau saya bantu mencarinya?\n\nPertanyaan ini tidak akan disimpan sebagai pengetahuan Tata."
        appendLog(
            "Tata",
            "Saya belum tahu jawabannya. Saya bisa membantu mencarinya di internet atau membuka AI lain. Pertanyaan ini hanya tampil sementara dan tidak disimpan ke database.",
            persist = false
        )
        speakAssistant("Saya belum tahu jawabannya. Mau kita cari di internet atau bertanya ke AI lain?")
        AlertDialog.Builder(this)
            .setTitle("Cari tahu bersama")
            .setMessage(message)
            .setItems(arrayOf("Cari di Internet", "Tanya ChatGPT", "Tanya Gemini", "Tempel jawaban untuk diringkas", "Nanti saja")) { dialog, which ->
                when (which) {
                    0 -> openResearchUrl("https://www.google.com/search?q=" + Uri.encode(question), "Pencarian internet")
                    1 -> openOtherAi("https://chatgpt.com/", question, "ChatGPT")
                    2 -> openOtherAi("https://gemini.google.com/app", question, "Gemini")
                    3 -> showTemporaryResearchInput(question)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun showLocalModelsManager() {
        val models = localModelStore.listModels()
        val activeName = localLlmEngine.activeModelPath?.let { java.io.File(it).name }
        val labels = models.map {
            it.name + " • " + formatModelSize(it.sizeBytes) +
                if (it.name == activeName && localLlmEngine.isLoaded) " • AKTIF" else ""
        }.toTypedArray()
        val nativeStatus = if (localLlmEngine.isAvailable) {
            "Mesin inferensi lokal tersedia." 
        } else {
            "Pustaka mesin AI lokal belum tersedia di APK ini. Perbarui APK setelah build native berhasil."
        }
        val loadedStatus = if (localLlmEngine.isLoaded) {
            "Model aktif: ${activeName ?: "model lokal"}."
        } else {
            "Belum ada model yang dimuat ke RAM."
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Model AI lokal")
            .setMessage(
                "Model diproses sepenuhnya di HP dan tidak dikirim ke server. " + nativeStatus + "\n" +
                    loadedStatus + "\n" +
                    "Ruang kosong: ${formatModelSize(localModelStore.availableBytes())}. Tata menjaga minimal 256 MB tetap kosong saat impor.\n\n" +
                    if (models.isEmpty()) "Belum ada file model GGUF. Impor model kecil yang kompatibel untuk memulai." else
                        "Model tersimpan: ${models.size}. Pilih model untuk mengaktifkan atau menghapusnya."
            )
            .setItems(labels) { _, which ->
                val selected = models[which]
                val isActive = localLlmEngine.isLoaded && localLlmEngine.activeModelPath == selected.file.absolutePath
                AlertDialog.Builder(this)
                    .setTitle(selected.name)
                    .setMessage(
                        "Ukuran: ${formatModelSize(selected.sizeBytes)}\n" +
                            if (isActive) "Model ini sedang aktif di RAM." else "Model tersimpan di penyimpanan privat dan belum aktif."
                    )
                    .setPositiveButton(if (isActive) "Nonaktifkan" else "Aktifkan model") { _, _ ->
                        if (isActive) deactivateLocalModel() else activateLocalModel(selected)
                    }
                    .setNeutralButton("Hapus model") { _, _ ->
                        AlertDialog.Builder(this)
                            .setTitle("Hapus file model?")
                            .setMessage("File ${selected.name} akan dihapus. Pengetahuan dan riwayat percakapan tidak ikut dihapus.")
                            .setPositiveButton("Ya, hapus") { _, _ ->
                                activityScope.launch {
                                    runCatching {
                                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                                            if (localLlmEngine.activeModelPath == selected.file.absolutePath) localLlmEngine.unload()
                                            localModelStore.deleteModel(selected)
                                        }
                                    }.onSuccess {
                                        Toast.makeText(this@MainActivity, if (it) "File model dihapus." else "File model tidak dapat dihapus.", Toast.LENGTH_LONG).show()
                                        showLocalModelsManager()
                                    }.onFailure { error ->
                                        Toast.makeText(this@MainActivity, "Gagal menghapus model: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            .setNegativeButton("Batal", null)
                            .show()
                    }
                    .setNegativeButton("Kembali", null)
                    .show()
            }
            .setPositiveButton("Impor model GGUF") { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                }
                startActivityForResult(intent, REQUEST_IMPORT_MODEL)
            }
            .setNeutralButton(if (localLlmEngine.isLoaded) "Lepas model" else "Tutup") { _, _ ->
                if (localLlmEngine.isLoaded) deactivateLocalModel()
            }
            .create()
        dialog.show()
    }

    private fun activateLocalModel(model: LocalModelFile) {
        if (!localLlmEngine.isAvailable) {
            Toast.makeText(this, "Mesin AI lokal belum tersedia pada APK ini.", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Memuat model ke RAM. Proses dapat memerlukan waktu.", Toast.LENGTH_LONG).show()
        activityScope.launch {
            val result = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    localLlmEngine.loadModel(model.file.absolutePath, contextSize = 1024, threads = 2)
                }
            }
            result.onSuccess { message ->
                Toast.makeText(
                    this@MainActivity,
                    if (message == "OK") "Model ${model.name} aktif. Tata siap menjawab secara lokal." else message,
                    Toast.LENGTH_LONG
                ).show()
                showLocalModelsManager()
            }.onFailure { error ->
                Toast.makeText(this@MainActivity, "Gagal memuat model: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun deactivateLocalModel() {
        activityScope.launch {
            runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) { localLlmEngine.unload() }
            }.onSuccess {
                Toast.makeText(this@MainActivity, "Model dilepas dari RAM.", Toast.LENGTH_SHORT).show()
                showLocalModelsManager()
            }.onFailure { error ->
                Toast.makeText(this@MainActivity, "Gagal melepas model: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun formatModelSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024.0)
        else String.format(Locale.US, "%.0f MB", mb)
    }

    private fun importLocalModel(uri: Uri) {
        activityScope.launch {
            runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    localModelStore.importFromUri(uri)
                }
            }.onSuccess { model ->
                Toast.makeText(this@MainActivity, "Model ${model.name} berhasil disimpan (${formatModelSize(model.sizeBytes)}).", Toast.LENGTH_LONG).show()
                showLocalModelsManager()
            }.onFailure { error ->
                Toast.makeText(this@MainActivity, "Gagal mengimpor model: ${error.localizedMessage ?: "file tidak valid"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showTemporaryResearchInput(question: String) {
        val answerInput = EditText(this).apply {
            hint = "Tempel teks hasil pencarian atau jawaban AI di sini..."
            textSize = 14f
            minLines = 5
            maxLines = 10
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(TextView(this@MainActivity).apply {
                text = "Tempel hasil yang ingin Tata baca. Teks ini hanya diproses sementara di perangkat dan tidak disimpan ke database."
                textSize = 13f
                setTextColor(Color.rgb(90, 90, 102))
            })
            addView(answerInput, LinearLayout.LayoutParams(-1, dp(150)).apply { topMargin = dp(10) })
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Baca hasil pencarian")
            .setView(content)
            .setPositiveButton("Baca & ringkas", null)
            .setNegativeButton("Batal", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pastedText = answerInput.text.toString().trim()
                if (pastedText.isBlank()) {
                    answerInput.error = "Tempel teks terlebih dahulu"
                    return@setOnClickListener
                }
                if (pastedText.length > 12000) {
                    answerInput.error = "Maksimal 12.000 karakter agar pemrosesan tetap ringan"
                    return@setOnClickListener
                }
                dialog.dismiss()
                summarizeResearchTemporarily(question, pastedText)
            }
        }
        dialog.show()
    }

    private fun summarizeResearchTemporarily(question: String, sourceText: String) {
        temporaryResearchText = sourceText
        val cleaned = sourceText.replace(Regex("\\s+"), " ").trim()
        val sentences = Regex("(?<=[.!?])\\s+")
            .split(cleaned)
            .map { it.trim() }
            .filter { it.length >= 25 }
            .distinctBy { it.lowercase(Locale("id", "ID")) }
        val summary = when {
            sentences.isEmpty() -> cleaned.take(700)
            sentences.size <= 3 -> sentences.joinToString(" ")
            else -> sentences.take(3).joinToString(" ")
        }.let { if (it.length > 1100) it.take(1097).trimEnd() + "..." else it }
        val result = "Ringkasan awal dari teks yang Anda tempel (diproses lokal, bukan verifikasi fakta):\n$summary\n\nCatatan: Tata belum memeriksa kebenaran sumber atau membandingkan sumber lain. Teks dan ringkasan ini tidak disimpan ke database."
        appendLog("Anda", "Memberikan teks hasil pencarian untuk diringkas.", persist = false)
        appendLog("Tata AI", result, persist = false)
        AlertDialog.Builder(this)
            .setTitle("Ringkasan sementara")
            .setMessage("Pertanyaan: $question\n\n$summary\n\nCatatan: ini ringkasan awal berdasarkan bagian teks yang ditempel, bukan verifikasi fakta. Belum disimpan ke database.")
            .setPositiveButton("Mengerti", null)
            .setNeutralButton("Tanya isi teks") { _, _ -> showTemporaryFollowUpInput() }
            .setNegativeButton("Ajari Tata") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Simpan sebagai pengetahuan?")
                    .setMessage("Tata akan membuka formulir agar Anda memeriksa dan mengedit pertanyaan serta jawaban terlebih dahulu. Tidak ada data yang disimpan sampai Anda menekan tombol Simpan.")
                    .setPositiveButton("Lanjut ke formulir") { _, _ ->
                        showKnowledgeEditor(null, question, summary)
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
            .show()
        speakAssistant("Berikut ringkasan awal dari teks yang Anda tempel. $summary. Perlu diingat, saya belum memverifikasi kebenaran sumbernya dan tidak menyimpan teks ini.")
    }


    private fun showTemporaryFollowUpInput() {
        val source = temporaryResearchText
        if (source.isNullOrBlank()) {
            Toast.makeText(this, "Teks sementara sudah tidak tersedia. Tempel ulang jika ingin bertanya.", Toast.LENGTH_LONG).show()
            return
        }
        val questionInput = EditText(this).apply {
            hint = "Contoh: Apa penyebabnya? Siapa yang terlibat?"
            textSize = 14f
            minLines = 2
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Tanya berdasarkan teks sementara")
            .setMessage("Tata mencari kalimat yang paling cocok dari teks yang tadi ditempel. Pemrosesan lokal dan tidak disimpan.")
            .setView(questionInput)
            .setPositiveButton("Cari jawaban", null)
            .setNegativeButton("Batal", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val question = questionInput.text.toString().trim()
                if (question.isBlank()) {
                    questionInput.error = "Tulis pertanyaan terlebih dahulu"
                    return@setOnClickListener
                }
                dialog.dismiss()
                answerFromTemporaryText(question, source)
            }
        }
        dialog.show()
    }

    private fun answerFromTemporaryText(question: String, sourceText: String) {
        val stopWords = setOf("apa", "yang", "dan", "atau", "ini", "itu", "dari", "untuk", "dengan", "bagaimana", "mengapa", "kenapa", "jelaskan", "tolong", "tentang", "siapa", "kapan", "dimana", "adalah", "saya", "aku")
        val keywords = Regex("[a-zA-Z0-9]{3,}").findAll(question.lowercase(Locale.ROOT))
            .map { it.value }.filterNot { it in stopWords }.toSet()
        val sentences = Regex("(?<=[.!?])\\s+")
            .split(sourceText.replace(Regex("\\s+"), " ").trim())
            .map { it.trim() }
            .filter { it.length >= 20 }
            .distinctBy { it.lowercase(Locale.ROOT) }
        val ranked = sentences.map { sentence ->
            val words = Regex("[a-zA-Z0-9]{3,}").findAll(sentence.lowercase(Locale.ROOT)).map { it.value }.toSet()
            sentence to keywords.count { it in words }
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(3)
        val answer = if (ranked.isEmpty()) {
            "Saya belum menemukan kalimat yang cocok. Coba gunakan kata yang memang muncul di teks tempelan."
        } else ranked.joinToString("\n\n") { it.first }
        val response = "$answer\n\nCatatan: ini pencarian kalimat berdasarkan kecocokan kata, bukan penalaran AI generatif atau verifikasi fakta. Teks dan pertanyaan ini hanya sementara, tidak disimpan ke database."
        appendLog("Anda", question, persist = false)
        appendLog("Tata AI", response, persist = false)
        AlertDialog.Builder(this).setTitle("Jawaban dari teks tempelan").setMessage(response)
            .setPositiveButton("Tutup", null).show()
        speakAssistant(answer)
    }

    private fun buildIdentityDescription(): String {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName ?: "versi belum ditentukan"
        @Suppress("DEPRECATION")
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
        val capabilities = knowledgeStore.getAssistantCapabilities()
            .filter { it.enabled }
            .joinToString(" ") { it.displayName + ": " + it.description }
        val localModelStatus = if (localLlmEngine.isLoaded) {
            "Model bahasa generatif lokal sedang aktif dan dapat menjawab pertanyaan terbuka tanpa API."
        } else {
            "Percakapan dasar masih berbasis aturan dan pencocokan teks. Model generatif lokal dapat diaktifkan dari menu Model AI lokal."
        }
        return "Halo, saya Tata AI. AI adalah singkatan dari Artificial Intelligence, dalam bahasa Indonesia berarti kecerdasan buatan. Saya adalah asisten digital Android yang dikembangkan dengan Kotlin. Versi aplikasi saya $versionName, kode versi $versionCode. Saya tidak memakai API AI eksternal. Kemampuan saya saat ini: $capabilities Saya juga dapat membantu perintah dasar, membaca pengetahuan yang Anda simpan dengan izin, mengelola suara, dan membaca berita RSS saat internet tersedia. Saat offline, fitur lokal tetap dapat digunakan. $localModelStatus Saya tidak memiliki perasaan seperti manusia. Saya mengenali petunjuk emosi dari kata-kata dan merespons dengan empati, tetapi tidak bisa mengetahui perasaan seseorang secara pasti. Saya tidak menyimpan pengetahuan baru tanpa izin Anda."
    }

    private fun showAboutTata() {
        val description = buildIdentityDescription()
        AlertDialog.Builder(this)
            .setTitle("Tentang Tata AI")
            .setMessage(description)
            .setPositiveButton("Kemampuan & profil") { _, _ -> showCapabilityProfileManager() }
            .setNeutralButton("Bacakan") { _, _ -> speakAssistant(description) }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun showCapabilityProfileManager() {
        val capabilities = knowledgeStore.getAssistantCapabilities()
        if (capabilities.isEmpty()) {
            AlertDialog.Builder(this).setMessage("Belum ada profil kemampuan.").setPositiveButton("Tutup", null).show()
            return
        }
        val labels = capabilities.map { it.displayName + " • v" + it.version + if (it.enabled) "" else " • Nonaktif" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Profil kemampuan Tata")
            .setMessage("Pilih kemampuan untuk melihat atau memperbarui keterangannya. Perubahan hanya disimpan setelah Anda menekan Simpan. Mengubah keterangan profil tidak otomatis menambah kode fungsi baru.")
            .setItems(labels) { _, which -> editCapabilityDescription(capabilities[which]) }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun editCapabilityDescription(capability: AssistantCapability) {
        val editor = EditText(this).apply {
            setText(capability.description)
            textSize = 14f
            minLines = 4
            maxLines = 8
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Ubah " + capability.displayName)
            .setMessage("Deskripsi ini menjadi bagian dari profil yang dapat Tata ceritakan. Simpan hanya jika Anda menyetujui perubahan.")
            .setView(editor)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val updated = editor.text.toString().trim()
                if (updated.isBlank()) {
                    editor.error = "Deskripsi tidak boleh kosong"
                    return@setOnClickListener
                }
                runCatching { knowledgeStore.updateCapabilityDescription(capability.key, updated) }
                    .onSuccess {
                        dialog.dismiss()
                        Toast.makeText(this, "Profil kemampuan diperbarui.", Toast.LENGTH_SHORT).show()
                        showCapabilityProfileManager()
                    }
                    .onFailure { editor.error = it.message ?: "Tidak dapat menyimpan perubahan" }
            }
        }
        dialog.show()
    }

    private fun openOtherAi(url: String, question: String, label: String) {
        runCatching {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Pertanyaan untuk $label", question))
        }
        Toast.makeText(this, "Pertanyaan disalin. Tempelkan di $label untuk mencari jawabannya.", Toast.LENGTH_LONG).show()
        openResearchUrl(url, label)
    }

    private fun openResearchUrl(url: String, label: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Tidak ada browser untuk membuka $label.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Tidak dapat membuka $label.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showNewsSourcesManager() {
        val sources = knowledgeStore.getAllNewsSources()
        val labels = sources.map { source ->
            "${source.name} • ${if (source.enabled) "Aktif" else "Nonaktif"}"
        }.toTypedArray()
        val builder = AlertDialog.Builder(this)
            .setTitle("Sumber berita Tata")
            .setItems(labels) { _, which -> showNewsSourceOptions(sources[which]) }
            .setPositiveButton("Tambah sumber", null)
            .setNegativeButton("Tutup", null)
        if (sources.isEmpty()) builder.setMessage("Belum ada sumber berita.")
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.dismiss()
                showNewsSourceEditor()
            }
        }
        dialog.show()
    }

    private fun showNewsSourceOptions(source: NewsSource) {
        val actions = arrayOf(
            if (source.enabled) "Nonaktifkan sumber" else "Aktifkan sumber",
            "Hapus sumber"
        )
        AlertDialog.Builder(this)
            .setTitle(source.name)
            .setItems(actions) { _, which ->
                if (which == 0) {
                    knowledgeStore.setNewsSourceEnabled(source.id, !source.enabled)
                    Toast.makeText(this, "Status sumber berita diperbarui.", Toast.LENGTH_SHORT).show()
                    showNewsSourcesManager()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Hapus sumber berita?")
                        .setMessage("Sumber ${source.name} akan dihapus dari database Tata.")
                        .setPositiveButton("Hapus") { _, _ ->
                            knowledgeStore.deleteNewsSource(source.id)
                            Toast.makeText(this, "Sumber berita dihapus.", Toast.LENGTH_SHORT).show()
                            showNewsSourcesManager()
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showNewsSourceEditor() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val name = EditText(this).apply {
            hint = "Nama sumber, contoh: ANTARA"
            setSingleLine(true)
        }
        val url = EditText(this).apply {
            hint = "URL feed RSS (https://...)"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        form.addView(name)
        form.addView(url)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Tambah sumber berita")
            .setView(form)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val sourceName = name.text.toString().trim()
                val sourceUrl = url.text.toString().trim()
                if (sourceName.isBlank() || sourceUrl.isBlank()) {
                    Toast.makeText(this, "Nama dan URL sumber wajib diisi.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                runCatching { knowledgeStore.addNewsSource(sourceName, sourceUrl) }
                    .onSuccess {
                        dialog.dismiss()
                        Toast.makeText(this, "Sumber berita disimpan.", Toast.LENGTH_SHORT).show()
                        showNewsSourcesManager()
                    }
                    .onFailure {
                        Toast.makeText(this, "Gagal menyimpan sumber: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
            }
        }
        dialog.show()
    }

    private fun requestChatStorageConsentIfNeeded() {
        if (knowledgeStore.getSetting(KEY_CHAT_STORAGE_CONSENT) != null) return
        AlertDialog.Builder(this)
            .setTitle("Izin penyimpanan percakapan")
            .setMessage("Tata dapat menyimpan riwayat percakapan di database lokal HP agar percakapan dapat ditinjau. Riwayat tidak dikirim ke server. Apakah Anda mengizinkan penyimpanan ini?")
            .setPositiveButton("Izinkan simpan") { _, _ ->
                knowledgeStore.setSetting(KEY_CHAT_STORAGE_CONSENT, "allow")
                Toast.makeText(this, "Penyimpanan riwayat percakapan diizinkan.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Jangan simpan") { _, _ ->
                knowledgeStore.setSetting(KEY_CHAT_STORAGE_CONSENT, "deny")
                Toast.makeText(this, "Riwayat percakapan tidak akan disimpan.", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun showDataPrivacySettings() {
        val allowed = knowledgeStore.getSetting(KEY_CHAT_STORAGE_CONSENT) == "allow"
        val options = arrayOf(
            if (allowed) "Matikan penyimpanan riwayat percakapan" else "Izinkan penyimpanan riwayat percakapan",
            "Hapus seluruh riwayat percakapan tersimpan",
            "Hapus seluruh pengetahuan yang dipelajari"
        )
        AlertDialog.Builder(this)
            .setTitle("Privasi dan data Tata")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val newValue = if (allowed) "deny" else "allow"
                        knowledgeStore.setSetting(KEY_CHAT_STORAGE_CONSENT, newValue)
                        Toast.makeText(
                            this,
                            if (newValue == "allow") "Penyimpanan riwayat diizinkan." else "Penyimpanan riwayat dimatikan.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    1 -> AlertDialog.Builder(this)
                        .setTitle("Hapus riwayat percakapan?")
                        .setMessage("Seluruh riwayat percakapan yang tersimpan di HP ini akan dihapus. Pengetahuan Tata tidak ikut dihapus.")
                        .setPositiveButton("Ya, hapus") { _, _ ->
                            knowledgeStore.clearChat()
                            Toast.makeText(this, "Riwayat percakapan dihapus.", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                    2 -> showClearKnowledgeConfirmation()
                }
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun showClearKnowledgeConfirmation() {
        val count = knowledgeStore.getKnowledgeCount()
        val message = if (count == 0) {
            "Saat ini belum ada pengetahuan tersimpan yang perlu dihapus."
        } else {
            "Tata akan menghapus $count data pengetahuan yang tersimpan. Riwayat percakapan, pengaturan aplikasi, dan konfigurasi sumber berita tidak ikut dihapus. Tindakan ini tidak dapat dibatalkan."
        }
        if (ttsReady) textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "tata-confirm-clear-knowledge")
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

    private fun appendLog(speaker: String, message: String, persist: Boolean = true) {
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
        val messageColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        messageColumn.addView(bubble, LinearLayout.LayoutParams(-1, -2))
        if (isUser) lastUserQuestionForCorrection = message
        if (speaker == "Tata AI" && lastUserQuestionForCorrection.isNotBlank()) {
            val correctionButton = Button(this).apply {
                text = "Koreksi jawaban Tata"
                isAllCaps = false
                textSize = 12f
                setOnClickListener {
                    showLearningRecordEditor(lastUserQuestionForCorrection, message)
                }
            }
            messageColumn.addView(correctionButton, LinearLayout.LayoutParams(-1, dp(40)))
        }
        row.addView(messageColumn, LinearLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.78f).toInt(), -2
        ))
        logView.addView(row)
        if (persist && ::knowledgeStore.isInitialized && knowledgeStore.getSetting(KEY_CHAT_STORAGE_CONSENT) == "allow") {
            knowledgeStore.saveChat(speaker, message)
        }
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
            else {
                restorePreferredVoice()
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        conversationHandler.post {
                            if (!activityDestroyed && ::listenButton.isInitialized) {
                                listenButton.setSpeaking(true)
                                if (::conversationStatusView.isInitialized) conversationStatusView.text = "TATA SEDANG BERBICARA..."
                            }
                        }
                    }
                    override fun onDone(utteranceId: String?) {
                        conversationHandler.post {
                            if (::listenButton.isInitialized) listenButton.setSpeaking(false)
                            if (conversationModeActive) startListening()
                            else if (::conversationStatusView.isInitialized) conversationStatusView.text = "SIAP"
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        conversationHandler.post {
                            if (::listenButton.isInitialized) listenButton.setSpeaking(false)
                            if (utteranceId?.startsWith("tata-conversation-") == true &&
                                selectedVoiceNetworkMode == true && !activityDestroyed
                            ) {
                                val retryText = lastSpeechText
                                applyVoiceForConnection(false)
                                if (!retryText.isNullOrBlank()) {
                                    textToSpeech.speak(
                                        retryText,
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        "tata-offline-retry-" + System.nanoTime()
                                    )
                                    return@post
                                }
                            }
                            if (conversationModeActive && !activityDestroyed) startListening()
                        }
                    }
                })
            }        } else {
            appendLog("Sistem", "Text-to-Speech gagal diinisialisasi.")
        }
    }

    override fun onDestroy() {
        activityDestroyed = true
        conversationModeActive = false
        conversationHandler.removeCallbacksAndMessages(null)
        if (recognizerReady) {
            runCatching { speechRecognizer.cancel() }
            runCatching { speechRecognizer.destroy() }
            recognizerReady = false
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
        private const val REQUEST_IMPORT_MODEL = 304
        private const val REQUEST_EXPORT_LEARNING = 305
        private const val REQUEST_IMPORT_LEARNING = 306
        private const val KEY_CHAT_STORAGE_CONSENT = "chat_history_consent"
        private const val PREFS_VOICE = "tata_voice_preferences"
private const val KEY_VOICE_NAME = "voice_name"
    }
}
