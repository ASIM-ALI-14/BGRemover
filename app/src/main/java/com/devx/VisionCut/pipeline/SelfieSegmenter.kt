package com.devx.VisionCut.pipeline

import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions

class SelfieSegmenter : AutoCloseable {

    companion object {
        private const val TAG = "SelfieSegmenter"
    }

    // STREAM_MODE = temporal smoothing across frames = much better edges on video
    private val segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .enableRawSizeMask()
            .build()
    )

    fun segment(bitmap: Bitmap): Bitmap? {
        return try {
            val result = Tasks.await(
                segmenter.process(InputImage.fromBitmap(bitmap, 0))
            )

            val mask  = result.buffer
            mask.rewind()

            val maskW = result.width
            val maskH = result.height
            val w     = bitmap.width
            val h     = bitmap.height

            // Step 1 — read raw mask values
            val rawMask = FloatArray(maskW * maskH)
            mask.asFloatBuffer().get(rawMask)

            // Step 2 — erode + dilate + blur to clean edge noise
            val cleanedMask = morphologicalClean(rawMask, maskW, maskH)

            // Step 3 — apply cleaned mask to full-resolution bitmap
            applyMask(bitmap, cleanedMask, maskW, maskH, w, h)

        } catch (e: Exception) {
            Log.e(TAG, "Segmentation failed", e)
            null
        }
    }

    /**
     * Erode then dilate the mask to remove noisy edge pixels,
     * followed by Gaussian blur for soft feathering.
     */
    private fun morphologicalClean(mask: FloatArray, w: Int, h: Int): FloatArray {
        // Erode — shrink uncertain edges inward
        val eroded = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var minVal = mask[y * w + x]
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val ny = (y + dy).coerceIn(0, h - 1)
                        val v  = mask[ny * w + nx]
                        if (v < minVal) minVal = v
                    }
                }
                eroded[y * w + x] = minVal
            }
        }

        // Dilate — expand back (net effect is edge cleaning)
        val dilated = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var maxVal = eroded[y * w + x]
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val ny = (y + dy).coerceIn(0, h - 1)
                        val v  = eroded[ny * w + nx]
                        if (v > maxVal) maxVal = v
                    }
                }
                dilated[y * w + x] = maxVal
            }
        }

        // Gaussian blur — smooth transition zone for feathered edges
        return gaussianBlur(dilated, w, h)
    }

    /**
     * 5×5 Gaussian blur (sigma ≈ 1.0) — makes hair/shoulder edges
     * look soft instead of hard-cut.
     */
    private fun gaussianBlur(mask: FloatArray, w: Int, h: Int): FloatArray {
        val kernel = floatArrayOf(
            0.00390625f, 0.015625f,  0.0234375f, 0.015625f,  0.00390625f,
            0.015625f,   0.0625f,    0.09375f,   0.0625f,    0.015625f,
            0.0234375f,  0.09375f,   0.140625f,  0.09375f,   0.0234375f,
            0.015625f,   0.0625f,    0.09375f,   0.0625f,    0.015625f,
            0.00390625f, 0.015625f,  0.0234375f, 0.015625f,  0.00390625f
        )

        val result = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                for (ky in -2..2) {
                    for (kx in -2..2) {
                        val nx = (x + kx).coerceIn(0, w - 1)
                        val ny = (y + ky).coerceIn(0, h - 1)
                        sum += mask[ny * w + nx] * kernel[(ky + 2) * 5 + (kx + 2)]
                    }
                }
                result[y * w + x] = sum
            }
        }
        return result
    }

    /**
     * Apply cleaned mask to full-resolution source bitmap
     * using bilinear interpolation for smooth upscaling.
     */
    private fun applyMask(
        src: Bitmap,
        mask: FloatArray,
        maskW: Int,
        maskH: Int,
        outW: Int,
        outH: Int
    ): Bitmap {
        val srcPixels = IntArray(outW * outH)
        src.getPixels(srcPixels, 0, outW, 0, 0, outW, outH)
        val outPixels = IntArray(outW * outH)

        for (y in 0 until outH) {
            for (x in 0 until outW) {
                // Bilinear interpolation: map full-res pixel to mask coords
                val fx = x.toFloat() / outW * (maskW - 1)
                val fy = y.toFloat() / outH * (maskH - 1)
                val x0 = fx.toInt().coerceIn(0, maskW - 1)
                val y0 = fy.toInt().coerceIn(0, maskH - 1)
                val x1 = (x0 + 1).coerceIn(0, maskW - 1)
                val y1 = (y0 + 1).coerceIn(0, maskH - 1)
                val dx = fx - x0
                val dy = fy - y0

                val confidence = (
                        mask[y0 * maskW + x0] * (1 - dx) * (1 - dy) +
                                mask[y0 * maskW + x1] *       dx  * (1 - dy) +
                                mask[y1 * maskW + x0] * (1 - dx)  *       dy +
                                mask[y1 * maskW + x1] *       dx  *       dy
                        ).coerceIn(0f, 1f)

                // Smooth-step alpha for natural edge transition
                val alpha = when {
                    confidence >= 0.85f -> 255
                    confidence <= 0.15f -> 0
                    else -> {
                        val t  = (confidence - 0.15f) / 0.70f
                        val st = t * t * (3f - 2f * t)
                        (st * 255f).toInt().coerceIn(0, 255)
                    }
                }

                outPixels[y * outW + x] =
                    (alpha shl 24) or (srcPixels[y * outW + x] and 0x00FFFFFF)
            }
        }

        return Bitmap.createBitmap(outPixels, outW, outH, Bitmap.Config.ARGB_8888)
    }

    override fun close() = segmenter.close()
}