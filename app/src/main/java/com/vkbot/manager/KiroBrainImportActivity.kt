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
            val source = bytes.toString(Charsets.UTF_8)
            val rows = if (source.startsWith("# KIRO-ANSWER-ARCHIVE/1")) {
                KiroAnswerArchive.parse(source)
            } else {
                KiroAnswerArchive.importLegacy(source)
            }
            require(rows.isNotEmpty()) { "Нет записей для импорта" }
            val archive = KiroAnswerArchive.export(rows)
            MaterialAlertDialogBuilder(this)
                .setTitle("Предпросмотр импорта")
                .setMessage("Найдено записей: ${rows.size}.\nВложения и шаблоны сохраняются как данные, но пока не исполняются.\n\nСоздать архив Kiro Brain? Исходный answer.txt не изменится.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Импортировать") { _, _ ->
                    try {
                        val target = File(filesDir, "kiro_answer_archive_v1.txt")
                        val temp = File(filesDir, "kiro_answer_archive_v1.txt.tmp")
                        temp.writeText(archive, Charsets.UTF_8)
                        if (target.exists()) {
                            val backup = File(filesDir, "kiro_answer_archive_v1.txt.bak")
                            require(target.copyTo(backup, overwrite = true).exists())
                        }
                        require(temp.renameTo(target)) { "Не удалось сохранить архив" }
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
