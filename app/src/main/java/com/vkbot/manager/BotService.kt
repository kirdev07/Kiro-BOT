package com.vkbot.manager

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.vkbot.manager.ai.AiAssistant
import com.vkbot.manager.ai.AiTurn
import com.vkbot.manager.botbrain.BotBrain
import com.vkbot.manager.botbrain.BotMessage
import com.vkbot.manager.utils.BotNotificationHelper
import com.vkbot.manager.utils.MediaResponses
import com.vkbot.manager.utils.BotDataManager
import com.vkbot.manager.utils.BlacklistManager
import com.vkbot.manager.utils.RecentDialog
import com.vkbot.manager.utils.SettingsManager
import com.vkbot.manager.utils.NetworkHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/**
 * Фоновый сервис управления ботами.
 * Запускает ядро бота Telegram и держит его в фоне.
 */
class BotService : Service() {

    companion object {
        const val ACTION_START = "START_BOT"
        const val ACTION_STOP = "STOP_BOT"
        const val ACTION_RELOAD = "RELOAD_DATABASE"
        /** Убрать вопрос из журнала «Не знаю ответа» (текст — в [EXTRA_TEXT]). */
        const val ACTION_UNANSWERED_REMOVE = "UNANSWERED_REMOVE"
        const val EXTRA_TEXT = "text"
        const val ACTION_NOTIFICATION_DISMISSED = "NOTIFICATION_DISMISSED"
        const val NOTIFICATION_ID = 1
        private const val LOG_FILE_NAME = "bot_logs.txt"

        val logMutex = Mutex()

        suspend fun logToFile(context: Context, message: String) {
            logMutex.withLock {
                try {
                    val file = File(context.filesDir, LOG_FILE_NAME)
                    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    val line = "[$timestamp] $message\n"

                    file.appendText(line, Charsets.UTF_8)

                    if (file.length() > 1024 * 1024) {
                        file.writeText("Log cleared due to size limit\n", Charsets.UTF_8)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private lateinit var sharedPrefs: SharedPreferences
    private var botJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile private var isRunning = false
    private val activeBots = ConcurrentHashMap<Int, MessengerBot>()
    private var botBrain: BotBrain? = null
    private val syncMutex = Mutex()
    /** Токен, с которым запущен каждый бот: смена токена в UI перезапускает бота. */
    private val runningTokens = ConcurrentHashMap<Int, String>()
    /** Токен, с которым запуск не удался: не повторяем каждые 10 сек, пока токен не сменят. */
    private val failedTokens = ConcurrentHashMap<Int, String>()

    /** ИИ-помощник: отвечает, когда в базе нет ответа (настройки — экран «ИИ-помощник»). */
    private val aiAssistant = AiAssistant(
        config = { SettingsManager.aiConfig },
        usage = SettingsManager.aiUsage,
        onLog = { addLog("🤖 $it") }
    )

    /** «!запомни», «!забудь», «!незнаю» — только для владельцев из настроек; «!id» — для всех. */
    private val chatCommands = ChatCommands(
        isOwner = { id -> id in ChatCommands.parseOwnerIds(SettingsManager.ownerIds) },
        teach = { question, answer -> botBrain?.teach(question, answer) == true },
        forget = { question -> botBrain?.forget(question) ?: -1 },
        unanswered = { botBrain?.unanswered?.top() ?: emptyList() },
        answersCount = { botBrain?.answerDatabase?.answersCount ?: 0 }
    )

    private val notificationDismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_NOTIFICATION_DISMISSED && isRunning) {
                addLog("⚠️ Уведомление закрыто! Восстанавливаем...")
                updateNotification(null)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sharedPrefs = getSharedPreferences("vk_bot_settings", MODE_PRIVATE)
        BotNotificationHelper.createChannel(this)
        BlacklistManager.init(this)
        SettingsManager.init(this)
        MediaResponses.init(this)

        val filter = IntentFilter(ACTION_NOTIFICATION_DISMISSED)
        ContextCompat.registerReceiver(
            this, notificationDismissReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            if (sharedPrefs.getBoolean("bot_running", false)) {
                addLog("🔄 Система перезапустила сервис. Восстанавливаем работу...")
                startBot()
            } else {
                stopSelf()
            }
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                if (isRunning) {
                    serviceScope.launch { syncActiveBots() }
                } else {
                    startBot()
                }
            }
            ACTION_STOP -> stopBot()
            // Редактор шлёт эти команды и когда бот выключен — тогда сервис не должен оставаться висеть
            ACTION_RELOAD -> if (isRunning) reloadDatabase() else stopSelf()
            ACTION_UNANSWERED_REMOVE -> if (isRunning) {
                val text = intent.getStringExtra(EXTRA_TEXT)
                serviceScope.launch(Dispatchers.IO) {
                    // Без текста — очистить весь журнал
                    if (text == null) botBrain?.clearUnanswered() else botBrain?.forgetUnanswered(text)
                }
            } else stopSelf()
            else -> if (isRunning) updateNotification(null)
        }
        return START_STICKY
    }

    private fun startBot() {
        if (isRunning) return

        val notification = BotNotificationHelper.createForegroundNotification(this, "Запуск бота...")
        try {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (_: Exception) {
            stopSelf()
            return
        }

        clearLogs()
        sharedPrefs.edit { putBoolean("bot_running", true) }

        botJob?.cancel()
        botJob = serviceScope.launch {
            try {
                isRunning = true
                addLog("🧠 Kiro Brain 4.0: основной движок ответов")
                addLog("ℹ️ Старый BotBrain пока загружается для совместимости команд и настроек")
                val archiveReady = File(filesDir, "kiro_answer_archive_v1.txt").isFile
                val textReady = File(filesDir, "kiro_replies_v1.txt").isFile &&
                    File(filesDir, "kiro_dialogs_v1.txt").isFile
                if (!archiveReady && !textReady) {
                    addLog("⚠️ Kiro Brain: отдельная база не импортирована. Ответы из старой базы не используются.")
                }
                botBrain = BotBrain(this@BotService).apply {
                    isUnansweredLogEnabled = { SettingsManager.isUnansweredLogEnabled }
                    aiResponder = { m, turns ->
                        val history = turns.flatMap { listOf(AiTurn(true, it.userText), AiTurn(false, it.botText)) }
                        aiAssistant.reply(m.authorName, m.text, m.isGroupChat, history)
                    }
                    aiAvailable = { SettingsManager.aiConfig.let { it.enabled && it.isComplete } }
                    debugListener = { debug -> if (SettingsManager.isBrainDebugEnabled) addLog(debug.format()) }
                    personality = { SettingsManager.personality }
                    autoMood = { SettingsManager.isAutoMood }
                    reloadRules()
                    setLogCallback { message ->
                         if (!message.contains("Поиск") && !message.contains("score") && !message.contains("Индексация")) {
                             val clean = message.replace(Regex("\\[.*?]"), "")
                                                .replace(Regex("[🧠📊📁✅🔍📝🎯🌐🔗📬📩📎❌💥🤖]"), "")
                                                .trim()
                             serviceScope.launch { addLog(clean) }
                         }
                    }
                }

                val count = botBrain?.answerDatabase?.answersCount ?: 0
                addLog("📚 База загружена: $count ответов")
                sharedPrefs.edit { putInt("stat_total_answers", count) }

                startBackgroundTasks()
                syncActiveBots()

            } catch (e: Exception) {
                if (e !is CancellationException) {
                    updateNotification("Ошибка: ${e.message}")
                    addLog("❌ Критическая ошибка: ${e.message}")
                    stopBot()
                }
            }
        }
    }

    // Mutex: периодическая синхронизация и ACTION_START не должны создать два ядра на один токен
    private suspend fun syncActiveBots() = syncMutex.withLock { withContext(Dispatchers.IO) {
        val bots = BotDataManager.loadBots(this@BotService)
        val idList = bots.map { it.id }

        for (bot in bots) {
            val token = bot.token
            // Смена токена — перезапуск ядра
            val identity = token
            val shouldRun = token.isNotEmpty()
            val name = bot.name
            val id = bot.id

            if (shouldRun && activeBots.containsKey(id) && runningTokens[id] != identity) {
                addLog("🔄 Настройки $name изменены, перезапуск...")
                activeBots.remove(id)?.stop()
                runningTokens.remove(id)
            }
            if (!shouldRun) failedTokens.remove(id)

            if (shouldRun && token.isNotEmpty() && !activeBots.containsKey(id) && failedTokens[id] != identity) {
                addLog("🚀 Запуск бота Telegram $name...")

                val onLog: (String) -> Unit = { logMsg -> addLog("[$name] $logMsg") }
                val onStatus: (String) -> Unit = { text -> if (isRunning) updateNotification(text) }
                val onWait: suspend () -> Unit = { NetworkHelper.waitForNetwork(this@BotService) }
                val newBot: MessengerBot = TelegramBot(token, onLog, onStatus, onWait)

                newBot.setMessageProcessor { message -> processSmartMessage(message, id) }
                activeBots[id] = newBot
                runningTokens[id] = identity
                failedTokens.remove(id)
                // Каждый запуск — новый сеанс: счётчики «Обработано» / «Отвечено» с нуля
                sharedPrefs.edit {
                    putLong("bot_${id}_start_time", System.currentTimeMillis())
                    putLong("bot_${id}_processed", 0)
                    putLong("bot_${id}_answered", 0)
                }
                // Запуск вне блокировки: ожидание сети не должно держать syncMutex
                serviceScope.launch(Dispatchers.IO) {
                    if (!newBot.start()) {
                        syncMutex.withLock {
                            if (activeBots[id] === newBot) {
                                activeBots.remove(id)
                                runningTokens.remove(id)
                                failedTokens[id] = identity
                                sharedPrefs.edit { putLong("bot_${id}_start_time", 0) }
                            }
                        }
                        newBot.stop()
                        addLog("❌ $name не запущен. Исправьте токен (Главная → «Изменить») и запустите бота снова")
                        updateStatusNotification()
                    }
                }
            }
            else if (!shouldRun && activeBots.containsKey(id)) {
                runningTokens.remove(id)
                activeBots.remove(id)?.stop()
                sharedPrefs.edit { putLong("bot_${id}_start_time", 0) }
                addLog("⏹ $name остановлен")
            }
        }

        // Остановка ботов, которых больше нет в списке ID
        val runningIds = activeBots.keys.toList()
        for (rid in runningIds) {
            if (!idList.contains(rid)) {
                addLog("🗑 Выгрузка удаленного бота ID $rid")
                runningTokens.remove(rid)
                activeBots.remove(rid)?.stop()
            }
        }

        delay(200)
        updateStatusNotification()
    } }

    // Kiro Brain is the default reply engine in the 4.0 development branch.
    // Legacy engine remains available only through an explicit rollback preference.
    private val experimentalBrains = ConcurrentHashMap<Int, com.vkbot.manager.botbrain.independent.KiroBrainSwitch>()

    // Separate opt-in archive: a prevalidated KIRO-ANSWER-ARCHIVE/1 file.
    // The original answer.txt is never read or modified by this route.
    private val experimentalArchives =
        ConcurrentHashMap<Int, com.vkbot.manager.botbrain.independent.KiroArchiveSwitch>()
    private val experimentalArchiveVersions = ConcurrentHashMap<Int, Pair<Long, Long>>()

    private fun experimentalArchiveReply(botId: Int, sessionId: Long, text: String): com.vkbot.manager.botbrain.independent.KiroArchiveConversation.Reply? {
        val archive = File(filesDir, "kiro_answer_archive_v1.txt")
        if (!archive.isFile || archive.length() > 8L * 1024 * 1024) {
            synchronized(experimentalArchives) {
                experimentalArchives.remove(botId)
                experimentalArchiveVersions.remove(botId)
            }
            return null
        }
        val version = archive.lastModified() to archive.length()
        val engine = synchronized(experimentalArchives) {
            if (experimentalArchiveVersions[botId] != version || experimentalArchives[botId] == null) {
                val rows = try {
                    com.vkbot.manager.botbrain.independent.KiroAnswerArchive.parse(
                        archive.readText(Charsets.UTF_8)
                    )
                } catch (_: java.io.IOException) {
                    experimentalArchives.remove(botId)
                    experimentalArchiveVersions.remove(botId)
                    return null
                } catch (_: IllegalArgumentException) {
                    experimentalArchives.remove(botId)
                    experimentalArchiveVersions.remove(botId)
                    return null
                }
                experimentalArchives[botId] = com.vkbot.manager.botbrain.independent.KiroArchiveSwitch(rows)
                experimentalArchiveVersions[botId] = version
            }
            experimentalArchives.getValue(botId)
        }
        return engine.replyRich(sessionId, text)
    }

    private fun experimentalReply(botId: Int, chatId: Long, text: String): String? {
        if (!sharedPrefs.getBoolean("kiro_independent_engine_experimental", true)) return null
        val switch = experimentalBrains[botId] ?: synchronized(experimentalBrains) {
            experimentalBrains[botId] ?: run {
                val replyFile = File(filesDir, "kiro_replies_v1.txt")
                val dialogFile = File(filesDir, "kiro_dialogs_v1.txt")
                if (!replyFile.isFile || !dialogFile.isFile) return null
                val bundle = com.vkbot.manager.botbrain.independent.KiroBrainBundle.load(
                    replyFile.readText(Charsets.UTF_8),
                    dialogFile.readText(Charsets.UTF_8)
                )
                com.vkbot.manager.botbrain.independent.KiroBrainSwitch(bundle).also {
                    it.setEnabled(true)
                    experimentalBrains[botId] = it
                }
            }
        }
        return switch.reply(chatId, text)
    }

    private fun processSmartMessage(message: Map<String, Any>, botId: Int): Map<String, Any>? {
        return try {
            val text = message["text"] as? String ?: ""
            val fromId = (message["from_id"] as? Number)?.toString() ?: return null
            val authorName = (message["first_name"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: "Друг"
            @Suppress("UNCHECKED_CAST")
            val attachmentTypes = (message["attachment_types"] as? List<String>) ?: emptyList()

            val currentProcessed = sharedPrefs.getLong("bot_${botId}_processed", 0)
            sharedPrefs.edit { putLong("bot_${botId}_processed", currentProcessed + 1) }

            // Команды из чата: «/…» в Telegram теряет «/» в text, поэтому смотрим исходный текст
            val rawText = message["raw_text"] as? String ?: text
            val commandText = if (rawText.trimStart().startsWith("/")) rawText else text
            when (val command = chatCommands.handle(commandText, fromId.toLong())) {
                is ChatCommands.Result.Reply -> {
                    addLog("⌨ Команда от $authorName: ${commandText.lineSequence().first().take(60)}")
                    return mapOf("text" to command.text)
                }
                ChatCommands.Result.Ignore -> return null
                ChatCommands.Result.NotCommand -> Unit
            }

            attachmentTypes.forEach { type ->
                MediaResponses.getRandomResponse(type)?.let { response ->
                    val currentAnswered = sharedPrefs.getLong("bot_${botId}_answered", 0)
                    sharedPrefs.edit { putLong("bot_${botId}_answered", currentAnswered + 1) }
                    return mapOf("text" to response)
                }
            }

            if (text.isBlank()) return null

            // Беседа/группа: сообщение пришло не в личку (адресат ≠ автор)
            val peerId = (message["peer_id"] as? Number)?.toString()
            val isGroupChat = peerId != null && peerId != fromId
            // Keep existing command, spam, group and attachment checks above this point.
            // The new engine uses only separate, user-provided KIRO-* files.
            if (sharedPrefs.getBoolean("kiro_independent_engine_experimental", true)) {
                val isolatedChatId = (message["peer_id"] as? Number)?.toLong() ?: fromId.toLong()
                // Group users get separate dialog contexts; private chats keep chat identity.
                val sessionId = if (isGroupChat) {
                    (isolatedChatId * 31L) xor fromId.toLong()
                } else isolatedChatId
                val archiveMode = File(filesDir, "kiro_answer_archive_v1.txt").isFile
                val rich = if (archiveMode) experimentalArchiveReply(botId, sessionId, text) else null
                val independent = if (archiveMode) rich?.text else experimentalReply(botId, sessionId, text)
                if (independent != null && (independent.isNotBlank() || rich?.attachments?.isNotEmpty() == true)) {
                    val answered = sharedPrefs.getLong("bot_${botId}_answered", 0)
                    sharedPrefs.edit { putLong("bot_${botId}_answered", answered + 1) }
                    RecentDialog.add(this, authorName, text, independent.ifBlank { "📎" })
                    // TelegramBot already handles Attachment URLs and VK legacy links.
                    val media = rich?.attachments.orEmpty().map { com.vkbot.manager.botbrain.Attachment(it) }
                    return mapOf("text" to independent, "attachments" to media)
                }
                // Do not silently use legacy answers if Kiro Brain has no matching rule.
                return null
            }
            val response = botBrain?.processMessage(BotMessage(text, fromId, authorName, isGroupChat = isGroupChat, chatId = peerId.orEmpty()))
            if (response != null && !response.isEmpty) {
                val currentAnswered = sharedPrefs.getLong("bot_${botId}_answered", 0)
                sharedPrefs.edit { putLong("bot_${botId}_answered", currentAnswered + 1) }
                RecentDialog.add(this, authorName, text, response.text.ifEmpty { "📎" })

                return mapOf(
                    "text" to response.text,
                    "attachments" to response.attachments
                )
            }
            null
        } catch (e: Exception) {
            addLog("⚠ Ошибка обработки: ${e.message}")
            null
        }
    }

    private fun addLog(message: String) {
        serviceScope.launch(Dispatchers.IO) {
            logToFile(applicationContext, message)
        }
    }

    private fun clearLogs() {
        serviceScope.launch(Dispatchers.IO) {
            logMutex.withLock {
                try {
                    File(filesDir, LOG_FILE_NAME).writeText("", Charsets.UTF_8)
                } catch (_: Exception) {}
            }
        }
    }

    private fun startBackgroundTasks() {
        serviceScope.launch {
            launch {
                while (isActive && isRunning) {
                    delay(10000L)
                    syncActiveBots()
                }
            }

            launch(Dispatchers.IO) {
                // Раз в минуту: журнал «Не знаю ответа» быстро появляется в редакторе; без изменений запись не идёт
                while (isActive && isRunning) {
                    delay(60 * 1000L)
                    botBrain?.saveStats()
                }
            }

            while (isActive && isRunning) {
                delay(30 * 60 * 1000L)
                activeBots.values.forEach { it.clearUserCache() }
                updateNotification(null)
            }
        }
    }


    private fun updateStatusNotification() {
        val totalActive = activeBots.size
        val text = if (totalActive > 0) {
            val botWord = if (totalActive == 1) "бот" else if (totalActive in 2..4) "бота" else "ботов"
            "В работе $totalActive $botWord"
        } else "Все боты выключены"
        updateNotification(text)
    }

    private fun updateNotification(text: String?) {
        if (!isRunning) return
        val nm = getSystemService(NotificationManager::class.java) ?: return

        val notificationText = text ?: run {
            val total = activeBots.size
            if (total > 0) {
                val word = if (total == 1) "бот" else if (total in 2..4) "бота" else "ботов"
                "В работе $total $word"
            } else "Все боты выключены"
        }

        try {
            val notification = BotNotificationHelper.createForegroundNotification(this, notificationText)
            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    private fun reloadDatabase() {
        if (isRunning) {
            serviceScope.launch(Dispatchers.IO) {
                botBrain?.let { brain ->
                    brain.reloadDatabase()
                    val count = brain.answerDatabase?.answersCount ?: 0
                    addLog("🔄 База данных обновлена: $count ответов")
                }
                MediaResponses.loadAll()
            }
        }
    }

    private fun stopBot() {
        isRunning = false
        activeBots.values.forEach { it.stop() }
        activeBots.clear()
        runningTokens.clear()
        failedTokens.clear()
        saveStatsInBackground()
        botJob?.cancel()
        sharedPrefs.edit { putBoolean("bot_running", false) }
        addLog("⏹ Бот остановлен")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        addLog("⚙️ Приложение закрыто, бот продолжает работу в фоне")
    }

    /** serviceScope при остановке отменяется, поэтому пишем в отдельном потоке. */
    private fun saveStatsInBackground() {
        val brain = botBrain ?: return
        Thread { brain.saveStats() }.start()
    }

    override fun onDestroy() {
        isRunning = false
        activeBots.values.forEach { it.stop() }
        activeBots.clear()
        saveStatsInBackground()
        serviceScope.cancel()
        try {
            unregisterReceiver(notificationDismissReceiver)
        } catch (_: Exception) {}
        sharedPrefs.edit { putBoolean("bot_running", false) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
