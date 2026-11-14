package com.pypath.app.ui.screens.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pypath.app.cloud.AccountDeletionApi
import com.pypath.app.privacy.PrivacyPolicy
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.theme.AppTheme

/** Which confirmation dialog is open. */
private enum class PrivacyDialog { NONE, POLICY, COPY, WITHDRAW, DELETE }

/**
 * "Privacy & account" (from the account dialog on Home and Profile).
 *
 * - Read the Privacy Policy.
 * - Add guest progress kept on this phone to the signed-in account (explicit, explained action).
 * - Withdraw consent: recorded on the server; syncing stops and the learner is signed out.
 * - Delete my account: server-side deletion, confirmed by typing DELETE.
 *
 * Every action reports success only when it really succeeded; failures are shown as such.
 */
@Composable
fun PrivacyAccountScreen(
    /** Signed-in account e-mail; null = not signed in (guest). */
    email: String?,
    /** The signed-in learner agreed to the current Privacy Policy. */
    consentGiven: Boolean,
    guestProgressAvailable: Boolean,
    onBack: () -> Unit,
    onCopyGuestProgress: ((String?) -> Unit) -> Unit,
    onWithdrawConsent: ((String?) -> Unit) -> Unit,
    onDeleteAccount: ((String?) -> Unit) -> Unit,
    /** After withdrawal or deletion: back to the (guest) dashboard. */
    onDone: () -> Unit,
    /** Policy text; null = read from the app assets (tests pass it directly). */
    policyText: String? = null,
) {
    val c = AppTheme.colors
    var dialog by rememberSaveable { mutableStateOf(PrivacyDialog.NONE) }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var info by rememberSaveable { mutableStateOf<String?>(null) }
    // Withdrawal or deletion finished: the learner is now a guest; only this message is shown.
    var finished by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = if (finished != null) onDone else onBack, enabled = !busy) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.text)
            }
            Text(
                "Privacy & account", style = MaterialTheme.typography.titleLarge, color = c.text,
                modifier = Modifier.semantics { heading() },
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val done = finished
            if (done != null) {
                Card {
                    Text(done, style = MaterialTheme.typography.bodyLarge, color = c.text)
                    Spacer(Modifier.height(12.dp))
                    AppButton("Continue as guest", onClick = onDone, modifier = Modifier.fillMaxWidth())
                }
                return@Column
            }

            Card {
                Title("Your account")
                Body(if (email != null) "Signed in as $email." else "You are not signed in. Guest progress stays only on this phone.")
                Body(
                    if (email == null) "No Privacy Policy agreement is needed in guest mode."
                    else if (consentGiven) "You agreed to the Privacy Policy (version ${PrivacyPolicy.VERSION}). Your progress is backed up to your account."
                    else "You have not agreed to the current Privacy Policy, so nothing is backed up.",
                )
                TextButton(onClick = { dialog = PrivacyDialog.POLICY }) { Text("Read the Privacy Policy") }
            }

            if (email != null && guestProgressAvailable) {
                Card {
                    Title("Guest progress on this phone")
                    Body(
                        "This phone has progress from guest mode. It is kept separately and is not part of your account. " +
                            "You can add it to this account if it is yours.",
                    )
                    AppButton(
                        "Add guest progress to this account", onClick = { dialog = PrivacyDialog.COPY },
                        kind = ButtonKind.Secondary, enabled = !busy && consentGiven, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (email != null) {
                Card {
                    Title("Withdraw consent")
                    Body(
                        "Withdrawing your consent stops cloud backup straight away and signs you out on this phone. " +
                            "Accounts need your consent, so afterwards you can use PyPath only as a guest (first lesson). " +
                            "Your account and cloud backup stay stored until you delete your account, so you can agree again later. " +
                            "To have your data erased, delete your account instead.",
                    )
                    AppButton(
                        "Withdraw consent", onClick = { dialog = PrivacyDialog.WITHDRAW },
                        kind = ButtonKind.Secondary, enabled = !busy && consentGiven, modifier = Modifier.fillMaxWidth(),
                    )
                }
                Card {
                    Title("Delete my account")
                    Body(
                        "Deletes your PyPath account, your cloud backup (progress, quiz results, XP, streaks, programs and synced settings) " +
                            "and your Privacy Policy records from our server. This cannot be undone. " +
                            "This account's data is also removed from this phone; guest progress on this phone is not affected.",
                    )
                    AppButton(
                        "Delete my account", onClick = { dialog = PrivacyDialog.DELETE },
                        kind = ButtonKind.Secondary, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            info?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.success) }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.error) }
        }
    }

    when (dialog) {
        PrivacyDialog.NONE -> Unit
        PrivacyDialog.POLICY -> PolicyDialog(policyText, onDismiss = { dialog = PrivacyDialog.NONE })
        PrivacyDialog.COPY -> ConfirmDialog(
            title = "Add guest progress?",
            text = "Lessons, quiz results, XP history and saved programs from guest mode on this phone will be added to ${email ?: "this account"}. " +
                "Nothing in your account is removed and your account's settings stay as they are. Only do this if the guest progress is yours.",
            confirm = "Add to my account", busy = busy,
            onDismiss = { dialog = PrivacyDialog.NONE },
            onConfirm = {
                busy = true; error = null; info = null
                onCopyGuestProgress { err ->
                    busy = false; dialog = PrivacyDialog.NONE
                    if (err == null) info = "Guest progress was added to your account." else error = err
                }
            },
        )
        PrivacyDialog.WITHDRAW -> ConfirmDialog(
            title = "Withdraw consent?",
            text = "Cloud backup stops and you will be signed out on this phone. Your account and cloud backup stay stored until you delete your account. " +
                "If you log in again, you will be asked to agree to the Privacy Policy again.",
            confirm = "Withdraw consent", busy = busy, destructive = true,
            onDismiss = { dialog = PrivacyDialog.NONE },
            onConfirm = {
                busy = true; error = null; info = null
                onWithdrawConsent { err ->
                    busy = false; dialog = PrivacyDialog.NONE
                    if (err == null) finished = "Your consent was withdrawn. Cloud backup has stopped and you are signed out. Your account still exists; you can delete it by logging in again and choosing Delete my account."
                    else error = err
                }
            },
        )
        PrivacyDialog.DELETE -> DeleteDialog(
            busy = busy,
            onDismiss = { dialog = PrivacyDialog.NONE },
            onConfirm = {
                busy = true; error = null; info = null
                onDeleteAccount { err ->
                    busy = false; dialog = PrivacyDialog.NONE
                    if (err == null) finished = "Your PyPath account and its cloud data were deleted. You are now using PyPath as a guest."
                    else error = err
                }
            },
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun Title(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.text, modifier = Modifier.semantics { heading() })

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted)

@Composable
private fun ConfirmDialog(
    title: String, text: String, confirm: String, busy: Boolean,
    onDismiss: () -> Unit, onConfirm: () -> Unit, destructive: Boolean = false,
) {
    val c = AppTheme.colors
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = { Text(text, color = c.textMuted) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(if (busy) "Please wait..." else confirm, color = if (destructive) c.error else c.brand)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
        containerColor = c.surface,
    )
}

/** Account deletion confirmation (type DELETE); also used on the Privacy Policy consent screen. */
@Composable
internal fun DeleteDialog(busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val c = AppTheme.colors
    var typed by rememberSaveable { mutableStateOf("") }
    val ok = typed.trim() == AccountDeletionApi.CONFIRM_WORD
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Delete your account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This permanently deletes your PyPath account and its cloud data. It cannot be undone. " +
                        "You need an internet connection. Type DELETE to confirm.",
                    color = c.textMuted,
                )
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it }, singleLine = true, enabled = !busy,
                    label = { Text("Type DELETE") }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = ok && !busy) {
                Text(if (busy) "Deleting..." else "Delete account", color = if (ok) c.error else c.textFaint)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
        containerColor = c.surface,
    )
}

@Composable
private fun PolicyDialog(policyText: String?, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    val context = LocalContext.current
    val blocks = remember(context, policyText) { PrivacyPolicy.parse(policyText ?: PrivacyPolicy.load(context)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy Policy") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                blocks.forEach { b ->
                    when (b) {
                        is PrivacyPolicy.Block.Title -> Text(b.text, style = MaterialTheme.typography.titleMedium, color = c.text)
                        is PrivacyPolicy.Block.Heading -> Text(b.text, style = MaterialTheme.typography.titleSmall, color = c.text)
                        is PrivacyPolicy.Block.SubHeading -> Text(b.text, style = MaterialTheme.typography.labelLarge, color = c.text)
                        is PrivacyPolicy.Block.Paragraph -> Text(b.text, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                        is PrivacyPolicy.Block.Bullet -> Text("• " + b.text, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        containerColor = c.surface,
    )
}
