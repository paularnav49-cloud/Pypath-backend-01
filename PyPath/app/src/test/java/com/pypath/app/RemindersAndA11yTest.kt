package com.pypath.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.pypath.app.data.settings.ReminderPrefs
import com.pypath.app.domain.A11y
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.Layouts
import com.pypath.app.domain.Reminders
import com.pypath.app.ui.theme.AppColors
import com.pypath.app.ui.theme.DarkAppColors
import com.pypath.app.ui.theme.LightAppColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Batch 3 Part 3: reminders, screen-reader text, tablet layout rules and WCAG contrast. */
class RemindersAndA11yTest {

    // ───────────── "Should send today" ─────────────

    private val today = 20_000L

    @Test fun sendsWhenOnAllowedNotSentAndGoalNotMet() {
        assertTrue(Reminders.shouldSend(enabled = true, notificationsAllowed = true, lastSentDay = null, today = today, goalMetToday = false))
        assertTrue(Reminders.shouldSend(true, true, lastSentDay = today - 1, today = today, goalMetToday = false))
    }

    @Test fun neverSendsWhenOff() {
        assertFalse(Reminders.shouldSend(enabled = false, notificationsAllowed = true, lastSentDay = null, today = today, goalMetToday = false))
    }

    @Test fun neverSendsWithoutNotificationPermission() {
        assertFalse(Reminders.shouldSend(true, notificationsAllowed = false, lastSentDay = null, today = today, goalMetToday = false))
    }

    @Test fun atMostOnePerDay() {
        assertFalse(Reminders.shouldSend(true, true, lastSentDay = today, today = today, goalMetToday = false))
    }

    @Test fun notSentWhenTodaysGoalIsMet() {
        assertFalse(Reminders.shouldSend(true, true, lastSentDay = null, today = today, goalMetToday = true))
    }

    @Test fun reminderIsOffByDefaultAt19() {
        val p = ReminderPrefs()
        assertFalse(p.enabled)
        assertEquals(19 * 60, p.minutes)
        assertNull(p.lastSentDay)
        assertEquals("19:00", Reminders.label(Reminders.DEFAULT_MINUTES))
        assertEquals("07:05", Reminders.label(7 * 60 + 5))
    }

    @Test fun minutesAreClamped() {
        assertEquals(0, Reminders.clampMinutes(-5))
        assertEquals(1439, Reminders.clampMinutes(5000))
    }

    // ───────────── Next trigger time ─────────────

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, z: ZoneId = zone) = ZonedDateTime.of(LocalDateTime.of(y, mo, d, h, mi), z)

    @Test fun nextTriggerIsLaterToday() {
        assertEquals(at(2026, 10, 12, 19, 0), Reminders.nextTrigger(at(2026, 10, 12, 8, 30), 19 * 60))
    }

    @Test fun nextTriggerIsTomorrowWhenTimePassedOrNow() {
        assertEquals(at(2026, 10, 13, 19, 0), Reminders.nextTrigger(at(2026, 10, 12, 20, 0), 19 * 60))
        assertEquals(at(2026, 10, 13, 19, 0), Reminders.nextTrigger(at(2026, 10, 12, 19, 0), 19 * 60))
    }

    @Test fun nextTriggerKeepsClockTimeAcrossDaylightSaving() {
        val berlin = ZoneId.of("Europe/Berlin")
        // Clocks go forward on 29 March 2026 and back on 25 October 2026.
        val spring = Reminders.nextTrigger(at(2026, 3, 28, 20, 0, berlin), 19 * 60)
        assertEquals(at(2026, 3, 29, 19, 0, berlin), spring)
        val autumn = Reminders.nextTrigger(at(2026, 10, 24, 22, 0, berlin), 19 * 60)
        assertEquals(19, autumn.hour)
        assertEquals(25, autumn.dayOfMonth)
    }

    @Test fun nextTriggerMillisMatchesZonedTime() {
        val now = at(2026, 10, 12, 8, 0)
        assertEquals(
            at(2026, 10, 12, 19, 0).toInstant().toEpochMilli(),
            Reminders.nextTriggerMillis(now.toInstant().toEpochMilli(), zone, 19 * 60),
        )
    }

    // ───────────── Messages ─────────────

    @Test fun sixToEightRotatingMessages() {
        assertTrue(Reminders.messageCount in 6..8)
        val plain = (0 until Reminders.messageCount).map { Reminders.message(today + it, streak = 0) }
        assertEquals(Reminders.messageCount, plain.toSet().size)
        // Rotates: the cycle repeats after messageCount days.
        assertEquals(Reminders.message(today, 0), Reminders.message(today + Reminders.messageCount, 0))
    }

    @Test fun messagesMentionTheStreakWhenThereIsOne() {
        for (d in 0 until Reminders.messageCount) {
            val m = Reminders.message(today + d, streak = 12)
            assertTrue("${m.title} ${m.text}", ("${m.title} ${m.text}").contains("12") || ("${m.title} ${m.text}").contains("13"))
            val plain = Reminders.message(today + d, streak = 0)
            assertFalse(plain.title.contains("streak", ignoreCase = true) || plain.text.contains("streak", ignoreCase = true))
        }
    }

    // ───────────── Screen-reader text ─────────────

    @Test fun codeIsReadLineByLineWithIndentation() {
        val spoken = A11y.describeCode("for i in range(3):\n    print(i)\n\n        x = 1\n", output = "0\n1\n2")
        assertEquals(
            "Code, 4 lines. Line 1: for i in range(3):. Line 2, indented 1 level: print(i). Line 3: blank. " +
                "Line 4, indented 2 levels: x = 1. Output: 0\n1\n2.",
            spoken,
        )
        assertEquals(1, A11y.indentLevel("\tx"))
        assertEquals(1, A11y.indentLevel("  x")) // partial levels round up
        assertEquals(0, A11y.indentLevel("x"))
    }

    @Test fun levelCardAndBadgeDescriptions() {
        assertEquals(
            "Level 2, Decisions. In progress. 1 of 4 sub-levels done.",
            A11y.levelCard(2, "Decisions", "In progress", 1, 4, locked = false, unlockHint = null),
        )
        assertEquals(
            "Level 3, Loops. Locked. Complete Level 2 to unlock.",
            A11y.levelCard(3, "Loops", "Not started", 0, 4, locked = true, unlockHint = "Complete Level 2 to unlock"),
        )
        assertEquals("Loop Master badge, earned", A11y.badge("Loop Master", true))
        assertEquals("Loop Master badge, locked", A11y.badge("Loop Master", false))
    }

    // ───────────── Tablet layout rules ─────────────

    @Test fun phoneLayoutBelow600dp() {
        assertFalse(Layouts.isTablet(411))
        assertFalse(Layouts.isTablet(599))
        assertTrue(Layouts.isTablet(600))
        assertEquals(1, Layouts.dashboardColumns(599))
        assertEquals(2, Layouts.dashboardColumns(800))
        assertFalse(Layouts.isTwoPane(700))
        assertTrue(Layouts.isTwoPane(720))
    }

    @Test fun twoPaneDefaultsToTheSubLevelInProgressOrNextAvailable() {
        val course = loadTestCourse()
        val fresh = CourseSnapshot(course, completed())
        val l1 = fresh.levels.first()
        assertEquals(l1.subLevels.first().subLevel.id, Layouts.defaultSubLevel(l1))
        val after = CourseSnapshot(course, completed("l1s1"))
        assertEquals("l1s2", Layouts.defaultSubLevel(after.levels.first()))
        // A locked level has nothing to show.
        assertNull(Layouts.defaultSubLevel(fresh.levels[1]))
    }

    // ───────────── WCAG AA contrast ─────────────

    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    private fun check(name: String, fg: Color, bg: Color, need: Double, failures: MutableList<String>) {
        val r = ratio(if (fg.alpha < 1f) fg.compositeOver(bg) else fg, bg)
        if (r + 1e-6 < need) failures += "$name: ${"%.2f".format(r)} < $need"
    }

    private fun themePairs(t: AppColors, label: String, f: MutableList<String>) {
        val text = 4.5
        val graphic = 3.0
        listOf(t.background, t.surface, t.surfaceAlt).forEachIndexed { i, bg ->
            val n = listOf("background", "surface", "surfaceAlt")[i]
            check("$label text/$n", t.text, bg, text, f)
            check("$label textMuted/$n", t.textMuted, bg, text, f)
            check("$label textFaint/$n", t.textFaint, bg, text, f)
            check("$label locked icon/$n", t.locked, bg, graphic, f)
        }
        check("$label brand/surface", t.brand, t.surface, text, f)
        check("$label brand/background", t.brand, t.background, text, f)
        check("$label brand/brandSoft", t.brand, t.brandSoft, text, f)
        check("$label onBrand/brand", t.onBrand, t.brand, text, f)
        check("$label success/surface", t.success, t.surface, text, f)
        check("$label success/successSoft", t.success, t.successSoft, text, f)
        check("$label onSuccess/success", t.onSuccess, t.success, text, f)
        check("$label error/surface", t.error, t.surface, text, f)
        check("$label error/errorSoft", t.error, t.errorSoft, text, f)
        check("$label onError/error", t.onError, t.error, text, f)
        check("$label text/successSoft", t.text, t.successSoft, text, f)
        check("$label text/errorSoft", t.text, t.errorSoft, text, f)
        check("$label text/brandSoft", t.text, t.brandSoft, text, f)
        val code = t.code
        listOf(code.text, code.keyword, code.builtin, code.string, code.number, code.comment, code.function, code.operator)
            .forEachIndexed { i, fg -> check("$label code[$i]/code.background", fg, code.background, text, f) }
        check("$label comment/code.header", code.comment, code.header, text, f)
        check("$label outputText/outputBg", code.outputText, code.outputBg, text, f)
        check("$label comment/outputBg", code.comment, code.outputBg, text, f)
    }

    @Test fun themeColorsMeetWcagAa() {
        val failures = mutableListOf<String>()
        themePairs(LightAppColors, "light", failures)
        themePairs(DarkAppColors, "dark", failures)
        // Dashboard hero card: translucent white on the fixed blue gradient.
        for (bg in listOf(Color(0xFF3D5AFE), Color(0xFF2335C8))) {
            check("hero label", Color.White.copy(alpha = 0.92f), bg, 4.5, failures)
            check("hero accent ring", Color(0xFFFFC83D), bg, 3.0, failures)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
