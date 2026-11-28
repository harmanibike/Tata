package com.harmanibike.voiceo3

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

data class LocalModelFile(
    val name: String,
    val file: File,
    val sizeBytes: Long
)

class LocalModelStore(context: Context) {
    private val appContext = context.applicationContext
    private val modelDirectory = File(appContext.filesDir, "local_models").apply {
        if (!exists()) mkdirs()
    }

    fun listModels(): List<LocalModelFile> = modelDirectory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) }
        .sortedByDescending { it.lastModified() }
        .map { LocalModelFile(it.name, it, it.length()) }

    fun importFromUri(uri: Uri): LocalModelFile {
        val resolver = appContext.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            ?.takeIf { it.isNotBlank() }
            ?: "model.gguf"
        val safeBaseName = displayName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(100)
            .ifBlank { "model.gguf" }
        val finalName = if (safeBaseName.endsWith(".gguf", ignoreCase = true)) safeBaseName else "$safeBaseName.gguf"
        val uniqueName = if (File(modelDirectory, finalName).exists()) {
            "${finalName.removeSuffix(".gguf")}_${System.currentTimeMillis()}.gguf"
        } else finalName
        val destination = File(modelDirectory, uniqueName)
        val temporary = File(modelDirectory, "$uniqueName.importing")

        try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temporary).use { output ->
                    val header = ByteArray(4)
                    var offset = 0
                    while (offset < header.size) {
                        val count = input.read(header, offset, header.size - offset)
                        if (count < 0) throw IllegalArgumentException("File model terlalu pendek atau rusak.")
                        offset += count
                    }
                    require(isGgufHeader(header)) { "Format tidak dikenali. Tata hanya menerima model GGUF." }
                    output.write(header)
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            } ?: throw IllegalArgumentException("File tidak dapat dibaca.")
            if (!temporary.renameTo(destination)) {
                throw IllegalStateException("Tidak dapat menyimpan model ke penyimpanan aplikasi.")
            }
        } catch (error: Exception) {
            temporary.delete()
            destination.delete()
            throw error
        }

        return LocalModelFile(destination.name, destination, destination.length())
    }

    fun deleteModel(model: LocalModelFile): Boolean {
        val candidate = model.file.canonicalFile
        val allowedDirectory = modelDirectory.canonicalFile
        require(candidate.parentFile == allowedDirectory) { "Lokasi file model tidak valid." }
        require(candidate.extension.equals("gguf", ignoreCase = true)) { "Jenis file tidak valid." }
        return candidate.delete()
    }

    companion object {
        fun isGgufHeader(header: ByteArray): Boolean =
            header.size >= 4 &&
                header[0] == 'G'.code.toByte() &&
                header[1] == 'G'.code.toByte() &&
                header[2] == 'U'.code.toByte() &&
                header[3] == 'F'.code.toByte()
    }
}
