package com.devx.VisionCut.util


import android.graphics.Bitmap
import android.net.Uri

/** Represents a single video processed and saved by this app. */
data class ProcessedVideo(
    val id         : Long,
    val uri        : Uri,
    val name       : String,
    val dateMs     : Long,      // epoch milliseconds (DATE_ADDED × 1000)
    val durationMs : Long,      // milliseconds
    val sizeBytes  : Long,
    val thumbnail  : Bitmap?    // null if loading failed
)