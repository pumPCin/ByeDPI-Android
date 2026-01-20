package io.github.romanvht.byedpi.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppSelectionAdapter(
    context: Context,
    allApps: List<AppInfo>
) : RecyclerView.Adapter<AppSelectionAdapter.ViewHolder>(), Filterable {

    private val context = context.applicationContext
    private val pm = context.packageManager
    private val originalApps: List<AppInfo> = allApps
    private val filteredApps: MutableList<AppInfo> = allApps.toMutableList()
    private val adapterScope = CoroutineScope(Dispatchers.Main + Job())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.appIcon)
        val appName: TextView = view.findViewById(R.id.appName)
        val appPackageName: TextView = view.findViewById(R.id.appPackageName)
        val appCheckBox: CheckBox = view.findViewById(R.id.appCheckBox)
        var iconLoadJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.app_selection_item, parent, false)
        val holder = ViewHolder(view)

        holder.itemView.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                val app = filteredApps[position]
                app.isSelected = !app.isSelected
                notifyItemChanged(position)
                updateSelectedApps()
            }
        }

        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = filteredApps[position]

        if (app.appName == app.packageName) {
            holder.appName.text = app.packageName
            holder.appPackageName.visibility = View.GONE
        } else {
            holder.appName.text = app.appName
            holder.appPackageName.text = app.packageName
            holder.appPackageName.visibility = View.VISIBLE
        }

        holder.appCheckBox.isChecked = app.isSelected

        holder.iconLoadJob?.cancel()

        if (app.icon != null) {
            holder.appIcon.setImageDrawable(app.icon)
        } else {
            holder.appIcon.setImageDrawable(pm.defaultActivityIcon)

            holder.iconLoadJob = adapterScope.launch {
                val icon = withContext(Dispatchers.IO) {
                    try {
                        pm.getApplicationIcon(app.packageName)
                    } catch (_: Exception) {
                        null
                    }
                }

                if (icon != null) {
                    app.icon = icon
                    holder.appIcon.setImageDrawable(icon)
                }
            }
        }
    }

    override fun getItemCount(): Int = filteredApps.size

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.iconLoadJob?.cancel()
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.lowercase().orEmpty()
                var filteredList = originalApps

                if (query.isNotEmpty()) {
                    filteredList = originalApps.filter {
                        it.appName.lowercase().contains(query) ||
                                it.packageName.lowercase().contains(query)
                    }
                }

                return FilterResults().apply {
                    values = filteredList
                }
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun publishResults(constraint: CharSequence, results: FilterResults) {
                val newList = (results.values as List<*>).filterIsInstance<AppInfo>()
                filteredApps.clear()
                filteredApps.addAll(newList)
                notifyDataSetChanged()
            }
        }
    }

    private fun getAllSelectedPackages(): Set<String> {
        return originalApps.filter { it.isSelected }.map { it.packageName }.toSet()
    }

    private fun updateSelectedApps() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val selectedApps = getAllSelectedPackages()
        prefs.edit { putStringSet("selected_apps", selectedApps) }
    }
}