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

fun SharedPreferences.checkIpAndPortInCmd(): Pair<String?, String?> {
    val cmdEnable = getBoolean("byedpi_enable_cmd_settings", true)
    if (!cmdEnable) return Pair(null, null)

    val cmdArgs = getString("byedpi_cmd_args", "-s1 -q1 -Y -At -f-1 -r1+s -As")?.let { shellSplit(it) } ?: emptyList()

    fun getArgValue(argsList: List<String>, keys: List<String>): String? {
        for (key in keys) {
            val index = argsList.indexOfFirst { arg -> keys.any { arg.startsWith(it) } }
            if (index != -1) {
                val arg = argsList[index]
                val keyMatch = keys.firstOrNull { arg.startsWith(it) }
                if (keyMatch != null) {
                    return if (arg.length > keyMatch.length) {
                        arg.substring(keyMatch.length)
                    } else if (index + 1 < argsList.size) {
                        argsList[index + 1]
                    } else null
                }
            }
        }
        return null
    }

    val cmdIp = getArgValue(cmdArgs, listOf("-i", "--ip"))
    val cmdPort = getArgValue(cmdArgs, listOf("-p", "--port"))

    return Pair(cmdIp, cmdPort)
}

fun SharedPreferences.getProxyIpAndPort(): Pair<String, String> {
    val (cmdIp, cmdPort) = checkIpAndPortInCmd()

    val ip = cmdIp ?: getStringNotNull("byedpi_proxy_ip", "127.0.0.1")
    val port = cmdPort ?: getStringNotNull("byedpi_proxy_port", "1080")

    return Pair(ip, port)
}
