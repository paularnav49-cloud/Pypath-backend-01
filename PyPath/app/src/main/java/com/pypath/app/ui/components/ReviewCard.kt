package com.pypath.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pypath.app.domain.ReviewInfo
import com.pypath.app.domain.Streaks
import com.pypath.app.domain.XpRules
import com.pypath.app.ui.theme.AppTheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val reviewDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** Batch 3 dashboard card: questions due for spaced review, or "All caught up". */
@Composable
fun ReviewCard(info: ReviewInfo, onReview: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    AppCard(modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text("Review", style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        when {
            info.resumeAt != null -> {
                Text(
                    "Review in progress: question ${info.resumeAt} of ${info.resumeTotal}.",
                    style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                )
                Spacer(Modifier.height(12.dp))
                AppButton("Resume review", onReview, Modifier.fillMaxWidth(), trailing = Icons.AutoMirrored.Filled.ArrowForward)
            }
            info.dueCount > 0 -> {
                val n = info.dueCount
                val session = minOf(n, XpRules.REVIEW_SESSION_SIZE)
                Text(
                    (if (n == 1) "1 question is due today." else "$n questions are due today.") +
                        " A session has up to ${XpRules.REVIEW_SESSION_SIZE} questions, no pass mark.",
                    style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                )
                Spacer(Modifier.height(12.dp))
                AppButton(
                    if (session == 1) "Review 1 question" else "Review $session questions", onReview,
                    Modifier.fillMaxWidth(), leading = Icons.Filled.Refresh,
                )
            }
            else -> {
                Text("All caught up", style = MaterialTheme.typography.titleSmall, color = c.success)
                Spacer(Modifier.height(2.dp))
                val next = info.nextDueDay
                Text(
                    if (next != null) "Next review: ${Streaks.date(next).format(reviewDate)}."
                    else "Questions you answer wrong come back here for review.",
                    style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                )
            }
        }
    }
}
