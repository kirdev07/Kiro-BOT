package com.vkbot.manager

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.vkbot.manager.databinding.FragmentHomeBinding
import com.vkbot.manager.utils.BotDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {
    
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var sharedPrefs: SharedPreferences
    private var isServiceRunning = false
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPrefs = requireContext().getSharedPreferences("vk_bot_settings", Context.MODE_PRIVATE)
        setupUI()
    }
    
    private var statusUpdaterJob: Job? = null

    override fun onResume() {
        super.onResume()
        checkBotStatus()
        updateBotsStatusList()
        startStatusUpdater()
    }
    
    override fun onPause() {
        super.onPause()
        statusUpdaterJob?.cancel()
    }

    private fun startStatusUpdater() {
        statusUpdaterJob?.cancel()
        statusUpdaterJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                updateBotsStatusList()
            }
        }
    }
    
    private fun setupUI() {
        binding.btnStartStop.setOnClickListener {
            if (isServiceRunning) {
                stopBot()
            } else {
                startBot()
            }
        }

        binding.btnInfo.setOnClickListener {
            val visible = binding.layoutInfo.isVisible
            binding.layoutInfo.isVisible = !visible
            binding.btnInfo.setIconResource(if (!visible) R.drawable.ic_close else R.drawable.ic_info)
        }
    }
    
    private data class BotUIState(
        val name: String, 
        val isEnabled: Boolean, 
        val isServiceRunning: Boolean,
        val id: Int
    )
    
    private var lastBotsList: List<BotUIState>? = null

    /** Загружает список ботов и показывает их статус на главном экране */
    private fun updateBotsStatusList() {
        viewLifecycleOwner.lifecycleScope.launch {
            val newBotsList = withContext(Dispatchers.IO) {
                val list = mutableListOf<BotUIState>()
                val isServiceActuallyRunning = isServiceRunningInForeground(BotService::class.java)
                
                try {
                    val bots = BotDataManager.loadBots(requireContext())
                    for (bot in bots) {
                        list.add(BotUIState(bot.name, bot.isRunning, isServiceActuallyRunning, bot.id))
                    }
                } catch (_: Exception) {}
                list
            }

            if (lastBotsList == newBotsList) return@launch
            lastBotsList = newBotsList

            val container = binding.llBotsStatus
            container.removeAllViews()

            if (newBotsList.isEmpty()) {
                binding.tvNoBotsHint.isVisible = true
                return@launch
            }

            binding.tvNoBotsHint.isVisible = false
            for (state in newBotsList) {
                val card = buildBotStatusCard(state.name, state.isEnabled, state.isServiceRunning)
                container.addView(card)
            }
        }
    }

    /** Создаёт карточку-строку для одного бота */
    private fun buildBotStatusCard(name: String, isEnabled: Boolean, isServiceRunning: Boolean): View {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        val card = com.google.android.material.card.MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (8 * dp).toInt() }
            radius = 12 * dp
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.bg_card))
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
        }

        val indicator = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((4 * dp).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
                .also { it.marginEnd = (12 * dp).toInt() }
            
            val color = when {
                !isEnabled -> "#333333".toColorInt()
                isServiceRunning -> ContextCompat.getColor(ctx, R.color.status_running_green_light)
                else -> ContextCompat.getColor(ctx, R.color.status_pending_orange)
            }
            setBackgroundColor(color)
        }
        row.addView(indicator)

        val tvName = TextView(ctx).apply {
            text = name
            textSize = 15f
            setTextColor(ContextCompat.getColor(ctx, R.color.white))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(tvName)

        val tvStatus = TextView(ctx).apply {
            when {
                !isEnabled -> {
                    text = getString(R.string.status_disabled)
                    setTextColor(ContextCompat.getColor(ctx, R.color.text_medium))
                }
                isServiceRunning -> {
                    text = getString(R.string.status_working)
                    setTextColor(ContextCompat.getColor(ctx, R.color.status_running_green_light))
                }
                else -> {
                    text = getString(R.string.status_pending)
                    setTextColor(ContextCompat.getColor(ctx, R.color.status_pending_orange))
                }
            }
            textSize = 12f
        }
        row.addView(tvStatus)

        card.addView(row)
        return card
    }
    
    private fun startBot() {
        val hasActiveBots = try {
            val bots = BotDataManager.loadBots(requireContext())
            bots.any { it.isRunning && it.token.isNotEmpty() }
        } catch (_: Exception) {
            false
        }

        if (!hasActiveBots) {
            Toast.makeText(requireContext(), R.string.no_active_bots_error, Toast.LENGTH_LONG).show()
            return
        }

        addLogToFile(getString(R.string.log_init_start))

        val intent = Intent(requireContext(), BotService::class.java).apply {
            action = BotService.ACTION_START
        }

        try {
            requireContext().startForegroundService(intent)
            updateBotStatus(true)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.start_error_format, e.message), Toast.LENGTH_LONG).show()
            addLogToFile(getString(R.string.log_start_error_format, e.message))
        }
    }

    private fun stopBot() {
        try {
            val intent = Intent(requireContext(), BotService::class.java).apply {
                action = BotService.ACTION_STOP
            }
            requireContext().startService(intent)
        } catch (_: Exception) {}

        try {
            requireContext().stopService(Intent(requireContext(), BotService::class.java))
        } catch (_: Exception) {}

        val botIds = BotDataManager.loadBots(requireContext()).map { it.id }
        sharedPrefs.edit {
            putBoolean("bot_running", false)
            for (id in botIds) {
                putLong("bot_${id}_start_time", 0L)
                putLong("bot_${id}_processed", 0L)
                putLong("bot_${id}_answered", 0L)
            }
        }

        updateBotStatus(false)
        updateBotsStatusList()
        addLogToFile(getString(R.string.log_stop_command_sent))
    }
    
    private fun addLogToFile(message: String) {
        val appContext = requireContext().applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            BotService.logToFile(appContext, "UI: $message")
        }
    }
    
    private fun updateBotStatus(running: Boolean) {
        isServiceRunning = running
        val ctx = requireContext()
        
        if (running) {
            binding.btnStartStop.apply {
                text = getString(R.string.stop_bot_btn)
                setIconResource(R.drawable.ic_reconnect)
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.bg_main))
                setTextColor(ContextCompat.getColor(ctx, R.color.white))
                iconTint = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.white))
            }
        } else {
            binding.btnStartStop.apply {
                text = getString(R.string.start_bot_btn)
                setIconResource(R.drawable.ic_play)
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.white))
                setTextColor(ContextCompat.getColor(ctx, R.color.black))
                iconTint = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.black))
            }
        }
    }
    
    private fun checkBotStatus() {
        updateBotStatus(isServiceRunningInForeground(BotService::class.java))
    }
    
    @Suppress("DEPRECATION")
    private fun isServiceRunningInForeground(serviceClass: Class<*>): Boolean {
        val manager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningServices = manager.getRunningServices(Int.MAX_VALUE) ?: return false
        
        for (service in runningServices) {
            if (serviceClass.name == service.service.className) {
                return service.foreground
            }
        }
        return false
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}