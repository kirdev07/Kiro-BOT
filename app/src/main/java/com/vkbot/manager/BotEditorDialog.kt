package com.vkbot.manager

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vkbot.manager.databinding.DialogAddBotBinding
import com.vkbot.manager.utils.Bot
import com.vkbot.manager.utils.BotDataManager
import com.vkbot.manager.utils.ServiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Добавление, изменение и удаление бота Telegram (приложение управляет одним ботом). */
object BotEditorDialog {

    private const val TAG = "BotEditorDialog"
    private val TELEGRAM_TOKEN = Regex("^\\d{5,}:[A-Za-z0-9_-]{30,}$")

    /** [bot] == null — добавление. [onChanged] вызывается после сохранения на диск. */
    fun show(fragment: Fragment, bot: Bot?, onChanged: () -> Unit) {
        val context = fragment.requireContext()
        val dialogBinding = DialogAddBotBinding.inflate(fragment.layoutInflater)

        bot?.let {
            dialogBinding.etBotName.setText(it.name)
            dialogBinding.etBotToken.setText(it.token)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(if (bot == null) R.string.add_bot_title else R.string.edit_bot_title)
            .setView(dialogBinding.root)
            .setPositiveButton(if (bot == null) R.string.add else R.string.save) { _, _ ->
                val name = dialogBinding.etBotName.text.toString().trim()
                val token = dialogBinding.etBotToken.text.toString().trim()

                if (name.isEmpty() || token.isEmpty()) {
                    Toast.makeText(context, R.string.fill_all_fields_error, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (!TELEGRAM_TOKEN.matches(token)) {
                    Toast.makeText(context, R.string.token_format_error_telegram, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                val saved = bot?.copy(name = name, token = token) ?: Bot(1, name, token)
                save(fragment, listOf(saved), onChanged)
                Toast.makeText(context, if (bot == null) R.string.bot_added_success else R.string.bot_updated_success, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun confirmDelete(fragment: Fragment, bot: Bot, onChanged: () -> Unit) {
        val context = fragment.requireContext()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.delete_bot_title)
            .setMessage(context.getString(R.string.delete_bot_confirm_format, bot.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                // Удалили бота у работающего сервиса — останавливаем его
                val serviceRunning = ServiceState.isBotServiceRunning(context)
                save(fragment, emptyList()) {
                    if (serviceRunning) {
                        try {
                            context.startService(Intent(context, BotService::class.java).setAction(BotService.ACTION_STOP))
                        } catch (e: Exception) {
                            Log.e(TAG, "Service sync error", e)
                        }
                    }
                    onChanged()
                }
                Toast.makeText(context, R.string.bot_deleted_success, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun save(fragment: Fragment, bots: List<Bot>, onSaved: () -> Unit) {
        val appContext: Context = fragment.requireContext().applicationContext
        fragment.lifecycleScope.launch(Dispatchers.IO) {
            BotDataManager.saveBots(appContext, bots)
            withContext(Dispatchers.Main) { if (fragment.isAdded) onSaved() }
        }
    }
}
