package com.devx.testapp.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Purple = Color(0xFF6200EE)
private val HandleW = 24.dp
private val TouchW = 48.dp
private val BarH = 60.dp
private const val THUMB_COUNT = 8

@Composable
fun VideoTrimmerBar(
    uri: Uri,
    durationUs: Long,
    trimStart: Float,
    trimEnd: Float,
    onTrimStartChanged: (Float) -> Unit,
    onTrimEndChanged: (Float) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // ✅ FIX 1: Keep references to the LATEST values without restarting pointerInput
    val currentTrimStart = rememberUpdatedState(trimStart)
    val currentTrimEnd = rememberUpdatedState(trimEnd)

    // ── Load thumbnails ───────────────────────────────────────────────────
    val thumbnails = remember(uri) { mutableStateListOf<Bitmap>() }

    LaunchedEffect(uri, durationUs) {
        if (durationUs <= 0L) return@LaunchedEffect
        thumbnails.clear()
        withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, uri)
                val step = durationUs / THUMB_COUNT
                repeat(THUMB_COUNT) { i ->
                    val t = i * step + step / 2
                    r.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { bmp ->
                            withContext(Dispatchers.Main) { thumbnails.add(bmp) }
                        }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                r.release()
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(BarH)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1A1A1A))
    ) {
        val totalW: Dp = maxWidth
        val totalPx: Float = with(density) { totalW.toPx() }

        // ── Thumbnail strip ───────────────────────────────────────────────
        Row(Modifier.fillMaxSize()) {
            if (thumbnails.isEmpty()) {
                repeat(THUMB_COUNT) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFF2A2A2A))
                            .border(0.5.dp, Color(0xFF333333))
                    )
                }
            } else {
                thumbnails.forEach { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // ── Dim overlays & selection ──────────────────────────────────────
        val startPx = trimStart * totalPx
        val endPx = trimEnd * totalPx

        // Dim left
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(density) { startPx.toDp() }.coerceAtLeast(0.dp))
                .align(Alignment.CenterStart)
                .background(Color.Black.copy(alpha = 0.65f))
        )

        // Dim right
        val rightW = with(density) { (totalPx - endPx).toDp() }.coerceAtLeast(0.dp)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(rightW)
                .align(Alignment.CenterEnd)
                .background(Color.Black.copy(alpha = 0.65f))
        )

        // Selection border
        val selW = with(density) { (endPx - startPx).toDp() }.coerceAtLeast(0.dp)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(selW)
                .offset(x = with(density) { startPx.toDp() })
                .border(3.dp, Purple, RoundedCornerShape(4.dp))
                .background(Purple.copy(alpha = 0.06f))
        )

        // ── LEFT handle ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(TouchW)
                .offset(
                    x = with(density) { startPx.toDp() } - TouchW / 2
                )
                // ✅ FIX 2: Key on totalPx (only changes on rotation), NOT trimStart
                .pointerInput(totalPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {},
                        onDragEnd = {},
                        onDragCancel = {},
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val delta = dragAmount / totalPx

                            // ✅ FIX 3: Read the LIVE value using .value
                            val newStart = (currentTrimStart.value + delta)
                                .coerceIn(0f, (currentTrimEnd.value - 0.04f).coerceAtLeast(0f))
                            onTrimStartChanged(newStart)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(HandleW)
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(Purple),
                contentAlignment = Alignment.Center
            ) {
                HandleGrip()
            }
        }

        // ── RIGHT handle ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(TouchW)
                .offset(
                    x = with(density) { endPx.toDp() } - TouchW / 2
                )
                .pointerInput(totalPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {},
                        onDragEnd = {},
                        onDragCancel = {},
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val delta = dragAmount / totalPx

                            // ✅ FIX 3: Read the LIVE value using .value
                            val newEnd = (currentTrimEnd.value + delta)
                                .coerceIn((currentTrimStart.value + 0.04f).coerceAtMost(1f), 1f)
                            onTrimEndChanged(newEnd)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(HandleW)
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(Purple),
                contentAlignment = Alignment.Center
            ) {
                HandleGrip()
            }
        }
    }
}

@Composable
private fun HandleGrip() {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(1) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .background(
                        Color.White.copy(alpha = 0.9f),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}