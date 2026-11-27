package com.harmanibike.voiceo3

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class NetworkHelper(context: Context) {
    private val appContext = context.applicationContext

    fun hasInternetConnection(): Boolean {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun getPaxgUsdtPrice(): String = withContext(Dispatchers.IO) {
        if (!hasInternetConnection()) return@withContext "Internet tidak tersedia."
        val connection = (URL("https://api.binance.com/api/v3/ticker/price?symbol=PAXGUSDT")
            .openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) return@withContext "Gagal mengambil harga emas (HTTP $status)."
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val price = JSONObject(body).optString("price")
            val number = price.toDoubleOrNull()
                ?: return@withContext "Data harga emas tidak valid."
            "Harga PAXG/USDT saat ini: " + String.format(Locale.US, "%,.2f", number) + " USDT per PAXG."
        } catch (error: Exception) {
            "Tidak bisa mengambil harga emas: ${error.localizedMessage ?: "koneksi bermasalah"}."
        } finally {
            connection.disconnect()
        }
    }
}
