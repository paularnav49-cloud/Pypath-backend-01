package com.pypath.app.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.pypath.app.PyPathApp
import com.pypath.app.R
import com.pypath.app.cloud.AccountCheck
import com.pypath.app.cloud.AccountCheckException
import com.pypath.app.cloud.AuthMode
import com.pypath.app.cloud.GoogleAuthOutcome
import com.pypath.app.cloud.SupabaseException
import com.pypath.app.data.auth.GoogleAccount
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.security.SecureRandom

/** Outcome of one "Sign in with Google" / "Login with Google" attempt. */
sealed interface SignInResult {
    /** [existed] = the PyPath account was there before (null when the server could not say). */
    data class Success(val account: GoogleAccount, val existed: Boolean? = null) : SignInResult
    /** The learner closed the Google sheet. Not an error. */
    data object Cancelled : SignInResult
    data class Failure(val message: String) : SignInResult
    /**
     * "Login with Google": no PyPath account exists for the chosen Google account. Nothing was
     * created. [pending] lets "Sign in with Google" create the account with the same identity.
     */
    data class AccountNotFound(val pending: PendingGoogleSignUp) : SignInResult
}

/**
 * The Google identity chosen during a "Login with Google" that found no account, kept in memory
 * only (never saved, never logged) so the learner can create the account without picking again.
 * Google ID tokens are short-lived; if it is no longer accepted the account picker opens again.
 */
class PendingGoogleSignUp internal constructor(
    internal val idToken: String,
    internal val rawNonce: String,
    val email: String,
    internal val account: GoogleAccount,
) {
    override fun toString(): String = "PendingGoogleSignUp(email=$email)"
}

/**
 * Google Sign-In through Android Credential Manager ("Sign in with Google" button flow), followed by
 * Supabase sign-in with the Google ID token.
 *
 * Google Play services shows the account picker and talks to Google. The ID token is checked by the
 * PyPath server (Sign up vs Login, see [com.pypath.app.cloud.CloudSync.authenticateWithGoogle]) and
 * exchanged once for a Supabase session; it is never stored. The learner is signed in only if that
 * exchange succeeds. A fresh nonce protects against replay: Google receives its SHA-256, Supabase the
 * raw value, and Supabase checks they match.
 * Needs a Web OAuth client id in res/values/google_auth.xml; see README "Google Sign-In".
 */
object GoogleSignIn {

    fun webClientId(context: Context): String = context.getString(R.string.google_web_client_id).trim()

    fun isConfigured(context: Context): Boolean = GoogleAuthConfig.isValidWebClientId(webClientId(context))

    /**
     * [activityContext] must be an Activity context (Credential Manager shows its sheet over it).
     * [mode] SIGN_UP = "Sign in with Google" (creates the account if needed);
     * LOG_IN = "Login with Google" (existing accounts only, never creates one).
     */
    suspend fun signIn(activityContext: Context, mode: AuthMode = AuthMode.SIGN_UP): SignInResult {
        val clientId = webClientId(activityContext)
        if (!GoogleAuthConfig.isValidWebClientId(clientId)) {
            return SignInResult.Failure(
                "Google Sign-In is not set up in this build yet. Put your Web client ID in " +
                    "app/src/main/res/values/google_auth.xml and build again.",
            )
        }
        val cloud = cloudOf(activityContext)
            ?: return SignInResult.Failure("Sign-in is not available right now. Please restart PyPath and try again.")
        val rawNonce = Nonce.newRaw()
        val option = GetSignInWithGoogleOption.Builder(clientId).setNonce(Nonce.sha256Hex(rawNonce)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                val account = GoogleAccount(
                    id = GoogleAuthConfig.subjectFromIdToken(google.idToken) ?: google.id,
                    email = google.id,
                    displayName = google.displayName.orEmpty(),
                    givenName = google.givenName.orEmpty(),
                )
                complete(cloud, google.idToken, rawNonce, account, mode)
            } else {
                SignInResult.Failure("Google returned an unexpected sign-in type. Please try again.")
            }
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: NoCredentialException) {
            SignInResult.Failure("No Google account was found. Add a Google account in your phone's Settings, then try again.")
        } catch (e: GoogleIdTokenParsingException) {
            SignInResult.Failure("Could not read the Google sign-in response. Please try again.")
        } catch (e: GetCredentialException) {
            SignInResult.Failure(GoogleAuthConfig.friendlyError(e.message))
        } catch (e: Exception) {
            SignInResult.Failure(GoogleAuthConfig.friendlyError(e.message))
        }
    }

    /**
     * "Sign in with Google" after "Account not found": creates the account for the Google identity
     * the learner already chose. If that sign-in proof has expired, the account picker opens again.
     */
    suspend fun signUpWith(activityContext: Context, pending: PendingGoogleSignUp): SignInResult {
        val cloud = cloudOf(activityContext) ?: return signIn(activityContext, AuthMode.SIGN_UP)
        val outcome = cloud.authenticateWithGoogle(pending.idToken, pending.rawNonce, AuthMode.SIGN_UP)
        val e = outcome.exceptionOrNull()
        if (e != null) {
            // Expired / no longer accepted proof of the Google identity: ask Google again.
            val stale = (e is AccountCheckException && e.check is AccountCheck.Rejected) ||
                (e is SupabaseException && e.code in 400..499 && e.code != 429)
            return if (stale) signIn(activityContext, AuthMode.SIGN_UP) else SignInResult.Failure(GoogleAuthConfig.friendlyCloudError(e))
        }
        return toResult(outcome.getOrThrow(), pending.idToken, pending.rawNonce, pending.account)
    }

    private suspend fun complete(
        cloud: com.pypath.app.cloud.CloudSync,
        idToken: String,
        rawNonce: String,
        account: GoogleAccount,
        mode: AuthMode,
    ): SignInResult {
        // The PyPath server must accept the token before the learner counts as signed in.
        val outcome = cloud.authenticateWithGoogle(idToken, rawNonce, mode)
        val o = outcome.getOrElse { return SignInResult.Failure(GoogleAuthConfig.friendlyCloudError(it)) }
        return toResult(o, idToken, rawNonce, account)
    }

    private fun toResult(o: GoogleAuthOutcome, idToken: String, rawNonce: String, account: GoogleAccount): SignInResult = when (o) {
        GoogleAuthOutcome.AccountNotFound -> SignInResult.AccountNotFound(PendingGoogleSignUp(idToken, rawNonce, account.email, account))
        is GoogleAuthOutcome.SignedIn -> SignInResult.Success(account, o.existed)
    }

    private fun cloudOf(context: Context) = (context.applicationContext as? PyPathApp)?.container?.cloudSync

    /** Tells Credential Manager / Google to forget the current sign-in. Never throws. */
    suspend fun signOut(context: Context) {
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

}

/** Sign-in nonce: a random value per attempt; Google gets its SHA-256 (hex), Supabase the raw value. */
object Nonce {
    fun newRaw(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun sha256Hex(raw: String): String =
        MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** Pure helpers (no Android types), covered by unit tests. */
object GoogleAuthConfig {
    private const val SUFFIX = ".apps.googleusercontent.com"

    /** A Web OAuth client id looks like 1234567890-abc123.apps.googleusercontent.com. */
    fun isValidWebClientId(id: String): Boolean {
        val t = id.trim()
        return t.endsWith(SUFFIX) && t.length > SUFFIX.length && !t.contains(' ') && !t.startsWith("YOUR_")
    }

    /** Reads the "sub" claim (stable Google account id) from a JWT ID token, or null. Not a verification. */
    fun subjectFromIdToken(idToken: String): String? = runCatching {
        val payload = idToken.split('.').getOrNull(1) ?: return null
        val json = String(java.util.Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '=')), Charsets.UTF_8)
        Json.parseToJsonElement(json).jsonObject["sub"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** Message for a failed Supabase sign-in (the Google part already worked). */
    fun friendlyCloudError(e: Throwable?): String = when {
        e is java.io.IOException -> "No internet connection. Connect to the internet and try again."
        e is AccountCheckException -> when (e.check) {
            AccountCheck.NotDeployed ->
                "Login with Google is not available yet: the PyPath account check service is not set up on the server. " +
                    "Please try again later."
            AccountCheck.RateLimited -> "Too many sign-in attempts. Please wait a few minutes and try again."
            is AccountCheck.Rejected -> "Your Google sign-in could not be verified. Please try again."
            else -> "PyPath's sign-in service is temporarily unavailable. Please try again in a moment."
        }
        e is SupabaseException && e.message.orEmpty().contains("PYPATH_SIGNUP_REQUIRED") ->
            "No PyPath account exists for this Google account yet. Use \"Sign in with Google\" to create one."
        e is SupabaseException && (e.message.orEmpty().contains("audience", ignoreCase = true) ||
            e.message.orEmpty().contains("provider", ignoreCase = true)) ->
            "Your PyPath account could not be opened: Google sign-in is not enabled correctly on the PyPath server (${e.message})."
        e is SupabaseException && e.message.orEmpty().contains("nonce", ignoreCase = true) ->
            "Sign-in check failed. Please try again."
        e is SupabaseException -> "Could not sign in to your PyPath account (${e.code}). Please try again."
        else -> "Could not sign in to your PyPath account. Please try again."
    }

    fun friendlyError(raw: String?): String {
        val m = raw.orEmpty()
        return when {
            m.contains("28444") || m.contains("Developer console", ignoreCase = true) || m.contains("[10]") ->
                "Google Sign-In is not set up correctly for this app. In Google Cloud Console, add an Android OAuth " +
                    "client with package name com.pypath.app and this build's SHA-1, and use the Web client ID in google_auth.xml."
            m.contains("network", ignoreCase = true) || m.contains("7:") ->
                "No connection to Google. Check your internet connection and try again."
            m.isBlank() -> "Sign-in failed. Please try again."
            else -> "Sign-in failed: $m"
        }
    }
}
