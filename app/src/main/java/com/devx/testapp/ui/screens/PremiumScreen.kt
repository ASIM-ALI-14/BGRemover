package com.devx.testapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.ui.revenuecatui.Paywall
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener
import com.revenuecat.purchases.ui.revenuecatui.PaywallOptions
import com.devx.testapp.viewmodel.PremiumViewModel
import com.revenuecat.purchases.ui.revenuecatui.PaywallFooter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun PremiumScreen(
    premiumViewModel: PremiumViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val hasNavigatedBack = remember { AtomicBoolean(false) }

    fun navigateBackOnce() {
        if (hasNavigatedBack.compareAndSet(false, true)) {
            scope.launch(Dispatchers.Main) { onBack() }
        }
    }

    val isLoading by premiumViewModel.isCheckingStatus.collectAsState()

    // ✅ Safety net — if screen is disposed without any callback firing
    DisposableEffect(Unit) {
        onDispose {
            premiumViewModel.resetCheckingStatus()
        }
    }
    BackHandler(enabled = isLoading) {
        premiumViewModel.resetCheckingStatus() // stops loading
        // No navigateBackOnce() → stays on paywall to try again
    }
    Paywall(
        options = PaywallOptions.Builder(
            dismissRequest = {
                // ✅ User tapped X dismiss button on paywall
                premiumViewModel.resetCheckingStatus()
                navigateBackOnce()
            }
        )
            .setShouldDisplayDismissButton(true)
            .setListener(object : PaywallListener {

                // ✅ Purchase success
                override fun onPurchaseCompleted(
                    customerInfo: CustomerInfo,
                    storeTransaction: StoreTransaction
                ) {
                    premiumViewModel.updateFromCustomerInfo(customerInfo)
                    navigateBackOnce()
                }

                // ✅ User pressed BACK or tapped OUTSIDE Google Play billing dialog
                override fun onPurchaseCancelled() {
                    premiumViewModel.resetCheckingStatus()
                    // No navigateBackOnce() → user stays on paywall to try again
                }

                // ✅ Purchase failed (network error, card declined, etc.)
                override fun onPurchaseError(error: PurchasesError) {
                    premiumViewModel.resetCheckingStatus()
                    // No navigateBackOnce() → user stays on paywall to try again
                }

                // ✅ Restore success
                override fun onRestoreCompleted(customerInfo: CustomerInfo) {
                    premiumViewModel.updateFromCustomerInfo(customerInfo)
                    navigateBackOnce()
                }

                // ✅ Restore failed
                override fun onRestoreError(error: PurchasesError) {
                    premiumViewModel.resetCheckingStatus()
                    // No navigateBackOnce() → user stays on paywall to try again
                }

            })
            .build()
    )
}