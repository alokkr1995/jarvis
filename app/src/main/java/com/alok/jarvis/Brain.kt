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
    companion object { const val FALLBACK = "gemini-flash-lite-latest" }
    private val settings = Settings(context)
    private val store = Store(context)
    private val phone = Phone(context)
    private val history = JSONArray()   // एक बातचीत (session) की history

    private val gHistory = JSONArray()  // Gemini session history

    fun resetSession() {
        while (history.length() > 0) history.remove(0)
        while (gHistory.length() > 0) gHistory.remove(0)
    }

    private fun systemPrompt(): String {
        val now = SimpleDateFormat("EEEE, dd MMM yyyy, HH:mm", Locale.ENGLISH).format(Date())
        val rules = settings.customRules.ifBlank { "(none yet)" }
        return """
${settings.personality}

Current time: $now (India).

You can control Alok's phone with tools: open apps, call people, send SMS, send WhatsApp messages,
set alarms, torch, YouTube search, Google Maps navigation. Also notes, to-do lists, reminders, asking GPT,
remembering custom rules. When Alok asks for one of these, just do it with the tool (no need to ask permission),
then confirm in one short sentence. Contact names: pass them in English/Roman letters as they'd be saved in the phone
(e.g. "राहुल" → "Rahul", "मम्मी" → "Mummy"). If a tool says several contacts match, ask which one.
Write messages exactly in the words/language Alok wants to send.
For reminders always pass datetime in ISO format, e.g. 2026-11-24T09:00.
Calendar, Gmail and meeting recording are coming in a later update — say so if asked.

Alok's custom rules:
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
            .put(tool("ask_gpt", "Ask OpenAI GPT a question, when Alok says 'ask GPT' or wants a second opinion.",
                JSONObject().put("question", str("The question for GPT")), listOf("question")))
            .put(tool("add_note", "Save a note.",
                JSONObject().put("text", str("Note text")).put("tag", str("Project/topic, e.g. BIT, Kathalok, TVF, personal")),
                listOf("text", "tag")))
            .put(tool("search_notes", "Search notes (empty query = recent notes).",
                JSONObject().put("query", str("Search word or tag")), listOf("query")))
            .put(tool("add_todo", "Add a task to a to-do list.",
                JSONObject().put("list", str("List name: tvf, kathalok, personal, shopping or other"))
                    .put("text", str("task")).put("due", str("deadline, optional")),
                listOf("list", "text")))
            .put(tool("list_todos", "List pending tasks.",
                JSONObject().put("list", str("List name, or empty for all lists"))
                    .put("include_done", JSONObject().put("type", "boolean")),
                listOf()))
            .put(tool("complete_todo", "Mark a task as done.",
                JSONObject().put("id", JSONObject().put("type", "integer")), listOf("id")))
            .put(tool("set_reminder", "Set a reminder at a time (notification + spoken by Jarvis).",
                JSONObject().put("text", str("What to remind about"))
                    .put("datetime", str("ISO local datetime, e.g. 2026-11-24T09:00")),
                listOf("text", "datetime")))
            .put(tool("open_app", "Open an installed app on the phone (WhatsApp, Instagram, Camera, Spotify...).",
                JSONObject().put("name", str("App name as shown on the phone")), listOf("name")))
            .put(tool("call", "Phone call to a contact or number.",
                JSONObject().put("contact", str("Contact name in Roman letters, or a phone number")), listOf("contact")))
            .put(tool("send_sms", "Send an SMS text message.",
                JSONObject().put("contact", str("Contact name in Roman letters, or a phone number"))
                    .put("message", str("Message text")), listOf("contact", "message")))
            .put(tool("send_whatsapp", "Send a WhatsApp message to a contact.",
                JSONObject().put("contact", str("Contact name in Roman letters, or a phone number"))
                    .put("message", str("Message text")), listOf("contact", "message")))
            .put(tool("set_alarm", "Set an alarm in the phone's clock app.",
                JSONObject().put("hour", JSONObject().put("type", "integer").put("description", "0-23"))
                    .put("minute", JSONObject().put("type", "integer")).put("label", str("Alarm label, optional")),
                listOf("hour", "minute")))
            .put(tool("torch", "Turn the flashlight on or off.",
                JSONObject().put("on", JSONObject().put("type", "boolean")), listOf("on")))
            .put(tool("youtube", "Search / play something on YouTube.",
                JSONObject().put("query", str("What to search")), listOf("query")))
            .put(tool("navigate", "Start Google Maps navigation to a place.",
                JSONObject().put("place", str("Destination")), listOf("place")))
            .put(tool("add_custom_rule", "Permanently remember a new rule/preference from Alok (e.g. what 'shoot mode' means).",
                JSONObject().put("rule", str("The rule, in Alok's words")), listOf("rule")))
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
            "open_app" -> phone.openApp(input.getString("name"))
            "call" -> phone.call(input.getString("contact"))
            "send_sms" -> phone.sms(input.getString("contact"), input.getString("message"))
            "send_whatsapp" -> phone.whatsapp(input.getString("contact"), input.getString("message"))
            "set_alarm" -> phone.alarm(input.getInt("hour"), input.optInt("minute", 0), input.optString("label", ""))
            "torch" -> phone.torch(input.optBoolean("on", true))
            "youtube" -> phone.youtube(input.getString("query"))
            "navigate" -> phone.maps(input.getString("place"))
            "add_custom_rule" -> {
                settings.customRules = (settings.customRules + "\n- " + input.getString("rule")).trim()
                "Rule saved"
            }
            else -> "Unknown tool $name"
        }
    } catch (e: Exception) {
        "Error: ${e.message}"
    }

    /** User की बात लो, Claude से जवाब लाओ (tools चलाते हुए)। Background thread पर call करें। */
    fun ask(userText: String): String {
        if (settings.anthropicKey.isBlank()) {
            if (settings.geminiKey.isNotBlank()) return askGemini(userText)
            return "सर, पहले Setup में Gemini key डाल दीजिए।"
        }
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
                return sb.toString().trim().ifBlank { "हो गया सर।" }
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
        return "सर, बात थोड़ी उलझ गई, एक बार फिर बोलिए।"
    }

    /** Free brain: Google Gemini with function calling */
    private fun askGemini(userText: String): String {
        val decls = JSONArray()
        for (i in 0 until tools.length()) {
            val t = tools.getJSONObject(i)
            decls.put(JSONObject().put("name", t.getString("name"))
                .put("description", t.getString("description"))
                .put("parameters", t.getJSONObject("input_schema")))
        }
        gHistory.put(JSONObject().put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", userText))))
        var model = settings.geminiModel.let { if (it == FALLBACK) "gemini-flash-latest" else it }

        repeat(8) {
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt()))))
                .put("contents", gHistory)
                .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", decls)))
            val resp = try {
                Http.postJson("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
                    mapOf("x-goog-api-key" to settings.geminiKey), body)
            } catch (e: Exception) {
                // Busy / quota / unknown model → fall back to the light model once
                val m = e.message ?: ""
                if (model != FALLBACK && (m.contains("HTTP 503") || m.contains("HTTP 429") || m.contains("HTTP 404"))) {
                    model = FALLBACK
                    Http.postJson("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
                        mapOf("x-goog-api-key" to settings.geminiKey), body)
                } else throw e
            }
            val cands = resp.optJSONArray("candidates")
            if (cands == null || cands.length() == 0) return "सर, जवाब नहीं आया, एक बार फिर बोलिए।"
            val content = cands.getJSONObject(0).optJSONObject("content")
                ?: return "सर, जवाब नहीं आया, एक बार फिर बोलिए।"
            if (!content.has("role")) content.put("role", "model")
            gHistory.put(content)
            val parts = content.optJSONArray("parts") ?: JSONArray()

            val responses = JSONArray()
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val call = part.optJSONObject("functionCall")
                if (call != null) {
                    val out = runTool(call.getString("name"), call.optJSONObject("args") ?: JSONObject())
                    val fr = JSONObject().put("name", call.getString("name"))
                        .put("response", JSONObject().put("result", out))
                    if (call.has("id")) fr.put("id", call.getString("id"))
                    responses.put(JSONObject().put("functionResponse", fr))
                } else if (part.has("text") && !part.optBoolean("thought", false)) {
                    sb.append(part.getString("text")).append(' ')
                }
            }
            if (responses.length() == 0) return sb.toString().trim().ifBlank { "हो गया सर।" }
            gHistory.put(JSONObject().put("role", "user").put("parts", responses))
        }
        return "सर, बात थोड़ी उलझ गई, एक बार फिर बोलिए।"
    }

    private fun askGpt(question: String): String {
        if (settings.openaiKey.isBlank()) return "OpenAI API key is not set"
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
