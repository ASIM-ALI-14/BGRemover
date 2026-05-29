package com.devx.BGRemover.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object GalleryExporter {

    fun saveToGallery(context: Context, file: File): Uri? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            saveMediaStore(context, file)
        else
            saveLegacy(context, file)
    } catch (e: Exception) { e.printStackTrace(); null }

    private fun saveMediaStore(context: Context, file: File): Uri? {
        val isWebm    = file.extension.lowercase() == "webm"
// In saveMediaStore, change mime type detection:
        val isZip   = file.extension.lowercase() == "zip"
        val mimeType = when (file.extension.lowercase()) {
            "webm" -> "video/webm"
            "zip"  -> "application/zip"
            else   -> "video/mp4"
        }
        val fileName  = "BGRemoved_${System.currentTimeMillis()}.${file.extension}"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME,  fileName)
            put(MediaStore.Video.Media.MIME_TYPE,     mimeType)
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MOVIES}/BGRemover")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri      = resolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { file.inputStream().copyTo(it) }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, file: File): Uri {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES
            ), "BGRemover"
        ).also { it.mkdirs() }
        val dest = File(dir, "BGRemoved_${System.currentTimeMillis()}.${file.extension}")
        file.copyTo(dest, overwrite = true)
        MediaScannerConnection.scanFile(
            context, arrayOf(dest.absolutePath),
            arrayOf(if (file.extension == "webm") "video/webm" else "video/mp4"), null)
        return Uri.fromFile(dest)
    }
}