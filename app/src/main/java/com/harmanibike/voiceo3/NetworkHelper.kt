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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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


    /**
     * Pencarian web langsung tanpa API AI atau API key.
     * Mengambil hasil DuckDuckGo HTML, lalu membaca maksimal tiga halaman publik.
     */
    suspend fun searchWeb(query: String): String? = withContext(Dispatchers.IO) {
        if (!hasInternetConnection()) return@withContext null
        val cleanQuery = query.trim().take(300)
        if (cleanQuery.isBlank()) return@withContext null

        try {
            val searchDoc = Jsoup.connect("https://html.duckduckgo.com/html/")
                .data("q", cleanQuery)
                .userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .timeout(12_000)
                .maxBodySize(1_500_000)
                .get()

            val results = searchDoc.select("a.result__a").mapNotNull { link ->
                val title = link.text().trim()
                val href = link.absUrl("href").ifBlank { link.attr("href") }
                val resolved = resolveSearchUrl(href)
                val snippet = link.parent()?.parent()?.selectFirst(".result__snippet")?.text()?.trim().orEmpty()
                if (title.isBlank() || !resolved.startsWith("https://")) null
                else WebResult(title, resolved, snippet)
            }.distinctBy { it.url }.take(4)

            if (results.isEmpty()) return@withContext "Saya belum menemukan hasil web yang dapat dibaca untuk: $cleanQuery. Coba gunakan kata kunci yang lebih spesifik."

            val pages = results.take(3).map { result ->
                val body = runCatching { readWebPage(result.url) }.getOrDefault("")
                result.copy(content = body)
            }
            val usable = pages.filter { it.snippet.isNotBlank() || it.content.isNotBlank() }
            if (usable.isEmpty()) {
                return@withContext buildString {
                    append("Saya menemukan hasil pencarian, tetapi halaman sumber tidak dapat dibaca otomatis.\n\n")
                    results.forEachIndexed { i, item ->
                        append(i + 1).append(". ").append(item.title).append("\n").append(item.url).append("\n")
                    }
                }
            }

            buildString {
                append("Hasil pencarian web untuk: ").append(cleanQuery).append("\n")
                append("Diambil langsung dari web; periksa tanggal dan konteks sumber sebelum mengambil keputusan.\n\n")
                usable.forEachIndexed { i, item ->
                    append(i + 1).append(". ").append(item.title).append("\n")
                    if (item.snippet.isNotBlank()) append("Cuplikan: ").append(item.snippet.take(350)).append("\n")
                    if (item.content.isNotBlank()) append("Isi halaman: ").append(item.content.take(1400)).append("\n")
                    append("Sumber: ").append(item.url)
                    if (i != usable.lastIndex) append("\n\n")
                }
            }.take(6000)
        } catch (_: Exception) {
            null
        }
    }

    private data class WebResult(
        val title: String,
        val url: String,
        val snippet: String,
        val content: String = ""
    )

    private fun resolveSearchUrl(href: String): String {
        if (href.startsWith("https://") && !href.contains("duckduckgo.com/l/")) return href
        val query = runCatching { android.net.Uri.parse(href).getQueryParameter("uddg") }.getOrNull()
        return query.orEmpty()
    }

    private fun readWebPage(pageUrl: String): String {
        val doc: Document = Jsoup.connect(pageUrl)
            .userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .timeout(10_000)
            .maxBodySize(1_000_000)
            .followRedirects(true)
            .get()
        doc.select("script, style, nav, footer, header, aside, form, noscript, iframe, svg").remove()
        val main = doc.selectFirst("article, main, [role=main]") ?: doc.body()
        return main?.text()?.replace(Regex("\\s+"), " ")?.trim()?.take(1800).orEmpty()
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

        feeds.forEach { (source, feedUrl) ->
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
