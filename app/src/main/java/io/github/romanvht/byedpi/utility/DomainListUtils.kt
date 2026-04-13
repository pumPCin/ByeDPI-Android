package io.github.romanvht.byedpi.utility

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.romanvht.byedpi.data.DomainList
import java.io.File

object DomainListUtils {
    private const val DOMAIN_LISTS_FILE = "domain_lists.json"

    private val gson = Gson()

    fun getDefaultActiveIds(lang: String): Set<String> = when (lang) {
        "tr" -> setOf("türkiye", "discord")
        else -> setOf("youtube", "googlevideo")
    }

    fun syncLists(context: Context) {
        val currentLists = getAllLists(context).toMutableList()

        val builtInMap = currentLists
            .filter { it.isBuiltIn }
            .associateBy { it.id }

        val assetFiles = context.assets.list("")?.filter {
            it.startsWith("proxytest_") && it.endsWith(".sites")
        } ?: emptyList()

        val newBuiltInIds = mutableSetOf<String>()

        for (assetFile in assetFiles) {
            val id = assetFile
                .removePrefix("proxytest_")
                .removeSuffix(".sites")

            newBuiltInIds.add(id)

            val domains = context.assets.open(assetFile)
                .bufferedReader()
                .useLines { it.toList() }
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val existing = builtInMap[id]

            when {
                existing == null -> {
                    currentLists.add(
                        DomainList(
                            id = id,
                            name = id.replaceFirstChar { it.uppercase() },
                            domains = domains,
                            isActive = id in getDefaultActiveIds(SettingsUtils.getCurrentLanguage(context)),
                            isBuiltIn = true
                        )
                    )
                }

                existing.isDeleted -> {
                    continue
                }

                existing.isModified -> {
                    continue
                }

                else -> {
                    val index = currentLists.indexOfFirst { it.id == id }

                    currentLists[index] = existing.copy(
                        domains = domains
                    )
                }
            }
        }

        currentLists.removeAll {
            it.isBuiltIn && it.id !in newBuiltInIds
        }

        saveLists(context, currentLists)
    }

    fun getAllLists(context: Context): MutableList<DomainList> {
        val listsFile = File(context.filesDir, DOMAIN_LISTS_FILE)

        if (!listsFile.exists()) {
            return mutableListOf()
        }

        return try {
            val json = listsFile.readText()
            val type = object : TypeToken<List<DomainList>>() {}.type
            val lists: List<DomainList> = gson.fromJson(json, type) ?: emptyList()
            lists.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun getLists(context: Context): List<DomainList> {
        return getAllLists(context).filter { !it.isDeleted }
    }

    fun getActiveDomains(context: Context): List<String> {
        return getLists(context).filter { it.isActive }.flatMap { it.domains }.distinct()
    }

    fun saveLists(context: Context, lists: List<DomainList>) {
        val listsFile = File(context.filesDir, DOMAIN_LISTS_FILE)
        val json = gson.toJson(lists)
        listsFile.writeText(json)
    }

    fun addList(context: Context, name: String, domains: List<String>): Boolean {
        val lists = getAllLists(context).toMutableList()
        val id = name.lowercase().replace(" ", "_")

        val existing = lists.find { it.id == id }

        if (existing != null) {
            if (existing.isBuiltIn && existing.isDeleted) {
                val index = lists.indexOf(existing)

                lists[index] = existing.copy(
                    name = name,
                    domains = domains,
                    isDeleted = false,
                    isModified = true,
                    isActive = true
                )

                saveLists(context, lists)
                return true
            }

            return false
        }

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
        val lists = getAllLists(context).toMutableList()
        val index = lists.indexOfFirst { it.id == id }

        if (index == -1) return false

        val oldList = lists[index]

        lists[index] = oldList.copy(
            name = name,
            domains = domains,
            isModified = oldList.isBuiltIn
        )

        saveLists(context, lists)
        return true
    }

    fun toggleListActive(context: Context, id: String): Boolean {
        val lists = getAllLists(context).toMutableList()
        val index = lists.indexOfFirst { it.id == id }

        if (index == -1) return false

        val list = lists[index]
        lists[index] = list.copy(isActive = !list.isActive)

        saveLists(context, lists)
        return true
    }

    fun deleteList(context: Context, id: String): Boolean {
        val lists = getAllLists(context).toMutableList()
        val index = lists.indexOfFirst { it.id == id }

        if (index == -1) return false

        val list = lists[index]

        if (list.isBuiltIn) {
            lists[index] = list.copy(
                isDeleted = true,
                isActive = false
            )
        } else {
            lists.removeAt(index)
        }

        saveLists(context, lists)
        return true
    }

    fun resetLists(context: Context) {
        val file = File(context.filesDir, DOMAIN_LISTS_FILE)

        if (file.exists()) {
            file.delete()
        }

        syncLists(context)
    }
}