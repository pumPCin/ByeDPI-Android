package io.github.dovecoteescapee.byedpi.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.data.AppSettings
import io.github.dovecoteescapee.byedpi.fragments.ByeDpiCMDSettingsFragment
import io.github.dovecoteescapee.byedpi.fragments.ByeDpiUISettingsFragment
import io.github.dovecoteescapee.byedpi.fragments.MainSettingsFragment
import io.github.dovecoteescapee.byedpi.utility.SettingsUtils
import io.github.dovecoteescapee.byedpi.utility.SettingsUtils.Section

class SettingsActivity : BaseActivity() {
    private var exportSections = Section.entries.toSet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setupToolbar()

        if (savedInstanceState == null) {
            val openFragment = intent.getStringExtra("open_fragment")

            when (openFragment) {
                "cmd" -> {
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.settings, ByeDpiCMDSettingsFragment())
                        .commit()
                }
                "ui" -> {
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.settings, ByeDpiUISettingsFragment())
                        .commit()
                }
                else -> {
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.settings, MainSettingsFragment())
                        .commit()
                }
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            onBackPressedDispatcher.onBackPressed()
            true
        }

        R.id.action_reset_settings -> {
            SettingsUtils.resetSettings(this) {
                recreate()
            }
            true
        }

        R.id.action_export_settings -> {
            showSectionSelectionDialog(
                title = R.string.export_settings,
                availableSections = Section.entries.toSet()
            ) { sections ->
                exportSections = sections
                val fileName = "bbd_${System.currentTimeMillis().toReadableDateTime()}.json"
                exportSettingsLauncher.launch(fileName)
            }
            true
        }

        R.id.action_import_settings -> {
            importSettingsLauncher.launch(arrayOf("application/json"))
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private val exportSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            SettingsUtils.exportSettings(this, it, exportSections)
        }
    }

    private val importSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val settings = SettingsUtils.readSettings(this, it) ?: return@let
            val availableSections = SettingsUtils.getAvailableSections(settings)

            if (availableSections.isEmpty()) {
                return@let
            }

            showImportDialog(settings, availableSections)
        }
    }

    private fun showImportDialog(settings: AppSettings, availableSections: Set<Section>) {
        showSectionSelectionDialog(
            title = R.string.import_settings,
            availableSections = availableSections
        ) { sections ->
            SettingsUtils.importSettings(this, settings, sections) {
                recreate()
            }
        }
    }

    private fun showSectionSelectionDialog(
        @StringRes title: Int,
        availableSections: Set<Section>,
        onConfirmed: (Set<Section>) -> Unit
    ) {
        val sections = Section.entries.filter { it in availableSections }
        val checkedItems = BooleanArray(sections.size) { true }
        val labels = sections.map { getString(it.label) }.toTypedArray()

        lateinit var dialog: AlertDialog
        dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMultiChoiceItems(labels, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = checkedItems.any { it }
            }
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            positiveButton.setOnClickListener {
                val selected = sections
                    .filterIndexed { index, _ -> checkedItems[index] }
                    .toSet()

                if (selected.isNotEmpty()) {
                    dialog.dismiss()
                    onConfirmed(selected)
                }
            }

            positiveButton.isEnabled = checkedItems.any { it }
        }

        dialog.show()
    }

    @get:StringRes
    private val Section.label: Int
        get() = when (this) {
            Section.SETTINGS -> R.string.backup_settings
            Section.HISTORY -> R.string.backup_command_history
            Section.APPS -> R.string.backup_selected_apps
            Section.DOMAIN_LISTS -> R.string.backup_domain_lists
        }

    private fun Long.toReadableDateTime(): String {
        val format = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault())
        return format.format(this)
    }
}
