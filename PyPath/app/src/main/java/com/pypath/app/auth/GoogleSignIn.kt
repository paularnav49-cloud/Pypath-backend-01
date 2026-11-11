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
import com.pypath.app.cloud.SupabaseException
import com.pypath.app.data.auth.GoogleAccount
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.security.SecureRandom

/** Outcome of one "Sign in with Google" attempt. */
sealed interface SignInResult {
    data class Success(val account: GoogleAccount) : SignInResult
    /** The learner closed the Google sheet. Not an error. */
    data object Cancelled : SignInResult
    data class Failure(val message: String) : SignInResult
}

/**
 * Google Sign-In through Android Credential Manager ("Sign in with Google" button flow), followed by
 * Supabase sign-in with the Google ID token.
 *
 * Google Play services shows the account picker and talks to Google. The ID token is then exchanged
 * once for a Supabase session (see [com.pypath.app.cloud.CloudSync.signInWithGoogle]) and never
 * stored. The learner is signed in only if that exchange succeeds. A fresh nonce protects against
 * replay: Google receives its SHA-256, Supabase the raw value, and Supabase checks they match.
 * Needs a Web OAuth client id in res/values/google_auth.xml; see README "Google Sign-In".
 */
object GoogleSignIn {

    fun webClientId(context: Context): String = context.getString(R.string.google_web_client_id).trim()

    fun isConfigured(context: Context): Boolean = GoogleAuthConfig.isValidWebClientId(webClientId(context))

    /** [activityContext] must be an Activity context (Credential Manager shows its sheet over it). */
    suspend fun signIn(activityContext: Context): SignInResult {
        val clientId = webClientId(activityContext)
        if (!GoogleAuthConfig.isValidWebClientId(clientId)) {
            return SignInResult.Failure(
                "Google Sign-In is not set up in this build yet. Put your Web client ID in " +
                    "app/src/main/res/values/google_auth.xml and build again.",
            )
        }
        val cloud = (activityContext.applicationContext as? PyPathApp)?.container?.cloudSync
            ?: return SignInResult.Failure("Sign-in is not available right now. Please restart PyPath and try again.")
        val rawNonce = Nonce.newRaw()
        val option = GetSignInWithGoogleOption.Builder(clientId).setNonce(Nonce.sha256Hex(rawNonce)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                // Supabase must accept the token before the learner counts as signed in.
                val session = cloud.signInWithGoogle(google.idToken, rawNonce)
                if (session.isFailure) {
                    return SignInResult.Failure(GoogleAuthConfig.friendlyCloudError(session.exceptionOrNull()))
                }
                SignInResult.Success(
                    GoogleAccount(
                        id = GoogleAuthConfig.subjectFromIdToken(google.idToken) ?: google.id,
                        email = google.id,
                        displayName = google.displayName.orEmpty(),
                        givenName = google.givenName.orEmpty(),
                    ),
                )
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
