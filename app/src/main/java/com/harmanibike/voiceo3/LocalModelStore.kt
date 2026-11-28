package com.harmanibike.voiceo3

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.os.StatFs
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

    fun availableBytes(): Long = runCatching {
        StatFs(modelDirectory.absolutePath).availableBytes
    }.getOrDefault(0L)

    fun importFromUri(uri: Uri): LocalModelFile {
        val resolver = appContext.contentResolver
        var reportedSize: Long? = null
        val displayName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) reportedSize = cursor.getLong(sizeIndex)
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else null
        }?.takeIf { it.isNotBlank() } ?: "model.gguf"

        val freeBytes = availableBytes()
        val importLimit = safeImportLimit(freeBytes)
        require(importLimit > 0L) {
            "Ruang penyimpanan hampir habis. Sisakan minimal 256 MB sebelum mengimpor model."
        }
        if (reportedSize != null && reportedSize!! > 0L) {
            require(reportedSize!! <= importLimit) {
                "Ukuran model ${reportedSize!! / (1024 * 1024)} MB melebihi ruang aman yang tersedia."
            }
        }
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
                    var copiedBytes = header.size.toLong()
                    require(copiedBytes <= importLimit) { "Ukuran model melebihi ruang penyimpanan yang aman." }
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copiedBytes += count
                        require(copiedBytes <= importLimit) {
                            "Impor dihentikan agar tersisa minimal 256 MB ruang penyimpanan."
                        }
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
        private const val MINIMUM_FREE_SPACE_BYTES = 256L * 1024L * 1024L

        fun safeImportLimit(freeBytes: Long): Long =
            (freeBytes - MINIMUM_FREE_SPACE_BYTES).coerceAtLeast(0L)

        fun isGgufHeader(header: ByteArray): Boolean =
            header.size >= 4 &&
                header[0] == 'G'.code.toByte() &&
                header[1] == 'G'.code.toByte() &&
                header[2] == 'U'.code.toByte() &&
                header[3] == 'F'.code.toByte()
    }
}
