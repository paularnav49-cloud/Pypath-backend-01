package com.pypath.app.ui.screens.certificate

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.Certificates
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.AppTopBar
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.components.Eyebrow
import com.pypath.app.ui.components.ProgressBar
import com.pypath.app.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Course certificate: unlocked after Level 7, drawn and shared on the phone with no internet. */
@Composable
fun CertificateScreen(
    snapshot: CourseSnapshot,
    savedName: String,
    onNameChange: (String) -> Unit,
    onBack: () -> Unit,
    /** Premium Phase 1: opens the Image Center. null (Free, default) = the screen is unchanged. */
    onOpenImageCenter: (() -> Unit)? = null,
    /** Final frontend polish: shows the Image Center card locked (tap opens the Premium offer). */
    imageCenterLocked: Boolean = false,
) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize().background(c.background).imePadding()) {
        AppTopBar("Certificate", snapshot.course.title, onBack)
        if (!snapshot.certificateUnlocked) {
            Locked(snapshot)
            if (onOpenImageCenter != null) ImageCenterEntryCard(onOpenImageCenter, Modifier.padding(horizontal = 20.dp), locked = imageCenterLocked)
            return
        }
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var name by rememberSaveable { mutableStateOf(savedName) }
        // The saved name may arrive after the screen opens.
        LaunchedEffect(savedName) { if (name.isEmpty() && savedName.isNotEmpty()) name = savedName }
        LaunchedEffect(name) {
            delay(400)
            if (Certificates.cleanName(name) != savedName) onNameChange(Certificates.cleanName(name))
        }
        val info = remember(snapshot, name) { Certificates.info(snapshot, name) } ?: return
        // Re-drawn shortly after typing stops, off the main thread.
        val bitmap by produceState<Bitmap?>(null, info) {
            delay(250)
            value = withContext(Dispatchers.Default) { CertificateRenderer.render(context, info) }
        }
        var busy by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }
        val ready = info.name.isNotBlank() && bitmap != null && !busy

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Eyebrow("Congratulations", c.success)
            Spacer(Modifier.height(6.dp))
            Text("You finished the whole course", style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(6.dp))
            Text(
                "Type your name as it should appear. The certificate is made on your phone; nothing is uploaded.",
                style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(Certificates.MAX_NAME); message = null },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Name on certificate") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                supportingText = { Text("${name.length}/${Certificates.MAX_NAME}") },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.brand, unfocusedBorderColor = c.border,
                    focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
                    focusedTextColor = c.text, unfocusedTextColor = c.text, cursorColor = c.brand,
                    focusedLabelColor = c.brand, unfocusedLabelColor = c.textMuted,
                ),
            )
            Spacer(Modifier.height(12.dp))
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier.fillMaxWidth().aspectRatio(CertificateRenderer.WIDTH / CertificateRenderer.HEIGHT.toFloat())
                    .clip(shape).border(1.dp, c.border, shape).background(c.surfaceAlt),
                contentAlignment = Alignment.Center,
            ) {
                val b = bitmap
                if (b == null) CircularProgressIndicator(color = c.brand)
                else Image(b.asImageBitmap(), "Certificate preview for ${info.name.ifBlank { "you" }}", Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(8.dp))
            Text("Certificate ID ${info.id}", style = MaterialTheme.typography.bodySmall, color = c.textFaint)
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = c.success)
            }
            if (onOpenImageCenter != null) {
                Spacer(Modifier.height(16.dp))
                ImageCenterEntryCard(onOpenImageCenter, locked = imageCenterLocked)
            }
            Spacer(Modifier.height(20.dp))
        }
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            if (info.name.isBlank()) {
                Text("Type your name to share or save it.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                Spacer(Modifier.height(8.dp))
            }
            AppButton(
                "Share certificate",
                {
                    val b = bitmap ?: return@AppButton
                    busy = true
                    scope.launch {
                        val file = runCatching { withContext(Dispatchers.IO) { CertificateExport.writeToCache(context, b, info.id) } }.getOrNull()
                        busy = false
                        if (file == null) message = "Couldn't create the image. Please try again."
                        else runCatching { CertificateExport.share(context, file, info.courseTitle) }
                            .onFailure { message = "No app on this phone can share images." }
                    }
                },
                Modifier.fillMaxWidth(), enabled = ready,
                leading = Icons.Filled.Share,
            )
            if (CertificateExport.canSaveToGallery) {
                Spacer(Modifier.height(10.dp))
                AppButton(
                    "Save to gallery",
                    {
                        val b = bitmap ?: return@AppButton
                        busy = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                runCatching { CertificateExport.saveToGallery(context, b, info.id) }.getOrDefault(false)
                            }
                            busy = false
                            message = if (ok) "Saved to Pictures/PyPath" else "Couldn't save the image. Please try again."
                        }
                    },
                    Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, enabled = ready,
                )
            }
        }
    }
}

@Composable
private fun Locked(snapshot: CourseSnapshot) {
    val c = AppTheme.colors
    val subs = snapshot.orderedSubLevels
    val done = subs.count { it.status == com.pypath.app.domain.NodeStatus.COMPLETED }
    Column(Modifier.padding(20.dp)) {
        AppCard(Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Lock, null, tint = c.textFaint)
            Spacer(Modifier.height(8.dp))
            Text("Your certificate is locked", style = MaterialTheme.typography.titleMedium, color = c.text)
            Spacer(Modifier.height(4.dp))
            Text(
                "Finish every level, including the Level 7 final project, to unlock a certificate you can share. " +
                    "It's created on your phone, so no internet is needed.",
                style = MaterialTheme.typography.bodySmall, color = c.textMuted,
            )
            Spacer(Modifier.height(14.dp))
            ProgressBar(if (subs.isEmpty()) 0f else done.toFloat() / subs.size, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text("$done of ${subs.size} sub-levels done", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
    }
}
