package com.pypath.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pypath.app.PyPathApp
import com.pypath.app.audio.SoundManager
import com.pypath.app.cloud.CloudSync
import com.pypath.app.cloud.SyncStatus
import com.pypath.app.data.activity.ActivityData
import com.pypath.app.data.activity.ActivityRepository
import com.pypath.app.data.activity.ActivityUpdates
import com.pypath.app.data.auth.AccountState
import com.pypath.app.data.auth.AuthRepository
import com.pypath.app.data.auth.GoogleAccount
import kotlinx.coroutines.flow.MutableStateFlow
import com.pypath.app.premium.Entitlements
import com.pypath.app.premium.PremiumRepository
import kotlinx.coroutines.flow.map
import com.pypath.app.data.content.CourseRepository
import com.pypath.app.data.model.Level
import com.pypath.app.data.model.StepType
import com.pypath.app.data.progress.ProgressRepository
import com.pypath.app.data.settings.SettingsRepository
import com.pypath.app.domain.Access
import com.pypath.app.domain.AccessRules
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.DailyGoal
import com.pypath.app.domain.Review
import com.pypath.app.domain.Reminders
import com.pypath.app.data.settings.ReminderPrefs
import com.pypath.app.reminders.AlarmReminderControl
import com.pypath.app.reminders.ReminderControl
import kotlinx.coroutines.flow.first
import com.pypath.app.domain.Streaks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppState {
    data object Loading : AppState
    data class Ready(val snapshot: CourseSnapshot) : AppState
    data class Error(val message: String) : AppState
}

/**
 * Activity-scoped view model shared by every screen. It combines static course content
 * with live learner progress into a single [CourseSnapshot].
 */
class AppViewModel(
    private val courses: CourseRepository,
    private val progressRepo: ProgressRepository,
    private val settings: SettingsRepository,
    private val sounds: SoundManager?,
    private val activityRepo: ActivityRepository,
    /** Batch 3 Part 3: daily reminder alarm (null in previews and tests). */
    private val reminderControl: ReminderControl? = null,
    /** Google Sign-In account store (null in previews and tests: then sign-in is never asked for). */
    private val authRepo: AuthRepository? = null,
    /** Premium Phase 1: Premium access (null in previews and tests: then everything is Free). */
    private val premiumRepo: PremiumRepository? = null,
    /** Cloud backup for signed-in learners (null in previews and tests). */
    private val cloud: CloudSync? = null,
) : ViewModel() {

    // ───────────── Premium Phase 1 ─────────────

    /** Unlocked Premium features. Starts as Free, so nothing Premium flashes in while loading. */
    val entitlements: StateFlow<Entitlements> =
        (premiumRepo?.entitlements ?: MutableStateFlow(Entitlements.FREE))
            .stateIn(viewModelScope, SharingStarted.Eagerly, Entitlements.FREE)

    /** True when the local developer/testing switch is available (it is not a purchase). */
    val premiumDevToggle: Boolean get() = premiumRepo?.isDevToggle == true

    /** Development/testing only. */
    fun setDevPremium(enabled: Boolean) = viewModelScope.launch { runCatching { premiumRepo?.setDevPremium(enabled) } }

    // ───────────── Google Sign-In ─────────────

    /** Signed-in Google account; null only while it is still loading from the phone. */
    val account: StateFlow<AccountState?> =
        (authRepo?.account?.map<AccountState, AccountState?> { it } ?: MutableStateFlow(AccountState(skipped = true)))
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Cloud backup state for the account dialog. */
    val syncStatus: StateFlow<SyncStatus> = (cloud?.status ?: MutableStateFlow(SyncStatus.Off))
        .stateIn(viewModelScope, SharingStarted.Eagerly, SyncStatus.Off)

    // ───────────── After sign-in: restore the account, then decide where to go ─────────────

    /** Navigation request after sign-in, login or account deletion. Null = none in this app run. */
    sealed interface PostAuth {
        /** The account's cloud copy is being loaded: show "Restoring your progress…". */
        data object Restoring : PostAuth
        /** Restart the navigation at [route]. [id] changes with every request. */
        data class Go(val route: String, val id: Long) : PostAuth
    }

    private val _postAuth = MutableStateFlow<PostAuth?>(null)
    val postAuth: StateFlow<PostAuth?> = _postAuth
    private var postAuthId = 0L

    private val _authNotice = MutableStateFlow<String?>(null)
    /** One message shown on top of the next screen (e.g. "account deleted"); dismissed by the learner. */
    val authNotice: StateFlow<String?> = _authNotice
    fun dismissAuthNotice() { _authNotice.value = null }

    private fun go(route: String, notice: String? = null) {
        if (notice != null) _authNotice.value = notice
        _postAuth.value = PostAuth.Go(route, ++postAuthId)
    }

    /**
     * Called only after Supabase accepted the Google sign-in (see GoogleSignIn). If the current
     * Privacy Policy still needs to be accepted, the policy screen takes over and
     * [agreeToPrivacyPolicy] continues from there. Otherwise the account's cloud copy is loaded
     * FIRST and the next screen is chosen from the account's real data: a returning learner whose
     * onboarding is done goes straight to the dashboard; a new account gets the onboarding.
     */
    fun signedIn(user: GoogleAccount) = viewModelScope.launch {
        val restoring = cloud != null && accessNow() is Access.Full
        if (restoring) _postAuth.value = PostAuth.Restoring
        runCatching { authRepo?.signIn(user) }
        if (restoring) restoreAndContinue()
        fillCertificateName(user)
    }

    private suspend fun fillCertificateName(user: GoogleAccount) {
        // After the restore, so a name already saved in the account is never overwritten.
        if (user.displayName.isNotBlank()) runCatching {
            if (settings.certificateName.first().isBlank()) settings.setCertificateName(user.displayName.trim())
        }
    }

    /** Loads the account's cloud copy (bounded wait), then opens the dashboard or the onboarding. */
    private suspend fun restoreAndContinue() {
        val c = cloud ?: return
        _postAuth.value = PostAuth.Restoring
        val result = runCatching { c.restoreAccount() }.getOrDefault(com.pypath.app.cloud.RestoreResult.FAILED)
        val existed = c.lastAuthExisted
        val onboarded = runCatching { progressRepo.progress.first().onboardingCompleted }.getOrDefault(false)
        val next = com.pypath.app.domain.AuthFlow.afterAuth(
            onboarded = onboarded,
            restored = result == com.pypath.app.cloud.RestoreResult.RESTORED,
            existed = existed,
            guestOnboarded = runCatching { c.guestCompletedOnboarding() }.getOrDefault(false),
        )
        val notRestored = result == com.pypath.app.cloud.RestoreResult.OFFLINE || result == com.pypath.app.cloud.RestoreResult.FAILED
        if (next == com.pypath.app.domain.AfterAuth.DASHBOARD && !onboarded) {
            // Existing account, cloud copy not loaded yet: remember that onboarding is done, so the
            // next app start does not show it either (only this flag; no settings are changed).
            runCatching { progressRepo.completeOnboarding() }
        }
        val notice = when {
            !notRestored || existed == false -> null
            result == com.pypath.app.cloud.RestoreResult.OFFLINE ->
                "You're signed in, but your saved progress could not be loaded (no internet connection). It will appear automatically when you're back online. Nothing in your account was changed."
            else ->
                "You're signed in, but your saved progress could not be loaded right now. PyPath will keep trying. Nothing in your account was changed."
        }
        go(
            when (next) {
                com.pypath.app.domain.AfterAuth.DASHBOARD -> com.pypath.app.ui.navigation.Routes.DASHBOARD
                com.pypath.app.domain.AfterAuth.ONBOARDING_INTRO -> com.pypath.app.ui.navigation.Routes.ONBOARDING
                com.pypath.app.domain.AfterAuth.ONBOARDING_SETUP -> com.pypath.app.ui.navigation.Routes.ONBOARDING_GOAL
            },
            notice,
        )
    }

    /** Signs out of the account on this phone. Progress, XP, streaks and settings stay on the phone. */
    fun signOut() = viewModelScope.launch {
        runCatching { cloud?.signOut() }
        runCatching { authRepo?.signOut() }
    }

    /** "Continue without signing in". */
    fun skipSignIn() = viewModelScope.launch { runCatching { authRepo?.skip() } }

    // ───────────── Access: guest / Privacy Policy / full ─────────────

    /**
     * Guest (first lesson only), signed in but Privacy Policy not yet accepted, or full access.
     * Derived from the encrypted Supabase session and the server-confirmed consent only (never from
     * progress). Null while loading. Without a cloud (previews and tests) access is full.
     */
    val access: StateFlow<Access?> =
        (cloud?.let { c ->
            combine(c.ready, c.sessionUserId, c.consents.agreed) { ready, uid, agreed -> AccessRules.derive(ready, uid, agreed) }
        } ?: MutableStateFlow<Access?>(Access.Full(LOCAL_USER)))
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, if (cloud == null) Access.Full(LOCAL_USER) else null)

    /** Current access, computed directly (no waiting for the flow), e.g. right after signing in. */
    fun accessNow(): Access? = cloud?.let { AccessRules.derive(it.ready.value, it.sessionUserId.value, it.consents.agreed.value) }
        ?: Access.Full(LOCAL_USER)

    /** "Agree and Continue" on the Privacy Policy. [onDone] gets null on success or an error message. */
    fun agreeToPrivacyPolicy(onDone: (String?) -> Unit) = viewModelScope.launch {
        val c = cloud ?: return@launch onDone(null)
        // Set before the consent is confirmed, so the screen behind the policy is the restore
        // screen (never a flash of onboarding or dashboard built from the not-yet-restored profile).
        val before = _postAuth.value
        _postAuth.value = PostAuth.Restoring
        val r = c.agreeToPrivacyPolicy(com.pypath.app.privacy.PrivacyPolicy.VERSION)
        if (r.isFailure) _postAuth.value = before
        onDone(
            r.exceptionOrNull()?.let { e ->
                if (e is java.io.IOException) "No internet connection. Connect to the internet and tap Agree and Continue again."
                else "Your agreement could not be saved (${e.message ?: "unknown error"}). Please try again."
            },
        )
        if (r.isSuccess) {
            restoreAndContinue()
            account.value?.user?.let { fillCertificateName(it) }
        }
    }

    // ───────────── Privacy & account: guest copy, consent withdrawal, account deletion ─────────────

    /** True when guest progress on this phone can be copied into the signed-in account. */
    val guestProgressAvailable: StateFlow<Boolean> = (cloud?.guestProgressAvailable ?: MutableStateFlow(false))
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Explicit "Add guest progress to this account". [onDone] gets null on success or an error message. */
    fun copyGuestProgress(onDone: (String?) -> Unit) = viewModelScope.launch {
        val c = cloud ?: return@launch onDone("Cloud backup is not available in this build.")
        val r = c.copyGuestProgressToAccount()
        onDone(r.exceptionOrNull()?.let { friendlyCloudError(it, "Guest progress was not copied") })
    }

    /** "Withdraw consent": recorded on the server, then syncing stops and the learner is signed out. */
    fun withdrawConsent(onDone: (String?) -> Unit) = viewModelScope.launch {
        val c = cloud ?: return@launch onDone("Consent storage is not available in this build.")
        val r = c.withdrawConsent()
        if (r.isSuccess) runCatching { authRepo?.signOut() }
        onDone(r.exceptionOrNull()?.let { friendlyCloudError(it, "Your withdrawal was not recorded, so nothing changed") })
    }

    /** "Delete my account" (server-side). [onDone] gets null only when the server confirmed the deletion. */
    fun deleteAccount(onDone: (String?) -> Unit) = viewModelScope.launch {
        val c = cloud ?: return@launch onDone("Account deletion is not available in this build.")
        val r = c.deleteAccount()
        if (r.isSuccess) runCatching { authRepo?.forgetAccount() }
        onDone(r.exceptionOrNull()?.let { deletionErrorMessage(it) })
        // Only after the server confirmed: back to the welcome / authentication screen (also on the next app start).
        if (r.isSuccess) go(com.pypath.app.ui.navigation.Routes.SIGN_IN, ACCOUNT_DELETED_NOTICE)
    }

    private fun friendlyCloudError(e: Throwable, what: String): String = when (e) {
        is java.io.IOException -> "No internet connection. $what. Connect to the internet and try again."
        is com.pypath.app.cloud.SupabaseException -> "$what (server error ${e.code}). Please try again later."
        else -> "$what: ${e.message ?: "unknown error"}"
    }

    val state: StateFlow<AppState> =
        combine(
            flow { emit(runCatching { courses.loadCourse() }) },
            progressRepo.progress,
            access.map { it !is Access.Full }.distinctUntilChanged(),
        ) { course, progress, guestLimited ->
            course.fold(
                onSuccess = { AppState.Ready(CourseSnapshot(it, progress, guestLimited)) },
                onFailure = { AppState.Error(it.message ?: "Could not load course") },
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AppState.Loading)

    val soundEffects: StateFlow<Boolean> = settings.soundEffects
        .onEach { sounds?.enabled = it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Name on the course certificate ("" until the learner types one). */
    val certificateName: StateFlow<String> = settings.certificateName
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setCertificateName(name: String) = viewModelScope.launch { settings.setCertificateName(name) }

    /** Batch 3: question history, XP per day and streak days. */
    val activity: StateFlow<ActivityData> = activityRepo.activity
        .stateIn(viewModelScope, SharingStarted.Eagerly, ActivityData())

    val dailyGoal: StateFlow<DailyGoal> = settings.dailyGoal
        .stateIn(viewModelScope, SharingStarted.Eagerly, DailyGoal.DEFAULT)

    init {
        // Batch 3 Part 3: line the reminder up again on every app start (safety net for alarms that
        // were cleared, e.g. after the app was force-stopped).
        viewModelScope.launch {
            runCatching {
                val prefs = settings.reminder.first()
                if (prefs.enabled) reminderControl?.schedule(prefs.minutes)
            }
        }
        viewModelScope.launch {
            // Upgrade older saved progress once the course is known (badges for finished levels, then
            // retroactive XP). Runs before the XP sync below, so old content never counts as "today".
            val course = runCatching { courses.loadCourse() }.getOrNull() ?: return@launch
            runCatching { progressRepo.migrate(course) }
            // Live one-time XP: whenever progress changes (quiz passed, level or project finished,
            // practical task completed), grant XP for events not yet in xpAwards and count it today.
            progressRepo.progress.distinctUntilChanged().collect {
                val gained = runCatching { progressRepo.syncXp(course) }.getOrDefault(0)
                if (gained > 0) updateActivity { a, today, goal -> ActivityUpdates.addXp(a, today, goal, gained) }
            }
        }
    }

    private fun updateActivity(block: (ActivityData, Long, Int) -> ActivityData) = viewModelScope.launch {
        runCatching {
            val today = Streaks.today()
            val goal = dailyGoal.value.xp
            activityRepo.update { block(it, today, goal) }
        }
    }

    /**
     * Records one quiz answer (question history, review pool, XP, today's activity) and returns the
     * XP it earns. No answer XP once the sub-level quiz has been passed (replays earn nothing).
     */
    fun recordAnswer(subLevelId: String, questionId: String, correct: Boolean, hintUsed: Boolean): Int {
        val snap = (state.value as? AppState.Ready)?.snapshot
        val eligible = snap?.subLevel(subLevelId)?.progress?.quiz?.passed != true
        val xp = ActivityUpdates.answerXp(activity.value, questionId, correct, hintUsed, eligible)
        updateActivity { a, today, goal -> ActivityUpdates.recordAnswer(a, questionId, subLevelId, correct, hintUsed, eligible, today, goal) }
        return xp
    }

    // ───────────── Batch 3 Part 3: daily reminder ─────────────

    val reminder: StateFlow<ReminderPrefs> = settings.reminder
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReminderPrefs())

    /** Turns the reminder on or off. Call with true only after notification permission was granted. */
    fun setReminderEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setReminderEnabled(enabled)
        if (enabled) reminderControl?.schedule(reminder.value.minutes) else reminderControl?.cancel()
    }

    fun setReminderMinutes(minutes: Int) = viewModelScope.launch {
        settings.setReminderMinutes(minutes)
        if (reminder.value.enabled) reminderControl?.schedule(Reminders.clampMinutes(minutes))
    }

    /** A lesson page finished or a project stage answered (counts for the streak when the goal is off). */
    fun recordAction() = updateActivity { a, today, goal -> ActivityUpdates.recordAction(a, today, goal) }

    fun setDailyGoal(goal: DailyGoal) = viewModelScope.launch {
        settings.setDailyGoal(goal)
        // Today may now meet the new goal. Days already counted stay counted.
        runCatching { activityRepo.update { ActivityUpdates.evaluateDay(it, Streaks.today(), goal.xp) } }
    }

    // ───────────── Batch 3: spaced review ─────────────

    /**
     * Starts a review: due questions, or with [scope] the pool questions of those sub-levels
     * ("weakest topics"). Calls [onReady] with false when there is nothing to review.
     */
    fun startReview(scope: List<String>, onReady: (Boolean) -> Unit) {
        val snap = (state.value as? AppState.Ready)?.snapshot
        if (snap == null) { onReady(false); return }
        val index = Review.index(snap)
        viewModelScope.launch {
            var started = false
            runCatching {
                val today = Streaks.today()
                activityRepo.update { a ->
                    val ids = Review.pick(a, index, today, scope.toSet().takeIf { it.isNotEmpty() })
                    started = ids.isNotEmpty()
                    if (started) ActivityUpdates.startReview(a, ids, scope, today) else a
                }
            }
            onReady(started)
        }
    }

    /** One review answer: moves the question between Leitner boxes and advances the saved session. */
    fun recordReviewAnswer(subLevelId: String, questionId: String, correct: Boolean, hintUsed: Boolean) =
        updateActivity { a, today, goal -> ActivityUpdates.recordReviewAnswer(a, questionId, subLevelId, correct, hintUsed, today, goal) }

    /** Finishes the saved session early (only used when its remaining questions vanished from the course). */
    fun finishReview() = updateActivity { a, today, goal -> ActivityUpdates.finishReview(a, today, goal) }

    /** The summary was closed. */
    fun closeReview() = updateActivity { a, _, _ -> ActivityUpdates.clearReview(a) }

    /** The streak milestone celebration was shown. */
    fun consumeMilestone() = viewModelScope.launch { runCatching { activityRepo.update { ActivityUpdates.consumeMilestone(it) } } }

    fun completeOnboarding() = viewModelScope.launch { progressRepo.completeOnboarding() }

    // ───────────── Final frontend polish: onboarding goal + introductory Premium offer ─────────────

    /** Daily learning-time goal in minutes (null when never chosen, e.g. learners from before this update). */
    val learningMinutes: StateFlow<Int?> = settings.learningMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setLearningMinutes(minutes: Int) = viewModelScope.launch { runCatching { settings.setLearningMinutes(minutes) } }

    /** True from the end of the new onboarding until the first lesson is completed. */
    val firstLessonPending: StateFlow<Boolean> = settings.firstLessonPending
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val introOfferShown: StateFlow<Boolean> = settings.introOfferShown
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * End of the new onboarding: saves the time goal, suggests a matching XP goal (changeable any
     * time), marks onboarding as done and remembers that the first lesson comes next.
     */
    fun finishOnboarding(minutes: Int) = viewModelScope.launch {
        runCatching { settings.setLearningMinutes(minutes) }
        runCatching { setDailyGoal(com.pypath.app.domain.LearningTime.suggestedXpGoal(minutes)).join() }
        runCatching { settings.setFirstLessonPending(true) }
        progressRepo.completeOnboarding()
    }

    /** The first lesson was completed: the onboarding journey is over. */
    fun firstLessonDone() = viewModelScope.launch { runCatching { settings.setFirstLessonPending(false) } }

    /** The introductory offer was shown once; it never appears again. */
    fun markIntroOfferShown() = viewModelScope.launch { runCatching { settings.setIntroOfferShown(true) } }

    fun openSubLevel(id: String) = viewModelScope.launch { progressRepo.setCurrentSubLevel(id) }

    fun completeLesson(id: String, steps: List<StepType>) =
        viewModelScope.launch { progressRepo.markStepCompleted(id, StepType.LEARN, steps) }

    fun recordQuiz(id: String, score: Int, total: Int, passed: Boolean, steps: List<StepType>) =
        viewModelScope.launch { progressRepo.recordQuizResult(id, score, total, passed, steps) }

    fun markBriefingSeen(id: String) = viewModelScope.launch { progressRepo.markBriefingSeen(id) }

    fun recordProjectMiss(id: String) = viewModelScope.launch { progressRepo.recordProjectMiss(id) }

    fun completeProjectStage(id: String, stageId: String, allStageIds: List<String>, steps: List<StepType>) =
        viewModelScope.launch { progressRepo.completeProjectStage(id, stageId, allStageIds, steps) }

    fun earnBadge(levelId: String) = viewModelScope.launch { progressRepo.earnBadge(levelId) }

    /** Level number whose badge celebration is on screen, or null. */
    var celebratingLevel by mutableStateOf<Int?>(null)
        private set

    /** Shows the celebration once: the badge is saved as soon as it starts, so it never replays. */
    fun startCelebration(level: Level) {
        if (celebratingLevel != null) return
        celebratingLevel = level.number
        earnBadge(level.id)
    }

    fun endCelebration() { celebratingLevel = null }

    fun setSoundEffects(enabled: Boolean) = viewModelScope.launch { settings.setSoundEffects(enabled) }

    /** Clears course progress, badges, XP, question history, streaks and day tallies. Settings stay. */
    fun resetProgress() = viewModelScope.launch {
        runCatching { cloud?.noteReset() }
        progressRepo.reset()
        activityRepo.reset()
        // Batch 3 Part 3: Reset progress also turns the daily reminder off and cancels it.
        runCatching { settings.setReminderEnabled(false) }
        reminderControl?.cancel()
    }

    companion object {
        /** User id used when there is no cloud at all (previews and tests). */
        const val LOCAL_USER = "local"

        /** What the learner reads when deletion did not succeed. Never claims a deletion the server did not confirm. */
        fun deletionErrorMessage(e: Throwable): String = when (val d = (e as? com.pypath.app.cloud.AccountDeletionException)?.result) {
            com.pypath.app.cloud.AccountDeletion.NotDeployed ->
                "Account deletion is not set up on the PyPath server yet. Your account was NOT deleted. Please contact the PyPath team using the details in the Privacy Policy."
            com.pypath.app.cloud.AccountDeletion.Unauthorized ->
                "Your sign-in is no longer valid, so your account could not be deleted. Sign in again with Login with Google and retry. (If PyPath then says \"Account not found\", the account is already deleted.)"
            is com.pypath.app.cloud.AccountDeletion.Failed -> when {
                d.stage == com.pypath.app.cloud.AccountDeletionApi.GATEWAY_REJECTED || d.stage == "server_not_configured" ->
                    "Account deletion is not set up correctly on the PyPath server. Your account was NOT deleted. Please contact the PyPath team using the details in the Privacy Policy."
                d.stage == "cloud_data" ->
                    "The server could not delete your cloud data, so your account was NOT deleted. Please try again."
                d.stage == "auth_user" ->
                    "Your cloud data was deleted, but the account itself could not be deleted yet. Please try again (retrying is safe)."
                d.stage == "verify" || d.code == 503 ->
                    "The PyPath server is not available right now. Your account was NOT deleted. Please try again later."
                else ->
                    "The server could not finish deleting your account (error ${d.code}). Your account may NOT be fully deleted. Please try again."
            }
            else -> when (e) {
                is java.net.UnknownHostException, is java.net.ConnectException, is java.net.NoRouteToHostException ->
                    "No internet connection. Your account was NOT deleted. Connect to the internet and try again."
                is java.io.IOException ->
                    "The connection was interrupted, so PyPath could not confirm the deletion. Your account may NOT be deleted. Please try again when you're online (retrying is safe)."
                is com.pypath.app.cloud.SupabaseException -> "Your account was NOT deleted (server error ${e.code}). Please try again later."
                else -> "Your account was NOT deleted: ${e.message ?: "unknown error"}"
            }
        }
        const val ACCOUNT_DELETED_NOTICE =
            "Your PyPath account was permanently deleted, together with its cloud backup and privacy consent records. You can continue as a guest or create a new account with Sign in with Google."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PyPathApp
                val c = app.container
                AppViewModel(
                    c.courseRepository, c.progressRepository, c.settingsRepository, c.sounds, c.activityRepository,
                    AlarmReminderControl(app),
                    c.authRepository,
                    c.premiumRepository,
                    c.cloudSync,
                )
            }
        }
    }
}
