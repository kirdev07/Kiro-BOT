package com.vkbot.manager.botbrain

import android.content.Context
import android.os.Environment
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Файлы базы: answer.txt, синонимы, медиа-ответы и счётчики в папке kirdev_base.
 */
class AndroidFileManager(private val context: Context) {

    private val databaseFile: File
    private val folder: File

    init {
        // Инициализируем папку по умолчанию (приватная папка приложения)
        val appDir = context.getExternalFilesDir(null)
        var tempFolder = if (appDir != null) File(appDir, FOLDER_NAME) else File(context.filesDir, FOLDER_NAME)
        var accessible = false

        // Попытка использовать общее хранилище (Legacy)
        try {
            val root = Environment.getExternalStorageDirectory()
            val legacyFolder = File(root, FOLDER_NAME)
            if (if (legacyFolder.exists()) legacyFolder.canWrite() else legacyFolder.mkdirs()) {
                tempFolder = legacyFolder
                accessible = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка доступа к внешнему хранилищу (Legacy)", e)
        }

        // Если Legacy не сработал, проверяем/создаем нашу основную папку
        if (!accessible) {
            if (!tempFolder.exists() && !tempFolder.mkdirs()) {
                Log.e(TAG, "Критическая ошибка: не удалось создать папку ${tempFolder.absolutePath}")
            }
        }

        folder = tempFolder
        databaseFile = File(folder, FILE_NAME)

        // Копируем базу из assets только если файла НЕТ
        if (!databaseFile.exists()) {
            copyDatabaseFromAssets()
        }
    }

    private fun copyAssetFile(fileName: String, targetFile: File): Boolean {
        return try {
            context.assets.open(fileName).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun copyDatabaseFromAssets() {
        if (copyAssetFile(ASSET_NAME, databaseFile)) {
            Log.i(TAG, "✅ База успешно скопирована из assets")
        } else {
            Log.e(TAG, "❌ Ошибка копирования базы из assets")
        }
    }

    fun exportDatabase(): String? {
        if (!databaseFile.exists() || databaseFile.length() == 0L) return null

        try {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val timestamp = sdf.format(Date())
            val exportFileName = "answer_backup_$timestamp.txt"

            val parent = databaseFile.parentFile ?: return null
            val exportFile = File(parent, exportFileName)

            FileInputStream(databaseFile).use { input ->
                FileOutputStream(exportFile).use { output ->
                    input.copyTo(output)
                }
            }
            return exportFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "❌ Ошибка экспорта", e)
            return null
        }
    }

    fun loadAnswerDatabase(): List<AnswerElement> {
        val answers = mutableListOf<AnswerElement>()
        val uniqueEntries = mutableSetOf<String>()

        if (!databaseFile.exists()) return answers

        try {
            databaseFile.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                var id = 1L
                for (rawLine in lines) {
                    // BOM в начале файла не считается пробелом и не убирается trim()
                    val line = rawLine.removePrefix(BOM).trim()
                    if (line.isEmpty() || line.startsWith("#")) continue

                    val parts = line.split("|", limit = 7)
                    if (parts.size >= 2) {
                        val question = unescapeText(parts[0].trim())
                        val answer = unescapeText(parts[1].trim())
                        val attachmentsStr = if (parts.size >= 3) parts[2].trim() else ""

                        val repetitionLimit = if (parts.size >= 4) tryParseInt(parts[3]) else 0
                        val usageCount = if (parts.size >= 5) tryParseInt(parts[4]) else 0
                        val reqCtx = if (parts.size >= 6) parts[5].trim() else ""
                        val resCtx = if (parts.size >= 7) parts[6].trim() else ""

                        val uniqueKey = "${question.lowercase(Locale.getDefault())}|${answer.lowercase(Locale.getDefault())}|$attachmentsStr"
                        if (!uniqueEntries.add(uniqueKey)) continue

                        val attachments = mutableListOf<Attachment>()
                        if (attachmentsStr.isNotEmpty()) {
                            for (attStr in attachmentsStr.split(",")) {
                                Attachment.parse(attStr.trim())?.let { attachments.add(it) }
                            }
                        }

                        answers.add(
                            AnswerElement(
                                id = id++,
                                questionText = question,
                                answerText = answer,
                                answerAttachments = attachments,
                                _createdDate = Date(),
                                usageCount = usageCount,
                                repetitionLimit = repetitionLimit,
                                requiredContext = reqCtx,
                                resultContext = resCtx
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка чтения БД", e)
        }
        applyUsageStats(answers, loadUsageStats())
        return answers
    }

    private val usageStatsFile: File
        get() = File(folder, USAGE_STATS_FILE_NAME)

    private fun loadUsageStats(): Map<String, Int> {
        val file = usageStatsFile
        if (!file.exists()) return emptyMap()
        return try {
            val json = JSONObject(file.readText(StandardCharsets.UTF_8))
            json.keys().asSequence().associateWith { json.optInt(it, 0) }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка чтения $USAGE_STATS_FILE_NAME", e)
            emptyMap()
        }
    }

    /** Сохраняет счётчики использования отдельно от answer.txt, чтобы не затирать правки редактора. */
    fun saveUsageStats(answers: List<AnswerElement>): Boolean {
        val tempFile = File(folder, "$USAGE_STATS_FILE_NAME.tmp")
        return try {
            tempFile.writeText(JSONObject(collectUsageStats(answers)).toString(), StandardCharsets.UTF_8)
            atomicReplace(tempFile, usageStatsFile)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сохранения $USAGE_STATS_FILE_NAME", e)
            false
        }
    }

    private val unansweredFile: File
        get() = File(folder, UNANSWERED_FILE_NAME)

    fun loadUnanswered(): UnansweredLog =
        if (unansweredFile.exists()) UnansweredLog.fromJson(runCatching { unansweredFile.readText(StandardCharsets.UTF_8) }.getOrDefault(""))
        else UnansweredLog()

    fun saveUnanswered(log: UnansweredLog): Boolean {
        val tempFile = File(folder, "$UNANSWERED_FILE_NAME.tmp")
        return try {
            tempFile.writeText(log.toJson(), StandardCharsets.UTF_8)
            atomicReplace(tempFile, unansweredFile).also { if (it) log.markSaved() }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сохранения $UNANSWERED_FILE_NAME", e)
            false
        }
    }

    /** Копирует текущую базу в answer.bak (предыдущая версия) и сохраняет новую. */
    fun saveAnswerDatabaseWithBackup(answers: List<AnswerElement>): Boolean {
        try {
            if (databaseFile.exists()) databaseFile.copyTo(File(folder, "answer.bak"), overwrite = true)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка создания бэкапа: ${e.message}")
        }
        return saveAnswerDatabase(answers)
    }

    fun saveAnswerDatabase(answers: List<AnswerElement>): Boolean {
        val parent = databaseFile.parentFile ?: return false
        val tempFile = File(parent, "$FILE_NAME.tmp")

        try {
            tempFile.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                writer.write("# База ответов Kiro Bot (UTF-8)")
                writer.newLine()
                writer.write("# Формат: ВОПРОС|ОТВЕТ|ВЛОЖЕНИЯ|ЛИМИТ|СЧЕТЧИК|REQ_CTX|RES_CTX")
                writer.newLine()

                for (element in answers) {
                    val line = String.format(
                        Locale.ROOT, "%s|%s|%s|%d|%d|%s|%s",
                        escapeText(element.questionText),
                        escapeText(element.answerText),
                        serializeAttachments(element.answerAttachments),
                        element.repetitionLimit,
                        element.usageCount,
                        element.requiredContext,
                        element.resultContext
                    )
                    writer.write(line)
                    writer.newLine()
                }
            }
            return atomicReplace(tempFile, databaseFile)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Критическая ошибка сохранения", e)
            return false
        }
    }

    private fun atomicReplace(src: File, dest: File): Boolean {
        if (src.renameTo(dest)) return true
        if (dest.exists() && !dest.delete()) return false
        return src.renameTo(dest)
    }

    private fun serializeAttachments(attachments: List<Attachment>?): String {
        if (attachments.isNullOrEmpty()) return ""
        return attachments.joinToString(",") { it.toStorageString() }
    }

    private fun tryParseInt(valStr: String): Int {
        return try {
            valStr.trim().toInt()
        } catch (_: NumberFormatException) {
            0
        }
    }

    private fun escapeText(text: String?): String {
        return text?.replace("\n", "<br>")?.replace("|", "<pipe>") ?: ""
    }

    private fun unescapeText(text: String?): String {
        return text?.replace("<br>", "\n")?.replace("<pipe>", "|")?.replace("\\n", "\n") ?: ""
    }

    fun loadTxtList(fileName: String): List<String> {
        val file = File(folder, fileName)
        if (!file.exists()) {
            if (!copyAssetFile(fileName, file)) {
                Log.e(TAG, "❌ Файл $fileName не найден в assets")
            }
        }

        val lines = mutableListOf<String>()
        if (file.exists()) {
            try {
                file.bufferedReader(StandardCharsets.UTF_8).useLines { sequence ->
                    for (line in sequence) {
                        val trim = line.removePrefix(BOM).trim()
                        if (trim.isNotEmpty() && !trim.startsWith("#")) {
                            lines.add(trim)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка чтения $fileName", e)
            }
        }
        return lines
    }

    val answerFilePath: String
        get() = databaseFile.absolutePath

    val isFileExists: Boolean
        get() = databaseFile.exists()

    companion object {
        private const val TAG = "FileManager"
        private const val FOLDER_NAME = "kirdev_base"
        private const val FILE_NAME = "answer.txt"
        private const val ASSET_NAME = "answer.txt"
        private val BOM = Char(0xFEFF).toString()
        private const val USAGE_STATS_FILE_NAME = "usage_stats.json"
        private const val UNANSWERED_FILE_NAME = "unanswered.json"

        /** Добавляет ответ в конец базы (id — следующий после максимального). */
        internal fun withAnswer(answers: List<AnswerElement>, question: String, answer: String): List<AnswerElement> =
            answers + AnswerElement(id = (answers.maxOfOrNull { it.id } ?: 0) + 1, questionText = question.trim(), answerText = answer.trim())

        /** Убирает все ответы на вопрос (без учёта регистра, знаков и ё). @return новый список и сколько убрано */
        internal fun withoutQuestion(answers: List<AnswerElement>, question: String): Pair<List<AnswerElement>, Int> {
            val key = UnansweredLog.keyOf(question)
            if (key.isEmpty()) return answers to 0
            val kept = answers.filter { UnansweredLog.keyOf(it.questionText) != key }
            return kept to (answers.size - kept.size)
        }

        /** id ответа меняется при каждой загрузке, поэтому ключ — сам вопрос и ответ. */
        internal fun usageKey(e: AnswerElement) = "${e.questionText.lowercase(Locale.ROOT)}|${e.answerText}"

        internal fun applyUsageStats(answers: List<AnswerElement>, stats: Map<String, Int>) {
            if (stats.isEmpty()) return
            for (e in answers) stats[usageKey(e)]?.let { e.usageCount = it }
        }

        internal fun collectUsageStats(answers: List<AnswerElement>): Map<String, Int> =
            answers.filter { it.usageCount > 0 }.associate { usageKey(it) to it.usageCount }
    }
}
