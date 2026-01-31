package io.github.dovecoteescapee.byedpi.utility

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.google.gson.Gson
import io.github.dovecoteescapee.byedpi.BuildConfig
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.data.AppSettings

object SettingsUtils {
    private const val TAG = "SettingsUtils"

    fun setTheme(name: String) {
        val appTheme = themeByName(name) ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

        if (AppCompatDelegate.getDefaultNightMode() != appTheme) {
            AppCompatDelegate.setDefaultNightMode(appTheme)
        }
    }

    private fun themeByName(name: String): Int? = when (name) {
        "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> {
            null
        }
    }

    fun exportSettings(context: Context, uri: Uri) {
        try {
            val prefs = context.getPreferences()
            val history = HistoryUtils(context).getHistory()
            val apps = prefs.getSelectedApps()

            val settings = prefs.all.filterKeys { key ->
                key !in setOf("byedpi_command_history", "selected_apps")
            }

            val domainLists = DomainListUtils.getLists(context)

            val export = AppSettings(
                app = BuildConfig.APPLICATION_ID,
                version = BuildConfig.VERSION_NAME,
                history = history,
                apps = apps,
                domainLists = domainLists,
                settings = settings
            )

            val json = Gson().toJson(export)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
            }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Failed to export settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun importSettings(context: Context, uri: Uri, onRestart: () -> Unit) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val json = inputStream.bufferedReader().readText()
                val import = try {
                    Gson().fromJson(json, AppSettings::class.java)
                } catch (e: Exception) {
                    null
                }

                if (import == null || import.app != BuildConfig.APPLICATION_ID) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, R.string.logs_failed, Toast.LENGTH_LONG).show()
                    }
                    return@use
                }

                val prefs = context.getPreferences()
                prefs.edit (commit = true) {
                    clear()
                    import.settings.forEach { (key, value) ->
                        when (value) {
                            is Int -> putInt(key, value)
                            is Boolean -> putBoolean(key, value)
                            is String -> putString(key, value)
                            is Float -> putFloat(key, value)
                            is Long -> putLong(key, value)
                            is Double -> {
                                when (value) {
                                    value.toInt().toDouble() -> {
                                        putInt(key, value.toInt())
                                    }
                                    value.toLong().toDouble() -> {
                                        putLong(key, value.toLong())
                                    }
                                    else -> {
                                        putFloat(key, value.toFloat())
                                    }
                                }
                            }
                            is Collection<*> -> {
                                if (value.all { it is String }) {
                                    @Suppress("UNCHECKED_CAST")
                                    putStringSet(key, (value as Collection<String>).toSet())
                                }
                            }
                        }
                    }

                }

                if (import.apps !== null) {
                    prefs.edit (commit = true) { putStringSet("selected_apps", import.apps.toSet()) }
                }

                if (import.history !== null) {
                    HistoryUtils(context).saveHistory(import.history)
                }

                if (import.domainLists !== null) {
                    DomainListUtils.saveLists(context, import.domainLists)
                }

                Handler(Looper.getMainLooper()).post {
                    val newTheme = prefs.getString("app_theme", "system") ?: "system"
                    setTheme(newTheme)
                    onRestart()
                }
            }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Failed to import settings", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
