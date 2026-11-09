package com.pypath.app.domain

/** Batch 3 Part 3: tablet / landscape layout rules (plain Kotlin, unit-tested). */
object Layouts {
    /** Below this width (dp) the phone layout is used unchanged. */
    const val TABLET_MIN_DP = 600
    /** From this width (dp) the level screen shows the level list and the sub-level side by side. */
    const val TWO_PANE_MIN_DP = 720
    /** Max content width (dp) for lessons, quizzes, review and stats. */
    const val READING_MAX_DP = 640

    fun isTablet(widthDp: Int): Boolean = widthDp >= TABLET_MIN_DP
    fun isTwoPane(widthDp: Int): Boolean = widthDp >= TWO_PANE_MIN_DP
    fun dashboardColumns(widthDp: Int): Int = if (isTablet(widthDp)) 2 else 1

    /**
     * The sub-level shown on the right of the two-pane level screen when the learner hasn't picked
     * one yet: the one in progress, else the next available one, else the first playable one.
     */
    fun defaultSubLevel(level: LevelState): String? =
        (level.subLevels.firstOrNull { it.status == NodeStatus.IN_PROGRESS }
            ?: level.subLevels.firstOrNull { it.status == NodeStatus.AVAILABLE }
            ?: level.subLevels.firstOrNull { it.isPlayable })?.subLevel?.id
}
