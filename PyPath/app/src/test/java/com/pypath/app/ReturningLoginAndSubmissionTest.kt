package com.pypath.app

import com.pypath.app.cloud.AccountCheckApi
import com.pypath.app.cloud.AccountDeletion
import com.pypath.app.cloud.AccountDeletionApi
import com.pypath.app.cloud.AuthMode
import com.pypath.app.cloud.CloudRow
import com.pypath.app.cloud.CloudSnapshot
import com.pypath.app.cloud.CloudSync
import com.pypath.app.cloud.ConsentApi
import com.pypath.app.cloud.GoogleAuthOutcome
import com.pypath.app.cloud.HttpResult
import com.pypath.app.cloud.MemoryConsentStore
import com.pypath.app.cloud.MemorySessionStore
import com.pypath.app.cloud.MemorySyncMetaStore
import com.pypath.app.cloud.RestoreResult
import com.pypath.app.cloud.SupabaseAuth
import com.pypath.app.data.progress.UserProgress
import com.pypath.app.domain.Access
import com.pypath.app.domain.AccessRules
import com.pypath.app.domain.AfterAuth
import com.pypath.app.domain.AuthFlow
import com.pypath.app.domain.StartScreen
import com.pypath.app.practical.CompletionSaver
import com.pypath.app.practical.CompletionStatus
import com.pypath.app.privacy.PrivacyPolicy
import com.pypath.app.ui.AppViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Returning-user login, account deletion and Practical completion saving.
 *
 * MOCKED: Supabase HTTP is [FakeSupabase], the cloud table is [FakeServer] and the phone's stores
 * are [FakeLocal]. These are JVM unit tests; no real Supabase project, Google account or Android
 * device is involved.
 */
class ReturningLoginAndSubmissionTest {

    /** One phone: its own local stores, sharing the given "server side". */
    private class Phone(val supabase: FakeSupabase, val server: FakeServer) {
        val local = FakeLocal()
        val meta = MemorySyncMetaStore()
        val sessions = MemorySessionStore()
        val consents = MemoryConsentStore()
        val cloud = CloudSync(
            auth = SupabaseAuth(supabase), api = server, sessions = sessions, local = local, meta = meta,
            accounts = null, accountCheck = AccountCheckApi(supabase), consentApi = ConsentApi(supabase), consents = consents,
            deletion = AccountDeletionApi(supabase),
        )
        fun access() = AccessRules.derive(true, cloud.sessionUserId.value, consents.agreed.value)
        fun progress() = local.snap.progress.data

        suspend fun login(sub: String) = cloud.authenticateWithGoogle("google:$sub", "n", AuthMode.LOG_IN).getOrThrow()
        suspend fun signUp(sub: String) = cloud.authenticateWithGoogle("google:$sub", "n", AuthMode.SIGN_UP).getOrThrow()

        /** What the app does after a successful sign-in / login: restore, then decide where to go. */
        suspend fun afterAuth(): AfterAuth {
            val r = cloud.restoreAccount(timeoutMs = 2_000)
            return AuthFlow.afterAuth(progress().onboardingCompleted, r == RestoreResult.RESTORED, cloud.lastAuthExisted, cloud.guestCompletedOnboarding())
        }
    }

    private val supabase = FakeSupabase()
    private val server = FakeServer()

    private fun onboardedWith(vararg badges: String): CloudSnapshot = FakeLocal.empty().let {
        it.copy(progress = it.progress.copy(data = UserProgress(onboardingCompleted = true, earnedBadges = setOf(*badges))))
    }

    /** An account that finished onboarding on another phone and agreed to the current policy. */
    private fun existingAccount(sub: String, vararg badges: String): String {
        val uid = "user-$sub"
        supabase.accounts[sub] = uid
        supabase.consents[uid] = mutableListOf(PrivacyPolicy.VERSION)
        server.rowsByUser[uid] = CloudRow(5, onboardedWith(*badges))
        return uid
    }

    // ───────────── Navigation rules ─────────────

    @Test fun startScreen_freshInstallShowsSignInBeforeAnyIntroduction() {
        assertEquals(StartScreen.SIGN_IN, AuthFlow.start(onboarded = false, needsSignIn = true, signedInWithConsent = false))
        assertEquals(StartScreen.ONBOARDING, AuthFlow.start(onboarded = false, needsSignIn = false, signedInWithConsent = false))
        assertEquals(StartScreen.DASHBOARD, AuthFlow.start(onboarded = true, needsSignIn = false, signedInWithConsent = true))
    }

    @Test fun afterAuth_decisionTable() {
        assertEquals(AfterAuth.DASHBOARD, AuthFlow.afterAuth(onboarded = true, restored = true, existed = true, guestOnboarded = false))
        assertEquals(AfterAuth.ONBOARDING_INTRO, AuthFlow.afterAuth(onboarded = false, restored = true, existed = false, guestOnboarded = false))
        assertEquals(AfterAuth.ONBOARDING_SETUP, AuthFlow.afterAuth(onboarded = false, restored = true, existed = false, guestOnboarded = true))
        // Existing account but the cloud copy could not be loaded: never repeat onboarding.
        assertEquals(AfterAuth.DASHBOARD, AuthFlow.afterAuth(onboarded = false, restored = false, existed = true, guestOnboarded = false))
    }

    // ───────────── Scenarios 1-5, 11 ─────────────

    @Test fun s1_newAccount_getsOnboardingThenConsent() = runBlocking {
        val p = Phone(supabase, server)
        val out = p.signUp("new") as GoogleAuthOutcome.SignedIn
        assertEquals(false, out.existed)
        // No consent yet: the policy screen comes first (cloud is not used before agreement).
        assertEquals(Access.ConsentRequired("user-new"), p.access())
        p.cloud.agreeToPrivacyPolicy(PrivacyPolicy.VERSION).getOrThrow()
        assertEquals(AfterAuth.ONBOARDING_INTRO, p.afterAuth())
    }

    @Test fun s2_s11_existingAccount_loginGoesStraightToDashboard_noConsentAgain_noOverwrite() = runBlocking {
        val uid = existingAccount("b", "B1", "B2")
        val p = Phone(supabase, server)
        val out = p.login("b") as GoogleAuthOutcome.SignedIn
        assertEquals(true, out.existed)
        assertEquals(Access.Full(uid), p.access()) // consent found on the server: not asked again
        assertEquals(AfterAuth.DASHBOARD, p.afterAuth())
        assertTrue(p.progress().onboardingCompleted)
        assertEquals(setOf("B1", "B2"), p.progress().earnedBadges)
        assertEquals(setOf("B1", "B2"), server.rowsByUser[uid]!!.snapshot.progress.data.earnedBadges)
        assertEquals(0, supabase.createdUsers) // no duplicate account
    }

    @Test fun s3_logoutThenLogin_restoresProgress() = runBlocking {
        val uid = existingAccount("c", "C1")
        val p = Phone(supabase, server)
        p.login("c"); p.afterAuth()
        p.cloud.signOut()
        assertEquals(Access.Guest, p.access())
        assertFalse("C1" in p.progress().earnedBadges)
        p.login("c")
        assertEquals(AfterAuth.DASHBOARD, p.afterAuth())
        assertEquals(setOf("C1"), p.progress().earnedBadges)
        assertEquals(setOf("C1"), server.rowsByUser[uid]!!.snapshot.progress.data.earnedBadges)
    }

    @Test fun s4_appDataCleared_loginRestoresCloudCopy() = runBlocking {
        val uid = existingAccount("d", "D1")
        Phone(supabase, server).apply { login("d"); afterAuth() }
        val wiped = Phone(supabase, server) // brand-new local stores, as after "Clear data"
        wiped.login("d")
        assertEquals(AfterAuth.DASHBOARD, wiped.afterAuth())
        assertEquals(setOf("D1"), wiped.progress().earnedBadges)
        assertEquals(setOf("D1"), server.rowsByUser[uid]!!.snapshot.progress.data.earnedBadges)
    }

    @Test fun s5_unknownAccount_loginCreatesNothing() = runBlocking {
        val p = Phone(supabase, server)
        assertEquals(GoogleAuthOutcome.AccountNotFound, p.login("nobody"))
        assertEquals(0, supabase.tokenExchanges)
        assertEquals(0, supabase.createdUsers)
        assertNull(p.sessions.session)
        assertEquals(Access.Guest, p.access())
    }

    // ───────────── Scenarios 8-10, 12 ─────────────

    @Test fun s8_s9_s10_deletedAccount_isGone_andCannotLogInAsExisting() = runBlocking {
        val uid = existingAccount("e", "E1")
        val p = Phone(supabase, server)
        p.login("e"); p.afterAuth()
        assertTrue(p.cloud.deleteAccount().isSuccess)
        assertEquals(listOf(uid), supabase.deletedUsers)
        assertNull(supabase.consents[uid]) // the fake mirrors the server's data cleanup
        assertNull(p.sessions.session)
        assertNull(p.cloud.lastAuthExisted)
        assertEquals(GoogleAuthOutcome.AccountNotFound, p.login("e"))
    }

    @Test fun s12_networkFailure_loginAndDeletionNeverReportSuccess() = runBlocking {
        existingAccount("f", "F1")
        val p = Phone(supabase, server)
        supabase.offline = true
        assertTrue(p.cloud.authenticateWithGoogle("google:f", "n", AuthMode.LOG_IN).isFailure)
        assertNull(p.sessions.session)
        supabase.offline = false
        p.login("f")
        // Cloud copy unreachable during the restore: returning learner still goes to the dashboard
        // (never back through onboarding) and nothing is uploaded over the cloud copy.
        server.offline = true
        assertEquals(AfterAuth.DASHBOARD, p.afterAuth())
        server.offline = false
        supabase.offline = true
        val r = p.cloud.deleteAccount()
        assertTrue(r.isFailure)
        assertEquals("user-f", p.sessions.session?.userId)
        assertTrue(supabase.deletedUsers.isEmpty())
        val msg = AppViewModel.deletionErrorMessage(r.exceptionOrNull()!!)
        assertTrue(msg, msg.contains("NOT"))
    }

    @Test fun deletionMessages_neverClaimSuccess() {
        val offline = AppViewModel.deletionErrorMessage(UnknownHostException("x"))
        assertTrue(offline, offline.contains("not deleted", ignoreCase = true))
        val unsure = AppViewModel.deletionErrorMessage(IOException("reset"))
        assertTrue(unsure, unsure.contains("could not confirm", ignoreCase = true))
    }

    @Test fun gateway401_isNotMistakenForAnExpiredSignIn() {
        assertEquals(AccountDeletion.Unauthorized, AccountDeletionApi.parse(HttpResult(401, """{"error":"unauthorized"}""")))
        assertTrue(AccountDeletionApi.parse(HttpResult(401, """{"code":401,"message":"Invalid JWT"}""")) is AccountDeletion.Failed)
    }

    // ───────────── Practical: completion saving ─────────────

    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private suspend fun CompletionSaver.await(id: String): CompletionStatus? = withTimeout(5_000) {
        while (status(id) == CompletionStatus.SAVING) delay(5)
        status(id)
    }

    @Test fun completion_isSavedOnce_evenWithRapidRepeatedCalls() = runBlocking {
        val saves = AtomicInteger()
        val saver = CompletionSaver(scope(), { delay(50); saves.incrementAndGet() })
        repeat(5) { saver.save("t1") }
        assertEquals(CompletionStatus.SAVED, saver.await("t1"))
        saver.save("t1")
        assertEquals(1, saves.get())
    }

    @Test fun completion_temporaryFailureIsRetried() = runBlocking {
        val tries = AtomicInteger()
        val saver = CompletionSaver(scope(), { if (tries.incrementAndGet() < 2) throw IOException("disk busy") }, retryDelaysMs = listOf(10, 10))
        saver.save("t2")
        assertEquals(CompletionStatus.SAVED, saver.await("t2"))
        assertEquals(2, tries.get())
    }

    @Test fun completion_persistentFailureIsReported_andCanBeRetried() = runBlocking {
        var broken = true
        val saver = CompletionSaver(scope(), { if (broken) throw IOException("no space") }, retryDelaysMs = listOf(5, 5))
        saver.save("t3")
        assertEquals(CompletionStatus.FAILED, saver.await("t3")) // never silent
        broken = false
        saver.save("t3")
        assertEquals(CompletionStatus.SAVED, saver.await("t3"))
    }

    @Test fun completion_isNotLostWhenTheCallerGoesAway() = runBlocking {
        val owner = scope()
        val done = AtomicInteger()
        val saver = CompletionSaver(owner, { delay(100); done.incrementAndGet() })
        saver.save("t4")
        delay(20)
        owner.cancel() // e.g. the screen / view model is destroyed right after the pass
        delay(300)
        assertEquals(1, done.get())
    }

    @Test fun completion_differentTasksAreIndependent() = runBlocking {
        val saved = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        val saver = CompletionSaver(scope(), { saved += it })
        saver.save("a"); saver.save("b")
        assertEquals(CompletionStatus.SAVED, saver.await("a"))
        assertEquals(CompletionStatus.SAVED, saver.await("b"))
        assertEquals(setOf("a", "b"), saved.toSet())
    }
}
