package com.devx.BGRemover.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.devx.BGRemover.pipeline.FrameMaskApplier
import com.devx.BGRemover.ads.InterstitialAdManager
import com.devx.BGRemover.ui.components.VideoTrimmerBar
import com.devx.BGRemover.util.isNetworkAvailable
import com.devx.BGRemover.viewmodel.MainViewModel
import com.devx.BGRemover.viewmodel.PremiumViewModel
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialog
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialogOptions
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener

@OptIn(UnstableApi::class)
@Composable
fun RemoveBGScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    premiumViewModel: PremiumViewModel,
    onProcessed: () -> Unit,
    onGoPremium: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isPremium by premiumViewModel.isPremium.collectAsState()
    val adManager = remember { InterstitialAdManager(context as Activity) }

// ✅ ADD THIS: if the user leaves the screen while an ad is pending/loading,
// cancel the queued callback so processing never starts behind their back.
    DisposableEffect(Unit) {
        onDispose {
            adManager.cancel()
        }
    }
    // FIX: Collect the checking flag — button stays disabled until RevenueCat
    // has confirmed the user's status. This closes the race-condition window
    // where isPremium=false briefly even for paying users.
    val isCheckingStatus by premiumViewModel.isCheckingStatus.collectAsState()

    var showPaywall by remember { mutableStateOf(false) }
    var showNoInternetDialog by remember { mutableStateOf(false) }
    var isAdLoading by remember { mutableStateOf(false) }

    val showAd = !isPremium


    // FIX: React to isPremium changes:
    //   - becomes true  → cancel any queued ad callback immediately
    //   - becomes false → load ad for free users
    LaunchedEffect(isPremium) {
        if (isPremium) {
            adManager.cancel()
        } else {
            adManager.loadAd()
        }
    }

    if (showNoInternetDialog) {
        NoInternetDialog(
            onDismiss = { showNoInternetDialog = false },
            onGoToPremium = {
                showNoInternetDialog = false
                onGoPremium()
            }
        )
    }

    if (showPaywall) {
        PaywallDialog(
            PaywallDialogOptions.Builder()
                .setDismissRequest { showPaywall = false }
                .setShouldDisplayDismissButton(true)
                .setListener(object : PaywallListener {
                    override fun onPurchaseCompleted(
                        customerInfo: CustomerInfo,
                        storeTransaction: StoreTransaction
                    ) {
                        premiumViewModel.updateFromCustomerInfo(customerInfo)
                        showPaywall = false
                    }
                    override fun onRestoreCompleted(customerInfo: CustomerInfo) {
                        premiumViewModel.updateFromCustomerInfo(customerInfo)
                        showPaywall = false
                    }
                    override fun onPurchaseError(error: PurchasesError) { }
                    override fun onRestoreError(error: PurchasesError) { }
                })
                .build()
        )
    }

    val selectedUri by viewModel.selectedVideoUri.collectAsState()
    val outputUri by viewModel.outputVideoUri.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val durationUs by viewModel.videoDurationUs.collectAsState()
    val bgMode = viewModel.backgroundMode
    val isGreen = bgMode is FrameMaskApplier.BackgroundMode.SolidColor

    var trimStart by remember { mutableFloatStateOf(0.15f) }
    var trimEnd by remember { mutableFloatStateOf(0.85f) }
    var isPlaying by remember { mutableStateOf(false) }
    var videoAspectRatio by remember { mutableFloatStateOf(9f / 16f) }

    var adBarHeightPx by remember { mutableIntStateOf(0) }
    val adBarHeightDp = with(LocalDensity.current) { adBarHeightPx.toDp() }

    LaunchedEffect(selectedUri) {
        trimStart = 0.15f
        trimEnd = 0.85f
        viewModel.setBackgroundMode(
            FrameMaskApplier.BackgroundMode.SolidColor(0xFF00C853.toInt())
        )
    }

    val animProgress by animateFloatAsState(progress, label = "prog")

    var dotCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(isProcessing) {
        if (isProcessing) {
            while (true) {
                dotCount = (dotCount + 1) % 4
                kotlinx.coroutines.delay(500)
            }
        } else {
            dotCount = 0
        }
    }
    val dots = ".".repeat(dotCount)

    LaunchedEffect(outputUri) {
        if (outputUri != null) onProcessed()
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = false
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        videoAspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
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
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isPlaying) { exoPlayer.playWhenReady = isPlaying }

    LaunchedEffect(selectedUri, durationUs) {
        val uri = selectedUri ?: return@LaunchedEffect
        if (durationUs <= 0L) return@LaunchedEffect
        exoPlayer.setMediaItem(MediaItem.Builder().setUri(uri).build())
        exoPlayer.prepare()
        exoPlayer.seekTo((trimStart * durationUs / 1000L).toLong())
    }

    var lastTrimStart by remember { mutableFloatStateOf(0.15f) }
    var lastTrimEnd by remember { mutableFloatStateOf(0.85f) }

    LaunchedEffect(trimStart, trimEnd) {
        if (durationUs <= 0L) return@LaunchedEffect
        if (trimStart != lastTrimStart) {
            exoPlayer.seekTo((trimStart * durationUs / 1000L).toLong())
            lastTrimStart = trimStart
        } else if (trimEnd != lastTrimEnd) {
            exoPlayer.seekTo((trimEnd * durationUs / 1000L).toLong())
            lastTrimEnd = trimEnd
        }
    }

    LaunchedEffect(isPlaying, trimStart, trimEnd, durationUs) {
        if (!isPlaying || durationUs <= 0L) return@LaunchedEffect
        val startMs = (trimStart * durationUs / 1000L).toLong()
        val endMs = (trimEnd * durationUs / 1000L).toLong()
        while (true) {
            val currentPos = exoPlayer.currentPosition
            if (currentPos >= endMs || currentPos < startMs) exoPlayer.seekTo(startMs)
            kotlinx.coroutines.delay(16)
        }
    }

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = if (showAd) adBarHeightDp else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Top bar ───────────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(35.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF5F5F5))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "Back",
                        modifier = Modifier.size(18.dp).padding(start = 4.dp),
                        tint = Color.Black
                    )
                }
                Text(
                    text = "Remove Background",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            // ── Video preview ─────────────────────────────────────────────────
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(videoAspectRatio)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFE0E0E0)),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedUri != null) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            update = { it.player = exoPlayer }
                        )
                        Box(
                            modifier = Modifier.fillMaxSize().clickable { isPlaying = !isPlaying },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!isPlaying) {
                                IconButton(
                                    onClick = { isPlaying = true },
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
                    } else {
                        Text("No Video Selected", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (durationUs > 0L) {
                val startSec = (trimStart * durationUs / 1_000_000L).toInt()
                val endSec = (trimEnd * durationUs / 1_000_000L).toInt()
                Text(
                    text = "✂  ${startSec}s — ${endSec}s  (${endSec - startSec}s)",
                    fontSize = 12.sp,
                    color = Color(0xFF6200EE),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
            }

            if (selectedUri != null && durationUs > 0L) {
                VideoTrimmerBar(
                    uri = selectedUri!!,
                    durationUs = durationUs,
                    trimStart = trimStart,
                    trimEnd = trimEnd,
                    onTrimStartChanged = { trimStart = it },
                    onTrimEndChanged = { trimEnd = it }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2A2A2A))
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Background",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFF0F0F0))
                    .padding(4.dp)
            ) {
                BgOption(
                    label = "Green (MP4)",
                    isSelected = isGreen,
                    onClick = {
                        viewModel.setBackgroundMode(
                            FrameMaskApplier.BackgroundMode.SolidColor(0xFF00C853.toInt())
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                BgOption(
                    label = "Transparent (MOV)",
                    isSelected = !isGreen,
                    onClick = {
                        viewModel.setBackgroundMode(FrameMaskApplier.BackgroundMode.Transparent)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Remove BG button ──────────────────────────────────────────────
            Button(
                onClick = {
                    if (isAdLoading) return@Button

                    val startUs = if (durationUs > 0L) (trimStart * durationUs).toLong() else 0L
                    val endUs   = if (durationUs > 0L) (trimEnd   * durationUs).toLong() else Long.MAX_VALUE

                    when {
                        isPremium -> {
                            viewModel.startProcessing(trimStartUs = startUs, trimEndUs = endUs)
                        }
                        !isNetworkAvailable(context) -> {
                            Toast.makeText(context, "No internet connection", Toast.LENGTH_SHORT).show()
                            showNoInternetDialog = true
                        }
                        else -> {
                            isAdLoading = true
                            adManager.showAd(
                                onAdFinished = {
                                    isAdLoading = false  // ← watched full ad → start processing
                                    viewModel.startProcessing(trimStartUs = startUs, trimEndUs = endUs)
                                },
                                onAdDismissed = {
                                    isAdLoading = false  // ← back-pressed → just reset button, do nothing
                                }
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                // FIX: Also disable button while isCheckingStatus=true.
                // This prevents any tap during the brief async window where
                // isPremium is still false even for a premium user.
                enabled = !isProcessing && !isAdLoading && !isCheckingStatus && selectedUri != null,
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6200EE),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF6200EE),
                    disabledContentColor = Color.White
                )
            ) {
                when {
                    // Show subtle loading while we confirm premium status
                    isCheckingStatus -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                    isAdLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Loading Ad...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                    isProcessing -> {
                        Text(
                            text = "Removing Background$dots ${(animProgress * 100).toInt()}%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Remove Background",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BgOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color.White else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else Color.Gray,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun NoInternetDialog(
    onDismiss: () -> Unit,
    onGoToPremium: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF3F0FE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = Color(0xFF6200EE),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF3F0FE))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "No connection",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF534AB7)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "No internet detected",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Connect to the internet to continue, or go Premium for offline background removal — no ads, no limits.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFF5F5F5))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Remove background offline",
                        "No ads, ever",
                        "Unlimited exports"
                    ).forEach { feature ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF6200EE))
                            )
                            Text(text = feature, fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onGoToPremium,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6200EE),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = "Go Premium", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(0.dp))
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 24.dp),
                thickness = 0.5.dp,
                color = Color(0xFFEEEEEE)
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(text = "Maybe later", fontSize = 14.sp, color = Color.Gray)
            }
        }
    }
}