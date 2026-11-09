package com.pypath.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pypath.app.premium.Entitlements
import com.pypath.app.premium.PremiumFeature
import com.pypath.app.ui.theme.AppTheme

/**
 * Premium Phase 1: what Premium adds, plus the local developer/testing switch. The dialog says
 * plainly that this switch is not a purchase; real payments and server checks come later.
 */
@Composable
fun PremiumDialog(entitlements: Entitlements, onDevPremiumChange: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PyPath Premium") },
        text = {
            // Premium Phase 2: four features now, so the dialog body scrolls on small phones.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Everything you use today stays free. Premium adds:",
                    style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                )
                Spacer(Modifier.height(12.dp))
                PremiumFeature.entries.forEach { f ->
                    Row(Modifier.padding(vertical = 5.dp)) {
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(if (entitlements.has(f)) c.success else c.surfaceAlt),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Check, null, tint = if (entitlements.has(f)) c.onBrand else c.textFaint, modifier = Modifier.size(14.dp)) }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(f.title, style = MaterialTheme.typography.titleSmall, color = c.text)
                            Text(f.description, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Find them in Practical \u2192 My programs, Certificate \u2192 Image Center, Projects \u2192 Premium Project Library " +
                        "and Home \u2192 Quiz Arena / Code Battle.",
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceAlt).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Premium test mode", style = MaterialTheme.typography.titleSmall, color = c.text)
                            Text("For development and testing", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                        }
                        Switch(
                            checked = entitlements.isPremium, onCheckedChange = onDevPremiumChange,
                            modifier = Modifier.semantics { contentDescription = "Premium test mode" },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Icon(Icons.Filled.Info, null, tint = c.textFaint, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "This local switch is not a purchase and is not secure payment verification. " +
                                "Real payments will be added later.",
                            style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        containerColor = c.surface,
    )
}
