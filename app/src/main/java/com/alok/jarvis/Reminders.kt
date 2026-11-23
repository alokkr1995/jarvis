package com.alok.jarvis

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Notification
import java.time.LocalDateTime
import java.time.ZoneId

object Reminders {
    const val CHANNEL = "jarvis_reminders"

    fun schedule(context: Context, text: String, isoDateTime: String): String {
        val dt = LocalDateTime.parse(isoDateTime.take(16))
        val millis = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (millis < System.currentTimeMillis()) return "That time has already passed: $isoDateTime"

        val intent = Intent(context, ReminderReceiver::class.java).putExtra("text", text)
        val pi = PendingIntent.getBroadcast(
            context, (millis / 1000).toInt() xor text.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(AlarmManager::class.java)
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        return "Reminder set: '$text' at $isoDateTime"
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Jarvis reminders", NotificationManager.IMPORTANCE_HIGH)
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: "Reminder"
        Reminders.ensureChannel(context)
        val n = Notification.Builder(context, Reminders.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Jarvis reminder")
            .setContentText(text)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)   // text hidden on the lock screen
            .setPublicVersion(Notification.Builder(context, Reminders.CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Jarvis reminder").build())
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(text.hashCode(), n)
        // Service चल रही हो तो Jarvis बोलकर भी याद दिलाएगा
        // When the phone is locked the text isn't read out loud (someone else may be nearby)
        JarvisService.instance?.speakReminder(if (AppLock.phoneLocked(context)) null else text)
    }
}
