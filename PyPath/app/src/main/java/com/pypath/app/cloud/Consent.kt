package com.pypath.app.cloud

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pypath.app.privacy.PrivacyPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLEncoder

// ───────────── Server: public.privacy_consents ─────────────

/** The newest consent event of a user on the server. */
data class ConsentEvent(val version: String, val status: String) {
    val agreed: Boolean get() = status == ConsentApi.AGREED
}

/**
 * PostgREST access to `public.privacy_consents` (see supabase/migrations). Every call carries the
 * learner's own access token, so Row Level Security limits it to the learner's own rows; the server
 * fills `user_id` from the token and `consented_at` from its own clock. No token is ever stored in
 * the record.
 *
 * The table is an append-only log: "agreed" and "withdrawn" events. The newest event decides
 * whether the learner currently consents (see [latest]).
 */
open class ConsentApi(private val http: SupabaseHttp = SupabaseHttp()) {

    private val path = "/rest/v1/privacy_consents"

    /** The learner's newest consent event, or null when there is none. */
    open suspend fun latest(session: SupabaseSession): ConsentEvent? {
        val r = http.request(
            "GET", "$path?user_id=eq.${enc(session.userId)}&select=policy_version,status&order=id.desc&limit=1",
            accessToken = session.accessToken,
        )
        if (!r.ok) throw SupabaseException(r.code, SupabaseAuth.errorMessage(r.body).ifBlank { "HTTP ${r.code}" })
        return parseEvents(r.body).firstOrNull()
    }

    /** Versions the learner currently agrees to: the newest event if it is an agreement, else none. */
    open suspend fun agreedVersions(session: SupabaseSession): List<String> =
        latest(session)?.takeIf { it.agreed }?.let { listOf(it.version) }.orEmpty()

    /**
     * Records agreement to [version] together with the learner's self-declared age confirmation
     * (18 or older, or 12 to 17 with a parent's or guardian's agreement; not verified). If the database was not updated yet (no `age_confirmed` column), the agreement is
     * recorded without it; the agreed policy version itself requires that confirmation.
     */
    open suspend fun recordAgreement(session: SupabaseSession, version: String) {
        val r = post(session, version, AGREED, ageConfirmed = true)
        if (r.ok || r.code == 409) return
        if (r.code == 400 && r.body.contains("age_confirmed")) {
            val again = post(session, version, AGREED, ageConfirmed = null)
            if (again.ok || again.code == 409) return
            throw SupabaseException(again.code, SupabaseAuth.errorMessage(again.body).ifBlank { "HTTP ${again.code}" })
        }
        throw SupabaseException(r.code, SupabaseAuth.errorMessage(r.body).ifBlank { "HTTP ${r.code}" })
    }

    /** Records that the learner withdrew consent (needs the 20261114000000 correction migration). */
    open suspend fun recordWithdrawal(session: SupabaseSession, version: String) {
        val r = post(session, version, WITHDRAWN, ageConfirmed = null)
        if (r.ok) return
        throw SupabaseException(r.code, SupabaseAuth.errorMessage(r.body).ifBlank { "HTTP ${r.code}" })
    }

    private suspend fun post(session: SupabaseSession, version: String, status: String, ageConfirmed: Boolean?): HttpResult {
        val body = buildJsonObject {
            put("user_id", session.userId)
            put("policy_version", version)
            put("status", status)
            if (ageConfirmed != null) put("age_confirmed", ageConfirmed)
        }.toString()
        return http.request("POST", path, body = body, accessToken = session.accessToken, headers = mapOf("Prefer" to "return=minimal"))
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        const val AGREED = "agreed"
        const val WITHDRAWN = "withdrawn"
        private val json = Json { ignoreUnknownKeys = true }

        fun parseVersions(body: String): List<String> = parseEvents(body).map { it.version }

        /** Rows of `policy_version` (+ optional `status`, default agreed). Unreadable input = none. */
        fun parseEvents(body: String): List<ConsentEvent> = runCatching {
            json.parseToJsonElement(body).jsonArray.mapNotNull { el ->
                val o = el.jsonObject
                val v = (o["policy_version"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val st = (o["status"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: AGREED
                ConsentEvent(v, st)
            }
        }.getOrDefault(emptyList())
    }
}

// ───────────── Phone: cache of confirmed consent ─────────────

/**
 * Remembers, per user id, the newest policy version whose consent was confirmed by the server, so a
 * learner who already agreed is not asked again on every start (and can open the app offline).
 * The server record stays the source of truth: the cache is only written after the server confirmed
 * a record, and it is cleared for a user when the server reports no valid record.
 * [agreed] is null until the cache has been read.
 */
interface ConsentStore {
    val agreed: StateFlow<Map<String, String>?>
    suspend fun load()
    suspend fun markAgreed(userId: String, version: String)
    suspend fun forget(userId: String)
}

private val Context.consentStore: DataStore<Preferences> by preferencesDataStore(name = "pypath_privacy_consent")

class LocalConsentStore(private val context: Context, scope: CoroutineScope? = null) : ConsentStore {
    private val key = stringPreferencesKey("agreed_v1")
    private val json = Json { ignoreUnknownKeys = true }
    private val _agreed = MutableStateFlow<Map<String, String>?>(null)
    override val agreed: StateFlow<Map<String, String>?> = _agreed

    init { scope?.launch { load() } }

    override suspend fun load() {
        if (_agreed.value != null) return
        val raw = runCatching { context.consentStore.data.first()[key] }.getOrNull()
        _agreed.value = decode(raw)
    }

    override suspend fun markAgreed(userId: String, version: String) {
        load()
        val current = _agreed.value.orEmpty()
        val best = listOfNotNull(current[userId], version).maxOrNull() ?: version
        val next = current + (userId to best)
        _agreed.value = next
        runCatching { context.consentStore.edit { it[key] = encode(next) } }
    }

    override suspend fun forget(userId: String) {
        load()
        val next = _agreed.value.orEmpty() - userId
        _agreed.value = next
        runCatching { context.consentStore.edit { it[key] = encode(next) } }
    }

    private fun encode(m: Map<String, String>) = buildJsonObject { m.forEach { (k, v) -> put(k, v) } }.toString()

    private fun decode(raw: String?): Map<String, String> = runCatching {
        if (raw.isNullOrBlank()) emptyMap()
        else json.parseToJsonElement(raw).jsonObject.mapValues { it.value.jsonPrimitive.content }
    }.getOrDefault(emptyMap())
}

/** In-memory version for tests. */
class MemoryConsentStore(initial: Map<String, String> = emptyMap()) : ConsentStore {
    private val _agreed = MutableStateFlow<Map<String, String>?>(initial)
    override val agreed: StateFlow<Map<String, String>?> = _agreed
    override suspend fun load() {}
    override suspend fun markAgreed(userId: String, version: String) {
        val current = _agreed.value.orEmpty()
        _agreed.value = current + (userId to (listOfNotNull(current[userId], version).maxOrNull() ?: version))
    }
    override suspend fun forget(userId: String) { _agreed.value = _agreed.value.orEmpty() - userId }
}

/** True when [versions] contains a version that is still valid for [PrivacyPolicy.RENEWAL_REQUIRED_SINCE]. */
fun consentSatisfied(versions: Collection<String>): Boolean = versions.any { PrivacyPolicy.isConsentValid(it) }

// ───────────── Session tracking ─────────────

/**
 * Wraps a [SessionStore] and publishes the signed-in Supabase user id, so the app can decide
 * synchronously whether the learner is a guest. Every save/clear of the session (sign-in, refresh,
 * sign-out, expiry) goes through here. [loaded] is false until the stored session was read once.
 */
class TrackedSessionStore(private val inner: SessionStore) : SessionStore {
    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    override suspend fun load(): SupabaseSession? {
        val s = inner.load()
        _userId.value = s?.userId
        _loaded.value = true
        return s
    }

    override suspend fun save(session: SupabaseSession) {
        inner.save(session)
        _userId.value = session.userId
        _loaded.value = true
    }

    override suspend fun clear() {
        inner.clear()
        _userId.value = null
        _loaded.value = true
    }
}
