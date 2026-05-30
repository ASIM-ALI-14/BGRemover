package com.devx.VisionCut.viewmodel

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devx.VisionCut.util.ProcessedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoLibraryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG         = "VideoLibraryVM"
        private const val FOLDER_NAME = "BGRemover"  // must match GalleryExporter
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private val _videos    = MutableStateFlow<List<ProcessedVideo>>(emptyList())
    val videos: StateFlow<List<ProcessedVideo>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Shared between HomeScreen and VideoDetailScreen — set before navigating
    private val _selectedVideo = MutableStateFlow<ProcessedVideo?>(null)
    val selectedVideo: StateFlow<ProcessedVideo?> = _selectedVideo.asStateFlow()

    init { loadVideos() }

    // ── Load from MediaStore ──────────────────────────────────────────────────

    fun loadVideos() {
        viewModelScope.launch {
            _isLoading.value = true
            _videos.value    = fetchFromMediaStore()
            _isLoading.value = false
        }
    }

    private suspend fun fetchFromMediaStore(): List<ProcessedVideo> = withContext(Dispatchers.IO) {
        val ctx        = getApplication<Application>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val results    = mutableListOf<ProcessedVideo>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )

        // Filter to only the BGRemover subfolder our app writes to
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        else
            "${MediaStore.Video.Media.DATA} LIKE ?"

        ctx.contentResolver.query(
            collection, projection,
            selection, arrayOf("%$FOLDER_NAME%"),
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            while (cursor.moveToNext()) {
                val id  = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                results.add(
                    ProcessedVideo(
                        id         = id,
                        uri        = uri,
                        name       = cursor.getString(nameCol) ?: "video_$id.mp4",
                        dateMs     = cursor.getLong(dateCol) * 1_000L, // seconds → ms
                        durationMs = cursor.getLong(durCol),
                        sizeBytes  = cursor.getLong(sizeCol),
                        thumbnail  = loadThumbnail(ctx, uri, id)
                    )
                )
            }
        }
        results
    }

    @Suppress("DEPRECATION")
    private fun loadThumbnail(ctx: Application, uri: Uri, id: Long): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ctx.contentResolver.loadThumbnail(uri, Size(320, 180), null)
        } else {
            MediaStore.Video.Thumbnails.getThumbnail(
                ctx.contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "Thumbnail failed for $uri", e)
        null
    }

    // ── Selection ─────────────────────────────────────────────────────────────

    fun selectVideo(video: ProcessedVideo) { _selectedVideo.value = video }
    fun clearSelection()                   { _selectedVideo.value = null  }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Deletes a video from MediaStore.
     * On Android 11+ a system dialog may be needed — [onNeedsPermission] provides the sender.
     */
    fun deleteVideo(
        video: ProcessedVideo,
        onSuccess        : () -> Unit = {},
        onNeedsPermission: (IntentSender) -> Unit = {},
        onError          : (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val deleted = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.delete(video.uri, null, null)
                }
                if (deleted > 0) {
                    // Remove instantly — no need to reload the whole list
                    _videos.update { list -> list.filter { it.id != video.id } }
                    _selectedVideo.value = null
                    onSuccess()
                } else {
                    onError("Could not delete video")
                }
            } catch (e: SecurityException) {
                // API 30+ — need a system-shown delete dialog
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val req = MediaStore.createDeleteRequest(
                        getApplication<Application>().contentResolver, listOf(video.uri)
                    )
                    onNeedsPermission(req.intentSender)
                } else {
                    onError("Delete failed: permission denied")
                }
            }
        }
    }

    // ── Share ─────────────────────────────────────────────────────────────────

    fun shareVideo(video: ProcessedVideo) {
        val app = getApplication<Application>()
        app.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, video.uri)
                    type  = "video/mp4"
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                },
                "Share Video"
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
    }

    // ── Save copy to gallery ──────────────────────────────────────────────────
    // Saves a fresh copy to Movies/ (root) — different from Movies/BGRemover/

    fun saveCopyToGallery(
        video  : ProcessedVideo,
        onDone : (success: Boolean) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                withContext(Dispatchers.Main) { onDone(false) }
                return@launch
            }
            try {
                val app      = getApplication<Application>()
                val fileName = "VisionCut_${System.currentTimeMillis()}.mp4"
                val values   = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE,    "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = app.contentResolver
                val newUri   = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: run { withContext(Dispatchers.Main) { onDone(false) }; return@launch }

                resolver.openOutputStream(newUri)?.use { out ->
                    resolver.openInputStream(video.uri)?.use { inp -> inp.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(newUri, values, null, null)

                withContext(Dispatchers.Main) { onDone(true) }
            } catch (e: Exception) {
                Log.e(TAG, "saveCopyToGallery failed", e)
                withContext(Dispatchers.Main) { onDone(false) }
            }
        }
    }
}