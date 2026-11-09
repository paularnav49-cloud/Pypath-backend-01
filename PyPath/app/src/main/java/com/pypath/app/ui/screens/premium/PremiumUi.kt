package com.pypath.app.ui.screens.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.Pill
import com.pypath.app.ui.theme.AppTheme

/**
 * Premium Phase 2: small shared pieces, styled like the Phase 1 Premium UI (segmented switch from
 * the Advanced IDE, PRO pill and entry card from the Image Center).
 */
@Composable
internal fun PremiumTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceAlt).padding(4.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (on) c.surface else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 9.dp, horizontal = 4.dp)
                    .semantics { role = Role.Tab; contentDescription = "$label${if (on) ", selected" else ""}" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelLarge, color = if (on) c.text else c.textMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Shown instead of a Premium screen when the feature isn't unlocked (Free users never reach it from the UI). */
@Composable
internal fun PremiumLockedNotice(title: String, text: String, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Column(modifier.padding(20.dp)) {
        AppCard(Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Lock, null, tint = c.textFaint)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
    }
}

/** Entry card for a Premium area (same look as the Image Center card on the certificate screen). */
@Composable
fun PremiumEntryCard(icon: ImageVector, title: String, subtitle: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    AppCard(modifier.fillMaxWidth(), onClick = onOpen, background = c.brandSoft, borderColor = c.brand.copy(alpha = 0.35f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.brand), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = c.onBrand)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
            Spacer(Modifier.width(8.dp))
            Pill("PRO", c.onBrand, c.brand)
        }
    }
}

@Composable
internal fun difficultyColors(difficulty: String?): Pair<Color, Color> {
    val c = AppTheme.colors
    return when (difficulty?.lowercase()) {
        "hard" -> c.error to c.errorSoft
        "medium" -> c.accent to c.accentSoft
        else -> c.success to c.successSoft
    }
}
