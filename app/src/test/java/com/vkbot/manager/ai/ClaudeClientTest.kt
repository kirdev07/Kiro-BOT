package com.vkbot.manager.ai

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Запросы Claude через официальный SDK против поддельного API. */
class ClaudeClientTest {

    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().apply { start() } }
    @After fun stop() = server.shutdown()

    private fun base() = server.url("").toString().trimEnd('/')

    private fun ok(text: String, stop: String = "end_turn") = MockResponse().setHeader("Content-Type", "application/json").setBody(
        """{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5",
           "content":[{"type":"thinking","thinking":"","signature":"s"},{"type":"text","text":"$text"}],
           "stop_reason":"$stop","stop_sequence":null,
           "usage":{"input_tokens":10,"output_tokens":5}}"""
    )

    @Test
    fun opus5RequestHasLowEffortAdaptiveThinkingAndFallbacks() {
        server.enqueue(ok("Привет, я Kiro!"))
        val reply = ClaudeClient("test-key", "claude-opus-5", base())
            .reply("Ты бот", listOf(AiTurn(true, "кто ты?"), AiTurn(false, "бот"), AiTurn(true, "как зовут?")), 300)
        assertEquals("Привет, я Kiro!", reply)

        val req = server.takeRequest()
        assertEquals("/v1/messages", req.path)
        assertEquals("test-key", req.getHeader("x-api-key"))
        assertTrue(req.getHeader("anthropic-beta")!!.contains("server-side-fallback-2026-07-01"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("claude-opus-5", body.getString("model"))
        assertEquals("Ты бот", body.getString("system"))
        assertEquals("adaptive", body.getJSONObject("thinking").getString("type"))
        assertEquals("low", body.getJSONObject("output_config").getString("effort"))
        assertEquals("default", body.getString("fallbacks"))
        val messages = body.getJSONArray("messages")
        assertEquals(3, messages.length())
        assertEquals("assistant", messages.getJSONObject(1).getString("role"))
        assertEquals("как зовут?", messages.getJSONObject(2).getString("content"))
        assertTrue(body.getLong("max_tokens") >= 2048)
    }

    @Test
    fun otherModelsHaveNoFallbackBeta() {
        server.enqueue(ok("ок"))
        ClaudeClient("k", "claude-haiku-4-5", base()).reply("s", listOf(AiTurn(true, "hi")), 300)
        val req = server.takeRequest()
        assertFalse(JSONObject(req.body.readUtf8()).has("fallbacks"))
        assertTrue(req.getHeader("anthropic-beta")?.contains("server-side-fallback") != true)
    }

    @Test
    fun refusalIsError() {
        server.enqueue(ok("", stop = "refusal"))
        try {
            ClaudeClient("k", "claude-opus-5", base()).reply("s", listOf(AiTurn(true, "x")), 300)
            fail()
        } catch (e: AiException) {
            assertTrue(e.message!!.contains("отказался"))
        }
    }

    @Test
    fun badKeyGivesClearMessage() {
        server.enqueue(MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json")
            .setBody("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""))
        try {
            ClaudeClient("bad", "claude-opus-5", base()).reply("s", listOf(AiTurn(true, "x")), 300)
            fail()
        } catch (e: AiException) {
            assertEquals("неверный API-ключ Claude", e.message)
        }
    }
}
