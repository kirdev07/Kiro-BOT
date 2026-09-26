package com.vkbot.manager

import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.core.view.isVisible
import com.vkbot.manager.databinding.ItemAnswerBinding
import com.vkbot.manager.botbrain.AnswerElement

class AnswersAdapter(
    private val onEditClick: (AnswerElement) -> Unit,
    private val onSelectionModeChanged: (Boolean) -> Unit = {},
    private val onSelectionUpdate: () -> Unit = {} 
) : RecyclerView.Adapter<AnswersAdapter.AnswerViewHolder>() {
    
    private var answers = listOf<AnswerElement>()
    private val selectedItems = mutableSetOf<Long>()
    private var selectionMode = false
    
    fun updateAnswers(newAnswers: List<AnswerElement>) {
        val diffCallback = AnswerDiffCallback(answers, newAnswers)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        answers = newAnswers
        diffResult.dispatchUpdatesTo(this)
    }
    
    fun setSelectionMode(enabled: Boolean) {
        if (selectionMode == enabled) return
        
        selectionMode = enabled
        if (!enabled) {
            selectedItems.clear()
        }
        onSelectionModeChanged(enabled)
        notifyItemRangeChanged(0, itemCount)
    }
    
    val isSelectionMode get() = selectionMode
    
    fun getSelectedItems(): List<AnswerElement> {
        return answers.filter { selectedItems.contains(it.id) }
    }
    
    val selectedCount get() = selectedItems.size
    
    fun getItemAt(position: Int): AnswerElement = answers[position]
    
    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(answers.map { it.id })
        notifyItemRangeChanged(0, itemCount)
        onSelectionUpdate() 
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnswerViewHolder {
        val binding = ItemAnswerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AnswerViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: AnswerViewHolder, position: Int) {
        val answer = answers[position]
        holder.bind(
            answer = answer,
            onEditClick = onEditClick,
            onLongClick = { item ->
                if (!selectionMode) {
                    setSelectionMode(true)
                    toggleSelection(item.id)
                }
            },
            selectionMode = selectionMode,
            isSelected = answer.id in selectedItems,
            toggleSelection = this::toggleSelection
        )
    }
    
    override fun getItemCount(): Int = answers.size
    
    private fun toggleSelection(id: Long) {
        if (id in selectedItems) {
            selectedItems.remove(id)
        } else {
            selectedItems.add(id)
        }
        // Находим позицию элемента для точечного обновления
        val index = answers.indexOfFirst { it.id == id }
        if (index != -1) {
            notifyItemChanged(index)
        }
        onSelectionUpdate()
    }
    
    class AnswerViewHolder(private val binding: ItemAnswerBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(
            answer: AnswerElement,
            onEditClick: (AnswerElement) -> Unit,
            onLongClick: (AnswerElement) -> Unit,
            selectionMode: Boolean,
            isSelected: Boolean,
            toggleSelection: (Long) -> Unit
        ) {
            val context = itemView.context
            
            binding.tvQuestion.text = answer.questionText
            
            val answerText = answer.answerText
            if (answerText.isEmpty()) {
                binding.tvAnswer.text = context.getString(R.string.only_attachments)
                binding.tvAnswer.setTextColor(ContextCompat.getColor(context, R.color.text_low))
            } else {
                binding.tvAnswer.text = answerText
                binding.tvAnswer.setTextColor(ContextCompat.getColor(context, R.color.text_medium))
                
                // Переустановка текста для сброса Linkify
                val currentText = binding.tvAnswer.text
                binding.tvAnswer.text = currentText
                Linkify.addLinks(binding.tvAnswer, Linkify.WEB_URLS)
            }
            
            binding.tvAnswer.setOnClickListener { 
                if (!selectionMode) onEditClick(answer) else toggleSelection(answer.id)
            }
            
            val attachments = answer.answerAttachments
            if (attachments.isNotEmpty()) {
                val attachmentsStr = attachments.joinToString("\n") { it.toDisplayString() }
                binding.tvAttachments.text = context.getString(R.string.attachments_label_format, attachmentsStr)
                binding.tvAttachments.isVisible = true
            } else {
                binding.tvAttachments.isVisible = false
            }
            
            binding.checkboxSelect.isVisible = selectionMode
            binding.checkboxSelect.isChecked = isSelected
            
            binding.root.setOnClickListener {
                if (selectionMode) {
                    toggleSelection(answer.id)
                } else {
                    onEditClick(answer)
                }
            }
            
            binding.root.setOnLongClickListener {
                onLongClick(answer)
                true
            }
            
            binding.checkboxSelect.setOnClickListener {
                toggleSelection(answer.id)
            }
        }
    }

    private class AnswerDiffCallback(
        private val oldList: List<AnswerElement>,
        private val newList: List<AnswerElement>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }
        
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}