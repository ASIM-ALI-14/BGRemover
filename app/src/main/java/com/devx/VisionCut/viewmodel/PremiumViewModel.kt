package com.devx.VisionCut.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PremiumViewModel : ViewModel() {

    companion object {
        private const val TAG = "PremiumViewModel"
    }

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _isCheckingStatus = MutableStateFlow(true)
    val isCheckingStatus: StateFlow<Boolean> = _isCheckingStatus.asStateFlow()

    // Fires whenever RevenueCat receives updated customer data (e.g. after a purchase)
    private val customerInfoListener = UpdatedCustomerInfoListener { customerInfo ->
        handleCustomerInfo(customerInfo)
    }

    init {
        Purchases.sharedInstance.updatedCustomerInfoListener = customerInfoListener
        checkPremiumStatus()
    }

    /** Reset loading state when paywall is dismissed without completing a purchase. */
    fun resetCheckingStatus() {
        _isCheckingStatus.value = false
    }

    fun checkPremiumStatus() {
        _isCheckingStatus.value = true
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                handleCustomerInfo(customerInfo)
            }
            override fun onError(error: PurchasesError) {
                Log.e(TAG, "getCustomerInfo failed: ${error.message}")
                _isPremium.value        = false
                _isCheckingStatus.value = false
            }
        })
    }

    fun updateFromCustomerInfo(customerInfo: CustomerInfo) {
        handleCustomerInfo(customerInfo)
    }

    private fun handleCustomerInfo(customerInfo: CustomerInfo) {
        // "premium" must match the entitlement identifier in your RevenueCat dashboard.
        // Consider moving this key to PremiumConstants if not already there.
        _isPremium.value        = customerInfo.entitlements["premium"]?.isActive == true
        _isCheckingStatus.value = false
    }

    override fun onCleared() {
        super.onCleared()
        // Remove listener to prevent callbacks after ViewModel is destroyed
        Purchases.sharedInstance.updatedCustomerInfoListener = null
        _isCheckingStatus.value = false
    }
}