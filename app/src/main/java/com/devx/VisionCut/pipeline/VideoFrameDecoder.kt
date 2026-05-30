package com.devx.VisionCut.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import java.nio.ByteBuffer

class VideoFrameDecoder(
    private val context: Context,
    private val videoUri: Uri
) {
    companion object {
        private const val TAG        = "VideoFrameDecoder"
        private const val TIMEOUT_US = 10_000L
    }

    data class VideoInfo(
        val width      : Int,
        val height     : Int,
        val fps        : Int,
        val durationUs : Long,
        val mimeType   : String,
        val rotation   : Int    // 0 / 90 / 180 / 270
    )

    private var codec    : MediaCodec?     = null
    private var extractor: MediaExtractor? = null

    fun probeVideoInfo(): VideoInfo? {
        // Rotation is only available via MediaMetadataRetriever, not MediaExtractor
        val rotation = try {
            MediaMetadataRetriever().run {
                setDataSource(context, videoUri)
                val r = extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                )?.toIntOrNull() ?: 0
                release()
                r
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read rotation metadata", e)
            0
        }

        val ext = MediaExtractor()
        return try {
            ext.setDataSource(context, videoUri, null)
            findVideoTrack(ext)?.let { (_, fmt) ->
                VideoInfo(
                    width      = fmt.getInteger(MediaFormat.KEY_WIDTH),
                    height     = fmt.getInteger(MediaFormat.KEY_HEIGHT),
                    fps        = fmt.getSafe(MediaFormat.KEY_FRAME_RATE, 30),
                    durationUs = fmt.getSafeLong(MediaFormat.KEY_DURATION, 0L),
                    mimeType   = fmt.getString(MediaFormat.KEY_MIME) ?: "video/avc",
                    rotation   = rotation
                )
            }
        } finally {
            ext.release()
        }
    }

    suspend fun decode(onFrame: suspend (Bitmap, Long, Float) -> Boolean) {
        val ext = MediaExtractor().also { extractor = it }

        try {
            ext.setDataSource(context, videoUri, null)

            val (trackIdx, fmt) = findVideoTrack(ext) ?: run {
                Log.e(TAG, "No video track found")
                return
            }

            ext.selectTrack(trackIdx)

            val mime       = fmt.getString(MediaFormat.KEY_MIME) ?: "video/avc"
            val width      = fmt.getInteger(MediaFormat.KEY_WIDTH)
            val height     = fmt.getInteger(MediaFormat.KEY_HEIGHT)
            val durationUs = fmt.getSafeLong(MediaFormat.KEY_DURATION, 1L)

            val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }
            decoder.configure(fmt, null, null, 0)
            decoder.start()

            try {
                val info       = MediaCodec.BufferInfo()
                var sawEOS     = false
                var outputDone = false

                while (!outputDone) {
                    // Feed input frames
                    if (!sawEOS) {
                        val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                        if (inIdx >= 0) {
                            val buf: ByteBuffer = decoder.getInputBuffer(inIdx)!!
                            val size = ext.readSampleData(buf, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(
                                    inIdx, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                sawEOS = true
                            } else {
                                decoder.queueInputBuffer(inIdx, 0, size, ext.sampleTime, 0)
                                ext.advance()
                            }
                        }
                    }

                    // Drain output frames
                    val outIdx = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                    if (outIdx >= 0) {
                        val isEOS = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.getOutputImage(outIdx)?.let { image ->
                            try {
                                val bmp      = image.yuv420ToBitmap(width, height)
                                val progress = info.presentationTimeUs.toFloat() / durationUs
                                if (!onFrame(bmp, info.presentationTimeUs, progress))
                                    outputDone = true
                            } finally {
                                image.close()
                            }
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (isEOS) outputDone = true
                    }
                }
            } finally {
                // Always release codec even if an exception occurs mid-decode
                decoder.stop()
                decoder.release()
                codec = null
            }

        } finally {
            // Always release extractor even on early return or exception
            ext.release()
            extractor = null
        }
    }

    /** Cancel-safe cleanup for external interruption (e.g. coroutine cancellation). */
    fun release() {
        try { codec?.stop()        } catch (e: Exception) { /* may already be stopped */ }
        try { codec?.release()     } catch (e: Exception) { }
        try { extractor?.release() } catch (e: Exception) { }
        codec     = null
        extractor = null
    }

    private fun findVideoTrack(ext: MediaExtractor): Pair<Int, MediaFormat>? {
        for (i in 0 until ext.trackCount) {
            val fmt  = ext.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) return Pair(i, fmt)
        }
        return null
    }

    private fun MediaFormat.getSafe(key: String, def: Int) =
        if (containsKey(key)) getInteger(key) else def

    private fun MediaFormat.getSafeLong(key: String, def: Long) =
        if (containsKey(key)) getLong(key) else def
}

// ── YUV_420_888 → ARGB_8888 Bitmap ──────────────────────────────────────────
// Copies byte planes first (eliminates per-byte JNI overhead).
// Uses integer fixed-point BT.601 (≈3× faster than float math).
fun Image.yuv420ToBitmap(width: Int, height: Int): Bitmap {
    val yP = planes[0]; val uP = planes[1]; val vP = planes[2]

    val yBytes = ByteArray(yP.buffer.remaining()).also { yP.buffer.get(it) }
    val uBytes = ByteArray(uP.buffer.remaining()).also { uP.buffer.get(it) }
    val vBytes = ByteArray(vP.buffer.remaining()).also { vP.buffer.get(it) }

    val yStride  = yP.rowStride
    val uvStride = uP.rowStride
    val uvPixel  = uP.pixelStride

    val argb = IntArray(width * height)

    for (row in 0 until height) {
        for (col in 0 until width) {
            val yIdx  = row * yStride + col
            val uvIdx = (row / 2) * uvStride + (col / 2) * uvPixel

            val y =  (yBytes[yIdx].toInt()  and 0xFF)
            val u = ((uBytes[uvIdx].toInt() and 0xFF) - 128)
            val v = ((vBytes[uvIdx].toInt() and 0xFF) - 128)

            val r = (y + (v * 1436 shr 10)).coerceIn(0, 255)
            val g = (y - (u * 352  shr 10) - (v * 731 shr 10)).coerceIn(0, 255)
            val b = (y + (u * 1814 shr 10)).coerceIn(0, 255)

            argb[row * width + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
}