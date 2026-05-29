package com.devx.BGRemover.pipeline


import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

class VideoProcessingPipeline(private val context: Context) {

    companion object {
        private const val TAG = "Pipeline"
    }

    suspend fun process(
        inputUri: Uri,
        outputFile: File,
        background: FrameMaskApplier.BackgroundMode =
            FrameMaskApplier.BackgroundMode.SolidColor(0xFF000000.toInt()),
        trimStartUs: Long = 0L,
        trimEndUs: Long = Long.MAX_VALUE,
        onProgress: suspend (Float, Bitmap?) -> Unit = { _, _ -> }
    ): Uri = withContext(Dispatchers.Default) {

        // 1. Probe video info
        val decoder = VideoFrameDecoder(context, inputUri)
        val info = decoder.probeVideoInfo()
            ?: throw IllegalArgumentException("Cannot read video")

        val effectiveTrimEnd =
            if (trimEndUs == Long.MAX_VALUE) info.durationUs else trimEndUs
        val trimDuration = (effectiveTrimEnd - trimStartUs).coerceAtLeast(1L)

        Log.i(TAG, "Input: ${info.width}×${info.height} " +
                "rot=${info.rotation}° @${info.fps}fps")
        Log.i(TAG, "Trim: ${trimStartUs / 1_000_000}s " +
                "→ ${effectiveTrimEnd / 1_000_000}s")

        // 2. Calculate encoder dimensions
        val (encW, encH) = when (info.rotation) {
            90, 270 -> info.height.align() to info.width.align()
            else    -> info.width.align()  to info.height.align()
        }

        // 3. Transparent → use black bg (MP4 cannot store alpha)
        val effectiveBg =
            if (background is FrameMaskApplier.BackgroundMode.Transparent)
                FrameMaskApplier.BackgroundMode.SolidColor(android.graphics.Color.BLACK)
            else background

        // 4. Set up encoder → temp file
        val tempFile  = File(outputFile.parent, "tmp_${System.currentTimeMillis()}.mp4")
        val segmenter = SelfieSegmenter()
        val applier   = FrameMaskApplier()
        val encoder   = VideoFrameEncoder(tempFile, encW, encH, info.fps)

        encoder.start()

        var frameIdx      = 0
        var previewBitmap: Bitmap? = null
        var firstPts      = -1L

        // 5. Decode → segment → composite → encode
        decoder.decode { raw, ptsUs, _ ->
            if (!isActive) return@decode false

            // Skip frames before trim start
            if (ptsUs < trimStartUs) {
                raw.recycle()
                return@decode true
            }

            // Stop after trim end
            if (ptsUs > effectiveTrimEnd) {
                raw.recycle()
                return@decode false
            }

            // Normalize PTS so output video starts at 0
            if (firstPts < 0) firstPts = ptsUs
            val normalizedPts = ptsUs - firstPts

            val frame     = raw.rotated(info.rotation)
            val fg        = segmenter.segment(frame)
            val processed = applier.applyMask(frame, fg, effectiveBg)
            frame.recycle()
            fg?.recycle()

            encoder.encodeFrame(processed, normalizedPts)

            val progress  = (ptsUs - trimStartUs).toFloat() / trimDuration
            val isPreview = frameIdx % 20 == 0

            withContext(Dispatchers.Main) {
                onProgress(
                    progress * 0.90f,
                    if (isPreview) processed else null
                )
            }

            if (!isPreview) {
                processed.recycle()
            } else {
                previewBitmap?.recycle()
                previewBitmap = processed
            }

            frameIdx++
            true
        }

        // 6. Finish encoding
        encoder.finish()
        segmenter.close()
        previewBitmap?.recycle()
        previewBitmap = null

        Log.i(TAG, "Encoded $frameIdx frames")

        // 7. Mux audio into final output
        withContext(Dispatchers.Main) { onProgress(0.93f, null) }

        withContext(Dispatchers.IO) {
            AudioMuxer.mux(
                context          = context,
                videoFile        = tempFile,
                originalVideoUri = inputUri,
                outputFile       = outputFile,
                trimStartUs      = trimStartUs,
                trimEndUs        = effectiveTrimEnd
            )
        }

        tempFile.delete()

        Log.i(TAG, "Done → $outputFile ($frameIdx frames)")
        withContext(Dispatchers.Main) { onProgress(1f, null) }
        Uri.fromFile(outputFile)
    }

    private fun Bitmap.rotated(deg: Int): Bitmap {
        if (deg == 0) return this
        val m = Matrix().apply { postRotate(deg.toFloat()) }
        val r = Bitmap.createBitmap(this, 0, 0, width, height, m, true)
        recycle()
        return r
    }

    private fun Int.align() = (this + 1) / 2 * 2
}