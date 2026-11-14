package com.pypath.app.domain

/** Where the app goes after a successful Google sign-in / login (once the account's data was loaded). */
enum class AfterAuth {
    /** Returning learner (onboarding already done for this account): straight to the dashboard. */
    DASHBOARD,
    /** New account: the first-time introduction, then the setup questions. */
    ONBOARDING_INTRO,
    /** New account on a phone whose guest already saw the introduction: only the setup questions. */
    ONBOARDING_SETUP,
}

/** First screen when the app opens (no sign-in happened in this app run). */
enum class StartScreen { SIGN_IN, ONBOARDING, DASHBOARD }

/**
 * Navigation rules for sign-in. Pure functions, so they are unit tested.
 *
 * The decision after sign-in is made from the ACCOUNT's data (restored from the cloud first) and
 * the server's account check — never from a flag only the phone set.
 */
object AuthFlow {

    /**
     * @param onboarded the account's progress says onboarding was completed (after the restore)
     * @param restored the account's cloud copy was loaded and merged
     * @param existed server-side account check: true = existing account, false = just created, null = unknown
     * @param guestOnboarded the guest profile on this phone already went through the introduction
     */
    fun afterAuth(onboarded: Boolean, restored: Boolean, existed: Boolean?, guestOnboarded: Boolean): AfterAuth {
        val newAccountFlow = if (guestOnboarded) AfterAuth.ONBOARDING_SETUP else AfterAuth.ONBOARDING_INTRO
        return when {
            onboarded -> AfterAuth.DASHBOARD
            // The account's real data is known (or it was just created): it has not done onboarding.
            restored || existed == false -> newAccountFlow
            // Existing account whose cloud copy could not be loaded (e.g. offline): never make a
            // returning learner repeat onboarding; the data arrives with the next sync.
            existed == true -> AfterAuth.DASHBOARD
            else -> newAccountFlow
        }
    }

    /**
     * @param onboarded this phone's live profile completed onboarding
     * @param needsSignIn nobody signed in and the learner did not choose guest mode yet
     * @param signedInWithConsent a signed-in learner with accepted Privacy Policy
     */
    fun start(onboarded: Boolean, needsSignIn: Boolean, signedInWithConsent: Boolean): StartScreen = when {
        // First the welcome / authentication screen: a returning learner can log in before any
        // introduction is shown; new accounts and guests see the introduction afterwards.
        needsSignIn && !signedInWithConsent -> StartScreen.SIGN_IN
        !onboarded -> StartScreen.ONBOARDING
        else -> StartScreen.DASHBOARD
    }
}
