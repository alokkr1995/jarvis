package com.alok.jarvis

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings as AndroidSettings
import android.telephony.SmsManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Phone control: apps, calls, SMS, WhatsApp, alarms, torch. */
class Phone(private val context: Context) {

    private fun has(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun launch(i: Intent): String? = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i); null
    } catch (e: Exception) { e.message ?: "could not open" }

    /** Background app-launch needs "Display over other apps"; warn the brain if it is missing. */
    private fun bgNote() = if (!AndroidSettings.canDrawOverlays(context))
        " (If nothing opened, Alok must enable Settings → 'Allow app control' in Jarvis.)" else ""

    // ---------- Contacts ----------
    data class Contact(val name: String, val number: String)

    fun findContacts(query: String): List<Contact> {
        val q = query.trim()
        if (q.any { it.isDigit() } && q.count { it.isDigit() } >= 6) return listOf(Contact(q, q))
        if (!has(Manifest.permission.READ_CONTACTS)) return emptyList()
        val all = mutableListOf<Contact>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0) ?: continue
                val num = c.getString(1) ?: continue
                all += Contact(n, num)
            }
        }
        val ql = q.lowercase()
        val exact = all.filter { it.name.lowercase() == ql }
        val pick = exact.ifEmpty {
            all.filter { it.name.lowercase().contains(ql) }.ifEmpty {
                val words = ql.split(" ").filter { it.length > 1 }
                all.filter { c -> words.isNotEmpty() && words.all { c.name.lowercase().contains(it) } }
            }
        }
        return pick.distinctBy { digits(it.number).takeLast(10) }
    }

    private fun digits(n: String) = n.filter { it.isDigit() }

    /** Indian numbers → 91XXXXXXXXXX for WhatsApp */
    private fun intl(n: String): String {
        var d = digits(n)
        if (d.startsWith("00")) d = d.drop(2)
        if (d.length == 11 && d.startsWith("0")) d = d.drop(1)
        if (d.length == 10) d = "91$d"
        return d
    }

    /** Returns either a single contact or an explanation for the brain. */
    private fun resolve(who: String): Pair<Contact?, String?> {
        if (!has(Manifest.permission.READ_CONTACTS) && who.count { it.isDigit() } < 6)
            return null to "Contacts permission is off. Ask Alok to open Jarvis once and allow Contacts."
        val list = findContacts(who)
        return when {
            list.isEmpty() -> null to "No contact found for '$who'. Ask Alok for the exact saved name or the number."
            list.size == 1 -> list[0] to null
            else -> null to "Several matches: " + list.take(6).joinToString("; ") { "${it.name} (${it.number})" } +
                ". Ask Alok which one."
        }
    }

    // ---------- Tools ----------
    fun openApp(name: String): String {
        val pm = context.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val n = name.lowercase().trim()
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == n }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(n) }
            ?: apps.firstOrNull { it.activityInfo.packageName.lowercase().contains(n.replace(" ", "")) }
            ?: return "No app called '$name' is installed."
        val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return "Can't open ${hit.loadLabel(pm)}"
        return launch(i)?.let { "Failed: $it" } ?: "Opened ${hit.loadLabel(pm)}${bgNote()}"
    }

    fun call(who: String): String {
        val (c, err) = resolve(who); if (c == null) return err!!
        if (!has(Manifest.permission.CALL_PHONE)) {
            launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(c.number))))
            return "Call permission is off, so the dialer was opened with ${c.name}'s number."
        }
        return launch(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(c.number))))
            ?.let { "Failed: $it" } ?: "Calling ${c.name}${bgNote()}"
    }

    fun sms(who: String, message: String): String {
        val (c, err) = resolve(who); if (c == null) return err!!
        if (!has(Manifest.permission.SEND_SMS)) return "SMS permission is off. Ask Alok to allow SMS for Jarvis."
        return try {
            @Suppress("DEPRECATION")
            val sm = context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            sm.sendMultipartTextMessage(c.number, null, sm.divideMessage(message), null, null)
            "SMS sent to ${c.name}"
        } catch (e: Exception) { "SMS failed: ${e.message}" }
    }

    fun whatsapp(who: String, message: String): String {
        val (c, err) = resolve(who); if (c == null) return err!!
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=${intl(c.number)}&text=${Uri.encode(message)}")
        val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull {
            try { context.packageManager.getPackageInfo(it, 0); true } catch (_: Exception) { false }
        } ?: return "WhatsApp is not installed."
        val auto = AutoSend.isEnabled(context)
        if (auto) AutoSend.arm()
        val e = launch(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg))
        if (e != null) return "Failed to open WhatsApp: $e"
        return if (auto) "WhatsApp message to ${c.name} is being sent automatically."
        else "Opened WhatsApp chat with ${c.name} with the message typed. Auto-send is off, so Alok needs to tap send " +
            "(he can turn on auto-send in Jarvis Settings)." + bgNote()
    }

    fun alarm(hour: Int, minute: Int, label: String): String =
        launch(Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour).putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Jarvis" })
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            ?.let { "Failed: $it" } ?: "Alarm set for %02d:%02d".format(hour, minute)

    fun torch(on: Boolean): String = try {
        val cm = context.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.first()
        cm.setTorchMode(id, on); if (on) "Torch on" else "Torch off"
    } catch (e: Exception) { "Torch failed: ${e.message}" }

    fun youtube(query: String): String {
        val app = launch(Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", query))
        if (app == null) return "Opened YouTube search for $query"
        val web = launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))))
        return if (web == null) "Opened YouTube search for $query" else "Failed: $web"
    }

    fun maps(place: String): String =
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(place))))
            ?.let { "Failed: $it" } ?: "Started navigation to $place"
}

/**
 * Optional accessibility helper: taps WhatsApp's Send button right after Jarvis opens a chat.
 * Only acts for ~15 seconds after Jarvis asks it to — it never reads or touches anything else.
 */
class AutoSend : AccessibilityService() {
    companion object {
        @Volatile private var armedUntil = 0L
        fun arm() { armedUntil = System.currentTimeMillis() + 15000 }
        fun isEnabled(c: Context): Boolean {
            val list = AndroidSettings.Secure.getString(c.contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(c, AutoSend::class.java).flattenToString()
            return list.split(':').any { it.equals(me, true) }
        }
        private val SEND_IDS = listOf("com.whatsapp:id/send", "com.whatsapp.w4b:id/send")
    }

    private val main = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (System.currentTimeMillis() > armedUntil) return
        val root = rootInActiveWindow ?: return
        var node: AccessibilityNodeInfo? = null
        for (id in SEND_IDS) { node = root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull(); if (node != null) break }
        if (node == null) node = root.findAccessibilityNodeInfosByText("Send")?.firstOrNull {
            it.isClickable && (it.contentDescription?.toString()?.equals("Send", true) == true)
        }
        val target = node
        if (target != null && target.isEnabled) {
            armedUntil = 0L
            main.postDelayed({ target.performAction(AccessibilityNodeInfo.ACTION_CLICK) }, 300)
            JarvisService.log("WhatsApp: sent")
        }
    }

    override fun onInterrupt() {}
}
