package com.pypath.app

import com.pypath.app.auth.GoogleAuthConfig
import com.pypath.app.data.auth.AccountState
import com.pypath.app.data.auth.GoogleAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class GoogleAuthTest {

    @Test
    fun webClientIdValidation() {
        assertTrue(GoogleAuthConfig.isValidWebClientId("1234567890-abc123.apps.googleusercontent.com"))
        assertTrue(GoogleAuthConfig.isValidWebClientId("  1234567890-abc123.apps.googleusercontent.com  "))
        assertFalse(GoogleAuthConfig.isValidWebClientId(""))
        assertFalse(GoogleAuthConfig.isValidWebClientId(".apps.googleusercontent.com"))
        assertFalse(GoogleAuthConfig.isValidWebClientId("YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"))
        assertFalse(GoogleAuthConfig.isValidWebClientId("not a client id"))
    }

    @Test
    fun subjectIsReadFromIdToken() {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val header = enc.encodeToString("""{"alg":"RS256"}""".toByteArray())
        val payload = enc.encodeToString("""{"sub":"110169484474386276334","email":"a@b.com"}""".toByteArray())
        assertEquals("110169484474386276334", GoogleAuthConfig.subjectFromIdToken("$header.$payload.sig"))
        assertNull(GoogleAuthConfig.subjectFromIdToken("garbage"))
        assertNull(GoogleAuthConfig.subjectFromIdToken(""))
    }

    @Test
    fun accountNamesAndInitials() {
        val full = GoogleAccount(id = "1", email = "arnav.paul@gmail.com", displayName = "Arnav Kumar Paul", givenName = "Arnav")
        assertEquals("Arnav", full.firstName)
        assertEquals("AP", full.initials)
        val noGiven = GoogleAccount(id = "1", email = "x@y.com", displayName = "Riya Sen")
        assertEquals("Riya", noGiven.firstName)
        val emailOnly = GoogleAccount(id = "1", email = "coder@y.com")
        assertEquals("coder", emailOnly.firstName)
        assertEquals("C", emailOnly.initials)
    }

    @Test
    fun signInScreenOnlyWhenNeitherSignedInNorSkipped() {
        assertTrue(AccountState().needsSignIn)
        assertFalse(AccountState(skipped = true).needsSignIn)
        assertFalse(AccountState(user = GoogleAccount("1", "a@b.com")).needsSignIn)
        assertTrue(AccountState(user = GoogleAccount("1", "a@b.com")).signedIn)
    }

    @Test
    fun configErrorsGetAHelpfulMessage() {
        assertTrue(GoogleAuthConfig.friendlyError("[28444] Developer console is not set up correctly.").contains("SHA-1"))
        assertEquals("Sign-in failed. Please try again.", GoogleAuthConfig.friendlyError(null))
    }
}
