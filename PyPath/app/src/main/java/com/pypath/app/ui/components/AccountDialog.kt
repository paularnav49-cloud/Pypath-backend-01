package com.pypath.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pypath.app.data.auth.AccountState
import com.pypath.app.data.auth.GoogleAccount
import com.pypath.app.ui.screens.signin.AccountAvatar
import com.pypath.app.ui.screens.signin.GoogleSignInButton
import com.pypath.app.ui.theme.AppTheme

/** Google account: shows who is signed in with a Sign out button, or the Sign in with Google button. */
@Composable
fun AccountDialog(
    account: AccountState,
    onSignedIn: (GoogleAccount) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = AppTheme.colors
    val user = account.user
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (user != null) "Google account" else "Sign in") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (user != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AccountAvatar(user)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            if (user.displayName.isNotBlank()) {
                                Text(user.displayName, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(user.email, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Signing out keeps all your progress on this phone.",
                        style = MaterialTheme.typography.bodySmall, color = c.textFaint,
                    )
                } else {
                    Text(
                        "Sign in with Google to see your name in PyPath and on your certificate. Your progress stays on this phone.",
                        style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                    )
                    Spacer(Modifier.height(16.dp))
                    GoogleSignInButton(onSignedIn = { onSignedIn(it); onDismiss() }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (user != null) {
                TextButton(onClick = { onSignOut(); onDismiss() }) { Text("Sign out", color = c.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        containerColor = c.surface,
    )
}
