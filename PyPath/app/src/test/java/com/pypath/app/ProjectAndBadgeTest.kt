package com.pypath.app

import com.pypath.app.data.model.StepType
import com.pypath.app.data.progress.ProgressUpdates
import com.pypath.app.data.progress.UserProgress
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.NodeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProjectTest {
    private val course = loadTestCourse()
    // Batch 1: advanced Levels 8-11 follow the final project, so "the course" here means the core levels.
    private val core = course.levels.filterNot { it.advanced }
    private val project = core.last().subLevels.single()
    private val stageIds = project.stages.map { it.id }
    private val beforeProject = core.dropLast(1).flatMap { l -> l.subLevels.map { it.id } }.toTypedArray()

    @Test fun projectShape() {
        assertTrue(project.isProject)
        assertEquals(listOf(StepType.PROJECT), project.steps)
        assertTrue(project.stages.size in 8..14)
        assertNotNull(project.briefing)
        assertTrue(project.briefing!!.sampleOutput.isNotBlank())
        project.stages.forEach { assertEquals("${it.id} feedback", 4, it.optionFeedback.size) }
        val ids = course.levels.flatMap { l -> l.subLevels.map { it.id } }.toSet()
        project.briefing!!.concepts.forEach { assertTrue(it.subLevelId, it.subLevelId in ids) }
    }

    @Test fun revealedLinesRebuildFinalCodeExactly() {
        val lines = arrayOfNulls<String>(project.finalCode.size)
        project.stages.forEach { st ->
            st.revealsLines.forEach { n ->
                assertNull("line $n revealed twice", lines[n - 1])
                lines[n - 1] = project.finalCode[n - 1]
            }
        }
        assertEquals(project.finalCode, lines.toList())
        // Code options must match the code they reveal (ignoring indentation).
        project.stages.filter { it.optionsAreCode }.forEach { st ->
            val right = st.question.options[st.question.correctIndex].lines().map { it.trim() }
            val revealed = st.revealsLines.map { project.finalCode[it - 1].trim() }
            assertTrue(st.id, revealed.containsAll(right))
        }
    }

    @Test fun projectUnlocksOnlyAfterLevel6() {
        val almost = beforeProject.dropLast(1).toTypedArray()
        assertEquals(NodeStatus.LOCKED, CourseSnapshot(course, completed(*almost)).subLevel("l7s1")!!.status)
        assertEquals(NodeStatus.AVAILABLE, CourseSnapshot(course, completed(*beforeProject)).subLevel("l7s1")!!.status)
    }

    @Test fun progressionAndResume() {
        var p = completed(*beforeProject).copy(earnedBadges = setOf("l1", "l2", "l3", "l4", "l5", "l6"))
        p = ProgressUpdates.markBriefingSeen(p, "l7s1")
        assertEquals(NodeStatus.IN_PROGRESS, CourseSnapshot(course, p).subLevel("l7s1")!!.status)
        p = ProgressUpdates.recordProjectMiss(p, "l7s1")
        stageIds.take(4).forEach { p = ProgressUpdates.completeProjectStage(p, "l7s1", it, stageIds, project.steps, 1L) }
        // Same stage twice is ignored.
        p = ProgressUpdates.completeProjectStage(p, "l7s1", stageIds[0], stageIds, project.steps, 1L)

        // Survives a save/load round trip (resume after closing the app).
        val reloaded = ProgressUpdates.decode(ProgressUpdates.encode(p))
        val pr = reloaded.of("l7s1").project!!
        assertTrue(pr.briefingSeen)
        assertEquals(stageIds.take(4), pr.completedStages)
        assertEquals(1, pr.wrongAttempts)
        assertFalse(reloaded.of("l7s1").completed)
        assertEquals(stageIds[4], stageIds.first { it !in pr.completedStages })

        var q = reloaded
        stageIds.drop(4).forEach { q = ProgressUpdates.completeProjectStage(q, "l7s1", it, stageIds, project.steps, 2L) }
        assertTrue(q.of("l7s1").completed)
        assertTrue(StepType.PROJECT in q.of("l7s1").completedSteps)
        val snap = CourseSnapshot(course, q)
        assertEquals(NodeStatus.COMPLETED, snap.level("l7")!!.status)
        // Batch 1 added advanced Levels 8-11 after the final project, so finishing L7 completes the
        // core course (certificate), not every available level.
        assertTrue(snap.certificateUnlocked)
        assertFalse(snap.allAvailableComplete)
        assertEquals("l7", snap.pendingBadgeLevel?.level?.id)
    }

    // ───── v0.4.0: level projects ─────

    private fun finishProject(p: UserProgress, id: String): UserProgress {
        val sub = CourseSnapshot(course, p).subLevel(id)!!.subLevel
        val ids = sub.stages.map { it.id }
        var q = ProgressUpdates.markBriefingSeen(p, id)
        ids.forEach { q = ProgressUpdates.completeProjectStage(q, id, it, ids, sub.steps, 5L) }
        return q
    }

    @Test fun everyLevelBeforeTheFinalHasOneLevelProject() {
        val subIds = course.levels.flatMap { l -> l.subLevels.map { it.id } }.toSet()
        core.dropLast(1).forEach { lvl ->
            val bp = lvl.projects.single()
            assertEquals("${lvl.id}p1", bp.id)
            assertEquals("${lvl.number}.P", bp.code)
            assertTrue(bp.isProject)
            assertEquals(listOf(StepType.PROJECT), bp.steps)
            assertTrue(bp.id, bp.stages.size in 6..10)
            val b = bp.briefing!!
            assertTrue(b.fileName.endsWith(".py"))
            assertTrue(b.sampleOutput.isNotBlank())
            assertEquals(bp.id, b.usesInput, b.sampleInputs.isNotEmpty())
            assertEquals(bp.id, b.usesInput, bp.finalCode.any { "input(" in it })
            b.concepts.forEach {
                assertTrue(it.subLevelId, it.subLevelId in subIds)
                assertTrue("${bp.id} uses a later level", it.subLevelId[1].digitToInt() <= lvl.number)
            }
            val lines = arrayOfNulls<String>(bp.finalCode.size)
            bp.stages.forEach { st -> st.revealsLines.forEach { n -> assertNull(lines[n - 1]); lines[n - 1] = bp.finalCode[n - 1] } }
            assertEquals(bp.id, bp.finalCode, lines.toList())
        }
        assertTrue(core.last().projects.isEmpty())
        assertTrue(course.levels.filter { it.advanced }.all { it.projects.isEmpty() })
        assertEquals("expense_tracker.py", project.briefing!!.fileName)
    }

    @Test fun levelProjectUnlocksOnlyWhenItsLevelIsFinished() {
        val l1 = course.levels[0].subLevels.map { it.id }
        assertEquals(NodeStatus.LOCKED, CourseSnapshot(course, completed()).subLevel("l1p1")!!.status)
        assertEquals(NodeStatus.LOCKED, CourseSnapshot(course, completed(*l1.dropLast(1).toTypedArray())).subLevel("l1p1")!!.status)
        val snap = CourseSnapshot(course, completed(*l1.toTypedArray()))
        assertEquals(NodeStatus.AVAILABLE, snap.subLevel("l1p1")!!.status)
        assertTrue(snap.subLevel("l1p1")!!.isBonus)
        assertEquals(NodeStatus.LOCKED, snap.subLevel("l2p1")!!.status)
        assertTrue(snap.level("l1")!!.cheatSheetUnlocked)
        assertFalse(snap.level("l2")!!.cheatSheetUnlocked)
    }

    @Test fun levelProjectsDoNotChangeTotalsBadgesOrContinue() {
        val l1 = course.levels[0].subLevels.map { it.id }.toTypedArray()
        val before = CourseSnapshot(course, completed(*l1))
        // Opening the level project would set it as "current"; Continue must still follow the main path.
        var p = completed(*l1).copy(currentSubLevelId = "l1p1")
        p = finishProject(p, "l1p1")
        val after = CourseSnapshot(course, p)
        assertTrue(p.of("l1p1").completed)
        assertEquals(NodeStatus.COMPLETED, after.subLevel("l1p1")!!.status)
        assertEquals(before.playableTotal, after.playableTotal)
        assertEquals(before.completedTotal, after.completedTotal)
        assertEquals("l2s1", after.current?.subLevel?.id)
        assertTrue(after.orderedSubLevels.none { it.isBonus })
        assertEquals(6, after.bonusProjects.size)
        assertEquals(7, after.allProjects.size)
        assertEquals("l7s1", after.allProjects.last().subLevel.id)
        // Skipping every level project still completes the course.
        val all = course.levels.flatMap { l -> l.subLevels.map { it.id } }.toTypedArray()
        assertTrue(CourseSnapshot(course, completed(*all)).allAvailableComplete)
    }

    @Test fun certificateUnlocksAfterLevel7AndHasAStableId() {
        assertFalse(CourseSnapshot(course, completed(*beforeProject)).certificateUnlocked)
        assertNull(com.pypath.app.domain.Certificates.info(CourseSnapshot(course, completed(*beforeProject)), "Asha"))
        val done = finishProject(completed(*beforeProject), "l7s1")
        val snap = CourseSnapshot(course, done)
        assertTrue(snap.certificateUnlocked)
        val info = com.pypath.app.domain.Certificates.info(snap, "  Asha   Rao ")!!
        assertEquals("Asha Rao", info.name)
        assertEquals(course.title, info.courseTitle)
        assertEquals(7, info.levels)
        assertEquals(1, info.projects)
        assertEquals(5L, info.completedAt)
        assertTrue(info.id, Regex("PP-[0-9A-F]{4}-[0-9A-F]{4}").matches(info.id))
        assertEquals(info.id, com.pypath.app.domain.Certificates.certificateId("asha rao", 5L))
        assertFalse(info.id == com.pypath.app.domain.Certificates.certificateId("Asha Rai", 5L))
        assertEquals(40, com.pypath.app.domain.Certificates.cleanName("x".repeat(60)).length)
    }
}

class BadgeTest {
    private val course = loadTestCourse()
    private val level1 = arrayOf("l1s1", "l1s2", "l1s3", "l1s4")

    @Test fun pendingUntilEarnedThenNeverAgain() {
        var p = completed(*level1)
        assertEquals("l1", CourseSnapshot(course, p).pendingBadgeLevel?.level?.id)
        p = ProgressUpdates.earnBadge(p, "l1")
        val snap = CourseSnapshot(course, p)
        assertNull(snap.pendingBadgeLevel)
        assertTrue(snap.level("l1")!!.badgeEarned)
        assertEquals(p, ProgressUpdates.earnBadge(p, "l1")) // idempotent
    }

    @Test fun badgesPersist() {
        val p = ProgressUpdates.earnBadge(ProgressUpdates.earnBadge(completed(*level1), "l1"), "l2")
        assertEquals(setOf("l1", "l2"), ProgressUpdates.decode(ProgressUpdates.encode(p)).earnedBadges)
    }

    @Test fun resetClearsBadgesButKeepsOnboarding() {
        val p = ProgressUpdates.reset(ProgressUpdates.earnBadge(completed(*level1), "l1"))
        assertTrue(p.earnedBadges.isEmpty())
        assertTrue(p.subLevels.isEmpty())
        assertTrue(p.onboardingCompleted)
    }

    @Test fun noBadgeForUnfinishedLevel() {
        assertNull(CourseSnapshot(course, completed("l1s1", "l1s2", "l1s3")).pendingBadgeLevel)
    }
}

class OldProgressTest {
    private val course = loadTestCourse()

    /** Exactly what version 1 of the app saved (schema 1, no badges, no project field). */
    private val v1Json = """
        {"schemaVersion":1,"onboardingCompleted":true,"currentSubLevelId":"l2s1",
         "subLevels":{
           "l1s1":{"completedSteps":["learn","quiz"],"quiz":{"lastScore":4,"bestScore":4,"total":4,"attempts":1,"passed":true},"completed":true,"completedAt":1,"lastOpenedAt":1},
           "l1s2":{"completedSteps":["learn","quiz"],"quiz":{"lastScore":3,"bestScore":4,"total":4,"attempts":2,"passed":true},"completed":true,"completedAt":2,"lastOpenedAt":2},
           "l1s3":{"completedSteps":["learn","quiz"],"quiz":{"lastScore":4,"bestScore":4,"total":4,"attempts":1,"passed":true},"completed":true,"completedAt":3,"lastOpenedAt":3},
           "l1s4":{"completedSteps":["learn","quiz"],"quiz":{"lastScore":4,"bestScore":4,"total":4,"attempts":1,"passed":true},"completed":true,"completedAt":4,"lastOpenedAt":4},
           "l2s1":{"completedSteps":["learn"],"quiz":null,"completed":false,"completedAt":null,"lastOpenedAt":5}
         }}
    """.trimIndent()

    @Test fun v1JsonStillLoads() {
        val p = ProgressUpdates.decode(v1Json)
        assertEquals(1, p.schemaVersion)
        assertTrue(p.onboardingCompleted)
        assertTrue(p.earnedBadges.isEmpty())
        assertNull(p.of("l2s1").project)
        val snap = CourseSnapshot(course, p)
        assertEquals(NodeStatus.COMPLETED, snap.level("l1")!!.status)
        assertEquals(NodeStatus.IN_PROGRESS, snap.subLevel("l2s1")!!.status)
        assertNull("no celebration burst before migration", snap.pendingBadgeLevel)
    }

    @Test fun migrationGrantsBadgesSilently() {
        val m = ProgressUpdates.migrate(ProgressUpdates.decode(v1Json), course)
        assertEquals(UserProgress.CURRENT_SCHEMA, m.schemaVersion)
        assertEquals(setOf("l1"), m.earnedBadges)
        assertNull(CourseSnapshot(course, m).pendingBadgeLevel)
        assertEquals(m, ProgressUpdates.migrate(m, course)) // runs once
        assertEquals("l2s1", m.currentSubLevelId)
    }

    @Test fun garbageFallsBackToFreshProgress() {
        assertEquals(UserProgress(), ProgressUpdates.decode("not json"))
        assertEquals(UserProgress(), ProgressUpdates.decode(null))
    }

    @Test fun soundFilesAreSmallWhenPresent() {
        File("src/main/res/raw").listFiles { f -> f.extension == "wav" }?.forEach {
            assertTrue("${it.name} ${it.length()}", it.length() < 150 * 1024)
        }
    }
}
