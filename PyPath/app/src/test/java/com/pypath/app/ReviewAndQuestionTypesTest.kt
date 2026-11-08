package com.pypath.app

import com.pypath.app.data.activity.ActivityData
import com.pypath.app.data.activity.ActivityUpdates
import com.pypath.app.data.activity.DayTally
import com.pypath.app.data.activity.QuestionRecord
import com.pypath.app.data.activity.ReviewSessionState
import com.pypath.app.data.content.CourseParser
import com.pypath.app.data.model.Question
import com.pypath.app.data.model.QuestionType
import com.pypath.app.data.progress.QuizResult
import com.pypath.app.data.progress.UserProgress
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.QuestionCheck
import com.pypath.app.domain.Review
import com.pypath.app.domain.XpRules
import com.pypath.app.ui.mcq.McqPhase
import com.pypath.app.ui.mcq.McqSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Batch 3 Part 2: the predict / fill / order question types and the content that uses them. */
class QuestionTypesTest {
    private val course = loadTestCourse()
    private val questions = course.levels.flatMap { it.subLevels }.filterNot { it.isProject }
        .flatMap { s -> s.quiz!!.questions.map { s.id to it } }

    private val predict = Question(
        id = "p", prompt = "?", code = "print('Hi')", explanation = "e", type = QuestionType.PREDICT,
        answer = "Hi there\nok", alternatives = listOf("Hello"),
    )
    private val fill = Question(
        id = "f", prompt = "?", code = "for n in {{1}}(1, {{2}}):\n    print(n)", explanation = "e",
        type = QuestionType.FILL, blanks = listOf("range", "4"), distractors = listOf("len"),
    )
    private val order = Question(
        id = "o", prompt = "?", explanation = "e", type = QuestionType.ORDER,
        lines = listOf("a = 1", "b = 2", "print(a + b)", "print('end')"), alternativeOrders = listOf(listOf(1, 0, 2, 3)),
    )

    @Test fun oldJsonWithoutTypeIsAnMcqAndNewTypesParse() {
        val level = CourseParser.parseLevel(
            """{"id":"x","number":1,"title":"t","description":"d","subLevels":[{"id":"xs1","code":"1.1","title":"t","summary":"s","minutes":1,
            "steps":["learn","quiz"],"lesson":{"pages":[]},"quiz":{"passPercent":60,"questions":[
            {"id":"a","prompt":"p","options":["1","2","3","4"],"correctIndex":2,"explanation":"e"},
            {"id":"b","type":"predict","prompt":"p","code":"print(1)","answer":"1","explanation":"e"},
            {"id":"c","type":"something-new","prompt":"p","options":["1","2","3","4"],"correctIndex":0,"explanation":"e"}]}}]}""",
        )
        val qs = level.subLevels[0].quiz!!.questions
        assertEquals(QuestionType.MCQ, qs[0].type)
        assertTrue(qs[0].isMcq)
        assertEquals(QuestionType.PREDICT, qs[1].kind)
        assertEquals(emptyList<String>(), qs[1].options)
        assertTrue("unknown types fall back to MCQ", qs[2].isMcq)
    }

    @Test fun predictComparisonIgnoresSpacesAndBlankLinesButNotLineBreaks() {
        assertTrue(QuestionCheck.predictCorrect(predict, "Hi there\nok"))
        assertTrue(QuestionCheck.predictCorrect(predict, "  Hi   there \r\n ok  \n\n"))
        assertTrue(QuestionCheck.predictCorrect(predict, "\nHello\n"))
        assertFalse(QuestionCheck.predictCorrect(predict, "Hi there ok"))
        assertFalse("case matters by default", QuestionCheck.predictCorrect(predict, "hi there\nok"))
        assertTrue(QuestionCheck.predictCorrect(predict.copy(caseSensitive = false), "HI THERE\nOK"))
        assertFalse(QuestionCheck.predictCorrect(predict, "   "))
    }

    @Test fun fillBankIsStableAndOnlyTheRightTokensAreCorrect() {
        val bank = QuestionCheck.fillBank(fill)
        assertEquals(bank, QuestionCheck.fillBank(fill))
        assertEquals(setOf("range", "4", "len"), bank.toSet())
        val right = listOf(bank.indexOf("range"), bank.indexOf("4"))
        assertTrue(QuestionCheck.fillCorrect(fill, right))
        assertFalse(QuestionCheck.fillCorrect(fill, listOf(bank.indexOf("len"), bank.indexOf("4"))))
        assertFalse(QuestionCheck.fillCorrect(fill, listOf(right[0], null)))
        assertEquals("for n in range(1, 4):\n    print(n)", QuestionCheck.fillSolution(fill))
        assertEquals("for n in range(1, ____):\n    print(n)", QuestionCheck.fillPreview(fill, listOf(right[0], null)))
    }

    @Test fun orderAcceptsTheAnswerAndListedAlternativesAndNeverStartsSolved() {
        assertTrue(QuestionCheck.orderCorrect(order, listOf(0, 1, 2, 3)))
        assertTrue(QuestionCheck.orderCorrect(order, listOf(1, 0, 2, 3)))
        assertFalse(QuestionCheck.orderCorrect(order, listOf(0, 2, 1, 3)))
        assertFalse(QuestionCheck.orderCorrect(order, QuestionCheck.orderStart(order)))
        assertEquals(listOf(1, 0, 2), QuestionCheck.move(listOf(0, 1, 2), 1, -1))
        assertEquals(listOf(0, 1, 2), QuestionCheck.move(listOf(0, 1, 2), 0, -1))
        assertEquals("indented 1 level: print(n)", QuestionCheck.spokenLine("    print(n)"))
    }

    @Test fun sessionRunsEveryTypeWithTheSameScoring() {
        val mcq = Question(id = "m", prompt = "?", options = listOf("a", "b", "c", "d"), correctIndex = 1, explanation = "e")
        val s = McqSession(listOf(mcq, predict, fill, order), passPercent = 60)
        s.select(1); s.check(); assertTrue(s.isCorrect); s.next()
        assertFalse(s.canCheck)
        s.type("Hi there\nok"); assertTrue(s.canCheck); s.check(); assertTrue(s.isCorrect); s.next()
        val bank = QuestionCheck.fillBank(fill)
        s.tapToken(bank.indexOf("4")) // goes into blank 1 (wrong place)
        s.tapBlank(0) // empties it again
        assertEquals(listOf<Int?>(null, null), s.fill)
        s.tapToken(bank.indexOf("range")); s.tapToken(bank.indexOf("4"))
        assertTrue(s.canCheck); s.check(); assertTrue(s.isCorrect); s.next()
        assertFalse(s.isCorrect) // shuffled start is never correct
        s.check(); assertFalse(s.isCorrect)
        s.moveLine(0, 1) // locked after checking
        s.next()
        assertEquals(McqPhase.FINISHED, s.phase)
        assertEquals(3, s.score)
        assertEquals(75, s.percent)
        assertEquals(listOf(true, true, true, false), s.results)
        assertEquals(listOf(1, -1, -1, -1), s.answers)
    }

    @Test fun contentHasEnoughNewQuestionsSpreadOut() {
        val newOnes = questions.filter { !it.second.isMcq }
        assertTrue(newOnes.count { it.second.kind == QuestionType.PREDICT } >= 12)
        assertTrue(newOnes.count { it.second.kind == QuestionType.FILL } >= 12)
        assertTrue(newOnes.count { it.second.kind == QuestionType.ORDER } >= 10)
        val perSub = newOnes.groupingBy { it.first }.eachCount()
        assertTrue(perSub.values.all { it <= 2 })
        assertTrue(perSub.size in 20..25)
        course.levels.flatMap { it.subLevels }.filterNot { it.isProject }.forEach { s ->
            val kinds = s.quiz!!.questions.map { it.isMcq }
            assertEquals("${s.id}: new types come after the MCQs", kinds.sortedDescending(), kinds)
        }
    }

    @Test fun everyNewQuestionIsWellFormed() {
        questions.map { it.second }.filter { !it.isMcq }.forEach { q ->
            assertTrue("${q.id} hint", !q.hint.isNullOrBlank())
            assertTrue("${q.id} explanation", q.explanation.isNotBlank())
            when (q.kind) {
                QuestionType.PREDICT -> {
                    assertTrue(q.id, !q.code.isNullOrBlank() && !q.answer.isNullOrBlank())
                    assertTrue(q.id, QuestionCheck.predictCorrect(q, q.answer!!))
                }
                QuestionType.FILL -> {
                    assertTrue(q.id, q.blanks.size in 1..3 && q.distractors.size in 1..2)
                    val bank = QuestionCheck.fillBank(q)
                    assertEquals("${q.id} distinct bank", bank.size, bank.toSet().size)
                    assertEquals(q.id, q.blanks.size, QuestionCheck.fillParts(q).count { it is QuestionCheck.FillPart.Blank })
                    assertTrue(q.id, QuestionCheck.fillCorrect(q, q.blanks.map { bank.indexOf(it) }))
                    assertFalse(q.id, QuestionCheck.fillSolution(q).contains("{{"))
                }
                QuestionType.ORDER -> {
                    assertTrue(q.id, q.lines.size in 4..7)
                    assertFalse("${q.id} starts solved", QuestionCheck.orderCorrect(q, QuestionCheck.orderStart(q)))
                    assertTrue(q.id, QuestionCheck.orderCorrect(q, q.lines.indices.toList()))
                }
            }
        }
    }
}

/** Batch 3 Part 2: learners who already passed stay passed; the pass percent uses the new total. */
class PassFlagTest {
    @Test fun passedStaysPassedAfterQuestionsAreAppended() {
        val old = QuizResult(4, 4, 4, 1, true) // saved before: 4/4 on a 4-question quiz
        val after = QuizResult.record(old, score = 2, total = 6, passed = false)
        assertTrue(after.passed)
        assertEquals(4, after.bestScore)
        assertEquals(4, after.bestOutOf)
        assertEquals(100, after.bestPercent)
        assertEquals(2, after.lastScore)
        assertEquals(6, after.total)
        val better = QuizResult.record(after, score = 6, total = 6, passed = true)
        assertEquals(6, better.bestScore); assertEquals(6, better.bestOutOf)
    }

    @Test fun oldSaveWithoutBestTotalUsesTotal() {
        val r = QuizResult(3, 3, 4, 1, true)
        assertEquals(4, r.bestOutOf)
        assertEquals(75, r.bestPercent)
    }

    @Test fun newAttemptsArePercentOfTheNewTotal() {
        val qs = List(6) { i -> Question(id = "q$i", prompt = "Q", options = listOf("a", "b", "c", "d"), correctIndex = 0, explanation = "") }
        val s = McqSession(qs, passPercent = 60)
        repeat(6) { i -> s.select(if (i < 3) 0 else 1); s.check(); s.next() }
        assertEquals(50, s.percent) // 3 of 6, although 3 of the original 4 would have been 75%
        assertFalse(s.passed)
    }
}

/** Batch 3 Part 2: Leitner boxes, due dates, picking, missing ids and resuming after the app is killed. */
class ReviewTest {
    private val course = loadTestCourse()
    private val snap = CourseSnapshot(course, UserProgress())
    private val index = Review.index(snap)
    private val day = 20_000L
    private val l1 = course.levels[0].subLevels[0].quiz!!.questions.map { it.id }
    private val l2 = course.levels[1].subLevels[0].quiz!!.questions.map { it.id }
    private val l3 = course.levels[2].subLevels[0].quiz!!.questions.map { it.id }.take(4)

    private fun answer(a: ActivityData, id: String, correct: Boolean, d: Long = day, review: Boolean = false) =
        ActivityUpdates.recordAnswer(a, id, "", correct, false, eligible = true, today = d, goalXp = 30, fromReview = review)

    @Test fun onlyWrongAnswersEnterThePool() {
        var a = answer(ActivityData(), l1[0], correct = true)
        assertEquals(0, a.questions.getValue(l1[0]).box)
        a = answer(a, l1[1], correct = false)
        assertEquals(1, a.questions.getValue(l1[1]).box)
        assertEquals(day + 1, a.questions.getValue(l1[1]).dueDay)
        assertEquals(listOf(l1[1]), Review.pick(a, index, day + 1))
        assertTrue(Review.pick(a, index, day).isEmpty()) // not due yet
    }

    @Test fun boxesMoveUpWithTheIntervalsAndWrongResets() {
        var a = answer(ActivityData(), l1[0], correct = false)
        var d = day
        for ((box, interval) in listOf(2 to 3, 3 to 7, 4 to 14, 5 to 30, 5 to 30)) {
            d = a.questions.getValue(l1[0]).dueDay!!
            a = answer(a, l1[0], correct = true, d = d, review = true)
            assertEquals(box, a.questions.getValue(l1[0]).box)
            assertEquals(d + interval, a.questions.getValue(l1[0]).dueDay)
        }
        a = answer(a, l1[0], correct = false, d = d + 30, review = true)
        assertEquals(1, a.questions.getValue(l1[0]).box)
        assertEquals(d + 31, a.questions.getValue(l1[0]).dueDay)
    }

    @Test fun earlyReviewKeepsTheBoxAndQuizAnswersNeverMoveItUp() {
        var a = answer(ActivityData(), l1[0], correct = false)
        a = answer(a, l1[0], correct = true, d = day, review = true) // due tomorrow, reviewed early
        assertEquals(1, a.questions.getValue(l1[0]).box)
        a = answer(a, l1[0], correct = true, d = day + 5, review = false) // normal quiz
        assertEquals(1, a.questions.getValue(l1[0]).box)
    }

    @Test fun pickIsOldestFirstMixedAcrossLevelsLimitedAndNeverRepeats() {
        val records = buildMap {
            l1.forEachIndexed { i, id -> put(id, QuestionRecord("l1s1", attempts = 1, box = 1, dueDay = day - 1, lastDay = day - 2 + i)) }
            l2.forEachIndexed { i, id -> put(id, QuestionRecord("l2s1", attempts = 1, box = 1, dueDay = day - 1, lastDay = day - 2 + i)) }
            l3.forEach { put(it, QuestionRecord("l3s1", attempts = 1, box = 2, dueDay = day - 5)) }
            put("gone_question", QuestionRecord("l9s9", attempts = 1, box = 1, dueDay = day - 30))
        }
        val a = ActivityData(questions = records)
        val picked = Review.pick(a, index, day)
        assertEquals(XpRules.REVIEW_SESSION_SIZE, picked.size)
        assertEquals(picked.size, picked.toSet().size)
        assertFalse("missing ids are ignored", "gone_question" in picked)
        assertEquals("oldest due first", l3, picked.take(l3.size))
        val sameDay = picked.drop(l3.size).take(4)
        assertEquals("mixed across levels", listOf(l1[0], l2[0], l1[1], l2[1]), sameDay)
        val info = Review.info(a, index, day)
        assertEquals(l1.size + l2.size + l3.size, info.dueCount)
        assertNull(info.nextDueDay)
    }

    @Test fun allCaughtUpShowsTheNextDueDay() {
        val a = ActivityData(questions = mapOf(l1[0] to QuestionRecord("l1s1", box = 2, dueDay = day + 3), "gone" to QuestionRecord(box = 1, dueDay = day - 1)))
        val info = Review.info(a, index, day)
        assertEquals(0, info.dueCount)
        assertEquals(day + 3, info.nextDueDay)
        assertEquals(1, info.poolSize)
    }

    @Test fun scopedReviewUsesOnlyThoseSubLevelsEvenIfNotDue() {
        val a = ActivityData(questions = mapOf(
            l1[0] to QuestionRecord("l1s1", box = 1, dueDay = day + 1),
            l2[0] to QuestionRecord("l2s1", box = 1, dueDay = day - 1),
        ))
        assertEquals(listOf(l1[0]), Review.pick(a, index, day, scope = setOf("l1s1")))
    }

    @Test fun sessionSummaryXpAndDailyXpLimit() {
        var a = ActivityData(questions = mapOf(
            l1[0] to QuestionRecord("l1s1", attempts = 1, box = 1, dueDay = day),
            l1[1] to QuestionRecord("l1s1", attempts = 1, box = 2, dueDay = day),
        ))
        a = ActivityUpdates.startReview(a, listOf(l1[0], l1[1]), emptyList(), day)
        a = ActivityUpdates.recordReviewAnswer(a, l1[0], "l1s1", correct = true, hintUsed = false, today = day, goalXp = 30)
        assertFalse(a.reviewSession!!.finished)
        a = ActivityUpdates.recordReviewAnswer(a, l1[1], "l1s1", correct = false, hintUsed = true, today = day, goalXp = 30)
        val s = a.reviewSession!!
        assertTrue(s.finished)
        assertEquals(2, s.answered); assertEquals(1, s.correct); assertEquals(1, s.movedUp)
        assertEquals(XpRules.REVIEW_SESSION, s.xpEarned)
        assertEquals(XpRules.REVIEW_SESSION, a.reviewXp)
        assertEquals(1, a.questions.getValue(l1[1]).box)
        assertNull(ActivityUpdates.clearReview(a).reviewSession)
        // After the daily limit, sessions still work but pay no XP.
        var b = a.copy(days = a.days + (day to (a.days[day] ?: DayTally()).copy(reviews = XpRules.REVIEW_SESSIONS_WITH_XP_PER_DAY)))
        b = ActivityUpdates.startReview(b, listOf(l1[1]), emptyList(), day)
        b = ActivityUpdates.recordReviewAnswer(b, l1[1], "l1s1", correct = true, hintUsed = false, today = day, goalXp = 30)
        assertEquals(0, b.reviewSession!!.xpEarned)
        assertEquals(a.reviewXp, b.reviewXp)
    }

    @Test fun resumeAfterTheAppIsKilledMidSession() {
        var a = ActivityData(questions = l1.take(3).associateWith { QuestionRecord("l1s1", attempts = 1, box = 1, dueDay = day) })
        a = ActivityUpdates.startReview(a, l1.take(3), emptyList(), day)
        a = ActivityUpdates.recordReviewAnswer(a, l1[0], "l1s1", correct = true, hintUsed = false, today = day, goalXp = 30)
        // Process death: everything is restored from the saved JSON.
        val restored = ActivityUpdates.decode(ActivityUpdates.encode(a))
        assertEquals(a, restored)
        assertEquals(listOf(l1[1], l1[2]), Review.remaining(restored, index).map { it.question.id })
        val info = Review.info(restored, index, day)
        assertEquals(2, info.resumeAt); assertEquals(3, info.resumeTotal)
    }

    @Test fun missingIdsInASavedSessionAreSkippedWithoutCrashing() {
        var a = ActivityData(questions = mapOf(l1[0] to QuestionRecord("l1s1", attempts = 1, box = 1, dueDay = day)))
        a = a.copy(reviewSession = ReviewSessionState(ids = listOf("removed_q", l1[0], "removed_too"), startedDay = day))
        assertEquals(listOf(l1[0]), Review.remaining(a, index).map { it.question.id })
        a = ActivityUpdates.recordReviewAnswer(a, l1[0], "l1s1", correct = true, hintUsed = false, today = day, goalXp = 30)
        assertEquals(2, a.reviewSession!!.position)
        assertTrue(Review.remaining(a, index).isEmpty())
        a = ActivityUpdates.finishReview(a, day, 30)
        assertTrue(a.reviewSession!!.finished)
        assertEquals(1, a.reviewSession!!.answered)
    }

    @Test fun oldActivityJsonWithoutReviewFieldsStillLoads() {
        val old = """{"schemaVersion":1,"questions":{"l1s1q1":{"subLevelId":"l1s1","attempts":2,"correct":1,"box":1,"dueDay":20001}},
            "days":{"20000":{"xp":12,"actions":3}},"reviewXp":0}"""
        val a = ActivityUpdates.decode(old)
        assertNull(a.reviewSession)
        assertEquals(0, a.days.getValue(20_000L).reviews)
        assertEquals(1, a.questions.getValue("l1s1q1").box)
        assertNotEquals(ActivityData(), a)
    }

    @Test fun reviewIndexCoversQuizQuestionsOfEveryType() {
        val kinds = index.values.map { it.question.kind }.toSet()
        assertEquals(QuestionType.ALL, kinds)
        assertTrue(File("src/main/assets/course/levels/l1.json").exists())
    }
}
