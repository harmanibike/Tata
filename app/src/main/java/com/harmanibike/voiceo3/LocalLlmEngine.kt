package com.harmanibike.voiceo3

/**
 * JNI facade for the optional on-device language model.
 * Calls must run on Dispatchers.IO. No prompt or model data is sent over a network.
 */
class LocalLlmEngine {
    @Volatile
    private var loaded = false

    private val conversationContext = LocalConversationContext(maxTurns = 2, promptLimit = 1400)

    @Volatile
    var activeModelPath: String? = null
        private set

    val isAvailable: Boolean
        get() = nativeLibraryAvailable

    val isLoaded: Boolean
        get() = loaded

    fun loadModel(path: String, contextSize: Int = 1024, threads: Int = 2): String {
        check(nativeLibraryAvailable) { "Mesin AI lokal belum tersedia pada APK ini." }
        val result = nativeLoadModel(path, contextSize.coerceIn(512, 2048), threads.coerceIn(1, 4))
        loaded = result == "OK"
        activeModelPath = if (loaded) path else null
        return result
    }

    fun generate(prompt: String, maxTokens: Int = 160): String? {
        if (!isLoaded || prompt.isBlank()) return null
        val question = prompt.trim().take(700)
        val contextualPrompt = conversationContext.buildPrompt(question)
        val answer = nativeGenerate(contextualPrompt.take(1400), maxTokens.coerceIn(32, 220))
        if (!answer.isNullOrBlank() &&
            !answer.startsWith("Pertanyaan terlalu panjang") &&
            !answer.startsWith("Model belum") &&
            !answer.startsWith("Model gagal") &&
            !answer.startsWith("Generasi dihentikan") &&
            !answer.startsWith("Prompt tidak") &&
            !answer.startsWith("Tokenisasi")) {
            conversationContext.remember(question, answer)
        }
        return answer
    }

    fun cancel() {
        if (nativeLibraryAvailable) runCatching { nativeCancel() }
    }

    fun unload() {
        // Mark unavailable before waiting for native generation to finish.
        loaded = false
        activeModelPath = null
        conversationContext.clear()
        if (nativeLibraryAvailable) runCatching { nativeUnload() }
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
