package com.pypath.app.reminders

import android.content.Context

/** Batch 3 Part 3: what the view model needs from reminder scheduling (replaceable in tests). */
interface ReminderControl {
    fun schedule(minutes: Int)
    fun cancel()
}

class AlarmReminderControl(private val context: Context) : ReminderControl {
    override fun schedule(minutes: Int) {
        ReminderNotifier.ensureChannel(context)
        ReminderScheduler.schedule(context, minutes)
    }

    override fun cancel() = ReminderScheduler.cancel(context)
}
