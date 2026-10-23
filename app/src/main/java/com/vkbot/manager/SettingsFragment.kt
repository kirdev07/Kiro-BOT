package com.vkbot.manager

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.vkbot.manager.databinding.FragmentSettingsBinding
import com.vkbot.manager.utils.SettingsManager

/**
 * Настройки. Без аргумента — меню разделов; с [ARG_SECTION] — один раздел
 * (открывается поверх меню, «назад» возвращает к нему).
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        showSection(arguments?.getString(ARG_SECTION))
        loadSettings()
        setupListeners()
        val importButton = android.widget.Button(requireContext()).apply {
            text = "Kiro Brain 4.0 — импорт базы ответов"
            setOnClickListener { startActivity(Intent(requireContext(), KiroBrainImportActivity::class.java)) }
        }
        binding.groupDeveloper.addView(importButton)
    }

    /** Раздел → группа настроек и заголовок. */
    private fun sections() = with(binding) {
        mapOf(
            SECTION_CONVERSATION to (groupConversation to R.string.settings_section_conversation),
            SECTION_CHATS to (groupChats to R.string.settings_section_chats),
            SECTION_PROTECTION to (groupProtection to R.string.settings_section_protection),
            SECTION_LEARNING to (groupLearning to R.string.settings_section_learning),
            SECTION_DEVELOPER to (groupDeveloper to R.string.settings_section_developer)
        )
    }

    private fun showSection(section: String?) = with(binding) {
        val all = sections()
        settingsMenu.isVisible = section == null
        for ((id, group) in all) group.first.isVisible = id == section
        tvSettingsTitle.setText(all[section]?.second ?: R.string.settings_title)

        val rows = mapOf(rowConversation to SECTION_CONVERSATION, rowChats to SECTION_CHATS, rowProtection to SECTION_PROTECTION,
            rowLearning to SECTION_LEARNING, rowDeveloper to SECTION_DEVELOPER)
        for ((row, id) in rows) row.setOnClickListener { (activity as? MainActivity)?.openSubScreen(newInstance(id)) }
    }

    /** Набор характера → кнопка. */
    private val personalityButtons = mapOf(
        "" to R.id.btn_personality_default, "warm" to R.id.btn_personality_warm,
        "cheeky" to R.id.btn_personality_cheeky, "polite" to R.id.btn_personality_polite
    )

    /** Пока характер меняется по настроению, ручной выбор не действует. */
    private fun updatePersonalityEnabled() = with(binding) {
        val manual = !switchAutoMood.isChecked
        togglePersonality.isEnabled = manual
        for (i in 0 until togglePersonality.childCount) togglePersonality.getChildAt(i).isEnabled = manual
        togglePersonality.alpha = if (manual) 1f else 0.4f
    }

    private fun loadSettings() {
        with(binding) {
            // Загрузка уровня анти-спама
            when {
                !SettingsManager.isAntiSpamEnabled -> toggleAntispamLevel.check(R.id.btn_antispam_off)
                SettingsManager.spamLimit >= SettingsManager.SPAM_LIMIT_NORMAL -> toggleAntispamLevel.check(R.id.btn_antispam_normal)
                else -> toggleAntispamLevel.check(R.id.btn_antispam_strict)
            }

            switchAutoban.isChecked = SettingsManager.isAutoBanEnabled

            // Загрузка стратегии ответов
            when {
                SettingsManager.isFallbackSilenceEnabled -> toggleFallbackStrategy.check(R.id.btn_fallback_silent)
                SettingsManager.isRandomFallbackEnabled -> toggleFallbackStrategy.check(R.id.btn_fallback_random)
                else -> toggleFallbackStrategy.check(R.id.btn_fallback_error)
            }
            switchChats.isChecked = SettingsManager.isChatsEnabled
            etChatPrefix.setText(SettingsManager.chatPrefix)
            etOwnerIds.setText(SettingsManager.ownerIds)
            switchUnansweredLog.isChecked = SettingsManager.isUnansweredLogEnabled
            switchBrainDebug.isChecked = SettingsManager.isBrainDebugEnabled
            togglePersonality.check(personalityButtons[SettingsManager.personality] ?: R.id.btn_personality_default)
            switchAutoMood.isChecked = SettingsManager.isAutoMood
            updatePersonalityEnabled()

            updateSubSettings()
        }
    }

    private fun setupListeners() {
        with(binding) {
            etOwnerIds.doAfterTextChanged { SettingsManager.ownerIds = it?.toString().orEmpty().trim() }
            btnOpenBlacklist.setOnClickListener { (activity as? MainActivity)?.openSubScreen(BlacklistFragment()) }
            switchUnansweredLog.setOnCheckedChangeListener { _, isChecked -> SettingsManager.isUnansweredLogEnabled = isChecked }
            switchBrainDebug.setOnCheckedChangeListener { _, isChecked -> SettingsManager.isBrainDebugEnabled = isChecked }
            switchAutoMood.setOnCheckedChangeListener { _, isChecked ->
                SettingsManager.isAutoMood = isChecked
                updatePersonalityEnabled()
            }
            togglePersonality.addOnButtonCheckedListener { _, checkedId, isChecked ->
                val id = personalityButtons.entries.firstOrNull { it.value == checkedId }?.key ?: return@addOnButtonCheckedListener
                if (isChecked && id != SettingsManager.personality) {
                    SettingsManager.personality = id
                    // Работающий бот перечитает фразы
                    runCatching { requireContext().startService(Intent(requireContext(), BotService::class.java).setAction(BotService.ACTION_RELOAD)) }
                }
            }

            toggleAntispamLevel.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    when (checkedId) {
                        R.id.btn_antispam_off -> SettingsManager.isAntiSpamEnabled = false
                        R.id.btn_antispam_normal -> {
                            SettingsManager.isAntiSpamEnabled = true
                            SettingsManager.spamLimit = SettingsManager.SPAM_LIMIT_NORMAL
                        }
                        R.id.btn_antispam_strict -> {
                            SettingsManager.isAntiSpamEnabled = true
                            SettingsManager.spamLimit = SettingsManager.SPAM_LIMIT_STRICT
                        }
                    }
                    updateSubSettings()
                }
            }

            switchAutoban.setOnCheckedChangeListener { _, isChecked ->
                SettingsManager.isAutoBanEnabled = isChecked
            }

            toggleFallbackStrategy.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    when (checkedId) {
                        R.id.btn_fallback_silent -> {
                            SettingsManager.isFallbackSilenceEnabled = true
                            SettingsManager.isRandomFallbackEnabled = false
                        }
                        R.id.btn_fallback_random -> {
                            SettingsManager.isFallbackSilenceEnabled = false
                            SettingsManager.isRandomFallbackEnabled = true
                        }
                        R.id.btn_fallback_error -> {
                            SettingsManager.isFallbackSilenceEnabled = false
                            SettingsManager.isRandomFallbackEnabled = false
                        }
                    }
                }
            }

            switchChats.setOnCheckedChangeListener { _, isChecked ->
                SettingsManager.isChatsEnabled = isChecked
                updateSubSettings()
            }

            etChatPrefix.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val value = s?.toString().orEmpty()
                    if (value.isNotBlank()) {
                        SettingsManager.chatPrefix = value
                    }
                }
            })
        }
    }

    private fun updateSubSettings() {
        if (_binding == null) return

        with(binding) {
            val isAntispamOn = toggleAntispamLevel.checkedButtonId != R.id.btn_antispam_off
            switchAutoban.isEnabled = isAntispamOn

            // Поле префикса активно только если включены беседы
            inputChatPrefixLayout.isEnabled = switchChats.isChecked
            etChatPrefix.isEnabled = switchChats.isChecked
        }
    }

    companion object {
        private const val ARG_SECTION = "section"
        private const val SECTION_CONVERSATION = "conversation"
        private const val SECTION_CHATS = "chats"
        private const val SECTION_PROTECTION = "protection"
        private const val SECTION_LEARNING = "learning"
        private const val SECTION_DEVELOPER = "developer"

        fun newInstance(section: String) = SettingsFragment().apply { arguments = Bundle().apply { putString(ARG_SECTION, section) } }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
