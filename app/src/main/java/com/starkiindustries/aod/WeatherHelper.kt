package com.starkiindustries.aod

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

data class WeatherData(val tempC: Int, val condition: String)

object WeatherHelper {

    // wttr.in provides free, no-key-required weather via IP-based location.
    private const val URL = "https://wttr.in/?format=j1"

    suspend fun fetch(): WeatherData? = withContext(Dispatchers.IO) {
        try {
            val json = URL(URL).readText()
            // Pull first temp_C and first weatherDesc value
            val temp = json
                .substringAfter("\"temp_C\":\"", "")
                .substringBefore("\"")
                .toIntOrNull() ?: return@withContext null
            val desc = json
                .substringAfter("\"weatherDesc\":[{\"value\":\"", "")
                .substringBefore("\"")
                .ifBlank { "Clear" }
            WeatherData(temp, desc)
        } catch (_: Exception) {
            null
        }
    }
}
