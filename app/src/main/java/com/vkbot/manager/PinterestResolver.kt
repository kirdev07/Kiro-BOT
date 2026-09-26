package com.vkbot.manager

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Ссылка на пин Pinterest (pinterest.com/pin/…, pin.it/…) → прямая ссылка на картинку i.pinimg.com.
 * 1) по номеру пина — лёгкий ответ сервиса виджетов (~1 КБ);
 * 2) если не вышло — картинка из og:image страницы пина (~1 МБ, работает всегда).
 */
class PinterestResolver(
    private val widgetsBase: String = "https://widgets.pinterest.com",
    private val extraHosts: List<String> = emptyList()
) {

    fun isPinterest(url: String): Boolean {
        val host = hostOf(url)
        return host == "pin.it" || host.contains("pinterest.") || host in extraHosts
    }

    /** @return прямая ссылка на картинку или null, если это не пин / картинку найти не удалось. */
    fun resolveImageUrl(url: String): String? {
        if (!isPinterest(url)) return null
        val pinUrl = if (hostOf(url) == "pin.it") expandShortLink(url) ?: return null else url
        val pinId = PIN_ID.find(pinUrl)?.groupValues?.get(1)
        pinId?.let { imageFromWidgets(it) }?.let { return it }
        return imageFromPage(pinUrl)
    }

    private fun imageFromWidgets(pinId: String): String? = try {
        val body = get("$widgetsBase/v3/pidgets/pins/info/?pin_ids=${URLEncoder.encode(pinId, "UTF-8")}", MAX_JSON_BYTES)
        val images = JSONObject(body).optJSONArray("data")?.optJSONObject(0)?.optJSONObject("images")
        // Самый большой из предложенных размеров; 564x → 736x (есть почти всегда и чётче)
        val best = images?.keys()?.asSequence()
            ?.mapNotNull { images.optJSONObject(it) }
            ?.maxByOrNull { it.optInt("width") }
            ?.optString("url")
        best?.takeIf { it.isNotEmpty() }?.let { upgradeSize(it) }
    } catch (_: Exception) {
        null
    }

    private fun imageFromPage(pinUrl: String): String? = try {
        val html = get(pinUrl, MAX_PAGE_BYTES)
        OG_IMAGE.find(html)?.groupValues?.let { it[1].ifEmpty { it[2] } }?.takeIf { it.startsWith("http") }
    } catch (_: Exception) {
        null
    }

    private fun upgradeSize(url: String): String {
        if (!url.contains("/564x/")) return url
        val bigger = url.replace("/564x/", "/736x/")
        return if (exists(bigger)) bigger else url
    }

    private fun exists(url: String): Boolean = try {
        val c = open(url)
        c.requestMethod = "HEAD"
        try { c.responseCode in 200..299 } finally { c.disconnect() }
    } catch (_: Exception) {
        false
    }

    /** pin.it/abc → итоговый адрес после редиректов (pinterest.com/pin/123/…). */
    private fun expandShortLink(url: String): String? = try {
        val c = open(url)
        try {
            c.responseCode
            c.url.toString().takeIf { isPinterest(it) && hostOf(it) != "pin.it" }
        } finally {
            c.disconnect()
        }
    } catch (_: Exception) {
        null
    }

    private fun get(url: String, maxBytes: Int): String {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            val out = ByteArrayOutputStream()
            c.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (out.size() < maxBytes) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
            }
            return out.toString("UTF-8")
        } finally {
            c.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) KiroBot")
        }

    private fun hostOf(url: String) =
        url.substringAfter("://").substringBefore("/").substringBefore("?").substringBefore(":").lowercase()

    companion object {
        private const val TIMEOUT_MS = 15_000
        private const val MAX_JSON_BYTES = 256 * 1024
        private const val MAX_PAGE_BYTES = 3 * 1024 * 1024
        /** /pin/123 или /pin/anime-wallpaper--123 */
        private val PIN_ID = Regex("/pin/(?:[^/?#]*--)?(\\d+)")
        /** og:image в любом порядке атрибутов content/property */
        private val OG_IMAGE = Regex(
            "<meta[^>]*content=\"([^\"]+)\"[^>]*property=\"og:image\"|<meta[^>]*property=\"og:image\"[^>]*content=\"([^\"]+)\""
        )
    }
}
