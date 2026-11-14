package com.pypath.app.premium

/**
 * Final frontend polish: every Premium price shown in the app comes from here, so prices can be
 * changed in one place when real payments are added on the backend.
 *
 * DISPLAY ONLY. Nothing in this file takes or verifies a payment. Real checkout (Razorpay) and
 * entitlement verification will be implemented on the backend; until then the only way to get
 * Premium access is the existing local Premium test mode ([LocalDevPremiumRepository]).
 */
enum class PremiumPlan(val id: String, val label: String, val pricePaise: Int, val periodLabel: String, val months: Int) {
    MONTHLY("monthly", "Monthly", 79_00, "month", 1),
    YEARLY("yearly", "Yearly", 799_00, "year", 12);

    /** Whole rupees (all current prices are whole rupees). */
    val priceRupees: Int get() = pricePaise / 100
    val priceLabel: String get() = PremiumPricing.rupees(priceRupees)
    /** e.g. "₹79/month". */
    val fullLabel: String get() = "$priceLabel/$periodLabel"
}

object PremiumPricing {
    const val CURRENCY_SYMBOL = "\u20B9"

    fun rupees(amount: Int): String = "$CURRENCY_SYMBOL$amount"

    /** What 12 monthly payments cost (₹948 at ₹79). */
    val monthlyForOneYear: Int get() = PremiumPlan.MONTHLY.priceRupees * 12

    /** How much the yearly plan saves compared with 12 monthly payments (₹149). */
    val yearlySavings: Int get() = (monthlyForOneYear - PremiumPlan.YEARLY.priceRupees).coerceAtLeast(0)

    /** Rounded-down saving as a percentage of 12 monthly payments (15%). */
    val yearlySavingsPercent: Int get() = if (monthlyForOneYear == 0) 0 else yearlySavings * 100 / monthlyForOneYear

    /** Default selection on the purchase screen. */
    val DEFAULT_PLAN = PremiumPlan.YEARLY
}

/** One cell of the Free vs Premium table. */
enum class Availability(val label: String) { YES("Yes"), LOCKED("Locked"), LIMITED("Limited"), FULL("Full") }

data class ComparisonRow(val feature: String, val free: Availability, val premium: Availability)

/** Free vs Premium comparison shown on every Premium offer. */
object PremiumComparison {
    val rows: List<ComparisonRow> = listOf(
        ComparisonRow("Python lessons", Availability.YES, Availability.YES),
        ComparisonRow("Existing basic practical coding", Availability.YES, Availability.YES),
        ComparisonRow("Basic quizzes", Availability.YES, Availability.YES),
        ComparisonRow("Learning Analytics", Availability.YES, Availability.YES),
        ComparisonRow("Offline Learning Library", Availability.YES, Availability.YES),
        ComparisonRow("Advanced Python IDE upgrades", Availability.LOCKED, Availability.YES),
        ComparisonRow("Premium Image Center upgrades", Availability.LOCKED, Availability.YES),
        ComparisonRow("Advanced Project Library", Availability.LIMITED, Availability.FULL),
        ComparisonRow("Advanced Quiz Arena", Availability.LIMITED, Availability.FULL),
        ComparisonRow("Premium Code Battle", Availability.LOCKED, Availability.YES),
        ComparisonRow("Premium badges and rewards", Availability.LIMITED, Availability.YES),
    )
}

/**
 * Premium offer rules. Every decision uses the existing [Entitlements] (Phase 1 PremiumAccess) as
 * the single source of truth; nothing here grants or stores access.
 */
object PremiumOffers {
    /** Rule A/D: a Free user tapping a locked feature sees the offer. Rule B/C: Premium (incl. test mode) never does. */
    fun shouldOfferFor(entitlements: Entitlements, feature: PremiumFeature): Boolean = !entitlements.has(feature)

    /**
     * Introductory offer after the first lesson: only for a learner who came through the new
     * onboarding ([firstLessonPending]), has no Premium access, and hasn't seen it yet.
     */
    fun shouldShowIntroOffer(entitlements: Entitlements, firstLessonPending: Boolean, introOfferShown: Boolean): Boolean =
        firstLessonPending && !introOfferShown && !entitlements.isPremium
}
