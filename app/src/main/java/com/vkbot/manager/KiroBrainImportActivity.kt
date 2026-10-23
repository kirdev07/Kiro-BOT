package com.vkbot.manager

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vkbot.manager.botbrain.independent.KiroAnswerArchive
import com.vkbot.manager.botbrain.independent.KiroArchiveAudit
import java.io.File

/** Manual import only. Does not activate experimental replies or overwrite answer.txt. */
class KiroBrainImportActivity : AppCompatActivity() {
    private val chooseFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var read: Int
                while (input.read(chunk).also { read = it } != -1) {
                    require(output.size() + read <= 8 * 1024 * 1024) { "Файл больше 8 МБ" }
                    output.write(chunk, 0, read)
                }
                output.toByteArray()
            } ?: error("Не удалось открыть файл")
            val source = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
            val rows = if (source.startsWith("# KIRO-ANSWER-ARCHIVE/1")) {
                KiroAnswerArchive.parse(source)
            } else {
                KiroAnswerArchive.importLegacy(source)
            }
            require(rows.isNotEmpty()) { "Нет записей для импорта" }
            val archive = KiroAnswerArchive.export(rows)
            val audit = KiroArchiveAudit.inspect(rows)
            MaterialAlertDialogBuilder(this)
                .setTitle("Предпросмотр импорта")
                .setMessage("Всего: ${audit.total}\nГотовы к текстовому поиску: ${audit.ready}\nС вложениями: ${audit.media}\nС шаблонами: ${audit.patterns}\nС контекстом: ${audit.contextual}\nС лимитами повторов: ${audit.repeatLimits}\nПустые поля: ${audit.emptyFields}\nНекорректные лимиты: ${audit.invalidLimits}\nПовторы пар вопрос/ответ: ${audit.duplicatePairs}\n\nКатегории пересекаются. Простые шаблоны * и безопасные ссылки вложений поддерживаются экспериментально; regex-правила не исполняются.\n\nИмпортировать без изменения исходного answer.txt?")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Импортировать") { _, _ ->
                    try {
                        val target = File(filesDir, "kiro_answer_archive_v1.txt")
                        
                        if (target.exists()) {
                            val backup = File(filesDir, "kiro_answer_archive_v1.txt.bak")
                            require(target.copyTo(backup, overwrite = true).exists())
                        }
                        // AtomicFile protects the previous archive against interrupted writes.
                        val atomic = android.util.AtomicFile(target)
                        val stream = atomic.startWrite()
                        try {
                            stream.write(archive.toByteArray(Charsets.UTF_8))
                            atomic.finishWrite(stream)
                        } catch (error: Exception) {
                            atomic.failWrite(stream)
                            throw error
                        }

                        Toast.makeText(this, "Архив сохранён. Экспериментальный движок не включён.", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }.show()
        } catch (e: Exception) {
            MaterialAlertDialogBuilder(this).setTitle("Импорт не выполнен")
                .setMessage(e.message ?: "Ошибка чтения").setPositiveButton("OK", null).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 24)
        }
        layout.addView(TextView(this).apply {
            text = "Kiro Brain — импорт базы\n\nВыбери answer.txt или готовый архив. Сначала увидишь число записей и подтвердишь сохранение. Рабочая база Kiro Bot не меняется."
            textSize = 18f
        })
        layout.addView(Button(this).apply {
            text = "Выбрать файл"
            setOnClickListener { chooseFile.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) }
        })
        setContentView(layout)
    }
}
