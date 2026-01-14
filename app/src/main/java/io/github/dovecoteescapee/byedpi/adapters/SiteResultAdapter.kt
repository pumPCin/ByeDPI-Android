package io.github.dovecoteescapee.byedpi.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.data.SiteResult

class SiteResultAdapter : RecyclerView.Adapter<SiteResultAdapter.SiteViewHolder>() {

    private val sites = mutableListOf<SiteResult>()

    class SiteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val siteTextView: TextView = view.findViewById(R.id.siteTextView)
        val resultTextView: TextView = view.findViewById(R.id.siteResultTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SiteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_site_result, parent, false)
        return SiteViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: SiteViewHolder, position: Int) {
        val site = sites[position]
        holder.siteTextView.text = site.site
        holder.resultTextView.text = "${site.successCount}/${site.totalCount}"
    }

    override fun getItemCount() = sites.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateSites(newSites: List<SiteResult>) {
        sites.clear()
        sites.addAll(newSites)
        notifyDataSetChanged()
    }
}
