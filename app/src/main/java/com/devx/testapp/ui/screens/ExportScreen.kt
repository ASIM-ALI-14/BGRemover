package com.devx.testapp.ui.screens

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
import androidx.compose.ui.unit.sp
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
import com.devx.testapp.ui.components.RewardedAdManager
import com.devx.testapp.viewmodel.MainViewModel
import com.devx.testapp.viewmodel.PremiumViewModel

@androidx.annotation.OptIn(UnstableApi::class)
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
    val adManager = remember { RewardedAdManager(context as Activity) }

// ✅ ADD THIS
    DisposableEffect(Unit) {
        onDispose {
            adManager.cancel()
        }
    }
    // FIX: collect isCheckingStatus — same guard as RemoveBGScreen.
    // Without this the Share/Export buttons were tappable while isPremium
    // was still false (RevenueCat async), so the rewarded ad fired even for
    // paying users on the first tap.
    val isCheckingStatus by premiumViewModel.isCheckingStatus.collectAsState()

    var videoAspectRatio    by remember { mutableFloatStateOf(9f / 16f) }
    var isPlaying           by remember { mutableStateOf(false) }
    var isAdLoadingForShare  by remember { mutableStateOf(false) }
    var isAdLoadingForExport by remember { mutableStateOf(false) }


    // Cancel queued ad callback the moment premium is confirmed;
    // only preload for free users.
    LaunchedEffect(isPremium) {
        if (isPremium) adManager.cancel() else adManager.preload()
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Export Video",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(start = 25.dp)
                            .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                            .background(Color.White, CircleShape)
                            .size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = "Back",
                            tint = Color.Black,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White
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

            // ── Video Preview ─────────────────────────────────────────────────
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
                        .background(Color(0xFFF8F8F8)),
                    contentAlignment = Alignment.Center
                ) {
                    outputUri?.let { uri ->
                        VideoPlayer(
                            uri = uri,
                            isPlaying = isPlaying,
                            onTogglePlay = { isPlaying = !isPlaying },
                            onSizeChanged = { ratio -> videoAspectRatio = ratio }
                        )
                    } ?: CircularProgressIndicator(color = Color(0xFF6200EE))
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Buttons ───────────────────────────────────────────────────────
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
                                onRewarded = {
                                    isAdLoadingForShare = false
                                    viewModel.shareVideo()
                                },
                                onAdDismissed = {          // ✅ back-press resets spinner without sharing
                                    isAdLoadingForShare = false
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    // FIX: disabled while isCheckingStatus — same pattern as RemoveBGScreen
                    enabled = !isAdLoadingForShare && !isAdLoadingForExport && !isCheckingStatus,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.White,
                        disabledContentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    when {
                        isCheckingStatus -> {
                            // Brief spinner while we confirm premium status
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        }
                        isAdLoadingForShare -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        }
                        else -> {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
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
                            isAdLoadingForExport = true
                            isAdLoadingForExport = true
                            adManager.showAd(
                                onRewarded = {
                                    isAdLoadingForExport = false
                                    Toast.makeText(context, "Video saved to gallery!", Toast.LENGTH_SHORT).show()
                                },
                                onAdDismissed = {          // ✅ back-press resets spinner without saving
                                    isAdLoadingForExport = false
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    // FIX: disabled while isCheckingStatus
                    enabled = !isAdLoadingForExport && !isAdLoadingForShare && !isCheckingStatus,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6901FC),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF6901FC),
                        disabledContentColor = Color.White
                    ),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    when {
                        isCheckingStatus -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        }
                        isAdLoadingForExport -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        }
                        else -> {
                            Icon(
                                Icons.Default.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Export", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    uri: Uri,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onSizeChanged: (Float) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_ALL
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        onSizeChanged(videoSize.width.toFloat() / videoSize.height.toFloat())
                    }
                }
            })
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
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
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
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
                    tint = Color.White
                )
            }
        }
    }
}