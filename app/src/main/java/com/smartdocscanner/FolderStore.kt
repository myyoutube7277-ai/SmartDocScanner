package com.smartdocscanner

import android.content.Context
import org.json.JSONArray

object FolderStore {
    private const val PREF = "smart_doc_folders"
    private const val KEY = "folders"

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun all(c: Context): List<String> {
        val a = JSONArray(prefs(c).getString(KEY, "[]"))
        return buildList {
            for (i in 0 until a.length()) add(a.getString(i))
        }.filter { it.isNotBlank() }.distinct().sorted()
    }

    fun add(c: Context, name: String): Boolean {
        val clean = name.trim()
        if (clean.isBlank()) return false
        val current = all(c).toMutableList()
        if (current.any { it.equals(clean, true) }) return false
        current.add(clean)
        save(c, current)
        return true
    }

    fun delete(c: Context, name: String) {
        save(c, all(c).filterNot { it.equals(name, true) })
        DocumentStore.all(c).filter { it.folder.equals(name, true) }.forEach {
            DocumentStore.update(c, it.copy(folder = ""))
        }
    }

    private fun save(c: Context, values: List<String>) {
        val a = JSONArray()
        values.distinct().sorted().forEach(a::put)
        prefs(c).edit().putString(KEY, a.toString()).apply()
    }
}
