package com.devx.VisionCut.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

object GalleryExporter {

    private const val TAG = "GalleryExporter"

    fun saveToGallery(context: Context, file: File): Uri? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            saveMediaStore(context, file)
        else
            saveLegacy(context, file)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save to gallery", e)
        null
    }

    private fun saveMediaStore(context: Context, file: File): Uri? {
        val ext      = file.extension.lowercase()
        val mimeType = when (ext) {
            "webm" -> "video/webm"
            "zip"  -> "application/zip"
            else   -> "video/mp4"
        }
        val fileName = "BGRemoved_${System.currentTimeMillis()}.$ext"

        // NOTE: zip files are still inserted into MediaStore.Video — they won't
        // appear correctly in the gallery. If zip export is real, use a separate
        // MediaStore.Files or Downloads URI for non-video types.
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME,  fileName)
            put(MediaStore.Video.Media.MIME_TYPE,     mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/BGRemover")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri      = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        resolver.openOutputStream(uri)?.use { out -> file.inputStream().copyTo(out) }

        // Mark file as complete so media scanner picks it up
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, file: File): Uri {
        val ext = file.extension.lowercase()  // lowercase for consistent comparison
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "BGRemover"
        ).also { it.mkdirs() }

        val dest = File(dir, "BGRemoved_${System.currentTimeMillis()}.$ext")
        file.copyTo(dest, overwrite = true)

        val mimeType = if (ext == "webm") "video/webm" else "video/mp4"
        MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mimeType), null)

        return Uri.fromFile(dest)
    }
}