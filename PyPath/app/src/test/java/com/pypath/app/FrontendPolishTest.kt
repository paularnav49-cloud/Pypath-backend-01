package com.pypath.app

import com.pypath.app.domain.DailyGoal
import com.pypath.app.domain.LearningTime
import com.pypath.app.premium.Availability
import com.pypath.app.premium.Entitlements
import com.pypath.app.premium.PremiumComparison
import com.pypath.app.premium.PremiumFeature
import com.pypath.app.premium.PremiumOffers
import com.pypath.app.premium.PremiumPlan
import com.pypath.app.premium.PremiumPricing
import com.pypath.app.premium.PremiumSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Final frontend polish: pricing, comparison table, offer rules and onboarding time goal. */
class FrontendPolishTest {

    private val free = Entitlements.FREE
    private val testMode = Entitlements.all(PremiumSource.LOCAL_DEV)

    // ── Pricing (centralised) ──

    @Test fun monthlyPriceIs79() {
        assertEquals(79, PremiumPlan.MONTHLY.priceRupees)
        assertEquals("\u20B979/month", PremiumPlan.MONTHLY.fullLabel)
    }

    @Test fun yearlyPriceIs799() {
        assertEquals(799, PremiumPlan.YEARLY.priceRupees)
        assertEquals("\u20B9799/year", PremiumPlan.YEARLY.fullLabel)
    }

    @Test fun twelveMonthlyPaymentsCost948AndYearlySaves149() {
        assertEquals(948, PremiumPricing.monthlyForOneYear)
        assertEquals(149, PremiumPricing.yearlySavings)
        assertEquals(15, PremiumPricing.yearlySavingsPercent)
    }

    // ── Free vs Premium comparison ──

    @Test fun comparisonTableMatchesSpec() {
        val expected = listOf(
            Triple("Python lessons", Availability.YES, Availability.YES),
            Triple("Existing basic practical coding", Availability.YES, Availability.YES),
            Triple("Basic quizzes", Availability.YES, Availability.YES),
            Triple("Learning Analytics", Availability.YES, Availability.YES),
            Triple("Offline Learning Library", Availability.YES, Availability.YES),
            Triple("Advanced Python IDE upgrades", Availability.LOCKED, Availability.YES),
            Triple("Premium Image Center upgrades", Availability.LOCKED, Availability.YES),
            Triple("Advanced Project Library", Availability.LIMITED, Availability.FULL),
            Triple("Advanced Quiz Arena", Availability.LIMITED, Availability.FULL),
            Triple("Premium Code Battle", Availability.LOCKED, Availability.YES),
            Triple("Premium badges and rewards", Availability.LIMITED, Availability.YES),
        )
        assertEquals(expected, PremiumComparison.rows.map { Triple(it.feature, it.free, it.premium) })
    }

    // ── Offer rules (single source of truth: Entitlements) ──

    @Test fun freeUserTappingLockedFeatureSeesOffer() {
        PremiumFeature.entries.forEach { assertTrue(it.name, PremiumOffers.shouldOfferFor(free, it)) }
    }

    @Test fun premiumTestModeNeverSeesOffer() {
        PremiumFeature.entries.forEach { assertFalse(it.name, PremiumOffers.shouldOfferFor(testMode, it)) }
    }

    @Test fun partialEntitlementOnlyOffersMissingFeatures() {
        val onlyIde = Entitlements(setOf(PremiumFeature.ADVANCED_IDE), PremiumSource.LOCAL_DEV)
        assertFalse(PremiumOffers.shouldOfferFor(onlyIde, PremiumFeature.ADVANCED_IDE))
        assertTrue(PremiumOffers.shouldOfferFor(onlyIde, PremiumFeature.CODE_BATTLE))
    }

    @Test fun introOfferShownOnceForNewFreeLearner() {
        assertTrue(PremiumOffers.shouldShowIntroOffer(free, firstLessonPending = true, introOfferShown = false))
        // Never repeats.
        assertFalse(PremiumOffers.shouldShowIntroOffer(free, firstLessonPending = true, introOfferShown = true))
        // Existing learners (no new onboarding) never get it.
        assertFalse(PremiumOffers.shouldShowIntroOffer(free, firstLessonPending = false, introOfferShown = false))
    }

    @Test fun introOfferSkippedForPremiumAndTestMode() {
        assertFalse(PremiumOffers.shouldShowIntroOffer(testMode, firstLessonPending = true, introOfferShown = false))
    }

    // ── Onboarding daily time goal ──

    @Test fun onboardingOffersExactlyFourChoices() {
        assertEquals(listOf(5, 10, 15, 30), LearningTime.OPTIONS)
        assertTrue(LearningTime.isValid(15))
        assertFalse(LearningTime.isValid(20))
        assertFalse(LearningTime.isValid(null))
    }

    @Test fun monthlyImpactLabels() {
        assertEquals("2 hours 30 minutes", LearningTime.monthlyLabel(5))
        assertEquals("5 hours", LearningTime.monthlyLabel(10))
        assertEquals("7 hours 30 minutes", LearningTime.monthlyLabel(15))
        assertEquals("15 hours", LearningTime.monthlyLabel(30))
    }

    @Test fun impactMessageMatchesSpecExample() {
        assertEquals(
            "You chose 15 minutes every day. That's 7 hours and 30 minutes of Python learning in a month!",
            LearningTime.impactMessage(15),
        )
        assertEquals("You chose 10 minutes every day. That's 5 hours of Python learning in a month!", LearningTime.impactMessage(10))
    }

    @Test fun suggestedXpGoalNeverTurnsGoalOff() {
        LearningTime.OPTIONS.forEach { assertTrue(LearningTime.suggestedXpGoal(it) != DailyGoal.OFF) }
        assertEquals(DailyGoal.CASUAL, LearningTime.suggestedXpGoal(5))
        assertEquals(DailyGoal.SERIOUS, LearningTime.suggestedXpGoal(30))
    }

    @Test fun shortLabels() {
        assertEquals("2h 30m", LearningTime.shortLabel(150))
        assertEquals("5h", LearningTime.shortLabel(300))
        assertEquals("15m", LearningTime.shortLabel(15))
    }
}
