package com.devx.VisionCut.ui.screens

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.devx.VisionCut.ads.RewardedAdManager
import com.devx.VisionCut.viewmodel.MainViewModel
import com.devx.VisionCut.viewmodel.PremiumViewModel

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: MainViewModel,
    premiumViewModel: PremiumViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val outputUri        by viewModel.outputVideoUri.collectAsState()
    val context          = LocalContext.current
    val isPremium        by premiumViewModel.isPremium.collectAsState()
    val isCheckingStatus by premiumViewModel.isCheckingStatus.collectAsState()
    val adManager        = remember { RewardedAdManager(context as Activity) }

    // Cancel any pending ad callback when the screen is disposed
    DisposableEffect(Unit) {
        onDispose { adManager.cancel() }
    }

    var videoAspectRatio    by remember { mutableFloatStateOf(9f / 16f) }
    var isPlaying           by remember { mutableStateOf(false) }
    var isAdLoadingForShare  by remember { mutableStateOf(false) }
    var isAdLoadingForExport by remember { mutableStateOf(false) }

    // Cancel queued callback once premium is confirmed; preload for free users
    LaunchedEffect(isPremium) {
        if (isPremium) adManager.cancel() else adManager.preload()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Export Video",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(start = 25.dp)
                            .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Video preview ─────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(videoAspectRatio)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    outputUri?.let { uri ->
                        VideoPlayer(
                            uri = uri,
                            isPlaying = isPlaying,
                            onTogglePlay = { isPlaying = !isPlaying },
                            onSizeChanged = { ratio -> videoAspectRatio = ratio }
                        )
                    } ?: CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Share / Export buttons ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ── SHARE ─────────────────────────────────────────────────────
                ElevatedButton(
                    onClick = {
                        if (isAdLoadingForShare || isAdLoadingForExport) return@ElevatedButton
                        if (isPremium) {
                            viewModel.shareVideo()
                        } else {
                            isAdLoadingForShare = true
                            adManager.showAd(
                                onRewarded   = { isAdLoadingForShare = false; viewModel.shareVideo() },
                                onAdDismissed = { isAdLoadingForShare = false }
                            )
                        }
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    enabled  = !isAdLoadingForShare && !isAdLoadingForExport && !isCheckingStatus,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor         = MaterialTheme.colorScheme.surface,
                        contentColor           = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContentColor   = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    when {
                        isCheckingStatus || isAdLoadingForShare -> {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(18.dp),
                                color       = MaterialTheme.colorScheme.onSurface,
                                strokeWidth = 2.dp
                            )
                        }
                        else -> {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Share", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ── EXPORT ────────────────────────────────────────────────────
                Button(
                    onClick = {
                        if (isAdLoadingForExport || isAdLoadingForShare) return@Button
                        if (isPremium) {
                            Toast.makeText(context, "Video saved to gallery!", Toast.LENGTH_SHORT).show()
                        } else {
                            isAdLoadingForExport = true  // BUG FIX: removed duplicate assignment
                            adManager.showAd(
                                onRewarded = {
                                    isAdLoadingForExport = false
                                    Toast.makeText(context, "Video saved to gallery!", Toast.LENGTH_SHORT).show()
                                },
                                onAdDismissed = { isAdLoadingForExport = false }
                            )
                        }
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    enabled  = !isAdLoadingForExport && !isAdLoadingForShare && !isCheckingStatus,
                    colors = ButtonDefaults.buttonColors(
                        containerColor         = MaterialTheme.colorScheme.primary,
                        contentColor           = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary,
                        disabledContentColor   = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    when {
                        isCheckingStatus || isAdLoadingForExport -> {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(18.dp),
                                color       = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        }
                        else -> {
                            Icon(
                                Icons.Default.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint     = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Export",
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    uri: Uri,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onSizeChanged: (Float) -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
            repeatMode    = Player.REPEAT_MODE_ALL
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0)
                        onSizeChanged(videoSize.width.toFloat() / videoSize.height.toFloat())
                }
            })
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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) { exoPlayer.playWhenReady = isPlaying }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onTogglePlay() },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player     = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)  // always black behind video
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        // Play button overlay — always dark regardless of theme
        if (!isPlaying) {
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(32.dp),
                    tint     = Color.White
                )
            }
        }
    }
}