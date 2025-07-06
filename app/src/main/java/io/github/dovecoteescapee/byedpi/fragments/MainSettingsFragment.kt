package io.github.dovecoteescapee.byedpi.fragments

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.*
import io.github.dovecoteescapee.byedpi.BuildConfig
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.activities.TestActivity
import io.github.dovecoteescapee.byedpi.data.Mode
import io.github.dovecoteescapee.byedpi.utility.*

class MainSettingsFragment : PreferenceFragmentCompat() {
    companion object {
        private val TAG: String = MainSettingsFragment::class.java.simpleName

        fun setTheme(name: String) =
            themeByName(name)?.let {
                AppCompatDelegate.setDefaultNightMode(it)
            } ?: throw IllegalStateException("Invalid value for app_theme: $name")

        private fun themeByName(name: String): Int? = when (name) {
            "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> {
                null
            }
        }
    }

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            updatePreferences()
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_settings, rootKey)

        setEditTextPreferenceListener("byedpi_proxy_ip") { checkIp(it) }
        setEditTestPreferenceListenerPort("byedpi_proxy_port")

        setEditTextPreferenceListener("dns_ip") {
            it.isBlank() || checkNotLocalIp(it)
        }

        findPreferenceNotNull<ListPreference>("app_theme")
            .setOnPreferenceChangeListener { _, newValue ->
                setTheme(newValue as String)
                true
            }
        
        val switchCmdSettings = findPreferenceNotNull<SwitchPreference>("byedpi_enable_cmd_settings")
        val uiSettings = findPreferenceNotNull<Preference>("byedpi_ui_settings")
        val cmdSettings = findPreferenceNotNull<Preference>("byedpi_cmd_settings")
        val proxyTest = findPreferenceNotNull<Preference>("proxy_test")

        val setByeDpiSettingsMode = { enable: Boolean ->
            uiSettings.isEnabled = !enable
            cmdSettings.isEnabled = enable
            proxyTest.isEnabled = enable
        }

        setByeDpiSettingsMode(switchCmdSettings.isChecked)

        switchCmdSettings.setOnPreferenceChangeListener { _, newValue ->
            setByeDpiSettingsMode(newValue as Boolean)
            updatePreferences()
            true
        }

        findPreferenceNotNull<Preference>("proxy_test")
            .setOnPreferenceClickListener {
                val intent = Intent(context, TestActivity::class.java)
                startActivity(intent)
                true
            }

        findPreferenceNotNull<Preference>("version").summary = BuildConfig.VERSION_NAME

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
        val mode = findPreferenceNotNull<ListPreference>("byedpi_mode").value.let { Mode.fromString(it) }
        val dns = findPreferenceNotNull<EditTextPreference>("dns_ip")
        val ipv6 = findPreferenceNotNull<SwitchPreference>("ipv6_enable")
        val proxy = findPreferenceNotNull<PreferenceCategory>("byedpi_proxy_category")

        val applistType = findPreferenceNotNull<ListPreference>("applist_type")
        val selectedApps = findPreferenceNotNull<Preference>("selected_apps")

        if (sharedPreferences?.getBoolean("byedpi_enable_cmd_settings", false) == true) {
            val (cmdIp, cmdPort) = sharedPreferences?.checkIpAndPortInCmd() ?: Pair(null, null)
            proxy.isVisible = cmdIp == null && cmdPort == null
        } else {
            proxy.isVisible = true
        }

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
    }
}
