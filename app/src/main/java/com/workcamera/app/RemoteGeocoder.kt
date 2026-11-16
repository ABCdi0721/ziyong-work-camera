package com.workcamera.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * 系统逆地理编码没有给出街道/小区级信息时的在线兜底（Photon / OpenStreetMap 数据）。
 * 无需密钥，返回中文地名；失败时静默返回 null，不影响拍照。
 */
object RemoteGeocoder {

    private const val ENDPOINT = "https://photon.komoot.io/reverse"

    fun lookup(latitude: Double, longitude: Double, timeoutMs: Int = 5000): String? {
        val request = String.format(
            Locale.US,
            "%s?lat=%.6f&lon=%.6f&limit=8",
            ENDPOINT, latitude, longitude
        )
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(request).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                setRequestProperty("User-Agent", "WorkCamera/1.0 (Android)")
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parse(body)
        } catch (e: Exception) {
            null
        } finally {
            try {
                connection?.disconnect()
            } catch (e: Exception) {
            }
        }
    }

    private fun parse(body: String): String? {
        val features = JSONObject(body).optJSONArray("features") ?: return null
        var best: String? = null
        var bestScore = Int.MIN_VALUE

        for (index in 0 until minOf(features.length(), 8)) {
            val properties = features.optJSONObject(index)?.optJSONObject("properties") ?: continue
            val area = firstNonBlank(
                properties.optString("county"),
                properties.optString("district"),
                properties.optString("city"),
                properties.optString("state")
            )
            val name = properties.optString("name").trim()
            val street = properties.optString("street").trim()
            val number = properties.optString("housenumber").trim()
            val type = properties.optString("type").trim()

            val feature = if (name.isEmpty() || name == area) null else name
            val composed = AddressFormatter.compose(area, feature, street, number) ?: continue

            var score = AddressFormatter.score(composed)
            if (type == "house") score += 10
            if (type == "locality" || type == "district") score += 4
            if (area != null && composed == area) score -= 20
            if (name.isNotEmpty() && name.any { it.isDigit() } && street.isEmpty()) score -= 6

            if (score > bestScore) {
                best = composed
                bestScore = score
            }
        }
        return best
    }

    private fun firstNonBlank(vararg values: String): String? {
        for (value in values) {
            val text = value.trim()
            if (text.isNotEmpty()) return text
        }
        return null
    }
}
