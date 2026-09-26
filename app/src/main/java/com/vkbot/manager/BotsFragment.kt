package com.vkbot.manager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vkbot.manager.databinding.DialogAddBotBinding
import com.vkbot.manager.databinding.FragmentBotsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vkbot.manager.utils.BotDataManager
import com.vkbot.manager.utils.Bot
import com.vkbot.manager.utils.BotPlatform


/**
 * Фрагмент управления списком ботов.
 */
class BotsFragment : Fragment() {
    
    private var _binding: FragmentBotsBinding? = null
    private val binding get() = _binding!!
    
    private val bots = mutableListOf<Bot>()
    private lateinit var adapter: BotsAdapter
    
    companion object {
        private const val TAG = "BotsFragment"
        private val TELEGRAM_TOKEN = Regex("^\\d{5,}:[A-Za-z0-9_-]{30,}$")
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBotsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupListeners()
        
        // Загружаем данные с учетом миграции
        loadBots()
        
        // Запускаем обновление статистики
        startStatsUpdater()
    }
    
    private fun setupRecyclerView() {
        adapter = BotsAdapter(::onEditBot, ::onDeleteBot, ::onToggleBot, ::onClearStats)
        binding.recyclerViewBots.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@BotsFragment.adapter
        }
    }
    
    private fun setupListeners() {
        binding.fabAddBot.setOnClickListener {
            if (bots.size >= BotDataManager.MAX_BOTS) {
                Toast.makeText(requireContext(), R.string.max_bots_error, Toast.LENGTH_SHORT).show()
            } else {
                showBotDialog(null)
            }
        }
    }
    
    private fun loadBots() {
        viewLifecycleOwner.lifecycleScope.launch {
            val loadedBots = withContext(Dispatchers.IO) {
                BotDataManager.loadBots(requireContext())
            }
            
            Log.d(TAG, "Loaded ${loadedBots.size} bots")
            bots.clear()
            bots.addAll(loadedBots)
            adapter.updateBots(bots.toList())
            updateUIState()
        }
    }
    
    
    /** [onSaved] вызывается в главном потоке после записи — сервис должен читать уже новое состояние. */
    private fun saveBots(onSaved: ((Context) -> Unit)? = null) {
        val appContext = requireContext().applicationContext
        val botsCopy = bots.map { it.copy() }

        lifecycleScope.launch(Dispatchers.IO) {
            BotDataManager.saveBots(appContext, botsCopy)
            if (onSaved != null) withContext(Dispatchers.Main) { onSaved(appContext) }
        }
    }
    
    private fun updateUIState() {
        val isEmpty = bots.isEmpty()
        binding.emptyStateBots.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerViewBots.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.fabAddBot.visibility = if (bots.size < BotDataManager.MAX_BOTS) View.VISIBLE else View.GONE
        binding.tvBotsCounter.text = getString(R.string.bots_counter_format, bots.size)
    }
    
    private fun showBotDialog(bot: Bot?) {
        val context = requireContext()
        val dialogBinding = DialogAddBotBinding.inflate(layoutInflater)
        
        bot?.let {
            dialogBinding.etBotName.setText(it.name)
            dialogBinding.etBotToken.setText(it.token)
        }

        // Выбор платформы: подсказка к токену объясняет, где его взять
        fun selectedPlatform() =
            if (dialogBinding.togglePlatform.checkedButtonId == R.id.btn_platform_telegram) BotPlatform.TELEGRAM else BotPlatform.VK
        fun updateTokenHint() {
            val telegram = selectedPlatform() == BotPlatform.TELEGRAM
            dialogBinding.tilBotToken.hint = getString(if (telegram) R.string.token_hint_telegram else R.string.token_hint_vk)
            dialogBinding.tilBotToken.helperText = getString(if (telegram) R.string.token_help_telegram else R.string.token_help_vk)
        }
        dialogBinding.togglePlatform.check(
            if (bot?.platform == BotPlatform.TELEGRAM) R.id.btn_platform_telegram else R.id.btn_platform_vk
        )
        updateTokenHint()
        dialogBinding.togglePlatform.addOnButtonCheckedListener { _, _, isChecked -> if (isChecked) updateTokenHint() }

        MaterialAlertDialogBuilder(context)
            .setTitle(if (bot == null) R.string.add_bot_title else R.string.edit_bot_title)
            .setView(dialogBinding.root)
            .setPositiveButton(if (bot == null) R.string.add else R.string.save) { _, _ ->
                val name = dialogBinding.etBotName.text.toString().trim()
                val token = dialogBinding.etBotToken.text.toString().trim()
                val platform = selectedPlatform()

                if (name.isEmpty() || token.isEmpty()) {
                    Toast.makeText(context, R.string.fill_all_fields_error, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (platform == BotPlatform.TELEGRAM && !TELEGRAM_TOKEN.matches(token)) {
                    Toast.makeText(context, R.string.token_format_error_telegram, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                if (bots.any { it.id != bot?.id && it.name.equals(name, ignoreCase = true) }) {
                    Toast.makeText(context, R.string.bot_name_exists_error, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (bot == null) {
                    val newId = (bots.maxOfOrNull { it.id } ?: 0) + 1
                    bots.add(Bot(newId, name, token, platform = platform))
                    Toast.makeText(context, R.string.bot_added_success, Toast.LENGTH_SHORT).show()
                } else {
                    bot.name = name
                    bot.token = token
                    bot.platform = platform
                    Toast.makeText(context, R.string.bot_updated_success, Toast.LENGTH_SHORT).show()
                }
                
                saveBots()
                adapter.updateBots(bots.toList())
                updateUIState()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    /** Адаптер отдаёт копию — находим настоящий объект из списка фрагмента. */
    private fun findBot(clicked: Bot): Bot? = bots.find { it.id == clicked.id }

    private fun onEditBot(clicked: Bot) {
        findBot(clicked)?.let { showBotDialog(it) }
    }

    private fun onDeleteBot(clicked: Bot) {
        val bot = findBot(clicked) ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_bot_title)
            .setMessage(getString(R.string.delete_bot_confirm_format, bot.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                bots.remove(bot)
                val anyRunning = bots.any { it.isRunning }
                // Уведомляем сервис после сохранения
                saveBots { ctx ->
                    val intent = Intent(ctx, BotService::class.java).apply {
                        action = if (anyRunning) BotService.ACTION_START else BotService.ACTION_STOP
                    }
                    try {
                        if (anyRunning) ctx.startForegroundService(intent) else ctx.startService(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Service sync error", e)
                    }
                }
                adapter.updateBots(bots.toList())
                updateUIState()

                Toast.makeText(requireContext(), R.string.bot_deleted_success, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun onClearStats(clicked: Bot) {
        val bot = findBot(clicked) ?: return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clear_stats_title)
            .setMessage(getString(R.string.clear_stats_confirm_format, bot.name))
            .setPositiveButton(R.string.reset) { _, _ ->
                bot.processedMessages = 0
                bot.answeredMessages = 0
                
                requireContext().getSharedPreferences("vk_bot_settings", Context.MODE_PRIVATE).edit {
                    putLong("bot_${bot.id}_processed", 0)
                    putLong("bot_${bot.id}_answered", 0)
                }
                
                saveBots()
                adapter.updateBots(bots.toList())
                Toast.makeText(requireContext(), R.string.stats_cleared_success, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun onToggleBot(clicked: Bot) {
        val bot = findBot(clicked) ?: return
        bot.isRunning = !bot.isRunning
        val status = if (bot.isRunning) R.string.status_auto_start_on else R.string.status_auto_start_off
        Toast.makeText(requireContext(), getString(R.string.bot_status_changed_format, bot.name, getString(status)), Toast.LENGTH_SHORT).show()
        
        // Синхронизация с сервисом сразу после сохранения
        saveBots { ctx ->
            val intent = Intent(ctx, BotService::class.java).apply { action = BotService.ACTION_START }
            try {
                ctx.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Service sync error", e)
            }
        }
        adapter.updateBots(bots.toList())
    }
    
    private fun startStatsUpdater() {
        lifecycleScope.launch {
            val prefs = requireContext().getSharedPreferences("vk_bot_settings", Context.MODE_PRIVATE)
            while (isActive) {
                delay(1000)
                if (!isAdded) continue
                
                var changed = false
                bots.forEach { bot ->
                    val keyId = bot.id
                    val processed = prefs.getLong("bot_${keyId}_processed", 0)
                    val answered = prefs.getLong("bot_${keyId}_answered", 0)
                    val isRunning = prefs.getBoolean("bot_${keyId}_running", false)
                    val startTime = prefs.getLong("bot_${keyId}_start_time", 0)
                    
                    if (bot.processedMessages != processed || bot.answeredMessages != answered || 
                        bot.isRunning != isRunning || bot.startTime != startTime) {
                        
                        bot.processedMessages = processed
                        bot.answeredMessages = answered
                        bot.isRunning = isRunning
                        bot.startTime = startTime
                        changed = true
                    }
                }
                
                if (changed) {
                    adapter.updateBots(bots.toList())
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
