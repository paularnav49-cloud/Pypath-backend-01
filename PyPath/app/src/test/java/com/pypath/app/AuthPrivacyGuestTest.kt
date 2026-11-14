package com.pypath.app

import com.pypath.app.cloud.AccountCheck
import com.pypath.app.cloud.AccountCheckApi
import com.pypath.app.cloud.AccountCheckException
import com.pypath.app.cloud.ActivityEnvelope
import com.pypath.app.cloud.AuthMode
import com.pypath.app.cloud.CloudLocalData
import com.pypath.app.cloud.CloudMerge
import com.pypath.app.cloud.CloudRow
import com.pypath.app.cloud.CloudSnapshot
import com.pypath.app.cloud.CloudStateApi
import com.pypath.app.cloud.CloudSync
import com.pypath.app.cloud.ConsentApi
import com.pypath.app.cloud.GoogleAuthOutcome
import com.pypath.app.cloud.HttpResult
import com.pypath.app.cloud.MemoryConsentStore
import com.pypath.app.cloud.MemorySessionStore
import com.pypath.app.cloud.MemorySyncMetaStore
import com.pypath.app.cloud.ProgramsEnvelope
import com.pypath.app.cloud.ProgressEnvelope
import com.pypath.app.cloud.SettingsEnvelope
import com.pypath.app.cloud.SupabaseAuth
import com.pypath.app.cloud.SupabaseHttp
import com.pypath.app.cloud.SupabaseSession
import com.pypath.app.cloud.SyncMeta
import com.pypath.app.cloud.SyncStatus
import com.pypath.app.data.progress.UserProgress
import com.pypath.app.domain.Access
import com.pypath.app.domain.AccessRules
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.NodeStatus
import com.pypath.app.privacy.PrivacyPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * Stand-in for the Supabase project: Auth token endpoint, the google-account-check Edge Function
 * and the privacy_consents table. Records every call, so tests can prove that no account was
 * created (no token exchange) when it must not be.
 */
internal class FakeSupabase : SupabaseHttp("https://example.invalid", "test-key") {
    /** Google identities that already have a Supabase account (Google sub -> Supabase user id). */
    val accounts = mutableMapOf("sub-existing" to "user-existing")
    var functionDeployed = true
    var functionStatus: Int? = null // forces e.g. 429 / 503
    var offline = false
    var consentOffline = false
    /** Consent log per user, oldest first: "<version>" = agreed, "withdrawn:<version>" = withdrawn. */
    val consents = mutableMapOf<String, MutableList<String>>()
    /** Simulates a database WITHOUT the 20261114000000 correction migration. */
    var oldConsentSchema = false
    val consentBodies = mutableListOf<String>()
    /** delete-account function: "ok", "missing" (404), "fail" (502). */
    var deleteFunction = "ok"
    val deletedUsers = mutableListOf<String>()
    val calls = mutableListOf<String>()
    var tokenExchanges = 0
    var createdUsers = 0

    /** The fake Google ID token is just "google:<sub>". */
    private fun subOf(idToken: String) = idToken.removePrefix("google:")

    override suspend fun request(method: String, path: String, body: String?, accessToken: String?, headers: Map<String, String>): HttpResult {
        calls += "$method $path"
        if (offline) throw IOException("offline")
        return when {
            path.startsWith("/functions/v1/google-account-check") -> {
                if (!functionDeployed) return HttpResult(404, """{"code":"NOT_FOUND","message":"Requested function was not found"}""")
                functionStatus?.let { return HttpResult(it, """{"error":"forced"}""") }
                val sub = subOf(Regex("\"id_token\":\"([^\"]+)\"").find(body.orEmpty())!!.groupValues[1])
                val mode = Regex("\"mode\":\"([^\"]+)\"").find(body.orEmpty())!!.groupValues[1]
                HttpResult(200, """{"exists":${accounts.containsKey(sub)},"mode":"$mode"}""")
            }
            path.startsWith("/auth/v1/token?grant_type=id_token") -> {
                tokenExchanges++
                val sub = subOf(Regex("\"id_token\":\"([^\"]+)\"").find(body.orEmpty())!!.groupValues[1])
                // Like Supabase Auth: the id_token grant creates the user if it does not exist.
                val uid = accounts.getOrPut(sub) { createdUsers++; "user-$sub" }
                HttpResult(200, session(uid))
            }
            path.startsWith("/auth/v1/token?grant_type=refresh_token") -> HttpResult(200, session("user-existing"))
            path.startsWith("/rest/v1/privacy_consents") -> {
                if (consentOffline) throw IOException("offline")
                val uid = accessToken!!.removePrefix("at-")
                if (method == "GET") {
                    // Like PostgREST with RLS: only the caller's rows; "order=id.desc&limit=1" = newest only.
                    val rows = consents[uid].orEmpty().let { if (path.contains("limit=1")) it.takeLast(1) else it }
                    HttpResult(200, rows.joinToString(",", "[", "]") {
                        if (it.startsWith("withdrawn:")) """{"policy_version":"${it.removePrefix("withdrawn:")}","status":"withdrawn"}"""
                        else """{"policy_version":"$it","status":"agreed"}"""
                    })
                } else {
                    val b = body.orEmpty()
                    consentBodies += b
                    val v = Regex("\"policy_version\":\"([^\"]+)\"").find(b)!!.groupValues[1]
                    val st = Regex("\"status\":\"([^\"]+)\"").find(b)?.groupValues?.get(1) ?: "agreed"
                    // The trigger + RLS: user_id always comes from the token; a body naming someone else changes nothing.
                    val list = consents.getOrPut(uid) { mutableListOf() }
                    when {
                        oldConsentSchema && b.contains("age_confirmed") ->
                            HttpResult(400, """{"code":"PGRST204","message":"Could not find the 'age_confirmed' column of 'privacy_consents' in the schema cache"}""")
                        oldConsentSchema && st != "agreed" -> HttpResult(403, """{"message":"new row violates row-level security policy"}""")
                        oldConsentSchema && v in list -> HttpResult(409, """{"message":"duplicate key"}""")
                        else -> { list += if (st == "withdrawn") "withdrawn:$v" else v; HttpResult(201, "") }
                    }
                }
            }
            path.startsWith("/functions/v1/delete-account") -> {
                val uid = accessToken!!.removePrefix("at-")
                when (deleteFunction) {
                    "missing" -> HttpResult(404, """{"code":"NOT_FOUND","message":"Requested function was not found"}""")
                    "fail" -> HttpResult(502, """{"error":"delete_failed","stage":"auth_user","deleted":false}""")
                    else -> {
                        deletedUsers += uid
                        accounts.entries.removeAll { it.value == uid }
                        consents.remove(uid)
                        HttpResult(200, """{"deleted":true}""")
                    }
                }
            }
            else -> HttpResult(404, "")
        }
    }

    private fun session(uid: String) =
        """{"access_token":"at-$uid","refresh_token":"rt-$uid","expires_in":3600,"user":{"id":"$uid","email":"$uid@example.com"}}"""
}

private class NoCloudRows : CloudStateApi() {
    var writes = 0
    override suspend fun fetch(session: SupabaseSession): CloudRow? = null
    override suspend fun insert(session: SupabaseSession, snapshot: CloudSnapshot): CloudRow? { writes++; return CloudRow(1, snapshot) }
    override suspend fun update(session: SupabaseSession, snapshot: CloudSnapshot, expectedRevision: Long): CloudRow? { writes++; return CloudRow(expectedRevision + 1, snapshot) }
}

internal class LocalOnly : CloudLocalData {
    var snap = CloudSnapshot(ProgressEnvelope(), ActivityEnvelope(), ProgramsEnvelope(), SettingsEnvelope())
    override suspend fun read(meta: SyncMeta) = snap
    override suspend fun mergeIn(remote: CloudSnapshot, localResetAt: Long) { snap = CloudMerge.snapshot(snap, remote) }
    override suspend fun replaceWith(remote: CloudSnapshot?) { if (remote != null) snap = remote }
    override val changes: Flow<Unit> = emptyFlow()
    override suspend fun saveBackup(owner: String, snapshot: CloudSnapshot) {}
    override suspend fun takeBackup(owner: String): CloudSnapshot? = null
    override suspend fun deleteBackup(owner: String) {}
}

class AuthFlowTest {
    private val server = FakeSupabase()
    private val sessions = MemorySessionStore()
    private val consents = MemoryConsentStore()
    private val rows = NoCloudRows()
    private val cloud = CloudSync(
        auth = SupabaseAuth(server), api = rows, sessions = sessions, local = LocalOnly(), meta = MemorySyncMetaStore(),
        accounts = null, accountCheck = AccountCheckApi(server), consentApi = ConsentApi(server), consents = consents,
    )

    private fun access() = AccessRules.derive(true, cloud.sessionUserId.value, consents.agreed.value)

    @Test fun loginWithUnknownGoogleAccountNeverCreatesAnAccount() = runBlocking {
        val r = cloud.authenticateWithGoogle("google:sub-new", "nonce", AuthMode.LOG_IN).getOrThrow()
        assertEquals(GoogleAuthOutcome.AccountNotFound, r)
        assertEquals(0, server.tokenExchanges)
        assertEquals(0, server.createdUsers)
        assertNull(sessions.session)
        assertEquals(Access.Guest, access())
    }

    @Test fun signUpAfterAccountNotFoundCreatesTheAccountForTheSameIdentity() = runBlocking {
        assertEquals(GoogleAuthOutcome.AccountNotFound, cloud.authenticateWithGoogle("google:sub-new", "n", AuthMode.LOG_IN).getOrThrow())
        val r = cloud.authenticateWithGoogle("google:sub-new", "n", AuthMode.SIGN_UP).getOrThrow() as GoogleAuthOutcome.SignedIn
        assertEquals("user-sub-new", r.session.userId)
        assertEquals(false, r.existed)
        assertEquals(1, server.createdUsers)
        assertEquals(Access.ConsentRequired("user-sub-new"), access())
    }

    @Test fun loginWithExistingAccountOpensItWithoutCreatingAnother() = runBlocking {
        val r = cloud.authenticateWithGoogle("google:sub-existing", "n", AuthMode.LOG_IN).getOrThrow() as GoogleAuthOutcome.SignedIn
        assertEquals("user-existing", r.session.userId)
        assertEquals(true, r.existed)
        assertEquals(0, server.createdUsers)
        assertEquals("user-existing", sessions.session?.userId)
    }

    @Test fun signUpWithExistingAccountOpensItNoDuplicate() = runBlocking {
        val r = cloud.authenticateWithGoogle("google:sub-existing", "n", AuthMode.SIGN_UP).getOrThrow() as GoogleAuthOutcome.SignedIn
        assertEquals("user-existing", r.session.userId)
        assertEquals(true, r.existed)
        assertEquals(0, server.createdUsers)
        assertEquals(1, server.accounts.size)
    }

    @Test fun loginFailsClosedWhenTheCheckServiceIsMissing() = runBlocking {
        server.functionDeployed = false
        val r = cloud.authenticateWithGoogle("google:sub-new", "n", AuthMode.LOG_IN)
        val e = r.exceptionOrNull() as AccountCheckException
        assertEquals(AccountCheck.NotDeployed, e.check)
        assertEquals(0, server.tokenExchanges)
        assertNull(sessions.session)
    }

    @Test fun signUpStillWorksWhenTheCheckServiceIsMissing() = runBlocking {
        server.functionDeployed = false
        val r = cloud.authenticateWithGoogle("google:sub-new", "n", AuthMode.SIGN_UP).getOrThrow() as GoogleAuthOutcome.SignedIn
        assertNull(r.existed)
        assertEquals("user-sub-new", sessions.session?.userId)
    }

    @Test fun rateLimitAndServerErrorsNeverSignIn() = runBlocking {
        server.functionStatus = 429
        assertEquals(AccountCheck.RateLimited, (cloud.authenticateWithGoogle("google:sub-existing", "n", AuthMode.LOG_IN).exceptionOrNull() as AccountCheckException).check)
        server.functionStatus = 503
        assertTrue(cloud.authenticateWithGoogle("google:sub-existing", "n", AuthMode.SIGN_UP).isFailure)
        server.functionStatus = 401
        assertTrue((cloud.authenticateWithGoogle("google:sub-existing", "n", AuthMode.LOG_IN).exceptionOrNull() as AccountCheckException).check is AccountCheck.Rejected)
        assertEquals(0, server.tokenExchanges)
        assertNull(sessions.session)
    }

    @Test fun offlineSignInFailsAndKeepsGuest() = runBlocking {
        server.offline = true
        assertTrue(cloud.authenticateWithGoogle("google:sub-existing", "n", AuthMode.LOG_IN).exceptionOrNull() is IOException)
        assertTrue(cloud.authenticateWithGoogle("google:sub-new", "n", AuthMode.SIGN_UP).exceptionOrNull() is IOException)
        assertNull(sessions.session)
    }

    @Test fun earlierConsentOnTheServerIsHonouredOnLogin() = runBlocking {
        server.consents["user-existing"] = mutableListOf(PrivacyPolicy.VERSION)
        cloud.authenticateWithGoogle("google:sub-existing", "n", AuthMode.LOG_IN).getOrThrow()
        assertEquals(Access.Full("user-existing"), access())
    }

    @Test fun outdatedConsentMustBeRenewed() = runBlocking {
        server.consents["user-existing"] = mutableListOf("2020-01-01")
        cloud.authenticateWithGoogle("google:sub-existing", "n", AuthMode.LOG_IN).getOrThrow()
        assertEquals(Access.ConsentRequired("user-existing"), access())
        assertFalse(PrivacyPolicy.isConsentValid("2020-01-01"))
        assertTrue(PrivacyPolicy.isConsentValid(PrivacyPolicy.VERSION))
        assertFalse(PrivacyPolicy.isConsentValid(null))
        assertFalse(PrivacyPolicy.isConsentValid(""))
    }

    @Test fun agreeingStoresTheRecordOnTheServerThenUnlocks() = runBlocking {
        cloud.authenticateWithGoogle("google:sub-new", "n", AuthMode.SIGN_UP).getOrThrow()
        assertEquals(Access.ConsentRequired("user-sub-new"), access())
        cloud.agreeToPrivacyPolicy(PrivacyPolicy.VERSION).getOrThrow()
        assertEquals(listOf(PrivacyPolicy.VERSION), server.consents["user-sub-new"])
        assertEquals(Access.Full("user-sub-new"), access())
        // Agreeing twice (e.g. a retry) is fine: the newest record is still an agreement.
        assertTrue(cloud.agreeToPrivacyPolicy(PrivacyPolicy.VERSION).isSuccess)
        assertEquals(PrivacyPolicy.VERSION, server.consents["user-sub-new"]!!.last())
        assertEquals(Access.Full("user-sub-new"), access())
        // The (self-declared) age confirmation is stored with the agreement.
        assertTrue(server.consentBodies.last().contains("\"age_confirmed\":true"))
    }

    @Test fun agreementThatCouldNotBeSavedKeepsThePolicyOnScreen() = runBlocking {
        cloud.authenticateWithGoogle("google:sub-new", "n", AuthMode.SIGN_UP).getOrThrow()
        server.consentOffline = true
        assertTrue(cloud.agreeToPrivacyPolicy(PrivacyPolicy.VERSION).isFailure)
        assertEquals(Access.ConsentRequired("user-sub-new"), access())
    }

    @Test fun nothingIsBackedUpBeforeConsent() = runBlocking {
        cloud.authenticateWithGoogle("google:sub-new", "n", AuthMode.SIGN_UP).getOrThrow()
        assertEquals(SyncStatus.Off, cloud.syncNow())
        assertEquals(0, rows.writes)
        cloud.agreeToPrivacyPolicy(PrivacyPolicy.VERSION).getOrThrow()
        assertTrue(cloud.syncNow() is SyncStatus.Synced)
        assertEquals(1, rows.writes)
    }

    @Test fun serverWithoutRecordRemovesAStaleCachedConsent() = runBlocking {
        cloud.authenticateWithGoogle("google:sub-existing", "n", AuthMode.LOG_IN).getOrThrow()
        consents.markAgreed("user-existing", PrivacyPolicy.VERSION) // e.g. cached, but deleted on the server
        cloud.refreshConsent()
        assertEquals(Access.ConsentRequired("user-existing"), access())
    }

    @Test fun signOutReturnsToGuest() = runBlocking {
        server.consents["user-existing"] = mutableListOf(PrivacyPolicy.VERSION)
        cloud.authenticateWithGoogle("google:sub-existing", "n", AuthMode.LOG_IN).getOrThrow()
        cloud.signOut()
        assertEquals(Access.Guest, access())
        // Consent stays remembered for that account: logging in again goes straight on.
        cloud.authenticateWithGoogle("google:sub-existing", "n", AuthMode.LOG_IN).getOrThrow()
        assertEquals(Access.Full("user-existing"), access())
    }

    @Test fun startAsksTheServerBeforeShowingThePolicyToAStoredSession() = runBlocking {
        server.consents["user-existing"] = mutableListOf(PrivacyPolicy.VERSION)
        val stored = MemorySessionStore(SupabaseSession("at-user-existing", "rt", Long.MAX_VALUE / 2, "user-existing"))
        val c = CloudSync(
            auth = SupabaseAuth(server), api = NoCloudRows(), sessions = stored, local = LocalOnly(), meta = MemorySyncMetaStore(),
            accounts = null, accountCheck = AccountCheckApi(server), consentApi = ConsentApi(server), consents = MemoryConsentStore(),
        )
        assertFalse(c.ready.value)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            c.start(scope)
            withTimeout(5_000) { while (!c.ready.value) delay(10) }
            assertEquals(Access.Full("user-existing"), AccessRules.derive(c.ready.value, c.sessionUserId.value, c.consents.agreed.value))
        } finally {
            scope.cancel()
        }
    }
}

class AccessRulesTest {
    @Test fun derive() {
        assertNull(AccessRules.derive(false, null, emptyMap()))
        assertEquals(Access.Guest, AccessRules.derive(true, null, null))
        assertNull(AccessRules.derive(true, "u", null))
        assertEquals(Access.ConsentRequired("u"), AccessRules.derive(true, "u", emptyMap()))
        assertEquals(Access.ConsentRequired("u"), AccessRules.derive(true, "u", mapOf("other" to PrivacyPolicy.VERSION)))
        assertEquals(Access.Full("u"), AccessRules.derive(true, "u", mapOf("u" to PrivacyPolicy.VERSION)))
        assertTrue(Access.Guest.limitedToFirstLesson)
        assertTrue(Access.ConsentRequired("u").limitedToFirstLesson)
        assertFalse(Access.Full("u").limitedToFirstLesson)
    }

    @Test fun accountCheckResponses() {
        assertEquals(AccountCheck.Checked(true), AccountCheckApi.parse(HttpResult(200, """{"exists":true,"mode":"login"}""")))
        assertEquals(AccountCheck.Checked(false), AccountCheckApi.parse(HttpResult(200, """{"exists":false}""")))
        assertTrue(AccountCheckApi.parse(HttpResult(200, "{}")) is AccountCheck.Unavailable)
        assertTrue(AccountCheckApi.parse(HttpResult(200, "not json")) is AccountCheck.Unavailable)
        assertEquals(AccountCheck.RateLimited, AccountCheckApi.parse(HttpResult(429, "")))
        assertEquals(AccountCheck.Rejected("invalid_token"), AccountCheckApi.parse(HttpResult(401, """{"error":"invalid_token"}""")))
        assertEquals(AccountCheck.NotDeployed, AccountCheckApi.parse(HttpResult(404, """{"code":"NOT_FOUND"}""")))
        assertEquals(AccountCheck.Unavailable(500), AccountCheckApi.parse(HttpResult(500, "")))
    }

    @Test fun consentVersionsParse() {
        assertEquals(listOf("2026-11-13", "2027-01-01.1"), ConsentApi.parseVersions("""[{"policy_version":"2026-11-13"},{"policy_version":"2027-01-01.1"}]"""))
        assertEquals(emptyList<String>(), ConsentApi.parseVersions("[]"))
        assertEquals(emptyList<String>(), ConsentApi.parseVersions("oops"))
    }
}

class GuestRestrictionTest {
    private val course = loadTestCourse()

    @Test fun guestCanOnlyOpenTheFirstLesson() {
        val snap = CourseSnapshot(course, UserProgress(onboardingCompleted = true), guestLimited = true)
        val first = snap.guestLessonId!!
        assertEquals(snap.orderedSubLevels.first { it.status != NodeStatus.COMING_SOON }.subLevel.id, first)
        val playable = (snap.orderedSubLevels + snap.bonusProjects).filter { it.isPlayable }.map { it.subLevel.id }
        assertEquals(listOf(first), playable)
        assertFalse(snap.requiresAccount(first))
        snap.orderedSubLevels.drop(1).filter { it.status != NodeStatus.COMING_SOON }.forEach {
            assertEquals(NodeStatus.LOCKED, it.status)
            assertTrue(it.accountRequired)
            assertTrue(snap.requiresAccount(it.subLevel.id))
        }
        assertFalse(snap.guestFinishedFirstLesson)
    }

    @Test fun savedOrEditedProgressCannotUnlockLessonsForGuests() {
        // Even if local progress says everything was completed (old progress, or edited storage),
        // a guest still gets only the first lesson. The progress itself is kept.
        val all = course.levels.filter { it.available }.flatMap { l -> l.subLevels.map { it.id } + l.projects.map { it.id } }
        val progress = completed(*all.toTypedArray())
        val snap = CourseSnapshot(course, progress, guestLimited = true)
        assertEquals(listOf(snap.guestLessonId), (snap.orderedSubLevels + snap.bonusProjects).filter { it.isPlayable }.map { it.subLevel.id })
        assertTrue(snap.guestFinishedFirstLesson)
        assertFalse(snap.certificateUnlocked)
        assertNull(snap.continueTarget)
        assertTrue(snap.bonusProjects.filter { it.status != NodeStatus.COMING_SOON }.all { it.accountRequired && !it.isPlayable })
        assertEquals(progress, snap.progress)
        // Signed in: the same progress unlocks everything again (nothing was lost).
        val full = CourseSnapshot(course, progress)
        assertTrue(full.certificateUnlocked)
        assertTrue(full.orderedSubLevels.none { it.accountRequired })
    }

    @Test fun afterTheFirstLessonTheNextStepNeedsAnAccount() {
        val snap = CourseSnapshot(course, completed(CourseSnapshot(course, UserProgress()).guestLessonId!!), guestLimited = true)
        assertTrue(snap.guestFinishedFirstLesson)
        assertNull(snap.continueTarget)
        val next = snap.next(snap.guestLessonId!!)!!
        assertTrue(snap.requiresAccount(next.subLevel.id))
    }

    @Test fun signedInLearnersAreNotLimited() {
        val snap = CourseSnapshot(course, completed(CourseSnapshot(course, UserProgress()).guestLessonId!!))
        assertFalse(snap.guestLimited)
        assertTrue(snap.orderedSubLevels.none { it.accountRequired })
        assertNotNull(snap.continueTarget)
        assertTrue(snap.continueTarget!!.isPlayable)
    }
}

class PrivacyPolicyTextTest {
    private val text = File("src/main/assets/legal/privacy_policy.txt").readText()

    @Test fun policyCoversEveryRequiredTopic() {
        val headings = PrivacyPolicy.parse(text).filterIsInstance<PrivacyPolicy.Block.Heading>().map { it.text }
        val required = listOf(
            "Information we collect", "Google Sign-In", "Supabase authentication and cloud storage",
            "Synced progress, activity, question history, programs and settings", "Local storage on your phone",
            "Data transmission and security", "Data retention", "Account deletion", "Third-party services",
            "Offline functionality", "Children and teenagers", "Your rights and changes to this policy", "Contact",
        )
        required.forEach { r -> assertTrue("missing section: $r", headings.any { it.contains(r) }) }
        assertEquals(13, headings.size)
    }

    @Test fun versionIsFilledInAndNoContactDetailsAreInvented() {
        val blocks = PrivacyPolicy.parse(text)
        assertTrue(blocks.first() is PrivacyPolicy.Block.Title)
        assertTrue(blocks.any { it is PrivacyPolicy.Block.Paragraph && it.text == "Version ${PrivacyPolicy.VERSION}" })
        assertFalse(blocks.toString().contains("{VERSION}"))
        assertTrue(text.contains("[CONTACT EMAIL"))
        assertTrue(text.contains("[OPERATOR NAME"))
        assertFalse(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[a-z]{2,}").containsMatchIn(text))
    }

    /** PyPath is for ages 12+: no 18+ / adults-only restriction and no claim of verified parental consent. */
    @Test fun audienceIs12PlusWithoutAdultOnlyRestriction() {
        val sources = listOf(
            text,
            File("src/main/java/com/pypath/app/ui/screens/privacy/PrivacyConsentScreen.kt").readText(),
            File("src/main/java/com/pypath/app/ui/screens/signin/SignInScreen.kt").readText(),
            File("../docs/privacy-policy.md").readText(),
        )
        val banned = listOf(
            "only for people who are 18", "accounts are for adults", "adults only", "Accounts are for 18+",
            "I'm under 18", "I confirm that I am 18 years of age or older", "please do not create an account; you can use PyPath as a guest",
        )
        sources.forEach { s -> banned.forEach { b -> assertFalse("still contains: $b", s.contains(b, ignoreCase = true)) } }
        assertTrue(text.contains("designed for learners aged 12 and up"))
        assertTrue(text.contains("does not currently check your age or verify a parent's or guardian's identity or consent"))
        assertFalse(Regex("parental consent (has been|is) verified", RegexOption.IGNORE_CASE).containsMatchIn(sources.joinToString()))
        assertTrue(sources[1].contains("I am 18 or older, or I am 12 to 17 and my parent or guardian"))
        assertTrue(sources[2].contains("designed for learners aged 12 and up"))
    }

    @Test fun parserHandlesMarkup() {
        val b = PrivacyPolicy.parse("# T\n\n## H\nline one\nline two\n\n- item\n### S\n")
        assertEquals(
            listOf(
                PrivacyPolicy.Block.Title("T"), PrivacyPolicy.Block.Heading("H"), PrivacyPolicy.Block.Paragraph("line one line two"),
                PrivacyPolicy.Block.Bullet("item"), PrivacyPolicy.Block.SubHeading("S"),
            ),
            b,
        )
    }
}
