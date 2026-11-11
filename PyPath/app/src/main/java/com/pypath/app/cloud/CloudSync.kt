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
    private val sessions: SessionStore,
    private val local: CloudLocalData,
    private val meta: SyncMetaStore,
    private val accounts: AuthRepository?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Off)
    val status: StateFlow<SyncStatus> = _status

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
        sessions.save(session)
        session
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
            reconcileAccount()
            requests.trySend(true)
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
        var session = sessions.load() ?: return@withLock SyncStatus.Off.also { _status.value = it }
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

    /** The session can no longer be used: sign out on this phone (data stays). */
    private suspend fun expire() {
        sessions.clear()
        runCatching { accounts?.signOut() }
    }

    private suspend fun pass(session: SupabaseSession): SyncStatus {
        val uid = session.userId
        repeat(MAX_ATTEMPTS) {
            val row = api.fetch(session)
            if (row != null && !row.readable) {
                return SyncStatus.Failed("Your cloud backup was saved by a newer PyPath version. Update the app to keep syncing.")
            }
            var m = meta.load()
            if (m.owner != null && m.owner != uid) {
                // This phone holds another account's data: keep any unsynced part aside (never upload it here).
                val theirs = local.read(m)
                if (CloudCodec.fingerprint(theirs) != m.fingerprint) local.saveBackup(m.owner!!, theirs)
                local.replaceWith(row?.snapshot)
                val resetAt = row?.snapshot?.let { maxOf(it.progress.resetAt, it.activity.resetAt) } ?: 0
                meta.update { SyncMeta(owner = uid, resetAt = resetAt) }
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
        const val MAX_ATTEMPTS = 4
    }
}
