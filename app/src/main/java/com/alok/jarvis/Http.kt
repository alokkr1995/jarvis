package com.alok.jarvis

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Http {
    /** JSON POST — background thread से ही call करें */
    fun postJson(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
        val bytes = postRaw(url, headers, body)
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    fun postRaw(url: String, headers: Map<String, String>, body: JSONObject): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000
        c.readTimeout = 90000
        c.doOutput = true
        c.setRequestProperty("content-type", "application/json")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val data = stream?.use { it.readBytes() } ?: ByteArray(0)
        c.disconnect()
        if (code !in 200..299) {
            throw RuntimeException("HTTP $code: ${String(data, Charsets.UTF_8).take(300)}")
        }
        return data
    }
}
