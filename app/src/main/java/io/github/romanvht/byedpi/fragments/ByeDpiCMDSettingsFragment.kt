package io.github.romanvht.byedpi.fragments

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.*
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.utility.findPreferenceNotNull
import androidx.appcompat.app.AlertDialog
import io.github.romanvht.byedpi.data.Command
import io.github.romanvht.byedpi.utility.ClipboardUtils
import io.github.romanvht.byedpi.utility.HistoryUtils

class ByeDpiCMDSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var cmdHistoryUtils: HistoryUtils
    private lateinit var editTextPreference: EditTextPreference
    private lateinit var historyHeader: Preference
    private val historyPreferences = mutableListOf<Preference>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.byedpi_cmd_settings, rootKey)

        cmdHistoryUtils = HistoryUtils(requireContext())

        editTextPreference = findPreferenceNotNull("byedpi_cmd_args")
        historyHeader = findPreferenceNotNull("cmd_history_header")

        editTextPreference.setOnPreferenceChangeListener { _, newValue ->
            val newCommand = newValue.toString()
            if (newCommand.isNotBlank()) cmdHistoryUtils.addCommand(newCommand)
            updateHistoryItems()
            true
        }

        historyHeader.setOnPreferenceClickListener {
            showHistoryClearDialog()
            true
        }

        updateHistoryItems()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.post {
            view.findViewById<View>(R.id.btn_clear)?.setOnClickListener {
                editTextPreference.text = ""
                updateHistoryItems()
            }

            view.findViewById<View>(R.id.btn_paste)?.setOnClickListener {
                val text = ClipboardUtils.paste(requireContext())
                if (!text.isNullOrBlank()) {
                    editTextPreference.text = text
                    cmdHistoryUtils.addCommand(text)
                    updateHistoryItems()
                }
            }
        }
    }

    private fun updateHistoryItems() {
        if (listView !== null) {
            listView.itemAnimator = null
        }

        historyPreferences.forEach { preference ->
            preferenceScreen.removePreference(preference)
        }

        historyPreferences.clear()
        val history = cmdHistoryUtils.getHistory()
        historyHeader.isVisible = history.isNotEmpty()

        if (history.isNotEmpty()) {
            history.sortedWith(compareByDescending<Command> { it.pinned }.thenBy { history.indexOf(it) })
                .forEachIndexed { _, command ->
                    val preference = createPreference(command)
                    historyPreferences.add(preference)
                    preferenceScreen.addPreference(preference)
                }
        }
    }

    private fun createPreference(command: Command) =
        object : Preference(requireContext()) {
            override fun onBindViewHolder(holder: PreferenceViewHolder) {
                super.onBindViewHolder(holder)

                val nameView = holder.itemView.findViewById<TextView>(R.id.command_name)
                val summaryView = holder.itemView.findViewById<TextView>(android.R.id.summary)
                val pinIcon = holder.itemView.findViewById<ImageView>(R.id.pin_icon)
                val commandName = command.name

                if (!commandName.isNullOrBlank()) {
                    nameView.visibility = View.VISIBLE
                    nameView.text = command.name
                } else {
                    nameView.visibility = View.GONE
                }

                val summaryText = buildSummary(command)
                if (summaryText.isNotBlank()) {
                    summaryView.visibility = View.VISIBLE
                    summaryView.text = summaryText
                } else {
                    summaryView.visibility = View.GONE
                }

                pinIcon.visibility = if (command.pinned) View.VISIBLE else View.GONE
            }
        }.apply {
            title = command.text
            layoutResource = R.layout.history_item
            setOnPreferenceClickListener {
                showActionDialog(command)
                true
            }
        }

    private fun buildSummary(command: Command): String {
        val parts = mutableListOf<String>()

        if (command.text == editTextPreference.text) {
            parts.add(getString(R.string.cmd_history_applied))
        }

        return parts.joinToString(" • ")
    }

    private fun showHistoryClearDialog() {
        val options = arrayOf(
            getString(R.string.cmd_history_delete_unpinned),
            getString(R.string.cmd_history_delete_all),
        )

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.cmd_history_menu))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> deleteUnpinnedHistory()
                    1 -> deleteAllHistory()
                }
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }

    private fun deleteAllHistory() {
        cmdHistoryUtils.clearAllHistory()
        updateHistoryItems()
    }

    private fun deleteUnpinnedHistory() {
        cmdHistoryUtils.clearUnpinnedHistory()
        updateHistoryItems()
    }

    private fun showActionDialog(command: Command) {
        val options = arrayOf(
            getString(R.string.cmd_history_apply),
            if (command.pinned) getString(R.string.cmd_history_unpin) else getString(R.string.cmd_history_pin),
            getString(R.string.cmd_history_rename),
            getString(R.string.cmd_history_edit),
            getString(R.string.cmd_history_copy),
            getString(R.string.cmd_history_delete)
        )

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.cmd_history_menu))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> applyCommand(command.text)
                    1 -> if (command.pinned) unpinCommand(command.text) else pinCommand(command.text)
                    2 -> showRenameDialog(command)
                    3 -> showEditDialog(command)
                    4 -> ClipboardUtils.copy(requireContext(), command.text, "command")
                    5 -> deleteCommand(command.text)
                }
            }
            .show()
    }

    private fun showRenameDialog(command: Command) {
        val input = EditText(requireContext()).apply {
            setText(command.name)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(input)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.cmd_history_rename))
            .setView(container)
            .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                val newName = input.text.toString()
                if (newName != command.name) {
                    cmdHistoryUtils.renameCommand(command.text, newName)
                    updateHistoryItems()
                }
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }

    private fun showEditDialog(command: Command) {
        val input = EditText(requireContext()).apply {
            setText(command.text)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(input)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.cmd_history_edit))
            .setView(container)
            .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                val newText = input.text.toString()
                if (newText.isNotBlank() && newText != command.text) {
                    cmdHistoryUtils.editCommand(command.text, newText)
                    if (editTextPreference.text == command.text) applyCommand(newText)
                    updateHistoryItems()
                }
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }

    private fun applyCommand(command: String) {
        editTextPreference.text = command
        updateHistoryItems()
    }

    private fun pinCommand(command: String) {
        cmdHistoryUtils.pinCommand(command)
        updateHistoryItems()
    }

    private fun unpinCommand(command: String) {
        cmdHistoryUtils.unpinCommand(command)
        updateHistoryItems()
    }

    private fun deleteCommand(command: String) {
        cmdHistoryUtils.deleteCommand(command)
        updateHistoryItems()
    }
}