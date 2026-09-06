package com.smartdocscanner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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

    fun all(c: Context): MutableList<DocumentRecord> {
        val a = JSONArray(prefs(c).getString(KEY, "[]"))
        val out = mutableListOf<DocumentRecord>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out += DocumentRecord(
                o.getLong("id"), o.getString("name"), o.getString("path"),
                o.optString("folder"), o.optBoolean("favorite"), o.optBoolean("private"),
                o.optLong("created", System.currentTimeMillis())
            )
        }
        return out.sortedByDescending { it.created }.toMutableList()
    }

    fun add(c: Context, r: DocumentRecord) {
        val list = all(c); list += r; save(c, list)
    }

    fun update(c: Context, r: DocumentRecord) {
        val list = all(c)
        val i = list.indexOfFirst { it.id == r.id }
        if (i >= 0) { list[i] = r; save(c, list) }
    }

    fun delete(c: Context, r: DocumentRecord) {
        File(r.path).delete()
        save(c, all(c).filterNot { it.id == r.id })
    }

    private fun save(c: Context, list: List<DocumentRecord>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().apply {
                put("id", it.id); put("name", it.name); put("path", it.path)
                put("folder", it.folder); put("favorite", it.favorite)
                put("private", it.privateDoc); put("created", it.created)
            })
        }
        prefs(c).edit().putString(KEY, a.toString()).apply()
    }
}
