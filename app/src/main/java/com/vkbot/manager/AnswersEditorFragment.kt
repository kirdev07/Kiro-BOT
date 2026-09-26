package com.vkbot.manager

import android.content.res.ColorStateList

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vkbot.manager.databinding.FragmentAnswersEditorBinding
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.isVisible
import androidx.core.graphics.toColorInt
import com.vkbot.manager.botbrain.AndroidFileManager
import com.vkbot.manager.botbrain.AnswerElement
import com.vkbot.manager.botbrain.Attachment
import com.vkbot.manager.botbrain.UnansweredLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnswersEditorFragment : Fragment() {
    
    private var _binding: FragmentAnswersEditorBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var answersAdapter: AnswersAdapter
    private lateinit var fileManager: AndroidFileManager
    private var allAnswers = mutableListOf<AnswerElement>()
    private var searchJob: Job? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnswersEditorBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            sharedPrefs = requireContext().getSharedPreferences("vk_bot_settings", Context.MODE_PRIVATE)
            fileManager = AndroidFileManager(requireContext())
            
            setupRecyclerView()
            setupUI()
            loadAnswers()
            setupBackPressHandler()
        } catch (e: Exception) {
            Log.e("AnswersEditor", "❌ CRASH in onViewCreated: ${e.message}", e)
            Toast.makeText(requireContext(), "Ошибка инициализации: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (answersAdapter.isSelectionMode) {
                    exitSelectionMode()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
    
    private fun setupRecyclerView() {
        answersAdapter = AnswersAdapter(
            onEditClick = { answer -> 
                if (!answersAdapter.isSelectionMode) {
                    showEditDialog(answer)
                }
            },
            onSelectionModeChanged = { _ -> updateSelectionPanel() },
            onSelectionUpdate = { updateSelectionPanel() }
        )
        
        binding.recyclerViewAnswers.apply {
            adapter = answersAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
        
        // НОВОЕ: Настройка свайпов
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                
                if (answersAdapter.isSelectionMode) {
                    answersAdapter.notifyItemChanged(pos)
                    return
                }
                
                val answer = answersAdapter.getItemAt(pos)
                answersAdapter.notifyItemChanged(pos) // Возвращаем визуально на место
                
                if (direction == ItemTouchHelper.LEFT) {
                    showDeleteDialog(answer)
                } else if (direction == ItemTouchHelper.RIGHT) {
                    showEditDialog(answer)
                }
            }
            
            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                val itemView = viewHolder.itemView
                val context = recyclerView.context
                
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val p = Paint()
                    if (dX > 0) {
                        p.color = "#4CAF50".toColorInt()
                        c.drawRect(itemView.left.toFloat(), itemView.top.toFloat(), itemView.left + dX, itemView.bottom.toFloat(), p)
                        val icon = ContextCompat.getDrawable(context, R.drawable.ic_edit)
                        icon?.let {
                            val margin = (itemView.height - it.intrinsicHeight) / 2
                            it.setBounds(itemView.left + margin, itemView.top + margin, itemView.left + margin + it.intrinsicWidth, itemView.bottom - margin)
                            it.draw(c)
                        }
                    } else if (dX < 0) {
                        p.color = "#F44336".toColorInt()
                        c.drawRect(itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat(), p)
                        val icon = ContextCompat.getDrawable(context, R.drawable.ic_delete)
                        icon?.let {
                            val margin = (itemView.height - it.intrinsicHeight) / 2
                            it.setBounds(itemView.right - margin - it.intrinsicWidth, itemView.top + margin, itemView.right - margin, itemView.bottom - margin)
                            it.draw(c)
                        }
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerViewAnswers)
    }
    
    private fun setupUI() {
        // Кнопка добавления нового ответа
        binding.btnAddAnswer.setOnClickListener {
            showAddDialog()
        }
        
        // Поиск
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterAnswers(s.toString())
            }
        })
        
        // Кнопка экспорта
        binding.btnExport.setOnClickListener {
            exportDatabase()
        }
        
        // Кнопка импорта
        binding.btnImport.setOnClickListener {
            importDatabase()
        }

        // Журнал «Не знаю ответа»
        binding.btnUnanswered.setOnClickListener {
            showUnansweredDialog()
        }
        
        // Массовые операции
        binding.btnSelectAll.setOnClickListener {
            answersAdapter.selectAll()
            updateSelectionPanel()
        }
        
        binding.btnDeleteSelected.setOnClickListener {
            showDeleteSelectedDialog()
        }
        
        binding.btnCancelSelection.setOnClickListener {
            exitSelectionMode()
        }
        
        // Слушаем изменения в адаптере для обновления панели
        binding.recyclerViewAnswers.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (answersAdapter.isSelectionMode) {
                    updateSelectionPanel()
                }
            }
        })
    }
    private fun updateSelectionPanel() {
        val count = answersAdapter.selectedCount
        binding.tvSelectedCount.text = getString(R.string.selected_count_format, count)
        binding.selectionPanel.isVisible = answersAdapter.isSelectionMode
    }
    private fun exitSelectionMode() {
        answersAdapter.setSelectionMode(false)
        updateSelectionPanel()
    }
    
    private fun loadAnswers() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.isVisible = true
            
            Log.i("AnswersEditor", "=== ЗАГРУЗКА ОТВЕТОВ (Kiro Bot) ===")
            
            val answers = withContext(Dispatchers.IO) { fileManager.loadAnswerDatabase() }
            
            Log.i("AnswersEditor", "Загружено из файла: ${answers.size} ответов")
            
            allAnswers.clear()
            allAnswers.addAll(answers)
            answersAdapter.updateAnswers(answers)
            
            Log.i("AnswersEditor", "allAnswers.size = ${allAnswers.size}")
            Log.i("AnswersEditor", "answersAdapter показывает: ${answers.size}")
            
            binding.progressBar.isVisible = false
            binding.tvAnswersCount.text = getString(R.string.total_answers_format, answers.size)
            
            // Показываем/скрываем Empty State
            binding.emptyState.isVisible = answers.isEmpty()
        }
    }
    
    /**
     * Инкрементальный поиск ТОЛЬКО по вопросам
     * Поиск начинается с первых букв и не зависит от регистра
     */
    private fun filterAnswers(query: String) {
        // Отменяем предыдущий поиск, если пользователь продолжает печатать
        searchJob?.cancel()
        
        // Делаем снимок списка в главном потоке, чтобы избежать ConcurrentModificationException
        // если вдруг список изменится во время поиска
        val snapshot = ArrayList(allAnswers)
        
        searchJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val filtered = if (query.isEmpty()) {
                snapshot
            } else {
                val queryLower = query.trim().lowercase()
                
                // Разделяем результаты по приоритетам
                val exactMatches = mutableListOf<AnswerElement>()
                val startsWithMatches = mutableListOf<AnswerElement>()
                val wordStartsMatches = mutableListOf<AnswerElement>()
                val containsMatches = mutableListOf<AnswerElement>()
                
                snapshot.forEach { answer ->
                    val questionLower = answer.questionText.lowercase()
                    
                    when {
                        // Приоритет 1: Точное совпадение вопроса
                        questionLower == queryLower -> exactMatches.add(answer)
                        
                        // Приоритет 2: Вопрос начинается с запроса
                        questionLower.startsWith(queryLower) -> startsWithMatches.add(answer)
                        
                        // Приоритет 3: Любое слово в вопросе начинается с запроса
                        questionStartsWithWord(questionLower, queryLower) -> wordStartsMatches.add(answer)
                        
                        // Приоритет 4: Запрос содержится где-то в вопросе
                        questionLower.contains(queryLower) -> containsMatches.add(answer)
                    }
                }
                
                // Объединяем результаты
                exactMatches + startsWithMatches + wordStartsMatches + containsMatches
            }
            
            withContext(Dispatchers.Main) {
                answersAdapter.updateAnswers(filtered)
                binding.emptyState.isVisible = filtered.isEmpty()
            }
        }
    }
    
    /**
     * Проверяет, начинается ли какое-либо слово в тексте с заданного запроса
     * Например: для "как дела?" и запроса "де" вернет true
     */
    private fun questionStartsWithWord(text: String, query: String): Boolean {
        // Разбиваем текст на слова
        val words = text.split(Regex("\\s+"))
        
        // Проверяем, начинается ли хотя бы одно слово с запроса
        return words.any { word -> word.startsWith(query) }
    }
    
    private fun refreshList() {
        val query = binding.etSearch.text.toString().trim()
        if (query.isNotEmpty()) {
            filterAnswers(query) // Применяем текущий фильтр
        } else {
            answersAdapter.updateAnswers(allAnswers) // Или показываем все
        }
        binding.tvAnswersCount.text = getString(R.string.total_answers_format, allAnswers.size)
        // Если мы в режиме выбора, нужно обновить панель или сбросить выбор, 
        // если удаленных элементов больше нет.
        updateSelectionPanel()
    }
    
    /** @param prefillQuestion вопрос из журнала «Не знаю ответа», [prefillAnswer] — подсказка ИИ; [onAdded] — после сохранения */
    private fun showAddDialog(prefillQuestion: String? = null, prefillAnswer: String? = null, onAdded: (() -> Unit)? = null) {
        try {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_answer, null)
            val etQuestion = dialogView.findViewById<EditText>(R.id.et_question)
            val etAnswer = dialogView.findViewById<EditText>(R.id.et_answer)
            val etAttachments = dialogView.findViewById<EditText>(R.id.et_attachments)
            val etRepetitionLimit = dialogView.findViewById<EditText>(R.id.et_repetition_limit)
            val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
            val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save)
            val tvTitle = dialogView.findViewById<TextView>(R.id.tv_dialog_title)
            
            tvTitle.text = getString(R.string.add_answer_title)
            etRepetitionLimit.setText("3")
            prefillQuestion?.let { etQuestion.setText(it) }
            prefillAnswer?.takeIf { it.isNotBlank() }?.let { etAnswer.setText(it) }
            
            val dialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create()
            
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val btnAdvanced = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_advanced_settings)
            val layoutAdvanced = dialogView.findViewById<View>(R.id.layout_advanced_settings)
            
            btnAdvanced.setOnClickListener {
                layoutAdvanced.isVisible = !layoutAdvanced.isVisible
                btnAdvanced.text = if (layoutAdvanced.isVisible) {
                    getString(R.string.collapse_settings)
                } else {
                    getString(R.string.expand_settings)
                }
            }
            
            btnCancel.setOnClickListener {
                dialog.dismiss()
            }
            
            btnSave.setOnClickListener {
                val question = etQuestion.text.toString().trim()
                val answer = etAnswer.text.toString().trim()
                val attachmentsStr = etAttachments.text.toString().trim()
                val limitStr = etRepetitionLimit.text.toString().trim()
                val repetitionLimit = limitStr.toIntOrNull() ?: 0
                
                if (question.isEmpty()) {
                    etQuestion.error = getString(R.string.enter_question_error)
                    return@setOnClickListener
                }
                
                val attachments = parseAttachments(attachmentsStr)
                
                // Проверяем: должен быть либо ответ, либо вложения
                if (answer.isEmpty() && attachments.isEmpty()) {
                    etAnswer.error = getString(R.string.enter_answer_or_attachments_error)
                    etAttachments.error = getString(R.string.enter_answer_or_attachments_error)
                    return@setOnClickListener
                }
                
                addAnswer(question, answer, attachments, repetitionLimit, onSaved = onAdded)
                dialog.dismiss()
            }
            
            dialog.show()
        } catch (e: Exception) {
            Log.e("AnswersEditor", "❌ CRASH in showAddDialog: ${e.message}", e)
            Toast.makeText(requireContext(), "Ошибка открытия диалога: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showEditDialog(answerElement: AnswerElement) {
        try {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_answer, null)
            val etQuestion = dialogView.findViewById<EditText>(R.id.et_question)
            val etAnswer = dialogView.findViewById<EditText>(R.id.et_answer)
            val etAttachments = dialogView.findViewById<EditText>(R.id.et_attachments)
            val etRepetitionLimit = dialogView.findViewById<EditText>(R.id.et_repetition_limit)
            val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
            val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save)
            val btnDelete = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_delete)
            val tvTitle = dialogView.findViewById<TextView>(R.id.tv_dialog_title)
            
            tvTitle.text = getString(R.string.edit_answer_title)
            btnDelete.isVisible = true
            
            etQuestion.setText(answerElement.questionText)
            etAnswer.setText(answerElement.answerText)
            
            // Заполняем вложения - показываем полные VK ссылки
            val attachmentsStr = answerElement.answerAttachments.joinToString("\n") { it.toDisplayString() }
            etAttachments.setText(attachmentsStr)
            
            if (answerElement.repetitionLimit > 0) {
                etRepetitionLimit.setText(answerElement.repetitionLimit.toString())
            }
            
            val dialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create()
            
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            
            val btnAdvanced = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_advanced_settings)
            val layoutAdvanced = dialogView.findViewById<View>(R.id.layout_advanced_settings)
            
            // Авто-раскрытие, если есть данные
            if (answerElement.repetitionLimit > 0) {
                layoutAdvanced.isVisible = true
                btnAdvanced.text = getString(R.string.collapse_settings)
            }

            btnAdvanced.setOnClickListener {
                layoutAdvanced.isVisible = !layoutAdvanced.isVisible
                btnAdvanced.text = if (layoutAdvanced.isVisible) {
                    getString(R.string.collapse_settings)
                } else {
                    getString(R.string.expand_settings)
                }
            }
            
            btnCancel.setOnClickListener {
                dialog.dismiss()
            }
            
            btnDelete.setOnClickListener {
                dialog.dismiss()
                showDeleteDialog(answerElement)
            }
            
            btnSave.setOnClickListener {
                val question = etQuestion.text.toString().trim()
                val answer = etAnswer.text.toString().trim()
                val inputAttachmentsStr = etAttachments.text.toString().trim()
                val limitStr = etRepetitionLimit.text.toString().trim()
                val repetitionLimit = limitStr.toIntOrNull() ?: 0
                
                if (question.isEmpty()) {
                    etQuestion.error = getString(R.string.enter_question_error)
                    return@setOnClickListener
                }
                
                val attachments = parseAttachments(inputAttachmentsStr)
                
                // Проверяем: должен быть либо ответ, либо вложения
                if (answer.isEmpty() && attachments.isEmpty()) {
                    etAnswer.error = getString(R.string.enter_answer_or_attachments_error)
                    etAttachments.error = getString(R.string.enter_answer_or_attachments_error)
                    return@setOnClickListener
                }
                
                updateAnswer(answerElement.id, question, answer, attachments, answerElement.usageCount, repetitionLimit, answerElement.requiredContext, answerElement.resultContext)
                dialog.dismiss()
            }
            
            dialog.show()
        } catch (e: Exception) {
            Log.e("AnswersEditor", "❌ CRASH in showEditDialog: ${e.message}", e)
            Toast.makeText(requireContext(), "Ошибка открытия диалога: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun parseAttachments(attachmentsStr: String): List<Attachment> {
        if (attachmentsStr.isEmpty()) return emptyList()
        
        Log.i("AnswersEditor", "=== ПАРСИНГ ВЛОЖЕНИЙ ===")
        
        val attachments = mutableListOf<Attachment>()
        val parts = attachmentsStr.split(",", "\n").map { it.trim() }
        
        for (part in parts) {
            if (part.isEmpty()) continue
            
            // Используем встроенный парсер Attachment, он поддерживает любые типы и access_key
            val attachment = Attachment.parse(part)
            
            if (attachment != null) {
                attachments.add(attachment)
                Log.i("AnswersEditor", "✅ Распознано: ${attachment.toStorageString()}")
            } else {
                Log.w("AnswersEditor", "❌ Не удалось распознать: $part")
            }
        }
        
        return attachments
    }
    
    private fun showDeleteDialog(answerElement: AnswerElement) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_delete, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_dialog_title)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tv_dialog_message)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
        val btnDelete = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_delete)
        
        btnDelete.text = getString(R.string.delete)
        
        tvTitle.text = getString(R.string.delete_answer_title)
        tvMessage.text = getString(R.string.question_format, answerElement.questionText)
        
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnDelete.setOnClickListener {
            deleteAnswer(answerElement.id)
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun addAnswer(question: String, answer: String, attachments: List<Attachment>, repetitionLimit: Int = 0, requiredContext: String = "", resultContext: String = "", onSaved: (() -> Unit)? = null) {
        val usageCount = 0 // Используем константу, так как при добавлении всегда 0
        lifecycleScope.launch {
            val newId = (allAnswers.maxOfOrNull { it.id } ?: 0) + 1
            val newAnswer = AnswerElement(newId, question, answer, attachments, java.util.Date(), usageCount, repetitionLimit, requiredContext, resultContext)
            
            Log.i("AnswersEditor", "=== ДОБАВЛЕНИЕ ОТВЕТА ===")
            Log.i("AnswersEditor", "Вопрос: $question")
            Log.i("AnswersEditor", "Ответ: $answer")
            Log.i("AnswersEditor", "Вложений: ${attachments.size}")
            attachments.forEach { 
                Log.i("AnswersEditor", "  - ${it.toStorageString()}")
            }
            
            allAnswers.add(newAnswer)
            
            // Создаем копию списка для сохранения, чтобы избежать ConcurrentModificationException
            val listToSave = ArrayList(allAnswers)
            
            val saved = saveWithBackup(listToSave)
            
            if (!isAdded) return@launch
            
            if (saved) {
                // ИСПРАВЛЕНИЕ: Обновляем список с учетом поиска
                refreshList()
                Toast.makeText(requireContext(), R.string.answer_added_success, Toast.LENGTH_SHORT).show()
                onSaved?.invoke()
                reloadBotDatabase()
            } else {
                Toast.makeText(requireContext(), R.string.save_error, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateAnswer(id: Long, question: String, answer: String, attachments: List<Attachment>, usageCount: Int = 0, repetitionLimit: Int = 0, requiredContext: String = "", resultContext: String = "") {
        lifecycleScope.launch {
            Log.i("AnswersEditor", "=== ОБНОВЛЕНИЕ ОТВЕТА ===")
            Log.i("AnswersEditor", "ID: $id")
            Log.i("AnswersEditor", "Вопрос: $question")
            Log.i("AnswersEditor", "Ответ: '$answer' (длина: ${answer.length})")
            Log.i("AnswersEditor", "Лимит повторов: $repetitionLimit")
            Log.i("AnswersEditor", "Статистика использований: $usageCount")
            Log.i("AnswersEditor", "Вложений: ${attachments.size}")
            attachments.forEach { 
                Log.i("AnswersEditor", "  - ${it.toStorageString()}")
            }
            
            Log.i("AnswersEditor", "Размер allAnswers ДО обновления: ${allAnswers.size}")
            
            val index = allAnswers.indexOfFirst { it.id == id }
            if (index != -1) {
                // Сохраняем оригинальную дату создания
                val originalDate = allAnswers[index].createdDate
                
                val updatedAnswer = AnswerElement(id, question, answer, attachments, originalDate, usageCount, repetitionLimit, requiredContext, resultContext)
                allAnswers[index] = updatedAnswer
                
                Log.i("AnswersEditor", "Размер allAnswers ПОСЛЕ обновления: ${allAnswers.size}")
                Log.i("AnswersEditor", "Индекс обновленного элемента: $index")
                
                // Создаем копию списка для сохранения, чтобы избежать ConcurrentModificationException
                val listToSave = ArrayList(allAnswers)
                
                val saved = saveWithBackup(listToSave)
                
                if (!isAdded) return@launch
                
                if (saved) {
                    Log.i("AnswersEditor", "✅ Сохранение успешно")
                    
                        
                    refreshList()
                    Toast.makeText(requireContext(), R.string.answer_updated_success, Toast.LENGTH_SHORT).show()
                    reloadBotDatabase()
                }
            } else {
                Log.e("AnswersEditor", "❌ Элемент с ID $id не найден!")
            }
        }
    }
    
    private fun deleteAnswer(id: Long) {
        lifecycleScope.launch {
            allAnswers.removeAll { it.id == id }
            
            // Создаем копию списка для сохранения, чтобы избежать ConcurrentModificationException
            val listToSave = ArrayList(allAnswers)
            
            val saved = saveWithBackup(listToSave)
            
            if (!isAdded) return@launch
            
            if (saved) {
                refreshList()
                Toast.makeText(requireContext(), R.string.answer_deleted_success, Toast.LENGTH_SHORT).show()
                reloadBotDatabase()
            } else {
                Toast.makeText(requireContext(), R.string.save_error, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showDeleteSelectedDialog() {
        val selectedCount = answersAdapter.selectedCount
        if (selectedCount == 0) {
            Toast.makeText(requireContext(), R.string.select_answers_to_delete, Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_delete, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_dialog_title)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tv_dialog_message)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
        val btnDelete = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_delete)
        
        tvTitle.text = getString(R.string.delete_selected_answers_title)
        tvMessage.text = getString(R.string.delete_selected_answers_count_format, selectedCount)
        
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnDelete.setOnClickListener {
            deleteSelectedAnswers()
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun deleteSelectedAnswers() {
        lifecycleScope.launch {
            val selectedItems = answersAdapter.getSelectedItems()
            if (selectedItems.isEmpty()) return@launch
            
            val selectedIds = selectedItems.map { it.id }.toSet()
            allAnswers.removeAll { selectedIds.contains(it.id) }
            
            // Создаем копию списка для сохранения, чтобы избежать ConcurrentModificationException
            val listToSave = ArrayList(allAnswers)
            
            val saved = saveWithBackup(listToSave)
            
            if (!isAdded) return@launch
            
            if (saved) {
                refreshList()
                Toast.makeText(requireContext(), getString(R.string.deleted_answers_count_format, selectedItems.size), Toast.LENGTH_SHORT).show()
                exitSelectionMode()
                reloadBotDatabase()
            } else {
                Toast.makeText(requireContext(), R.string.save_error, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private suspend fun saveWithBackup(list: List<AnswerElement>): Boolean =
        withContext(Dispatchers.IO) { fileManager.saveAnswerDatabaseWithBackup(list) }

    /** Журнал «Не знаю ответа»: чаще всего задаваемые вопросы без ответа в базе. */
    private fun showUnansweredDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) { fileManager.loadUnanswered().top() }
            val builder = MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.unanswered_title, entries.size))
            if (entries.isEmpty()) {
                builder.setMessage(R.string.unanswered_empty).setPositiveButton(R.string.understood, null)
            } else {
                // 🤖 — на вопрос уже есть подсказка от ИИ
                val items = entries.map {
                    getString(R.string.unanswered_item_format, it.text, it.count) + if (it.suggestion.isNotEmpty()) "  🤖" else ""
                }.toTypedArray()
                builder.setItems(items) { _, which -> showUnansweredActions(entries[which]) }
                    .setNeutralButton(R.string.unanswered_clear_all) { _, _ -> removeUnanswered(null) }
                    .setNegativeButton(R.string.cancel, null)
            }
            builder.show()
        }
    }

    private fun showUnansweredActions(entry: UnansweredLog.Entry) {
        val builder = MaterialAlertDialogBuilder(requireContext()).setTitle(entry.text)
        if (entry.suggestion.isNotEmpty()) builder.setMessage(getString(R.string.unanswered_ai_suggestion_format, entry.suggestion))
        // В сообщении и списке сразу нельзя — кнопки вместо пунктов
        builder.setPositiveButton(R.string.unanswered_add_answer) { _, _ ->
            showAddDialog(prefillQuestion = entry.text, prefillAnswer = entry.suggestion, onAdded = { removeUnanswered(entry.text) })
        }
            .setNeutralButton(R.string.unanswered_remove) { _, _ -> removeUnanswered(entry.text) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Убирает вопрос из журнала ([text] = null — очистить весь журнал): в файле и у работающего бота. */
    private fun removeUnanswered(text: String?) {
        val appContext = requireContext().applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            val log = fileManager.loadUnanswered()
            if (text == null) log.clear() else log.remove(text)
            fileManager.saveUnanswered(log)
            // Работающий бот держит журнал в памяти — иначе он вернул бы запись при следующем сохранении
            if (sharedPrefs.getBoolean("bot_running", false)) {
                try {
                    appContext.startService(Intent(appContext, BotService::class.java).apply {
                        action = BotService.ACTION_UNANSWERED_REMOVE
                        text?.let { putExtra(BotService.EXTRA_TEXT, it) }
                    })
                } catch (e: Exception) {
                    Log.e("AnswersEditor", "Не удалось уведомить сервис: ${e.message}")
                }
            }
            if (text == null) withContext(Dispatchers.Main) {
                Toast.makeText(appContext, R.string.unanswered_cleared, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun reloadBotDatabase() {
        Log.i("AnswersEditor", "📝 ========================================")
        Log.i("AnswersEditor", "📝 БАЗА ДАННЫХ ОТРЕДАКТИРОВАНА")
        Log.i("AnswersEditor", "📊 Количество ответов: ${allAnswers.size}")
        Log.i("AnswersEditor", "🔄 Отправка команды перезагрузки боту...")
        
        try {
            val intent = Intent(requireContext(), BotService::class.java)
            intent.action = BotService.ACTION_RELOAD
            
            // ИСПРАВЛЕНИЕ: Используем startService вместо startForegroundService,
            // чтобы избежать крэша ForegroundServiceDidNotStartInTimeException, 
            // так как мы находимся в активном UI-потоке.
            requireContext().startService(intent)
            
            Log.i("AnswersEditor", "✅ Команда перезагрузки отправлена")
        } catch (e: Exception) {
            Log.e("AnswersEditor", "⚠️ Не удалось перезагрузить сервис: ${e.message}")
        }
        
        Log.i("AnswersEditor", "📝 ========================================")
    }
    
    private fun exportDatabase() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.isVisible = true
            
            val exportPath = withContext(Dispatchers.IO) {
                if (fileManager.isFileExists) {
                    fileManager.exportDatabase()
                } else {
                    null
                }
            }
            
            binding.progressBar.isVisible = false
            
            if (!isAdded) return@launch
            
            if (exportPath != null) {
                Toast.makeText(
                    requireContext(), 
                    getString(R.string.backup_created_success_format, exportPath), 
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    requireContext(), 
                    R.string.backup_error, 
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun importDatabase() {
        // В упрощенной версии файл уже находится в доступном месте
        val path = fileManager.answerFilePath
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_delete, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_dialog_title)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tv_dialog_message)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
        val btnDelete = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_delete)
        
        tvTitle.text = getString(R.string.import_database_title)
        tvMessage.text = getString(R.string.import_database_confirm_format, path)
        btnDelete.text = getString(R.string.reload)
        
        // Делаем текст белым, а фон темно-серым (чтобы белый текст было видно и кнопка не сливалась)
        btnDelete.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.bg_card_highlight)
        )
        btnDelete.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnDelete.setOnClickListener {
            performImport()
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun performImport() {
        loadAnswers()
        reloadBotDatabase()
        if (isAdded) {
            Toast.makeText(requireContext(), R.string.database_reloaded_success, Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
