package com.pypath.app

import com.pypath.app.auth.Nonce
import com.pypath.app.cloud.ActivityEnvelope
import com.pypath.app.cloud.CloudCodec
import com.pypath.app.cloud.CloudLocalData
import com.pypath.app.cloud.CloudMerge
import com.pypath.app.cloud.CloudRow
import com.pypath.app.cloud.CloudSettings
import com.pypath.app.cloud.CloudSnapshot
import com.pypath.app.cloud.CloudStateApi
import com.pypath.app.cloud.CloudSync
import com.pypath.app.cloud.MemorySessionStore
import com.pypath.app.cloud.MemorySyncMetaStore
import com.pypath.app.cloud.ProgramsEnvelope
import com.pypath.app.cloud.ProgressEnvelope
import com.pypath.app.cloud.SettingsEnvelope
import com.pypath.app.cloud.SupabaseAuth
import com.pypath.app.cloud.SupabaseException
import com.pypath.app.cloud.SupabaseSession
import com.pypath.app.cloud.SyncMeta
import com.pypath.app.cloud.SyncStatus
import com.pypath.app.data.activity.ActivityData
import com.pypath.app.data.activity.DayTally
import com.pypath.app.data.activity.QuestionRecord
import com.pypath.app.data.model.StepType
import com.pypath.app.data.programs.ProgramBook
import com.pypath.app.data.programs.ProgramLibrary
import com.pypath.app.data.programs.SavedProgram
import com.pypath.app.data.progress.QuizResult
import com.pypath.app.data.progress.SubLevelProgress
import com.pypath.app.data.progress.UserProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.MessageDigest

class CloudMergeTest {

    @Test fun progressIsUnionedAndNeverLost() {
        val a = UserProgress(
            onboardingCompleted = true,
            currentSubLevelId = "1.1",
            subLevels = mapOf(
                "1.1" to SubLevelProgress(completedSteps = setOf(StepType.values()[0]), completed = true, completedAt = 50, lastOpenedAt = 100),
            ),
            earnedBadges = setOf("L1"), completedTasks = setOf("t1"), xpAwards = mapOf("a" to 10),
        )
        val b = UserProgress(
            currentSubLevelId = "1.2",
            subLevels = mapOf(
                "1.1" to SubLevelProgress(completedSteps = setOf(StepType.values()[1]), completedAt = 40, lastOpenedAt = 90),
                "1.2" to SubLevelProgress(lastOpenedAt = 200),
            ),
            earnedBadges = setOf("L2"), completedTasks = setOf("t2"), xpAwards = mapOf("a" to 5, "b" to 7),
        )
        val m = CloudMerge.progress(a, b)
        assertTrue(m.onboardingCompleted)
        assertEquals("1.2", m.currentSubLevelId) // b was opened more recently
        assertEquals(setOf(StepType.values()[0], StepType.values()[1]), m.subLevels["1.1"]!!.completedSteps)
        assertTrue(m.subLevels["1.1"]!!.completed)
        assertEquals(40L, m.subLevels["1.1"]!!.completedAt)
        assertEquals(100L, m.subLevels["1.1"]!!.lastOpenedAt)
        assertEquals(setOf("L1", "L2"), m.earnedBadges)
        assertEquals(setOf("t1", "t2"), m.completedTasks)
        assertEquals(mapOf("a" to 10, "b" to 7), m.xpAwards)
        // Idempotent: merging again changes nothing.
        assertEquals(m, CloudMerge.progress(m, b))
        assertEquals(m, CloudMerge.progress(m, a))
    }

    @Test fun quizKeepsBestAndPassed() {
        val a = QuizResult(lastScore = 2, bestScore = 4, total = 5, attempts = 3, passed = true, bestTotal = 5)
        val b = QuizResult(lastScore = 5, bestScore = 5, total = 5, attempts = 4, passed = false, bestTotal = 5)
        val q = CloudMerge.quiz(a, b)!!
        assertEquals(5, q.bestScore)
        assertEquals(4, q.attempts)
        assertTrue(q.passed)
        assertEquals(5, q.lastScore)
    }

    @Test fun activityMergesDaysQuestionsAndStreaks() {
        val a = ActivityData(
            questions = mapOf("q1" to QuestionRecord(attempts = 3, lastDay = 10, xp = 5)),
            days = mapOf(10L to DayTally(xp = 20, actions = 2)),
            activeDays = listOf(8, 9, 10), frozenDays = listOf(7), longestStreak = 3, freezes = 1, runEnd = 10, runLength = 3, reviewXp = 4,
        )
        val b = ActivityData(
            questions = mapOf("q1" to QuestionRecord(attempts = 2, lastDay = 12, xp = 9), "q2" to QuestionRecord(attempts = 1)),
            days = mapOf(10L to DayTally(xp = 5, actions = 6), 11L to DayTally(xp = 3)),
            activeDays = listOf(7, 11), longestStreak = 2, freezes = 2, runEnd = 11, runLength = 5, reviewXp = 1,
        )
        val m = CloudMerge.activity(a, b)
        assertEquals(3, m.questions["q1"]!!.attempts)
        assertEquals(9, m.questions["q1"]!!.xp)
        assertTrue("q2" in m.questions)
        assertEquals(DayTally(xp = 20, actions = 6), m.days[10L])
        assertEquals(listOf(7L, 8L, 9L, 10L, 11L), m.activeDays)
        assertTrue(m.frozenDays.isEmpty()) // day 7 became an active day
        assertEquals(2, m.freezes) // b used the app last
        assertEquals(5, m.longestStreak)
        assertEquals(4, m.reviewXp)
        assertEquals(m, CloudMerge.activity(m, b))
    }

    @Test fun programsMergeByIdNewestWinsAndDeletionsStick() {
        val p1 = SavedProgram("1", "Hello", code = "old", createdAt = 1, updatedAt = 10)
        val p1new = p1.copy(code = "new", updatedAt = 20)
        val p2 = SavedProgram("2", "Game", createdAt = 2, updatedAt = 5)
        val p3 = SavedProgram("3", "Hello", createdAt = 3, updatedAt = 6) // same name, different program
        val phone = ProgramLibrary(programs = listOf(p1new, p2))
        val cloud = ProgramLibrary(programs = listOf(p1, p3))
        val m = CloudMerge.programs(phone, cloud)
        assertEquals("new", m.find("1")!!.code)
        assertEquals(3, m.programs.size)
        assertEquals(m.programs.size, m.programs.map { it.name.lowercase() }.toSet().size)

        // Deleted on the phone: does not come back from the cloud copy.
        val deleted = ProgramBook.delete(m, "2", now = 100)
        assertEquals(100L, deleted.deleted["2"])
        val again = CloudMerge.programs(deleted, m)
        assertNull(again.find("2"))
        // Edited elsewhere after the deletion: the newer edit survives.
        val edited = m.copy(programs = m.programs.map { if (it.id == "2") it.copy(updatedAt = 200) else it })
        assertTrue(CloudMerge.programs(deleted, edited).find("2") != null)
        assertEquals(again, CloudMerge.programs(again, deleted))
    }

    @Test fun settingsNewestChangeWinsPerField() {
        val phone = CloudSettings(soundEffects = false, certificateName = "Asha", dailyGoal = "CASUAL",
            stamps = mapOf(CloudSettings.SOUND to 50, CloudSettings.NAME to 10))
        val cloud = CloudSettings(soundEffects = true, certificateName = "Asha K", dailyGoal = "SERIOUS", introOfferShown = true,
            stamps = mapOf(CloudSettings.SOUND to 20, CloudSettings.NAME to 30, CloudSettings.GOAL to 5))
        val m = CloudMerge.settings(phone, cloud)
        assertFalse(m.soundEffects)
        assertEquals("Asha K", m.certificateName)
        assertEquals("SERIOUS", m.dailyGoal)
        assertTrue(m.introOfferShown)
        assertEquals(30L, m.stamps[CloudSettings.NAME])
    }

    @Test fun newerResetWinsWholesale() {
        val oldProgress = ProgressEnvelope(resetAt = 0, data = UserProgress(earnedBadges = setOf("L1"), onboardingCompleted = true))
        val resetProgress = ProgressEnvelope(resetAt = 500, data = UserProgress())
        val (p, a) = CloudMerge.progressAndActivity(oldProgress, ActivityEnvelope(data = ActivityData(activeDays = listOf(1))), resetProgress, ActivityEnvelope(resetAt = 500))
        assertTrue(p.data.earnedBadges.isEmpty())
        assertTrue(p.data.onboardingCompleted)
        assertTrue(a.data.activeDays.isEmpty())
        assertEquals(500L, p.resetAt)
    }
}

class CloudCodecAndAuthTest {

    @Test fun nonceIsRandomAndHashedAsHexSha256() {
        val a = Nonce.newRaw()
        val b = Nonce.newRaw()
        assertNotEquals(a, b)
        assertTrue(a.length >= 40)
        val expected = MessageDigest.getInstance("SHA-256").digest("abc".toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(expected, Nonce.sha256Hex("abc"))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Nonce.sha256Hex("abc"))
    }

    @Test fun sessionIsParsedFromGoTrueResponse() {
        val s = SupabaseAuth.parseSession(
            """{"access_token":"at","token_type":"bearer","expires_in":3600,"expires_at":1900000000,"refresh_token":"rt","user":{"id":"u-1","email":"a@b.com"}}""",
            nowSeconds = 1,
        )
        assertEquals(SupabaseSession("at", "rt", 1900000000, "u-1", "a@b.com"), s)
        assertTrue(s.expiresSoon(1900000000 - 30))
        assertFalse(s.expiresSoon(1900000000 - 3000))
        assertEquals("Invalid nonce", SupabaseAuth.errorMessage("""{"code":400,"error_code":"bad","msg":"Invalid nonce"}"""))
    }

    @Test fun rowsRoundTripAndUnknownFormatIsNeverOverwritten() {
        val snap = CloudSnapshot(
            ProgressEnvelope(resetAt = 3, data = UserProgress(earnedBadges = setOf("L1"))),
            ActivityEnvelope(resetAt = 3, data = ActivityData(activeDays = listOf(5))),
            ProgramsEnvelope(data = ProgramLibrary(programs = listOf(SavedProgram("1", "A")))),
            SettingsEnvelope(data = CloudSettings(certificateName = "Asha")),
        )
        val body = "[" + CloudCodec.rowJson(snap, 7, "u-1", "2026-01-01T00:00:00Z") + "]"
        val row = CloudCodec.parseRows(body).single()
        assertEquals(7L, row.revision)
        assertEquals(snap, row.snapshot)
        assertTrue(row.readable)
        assertEquals(CloudCodec.fingerprint(snap), CloudCodec.fingerprint(row.snapshot))

        val newer = CloudCodec.parseRows("""[{"user_id":"u","revision":2,"progress":{"v":99,"data":{}},"activity":null,"programs":{},"settings":null}]""").single()
        assertFalse(newer.readable)
        val empty = CloudCodec.parseRows("""[{"user_id":"u","revision":0,"progress":null,"activity":{},"programs":null,"settings":null}]""").single()
        assertTrue(empty.readable)
    }
}

/** Server stand-in with the same optimistic revision rule as the PATCH filter. */
private class FakeServer : CloudStateApi() {
    var row: CloudRow? = null
    var offline = false
    var writes = 0
    var conflictsToInject = 0
    val rowsByUser = mutableMapOf<String, CloudRow>()

    override suspend fun fetch(session: SupabaseSession): CloudRow? {
        if (offline) throw IOException("offline")
        return rowsByUser[session.userId]
    }

    override suspend fun insert(session: SupabaseSession, snapshot: CloudSnapshot): CloudRow? {
        if (offline) throw IOException("offline")
        if (rowsByUser.containsKey(session.userId)) return null
        writes++
        return CloudRow(1, snapshot).also { rowsByUser[session.userId] = it }
    }

    override suspend fun update(session: SupabaseSession, snapshot: CloudSnapshot, expectedRevision: Long): CloudRow? {
        if (offline) throw IOException("offline")
        val cur = rowsByUser[session.userId] ?: return null
        if (conflictsToInject > 0) {
            conflictsToInject--
            // Another phone wrote meanwhile: adds a badge and bumps the revision.
            val p = cur.snapshot.progress
            rowsByUser[session.userId] = CloudRow(cur.revision + 1, cur.snapshot.copy(progress = p.copy(data = p.data.copy(earnedBadges = p.data.earnedBadges + "OTHER"))))
            return null
        }
        if (cur.revision != expectedRevision) return null
        writes++
        return CloudRow(expectedRevision + 1, snapshot).also { rowsByUser[session.userId] = it }
    }
}

private class FakeLocal(var snap: CloudSnapshot = empty()) : CloudLocalData {
    val backups = mutableMapOf<String, CloudSnapshot>()
    override suspend fun read(meta: SyncMeta) = snap.copy(
        progress = snap.progress.copy(resetAt = meta.resetAt), activity = snap.activity.copy(resetAt = meta.resetAt),
    )
    override suspend fun mergeIn(remote: CloudSnapshot, localResetAt: Long) {
        val local = read(SyncMeta(resetAt = localResetAt))
        val m = CloudMerge.snapshot(local, remote)
        snap = m
    }
    override suspend fun replaceWith(remote: CloudSnapshot?) { snap = remote ?: empty() }
    override val changes: Flow<Unit> = emptyFlow()
    override suspend fun saveBackup(owner: String, snapshot: CloudSnapshot) { backups[owner] = snapshot }
    override suspend fun takeBackup(owner: String) = backups[owner]
    override suspend fun deleteBackup(owner: String) { backups.remove(owner) }

    companion object {
        fun empty() = CloudSnapshot(ProgressEnvelope(), ActivityEnvelope(), ProgramsEnvelope(), SettingsEnvelope())
    }
}

class CloudSyncFlowTest {
    private fun session(uid: String) = SupabaseSession("at", "rt", Long.MAX_VALUE / 2, uid)
    private fun badges(vararg b: String) = FakeLocal.empty().let { it.copy(progress = it.progress.copy(data = UserProgress(earnedBadges = setOf(*b)))) }

    private fun sync(server: FakeServer, local: FakeLocal, meta: MemorySyncMetaStore, uid: String?) = CloudSync(
        auth = SupabaseAuth(), api = server, sessions = MemorySessionStore(uid?.let { session(it) }),
        local = local, meta = meta, accounts = null, clock = { 1_000L },
    )

    @Test fun guestNeverSyncs() = runBlocking {
        val server = FakeServer()
        val status = sync(server, FakeLocal(badges("L1")), MemorySyncMetaStore(), uid = null).syncNow()
        assertEquals(SyncStatus.Off, status)
        assertTrue(server.rowsByUser.isEmpty())
    }

    @Test fun existingLocalDataIsUploadedOnFirstSignIn() = runBlocking {
        val server = FakeServer()
        val meta = MemorySyncMetaStore()
        val status = sync(server, FakeLocal(badges("L1")), meta, "u1").syncNow()
        assertTrue(status is SyncStatus.Synced)
        assertEquals(setOf("L1"), server.rowsByUser["u1"]!!.snapshot.progress.data.earnedBadges)
        assertEquals("u1", meta.meta.owner)
    }

    @Test fun cloudDataIsMergedNotOverwrittenAndConflictsRetry() = runBlocking {
        val server = FakeServer()
        server.rowsByUser["u1"] = CloudRow(4, badges("CLOUD"))
        server.conflictsToInject = 1
        val local = FakeLocal(badges("PHONE"))
        val status = sync(server, local, MemorySyncMetaStore(), "u1").syncNow()
        assertTrue(status is SyncStatus.Synced)
        val stored = server.rowsByUser["u1"]!!
        assertEquals(setOf("CLOUD", "PHONE", "OTHER"), stored.snapshot.progress.data.earnedBadges)
        assertEquals(6L, stored.revision)
        assertEquals(setOf("CLOUD", "PHONE", "OTHER"), local.snap.progress.data.earnedBadges)
    }

    @Test fun noUploadWhenNothingChanged() = runBlocking {
        val server = FakeServer()
        val local = FakeLocal(badges("L1"))
        val meta = MemorySyncMetaStore()
        val s = sync(server, local, meta, "u1")
        s.syncNow()
        val writes = server.writes
        s.syncNow()
        s.syncNow(force = false)
        assertEquals(writes, server.writes)
    }

    @Test fun offlineKeepsLocalData() = runBlocking {
        val server = FakeServer().apply { offline = true }
        val local = FakeLocal(badges("L1"))
        val status = sync(server, local, MemorySyncMetaStore(), "u1").syncNow()
        assertEquals(SyncStatus.Offline, status)
        assertEquals(setOf("L1"), local.snap.progress.data.earnedBadges)
    }

    @Test fun anotherAccountNeverReceivesThisAccountsData() = runBlocking {
        val server = FakeServer()
        server.rowsByUser["u2"] = CloudRow(1, badges("U2"))
        val local = FakeLocal(badges("U1-UNSYNCED"))
        val meta = MemorySyncMetaStore(SyncMeta(owner = "u1"))
        sync(server, local, meta, "u2").syncNow()
        assertEquals(setOf("U2"), server.rowsByUser["u2"]!!.snapshot.progress.data.earnedBadges)
        assertEquals(setOf("U2"), local.snap.progress.data.earnedBadges)
        assertEquals(setOf("U1-UNSYNCED"), local.backups["u1"]!!.progress.data.earnedBadges)
        // u1 signs in again: the kept-aside data reaches u1's own account.
        sync(server, local, meta, "u1").syncNow()
        assertEquals(setOf("U1-UNSYNCED"), server.rowsByUser["u1"]!!.snapshot.progress.data.earnedBadges)
        assertTrue(local.backups.isEmpty())
    }

    @Test fun resetOnThisPhoneReachesTheCloud() = runBlocking {
        val server = FakeServer()
        server.rowsByUser["u1"] = CloudRow(1, badges("OLD"))
        val meta = MemorySyncMetaStore(SyncMeta(owner = "u1", resetAt = 0))
        val local = FakeLocal(FakeLocal.empty())
        val s = sync(server, local, meta, "u1")
        s.noteReset()
        s.syncNow()
        assertTrue(server.rowsByUser["u1"]!!.snapshot.progress.data.earnedBadges.isEmpty())
        assertTrue(server.rowsByUser["u1"]!!.snapshot.progress.resetAt > 0)
    }

    @Test fun unreadableCloudRowIsNotOverwritten() = runBlocking {
        val server = FakeServer()
        server.rowsByUser["u1"] = CloudRow(3, badges("NEW"), readable = false)
        val status = sync(server, FakeLocal(badges("L1")), MemorySyncMetaStore(), "u1").syncNow()
        assertTrue(status is SyncStatus.Failed)
        assertEquals(0, server.writes)
    }

    @Test fun rejectedTokenAsksToSignInAgain() = runBlocking {
        val server = object : CloudStateApi() {
            override suspend fun fetch(session: SupabaseSession): CloudRow? = throw SupabaseException(401, "JWT expired")
        }
        val sessions = MemorySessionStore(session("u1"))
        val failingHttp = object : com.pypath.app.cloud.SupabaseHttp() {
            override suspend fun request(method: String, path: String, body: String?, accessToken: String?, headers: Map<String, String>) =
                com.pypath.app.cloud.HttpResult(400, """{"msg":"Invalid Refresh Token"}""")
        }
        val s = CloudSync(SupabaseAuth(failingHttp), server, sessions, FakeLocal(), MemorySyncMetaStore(), null, clock = { 1L })
        val status = s.syncNow()
        assertTrue(status is SyncStatus.Failed)
        assertNull(sessions.session)
    }
}
