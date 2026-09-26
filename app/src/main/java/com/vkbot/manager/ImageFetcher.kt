package com.vkbot.manager

import java.net.URL
import java.net.URLDecoder

/**
 * Ссылка → картинка, готовая к отправке в VK/Telegram.
 *
 * Понимает: прямые ссылки на картинки (тип — по содержимому, даже если сайт говорит «просто файл»),
 * страницы сайтов (берётся картинка-превью og:image / twitter:image), пины Pinterest,
 * ссылки из поиска картинок Яндекса/Google/Bing. Видео не скачивает — они уходят ссылкой.
 *
 * @param convert перекодирует форматы, которые VK/Telegram могут не принять (WebP, AVIF, HEIC…), в JPEG
 */
class ImageFetcher(
    private val pinterest: PinterestResolver = PinterestResolver(),
    private val convert: (DownloadedMedia) -> DownloadedMedia = { it }
) {
    /** Ссылка не картинка (страница без картинки, видео…) — отправлять ссылкой. */
    class NotAnImage(reason: String) : Exception(reason)

    /**
     * @throws NotAnImage если по ссылке нет картинки
     * @throws java.io.IOException при ошибке сети/сайта (причина — в message)
     */
    fun fetch(url: String): DownloadedMedia {
        // Видео — всегда ссылкой (иначе ушла бы картинка-обложка вместо ролика)
        if (isVideoSite(url)) throw NotAnImage("видео")
        val direct = pinterest.resolveImageUrl(url) ?: searchEngineImage(url) ?: url
        val first = MediaFiles.get(direct)
        imageOf(first)?.let { return convert(it) }

        if (first.headerType.startsWith("video/")) throw NotAnImage("это видео")
        if (!first.isHtml) throw NotAnImage("не картинка (${first.headerType.ifEmpty { "неизвестный тип" }})")
        if (isVideoPage(first)) throw NotAnImage("видео")

        val pageImage = pageImageUrl(first) ?: throw NotAnImage("на странице нет картинки")
        val second = MediaFiles.get(pageImage)
        return imageOf(second)?.let(convert) ?: throw NotAnImage("картинка со страницы не открылась")
    }

    private fun imageOf(result: HttpResult): DownloadedMedia? {
        val type = MediaFiles.sniffImageType(result.bytes)
            ?: result.headerType.takeIf { it.startsWith("image/") && !result.isHtml }
            ?: return null
        return DownloadedMedia(result.bytes, type)
    }

    companion object {
        /** Параметры, в которых поиск картинок передаёт адрес оригинала. */
        private val SEARCH_PARAMS = listOf("img_url", "imgurl", "mediaurl")

        private val META_IMAGE = listOf("og:image:secure_url", "og:image", "twitter:image", "twitter:image:src")

        private val VIDEO_HOSTS = listOf(
            "youtube.com", "youtu.be", "rutube.ru", "vkvideo.ru", "tiktok.com", "vimeo.com",
            "twitch.tv", "dzen.ru", "coub.com", "dailymotion.com"
        )

        fun isVideoSite(url: String): Boolean {
            val host = url.substringAfter("://").substringBefore("/").substringBefore("?").substringBefore(":").lowercase()
            return VIDEO_HOSTS.any { host == it || host.endsWith(".$it") }
        }

        /** Страница с роликом: og:type = video.* или есть og:video. */
        private fun isVideoPage(page: HttpResult): Boolean {
            val html = String(page.bytes, 0, minOf(page.bytes.size, 512 * 1024), Charsets.UTF_8)
            return Regex("<meta[^>]*(?:property|name)=[\"']og:(?:type[\"'][^>]*content=[\"']video|video[:\"'])", RegexOption.IGNORE_CASE).containsMatchIn(html) ||
                Regex("<meta[^>]*content=[\"']video[^\"']*[\"'][^>]*(?:property|name)=[\"']og:type[\"']", RegexOption.IGNORE_CASE).containsMatchIn(html)
        }

        /** yandex.ru/images/search?…&img_url=… , google.com/imgres?imgurl=… , bing.com/images/search?…&mediaurl=… */
        fun searchEngineImage(url: String): String? {
            val query = url.substringAfter("?", "").substringBefore("#")
            if (query.isEmpty()) return null
            for (pair in query.split("&")) {
                val key = pair.substringBefore("=").lowercase()
                if (key in SEARCH_PARAMS) {
                    val value = URLDecoder.decode(pair.substringAfter("=", ""), "UTF-8")
                    if (value.startsWith("http")) return value
                    if (value.startsWith("//")) return "https:$value"
                }
            }
            return null
        }

        /** Картинка-превью страницы (og:image и т.п.), с учётом относительных адресов и &amp;. */
        fun pageImageUrl(page: HttpResult): String? {
            val html = String(page.bytes, Charsets.UTF_8)
            for (name in META_IMAGE) {
                val n = Regex.escape(name)
                val found = Regex("<meta[^>]*(?:property|name)=[\"']$n[\"'][^>]*content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)
                    ?: Regex("<meta[^>]*content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"']$n[\"']", RegexOption.IGNORE_CASE).find(html)
                val value = found?.groupValues?.get(1)?.replace("&amp;", "&")?.trim()
                if (!value.isNullOrEmpty()) return runCatching { URL(URL(page.finalUrl), value).toString() }.getOrNull()
            }
            return null
        }
    }
}
