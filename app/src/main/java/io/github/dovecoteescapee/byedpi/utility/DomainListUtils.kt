package io.github.dovecoteescapee.byedpi.utility

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.dovecoteescapee.byedpi.data.DomainList
import java.io.File
import androidx.core.content.edit

object DomainListUtils {
    private const val DOMAIN_LISTS_FILE = "domain_lists.json"
    private const val KEY_INITIAL_LOADED = "domain_initial_loaded"

    private val gson = Gson()

    fun initializeDefaultLists(context: Context) {
        val prefs = context.getPreferences()
        val isInitialized = prefs.getBoolean(KEY_INITIAL_LOADED, false)

        if (isInitialized) return

        val defaultLists = mutableListOf<DomainList>()

        val assetFiles = context.assets.list("")?.filter {
            it.startsWith("proxytest_") && it.endsWith(".sites")
        } ?: emptyList()

        for (assetFile in assetFiles) {
            val listName = assetFile
                .removePrefix("proxytest_")
                .removeSuffix(".sites")

            val domains = context.assets.open(assetFile)
                .bufferedReader()
                .useLines { it.toList() }
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            defaultLists.add(
                DomainList(
                    id = listName,
                    name = listName.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase() else it.toString()
                    },
                    domains = domains,
                    isActive = listName in setOf("youtube", "googlevideo"),
                    isBuiltIn = true
                )
            )
        }

        saveLists(context, defaultLists)

        prefs.edit { putBoolean(KEY_INITIAL_LOADED, true) }
    }

    fun getLists(context: Context): List<DomainList> {
        val listsFile = File(context.filesDir, DOMAIN_LISTS_FILE)

        if (!listsFile.exists()) {
            return emptyList()
        }

        return try {
            val json = listsFile.readText()
            val type = object : TypeToken<List<DomainList>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveLists(context: Context, lists: List<DomainList>) {
        val listsFile = File(context.filesDir, DOMAIN_LISTS_FILE)
        val json = gson.toJson(lists)
        listsFile.writeText(json)
    }

    fun getActiveDomains(context: Context): List<String> {
        return getLists(context)
            .filter { it.isActive }
            .flatMap { it.domains }
            .distinct()
    }

    fun addList(context: Context, name: String, domains: List<String>): Boolean {
        val lists = getLists(context).toMutableList()
        val id = name.lowercase().replace(" ", "_")

        if (lists.any { it.id == id }) return false

        lists.add(
            DomainList(
                id = id,
                name = name,
                domains = domains,
                isActive = true,
                isBuiltIn = false
            )
        )

        saveLists(context, lists)
        return true
    }

    fun updateList(context: Context, id: String, name: String, domains: List<String>): Boolean {
        val lists = getLists(context).toMutableList()
        val index = lists.indexOfFirst { it.id == id }

        if (index == -1) return false

        val oldList = lists[index]
        lists[index] = oldList.copy(name = name, domains = domains)

        saveLists(context, lists)
        return true
    }

    fun toggleListActive(context: Context, id: String): Boolean {
        val lists = getLists(context).toMutableList()
        val index = lists.indexOfFirst { it.id == id }

        if (index == -1) return false

        val list = lists[index]
        lists[index] = list.copy(isActive = !list.isActive)

        saveLists(context, lists)
        return true
    }

    fun deleteList(context: Context, id: String): Boolean {
        val lists = getLists(context).toMutableList()

        val iterator = lists.iterator()
        var removed = false

        while (iterator.hasNext()) {
            val list = iterator.next()
            if (list.id == id) {
                iterator.remove()
                removed = true
            }
        }

        if (removed) {
            saveLists(context, lists)
        }

        return removed
    }
}