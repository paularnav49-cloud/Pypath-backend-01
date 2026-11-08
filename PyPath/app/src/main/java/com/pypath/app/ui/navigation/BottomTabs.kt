package com.pypath.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pypath.app.ui.theme.AppTheme

private data class Tab(val route: String, val label: String, val icon: ImageVector)

/** "</>" code glyph for the Practical tab (not in material-icons-core). */
private val CodeIcon: ImageVector = ImageVector.Builder("Code", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(8f, 7f); lineTo(3f, 12f); lineTo(8f, 17f)
        moveTo(16f, 7f); lineTo(21f, 12f); lineTo(16f, 17f)
        moveTo(13.5f, 5f); lineTo(10.5f, 19f)
    }
}.build()

private val tabs = listOf(
    Tab(Routes.DASHBOARD, "Learn", Icons.Filled.Home),
    Tab(Routes.PRACTICAL, "Practical", CodeIcon),
)

/** Main app navigation between the learning path and the Practical coding tab. */
@Composable
fun BottomTabs(current: String?, onSelect: (String) -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEach { tab ->
                val selected = tab.route == current
                val fg = if (selected) c.brand else c.textMuted
                Column(
                    Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            role = Role.Tab,
                        ) { onSelect(tab.route) }
                        .semantics { this.selected = selected; contentDescription = "${tab.label} tab" }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.clip(CircleShape).background(if (selected) c.brandSoft else Color.Transparent)
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) { Icon(tab.icon, null, tint = fg, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, style = MaterialTheme.typography.labelMedium, color = fg)
                }
                if (tab != tabs.last()) Spacer(Modifier.width(12.dp))
            }
        }
    }
}
