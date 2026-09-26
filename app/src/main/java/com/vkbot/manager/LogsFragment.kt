package com.vkbot.manager

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vkbot.manager.databinding.FragmentLogsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LogsFragment : Fragment() {
    
    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var logsAdapter: LogsAdapter
    private var isUserScrolling = false
    private var shouldAutoScroll = true
    private var isFirstLoad = true
    private var lastFileModifiedTime: Long = 0
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        startLogUpdates()
        
        binding.fabClearLogs.setOnClickListener {
            clearLogs()
        }
        
        binding.fabShareLogs.setOnClickListener {
            shareLogs()
        }
    }
    
    private fun clearLogs() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_logs_title)
            .setMessage(R.string.clear_logs_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        File(requireContext().filesDir, LOG_FILE_NAME).writeText("")
                        lastFileModifiedTime = 0
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                logsAdapter.submitList(emptyList())
                                Toast.makeText(requireContext(), R.string.clear_logs_success, Toast.LENGTH_SHORT).show()
                                binding.emptyStateLogs.isVisible = true
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error clearing logs", e)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun shareLogs() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(requireContext().filesDir, LOG_FILE_NAME)
                if (!file.exists() || file.length() == 0L) {
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            Toast.makeText(requireContext(), R.string.logs_empty_error, Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@launch
                }
                
                val text = file.readText().takeLast(100000) // Берем последние 100к символов
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, getString(R.string.share_logs_title))
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        startActivity(shareIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing logs", e)
            }
        }
    }
    
    private fun setupRecyclerView() {
        logsAdapter = LogsAdapter()
        
        binding.recyclerViewLogs.apply {
            adapter = logsAdapter
            val layoutManager = LinearLayoutManager(requireContext())
            layoutManager.stackFromEnd = true
            this.layoutManager = layoutManager
            
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        isUserScrolling = true
                        shouldAutoScroll = false
                    } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        isUserScrolling = false
                        
                        val lastPos = layoutManager.findLastCompletelyVisibleItemPosition()
                        val totalCount = logsAdapter.itemCount
                        
                        if (lastPos >= totalCount - 1) {
                            shouldAutoScroll = true
                        }
                    }
                }
            })
        }
    }
    
    private suspend fun updateLogs() = withContext(Dispatchers.IO) {
        val context = context ?: return@withContext
        val file = File(context.filesDir, LOG_FILE_NAME)
        
        if (!file.exists()) {
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    logsAdapter.submitList(emptyList())
                    binding.emptyStateLogs.isVisible = true
                }
            }
            return@withContext
        }
        
        val lastModified = file.lastModified()
        if (lastModified <= lastFileModifiedTime) {
            return@withContext
        }
        lastFileModifiedTime = lastModified
        
        val lines = try {
            file.readLines()
        } catch (e: Exception) {
            listOf(getString(R.string.log_read_error_format, e.message ?: ""))
        }
        
        // ОПТИМИЗАЦИЯ: Берем только последние 500 строк, чтобы не тормозить UI
        val recentLines = if (lines.size > 500) lines.takeLast(500) else lines
        
        val logEntries = recentLines.mapIndexed { index, line ->
            LogEntry(index.toLong(), line)
        }
        
        withContext(Dispatchers.Main) main@{
            if (_binding == null) return@main

            if (isFirstLoad) {
                binding.recyclerViewLogs.alpha = 0f
                logsAdapter.submitList(logEntries) {
                    binding.recyclerViewLogs.animate()
                        .alpha(1f)
                        .setDuration(500)
                        .start()
                    
                    if (logEntries.isNotEmpty()) {
                        binding.recyclerViewLogs.scrollToPosition(logEntries.size - 1)
                    }
                }
                isFirstLoad = false
            } else {
                logsAdapter.submitList(logEntries) {
                    if (shouldAutoScroll && !isUserScrolling && logEntries.isNotEmpty()) {
                        binding.recyclerViewLogs.scrollToPosition(logEntries.size - 1)
                    }
                }
            }
            
            binding.emptyStateLogs.isVisible = logEntries.isEmpty() || (logEntries.size == 1 && logEntries[0].message.contains("Ожидание"))
        }
    }
    
    private fun startLogUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            updateLogs()
            
            while (isActive) {
                delay(1000)
                if (isAdded && _binding != null) {
                    updateLogs()
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "LogsFragment"
        private const val LOG_FILE_NAME = "bot_logs.txt"
    }
}