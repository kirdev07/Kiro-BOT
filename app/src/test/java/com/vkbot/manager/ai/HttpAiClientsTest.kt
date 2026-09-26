package com.vkbot.manager.ai

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** OpenAI-совместимый API, Gemini и YandexGPT против поддельных серверов. */
class HttpAiClientsTest {

    private lateinit var server: MockWebServer
    private val history = listOf(AiTurn(true, "привет"), AiTurn(false, "здравствуй"), AiTurn(true, "как дела?"))

    @Before fun start() { server = MockWebServer().apply { start() } }
    @After fun stop() = server.shutdown()

    private fun base() = server.url("").toString().trimEnd('/')
    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun expectError(block: () -> Unit): String {
        try { block(); fail("ожидалась ошибка") } catch (e: AiException) { return e.message!! }
        return ""
    }

    // --- OpenAI-совместимый ---

    @Test fun openAiCompatibleRequestAndResponse() {
        server.enqueue(json("""{"choices":[{"message":{"role":"assistant","content":" Отлично! "}}]}"""))
        val reply = OpenAiCompatibleClient("${base()}/v1/", "sk-test", "deepseek-chat").reply("Ты бот", history, 200)
        assertEquals("Отлично!", reply)
        val req = server.takeRequest()
        assertEquals("/v1/chat/completions", req.path)
        assertEquals("Bearer sk-test", req.getHeader("Authorization"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("deepseek-chat", body.getString("model"))
        // Запас под рассуждения думающих моделей
        assertEquals(1024, body.getInt("max_tokens"))
        val messages = body.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("assistant", messages.getJSONObject(2).getString("role"))
        assertEquals("как дела?", messages.getJSONObject(3).getString("content"))
    }

    @Test fun retriesWithMaxCompletionTokensForNewOpenAiModels() {
        server.enqueue(json("""{"error":{"message":"Unsupported parameter: 'max_tokens' is not supported with this model. Use 'max_completion_tokens' instead."}}""", 400))
        server.enqueue(json("""{"choices":[{"message":{"content":"ок"}}]}"""))
        assertEquals("ок", OpenAiCompatibleClient(base(), "k", "m").reply("s", history, 100))
        server.takeRequest()
        val retry = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(1024, retry.getInt("max_completion_tokens"))
        assertTrue(!retry.has("max_tokens"))
    }

    @Test fun localServerWorksWithoutKey() {
        server.enqueue(json("""{"choices":[{"message":{"content":[{"type":"text","text":"части "},{"type":"text","text":"ответа"}]}}]}"""))
        assertEquals("части ответа", OpenAiCompatibleClient(base(), "", "llama").reply("s", history, 100))
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test fun openAiErrorsAreReadable() {
        server.enqueue(json("""{"error":{"message":"Incorrect API key provided"}}""", 401))
        val msg = expectError { OpenAiCompatibleClient(base(), "bad", "m").reply("s", history, 100) }
        assertTrue(msg, msg.contains("неверный API-ключ") && msg.contains("Incorrect API key"))
    }

    @Test fun badUrlIsReported() {
        val msg = expectError { OpenAiCompatibleClient("не адрес", "k", "m").reply("s", history, 100) }
        assertTrue(msg, msg.contains("адрес"))
    }

    @Test fun modelListSkipsNonChatModels() {
        server.enqueue(json("""{"object":"list","data":[{"id":"openai/gpt-oss-120b"},{"id":"whisper-large-v3"},{"id":"llama-3.1-8b-instant"},{"id":"meta-llama/llama-prompt-guard-2-22m"}]}"""))
        assertEquals(listOf("llama-3.1-8b-instant", "openai/gpt-oss-120b"), OpenAiCompatibleClient.listModels("${base()}/openai/v1", "gsk_test"))
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/openai/v1/models", req.path)
        assertEquals("Bearer gsk_test", req.getHeader("Authorization"))
    }

    @Test fun modelListErrorIsReadable() {
        server.enqueue(json("""{"error":{"message":"Invalid API Key"}}""", 401))
        val msg = expectError { OpenAiCompatibleClient.listModels(base(), "bad") }
        assertTrue(msg, msg.contains("неверный API-ключ"))
    }

    @Test fun baseUrlWithoutSchemeGetsHttps() {
        assertEquals("https://api.deepseek.com/chat/completions", OpenAiCompatibleClient.endpointFor("api.deepseek.com/"))
        assertEquals("http://localhost:11434/v1/chat/completions", OpenAiCompatibleClient.endpointFor("http://localhost:11434/v1"))
        assertEquals("https://x.ai/v1/chat/completions", OpenAiCompatibleClient.endpointFor(" https://x.ai/v1/chat/completions "))
    }

    @Test fun plainHttpIsOnlyForLocalhost() {
        val msg = expectError { OpenAiCompatibleClient("http://example.com/v1", "k", "m").reply("s", history, 100) }
        assertTrue(msg, msg.contains("https"))
    }

        // --- Gemini ---

    @Test fun geminiRequestAndResponseSkipThoughts() {
        server.enqueue(json("""{"candidates":[{"content":{"role":"model","parts":[
            {"text":"размышляю...","thought":true},{"text":"Всё хорошо!"}]}}]}"""))
        val reply = GeminiClient("g-key", "gemini-3.8-flash", "${base()}/v1beta").reply("Ты бот", history, 200)
        assertEquals("Всё хорошо!", reply)
        val req = server.takeRequest()
        assertEquals("/v1beta/models/gemini-3.8-flash:generateContent", req.path)
        assertEquals("g-key", req.getHeader("x-goog-api-key"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("Ты бот", body.getJSONObject("system_instruction").getJSONArray("parts").getJSONObject(0).getString("text"))
        val contents = body.getJSONArray("contents")
        assertEquals("model", contents.getJSONObject(1).getString("role"))
        assertEquals("low", body.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"))
    }

    @Test fun geminiBlockedPrompt() {
        server.enqueue(json("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
        val msg = expectError { GeminiClient("k", "m", base()).reply("s", history, 100) }
        assertTrue(msg, msg.contains("SAFETY"))
    }

    // --- YandexGPT ---

    @Test fun yandexRequestAndResponse() {
        server.enqueue(json("""{"result":{"alternatives":[{"message":{"role":"assistant","text":"Привет от Яндекса"},"status":"ALTERNATIVE_STATUS_FINAL"}]}}"""))
        val client = YandexGptClient("y-key", "b1gfolder", "yandexgpt-lite/latest", "${base()}/foundationModels/v1/completion")
        assertEquals("Привет от Яндекса", client.reply("Ты бот", history, 200))
        val req = server.takeRequest()
        assertEquals("Api-Key y-key", req.getHeader("Authorization"))
        assertEquals("b1gfolder", req.getHeader("x-folder-id"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("gpt://b1gfolder/yandexgpt-lite/latest", body.getString("modelUri"))
        assertEquals("system", body.getJSONArray("messages").getJSONObject(0).getString("role"))
        assertEquals("как дела?", body.getJSONArray("messages").getJSONObject(3).getString("text"))
    }

    @Test fun yandexFullModelUriAndMissingFolder() {
        assertEquals("gpt://other/yandexgpt/latest", YandexGptClient("k", "b1g", "gpt://other/yandexgpt/latest").modelUri())
        val msg = expectError { YandexGptClient("k", "", "yandexgpt-lite").reply("s", history, 100) }
        assertTrue(msg, msg.contains("каталог"))
    }
}
