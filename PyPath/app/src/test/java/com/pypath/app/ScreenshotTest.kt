package com.pypath.app

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.pypath.app.data.model.StepType
import com.pypath.app.data.progress.QuizResult
import com.pypath.app.data.progress.SubLevelProgress
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.ui.mcq.McqQuestionCard
import com.pypath.app.ui.mcq.McqSession
import com.pypath.app.ui.screens.completion.CompletionScreen
import com.pypath.app.ui.screens.dashboard.DashboardScreen
import com.pypath.app.ui.screens.lesson.LessonScreen
import com.pypath.app.ui.screens.level.LevelScreen
import com.pypath.app.ui.screens.onboarding.OnboardingScreen
import com.pypath.app.ui.screens.sublevel.SubLevelScreen
import com.pypath.app.ui.theme.AppTheme
import com.pypath.app.ui.theme.PyPathTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/** Renders key screens to PNG for visual review (./gradlew recordPaparazziDebug). */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 0.1)

    private val course = loadTestCourse()
    private val midProgress = completed("l1s1").let {
        it.copy(
            currentSubLevelId = "l1s2",
            subLevels = it.subLevels + ("l1s2" to SubLevelProgress(completedSteps = setOf(StepType.LEARN), quiz = QuizResult(1, 1, 4, 1, false))),
        )
    }
    private val snap = CourseSnapshot(course, midProgress)

    private fun shot(dark: Boolean = false, content: @Composable () -> Unit) {
        if (dark) paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
        paparazzi.snapshot { PyPathTheme(dark = dark) { content() } }
    }

    @Test fun onboarding() = shot { OnboardingScreen(onFinish = {}) }
    @Test fun dashboardFresh() = shot { DashboardScreen(CourseSnapshot(course, completed()), {}, {}, {}) }
    @Test fun dashboard() = shot { DashboardScreen(snap, {}, {}, {}) }
    @Test fun dashboardDark() = shot(dark = true) { DashboardScreen(snap, {}, {}, {}) }
    @Test fun level() = shot { LevelScreen(snap, "l1", {}, {}) }
    @Test fun subLevel() = shot { SubLevelScreen(snap, "l1s2", {}, {}, {}, {}) }
    @Test fun lesson() = shot { LessonScreen(snap.subLevel("l1s2"), {}, {}) }
    @Test fun completion() = shot { CompletionScreen(CourseSnapshot(course, completed("l1s1", "l1s2")), "l1s2", {}, {}, {}) }

    @Test fun mcqFeedbackWrong() = shot {
        val q = snap.subLevel("l1s2")!!.subLevel.quiz!!
        val s = McqSession(q.questions, q.passPercent).apply { select(0); check(); next(); select(0); check() }
        Column(Modifier.fillMaxSize().background(AppTheme.colors.background).padding(20.dp)) { McqQuestionCard(s) }
    }

    @Test fun mcqSelected() = shot {
        val q = snap.subLevel("l1s1")!!.subLevel.quiz!!
        val s = McqSession(q.questions, q.passPercent).apply { select(1) }
        Column(Modifier.fillMaxSize().background(AppTheme.colors.background).padding(20.dp)) { McqQuestionCard(s) }
    }

    // ───────────── Premium Phase 1 ─────────────

    private val programLib = run {
        var l = com.pypath.app.data.programs.ProgramLibrary()
        l = com.pypath.app.data.programs.ProgramBook.create(l, "Calculator", "School", "a = 1\nb = 2\nprint(a + b)", now = System.currentTimeMillis() - 3_600_000, id = "c").first
        l = com.pypath.app.data.programs.ProgramBook.create(l, "Guess the number", "Games", "import random\n", now = System.currentTimeMillis() - 120_000, id = "g").first
        l = com.pypath.app.data.programs.ProgramBook.create(l, "My first program", "", com.pypath.app.data.programs.ProgramBook.STARTER_CODE, now = System.currentTimeMillis(), id = "f").first
        l
    }

    private fun sheet(dark: Boolean = false, content: @Composable () -> Unit) = shot(dark) {
        Column(Modifier.fillMaxSize().background(AppTheme.colors.surface).padding(top = 16.dp)) { content() }
    }

    @Test fun premiumIdeModeSwitch() = shot {
        Column(Modifier.fillMaxSize().background(AppTheme.colors.background).padding(20.dp)) {
            com.pypath.app.ui.screens.practical.IdeModeSwitch(com.pypath.app.ui.screens.practical.IdeMode.PROGRAMS, {})
        }
    }
    @Test fun premiumProgramManager() = sheet { com.pypath.app.ui.screens.practical.ProgramManager(programLib, "f", {}, {}, {}, {}) }
    @Test fun premiumProgramManagerDark() = sheet(dark = true) { com.pypath.app.ui.screens.practical.ProgramManager(programLib, "c", {}, {}, {}, {}) }
    @Test fun premiumSnippets() = sheet { com.pypath.app.ui.screens.practical.SnippetLibrary {} }
    @Test fun premiumDialog() = shot {
        com.pypath.app.ui.components.PremiumDialog(com.pypath.app.premium.Entitlements.all(com.pypath.app.premium.PremiumSource.LOCAL_DEV), {}, {})
    }
    @Test fun premiumCertificateLockedWithImageCenter() = shot {
        com.pypath.app.ui.screens.certificate.CertificateScreen(snap, "", {}, {}, onOpenImageCenter = {})
    }
    @Test fun freeCertificateLocked() = shot { com.pypath.app.ui.screens.certificate.CertificateScreen(snap, "", {}, {}) }
    @Test fun freeImageCenterLocked() = shot { com.pypath.app.ui.screens.certificate.ImageCenterScreen(com.pypath.app.premium.Entitlements.FREE, {}) }

    /** Draws both Image Center images with the real renderer and writes them for review. */
    @Test fun premiumImageCenterImages() {
        val out = java.io.File("build/premium-images").apply { mkdirs() }
        com.pypath.app.ui.screens.certificate.PremiumImage.entries.forEach { img ->
            val bmp = com.pypath.app.ui.screens.certificate.ImageCenterRenderer.render(paparazzi.context, img)
            org.junit.Assert.assertEquals(img.width, bmp.width)
            org.junit.Assert.assertEquals(img.height, bmp.height)
            java.io.File(out, img.fileName).outputStream().use { bmp.compress(img.format, img.quality, it) }
        }
    }

    // ───────────── Premium Phase 2 ─────────────

    private val p2Projects = com.pypath.app.premium.PremiumContentParser.projects(java.io.File("src/main/assets/premium/projects.json").readText())
    private val p2Arena = com.pypath.app.premium.PremiumContentParser.arena(java.io.File("src/main/assets/premium/arena.json").readText())
    private val p2Pool = com.pypath.app.premium.ArenaPool(course, p2Arena)
    private val p2Snap = CourseSnapshot(course, completed(*course.levels.take(3).flatMap { it.subLevels }.map { it.id }.toTypedArray()))
    private val p2Passed = setOf("premium-ch-calculator", "premium-ch-triangle")

    private val p2Result = run {
        val m = p2Arena.mocks.first()
        val ids = com.pypath.app.premium.ArenaEngine.mockQuestions(p2Pool, m)
        var a = com.pypath.app.premium.ArenaEngine.newAttempt(m.id, com.pypath.app.premium.ArenaMode.MOCK, m.title, ids, 0L, m.minutes * 60_000L)
        ids.take(ids.size - 4).forEachIndexed { i, id ->
            val q = p2Pool.byId[id]!!.question
            val right = i % 3 != 1
            val text = if (q.options.isNotEmpty()) q.options[if (right) q.correctIndex else (q.correctIndex + 1) % q.options.size] else com.pypath.app.domain.QuestionCheck.correctAnswerText(q)
            a = com.pypath.app.premium.ArenaEngine.record(a, com.pypath.app.premium.ArenaAnswer(id, right, text), 1_000L)
        }
        com.pypath.app.premium.ArenaEngine.score(a, p2Pool, 21 * 60_000L + 14_000L, timedOut = false)
    }

    private val p2Workspace: @Composable () -> Unit = {
        com.pypath.app.ui.components.CodeBlock(p2Projects.projects.first().starterCode.ifBlank { "# your code" })
    }

    @Test fun premiumProjectLibrary() = shot { com.pypath.app.ui.screens.premium.ProjectLibraryScreen(p2Projects, p2Passed, {}, {}, {}) }
    @Test fun premiumProjectLibraryDark() = shot(dark = true) { com.pypath.app.ui.screens.premium.ProjectLibraryScreen(p2Projects, p2Passed, {}, {}, {}, initialTab = 1) }
    @Test fun premiumProjectChallenges() = shot { com.pypath.app.ui.screens.premium.ProjectLibraryScreen(p2Projects, p2Passed, {}, {}, {}, initialTab = 2) }
    @Test fun premiumProjectDetail() = shot {
        val p = p2Projects.realWorld.first()
        com.pypath.app.ui.screens.premium.PremiumProjectScreen(p, p2Projects, p2Passed, {}, {}, { id -> snap.subLevel(id)?.let { "${it.subLevel.code} ${it.subLevel.title}" } }, p2Workspace)
    }
    @Test fun premiumProjectDetailDark() = shot(dark = true) {
        val p = p2Projects.advanced[2]
        com.pypath.app.ui.screens.premium.PremiumProjectScreen(p, p2Projects, p2Passed, {}, {}, { id -> snap.subLevel(id)?.let { "${it.subLevel.code} ${it.subLevel.title}" } }, p2Workspace)
    }
    @Test fun premiumChallenge() = shot {
        val t = p2Projects.challenges.first { it.id == "premium-ch-calculator" }
        com.pypath.app.ui.screens.premium.PremiumChallengeScreen(t, p2Projects.projectOf(t.id), true, {}, {}, p2Workspace)
    }
    @Test fun premiumArenaTimed() = shot {
        com.pypath.app.ui.screens.premium.QuizArenaScreen(p2Pool, p2Snap, null, null, null, null, {}, { _, _ -> }, {}, {}, {}, {})
    }
    @Test fun premiumArenaHardDark() = shot(dark = true) {
        com.pypath.app.ui.screens.premium.QuizArenaScreen(p2Pool, p2Snap, null, null, null, null, {}, { _, _ -> }, {}, {}, {}, {}, initialTab = 1)
    }
    @Test fun premiumArenaMocksWithActive() = shot {
        val ids = com.pypath.app.premium.ArenaEngine.timedQuestions(p2Pool, 1, 10, 7)
        var a = com.pypath.app.premium.ArenaEngine.newAttempt("timed-l1", com.pypath.app.premium.ArenaMode.TIMED, "Level 1 · timed", ids, 0L, 300_000L)
        ids.take(4).forEach { a = com.pypath.app.premium.ArenaEngine.record(a, com.pypath.app.premium.ArenaAnswer(it, true), 1L) }
        val data = com.pypath.app.premium.ArenaEngine.finish(com.pypath.app.premium.PremiumStudyData(), p2Result).copy(active = a)
        com.pypath.app.ui.screens.premium.QuizArenaScreen(p2Pool, p2Snap, data, a, 184_000L, null, {}, { _, _ -> }, {}, {}, {}, {}, initialTab = 2)
    }
    @Test fun premiumArenaResult() = shot { com.pypath.app.ui.screens.premium.ArenaResultScreen(p2Result, p2Pool, false, {}) }
    @Test fun premiumArenaResultDark() = shot(dark = true) { com.pypath.app.ui.screens.premium.ArenaResultScreen(p2Result.copy(timedOut = true), p2Pool, true, {}) }
    @Test fun premiumArenaExamQuestion() = shot {
        val ids = com.pypath.app.premium.ArenaEngine.mockQuestions(p2Pool, p2Arena.mocks.first())
        val qs = ids.drop(5).take(3).map { p2Pool.byId[it]!!.question.copy(hint = null) }
        val s = McqSession(qs, 60, numberOffset = 5, displayTotal = ids.size).apply { if (qs[0].options.isNotEmpty()) select(1) }
        Column(Modifier.fillMaxSize().background(AppTheme.colors.background).padding(20.dp)) { McqQuestionCard(s) }
    }
    @Test fun premiumDashboardExtras() = shot {
        DashboardScreen(snap, {}, {}, {}, onOpenQuizArena = {}, onOpenPremiumProjects = {})
    }
    @Test fun premiumProjectsEntry() = shot {
        com.pypath.app.ui.screens.projects.ProjectsScreen(snap, {}, {}, onOpenPremiumLibrary = {})
    }
    @Test fun freeProjects() = shot { com.pypath.app.ui.screens.projects.ProjectsScreen(snap, {}, {}) }

    // ───────────── Premium Code Battle ─────────────
    private val cb = com.pypath.app.premium.BattleContentParser.parse(java.io.File("src/main/assets/premium/battle.json").readText())
    private fun cbPlay(d: com.pypath.app.premium.BattleData, c: com.pypath.app.premium.BattleChallenge, passed: Int, used: Long, start: Long) =
        com.pypath.app.premium.BattleEngine.newAttempt(c, start).let { a ->
            com.pypath.app.premium.BattleEngine.finish(d, cb, c, com.pypath.app.premium.BattleEngine.submit(a, "x", start + used, false), passed, c.tests.size, start + used)
        }
    private val cbData = run {
        val e = cb.of("easy"); val m = cb.of("medium")
        var d = com.pypath.app.premium.BattleData()
        d = cbPlay(d, e[0], e[0].tests.size, 95_000, 1_778_000_000_000L).first
        d = cbPlay(d, e[1], 3, 300_000, 1_778_100_000_000L).first
        d = cbPlay(d, e[1], e[1].tests.size, 200_000, 1_778_200_000_000L).first
        d = cbPlay(d, m[0], 4, 400_000, 1_778_300_000_000L).first
        d
    }
    private val cbWorkspace: @Composable (Boolean) -> Unit = { editable ->
        if (editable) com.pypath.app.ui.components.CodeBlock("text = input()\ncount = 0\nfor ch in text.lower():\n    if ch in \"aeiou\":\n        count += 1\nprint(count)", title = "battle.py")
        else com.pypath.app.ui.components.CodeBlock("print(1)", title = "Submitted code")
    }
    private fun cbOutcome(c: com.pypath.app.premium.BattleChallenge, solved: Boolean): com.pypath.app.premium.BattleOutcome {
        val task = c.task()
        val results = task.tests.mapIndexed { i, t ->
            val ok = solved || i == 0 || (i % 2 == 0)
            val run = if (ok) com.pypath.app.practical.TestRun(t.expectedOutput, com.pypath.app.practical.RunEnd.FINISHED)
                else if (i == 3) com.pypath.app.practical.TestRun("", com.pypath.app.practical.RunEnd.TIMED_OUT)
                else com.pypath.app.practical.TestRun(t.stdin.joinToString("\n") + "\nwrong", com.pypath.app.practical.RunEnd.FINISHED)
            com.pypath.app.practical.evaluateTest(task, i, t, run)
        }
        val report = com.pypath.app.practical.CheckReport(task.tests.size, results)
        val (d, trophies) = cbPlay(com.pypath.app.premium.BattleData(), c, report.passedCount, if (solved) 130_000 else c.timeLimitMs, 1_778_400_000_000L)
        return com.pypath.app.premium.BattleOutcome(d.history.first(), trophies, report)
    }

    @Test fun codeBattleEasy() = shot { com.pypath.app.ui.screens.premium.CodeBattleScreen(cb, cbData, null, null, {}, {}) }
    @Test fun codeBattleHardDarkActive() = shot(dark = true) {
        val a = com.pypath.app.premium.BattleEngine.newAttempt(cb.of("hard")[1], 0L)
        com.pypath.app.ui.screens.premium.CodeBattleScreen(cb, cbData.copy(active = a), 612_000L, null, {}, {}, initialTab = 2)
    }
    @Test fun codeBattleRecords() = shot { com.pypath.app.ui.screens.premium.CodeBattleScreen(cb, cbData, null, null, {}, {}, initialTab = 3) }
    @Test fun codeBattleBrief() = shot {
        com.pypath.app.ui.screens.premium.BattleScreen(cb.of("medium")[0], cbData, null, null, null, {}, {}, {}, {}, {}, {}, {}, { null }, cbWorkspace)
    }
    @Test fun codeBattlePlay() = shot {
        val c = cb.of("medium")[0]
        val a = com.pypath.app.premium.BattleEngine.newAttempt(c, 0L)
        com.pypath.app.ui.screens.premium.BattleScreen(c, cbData.copy(active = a), 47_000L, null, null, {}, {}, {}, {}, {}, {}, {}, { null }, cbWorkspace)
    }
    @Test fun codeBattleTesting() = shot {
        val c = cb.of("medium")[0]
        val a = com.pypath.app.premium.BattleEngine.submit(com.pypath.app.premium.BattleEngine.newAttempt(c, 0L), "print(1)", 120_000L, false)
        com.pypath.app.ui.screens.premium.BattleScreen(c, cbData.copy(active = a), null, null,
            com.pypath.app.ui.screens.premium.BattleTestingState(3, c.tests.size, null), {}, {}, {}, {}, {}, {}, {}, { null }, cbWorkspace)
    }
    @Test fun codeBattleResultSolved() = shot {
        val c = cb.of("hard")[0]
        com.pypath.app.ui.screens.premium.BattleScreen(c, com.pypath.app.premium.BattleData(), null, cbOutcome(c, true), null, {}, {}, {}, {}, {}, {}, {}, { null }, cbWorkspace)
    }
    @Test fun codeBattleResultFailedDark() = shot(dark = true) {
        val c = cb.of("easy")[2]
        com.pypath.app.ui.screens.premium.BattleScreen(c, com.pypath.app.premium.BattleData(), null, cbOutcome(c, false), null, {}, {}, {}, {}, {}, {}, {}, { null }, cbWorkspace)
    }
    @Test fun codeBattleFreeLocked() = shot { com.pypath.app.ui.navigation.CodeBattleLocked {} }
    // Tall screen so the Extras section (with the Code Battle card) is visible.
    private fun tallShot(content: @Composable () -> Unit) {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_6.copy(screenHeight = 4200))
        paparazzi.snapshot { PyPathTheme(dark = false) { content() } }
    }
    @Test fun dashboardCodeBattleCard() = tallShot {
        DashboardScreen(snap, {}, {}, {}, onOpenQuizArena = {}, onOpenPremiumProjects = {}, onOpenCodeBattle = {})
    }
    @Test fun dashboardCodeBattleCardFreeLocked() = tallShot {
        DashboardScreen(snap, {}, {}, {}, onOpenCodeBattle = {}, codeBattleLocked = true)
    }
}
