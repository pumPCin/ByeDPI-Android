package io.github.dovecoteescapee.byedpi.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import androidx.preference.*
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.utility.findPreferenceNotNull
import androidx.appcompat.app.AlertDialog
import io.github.dovecoteescapee.byedpi.data.Command
import io.github.dovecoteescapee.byedpi.utility.HistoryUtils

class ByeDpiCommandLineSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var cmdHistoryUtils: HistoryUtils
    private lateinit var editTextPreference: EditTextPreference
    private lateinit var historyCategory: PreferenceCategory

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.byedpi_cmd_settings, rootKey)

        cmdHistoryUtils = HistoryUtils(requireContext())

        editTextPreference = findPreferenceNotNull("byedpi_cmd_args")
        historyCategory = findPreferenceNotNull("cmd_history_category")

        editTextPreference.setOnPreferenceChangeListener { _, newValue ->
            val newCommand = newValue.toString()
            if (newCommand.isNotBlank()) cmdHistoryUtils.addCommand(newCommand)
            updateHistoryCategory()
            true
        }

        updateHistoryCategory()
    }

    private fun updateHistoryCategory() {
        historyCategory.removeAll()
        val history = cmdHistoryUtils.getHistory()

        history.sortedWith(compareByDescending<Command> { it.pinned }.thenBy { history.indexOf(it) })
            .forEach { command ->
                val preference = createPreference(command)
                historyCategory.addPreference(preference)
            }
    }

    private fun createPreference(command: Command) =
        Preference(requireContext()).apply {
            title = command.text
            summary = buildSummary(command)
            setOnPreferenceClickListener {
                showActionDialog(command)
                true
            }
        }

    private fun buildSummary(command: Command): String {
        val summary = StringBuilder()
        if (command.name != null) {
            summary.append(command.name)
        }
        if (command.pinned) {
            if (summary.isNotEmpty()) summary.append(" - ")
            summary.append(context?.getString(R.string.cmd_history_pinned))
        }
        return summary.toString()
    }

    private fun showActionDialog(command: Command) {
        val options = arrayOf(
            getString(R.string.cmd_history_apply),
            if (command.pinned) getString(R.string.cmd_history_unpin) else getString(R.string.cmd_history_pin),
            getString(R.string.cmd_history_rename),
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
                    3 -> copyToClipboard(command.text)
                    4 -> deleteCommand(command.text)
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
                if (newName.isNotBlank()) {
                    cmdHistoryUtils.renameCommand(command.text, newName)
                    updateHistoryCategory()
                }
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }

    private fun applyCommand(command: String) {
        editTextPreference.text = command
    }

    private fun pinCommand(command: String) {
        cmdHistoryUtils.pinCommand(command)
        updateHistoryCategory()
    }

    private fun unpinCommand(command: String) {
        cmdHistoryUtils.unpinCommand(command)
        updateHistoryCategory()
    }

    private fun deleteCommand(command: String) {
        cmdHistoryUtils.deleteCommand(command)
        updateHistoryCategory()
    }

    private fun copyToClipboard(command: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Command", command)
        clipboard.setPrimaryClip(clip)
    }
}
