package com.pypath.app.cloud

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.time.Instant

/** The learner's row in `public.user_cloud_state`. */
data class CloudRow(
    val revision: Long,
    val snapshot: CloudSnapshot,
    /**
     * False when a column holds data this version cannot read (for example written by a newer
     * PyPath version). Such a row is never overwritten.
     */
    val readable: Boolean = true,
)

/**
 * PostgREST access to `public.user_cloud_state`. Every call carries the learner's own access token,
 * so Row Level Security limits it to the learner's own row. The table is never created or altered
 * by the app.
 */
open class CloudStateApi(private val http: SupabaseHttp = SupabaseHttp()) {

    private val path = "/rest/v1/${SupabaseConfig.TABLE}"

    open suspend fun fetch(session: SupabaseSession): CloudRow? {
        val r = http.request("GET", "$path?user_id=eq.${enc(session.userId)}&select=*", accessToken = session.accessToken)
        check(r)
        return CloudCodec.parseRows(r.body).firstOrNull()
    }

    /** Creates the row. Returns null if it already exists (created meanwhile by another phone). */
    open suspend fun insert(session: SupabaseSession, snapshot: CloudSnapshot): CloudRow? {
        val body = CloudCodec.rowJson(snapshot, revision = 1, userId = session.userId, now = Instant.now().toString())
        val r = http.request("POST", path, body = body, accessToken = session.accessToken, headers = mapOf("Prefer" to "return=representation"))
        if (r.code == 409) return null
        check(r)
        return CloudCodec.parseRows(r.body).firstOrNull()
    }

    /**
     * Optimistic update: only succeeds while the row is still at [expectedRevision]. Returns null
     * when another phone changed it first (the caller fetches, merges again and retries).
     */
    open suspend fun update(session: SupabaseSession, snapshot: CloudSnapshot, expectedRevision: Long): CloudRow? {
        val body = CloudCodec.rowJson(snapshot, revision = expectedRevision + 1, userId = null, now = Instant.now().toString())
        val r = http.request(
            "PATCH", "$path?user_id=eq.${enc(session.userId)}&revision=eq.$expectedRevision",
            body = body, accessToken = session.accessToken, headers = mapOf("Prefer" to "return=representation"),
        )
        check(r)
        return CloudCodec.parseRows(r.body).firstOrNull()
    }

    private fun check(r: HttpResult) {
        if (!r.ok) throw SupabaseException(r.code, SupabaseAuth.errorMessage(r.body).ifBlank { "HTTP ${r.code}" })
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

/** JSON encoding of the synced columns. Unreadable or missing columns decode as empty values. */
object CloudCodec {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    fun parseRows(body: String): List<CloudRow> {
        val arr = runCatching { json.parseToJsonElement(body).jsonArray }.getOrElse { JsonArray(emptyList()) }
        return arr.map { el ->
            val o = el.jsonObject
            var readable = true
            fun <T> col(name: String, s: KSerializer<T>, empty: T): T {
                val r = column(o[name], s)
                if (r is Column.Unreadable) readable = false
                return (r as? Column.Value<T>)?.value ?: empty
            }
            val snapshot = CloudSnapshot(
                progress = col("progress", ProgressEnvelope.serializer(), ProgressEnvelope()),
                activity = col("activity", ActivityEnvelope.serializer(), ActivityEnvelope()),
                programs = col("programs", ProgramsEnvelope.serializer(), ProgramsEnvelope()),
                settings = col("settings", SettingsEnvelope.serializer(), SettingsEnvelope()),
            )
            CloudRow(revision = (o["revision"] as? JsonPrimitive)?.longOrNull ?: 0L, snapshot = snapshot, readable = readable)
        }
    }

    private sealed interface Column<out T> {
        data object Empty : Column<Nothing>
        data object Unreadable : Column<Nothing>
        data class Value<T>(val value: T) : Column<T>
    }

    private fun <T> column(el: JsonElement?, s: KSerializer<T>): Column<T> {
        if (el == null || el is JsonNull) return Column.Empty
        // `json` columns may come back as an encoded string; `jsonb` as an object.
        val obj = (el as? JsonObject)
            ?: runCatching { json.parseToJsonElement(el.jsonPrimitive.content) as JsonObject }.getOrNull()
            ?: return Column.Unreadable
        if (obj.isEmpty()) return Column.Empty
        val version = (obj["v"] as? JsonPrimitive)?.longOrNull ?: return Column.Unreadable
        if (version > CLOUD_FORMAT) return Column.Unreadable
        return runCatching { Column.Value(json.decodeFromJsonElement(s, obj)) }.getOrElse { Column.Unreadable }
    }

    fun rowJson(s: CloudSnapshot, revision: Long, userId: String?, now: String): String = buildJsonObject {
        if (userId != null) put("user_id", userId)
        put("progress", json.encodeToJsonElement(ProgressEnvelope.serializer(), s.progress))
        put("activity", json.encodeToJsonElement(ActivityEnvelope.serializer(), s.activity))
        put("programs", json.encodeToJsonElement(ProgramsEnvelope.serializer(), s.programs))
        put("settings", json.encodeToJsonElement(SettingsEnvelope.serializer(), s.settings))
        put("revision", revision)
        put("updated_at", now)
    }.toString()

    /** Stable fingerprint, used to skip uploads when nothing changed. */
    fun fingerprint(s: CloudSnapshot): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(rowJson(s, 0, null, "").toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
