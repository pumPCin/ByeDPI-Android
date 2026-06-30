package io.github.dovecoteescapee.byedpi.fragments

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.*
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.BuildConfig
import io.github.dovecoteescapee.byedpi.activities.TestActivity
import io.github.dovecoteescapee.byedpi.data.Mode
import io.github.dovecoteescapee.byedpi.utility.*

class MainSettingsFragment : PreferenceFragmentCompat() {
    companion object {
        private val TAG: String = MainSettingsFragment::class.java.simpleName
    }

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            updatePreferences()
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_settings, rootKey)

        setEditTextPreferenceListener("byedpi_proxy_ip") { checkIp(it) }
        setEditTestPreferenceListenerPort("byedpi_proxy_port")
        setEditTextPreferenceListener("dns_ip") { it.isBlank() || checkNotLocalIp(it) }

        findPreferenceNotNull<ListPreference>("app_theme")
            .setOnPreferenceChangeListener { _, newValue ->
                SettingsUtils.setTheme(newValue as String)
                true
            }

        findPreferenceNotNull<Preference>("proxy_test")
            .setOnPreferenceClickListener {
                val intent = Intent(context, TestActivity::class.java)
                startActivity(intent)
                true
            }

        findPreferenceNotNull<Preference>("battery_optimization")
            .setOnPreferenceClickListener {
                BatteryUtils.requestBatteryOptimization(requireContext())
                true
            }

        findPreferenceNotNull<Preference>("storage_access")
            .setOnPreferenceClickListener {
                StorageUtils.requestStoragePermission(this)
                true
            }

        findPreferenceNotNull<Preference>("dns_ip_picker")
            .setOnPreferenceClickListener {
                showDnsDialog()
                true
            }

        findPreferenceNotNull<Preference>("version").summary = BuildConfig.VERSION_NAME
        findPreferenceNotNull<Preference>("byedpi_version").summary = "0.17.3+ (latest)"

        updatePreferences()
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences?.registerOnSharedPreferenceChangeListener(preferenceListener)
        updatePreferences()
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun updatePreferences() {
        val cmdEnable = findPreferenceNotNull<SwitchPreference>("byedpi_enable_cmd_settings").isChecked
        val mode = findPreferenceNotNull<ListPreference>("byedpi_mode").value.let { Mode.fromString(it) }
        val dns = findPreferenceNotNull<Preference>("dns_ip_picker")
        val dnsEdit = findPreferenceNotNull<EditTextPreference>("dns_ip")
        val ipv6 = findPreferenceNotNull<SwitchPreference>("ipv6_enable")
        val proxy = findPreferenceNotNull<PreferenceCategory>("byedpi_proxy_category")

        val applistType = findPreferenceNotNull<ListPreference>("applist_type")
        val selectedApps = findPreferenceNotNull<Preference>("selected_apps")
        val batteryOptimization = findPreferenceNotNull<Preference>("battery_optimization")
        val storageAccess = findPreferenceNotNull<Preference>("storage_access")

        val uiSettings = findPreferenceNotNull<Preference>("byedpi_ui_settings")
        val cmdSettings = findPreferenceNotNull<Preference>("byedpi_cmd_settings")
        val proxyTest = findPreferenceNotNull<Preference>("proxy_test")

        if (cmdEnable) {
            val (cmdIp, cmdPort) = sharedPreferences?.checkIpAndPortInCmd() ?: Pair(null, null)
            proxy.isVisible = cmdIp == null && cmdPort == null
        } else {
            proxy.isVisible = true
        }

        uiSettings.isEnabled = !cmdEnable
        cmdSettings.isEnabled = cmdEnable
        proxyTest.isEnabled = cmdEnable

        dns.summary = dnsSummary()
        dnsEdit.isVisible = false

        when (mode) {
            Mode.VPN -> {
                dns.isVisible = true
                ipv6.isVisible = true

                when (applistType.value) {
                    "disable" -> {
                        applistType.isVisible = true
                        selectedApps.isVisible = false
                    }
                    "blacklist", "whitelist" -> {
                        applistType.isVisible = true
                        selectedApps.isVisible = true
                    }
                    else -> {
                        applistType.isVisible = true
                        selectedApps.isVisible = false
                    }
                }
            }

            Mode.Proxy -> {
                dns.isVisible = false
                ipv6.isVisible = false
                applistType.isVisible = false
                selectedApps.isVisible = false
            }
        }

        if (BatteryUtils.isOptimizationDisabled(requireContext())) {
            batteryOptimization.summary = getString(R.string.battery_optimization_disabled_summary)
        } else {
            batteryOptimization.summary = getString(R.string.battery_optimization_summary)
        }

        if (StorageUtils.hasStoragePermission(requireContext())) {
            storageAccess.summary = getString(R.string.storage_access_allowed_summary)
        } else {
            storageAccess.summary = getString(R.string.storage_access_summary)
        }
    }

    private val dnsPresets: List<Pair<String, String>>
        get() = listOf(
            "" to getString(R.string.dns_system),
            "8.8.4.4" to getString(R.string.dns_google),
            "1.0.0.1" to getString(R.string.dns_cloudflare),
            "9.9.9.9" to getString(R.string.dns_quad9),
        )

    private fun dnsSummary(): String {
        val ip = sharedPreferences?.getStringNotNull("dns_ip", "1.0.0.1") ?: "1.0.0.1"
        val preset = dnsPresets.firstOrNull { it.first == ip } ?: return ip
        return if (preset.first.isBlank()) preset.second else "${preset.second} ($ip)"
    }

    private fun showDnsDialog() {
        val context = requireContext()
        val presets = dnsPresets

        val items = (presets.map { it.second } + getString(R.string.dns_custom)).toTypedArray()
        val dnsPref = findPreferenceNotNull<EditTextPreference>("dns_ip")

        AlertDialog.Builder(context)
            .setTitle(getString(R.string.dbs_ip_setting))
            .setItems(items) { _, which ->
                if (which < presets.size) {
                    sharedPreferences?.edit { putString("dns_ip", presets[which].first) }
                } else {
                    onDisplayPreferenceDialog(dnsPref)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
