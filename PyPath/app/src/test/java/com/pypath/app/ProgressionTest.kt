package com.pypath.app

import com.pypath.app.data.content.CourseParser
import com.pypath.app.data.model.Course
import com.pypath.app.data.model.Question
import com.pypath.app.data.model.StepType
import com.pypath.app.data.progress.QuizResult
import com.pypath.app.data.progress.SubLevelProgress
import com.pypath.app.data.progress.UserProgress
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.NodeStatus
import com.pypath.app.ui.mcq.McqPhase
import com.pypath.app.ui.mcq.McqSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Reads the course index plus every level file it lists (same as AssetCourseRepository). */
fun loadTestCourse(): Course {
    val dir = File("src/main/assets/course")
    return CourseParser.parseWithLevels(File(dir, "course.json").readText()) { File(dir, it).readText() }
}

fun completed(vararg ids: String) = UserProgress(
    onboardingCompleted = true,
    subLevels = ids.associateWith {
        SubLevelProgress(setOf(StepType.LEARN, StepType.QUIZ), QuizResult(4, 4, 4, 1, true), completed = true)
    },
)

class ContentTest {
    @Test fun everyAvailableSubLevelHasLessonAndValidQuiz() {
        val course = loadTestCourse()
        course.levels.filter { it.available }.flatMap { it.subLevels }.filterNot { it.isProject }.forEach { s ->
            assertTrue("${s.id} lesson", s.lesson!!.pages.isNotEmpty())
            val q = s.quiz!!
            assertTrue("${s.id} has questions", q.questions.size >= 3)
            // Batch 3 Part 2: the MCQ rules apply to MCQs; predict/fill/order are checked in QuestionTypesTest.
            q.questions.filter { it.isMcq }.forEach {
                assertEquals("${it.id} options", 4, it.options.size)
                assertTrue("${it.id} correctIndex", it.correctIndex in 0..3)
            }
        }
        val ids = course.levels.flatMap { l -> l.subLevels.map { it.id } }
        assertEquals("unique ids", ids.size, ids.toSet().size)
    }
}

class ProgressionTest {
    private val course = loadTestCourse()

    @Test fun freshUserOnlyFirstSubLevelUnlocked() {
        val snap = CourseSnapshot(course, UserProgress())
        assertEquals(NodeStatus.AVAILABLE, snap.subLevel("l1s1")!!.status)
        assertEquals(NodeStatus.LOCKED, snap.subLevel("l1s2")!!.status)
        assertEquals(NodeStatus.LOCKED, snap.level("l2")!!.status)
        assertEquals(NodeStatus.LOCKED, snap.level("l3")!!.status)
        assertEquals(NodeStatus.LOCKED, snap.level("l7")!!.status)
        assertEquals("l1s1", snap.continueTarget!!.subLevel.id)
        assertEquals(0f, snap.overallFraction)
    }

    @Test fun lessonOnlyIsInProgressNotComplete() {
        val p = UserProgress(subLevels = mapOf("l1s1" to SubLevelProgress(completedSteps = setOf(StepType.LEARN))))
        val snap = CourseSnapshot(course, p)
        assertEquals(NodeStatus.IN_PROGRESS, snap.subLevel("l1s1")!!.status)
        assertEquals(NodeStatus.LOCKED, snap.subLevel("l1s2")!!.status)
    }

    @Test fun completingUnlocksNextAcrossLevels() {
        val snap = CourseSnapshot(course, completed("l1s1", "l1s2", "l1s3", "l1s4"))
        assertEquals(NodeStatus.COMPLETED, snap.level("l1")!!.status)
        assertEquals(NodeStatus.AVAILABLE, snap.subLevel("l2s1")!!.status)
        assertEquals("l2s1", snap.continueTarget!!.subLevel.id)
    }

    @Test fun allAvailableDone() {
        val all = course.levels.flatMap { l -> l.subLevels.map { it.id } }.toTypedArray()
        val snap = CourseSnapshot(course, completed(*all))
        assertNull(snap.continueTarget)
        assertTrue(snap.allAvailableComplete)
        assertEquals(1f, snap.overallFraction)
    }
}

class McqSessionTest {
    private val qs: List<Question> = List(4) { i -> Question(id = "q$i", prompt = "Q$i", options = listOf("a", "b", "c", "d"), correctIndex = 1, explanation = "") }

    @Test fun scoringAndPassing() {
        val s = McqSession(qs, passPercent = 60)
        repeat(4) { i ->
            assertEquals(McqPhase.ANSWERING, s.phase)
            s.check() // no selection → ignored
            assertEquals(McqPhase.ANSWERING, s.phase)
            s.select(if (i < 3) 1 else 0)
            s.check()
            assertEquals(McqPhase.FEEDBACK, s.phase)
            s.select(2) // locked after checking
            s.next()
        }
        assertEquals(McqPhase.FINISHED, s.phase)
        assertEquals(3, s.score)
        assertEquals(75, s.percent)
        assertTrue(s.passed)
        assertEquals(1, s.attempt)
    }

    @Test fun failingAndRestart() {
        val s = McqSession(qs, passPercent = 60)
        repeat(4) { s.select(0); s.check(); s.next() }
        assertFalse(s.passed)
        s.restart()
        assertEquals(0, s.index); assertEquals(0, s.answers.size); assertEquals(McqPhase.ANSWERING, s.phase)
    }
}
