package com.pypath.app.ui.screens.certificate

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pypath.app.premium.Entitlements
import com.pypath.app.premium.PremiumFeature
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.AppTopBar
import com.pypath.app.ui.components.Eyebrow
import com.pypath.app.ui.components.Pill
import com.pypath.app.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Result of the last download of one image. */
sealed interface DownloadState {
    data object Idle : DownloadState
    data object Saving : DownloadState
    data object Saved : DownloadState
    data class Failed(val reason: String) : DownloadState
}

/**
 * Premium Phase 1: the Image Center. Exactly two downloadable PyPath images ([PremiumImage]), each
 * with a preview and a Download button that saves into Pictures/PyPath through the same MediaStore
 * pipeline as the certificate ([CertificateExport]). Free users see a short locked notice instead.
 */
@Composable
fun ImageCenterScreen(premium: Entitlements, onBack: () -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        AppTopBar("Image Center", "PyPath Premium", onBack)
        if (!premium.has(PremiumFeature.IMAGE_CENTER)) {
            Column(Modifier.padding(20.dp)) {
                AppCard(Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Lock, null, tint = c.textFaint)
                    Spacer(Modifier.height(8.dp))
                    Text("Image Center is part of Premium", style = MaterialTheme.typography.titleMedium, color = c.text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Your certificate stays free. Premium adds two PyPath images you can download to your gallery.",
                        style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                    )
                }
            }
            return
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Eyebrow("Premium", c.brand)
            Spacer(Modifier.height(6.dp))
            Text("Download PyPath images", style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(6.dp))
            Text(
                "Both images are made on your phone and saved to Pictures/PyPath. Nothing is uploaded.",
                style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
            )
            Spacer(Modifier.height(16.dp))
            PremiumImage.entries.forEach { img ->
                ImageCard(img)
                Spacer(Modifier.height(16.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ImageCard(img: PremiumImage) {
    val c = AppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bitmap by produceState<Bitmap?>(null, img) {
        value = withContext(Dispatchers.Default) { runCatching { ImageCenterRenderer.render(context, img) }.getOrNull() }
    }
    var state by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }

    val save: () -> Unit = {
        val b = bitmap
        if (b != null) {
            state = DownloadState.Saving
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            CertificateExport.saveImageToGallery(context, b, img.fileName, img.mimeType, img.format, img.quality)
                        } else {
                            CertificateExport.saveImageLegacy(context, b, img.fileName, img.mimeType, img.format, img.quality)
                        }
                    }.getOrDefault(false)
                }
                state = if (ok) DownloadState.Saved else DownloadState.Failed("Couldn't save the image. Check free space and try again.")
            }
        } else {
            state = DownloadState.Failed("The image isn't ready yet. Please try again.")
        }
    }
    // Android 8–9 only: storage permission for the gallery (Android 10+ needs none).
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save() else state = DownloadState.Failed("Storage permission is needed to save images on this Android version.")
    }
    val download: () -> Unit = {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else save()
    }

    AppCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        val shape = RoundedCornerShape(14.dp)
        // Tall wallpaper preview is limited in width so the card stays a comfortable size.
        val tall = img.height > img.width * 1.4f
        Box(Modifier.fillMaxWidth().background(c.surfaceAlt, shape).padding(if (tall) 12.dp else 0.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.then(if (tall) Modifier.widthIn(max = 200.dp) else Modifier).fillMaxWidth()
                    .aspectRatio(img.width / img.height.toFloat())
                    .clip(shape).border(1.dp, c.border, shape).background(c.surfaceAlt),
                contentAlignment = Alignment.Center,
            ) {
                val b = bitmap
                if (b == null) CircularProgressIndicator(color = c.brand)
                else Image(b.asImageBitmap(), "Preview of ${img.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(img.title, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
            Pill("PRO", c.onBrand, c.brand, icon = Icons.Filled.Star)
        }
        Spacer(Modifier.height(4.dp))
        Text(img.description, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        Spacer(Modifier.height(4.dp))
        Text(img.sizeLabel, style = MaterialTheme.typography.bodySmall, color = c.textFaint)
        Spacer(Modifier.height(12.dp))
        AppButton(
            text = when (state) { DownloadState.Saving -> "Saving\u2026"; DownloadState.Saved -> "Download again"; else -> "Download" },
            onClick = download,
            modifier = Modifier.fillMaxWidth(),
            enabled = bitmap != null && state != DownloadState.Saving,
        )
        when (val s = state) {
            DownloadState.Saved -> StatusLine(Icons.Filled.CheckCircle, "Saved to your gallery (Pictures/PyPath)", c.success)
            is DownloadState.Failed -> StatusLine(Icons.Filled.Warning, s.reason, c.error)
            else -> {}
        }
    }
}

@Composable
private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: androidx.compose.ui.graphics.Color) {
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** Entry card shown on the certificate screen to Premium users (Free users don't see it). */
@Composable
fun ImageCenterEntryCard(onOpen: () -> Unit, modifier: Modifier = Modifier, locked: Boolean = false) {
    val c = AppTheme.colors
    AppCard(
        modifier.fillMaxWidth(), onClick = onOpen,
        background = if (locked) c.surface else c.brandSoft, borderColor = if (locked) c.border else c.brand.copy(alpha = 0.35f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (locked) c.surfaceAlt else c.brand), contentAlignment = Alignment.Center) {
                Icon(if (locked) Icons.Filled.Lock else Icons.Filled.Star, null, tint = if (locked) c.textFaint else c.onBrand)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Image Center", style = MaterialTheme.typography.titleSmall, color = c.text)
                Text("Download 2 PyPath images to your gallery", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
            com.pypath.app.ui.screens.premium.PremiumBadge(locked)
        }
    }
}
