package com.vkbot.manager

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vkbot.manager.databinding.FragmentBlacklistBinding
import com.vkbot.manager.utils.BlacklistManager
import com.vkbot.manager.utils.BotDataManager
import com.vkbot.manager.utils.BotPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

/**
 * Фрагмент управления черным списком пользователей.
 * Поиск пользователя по ссылке — через API VK; для Telegram — по числовому ID.
 */
class BlacklistFragment : Fragment() {

    private var _binding: FragmentBlacklistBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: BlacklistAdapter

    override fun onCreateView(
        inflater: LayoutInflater, 
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBlacklistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupListeners()
        updateList()
    }

    private fun setupRecyclerView() {
        adapter = BlacklistAdapter(emptyList()) { userId ->
            BlacklistManager.remove(userId)
            updateList()
            val msg = getString(R.string.blacklist_user_removed_format, userId.toString())
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
        
        binding.rvBlacklist.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@BlacklistFragment.adapter
        }
    }

    private fun setupListeners() {
        binding.btnAddUser.setOnClickListener {
            val idStr = binding.etUserId.text.toString().trim()
            if (idStr.isEmpty()) {
                Toast.makeText(requireContext(), R.string.enter_id_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            toggleLoading(true)
            
            // Извлекаем screen_name или ID из ввода
            var screenName = idStr.substringAfterLast("/")
            if (screenName.startsWith("@")) screenName = screenName.drop(1)
            
            fetchUserAndAdd(screenName)
        }
    }
    
    private fun toggleLoading(isLoading: Boolean) {
        binding.btnAddUser.isEnabled = !isLoading
        binding.btnAddUser.text = if (isLoading) getString(R.string.searching) else getString(R.string.add)
    }

    private fun fetchUserAndAdd(screenName: String) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var finalId = -1L
            var finalName = getString(R.string.unknown_vk_id)
            var success = false
            var errorMessage = getString(R.string.user_not_found_error)
            
            try {
                // Поиск по ссылке/нику возможен только через API VK; для Telegram — числовой ID
                val token = BotDataManager.loadBots(appContext)
                    .firstOrNull { it.platform == BotPlatform.VK && it.token.isNotEmpty() }?.token
                
                if (token != null) {
                    val encodedName = URLEncoder.encode(screenName, "UTF-8")
                    val url = "https://api.vk.com/method/users.get?user_ids=$encodedName&v=5.131&access_token=$token"
                    val response = URL(url).readText()
                    val json = JSONObject(response)
                    
                    if (json.has("response")) {
                        val array = json.getJSONArray("response")
                        if (array.length() > 0) {
                            val userObj = array.getJSONObject(0)
                            finalId = userObj.getLong("id")
                            val firstName = userObj.optString("first_name", "")
                            val lastName = userObj.optString("last_name", "")
                            finalName = "$firstName $lastName".trim()
                            success = true
                        }
                    } else if (json.has("error")) {
                        errorMessage = json.getJSONObject("error").optString("error_msg", getString(R.string.vk_api_error))
                    }
                } else {
                    // Fallback: пробуем парсить как прямой ID
                    val numericOnly = screenName.replace(Regex("[^0-9]"), "")
                    if (numericOnly.isNotEmpty()) {
                        finalId = numericOnly.toLongOrNull() ?: -1L
                        if (finalId > 0) success = true
                    } else {
                        errorMessage = getString(R.string.no_token_numeric_id_error)
                    }
                }
            } catch (e: Exception) {
                Log.e("Blacklist", "Network error", e)
                errorMessage = getString(R.string.network_error)
            }
            
            withContext(Dispatchers.Main) {
                toggleLoading(false)
                
                if (success && finalId > 0) {
                    if (BlacklistManager.isBlacklisted(finalId)) {
                        Toast.makeText(context, getString(R.string.user_already_blacklisted_format, finalName), Toast.LENGTH_SHORT).show()
                    } else {
                        BlacklistManager.add(finalId, finalName)
                        binding.etUserId.text.clear()
                        hideKeyboard()
                        updateList()
                        Toast.makeText(context, getString(R.string.user_blocked_success_format, finalName), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun updateList() {
        val users = BlacklistManager.getAll()
        adapter.updateData(users)
        
        binding.tvBlacklistTitle.text = getString(R.string.blacklist_title_format, users.size)
        
        val isEmpty = users.isEmpty()
        binding.rvBlacklist.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }
    
    private fun hideKeyboard() {
        val view = activity?.currentFocus ?: return
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
