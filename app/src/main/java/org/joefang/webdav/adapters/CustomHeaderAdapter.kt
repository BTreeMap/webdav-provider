package org.joefang.webdav.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.joefang.webdav.data.CustomHeader
import org.joefang.webdav.databinding.ItemCustomHeaderBinding

/**
 * Adapter for displaying and managing custom HTTP headers in a RecyclerView.
 */
class CustomHeaderAdapter(
    private val listener: Listener
) : RecyclerView.Adapter<CustomHeaderAdapter.ViewHolder>() {
    
    private val headers = mutableListOf<CustomHeader>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemCustomHeaderBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val header = headers[position]
        holder.bind(header)
    }

    override fun getItemCount(): Int = headers.size

    fun setHeaders(newHeaders: List<CustomHeader>) {
        headers.clear()
        headers.addAll(newHeaders)
        notifyDataSetChanged()
    }

    fun getHeaders(): List<CustomHeader> = headers.toList()

    fun addHeader(header: CustomHeader) {
        headers.add(header)
        notifyItemInserted(headers.size - 1)
    }

    fun updateHeader(position: Int, header: CustomHeader) {
        if (position in headers.indices) {
            headers[position] = header
            notifyItemChanged(position)
        }
    }

    fun removeHeader(position: Int) {
        if (position in headers.indices) {
            headers.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    inner class ViewHolder(
        private val binding: ItemCustomHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(header: CustomHeader) {
            binding.header = header
            binding.executePendingBindings()

            // Show warning for dangerous headers
            binding.textWarning.visibility = if (CustomHeader.isDangerousHeader(header.name)) {
                View.VISIBLE
            } else {
                View.GONE
            }

            binding.checkboxEnabled.setOnCheckedChangeListener { _, isChecked ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val updatedHeader = headers[position].copy(enabled = isChecked)
                    headers[position] = updatedHeader
                    listener.onHeaderEnabledChanged(position, updatedHeader)
                }
            }

            binding.btnEdit.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onEditHeader(position, headers[position])
                }
            }

            binding.btnDelete.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onDeleteHeader(position, headers[position])
                }
            }
        }
    }

    interface Listener {
        fun onEditHeader(position: Int, header: CustomHeader)
        fun onDeleteHeader(position: Int, header: CustomHeader)
        fun onHeaderEnabledChanged(position: Int, header: CustomHeader)
    }
}
