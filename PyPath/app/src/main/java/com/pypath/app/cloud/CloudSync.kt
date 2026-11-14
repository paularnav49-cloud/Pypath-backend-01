package com.pypath.app.cloud

import com.pypath.app.data.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** Result of "Sign in with Google" / "Login with Google" after the Google account was picked. */
sealed interface GoogleAuthOutcome {
    /** Supabase accepted the Google token. [existed] = the account was there before (null = not checked). */
    data class SignedIn(val session: SupabaseSession, val existed: Boolean?) : GoogleAuthOutcome
    /** Login only: no PyPath account exists for this Google identity. Nothing was created. */
    data object AccountNotFound : GoogleAuthOutcome
}

/** The server-side account check did not give a usable answer. */
class AccountCheckException(val check: AccountCheck, val mode: AuthMode) : Exception("Account check failed: $check")

/** The server did not delete the account (nothing on the phone was changed). */
class AccountDeletionException(val result: AccountDeletion) : Exception("Account deletion failed: $result")

/** Result of loading a signed-in account's cloud copy right after sign-in ([CloudSync.restoreAccount]). */
enum class RestoreResult {
    /** The cloud copy was downloaded and merged into this phone's (account) profile. */
    RESTORED,
    /** No connection (or the server did not answer in time): nothing was merged yet; a retry is scheduled. */
    OFFLINE,
    /** The server answered with an error: nothing was merged yet; a retry is scheduled. */
    FAILED,
    /** The current Privacy Policy is not accepted yet: nothing is synced until it is. */
    NO_CONSENT,
    /** Not signed in (any more). */
    SIGNED_OUT,
}

/** What the account dialog shows about the cloud backup. */
sealed interface SyncStatus {
    /** Not signed in: everything stays on this phone (guest mode). */
    data object Off : SyncStatus
    data object Syncing : SyncStatus
    data class Synced(val at: Long) : SyncStatus
    /** No connection; changes are kept on the phone and sent later. */
    data object Offline : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

/**
 * Cloud backup of progress, activity/streaks, saved programs and settings to the learner's own
 * `user_cloud_state` row in Supabase.
 *
 * Safety rules:
 *  - Only for signed-in learners; guests never sync.
 *  - Guest data and account data never mix. The phone's live stores hold ONE profile at a time
 *    ([SyncMeta.owner]: an account id, or null = guest). When an account becomes active, guest data
 *    is set aside in [CloudLocalData.GUEST_SLOT] (never uploaded); on sign-out it comes back. A null
 *    owner is never treated as permission to merge. Guest progress reaches an account only through
 *    [copyGuestProgressToAccount], an explicit action of the signed-in learner.
 *  - Cloud data is never overwritten blindly: every sync downloads the row, merges it with the
 *    phone's data ([CloudMerge]), and writes back with an optimistic revision check, so a change
 *    made meanwhile by another phone is merged, not lost.
 *  - Local data always stays on the phone; offline changes are sent later.
 *  - Data of one account is never uploaded to another account (see [SyncMeta.owner]).
 *  - Premium entitlements are never synced or trusted from the cloud row.
 */
class CloudSync(
    private val auth: SupabaseAuth,
    private val api: CloudStateApi,
    sessions: SessionStore,
    private val local: CloudLocalData,
    private val meta: SyncMetaStore,
    private val accounts: AuthRepository?,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Server-side check that keeps Sign up and Login separate (google-account-check Edge Function). */
    private val accountCheck: AccountCheckApi? = null,
    /** `public.privacy_consents` (null in older tests: consent is then never confirmed from the server). */
    private val consentApi: ConsentApi? = null,
    /** Phone cache of server-confirmed Privacy Policy consent. */
    val consents: ConsentStore = MemoryConsentStore(),
    /** Server-side account deletion (delete-account Edge Function); null = not available. */
    private val deletion: AccountDeletionApi? = null,
) {
    /** Every session change goes through this wrapper, so [sessionUserId] is always current. */
    private val sessions = TrackedSessionStore(sessions)

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Off)
    val status: StateFlow<SyncStatus> = _status

    /** Supabase user id of the signed-in learner, or null for a guest. */
    val sessionUserId: StateFlow<String?> get() = sessions.userId

    private val _ready = MutableStateFlow(false)
    /** True once the saved session was read and reconciled at start (until then, the guest/sign-in state is unknown). */
    val ready: StateFlow<Boolean> = _ready

    private val _guestOffer = MutableStateFlow(false)
    /** True when this phone holds guest progress that the signed-in learner may copy into the account. */
    val guestProgressAvailable: StateFlow<Boolean> = _guestOffer

    private val mutex = Mutex()
    private val requests = Channel<Boolean>(Channel.CONFLATED)
    private var started = false
    private var retryJob: Job? = null
    private var retryDelay = FIRST_RETRY_MS
    private lateinit var scope: CoroutineScope

    // ───────────── Sign-in / sign-out ─────────────

    /**
     * Exchanges a Google ID token (used once, never stored) for a Supabase session and stores the
     * session encrypted. The learner counts as signed in only if this succeeds.
     */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String): Result<SupabaseSession> = runCatching {
        val session = auth.signInWithGoogle(idToken, rawNonce)
        confirmConsentFromServer(session)
        sessions.save(session)
        mutex.withLock { activateLocked(session.userId) }
        refreshGuestOffer()
        session
    }

    /**
     * "Sign in with Google" ([AuthMode.SIGN_UP]) or "Login with Google" ([AuthMode.LOG_IN]).
     *
     * The server-side check (Edge Function, which verifies the Google token itself) runs first:
     *  - LOG_IN: the Google token is exchanged for a Supabase session ONLY if an account with this
     *    Google identity already exists; otherwise [GoogleAuthOutcome.AccountNotFound] and nothing is
     *    created. If the check is unavailable, login fails (it never falls back to silent sign-up).
     *  - SIGN_UP: creates the account, or opens the existing one (Supabase never makes a duplicate
     *    for the same Google identity). If the check function is not deployed, sign-up still works.
     * Consent already recorded on the server is looked up before the session is saved, so a learner
     * who agreed before is not asked again.
     */
    suspend fun authenticateWithGoogle(idToken: String, rawNonce: String, mode: AuthMode): Result<GoogleAuthOutcome> = runCatching {
        val check = accountCheck?.check(idToken, rawNonce, mode)
        val existed: Boolean? = when (check) {
            is AccountCheck.Checked -> check.exists
            null, AccountCheck.NotDeployed -> if (mode == AuthMode.LOG_IN) throw AccountCheckException(check ?: AccountCheck.NotDeployed, mode) else null
            else -> throw AccountCheckException(check, mode)
        }
        if (mode == AuthMode.LOG_IN && existed != true) return@runCatching GoogleAuthOutcome.AccountNotFound
        val session = auth.signInWithGoogle(idToken, rawNonce)
        lastAuthExisted = existed
        confirmConsentFromServer(session)
        sessions.save(session)
        // The account's own data goes into the live stores; guest data is set aside (never uploaded).
        mutex.withLock { activateLocked(session.userId) }
        refreshGuestOffer()
        GoogleAuthOutcome.SignedIn(session, existed)
    }

    /**
     * What the server-side account check said during the last sign-in in this app run: true = the
     * account existed before (always true for Login with Google), false = newly created, null =
     * unknown (check not deployed, or no sign-in in this run). Comes from the server answer only.
     */
    @Volatile
    var lastAuthExisted: Boolean? = null
        private set

    /**
     * Loads the signed-in account's cloud copy NOW (download + safe merge + upload of the merge),
     * so the app can decide where to go from the account's real data (for example "onboarding
     * already done") instead of the still-empty profile right after sign-in. Never overwrites the
     * cloud copy with empty data: the normal merge rules apply. Waits at most [timeoutMs]; when it
     * does not succeed, a background retry is scheduled.
     */
    suspend fun restoreAccount(timeoutMs: Long = RESTORE_MS): RestoreResult {
        if (sessions.load() == null) return RestoreResult.SIGNED_OUT
        val st = withTimeoutOrNull(timeoutMs) {
            runCatching { syncNow(force = true) }.getOrElse { if (it is IOException) SyncStatus.Offline else SyncStatus.Failed(it.message ?: "Sync failed") }
        }
        val r = when (st) {
            is SyncStatus.Synced -> RestoreResult.RESTORED
            null, SyncStatus.Offline -> RestoreResult.OFFLINE
            is SyncStatus.Failed, SyncStatus.Syncing -> RestoreResult.FAILED
            SyncStatus.Off -> if (sessions.userId.value == null) RestoreResult.SIGNED_OUT else RestoreResult.NO_CONSENT
        }
        if (r == RestoreResult.OFFLINE || r == RestoreResult.FAILED) {
            if (st == null) _status.value = SyncStatus.Offline
            requestSync(immediate = true)
        }
        return r
    }

    /** True when the guest profile kept on this phone finished the onboarding questions (read only; nothing is copied). */
    suspend fun guestCompletedOnboarding(): Boolean =
        runCatching { local.takeBackup(CloudLocalData.GUEST_SLOT)?.progress?.data?.onboardingCompleted == true }.getOrDefault(false)

    // ───────────── Privacy Policy consent ─────────────

    /**
     * Reads the learner's newest consent event and updates the phone cache. A withdrawal or an
     * outdated version clears the cache (the policy is shown again). Offline: cache unchanged.
     */
    private suspend fun confirmConsentFromServer(session: SupabaseSession) {
        val api = consentApi ?: return
        val versions = runCatching { api.agreedVersions(session) }.getOrNull() ?: return
        val valid = versions.filter { com.pypath.app.privacy.PrivacyPolicy.isConsentValid(it) }
        if (valid.isNotEmpty()) consents.markAgreed(session.userId, valid.max()) else consents.forget(session.userId)
    }

    /**
     * "Withdraw consent": records a withdrawal event on the server (own row only, RLS), then stops
     * syncing and signs out on this phone. The account and its cloud backup stay stored until the
     * learner deletes the account; logging in again shows the policy again. Fails without changing
     * anything when the server did not accept the withdrawal.
     */
    suspend fun withdrawConsent(): Result<Unit> = runCatching {
        val api = consentApi ?: error("Consent storage is not available.")
        var session = sessions.load() ?: error("You are not signed in.")
        session = fresh(session) ?: error("Please sign in again.")
        val version = consents.agreed.value?.get(session.userId) ?: com.pypath.app.privacy.PrivacyPolicy.VERSION
        try {
            api.recordWithdrawal(session, version)
        } catch (e: SupabaseException) {
            if (e.code != 401) throw e
            val again = auth.refresh(session.refreshToken)
            sessions.save(again)
            session = again
            api.recordWithdrawal(session, version)
        }
        // From now on nothing is backed up for this account (the sign-out below skips its final sync).
        consents.forget(session.userId)
        signOut()
    }

    /**
     * "Delete my account": the delete-account Edge Function deletes, on the server, this learner's
     * cloud backup and Supabase Auth user (consent records go with it). Only after the server
     * confirmed is the account removed from the phone: its data is not kept aside, and the guest
     * profile comes back. On any failure nothing on the phone changes and the error is returned.
     */
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val api = deletion ?: throw AccountDeletionException(AccountDeletion.NotDeployed)
        // Holding the lock: no sync can run (and re-create data) while the account is being deleted.
        val uid = mutex.withLock {
            var session = sessions.load() ?: error("You are not signed in.")
            session = fresh(session) ?: error("Please sign in again, then delete your account.")
            var r = api.delete(session)
            if (r == AccountDeletion.Unauthorized) {
                val again = runCatching { auth.refresh(session.refreshToken) }.getOrNull() ?: throw AccountDeletionException(r)
                sessions.save(again)
                session = again
                r = api.delete(session)
            }
            if (r != AccountDeletion.Deleted) throw AccountDeletionException(r)
            sessions.clear()
            lastAuthExisted = null
            retryJob?.cancel()
            restoreGuestLocked(keepAccountData = false)
            local.deleteBackup(session.userId)
            local.forgetGuestCopied(session.userId)
            session.userId
        }
        consents.forget(uid)
        runCatching { accounts?.signOut() }
        _status.value = SyncStatus.Off
    }

    // ───────────── Guest profile and account profiles on this phone ─────────────

    /** Puts [uid]'s profile into the live stores. Caller holds [mutex]. */
    private suspend fun activateLocked(uid: String) {
        val m = meta.load()
        if (m.owner == uid) return
        val current = local.read(m)
        val previous = m.owner
        if (previous == null) {
            // Guest-mode data: set aside on this phone only. It is never uploaded or merged into an
            // account automatically (a missing owner is not permission to merge).
            local.saveBackup(CloudLocalData.GUEST_SLOT, current)
        } else if (CloudCodec.fingerprint(current) != m.fingerprint) {
            // Another account's unsynced changes: kept aside for that account only.
            local.saveBackup(previous, current)
        }
        // Start from an empty profile; the account's cloud copy (and anything it left on this
        // phone earlier) is merged in by the next sync.
        local.replaceWith(null)
        meta.update { SyncMeta(owner = uid) }
    }

    /**
     * Puts the guest profile back into the live stores (after sign-out, expiry or deletion). The
     * account's unsynced changes are kept aside for that account only ([keepAccountData]), or
     * dropped when the account was deleted. Caller holds [mutex].
     */
    private suspend fun restoreGuestLocked(keepAccountData: Boolean) {
        val m = meta.load()
        val owner = m.owner ?: return
        val current = local.read(m)
        if (keepAccountData) {
            if (CloudCodec.fingerprint(current) != m.fingerprint) local.saveBackup(owner, current)
        } else {
            local.deleteBackup(owner)
            local.forgetGuestCopied(owner)
        }
        val guest = local.takeBackup(CloudLocalData.GUEST_SLOT)
        local.replaceWith(guest)
        val resetAt = guest?.let { maxOf(it.progress.resetAt, it.activity.resetAt) } ?: 0
        meta.update { SyncMeta(owner = null, resetAt = resetAt) }
        if (guest != null) local.deleteBackup(CloudLocalData.GUEST_SLOT)
        _guestOffer.value = false
    }

    /** Re-checks whether guest progress can be offered to the signed-in learner. */
    suspend fun refreshGuestOffer() {
        val uid = sessions.userId.value
        if (uid == null) { _guestOffer.value = false; return }
        val guest = runCatching { local.takeBackup(CloudLocalData.GUEST_SLOT) }.getOrNull()
        _guestOffer.value = guest != null && hasLearning(guest) &&
            runCatching { local.guestCopiedFingerprint(uid) }.getOrNull() != CloudCodec.fingerprint(guest)
    }

    private fun hasLearning(s: CloudSnapshot): Boolean =
        s.progress.data.subLevels.isNotEmpty() || s.progress.data.earnedBadges.isNotEmpty() ||
            s.progress.data.completedTasks.isNotEmpty() || s.activity.data.questions.isNotEmpty() ||
            s.activity.data.days.isNotEmpty() ||
            s.programs.data.programs.isNotEmpty()

    /**
     * Explicit, learner-confirmed action: adds the guest progress kept on this phone to the
     * signed-in account (lessons, quiz results, XP history and saved programs are merged; nothing in
     * the account is removed and the account's settings are not changed). Before anything is copied
     * the server must accept the session (proves which account receives it). The guest copy stays on
     * the phone.
     */
    suspend fun copyGuestProgressToAccount(): Result<Unit> = runCatching {
        var session = sessions.load() ?: error("Please sign in first.")
        if (consentApi != null && !com.pypath.app.privacy.PrivacyPolicy.isConsentValid(consents.agreed.value?.get(session.userId))) {
            error("Please agree to the Privacy Policy first.")
        }
        session = fresh(session) ?: error("Please sign in again.")
        // Destination check: the server answers only for the account this token belongs to (RLS).
        api.fetch(session)
        mutex.withLock {
            val uid = session.userId
            if (sessions.load()?.userId != uid) error("The signed-in account changed. Nothing was copied.")
            activateLocked(uid)
            val m = meta.load()
            val guest = local.takeBackup(CloudLocalData.GUEST_SLOT) ?: error("There is no guest progress on this phone.")
            val add = guest.copy(
                progress = guest.progress.copy(resetAt = m.resetAt),
                activity = guest.activity.copy(resetAt = m.resetAt),
                settings = SettingsEnvelope(),
            )
            local.mergeIn(add, m.resetAt)
            local.markGuestCopied(uid, CloudCodec.fingerprint(guest))
        }
        _guestOffer.value = false
        requestSync(immediate = true)
    }

    /**
     * "Agree and Continue": stores the consent record on the server (own row, RLS-protected; the
     * server sets the time). Only after the server accepted it is the phone cache updated and the
     * dashboard opened. Fails offline (the policy stays on screen).
     */
    suspend fun agreeToPrivacyPolicy(version: String): Result<Unit> = runCatching {
        val api = consentApi ?: error("Consent storage is not available.")
        var session = sessions.load() ?: error("Please sign in again.")
        session = fresh(session) ?: error("Please sign in again.")
        try {
            api.recordAgreement(session, version)
        } catch (e: SupabaseException) {
            if (e.code != 401 && e.code != 403) throw e
            val again = auth.refresh(session.refreshToken)
            sessions.save(again)
            session = again
            api.recordAgreement(session, version)
        }
        consents.markAgreed(session.userId, version)
    }

    /** Checks the server copy of the consent for the signed-in learner (used at start). */
    suspend fun refreshConsent() {
        val session = sessions.load() ?: return
        val s = runCatching { fresh(session) }.getOrNull() ?: return
        confirmConsentFromServer(s)
    }

    /** Final best-effort sync, then ends the Supabase session. Local data stays on the phone. */
    suspend fun signOut() {
        val session = sessions.load()
        if (session != null) {
            withTimeoutOrNull(SIGN_OUT_SYNC_MS) { runCatching { syncNow() } }
            runCatching { auth.signOut(session.accessToken) }
        }
        sessions.clear()
        retryJob?.cancel()
        // The account's data leaves the live stores (unsynced changes are kept aside for that account
        // only) and the guest profile comes back, so the next user of this phone never sees it.
        mutex.withLock { restoreGuestLocked(keepAccountData = true) }
        _status.value = SyncStatus.Off
    }

    suspend fun isSignedIn(): Boolean = sessions.load() != null

    /** "Reset progress" pressed: remembered so the reset also reaches the cloud and other phones. */
    suspend fun noteReset() {
        val now = clock()
        meta.update { it.copy(resetAt = maxOf(now, it.resetAt + 1)) }
    }

    // ───────────── Scheduling ─────────────

    /** Starts background syncing (main process only). Safe to call more than once. */
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        this.scope = scope
        scope.launch {
            try {
                runCatching { consents.load() }
                runCatching { sessions.load() }
                runCatching { reconcileAccount() }
                // The live stores must hold the signed-in account's profile, or the guest profile
                // when nobody is signed in (e.g. an account signed out by an older app version).
                runCatching {
                    mutex.withLock {
                        val uid = sessions.load()?.userId
                        if (uid != null) activateLocked(uid) else restoreGuestLocked(keepAccountData = true)
                    }
                }
                runCatching { refreshGuestOffer() }
                // A signed-in learner without confirmed consent on this phone (e.g. after this
                // update, or a new phone): ask the server first, so someone who already agreed is
                // not shown the policy again. Short timeout; offline it simply shows the policy.
                val uid = sessions.userId.value
                if (uid != null && !com.pypath.app.privacy.PrivacyPolicy.isConsentValid(consents.agreed.value?.get(uid))) {
                    withTimeoutOrNull(CONSENT_CHECK_MS) { runCatching { refreshConsent() } }
                }
            } finally {
                _ready.value = true
            }
            requests.trySend(true)
            // Later on: confirm a cached consent against the server (source of truth).
            if (sessions.userId.value != null) runCatching { refreshConsent() }
        }
        scope.launch {
            for (immediate in requests) {
                if (!immediate) delay(DEBOUNCE_MS)
                val result = runCatching { syncNow(force = immediate) }.getOrElse { SyncStatus.Failed(it.message ?: "Sync failed") }
                scheduleRetry(result)
            }
        }
        scope.launch {
            local.changes.collect { requests.trySend(false) }
        }
    }

    /** Asks for a sync soon ([immediate] = now, e.g. when the app goes to the background). */
    fun requestSync(immediate: Boolean = false) {
        if (started) requests.trySend(immediate)
    }

    private fun scheduleRetry(result: SyncStatus) {
        retryJob?.cancel()
        if (result is SyncStatus.Offline || result is SyncStatus.Failed) {
            val wait = retryDelay
            retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_MS)
            retryJob = scope.launch { delay(wait); requests.trySend(true) }
        } else {
            retryDelay = FIRST_RETRY_MS
        }
    }

    /**
     * A Google account saved by an older app version (before cloud sign-in) has no Supabase session:
     * that learner is shown as signed out (all data stays) and can sign in again.
     */
    private suspend fun reconcileAccount() {
        val repo = accounts ?: return
        val state = runCatching { repo.account.first() }.getOrNull() ?: return
        val session = sessions.load()
        if (state.user != null && session == null) runCatching { repo.signOut() }
        if (state.user == null && session != null) sessions.clear()
    }

    // ───────────── One sync pass ─────────────

    /** Runs one full sync. [force] = false skips the network when nothing changed locally. */
    suspend fun syncNow(force: Boolean = true): SyncStatus = mutex.withLock {
        var session = sessions.load() ?: run {
            // Signed out (e.g. the session expired): make sure the guest profile is the live one.
            restoreGuestLocked(keepAccountData = true)
            return@withLock SyncStatus.Off.also { _status.value = it }
        }
        // Nothing is backed up before the learner agreed to the current Privacy Policy.
        if (consentApi != null && !com.pypath.app.privacy.PrivacyPolicy.isConsentValid(consents.agreed.value?.get(session.userId))) {
            return@withLock SyncStatus.Off.also { _status.value = it }
        }
        if (!force) {
            val m = meta.load()
            if (m.owner == session.userId && m.fingerprint.isNotEmpty() &&
                CloudCodec.fingerprint(local.read(m)) == m.fingerprint
            ) return@withLock _status.value
        }
        _status.value = SyncStatus.Syncing
        val result = try {
            session = fresh(session) ?: return@withLock SyncStatus.Off.also { _status.value = it }
            pass(session)
        } catch (e: IOException) {
            SyncStatus.Offline
        } catch (e: SupabaseException) {
            if (e.code == 401 || e.code == 403) {
                // Token no longer accepted: try one refresh, else ask the learner to sign in again.
                val again = runCatching { auth.refresh(session.refreshToken) }.getOrNull()
                if (again != null) {
                    sessions.save(again)
                    runCatching { pass(again) }.getOrElse { failure(it) }
                } else {
                    expire()
                    SyncStatus.Failed("Please sign in again to keep backing up your progress.")
                }
            } else failure(e)
        } catch (e: Exception) {
            failure(e)
        }
        if (sessions.userId.value == null) restoreGuestLocked(keepAccountData = true)
        _status.value = result
        result
    }

    private fun failure(e: Throwable): SyncStatus =
        if (e is IOException) SyncStatus.Offline else SyncStatus.Failed(friendly(e))

    private fun friendly(e: Throwable): String = when (e) {
        is SupabaseException -> "Cloud backup failed (${e.code}): ${e.message}"
        else -> "Cloud backup failed: ${e.message ?: e.javaClass.simpleName}"
    }

    /** Returns a usable session, refreshing it when it is about to expire; null if it was revoked. */
    private suspend fun fresh(session: SupabaseSession): SupabaseSession? {
        if (!session.expiresSoon(clock() / 1000)) return session
        return try {
            auth.refresh(session.refreshToken).also { sessions.save(it) }
        } catch (e: SupabaseException) {
            if (e.code in 400..499) { expire(); null } else throw e
        }
    }

    /** The session can no longer be used: sign out on this phone (data stays, kept for that account). */
    private suspend fun expire() {
        sessions.clear()
        runCatching { accounts?.signOut() }
        // The next sync pass puts the guest profile back (it needs the lock this caller may hold).
        requestSync(immediate = true)
    }

    private suspend fun pass(session: SupabaseSession): SyncStatus {
        val uid = session.userId
        // The server is the source of truth for consent: if it was withdrawn (e.g. on another phone)
        // or is outdated, nothing is uploaded and the policy is shown again.
        val consentServer = consentApi
        if (consentServer != null) {
            val ev = consentServer.latest(session)
            if (ev == null || !ev.agreed || !com.pypath.app.privacy.PrivacyPolicy.isConsentValid(ev.version)) {
                consents.forget(uid)
                return SyncStatus.Off
            }
        }
        repeat(MAX_ATTEMPTS) {
            val row = api.fetch(session)
            if (row != null && !row.readable) {
                return SyncStatus.Failed("Your cloud backup was saved by a newer PyPath version. Update the app to keep syncing.")
            }
            var m = meta.load()
            if (m.owner != uid) {
                // The live stores hold the guest profile (owner null) or another account's data: set it
                // aside (never uploaded here) and continue with this account's own profile.
                activateLocked(uid)
                m = meta.load()
            }
            // Data this account left on the phone earlier (kept aside while another account was used).
            val backup = local.takeBackup(uid)
            if (backup != null) local.mergeIn(backup, m.resetAt)
            if (row != null) {
                local.mergeIn(row.snapshot, m.resetAt)
                val remoteReset = maxOf(row.snapshot.progress.resetAt, row.snapshot.activity.resetAt)
                if (remoteReset > m.resetAt) meta.update { it.copy(resetAt = remoteReset) }
            }
            meta.update { it.copy(owner = uid) }
            m = meta.load()
            val upload = local.read(m)
            val fp = CloudCodec.fingerprint(upload)
            if (row != null && CloudCodec.fingerprint(row.snapshot) == fp) {
                done(row.revision, fp, uid, backup != null)
                return SyncStatus.Synced(clock())
            }
            val written = if (row == null) api.insert(session, upload) else api.update(session, upload, row.revision)
            if (written != null) {
                done(written.revision, fp, uid, backup != null)
                return SyncStatus.Synced(clock())
            }
            // Someone else wrote first: download again, merge again, retry.
        }
        return SyncStatus.Failed("Cloud backup is busy on another device. Will try again.")
    }

    private suspend fun done(revision: Long, fp: String, uid: String, hadBackup: Boolean) {
        meta.update { it.copy(owner = uid, revision = revision, fingerprint = fp, lastSyncedAt = clock()) }
        if (hadBackup) local.deleteBackup(uid)
    }

    companion object {
        const val DEBOUNCE_MS = 4_000L
        const val FIRST_RETRY_MS = 30_000L
        const val MAX_RETRY_MS = 15 * 60_000L
        const val SIGN_OUT_SYNC_MS = 8_000L
        /** Longest wait for the account's cloud copy after sign-in before the app continues without it. */
        const val RESTORE_MS = 20_000L
        const val MAX_ATTEMPTS = 4
        const val CONSENT_CHECK_MS = 4_000L
    }
}
