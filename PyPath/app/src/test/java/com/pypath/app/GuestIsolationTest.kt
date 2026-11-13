package com.pypath.app

import com.pypath.app.cloud.AccountCheckApi
import com.pypath.app.cloud.AccountDeletion
import com.pypath.app.cloud.AccountDeletionApi
import com.pypath.app.cloud.AccountDeletionException
import com.pypath.app.cloud.AuthMode
import com.pypath.app.cloud.CloudLocalData
import com.pypath.app.cloud.CloudRow
import com.pypath.app.cloud.CloudSnapshot
import com.pypath.app.cloud.CloudSync
import com.pypath.app.cloud.ConsentApi
import com.pypath.app.cloud.HttpResult
import com.pypath.app.cloud.MemoryConsentStore
import com.pypath.app.cloud.MemorySessionStore
import com.pypath.app.cloud.MemorySyncMetaStore
import com.pypath.app.cloud.SupabaseAuth
import com.pypath.app.cloud.SupabaseSession
import com.pypath.app.cloud.SyncMeta
import com.pypath.app.cloud.SyncStatus
import com.pypath.app.data.progress.UserProgress
import com.pypath.app.domain.Access
import com.pypath.app.domain.AccessRules
import com.pypath.app.privacy.PrivacyPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Corrections: guest progress never mixes with an account, account switching is isolated, consent
 * withdrawal stops syncing, and account deletion reports only real success.
 *
 * Mocked: the Supabase HTTP API is [FakeSupabase] and the cloud table is [FakeServer]; no real
 * Supabase project, Google account or device is involved.
 */
class GuestIsolationTest {
    private val supabase = FakeSupabase()
    private val server = FakeServer()
    private val local = FakeLocal()
    private val meta = MemorySyncMetaStore()
    private val sessions = MemorySessionStore()
    private val consents = MemoryConsentStore()
    private val cloud = CloudSync(
        auth = SupabaseAuth(supabase), api = server, sessions = sessions, local = local, meta = meta,
        accounts = null, accountCheck = AccountCheckApi(supabase), consentApi = ConsentApi(supabase), consents = consents,
        deletion = AccountDeletionApi(supabase),
    )

    private fun badges(vararg b: String): CloudSnapshot =
        FakeLocal.empty().let { it.copy(progress = it.progress.copy(data = UserProgress(earnedBadges = setOf(*b)))) }
    private fun phoneBadges() = local.snap.progress.data.earnedBadges
    private fun cloudBadges(uid: String) = server.rowsByUser[uid]?.snapshot?.progress?.data?.earnedBadges
    private fun access() = AccessRules.derive(true, cloud.sessionUserId.value, consents.agreed.value)

    /** "Sign in with Google" for Google account [sub] + "Agree and Continue". Supabase user id = "user-<sub>". */
    private suspend fun signUpAndAgree(sub: String): String {
        cloud.authenticateWithGoogle("google:$sub", "n", AuthMode.SIGN_UP).getOrThrow()
        cloud.agreeToPrivacyPolicy(PrivacyPolicy.VERSION).getOrThrow()
        return "user-$sub"
    }

    private suspend fun logIn(sub: String) {
        cloud.authenticateWithGoogle("google:$sub", "n", AuthMode.LOG_IN).getOrThrow()
    }

    // ───────────── Guest isolation ─────────────

    @Test fun guestThenAccountA_guestDataIsNotUploadedOrShown() = runBlocking {
        local.snap = badges("GUEST")
        val a = signUpAndAgree("a")
        assertTrue(cloud.syncNow() is SyncStatus.Synced)
        assertEquals(emptySet<String>(), cloudBadges(a))
        assertEquals(emptySet<String>(), phoneBadges())
        assertEquals(setOf("GUEST"), local.backups[CloudLocalData.GUEST_SLOT]!!.progress.data.earnedBadges)
        // Signing out brings the guest progress back, unchanged.
        cloud.signOut()
        assertEquals(setOf("GUEST"), phoneBadges())
        assertNull(meta.meta.owner)
    }

    @Test fun guestThenAccountB_existingCloudProgressIsNeverOverwrittenOrMixed() = runBlocking {
        supabase.accounts["b"] = "user-b"
        supabase.consents["user-b"] = mutableListOf(PrivacyPolicy.VERSION)
        server.rowsByUser["user-b"] = CloudRow(3, badges("B-CLOUD"))
        local.snap = badges("GUEST")
        logIn("b")
        assertTrue(cloud.syncNow() is SyncStatus.Synced)
        assertEquals(setOf("B-CLOUD"), cloudBadges("user-b"))
        assertEquals(setOf("B-CLOUD"), phoneBadges())
        assertEquals(0, server.writes)
    }

    @Test fun accountA_signOut_guestSeesOnlyGuestData() = runBlocking {
        local.snap = badges("GUEST")
        val a = signUpAndAgree("a")
        cloud.syncNow()
        local.snap = local.snap.let { it.copy(progress = it.progress.copy(data = it.progress.data.copy(earnedBadges = setOf("A-PRIVATE")))) }
        cloud.signOut() // final sync uploads A's change to A only
        assertEquals(setOf("A-PRIVATE"), cloudBadges(a))
        assertEquals(setOf("GUEST"), phoneBadges())
        assertFalse(phoneBadges().contains("A-PRIVATE"))
        assertEquals(Access.Guest, access())
        // Guest mode never syncs.
        val writes = server.writes
        assertEquals(SyncStatus.Off, cloud.syncNow())
        assertEquals(writes, server.writes)
    }

    @Test fun accountA_signOut_accountB_neverReceivesAData() = runBlocking {
        val a = signUpAndAgree("a")
        cloud.syncNow()
        local.snap = badges("A-UNSYNCED")
        server.offline = true
        cloud.signOut() // offline: A's change cannot be uploaded and is kept aside for A only
        server.offline = false
        assertEquals(setOf("A-UNSYNCED"), local.backups[a]!!.progress.data.earnedBadges)
        assertFalse(phoneBadges().contains("A-UNSYNCED"))

        val b = signUpAndAgree("b")
        assertTrue(cloud.syncNow() is SyncStatus.Synced)
        assertEquals(emptySet<String>(), cloudBadges(b))
        assertEquals(emptySet<String>(), phoneBadges())
        cloud.signOut()

        // A logs in again: A's kept-aside change reaches A's own account.
        logIn("a")
        cloud.syncNow()
        assertEquals(setOf("A-UNSYNCED"), cloudBadges(a))
        assertEquals(emptySet<String>(), cloudBadges(b))
    }

    @Test fun nullOwnerIsNeverPermissionToMerge() = runBlocking {
        // A phone from the previous version: signed-in session, data with no recorded owner.
        sessions.save(SupabaseSession("at-user-a", "rt", Long.MAX_VALUE / 2, "user-a"))
        consents.markAgreed("user-a", PrivacyPolicy.VERSION)
        supabase.consents["user-a"] = mutableListOf(PrivacyPolicy.VERSION)
        server.rowsByUser["user-a"] = CloudRow(1, badges("A-CLOUD"))
        meta.update { SyncMeta(owner = null) }
        local.snap = badges("UNKNOWN-OWNER")
        cloud.syncNow()
        assertEquals(setOf("A-CLOUD"), cloudBadges("user-a"))
        assertEquals(setOf("A-CLOUD"), phoneBadges())
        assertEquals(setOf("UNKNOWN-OWNER"), local.backups[CloudLocalData.GUEST_SLOT]!!.progress.data.earnedBadges)
    }

    // ───────────── Explicit guest copy ─────────────

    @Test fun guestProgressIsCopiedOnlyByExplicitActionForAVerifiedAccount() = runBlocking {
        local.snap = badges("GUEST")
        val a = signUpAndAgree("a")
        cloud.syncNow()
        assertTrue(cloud.guestProgressAvailable.value)
        assertEquals(emptySet<String>(), cloudBadges(a))

        // The server does not accept the session: nothing is copied.
        server.rejectToken = true
        assertTrue(cloud.copyGuestProgressToAccount().isFailure)
        assertEquals(emptySet<String>(), phoneBadges())
        server.rejectToken = false

        assertTrue(cloud.copyGuestProgressToAccount().isSuccess)
        assertEquals(setOf("GUEST"), phoneBadges())
        cloud.syncNow()
        assertEquals(setOf("GUEST"), cloudBadges(a))
        assertFalse(cloud.guestProgressAvailable.value)
        // The guest copy stays on the phone for guest mode.
        cloud.signOut()
        assertEquals(setOf("GUEST"), phoneBadges())
    }

    @Test fun guestCopyNeedsASignedInAccountWithConsent() = runBlocking {
        local.snap = badges("GUEST")
        assertTrue(cloud.copyGuestProgressToAccount().isFailure) // guest
        cloud.authenticateWithGoogle("google:new", "n", AuthMode.SIGN_UP).getOrThrow()
        assertTrue(cloud.copyGuestProgressToAccount().isFailure) // no consent yet
        assertEquals(emptySet<String>(), phoneBadges())
    }

    // ───────────── Consent withdrawal ─────────────

    @Test fun withdrawalIsRecordedStopsSyncAndSignsOut() = runBlocking {
        local.snap = badges("GUEST")
        val a = signUpAndAgree("a")
        cloud.syncNow()
        local.snap = badges("A-AFTER")
        assertTrue(cloud.withdrawConsent().isSuccess)
        assertEquals("withdrawn:${PrivacyPolicy.VERSION}", supabase.consents[a]!!.last())
        assertNull(sessions.session)
        assertEquals(Access.Guest, access())
        assertEquals(setOf("GUEST"), phoneBadges())
        // Nothing was uploaded after the withdrawal, and nothing is uploaded later.
        assertEquals(emptySet<String>(), cloudBadges(a))
        assertEquals(SyncStatus.Off, cloud.syncNow())
        // Logging in again shows the policy again (the newest server record is the withdrawal).
        logIn("a")
        assertEquals(Access.ConsentRequired(a), access())
        assertEquals(SyncStatus.Off, cloud.syncNow())
        assertEquals(emptySet<String>(), cloudBadges(a))
    }

    @Test fun withdrawalOnAnotherPhoneStopsSyncHere() = runBlocking {
        val a = signUpAndAgree("a")
        cloud.syncNow()
        val writes = server.writes
        supabase.consents[a]!!.add("withdrawn:${PrivacyPolicy.VERSION}")
        local.snap = badges("NEW")
        assertEquals(SyncStatus.Off, cloud.syncNow())
        assertEquals(writes, server.writes)
        assertEquals(Access.ConsentRequired(a), access())
    }

    @Test fun withdrawalThatTheServerRejectsChangesNothing() = runBlocking {
        val a = signUpAndAgree("a")
        supabase.oldConsentSchema = true // correction migration not applied
        val r = cloud.withdrawConsent()
        assertTrue(r.isFailure)
        assertNotNull(sessions.session)
        assertEquals(Access.Full(a), access())
    }

    @Test fun agreementStillWorksWithoutTheAgeColumn() = runBlocking {
        supabase.oldConsentSchema = true
        signUpAndAgree("a")
        assertEquals(Access.Full("user-a"), access())
        assertFalse(supabase.consentBodies.last().contains("age_confirmed"))
    }

    // ───────────── Account deletion ─────────────

    @Test fun deletionRemovesOnlyThisAccountAfterServerConfirmation() = runBlocking {
        local.snap = badges("GUEST")
        val b = signUpAndAgree("b")
        cloud.signOut()
        val a = signUpAndAgree("a")
        cloud.syncNow()
        local.snap = badges("A-DATA")
        assertTrue(cloud.deleteAccount().isSuccess)
        assertEquals(listOf(a), supabase.deletedUsers)
        assertTrue(supabase.accounts.containsValue(b))
        assertNull(sessions.session)
        assertEquals(Access.Guest, access())
        assertEquals(setOf("GUEST"), phoneBadges())
        assertNull(local.backups[a])
        assertNull(meta.meta.owner)
    }

    @Test fun deletionFailureIsReportedAndNothingChangesOnThePhone() = runBlocking {
        for ((mode, expected) in listOf("missing" to AccountDeletion.NotDeployed, "fail" to AccountDeletion.Failed(502, "auth_user"))) {
            val c = GuestIsolationTest()
            c.supabase.deleteFunction = mode
            val a = c.signUpAndAgree("a")
            c.local.snap = c.badges("A-DATA")
            val r = c.cloud.deleteAccount()
            assertTrue(r.isFailure)
            assertEquals(expected, (r.exceptionOrNull() as AccountDeletionException).result)
            assertEquals(a, c.sessions.session?.userId)
            assertEquals(Access.Full(a), c.access())
            assertEquals(setOf("A-DATA"), c.phoneBadges())
            assertTrue(c.supabase.deletedUsers.isEmpty())
        }
    }

    @Test fun deletionResponsesAreParsedStrictly() {
        assertEquals(AccountDeletion.Deleted, AccountDeletionApi.parse(HttpResult(200, """{"deleted":true}""")))
        assertTrue(AccountDeletionApi.parse(HttpResult(200, """{"deleted":false}""")) is AccountDeletion.Failed)
        assertTrue(AccountDeletionApi.parse(HttpResult(200, "")) is AccountDeletion.Failed)
        assertEquals(AccountDeletion.NotDeployed, AccountDeletionApi.parse(HttpResult(404, "")))
        assertEquals(AccountDeletion.Unauthorized, AccountDeletionApi.parse(HttpResult(401, "")))
        assertEquals(AccountDeletion.Failed(502, "cloud_data"), AccountDeletionApi.parse(HttpResult(502, """{"stage":"cloud_data"}""")))
    }

    // ───────────── Google Web client ID ─────────────

    @Test fun webClientIdIsTheSameEverywhere() {
        val xml = File("src/main/res/values/google_auth.xml").readText()
        val id = Regex("""name="google_web_client_id"[^>]*>\s*([^<\s]+)\s*<""").find(xml)!!.groupValues[1]
        assertTrue(id, Regex("""^[0-9]+-[a-z0-9]+\.apps\.googleusercontent\.com$""").matches(id))
        val project = id.substringBefore('-')
        val docs = listOf(File("../README.md"), File("../../PYPATH-AUTH-PRIVACY-UPLOAD.md")).filter { it.exists() }
        assertTrue(docs.isNotEmpty())
        val mentions = docs.flatMap { f -> Regex("""$project-[A-Za-z0-9]+\.apps\.googleusercontent\.com""").findAll(f.readText()).map { f.name to it.value }.toList() }
        assertTrue("the documentation must name the Web client ID", mentions.isNotEmpty())
        mentions.forEach { (file, value) -> assertEquals("Web client ID in $file", id, value) }
    }
}
