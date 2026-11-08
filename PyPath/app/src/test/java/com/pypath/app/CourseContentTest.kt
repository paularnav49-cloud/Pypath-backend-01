package com.pypath.app

import com.pypath.app.data.content.CourseParser
import com.pypath.app.data.model.LessonBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the generated JSON in assets/course against the course rules. */
class CourseContentTest {
    private val course = loadTestCourse()
    private val subs = course.levels.flatMap { it.subLevels }
    private val lessons = subs.filterNot { it.isProject }
    private val newLessons = course.levels.filter { it.number >= 3 }.flatMap { it.subLevels }.filterNot { it.isProject }

    @Test fun indexListsElevenLevelFilesThatAllParse() {
        val dir = File("src/main/assets/course")
        val index = CourseParser.parse(File(dir, "course.json").readText())
        assertEquals(2, index.schemaVersion)
        assertEquals(11, index.levelFiles.size) // Levels 1-7 + Batch 1 advanced Levels 8-11
        index.levelFiles.forEach { assertNotNull(it, CourseParser.parseLevel(File(dir, it).readText())) }
        assertEquals((1..11).toList(), course.levels.map { it.number })
        assertTrue("every level is available", course.levels.all { it.available })
    }

    @Test fun idsAreUniqueEverywhere() {
        val ids = course.levels.map { it.id } + subs.map { it.id } +
            lessons.flatMap { s -> s.quiz!!.questions.map { it.id } } +
            subs.flatMap { s -> s.stages.flatMap { listOf(it.id, it.question.id) } }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun questionIdsFollowThePattern() {
        newLessons.forEach { s ->
            s.quiz!!.questions.forEachIndexed { i, q -> assertEquals("${s.id}q${i + 1}", q.id) }
        }
    }

    @Test fun everyQuestionHasFourOptionsAndAValidAnswer() {
        // Batch 3 Part 2: MCQs only (predict/fill/order questions are checked in QuestionTypesTest).
        val all = lessons.flatMap { it.quiz!!.questions.filter { q -> q.isMcq } } + subs.flatMap { s -> s.stages.map { it.question } }
        all.forEach {
            assertEquals("${it.id} options", 4, it.options.size)
            assertEquals("${it.id} distinct options", 4, it.options.toSet().size)
            assertTrue("${it.id} correctIndex", it.correctIndex in 0..3)
            assertTrue("${it.id} explanation", it.explanation.isNotBlank())
        }
    }

    @Test fun practicalContractIdsStillExistWithTheirMeaning() {
        listOf("l1s1", "l1s2", "l1s3", "l1s4", "l2s1", "l2s2", "l2s3", "l3s1", "l3s2", "l5s3").forEach { id ->
            assertTrue(id, subs.any { it.id == id })
        }
        assertEquals("for loops", subs.first { it.id == "l3s1" }.title)
        assertEquals("while loops", subs.first { it.id == "l3s2" }.title)
        assertEquals("List methods", subs.first { it.id == "l5s3" }.title)
    }

    @Test fun pageAndQuestionCountsVary() {
        val pages = newLessons.map { it.lesson!!.pages.size }
        val mcqs = newLessons.map { s -> s.quiz!!.questions.count { it.isMcq } }
        assertTrue(pages.all { it in 2..6 })
        assertTrue(mcqs.all { it in 3..8 })
        assertTrue("page counts not all equal", pages.toSet().size > 1)
        assertTrue("MCQ counts not all equal", mcqs.toSet().size > 1)
        assertFalse("consecutive page counts", pages.zipWithNext().any { (a, b) -> a == b })
        assertFalse("consecutive MCQ counts", mcqs.zipWithNext().any { (a, b) -> a == b })
        assertTrue("sub-levels per level vary", course.levels.map { it.subLevels.size }.toSet().size > 1)
    }

    @Test fun correctAnswersAreSpreadOut() {
        newLessons.forEach { s ->
            val pos = s.quiz!!.questions.filter { it.isMcq }.map { it.correctIndex }
            assertFalse("${s.id} three in a row: $pos", pos.windowed(3).any { it.toSet().size == 1 })
            val counts = (0..3).map { k -> pos.count { it == k } }
            assertTrue("${s.id} balanced: $pos", counts.max() - counts.min() <= 1)
            assertTrue("${s.id} passPercent", s.quiz!!.passPercent == 60)
        }
    }

    @Test fun everyNewLessonHasCodeAndACommonMistakeTip() {
        newLessons.forEach { s ->
            val blocks = s.lesson!!.pages.flatMap { it.blocks }
            assertTrue("${s.id} code with output", blocks.any { it is LessonBlock.Code && it.output != null })
            assertTrue("${s.id} common mistake", blocks.any { it is LessonBlock.Tip && it.title == "Common mistake" })
        }
    }

    // ───── v0.4.0: glossary and cheat sheets ─────

    @Test fun glossaryLoadsAndLinksToRealSubLevels() {
        val ids = subs.associateBy { it.id }
        assertTrue(course.glossary.size >= 50)
        assertEquals(course.glossary.size, course.glossary.map { it.term.lowercase() }.toSet().size)
        course.glossary.forEach { t ->
            assertTrue(t.term, t.subLevelId in ids)
            assertEquals(t.term, Regex("l(\\d+)s\\d+").find(t.subLevelId)!!.groupValues[1].toInt(), t.level)
            assertTrue(t.term, t.definition.isNotBlank())
        }
        (1..11).forEach { n -> assertTrue("no terms for level $n", course.glossary.any { it.level == n }) }
    }

    @Test fun glossarySearchRanksAndFilters() {
        val terms = course.glossary
        val all = com.pypath.app.domain.GlossarySearch.filter(terms, "")
        assertEquals(terms.size, all.size)
        assertEquals(all.map { com.pypath.app.domain.GlossarySearch.sortKey(it.term) }.sorted(),
            all.map { com.pypath.app.domain.GlossarySearch.sortKey(it.term) })
        val list = com.pypath.app.domain.GlossarySearch.filter(terms, "  LIST ")
        assertTrue(list.isNotEmpty())
        assertTrue(list.first().term, list.first().term.lowercase().trimStart { !it.isLetterOrDigit() }.startsWith("list"))
        val l5 = com.pypath.app.domain.GlossarySearch.filter(terms, "", level = 5)
        assertTrue(l5.isNotEmpty() && l5.all { it.level == 5 })
        assertTrue(com.pypath.app.domain.GlossarySearch.filter(terms, "zzzqqq").isEmpty())
    }

    @Test fun missingGlossaryDoesNotBreakTheCourse() {
        val dir = File("src/main/assets/course")
        val c = CourseParser.parseWithLevels(File(dir, "course.json").readText()) { name ->
            if (name == "glossary.json") error("missing") else File(dir, name).readText()
        }
        assertEquals(11, c.levels.size)
        assertTrue(c.glossary.isEmpty())
    }

    @Test fun everyLevelHasACheatSheet() {
        course.levels.forEach { lvl ->
            val sheet = lvl.cheatSheet
            assertNotNull(lvl.id, sheet)
            assertTrue(lvl.id, sheet!!.sections.isNotEmpty() && sheet.sections.all { it.items.isNotEmpty() })
            assertTrue(lvl.id, sheet.remember.isNotEmpty())
            val text = com.pypath.app.ui.screens.reference.asText(lvl.number, sheet)
            assertTrue(text.startsWith("PyPath - Level ${lvl.number} cheat sheet: ${sheet.title}"))
            assertTrue(text.contains(sheet.sections.first().items.first().code))
        }
    }

    // ───── Batch 1: hints, option feedback and the advanced levels ─────

    @Test fun everyLessonQuestionHasAHintAndFeedbackForEachOption() {
        val questions = lessons.flatMap { s -> s.quiz!!.questions.filter { it.isMcq } }
        assertEquals(207, questions.size) // 125 existing + 82 in Levels 8-11 (Batch 3 Part 2 types are not MCQs)
        questions.forEach { q ->
            val hint = q.hint
            assertTrue("${q.id} hint", !hint.isNullOrBlank() && hint.split(" ").size in 6..20)
            assertFalse("${q.id} hint gives the answer away", hint!!.contains(q.options[q.correctIndex]))
            assertEquals("${q.id} feedback", 4, q.optionFeedback?.size)
            assertTrue("${q.id} feedback aligned", q.optionFeedback!![q.correctIndex].startsWith("Right:"))
            (0..3).filter { it != q.correctIndex }.forEach { i -> assertNotNull(q.feedbackFor(i)) }
        }
        assertEquals("hints are unique", questions.size, questions.map { it.hint }.toSet().size)
    }

    @Test fun advancedLevelsComeAfterTheFinalProject() {
        val core = course.levels.filterNot { it.advanced }
        val advanced = course.levels.filter { it.advanced }
        assertEquals((1..7).toList(), core.map { it.number })
        assertEquals((8..11).toList(), advanced.map { it.number })
        advanced.forEach { lvl ->
            assertTrue(lvl.title, lvl.title.endsWith("(Advanced)"))
            assertTrue(lvl.id, lvl.available && lvl.projects.isEmpty())
            lvl.subLevels.forEachIndexed { i, s -> assertEquals("${lvl.id}s${i + 1}", s.id) }
        }
        val counts = course.levels.map { it.subLevels.size }.drop(6) // Level 7 and the advanced levels
        assertFalse("advanced sub-level counts", counts.zipWithNext().any { (a, b) -> a == b })
        assertEquals(advanced.size, advanced.map { it.subLevels.size }.toSet().size)
    }

    @Test fun questionWithoutBatch1FieldsStillParses() {
        val q = com.pypath.app.data.model.Question(id = "x", prompt = "p", options = listOf("a", "b", "c", "d"), correctIndex = 0, explanation = "e")
        assertEquals(null, q.hint)
        assertEquals(null, q.feedbackFor(1))
        val short = q.copy(optionFeedback = listOf("only one"))
        assertEquals("feedback ignored unless one per option", null, short.feedbackFor(0))
    }
}
