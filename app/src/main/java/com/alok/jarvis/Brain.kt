package com.alok.jarvis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Jarvis का दिमाग़: Claude API + tools.
 * GPT एक tool की तरह जुड़ा है ("GPT से पूछो...").
 */
class Brain(private val context: Context) {
    private val settings = Settings(context)
    private val store = Store(context)
    private val history = JSONArray()   // एक बातचीत (session) की history

    fun resetSession() {
        while (history.length() > 0) history.remove(0)
    }

    private fun systemPrompt(): String {
        val now = SimpleDateFormat("EEEE, dd MMM yyyy, HH:mm", Locale.ENGLISH).format(Date())
        val rules = settings.customRules.ifBlank { "(अभी कोई नहीं)" }
        return """
${settings.personality}

अभी का समय: $now (India time)।

Tools का इस्तेमाल करो जब ज़रूरत हो: notes, to-do lists, reminders, GPT से पूछना, custom नियम याद रखना।
Reminder के लिए datetime हमेशा ISO format में दो, जैसे 2026-11-24T09:00।
अगर कोई काम तुम्हारे पास अभी नहीं है (जैसे WhatsApp, calendar, call), तो साफ़ बोलो कि ये अगले phase में आएगा।

Alok के custom नियम:
$rules
""".trim()
    }

    private val tools: JSONArray by lazy {
        fun tool(name: String, desc: String, props: JSONObject, required: List<String>) =
            JSONObject().put("name", name).put("description", desc).put(
                "input_schema",
                JSONObject().put("type", "object").put("properties", props)
                    .put("required", JSONArray(required))
            )
        fun str(d: String) = JSONObject().put("type", "string").put("description", d)

        JSONArray()
            .put(tool("ask_gpt", "OpenAI GPT से कोई सवाल पूछो, जब Alok बोले 'GPT से पूछो' या second opinion चाहिए।",
                JSONObject().put("question", str("GPT से पूछने वाला सवाल")), listOf("question")))
            .put(tool("add_note", "एक note save करो।",
                JSONObject().put("text", str("note का text")).put("tag", str("project/topic, जैसे BIT, Kathalok, TVF, personal")),
                listOf("text", "tag")))
            .put(tool("search_notes", "Notes ढूँढो (query खाली = हाल के सारे notes)।",
                JSONObject().put("query", str("ढूँढने का शब्द या tag")), listOf("query")))
            .put(tool("add_todo", "To-do list में task जोड़ो।",
                JSONObject().put("list", str("list का नाम: tvf, kathalok, personal, shopping या कोई और"))
                    .put("text", str("task")).put("due", str("deadline, optional")),
                listOf("list", "text")))
            .put(tool("list_todos", "Pending tasks दिखाओ।",
                JSONObject().put("list", str("list का नाम, या खाली = सारी lists"))
                    .put("include_done", JSONObject().put("type", "boolean")),
                listOf()))
            .put(tool("complete_todo", "Task को done mark करो।",
                JSONObject().put("id", JSONObject().put("type", "integer")), listOf("id")))
            .put(tool("set_reminder", "किसी समय पर reminder लगाओ (notification + Jarvis की आवाज़)।",
                JSONObject().put("text", str("किस बात का reminder"))
                    .put("datetime", str("ISO local datetime, जैसे 2026-11-24T09:00")),
                listOf("text", "datetime")))
            .put(tool("add_custom_rule", "Alok का कोई नया नियम/preference हमेशा के लिए याद रखो (जैसे 'shoot mode' का मतलब)।",
                JSONObject().put("rule", str("नियम, Alok के शब्दों में")), listOf("rule")))
    }

    private fun runTool(name: String, input: JSONObject): String = try {
        when (name) {
            "ask_gpt" -> askGpt(input.getString("question"))
            "add_note" -> store.addNote(input.getString("text"), input.optString("tag", "general"))
            "search_notes" -> store.searchNotes(input.optString("query", ""))
            "add_todo" -> store.addTodo(input.getString("list"), input.getString("text"),
                input.optString("due").ifBlank { null })
            "list_todos" -> store.listTodos(input.optString("list").ifBlank { null },
                input.optBoolean("include_done", false))
            "complete_todo" -> store.completeTodo(input.getInt("id"))
            "set_reminder" -> Reminders.schedule(context, input.getString("text"), input.getString("datetime"))
            "add_custom_rule" -> {
                settings.customRules = (settings.customRules + "\n- " + input.getString("rule")).trim()
                "नियम save हो गया"
            }
            else -> "Unknown tool $name"
        }
    } catch (e: Exception) {
        "Error: ${e.message}"
    }

    /** User की बात लो, Claude से जवाब लाओ (tools चलाते हुए)। Background thread पर call करें। */
    fun ask(userText: String): String {
        if (settings.anthropicKey.isBlank()) return "Sir, पहले settings में Anthropic API key डालिए।"
        history.put(JSONObject().put("role", "user").put("content", userText))

        repeat(8) {   // ज़्यादा से ज़्यादा 8 tool rounds
            val body = JSONObject()
                .put("model", settings.claudeModel)
                .put("max_tokens", 1024)
                .put("system", systemPrompt())
                .put("tools", tools)
                .put("messages", history)
            val resp = Http.postJson(
                "https://api.anthropic.com/v1/messages",
                mapOf("x-api-key" to settings.anthropicKey, "anthropic-version" to "2023-06-01"),
                body
            )
            val content = resp.getJSONArray("content")
            history.put(JSONObject().put("role", "assistant").put("content", content))

            if (resp.optString("stop_reason") != "tool_use") {
                val sb = StringBuilder()
                for (i in 0 until content.length()) {
                    val b = content.getJSONObject(i)
                    if (b.optString("type") == "text") sb.append(b.getString("text")).append(' ')
                }
                return sb.toString().trim().ifBlank { "हो गया।" }
            }

            val results = JSONArray()
            for (i in 0 until content.length()) {
                val b = content.getJSONObject(i)
                if (b.optString("type") == "tool_use") {
                    val out = runTool(b.getString("name"), b.optJSONObject("input") ?: JSONObject())
                    results.put(JSONObject().put("type", "tool_result")
                        .put("tool_use_id", b.getString("id")).put("content", out))
                }
            }
            history.put(JSONObject().put("role", "user").put("content", results))
        }
        return "Sir, ये काम थोड़ा उलझ गया, एक बार फिर से बोलिए।"
    }

    private fun askGpt(question: String): String {
        if (settings.openaiKey.isBlank()) return "OpenAI API key settings में नहीं है"
        val body = JSONObject()
            .put("model", settings.gptModel)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", question)))
        val resp = Http.postJson(
            "https://api.openai.com/v1/chat/completions",
            mapOf("Authorization" to "Bearer ${settings.openaiKey}"),
            body
        )
        return resp.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
    }
}
