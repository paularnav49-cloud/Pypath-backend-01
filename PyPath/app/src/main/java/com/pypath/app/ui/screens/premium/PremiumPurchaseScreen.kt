package com.pypath.app.ui.screens.premium

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.pypath.app.premium.Availability
import com.pypath.app.premium.Entitlements
import com.pypath.app.premium.PremiumComparison
import com.pypath.app.premium.PremiumFeature
import com.pypath.app.premium.PremiumPlan
import com.pypath.app.premium.PremiumPricing
import com.pypath.app.premium.PremiumSource
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.components.Pill
import com.pypath.app.ui.theme.AppTheme

/** Why the Premium screen was opened. */
enum class PremiumEntry {
    /** Opened by hand (Profile, menu). */
    MANUAL,
    /** A Free user tapped a locked feature. */
    LOCKED_FEATURE,
    /** Introductory offer after the first lesson. */
    INTRO,
}

private val HeroStart = Color(0xFF3D5AFE)
private val HeroEnd = Color(0xFF2335C8)

/**
 * Final frontend polish: Premium plans, Free vs Premium comparison and an upgrade action.
 *
 * There is NO payment processing in this build. "Upgrade" explains that checkout is not available
 * yet; it never pretends a purchase succeeded and never changes access. Premium access still comes
 * only from the existing Premium test mode (Phase 1 PremiumAccess).
 */
@Composable
fun PremiumPurchaseScreen(
    entitlements: Entitlements,
    onClose: () -> Unit,
    entry: PremiumEntry = PremiumEntry.MANUAL,
    /** The locked feature that was tapped (for [PremiumEntry.LOCKED_FEATURE]). */
    feature: PremiumFeature? = null,
) {
    val c = AppTheme.colors
    var selected by rememberSaveable { mutableStateOf(PremiumPricing.DEFAULT_PLAN) }
    var checkoutInfo by rememberSaveable { mutableStateOf(false) }
    val active = entitlements.isPremium

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("PRO", c.onBrand, c.brand)
            Spacer(Modifier.width(8.dp))
            Text("PyPath Premium", style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = c.textMuted) }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                Spacer(Modifier.height(4.dp))
                Hero(entry, feature, entitlements)
                Spacer(Modifier.height(16.dp))
                Text("Choose your plan", style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PremiumPlan.entries.forEach { plan ->
                        PlanCard(plan, selected == plan, { selected = plan }, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Paying monthly for 12 months costs ${PremiumPricing.rupees(PremiumPricing.monthlyForOneYear)}. " +
                        "The yearly plan saves you ${PremiumPricing.rupees(PremiumPricing.yearlySavings)}.",
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
                Spacer(Modifier.height(18.dp))
                Text("Free vs Premium", style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(10.dp))
                ComparisonTable()
                Spacer(Modifier.height(12.dp))
                Text(
                    "Everything that is free today stays free. Premium only adds extras on top.",
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                if (active) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.successSoft).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = c.success)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (entitlements.source == PremiumSource.LOCAL_DEV) "Premium is active (test mode). Every Premium feature is unlocked."
                            else "Premium is active. Every Premium feature is unlocked.",
                            style = MaterialTheme.typography.bodyMedium, color = c.text,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    AppButton("Done", onClose, Modifier.fillMaxWidth())
                } else {
                    AppButton(
                        "Upgrade \u2013 ${selected.fullLabel}", { checkoutInfo = true },
                        Modifier.fillMaxWidth().semantics { contentDescription = "Upgrade to Premium, ${selected.label} plan, ${selected.fullLabel}" },
                        leading = Icons.Filled.Star,
                    )
                    Spacer(Modifier.height(4.dp))
                    AppButton(if (entry == PremiumEntry.INTRO) "Maybe later" else "Not now", onClose, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost)
                }
            }
        }
    }

    if (checkoutInfo) {
        AlertDialog(
            onDismissRequest = { checkoutInfo = false },
            icon = { Icon(Icons.Filled.Info, null, tint = c.brand) },
            title = { Text("Checkout is coming soon") },
            text = {
                Text(
                    "Secure payments aren't available in this version of PyPath yet, so nothing was purchased and you " +
                        "haven't been charged. You chose the ${selected.label} plan (${selected.fullLabel}). " +
                        "Keep learning for free in the meantime.",
                )
            },
            confirmButton = { TextButton(onClick = { checkoutInfo = false }) { Text("OK") } },
            containerColor = c.surface,
        )
    }
}

@Composable
private fun Hero(entry: PremiumEntry, feature: PremiumFeature?, entitlements: Entitlements) {
    val title: String
    val body: String
    when {
        entry == PremiumEntry.LOCKED_FEATURE && feature != null && !entitlements.has(feature) -> {
            title = "${feature.title} is a Premium feature"
            body = "It's locked on the Free plan. ${feature.description}. Upgrade to unlock it and every other Premium extra."
        }
        entry == PremiumEntry.INTRO -> {
            title = "Great job on your first lesson!"
            body = "You're on your way. Premium adds advanced tools, projects and challenges to help you learn even faster."
        }
        else -> {
            title = "Go further with Premium"
            body = "Advanced tools, real-world projects, harder quizzes and Code Battle, on top of the free course."
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(HeroStart, HeroEnd))).padding(20.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(if (entry == PremiumEntry.LOCKED_FEATURE) Icons.Filled.Lock else Icons.Filled.Star, null, tint = Color.White)
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
        Spacer(Modifier.height(14.dp))
        PremiumFeature.entries.forEach { f ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(f.title, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Premium badges and rewards", style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PlanCard(plan: PremiumPlan, selected: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.heightIn(min = 150.dp).clip(shape)
            .background(if (selected) c.brandSoft else c.surface)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) c.brand else c.border), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(plan.label, style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(20.dp).clip(CircleShape).background(if (selected) c.brand else Color.Transparent)
                    .border(2.dp, if (selected) c.brand else c.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (selected) Icon(Icons.Filled.Check, null, tint = c.onBrand, modifier = Modifier.size(13.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(plan.priceLabel, style = MaterialTheme.typography.headlineSmall, color = c.text, fontWeight = FontWeight.ExtraBold)
            Text("/${plan.periodLabel}", style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
        }
        Spacer(Modifier.height(6.dp))
        if (plan == PremiumPlan.YEARLY) {
            Text(
                PremiumPricing.rupees(PremiumPricing.monthlyForOneYear), style = MaterialTheme.typography.bodySmall,
                color = c.textFaint, textDecoration = TextDecoration.LineThrough,
            )
            Spacer(Modifier.height(4.dp))
            Pill("Save ${PremiumPricing.rupees(PremiumPricing.yearlySavings)}", c.onBrand, c.success)
        } else {
            Text("Billed every month", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            Spacer(Modifier.height(4.dp))
            Text("Cancel any time", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
    }
}

@Composable
fun ComparisonTable(modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    AppCard(modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().background(c.surfaceAlt).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("Feature", style = MaterialTheme.typography.labelLarge, color = c.textMuted, modifier = Modifier.weight(1f))
            Text("Free", style = MaterialTheme.typography.labelLarge, color = c.textMuted, modifier = Modifier.width(68.dp), textAlign = TextAlign.Center)
            Text("Premium", style = MaterialTheme.typography.labelLarge, color = c.brand, modifier = Modifier.width(72.dp), textAlign = TextAlign.Center)
        }
        PremiumComparison.rows.forEachIndexed { i, row ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "${row.feature}: Free ${row.free.label}, Premium ${row.premium.label}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.feature, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
                Cell(row.free, Modifier.width(68.dp))
                Cell(row.premium, Modifier.width(72.dp))
            }
        }
    }
}

@Composable
private fun Cell(a: Availability, modifier: Modifier) {
    val c = AppTheme.colors
    Box(modifier, contentAlignment = Alignment.Center) {
        when (a) {
            Availability.YES, Availability.FULL -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, null, tint = c.success, modifier = Modifier.size(16.dp))
                if (a == Availability.FULL) Text(" Full", style = MaterialTheme.typography.labelMedium, color = c.success)
            }
            Availability.LOCKED -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = c.textFaint, modifier = Modifier.size(14.dp))
                Text(" Locked", style = MaterialTheme.typography.labelMedium, color = c.textFaint)
            }
            Availability.LIMITED -> Text("Limited", style = MaterialTheme.typography.labelMedium, color = c.accent)
        }
    }
}

/** Small "Premium" marker for locked entry points: a lock when locked, the PRO pill when unlocked. */
@Composable
fun PremiumBadge(locked: Boolean, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    if (locked) Pill("Premium", c.textMuted, c.surfaceAlt, modifier, icon = Icons.Filled.Lock)
    else Pill("PRO", c.onBrand, c.brand, modifier)
}
