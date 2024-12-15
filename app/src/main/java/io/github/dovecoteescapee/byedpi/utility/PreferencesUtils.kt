package io.github.dovecoteescapee.byedpi.utility

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import io.github.dovecoteescapee.byedpi.data.Mode

val PreferenceFragmentCompat.sharedPreferences
    get() = preferenceScreen.sharedPreferences

fun Context.getPreferences(): SharedPreferences =
    PreferenceManager.getDefaultSharedPreferences(this)

fun SharedPreferences.getStringNotNull(key: String, defValue: String): String =
    getString(key, defValue) ?: defValue

fun SharedPreferences.mode(): Mode =
    Mode.fromString(getStringNotNull("byedpi_mode", "vpn"))

fun <T : Preference> PreferenceFragmentCompat.findPreferenceNotNull(key: CharSequence): T =
    findPreference(key) ?: throw IllegalStateException("Preference $key not found")

fun SharedPreferences.getSelectedApps(): List<String> {
    return getStringSet("selected_apps", emptySet())?.toList() ?: emptyList()
}

fun SharedPreferences.getProxyIpAndPort(): Pair<String, String> {
    val cmdEnable = getBoolean("byedpi_enable_cmd_settings", true)
    val cmdArgs = if (cmdEnable) getString("byedpi_cmd_args", "--split 2 --disorder 3 --fake -1 --ttl 5") else null
    val args = cmdArgs?.split(" ") ?: emptyList()

    fun getArgValue(argsList: List<String>, keys: List<String>): String? {
        for (key in keys) {
            val index = argsList.indexOf(key)
            if (index != -1 && index + 1 < argsList.size) {
                return argsList[index + 1]
            }
        }
        return null
    }

    val cmdIp = getArgValue(args, listOf("-i", "--ip"))
    val cmdPort = getArgValue(args, listOf("-p", "--port"))

    val ip = cmdIp ?: getStringNotNull("byedpi_proxy_ip", "127.0.0.1")
    val port = cmdPort ?: getStringNotNull("byedpi_proxy_port", "1080")

    return Pair(ip, port)
}
