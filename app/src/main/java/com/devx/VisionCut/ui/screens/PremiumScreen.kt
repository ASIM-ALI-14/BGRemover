package com.devx.VisionCut.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.ui.revenuecatui.Paywall
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener
import com.revenuecat.purchases.ui.revenuecatui.PaywallOptions
import com.devx.VisionCut.viewmodel.PremiumViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun PremiumScreen(
    premiumViewModel: PremiumViewModel,
    onBack: () -> Unit
) {
    val scope              = rememberCoroutineScope()
    val hasNavigatedBack   = remember { AtomicBoolean(false) }
    val isLoading          by premiumViewModel.isCheckingStatus.collectAsState()

    // Prevent double navigation (e.g. purchase + dismiss firing together)
    fun navigateBackOnce() {
        if (hasNavigatedBack.compareAndSet(false, true)) {
            // rememberCoroutineScope() already runs on Main — no Dispatchers.Main needed
            scope.launch { onBack() }
        }
    }

    // Safety net: reset loading state if screen is disposed without a callback
    DisposableEffect(Unit) {
        onDispose { premiumViewModel.resetCheckingStatus() }
    }

    // Intercept back while loading — stays on paywall so user can retry
    BackHandler(enabled = isLoading) {
        premiumViewModel.resetCheckingStatus()
    }

    Paywall(
        options = PaywallOptions.Builder(
            dismissRequest = {
                // User tapped X on paywall
                premiumViewModel.resetCheckingStatus()
                navigateBackOnce()
            }
        )
            .setShouldDisplayDismissButton(true)
            .setListener(object : PaywallListener {

                // Purchase succeeded
                override fun onPurchaseCompleted(customerInfo: CustomerInfo, storeTransaction: StoreTransaction) {
                    premiumViewModel.updateFromCustomerInfo(customerInfo)
                    navigateBackOnce()
                }

                // User pressed back or tapped outside billing sheet — stay on paywall
                override fun onPurchaseCancelled() {
                    premiumViewModel.resetCheckingStatus()
                }

                // Network error / card declined — stay on paywall to retry
                override fun onPurchaseError(error: PurchasesError) {
                    premiumViewModel.resetCheckingStatus()
                }

                // Restore succeeded
                override fun onRestoreCompleted(customerInfo: CustomerInfo) {
                    premiumViewModel.updateFromCustomerInfo(customerInfo)
                    navigateBackOnce()
                }

                // Restore failed — stay on paywall to retry
                override fun onRestoreError(error: PurchasesError) {
                    premiumViewModel.resetCheckingStatus()
                }
            })
            .build()
    )
}