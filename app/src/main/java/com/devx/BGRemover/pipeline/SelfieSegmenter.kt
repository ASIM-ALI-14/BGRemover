package com.devx.BGRemover.pipeline

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions

class SelfieSegmenter : AutoCloseable {

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

            val mask = result.buffer
            mask.rewind()

            val maskW = result.width
            val maskH = result.height
            val w = bitmap.width
            val h = bitmap.height

            // Step 1 — read raw mask
            val rawMask = FloatArray(maskW * maskH)
            mask.asFloatBuffer().get(rawMask)

            // Step 2 — clean mask: erode then dilate (removes noise at edges)
            val cleanedMask = morphologicalClean(rawMask, maskW, maskH)

            // Step 3 — apply to full resolution bitmap
            applyMask(bitmap, cleanedMask, maskW, maskH, w, h)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Erode then dilate the mask to remove noisy pixels at edges.
     * This is the key to getting clean edges instead of the "cut out" look.
     */
    private fun morphologicalClean(
        mask: FloatArray,
        w: Int,
        h: Int
    ): FloatArray {
        // Step 1: Erode (shrink uncertain edges inward)
        val eroded = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var minVal = mask[y * w + x]
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val ny = (y + dy).coerceIn(0, h - 1)
                        val v = mask[ny * w + nx]
                        if (v < minVal) minVal = v
                    }
                }
                eroded[y * w + x] = minVal
            }
        }

        // Step 2: Dilate (expand back — net effect is edge cleaning)
        val dilated = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var maxVal = eroded[y * w + x]
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val ny = (y + dy).coerceIn(0, h - 1)
                        val v = eroded[ny * w + nx]
                        if (v > maxVal) maxVal = v
                    }
                }
                dilated[y * w + x] = maxVal
            }
        }

        // Step 3: Gaussian blur on mask edges for feathering
        return gaussianBlur(dilated, w, h)
    }

    /**
     * 5x5 Gaussian blur on the mask — smooths the transition zone.
     * This is what makes hair and shoulder edges look soft not hard.
     */
    private fun gaussianBlur(mask: FloatArray, w: Int, h: Int): FloatArray {
        // 5x5 Gaussian kernel (sigma=1.0)
        val kernel = floatArrayOf(
            0.00390625f, 0.015625f, 0.0234375f, 0.015625f, 0.00390625f,
            0.015625f,   0.0625f,  0.09375f,   0.0625f,   0.015625f,
            0.0234375f,  0.09375f, 0.140625f,  0.09375f,  0.0234375f,
            0.015625f,   0.0625f,  0.09375f,   0.0625f,   0.015625f,
            0.00390625f, 0.015625f, 0.0234375f, 0.015625f, 0.00390625f
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
     * Apply cleaned mask to full-resolution source bitmap.
     * Uses bilinear interpolation for smooth upscaling of mask.
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
                // Bilinear interpolation from mask to full resolution
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
                                mask[y0 * maskW + x1] *       dx * (1 - dy) +
                                mask[y1 * maskW + x0] * (1 - dx) *       dy +
                                mask[y1 * maskW + x1] *       dx *       dy
                        ).coerceIn(0f, 1f)

                // Smooth-step alpha — natural edge transition
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