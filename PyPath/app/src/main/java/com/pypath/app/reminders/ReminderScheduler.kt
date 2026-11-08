package com.pypath.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.pypath.app.domain.Reminders
import java.time.ZoneId

/**
 * Batch 3 Part 3: schedules the daily reminder with an INEXACT AlarmManager alarm
 * (setAndAllowWhileIdle), which needs no exact-alarm permission and no extra library. Android may
 * deliver it some minutes late to save battery. Only one alarm exists at a time: after it fires,
 * the receiver schedules the next day. Reboots, clock changes, time-zone changes and app updates
 * are handled by [ReminderReceiver], and every app start schedules again as a safety net.
 */
object ReminderScheduler {
    const val ACTION_REMIND = "com.pypath.app.action.STUDY_REMINDER"
    private const val REQUEST_CODE = 4107

    private fun pendingIntent(context: Context, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND)
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    /** (Re)schedules the next reminder at [minutes] after midnight, local time. */
    fun schedule(context: Context, minutes: Int, nowMillis: Long = System.currentTimeMillis()) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, create = true) ?: return
        val at = Reminders.nextTriggerMillis(nowMillis, ZoneId.systemDefault(), minutes)
        runCatching { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
    }

    /** Cancels the pending reminder (turned off, or Reset progress). */
    fun cancel(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, create = false) ?: return
        alarm.cancel(pi)
        pi.cancel()
    }
}
