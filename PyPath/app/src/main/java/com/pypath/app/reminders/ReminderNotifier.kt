package com.pypath.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pypath.app.MainActivity
import com.pypath.app.R
import com.pypath.app.domain.Reminders

/** Batch 3 Part 3: the "Study reminders" channel and the reminder notification. */
object ReminderNotifier {
    const val CHANNEL_ID = "study_reminders"
    private const val NOTIFICATION_ID = 4107

    /** True when the app may show notifications (permission on Android 13+, and not blocked in settings). */
    fun allowed(context: Context): Boolean {
        val permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permission && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Study reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "One friendly reminder a day, only if today's goal isn't met yet."
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** Shows the reminder. Tapping it opens the app at the dashboard. Returns true when shown. */
    fun show(context: Context, message: Reminders.Message): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        ensureChannel(context)
        // A fresh task starts at the dashboard (the start screen once onboarding is done).
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pi = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(message.title)
            .setContentText(message.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
