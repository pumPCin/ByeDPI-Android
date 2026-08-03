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
    private val separatePreferenceKeys = setOf("byedpi_command_history", "selected_apps")

    enum class Section {
        SETTINGS,
        HISTORY,
        APPS,
        DOMAIN_LISTS
    }

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

    fun exportSettings(context: Context, uri: Uri, sections: Set<Section>) {
        try {
            val prefs = context.getPreferences()

            val export = AppSettings(
                app = BuildConfig.APPLICATION_ID,
                version = BuildConfig.VERSION_NAME,
                history = if (Section.HISTORY in sections) {
                    HistoryUtils(context).getHistory()
                } else {
                    null
                },
                apps = if (Section.APPS in sections) prefs.getSelectedApps() else null,
                domainLists = if (Section.DOMAIN_LISTS in sections) {
                    DomainListUtils.getAllLists(context)
                } else {
                    null
                },
                settings = if (Section.SETTINGS in sections) {
                    prefs.all.filterKeys { it !in separatePreferenceKeys }
                } else {
                    null
                }
            )

            val json = Gson().toJson(export)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
            }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun readSettings(context: Context, uri: Uri): AppSettings? {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            } ?: return null

            val settings = Gson().fromJson(json, AppSettings::class.java)
            if (settings?.app == BuildConfig.APPLICATION_ID) {
                settings
            } else {
                Toast.makeText(context, R.string.import_failed, Toast.LENGTH_LONG).show()
                null
            }
        } catch (e: Exception) {
            Toast.makeText(context, R.string.import_failed, Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun getAvailableSections(settings: AppSettings): Set<Section> = buildSet {
        if (settings.settings != null) add(Section.SETTINGS)
        if (settings.history != null) add(Section.HISTORY)
        if (settings.apps != null) add(Section.APPS)
        if (settings.domainLists != null) add(Section.DOMAIN_LISTS)
    }

    fun importSettings(
        context: Context,
        import: AppSettings,
        sections: Set<Section>,
        onRestart: () -> Unit
    ) {
        try {
            val prefs = context.getPreferences()

            if (Section.SETTINGS in sections && import.settings != null) {
                prefs.edit(commit = true) {
                    prefs.all.keys
                        .filter { it !in separatePreferenceKeys }
                        .forEach { remove(it) }

                    import.settings
                        .filterKeys { it !in separatePreferenceKeys }
                        .forEach { (key, value) ->
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
            }

            if (Section.APPS in sections && import.apps != null) {
                prefs.edit(commit = true) { putStringSet("selected_apps", import.apps.toSet()) }
            }

            if (Section.HISTORY in sections && import.history != null) {
                HistoryUtils(context).saveHistory(import.history)
            }

            if (Section.DOMAIN_LISTS in sections && import.domainLists != null) {
                val normalized = import.domainLists.map {
                    if (it.isBuiltIn) {
                        it.copy(
                            isModified = it.isModified,
                            isDeleted = it.isDeleted
                        )
                    } else {
                        it.copy(
                            isModified = false,
                            isDeleted = false
                        )
                    }
                }

                DomainListUtils.saveLists(context, normalized)
            }

            if (Section.SETTINGS in sections) {
                val newLang = prefs.getString("language", "system") ?: "system"
                val newTheme = prefs.getString("app_theme", "system") ?: "system"
                setTheme(newTheme)
            }

            Handler(Looper.getMainLooper()).post {
                onRestart()
            }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, R.string.import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun resetSettings(context: Context, onRestart: () -> Unit) {
        try {
            val prefs = context.getPreferences()

            prefs.edit(commit = true) {
                clear()
            }

            HistoryUtils(context).saveHistory(emptyList())
            DomainListUtils.resetLists(context)

            setTheme("system")

            Handler(Looper.getMainLooper()).post {
                onRestart()
            }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Failed to reset settings", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
