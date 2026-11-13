package com.pypath.app.ui.screens.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pypath.app.privacy.PrivacyPolicy
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.theme.AppTheme

/**
 * Mandatory Privacy Policy screen after "Sign in with Google" / "Login with Google", before the
 * dashboard. PyPath is designed for learners aged 12 and up. Scrolling the text does not count as
 * agreement: the learner must tick the policy checkbox and the age confirmation (18 or older, or
 * 12 to 17 with a parent's or guardian's agreement), and "Agree and Continue" is enabled only then.
 * The age confirmation is self-declared: PyPath does not verify age or parental consent, and the
 * screen does not claim it does. "I'm under 12" offers deleting the account straight away (or
 * signing out), and "Delete my account" (type DELETE) is available to everyone, for example after
 * withdrawing consent; guest mode stays available. The agreement is saved on the PyPath server first;
 * if the app is closed before that, this screen comes back on the next start.
 */
@Composable
fun PrivacyConsentScreen(
    /** Saves the agreement; calls back with null on success or an error message. */
    onAgree: ((String?) -> Unit) -> Unit,
    onSignOut: () -> Unit,
    /** Policy text; null = read from the app assets (tests pass it directly). */
    policyText: String? = null,
    /** Deletes the account on the server; calls back with null on success or an error message. */
    onDeleteAccount: (((String?) -> Unit) -> Unit)? = null,
) {
    val c = AppTheme.colors
    val context = LocalContext.current
    val blocks = remember(context, policyText) { PrivacyPolicy.parse(policyText ?: PrivacyPolicy.load(context)) }
    var checked by rememberSaveable { mutableStateOf(false) }
    var ageConfirmed by rememberSaveable { mutableStateOf(false) }
    var underTwelveDialog by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Privacy Policy", style = MaterialTheme.typography.headlineSmall, color = c.text,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Please read how PyPath uses your information. You need to agree before you continue.",
            style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        Spacer(Modifier.height(12.dp))
        val shape = RoundedCornerShape(16.dp)
        Column(
            Modifier.weight(1f).widthIn(max = 560.dp).fillMaxWidth().clip(shape).background(c.surface)
                .border(1.dp, c.border, shape).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (blocks.isEmpty()) {
                Text("The Privacy Policy could not be loaded. Please reinstall or update PyPath.", color = c.error)
            }
            blocks.forEach { b ->
                when (b) {
                    is PrivacyPolicy.Block.Title -> Text(b.text, style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.semantics { heading() })
                    is PrivacyPolicy.Block.Heading -> Text(b.text, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                    is PrivacyPolicy.Block.SubHeading -> Text(b.text, style = MaterialTheme.typography.titleSmall, color = c.text)
                    is PrivacyPolicy.Block.Paragraph -> Text(b.text, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    is PrivacyPolicy.Block.Bullet -> Row {
                        Text("•", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                        Spacer(Modifier.width(8.dp))
                        Text(b.text, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                    .toggleable(value = checked, enabled = !busy && blocks.isNotEmpty(), role = Role.Checkbox) { checked = it; error = null },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked, onCheckedChange = null, enabled = !busy && blocks.isNotEmpty(),
                    colors = CheckboxDefaults.colors(checkedColor = c.brand),
                )
                Spacer(Modifier.width(8.dp))
                Text("I have read and agree to the Privacy Policy.", style = MaterialTheme.typography.bodyLarge, color = c.text)
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                    .toggleable(value = ageConfirmed, enabled = !busy && blocks.isNotEmpty(), role = Role.Checkbox) { ageConfirmed = it; error = null },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = ageConfirmed, onCheckedChange = null, enabled = !busy && blocks.isNotEmpty(),
                    colors = CheckboxDefaults.colors(checkedColor = c.brand),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "I am 18 or older, or I am 12 to 17 and my parent or guardian has read this Privacy Policy and agrees to me using a PyPath account.",
                    style = MaterialTheme.typography.bodyLarge, color = c.text,
                )
            }
            Text(
                "Under 18? Please read this policy with a parent or guardian first. PyPath does not check your age or verify a parent's consent.",
                style = MaterialTheme.typography.bodySmall, color = c.textMuted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            AppButton(
                if (busy) "Saving..." else "Agree and Continue",
                onClick = {
                    if (!checked || !ageConfirmed || busy) return@AppButton
                    busy = true
                    error = null
                    onAgree { message -> busy = false; error = message }
                },
                enabled = checked && ageConfirmed && !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            val message = error
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, color = c.error, textAlign = TextAlign.Center)
            }
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { underTwelveDialog = true }, enabled = !busy) {
                    Text("I'm under 12", color = c.textMuted)
                }
                TextButton(onClick = onSignOut, enabled = !busy) {
                    Text("Sign out instead", color = c.textMuted)
                }
            }
            if (onDeleteAccount != null) {
                TextButton(onClick = { deleteDialog = true }, enabled = !busy) {
                    Text("Delete my account", color = c.textMuted)
                }
            }
        }
    }

    if (deleteDialog && onDeleteAccount != null) {
        DeleteDialog(
            busy = busy,
            onDismiss = { deleteDialog = false },
            onConfirm = {
                busy = true
                error = null
                onDeleteAccount { message ->
                    busy = false
                    error = message
                    deleteDialog = false
                }
            },
        )
    }

    if (underTwelveDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) underTwelveDialog = false },
            title = { Text("PyPath is for ages 12+") },
            text = {
                Text(
                    "PyPath is designed for learners aged 12 and up, so please don't create an account if you are under 12. " +
                        "You can keep learning as a guest: guest progress stays only on this phone and nothing is sent to our servers." +
                        (if (onDeleteAccount != null) "\n\nDelete this account now? The PyPath account and any PyPath data stored for it on our server will be deleted." else ""),
                    color = c.textMuted,
                )
            },
            confirmButton = {
                if (onDeleteAccount != null) {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            error = null
                            onDeleteAccount { message ->
                                busy = false
                                error = message
                                underTwelveDialog = false
                            }
                        },
                    ) { Text(if (busy) "Deleting..." else "Delete account", color = c.error) }
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { underTwelveDialog = false; onSignOut() }) { Text("Sign out") }
            },
            containerColor = c.surface,
        )
    }
}
