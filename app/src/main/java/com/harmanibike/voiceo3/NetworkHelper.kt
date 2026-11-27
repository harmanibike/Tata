package com.harmanibike.voiceo3

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class NetworkHelper(context: Context, private val knowledgeStore: KnowledgeStore) {
    private val appContext = context.applicationContext

    private data class NewsItem(
        val title: String,
        val description: String,
        val link: String,
        val source: String,
        val publishedAt: Date
    )

    fun hasInternetConnection(): Boolean {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun getTodayNews(): String = withContext(Dispatchers.IO) {
        if (!hasInternetConnection()) return@withContext "Internet tidak tersedia. Hubungkan HP ke internet untuk mengambil berita hari ini."

        val feeds = knowledgeStore.getEnabledNewsSources()
            .filter { (name, feedUrl) ->
                name.isNotBlank() && (feedUrl.startsWith("https://") || feedUrl.startsWith("http://"))
            }
        if (feeds.isEmpty()) {
            return@withContext "Belum ada sumber berita aktif di database Tata. Aktifkan sumber berita terlebih dahulu."
        }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        val allNews = mutableListOf<NewsItem>()
        val errors = mutableListOf<String>()

        feeds.forEach { (feedUrl, source) ->
            try {
                allNews += readFeed(feedUrl, source).filter { item ->
                    SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(item.publishedAt) == today
                }
            } catch (error: Exception) {
                errors += source
            }
        }

        val latest = allNews.distinctBy { it.link }.sortedByDescending { it.publishedAt }.take(5)
        if (latest.isEmpty()) {
            return@withContext if (errors.size == feeds.size) {
                "Maaf, semua sumber berita sedang tidak dapat diakses. Coba lagi beberapa saat."
            } else {
                "Belum ditemukan berita bertanggal hari ini dari sumber yang tersedia. Coba perbarui beberapa saat lagi."
            }
        }

        val timeFormat = SimpleDateFormat("HH:mm", Locale("id", "ID"))
        buildString {
            append("Berita hari ini, ")
            append(SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(Date()))
            append(". Ditemukan ")
            append(latest.size)
            append(" berita terbaru.\\n\\n")
            latest.forEachIndexed { index, item ->
                append(index + 1).append(". ").append(item.title).append("\\n")
                if (item.description.isNotBlank()) {
                    append(item.description.take(260)).append(if (item.description.length > 260) "…" else "").append("\\n")
                }
                append("Sumber: ").append(item.source)
                    .append(" • ").append(timeFormat.format(item.publishedAt)).append(" WIB\\n")
                append("Tautan: ").append(item.link)
                if (index != latest.lastIndex) append("\\n\\n")
            }
        }
    }

    private fun readFeed(feedUrl: String, source: String): List<NewsItem> {
        val connection = (URL(feedUrl).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("User-Agent", "TataAI/2.0 (Android news reader)")
            connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
            val status = connection.responseCode
            if (status !in 200..299) error("RSS HTTP $status")
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            return connection.inputStream.use { input ->
                parser.setInput(input, null)
                val result = mutableListOf<NewsItem>()
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name.equals("item", true)) {
                        parseItem(parser, source)?.let(result::add)
                    }
                    event = parser.next()
                }
                result
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseItem(parser: XmlPullParser, source: String): NewsItem? {
        var title = ""
        var description = ""
        var link = ""
        var published = ""
        val depth = parser.depth
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_TAG && parser.depth == depth && parser.name.equals("item", true)) break
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.lowercase(Locale.ROOT)) {
                    "title" -> title = parser.nextText().trim()
                    "link" -> link = parser.nextText().trim()
                    "description", "summary" -> description = parser.nextText().trim()
                    "pubdate", "published", "updated" -> if (published.isBlank()) published = parser.nextText().trim()
                }
            }
        }
        val date = parsePublishedDate(published) ?: return null
        val cleanDescription = Html.fromHtml(description, Html.FROM_HTML_MODE_COMPACT)
            .toString().replace(Regex("\\s+"), " ").trim()
        if (title.isBlank() || link.isBlank()) return null
        return NewsItem(title, cleanDescription, link, source, date)
    }

    private fun parsePublishedDate(value: String): Date? {
        if (value.isBlank()) return null
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, d MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = true
                    timeZone = TimeZone.getTimeZone("GMT")
                }.parse(value)
            }.getOrNull()
        }
    }
}
