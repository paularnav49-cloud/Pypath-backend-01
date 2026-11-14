package com.pypath.app.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Batch 3 Part 3: daily study reminder rules. Pure functions (no Android), unit-tested.
 * Scheduling and the notification itself live in the reminders package.
 */
object Reminders {
    /** 19:00, in minutes after midnight. */
    const val DEFAULT_MINUTES = 19 * 60

    fun clampMinutes(minutes: Int): Int = minutes.coerceIn(0, 24 * 60 - 1)

    /** "19:00" style label for a time of day. */
    fun label(minutes: Int): String {
        val m = clampMinutes(minutes)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /**
     * Whether the reminder should be shown now: it is switched on, notifications are allowed,
     * none was shown today, and today's daily goal is not met yet.
     */
    fun shouldSend(enabled: Boolean, notificationsAllowed: Boolean, lastSentDay: Long?, today: Long, goalMetToday: Boolean): Boolean =
        enabled && notificationsAllowed && lastSentDay != today && !goalMetToday

    /**
     * The next time the reminder should fire: today at [minutes] if that is still ahead of [now],
     * otherwise tomorrow. Computed in local time, so daylight-saving changes keep the clock time.
     */
    fun nextTrigger(now: ZonedDateTime, minutes: Int): ZonedDateTime {
        val m = clampMinutes(minutes)
        val time = LocalTime.of(m / 60, m % 60)
        val today = now.toLocalDate().atTime(time).atZone(now.zone)
        return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
    }

    fun nextTriggerMillis(nowMillis: Long, zone: ZoneId, minutes: Int): Long =
        nextTrigger(Instant.ofEpochMilli(nowMillis).atZone(zone), minutes).toInstant().toEpochMilli()

    /** Title and text of the notification. */
    data class Message(val title: String, val text: String)

    /**
     * Seven rotating offline messages. Each has a plain wording and a wording that mentions the
     * streak ([n] days), used when the learner has a streak.
     */
    private val messages: List<Pair<Message, (Int) -> Message>> = listOf(
        Message("Time for a little Python", "Five minutes today keeps your skills fresh.") to
            { n -> Message("Keep your $n-day streak going", "Five minutes of Python today keeps it alive.") },
        Message("Your next lesson is waiting", "Pick up where you left off in PyPath.") to
            { n -> Message("$n days in a row", "Pick up where you left off and make it ${n + 1}.") },
        Message("Quick practice?", "One short lesson or a few questions is enough for today.") to
            { n -> Message("Don't lose your $n-day streak", "One short lesson or a few questions is enough.") },
        Message("Keep learning Python", "Small steps every day add up fast.") to
            { n -> Message("Your streak: $n days", "Small steps every day add up fast. Keep it going.") },
        Message("Ready for a challenge?", "Try a quiz or review the questions you found tricky.") to
            { n -> Message("Ready for day ${n + 1}?", "Try a quiz or review the questions you found tricky.") },
        Message("PyPath misses you", "Come back for a quick lesson whenever you're ready.") to
            { n -> Message("Your $n-day streak misses you", "Come back for a quick lesson before the day ends.") },
        Message("Learn something new today", "A new Python idea is only a few minutes away.") to
            { n -> Message("$n-day streak, nice", "A new Python idea is only a few minutes away.") },
    )

    /** A friendly offline message that rotates by day and mentions the streak when there is one. */
    fun message(today: Long, streak: Int): Message {
        val (plain, streaky) = messages[Math.floorMod(today, messages.size.toLong()).toInt()]
        return if (streak > 0) streaky(streak) else plain
    }

    /** Number of rotating messages (the spec asks for 6 to 8). */
    val messageCount: Int get() = messages.size
}
