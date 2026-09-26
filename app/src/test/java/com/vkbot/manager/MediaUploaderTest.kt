package com.vkbot.manager

import com.vkbot.manager.botbrain.Attachment
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Загрузка вложений без настоящего VK: локальный сервер отдаёт картинки/страницы
 * и изображает upload-сервер VK, API VK подменён лямбдой.
 */
class MediaUploaderTest {

    private lateinit var server: MockWebServer
    private lateinit var base: String
    private val apiCalls = mutableListOf<String>()
    private val uploadedFields = mutableListOf<String>()

    private val fakeApi: suspend (String, Map<String, String>) -> String = { method, params ->
        apiCalls.add(method)
        when (method) {
            "photos.getMessagesUploadServer", "docs.getMessagesUploadServer" ->
                """{"response":{"upload_url":"$base/upload"}}"""
            "photos.saveMessagesPhoto" -> {
                assertEquals("[photo-data]", params["photo"])
                """{"response":[{"owner_id":-100,"id":555,"access_key":"key1"}]}"""
            }
            "docs.save" -> """{"response":{"type":"doc","doc":{"owner_id":-100,"id":777,"access_key":"key2"}}}"""
            else -> """{"error":{"error_msg":"unexpected $method"}}"""
        }
    }

    @Before
    fun startServer() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                fun file(type: String, body: ByteArray) =
                    MockResponse().setHeader("Content-Type", type).setBody(Buffer().write(body))
                return when (request.path) {
                    "/cat.jpg" -> file("image/jpeg", ByteArray(2048) { it.toByte() })
                    "/anim.gif" -> file("image/gif", "GIF89a....".toByteArray())
                    "/watch" -> file("text/html; charset=utf-8", "<html>video page</html>".toByteArray())
                    "/upload" -> {
                        val body = request.body.readString(Charsets.ISO_8859_1)
                        val field = Regex("name=\"(\\w+)\"").find(body)?.groupValues?.get(1).orEmpty()
                        synchronized(uploadedFields) { uploadedFields.add(field) }
                        val json = if (field == "photo") """{"server":1,"photo":"[photo-data]","hash":"h"}""" else """{"file":"file-data"}"""
                        MockResponse().setBody(json)
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        base = server.url("/").toString().trimEnd('/')
    }

    @After
    fun stopServer() = server.shutdown()

    private fun resolve(uploader: MediaUploader, vararg values: String) = runBlocking {
        uploader.resolve(values.map { Attachment.parse(it)!! }, peerId = 2000000001)
    }

    @Test
    fun imageLinkIsUploadedAsPhoto() {
        val result = resolve(MediaUploader(fakeApi), "$base/cat.jpg")
        assertEquals(listOf("photo-100_555_key1"), result.vkAttachments)
        assertTrue(result.links.isEmpty())
        assertEquals(listOf("photo"), uploadedFields)
    }

    @Test
    fun gifIsUploadedAsDocumentToStayAnimated() {
        val result = resolve(MediaUploader(fakeApi), "$base/anim.gif")
        assertEquals(listOf("doc-100_777_key2"), result.vkAttachments)
        assertEquals(listOf("file"), uploadedFields)
    }

    @Test
    fun pageLinkGoesToTextForPreview() {
        val uploader = MediaUploader(fakeApi)
        val result = resolve(uploader, "$base/watch")
        assertTrue(result.vkAttachments.isEmpty())
        assertEquals(listOf("$base/watch"), result.links)
        assertTrue(apiCalls.isEmpty())
    }

    @Test
    fun vkAttachmentIsSentAsIs() {
        val result = resolve(MediaUploader(fakeApi), "https://vk.com/video-123_456")
        assertEquals(listOf("video-123_456"), result.vkAttachments)
        assertTrue(apiCalls.isEmpty())
    }

    @Test
    fun uploadedImageIsCached() {
        val uploader = MediaUploader(fakeApi)
        resolve(uploader, "$base/cat.jpg")
        val callsAfterFirst = apiCalls.size
        val second = resolve(uploader, "$base/cat.jpg")
        assertEquals(listOf("photo-100_555_key1"), second.vkAttachments)
        assertEquals(callsAfterFirst, apiCalls.size)
    }

    @Test
    fun brokenLinkFallsBackToText() {
        val result = resolve(MediaUploader(fakeApi), "http://127.0.0.1:1/nothing.jpg")
        assertEquals(listOf("http://127.0.0.1:1/nothing.jpg"), result.links)
    }
}
