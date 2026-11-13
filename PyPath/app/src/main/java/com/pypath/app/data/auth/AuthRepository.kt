package com.pypath.app.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Google Sign-In: its own DataStore file, so "Reset progress" never signs the learner out and
// signing out never touches progress, XP, streaks or settings.
private val Context.accountStore: DataStore<Preferences> by preferencesDataStore(name = "pypath_account")

/** The Google account the learner signed in with. Only these fields are kept, on the phone only. */
data class GoogleAccount(
    /** Google's stable account id (the ID token "sub"); falls back to the email. */
    val id: String,
    val email: String,
    val displayName: String = "",
    val givenName: String = "",
) {
    /** Best short name for greetings: given name, else first word of the display name, else the email name. */
    val firstName: String
        get() = givenName.trim().ifEmpty { displayName.trim().substringBefore(' ') }.ifEmpty { email.substringBefore('@') }

    /** One or two letters for the avatar circle. */
    val initials: String
        get() {
            val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val letters = when {
                parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}"
                parts.size == 1 -> parts.first().take(1)
                else -> email.take(1)
            }
            return letters.uppercase().ifEmpty { "?" }
        }
}

/**
 * [user] is the signed-in account, or null. [skipped] is true once the learner chose
 * "Continue without signing in", so the sign-in screen is not shown again on every start.
 */
data class AccountState(val user: GoogleAccount? = null, val skipped: Boolean = false) {
    val signedIn: Boolean get() = user != null
    /** The sign-in screen is shown only to learners who neither signed in nor skipped. */
    val needsSignIn: Boolean get() = user == null && !skipped
}

interface AuthRepository {
    val account: Flow<AccountState>
    suspend fun signIn(user: GoogleAccount)
    suspend fun signOut()
    suspend fun skip()
}

class LocalAuthRepository(private val context: Context) : AuthRepository {
    private val idKey = stringPreferencesKey("google_id")
    private val emailKey = stringPreferencesKey("google_email")
    private val nameKey = stringPreferencesKey("google_display_name")
    private val givenKey = stringPreferencesKey("google_given_name")
    private val skippedKey = booleanPreferencesKey("sign_in_skipped")

    override val account: Flow<AccountState> = context.accountStore.data.map { p ->
        val id = p[idKey]
        val email = p[emailKey]
        val user = if (!id.isNullOrEmpty() && !email.isNullOrEmpty()) {
            GoogleAccount(id = id, email = email, displayName = p[nameKey].orEmpty(), givenName = p[givenKey].orEmpty())
        } else null
        AccountState(user = user, skipped = p[skippedKey] ?: false)
    }

    override suspend fun signIn(user: GoogleAccount) {
        context.accountStore.edit {
            it[idKey] = user.id
            it[emailKey] = user.email
            it[nameKey] = user.displayName
            it[givenKey] = user.givenName
            it[skippedKey] = false
        }
    }

    /** Forgets the account. Marks the screen as skipped, so the app keeps opening on the dashboard. */
    override suspend fun signOut() {
        context.accountStore.edit {
            it.remove(idKey); it.remove(emailKey); it.remove(nameKey); it.remove(givenKey)
            it[skippedKey] = true
        }
    }

    override suspend fun skip() {
        context.accountStore.edit { it[skippedKey] = true }
    }
}
