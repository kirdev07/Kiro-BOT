package com.vkbot.manager

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.vkbot.manager.databinding.FragmentBlacklistBinding
import com.vkbot.manager.utils.BlacklistManager
import com.vkbot.manager.utils.BlockedUser

/**
 * Чёрный список: пользователи Telegram по числовому ID (бот их полностью игнорирует).
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
        adapter = BlacklistAdapter(emptyList()) { user -> removeWithUndo(user) }

        binding.rvBlacklist.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@BlacklistFragment.adapter
        }

        // Смахнуть влево или вправо — убрать из чёрного списка (с кнопкой «Вернуть»)
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) removeWithUndo(adapter.userAt(pos))
            }
        }).attachToRecyclerView(binding.rvBlacklist)
    }

    private fun removeWithUndo(user: BlockedUser) {
        BlacklistManager.remove(user.id)
        updateList()
        Snackbar.make(binding.root, getString(R.string.blacklist_user_removed_format, user.id.toString()), Snackbar.LENGTH_LONG)
            .setAction(R.string.blacklist_undo) {
                BlacklistManager.add(user.id, user.name)
                if (_binding != null) updateList()
            }
            .show()
    }

    private fun setupListeners() {
        binding.btnAddUser.setOnClickListener { addById(binding.etUserId.text.toString()) }
    }

    /** Числовой ID пользователя Telegram (его можно узнать командой !id у бота). */
    private fun addById(input: String) {
        val id = input.trim().toLongOrNull()
        if (id == null || id <= 0) {
            Toast.makeText(requireContext(), R.string.enter_id_error, Toast.LENGTH_SHORT).show()
            return
        }
        val name = getString(R.string.blacklist_default_name)
        if (BlacklistManager.isBlacklisted(id)) {
            Toast.makeText(requireContext(), getString(R.string.user_already_blacklisted_format, id.toString()), Toast.LENGTH_SHORT).show()
            return
        }
        BlacklistManager.add(id, name)
        binding.etUserId.text?.clear()
        hideKeyboard()
        updateList()
        Toast.makeText(requireContext(), getString(R.string.user_blocked_success_format, id.toString()), Toast.LENGTH_SHORT).show()
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
