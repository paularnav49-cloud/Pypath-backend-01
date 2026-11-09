package com.pypath.app.ui.navigation

import androidx.compose.runtime.key
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.pypath.app.domain.Layouts
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.rememberNavController
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.LearnerStats
import com.pypath.app.ui.components.StreakCelebration
import com.pypath.app.ui.components.rememberToday
import com.pypath.app.ui.screens.stats.StatsScreen
import com.pypath.app.ui.screens.review.ReviewScreen
import com.pypath.app.domain.Review
import com.pypath.app.ui.AppState
import com.pypath.app.ui.AppViewModel
import com.pypath.app.ui.screens.completion.CompletionScreen
import com.pypath.app.ui.screens.dashboard.DashboardScreen
import com.pypath.app.ui.screens.lesson.LessonScreen
import com.pypath.app.ui.screens.level.LevelScreen
import com.pypath.app.ui.screens.onboarding.OnboardingScreen
import com.pypath.app.ui.screens.quiz.QuizScreen
import com.pypath.app.ui.screens.practical.PracticalScreen
import com.pypath.app.ui.screens.practical.PracticalViewModel
import com.pypath.app.ui.screens.project.ProjectScreen
import com.pypath.app.ui.screens.projects.ProjectsScreen
import com.pypath.app.ui.screens.reference.CheatSheetScreen
import com.pypath.app.ui.screens.reference.CheatSheetsScreen
import com.pypath.app.ui.screens.reference.GlossaryScreen
import com.pypath.app.ui.screens.certificate.CertificateScreen
import com.pypath.app.ui.screens.certificate.ImageCenterScreen
import com.pypath.app.premium.PremiumFeature
import com.pypath.app.premium.PremiumOffers
import com.pypath.app.ui.components.AccountDialog
import com.pypath.app.ui.components.PremiumDialog
import com.pypath.app.ui.screens.onboarding.LearningGoalScreen
import com.pypath.app.ui.screens.onboarding.LearningImpactScreen
import com.pypath.app.ui.screens.premium.CodeBattleViewModel
import com.pypath.app.ui.screens.premium.PremiumEntry
import com.pypath.app.ui.screens.premium.PremiumPurchaseScreen
import com.pypath.app.ui.screens.profile.ProfileScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.pypath.app.ui.screens.sublevel.SubLevelScreen
import com.pypath.app.ui.screens.signin.SignInScreen
import com.pypath.app.ui.badges.BadgeCelebration
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.pypath.app.auth.GoogleSignIn
import kotlinx.coroutines.launch
import com.pypath.app.ui.theme.AppTheme

/** All destinations in one place. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val SIGN_IN = "signin"
    const val DASHBOARD = "dashboard"
    const val PRACTICAL = "practical"
    const val LEVEL = "level/{id}"
    const val SUBLEVEL = "sublevel/{id}"
    const val LESSON = "lesson/{id}"
    const val QUIZ = "quiz/{id}"
    const val COMPLETE = "complete/{id}"
    const val PROJECT = "project/{id}"
    const val PROJECTS = "projects"
    const val GLOSSARY = "glossary"
    const val CHEATSHEETS = "cheatsheets"
    const val CHEATSHEET = "cheatsheet/{id}"
    const val CERTIFICATE = "certificate"
    /** Premium Phase 1. */
    const val IMAGE_CENTER = "image_center"
    const val STATS = "stats"
    const val REVIEW = "review"
    /** Final frontend polish. */
    const val PROFILE = "profile"
    const val ONBOARDING_GOAL = "onboarding_goal"
    const val ONBOARDING_IMPACT = "onboarding_impact/{minutes}"
    const val PREMIUM = "premium?entry={entry}&feature={feature}"

    fun onboardingImpact(minutes: Int) = "onboarding_impact/$minutes"
    fun premium(entry: PremiumEntry = PremiumEntry.MANUAL, feature: PremiumFeature? = null) =
        "premium?entry=${entry.name}&feature=${feature?.name.orEmpty()}"

    fun level(id: String) = "level/$id"
    fun subLevel(id: String) = "sublevel/$id"
    fun lesson(id: String) = "lesson/$id"
    fun quiz(id: String) = "quiz/$id"
    fun complete(id: String) = "complete/$id"
    fun project(id: String) = "project/$id"
    fun cheatSheet(levelId: String) = "cheatsheet/$levelId"

    /** Screens where a pending level badge celebration may appear (never Practical, lessons, quizzes or a project mid-build). */
    val celebrationRoutes = setOf(DASHBOARD, COMPLETE, LEVEL, SUBLEVEL)
}

@Composable
fun PyPathNavHost(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val accountState = account
    when (val s = state) {
        AppState.Loading -> Box(Modifier.fillMaxSize().background(c.background))
        is AppState.Error -> Box(Modifier.fillMaxSize().background(c.background).padding(24.dp), contentAlignment = Alignment.Center) {
            Text(s.message, color = c.error)
        }
        is AppState.Ready -> {
            // Google Sign-In: wait (behind the splash) until the saved account is known.
            if (accountState == null) {
                Box(Modifier.fillMaxSize().background(c.background))
            } else {
                val nav = rememberNavController()
                // Decided once: changing the start destination later would reset the back stack.
                val start = remember {
                    when {
                        !s.snapshot.progress.onboardingCompleted -> Routes.ONBOARDING
                        accountState.needsSignIn -> Routes.SIGN_IN
                        else -> Routes.DASHBOARD
                    }
                }
                AppNavHost(nav, start, s.snapshot, vm)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppNavHost(nav: NavHostController, start: String, snap: CourseSnapshot, vm: AppViewModel) {
    val c = AppTheme.colors
    val dur = 320
    // Activity-scoped: Practical code drafts and the Python process survive tab switches.
    val practicalVm: PracticalViewModel = viewModel()
    // Premium Phase 2: the Arena/Project Library view model is created on first use, at activity scope.
    val activityOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showTabs = (route == Routes.DASHBOARD || route == Routes.PRACTICAL || route == Routes.PROFILE) && !WindowInsets.isImeVisible
    // Batch 3: XP, streak and goal, recomputed when progress, activity, goal or the local day change.
    val activity by vm.activity.collectAsStateWithLifecycle()
    val goal by vm.dailyGoal.collectAsStateWithLifecycle()
    val today = rememberToday()
    val tasksTotal = practicalVm.tasks.size.takeIf { it > 0 }
    val stats = remember(snap, activity, goal, today, tasksTotal) { LearnerStats.build(snap, activity, goal, today, tasksTotal) }
    // Batch 3 Part 2: spaced review. Ids missing from today's course are ignored.
    val reviewIndex = remember(snap) { Review.index(snap) }
    val reviewInfo = remember(activity, reviewIndex, today) { Review.info(activity, reviewIndex, today) }
    val openReview: (List<String>) -> Unit = { scope ->
        val resuming = scope.isEmpty() && reviewInfo.resumeAt != null
        if (resuming) nav.navigate(Routes.REVIEW) { launchSingleTop = true }
        else vm.startReview(scope) { ok -> if (ok) nav.navigate(Routes.REVIEW) { launchSingleTop = true } }
    }
    // Final frontend polish: Premium gate. Premium (incl. test mode) opens the feature directly; Free
    // users see the Premium offer (X returns to where they were). Phase 1 Entitlements decide.
    val entitlementsNow by vm.entitlements.collectAsStateWithLifecycle()
    val gated: (PremiumFeature, String) -> Unit = { feature, target ->
        if (PremiumOffers.shouldOfferFor(entitlementsNow, feature)) nav.navigate(Routes.premium(PremiumEntry.LOCKED_FEATURE, feature)) { launchSingleTop = true }
        else nav.navigate(target) { launchSingleTop = true }
    }
    val firstLessonId = snap.orderedSubLevels.firstOrNull { it.isPlayable }?.subLevel?.id
    val openTab: (String) -> Unit = { target ->
        if (route != target) nav.navigate(target) {
            popUpTo(Routes.DASHBOARD)
            launchSingleTop = true
        }
    }

    // Batch 3 Part 3: one sub-level screen, used full screen and in the two-pane level layout.
    // [markOpened] is false when the two-pane layout shows a sub-level the learner didn't pick yet.
    val subLevelContent: @Composable (String, Boolean) -> Unit = { id, markOpened ->
        SubLevelScreen(
            snapshot = snap, subLevelId = id,
            onBack = { nav.popBackStack() },
            onOpened = { if (markOpened) vm.openSubLevel(id) },
            onStartLesson = { nav.navigate(Routes.lesson(id)) },
            onStartQuiz = { nav.navigate(Routes.quiz(id)) },
            onStartProject = { nav.navigate(Routes.project(id)) },
            practiceTask = practicalVm.tasks.firstOrNull { it.afterSubLevel == id },
            onOpenPractice = { taskId ->
                practicalVm.select(taskId)
                nav.navigate(Routes.PRACTICAL) { popUpTo(Routes.DASHBOARD); launchSingleTop = true }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(c.background)) {
    NavHost(
        navController = nav,
        startDestination = start,
        modifier = Modifier.weight(1f).fillMaxWidth().background(c.background),
        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(dur), initialOffset = { it / 5 }) + fadeIn(tween(dur)) },
        exitTransition = { fadeOut(tween(dur / 2)) },
        popEnterTransition = { fadeIn(tween(dur)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(dur), targetOffset = { it / 5 }) + fadeOut(tween(dur / 2)) },
    ) {
        composable(Routes.ONBOARDING, enterTransition = { fadeIn() }) {
            OnboardingScreen(onFinish = {
                // Final frontend polish: intro → Google Sign-In (skippable) → daily time goal → monthly
                // impact. Onboarding is saved as done only at the end ("Let's Start Learning").
                val next = if (vm.account.value?.needsSignIn == true) Routes.SIGN_IN else Routes.ONBOARDING_GOAL
                nav.navigate(next) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }

        composable(Routes.ONBOARDING_GOAL, enterTransition = { fadeIn() }) {
            val saved by vm.learningMinutes.collectAsStateWithLifecycle()
            LearningGoalScreen(initial = saved, onContinue = { minutes -> nav.navigate(Routes.onboardingImpact(minutes)) })
        }

        composable(
            Routes.ONBOARDING_IMPACT,
            arguments = listOf(navArgument("minutes") { type = NavType.IntType; defaultValue = 15 }),
        ) { entry ->
            val minutes = entry.arguments?.getInt("minutes") ?: 15
            LearningImpactScreen(minutes = minutes, onStart = {
                vm.finishOnboarding(minutes)
                // Straight into the first real lesson; Back from it lands on the dashboard.
                nav.navigate(Routes.DASHBOARD) { popUpTo(nav.graph.id) { inclusive = true } }
                if (firstLessonId != null) {
                    nav.navigate(Routes.subLevel(firstLessonId))
                    nav.navigate(Routes.lesson(firstLessonId))
                }
            })
        }

        composable(Routes.SIGN_IN, enterTransition = { fadeIn() }) {
            val toDashboard = {
                // New learners continue to the time-goal step; returning learners go home.
                val next = if (!snap.progress.onboardingCompleted) Routes.ONBOARDING_GOAL else Routes.DASHBOARD
                nav.navigate(next) { popUpTo(Routes.SIGN_IN) { inclusive = true } }
            }
            SignInScreen(
                onSignedIn = { user -> vm.signedIn(user); toDashboard() },
                onSkip = { vm.skipSignIn(); toDashboard() },
            )
        }

        composable(Routes.DASHBOARD, enterTransition = { fadeIn(tween(dur)) }) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val soundOn by vm.soundEffects.collectAsStateWithLifecycle()
            val reminderPrefs by vm.reminder.collectAsStateWithLifecycle()
            val accountNow by vm.account.collectAsStateWithLifecycle()
            val premiumNow by vm.entitlements.collectAsStateWithLifecycle()
            DashboardScreen(
                snapshot = snap,
                onContinue = { id -> nav.navigate(Routes.subLevel(id)) },
                onOpenLevel = { id -> nav.navigate(Routes.level(id)) },
                onResetProgress = vm::resetProgress,
                soundEffects = soundOn,
                onSoundEffectsChange = vm::setSoundEffects,
                onOpenProjects = { nav.navigate(Routes.PROJECTS) },
                onOpenGlossary = { nav.navigate(Routes.GLOSSARY) },
                onOpenCheatSheets = { nav.navigate(Routes.CHEATSHEETS) },
                onOpenCertificate = { nav.navigate(Routes.CERTIFICATE) },
                stats = stats,
                onOpenStats = { openTab(Routes.PROFILE) },
                onDailyGoalChange = vm::setDailyGoal,
                review = reviewInfo,
                onReview = { openReview(emptyList()) },
                reminder = reminderPrefs,
                onReminderEnabledChange = { vm.setReminderEnabled(it) },
                onReminderMinutesChange = { vm.setReminderMinutes(it) },
                account = accountNow,
                onSignedIn = { vm.signedIn(it) },
                onSignOut = {
                    vm.signOut()
                    scope.launch { GoogleSignIn.signOut(context) }
                },
                premium = premiumNow,
                onDevPremiumChange = vm::setDevPremium,
                // Final frontend polish: Premium cards always visible; locked ones open the Premium offer.
                onOpenQuizArena = { gated(PremiumFeature.QUIZ_ARENA, PremiumRoutes.ARENA) },
                onOpenPremiumProjects = { gated(PremiumFeature.PROJECT_LIBRARY, PremiumRoutes.PROJECTS) },
                onOpenCodeBattle = { gated(PremiumFeature.CODE_BATTLE, CodeBattleRoutes.HOME) },
                codeBattleLocked = !premiumNow.has(PremiumFeature.CODE_BATTLE),
                quizArenaLocked = !premiumNow.has(PremiumFeature.QUIZ_ARENA),
                premiumProjectsLocked = !premiumNow.has(PremiumFeature.PROJECT_LIBRARY),
                statsInProfile = true,
                onOpenPremiumPlans = { nav.navigate(Routes.premium()) { launchSingleTop = true } },
            )
        }

        composable(Routes.PROFILE, enterTransition = { fadeIn(tween(dur)) }, exitTransition = { fadeOut(tween(dur / 2)) }) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val premium by vm.entitlements.collectAsStateWithLifecycle()
            val minutes by vm.learningMinutes.collectAsStateWithLifecycle()
            val accountNow by vm.account.collectAsStateWithLifecycle()
            var testModeDialog by rememberSaveable { mutableStateOf(false) }
            var accountDialog by rememberSaveable { mutableStateOf(false) }
            // Earned Code Battle trophies (real saved data; loaded once per app run).
            val bvm: CodeBattleViewModel = viewModel(activityOwner)
            LaunchedEffect(Unit) { bvm.load() }
            val battle by bvm.data.collectAsStateWithLifecycle()
            ProfileScreen(
                stats = stats, snapshot = snap, entitlements = premium, learningMinutes = minutes,
                onGoalChange = vm::setDailyGoal,
                onLearningMinutesChange = { vm.setLearningMinutes(it) },
                onPractise = { id -> nav.navigate(Routes.subLevel(id)) },
                onReviewTopics = { ids -> openReview(ids) },
                onOpenPlans = { nav.navigate(Routes.premium()) { launchSingleTop = true } },
                onOpenPremiumTestMode = { testModeDialog = true },
                onOpenRewards = { gated(PremiumFeature.CODE_BATTLE, CodeBattleRoutes.HOME) },
                earnedTrophies = battle?.trophies?.keys.orEmpty(),
                account = accountNow,
                onOpenAccount = { accountDialog = true },
            )
            if (testModeDialog) PremiumDialog(premium, vm::setDevPremium, onDismiss = { testModeDialog = false })
            val acc = accountNow
            if (accountDialog && acc != null) {
                AccountDialog(
                    account = acc, onSignedIn = { vm.signedIn(it) },
                    onSignOut = { vm.signOut(); scope.launch { GoogleSignIn.signOut(context) } },
                    onDismiss = { accountDialog = false },
                )
            }
        }

        // Final frontend polish: Premium plans / upgrade offer (no payment processing in this build).
        composable(
            Routes.PREMIUM,
            arguments = listOf(
                navArgument("entry") { type = NavType.StringType; defaultValue = PremiumEntry.MANUAL.name },
                navArgument("feature") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val premium by vm.entitlements.collectAsStateWithLifecycle()
            val kind = runCatching { PremiumEntry.valueOf(entry.arguments?.getString("entry").orEmpty()) }.getOrDefault(PremiumEntry.MANUAL)
            val feature = entry.arguments?.getString("feature")?.takeIf { it.isNotBlank() }
                ?.let { name -> runCatching { PremiumFeature.valueOf(name) }.getOrNull() }
            val close: () -> Unit = {
                if (kind == PremiumEntry.INTRO) nav.navigate(Routes.DASHBOARD) { popUpTo(Routes.DASHBOARD) { inclusive = true } }
                else nav.popBackStack()
            }
            // Rule B/C: once the feature is unlocked (e.g. test mode turned on) never keep a locked offer up.
            LaunchedEffect(premium, kind, feature) {
                if (kind == PremiumEntry.LOCKED_FEATURE && feature != null && premium.has(feature)) nav.popBackStack()
            }
            PremiumPurchaseScreen(entitlements = premium, onClose = close, entry = kind, feature = feature)
        }

        composable(Routes.REVIEW) {
            ReviewScreen(
                activity = activity,
                index = reviewIndex,
                onAnswer = { item, correct, hintUsed -> vm.recordReviewAnswer(item.subLevel.subLevel.id, item.question.id, correct, hintUsed) },
                onFinishEarly = vm::finishReview,
                onDone = { vm.closeReview(); nav.popBackStack() },
                onLeave = { nav.popBackStack() },
            )
        }

        composable(Routes.STATS) {
            StatsScreen(
                stats = stats,
                onBack = { nav.popBackStack() },
                onGoalChange = vm::setDailyGoal,
                onPractise = { id -> nav.navigate(Routes.subLevel(id)) },
                onReviewTopics = { ids -> openReview(ids) },
            )
        }

        composable(Routes.PRACTICAL, enterTransition = { fadeIn(tween(dur)) }, exitTransition = { fadeOut(tween(dur / 2)) }) {
            val premium by vm.entitlements.collectAsStateWithLifecycle()
            PracticalScreen(
                snapshot = snap, vm = practicalVm, premium = premium,
                onPremiumLocked = { nav.navigate(Routes.premium(PremiumEntry.LOCKED_FEATURE, PremiumFeature.ADVANCED_IDE)) { launchSingleTop = true } },
            )
        }

        composable(Routes.LEVEL) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            // Batch 3 Part 3: from 720dp (landscape, tablets) the level list and the chosen sub-level
            // sit side by side. The choice survives rotation.
            var picked by rememberSaveable(id) { mutableStateOf<String?>(null) }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val twoPane = Layouts.isTwoPane(maxWidth.value.toInt())
                val level = snap.level(id)
                val shown = (picked ?: level?.let { Layouts.defaultSubLevel(it) })
                    ?.takeIf { sid -> snap.subLevel(sid)?.isPlayable == true }
                if (twoPane && level != null) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(0.45f).fillMaxHeight()) {
                            LevelScreen(
                                snapshot = snap, levelId = id,
                                onBack = { nav.popBackStack() },
                                onOpenSubLevel = { subId -> picked = subId },
                                onOpenProject = { projectId -> nav.navigate(Routes.project(projectId)) },
                                onOpenCheatSheet = { levelId -> nav.navigate(Routes.cheatSheet(levelId)) },
                                selectedSubLevelId = shown,
                            )
                        }
                        Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
                        Box(Modifier.weight(0.55f).fillMaxHeight()) {
                            if (shown != null) {
                                // Re-keyed so picking the already-shown sub-level still counts as opening it.
                                key(shown, shown == picked) { subLevelContent(shown, shown == picked) }
                            } else {
                                Box(Modifier.fillMaxSize().background(c.background).padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("Pick a sub-level to see its steps.", color = c.textMuted)
                                }
                            }
                        }
                    }
                } else {
                    LevelScreen(
                        snapshot = snap, levelId = id,
                        onBack = { nav.popBackStack() },
                        onOpenSubLevel = { subId -> nav.navigate(Routes.subLevel(subId)) },
                        onOpenProject = { projectId -> nav.navigate(Routes.project(projectId)) },
                        onOpenCheatSheet = { levelId -> nav.navigate(Routes.cheatSheet(levelId)) },
                    )
                }
            }
        }

        composable(Routes.PROJECTS) {
            val premium by vm.entitlements.collectAsStateWithLifecycle()
            ProjectsScreen(
                snapshot = snap, onBack = { nav.popBackStack() }, onOpenProject = { id -> nav.navigate(Routes.project(id)) },
                onOpenPremiumLibrary = { gated(PremiumFeature.PROJECT_LIBRARY, PremiumRoutes.PROJECTS) },
                premiumLibraryLocked = !premium.has(PremiumFeature.PROJECT_LIBRARY),
            )
        }

        // Premium Phase 2: Premium Project Library and Advanced Quiz Arena.
        premiumPhase2Routes(nav, snap, vm, practicalVm, activityOwner)

        // Premium Code Battle.
        codeBattleRoutes(nav, vm, practicalVm, activityOwner)

        composable(Routes.GLOSSARY) {
            GlossaryScreen(snapshot = snap, onBack = { nav.popBackStack() }, onOpenSubLevel = { id -> nav.navigate(Routes.subLevel(id)) })
        }

        composable(Routes.CHEATSHEETS) {
            CheatSheetsScreen(snapshot = snap, onBack = { nav.popBackStack() }, onOpenCheatSheet = { id -> nav.navigate(Routes.cheatSheet(id)) })
        }

        composable(Routes.CHEATSHEET) { entry ->
            CheatSheetScreen(snapshot = snap, levelId = entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
        }

        composable(Routes.CERTIFICATE) {
            val savedName by vm.certificateName.collectAsStateWithLifecycle()
            val premium by vm.entitlements.collectAsStateWithLifecycle()
            CertificateScreen(
                snapshot = snap, savedName = savedName, onNameChange = vm::setCertificateName, onBack = { nav.popBackStack() },
                onOpenImageCenter = { gated(PremiumFeature.IMAGE_CENTER, Routes.IMAGE_CENTER) },
                imageCenterLocked = !premium.has(PremiumFeature.IMAGE_CENTER),
            )
        }

        composable(Routes.IMAGE_CENTER) {
            val premium by vm.entitlements.collectAsStateWithLifecycle()
            ImageCenterScreen(premium = premium, onBack = { nav.popBackStack() })
        }

        composable(Routes.SUBLEVEL) { entry ->
            subLevelContent(entry.arguments?.getString("id").orEmpty(), true)
        }

        composable(Routes.LESSON) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val state = snap.subLevel(id)
            LessonScreen(
                state = state,
                onClose = { nav.popBackStack() },
                onFinished = {
                    if (state != null) vm.completeLesson(id, state.subLevel.steps)
                    nav.navigate(Routes.quiz(id)) { popUpTo(Routes.LESSON) { inclusive = true } }
                },
                onPageDone = vm::recordAction,
            )
        }

        composable(Routes.QUIZ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val state = snap.subLevel(id)
            QuizScreen(
                state = state,
                onClose = { nav.popBackStack() },
                onSubmitResult = { score, total, passed ->
                    if (state != null) vm.recordQuiz(id, score, total, passed, state.subLevel.steps)
                },
                onPassedContinue = {
                    // Final frontend polish: the first lesson after the new onboarding.
                    val firstAfterOnboarding = id == firstLessonId && vm.firstLessonPending.value
                    if (firstAfterOnboarding) {
                        val offer = PremiumOffers.shouldShowIntroOffer(entitlementsNow, true, vm.introOfferShown.value)
                        vm.firstLessonDone()
                        if (offer) {
                            vm.markIntroOfferShown()
                            nav.navigate(Routes.DASHBOARD) { popUpTo(Routes.DASHBOARD) { inclusive = true } }
                            nav.navigate(Routes.premium(PremiumEntry.INTRO))
                        } else if (entitlementsNow.isPremium) {
                            // Rule C: Premium skips the intro offer and goes straight to the dashboard.
                            nav.navigate(Routes.DASHBOARD) { popUpTo(Routes.DASHBOARD) { inclusive = true } }
                        } else {
                            nav.navigate(Routes.complete(id)) { popUpTo(Routes.SUBLEVEL) { inclusive = true } }
                        }
                    } else {
                        nav.navigate(Routes.complete(id)) { popUpTo(Routes.SUBLEVEL) { inclusive = true } }
                    }
                },
                onReviewLesson = {
                    nav.navigate(Routes.lesson(id)) { popUpTo(Routes.QUIZ) { inclusive = true } }
                },
                onAnswer = { questionId, correct, hintUsed -> vm.recordAnswer(id, questionId, correct, hintUsed) },
            )
        }

        composable(Routes.PROJECT) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val state = snap.subLevel(id)
            ProjectScreen(
                snapshot = snap, subLevelId = id,
                onBack = { nav.popBackStack() },
                // Level projects are optional, so they never become the "Continue Learning" target.
                onOpened = { if (state?.isBonus != true) vm.openSubLevel(id) },
                onOpenConcept = { conceptId -> nav.navigate(Routes.subLevel(conceptId)) },
                onBriefingSeen = { vm.markBriefingSeen(id) },
                onStageCorrect = { stageId ->
                    if (state != null) vm.completeProjectStage(id, stageId, state.subLevel.stages.map { it.id }, state.subLevel.steps)
                    vm.recordAction()
                },
                onMiss = {
                    vm.recordProjectMiss(id)
                    vm.recordAction()
                },
                // The level badge celebration (if still pending) appears on the dashboard.
                onFinish = {
                    if (state?.isBonus == true) nav.popBackStack()
                    else nav.navigate(Routes.DASHBOARD) { popUpTo(Routes.DASHBOARD) { inclusive = true } }
                },
            )
        }

        composable(Routes.COMPLETE) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            CompletionScreen(
                snapshot = snap, subLevelId = id,
                onNext = { nextId ->
                    nav.navigate(Routes.subLevel(nextId)) { popUpTo(Routes.COMPLETE) { inclusive = true } }
                },
                onBackToLevel = { levelId ->
                    nav.navigate(Routes.level(levelId)) { popUpTo(Routes.DASHBOARD) }
                },
                onDashboard = { nav.navigate(Routes.DASHBOARD) { popUpTo(Routes.DASHBOARD) { inclusive = true } } },
            )
        }
    }
    if (showTabs) BottomTabs(route, openTab)
    }

    // Level badge celebration: shown once, over the completion screen or the dashboard.
    val pending = snap.pendingBadgeLevel
    LaunchedEffect(pending?.level?.id, route) {
        if (pending != null && route in Routes.celebrationRoutes) vm.startCelebration(pending.level)
    }
    vm.celebratingLevel?.let { number -> BadgeCelebration(number, onContinue = vm::endCelebration) }

    // Batch 3: streak milestone (3, 7, 14, 30, 100 days), after any badge celebration, same screens.
    val milestone = activity.pendingMilestone
    if (milestone != null && vm.celebratingLevel == null && pending == null && route in Routes.celebrationRoutes) {
        StreakCelebration(milestone, onContinue = vm::consumeMilestone)
    }
    }
}
