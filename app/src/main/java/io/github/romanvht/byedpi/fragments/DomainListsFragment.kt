package io.github.romanvht.byedpi.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.adapters.DomainListAdapter
import io.github.romanvht.byedpi.data.DomainList
import io.github.romanvht.byedpi.utility.ClipboardUtils
import io.github.romanvht.byedpi.utility.DomainListUtils

class DomainListsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DomainListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.domain_lists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        DomainListUtils.initializeDefaultLists(requireContext())
        recyclerView = view.findViewById(R.id.domain_lists_recycler)

        view.findViewById<View>(R.id.add_new_list_button).setOnClickListener {
            showAddDialog()
        }

        adapter = DomainListAdapter(
            onToggle = { domainList ->
                DomainListUtils.toggleListActive(requireContext(), domainList.id)
                refreshLists()
            },
            onEdit = { domainList ->
                showEditDialog(domainList)
            },
            onDelete = { domainList ->
                DomainListUtils.deleteList(requireContext(), domainList.id)
                refreshLists()
            },
            onCopy = { domainList ->
                copyDomainsToClipboard(domainList)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        refreshLists()
    }

    private fun refreshLists() {
        val lists = DomainListUtils.getLists(requireContext())
        adapter.submitList(lists)
    }

    private fun copyDomainsToClipboard(domainList: DomainList) {
        val domainsText = domainList.domains.joinToString("\n")
        ClipboardUtils.copy(requireContext(), domainsText, domainList.name, showToast = false)
        Toast.makeText(requireContext(), R.string.toast_copied, Toast.LENGTH_SHORT).show()
    }

    private fun showAddDialog() {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val nameInput = EditText(context).apply {
            hint = getString(R.string.domain_list_name_hint)
        }

        val domainsInput = EditText(context).apply {
            hint = getString(R.string.domain_list_domains_hint)
            minLines = 5
            gravity = android.view.Gravity.BOTTOM
        }

        layout.addView(nameInput)
        layout.addView(domainsInput)

        AlertDialog.Builder(context, R.style.CustomAlertDialog)
            .setTitle(R.string.domain_list_add_new)
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = nameInput.text.toString().trim()
                val domainsText = domainsInput.text.toString()
                val domains = domainsText.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                if (name.isEmpty()) {
                    Toast.makeText(context, R.string.domain_list_name_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (domains.isEmpty()) {
                    Toast.makeText(context, R.string.domain_list_domains_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (DomainListUtils.addList(context, name, domains)) {
                    Toast.makeText(context, R.string.domain_list_added, Toast.LENGTH_SHORT).show()
                    refreshLists()
                } else {
                    Toast.makeText(context, R.string.domain_list_already_exists, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(domainList: DomainList) {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val nameInput = EditText(context).apply {
            hint = getString(R.string.domain_list_name_hint)
            setText(domainList.name)
        }

        val domainsInput = EditText(context).apply {
            hint = getString(R.string.domain_list_domains_hint)
            minLines = 5
            gravity = android.view.Gravity.BOTTOM
            setText(domainList.domains.joinToString("\n"))
        }

        layout.addView(nameInput)
        layout.addView(domainsInput)

        AlertDialog.Builder(context, R.style.CustomAlertDialog)
            .setTitle(R.string.domain_list_edit)
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = nameInput.text.toString().trim()
                val domainsText = domainsInput.text.toString()
                val domains = domainsText.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                if (name.isEmpty()) {
                    Toast.makeText(context, R.string.domain_list_name_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (domains.isEmpty()) {
                    Toast.makeText(context, R.string.domain_list_domains_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (DomainListUtils.updateList(context, domainList.id, name, domains)) {
                    Toast.makeText(context, R.string.domain_list_updated, Toast.LENGTH_SHORT).show()
                    refreshLists()
                } else {
                    Toast.makeText(context, R.string.domain_list_update_failed, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}