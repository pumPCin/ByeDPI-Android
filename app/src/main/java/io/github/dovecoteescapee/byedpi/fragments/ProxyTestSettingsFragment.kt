package io.github.dovecoteescapee.byedpi.fragments

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.*
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.utility.*

class ProxyTestSettingsFragment : PreferenceFragmentCompat() {

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            updatePreferences()
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.proxy_test_settings, rootKey)
        updatePreferences()
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences?.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun updatePreferences() {
        val switchUserDomains = findPreferenceNotNull<SwitchPreference>("byedpi_proxytest_userdomains")
        val switchUserCommands = findPreferenceNotNull<SwitchPreference>("byedpi_proxytest_usercommands")
        val textUserDomains = findPreferenceNotNull<EditTextPreference>("byedpi_proxytest_domains")
        val textUserCommands = findPreferenceNotNull<EditTextPreference>("byedpi_proxytest_commands")

        val setUserDomains = { enable: Boolean -> textUserDomains.isEnabled = enable }
        val setUserCommands = { enable: Boolean -> textUserCommands.isEnabled = enable }

        setUserDomains(switchUserDomains.isChecked)
        setUserCommands(switchUserCommands.isChecked)
    }
}
