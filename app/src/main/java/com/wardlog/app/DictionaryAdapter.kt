package com.wardlog.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wardlog.app.databinding.ItemDictionaryEntryBinding

class DictionaryAdapter(
    private val onEdit: (DictionaryEntry) -> Unit,
    private val onDelete: (DictionaryEntry) -> Unit
) : ListAdapter<DictionaryEntry, DictionaryAdapter.EntryViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        val binding = ItemDictionaryEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EntryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EntryViewHolder(private val binding: ItemDictionaryEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: DictionaryEntry) {
            val isDoctor = entry.category == DictionaryEntry.CATEGORY_DOCTOR
            binding.categoryTag.text = if (isDoctor) "DOCTOR" else "TERM"
            binding.categoryTag.setBackgroundResource(
                if (isDoctor) R.drawable.bg_tag_doctor else R.drawable.bg_tag_term
            )
            binding.primaryText.text = if (isDoctor) {
                "Dr ${entry.canonical}"
            } else {
                "${entry.alias} → ${entry.canonical}"
            }
            binding.root.setOnClickListener { onEdit(entry) }
            binding.root.setOnLongClickListener {
                onDelete(entry)
                true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DictionaryEntry>() {
        override fun areItemsTheSame(oldItem: DictionaryEntry, newItem: DictionaryEntry) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: DictionaryEntry, newItem: DictionaryEntry) = oldItem == newItem
    }
}
