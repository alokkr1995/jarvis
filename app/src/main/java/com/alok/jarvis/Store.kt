package com.alok.jarvis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Notes और to-do lists फ़ोन में एक JSON file में रहते हैं।
 * (Phase 3 में इन्हें Google Drive / Google Tasks से sync करेंगे।)
 */
class Store(context: Context) {
    private val file = File(context.filesDir, "jarvis_data.json")

    @Synchronized
    private fun load(): JSONObject =
        if (file.exists()) JSONObject(file.readText())
        else JSONObject().put("notes", JSONArray()).put("todos", JSONArray()).put("next_id", 1)

    @Synchronized
    private fun save(o: JSONObject) = file.writeText(o.toString(2))

    @Synchronized
    private fun nextId(o: JSONObject): Int {
        val id = o.optInt("next_id", 1)
        o.put("next_id", id + 1)
        return id
    }

    @Synchronized
    fun addNote(text: String, tag: String): String {
        val o = load()
        val id = nextId(o)
        o.getJSONArray("notes").put(
            JSONObject().put("id", id).put("text", text).put("tag", tag)
                .put("time", System.currentTimeMillis())
        )
        save(o)
        return "Note #$id saved (tag: $tag)"
    }

    @Synchronized
    fun searchNotes(query: String): String {
        val notes = load().getJSONArray("notes")
        val q = query.lowercase()
        val out = JSONArray()
        for (i in notes.length() - 1 downTo 0) {
            val n = notes.getJSONObject(i)
            if (q.isBlank() || n.getString("text").lowercase().contains(q) ||
                n.getString("tag").lowercase().contains(q)) out.put(n)
            if (out.length() >= 20) break
        }
        return if (out.length() == 0) "No notes found" else out.toString()
    }

    @Synchronized
    fun addTodo(list: String, text: String, due: String?): String {
        val o = load()
        val id = nextId(o)
        o.getJSONArray("todos").put(
            JSONObject().put("id", id).put("list", list.lowercase()).put("text", text)
                .put("due", due ?: "").put("done", false)
        )
        save(o)
        return "Task #$id added to '$list'"
    }

    @Synchronized
    fun listTodos(list: String?, includeDone: Boolean): String {
        val todos = load().getJSONArray("todos")
        val out = JSONArray()
        for (i in 0 until todos.length()) {
            val t = todos.getJSONObject(i)
            if (!includeDone && t.getBoolean("done")) continue
            if (!list.isNullOrBlank() && t.getString("list") != list.lowercase()) continue
            out.put(t)
        }
        return if (out.length() == 0) "No pending tasks" else out.toString()
    }

    @Synchronized
    fun pendingCount(): Int {
        val todos = load().getJSONArray("todos"); var n = 0
        for (i in 0 until todos.length()) if (!todos.getJSONObject(i).getBoolean("done")) n++
        return n
    }

    @Synchronized
    fun notesCount(): Int = load().getJSONArray("notes").length()

    @Synchronized
    fun completeTodo(id: Int): String {
        val o = load()
        val todos = o.getJSONArray("todos")
        for (i in 0 until todos.length()) {
            val t = todos.getJSONObject(i)
            if (t.getInt("id") == id) {
                t.put("done", true); save(o); return "Task #$id done: ${t.getString("text")}"
            }
        }
        return "Task #$id not found"
    }
}
