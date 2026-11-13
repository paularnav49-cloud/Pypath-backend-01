package com.pypath.app.domain

/**
 * Final frontend polish: daily learning-time goal chosen during onboarding (minutes per day).
 * It sits next to the existing XP [DailyGoal]; the XP goal still drives streaks exactly as before.
 */
object LearningTime {
    /** The four onboarding choices, in minutes per day. */
    val OPTIONS: List<Int> = listOf(5, 10, 15, 30)

    const val DAYS_IN_MONTH = 30

    fun isValid(minutes: Int?): Boolean = minutes != null && minutes in OPTIONS

    /** Total minutes over a 30-day month. */
    fun monthlyMinutes(dailyMinutes: Int): Int = dailyMinutes * DAYS_IN_MONTH

    /** "2 hours 30 minutes", "5 hours", "7 hours 30 minutes", "15 hours". */
    fun monthlyLabel(dailyMinutes: Int): String = durationLabel(monthlyMinutes(dailyMinutes), joiner = " ")

    /** "7 hours and 30 minutes" (sentence form). */
    fun monthlySentenceLabel(dailyMinutes: Int): String = durationLabel(monthlyMinutes(dailyMinutes), joiner = " and ")

    fun dailyLabel(minutes: Int): String = "$minutes minutes/day"

    fun durationLabel(totalMinutes: Int, joiner: String = " "): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        val hours = if (h == 1) "1 hour" else "$h hours"
        val mins = if (m == 1) "1 minute" else "$m minutes"
        return when {
            h == 0 -> mins
            m == 0 -> hours
            else -> "$hours$joiner$mins"
        }
    }

    /** Compact form for small spaces: "2h 30m", "5h". */
    fun shortLabel(totalMinutes: Int): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    /** "You chose 15 minutes every day. That's 7 hours and 30 minutes of Python learning in a month!" */
    fun impactMessage(dailyMinutes: Int): String =
        "You chose $dailyMinutes minutes every day. That's ${monthlySentenceLabel(dailyMinutes)} of Python learning in a month!"

    /**
     * XP goal suggested for a new learner's time goal (applied once, at the end of onboarding).
     * The learner can still change the XP goal any time.
     */
    fun suggestedXpGoal(dailyMinutes: Int): DailyGoal = when {
        dailyMinutes <= 5 -> DailyGoal.CASUAL
        dailyMinutes <= 15 -> DailyGoal.REGULAR
        else -> DailyGoal.SERIOUS
    }
}
