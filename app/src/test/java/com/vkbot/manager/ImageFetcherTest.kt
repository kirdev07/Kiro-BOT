package com.vkbot.manager

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Картинки с «трудных» сайтов: неверный тип, страницы, защита, перенаправления. */
class ImageFetcherTest {

    private lateinit var server: MockWebServer
    private val converted = mutableListOf<String>()
    private lateinit var fetcher: ImageFetcher

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(500) { 1 }
    private val webp = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBPVP8 ".toByteArray() + ByteArray(100)

    private fun url(path: String) = server.url(path).toString()
    private fun bytes(type: String, body: ByteArray) = MockResponse().setHeader("Content-Type", type).setBody(Buffer().write(body))
    private fun html(body: String) = MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(body)

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/cdn/photo" -> bytes("application/octet-stream", jpeg)
                "/real.jpg" -> bytes("image/jpeg", jpeg)
                "/protected.jpg" ->
                    if (request.getHeader("User-Agent").orEmpty().contains("Mozilla") && request.getHeader("Referer") != null)
                        bytes("image/jpeg", jpeg) else MockResponse().setResponseCode(403)
                "/r1" -> MockResponse().setResponseCode(302).setHeader("Location", "/r2")
                "/r2" -> MockResponse().setResponseCode(301).setHeader("Location", url("/real.jpg"))
                "/article" -> html("""<html><head><title>Пост</title>
                    <meta property="og:image" content="/img/cover.jpg?w=800&amp;h=600"></head><body>текст</body></html>""")
                "/img/cover.jpg?w=800&h=600" -> bytes("image/jpeg", jpeg)
                "/tweet" -> html("""<html><head><meta name="twitter:image" content="${url("/real.jpg")}"></head></html>""")
                "/no-image" -> html("<html><head><title>Пусто</title></head></html>")
                "/watch" -> html("""<html><head><meta property="og:type" content="video.other">
                    <meta property="og:image" content="${url("/real.jpg")}"></head></html>""")
                "/clip.mp4" -> bytes("video/mp4", ByteArray(100))
                "/pic.webp" -> bytes("image/webp", webp)
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        fetcher = ImageFetcher(
            pinterest = PinterestResolver(widgetsBase = url("/").trimEnd('/')),
            convert = { media -> converted.add(media.contentType); if (media.contentType == "image/webp") DownloadedMedia(jpeg, "image/jpeg") else media }
        )
    }

    @After
    fun stop() = server.shutdown()

    private fun assertNotImage(link: String) {
        try {
            fetcher.fetch(link)
            fail("ожидалось NotAnImage для $link")
        } catch (_: ImageFetcher.NotAnImage) {
        }
    }

    @Test fun imageServedAsOctetStreamIsDetectedByBytes() {
        val media = fetcher.fetch(url("/cdn/photo"))
        assertEquals("image/jpeg", media.contentType)
        assertArrayEquals(jpeg, media.bytes)
    }

    @Test fun pageLinkGivesItsPreviewImage() =
        assertEquals("image/jpeg", fetcher.fetch(url("/article")).contentType)

    @Test fun twitterImageIsUsedToo() =
        assertEquals("image/jpeg", fetcher.fetch(url("/tweet")).contentType)

    @Test fun hotlinkProtectedImageWorks() =
        assertEquals("image/jpeg", fetcher.fetch(url("/protected.jpg")).contentType)

    @Test fun redirectsAreFollowed() =
        assertEquals("image/jpeg", fetcher.fetch(url("/r1")).contentType)

    @Test fun videoIsNotDownloaded() = assertNotImage(url("/clip.mp4"))

    @Test fun pageWithoutImageIsLink() = assertNotImage(url("/no-image"))

    // Видео — ссылкой, даже если у страницы есть картинка-обложка
    @Test fun videoPageIsLinkNotCover() = assertNotImage(url("/watch"))

    @Test fun videoSitesAreNotFetched() {
        assertTrue(ImageFetcher.isVideoSite("https://www.youtube.com/watch?v=abc"))
        assertTrue(ImageFetcher.isVideoSite("https://youtu.be/abc"))
        assertTrue(ImageFetcher.isVideoSite("https://rutube.ru/video/123/"))
        assertTrue(ImageFetcher.isVideoSite("https://m.tiktok.com/@user/video/1"))
        assertTrue(!ImageFetcher.isVideoSite("https://notyoutube.com.example/a.jpg"))
        assertNotImage("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    }

    @Test fun webpIsConvertedToJpeg() {
        val media = fetcher.fetch(url("/pic.webp"))
        assertEquals("image/jpeg", media.contentType)
        assertTrue("image/webp" in converted)
    }

    @Test fun httpErrorExplainsReason() {
        try {
            fetcher.fetch(url("/missing.jpg"))
            fail()
        } catch (e: java.io.IOException) {
            assertTrue(e.message!!.contains("404"))
        }
    }

    @Test fun searchEngineLinksGiveOriginal() {
        assertEquals("https://site.com/a.jpg",
            ImageFetcher.searchEngineImage("https://yandex.ru/images/search?text=cat&img_url=https%3A%2F%2Fsite.com%2Fa.jpg&pos=1"))
        assertEquals("https://site.com/b.png",
            ImageFetcher.searchEngineImage("https://www.google.com/imgres?imgurl=https://site.com/b.png&imgrefurl=x"))
        assertEquals("https://site.com/c.gif",
            ImageFetcher.searchEngineImage("https://www.bing.com/images/search?q=x&mediaurl=https%3a%2f%2fsite.com%2fc.gif"))
        assertEquals(null, ImageFetcher.searchEngineImage("https://site.com/page?id=5"))
    }

    @Test fun sniffKnowsFormats() {
        assertEquals("image/jpeg", MediaFiles.sniffImageType(jpeg))
        assertEquals("image/webp", MediaFiles.sniffImageType(webp))
        assertEquals("image/gif", MediaFiles.sniffImageType("GIF89a....".toByteArray()))
        assertEquals(null, MediaFiles.sniffImageType("<html>".toByteArray()))
    }
}
