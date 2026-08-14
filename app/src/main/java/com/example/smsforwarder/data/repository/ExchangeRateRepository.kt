package com.example.smsforwarder.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

interface ExchangeRateRepository {
    suspend fun getRateToKRW(currencyCode: String): Double
}

class ExchangeRateRepositoryImpl(context: Context) : ExchangeRateRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences("exchange_rates_cache", Context.MODE_PRIVATE)

    // Fallback default exchange rates (KRW per 1 unit), shared with AmountExtractor
    private val fallbackRates = com.example.smsforwarder.domain.parser.AmountExtractor.fallbackRatesKRW

    override suspend fun getRateToKRW(currencyCode: String): Double {
        val upperCode = currencyCode.uppercase()
        if (upperCode == "KRW" || upperCode == "원") return 1.0

        // 1. Try to fetch real-time rate from open exchange rate API
        val fetchedRate = fetchRealTimeRate(upperCode)
        if (fetchedRate != null && fetchedRate > 0) {
            saveCachedRate(upperCode, fetchedRate)
            return fetchedRate
        }

        // 2. Fallback to cached rate
        val cachedRate = getCachedRate(upperCode)
        if (cachedRate != null && cachedRate > 0) {
            Log.i("ExchangeRateRepo", "Using cached exchange rate for $upperCode: $cachedRate")
            return cachedRate
        }

        // 3. Fallback to default hardcoded rate
        val defaultRate = fallbackRates[upperCode] ?: 1350.0
        Log.w("ExchangeRateRepo", "Using fallback default exchange rate for $upperCode: $defaultRate")
        return defaultRate
    }

    private suspend fun fetchRealTimeRate(currencyCode: String): Double? = withContext(Dispatchers.IO) {
        try {
            // Free open exchange rate API endpoint
            val urlString = "https://open.er-api.com/v6/latest/$currencyCode"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                if (json.optString("result") == "success") {
                    val rates = json.optJSONObject("rates")
                    if (rates != null && rates.has("KRW")) {
                        return@withContext rates.getDouble("KRW")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("ExchangeRateRepo", "Failed to fetch real-time exchange rate for $currencyCode: ${e.message}")
        }
        return@withContext null
    }

    private fun getCachedRate(currencyCode: String): Double? {
        val key = "rate_$currencyCode"
        if (prefs.contains(key)) {
            val rate = prefs.getFloat(key, -1f).toDouble()
            if (rate > 0) return rate
        }
        return null
    }

    private fun saveCachedRate(currencyCode: String, rate: Double) {
        prefs.edit().putFloat("rate_$currencyCode", rate.toFloat()).apply()
    }
}
