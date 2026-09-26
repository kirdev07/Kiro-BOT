package com.vkbot.manager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vkbot.manager.ai.AiAssistant
import com.vkbot.manager.ai.AiConfig
import com.vkbot.manager.ai.AiException
import com.vkbot.manager.ai.AiProvider
import com.vkbot.manager.ai.OpenAiCompatibleClient
import com.vkbot.manager.databinding.FragmentAiBinding
import com.vkbot.manager.utils.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Настройки ИИ-помощника: сервис, модель, ключ, характер, лимиты, проверка. */
class AiFragment : Fragment() {

    private var _binding: FragmentAiBinding? = null
    private val binding get() = _binding!!

    /** Идёт заполнение полей из настроек — не сохранять обратно. */
    private var filling = false
    private var provider = AiProvider.CLAUDE
    /** Отложенное сохранение текстовых полей — не на каждое нажатие клавиши. */
    private var pendingSave: Job? = null
    /** Загруженный список моделей для пары (адрес, ключ). */
    private var loadedModels: Pair<Pair<String, String>, List<String>>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.ddProvider.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, AiProvider.entries.map { it.title }))
        binding.etBaseUrl.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, AiProvider.BASE_URL_PRESETS))
        fill(SettingsManager.aiConfig)
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateUsage()
    }

    private fun fill(c: AiConfig) = with(binding) {
        filling = true
        provider = c.provider
        switchAiEnabled.isChecked = c.enabled
        ddProvider.setText(c.provider.title, false)
        etModel.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, c.provider.models))
        etModel.setText(c.model, false)
        etApiKey.setText(c.apiKey)
        etBaseUrl.setText(c.baseUrl, false)
        etFolderId.setText(c.folderId)
        etPersona.setText(c.persona)
        etMaxChars.setText(c.maxChars.toString())
        etDailyLimit.setText(c.dailyLimit.toString())
        switchAiInChats.isChecked = c.inChats
        updateProviderViews()
        filling = false
    }

    private fun updateProviderViews() = with(binding) {
        tilBaseUrl.isVisible = provider.needsBaseUrl
        tilFolderId.isVisible = provider.needsFolderId
        tilApiKey.hint = getString(if (provider.keyOptional) R.string.ai_api_key_optional else R.string.ai_api_key)
        tvProviderHelp.setText(
            when (provider) {
                AiProvider.CLAUDE -> R.string.ai_help_claude
                AiProvider.GEMINI -> R.string.ai_help_gemini
                AiProvider.YANDEX -> R.string.ai_help_yandex
                AiProvider.OPENAI_COMPATIBLE -> R.string.ai_help_openai
            }
        )
    }

    private fun setupListeners() = with(binding) {
        ddProvider.setOnItemClickListener { _, _, position, _ ->
            val chosen = AiProvider.entries[position]
            if (chosen == provider) return@setOnItemClickListener
            save()
            // У каждого сервиса свои ключ и модель
            SettingsManager.aiConfig = SettingsManager.aiConfigFor(chosen)
            fill(SettingsManager.aiConfig)
        }
        // Подсказки открываются по нажатию, а не только при вводе
        etModel.setOnClickListener {
            if (provider == AiProvider.OPENAI_COMPATIBLE) loadModels() else if (provider.models.isNotEmpty()) etModel.showDropDown()
        }
        etBaseUrl.setOnClickListener { etBaseUrl.showDropDown() }

        switchAiEnabled.setOnCheckedChangeListener { _, _ -> save() }
        switchAiInChats.setOnCheckedChangeListener { _, _ -> save() }
        listOf(etModel, etApiKey, etBaseUrl, etFolderId, etPersona, etMaxChars, etDailyLimit).forEach { field ->
            field.doAfterTextChanged { saveLater() }
        }
        btnAiTest.setOnClickListener { runTest() }
    }

    private fun currentConfig(): AiConfig = with(binding) {
        AiConfig(
            enabled = switchAiEnabled.isChecked,
            provider = provider,
            model = etModel.text.toString().trim(),
            apiKey = etApiKey.text.toString().trim(),
            baseUrl = etBaseUrl.text.toString().trim(),
            folderId = etFolderId.text.toString().trim(),
            persona = etPersona.text.toString(),
            maxChars = etMaxChars.text.toString().toIntOrNull() ?: 300,
            dailyLimit = etDailyLimit.text.toString().toIntOrNull() ?: 0,
            inChats = switchAiInChats.isChecked
        )
    }

    private fun saveLater() {
        if (filling) return
        pendingSave?.cancel()
        pendingSave = viewLifecycleOwner.lifecycleScope.launch {
            delay(SAVE_DELAY_MS)
            save()
        }
    }

    private fun save() {
        pendingSave?.cancel()
        if (filling || _binding == null) return
        SettingsManager.aiConfig = currentConfig()
        updateUsage()
    }

    private fun updateUsage() {
        val b = _binding ?: return
        b.tvUsageToday.text = getString(R.string.ai_usage_today, SettingsManager.aiUsage.today(), currentConfig().dailyLimit)
    }

    /** Модели любого OpenAI-совместимого сервиса загружаются по ключу — их не нужно искать в кабинете. */
    private fun loadModels() {
        val config = currentConfig()
        if (config.baseUrl.isBlank()) {
            Toast.makeText(requireContext(), R.string.ai_models_need_url, Toast.LENGTH_SHORT).show()
            return
        }
        val key = config.baseUrl to config.apiKey
        loadedModels?.takeIf { it.first == key }?.let { showModels(it.second); return }
        binding.tilModel.helperText = getString(R.string.ai_models_loading)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { OpenAiCompatibleClient.listModels(config.baseUrl, config.apiKey) } }
            val b = _binding ?: return@launch
            b.tilModel.helperText = null
            result.onSuccess { models ->
                loadedModels = key to models
                showModels(models)
            }.onFailure { e ->
                Toast.makeText(requireContext(), getString(R.string.ai_models_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showModels(models: List<String>) {
        if (models.isEmpty()) {
            Toast.makeText(requireContext(), R.string.ai_models_empty, Toast.LENGTH_SHORT).show()
            return
        }
        binding.etModel.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, models))
        binding.etModel.showDropDown()
    }

    private fun runTest() {
        val config = currentConfig()
        binding.btnAiTest.isEnabled = false
        binding.btnAiTest.setText(R.string.ai_testing)
        viewLifecycleOwner.lifecycleScope.launch {
            val started = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                runCatching { AiAssistant(config = { config }, usage = SettingsManager.aiUsage).test(config) }
            }
            val seconds = (System.currentTimeMillis() - started) / 1000.0
            binding.btnAiTest.isEnabled = true
            binding.btnAiTest.setText(R.string.ai_test)
            val dialog = MaterialAlertDialogBuilder(requireContext()).setPositiveButton(android.R.string.ok, null)
            result.onSuccess { answer ->
                dialog.setTitle(R.string.ai_test_ok_title).setMessage(getString(R.string.ai_test_ok_format, answer, seconds))
            }.onFailure { e ->
                val reason = if (e is AiException) e.message else "${e.javaClass.simpleName}: ${e.message}"
                dialog.setTitle(R.string.ai_test_fail_title).setMessage(reason)
            }
            dialog.show()
        }
    }

    override fun onPause() {
        super.onPause()
        // Уходим с экрана — сохраняем то, что ещё ждёт задержки
        if (pendingSave?.isActive == true) save()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val SAVE_DELAY_MS = 400L
    }
}
