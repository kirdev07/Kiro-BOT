package com.vkbot.manager

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue

/** Pinterest против поддельного сервера: виджеты, страница пина, размеры картинок. */
class PinterestResolverTest {

    private lateinit var server: MockWebServer
    private val requested = ConcurrentLinkedQueue<String>()
    private var widgetsWork = true
    private var has736 = true

    private lateinit var resolver: PinterestResolver
    private fun base() = server.url("").toString().trimEnd('/')

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!
                requested.add(path)
                return when {
                    path.startsWith("/v3/pidgets/pins/info/") ->
                        if (!widgetsWork) MockResponse().setResponseCode(500)
                        else MockResponse().setBody(
                            """{"status":"success","data":[{"id":"111","images":{
                              "236x":{"url":"${base()}/236x/aa.jpg","width":236},
                              "564x":{"url":"${base()}/564x/aa.jpg","width":564}}}]}"""
                        )
                    path == "/736x/aa.jpg" -> if (has736) MockResponse().setHeader("Content-Type", "image/jpeg") else MockResponse().setResponseCode(403)
                    path.startsWith("/pin/") -> MockResponse().setBody(
                        "<html><head>" + "x".repeat(50_000) +
                        """<meta content="${base()}/736x/page.jpg" data-app="true" name="og:image" property="og:image"/>
                           <meta content="413" name="og:image:height" property="og:image:height"/></head></html>"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        resolver = PinterestResolver(widgetsBase = base(), extraHosts = listOf(server.hostName))
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun recognizesPinterestLinks() {
        val real = PinterestResolver()
        assertTrue(real.isPinterest("https://www.pinterest.com/pin/585116176588638487/"))
        assertTrue(real.isPinterest("https://ru.pinterest.com/pin/123/"))
        assertTrue(real.isPinterest("https://pin.it/4abcDEF"))
        assertFalse(real.isPinterest("https://example.com/pinterest.jpg"))
        assertNull(real.resolveImageUrl("https://example.com/cat.jpg"))
    }

    @Test
    fun widgetsGiveLargestImageUpgradedTo736() {
        assertEquals("${base()}/736x/aa.jpg", resolver.resolveImageUrl("${base()}/pin/111/"))
        assertTrue("страница пина не нужна", requested.none { it.startsWith("/pin/") })
    }

    @Test
    fun keeps564WhenBiggerSizeMissing() {
        has736 = false
        assertEquals("${base()}/564x/aa.jpg", resolver.resolveImageUrl("${base()}/pin/111/"))
    }

    @Test
    fun pinIdIsTakenFromSlugUrl() {
        resolver.resolveImageUrl("${base()}/pin/anime-wallpaper--111/")
        assertTrue(requested.any { it.contains("pin_ids=111") })
    }

    @Test
    fun fallsBackToPageOgImage() {
        widgetsWork = false
        assertEquals("${base()}/736x/page.jpg", resolver.resolveImageUrl("${base()}/pin/111/"))
    }
}
