package com.devx.VisionCut.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devx.VisionCut.R
import com.devx.VisionCut.ui.components.ProcessedVideoItem
import com.devx.VisionCut.util.ProcessedVideo
import com.devx.VisionCut.viewmodel.MainViewModel
import com.devx.VisionCut.viewmodel.PremiumViewModel
import com.devx.VisionCut.viewmodel.VideoLibraryViewModel
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialog
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialogOptions
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm                   : MainViewModel,
    premiumViewModel     : PremiumViewModel,
    videoLibraryViewModel: VideoLibraryViewModel,
    onVideoSelected      : () -> Unit,
    onGoPremium          : () -> Unit,
    onOpenVideoDetail    : () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { vm.onVideoSelected(it); onVideoSelected() }
    }

    val context      = LocalContext.current
    val isPremium    by premiumViewModel.isPremium.collectAsState()
    val videos       by videoLibraryViewModel.videos.collectAsState()
    val isLoading    by videoLibraryViewModel.isLoading.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showPaywall  by remember { mutableStateOf(false) }
    val sheetState   = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Video to confirm deletion — null means no dialog showing
    var pendingDelete by remember { mutableStateOf<ProcessedVideo?>(null) }

    // API 30+ system delete dialog
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK)
            videoLibraryViewModel.loadVideos()
    }

    // ── Delete confirmation dialog ─────────────────────────────────────────────
    pendingDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title  = { Text("Delete Video") },
            text   = { Text("\"${video.name}\" will be permanently removed from your gallery.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        videoLibraryViewModel.deleteVideo(
                            video             = video,
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
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    // ── RevenueCat paywall dialog ─────────────────────────────────────────────
    if (showPaywall) {
        PaywallDialog(
            PaywallDialogOptions.Builder()
                .setDismissRequest { showPaywall = false }
                .setShouldDisplayDismissButton(true)
                .setListener(object : PaywallListener {
                    override fun onPurchaseCompleted(customerInfo: CustomerInfo, storeTransaction: StoreTransaction) {
                        premiumViewModel.updateFromCustomerInfo(customerInfo); showPaywall = false
                    }
                    override fun onRestoreCompleted(customerInfo: CustomerInfo) {
                        premiumViewModel.updateFromCustomerInfo(customerInfo); showPaywall = false
                    }
                    override fun onPurchaseError(error: PurchasesError) {}
                    override fun onRestoreError(error: PurchasesError) {}
                })
                .build()
        )
    }

    // ── Settings bottom sheet ─────────────────────────────────────────────────
    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState       = sheetState,
            containerColor   = MaterialTheme.colorScheme.surfaceVariant,
            dragHandle       = null
        ) {
            SettingsScreen(
                onDone      = { showSettings = false },
                onGoPremium = { showSettings = false; onGoPremium() },
                isPremium   = isPremium
            )
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text  = "VisionCut",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Card(
                    modifier  = Modifier.size(100.dp, 45.dp),
                    shape     = RoundedCornerShape(22.dp),
                    colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier             = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Outlined.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = {
                            if (isPremium) Toast.makeText(context, "Already subscribed! Enjoy 🎉", Toast.LENGTH_SHORT).show()
                            else onGoPremium()
                        }) {
                            Icon(Icons.Default.WorkspacePremium, "Premium", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { launcher.launch("video/*") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary,
                shape          = RoundedCornerShape(20),
                modifier       = Modifier.height(65.dp).padding(bottom = 16.dp, end = 8.dp)
            ) {
                Row(
                    modifier             = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // ── Loading ───────────────────────────────────────────────────
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color    = MaterialTheme.colorScheme.primary
                    )
                }

                // ── Empty state ───────────────────────────────────────────────
                videos.isEmpty() -> {
                    Column(
                        modifier             = Modifier.fillMaxSize(),
                        verticalArrangement   = Arrangement.Center,
                        horizontalAlignment   = Alignment.CenterHorizontally
                    ) {
                        val imageRes = if (isSystemInDarkTheme()) {
                            R.drawable.empty_state_dark
                        } else {
                            R.drawable.empty_state_light
                        }

                        Image(
                            painter = painterResource(imageRes),
                            contentDescription = "Empty",
                            modifier = Modifier.size(155.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No processed videos yet",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Tap + New to get started",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ── Video list ────────────────────────────────────────────────
                else -> {
                    LazyColumn(
                        modifier       = Modifier.fillMaxSize(),
                        // Extra bottom padding so the last item isn't hidden behind the FAB
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        item {
                            Text(
                                text     = "${videos.size} processed video${if (videos.size != 1) "s" else ""}",
                                style    = MaterialTheme.typography.labelMedium,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }
                        items(videos, key = { it.id }) { video ->
                            ProcessedVideoItem(
                                video     = video,
                                onClick   = {
                                    videoLibraryViewModel.selectVideo(video)
                                    onOpenVideoDetail()
                                },
                                onShare   = { videoLibraryViewModel.shareVideo(video) },
                                onSaveCopy = {
                                    videoLibraryViewModel.saveCopyToGallery(video) { success ->
                                        Toast.makeText(
                                            context,
                                            if (success) "✅ Saved to gallery!" else "Save failed",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onDelete  = { pendingDelete = video }
                            )
                            HorizontalDivider(
                                modifier  = Modifier.padding(start = 128.dp, end = 16.dp),
                                thickness = 0.5.dp,
                                color     = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
}