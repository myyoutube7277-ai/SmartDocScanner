package com.smartdocscanner

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray

/** Keeps captured scan pages outside cache so an interrupted scan can be recovered. */
object DraftStore {
    private const val PREF = "smartdoc_draft"
    private const val KEY = "pages"

    private fun dir(c: Context) = File(c.filesDir, "drafts").apply { mkdirs() }

    fun save(c: Context, pages: List<File>) {
        val saved = pages.mapIndexedNotNull { i, source ->
            if (!source.exists() || source.length() == 0L) return@mapIndexedNotNull null
            val target = File(dir(c), "page_${i + 1}.jpg")
            if (source.absolutePath != target.absolutePath) {
                source.inputStream().use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
            }
            target.absolutePath
        }
        val a = JSONArray(); saved.forEach { a.put(it) }
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply()
    }

    fun load(c: Context): List<File> {
        val raw = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val a = JSONArray(raw)
        return (0 until a.length()).mapNotNull { i -> File(a.getString(i)).takeIf { it.exists() && it.length() > 0L } }
    }

    fun clear(c: Context) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        dir(c).listFiles()?.forEach { it.delete() }
    }
}
