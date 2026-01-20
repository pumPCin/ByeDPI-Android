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

        setupNumberSummary("byedpi_proxytest_delay", R.string.proxytest_delay_desc)
        setupNumberSummary("byedpi_proxytest_requests", R.string.proxytest_requests_desc)
        setupNumberSummary("byedpi_proxytest_limit", R.string.proxytest_limit_desc)
        setupNumberSummary("byedpi_proxytest_timeout", R.string.proxytest_timeout_desc)

        setEditTestPreferenceListenerInt("byedpi_proxytest_delay", 0, 60)
        setEditTestPreferenceListenerInt("byedpi_proxytest_requests", 1, 20)
        setEditTestPreferenceListenerInt("byedpi_proxytest_timeout", 1, 20)
        setEditTestPreferenceListenerInt("byedpi_proxytest_limit", 1, 50)

        setEditTestPreferenceListenerDomain("byedpi_proxytest_sni")

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
        val switchUserCommands = findPreferenceNotNull<SwitchPreference>("byedpi_proxytest_usercommands")
        val textUserDomains = findPreferenceNotNull<EditTextPreference>("byedpi_proxytest_domains")
        val textUserCommands = findPreferenceNotNull<EditTextPreference>("byedpi_proxytest_commands")
        val domainLists = findPreferenceNotNull<MultiSelectListPreference>("byedpi_proxytest_domain_lists")

        val setUserCommands = { enable: Boolean -> textUserCommands.isEnabled = enable }

        textUserDomains.isEnabled = domainLists.values?.contains("custom") == true
        setUserCommands(switchUserCommands.isChecked)

        if (domainLists.values?.isNotEmpty() == true) {
            domainLists.summary = domainLists.values.joinToString("\n")
        }
    }

    private fun setupNumberSummary(key: String, descriptionResId: Int) {
        val pref = findPreference<EditTextPreference>(key) ?: return

        pref.summaryProvider = Preference.SummaryProvider<EditTextPreference> { p ->
            val value = p.text ?: "—"
            val description = getString(descriptionResId)
            "$value - $description"
        }
    }
}
