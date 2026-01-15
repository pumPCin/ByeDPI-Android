package io.github.dovecoteescapee.byedpi.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.fragments.ByeDpiCommandLineSettingsFragment
import io.github.dovecoteescapee.byedpi.fragments.ByeDpiUISettingsFragment
import io.github.dovecoteescapee.byedpi.fragments.MainSettingsFragment
import io.github.dovecoteescapee.byedpi.utility.SettingsUtils
import io.github.dovecoteescapee.byedpi.utility.getPreferences
import androidx.core.content.edit

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val openFragment = intent.getStringExtra("open_fragment")

        when (openFragment) {
            "cmd" -> {
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, ByeDpiCommandLineSettingsFragment())
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
            val prefs = getPreferences()
            prefs.edit { clear() }
            recreate()
            true
        }

        R.id.action_export_settings -> {
            val fileName = "bbd_${System.currentTimeMillis().toReadableDateTime()}.json"
            exportSettingsLauncher.launch(fileName)
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
            SettingsUtils.exportSettings(this, it)
        }
    }

    private val importSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            SettingsUtils.importSettings(this, it) {
                recreate()
            }
        }
    }

    private fun Long.toReadableDateTime(): String {
        val format = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault())
        return format.format(this)
    }
}
