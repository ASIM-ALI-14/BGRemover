package com.devx.testapp.viewmodel

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devx.testapp.pipeline.FrameMaskApplier
import com.devx.testapp.pipeline.VideoProcessingPipeline
import com.devx.testapp.util.GalleryExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // ── State ─────────────────────────────────────────────────────────────────

    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()

    private val _outputVideoUri = MutableStateFlow<Uri?>(null)
    val outputVideoUri: StateFlow<Uri?> = _outputVideoUri.asStateFlow()

    private val _processedFrame = MutableStateFlow<Bitmap?>(null)
    val processedFrame: StateFlow<Bitmap?> = _processedFrame.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _status = MutableStateFlow("Select a video to begin")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    // ✅ Video duration — needed by trimmer bar
    private val _videoDurationUs = MutableStateFlow(0L)
    val videoDurationUs: StateFlow<Long> = _videoDurationUs.asStateFlow()

    private val _bgMode: MutableState<FrameMaskApplier.BackgroundMode> =
        mutableStateOf(
            FrameMaskApplier.BackgroundMode.SolidColor(
                Color.parseColor("#1A1A2E")
            )
        )

    val backgroundMode: FrameMaskApplier.BackgroundMode get() = _bgMode.value

    fun setBackgroundMode(mode: FrameMaskApplier.BackgroundMode) {
        _bgMode.value = mode
    }

    fun resetExport() {
        _outputVideoUri.value = null
    }

    fun resetSelection() {
        _selectedVideoUri.value = null
        _videoDurationUs.value  = 0L
    }

    fun shareVideo() {
        val uri = _outputVideoUri.value ?: return
        
        // Convert file URI to content URI using FileProvider for safe sharing
        val contentUri = try {
            val file = File(uri.path ?: return)
            FileProvider.getUriForFile(
                getApplication(),
                "${getApplication<Application>().packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            uri // Fallback to original URI if something fails
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, contentUri)
            type = "video/mp4"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Video").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(chooser)
    }

    // ── Video selection ───────────────────────────────────────────────────────

    fun onVideoSelected(uri: Uri?) {
        if (uri == null) {
            _selectedVideoUri.value = null
            _outputVideoUri.value   = null
            _processedFrame.value   = null
            _videoDurationUs.value  = 0L
            _status.value           = "Select a video to begin"
            return
        }

        viewModelScope.launch {
            _selectedVideoUri.value = uri
            _outputVideoUri.value   = null
            _processedFrame.value   = null
            _videoDurationUs.value  = 0L
            _status.value           = "Loading…"

            validateAndCopy(uri)
                .onSuccess { internal ->
                    _selectedVideoUri.value = internal
                    loadMetadata(internal)
                }
                .onFailure {
                    _status.value = "Error: ${it.message}"
                }
        }
    }

    // ── Processing with trim ──────────────────────────────────────────────────

    fun startProcessing(
        trimStartUs: Long = 0L,
        trimEndUs: Long   = _videoDurationUs.value
    ) {
        val uri = _selectedVideoUri.value ?: run {
            _status.value = "No video selected"
            return
        }

        // Use full duration if trim end not set properly
        val effectiveEnd = if (trimEndUs <= 0L) _videoDurationUs.value else trimEndUs

        viewModelScope.launch {
            _isProcessing.value   = true
            _progress.value       = 0f
            _processedFrame.value = null

            try {
                val out = File(
                    getApplication<Application>().filesDir,
                    "output_${System.currentTimeMillis()}.mp4"
                )

                val pipeline = VideoProcessingPipeline(getApplication())

                val outUri = pipeline.process(
                    inputUri    = uri,
                    outputFile  = out,
                    background  = backgroundMode,
                    trimStartUs = trimStartUs,     // ✅ pass trim start
                    trimEndUs   = effectiveEnd,    // ✅ pass trim end
                    onProgress  = { p, bmp ->
                        _progress.value = p
                        if (bmp != null) {
                            val safe = bmp.copy(Bitmap.Config.ARGB_8888, false)
                            _processedFrame.value?.recycle()
                            _processedFrame.value = safe
                        }
                        _status.value = when {
                            p < 0.90f -> "Removing BG… ${(p * 100).toInt()}%"
                            p < 0.97f -> "Adding audio…"
                            p < 1.00f -> "Saving…"
                            else      -> "Done!"
                        }
                    }
                )

                _outputVideoUri.value = outUri
                _processedFrame.value?.recycle()
                _processedFrame.value = null

                val saved = withContext(Dispatchers.IO) {
                    GalleryExporter.saveToGallery(getApplication(), out)
                }
                _status.value = if (saved != null)
                    "✅ Saved to gallery!"
                else
                    "Done (gallery save failed)"

            } catch (e: Exception) {
                _status.value = "Error: ${e.message}"
                e.printStackTrace()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun validateAndCopy(uri: Uri): Result<Uri> =
        withContext(Dispatchers.IO) {
            try {
                val r = MediaMetadataRetriever()
                r.setDataSource(getApplication(), uri)
                val ok = r.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO
                ) == "yes"
                r.release()
                if (!ok) return@withContext Result.failure(Exception("Not a video"))
                Result.success(copyToInternal(uri))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun copyToInternal(uri: Uri): Uri {
        val ctx   = getApplication<Application>()
        val input = ctx.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open video")
        val dest  = File(ctx.filesDir, "input_video.mp4")
        FileOutputStream(dest).use { input.copyTo(it) }
        return Uri.fromFile(dest)
    }

    private fun loadMetadata(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ext = MediaExtractor()
            try {
                ext.setDataSource(getApplication(), uri, null)
                for (i in 0 until ext.trackCount) {
                    val fmt  = ext.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                    if (!mime.startsWith("video/")) continue

                    val w   = fmt.getInteger(MediaFormat.KEY_WIDTH)
                    val h   = fmt.getInteger(MediaFormat.KEY_HEIGHT)
                    val dur = if (fmt.containsKey(MediaFormat.KEY_DURATION))
                        fmt.getLong(MediaFormat.KEY_DURATION) else 0L
                    val fps = if (fmt.containsKey(MediaFormat.KEY_FRAME_RATE))
                        fmt.getInteger(MediaFormat.KEY_FRAME_RATE) else 30

                    withContext(Dispatchers.Main) {
                        _videoDurationUs.value = dur   // ✅ save duration for trimmer
                        _status.value = "${w}×${h} · ${fps}fps · ${dur / 1_000_000}s — Ready"
                    }
                    break
                }
            } finally {
                ext.release()
            }
        }
    }
}