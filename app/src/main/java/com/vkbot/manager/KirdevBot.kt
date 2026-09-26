package com.vkbot.manager

import android.util.Log
import com.vkbot.manager.botbrain.Attachment
import com.vkbot.manager.utils.BlacklistManager
import com.vkbot.manager.utils.NetworkHelper
import com.vkbot.manager.utils.SettingsManager
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Ядро бота VK (Bots Long Poll API сообщества).
 */
class KirdevBot(
    private val token: String,
    private val onLog: (String) -> Unit,
    private val onStatusUpdate: ((String) -> Unit)? = null,
    private val onWaitForNetwork: (suspend () -> Unit)? = null
) : MessengerBot {
    
    private var isRunning = false
    private var pollingJob: Job? = null
    private var historyJob: Job? = null
    private val botScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var server: String = ""
    private var key: String = ""
    private var ts: String = ""
    private var cachedGroupId: String? = null
    
    private data class UserInfo(val firstName: String, val fullName: String)

    private val userCache = ConcurrentHashMap<Int, UserInfo>()
    private var messageProcessor: ((Map<String, Any>) -> Map<String, Any>?)? = null
    private val mediaUploader = MediaUploader(api = { method, params -> makeApiRequest(method, params) }, onLog = onLog)
    
    companion object {
        private const val TAG = "VKBotManager"
        private const val API_VERSION = "5.131"
        private const val VK_API_URL = "https://api.vk.com/method/"
        /** Сколько бот «печатает» перед ответом */
        private const val TYPING_MS = 2500L
        /** Статус «печатает» в VK гаснет через ~10 с — обновляем чаще */
        private const val VK_TYPING_REFRESH_MS = 8000L
    }
    
    override fun setMessageProcessor(processor: (Map<String, Any>) -> Map<String, Any>?) {
        this.messageProcessor = processor
    }
    
    override fun clearUserCache() {
        userCache.clear()
    }
    
    /** @return true, если Long Poll запущен; false — ошибка токена/API (повтор бесполезен). */
    override suspend fun start(): Boolean {
        if (isRunning) return true

        while (true) {
            try {
                if (initLongPoll()) {
                    isRunning = true
                    onLog("Бот запущен и готов к работе!")
                    onStatusUpdate?.invoke("Бот работает")

                    val startTime = System.currentTimeMillis() / 1000
                    historyJob = botScope.launch { checkUnreadMessagesAlternative(startTime) }

                    startPolling()
                    return true
                } else {
                    onLog("Ошибка инициализации Long Poll: проверьте токен сообщества и включите Long Poll API в настройках сообщества")
                    return false
                }
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
    
    private suspend fun checkUnreadMessagesAlternative(startTime: Long) = withContext(Dispatchers.IO) {
        try {
            val groupId = getGroupId()
            val conversationsResponse = makeApiRequest("messages.getConversations", mapOf(
                "count" to "15",
                "extended" to "1"
            ))
            
            val conversationsObj = JSONObject(conversationsResponse)
            if (conversationsObj.has("error")) {
                val error = conversationsObj.getJSONObject("error")
                onLog("ОШИБКА API: ${error.getString("error_msg")}")
                return@withContext
            }
            
            val response = conversationsObj.getJSONObject("response")
            val conversations = response.getJSONArray("items")
            val profiles = if (response.has("profiles")) response.getJSONArray("profiles") else null
            
            if (conversations.length() == 0) return@withContext
            
            val userNames = mutableMapOf<Int, String>()
            profiles?.let { profilesArray ->
                for (i in 0 until profilesArray.length()) {
                    val profile = profilesArray.getJSONObject(i)
                    val firstName = profile.optString("first_name", "")
                    val lastName = profile.optString("last_name", "")
                    userNames[profile.getInt("id")] = "$firstName $lastName".trim()
                }
            }
            
            var totalProcessed = 0
            
            for (i in 0 until conversations.length()) {
                if (!isRunning || !isActive) break
                
                val conversation = conversations.getJSONObject(i)
                val lastMessage = conversation.getJSONObject("last_message")
                val peerId = lastMessage.getInt("peer_id")
                val fromId = lastMessage.getInt("from_id")
                val messageId = lastMessage.getInt("id")
                val outgoing = lastMessage.optInt("out", 0)
                val date = lastMessage.optLong("date", 0)
                
                if (fromId > 0 && outgoing == 0) {
                    val userInfo = userNames[fromId]?.let { fullName ->
                        val info = UserInfo(fullName.split(" ").firstOrNull() ?: fullName, fullName)
                        userCache[fromId] = info
                        info
                    } ?: getUserInfo(fromId)
                    
                    val messageAge = startTime - date
                    if (date >= startTime || messageAge > 86400) continue
                    
                    if (!checkIfMessageHasReply(peerId, messageId, groupId.toInt())) {
                        onLog("✓ Обработка пропущенного от ${userInfo.fullName}")
                        if (processMessage(lastMessage)) {
                            totalProcessed++
                            markAsRead(peerId, userInfo.fullName)
                        }
                        delay(300)
                        if (totalProcessed >= 15) break
                    }
                }
            }
        } catch (e: Exception) {
            onLog("ОШИБКА истории: ${e.message}")
        }
    }
    
    private suspend fun checkIfMessageHasReply(peerId: Int, messageId: Int, groupId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val historyResponse = makeApiRequest("messages.getHistory", mapOf(
                "peer_id" to peerId.toString(),
                "count" to "10"
            ))
            
            val response = JSONObject(historyResponse).optJSONObject("response") ?: return@withContext false
            val messages = response.getJSONArray("items")
            
            var incomingMessageIndex = -1
            for (i in 0 until messages.length()) {
                if (messages.getJSONObject(i).getInt("id") == messageId) {
                    incomingMessageIndex = i
                    break
                }
            }
            
            if (incomingMessageIndex == -1) return@withContext false
            
            for (i in (incomingMessageIndex - 1) downTo 0) {
                val message = messages.getJSONObject(i)
                if (message.optInt("out", 0) == 1 || message.getInt("from_id") == -groupId) {
                    return@withContext true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }
    
    private suspend fun markAsRead(peerId: Int, userName: String = "User") = withContext(Dispatchers.IO) {
        try {
            makeApiRequest("messages.markAsRead", mapOf("peer_id" to peerId.toString()))
            onLog("Сообщения от $userName прочитаны")
        } catch (e: Exception) {
            onLog("Ошибка markAsRead: ${e.message}")
        }
    }
    
    private suspend fun getGroupId(): String = withContext(Dispatchers.IO) {
        cachedGroupId?.let { return@withContext it }
        try {
            val response = makeApiRequest("groups.getById", mapOf("fields" to "can_message"))
            val responseObj = JSONObject(response)
            
            if (responseObj.has("error")) {
                val error = responseObj.getJSONObject("error")
                throw Exception("API Error: ${error.getString("error_msg")}")
            }
            
            val group = responseObj.getJSONArray("response").getJSONObject(0)
            val groupId = group.get("id").toString()
            cachedGroupId = groupId
            groupId
        } catch (e: Exception) {
            onLog("Ошибка ID группы: ${e.message}")
            throw e
        }
    }
    
    override fun stop() {
        isRunning = false
        pollingJob?.cancel()
        historyJob?.cancel()
        botScope.cancel()
        onLog("Бот остановлен")
    }
    
    private suspend fun initLongPoll(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = makeApiRequest("groups.getLongPollServer", mapOf("group_id" to getGroupId()))
            val data = JSONObject(response).optJSONObject("response") ?: return@withContext false
            
            server = data.getString("server")
            key = data.getString("key")
            ts = data.get("ts").toString()
            true
        } catch (e: Exception) {
            if (NetworkHelper.isNetworkError(e)) throw e
            false
        }
    }
    
    private fun startPolling() {
        pollingJob = botScope.launch {
            while (isRunning) {
                try {
                    val updates = getLongPollUpdates()
                    processUpdates(updates)
                    onStatusUpdate?.invoke("Бот работает")
                } catch (e: Exception) {
                    if (NetworkHelper.isNetworkError(e)) {
                        onLog("Ожидание сети...")
                        onStatusUpdate?.invoke("Ожидание сети...")
                        onWaitForNetwork?.invoke()
                    } else {
                        delay(5000)
                    }
                }
            }
        }
    }
    
    private suspend fun getLongPollUpdates(): JSONObject = withContext(Dispatchers.IO) {
        val url = "$server?act=a_check&key=$key&ts=$ts&wait=25"
        var lastException: Exception? = null

        for (attempt in 1..3) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 30000
                    connection.readTimeout = 35000
                    return@withContext JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                lastException = e
                if (NetworkHelper.isNetworkError(e)) {
                    onWaitForNetwork?.invoke()
                } else if (attempt < 3) {
                    delay(2000L)
                }
            }
        }
        throw lastException ?: Exception("Poll failed")
    }
    
    private val spamMap = HashMap<Int, MutableList<Long>>()
    private val spamLock = Any()

    private fun isSpam(userId: Int): Boolean {
        val now = System.currentTimeMillis()
        synchronized(spamLock) {
            val timestamps = spamMap.getOrPut(userId) { mutableListOf() }
            timestamps.removeAll { now - it > 4000 }
            timestamps.add(now)
            return timestamps.size > SettingsManager.spamLimit
        }
    }
    
    private suspend fun sendTyping(peerId: Int) {
        try {
            makeApiRequest("messages.setActivity", mapOf(
                "peer_id" to peerId.toString(),
                "type" to "typing"
            ))
        } catch (_: Exception) {}
    }

    private suspend fun processUpdates(updates: JSONObject) {
        try {
            if (updates.has("failed")) {
                when (updates.getInt("failed")) {
                    1 -> ts = updates.get("ts").toString()
                    // Без паузы при неудаче переподключения цикл опроса крутился бы вхолостую
                    2, 3 -> if (!initLongPoll()) delay(5000)
                }
                return
            }
            
            ts = updates.get("ts").toString()
            val updatesArray = updates.getJSONArray("updates")
            
            for (i in 0 until updatesArray.length()) {
                val update = updatesArray.getJSONObject(i)
                if (update.getString("type") == "message_new") {
                    val message = update.getJSONObject("object").getJSONObject("message")
                    botScope.launch { processMessage(message) }
                }
            }
        } catch (_: Exception) {}
    }
    
    private suspend fun processMessage(message: JSONObject): Boolean {
        try {
            var text = message.optString("text", "")
            val fromId = message.getInt("from_id")
            // Отрицательный from_id — сообщество/другой бот: не отвечаем, чтобы не зациклиться
            if (fromId <= 0) return true
            val peerId = message.getInt("peer_id")
            val isChat = peerId > 2000000000
            val attachmentTypes = parseAttachmentTypes(message.optJSONArray("attachments"))

            if (SettingsManager.isMarkAsReadEnabled) {
                botScope.launch { markAsRead(peerId) }
            }
            
            if (isChat) {
                if (!SettingsManager.isChatsEnabled) return true
                val prefix = SettingsManager.chatPrefix.lowercase()
                if (!text.lowercase().startsWith(prefix)) return true
                text = text.substring(prefix.length).trim()
                if (text.isEmpty() && attachmentTypes.isEmpty()) return true 
            }
            
            if (BlacklistManager.isBlacklisted(fromId.toLong())) return true
            
            if (SettingsManager.isAntiSpamEnabled && isSpam(fromId)) {
                if (SettingsManager.isAutoBanEnabled) {
                    BlacklistManager.add(fromId.toLong(), getUserInfo(fromId).fullName)
                }
                return true
            }
            
            val userInfo = getUserInfo(fromId)
            val messageMap = mapOf(
                "text" to text,
                "raw_text" to text,
                "from_id" to fromId,
                "peer_id" to peerId,
                "first_name" to userInfo.firstName,
                "attachment_types" to attachmentTypes
            )
            
            // «Печатает…» — сразу и пока ищется ответ (ИИ может думать несколько секунд)
            val started = System.currentTimeMillis()
            // Первый статус — сразу (иначе при быстром ответе корутина отменится раньше, чем его отправит)
            sendTyping(peerId)
            val typingJob = botScope.launch {
                while (isActive) { delay(VK_TYPING_REFRESH_MS); sendTyping(peerId) }
            }
            val processorResult = try { messageProcessor?.invoke(messageMap) } finally { typingJob.cancel() }
            if (processorResult != null) {
                val response = processorResult["text"] as? String
                @Suppress("UNCHECKED_CAST")
                val attachments = (processorResult["attachments"] as? List<Attachment>) ?: emptyList()
                
                if (response != null) {
                    // Имитация набора: дополняем до TYPING_MS, если ответ нашёлся быстрее
                    val remaining = TYPING_MS - (System.currentTimeMillis() - started)
                    if (remaining > 0) delay(remaining)
                    val replyToMsgId = if (isChat) message.optInt("conversation_message_id", 0) else message.optInt("id", 0)
                    return sendMessageWithAttachments(peerId, response, attachments, userInfo.fullName, replyToMsgId, isChat)
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Process error", e)
            return false
        }
    }
    
    private suspend fun sendMessageWithAttachments(
        peerId: Int, 
        text: String, 
        attachments: List<Attachment>, 
        userName: String,
        replyToMsgId: Int,
        isChat: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (text.trim().isEmpty() && attachments.isEmpty()) return@withContext false

            // Картинки по ссылкам загружаются в VK, прочие ссылки идут в текст (VK покажет превью)
            val media = mediaUploader.resolve(attachments, peerId)
            val fullText = (listOf(text.trim()) + media.links).filter { it.isNotEmpty() }.joinToString("\n")

            val params = mutableMapOf(
                "peer_id" to peerId.toString(),
                "message" to fullText,
                "random_id" to (0..Int.MAX_VALUE).random().toString()
            )
            
            if (replyToMsgId > 0) {
                val type = if (isChat) "conversation_message_ids" else "message_ids"
                params["forward"] = "{\"peer_id\":$peerId,\"$type\":[$replyToMsgId],\"is_reply\":1}"
            }
            
            if (media.vkAttachments.isNotEmpty()) {
                params["attachment"] = media.vkAttachments.joinToString(",")
            }
            
            val response = makeApiRequest("messages.send", params)
            JSONObject(response).optJSONObject("error")?.let { error ->
                onLog("Ошибка отправки $userName: ${error.optString("error_msg")} (код ${error.optInt("error_code")})")
                return@withContext false
            }

            onLog("Ответ отправлен $userName")
            return@withContext true
        } catch (e: Exception) {
            onLog("Ошибка отправки $userName: ${e.message}")
            false
        }
    }
    
    private suspend fun makeApiRequest(method: String, params: Map<String, String>): String = withContext(Dispatchers.IO) {
        val connection = URL("$VK_API_URL$method").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val postData = buildString {
                append("access_token=").append(token)
                append("&v=").append(API_VERSION)
                params.forEach { (k, v) ->
                    append("&").append(URLEncoder.encode(k, "UTF-8")).append("=").append(URLEncoder.encode(v, "UTF-8"))
                }
            }
            connection.outputStream.use { it.write(postData.toByteArray()) }
            val stream = if (connection.responseCode >= 400) connection.errorStream else connection.inputStream
            stream?.bufferedReader()?.use { it.readText() } ?: ""
        } finally {
            connection.disconnect()
        }
    }
    
    private suspend fun getUserInfo(userId: Int): UserInfo = withContext(Dispatchers.IO) {
        userCache[userId]?.let { return@withContext it }
        try {
            val response = makeApiRequest("users.get", mapOf("user_ids" to userId.toString(), "fields" to "first_name,last_name"))
            val user = JSONObject(response).getJSONArray("response").getJSONObject(0)
            val firstName = user.optString("first_name", "").trim()
            val fullName = "$firstName ${user.optString("last_name", "").trim()}".trim().ifEmpty { "User $userId" }
            val info = UserInfo(firstName.ifEmpty { "Friend" }, fullName)
            userCache[userId] = info
            info
        } catch (_: Exception) {
            UserInfo("Friend", "User $userId")
        }
    }

    private fun parseAttachmentTypes(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.optString("type")?.takeIf { it.isNotEmpty() }
        }
    }
}