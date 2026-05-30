package com.devx.VisionCut.ui.screens


import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.devx.VisionCut.viewmodel.VideoLibraryViewModel

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class)
@Composable
fun VideoDetailScreen(
    videoLibraryViewModel: VideoLibraryViewModel,
    onBack: () -> Unit
) {
    val video = videoLibraryViewModel.selectedVideo.collectAsState().value
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Navigate back automatically when the video is deleted (selectedVideo → null)
    LaunchedEffect(video) {
        if (video == null) onBack()
    }
    if (video == null) return  // guard while the LaunchedEffect fires

    var isPlaying       by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Launcher for API 30+ system delete dialog
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            videoLibraryViewModel.loadVideos()
            onBack()
        }
    }

    // ── ExoPlayer ─────────────────────────────────────────────────────────────
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(video.uri))
            prepare()
            playWhenReady = false
            repeatMode    = Player.REPEAT_MODE_ONE
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE  -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> if (isPlaying) exoPlayer.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isPlaying) { exoPlayer.playWhenReady = isPlaying }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    // ── Delete confirmation dialog ─────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title  = { Text("Delete Video") },
            text   = { Text("This will permanently remove the video from your gallery. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        videoLibraryViewModel.deleteVideo(
                            video             = video,
                            onSuccess         = { /* selectedVideo cleared → LaunchedEffect fires onBack() */ },
                            onNeedsPermission = { sender ->
                                deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                            },
                            onError = { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Full-screen layout with gradient overlays ──────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        // Video player
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player     = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable { isPlaying = !isPlaying }
        )

        // Play button overlay
        if (!isPlaying) {
            IconButton(
                onClick  = { isPlaying = true },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(68.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(38.dp),
                    tint     = Color.White
                )
            }
        }

        // ── Top bar (gradient overlay) ────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(0.75f), Color.Transparent)
                    )
                )
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = video.name,
                    style      = MaterialTheme.typography.titleSmall,
                    color      = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f)
                )
            }
        }

        // ── Bottom action bar (gradient overlay) ──────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(0.8f))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Share
            ActionButton(
                icon  = Icons.Default.Share,
                label = "Share",
                onClick = { videoLibraryViewModel.shareVideo(video) },
                modifier = Modifier.weight(1f)
            )

            // Save copy
            ActionButton(
                icon  = Icons.Default.FileDownload,
                label = "Save Copy",
                onClick = {
                    videoLibraryViewModel.saveCopyToGallery(video) { success ->
                        Toast.makeText(
                            context,
                            if (success) "✅ Saved to gallery!" else "Save failed — try again",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.weight(1f)
            )

            // Delete
            ActionButton(
                icon     = Icons.Default.Delete,
                label    = "Delete",
                tint     = Color(0xFFFF5252),
                onClick  = { showDeleteDialog = true },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ── Reusable action button for the bottom bar ─────────────────────────────────
@Composable
private fun ActionButton(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    label   : String,
    onClick : () -> Unit,
    modifier: Modifier = Modifier,
    tint    : Color = Color.White
) {
    Column(
        modifier             = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment  = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}