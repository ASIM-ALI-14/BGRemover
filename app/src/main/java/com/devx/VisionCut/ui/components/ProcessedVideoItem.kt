package com.devx.VisionCut.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devx.VisionCut.util.ProcessedVideo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProcessedVideoItem(
    video     : ProcessedVideo,
    onClick   : () -> Unit,
    onShare   : () -> Unit,
    onSaveCopy: () -> Unit,
    onDelete  : () -> Unit,
    modifier  : Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Thumbnail ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (video.thumbnail != null) {
                Image(
                    bitmap             = video.thumbnail.asImageBitmap(),
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            } else {
                // Branded placeholder when thumbnail unavailable
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    modifier = Modifier.size(26.dp)
                )
            }

            // Duration badge — bottom-right of thumbnail
            if (video.durationMs > 0L) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text  = video.durationMs.formatDuration(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // ── Info ──────────────────────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = video.name,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text  = video.dateMs.formatDate(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text  = video.sizeBytes.formatSize(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── More menu ─────────────────────────────────────────────────────────
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded          = showMenu,
                onDismissRequest  = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text         = { Text("Share") },
                    leadingIcon  = { Icon(Icons.Default.Share, null) },
                    onClick      = { showMenu = false; onShare() }
                )
                DropdownMenuItem(
                    text         = { Text("Save copy to gallery") },
                    leadingIcon  = { Icon(Icons.Default.FileDownload, null) },
                    onClick      = { showMenu = false; onSaveCopy() }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text        = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete, null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}

// ── Formatters ────────────────────────────────────────────────────────────────

private fun Long.formatDuration(): String {
    val totalSec = this / 1_000L
    val h   = totalSec / 3_600
    val m   = (totalSec % 3_600) / 60
    val s   = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun Long.formatDate(): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(this))

private fun Long.formatSize(): String = when {
    this >= 1_048_576L -> "%.1f MB".format(this / 1_048_576.0)
    this >= 1_024L     -> "%.0f KB".format(this / 1_024.0)
    else               -> "$this B"
}