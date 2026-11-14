package com.pypath.app.cloud

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** "Sign in with Google" creates an account when needed; "Login with Google" only opens existing ones. */
enum class AuthMode(val wire: String) { SIGN_UP("signup"), LOG_IN("login") }

/** Answer of the server-side account check. */
sealed interface AccountCheck {
    data class Checked(val exists: Boolean) : AccountCheck
    /** The google-account-check Edge Function is not deployed in the Supabase project. */
    data object NotDeployed : AccountCheck
    data object RateLimited : AccountCheck
    /** The server did not accept the Google token (expired, wrong client id, nonce mismatch, ...). */
    data class Rejected(val reason: String) : AccountCheck
    data class Unavailable(val code: Int) : AccountCheck
}

/**
 * Client for the `google-account-check` Supabase Edge Function (see supabase/functions/).
 *
 * The function verifies the Google ID token itself and, with the service role (server side only),
 * checks whether a Supabase Auth user with that Google identity exists. Only the owner of a valid,
 * fresh Google ID token can learn anything, so it cannot be used to enumerate accounts. The app only
 * sends the publishable key; no privileged key is in the APK. Throws [java.io.IOException] offline.
 */
open class AccountCheckApi(private val http: SupabaseHttp = SupabaseHttp()) {

    open suspend fun check(idToken: String, rawNonce: String, mode: AuthMode): AccountCheck {
        val body = buildJsonObject {
            put("mode", mode.wire)
            put("id_token", idToken)
            put("nonce", rawNonce)
        }.toString()
        val r = http.request("POST", PATH, body = body)
        return parse(r)
    }

    companion object {
        const val PATH = "/functions/v1/google-account-check"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(r: HttpResult): AccountCheck {
            val obj = runCatching { json.parseToJsonElement(r.body).jsonObject }.getOrNull()
            fun str(k: String) = runCatching { obj?.get(k)?.jsonPrimitive?.content }.getOrNull().orEmpty()
            return when {
                r.code == 200 && obj != null && obj["exists"]?.jsonPrimitive?.booleanOrNull != null ->
                    AccountCheck.Checked(obj["exists"]!!.jsonPrimitive.booleanOrNull == true)
                r.code == 200 -> AccountCheck.Unavailable(r.code)
                r.code == 429 -> AccountCheck.RateLimited
                r.code == 401 -> AccountCheck.Rejected(str("reason").ifBlank { str("error") })
                // Supabase answers 404 with code NOT_FOUND / "Requested function was not found" when the
                // function does not exist (our own function never answers 404).
                r.code == 404 -> AccountCheck.NotDeployed
                else -> AccountCheck.Unavailable(r.code)
            }
        }
    }
}
