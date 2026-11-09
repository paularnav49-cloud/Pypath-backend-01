package com.pypath.app.domain

import com.pypath.app.data.activity.ActivityData
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Daily goal setting. [xp] = 0 means "Off": any lesson page or question makes the day active. */
enum class DailyGoal(val label: String, val xp: Int) {
    OFF("Off", 0),
    CASUAL("Casual", 20),
    REGULAR("Regular", 40),
    SERIOUS("Serious", 80);

    val description: String get() = if (xp == 0) "Any lesson page or question counts" else "$xp XP a day"

    companion object {
        val DEFAULT = REGULAR
        fun fromName(name: String?): DailyGoal = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Streak facts for one day, all derived from [ActivityData]. */
data class StreakInfo(
    /** Consecutive active (or frozen) days ending today or yesterday; 0 otherwise. */
    val current: Int,
    val longest: Int,
    /** Days that met the goal (frozen days not included), within the stored 400 days. */
    val daysPractised: Int,
    val activeToday: Boolean,
    val freezes: Int,
    /**
     * Yesterday was missed but a freeze is ready: practising today keeps [protectedStreak].
     * [current] is 0 in this case until the learner practises.
     */
    val freezePending: Boolean = false,
    val protectedStreak: Int = 0,
    /** Day a freeze covered recently (yesterday or the day before), for a message. */
    val recentFreezeDay: Long? = null,
)

/**
 * Pure streak rules. A "day" is the device's local calendar date. A streak is the run of
 * consecutive active days ending today or yesterday, so it never breaks before the end of today.
 */
object Streaks {
    val MILESTONES = listOf(3, 7, 14, 30, 100)
    const val FREEZE_EVERY = 7
    const val MAX_FREEZES = 1

    fun dayOf(instant: Instant, zone: ZoneId): Long = instant.atZone(zone).toLocalDate().toEpochDay()

    /** Today's local day. Read at the moment of every event, so midnight rollovers need no restart. */
    fun today(): Long = LocalDate.now().toEpochDay()

    fun date(day: Long): LocalDate = LocalDate.ofEpochDay(day)

    /** Length of the run of consecutive days in [days] that ends at [end] (0 if [end] is not in it). */
    fun runEndingAt(end: Long, days: Set<Long>): Int {
        var n = 0
        var d = end
        while (d in days) { n++; d-- }
        return n
    }

    fun longestRun(days: Set<Long>): Int {
        var best = 0
        for (d in days) if (d - 1 !in days) best = maxOf(best, runEndingAtForward(d, days))
        return best
    }

    private fun runEndingAtForward(start: Long, days: Set<Long>): Int {
        var n = 0
        var d = start
        while (d in days) { n++; d++ }
        return n
    }

    fun info(data: ActivityData, today: Long): StreakInfo {
        val streakDays = (data.activeDays + data.frozenDays).toSet()
        val end = when {
            today in streakDays -> today
            today - 1 in streakDays -> today - 1
            else -> null
        }
        val counted = end?.let { runEndingAt(it, streakDays) } ?: 0
        val current = if (end != null && data.runEnd == end) maxOf(counted, data.runLength) else counted
        val pending = end == null && data.freezes > 0 && (today - 2) in streakDays
        val recentFreeze = data.lastFreezeUsedDay?.takeIf { it in (today - 2)..(today - 1) }
        return StreakInfo(
            current = current,
            longest = maxOf(data.longestStreak, longestRun(streakDays)),
            daysPractised = data.activeDays.size,
            activeToday = today in data.activeDays,
            freezes = data.freezes,
            freezePending = pending,
            protectedStreak = when {
                !pending -> 0
                data.runEnd == today - 2 -> maxOf(data.runLength, runEndingAt(today - 2, streakDays))
                else -> runEndingAt(today - 2, streakDays)
            },
            recentFreezeDay = recentFreeze,
        )
    }
}
