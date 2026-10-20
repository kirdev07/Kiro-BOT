package com.vkbot.manager

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.vkbot.manager.botbrain.AndroidFileManager
import com.vkbot.manager.databinding.FragmentHomeBinding
import com.vkbot.manager.utils.Bot
import com.vkbot.manager.utils.BotDataManager
import com.vkbot.manager.utils.RecentDialog
import com.vkbot.manager.utils.ServiceState
import com.vkbot.manager.utils.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Главная — панель управления ботом: статус и запуск, счётчики за сеанс,
 * состояние «мозга» (ИИ, характер, журнал) и последние сообщения.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedPrefs: SharedPreferences
    private var isServiceRunning = false
    private var bot: Bot? = null
    private var updaterJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPrefs = requireContext().getSharedPreferences("vk_bot_settings", Context.MODE_PRIVATE)
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
        updaterJob?.cancel()
        updaterJob = viewLifecycleOwner.lifecycleScope.launch {
            var tick = 0
            while (isActive) {
                delay(1000)
                refreshBot()
                // Журнал и последние сообщения читаются с диска — не каждую секунду
                if (++tick % 5 == 0) refreshDetails()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        updaterJob?.cancel()
    }

    private fun setupListeners() = with(binding) {
        btnStartStop.setOnClickListener { if (isServiceRunning) stopBot() else startBot() }
        btnAddBot.setOnClickListener { BotEditorDialog.show(this@HomeFragment, null) { refreshAll() } }
        btnEditBot.setOnClickListener { bot?.let { BotEditorDialog.show(this@HomeFragment, it) { refreshAll() } } }
        btnDeleteBot.setOnClickListener { bot?.let { BotEditorDialog.confirmDelete(this@HomeFragment, it) { refreshAll() } } }
        btnInfo.setOnClickListener {
            val visible = !layoutInfo.isVisible
            layoutInfo.isVisible = visible
            btnInfo.setIconResource(if (visible) R.drawable.ic_close else R.drawable.ic_info)
        }
        rowAi.setOnClickListener { (activity as? MainActivity)?.openTab(R.id.nav_ai) }
        rowPersonality.setOnClickListener { (activity as? MainActivity)?.openTab(R.id.nav_settings) }
        rowUnanswered.setOnClickListener { (activity as? MainActivity)?.openTab(R.id.nav_editor) }
    }

    private fun refreshAll() {
        refreshBot()
        refreshDetails()
    }

    /** Бот, статус и счётчики сеанса. */
    private fun refreshBot() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = requireContext().applicationContext
            val (loaded, running) = withContext(Dispatchers.IO) {
                BotDataManager.loadBots(ctx).firstOrNull() to ServiceState.isBotServiceRunning(ctx)
            }
            val b = _binding ?: return@launch
            bot = loaded
            isServiceRunning = running
            b.cardBot.isVisible = loaded != null
            b.cardNoBot.isVisible = loaded == null
            if (loaded == null) return@launch

            b.tvBotName.text = getString(R.string.home_bot_name_format, loaded.name)
            val color = ContextCompat.getColor(ctx, if (running) R.color.status_running_green_light else R.color.accent_error)
            b.viewStatusDot.backgroundTintList = ColorStateList.valueOf(color)
            b.tvStatus.setText(if (running) R.string.home_status_running else R.string.home_status_stopped)

            val startTime = if (running) sharedPrefs.getLong("bot_${loaded.id}_start_time", 0) else 0L
            b.tvProcessed.text = if (running) sharedPrefs.getLong("bot_${loaded.id}_processed", 0).toString() else "0"
            b.tvAnswered.text = if (running) sharedPrefs.getLong("bot_${loaded.id}_answered", 0).toString() else "0"
            b.tvUptime.text = formatUptime(if (startTime > 0) System.currentTimeMillis() - startTime else 0)
            updateStartButton(running)
        }
    }

    /** ИИ, характер, журнал «Не знаю ответа», последние сообщения. */
    private fun refreshDetails() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = requireContext().applicationContext
            val ai = SettingsManager.aiConfig
            val logEnabled = SettingsManager.isUnansweredLogEnabled
            val (unanswered, recent) = withContext(Dispatchers.IO) {
                val count = if (logEnabled) runCatching { AndroidFileManager(ctx).loadUnanswered().size }.getOrDefault(0) else 0
                count to RecentDialog.load(ctx)
            }
            val b = _binding ?: return@launch

            b.rowAi.text = if (ai.enabled && ai.isComplete) getString(R.string.home_ai_on, ai.provider.title) else getString(R.string.home_ai_off)
            b.rowPersonality.text = if (SettingsManager.isAutoMood) getString(R.string.home_personality_auto)
                else getString(R.string.home_personality_format, personalityTitle(SettingsManager.personality))
            b.rowUnanswered.text = if (logEnabled) getString(R.string.home_unanswered_format, unanswered) else getString(R.string.home_unanswered_off)
            showRecent(recent)
        }
    }

    private fun personalityTitle(id: String): String = getString(
        when (id) {
            "warm" -> R.string.personality_warm
            "cheeky" -> R.string.personality_cheeky
            "polite" -> R.string.personality_polite
            else -> R.string.personality_default
        }
    )

    private fun showRecent(items: List<RecentDialog.Item>) {
        val container = binding.llRecent
        container.removeAllViews()
        binding.tvRecentEmpty.isVisible = items.isEmpty()
        val ctx = requireContext()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val pad = (10 * resources.displayMetrics.density).toInt()
        for (item in items) {
            container.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, pad / 2, 0, pad / 2)
                addView(TextView(ctx).apply {
                    text = getString(R.string.home_recent_message_format, timeFormat.format(Date(item.time)), item.name, item.message)
                    setTextColor(ContextCompat.getColor(ctx, R.color.text_medium))
                    textSize = 13f
                    maxLines = 2
                })
                addView(TextView(ctx).apply {
                    text = getString(R.string.home_recent_reply_format, item.reply)
                    setTextColor(ContextCompat.getColor(ctx, R.color.white))
                    textSize = 14f
                    maxLines = 3
                })
            })
        }
    }

    private fun startBot() {
        if (bot?.token.isNullOrEmpty()) {
            Toast.makeText(requireContext(), R.string.no_active_bots_error, Toast.LENGTH_LONG).show()
            return
        }
        addLogToFile(getString(R.string.log_init_start))
        try {
            requireContext().startForegroundService(Intent(requireContext(), BotService::class.java).setAction(BotService.ACTION_START))
            updateStartButton(true)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.start_error_format, e.message), Toast.LENGTH_LONG).show()
            addLogToFile(getString(R.string.log_start_error_format, e.message))
        }
    }

    private fun stopBot() {
        val ctx = requireContext()
        try { ctx.startService(Intent(ctx, BotService::class.java).setAction(BotService.ACTION_STOP)) } catch (_: Exception) {}
        try { ctx.stopService(Intent(ctx, BotService::class.java)) } catch (_: Exception) {}

        sharedPrefs.edit {
            putBoolean("bot_running", false)
            bot?.let { putLong("bot_${it.id}_start_time", 0L) }
        }
        updateStartButton(false)
        refreshBot()
        addLogToFile(getString(R.string.log_stop_command_sent))
    }

    private fun updateStartButton(running: Boolean) {
        isServiceRunning = running
        val ctx = requireContext()
        val (bg, fg) = if (running) R.color.bg_card_highlight to R.color.white else R.color.white to R.color.black
        binding.btnStartStop.apply {
            setText(if (running) R.string.stop_bot_btn else R.string.start_bot_btn)
            setIconResource(if (running) R.drawable.ic_reconnect else R.drawable.ic_play)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, bg))
            setTextColor(ContextCompat.getColor(ctx, fg))
            iconTint = ColorStateList.valueOf(ContextCompat.getColor(ctx, fg))
        }
    }

    private fun addLogToFile(message: String) {
        val appContext = requireContext().applicationContext
        lifecycleScope.launch(Dispatchers.IO) { BotService.logToFile(appContext, "UI: $message") }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** 3 ч 05 мин → «3:05:12». */
        fun formatUptime(millis: Long): String {
            val s = millis / 1000
            return "%d:%02d:%02d".format(Locale.ROOT, s / 3600, (s / 60) % 60, s % 60)
        }
    }
}
