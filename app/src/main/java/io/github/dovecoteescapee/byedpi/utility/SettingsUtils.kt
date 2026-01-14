package io.github.dovecoteescapee.byedpi.utility

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
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

    fun setLang(lang: String) {
        val appLocale = localeByName(lang) ?: throw IllegalStateException("Invalid value for language: $lang")

        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != appLocale.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }

    private fun localeByName(lang: String): LocaleListCompat? = when (lang) {
        "system" -> LocaleListCompat.getEmptyLocaleList()
        "ru" -> LocaleListCompat.forLanguageTags("ru")
        "en" -> LocaleListCompat.forLanguageTags("en")
        "tr" -> LocaleListCompat.forLanguageTags("tr")
        "kk" -> LocaleListCompat.forLanguageTags("kk")
        else -> {
            Log.w(TAG, "Invalid value for language: $lang")
            null
        }
    }

    fun setTheme(name: String) {
        val appTheme = themeByName(name) ?: throw IllegalStateException("Invalid value for app_theme: $name")

        if (AppCompatDelegate.getDefaultNightMode() != appTheme) {
            AppCompatDelegate.setDefaultNightMode(appTheme)
        }
    }

    private fun themeByName(name: String): Int? = when (name) {
        "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> {
            Log.w(TAG, "Invalid value for app_theme: $name")
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

            val export = AppSettings(
                app = BuildConfig.APPLICATION_ID,
                version = BuildConfig.VERSION_NAME,
                history = history,
                apps = apps,
                settings = settings
            )

            val json = Gson().toJson(export)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export settings", e)
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
                prefs.edit {
                    clear()
                    import.settings.forEach { (key, value) ->
                        when (value) {
                            is Int -> putInt(key, value)
                            is Boolean -> putBoolean(key, value)
                            is String -> putString(key, value)
                            is Float -> putFloat(key, value)
                            is Long -> putLong(key, value)
                            is Collection<*> -> {
                                if (value.all { it is String }) {
                                    @Suppress("UNCHECKED_CAST")
                                    putStringSet(key, (value as Collection<String>).toSet())
                                }
                            }
                        }
                    }
                    putStringSet("selected_apps", import.apps.toSet())
                }
                HistoryUtils(context).saveHistory(import.history)

                onRestart()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import settings", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Failed to import settings", Toast.LENGTH_SHORT).show()
            }
        }
    }
}