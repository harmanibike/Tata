package com.harmanibike.voiceo3

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
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
    private lateinit var logView: TextView
    private lateinit var listenButton: Button
    private var ttsReady = false
    private var recognizerReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(22, 134, 91)
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        router = CommandRouter(this, KnowledgeStore(this), NetworkHelper(this))
        textToSpeech = TextToSpeech(this, this)
        buildInterface()
        requestAudioPermission()
        setupSpeechRecognizer()
        appendLog("Tata", "Halo, saya Tata. Tekan Bicara untuk memberi perintah. Ucapkan bantuan untuk melihat kemampuan saya.")
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(248, 250, 249))
            setPadding(dp(18), dp(18), dp(18), dp(14))
        }
        val title = TextView(this).apply {
            text = "Tata AI"
            textSize = 26f
            setTextColor(Color.rgb(25, 55, 42))
            gravity = Gravity.CENTER_VERTICAL
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(title, LinearLayout.LayoutParams(-1, dp(48)))

        val subtitle = TextView(this).apply {
            text = "Tata siap membantu • perintah suara lokal dan online"
            textSize = 13f
            setTextColor(Color.rgb(100, 115, 106))
        }
        root.addView(subtitle, LinearLayout.LayoutParams(-1, dp(30)))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.WHITE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        logView = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(35, 45, 39))
            setLineSpacing(dp(4).toFloat(), 1f)
        }
        scroll.addView(logView)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        listenButton = Button(this).apply {
            text = "🎙  BICARA"
            textSize = 16f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(22, 134, 91))
            setOnClickListener { startListening() }
        }
        val buttonParams = LinearLayout.LayoutParams(-1, dp(56))
        buttonParams.topMargin = dp(14)
        root.addView(listenButton, buttonParams)

        setContentView(root)
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
            appendLog("Tata AI", answer)
            if (ttsReady) textToSpeech.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "tata-ai-response")
        }
    }

    private fun appendLog(speaker: String, message: String) {
        if (!::logView.isInitialized) return
        logView.append("$speaker:\n$message\n\n")
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
    }
}
