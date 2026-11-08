package com.pypath.app.ui.components

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pypath.app.data.settings.ReminderPrefs
import com.pypath.app.domain.Reminders
import com.pypath.app.reminders.ReminderNotifier
import com.pypath.app.ui.theme.AppTheme

/**
 * Batch 3 Part 3: "Daily reminder" settings. Off by default. Turning it on explains why first, then
 * asks for the notification permission (Android 13+ only, never at first launch). If the learner
 * says no, the switch stays off and a button opens the app's notification settings.
 */
@Composable
fun ReminderDialog(
    prefs: ReminderPrefs,
    onEnabledChange: (Boolean) -> Unit,
    onMinutesChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = AppTheme.colors
    val context = LocalContext.current
    var explain by rememberSaveable { mutableStateOf(false) }
    var blocked by rememberSaveable { mutableStateOf(false) }
    // Re-read when the dialog is shown again, e.g. after coming back from the system settings.
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    val allowed = remember(refresh) { ReminderNotifier.allowed(context) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        if (granted && ReminderNotifier.allowed(context)) {
            blocked = false
            onEnabledChange(true)
        } else {
            blocked = true
            onEnabledChange(false)
        }
    }

    fun turnOn() {
        when {
            ReminderNotifier.allowed(context) -> { blocked = false; onEnabledChange(true) }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                explain = true
            else -> { blocked = true; onEnabledChange(false) } // blocked in the phone's settings
        }
    }

    val showBlocked = blocked || (prefs.enabled && !allowed)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily reminder", modifier = Modifier.semantics { heading() }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .toggleable(
                            value = prefs.enabled, role = Role.Switch,
                            onValueChange = { on -> if (on) turnOn() else { blocked = false; onEnabledChange(false) } },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Remind me to study", style = MaterialTheme.typography.titleSmall, color = c.text)
                        Text(if (prefs.enabled) "On" else "Off", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = prefs.enabled, onCheckedChange = null)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Time", style = MaterialTheme.typography.titleSmall, color = c.text)
                        Text(Reminders.label(prefs.minutes), style = MaterialTheme.typography.bodyLarge, color = c.text)
                    }
                    TextButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> onMinutesChange(hour * 60 + minute) },
                                prefs.minutes / 60, prefs.minutes % 60, DateFormat.is24HourFormat(context),
                            ).show()
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Change time") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "At most one reminder a day, and none once today's goal is met. Android may deliver it " +
                        "a few minutes late to save battery.",
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
                if (showBlocked) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Notifications are turned off for PyPath, so the reminder can't be shown. You can allow them in your phone's settings.",
                        style = MaterialTheme.typography.bodySmall, color = c.error,
                    )
                    TextButton(
                        onClick = {
                            refresh++
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier.heightIn(min = 48.dp).padding(top = 4.dp),
                    ) { Text("Open notification settings") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        containerColor = c.surface,
    )

    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            title = { Text("Allow notifications?") },
            text = {
                Text(
                    "PyPath needs permission to show one study reminder a day at the time you choose. " +
                        "Nothing is sent anywhere: the reminder is made on your phone. On the next screen Android asks you to allow it.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    explain = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { explain = false }) { Text("Not now") } },
            containerColor = c.surface,
        )
    }
}
