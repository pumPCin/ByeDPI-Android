package io.github.romanvht.byedpi.adapters

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.StrategyResult
import androidx.core.view.isGone
import androidx.core.view.isVisible

class StrategyResultAdapter(
    private val context: Context,
    private val onApply: (String) -> Unit,
    private val onConnect: (String) -> Unit
) : RecyclerView.Adapter<StrategyResultAdapter.StrategyViewHolder>() {

    private val strategies = mutableListOf<StrategyResult>()

    class StrategyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val commandTextView: TextView = view.findViewById(R.id.commandTextView)
        val progressLayout: LinearLayout = view.findViewById(R.id.progressLayout)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val progressTextView: TextView = view.findViewById(R.id.progressTextView)
        val expandButton: LinearLayout = view.findViewById(R.id.expandButton)
        val expandTextView: TextView = view.findViewById(R.id.expandTextView)
        val expandIcon: ImageView = view.findViewById(R.id.expandIcon)
        val sitesRecyclerView: RecyclerView = view.findViewById(R.id.sitesRecyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StrategyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_strategy_result, parent, false)
        return StrategyViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: StrategyViewHolder, position: Int) {
        val strategy = strategies[position]

        holder.commandTextView.text = strategy.command
        holder.commandTextView.setOnClickListener {
            showCommandMenu(strategy.command)
        }

        if (strategy.maxProgress > 0) {
            holder.progressLayout.visibility = View.VISIBLE
            holder.progressBar.max = strategy.maxProgress
            holder.progressTextView.visibility = View.VISIBLE

            if (strategy.isCompleted) {
                holder.progressBar.progress = strategy.successCount
                holder.progressTextView.text = "${strategy.successCount}/${strategy.totalRequests}"
            } else {
                holder.progressBar.progress = strategy.currentProgress
                holder.progressTextView.text = "${strategy.currentProgress}/${strategy.maxProgress}"
            }
        } else {
            holder.progressLayout.visibility = View.GONE
        }

        if (strategy.siteResults.isNotEmpty()) {
            holder.expandButton.visibility = View.VISIBLE

            holder.expandButton.setOnClickListener {
                strategy.isExpanded = !strategy.isExpanded
                notifyItemChanged(position)
            }

            if (strategy.isExpanded) {
                holder.expandTextView.text = context.getString(R.string.test_hide_details)
                holder.expandIcon.rotation = 180f
                holder.sitesRecyclerView.visibility = View.VISIBLE

                val siteAdapter = SiteResultAdapter()
                holder.sitesRecyclerView.layoutManager = LinearLayoutManager(context)
                holder.sitesRecyclerView.adapter = siteAdapter
                siteAdapter.updateSites(strategy.siteResults)
            } else {
                holder.expandTextView.text = context.getString(R.string.test_show_details)
                holder.expandIcon.rotation = 0f
                holder.sitesRecyclerView.visibility = View.GONE
            }
        } else {
            holder.expandButton.visibility = View.GONE
            holder.sitesRecyclerView.visibility = View.GONE
        }
    }

    override fun onBindViewHolder(holder: StrategyViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val strategy = strategies[position]

            if (strategy.maxProgress > 0) {
                holder.progressBar.progress = if (strategy.isCompleted) strategy.successCount else strategy.currentProgress
                holder.progressTextView.text = if (strategy.isCompleted) {
                    "${strategy.successCount}/${strategy.totalRequests}"
                } else {
                    "${strategy.currentProgress}/${strategy.maxProgress}"
                }
            }

            if (strategy.siteResults.isNotEmpty() && holder.expandButton.isGone) {
                holder.expandButton.visibility = View.VISIBLE
                holder.expandButton.setOnClickListener {
                    strategy.isExpanded = !strategy.isExpanded
                    notifyItemChanged(position)
                }
            }

            if (strategy.isExpanded && holder.sitesRecyclerView.isVisible) {
                val adapter = holder.sitesRecyclerView.adapter
                if (adapter is SiteResultAdapter) {
                    adapter.updateSites(strategy.siteResults)
                }
            }
        }
    }


    override fun getItemCount() = strategies.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateStrategies(newStrategies: List<StrategyResult>, sortByPercentage: Boolean = true) {
        strategies.clear()

        val sorted = if (sortByPercentage) {
            newStrategies.sortedWith(compareByDescending<StrategyResult> { it.isCompleted }
                .thenByDescending { it.successPercentage }
                .thenByDescending { it.successCount })
        } else {
            newStrategies
        }

        strategies.addAll(sorted)
        notifyDataSetChanged()
    }

    private fun showCommandMenu(command: String) {
        val menuItems = arrayOf(
            context.getString(R.string.test_cmd_connect),
            context.getString(R.string.test_cmd_apply),
            context.getString(R.string.cmd_history_copy)
        )

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.cmd_history_menu))
            .setItems(menuItems) { _, which ->
                when (which) {
                    0 -> onConnect(command)
                    1 -> onApply(command)
                    2 -> copyToClipboard(command)
                }
            }
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("command", text)
        clipboard.setPrimaryClip(clip)
    }
}