package com.vkbot.manager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.vkbot.manager.databinding.ItemBlacklistUserBinding
import com.vkbot.manager.utils.BlockedUser

class BlacklistAdapter(
    private var users: List<BlockedUser>,
    private val onRemoveClick: (Long) -> Unit
) : RecyclerView.Adapter<BlacklistAdapter.ViewHolder>() {

    class ViewHolder(private val binding: ItemBlacklistUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: BlockedUser, onRemoveClick: (Long) -> Unit) {
            val context = itemView.context
            
            // Устранение конкатенации: используем ресурсную строку с плейсхолдерами
            binding.tvUserId.text = context.getString(R.string.blacklist_item_format, user.name, user.id)
            
            binding.btnRemove.setOnClickListener {
                onRemoveClick(user.id)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBlacklistUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(users[position], onRemoveClick)
    }

    override fun getItemCount() = users.size

    fun updateData(newUsers: List<BlockedUser>) {
        val diffCallback = UserDiffCallback(users, newUsers)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        users = newUsers
        diffResult.dispatchUpdatesTo(this)
    }

    private class UserDiffCallback(
        private val oldList: List<BlockedUser>,
        private val newList: List<BlockedUser>
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
