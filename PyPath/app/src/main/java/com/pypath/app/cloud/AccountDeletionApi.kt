package com.pypath.app.cloud

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Answer of the `delete-account` Edge Function. */
sealed interface AccountDeletion {
    /** The Supabase Auth user and the learner's cloud data were deleted. */
    data object Deleted : AccountDeletion
    /** The function is not deployed in the Supabase project: nothing was deleted. */
    data object NotDeployed : AccountDeletion
    /** The server did not accept the session (sign in again and retry). Nothing was deleted. */
    data object Unauthorized : AccountDeletion
    /** The server could not finish. [stage] says what was done; retrying is safe. */
    data class Failed(val code: Int, val stage: String) : AccountDeletion
}

/**
 * Client for the `delete-account` Supabase Edge Function (see supabase/functions/delete-account).
 *
 * The app sends only its own Supabase access token (and the publishable key). The function finds
 * the user id from that token on the server, deletes that user's `user_cloud_state` row and then the
 * Supabase Auth user (privacy consent records are removed with it), using the service role that
 * exists only on the server. The request body never names a user, so it cannot target anyone else.
 * Throws [java.io.IOException] when offline.
 */
open class AccountDeletionApi(private val http: SupabaseHttp = SupabaseHttp()) {

    open suspend fun delete(session: SupabaseSession): AccountDeletion {
        val body = buildJsonObject { put("confirm", CONFIRM_WORD) }.toString()
        return parse(http.request("POST", PATH, body = body, accessToken = session.accessToken))
    }

    companion object {
        const val PATH = "/functions/v1/delete-account"
        /** What the learner types to confirm, and what the function expects in the request body. */
        const val CONFIRM_WORD = "DELETE"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(r: HttpResult): AccountDeletion {
            val obj = runCatching { json.parseToJsonElement(r.body).jsonObject }.getOrNull()
            fun str(k: String) = runCatching { obj?.get(k)?.jsonPrimitive?.content }.getOrNull().orEmpty()
            return when {
                r.code == 200 && obj?.get("deleted")?.jsonPrimitive?.booleanOrNull == true -> AccountDeletion.Deleted
                r.code == 401 -> AccountDeletion.Unauthorized
                // Supabase answers 404 NOT_FOUND when the function does not exist (ours never answers 404).
                r.code == 404 -> AccountDeletion.NotDeployed
                else -> AccountDeletion.Failed(r.code, str("stage").ifBlank { str("error") })
            }
        }
    }
}
