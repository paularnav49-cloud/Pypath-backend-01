package com.pypath.app.domain

import com.pypath.app.privacy.PrivacyPolicy

/**
 * What the learner may use right now. Decided only from the Supabase session (kept encrypted with
 * an Android Keystore key, see cloud/SessionStore.kt) and the confirmed Privacy Policy consent —
 * never from progress data, so editing saved progress or restarting the app cannot unlock lessons.
 */
sealed interface Access {
    /** Not signed in: only the first lesson of the course is open. */
    data object Guest : Access
    /** Signed in, but has not agreed to the current Privacy Policy: nothing but the policy screen. */
    data class ConsentRequired(val userId: String) : Access
    /** Signed in and agreed: the normal learning experience. */
    data class Full(val userId: String) : Access

    val limitedToFirstLesson: Boolean get() = this !is Full
}

object AccessRules {
    /**
     * @param sessionLoaded the stored session was read at least once (null result = guest)
     * @param userId Supabase user id of the stored session, or null
     * @param agreed confirmed consent per user id (null = cache not read yet)
     * @return null while still loading
     */
    fun derive(sessionLoaded: Boolean, userId: String?, agreed: Map<String, String>?): Access? {
        if (!sessionLoaded) return null
        if (userId == null) return Access.Guest
        if (agreed == null) return null
        return if (PrivacyPolicy.isConsentValid(agreed[userId])) Access.Full(userId) else Access.ConsentRequired(userId)
    }
}
