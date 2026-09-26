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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/** Ядро Telegram против поддельного Bot API (MockWebServer). */
class TelegramBotTest {

    private lateinit var server: MockWebServer
    private val calls = ConcurrentLinkedQueue<Pair<String, Map<String, String>>>()
    private val pendingUpdates = ConcurrentLinkedQueue<String>()
    private var getMeResponse = """{"ok":true,"result":{"id":1,"is_bot":true,"username":"kiro_test_bot"}}"""
    private var firstPollResponse: String? = null
    private val received = ConcurrentLinkedQueue<Map<String, Any>>()
    private val downloads = ConcurrentLinkedQueue<String>()
    @Volatile private var rejectPhotos = false
    private fun fileUrl(name: String) = server.url("/files/$name").toString()

    private val rules = ChatRules(
        chatsEnabled = { true }, chatPrefix = { "Бот," }, antiSpamEnabled = { false },
        autoBanEnabled = { false }, spamLimit = { 5 }, isBlacklisted = { it == 666L }, ban = { _, _ -> }
    )

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                // Файлы, которые бот скачивает (тип — только в Content-Type, расширения в ссылке нет)
                when (request.path) {
                    "/files/anime" -> { downloads.add("anime"); return MockResponse().setHeader("Content-Type", "image/jpeg").setBody(Buffer().write(ByteArray(3000) { 7 })) }
                    "/files/dance" -> return MockResponse().setHeader("Content-Type", "image/gif").setBody("GIF89a...")
                    "/files/page" -> return MockResponse().setHeader("Content-Type", "text/html").setBody("<html></html>")
                    "/files/clip" -> return MockResponse().setHeader("Content-Type", "video/mp4").setBody("....ftypmp42")
                }
                val method = request.path!!.substringAfterLast("/")
                val raw = request.body.readString(Charsets.ISO_8859_1)
                val params = if (request.getHeader("Content-Type").orEmpty().startsWith("multipart/")) {
                    // multipart: поля name="x" + пометка, что пришёл файл
                    Regex("name=\"(\\w+)\"(; filename=\"[^\"]*\")?\\r\\n(?:Content-Type: [^\\r]*\\r\\n)?\\r\\n([^\\r]*)")
                        .findAll(raw).associate { m ->
                            m.groupValues[1] to (if (m.groupValues[2].isNotEmpty()) "<file>" else String(m.groupValues[3].toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8))
                        }
                } else {
                    String(raw.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8).split("&").filter { it.contains("=") }.associate {
                        URLDecoder.decode(it.substringBefore("="), "UTF-8") to URLDecoder.decode(it.substringAfter("="), "UTF-8")
                    }
                }
                calls.add(method to params)
                return when (method) {
                    "sendPhoto" -> if (rejectPhotos) MockResponse().setBody("""{"ok":false,"error_code":400,"description":"Bad Request: PHOTO_INVALID_DIMENSIONS"}""")
                        else MockResponse().setBody("""{"ok":true,"result":{"photo":[{"file_id":"SMALL"},{"file_id":"PHOTO_ID"}]}}""")
                    "sendDocument" -> MockResponse().setBody("""{"ok":true,"result":{"document":{"file_id":"DOC_ID"}}}""")
                    "sendAnimation" -> MockResponse().setBody("""{"ok":true,"result":{"animation":{"file_id":"ANIM_ID"}}}""")
                    "getMe" -> MockResponse().setBody(getMeResponse)
                    "getUpdates" -> {
                        firstPollResponse?.let { firstPollResponse = null; return MockResponse().setBody(it) }
                        val update = pendingUpdates.poll()
                        if (update != null) MockResponse().setBody("""{"ok":true,"result":[$update]}""")
                        else MockResponse().setBody("""{"ok":true,"result":[]}""").setBodyDelay(100, TimeUnit.MILLISECONDS)
                    }
                    else -> MockResponse().setBody("""{"ok":true,"result":{}}""")
                }
            }
        }
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    private fun bot(reply: (Map<String, Any>) -> Map<String, Any>?) =
        TelegramBot("TEST:TOKEN", onLog = {}, rules = rules, apiBase = server.url("").toString().trimEnd('/')).apply {
            setMessageProcessor { msg -> received.add(msg); reply(msg) }
        }

    private fun message(id: Long, text: String, chatType: String = "private", fromId: Long = 42, isBot: Boolean = false, extra: String = "") =
        """{"update_id":$id,"message":{"message_id":$id,"from":{"id":$fromId,"is_bot":$isBot,"first_name":"Аня"},
           "chat":{"id":${if (chatType == "private") fromId else -100500},"type":"$chatType"},"text":"$text"$extra}}"""

    private fun waitFor(timeoutMs: Long = 5000, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (condition()) return true; Thread.sleep(20) }
        return condition()
    }

    private fun sent(method: String) = calls.filter { it.first == method }.map { it.second }

    @Test
    fun privateMessageGetsAnswer() {
        val b = bot { mapOf("text" to "Привет, ${it["first_name"]}!") }
        assertTrue(runBlocking { b.start() })
        pendingUpdates.add(message(1, "привет"))
        assertTrue(waitFor { sent("sendMessage").isNotEmpty() })
        b.stop()

        val msg = received.first()
        assertEquals("привет", msg["text"])
        assertEquals(42L, msg["from_id"])
        val reply = sent("sendMessage").first()
        assertEquals("Привет, Аня!", reply["text"])
        assertEquals("42", reply["chat_id"])
        assertTrue("в личке без reply", reply["reply_parameters"] == null)
        assertTrue(sent("sendChatAction").isNotEmpty())
    }

    @Test
    fun typingIsShownWhileAnswerIsBeingPrepared() {
        val release = java.util.concurrent.CountDownLatch(1)
        // Обработчик «думает», как ИИ, пока тест его не отпустит
        val b = bot { release.await(5, java.util.concurrent.TimeUnit.SECONDS); mapOf("text" to "готово") }
        runBlocking { b.start() }
        pendingUpdates.add(message(1, "сложный вопрос"))
        assertTrue("«печатает» до ответа", waitFor { sent("sendChatAction").isNotEmpty() })
        assertTrue(sent("sendMessage").isEmpty())
        release.countDown()
        assertTrue(waitFor { sent("sendMessage").isNotEmpty() })
        b.stop()
    }

    @Test
    fun groupCommandIsNormalizedAndRepliedTo() {
        val b = bot { mapOf("text" to "ok") }
        runBlocking { b.start() }
        pendingUpdates.add(message(1, "/как_дела@kiro_test_bot", chatType = "supergroup"))
        assertTrue(waitFor { sent("sendMessage").isNotEmpty() })
        b.stop()
        assertEquals("как дела", received.first()["text"])
        assertTrue(sent("sendMessage").first()["reply_parameters"]!!.contains("\"message_id\":1"))
    }

    @Test
    fun groupIgnoresMessagesNotAddressedToBot() {
        val b = bot { mapOf("text" to "ok") }
        runBlocking { b.start() }
        pendingUpdates.add(message(1, "просто болтаем", chatType = "group"))
        pendingUpdates.add(message(2, "/start@other_bot", chatType = "group"))
        pendingUpdates.add(message(3, "Бот, привет", chatType = "group"))
        assertTrue(waitFor { received.isNotEmpty() })
        Thread.sleep(300)
        b.stop()
        assertEquals(listOf("привет"), received.map { it["text"] })
    }

    @Test
    fun otherBotsAndBlacklistedUsersAreIgnored() {
        val b = bot { mapOf("text" to "ok") }
        runBlocking { b.start() }
        pendingUpdates.add(message(1, "привет", isBot = true, fromId = 7))
        pendingUpdates.add(message(2, "привет", fromId = 666))
        pendingUpdates.add(message(3, "привет", fromId = 43))
        assertTrue(waitFor { received.isNotEmpty() })
        Thread.sleep(300)
        b.stop()
        assertEquals(listOf(43L), received.map { it["from_id"] })
    }

    @Test
    fun imageLinkWithoutExtensionIsUploadedAsPhoto() {
        // Раньше тип определялся по расширению — такая ссылка уходила текстом
        val b = bot { mapOf("text" to "Держи", "attachments" to listOf(Attachment.parse(fileUrl("anime"))!!)) }
        runBlocking { b.start() }
        pendingUpdates.add(message(1, "/аниме"))
        assertTrue(waitFor { sent("sendPhoto").isNotEmpty() })
        b.stop()
        val photo = sent("sendPhoto").first()
        assertEquals("<file>", photo["photo"])
        assertEquals("Держи", photo["caption"])
        assertEquals("42", photo["chat_id"])
        assertTrue("ссылка не должна уйти текстом", sent("sendMessage").isEmpty())
    }

    @Test
    fun repeatedCommandUsesFileIdWithoutDownloading() {
        val b = bot { mapOf("text" to "", "attachments" to listOf(Attachment.parse(fileUrl("anime"))!!)) }
        runBlocking { b.start() }
        pendingUpdates.add(message(1, "аниме"))
        assertTrue(waitFor { sent("sendPhoto").size == 1 })
        pendingUpdates.add(message(2, "аниме"))
        assertTrue(waitFor { sent("sendPhoto").size == 2 })
        b.stop()
        assertEquals("PHOTO_ID", sent("sendPhoto")[1]["photo"])
        assertEquals(1, downloads.size)
    }

    @Test
    fun gifIsAnimationAndPagesAndVkGoToText() {
        val b = bot {
            mapOf("text" to "", "attachments" to listOf(
                Attachment.parse(fileUrl("dance"))!!,
                Attachment.parse(fileUrl("page"))!!,
                Attachment.parse("https://vk.com/video-1_2")!!
            ))
        }
        runBlocking { b.start() }
        pendingUpdates.add(message(1, "гиф"))
        assertTrue(waitFor { sent("sendAnimation").isNotEmpty() })
        b.stop()
        val anim = sent("sendAnimation").first()
        assertEquals("<file>", anim["animation"])
        assertEquals("${fileUrl("page")}\nhttps://vk.com/video-1_2", anim["caption"])
    }

    @Test
    fun videoLinkIsSentAsText() {
        val b = bot { mapOf("text" to "Смотри", "attachments" to listOf(Attachment.parse(fileUrl("clip"))!!)) }
        runBlocking { b.start() }
        pendingUpdates.add(message(1, "видео"))
        assertTrue(waitFor { sent("sendMessage").isNotEmpty() })
        b.stop()
        assertEquals("Смотри\n${fileUrl("clip")}", sent("sendMessage").first()["text"])
        assertTrue(calls.none { it.first == "sendVideo" || it.first == "sendPhoto" })
    }

    @Test
    fun photoRejectedByTelegramIsSentAsDocument() {
        rejectPhotos = true
        val b = bot { mapOf("text" to "", "attachments" to listOf(Attachment.parse(fileUrl("anime"))!!)) }
        runBlocking { b.start() }
        pendingUpdates.add(message(1, "аниме"))
        assertTrue(waitFor { sent("sendDocument").isNotEmpty() })
        b.stop()
        assertEquals("<file>", sent("sendDocument").first()["document"])
        assertTrue("до ссылки не дошло", sent("sendMessage").isEmpty())
    }

    @Test
    fun brokenImageLinkFallsBackToText() {
        val b = bot { mapOf("text" to "Держи", "attachments" to listOf(Attachment.parse("http://127.0.0.1:1/x.jpg")!!)) }
        runBlocking { b.start() }
        pendingUpdates.add(message(1, "аниме"))
        assertTrue(waitFor { sent("sendMessage").isNotEmpty() })
        b.stop()
        assertEquals("Держи\nhttp://127.0.0.1:1/x.jpg", sent("sendMessage").first()["text"])
    }

    @Test
    fun photoMessageReportsAttachmentType() {
        val b = bot { null }
        runBlocking { b.start() }
        pendingUpdates.add(message(1, "", extra = ""","photo":[{"file_id":"x"}]"""))
        assertTrue(waitFor { received.isNotEmpty() })
        b.stop()
        assertEquals(listOf("photo"), received.first()["attachment_types"])
    }

    @Test
    fun invalidTokenFailsToStart() {
        getMeResponse = """{"ok":false,"error_code":401,"description":"Unauthorized"}"""
        val b = bot { null }
        assertFalse(runBlocking { b.start() })
    }

    @Test
    fun webhookConflictIsResolved() {
        firstPollResponse = """{"ok":false,"error_code":409,"description":"Conflict: can't use getUpdates method while webhook is active"}"""
        val b = bot { mapOf("text" to "ok") }
        runBlocking { b.start() }
        pendingUpdates.add(message(1, "привет"))
        assertTrue(waitFor { sent("sendMessage").isNotEmpty() })
        b.stop()
        assertTrue(calls.any { it.first == "deleteWebhook" })
    }
}
