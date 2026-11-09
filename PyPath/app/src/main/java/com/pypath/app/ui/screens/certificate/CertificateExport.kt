package com.pypath.app.ui.screens.certificate

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File

/** Saves or shares the certificate image. Everything stays on the phone unless the learner shares it. */
object CertificateExport {

    /** Writes the PNG into cache/certificates (blocking: call off the main thread). */
    fun writeToCache(context: Context, bitmap: Bitmap, id: String): File {
        val dir = File(context.cacheDir, "certificates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the newest one
        val file = File(dir, "PyPath-certificate-$id.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    /** Opens the Android share sheet for [file] (call on the main thread). */
    fun share(context: Context, file: File, courseTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "I completed $courseTitle on PyPath.")
            clipData = ClipData.newRawUri("certificate", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share your certificate").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    }

    /** Gallery saving needs no permission from Android 10 (API 29). */
    val canSaveToGallery get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Saves into Pictures/PyPath (blocking: call off the main thread). Returns true on success. */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun saveToGallery(context: Context, bitmap: Bitmap, id: String): Boolean =
        saveImageToGallery(context, bitmap, "PyPath-certificate-$id.png", "image/png", Bitmap.CompressFormat.PNG, 100)

    /**
     * Premium Phase 1: the same MediaStore pipeline for any PyPath image (used by the Image Center).
     * Saves into Pictures/PyPath (blocking: call off the main thread). Returns true on success.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun saveImageToGallery(
        context: Context, bitmap: Bitmap, fileName: String, mimeType: String,
        format: Bitmap.CompressFormat, quality: Int,
    ): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PyPath")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val ok = resolver.openOutputStream(uri)?.use { bitmap.compress(format, quality, it) } ?: false
            if (!ok) { resolver.delete(uri, null, null); return false }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    /**
     * Premium Phase 1: Android 8–9 (API 26–28) have no scoped MediaStore saving, so the Image Center
     * writes to Pictures/PyPath directly (needs WRITE_EXTERNAL_STORAGE, declared only up to API 28)
     * and asks the media scanner to show the file in the gallery. Blocking: call off the main thread.
     */
    @Suppress("DEPRECATION")
    fun saveImageLegacy(context: Context, bitmap: Bitmap, fileName: String, mimeType: String, format: Bitmap.CompressFormat, quality: Int): Boolean {
        return try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PyPath").apply { mkdirs() }
            val dot = fileName.lastIndexOf('.')
            var file = File(dir, fileName)
            var n = 1
            while (file.exists()) { file = File(dir, fileName.substring(0, dot) + " ($n)" + fileName.substring(dot)); n++ }
            val ok = file.outputStream().use { bitmap.compress(format, quality, it) }
            if (!ok) { file.delete(); return false }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
            true
        } catch (e: Exception) {
            false
        }
    }
}
