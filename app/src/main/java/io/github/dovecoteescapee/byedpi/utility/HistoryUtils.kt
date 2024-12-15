package io.github.dovecoteescapee.byedpi.utility

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class HistoryUtils(context: Context) {

    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private val historyKey = "byedpi_cmd_history"
    private val pinnedHistoryKey = "byedpi_cmd_pinned_history"
    private val maxHistorySize = 20

    fun addCommand(command: String) {
        if (command.isBlank()) return

        val history = getHistory().toMutableList()

        history.remove(command)
        history.add(0, command)
        if (history.size > maxHistorySize) {
            history.removeAt(maxHistorySize)
        }

        saveHistory(history, historyKey)
    }

    fun pinCommand(command: String) {
        val pinnedHistory = getPinnedHistory().toMutableList()
        if (!pinnedHistory.contains(command)) {
            pinnedHistory.add(command)
            saveHistory(pinnedHistory, pinnedHistoryKey)
        }
    }

    fun unpinCommand(command: String) {
        val pinnedHistory = getPinnedHistory().toMutableList()
        if (pinnedHistory.remove(command)) {
            saveHistory(pinnedHistory, pinnedHistoryKey)
        }
    }

    fun deleteCommand(command: String) {
        val history = getHistory().toMutableList()
        val pinnedHistory = getPinnedHistory().toMutableList()

        history.remove(command)
        pinnedHistory.remove(command)

        saveHistory(history, historyKey)
        saveHistory(pinnedHistory, pinnedHistoryKey)
    }

    fun getHistory(): List<String> {
        return sharedPreferences.getStringSet(historyKey, emptySet())?.toList() ?: emptyList()
    }

    fun getPinnedHistory(): List<String> {
        return sharedPreferences.getStringSet(pinnedHistoryKey, emptySet())?.toList() ?: emptyList()
    }

    private fun saveHistory(history: List<String>, key: String) {
        sharedPreferences.edit().putStringSet(key, history.toSet()).apply()
    }
}
