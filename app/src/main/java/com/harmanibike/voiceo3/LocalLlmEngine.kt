package com.harmanibike.voiceo3

/**
 * JNI facade for the optional on-device language model.
 * Calls must run on Dispatchers.IO. No prompt or model data is sent over a network.
 */
class LocalLlmEngine {
    @Volatile
    private var loaded = false

    val isAvailable: Boolean
        get() = nativeLibraryAvailable

    val isLoaded: Boolean
        get() = loaded && runCatching { nativeIsLoaded() }.getOrDefault(false)

    fun loadModel(path: String, contextSize: Int = 1024, threads: Int = 2): String {
        check(nativeLibraryAvailable) { "Mesin AI lokal belum tersedia pada APK ini." }
        val result = nativeLoadModel(path, contextSize.coerceIn(512, 2048), threads.coerceIn(1, 4))
        loaded = result == "OK"
        return result
    }

    fun generate(prompt: String, maxTokens: Int = 160): String? {
        if (!isLoaded || prompt.isBlank()) return null
        return nativeGenerate(prompt.take(6000), maxTokens.coerceIn(16, 256))
    }

    fun cancel() {
        if (nativeLibraryAvailable) runCatching { nativeCancel() }
    }

    fun unload() {
        if (nativeLibraryAvailable) runCatching { nativeUnload() }
        loaded = false
    }

    companion object {
        private val nativeLibraryAvailable: Boolean = runCatching {
            System.loadLibrary("tata_llm")
            true
        }.getOrDefault(false)
    }

    private external fun nativeLoadModel(path: String, contextSize: Int, threads: Int): String
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String?
    private external fun nativeIsLoaded(): Boolean
    private external fun nativeCancel()
    private external fun nativeUnload()
}
