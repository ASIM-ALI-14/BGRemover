package com.devx.BGRemover.pipeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import androidx.annotation.ColorInt

class FrameMaskApplier {

    sealed class BackgroundMode {
        object Transparent : BackgroundMode()
        data class SolidColor(@ColorInt val color: Int = Color.BLACK) : BackgroundMode()
        data class Image(val image: Bitmap) : BackgroundMode()
        object BlurBackground : BackgroundMode()
    }

    fun applyMask(
        frame: Bitmap,
        foregroundBitmap: Bitmap?,
        mode: BackgroundMode = BackgroundMode.SolidColor(Color.BLACK)
    ): Bitmap {
        val w = frame.width
        val h = frame.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Step 1 — draw background
        when (mode) {
            is BackgroundMode.Transparent  -> {
                // Clear to fully transparent
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            }
            is BackgroundMode.SolidColor   -> canvas.drawColor(mode.color)
            is BackgroundMode.Image        -> {
                val scaled = Bitmap.createScaledBitmap(mode.image, w, h, true)
                canvas.drawBitmap(scaled, 0f, 0f, null)
                if (scaled !== mode.image) scaled.recycle()
            }
            is BackgroundMode.BlurBackground -> {
                val blurred = blurBitmap(frame)
                canvas.drawBitmap(blurred, 0f, 0f, null)
                blurred.recycle()
            }
        }

        // Step 2 — draw person on top (null-safe)
        foregroundBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        return output
    }

    /**
     * Fast blur by scale-down then scale-up.
     * Produces a Gaussian-like blur suitable for background replacement.
     */
    private fun blurBitmap(src: Bitmap): Bitmap {
        val scale   = 10
        val small   = Bitmap.createScaledBitmap(
            src, (src.width / scale).coerceAtLeast(1),
            (src.height / scale).coerceAtLeast(1), true
        )
        val blurred = Bitmap.createScaledBitmap(small, src.width, src.height, true)
        small.recycle()
        return blurred
    }
}