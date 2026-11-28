package com.harmanibike.voiceo3

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.net.Uri
import android.provider.Settings
import android.os.BatteryManager
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
    private val offlineConversation = OfflineConversationEngine()

    suspend fun handle(rawCommand: String): String = withContext(Dispatchers.Main) {
        val command = rawCommand.lowercase(Locale("id", "ID")).trim()
        when {
            command.contains("hapus semua pengetahuan") ||
                command.contains("hapus seluruh pengetahuan") ||
                command.contains("hapus semua yang kamu pelajari") ||
                command.contains("hapus semua yang pernah kamu pelajari") ||
                command.contains("hapus semua data yang kamu pelajari") ||
                command.contains("hapus seluruh data yang kamu pelajari") ||
                command.contains("hapus semua yang pernah dipelajari") ||
                command.contains("hapus seluruh yang pernah dipelajari") ||
                command.contains("hapus semua data yang pernah dipelajari") ||
                command.contains("kosongkan seluruh pengetahuan") ->
                CONFIRM_CLEAR_KNOWLEDGE
            command == "bantuan" || command == "fitur" || command == "apa yang bisa kamu lakukan" ->
                "Saya bisa memberi tahu jam dan tanggal, mengecek baterai, membuka WhatsApp, YouTube, kamera, Pengaturan, Telepon, peta, browser, Galeri, dan catatan. Saya juga bisa membaca berita terbaru hari ini dari sumber berita online. Mode ngobrol AI yang lebih luas sedang disiapkan."
            command in listOf("halo", "hai", "hello", "assalamualaikum", "selamat pagi", "selamat siang", "selamat sore", "selamat malam") ->
                "Halo! Saya Tata, asisten suara Anda. Ada yang bisa saya bantu?"
            command.contains("siapa nama kamu") || command.contains("siapa namamu") ||
                command.contains("nama kamu") || command.contains("siapa kamu") ->
                "Nama saya Tata, asisten suara Tata AI."
            command.contains("cek baterai") || command.contains("berapa baterai") ||
                command.contains("persen baterai") ->
                getBatteryStatus()
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
            command.contains("buka pengaturan") || command.contains("buka setelan") ->
                launchIntent(Intent(Settings.ACTION_SETTINGS), "Pengaturan")
            command.contains("buka telepon") || command.contains("buka dial") ->
                launchIntent(Intent(Intent.ACTION_DIAL), "Telepon")
            command.contains("buka maps") || command.contains("buka peta") ->
                launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")), "Aplikasi peta")
            command.contains("buka browser") || command.contains("buka internet") ->
                launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")), "Browser")
            command.contains("buka galeri") || command.contains("pilih foto") ->
                launchIntent(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), "Galeri")
            command.contains("buka catatan") || command.contains("buat catatan") ->
                launchIntent(Intent("android.intent.action.CREATE_NOTE").apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, "Catatan Tata")
                }, "aplikasi catatan")
            else -> {
                val localAnswer = knowledgeStore.search(command)
                if (localAnswer != null) localAnswer
                else if (command.contains("berita") || command.contains("bacakan kabar") ||
                    command.contains("kabar hari ini") || command.contains("headline")) {
                    networkHelper.getTodayNews()
                } else {
                    offlineConversation.respond(rawCommand)
                        ?: offlineConversation.unknownResponse()
                }
            }
        }
    }

    companion object {
        const val CONFIRM_CLEAR_KNOWLEDGE = "__TATA_CONFIRM_CLEAR_KNOWLEDGE__"
    }

    private fun getBatteryStatus(): String {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (level in 0..100) "Baterai perangkat tersisa $level persen."
        else "Informasi baterai tidak tersedia."
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
