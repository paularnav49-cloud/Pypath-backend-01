package com.pypath.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pypath.app.PyPathApp
import com.pypath.app.domain.Reminders
import com.pypath.app.domain.Streaks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Batch 3 Part 3: receives the daily reminder alarm, and re-schedules it after a reboot, a clock or
 * time-zone change, or an app update (alarms do not survive those). Not exported: the system can
 * still deliver its own broadcasts to it, other apps cannot.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? PyPathApp ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                withTimeoutOrNull(8_000) { handle(app, intent.action) }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(app: PyPathApp, action: String?) {
        val c = app.container
        val prefs = c.settingsRepository.reminder.first()
        if (!prefs.enabled) {
            ReminderScheduler.cancel(app)
            return
        }
        if (action == ReminderScheduler.ACTION_REMIND) {
            val today = Streaks.today()
            val activity = c.activityRepository.activity.first()
            val streak = Streaks.info(activity, today)
            val send = Reminders.shouldSend(
                enabled = true,
                notificationsAllowed = ReminderNotifier.allowed(app),
                lastSentDay = prefs.lastSentDay,
                today = today,
                goalMetToday = streak.activeToday,
            )
            if (send && ReminderNotifier.show(app, Reminders.message(today, streak.current))) {
                c.settingsRepository.markReminderSent(today)
            }
        }
        // Always line up the next one (also after boot, clock/time-zone change and app update).
        ReminderScheduler.schedule(app, prefs.minutes)
    }
}
