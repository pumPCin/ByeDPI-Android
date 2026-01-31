package io.github.romanvht.byedpi.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.DomainList

class DomainListAdapter(
    private val onToggle: (DomainList) -> Unit,
    private val onEdit: (DomainList) -> Unit,
    private val onDelete: (DomainList) -> Unit,
    private val onCopy: (DomainList) -> Unit
) : ListAdapter<DomainList, DomainListAdapter.DomainListViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DomainListViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_domain_list, parent, false)
        return DomainListViewHolder(view)
    }

    override fun onBindViewHolder(holder: DomainListViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DomainListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.list_name)
        private val countText: TextView = itemView.findViewById(R.id.list_count)
        private val checkbox: CheckBox = itemView.findViewById(R.id.list_checkbox)
        private val contentLayout: LinearLayout = itemView.findViewById(R.id.list_content)

        fun bind(domainList: DomainList) {
            nameText.text = domainList.name

            val domains = domainList.domains.take(5).joinToString("\n")

            val summary = if (domainList.domains.size > 5) {
                "$domains\n..."
            } else {
                domains
            }

            countText.text = summary

            checkbox.isChecked = domainList.isActive
            checkbox.setOnCheckedChangeListener { _, _ ->
                onToggle(domainList)
            }

            contentLayout.setOnClickListener {
                showActionDialog(domainList)
            }
        }

        private fun showActionDialog(domainList: DomainList) {
            val context = itemView.context

            val options = arrayOf(
                context.getString(R.string.domain_list_edit),
                context.getString(R.string.toast_copied),
                context.getString(R.string.domain_list_delete)
            )

            AlertDialog.Builder(context, R.style.CustomAlertDialog)
                .setTitle(domainList.name)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> onEdit(domainList)
                        1 -> onCopy(domainList)
                        2 -> onDelete(domainList)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<DomainList>() {
        override fun areItemsTheSame(oldItem: DomainList, newItem: DomainList): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DomainList, newItem: DomainList): Boolean {
            return oldItem == newItem
        }
    }
}