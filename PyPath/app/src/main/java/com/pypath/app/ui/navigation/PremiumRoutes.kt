package com.pypath.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.premium.ArenaEngine
import com.pypath.app.premium.ArenaMode
import com.pypath.app.premium.PremiumFeature
import com.pypath.app.ui.AppViewModel
import com.pypath.app.ui.components.AppTopBar
import com.pypath.app.ui.screens.practical.PracticalViewModel
import com.pypath.app.ui.screens.premium.ArenaPlayScreen
import com.pypath.app.ui.screens.premium.ArenaResultScreen
import com.pypath.app.ui.screens.premium.ArenaViewModel
import com.pypath.app.ui.screens.premium.PremiumChallengeScreen
import com.pypath.app.ui.screens.premium.PremiumLockedNotice
import com.pypath.app.ui.screens.premium.PremiumProjectScreen
import com.pypath.app.ui.screens.premium.PremiumWorkspace
import com.pypath.app.ui.screens.premium.ProjectLibraryScreen
import com.pypath.app.ui.screens.premium.QuizArenaScreen
import com.pypath.app.ui.theme.AppTheme

/** Premium Phase 2 destinations. Each one checks the Phase 1 entitlement before showing anything. */
object PremiumRoutes {
    const val PROJECTS = "premium_projects"
    const val PROJECT = "premium_project/{id}"
    const val CHALLENGE = "premium_challenge/{id}"
    const val ARENA = "quiz_arena"
    const val ARENA_PLAY = "arena_play"
    const val ARENA_RESULT = "arena_result/{key}"

    fun project(id: String) = "premium_project/$id"
    fun challenge(id: String) = "premium_challenge/$id"
    fun arenaResult(key: String) = "arena_result/$key"
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize().background(AppTheme.colors.background), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AppTheme.colors.brand)
    }
}

@Composable
private fun Locked(title: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(AppTheme.colors.background)) {
        AppTopBar(title = title, onBack = onBack)
        PremiumLockedNotice("$title is a Premium feature", "Turn on Premium to open it. Everything in the Free version keeps working as before.")
    }
}

internal fun NavGraphBuilder.premiumPhase2Routes(
    nav: NavHostController,
    snap: CourseSnapshot,
    vm: AppViewModel,
    practicalVm: PracticalViewModel,
    activityOwner: ViewModelStoreOwner,
) {
    composable(PremiumRoutes.PROJECTS) {
        val premium by vm.entitlements.collectAsStateWithLifecycle()
        if (!premium.has(PremiumFeature.PROJECT_LIBRARY)) { Locked("Premium projects") { nav.popBackStack() }; return@composable }
        val pvm: ArenaViewModel = viewModel(activityOwner)
        LaunchedEffect(Unit) { pvm.loadProjects() }
        val study by pvm.studyData.collectAsStateWithLifecycle()
        ProjectLibraryScreen(
            catalog = pvm.projects,
            passed = study?.passedChallenges.orEmpty() + practicalVm.premiumPassedThisSession,
            onBack = { nav.popBackStack() },
            onOpenProject = { nav.navigate(PremiumRoutes.project(it)) },
            onOpenChallenge = { nav.navigate(PremiumRoutes.challenge(it)) },
        )
    }

    composable(PremiumRoutes.PROJECT) { entry ->
        val premium by vm.entitlements.collectAsStateWithLifecycle()
        if (!premium.has(PremiumFeature.PROJECT_LIBRARY)) { Locked("Premium projects") { nav.popBackStack() }; return@composable }
        val pvm: ArenaViewModel = viewModel(activityOwner)
        LaunchedEffect(Unit) { pvm.loadProjects() }
        val study by pvm.studyData.collectAsStateWithLifecycle()
        val catalog = pvm.projects
        val project = catalog?.project(entry.arguments?.getString("id").orEmpty())
        val data = study
        if (catalog == null || data == null) { Loading(); return@composable }
        if (project == null) { Locked("Project") { nav.popBackStack() }; return@composable }
        val task = project.workspaceTask()
        PremiumProjectScreen(
            project = project, catalog = catalog,
            passed = data.passedChallenges + practicalVm.premiumPassedThisSession,
            onBack = { nav.popBackStack() },
            onOpenChallenge = { nav.navigate(PremiumRoutes.challenge(it)) },
            lessonName = { id -> snap.subLevel(id)?.let { "${it.subLevel.code} ${it.subLevel.title}" } },
            workspace = { PremiumWorkspace(practicalVm, task, project.fileName, data.drafts[task.id], pvm::saveDraft) },
        )
    }

    composable(PremiumRoutes.CHALLENGE) { entry ->
        val premium by vm.entitlements.collectAsStateWithLifecycle()
        if (!premium.has(PremiumFeature.PROJECT_LIBRARY)) { Locked("Premium projects") { nav.popBackStack() }; return@composable }
        val pvm: ArenaViewModel = viewModel(activityOwner)
        LaunchedEffect(Unit) { pvm.loadProjects() }
        val study by pvm.studyData.collectAsStateWithLifecycle()
        val catalog = pvm.projects
        val data = study
        if (catalog == null || data == null) { Loading(); return@composable }
        val id = entry.arguments?.getString("id").orEmpty()
        val task = catalog.challenge(id)
        if (task == null) { Locked("Challenge") { nav.popBackStack() }; return@composable }
        val project = catalog.projectOf(id)
        val cameFromProject = nav.previousBackStackEntry?.destination?.route == PremiumRoutes.PROJECT
        PremiumChallengeScreen(
            task = task, project = project,
            passed = id in data.passedChallenges || id in practicalVm.premiumPassedThisSession,
            onBack = { nav.popBackStack() },
            onOpenProject = if (cameFromProject) null else ({ nav.navigate(PremiumRoutes.project(it)) }),
            workspace = { PremiumWorkspace(practicalVm, task, project?.fileName ?: "main.py", data.drafts[task.id], pvm::saveDraft) },
        )
    }

    composable(PremiumRoutes.ARENA) {
        val premium by vm.entitlements.collectAsStateWithLifecycle()
        if (!premium.has(PremiumFeature.QUIZ_ARENA)) { Locked("Quiz Arena") { nav.popBackStack() }; return@composable }
        val avm: ArenaViewModel = viewModel(activityOwner)
        LaunchedEffect(snap.course) { avm.attach(snap.course) }
        val study by avm.studyData.collectAsStateWithLifecycle()
        val pool = avm.pool
        val play = { nav.navigate(PremiumRoutes.ARENA_PLAY) { launchSingleTop = true } }
        val startFresh: (() -> Unit) -> Unit = { begin -> if (avm.attempt != null) avm.submit(); avm.consumeFinished(); begin(); play() }
        QuizArenaScreen(
            pool = pool, snapshot = snap, study = study, active = avm.attempt, remainingMs = avm.remainingMs(),
            loadError = avm.loadError,
            onBack = { nav.popBackStack() },
            onStartTimed = { level, minutes ->
                val p = pool ?: return@QuizArenaScreen
                startFresh {
                    val ids = ArenaEngine.timedQuestions(p, level, p.catalog.timed.questionCount, System.currentTimeMillis())
                    avm.start(ArenaEngine.timedKey(level), ArenaMode.TIMED, "${p.levelTitles[level] ?: "Level $level"} · timed", ids, minutes * 60_000L)
                }
            },
            onStartHard = { set -> startFresh { avm.start(ArenaEngine.hardKey(set), ArenaMode.HARD, set.title, ArenaEngine.hardQuestions(set), null) } },
            onStartMock = { mock ->
                val p = pool ?: return@QuizArenaScreen
                startFresh { avm.start(ArenaEngine.mockKey(mock), ArenaMode.MOCK, mock.title, ArenaEngine.mockQuestions(p, mock), mock.minutes * 60_000L) }
            },
            onResume = { avm.consumeFinished(); play() },
            onReview = { key -> nav.navigate(PremiumRoutes.arenaResult(key)) },
        )
    }

    composable(PremiumRoutes.ARENA_PLAY) {
        val premium by vm.entitlements.collectAsStateWithLifecycle()
        if (!premium.has(PremiumFeature.QUIZ_ARENA)) { Locked("Quiz Arena") { nav.popBackStack() }; return@composable }
        val avm: ArenaViewModel = viewModel(activityOwner)
        LaunchedEffect(snap.course) { avm.attach(snap.course) }
        ArenaPlayScreen(
            vm = avm,
            onExit = { nav.popBackStack() },
            onFinished = { key -> nav.navigate(PremiumRoutes.arenaResult(key)) { popUpTo(PremiumRoutes.ARENA_PLAY) { inclusive = true } } },
        )
    }

    composable(PremiumRoutes.ARENA_RESULT) { entry ->
        val premium by vm.entitlements.collectAsStateWithLifecycle()
        if (!premium.has(PremiumFeature.QUIZ_ARENA)) { Locked("Quiz Arena") { nav.popBackStack() }; return@composable }
        val avm: ArenaViewModel = viewModel(activityOwner)
        LaunchedEffect(snap.course) { avm.attach(snap.course) }
        val study by avm.studyData.collectAsStateWithLifecycle()
        val key = entry.arguments?.getString("key").orEmpty()
        val auto = remember(key) { avm.autoSubmitted && avm.finishedKey == key }
        LaunchedEffect(key) { if (avm.finishedKey == key) avm.consumeFinished() }
        ArenaResultScreen(result = study?.results?.get(key), pool = avm.pool, autoSubmitted = auto, onBack = { nav.popBackStack() })
    }
}
