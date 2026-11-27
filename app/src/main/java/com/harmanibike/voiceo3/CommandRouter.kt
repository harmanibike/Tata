package com.harmanibike.voiceo3

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.speech.RecognizerIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommandRouter(
    private val context: Context,
    private val knowledgeStore: KnowledgeStore,
    private val networkHelper: NetworkHelper
) {
    suspend fun handle(rawCommand: String): String = withContext(Dispatchers.Main) {
        val command = rawCommand.lowercase(Locale("id", "ID")).trim()
        when {
            command.contains("jam berapa") || command == "jam" ->
                "Sekarang pukul " + SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date())
            command.contains("tanggal berapa") || command == "tanggal" || command.contains("hari ini tanggal") ->
                "Hari ini " + SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(Date())
            command.contains("buka whatsapp") || command.contains("buka wa") ->
                launchPackage("com.whatsapp", "WhatsApp")
            command.contains("buka youtube") ->
                launchPackage("com.google.android.youtube", "YouTube")
            command.contains("buka kamera") || command.contains("ambil foto") ->
                launchIntent(Intent(MediaStore.ACTION_IMAGE_CAPTURE), "Kamera")
            command.contains("buka catatan") || command.contains("buat catatan") ->
                launchIntent(Intent("android.intent.action.CREATE_NOTE").apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, "Catatan Tata")
                }, "aplikasi catatan")
            else -> {
                val localAnswer = knowledgeStore.search(command)
                if (localAnswer != null) localAnswer
                else if (command.contains("harga emas") || command.contains("harga paxg") ||
                    command.contains("paxg") || command.contains("harga gold")) {
                    networkHelper.getPaxgUsdtPrice()
                } else {
                    "Maaf, perintah belum ada di pengetahuan lokal. Coba tanyakan alamat Harmani Bike, jam, tanggal, atau harga emas PAXG saat online."
                }
            }
        }
    }

    private fun launchPackage(packageName: String, label: String): String {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return "$label belum terpasang di perangkat."
        return launchIntent(intent, label, "$label dibuka.")
    }

    private fun launchIntent(intent: Intent, label: String, success: String = "$label dibuka."): String {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            success
        } catch (_: ActivityNotFoundException) {
            "Tidak menemukan aplikasi untuk membuka $label."
        } catch (_: Exception) {
            "Tidak dapat membuka $label."
        }
    }
}
