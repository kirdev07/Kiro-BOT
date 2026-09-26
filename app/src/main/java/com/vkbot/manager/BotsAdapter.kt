package com.vkbot.manager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.vkbot.manager.databinding.ItemBotBinding
import com.vkbot.manager.utils.Bot
import java.util.Locale

/**
 * Адаптер для списка ботов.
 * Карточки ботов: статус, статистика, время работы, кнопки управления.
 */
class BotsAdapter(
    private val onEdit: (Bot) -> Unit,
    private val onDelete: (Bot) -> Unit,
    private val onToggle: (Bot) -> Unit,
    private val onClearStats: (Bot) -> Unit
) : RecyclerView.Adapter<BotsAdapter.BotViewHolder>() {
    
    private var bots = listOf<Bot>()
    
    fun updateBots(newBots: List<Bot>) {
        // Храним копии: фрагмент меняет Bot на месте, и без копий DiffUtil не увидит изменений
        val snapshot = newBots.map { it.copy() }
        val diffCallback = BotDiffCallback(bots, snapshot)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        bots = snapshot
        diffResult.dispatchUpdatesTo(this)
    }
    
    class BotViewHolder(private val binding: ItemBotBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(
            bot: Bot,
            onEdit: (Bot) -> Unit,
            onDelete: (Bot) -> Unit,
            onToggle: (Bot) -> Unit,
            onClearStats: (Bot) -> Unit
        ) {
            val context = itemView.context
            
            with(binding) {
                tvBotName.text = bot.name
                
                // Улучшенное скрытие токена
                val tokenPreview = if (bot.token.length > 10) "${bot.token.take(8)}..." else "—"
                tvBotTokenHint.text = context.getString(R.string.bot_token_preview_format, bot.platform.title, tokenPreview)
                
                llCopyToken.setOnClickListener {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("vk_token", bot.token)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, R.string.token_copied_toast, Toast.LENGTH_SHORT).show()
                }
                
                llStatistics.setOnLongClickListener {
                    onClearStats(bot)
                    true
                }
                
                // Динамический статус и индикатор
                if (bot.isRunning) {
                    tvBotStatus.text = context.getString(R.string.bot_status_running)
                    tvBotStatus.setTextColor(ContextCompat.getColor(context, R.color.status_running_green_light))
                    viewStatusIndicator.setBackgroundColor(ContextCompat.getColor(context, R.color.white))
                    
                    btnToggleBot.apply {
                        text = context.getString(R.string.action_disable)
                        setTextColor(ContextCompat.getColor(context, R.color.white))
                        backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.bg_main))
                    }
                } else {
                    tvBotStatus.text = context.getString(R.string.bot_status_stopped)
                    tvBotStatus.setTextColor(ContextCompat.getColor(context, R.color.text_medium))
                    viewStatusIndicator.setBackgroundColor("#222222".toColorInt())
                    
                    btnToggleBot.apply {
                        text = context.getString(R.string.action_enable)
                        setTextColor(ContextCompat.getColor(context, R.color.black))
                        backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white))
                    }
                }
                
                // Статистика
                tvBotProcessed.text = context.getString(R.string.processed_messages_format, bot.processedMessages)
                tvBotAnswered.text = context.getString(R.string.answered_messages_format, bot.answeredMessages)
                
                // Время работы (uptime)
                if (bot.isRunning && bot.startTime > 0) {
                    val uptimeMillis = System.currentTimeMillis() - bot.startTime
                    tvBotUptime.text = context.getString(R.string.uptime_format, formatDuration(uptimeMillis))
                } else {
                    tvBotUptime.text = context.getString(R.string.uptime_zero)
                }
                
                btnToggleBot.setOnClickListener { onToggle(bot) }
                btnEditBot.setOnClickListener { onEdit(bot) }
                btnDeleteBot.setOnClickListener { onDelete(bot) }
            }
        }
        
        private fun formatDuration(millis: Long): String {
            val seconds = (millis / 1000) % 60
            val minutes = (millis / (1000 * 60)) % 60
            val hours = (millis / (1000 * 60 * 60))
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BotViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return BotViewHolder(ItemBotBinding.inflate(layoutInflater, parent, false))
    }
    
    override fun onBindViewHolder(holder: BotViewHolder, position: Int) {
        holder.bind(bots[position], onEdit, onDelete, onToggle, onClearStats)
    }
    
    override fun getItemCount() = bots.size

    private class BotDiffCallback(
        private val oldList: List<Bot>,
        private val newList: List<Bot>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size
        
        override fun areItemsTheSame(oldPos: Int, newPos: Int) = oldList[oldPos].id == newList[newPos].id
        
        override fun areContentsTheSame(oldPos: Int, newPos: Int) = oldList[oldPos] == newList[newPos]
    }
}
