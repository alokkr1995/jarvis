package com.alok.jarvis

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updates: GitHub पर latest release देखो, नया हो तो download करके install screen खोलो।
 * (App को एक ही key से sign किया जाता है, इसलिए update पुराने app के ऊपर install होता है।)
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/alokkr1995/jarvis/releases/latest"
    private val main = Handler(Looper.getMainLooper())

    private fun currentBuild(a: Activity): Long =
        a.packageManager.getPackageInfo(a.packageName, 0).longVersionCode

    /** silent = true: सिर्फ़ update मिलने पर ही dialog दिखाओ */
    fun check(a: Activity, silent: Boolean, onAvailable: ((Long) -> Unit)? = null) {
        Thread {
            try {
                val c = URL(LATEST).openConnection() as HttpURLConnection
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.connectTimeout = 10000; c.readTimeout = 15000
                val json = JSONObject(c.inputStream.bufferedReader().readText())
                c.disconnect()
                val tag = json.getString("tag_name")             // e.g. build-21
                val latest = tag.substringAfter("build-").toLongOrNull() ?: 0L
                val assets = json.getJSONArray("assets")
                var url: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) url = asset.getString("browser_download_url")
                }
                val cur = currentBuild(a)
                main.post {
                    if (a.isFinishing) return@post
                    if (latest > cur && url != null) {
                        onAvailable?.invoke(latest)
                        AlertDialog.Builder(a)
                            .setTitle("UPDATE AVAILABLE")
                            .setMessage("Build $latest is ready (you have build $cur).\nDownload and install now?")
                            .setPositiveButton("UPDATE") { _, _ -> download(a, url, latest) }
                            .setNegativeButton("LATER", null)
                            .show()
                    } else if (!silent) {
                        Toast.makeText(a, "You're on the latest build ($cur)", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (!silent) main.post { Toast.makeText(a, "Update check failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun download(a: Activity, url: String, build: Long) {
        if (!a.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(a, "Allow Jarvis to install updates, then tap UPDATE again", Toast.LENGTH_LONG).show()
            a.startActivity(Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            return
        }
        Toast.makeText(a, "Downloading build $build…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val dir = File(a.cacheDir, "updates").apply { mkdirs() }
                val f = File(dir, "jarvis-update.apk")
                var link = url
                var conn: HttpURLConnection
                var hops = 0
                while (true) {   // GitHub download redirects to a CDN
                    conn = URL(link).openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 15000; conn.readTimeout = 60000
                    val code = conn.responseCode
                    if (code in 300..399 && hops < 5) {
                        link = conn.getHeaderField("Location"); conn.disconnect(); hops++
                    } else break
                }
                conn.inputStream.use { input -> f.outputStream().use { input.copyTo(it) } }
                conn.disconnect()
                val uri = FileProvider.getUriForFile(a, "com.alok.jarvis.files", f)
                main.post {
                    a.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            } catch (e: Exception) {
                main.post { Toast.makeText(a, "Download failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}
