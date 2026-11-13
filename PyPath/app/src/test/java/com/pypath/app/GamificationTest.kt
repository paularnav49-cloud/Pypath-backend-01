package com.pypath.app

import com.pypath.app.data.activity.ActivityData
import com.pypath.app.data.activity.ActivityUpdates
import com.pypath.app.data.activity.QuestionRecord
import com.pypath.app.data.progress.QuizResult
import com.pypath.app.data.model.StepType
import com.pypath.app.data.progress.ProgressUpdates
import com.pypath.app.data.progress.SubLevelProgress
import com.pypath.app.data.progress.UserProgress
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.DailyGoal
import com.pypath.app.domain.LearnerStats
import com.pypath.app.domain.Streaks
import com.pypath.app.domain.Xp
import com.pypath.app.domain.XpRules
import com.pypath.app.domain.XpTitles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private fun withSub(p: UserProgress, id: String, f: (SubLevelProgress) -> SubLevelProgress) =
    p.copy(subLevels = p.subLevels + (id to f(p.of(id))))

/** Batch 3, Part 1: progress schema 3 and retroactive XP. */
class XpMigrationTest {
    private val course = loadTestCourse()

    /** Exactly what the app saved before Batch 3 (schema 2, with Batch 2 completed tasks). */
    private val v2Json = """
        {"schemaVersion":2,"onboardingCompleted":true,"currentSubLevelId":"l2s2",
         "subLevels":{
           "l1s1":{"completedSteps":["learn","quiz"],"quiz":{"lastScore":4,"bestScore":4,"total":4,"attempts":1,"passed":true},"completed":true,"completedAt":1,"lastOpenedAt":1,"project":null},
           "l1s2":{"completedSteps":["learn","quiz"],"quiz":{"lastScore":3,"bestScore":4,"total":4,"attempts":2,"passed":true},"completed":true,"completedAt":2,"lastOpenedAt":2,"project":null},
           "l1s3":{"completedSteps":["learn","quiz"],"quiz":{"lastScore":4,"bestScore":4,"total":4,"attempts":1,"passed":true},"completed":true,"completedAt":3,"lastOpenedAt":3,"project":null},
           "l1s4":{"completedSteps":["learn","quiz"],"quiz":{"lastScore":4,"bestScore":4,"total":4,"attempts":1,"passed":true},"completed":true,"completedAt":4,"lastOpenedAt":4,"project":null},
           "l2s1":{"completedSteps":["learn","quiz"],"quiz":{"lastScore":3,"bestScore":3,"total":4,"attempts":1,"passed":true},"completed":true,"completedAt":5,"lastOpenedAt":5,"project":null},
           "l2s2":{"completedSteps":["learn"],"quiz":{"lastScore":1,"bestScore":1,"total":4,"attempts":1,"passed":false},"completed":false,"completedAt":null,"lastOpenedAt":6,"project":null}
         },
         "earnedBadges":["l1"],
         "completedTasks":["t_hello","t_sum"]}
    """.trimIndent()

    private val v1Json = """
        {"schemaVersion":1,"onboardingCompleted":true,"currentSubLevelId":"l2s1",
         "subLevels":{
           "l1s1":{"completedSteps":["learn","quiz"],"quiz":{"lastScore":4,"bestScore":4,"total":4,"attempts":1,"passed":true},"completed":true,"completedAt":1,"lastOpenedAt":1},
           "l1s2":{"completedSteps":["learn","quiz"],"quiz":{"lastScore":3,"bestScore":4,"total":4,"attempts":2,"passed":true},"completed":true,"completedAt":2,"lastOpenedAt":2},
           "l1s3":{"completedSteps":["learn","quiz"],"quiz":{"lastScore":4,"bestScore":4,"total":4,"attempts":1,"passed":true},"completed":true,"completedAt":3,"lastOpenedAt":3},
           "l1s4":{"completedSteps":["learn","quiz"],"quiz":{"lastScore":4,"bestScore":4,"total":4,"attempts":1,"passed":true},"completed":true,"completedAt":4,"lastOpenedAt":4}
         }}
    """.trimIndent()

    @Test fun schemaIsThree() = assertEquals(3, UserProgress.CURRENT_SCHEMA)

    @Test fun v2JsonLoadsAndGetsRetroactiveXpOnce() {
        val old = ProgressUpdates.decode(v2Json)
        assertEquals(2, old.schemaVersion)
        assertTrue(old.xpAwards.isEmpty())
        assertEquals(setOf("t_hello", "t_sum"), old.completedTasks)

        val m = ProgressUpdates.migrate(old, course)
        assertEquals(3, m.schemaVersion)
        val expected = mapOf(
            "quiz:l1s1" to 20, "quiz:l1s2" to 20, "quiz:l1s3" to 20, "quiz:l1s4" to 20, "quiz:l2s1" to 20,
            "level:l1" to 50, "task:t_hello" to 30, "task:t_sum" to 30,
        )
        assertEquals(expected, m.xpAwards)
        assertEquals(5 * 20 + 50 + 2 * 30, m.xpAwards.values.sum())
        // Everything else is untouched.
        assertEquals(old.subLevels, m.subLevels)
        assertEquals(old.earnedBadges, m.earnedBadges)
        assertEquals("l2s2", m.currentSubLevelId)
        // Runs once, and the live sync finds nothing new afterwards.
        assertEquals(m, ProgressUpdates.migrate(m, course))
        assertEquals(m to 0, ProgressUpdates.awardXp(m, course))
        // Survives a save/load round trip.
        assertEquals(m, ProgressUpdates.decode(ProgressUpdates.encode(m)))
    }

    @Test fun v1JsonMigratesThroughBothSteps() {
        val m = ProgressUpdates.migrate(ProgressUpdates.decode(v1Json), course)
        assertEquals(3, m.schemaVersion)
        assertEquals(setOf("l1"), m.earnedBadges)
        assertEquals(4 * 20 + 50, m.xpAwards.values.sum())
        assertNull(CourseSnapshot(course, m).pendingBadgeLevel)
    }

    @Test fun liveSyncDoesNothingBeforeMigration() {
        val old = ProgressUpdates.decode(v2Json)
        assertEquals(old to 0, ProgressUpdates.awardXp(old, course))
    }

    @Test fun passingAQuizPaysOnceAndReplaysPayNothing() {
        var p = ProgressUpdates.migrate(ProgressUpdates.decode(v2Json), course)
        p = withSub(p, "l2s2") {
            it.copy(quiz = QuizResult(4, 4, 4, 2, true), completedSteps = setOf(StepType.LEARN, StepType.QUIZ), completed = true)
        }
        val (p2, gained) = ProgressUpdates.awardXp(p, course)
        assertEquals(XpRules.QUIZ_PASS, gained)
        assertEquals(20, p2.xpAwards["quiz:l2s2"])
        // Re-doing the passed quiz changes scores but never pays again.
        val replay = withSub(p2, "l2s2") { it.copy(quiz = it.quiz!!.copy(lastScore = 2, attempts = 3)) }
        assertEquals(0, ProgressUpdates.awardXp(replay, course).second)
    }

    @Test fun finalProjectAndLevelProjectsPayOnce() {
        val core = course.levels.filterNot { it.advanced }
        val ids = core.flatMap { l -> l.subLevels.map { it.id } }.toTypedArray()
        val final = core.last().subLevels.single()
        val bonus = core.flatMap { it.projects }.first()
        var p = completed(*ids)
        p = withSub(p, bonus.id) { SubLevelProgress(setOf(StepType.PROJECT), completed = true) }
        val events = Xp.oneTimeEvents(course, p)
        assertEquals(XpRules.FINAL_PROJECT, events["final:${final.id}"])
        assertNull("the final project has no quiz XP", events["quiz:${final.id}"])
        assertEquals(XpRules.LEVEL_COMPLETE, events["level:${core.last().id}"])
        assertEquals(XpRules.LEVEL_PROJECT, events["project:${bonus.id}"])
        core.forEach { assertEquals(it.id, 50, events["level:${it.id}"]) }
        val (p2, gained) = ProgressUpdates.awardXp(p, course)
        assertEquals(events.values.sum(), gained)
        assertEquals(0, ProgressUpdates.awardXp(p2, course).second)
    }

    @Test fun resetClearsXp() {
        val m = ProgressUpdates.migrate(ProgressUpdates.decode(v2Json), course)
        val r = ProgressUpdates.reset(m)
        assertTrue(r.xpAwards.isEmpty())
        assertTrue(r.completedTasks.isEmpty())
        assertTrue(r.onboardingCompleted)
        assertEquals(3, r.schemaVersion)
    }
}

/** Per-answer XP and titles. */
class XpRulesTest {
    private val day = 20_000L
    private val goal = 40

    @Test fun answerXpRules() {
        assertEquals(10, Xp.answerXp(0, 0, correct = true, hintUsed = false, eligible = true))
        assertEquals(5, Xp.answerXp(0, 0, correct = true, hintUsed = true, eligible = true))
        assertEquals(4, Xp.answerXp(1, 0, correct = true, hintUsed = false, eligible = true))
        assertEquals(2, Xp.answerXp(3, 0, correct = true, hintUsed = true, eligible = true))
        assertEquals(0, Xp.answerXp(0, 0, correct = false, hintUsed = false, eligible = true))
        assertEquals("already earned", 0, Xp.answerXp(1, 10, correct = true, hintUsed = false, eligible = true))
        assertEquals("quiz already passed", 0, Xp.answerXp(0, 0, correct = true, hintUsed = false, eligible = false))
    }

    @Test fun questionEarnsXpOnlyOnce() {
        var a = ActivityData()
        a = ActivityUpdates.recordAnswer(a, "q1", "l1s1", correct = false, hintUsed = false, eligible = true, today = day, goalXp = goal)
        assertEquals(0, a.questions.getValue("q1").xp)
        assertEquals(4, ActivityUpdates.answerXp(a, "q1", correct = true, hintUsed = false, eligible = true))
        a = ActivityUpdates.recordAnswer(a, "q1", "l1s1", correct = true, hintUsed = false, eligible = true, today = day, goalXp = goal)
        assertEquals(4, a.questions.getValue("q1").xp)
        a = ActivityUpdates.recordAnswer(a, "q1", "l1s1", correct = true, hintUsed = false, eligible = true, today = day, goalXp = goal)
        val r = a.questions.getValue("q1")
        assertEquals(4, r.xp)
        assertEquals(3, r.attempts)
        assertEquals(2, r.correct)
        assertEquals(4, a.days.getValue(day).xp)
        assertEquals(3, a.days.getValue(day).actions)
    }

    @Test fun wrongAnswersEnterTheReviewPool() {
        var a = ActivityUpdates.recordAnswer(ActivityData(), "q1", "l1s1", true, false, true, day, goal)
        assertEquals(0, a.questions.getValue("q1").box)
        a = ActivityUpdates.recordAnswer(a, "q2", "l1s1", false, false, true, day, goal)
        assertEquals(1, a.questions.getValue("q2").box)
        assertEquals(day + 1, a.questions.getValue("q2").dueDay)
        // A correct normal-quiz answer leaves the box alone; a correct review moves it up.
        a = ActivityUpdates.recordAnswer(a, "q2", "l1s1", true, false, true, day + 1, goal)
        assertEquals(1, a.questions.getValue("q2").box)
        a = ActivityUpdates.recordAnswer(a, "q2", "l1s1", true, false, true, day + 1, goal, fromReview = true)
        assertEquals(2, a.questions.getValue("q2").box)
        assertEquals(day + 1 + 3, a.questions.getValue("q2").dueDay)
    }

    @Test fun titles() {
        assertEquals("Beginner", XpTitles.forXp(0).name)
        assertEquals("Learner", XpTitles.forXp(150).name)
        assertEquals("Coder", XpTitles.forXp(999).name)
        assertEquals("Problem Solver", XpTitles.forXp(1200).name)
        assertEquals("Python Pro", XpTitles.forXp(100_000).name)
        assertNull(XpTitles.next(2500))
        assertEquals(0.5f, XpTitles.progressToNext(75), 0.001f)
        assertEquals(1f, XpTitles.progressToNext(3000), 0.001f)
    }
}

/** F2 streak and daily goal rules, including the edge cases from the brief. */
class StreakTest {
    private val d0 = LocalDate.of(2026, 5, 1).toEpochDay()

    /** One day that meets the 40 XP goal. */
    private fun practise(a: ActivityData, day: Long, goal: Int = 40) = ActivityUpdates.addXp(a, day, goal, 40)

    private fun days(a: ActivityData, from: Long, count: Int, goal: Int = 40): ActivityData {
        var x = a
        for (i in 0 until count) x = practise(x, from + i, goal)
        return x
    }

    @Test fun firstLaunch() {
        val a = ActivityData()
        val s = Streaks.info(a, d0)
        assertEquals(0, s.current)
        assertEquals(0, s.longest)
        assertEquals(0, s.daysPractised)
        assertFalse(s.activeToday)
        assertFalse(s.freezePending)
        assertNull(a.pendingMilestone)
        assertEquals(a, ActivityUpdates.decode(null))
        assertEquals(a, ActivityUpdates.decode("broken"))
    }

    @Test fun partialDayDoesNotCountUntilTheGoalIsMet() {
        var a = ActivityUpdates.addXp(ActivityData(), d0, 40, 30)
        a = ActivityUpdates.recordAction(a, d0, 40)
        assertFalse(Streaks.info(a, d0).activeToday)
        assertEquals(0, Streaks.info(a, d0).current)
        a = ActivityUpdates.addXp(a, d0, 40, 10)
        assertTrue(Streaks.info(a, d0).activeToday)
        assertEquals(1, Streaks.info(a, d0).current)
    }

    @Test fun goalOffCountsAnyAction() {
        val a = ActivityUpdates.recordAction(ActivityData(), d0, DailyGoal.OFF.xp)
        assertTrue(Streaks.info(a, d0).activeToday)
        // Raising the goal later never removes a day that already counted.
        assertEquals(a, ActivityUpdates.evaluateDay(a, d0, DailyGoal.SERIOUS.xp))
    }

    @Test fun loweringTheGoalCanCompleteToday() {
        val a = ActivityUpdates.addXp(ActivityData(), d0, 40, 25)
        assertFalse(Streaks.info(a, d0).activeToday)
        assertTrue(Streaks.info(ActivityUpdates.evaluateDay(a, d0, DailyGoal.CASUAL.xp), d0).activeToday)
    }

    @Test fun streakEndsTodayOrYesterday() {
        val a = days(ActivityData(), d0, 4)
        assertEquals(4, Streaks.info(a, d0 + 3).current)
        assertEquals("still alive the next day", 4, Streaks.info(a, d0 + 4).current)
        assertEquals("broken after a missed day", 0, Streaks.info(a, d0 + 5).current)
        assertEquals(4, Streaks.info(a, d0 + 5).longest)
    }

    @Test fun midnightRolloverWhileTheAppIsOpen() {
        val zone = ZoneId.of("Asia/Kolkata")
        val before = LocalDateTime.of(2026, 5, 1, 23, 59, 30).atZone(zone).toInstant()
        val after = before.plusSeconds(60)
        val day1 = Streaks.dayOf(before, zone)
        val day2 = Streaks.dayOf(after, zone)
        assertEquals(day1 + 1, day2)
        var a = ActivityUpdates.addXp(ActivityData(), day1, 40, 30)
        a = ActivityUpdates.addXp(a, day2, 40, 30)
        // 30 + 30 across midnight is two partial days, not one full day.
        assertEquals(30, a.days.getValue(day1).xp)
        assertEquals(30, a.days.getValue(day2).xp)
        assertTrue(a.activeDays.isEmpty())
        a = practise(a, day2)
        assertEquals(listOf(day2), a.activeDays)
    }

    @Test fun timezoneChangeNeverCountsADayTwice() {
        val london = ZoneId.of("Europe/London")
        val tokyo = ZoneId.of("Asia/Tokyo")
        val t1 = LocalDateTime.of(2026, 5, 1, 20, 0).atZone(london).toInstant() // May 1 London, May 2 Tokyo
        val day1 = Streaks.dayOf(t1, london)
        var a = practise(ActivityData(), day1)
        // Fly to Tokyo: two hours later it's already May 2 there.
        val t2 = t1.plusSeconds(2 * 3600)
        val day2 = Streaks.dayOf(t2, tokyo)
        assertEquals(day1 + 1, day2)
        a = practise(a, day2)
        assertEquals(2, Streaks.info(a, day2).current)
        // Fly back west: it's May 1 or 2 again locally; those days are already counted.
        a = practise(a, Streaks.dayOf(t2.plusSeconds(3600), london))
        a = practise(a, day2)
        assertEquals(listOf(day1, day2), a.activeDays)
        assertEquals(2, Streaks.info(a, day2).longest)
    }

    @Test fun clockMovedBackwardsNeverReducesLongestOrGrantsTwice() {
        var a = days(ActivityData(), d0, 5)
        assertEquals(5, a.longestStreak)
        // The device date jumps back 10 days.
        val past = d0 - 10
        assertEquals(0, Streaks.info(a, past).current)
        assertEquals(5, Streaks.info(a, past).longest)
        a = practise(a, past)
        assertEquals(5, a.longestStreak)
        assertEquals(6, a.activeDays.size)
        // Back to the right date: days already counted are not counted again.
        val again = days(a, d0, 5)
        assertEquals(a.activeDays, again.activeDays)
        assertEquals(5, Streaks.info(again, d0 + 4).current)
        assertEquals(5, again.longestStreak)
    }

    @Test fun daylightSavingShiftsKeepDaysConsecutive() {
        val ny = ZoneId.of("America/New_York")
        // Spring forward on 8 March 2026 (a 23-hour day) and fall back on 1 November 2026 (25 hours).
        val spring = listOf(
            LocalDateTime.of(2026, 3, 7, 23, 30), LocalDateTime.of(2026, 3, 8, 23, 30), LocalDateTime.of(2026, 3, 9, 0, 30),
        ).map { Streaks.dayOf(it.atZone(ny).toInstant(), ny) }
        assertEquals(listOf(spring[0], spring[0] + 1, spring[0] + 2), spring)
        val a = spring.fold(ActivityData()) { acc, d -> practise(acc, d) }
        assertEquals(3, Streaks.info(a, spring[2]).current)
        // 01:30 happens twice on 1 November: both are the same local day.
        val first = LocalDateTime.of(2026, 11, 1, 1, 30).atZone(ny).withEarlierOffsetAtOverlap().toInstant()
        val second = LocalDateTime.of(2026, 11, 1, 1, 30).atZone(ny).withLaterOffsetAtOverlap().toInstant()
        assertEquals(3600, second.epochSecond - first.epochSecond)
        assertEquals(Streaks.dayOf(first, ny), Streaks.dayOf(second, ny))
        // 24 hours after 23:30 on 7 March is 00:30 on 9 March (8 March had 23 hours): day keys come
        // from the local date, not from elapsed time.
        val plus24h = LocalDateTime.of(2026, 3, 7, 23, 30).atZone(ny).toInstant().plusSeconds(24 * 3600)
        assertEquals(spring[0] + 2, Streaks.dayOf(plus24h, ny))
    }

    @Test fun milestonesAreQueuedAndConsumed() {
        var a = days(ActivityData(), d0, 2)
        assertNull(a.pendingMilestone)
        a = practise(a, d0 + 2)
        assertEquals(3, a.pendingMilestone)
        a = ActivityUpdates.consumeMilestone(a)
        assertNull(a.pendingMilestone)
        a = days(a, d0 + 3, 4)
        assertEquals(7, a.pendingMilestone)
    }

    @Test fun freezeCoversExactlyOneMissedDay() {
        var a = days(ActivityData(), d0, 7)
        assertEquals(1, a.freezes)
        // Miss d0+7. On d0+8 before practising, the streak is shown as protected.
        val pending = Streaks.info(a, d0 + 8)
        assertTrue(pending.freezePending)
        assertEquals(7, pending.protectedStreak)
        a = practise(a, d0 + 8)
        assertEquals(listOf(d0 + 7), a.frozenDays)
        assertEquals(0, a.freezes)
        val s = Streaks.info(a, d0 + 8)
        assertEquals(9, s.current)
        assertEquals(8, s.daysPractised)
        assertEquals(d0 + 7, s.recentFreezeDay)
    }

    @Test fun noFreezeForTwoMissedDaysOrWithoutOne() {
        var a = days(ActivityData(), d0, 7)
        a = practise(a, d0 + 9) // missed two days
        assertTrue(a.frozenDays.isEmpty())
        assertEquals(1, Streaks.info(a, d0 + 9).current)
        assertEquals(7, Streaks.info(a, d0 + 9).longest)

        var b = days(ActivityData(), d0, 3) // no freeze earned yet
        b = practise(b, d0 + 4)
        assertTrue(b.frozenDays.isEmpty())
        assertEquals(1, Streaks.info(b, d0 + 4).current)
    }

    @Test fun keepsOnlyTheLast400Days() {
        val a = days(ActivityData(), d0, 500)
        assertEquals(400, a.activeDays.size)
        assertEquals(d0 + 100, a.activeDays.first())
        assertEquals(500, a.longestStreak)
        assertEquals("long streaks keep counting past the stored window", 500, Streaks.info(a, d0 + 499).current)
        assertEquals(501, Streaks.info(practise(a, d0 + 500), d0 + 500).current)
        assertTrue(a.days.size <= 30)
        val round = ActivityUpdates.decode(ActivityUpdates.encode(a))
        assertEquals(a, round)
    }
}

/** Stats screen numbers. */
class StatsTest {
    private val course = loadTestCourse()
    private val day = 20_000L

    @Test fun emptyStateForANewLearner() {
        val s = LearnerStats.build(CourseSnapshot(course, UserProgress()), ActivityData(), DailyGoal.DEFAULT, day)
        assertEquals(0, s.totalXp)
        assertEquals("Beginner", s.title.name)
        assertNull(s.accuracyPercent)
        assertTrue(s.weakest.isEmpty())
        assertTrue(s.perLevel.isEmpty())
        assertFalse(s.historyPartial)
        assertEquals(0f, s.goalFraction, 0f)
        assertEquals(0, s.badgesEarned)
    }

    @Test fun totalsAccuracyAndWeakestTopics() {
        val q1 = course.levels[0].subLevels[0].quiz!!.questions.map { it.id }
        val q2 = course.levels[0].subLevels[1].quiz!!.questions.map { it.id }
        val q3 = course.levels[1].subLevels[0].quiz!!.questions.map { it.id }
        val records = buildMap {
            q1.forEach { put(it, QuestionRecord("l1s1", attempts = 1, correct = 1, xp = 10)) }
            q2.forEach { put(it, QuestionRecord("l1s2", attempts = 2, correct = 1, xp = 4)) }
            q3.take(2).forEach { put(it, QuestionRecord("l2s1", attempts = 2, correct = 0)) }
            put("removed_question", QuestionRecord("l9s9", attempts = 5, correct = 0, xp = 10))
        }
        val activity = ActivityData(questions = records, reviewXp = 10)
        val p = completed("l1s1").copy(xpAwards = mapOf("quiz:l1s1" to 20))
        val s = LearnerStats.build(CourseSnapshot(course, p), activity, DailyGoal.DEFAULT, day)
        // XP: one-time + every question's XP (even removed ones keep their XP) + review.
        assertEquals(20 + q1.size * 10 + q2.size * 4 + 10 + 10, s.totalXp)
        // Accuracy ignores ids that no longer exist.
        assertEquals(q1.size + q2.size * 2 + 4, s.questionsAnswered)
        assertEquals(q1.size + q2.size, s.questionsCorrect)
        assertEquals(listOf("l2s1", "l1s2"), s.weakest.map { it.subLevel.subLevel.id })
        assertEquals(listOf("l1", "l2"), s.perLevel.map { it.level.id })
        assertFalse("l1s1 was passed and is tracked", s.historyPartial)
    }

    @Test fun historyIsPartialForQuizzesPassedBeforeTheUpdate() {
        val s = LearnerStats.build(CourseSnapshot(course, completed("l1s1", "l1s2")), ActivityData(), DailyGoal.DEFAULT, day)
        assertTrue(s.historyPartial)
        assertEquals(2, s.subLevelsPassed)
    }

    @Test fun dailyGoalProgress() {
        val a = ActivityUpdates.addXp(ActivityData(), day, 40, 30)
        val s = LearnerStats.build(CourseSnapshot(course, UserProgress()), a, DailyGoal.REGULAR, day)
        assertEquals(30, s.todayXp)
        assertEquals(0.75f, s.goalFraction, 0.001f)
        assertFalse(s.goalMetToday)
        assertEquals(DailyGoal.REGULAR, DailyGoal.fromName(null))
        assertEquals(DailyGoal.OFF, DailyGoal.fromName("OFF"))
        assertEquals(DailyGoal.REGULAR, DailyGoal.fromName("unknown"))
    }
}
