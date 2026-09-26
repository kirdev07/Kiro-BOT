package com.vkbot.manager

import android.util.Log
import com.vkbot.manager.botbrain.Attachment
import com.vkbot.manager.utils.BlacklistManager
import com.vkbot.manager.utils.NetworkHelper
import com.vkbot.manager.utils.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** Правила общения, общие с VK-ботом (вынесены, чтобы ядро тестировалось без Android). */
class ChatRules(
    val chatsEnabled: () -> Boolean = { SettingsManager.isChatsEnabled },
    val chatPrefix: () -> String = { SettingsManager.chatPrefix },
    val antiSpamEnabled: () -> Boolean = { SettingsManager.isAntiSpamEnabled },
    val autoBanEnabled: () -> Boolean = { SettingsManager.isAutoBanEnabled },
    val spamLimit: () -> Int = { SettingsManager.spamLimit },
    val isBlacklisted: (Long) -> Boolean = { BlacklistManager.isBlacklisted(it) },
    val ban: (Long, String) -> Unit = { id, name -> BlacklistManager.add(id, name) }
)

/**
 * Ядро бота Telegram (Bot API, long polling через getUpdates).
 *
 * - Личные сообщения — всегда; группы — если включены «Беседы» и к боту обратились:
 *   команда «/…», упоминание @бота, ответ на сообщение бота или префикс из настроек.
 * - Команды «/anime@my_bot» и «/как_дела» превращаются в «anime» и «как дела» для поиска по базе.
 * - Картинки/GIF по ссылке (сайты, Pinterest, поиск картинок) бот скачивает и загружает файлом,
 *   затем отправляет по file_id без повторного скачивания. Видео и страницы уходят ссылкой.
 */
class TelegramBot(
    private val token: String,
    private val onLog: (String) -> Unit,
    private val onStatusUpdate: ((String) -> Unit)? = null,
    private val onWaitForNetwork: (suspend () -> Unit)? = null,
    private val rules: ChatRules = ChatRules(),
    private val apiBase: String = "https://api.telegram.org",
    private val fetcher: ImageFetcher = ImageFetcher(convert = AndroidImageConverter)
) : MessengerBot {

    @Volatile private var isRunning = false
    private var pollingJob: Job? = null
    private val botScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var offset = 0L
    private var botUsername = ""
    private var messageProcessor: ((Map<String, Any>) -> Map<String, Any>?)? = null

    private val spamMap = HashMap<Long, MutableList<Long>>()

    override fun setMessageProcessor(processor: (Map<String, Any>) -> Map<String, Any>?) {
        messageProcessor = processor
    }

    override fun clearUserCache() {
        synchronized(spamMap) { spamMap.clear() }
    }

    override suspend fun start(): Boolean {
        if (isRunning) return true
        while (true) {
            try {
                val me = call("getMe")
                if (!me.optBoolean("ok")) {
                    onLog("Ошибка Telegram: ${me.optString("description")}. Проверьте токен от @BotFather")
                    return false
                }
                botUsername = me.getJSONObject("result").optString("username")
                isRunning = true
                onLog("Бот @$botUsername запущен и готов к работе!")
                onStatusUpdate?.invoke("Бот работает")
                pollingJob = botScope.launch { pollLoop() }
                return true
            } catch (e: Exception) {
                if (NetworkHelper.isNetworkError(e)) {
                    onLog("Ожидание сети...")
                    onStatusUpdate?.invoke("Ожидание сети...")
                    onWaitForNetwork?.invoke()
                } else {
                    onLog("Ошибка запуска бота: ${e.message}")
                    Log.e(TAG, "Error starting bot", e)
                    return false
                }
            }
        }
    }

    override fun stop() {
        isRunning = false
        pollingJob?.cancel()
        botScope.cancel()
        onLog("Бот остановлен")
    }

    private suspend fun pollLoop() {
        while (isRunning) {
            try {
                val response = call(
                    "getUpdates",
                    mapOf(
                        "offset" to offset.toString(),
                        "timeout" to POLL_TIMEOUT_SEC.toString(),
                        "allowed_updates" to "[\"message\"]"
                    ),
                    readTimeoutMs = (POLL_TIMEOUT_SEC + 10) * 1000
                )
                if (!response.optBoolean("ok")) {
                    handlePollError(response)
                    continue
                }
                val updates = response.optJSONArray("result") ?: JSONArray()
                for (i in 0 until updates.length()) {
                    val update = updates.getJSONObject(i)
                    offset = maxOf(offset, update.getLong("update_id") + 1)
                    update.optJSONObject("message")?.let { msg -> botScope.launch { processMessage(msg) } }
                }
                onStatusUpdate?.invoke("Бот работает")
            } catch (e: Exception) {
                if (!isRunning) break
                if (NetworkHelper.isNetworkError(e)) {
                    onStatusUpdate?.invoke("Ожидание сети...")
                    onWaitForNetwork?.invoke()
                } else {
                    Log.e(TAG, "Poll error", e)
                    delay(5000)
                }
            }
        }
    }

    private suspend fun handlePollError(response: JSONObject) {
        when (response.optInt("error_code")) {
            // У бота включён webhook — long polling с ним не работает
            409 -> {
                onLog("У бота был включён webhook — отключаю, чтобы получать сообщения")
                call("deleteWebhook")
            }
            401 -> {
                onLog("Telegram отклонил токен: ${response.optString("description")}")
                isRunning = false
            }
            else -> {
                onLog("Ошибка Telegram: ${response.optString("description")}")
                delay(5000)
            }
        }
    }

    private suspend fun processMessage(msg: JSONObject) {
        try {
            val from = msg.optJSONObject("from") ?: return
            // Другие боты — не отвечаем, чтобы не зациклиться
            if (from.optBoolean("is_bot")) return
            val fromId = from.getLong("id")
            val chat = msg.getJSONObject("chat")
            val chatId = chat.getLong("id")
            val chatType = chat.optString("type")
            if (chatType == "channel") return

            val attachmentTypes = attachmentTypesOf(msg)
            var text = msg.optString("text").ifEmpty { msg.optString("caption") }.trim()
            // Исходный текст: «/запомни …» после разбора команды теряет «/», а команды владельца его ищут
            val rawText = text
            val isGroup = chatType == "group" || chatType == "supergroup"

            if (isGroup) {
                if (!rules.chatsEnabled()) return
                text = addressedText(msg, text) ?: return
                if (text.isEmpty() && attachmentTypes.isEmpty()) return
            } else {
                text = normalizeCommand(text)
            }

            if (rules.isBlacklisted(fromId)) return
            val firstName = from.optString("first_name").ifEmpty { "Друг" }
            if (rules.antiSpamEnabled() && isSpam(fromId)) {
                if (rules.autoBanEnabled()) rules.ban(fromId, "$firstName (Telegram)")
                return
            }

            // «Печатает…» — сразу и пока ищется ответ (статус в Telegram гаснет через 5 с — обновляем)
            val started = System.currentTimeMillis()
            val typing = mapOf("chat_id" to chatId.toString(), "action" to "typing")
            // Первый статус — сразу (иначе при быстром ответе корутина отменится раньше, чем его отправит)
            runCatching { call("sendChatAction", typing) }
            val typingJob = botScope.launch {
                while (isActive) {
                    delay(TYPING_REFRESH_MS)
                    runCatching { call("sendChatAction", typing) }
                }
            }
            val result = try {
                messageProcessor?.invoke(
                    mapOf(
                        "text" to text,
                        "raw_text" to rawText,
                        "from_id" to fromId,
                        "peer_id" to chatId,
                        "first_name" to firstName,
                        "attachment_types" to attachmentTypes
                    )
                )
            } finally {
                typingJob.cancel()
            } ?: return
            val response = result["text"] as? String ?: return
            @Suppress("UNCHECKED_CAST")
            val attachments = (result["attachments"] as? List<Attachment>) ?: emptyList()

            // Имитация набора: дополняем до TYPING_DELAY_MS, если ответ нашёлся быстрее
            val remaining = TYPING_DELAY_MS - (System.currentTimeMillis() - started)
            if (remaining > 0) delay(remaining)
            val replyTo = if (isGroup) msg.optLong("message_id") else 0L
            sendReply(chatId, response, attachments, replyTo, firstName)
        } catch (e: Exception) {
            Log.e(TAG, "Process error", e)
        }
    }

    /** Текст сообщения в группе без обращения к боту или null, если обращались не к нему. */
    private fun addressedText(msg: JSONObject, text: String): String? {
        val mention = "@$botUsername"
        val replyToBot = msg.optJSONObject("reply_to_message")
            ?.optJSONObject("from")?.optString("username")?.equals(botUsername, ignoreCase = true) == true
        val prefix = rules.chatPrefix().trim()
        return when {
            text.startsWith("/") -> {
                // Команда другому боту в группе: /cmd@other_bot
                val target = text.substringBefore(" ").substringAfter("@", "")
                if (target.isNotEmpty() && !target.equals(botUsername, ignoreCase = true)) null else normalizeCommand(text)
            }
            botUsername.isNotEmpty() && text.contains(mention, ignoreCase = true) ->
                text.replace(mention, "", ignoreCase = true).trim().trimStart(',', ' ')
            prefix.isNotEmpty() && text.startsWith(prefix, ignoreCase = true) -> text.substring(prefix.length).trim()
            replyToBot -> text
            else -> null
        }
    }

    /** «/anime@my_bot» → «anime», «/как_дела» → «как дела»; остальной текст не меняется. */
    private fun normalizeCommand(text: String): String {
        if (!text.startsWith("/")) return text
        val command = text.substringBefore(" ").removePrefix("/").substringBefore("@").replace('_', ' ')
        val rest = text.substringAfter(" ", "")
        return listOf(command, rest).filter { it.isNotBlank() }.joinToString(" ").trim()
    }

    /** Типы вложений в терминах VK, чтобы работали медиа-ответы из media_*.txt. */
    private fun attachmentTypesOf(msg: JSONObject): List<String> = listOfNotNull(
        "photo".takeIf { msg.has("photo") },
        "video".takeIf { msg.has("video") },
        "audio_message".takeIf { msg.has("voice") },
        "audio".takeIf { msg.has("audio") },
        "video_message".takeIf { msg.has("video_note") },
        "sticker".takeIf { msg.has("sticker") },
        "doc".takeIf { msg.has("document") && !msg.has("animation") },
        "graffiti".takeIf { msg.has("animation") }
    )

    private fun isSpam(userId: Long): Boolean {
        val now = System.currentTimeMillis()
        synchronized(spamMap) {
            val timestamps = spamMap.getOrPut(userId) { mutableListOf() }
            timestamps.removeAll { now - it > 4000 }
            timestamps.add(now)
            return timestamps.size > rules.spamLimit()
        }
    }

    private enum class MediaKind(val method: String, val field: String) {
        PHOTO("sendPhoto", "photo"),
        ANIMATION("sendAnimation", "animation"),
        /** Фото больше лимита Telegram (10 МБ) или с неподходящими размерами — файлом. */
        DOCUMENT("sendDocument", "document")
    }

    /** Медиа к отправке: уже загруженный в Telegram file_id или скачанный файл. */
    private class Media(val url: String, val kind: MediaKind, val fileId: String? = null, val file: DownloadedMedia? = null)

    /** url → (тип, file_id): повторная команда отправляется без скачивания. */
    private val uploadedFiles = ConcurrentHashMap<String, Pair<MediaKind, String>>()
    /** Ссылки, которые оказались не медиа (страницы, YouTube) — больше не проверяем. */
    private val notMedia = ConcurrentHashMap.newKeySet<String>()

    /** Картинка по ссылке (сайты, Pinterest, поиск картинок); видео и страницы — null, уходят ссылкой. */
    private suspend fun resolveMedia(url: String): Media? = withContext(Dispatchers.IO) {
        uploadedFiles[url]?.let { (kind, id) -> return@withContext Media(url, kind, fileId = id) }
        if (url in notMedia) return@withContext null
        try {
            val file = fetcher.fetch(url)
            val kind = when {
                file.isGif -> MediaKind.ANIMATION
                file.bytes.size > PHOTO_UPLOAD_LIMIT -> MediaKind.DOCUMENT
                else -> MediaKind.PHOTO
            }
            Media(url, kind, file = file)
        } catch (e: ImageFetcher.NotAnImage) {
            notMedia.add(url)
            onLog("Ссылка отправлена текстом ($url): ${e.message}")
            null
        } catch (e: Exception) {
            onLog("Не удалось скачать $url: ${e.message} — отправлена ссылкой")
            null
        }
    }

    private suspend fun sendReply(chatId: Long, text: String, attachments: List<Attachment>, replyTo: Long, userName: String) {
        val media = mutableListOf<Media>()
        val links = mutableListOf<String>()
        for (a in attachments) {
            if (!a.isExternal) {
                // Объект VK в Telegram не отправить — даём ссылку, Telegram покажет превью
                links.add("https://vk.com/${a.toVkString()}")
                continue
            }
            val resolved = resolveMedia(a.url)
            if (resolved != null) media.add(resolved) else links.add(a.url)
        }
        val fullText = (listOf(text.trim()) + links).filter { it.isNotEmpty() }.joinToString("\n")

        var replyParam = replyTo
        var caption = fullText
        // Подпись к медиа ограничена 1024 символами — длинный текст отдельным сообщением
        if (media.isEmpty() || fullText.length > CAPTION_LIMIT) {
            if (fullText.isNotEmpty()) {
                for (chunk in fullText.chunked(TEXT_LIMIT)) {
                    send("sendMessage", chatId, mapOf("text" to chunk), replyParam, userName)
                    replyParam = 0
                }
            }
            caption = ""
        }
        for (item in media) {
            val sent = sendMedia(chatId, item, caption, replyParam, userName)
                // Telegram не принял как фото (размеры, формат) — пробуем файлом
                ?: item.file?.takeIf { item.kind == MediaKind.PHOTO }?.let {
                    sendMedia(chatId, Media(item.url, MediaKind.DOCUMENT, file = it), caption, replyParam, userName)
                }
            if (sent == null) {
                // Не удалось отправить файлом — хотя бы ссылкой
                send("sendMessage", chatId, mapOf("text" to listOf(caption, item.url).filter { it.isNotEmpty() }.joinToString("\n")), replyParam, userName)
            }
            caption = ""
            replyParam = 0
        }
    }

    /** Отправляет медиа (по file_id или загрузкой файла) и запоминает file_id. @return ответ Telegram или null */
    private suspend fun sendMedia(chatId: Long, item: Media, caption: String, replyTo: Long, userName: String): JSONObject? {
        val fields = mutableMapOf("chat_id" to chatId.toString())
        if (caption.isNotEmpty()) fields["caption"] = caption
        if (replyTo > 0) fields["reply_parameters"] = replyParameters(replyTo)

        val response = if (item.fileId != null) {
            call(item.kind.method, fields + (item.kind.field to item.fileId))
        } else {
            val file = item.file ?: return null
            withContext(Dispatchers.IO) {
                JSONObject(MediaFiles.postMultipart("$apiBase/bot$token/${item.kind.method}", fields, item.kind.field, file).ifEmpty { "{}" })
            }
        }
        if (!response.optBoolean("ok")) {
            onLog("Ошибка отправки $userName: ${response.optString("description")} (код ${response.optInt("error_code")})")
            if (item.fileId != null) uploadedFiles.remove(item.url)
            return null
        }
        fileIdOf(response.optJSONObject("result"), item.kind)?.let { uploadedFiles[item.url] = item.kind to it }
        onLog("Ответ отправлен $userName")
        return response
    }

    /** file_id из ответа sendPhoto/sendAnimation/sendVideo/sendDocument. */
    private fun fileIdOf(message: JSONObject?, kind: MediaKind): String? {
        message ?: return null
        return when (kind) {
            MediaKind.PHOTO -> message.optJSONArray("photo")?.let { sizes ->
                if (sizes.length() > 0) sizes.getJSONObject(sizes.length() - 1).optString("file_id") else null
            }
            MediaKind.ANIMATION -> (message.optJSONObject("animation") ?: message.optJSONObject("document"))?.optString("file_id")
            MediaKind.DOCUMENT -> message.optJSONObject("document")?.optString("file_id")
        }?.takeIf { it.isNotEmpty() }
    }

    private fun replyParameters(replyTo: Long) = "{\"message_id\":$replyTo,\"allow_sending_without_reply\":true}"

    private suspend fun send(method: String, chatId: Long, params: Map<String, String>, replyTo: Long, userName: String): Boolean {
        val all = params.toMutableMap()
        all["chat_id"] = chatId.toString()
        if (replyTo > 0) all["reply_parameters"] = replyParameters(replyTo)
        val response = call(method, all)
        return if (response.optBoolean("ok")) {
            onLog("Ответ отправлен $userName")
            true
        } else {
            onLog("Ошибка отправки $userName: ${response.optString("description")} (код ${response.optInt("error_code")})")
            false
        }
    }

    private suspend fun call(
        method: String,
        params: Map<String, String> = emptyMap(),
        readTimeoutMs: Int = 30_000
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL("$apiBase/bot$token/$method").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 30_000
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val body = params.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }
            connection.outputStream.use { it.write(body.toByteArray()) }
            val stream = if (connection.responseCode >= 400) connection.errorStream else connection.inputStream
            JSONObject(stream?.bufferedReader()?.use { it.readText() }.orEmpty().ifEmpty { "{}" })
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "TelegramBot"
        private const val POLL_TIMEOUT_SEC = 25
        private const val TYPING_DELAY_MS = 1500L
        private const val TYPING_REFRESH_MS = 4500L
        private const val CAPTION_LIMIT = 1024
        private const val TEXT_LIMIT = 4096
        /** Лимит Telegram на загрузку фото. */
        private const val PHOTO_UPLOAD_LIMIT = 10 * 1024 * 1024
    }
}
