package com.devx.testapp.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devx.testapp.R
import com.devx.testapp.viewmodel.MainViewModel
import com.devx.testapp.viewmodel.PremiumViewModel
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialog
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialogOptions
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    premiumViewModel: PremiumViewModel,   // ← add this
    onVideoSelected: () -> Unit,
    onGoPremium: () -> Unit          // ← add

) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            vm.onVideoSelected(it)
            onVideoSelected()
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }  // ← add this
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isPremium by premiumViewModel.isPremium.collectAsState()
    val context = LocalContext.current  // add at top of composable
    // ── Built-in Paywall Dialog ───────────────────────────────────────────────
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

                    override fun onPurchaseError(error: PurchasesError) {}
                    override fun onRestoreError(error: PurchasesError) {}
                })
                .build()
        )
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
            containerColor = Color(0xFFF5F6F8),
            dragHandle = null
        ) {
            SettingsScreen(
                onDone = { showSettings = false },
                onGoPremium = {
                    showSettings = false
                    onGoPremium()        // ← navigate to full screen
                }, isPremium = isPremium
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VisionCut",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Card(
                    modifier = Modifier.size(100.dp, 45.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = Color.Black
                            )
                        }
                        // ← Premium icon now opens paywall
                        IconButton(onClick = {
                            if (isPremium) {
                                Toast.makeText(
                                    context,
                                    "Already subscribed! Enjoy 🎉",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                onGoPremium()
                            }
                        }) {
                            Icon(
                                Icons.Default.WorkspacePremium,
                                contentDescription = "Premium",
                                tint = Color(0xFF6901FC)
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { launcher.launch("video/*") },
                containerColor = Color(0xFF6200EE),
                contentColor = Color.White,
                shape = RoundedCornerShape(20),
                modifier = Modifier
                    .height(65.dp)
                    .padding(bottom = 16.dp, end = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.empty_state_light),
                contentDescription = "Empty",
                modifier = Modifier.size(155.dp)
            )
            Text(
                "No video yet",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray
            )
        }
    }
}
