package com.smartdocscanner

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Persistent index for every document created by the app. */
data class DocumentRecord(
    val id: Long,
    var name: String,
    var path: String,
    var folder: String = "",
    var favorite: Boolean = false,
    var privateDoc: Boolean = false,
    val created: Long = System.currentTimeMillis()
)

object DocumentStore {
    private const val PREF = "smart_docs"
    private const val KEY = "records"
    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun documentsDir(c: Context): File = File(c.filesDir, "documents").apply { mkdirs() }

    /** Copies a generated file into the persistent app Documents area. */
    fun persist(c: Context, source: File, name: String = source.name): File {
        val dir = documentsDir(c)
        val clean = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "document" }
        val dest = File(dir, clean)
        if (source.absolutePath != dest.absolutePath) source.inputStream().use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
        return dest
    }

    fun register(c: Context, source: File, name: String = source.name, folder: String = ""): DocumentRecord? {
        if (!source.exists() || source.length() <= 0L) return null
        val saved = persist(c, source, name)
        val record = DocumentRecord(System.currentTimeMillis(), name.substringBeforeLast('.', name), saved.absolutePath, folder)
        add(c, record)
        return record
    }

    fun all(c: Context): MutableList<DocumentRecord> {
        val a = JSONArray(prefs(c).getString(KEY, "[]"))
        val out = mutableListOf<DocumentRecord>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out += DocumentRecord(o.getLong("id"), o.getString("name"), o.getString("path"), o.optString("folder"), o.optBoolean("favorite"), o.optBoolean("private"), o.optLong("created", System.currentTimeMillis()))
        }
        return out.filter { File(it.path).exists() }.sortedByDescending { it.created }.toMutableList()
    }

    fun add(c: Context, r: DocumentRecord) { val list = all(c); list.removeAll { it.id == r.id }; list += r; save(c, list) }

    fun update(c: Context, r: DocumentRecord) {
        val list = all(c)
        val i = list.indexOfFirst { it.id == r.id }
        if (i >= 0) { list[i] = r; save(c, list) }
    }

    fun rename(c: Context, r: DocumentRecord, newName: String): DocumentRecord? {
        val old = File(r.path)
        if (!old.exists()) return null
        val ext = old.extension
        val clean = newName.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "document" }
        val target = File(old.parentFile ?: documentsDir(c), if (ext.isBlank()) clean else "$clean.$ext")
        if (target.absolutePath != old.absolutePath && !old.renameTo(target)) return null
        r.name = clean
        r.path = target.absolutePath
        update(c, r)
        return r
    }

    fun delete(c: Context, r: DocumentRecord) { File(r.path).delete(); save(c, all(c).filterNot { it.id == r.id }) }
    fun moveToFolder(c: Context, r: DocumentRecord, folder: String) { r.folder = folder.trim(); update(c, r) }

    private fun save(c: Context, list: List<DocumentRecord>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().apply { put("id", it.id); put("name", it.name); put("path", it.path); put("folder", it.folder); put("favorite", it.favorite); put("private", it.privateDoc); put("created", it.created) }) }
        prefs(c).edit().putString(KEY, a.toString()).apply()
    }
}
