package com.pypath.app.cloud

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URLEncoder

/** A signed-in Supabase session. Kept only in encrypted form on the phone (see [SessionStore]). */
@Serializable
data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    /** Epoch seconds when [accessToken] expires. */
    val expiresAt: Long,
    val userId: String,
    val email: String = "",
) {
    fun expiresSoon(nowSeconds: Long, marginSeconds: Long = 60): Boolean = nowSeconds + marginSeconds >= expiresAt
}

/**
 * Supabase Auth (GoTrue) calls: exchange a Google ID token for a session, refresh it, sign out.
 * The Google ID token is used once and never stored.
 */
class SupabaseAuth(private val http: SupabaseHttp = SupabaseHttp()) {

    /** `nonce` is the raw nonce whose SHA-256 was given to Google (see [com.pypath.app.auth.Nonce]). */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String): SupabaseSession {
        val body = buildJsonObject {
            put("provider", "google")
            put("id_token", idToken)
            put("nonce", rawNonce)
        }
        return token("id_token", body.toString())
    }

    suspend fun refresh(refreshToken: String): SupabaseSession =
        token("refresh_token", buildJsonObject { put("refresh_token", refreshToken) }.toString())

    /** Ends this session on the server (other devices stay signed in). Errors are ignored by callers. */
    suspend fun signOut(accessToken: String) {
        http.request("POST", "/auth/v1/logout?scope=local", body = "{}", accessToken = accessToken)
    }

    private suspend fun token(grant: String, body: String): SupabaseSession {
        val r = http.request("POST", "/auth/v1/token?grant_type=" + URLEncoder.encode(grant, "UTF-8"), body = body)
        if (!r.ok) throw SupabaseException(r.code, errorMessage(r.body).ifBlank { "HTTP ${r.code}" })
        return parseSession(r.body, System.currentTimeMillis() / 1000)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parseSession(body: String, nowSeconds: Long): SupabaseSession {
            val o = json.parseToJsonElement(body).jsonObject
            fun str(obj: JsonObject?, k: String) = obj?.get(k)?.jsonPrimitive?.content.orEmpty()
            val user = o["user"]?.jsonObject
            val access = str(o, "access_token")
            val refresh = str(o, "refresh_token")
            val userId = str(user, "id")
            if (access.isEmpty() || refresh.isEmpty() || userId.isEmpty()) throw SupabaseException(500, "Incomplete session from server")
            val expiresAt = o["expires_at"]?.jsonPrimitive?.longOrNull
                ?: (nowSeconds + (o["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600))
            return SupabaseSession(access, refresh, expiresAt, userId, str(user, "email"))
        }

        /** Readable message from a GoTrue / PostgREST error body. */
        fun errorMessage(body: String): String = runCatching {
            val o = json.parseToJsonElement(body).jsonObject
            listOf("msg", "error_description", "message", "error").firstNotNullOfOrNull { k ->
                o[k]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            }.orEmpty()
        }.getOrDefault("")
    }
}
