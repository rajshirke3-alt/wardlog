package com.wardlog.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wardlog.app.databinding.ItemRecordBinding

class RecordAdapter(
    private val onEdit: (Record) -> Unit,
    private val onDelete: (Record) -> Unit
) : ListAdapter<Record, RecordAdapter.RecordViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val binding = ItemRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val surfaceColor = ContextCompat.getColor(parent.context, R.color.surface)
        val surfaceAltColor = ContextCompat.getColor(parent.context, R.color.surface_alt)
        return RecordViewHolder(binding, surfaceColor, surfaceAltColor)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class RecordViewHolder(
        private val binding: ItemRecordBinding,
        private val surfaceColor: Int,
        private val surfaceAltColor: Int
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(record: Record, position: Int) {
            binding.colBed.text = record.bedNumber
            binding.colName.text = record.patientName
            binding.colConsultant.text = record.consultant
            binding.colDetails.text = record.details
            binding.root.setBackgroundColor(
                if (position % 2 == 0) surfaceColor else surfaceAltColor
            )
            binding.root.setOnClickListener { onEdit(record) }
            binding.root.setOnLongClickListener {
                onDelete(record)
                true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Record>() {
        override fun areItemsTheSame(oldItem: Record, newItem: Record) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Record, newItem: Record) = oldItem == newItem
    }
}
