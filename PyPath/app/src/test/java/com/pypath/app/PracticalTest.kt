package com.pypath.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.practical.PracticalCatalogParser
import com.pypath.app.practical.availability
import com.pypath.app.ui.screens.practical.PracticalViewModel
import com.pypath.app.ui.screens.practical.autoIndent
import com.pypath.app.ui.screens.practical.dedent
import com.pypath.app.ui.screens.practical.defaultTaskId
import com.pypath.app.ui.screens.practical.indent
import com.pypath.app.ui.screens.practical.lineAndColumn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PracticalTest {
    private val catalog = PracticalCatalogParser.parse(File("src/main/assets/practical/tasks.json").readText())
    private val course = loadTestCourse()

    @Test fun everyTaskPointsAtARealSubLevel() {
        val ids = course.levels.flatMap { it.subLevels }.map { it.id }.toSet()
        catalog.tasks.forEach { t -> t.afterSubLevel?.let { assertTrue("${t.id} -> $it", it in ids) } }
        assertEquals(catalog.tasks.size, catalog.tasks.map { it.id }.toSet().size)
    }

    @Test fun freshLearnerOnlyHasPlayground() {
        val list = availability(catalog.tasks, CourseSnapshot(course, completed()))
        assertEquals(listOf("playground"), list.filter { it.unlocked }.map { it.task.id })
        assertEquals("playground", defaultTaskId(list))
    }

    @Test fun tasksUnlockWithTheirSubLevel() {
        val list = availability(catalog.tasks, CourseSnapshot(course, completed("l1s1", "l1s2", "l1s3", "l1s4")))
        val open = list.filter { it.unlocked }.map { it.task.id }
        assertTrue("p-greeting" in open)
        assertFalse("p-voting" in open) // needs 2.2 if/else
        assertEquals("p-birthday-maths", defaultTaskId(list))
        val loops = list.first { it.task.id == "p-times-table" }
        // Level 3 is released now, so the loops task is simply locked until 3.1 is done.
        assertFalse(loops.comingSoon)
        assertFalse(loops.unlocked)
    }

    @Test fun autoIndentAfterColon() {
        val old = TextFieldValue("if x:", TextRange(5))
        val typed = TextFieldValue("if x:\n", TextRange(6))
        val r = autoIndent(old, typed)
        assertEquals("if x:\n    ", r.text)
        assertEquals(TextRange(10), r.selection)
    }

    @Test fun autoIndentKeepsIndentation() {
        val old = TextFieldValue("if x:\n    a = 1", TextRange(15))
        val r = autoIndent(old, TextFieldValue("if x:\n    a = 1\n", TextRange(16)))
        assertEquals("if x:\n    a = 1\n    ", r.text)
    }

    @Test fun tabAndShiftTab() {
        val v = TextFieldValue("print(1)", TextRange(0))
        val i = indent(v)
        assertEquals("    print(1)", i.text)
        assertEquals("print(1)", dedent(i).text)
        val multi = TextFieldValue("a\nb", TextRange(0, 3))
        assertEquals("    a\n    b", indent(multi).text)
    }

    @Test fun lineColumn() {
        assertEquals(2 to 3, lineAndColumn("ab\ncd", 5))
    }

    @Test fun tracebackParsing() {
        val tb = "Traceback (most recent call last):\n  File \"main.py\", line 3, in <module>\n    print(undefined_variable)\nNameError: name 'undefined_variable' is not defined\n"
        val (summary, line) = PracticalViewModel.parseTraceback(tb)
        assertEquals("NameError: name 'undefined_variable' is not defined", summary)
        assertEquals(3, line)
        assertNotNull(PracticalViewModel.parseTraceback("  File \"main.py\", line 1\n    if True\nSyntaxError: expected ':'").second)
    }
}
