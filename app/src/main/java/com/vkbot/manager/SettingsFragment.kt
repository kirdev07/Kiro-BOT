package com.vkbot.manager

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.vkbot.manager.databinding.FragmentSettingsBinding
import com.vkbot.manager.utils.SettingsManager

/**
 * Фрагмент настроек приложения.
 * Использует View Binding и KTX для чистоты кода.
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
        
        loadSettings()
        setupListeners()
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
            
            switchMarkRead.isChecked = SettingsManager.isMarkAsReadEnabled
            switchChats.isChecked = SettingsManager.isChatsEnabled
            etChatPrefix.setText(SettingsManager.chatPrefix)
            etOwnerIds.setText(SettingsManager.ownerIds)
            switchUnansweredLog.isChecked = SettingsManager.isUnansweredLogEnabled

            updateSubSettings()
        }
    }

    private fun setupListeners() {
        with(binding) {
            etOwnerIds.doAfterTextChanged { SettingsManager.ownerIds = it?.toString().orEmpty().trim() }
            switchUnansweredLog.setOnCheckedChangeListener { _, isChecked -> SettingsManager.isUnansweredLogEnabled = isChecked }

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
            
            
            switchMarkRead.setOnCheckedChangeListener { _, isChecked ->
                SettingsManager.isMarkAsReadEnabled = isChecked
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
