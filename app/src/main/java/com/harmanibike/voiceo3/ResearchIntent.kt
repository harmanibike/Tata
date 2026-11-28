package com.harmanibike.voiceo3

import java.util.Locale

/** Detects deliberate user requests to search the public web. */
object ResearchIntent {
    private val prefix = Regex(
        "(?i)^\\s*(?:tolong\\s+)?(?:cari|telusuri|search)\\s+(?:di\\s+)?(?:internet|web)\\s*(?:(?:tentang|mengenai)\\s*)?"
    )

    fun isExplicitWebSearch(rawText: String): Boolean = prefix.containsMatchIn(rawText)

    fun cleanQuery(rawText: String): String =
        rawText.replaceFirst(prefix, "")
            .trim()
            .ifBlank { rawText.trim() }
}
